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




#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <time.h>
#include <assert.h>

#include "hcclient.h"
#include "util.h"
#include "getopt.h"
#include "hctestutil.h"
#include "hctestcommon.h"

/* run aganist the emulator */
int emulator = 0;

static int duration = 3600;
static int delete_nth = 100;
static operation_t op = LOAD_STORE;
static stat_op_t** stat_res = NULL;
static long *seed_res = NULL;
static pthread_t* threads = NULL;
static char* dataVip = NULL;
static int port = 8080;
static char *name_space = "perf_types";
extern char *optarg;
extern int optind, opterr, optopt;

/* extern variables */
hc_long_t debug_flags = 0;
int failearly = 0;
char* tmpDir = "/tmp";

static void usage() 
{
  hc_test_log(LOG_ERROR_LEVEL,NULL,
    "\nusage: hcload \n"
    " -t <duration in seconds> (mix, mix2, store, add_md, query, queryplus,\n"
    "    retrieve only)\n"
    "    or specify -1 to run continuously for mix and mix2 only\n"
    "    Note that retrieve and add_md will only continue to run\n"
    "    while there are records to read from the store output file\n"
    " -n <nb_threads> \n"
    " -N <namespace> default is perf_types if not provided\n"
    "    All the metadata fields from the specified namespace will\n"
    "    be used to generate and query metadata. To avoid HADB limitations\n"
    "    the namespace should be made up of a single table.\n"
    " -o <store|retrieve|query|queryplus|delete|addmd|mix|mix2> \n"
    " -p <port> \n"
    " -v <dataVIP>\n"
    " -s <file_size1, file_size2, ..., file_sizeN> (store, mix, addmd only)\n"
    " -S <max_metadata_size>\n"
    "     Maximum num of characters for metadata string, char and binary types\n"
    "     Specify a single value only. Defaults are 64 for emulator and\n"
    "     512 for a cluster. A value > 512 is not allowed.\n"
    " -e <true|false|metadata_str_length_limit> (emulator)\n"
    " -f <true|<num>> (failearly)\n"
    " -l <tmpdir>\n"
    " -D <n> (delete nth file for mix2, default 100, 0=none)\n"
    " -d <debug> (non-zero prints debug info - see api code for bitmask)\n"
    " -h (print this message) \n"
    );
}

#ifdef	_MSC_VER	/* Microsoft Windows Only */
static LARGE_INTEGER freq;
extern int my_gettime(struct timespec *tspec) {
  LARGE_INTEGER tim;
  double seconds;

  if (freq.QuadPart == 0) {
     QueryPerformanceFrequency(&freq);
  }
  QueryPerformanceCounter(&tim);
  seconds = (double)tim.QuadPart / (double) freq.QuadPart;
  
  tspec->tv_sec = (int)seconds;
  tspec->tv_nsec = ((uint64_t)(seconds * 1000000000.0)) % 1000000000;

  return 0;
}
#endif	/* _MSC_VER */

static stat_op_t*

allocate_stat_op(operation_t op, int nb_sizes, int64_t* file_sizes, int md_sizes, int64_t* metadata_sizes)
{
  cumul_op_t* cumul = NULL;
  stat_op_t* res = NULL;
  int64_t *sizes = NULL;
  int64_t *sizes_md = NULL;
  int max_sizes = nb_sizes;

  if (max_sizes < md_sizes)
      max_sizes = md_sizes;
  
  if (nb_sizes != 0) {
    CALLOC(sizes, int64_t*, nb_sizes, sizeof(int64_t));
    memcpy(sizes,file_sizes,nb_sizes * sizeof(int64_t));
  }
  if (md_sizes != 0) {
    CALLOC(sizes_md, int64_t*, md_sizes, sizeof(int64_t));
    memcpy(sizes_md,metadata_sizes,md_sizes * sizeof(int64_t));
  }
  
  if (max_sizes != 0) {
    CALLOC(cumul, cumul_op_t*, max_sizes, sizeof(cumul_op_t));      
  }

  CALLOC(res, stat_op_t*, 1, sizeof(stat_op_t));
  res->op = op;
  res->nb_sizes = nb_sizes;
  res->md_sizes = md_sizes;
  res->time_loop = duration;
  res->sizes = sizes;
  res->metadata_sizes = metadata_sizes;
  res->res = cumul;
  return res;
}


