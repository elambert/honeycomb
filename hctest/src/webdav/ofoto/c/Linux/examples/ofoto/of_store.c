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
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <time.h>


#include "ofoto.h"

/* Honeycomb header files */
#include "../../include/hc.h"
#include "../../include/hcclient.h"

/* Common header files */
#include "../common/HoneycombMetadata.h"

#define FILENAME_5K   "/tmp/ofoto5k"
#define FILENAME_15K  "/tmp/ofoto15k"
#define FILENAME_50K  "/tmp/ofoto50k"
#define FILENAME_900K "/tmp/ofoto900k"

static char *md_names[] = {
    "ofoto.dir1", "ofoto.dir2", "ofoto.dir3", 
    "ofoto.dir4", "ofoto.dir5", "ofoto.dir6", 
    "ofoto.fname"
};

static int verbose = 0;
static char *hc_host;
static int port = 8080;
static hc_session_t *sessionp;
static hcerr_t hc_ret;
static time_t start, end;
static int count = 0;

static void
usage()
{
    fprintf(stderr, "usage: of_store <hc_host> <start_dir> <end_dir> <file_basename> <files_per_dir> [verbose|verbose2]\n");
    fprintf(stderr, "\t(hc_host can be host:port - default is 8080)\n");
    fprintf(stderr, "\t0 <= start_dir <= end_dir <= 255\n");
    exit(1);
}

static void 
err(char * str)
{
    fprintf(stderr, "ERROR: %s  %d\n", str, hc_ret);
    exit(1);
}

/***********************************************************
	FILES
 ***********************************************************/

static int fd5k = -1;
static int fd15k = -1;
static int fd50k = -1;
static int fd900k = -1;

static int 
do_open(char *fname)
{
    int fd = open(fname, O_RDONLY);
    if (fd == -1) {
        perror(fname);
        fprintf(stderr, "ERROR\n");
        exit(1);
    }
    return fd;
}

static void 
init_files()
{
    of_create_file(FILENAME_5K, 5);
    of_create_file(FILENAME_15K, 15);
    of_create_file(FILENAME_50K, 50);
    of_create_file(FILENAME_900K, 900);

    fd5k = do_open(FILENAME_5K);
    fd15k = do_open(FILENAME_15K);
    fd50k = do_open(FILENAME_50K);
    fd900k = do_open(FILENAME_900K);
}

static int
get_fd(int size_code)
{
    int fd;
    off_t pos;

    switch (size_code) {
      case FILE_5K:
        fd = fd5k; break;
      case FILE_15K: 
        fd = fd15k; break;
      case FILE_50K:
        fd = fd50k; break;
      case FILE_900K:
        fd = fd900k; break;
      default:
        err("size_code");
    }
    /* position to read whole file */
    pos = lseek(fd, 0, SEEK_SET);
    if (pos != 0) {
        perror("lseek()");
        fprintf(stderr, "ERROR\n");
        exit(1);
    }
    return fd;
}

/* callback for hc */
/*
HONEYCOMB_EXTERN typedef hcerr_t (*read_from_data_source) (void *stream, char *buff, long n, long *bytes_read);
*/
static long
read_bytes(void *fd, char *buf, long n)
{
    int bytes_read = read((int) fd, buf, n);
    if (verbose > 1)
        printf("read_bytes n=%ld read=%d\n", n, bytes_read);
    return bytes_read;
}

/**********************************************************/

static void
init_hc()
{
    /*
     *  start session
     */
    if (hc_init(malloc, free, realloc) != HCERR_OK)
        err("hc_init()");
    hc_ret = hc_session_create_ez(hc_host, port, &sessionp);
    if (hc_ret != HCERR_OK)
        err("hc_session_create_ez()");
}

static void
printpath(char **md_vals)
{
    /* debug print */
    char store_name[1024];
    sprintf(store_name, "%s/%s/%s/%s/%s/%s/%s", 
                        md_vals[0], md_vals[1], md_vals[2],
                        md_vals[3], md_vals[4], md_vals[5], md_vals[6]);
    printf("%s\n", store_name);
}

/* callback for md generator */
static void
store_file(char **md_vals, int size_code)
{
    if (hc_host == NULL) {
        printpath(md_vals);
    } else {
        int fd = get_fd(size_code);
        hc_nvr_t *nvrp;
        hc_system_record_t system_record;

        if (verbose)
            printpath(md_vals);
        hc_ret = hc_nvr_create_from_string_arrays(sessionp, &nvrp,
                               md_names, md_vals, 7);
        if (hc_ret != HCERR_OK)
            err("hc_nvr_create_from_string_arrays()");

        if (hc_store_both_ez(sessionp, &read_bytes, (void *) fd, nvrp,
                             &system_record) != HCERR_OK)
            err("hc_store_both_ez()");
        if (verbose)
            printf("  %s\n", system_record.oid);

        if (hc_nvr_free(nvrp) != HCERR_OK)
            err("hc_nvr_free()");

        if (verbose > 1)
            printf("stored\n");
        count++;
    }
}

int
main(int argc, char *argv[])
{
    int start_dir, end_dir;
    char *cp, *basename;
    int files_per_dir;

    /*
     *  parse args
     */
    if (argc != 6  &&  argc != 7)
        usage();

    hc_host = argv[1];
    cp = strtok(hc_host, ":");
    cp = strtok(NULL, ":");
    if (cp != NULL)
        port = atoi(cp);
    start_dir = atoi(argv[2]);
    if (start_dir < 0  ||  start_dir > MAX_DIR_DENSITY-1)
        usage();
    end_dir = atoi(argv[3]);
    if (end_dir < start_dir  ||  end_dir > MAX_DIR_DENSITY-1)
        usage();
    basename = argv[4];
    files_per_dir = atoi(argv[5]);
    if (files_per_dir < 1)
        usage();
    if (argc == 7) {
        if (!strcmp(argv[6], "verbose"))
            verbose = 1;
        else if (!strcmp(argv[6], "verbose2"))
            verbose = 2;
        else
            usage();
    }

    /*
     *  init for test or real mode
     */
    if (!strcmp(hc_host, "print")) {
        hc_host = NULL;
    } else {
        init_hc();
        init_files();
    }

    /*
     *  launch stores
     */
    time(&start);
    of_gen_files(start_dir, end_dir, basename, files_per_dir, &store_file);
    time(&end);
    printf("stored %d in %ld seconds\n", count, end-start);

    /*
     *  clean up
     */
    if (hc_host != NULL)
        hc_session_free(sessionp);

    return 0;
}

