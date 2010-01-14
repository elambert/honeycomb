/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



/* 
**  cc thisfile.c -lthread 
*/

/*
    C simulator of honeycomb load, primarily for testing write 
    performance with different nfs options. Uses multithreaded
    (parallel) writes. NOTE: could add metadata to this.

    See options and first set of #defines for parameters.

    The -l option allows testing on local mounts. The default
    is to not use local mounts.

    From HoneycombTestConstants.java:

    // === nomenclature ===
    //
    // 'fragment size' of 64k is actually the size of each data write to
    // the frag file. Max frag file size is apparently 3200x64k + any
    // parity and header/footer data. 'block size' thus refers to one
    // stripe of 64k across the fragment files. 'chunks' are also referred
    // to as 'extents'.
    //
*/

#include <stdio.h>
#include <sys/time.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>

#define _REENTRANT    /* basic 3-lines for threads */
/* #include <pthread.h> */
#include <thread.h> 

#define FRAGS_PER_SFILE 7

#define OK_OPEN_CUTOFF 150

#define DISTRIB_SIZE 10
#define DISTRIB_DEFAULT_MAX 400

#define FNAME_BUF_SIZE 256

#define DATA_BUF_SIZE (64 * 1024)
#define MAX_WRITE_BUFS 3200
#define DEFAULT_WRITE_BUFS 1000

#define DEFAULT_NB_DISKS 4
#define DEFAULT_NB_NODES 16
#define DEFAULT_NB_ITERATIONS 10
#define PROGRESSION_SIZE 50

int sync_slices = 1;
int simple_output = 0;
int repeatable_random = 0;

/********* shared variables **************/

int nb_nodes = DEFAULT_NB_NODES;
int nb_disks = DEFAULT_NB_DISKS;
int write_bufs = DEFAULT_WRITE_BUFS;
int pid, node, verbose = 0, quiet = 0, local_disks = 0, selected_host = -1;
int selected_disk = -1;
int frags_per_sfile = FRAGS_PER_SFILE;
char data_buf[DATA_BUF_SIZE];
char **data_bufs;

/********* shared across rounds of striped-per-frag threads **********/

thread_t tids[FRAGS_PER_SFILE];
int fds[FRAGS_PER_SFILE];
char fname_buffers[FRAGS_PER_SFILE][FNAME_BUF_SIZE];
long frag_times[FRAGS_PER_SFILE];
int frag_hosts[FRAGS_PER_SFILE];
int frag_disks[FRAGS_PER_SFILE];

/********************************************************************/

static void
usage()
{
  fprintf(stderr, "Usage :\n\
hcload [-i nb_iterations] [-w nb_write_bufs] [-n nb_nodes] [-d nb_disks per node] [-m max] [-o host] [-s disk] [-f fragments] [-lvrSN]\n\n");
  fprintf(stderr, "defaults:\n\
\tnb_disks\t%d\n\tnb_nodes\t%d\n\tnb_iterations\t%d\n\tnb_write_bufs\t%d\n",
DEFAULT_NB_DISKS, DEFAULT_NB_NODES, DEFAULT_NB_ITERATIONS, DEFAULT_WRITE_BUFS);
  fprintf(stderr, "\n\t-w\tnumber of 64k bufs (must be <= %d)\n", 
                   MAX_WRITE_BUFS);
  fprintf(stderr, "\t-f\tfragments (must be <= %d)\n", FRAGS_PER_SFILE);
  fprintf(stderr, "\t-l\tlocal disks only (fragments=nb_disks/node)\n");
  fprintf(stderr, "\t-o <host>\tspecific host (101, 102, .. 116)\n");
  fprintf(stderr, "\t-s <disk>\tspecific disk (0...max)\n");
  fprintf(stderr, "\t-N\tno sync between slices\n");
  fprintf(stderr, "\t-m <max>\tdistribution max\n");
  fprintf(stderr, "\t-r\trepeatable random choice of hosts/drives\n");
  fprintf(stderr, "\t-v\tverbose\n");
  fprintf(stderr, "\t-S\tsimple output (min/max/avg for open/write/close)\n");
  exit(1);
}

/*
 *  /dev/urandom is more unpredictable than random()
 */
int urandom_fd = -1;
int 
urandom()
{
  if (repeatable_random)
    return random();

  int ret;
  int cnt = read(urandom_fd, &ret, sizeof(ret));
  if (cnt == -1) {
    perror("reading /dev/urandom");
    exit(1);
  }
  if (cnt != sizeof(ret)) {
    fprintf(stderr, "read %d sted sizeof(int) %d\n",
                     cnt, sizeof(ret));
    exit(1);
  }
  if (ret < 0)
    ret *= -1;
  return ret;
}