static void
free_stat_op(stat_op_t* stat)
{
  if (stat == NULL) {
    return;
  }
  if (stat->res != NULL) {
    free(stat->res);
    stat->res = NULL;
  }
  if (stat->sizes != NULL) {
    free(stat->sizes);
    stat->sizes = NULL;
  }
  free(stat);
}


static void*
start_thread(void* arg)
{
  int my_thread_index = (int)arg;
  stat_op_t* stat;
  struct timespec init_seed;
  long seed;

  // On Windows, at least, each thread needs to call srandom()
  seed = seed_res[my_thread_index];
  srandom(seed);

#ifdef	DEBUG
  hc_test_log(LOG_DEBUG_LEVEL,NULL, "start thread %d\n", my_thread_index);
#endif
  stat = (stat_op_t*) stat_res[my_thread_index];

  switch(stat->op) {
  case LOAD_MIX:
    hc_test_log(LOG_INFO_LEVEL, NULL, "starting performance mix\n");
    start_perf_mix(stat, dataVip, port, name_space);
    break;
  case LOAD_MIX2:
    hc_test_log(LOG_INFO_LEVEL, NULL, "starting mix2\n");
    start_perf_mix2(stat, dataVip, port, delete_nth);
    break;
  case LOAD_STORE:
    start_perf_store(stat, dataVip, port, name_space);
    break;
  case LOAD_RETRIEVE:
    start_perf_retrieve(stat, dataVip, port);
    break;
  case LOAD_QUERY:
    start_perf_query(stat, dataVip, port);
    break;
  case LOAD_ADD_MD:
    start_perf_add_md(stat, dataVip, port, name_space);
    break;
  case LOAD_DELETE:
    start_perf_delete(stat, dataVip, port);
    break;
  case LOAD_QUERYPLUS:
    start_perf_queryplus(stat, dataVip, port);
    break;
  default:
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Unknown op %d -- exiting test!\n",stat->op);
    break;
  }
#ifdef DEBUG
  hc_test_log(LOG_DEBUG_LEVEL, NULL, "end thread %d\n", my_thread_index);
#endif
  return (void *)0;
}

static void
start_test(int nb_threads, operation_t op, int nb_sizes, int64_t* sizes, int md_sizes, int64_t* metadata_sizes)
{
  pthread_t t;
  int i = 0;
  int res;
  
  CALLOC(threads, pthread_t*, sizeof(pthread_t),nb_threads);

  // Pick a random seed for each thread
  for (i = 0; i < nb_threads; i++) {
    seed_res[i] = random();
    stat_res[i] = allocate_stat_op(op, nb_sizes, sizes, md_sizes, metadata_sizes);
  }
  // Start all the threads
  for (i = 0; i < nb_threads; i++) {
#ifdef	DEBUG
    hc_test_log(LOG_DEBUG_LEVEL,NULL, 
      "starting thread %d with random_seed %ld\n", i, seed_res[i]);
#endif
    res = pthread_create(&t, NULL, start_thread, (void*)i);
    if (res != 0) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot create thread, error = %d, abort...\n", res);
      exit (1);
    }
    threads[i] = t;
  }
  // Wait for all threads to complete
  for (i = 0; i < nb_threads; i++) {
    pthread_join(threads[i], NULL);
#ifdef DEBUG
    hc_test_log(LOG_DEBUG_LEVEL, NULL, "main thread joined thread %d\n", i);
#endif
  }
}


