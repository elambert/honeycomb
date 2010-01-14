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



#ifndef _UTIL_H
#define _UTIL_H

#define	DEBUG

#include <stdlib.h>
#include <stdio.h>
#include <sys/types.h>

#if  !defined(_MSC_VER)
#include <strings.h>
#else  /* _MSC_VER */
#include <windows.h>
#include <string.h>
#define uint16_t unsigned __int16 
#define uint32_t unsigned __int32
#define uint64_t unsigned __int64
#define int64_t __int64
#define int32_t __int32

#define clock_gettime(dummy,tspec) my_gettime(tspec)
extern int my_gettime(struct timespec *tspec);
#define random()  (((long)rand()) * ((long)rand()))
#define srandom(n) (srand((unsigned int)n))
#endif /* _MSC_VER */


/* size for the string representing the operation */
#define OP_LENGTH 64

#define MAX_SIZE_STR_MD_CLUSTER 512
#define STRING_MAX_SIZE_STR_MD_CLUSTER "512"
#define MAX_SIZE_STR_MD_EMUL    64
#define STRING_MAX_SIZE_STR_MD_EMUL    "64"
#define MIN_SIZE_STR_MD         4

#define MALLOC(R, T, S) \
    do { \
        R = (T) malloc(S); \
        if (R == NULL) { \
	  hc_test_log(LOG_ERROR_LEVEL, NULL, "can't allocate memory, abort test\n"); \
            exit (1); \
        } \
    } while (0)

#define CALLOC(R, T, N, S) \
    do { \
        R = (T) calloc(N, S); \
        if (R == NULL) { \
	  hc_test_log(LOG_ERROR_LEVEL, NULL, "can't allocate memory, abort test\n"); \
            exit (1); \
        } \
    } while (0)

#define REALLOC(R, T, S) \
    do { \
        R = (T) realloc(R, S); \
        if (R == NULL) { \
	  hc_test_log(LOG_ERROR_LEVEL, NULL, "can't allocate memory, abort test\n"); \
            exit (1); \
        } \
    } while (0)

#define STRDUP(R, S) \
     do { \
        R = strdup(S); \
        if (R == NULL) { \
	  hc_test_log(LOG_ERROR_LEVEL, NULL, "can't allocate memory, abort test\n"); \
            exit (1); \
        } \
    } while (0)



typedef enum operation_ {
  LOAD_UNDEF =  0,
  LOAD_STORE,
  LOAD_RETRIEVE,
  LOAD_QUERY,
  LOAD_QUERYPLUS,
  LOAD_ADD_MD,
  LOAD_DELETE,
  LOAD_MIX,
  LOAD_MIX2
} operation_t;

// number of test types run for "mix" option
const static int NUM_MIX_TEST_TYPES = 5;

// These are the underlying individual test types
typedef enum mix_test_types {
  MIX_TEST_STORE = 0,
  MIX_TEST_RETRIEVE,
  MIX_TEST_QUERY,
  MIX_TEST_QUERYPLUS,
  MIX_TEST_ADD_MD
} mix_test_types_t;

// Mix test mix options
typedef enum mix_operations_opts {
    MIX_1 = 1
} mix_operations_opts_t;

typedef struct cumul_op_ {
  int nb_op;
  int nb_errors;
  int nb_warnings;
  // usec
  uint64_t total_time;
} cumul_op_t;


typedef struct stat_op_ {
  operation_t op;
  uint64_t time_loop;
  int nb_sizes;
  int64_t *sizes;
  int md_sizes;
  int64_t *metadata_sizes;
  cumul_op_t* res;
} stat_op_t;


extern int emulator;
extern char *tmpDir;
extern hc_long_t debug_flags;
extern int failearly;


extern void start_perf_mix(stat_op_t* stat, char* test_vip,
        int port, char *name_space);
extern void start_perf_mix2(stat_op_t* stat, char* test_vip, int port, 
                            int delete_nth);
extern void start_perf_store( stat_op_t* stat, char* test_vip, int port,
        char* name_space);
extern void start_perf_retrieve( stat_op_t* stat, char* test_vip, int port);
extern void start_perf_query(stat_op_t* stat, char* test_vip, int port);
extern void start_perf_queryplus(stat_op_t* stat, char* test_vip, int port);
extern void start_perf_delete(stat_op_t* stat, char* test_vip, int port);
extern void start_perf_add_md(stat_op_t* stat, char* test_vip,
        int port, char *name_space);
extern void get_opstr(operation_t op, char* res);


#endif // _UTIL_H