long
get_system_millis()
{
  struct timeval t;
  gettimeofday(&t, NULL);

  return(t.tv_sec*1000 + t.tv_usec/1000);
}

typedef struct _statistic_t {
  long min, max, avg;
  long sfile_min, sfile_max, sfile_avg;
  int count1, count2;
  int dist1[DISTRIB_SIZE];
  int dist2[DISTRIB_SIZE];
  long dist_max;
  int excess1, excess2;
} statistic_t;

void 
avg_stat(statistic_t *st)
{
  if (st->count1 > 0)
    st->avg /= st->count1;
  if (st->count2 > 0)
    st->sfile_avg /= st->count2;
}

void
print_stat(statistic_t *st, char *tag)
{
  int i;

  if (simple_output) {
    printf("%s  min, max, avg: %d %d %d\n", tag, st->min, st->max, st->avg);
    return;
  }

  printf("%s:\n", tag);
  if (st->count1 > 0)
    printf("  min, max, avg: %d %d %d\n", st->min, st->max, st->avg);
  printf("  per striped file min, max, avg: %d %d %d\n", 
         st->sfile_min, st->sfile_max, st->sfile_avg);
  printf("  distributions 0..%d | greater\n  ", st->dist_max);
  if (st->count1 > 0) {
    for (i=0; i<DISTRIB_SIZE; i++)
      printf("%6d ", st->dist1[i]);
    if (st->excess1 > 0)
      printf(" | %d", st->excess1);
    printf("\n  ");
  }
  for (i=0; i<DISTRIB_SIZE; i++)
    printf("%6d ", st->dist2[i]);
  if (st->excess2 > 0)
    printf(" | %d", st->excess2);
  printf("\n");
}

statistic_t *
get_statistic(long dist_max)
{
  statistic_t *st = (statistic_t *) malloc(sizeof(statistic_t));
  if (st == NULL) {
    perror("malloc");
    exit(1);
  }
  memset(st, 0, sizeof(statistic_t));
  st->dist_max = dist_max;
  return st;
}

/*
**  return index of max time if times != NULL
*/
int
update_stats(statistic_t *st, long times[], long t1, int it)
{
  int i, max_index = 0;

  /*
  **  per-frag
  */
  if (times != NULL) {
    long max = times[0];

    st->count1 += frags_per_sfile;
    /* frags */
    if (it == 0) {
      st->min = st->max = times[0];
    }
    for (i=0; i<frags_per_sfile; i++) {
      long t = times[i];

      /* track max */
      if (t > max) {
        max = t;
        max_index = i;
      }
      
      /* stats*/

      st->avg += t;
      if (t < st->min)
        st->min = t;
      else if (t > st->max)
        st->max = t;

      /* distribution */
      if (t >= st->dist_max) {
        st->excess1++;
      } else {
        int bin = (int)((t * DISTRIB_SIZE)/st->dist_max);
        st->dist1[bin]++;
      }
    }
  }

  /* 
  **  striped file
  */
  st->count2++;
  if (it == 0)
    st->sfile_min = st->sfile_max = t1;
  else if (t1 < st->sfile_min)
    st->sfile_min = t1;
  else if (t1 > st->sfile_max)
    st->sfile_max = t1;
  st->sfile_avg += t1;

  if (t1 >= st->dist_max) {
     st->excess2++;
  } else {
    int bin = (int)((t1 * DISTRIB_SIZE)/st->dist_max);
    st->dist2[bin]++;
  }
  return max_index;
}

int
onetoone(int i) 
{
  if (i < node)
    return i + 101;
  else
    return i+1 + 101;
}

int counts[16];
int disk_counts[16 * 16];