static void 
display_results(int nb_threads, int num_sizes, int file_size_type)
{
  char op_str[OP_LENGTH];
  double res;
  int current_size = 0;
  uint64_t total_time = 0;
  uint64_t total_ops = 0;
  uint64_t total_errors = 0;
  uint64_t total_warnings = 0;
  int i, j;

  memset(op_str, 0, OP_LENGTH);
  get_opstr(stat_res[0]->op, op_str);

  hc_test_log(LOG_INFO_LEVEL, NULL, "Results for %s:\n", op_str);
  for (j = 0; j < num_sizes; j++) {
    total_time = 0;
    total_ops = 0;
    for (i = 0; i < nb_threads; i++) {
#ifdef	DEBUG
      hc_test_log(LOG_DEBUG_LEVEL, NULL, 
                 "      thread %d size_code %d time=%#6.4g ops=%d\n",
            i,j,
            (double)stat_res[i]->res[j].total_time / 1000000.0,
            stat_res[i]->res[j].nb_op);
#endif /* DEBUG */
      total_time += stat_res[i]->res[j].total_time;
      total_ops += stat_res[i]->res[j].nb_op;
      total_errors += stat_res[i]->res[j].nb_errors;
      total_warnings += stat_res[i]->res[j].nb_warnings;
    }

    res = ((double) total_ops / (double) total_time) * 1000000.0;

    /* file_size_type of 1 (true) means we are doing stats on file sizes.  Otherwise */
    /* we are doing stats on metadata sizes. */
    if (file_size_type == 1)
    {
      current_size = stat_res[0]->sizes[j];
    }
    else
    {
      current_size = stat_res[0]->metadata_sizes[j];
    }

    hc_test_log(LOG_INFO_LEVEL, NULL, "- nb_op = "LL_FORMAT", total_time (sec) ="LL_FORMAT", ops/sec "
		" (for size %d) = %#8.5g\n", 
	    total_ops, (total_time / 1000000), current_size, res);
    if (total_errors != 0 || total_warnings != 0) {
      hc_test_log(LOG_INFO_LEVEL, NULL, "- nb_errors = "LL_FORMAT", nb_warnings = "LL_FORMAT"\n",
		  total_errors, total_warnings);
      if (total_errors && !failearly)
	exit(1);
    }

  }
  hc_test_log(LOG_INFO_LEVEL, NULL, "\n");
}

static void
decode_size_input(char* sizes, int64_t** size_array, int* len) {
  int SEP = ',';
  char* prev = sizes;
  char* cur;
  int i;
  int nb_elements = 0;
  int val;


  while ((cur = strchr(prev, SEP)) != NULL) {
    nb_elements++;
    prev = cur + 1;
  }
  nb_elements++;
  CALLOC(*size_array, int64_t*, sizeof(int64_t), nb_elements);

  prev = sizes;
  *len = nb_elements;
  nb_elements = 0;
  while ((cur = strchr(prev, SEP)) != NULL) {
    *cur = '\0';
    val = atoi(prev);
    assert(val >= 0);
    (*size_array)[nb_elements++] = val;
    prev = cur + 1;
  }
  val = atoi(prev);
  assert(val >= 0);
  (*size_array)[nb_elements] = val;
}