void
choose_drives()
{
  int i, j, k, nnode, disk;

  memset(counts, 0, sizeof(counts));
  memset(disk_counts, 0, sizeof(disk_counts));

  for (i=0; i<frags_per_sfile; i++) {

    char *buffer = fname_buffers[i];

    /* 
    **  choose node
    */
    if (selected_host != -1) {
      nnode = selected_host;
    } else if (local_disks) {
      nnode = node;
    } else {
      if (nb_nodes-1 == frags_per_sfile) {
        /* perfect match */
        nnode = onetoone(i);
      } else if (nb_nodes-1 < frags_per_sfile) {
        /* >1 frag per node in 1+ cases */
        if (i < nb_nodes-1) {
          /* first 'layer' is one-to-one */
          nnode = onetoone(i);
          counts[nnode-101]++;
        } else {
          /* try for <= 2 frags/node */
          nnode = -1;
          for (k=0; k<i; k++) {
            int try = (int)(urandom() % nb_nodes);
            if (counts[try] == 1) {
              nnode = try + 101;
              counts[try]++;
              break;
            }
          }
          if (nnode == -1) {
            /* just place it at random */
            nnode = (int)(urandom() % nb_nodes) + 101;
            counts[nnode-101]++;
          }
        }
      } else {
        /* more than enough nodes - place randomly w/out collision */
        int max_ct = 0;
        for (j=0; ; j++) {
          int try = (int)(urandom() % nb_nodes);
          if (counts[try] > 0)
            continue;
          nnode = try + 101;
          if (nnode == node)
            continue;
          counts[try]++;
          break;
        }
      }
    }
    frag_hosts[i] = nnode - 101;

    /*
    **  choose drive
    */
    if (selected_disk != -1) {
      disk = selected_disk;
    } else if (local_disks  &&  frags_per_sfile == nb_disks) {
      disk = i;
    } else if (counts[nnode - 101] == 1) {
      /* pick 1st disk on this host at random */
      int hindex = (nnode - 101) * nb_disks;
      disk = urandom() % nb_disks;
      disk_counts[hindex + disk]++;
    } else {
      /* try to avoid collisions on this host */
      int hindex = (nnode - 101) * nb_disks;
      for (j=0; ; j++) {
        int count;
        disk = urandom() % nb_disks;
        count = disk_counts[hindex + disk];
        if (count == 0) {
          disk_counts[hindex + disk]++;
          break;
        }
        if (count == 1  &&  j > 16) {
          disk_counts[hindex + disk]++;
          break;
        }
        if (count > 1  && j > 32) {
          disk_counts[hindex + disk]++;
          break;
        }
      }
    }

    frag_disks[i] = disk;

    /*
    **  make path
    */
    if (local_disks) {
      snprintf(buffer, FNAME_BUF_SIZE, "/data/%d/%02d/%02d/TESTFILE.%d.%d.%d",
             disk,
             urandom() % 100,
             urandom() % 100,
             node, pid, i);
    } else {
      snprintf(buffer, FNAME_BUF_SIZE, 
            "/netdisks/10.123.45.%d/data/%d/%02d/%02d/TESTFILE.%d.%d.%d",
	     nnode,
	     disk,
	     urandom() % 100,
	     urandom() % 100,
             node, pid, i);
    }
  }
}

/************************** thread routines *******************/

void *
open_frag(void *arg)
{
  int frag_num = (int)arg;
  char *buffer = fname_buffers[frag_num];
  long t1;
  int fd = -1;
  ssize_t len;

#ifdef DEBUG
  printf("Trying to open file [%s]\n",
	 buffer);
#endif

  t1 = get_system_millis();

  /****************************************/

  fd = open(buffer, O_RDWR | O_CREAT, 0);
  if (fd == -1) {
    fprintf(stderr, "Failed to open file [%s] - %s\n",
	    buffer, strerror(errno));
    exit(-1);
  }
#ifdef DEBUG
  printf("Thread %d opened %s\n", frag_num, buffer);
#endif

  /* write filename as header */
  len = write(fd, buffer, strlen(buffer));
  if (len == -1) {
    fprintf(stderr, "Failed to write file [%s] - %s\n",
	    buffer, strerror(errno));
    exit(-1);
  }
  if (len != strlen(buffer)) {
    fprintf(stderr, "Write short [%s] - %d/%d - %s\n",
	    buffer, len, strlen(buffer), strerror(errno));
    exit(-1);
  }
  fds[frag_num] = fd;

  /****************************************/
  t1 = get_system_millis() - t1;
  frag_times[frag_num] = t1;

}

void *
write_slice(void *arg)
{
  int i;
  int frag_num = (int)arg;
  long t1 = get_system_millis();
  size_t len = write(fds[frag_num], data_bufs[frag_num], DATA_BUF_SIZE);
  if (len == -1) {
    perror("write");
    exit(1);
  }
  if (len != DATA_BUF_SIZE) {
    fprintf(stderr, "wrote %d sted %d: %s\n", len, DATA_BUF_SIZE, 
            fname_buffers[frag_num]);
    exit(1);
  }
  frag_times[frag_num] += get_system_millis() - t1;
}

void *
write_frag(void *arg)
{
  int i;
  int frag_num = (int)arg;
  long t1 = get_system_millis();
  for (i=0; i<write_bufs; i++) {
    size_t len = write(fds[frag_num], data_buf, DATA_BUF_SIZE);
    if (len == -1) {
      perror("write");
      exit(1);
    }
    if (len != DATA_BUF_SIZE) {
      fprintf(stderr, "wrote %d sted %d: %s\n", len, DATA_BUF_SIZE,
            fname_buffers[frag_num]);
      exit(1);
    }
  }
  frag_times[frag_num] += get_system_millis() - t1;
}

void *
close_frag(void *arg)
{
  int frag_num = (int)arg;
  char *buffer = fname_buffers[frag_num];
  long t1 = get_system_millis();

  if (close(fds[frag_num]) == -1) {
    perror("close");
    exit(-1);
  }
  t1 = get_system_millis() - t1;
  frag_times[frag_num] = t1;

#ifdef DEBUG
  printf("Thread %d closed/unlinked %s\n", frag_num, buffer);
#endif
}

void *
unlink_frag(void *arg)
{
  int frag_num = (int)arg;
  char *buffer = fname_buffers[frag_num];
  long t1 = get_system_millis();

  if (unlink(buffer)) {
    fprintf(stderr, "WARNING ! Failed to delete [%s]\n",
	    buffer);
  }
  t1 = get_system_millis() - t1;
  frag_times[frag_num] = t1;
}