int main(int argc, char* argv[])
{
  int nb_threads = 1;
  char op_str[OP_LENGTH];
  int64_t *file_sizes = NULL;
  int64_t *metadata_sizes = NULL;
  int nb_sizes = 0;
  int md_sizes = 0;
  int max_size = 0;
  int c;
  int file_size_type = 0;
  char * test;
  struct timespec init_seed;

  (void) clock_gettime(CLOCK_REALTIME, &init_seed);
  srandom(init_seed.tv_nsec);

  memset(op_str, 0, OP_LENGTH);

  while ((c = getopt(argc, argv, "D:d:n:N:t:o:v:p:s:S:e:f:l:h")) != EOF) {
    switch(c) {
      case 'D':
        delete_nth = atoi(optarg);
        break;
      case 'h':
	usage();
	exit(0);
      case 'S':
        decode_size_input(optarg, &metadata_sizes, &md_sizes);
        if (md_sizes > 1 || metadata_sizes[0] < MIN_SIZE_STR_MD ||
            metadata_sizes[0] > MAX_SIZE_STR_MD_CLUSTER) {
          hc_test_log(LOG_ERROR_LEVEL, NULL,
            "Invalid metadata size value specified for -S option.\n");
	  usage();
	  exit(1);            
        }
        break;
      case 's':
	decode_size_input(optarg, &file_sizes, &nb_sizes);
	break;
      case 'N':
 	hc_test_log(LOG_INFO_LEVEL, NULL, "Setting namespace to %s\n", optarg);	
        if (strlen(optarg) > 0) {
            CALLOC(name_space, char *, strlen(optarg) + 1, sizeof(char));
            strcpy(name_space, optarg);
            hc_test_log(LOG_INFO_LEVEL, NULL, "Setting namepsapce to %s\n", name_space);	
        }
        break;
      case 'n':
	nb_threads = atoi(optarg);
	break;
      case 'd':
	debug_flags = atoi(optarg);
	break;
      case 'f':
	if (strcmp(optarg, "true") == 0) {      
	  failearly = 1;
	} else {
	  failearly = atoi(optarg);
	}
	break;
      case 't':
	duration = atoi(optarg);
	break;
      case 'v':
 	hc_test_log(LOG_INFO_LEVEL, NULL, "Setting dataVip to %s\n", optarg);	
	STRDUP(dataVip, optarg);
	break;
      case 'p':
	port = atoi(optarg);
	break;
      case 'e':
	if (strcmp(optarg, "true") == 0) {      
	  emulator = 1;
	} else {
	  emulator = atoi(optarg);
	}
	break;
      case 'l':
	STRDUP(tmpDir,optarg);
	break;
      case 'o':
	if (strcmp(optarg, "store") == 0) {
	  op = LOAD_STORE;
	} else if (strcmp(optarg, "retrieve") == 0) {
	  op = LOAD_RETRIEVE;
	} else if(strcmp(optarg, "query") == 0) {
	  op = LOAD_QUERY;
	} else if(strcmp(optarg, "queryplus") == 0) {
	  op = LOAD_QUERYPLUS;
	} else if (strcmp(optarg, "delete") == 0) {
	  op = LOAD_DELETE;
	} else if (strcmp(optarg, "addmd") == 0) {
	  op = LOAD_ADD_MD;
        } else if (strcmp(optarg, "mix") == 0) {
          op = LOAD_MIX;
        } else if (strcmp(optarg, "mix2") == 0) {
          op = LOAD_MIX2;
	} else {
	  //fprintf(stderr, "operation %s not known\n",op);
          hc_test_log(LOG_ERROR_LEVEL, NULL,
            "operation %s not known\n",optarg);
	  usage();
	  exit(1);
	}
	break;
      default:
	usage();
	exit(1);
    }
    
    // Force a single metadata size if none provided.
    if (md_sizes == 0) {
        if (emulator == 1) {
            decode_size_input(STRING_MAX_SIZE_STR_MD_EMUL, &metadata_sizes,
                    &md_sizes);
        } else {
            decode_size_input(STRING_MAX_SIZE_STR_MD_CLUSTER, &metadata_sizes,
                    &md_sizes);
        }
    }
    
  }

  get_opstr(op, op_str);


  hc_set_global_parameter(HCOPT_DEBUG_FUNCTION, hc_debug_printf);


  if (debug_flags) {
    hc_set_global_parameter(HCOPT_DEBUG_FLAGS, debug_flags);
  }

  if (dataVip == NULL || 
      (file_sizes == NULL && (op == LOAD_STORE || op == LOAD_ADD_MD ||
        op == LOAD_MIX))) {
    hc_test_log(LOG_ERROR_LEVEL,NULL, 
		"must specify file sizes for store, mix or add_md test\n");
    usage();
    exit(1);
  }

  switch (op) {
  case LOAD_STORE:
  case LOAD_QUERY:
  case LOAD_QUERYPLUS:
  case LOAD_ADD_MD:
    hc_test_log(LOG_INFO_LEVEL, NULL, "start load test: %d threads, %d seconds, "
        "op = %s, name space = %s\n", nb_threads, duration, op_str, name_space);
    break;
  case LOAD_RETRIEVE:
  case LOAD_DELETE:
    hc_test_log(LOG_INFO_LEVEL, NULL, "start load test: %d threads, op = %s\n",
            nb_threads, op_str);
    break;
  }

  CALLOC(stat_res , stat_op_t**, nb_threads, sizeof(stat_op_t*));
  CALLOC(seed_res, long *, nb_threads, sizeof(long));

  hc_init(malloc, free, realloc);

  start_test(nb_threads, op, nb_sizes, file_sizes, md_sizes, metadata_sizes);
  switch (op) {
  case LOAD_MIX:
  case LOAD_MIX2:
    max_size = (stat_res[0]->nb_sizes > stat_res[0]->md_sizes) ? stat_res[0]->nb_sizes : stat_res[0]->md_sizes;
    file_size_type = (stat_res[0]->nb_sizes > stat_res[0]->md_sizes) ? 1 : 0; 
    break;
  case LOAD_STORE:
  case LOAD_RETRIEVE:
  case LOAD_DELETE:
    max_size = stat_res[0]->nb_sizes;
    file_size_type = 1;
    break; 
  case LOAD_QUERY:
  case LOAD_QUERYPLUS:
  case LOAD_ADD_MD:
    file_size_type = 0;
    max_size = stat_res[0]->md_sizes;
    break;
  }

  display_results(nb_threads, max_size, file_size_type);
  for (c = 0; c < nb_threads; c++) {
    free_stat_op(stat_res[c]);
  }
  free(stat_res);
  free(seed_res);
  free(file_sizes);

  hc_cleanup();
  return 0;
}