/************************** main *******************/
int
main(int argc, char *argv[])
{
  int nb_iterations = DEFAULT_NB_ITERATIONS;
  long distrib_max = DISTRIB_DEFAULT_MAX;

  /* timekeeping */
  statistic_t *open_stat, *write_stat, *close_stat, *unlink_stat, *tot_stat;
  char slowest_file[FNAME_BUF_SIZE];
  long slowest_time = -1;
  time_t slowest;
  int host_counts[16];
  int host_slow_counts[16];
  int disk_counts[16];
  int disk_slow_counts[16];
  int total_disk_slow = 0;

  int i, j, k;
  char c;
  char hostname[256];

  memset(host_counts, 0, sizeof(host_counts));
  memset(host_slow_counts, 0, sizeof(host_slow_counts));
  memset(disk_counts, 0, sizeof(disk_counts));
  memset(disk_slow_counts, 0, sizeof(disk_slow_counts));

  if (gethostname(hostname, 256) == -1) {
    perror("gethostname");
    exit(1);
  }
  i = strlen(hostname);
  node = 100 + atoi(hostname + i - 2);

  if (!repeatable_random) {
    urandom_fd = open("/dev/urandom", O_RDONLY);
    if (urandom_fd == -1) {
      perror("/dev/urandom");
      exit(1);
    }
  }

/* check urandom distrib 
  for (i=0; i<2000; i++) {
    int kk = urandom() % 4;
    disk_counts[kk]++;
  }
  for (i=0; i<2000; i++) {
    int kk = random() % 4;
    disk_slow_counts[kk]++;
  }
  printf("urandom:\n");
  for (i=0; i<4; i++)
    printf("%6d ", disk_counts[i]);
  printf("\n");
  printf("random (always the same):\n");
  for (i=0; i<4; i++)
    printf("%6d ", disk_slow_counts[i]);
  printf("\n");
  exit(0);
*/

/*
printf("stats: %x %x %x %x\n", open_stat, write_stat, close_stat, tot_stat);
exit(0);
*/
  while ((c=getopt(argc, argv, "i:d:n:m:o:s:f:w:NSqrvhl")) != -1) {
    switch (c) {
    case 'i':
      nb_iterations = atoi(optarg);
      if (nb_iterations == 0)
	usage();
      break;

    case 'n':
      nb_nodes = atoi(optarg);
      if (nb_nodes == 0)
	usage();
      break;
      
    case 'd':
      nb_disks = atoi(optarg);
      if (nb_disks == 0)
	usage();
      break;

    case 'm':
      distrib_max = atoi(optarg);
      break;

    case 'o':
      selected_host = atoi(optarg);
      if (selected_host < 101  ||  selected_host > 116)
        usage();
      break;

    case 'r':
      repeatable_random = 1;
      break;

    case 's':
      selected_disk = atoi(optarg);
      if (selected_disk < 0  ||  selected_disk > 7)
        usage();
      break;

    case 'f':
      frags_per_sfile = atoi(optarg);
      if (frags_per_sfile < 1  ||  frags_per_sfile > FRAGS_PER_SFILE)
        usage();
      break;

    case 'v':
      verbose = 1;
      break;

    case 'w':
      write_bufs = atoi(optarg);
      if (write_bufs < 0  ||  write_bufs > MAX_WRITE_BUFS)
        usage();
      break;

    case 'q':
      quiet = 1;
      break;

    case 'l':
      local_disks = 1;
      break;

    case 'S':
      simple_output = 1;
      break;

    case 'N':
      sync_slices = 0;
      break;

    case 'h':
    case '?':
    default:
      usage();
    }
  }

  if (selected_host != -1  &&  local_disks) {
    fprintf(stderr, "-o and -l are incompatible\n");
    usage();
  }
  if (quiet && verbose) {
    fprintf(stderr, "-q and -v are incompatible\n");
    usage();
  }
  if (local_disks) {
    printf("-l: setting frags_per_sfile to %d\n", nb_disks);
    frags_per_sfile = nb_disks;
  }

  for (i=0; i<DATA_BUF_SIZE; i++) {
    data_buf[i] = 'A' + (i % 26);
  }
  data_bufs = (char **) malloc(frags_per_sfile * sizeof(char *));
  for (i=0; i<frags_per_sfile; i++) {
    data_bufs[i] = (char *)malloc(DATA_BUF_SIZE);
    memcpy(data_bufs[i], data_buf, DATA_BUF_SIZE);
  }

  open_stat = get_statistic(distrib_max);
  write_stat = get_statistic(distrib_max);
  close_stat = get_statistic(distrib_max);
  unlink_stat = get_statistic(distrib_max);
  tot_stat = get_statistic(distrib_max);

  pid = getpid();

  if (!quiet  &&  !simple_output) {
    printf("********************\n\
* Configuration:\n\
* node = %d\n\
* pid = %d\n\
* %d frags/file\n\
* %d 64k bufs per frag\n\
* %d nodes\n\
* %d disks per node\n\
* %d iterations\n\
********************\n",
	 node, pid, frags_per_sfile, write_bufs,
         nb_nodes, nb_disks, nb_iterations);
    if (local_disks)
      printf("* local disks only\n");
    if (selected_host != -1)
      printf("* host %d only\n", selected_host);
    if (selected_disk != -1)
      printf("* disk %d only\n", selected_disk);

     printf("\n");
  }

  /*
  **  each iteration simulates storing a file 
  **  in FRAGS_PER_SFILE pieces
  */
  long t0, t2, t3;
  t3 = 0;
  t0 = get_system_millis();
  for (i=0; i<nb_iterations; i++) {
    int max_index;
    long t1;

    if (verbose)
      printf("sfile %d:\n", i);

    t2 = 0;

    /*
    **  initialize per-frag state
    */
    for (j=0; j<FRAGS_PER_SFILE; j++) {
      fds[j] = -1;
      fname_buffers[j][0] = '\0';
    }

    /*
    **  pick drives
    */
    choose_drives();

    /*
    **  open in parallel
    */
    t1 = get_system_millis();
    for (j=0; j<frags_per_sfile; j++) {
      thr_create(NULL, 0, open_frag, (void *) j, 0, &tids[j]);
    }
    /*
    **  sync
    */
    while (thr_join(0, NULL, NULL) == 0);

    /*
    **  analyze
    */
    t1 = get_system_millis() - t1;
    if (verbose)
      printf("\topen: %ld\n", t1);
    t2 += t1;
    max_index = update_stats(open_stat, frag_times, t1, i);

    /* track slowest frag for comparison w/ trace records */
    t1 = frag_times[max_index];
    if (i == 0  ||  t1 > slowest_time) {
      time(&slowest);
      slowest_time = t1;
      strlcpy(slowest_file, fname_buffers[max_index], FNAME_BUF_SIZE);
/* printf("slowest: %d  %ld %s  %s\n", i, slowest_time, slowest_file, ctime(&slowest)); */
    }

    if (t1 > OK_OPEN_CUTOFF) {
      /* pick up all relevant hosts */
      for (j=0; j<frags_per_sfile; j++) {
        if (frag_times[j] > OK_OPEN_CUTOFF) {
          total_disk_slow++;
          host_slow_counts[frag_hosts[j]]++;
          disk_slow_counts[frag_disks[j]]++;
        }
      }
      /* and all disks */
    }
    /* record host & disk distrib */
    for (j=0; j<frags_per_sfile; j++) {
      host_counts[frag_hosts[j]]++;
      disk_counts[frag_disks[j]]++;
    }

    /*
    **  write slices, sync, analyze
    */
    memset(frag_times, 0, sizeof(frag_times));
    t1 = get_system_millis();
    if (sync_slices) {
      for (j=0; j<write_bufs; j++) {
        for (k=0; k<frags_per_sfile; k++) {
          thr_create(NULL, 0, write_slice, (void *) k, 0, &tids[k]);
        }
        while (thr_join(0, NULL, NULL) == 0);
      }
    } else {
      for (k=0; k<frags_per_sfile; k++) {
        thr_create(NULL, 0, write_frag, (void *) k, 0, &tids[k]);
      }
      while (thr_join(0, NULL, NULL) == 0);
    }
    t1 = get_system_millis() - t1;
    if (verbose)
      printf("\twrite: %ld\n", t1);
    t2 += t1;
    t3 += t1;
    update_stats(write_stat, frag_times, t1, i);

    /* XXX add md write here.. */

    /*
    **  close, sync, analyze
    */
    t1 = get_system_millis();
    for (j=0; j<frags_per_sfile; j++) {
      thr_create(NULL, 0, close_frag, (void *) j, 0, &tids[j]);
    }
    while (thr_join(0, NULL, NULL) == 0);
    t1 = get_system_millis() - t1;
    if (verbose)
      printf("\tclose: %ld\n", t1);
    t2 += t1;
    update_stats(close_stat, frag_times, t1, i);

    update_stats(tot_stat, NULL, t2, i);

    /*
    **  unlink, sync, analyze (not counted in total)
    */
    t1 = get_system_millis();
    for (j=0; j<frags_per_sfile; j++) {
      thr_create(NULL, 0, unlink_frag, (void *) j, 0, &tids[j]);
    }
    while (thr_join(0, NULL, NULL) == 0);
    t1 = get_system_millis() - t1;
    if (verbose)
      printf("\tunlink: %ld\n", t1);
    update_stats(unlink_stat, frag_times, t1, i);

    if (verbose)
      printf("\ttotal[%d]: %ld\n", i, t2);
  }

  t0 = get_system_millis() - t0;

  avg_stat(open_stat);
  avg_stat(write_stat);
  avg_stat(close_stat);
  avg_stat(unlink_stat);
  avg_stat(tot_stat);

  print_stat(open_stat, "open");
  print_stat(write_stat, "write");
  print_stat(close_stat, "close");

  if (simple_output) {
    float tot_frags = (float)nb_iterations * (float)frags_per_sfile;
    printf("slow_open %d  %5.2f %%\n", total_disk_slow, 
           100.0 * (float)total_disk_slow / tot_frags);
    printf("agg_write_bw_mb_s %12.2f\n", 1000.0 *
                         (tot_frags * (float)DATA_BUF_SIZE * (float)write_bufs)
                         / ((float) t3 * (float)(1024 * 1024)));
/*
    printf("net_agg_bw_mb_s %12.2f\n", 1000.0 *
                         (tot_frags * (float)DATA_BUF_SIZE * (float)write_bufs)
                         / ((float) t0 * (float)(1024 * 1024)));
*/
    printf("total_time %ld\n", t0);
    exit(0);
  }

  print_stat(tot_stat, "open/write/close");
  print_stat(unlink_stat, "unlink");

  /* disk distrib */
  printf("\ndisk distrib for open > %d msec (total %d):\n  ", 
         OK_OPEN_CUTOFF, total_disk_slow);
  for (i=0; i<nb_disks; i++)
    printf("%6d ", disk_slow_counts[i]);
  printf("\n");
  printf("overall disk distrib:\n  ");
  for (i=0; i<nb_disks; i++)
    printf("%6d ", disk_counts[i]);
  printf("\n");

  /* host distrib */
  printf("\nhost distrib for open > %d msec:\n  ", OK_OPEN_CUTOFF);
  for (i=0; i<nb_nodes; i++)
    printf("%6d ", host_slow_counts[i]);
  printf("\n");
  printf("overall host distrib:\n  ");
  for (i=0; i<nb_nodes; i++)
    printf("%6d ", host_counts[i]);
  printf("\n");
  printf("slowest-to-open frag (%ld ms):  %s  %s\n", 
          slowest_time, slowest_file, ctime(&slowest));
}
