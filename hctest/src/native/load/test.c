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
#include "hctestutil.h"
#include "hctestcommon.h"
#include "util.h"

static const char SEP = '&';
static const int DIGEST_LEN = 20; // see sha1.h
static const int INT_LEN = 10; // string length to encode an int
static int OUTPUT_STR_LEN = -1;

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

static char *store_output_file = "store.out";
static char *query_input_file = "query.in";

/* The following are shared between threads */
FILE *store_out_fd = NULL;           // open store_output for "w"
FILE *retrieve_in_fd = NULL;        // open store_output for "r"
FILE *query_in_fd = NULL;          // open query_input for "r"
FILE *addmd_out_fd = NULL;          // open query_input for "w"
FILE *addmd_in_fd = NULL;         // open store_output for "r"

char *store_output = NULL;
char *query_input = NULL;
// Pointer to an array that contains which tests to run
// Use this to weight each test to influence the percentage
// of times it is executed
int* mix_test_list = NULL;
int mix_total_num = 0;
#define DEFAULT_MIX_QUERY_LIMIT 1
static long mix_query_limit = DEFAULT_MIX_QUERY_LIMIT;
#define DEFAULT_MIX_RETRIEVE_LIMIT 1
static long mix_retrieve_limit = DEFAULT_MIX_RETRIEVE_LIMIT;
/*
 * Max number of bytes allowed for passing parameter
 * values to queries. Use this value to limit the
 * size of the metadata created and stored so can
 * always perform a query using all the values. Actual value should be 8000
 * but setting it lower to give us a little margin for error.
 */
static const int MAX_QUERY_PARAM_BYTES = 7800; 

/*
 * Constants to limit number of metadata fields stored
 * and queried
 */
void perf_store(stat_op_t* stat, hc_session_t *session, char *name_space);
void perf_retrieve(stat_op_t* stat, hc_session_t *session);
void perf_query(stat_op_t* stat, hc_session_t *session);
void perf_queryplus(stat_op_t* stat, hc_session_t *session);
void perf_add_md(stat_op_t* stat, hc_session_t *session, char *name_space);
void perf_delete(stat_op_t* stat, hc_session_t *session);

// For printing info after an error. Prints and frees current nvr
hcerr_t print_and_free_nvr(char *prefix, hc_nvr_t *nvr);
int check_valid_oid (hc_oid oid);

//
// Init the session. Each thread calls that method once for a
// particular test.
//
static void init_session(char* test_vip, int port, hc_session_t** sessionp)
{
  hcerr_t hcerr;

  hc_test_log(LOG_DEBUG_LEVEL,NULL, "init_session %s:%d\n", test_vip,port);
  hcerr = hc_session_create_ez(test_vip, port, sessionp);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot create the session, abort...\n");
    exit(1);
  }  
  (void) pthread_mutex_lock(&lock);
  if (OUTPUT_STR_LEN == -1) {
    OUTPUT_STR_LEN = sizeof(hc_oid) + 2 * DIGEST_LEN + 1 + INT_LEN + 1 + 5;
  }
  if (store_output == NULL) {
    MALLOC(store_output, char *, strlen(tmpDir)+strlen(store_output_file)+2);
    sprintf(store_output,"%s/%s",tmpDir,store_output_file);
  }     
  if (query_input == NULL) {
    MALLOC(query_input, char *, strlen(tmpDir)+strlen(query_input_file)+2);
    sprintf(query_input,"%s/%s",tmpDir,query_input_file);
  }

  (void) pthread_mutex_unlock(&lock);
}

/*
 * Initializes the mix of tests run. 
 *
 * Currently only one mix_operations_opts_t value: MIX_1
 */
static void init_mix_tests(mix_operations_opts_t mix_option)
{
  int i;
  // Execute store operation 6x more often
  int store_mult = 6;
  // Excecute add md operation 4x more often
  int addmd_mult = 4;
  int num = 0;
  
  // Right now only one mix option so just ignore mix_option
  mix_query_limit = DEFAULT_MIX_QUERY_LIMIT;
  
  /*
   * This is a hack instead of using a weighted random number generator.
   * Setup the list of tests to run. Want to run store operation the most.
   * Then run add metadata slightly less than store operation. The queries
   * and retrieves should be run less frequently.
   */
  // Run store 6x more often; run and add md 4 times more often than other tests
  mix_total_num = NUM_MIX_TEST_TYPES + store_mult + addmd_mult;
  CALLOC(mix_test_list, int*, sizeof(int), mix_total_num);
  for (i = 0; i < NUM_MIX_TEST_TYPES; i++) {
      mix_test_list[i] = i;
  }
  num = NUM_MIX_TEST_TYPES;
  for (i = num; i < num + store_mult; i++) {
      mix_test_list[i] = MIX_TEST_STORE;
  }
  num = NUM_MIX_TEST_TYPES + store_mult;
  for (i = num; i < num + addmd_mult; i++) {
      mix_test_list[i] = MIX_TEST_ADD_MD;
  }
}

static int
is_stopped_loop(struct timespec* init, long time_loop)
{
  struct timespec cur;
  // Value of -1 means run forever
  if (time_loop == -1) {
      return 0;
  }
  
  (void) clock_gettime(CLOCK_REALTIME, &cur);
  if (cur.tv_sec - time_loop < init->tv_sec) {
    return 0;
  } else {
    return 1;
  }
}


static void
exit_maybe() {
  if (failearly > 1) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failearly: Terminating test early to catch the error\n");
    exit(1);
  }
}

static void
update_cumul_op(cumul_op_t* cumul, operation_t op, int nres,
		int nerrors, int nwarnings,
		struct timespec* init_perf, struct timespec* end_perf)
{
  uint64_t res = 0;
  char opstr[OP_LENGTH];
  memset(opstr, 0, OP_LENGTH);

//return;

  res = (end_perf->tv_sec - init_perf->tv_sec) * 1000000;
  res += (end_perf->tv_nsec - init_perf->tv_nsec) / 1000;
  cumul->total_time += res;
  cumul->nb_op += nres;
  cumul->nb_errors += nerrors;
  cumul->nb_warnings += nwarnings;

  if(debug_flags) {
    get_opstr(op, opstr);
    hc_test_log(LOG_DEBUG_LEVEL, NULL, 
		"- time for %s = "LL_FORMAT" us, cumul = 0x%lx, cumul_time = "LL_FORMAT", "
		" nres = %d, nb_errors = %d, nb_warnings = %d, nb_op = %d\n",
		opstr, res, (long)cumul, cumul->total_time,
		nres, cumul->nb_errors, cumul->nb_warnings, cumul->nb_op);
  }
}

static void
read_header_file(FILE* fd, int* nb_sizes, int64_t** sizes)
{
  int i;
  char discard;
  fscanf(fd, "%5d", nb_sizes);
  CALLOC(*sizes, int64_t*, sizeof(int64_t), *nb_sizes);
  for (i = 0; i < *nb_sizes; i++) {
    fscanf(fd, "&"LL_FORMAT, &(*sizes)[i]);
  }

  fread(&discard, 1,1,fd);
}

static void
write_header_file(FILE* fd, int* nb_sizes, int64_t** sizes)
{
  int i;

  fprintf(fd, "%5d", *nb_sizes);
  for (i = 0; i < *nb_sizes; i++) {
    fprintf(fd, "&"LL_FORMAT, (*sizes)[i]);
  }

  fprintf(fd,"\n");
}


static void
open_data_file(char* filename, char* mode, int* nb_sizes,
	       int64_t** sizes, int isqueryinput,
               FILE **out_fd)
{
  // not realy brilliant...
  // first thread fills in the info and others duplicate it
  static int st_nb_sizes = 0;
  static int64_t* st_sizes =  NULL;
  static int st_md_sizes = 0;
  static int64_t* st_metadata_sizes = NULL;

  int my_nb_sizes = 0;
  int my_md_sizes = 0;
  int64_t* my_sizes =  NULL;
  int64_t* my_metadata_sizes = NULL;
  int i;
  int nb_files;
  int time_len=12;
  char *savefile;
  time_t realseconds;
  struct tm *realtime;
  struct tm my_realtime;

  (void) pthread_mutex_lock(&lock);  
  if ((*out_fd) == NULL) {
    if (debug_flags) {
      /* Save old file before creating new */
      if (mode[0] == 'w') {
        time(&realseconds);
        realtime = localtime_r(&realseconds,&my_realtime);
        MALLOC(savefile, char *, strlen(tmpDir)+time_len+strlen(filename)+2);
        sprintf(savefile,"%s.%02d.%02d.%02d", filename,
          realtime->tm_hour, realtime->tm_min, realtime->tm_sec);
        rename(filename, savefile);
        free(savefile);
      }
    }
    *out_fd = fopen(filename, mode);
    if (*out_fd == NULL) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot open file %s, abort...\n", filename);
      exit(1);
    }
    if (mode[0] == 'w') {
      write_header_file(*out_fd, nb_sizes, sizes);
    } else if (mode[0] == 'r') {
      if (isqueryinput == 1)
        read_header_file(*out_fd, &my_md_sizes, &my_metadata_sizes);
      else
        read_header_file(*out_fd, &my_nb_sizes, &my_sizes);
      if (st_sizes == NULL) {
	st_sizes = my_sizes;
	st_nb_sizes = my_nb_sizes;
      } else {
	free(my_sizes);
      }
      if (st_metadata_sizes == NULL) {
        st_metadata_sizes = my_metadata_sizes;
        st_md_sizes = my_md_sizes;
      } else {
        free(my_metadata_sizes);
      }
    } else {
      assert(mode[0] == 'r' || mode[0] == 'w');
    }
  }
  (void) pthread_mutex_unlock(&lock);

  if (nb_sizes != NULL && sizes != NULL && mode[0] == 'r') {
    if (isqueryinput == 1) {
      *nb_sizes = st_md_sizes;
      CALLOC(*sizes, int64_t*, sizeof(int64_t), st_md_sizes);    
      for (i = 0; i < st_md_sizes; i++) {
        (*sizes)[i] = st_metadata_sizes[i];
      }
    }
    else {
      *nb_sizes = st_nb_sizes;
      CALLOC(*sizes, int64_t*, sizeof(int64_t), st_nb_sizes);
      for (i = 0; i < st_nb_sizes; i++) {
        (*sizes)[i] = st_sizes[i];
      }
    }
  }
}

//
// returns 0 if read was succesfull, -1 if end of file.
// - position the pointer digest in the buffer
// - return the size of the file in fsize
//
static int
get_oid_from_file(FILE* fd, char* tmp_buf, hc_oid oid,
                  char** digest, hc_long_t* fsize)
{
  size_t nb_read;
  (void) pthread_mutex_lock(&lock);
  nb_read = fread(tmp_buf, OUTPUT_STR_LEN, 1, fd);
  if (nb_read != 1) {
    if (feof(fd) != 0) { 
      (void) pthread_mutex_unlock(&lock);
      // Clear eof. May have just gotten ahead of the stores
      clearerr(fd);
      // return to caller that we hit EOF
      return -1;
    } else {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "fread(stream %d) failed... abort...\n", fileno(fd));
      exit (1);
    }
  }
  if (debug_flags)
    hc_test_log(LOG_DEBUG_LEVEL, NULL,"Read %.*s\n", OUTPUT_STR_LEN, tmp_buf);

  (void) pthread_mutex_unlock(&lock);
  if (tmp_buf[sizeof(hc_oid) - 1] != SEP ||
      tmp_buf[sizeof(hc_oid) + 2 * DIGEST_LEN] != SEP ||
      !check_valid_oid(tmp_buf)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, 
		"get_oid_from_file: wrong format for oid/digest %.*s, abort...\n",
		OUTPUT_STR_LEN, tmp_buf);
    exit(1);
  }
  tmp_buf[sizeof(hc_oid) - 1] = '\0';
  tmp_buf[sizeof(hc_oid) + 2 * DIGEST_LEN] = '\0';
  tmp_buf[OUTPUT_STR_LEN - 1] = '\0';
  (void) strcpy(oid, tmp_buf);
  if (fsize != NULL) {
    sscanf(tmp_buf + sizeof(hc_oid) + 2 * DIGEST_LEN + 1, "%"LL_MODIFIER"u", fsize);
  }
  if (digest != NULL) {
    *digest = tmp_buf + sizeof(hc_oid);
    if (debug_flags)
      hc_test_log(LOG_DEBUG_LEVEL, NULL, 
        "oid = %.*s, digest = %s, size = "LL_FORMAT"\n", 
		  OID_HEX_CHRSTR_LENGTH-1, oid, *digest, *fsize);
  }
  return 0;
}

static char*
get_hex_digest(unsigned char digest[])
{
  int j;
  char *res;
  MALLOC(res, char*, (2 * DIGEST_LEN + 1));
  for (j = 0; j < DIGEST_LEN; j++) {
    sprintf(res + j * 2, "%02x", digest[j]);
  }
  return res;
}

static void
update_query_file(FILE* fd, char* query, int nb_md, int64_t size)
{
  int length;
  char buffer[10 * 4096];

  length = strlen(query);
  if (length + 40 > sizeof(buffer)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL,
        "update_query_file: buffer too small for query len %d %s\n", 
        length, "update in test.c and recompile");
    exit(1);
  }
  sprintf(buffer, "%10d%.*s&%10d&%10"LL_MODIFIER"u\n",
	  length,length,query,
	  nb_md, size);

  if (debug_flags)
    hc_test_log(LOG_DEBUG_LEVEL, NULL, "storing query='%s'\n", buffer);
  fprintf(fd,"%s",buffer);
  (void) fflush(fd);
  
}

static char*
get_query_from_file(FILE** fd, int* res, int64_t* size)
{
  int length;
  char discard;
  int ret;
  char* query = NULL;
  FILE* curfd = *fd;

  (void) pthread_mutex_lock(&lock);
  if ((curfd == NULL) || (*fd == NULL)) {
    (void) pthread_mutex_unlock(&lock);
    return NULL;
  }
  
  ret = fscanf(curfd, "%10d&", &length);
  if (ret == EOF) {
     fclose(curfd);
     *fd = NULL;
     (void) pthread_mutex_unlock(&lock);
     return NULL;
  }
  CALLOC(query, char*, (length + 1), 1);
  (void) fread(query, (length + 1), 1, curfd);
  query[length] = '\0';	// overwrite the & separator
  (void) fscanf(curfd, "%10d&", res);
  (void) fscanf(curfd, LL_FORMAT, size);
  (void) fread(&discard, 1, 1, curfd);

  if (debug_flags)
    hc_test_log(LOG_DEBUG_LEVEL, NULL, 
     "read query (length=%d)='%s', res=%d, size="LL_FORMAT" eol=%s\n",
       length, query, *res, *size, discard=='\n' ? "OK" : "BAD");
  (void) pthread_mutex_unlock(&lock);
  return query;
}

 

static void
update_store_file(FILE* fd, hc_oid* oid, unsigned char digest[], 
            hc_long_t file_size, char* store_out_str)
{
  char* hex_digest = NULL;
  char* ptr = store_out_str + OUTPUT_STR_LEN;

  memset((void*) store_out_str, 0, OUTPUT_STR_LEN);
  memmove(store_out_str, oid, sizeof(hc_oid));
  store_out_str[sizeof(hc_oid)-1] = SEP;
  hex_digest = get_hex_digest(digest);
  memmove(store_out_str + sizeof(hc_oid),hex_digest, 2 * DIGEST_LEN);
  store_out_str[sizeof(hc_oid) + 2 * DIGEST_LEN] = SEP;  
  sprintf(store_out_str + sizeof(hc_oid) + 2 * DIGEST_LEN + 1, "%10"LL_MODIFIER"u",
          file_size);
  store_out_str[OUTPUT_STR_LEN -1] = '\n';
  if (debug_flags)
    hc_test_log(LOG_DEBUG_LEVEL, NULL, 
      "Storing: %.*s\n", OUTPUT_STR_LEN, store_out_str);
  free(hex_digest);
  (void) fwrite(store_out_str, OUTPUT_STR_LEN, 1, fd);
  (void) fflush(fd);
}

static int
get_size_res_index(stat_op_t* stat, hc_long_t fsize, int is_metadata)
{
  int i;

  if (is_metadata == 1) {
    for (i = 0; i < stat->md_sizes; i++) {
      if (fsize == stat->metadata_sizes[i]) {
        return i;
      }
    }
  }
  else {
    for (i = 0; i < stat->nb_sizes; i++) {
      if (fsize == stat->sizes[i]) {
        return i;
      }
    }
  }
  hc_test_log(LOG_ERROR_LEVEL, NULL, "unknown size of file "LL_FORMAT"\n",fsize);
  exit (1);
}


void
get_opstr(operation_t op, char* opstr)
{
  switch(op) {
  case LOAD_MIX:
    (void) strcpy(opstr, "mix");
    break;
  case LOAD_STORE:
    (void) strcpy(opstr, "store");
    break;
  case LOAD_RETRIEVE:
    (void) strcpy(opstr, "retrieve");
    break;
  case LOAD_QUERY:
    (void) strcpy(opstr, "query");
    break;
  case LOAD_QUERYPLUS:
    (void) strcpy(opstr, "queryplus");
    break;
  case LOAD_ADD_MD:
    (void) strcpy(opstr, "addmd");
    break;
  case LOAD_DELETE:
    (void) strcpy(opstr, "delete");
    break;
  default:
    (void) strcpy(opstr, "unknown");
    break;
  }
}

//
//
//  LOAD TESTS. 
//
//

void start_perf_mix(stat_op_t* stat, char* test_vip, int port, char *name_space)
{ 
  hc_session_t *session = NULL;
  hcerr_t hcerr;
  struct timespec init;
  stat_op_t tempStat;
  uint64_t time_loop = stat->time_loop;
 
  cumul_op_t* cumul = NULL;
  int max_sizes = (stat->nb_sizes > stat->md_sizes) ? stat->nb_sizes : stat->md_sizes;

  if (max_sizes != 0) {
    CALLOC(cumul, cumul_op_t*, max_sizes, sizeof(cumul_op_t));
  }
  stat->res = cumul;
  init_mix_tests(MIX_1);
  
  init_session(test_vip, port, &session);

  tempStat.op = stat->op;
  tempStat.time_loop = 0;
  tempStat.nb_sizes = stat->nb_sizes;
  tempStat.sizes = stat->sizes;
  tempStat.md_sizes = stat->md_sizes;
  tempStat.metadata_sizes = stat->metadata_sizes;
  tempStat.res = stat->res;

  if (time_loop != -1) {
      hc_test_log(LOG_INFO_LEVEL, NULL,
        "Performance Mix Test - minutes to run: "LL_FORMAT"\n", time_loop/60);
  } else {
      hc_test_log(LOG_INFO_LEVEL, NULL,
        "Performance Mix Test - minutes to run: unlimited\n");
  }
  
  hc_test_log(LOG_INFO_LEVEL, NULL, "Calling Store...\n");
  perf_store(&tempStat, session, name_space);

  hc_test_log(LOG_INFO_LEVEL, NULL, "Calling Store...\n");
  perf_store(&tempStat, session, name_space);

  hc_test_log(LOG_INFO_LEVEL, NULL, "Calling add metadata...\n");
  perf_add_md(&tempStat, session, name_space);

  (void) clock_gettime(CLOCK_REALTIME, &init);
  do {
    int perfRand = rand() % mix_total_num;
    perfRand = mix_test_list[perfRand];

    tempStat.op = stat->op;
    tempStat.time_loop = 0;
    tempStat.nb_sizes = stat->nb_sizes;
    tempStat.sizes = stat->sizes;
    tempStat.md_sizes = stat->md_sizes;
    tempStat.metadata_sizes = stat->metadata_sizes;
    tempStat.res = stat->res;

    switch(perfRand)
    {
      case MIX_TEST_STORE:
        hc_test_log(LOG_DEBUG_LEVEL, NULL, "Calling Store...\n");
        perf_store(&tempStat, session, name_space);
        break;
      case MIX_TEST_RETRIEVE:
        hc_test_log(LOG_DEBUG_LEVEL, NULL, "Calling Retrieve...\n");
        perf_retrieve(&tempStat, session);
        break;
      case MIX_TEST_QUERY:
        hc_test_log(LOG_DEBUG_LEVEL, NULL, "Calling query...\n");
        perf_query(&tempStat, session);
        break;
      case MIX_TEST_QUERYPLUS:
        hc_test_log(LOG_DEBUG_LEVEL, NULL,
            "Calling query(plus)...\n");
        perf_queryplus(&tempStat, session);
        break;
      case MIX_TEST_ADD_MD:
        hc_test_log(LOG_DEBUG_LEVEL, NULL, "Calling add metadata...\n");
        perf_add_md(&tempStat, session, name_space); 
        break;
    }
  } while (!is_stopped_loop(&init, time_loop));

  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session %d\n", hcerr);
    exit(1);
  }
}

void start_perf_mix2(stat_op_t *stat, char *test_vip, int port, int delete_nth)
{
  hc_session_t *session = NULL;
  hcerr_t hcerr;
  struct timespec init;
  uint64_t time_loop;
  int i, j, k;
  hc_pstmt_t *pstmt = NULL;

  if (stat->nb_sizes == 0) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "mix2: must specify sizes (-s)\n");
    exit(1);
  }

  time_loop = stat->time_loop;

  hc_test_log(LOG_INFO_LEVEL, NULL, "minutes to run: "LL_FORMAT"\n", time_loop/60);

  init_session(test_vip, port, &session);

  (void) clock_gettime(CLOCK_REALTIME, &init);

  for (i=0; !is_stopped_loop(&init, time_loop); i++) {
    hc_long_t size;
    double double_val = (double) random();
    hc_long_t long_val = random();
    hc_nvr_t *nvr = NULL;
    char query_string[1024]; // HACK/testware-sufficient
    hc_random_file_t *stored_r_file = NULL;
    hc_random_file_t *retrieved_r_file = create_random_file(0);
    hc_system_record_t sys_record;
    hc_query_result_set_t *qrs = NULL;
    int rs_finished = 0;
    hc_oid retrieved_oid;
    hc_oid tmp_retrieved_oid;
    int j;

    size = stat->sizes[i % stat->nb_sizes];

    //printf("== start %d  "LL_FORMAT"\n", i, size);
    //
    //  make metadata - uses single table in qa schema to avoid
    //  relalg failure
    //
    hcerr = hc_nvr_create(session, 3, &nvr);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot create nvr, abort...\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }
    hcerr = hc_nvr_add_string(nvr, "perf_types.type_string", "hcload.mix2");
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_nvr_add_string"
                  "perf_types.type_string...abort (needs perf schema)\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }
    hcerr = hc_nvr_add_long(nvr, "perf_types.type_long", long_val);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_nvr_add_long"
                  "perf_types.type_long...abort (needs perf schema)\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }
    hcerr = hc_nvr_add_double(nvr, "perf_types.type_double", double_val);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_nvr_add_double"
                  "perf_types.type_double...abort (needs perf schema)\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }

    //
    //  make query string for metadata
    //
/*
    sprintf(query_string, "system.test.type_string='hcload.mix2' AND "
        "system.test.type_long="LL_FORMAT" AND "
        "system.test.type_double=%.17G",
        long_val, double_val);
*/
    sprintf(query_string, "perf_types.type_string=? AND perf_types.type_long=? AND perf_types.type_double=?");
    hcerr = hc_pstmt_create(session, query_string, &pstmt);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_pstmt_create...abort\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }
    hcerr = hc_pstmt_set_string(pstmt, 1, "hcload.mix2");
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_pstmt_set_string...abort\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }
    hcerr = hc_pstmt_set_long(pstmt, 2, long_val);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_pstmt_set_long...abort\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }
    hcerr = hc_pstmt_set_double(pstmt, 3, double_val);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_pstmt_set_double...abort\n");
      log_session_error_info(session, hcerr);
      exit(1);
    }

    //
    //  make data
    //
    stored_r_file = create_random_file(size);

    //
    //  store
    //
    if (debug_flags) {
      hc_test_log(LOG_DEBUG_LEVEL, NULL, 
        "start store for size = "LL_FORMAT"\n", size);
    }
    hcerr = hc_store_both_ez(session, read_from_random_data_generator,
                               (void *) stored_r_file, nvr, &sys_record);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, 
        "failed to store file, size = "LL_FORMAT", iter = %d\n", size, i);
      log_session_error_info(session,hcerr);
      exit(1);
    }
    if (compare_rfile_to_sys_rec(stored_r_file ,&sys_record) != TRUE) {
      hc_test_log(LOG_ERROR_LEVEL, NULL,
        "error in storing file, compare_rfile_to_sys_rec\n");
      exit(1);
    }
    //printf("sto %s\n", sys_record.oid);

    //
    //  free metadata
    //
    hcerr = hc_nvr_free(nvr);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_nvr_free iter = %d\n", i);
      log_session_error_info(session, hcerr);
      exit(1);
    }

    //
    //  query 
    //
/*
    hcerr = hc_query_ez(session, query_string, NULL, 0, 100, &qrs);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_query_ez iter = %d\n", i);
      log_session_error_info(session, hcerr);
      exit(1);
    }
*/
    hcerr = hc_pstmt_query_ez(pstmt, NULL, 0, 100, &qrs);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_pstmt_query_ez iter = %d\n", i);
      log_session_error_info(session, hcerr);
      exit(1);
    }

    //
    //  get query result
    //
    memset(retrieved_oid,'\0',sizeof(hc_oid));
    memset(tmp_retrieved_oid, '\0', sizeof(hc_oid));
    for (j=0; !rs_finished; j++) {
      hcerr = hc_qrs_next_ez(qrs, &tmp_retrieved_oid, NULL, &rs_finished);
      if (hcerr != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_qrs_next_ez iter = %d.%d\n", 
                                           i, j);
        log_session_error_info(session, hcerr);
        exit(1);
      }
      if (!memcmp(sys_record.oid, tmp_retrieved_oid, sizeof(hc_oid))) {
        memcpy(retrieved_oid, tmp_retrieved_oid, sizeof(hc_oid));
      }
      //if (!rs_finished) printf("que %s\n", retrieved_oid);
    }

    //
    //  check query result
    //
    j = j - 1;
    if (j < 1) {
      hc_test_log(LOG_ERROR_LEVEL, NULL,
        "no results found, iter = %d\n", i);
      exit(1);
    } else if (j > 1) {
        hc_test_log(LOG_WARN_LEVEL, NULL,
            "WARNING: query returned too many results, iter = %d "
            "results: %d , expected 1, "
            "long value: "LL_FORMAT" double value: %.17G\n",
            i, j, long_val, double_val);
    } 
    
    if (memcmp(sys_record.oid, retrieved_oid, sizeof(hc_oid))) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "oid mismatch, iter = %d\n", i);
      hc_test_log(LOG_ERROR_LEVEL, NULL, "stored: %s\n", sys_record.oid);
      hc_test_log(LOG_ERROR_LEVEL, NULL, "rtrved: %s\n", retrieved_oid);
      exit(1);
    }

    //
    //  free query resultset 
    //
    hcerr = hc_qrs_free(qrs);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_qrs_free iter = %d\n", i);
      log_session_error_info(session, hcerr);
      exit(1);
    }

    //
    //  free prepared statement
    //
    hcerr = hc_pstmt_free(pstmt);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_pstmt_free iter = %d\n", i);
      log_session_error_info(session, hcerr);
      exit(1);
    }

    //
    //  retrieve file
    //
    init_random_file(retrieved_r_file);
    hcerr = hc_retrieve_ez(session, retrieve_data_to_rfile, retrieved_r_file,
                           &sys_record.oid);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_retrieve_ez, iter = %d\n", i);
      log_session_error_info(session, hcerr);
      exit(1);
    }
    //printf("rtv ok\n");

    //
    //  check file
    //
    sha1_finish(retrieved_r_file->sha_context, retrieved_r_file->digest);
    if (compare_rfiles(stored_r_file, retrieved_r_file) != TRUE ) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, 
                   "Stored file does not match received file.\n");
      exit(1);
    }

    //
    //  delete every Nth file
    //
    if (delete_nth  &&  i % delete_nth) {
      if (debug_flags)
        hc_test_log(LOG_DEBUG_LEVEL, NULL, "delete");
      hcerr = hc_delete_ez(session, &sys_record.oid);
      if (hcerr != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "hc_delete_ez, iter = %d\n", i);
        log_session_error_info(session, hcerr);
        exit(1);
      }
      //printf("del ok\n");
    }

  }

  //
  //  clean up session
  //
  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session %d\n", hcerr);
    exit(1);
  }

  //
  //  exit happily
  //
  hc_test_log(LOG_INFO_LEVEL, NULL, "Ending mix2\n");
  exit(0);
}


void start_perf_store(stat_op_t* stat, char* test_vip, int port,
        char *name_space)
{
  hc_session_t *session = NULL;
  hcerr_t hcerr;

  init_session(test_vip, port, &session);

  perf_store(stat, session, name_space);

  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session %d\n", hcerr)
;
    exit(1);
  }
}

void perf_store(stat_op_t* stat, hc_session_t *session, char *name_space)
{
  struct timespec init;
  struct timespec init_perf;
  struct timespec end_perf;
  int iteration;
  hc_system_record_t sys_record;
  hc_random_file_t *r_file = NULL;
  hc_nvr_t* nvr = NULL;
  cumul_op_t* cumul = NULL;
  char* store_out_str = NULL;
  hcerr_t hcerr;
  int i, num_md_fields;
  hc_schema_t *schema = NULL;
  char** md_name = NULL;
  hc_schema_entry_t* md_list = NULL;

  
  uint64_t time_loop = stat->time_loop;

  open_data_file(store_output, "wb", &(stat->nb_sizes),
		 &(stat->sizes), 0, 
                 &store_out_fd);

  MALLOC(store_out_str, char*, OUTPUT_STR_LEN);
  
  hcerr = hc_session_get_schema(session, &schema);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot retrieve schema, abort...\n");
    log_session_error_info(session, hcerr);
    exit(1);
  }

  // STEPH store_both_ez NEEDS some md...
  hcerr = hc_nvr_create(session, 1, &nvr);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot create nvr, abort...\n");
    log_session_error_info(session,hcerr);
    exit(1);
  }

  // Get metadata fields (in random order)
  md_name = get_random_entry_from_schema(schema, HC_STRING_TYPE, 1, name_space);

  hc_test_log(LOG_DEBUG_LEVEL, NULL, "adding string %s=default to nvr\n",
    md_name[0]);
  hcerr = hc_nvr_add_string(nvr, md_name[0], "default");
  
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL,
        "cannot add string %s=default to nvr, abort...\n", md_name[0]);
    log_session_error_info(session,hcerr);
    exit(1);
  }

  for (i = 0; i < stat->nb_sizes; i++) {
    (void) clock_gettime(CLOCK_REALTIME, &init);
    cumul = &stat->res[i];
    iteration = 0;
    if (debug_flags) 
      hc_test_log(LOG_DEBUG_LEVEL, NULL, 
        "start store for size = "LL_FORMAT", cumul = 0x%x\n",
            stat->sizes[i], cumul);
    do {
      r_file = create_random_file(stat->sizes[i]);
      (void) clock_gettime(CLOCK_REALTIME, &init_perf);

      hcerr = hc_store_both_ez(session, read_from_random_data_generator,
                               (void *) r_file,  nvr, &sys_record);
      if (hcerr != HCERR_OK) {
	hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to store file, size = "LL_FORMAT", iter = %d, "
                " abort...\n", stat->sizes[i], iteration);
	log_session_error_info(session,hcerr);
        exit(1);
      }
      (void) clock_gettime(CLOCK_REALTIME, &end_perf);
      update_cumul_op(cumul, LOAD_STORE, 1, 0, 0, &init_perf, &end_perf);
      if (!check_valid_oid(sys_record.oid)) {
	hc_test_log(LOG_ERROR_LEVEL, NULL, "store: received invalid OID %s with ret==HCERR_OK!\n",
		  sys_record.oid);
	exit(1);
      }

      update_store_file(store_out_fd, &sys_record.oid, r_file->digest,
                  r_file->file_size, store_out_str);
      iteration++;
      free_r_file(r_file);
    } while (!is_stopped_loop(&init, time_loop));
  }

  free(store_out_str);
  if (md_name != NULL)
    free(*md_name);
  hcerr = hc_nvr_free(nvr);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the name value record \n");
    log_session_error_info(session,hcerr);
    exit(1);
  }
}

void start_perf_retrieve(stat_op_t* stat, char* test_vip, int port)
{
  hc_session_t *session = NULL;
  hcerr_t hcerr;
  cumul_op_t* cumul = NULL;
  int nb_sizes;
  int64_t* sizes;

  init_session(test_vip, port, &session);

  open_data_file(store_output, "rb", &nb_sizes, &sizes, 0, &retrieve_in_fd);

  if (nb_sizes != 0) {
    CALLOC(cumul, cumul_op_t*, nb_sizes, sizeof(cumul_op_t));
  }

  stat->nb_sizes = nb_sizes;
  stat->sizes = sizes;
  stat->res = cumul;

  perf_retrieve(stat, session);

  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session\n");
    exit(1);
  }
}

void perf_retrieve(stat_op_t* stat, hc_session_t *session)
{
  struct timespec init;
  struct timespec init_perf;
  struct timespec end_perf;
  int iteration;
  hc_random_file_t *r_file = NULL;
  cumul_op_t* cumul = NULL;
  char* store_out_str = NULL;
  hc_oid oid;
  char* digest = NULL;
  char* hex_digest = NULL;
  hcerr_t hcerr;
  hc_long_t fsize;
  int nb_sizes;
  int64_t* sizes;
  uint64_t time_loop;
  int i;
  long num_retrieves = 0;
  int done = FALSE;

  MALLOC(store_out_str, char*, OUTPUT_STR_LEN);

  // For mix test the time_loop vaue will be 0
  time_loop = stat->time_loop;
  /*
   * We do retrieves over and over until =>
   * - hit EOF of store output file
   * - exceed the time limit
   * - hit the max number of retrieves for a single pass when running
   *   from the mix test
   */
  (void) clock_gettime(CLOCK_REALTIME, &init);
  iteration = 0;
  open_data_file(store_output, "rb", &nb_sizes, &sizes, 0, &retrieve_in_fd);
  while (get_oid_from_file(retrieve_in_fd, store_out_str, oid,
                           &digest, &fsize) != -1  &&
         !done) {
    cumul = &stat->res[get_size_res_index(stat, fsize, 0)];
    r_file = create_random_file(0);
    init_random_file(r_file);

    (void) clock_gettime(CLOCK_REALTIME, &init_perf);
    hcerr = hc_retrieve_ez(session, retrieve_data_to_rfile,
                           (void*) r_file, &oid);
    (void) clock_gettime(CLOCK_REALTIME, &end_perf);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to retrieve file, iter = %d, oid=%.*s"
                 " abort...\n", iteration,OID_HEX_CHRSTR_LENGTH-1,oid);
      log_session_error_info(session,hcerr);
      exit(1);
    }
    sha1_finish(r_file->sha_context,r_file->digest);

    update_cumul_op(cumul, LOAD_RETRIEVE, 1, 0, 0, &init_perf, &end_perf);
    if (fsize != r_file->file_size) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to validate size of retrieved file\n"
		  "    oid=%.*s"
		  " fsize = %"LL_MODIFIER"u, r_file->size = %"LL_MODIFIER"u, abort at iteration %d...\n",
		  OID_HEX_CHRSTR_LENGTH-1,oid,
		  fsize, r_file->file_size, iteration);
      exit(1);
    }

    hex_digest = get_hex_digest(r_file->digest);
    if (strcmp(hex_digest, digest) != 0) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to check integrity of the file, iter = %d, "
              " abort...\n", iteration);
      exit(1);
    }

    free(hex_digest);
    free_r_file(r_file);
    iteration++;
    // Exit this loop if max num retrieves performed for this pass or
    // time expired
    if (stat->op == LOAD_MIX) {
      num_retrieves++;
      if (num_retrieves > mix_retrieve_limit) {
        done = TRUE;
      }
    }
    if (is_stopped_loop(&init, time_loop)) {
        done = TRUE;
    }
  } // end while

  
  free(store_out_str);
}


void start_perf_query(stat_op_t* stat, char* test_vip, int port)
{
  hc_session_t *session = NULL;
  hcerr_t hcerr;
  int md_sizes;
  int64_t* metadata_sizes;
  cumul_op_t* cumul = NULL;

  init_session(test_vip, port, &session);

  open_data_file(query_input, "rb", &md_sizes, &metadata_sizes, 1, &query_in_fd);
  if (md_sizes != 0) {
    CALLOC(cumul, cumul_op_t*, md_sizes, sizeof(cumul_op_t));
  }
  stat->md_sizes = md_sizes;
  stat->metadata_sizes = metadata_sizes;
  stat->res = cumul;

  perf_query(stat, session);

  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session\n");
    exit(1);
  }
}

/*
 *  Runs Query test. If run from MIX tests then one or two queries executed.
 *  However if run as standalone then repeats queries until EOF of input
 *  file hit or time runs out.
*/
void perf_query(stat_op_t* stat, hc_session_t *session)
{
  struct timespec init;
  struct timespec init_perf;
  struct timespec end_perf;
  int iteration;
  hc_oid oid;
  hcerr_t hcerr;
  int nb_expected_res;
  int nb_res;
  int nb_errors;
  int nb_warnings;
  int64_t cur_size;
  int finished;
  char* query = NULL;
  cumul_op_t* cumul = NULL;
  hc_query_result_set_t *rset = NULL;
  uint64_t time_loop;
  int i;
  int done;
  int warning_printed = FALSE;

  // For mix test the time_loop vaue will be 0
  time_loop = stat->time_loop;
  /*
   * We do the query over and over until we reach the time limit.
   */
  (void) clock_gettime(CLOCK_REALTIME, &init);
  do {

    //get_query_from_file will close the fd when it finishes the file
    //then we reopen it (i.e. first thread does)
    open_data_file(query_input, "rb", NULL, NULL, 1, &query_in_fd);
    done = FALSE;

    while ((query = get_query_from_file(&query_in_fd, &nb_expected_res,
                                        &cur_size)) != NULL &&
            !done) {
      cumul = &stat->res[get_size_res_index(stat, cur_size, 1)];

      hcerr = hc_query_ez(session, query, NULL, 0, 100, &rset);
      nb_res = 0;
      nb_errors = 0;
      nb_warnings = 0;
      warning_printed = FALSE;

      if (hcerr != HCERR_OK) {
          if (treat_as_warning(session, hcerr)) {
              hc_test_log(LOG_WARN_LEVEL, NULL,
                "WARNING: failed to run the query %s, continue...\n", query);
              nb_warnings++;
              warning_printed = TRUE;
          } else {
            hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to run the query %s\n", query);
            log_session_error_info(session,hcerr);
            exit(1);
          }
      }
      (void) clock_gettime(CLOCK_REALTIME, &init_perf);
      if (hcerr == HCERR_OK) {
        do {
            hcerr = hc_qrs_next_ez(rset, &oid, NULL, &finished);
            if (hcerr != HCERR_OK) {
              if (treat_as_warning(session, hcerr)) {
                  hc_test_log(LOG_WARN_LEVEL, NULL,
                    "WARNING: failed to query next result, continue...\n");
                  nb_warnings++;
                  warning_printed = TRUE;
                  break;
              } else {
                hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to query next result\n");
                log_session_error_info(session,hcerr);
                exit(1);
              }
            }
            if (!finished) {
              nb_res++;
            }
          } while (!finished);
      } //end if (hcerr == HCERR_OK)
      
      if (rset != NULL) {
          hcerr = hc_qrs_free(rset);
          rset = NULL;

          if (hcerr != HCERR_OK) {
              if (treat_as_warning(session, hcerr)) {
                  if (warning_printed == FALSE) {
                      hc_test_log(LOG_WARN_LEVEL, NULL,
                        "WARNING: failed on hc_qrs_free query %s after %d results out"
                        " of %d expected, continue...\n",
                        query, nb_res, nb_expected_res);
                      nb_warnings++;
                      warning_printed = TRUE;
                  }
              } else {
                hc_test_log(LOG_ERROR_LEVEL, NULL, "failed on hc_qrs_free of query %s after %d results out of %d expected\n",
                        query, nb_res, nb_expected_res);
                log_session_error_info(session,hcerr);
                nb_errors++;
                exit_maybe();
              }
          } else if (nb_res > nb_expected_res) {
            hc_test_log(LOG_WARN_LEVEL, NULL, 
                    "WARNING: query %s returned too many results: %d res, expected %d, "
                    "continue...\n",
                    query, nb_res, nb_expected_res);
            nb_warnings++;
          } else if (nb_res < nb_expected_res) {
            hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to validate the query %s, "
                        "expected %d, found %d\n",
                        query, nb_expected_res, nb_res);
            nb_errors++;
            exit_maybe();
          }
      } // end if (rset != NULL)

      (void) clock_gettime(CLOCK_REALTIME, &end_perf);
      update_cumul_op(cumul, LOAD_QUERY, nb_res, nb_errors, nb_warnings, &init_perf, &end_perf);

      if (query != NULL) {
          free(query);
          query = NULL;
      }
      
      // Exit this loop after single query for mix test
      if (stat->op == LOAD_MIX) {
          done = TRUE;
      }
        
      // Exit loop time limit has been hit
      if (is_stopped_loop(&init, time_loop)) {
          done = TRUE;
      }
    }   // end while
  } while (!is_stopped_loop(&init, time_loop) && !done);
}

void start_perf_queryplus(stat_op_t* stat, char* test_vip, int port)
{
  hc_session_t *session = NULL;
  hcerr_t hcerr;
  int md_sizes;
  int64_t* metadata_sizes;
  cumul_op_t* cumul = NULL;

  init_session(test_vip, port, &session);

  open_data_file(query_input, "rb", &md_sizes, &metadata_sizes, 1, &query_in_fd);
  if (md_sizes != 0) {
    CALLOC(cumul, cumul_op_t*, md_sizes, sizeof(cumul_op_t));
  }
  stat->md_sizes = md_sizes;
  stat->metadata_sizes = metadata_sizes;
  stat->res = cumul;

  perf_queryplus(stat, session);

  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session\n");
    exit(1);
  }
}

void perf_queryplus(stat_op_t* stat, hc_session_t *session)
{
  struct timespec init;
  struct timespec init_perf;
  struct timespec end_perf;
  int iteration;
  hc_nvr_t *nvr = NULL;
  hcerr_t hcerr;
  int nb_expected_res;
  int nb_res;
  int nb_errors;
  int nb_warnings;
  int64_t cur_size;
  int finished;
  char* query = NULL;
  cumul_op_t* cumul = NULL;
  hc_query_result_set_t *rset = NULL;
  hc_oid retOid;
  uint64_t time_loop;
  int i;
  int n_selects = 1;
  char *selects[] = {"system.object_id"};
  int done;
  int warning_printed = FALSE;

  time_loop = stat->time_loop;

  /*
   * We do the query over and over until we reach the time limit.
   */
  (void) clock_gettime(CLOCK_REALTIME, &init);
  do {

    //get_query_from_file will close the fd when it finishes the file
    //then we reopen it (i.e. first thread does)
    open_data_file(query_input, "rb", NULL, NULL, 1, &query_in_fd);
    done = FALSE;

    while ((query = get_query_from_file(&query_in_fd, &nb_expected_res,
                                        &cur_size)) != NULL &&
                !done) {

      cumul = &stat->res[get_size_res_index(stat, cur_size, 1)];

      nb_res = 0;
      nb_errors = 0;
      nb_warnings = 0;
      warning_printed = FALSE;
      hcerr = hc_query_ez(session, query, selects, n_selects, 100, &rset);
      if (hcerr != HCERR_OK) {
          if (treat_as_warning(session, hcerr)) {
              hc_test_log(LOG_WARN_LEVEL, NULL,
                "WARNING: failed to run the query(plus) %s, continue...\n", query);
              nb_warnings++;
              warning_printed = TRUE;
          } else {
            hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to run the query(plus) %s\n", query);
            log_session_error_info(session,hcerr);
            exit(1);
          }
      }
      (void) clock_gettime(CLOCK_REALTIME, &init_perf);
      if (hcerr == HCERR_OK) {
          do {
            hcerr = hc_qrs_next_ez(rset, &retOid, &nvr, &finished);
            if (hcerr != HCERR_OK) {
              if (treat_as_warning(session, hcerr)) {
                  hc_test_log(LOG_WARN_LEVEL, NULL,
                    "WARNING: failed to query(plus) next result, continue...\n");
                  nb_warnings++;
                  warning_printed = TRUE;
                  break;
              } else {
                  hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to query(plus) next result\n");
                  log_session_error_info(session,hcerr);
                  exit(1);
              }
            }
            if (!finished) {
              if (nvr != NULL) {
                  hc_nvr_free(nvr);
                  nvr = NULL;
              }
              nb_res++;
            }
          } while (!finished);
      } // end if (hcerr == HCERR_OK)
      
      if (nvr != NULL) {
         hc_nvr_free(nvr);
         nvr = NULL;
      }
      if (rset != NULL) {
        hcerr = hc_qrs_free(rset);
        rset = NULL;

        if (hcerr != HCERR_OK) {
          if (treat_as_warning(session, hcerr)) {
             if (warning_printed == FALSE) {
                  hc_test_log(LOG_WARN_LEVEL, NULL,
                      "WARNING: failed on hc_qrs_free query (plus) %s after "
                      "%d results out of %d expected, continue...\n",
                      query, nb_res, nb_expected_res);
                  nb_warnings++;
                  warning_printed = TRUE;
              }
          } else {
            hc_test_log(LOG_ERROR_LEVEL, NULL, "failed on hc_qrs_free of query(plus) %s after %d results out of %d expected\n",
                    query, nb_res, nb_expected_res);
            log_session_error_info(session,hcerr);
            nb_errors++;
            exit_maybe();
          }
        } else if (nb_res > nb_expected_res) {
          hc_test_log(LOG_WARN_LEVEL, NULL, 
                    "WARNING: query(plus) %s returned too many results: %d res, expected %d, "
                    "continue...\n",
                    query, nb_res, nb_expected_res);
          nb_warnings++;
        } else if (nb_res < nb_expected_res) {
          hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to validate the query(plus) %s, "
                    "expected %d, found %d\n",
                    query, nb_expected_res, nb_res);
          nb_errors++;
          exit_maybe();
        }
      } // end if (rset != NULL)
      (void) clock_gettime(CLOCK_REALTIME, &end_perf);
      update_cumul_op(cumul, LOAD_QUERY, nb_res, nb_errors, nb_warnings, &init_perf, &end_perf);

      if (query != NULL) {
        free(query);
        query = NULL;
      }
      // Exit loop after a single query if running a mix test
      if (stat->op == LOAD_MIX) {
          done = TRUE;
      }
        
      // Exit loop time limit has been hit
      if (is_stopped_loop(&init, time_loop)) {
          done = TRUE;
      }
    } // end while
  } while (!is_stopped_loop(&init, time_loop) && !done);
}


void start_perf_add_md(stat_op_t* stat, char* test_vip, int port,
        char *name_space)
{
  hc_session_t *session = NULL;
  hcerr_t hcerr;

  init_session(test_vip, port, &session);

  perf_add_md(stat, session, name_space);

  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session\n");
    exit(1);
  }
}

void perf_add_md(stat_op_t *stat, hc_session_t *session, char *name_space)
{
    const int MAX_MD_PER_OID = 10;
    /* 
     * For each metadata field need at least 10 bytes. That will hold
     * 8 bytes for data value (largest type) and 2 bytes overhead.
     */
    const int MIN_PARAM_BYTES = 10;
    const int DATE_VALUE_BYTES = 4;
    const int TIME_VALUE_BYTES = 4;
    const int TIMESTAMP_VALUE_BYTES = 8;
    const int LONG_VALUE_BYTES = 8;
    const int DOUBLE_VALUE_BYTES = 8;
    const int CHAR_VALUE_BYTES = 1;
    const int STRING_VALUE_BYTES = 2;
    const int BINARY_VALUE_BYTES = 1;

    int MAX_SIZE_STR_MD;
    
    struct timespec init;
    struct timespec init_perf;
    struct timespec end_perf;
    int iteration;
    hc_system_record_t sys_record;
    hc_oid oid;
    hc_nvr_t* nvr = NULL;
    cumul_op_t* cumul = NULL;
    char* store_out_str = NULL;
    char** md_attrs = NULL;
    char** md_vals_str = NULL;
    struct tm *md_vals_date = NULL;
    time_t *md_vals_time = NULL;
    struct timespec *md_vals_timestamp = NULL;
    unsigned char *md_vals_binary = NULL;
    hc_long_t *md_vals_long = NULL;
    hc_double_t *md_vals_double = NULL;
    int nb_md = 1; 
    int size_idx = 0;
    hc_type_t type;
    char* query = NULL;
    char* tmp_query = NULL;
    hcerr_t hcerr;
    int i = 0;
    uint64_t time_loop;
    int get_status;
    int num_md_fields = 0;
    hc_schema_entry_t* md_list = NULL;
    hc_schema_entry_t* md_entry = NULL;

    int index = 0;
    int length = 0;
    hc_schema_t *schema = NULL;
    // Tracks amount of space used  for parameter values
    int param_space_used;

    // Now only a single metadata size is allowed
    assert(stat->md_sizes > 0);
    stat->md_sizes = 1;
    assert(stat->metadata_sizes[0] > 0);

    MAX_SIZE_STR_MD = stat->metadata_sizes[0];
    
    time_loop = stat->time_loop;
    hcerr = HCERR_OK;
    hcerr = hc_session_get_schema(session, &schema);
    if (hcerr != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "cannot retrieve schema, abort...\n");
        log_session_error_info(session, hcerr);
        exit(1);
    }
    
    // Open output files
    open_data_file(store_output, "rb", NULL, NULL, 0, &addmd_in_fd);
    open_data_file(query_input, "wb", &(stat->md_sizes),
        &(stat->metadata_sizes), 1, &addmd_out_fd);
    
    MALLOC(store_out_str, char*, OUTPUT_STR_LEN);
    (void) clock_gettime(CLOCK_REALTIME, &init);
    
    /*
     *  Loop adding metadata until the time expires
     *  or only execute loop once if running a mix test
     */
    do {
        
        /*
         * fetch one oid from the store files.
         */
        hc_test_log(LOG_DEBUG_LEVEL, NULL, "add_md: getting new OID\n");
        get_status = get_oid_from_file(addmd_in_fd, store_out_str, oid, NULL, NULL);
        if (get_status == -1) {
            hc_test_log(LOG_INFO_LEVEL, NULL,
            "add_md: no oids to read, hit end of store file\n");
            break;
        } else if (get_status == 1) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
            "add_md: error attempting to read next oid from store file, abort...\n");
            exit(1);
        }
        
        hc_test_log(LOG_DEBUG_LEVEL, NULL, "add_md: After getting new OID\n");
        
        // Get queryable metadata fields (in random order)
        num_md_fields = get_random_ns_entries_from_schema(schema, name_space,
                            &md_list, MAX_MD_PER_OID, 1);

        if (num_md_fields <= 0) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
                "add_md: no queryable fields found for specified "
                "namespace %s\n", name_space);
            exit(1);
        }
        param_space_used = 0;
        
        if (md_list == NULL) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
                "add_md: Metdata list is null - no queryable fields found"
                "for specified namespace %s\n", name_space);
            exit(1);
        }
        
        hc_test_log(LOG_DEBUG_LEVEL, NULL,
            "add_md: %d queryable metadata fields found for schema %s\n",
            num_md_fields, name_space);
        
        hcerr = hc_nvr_create(session, num_md_fields, &nvr);
        if (hcerr != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to create nvr, abort...\n ");
            log_session_error_info(session, hcerr);
            exit(1);
        }
        
        /*
         * create the name value record.
         */
        md_entry = md_list;
        for (i = 0; i < num_md_fields; i++) {
            /* Make sure metadata generated can be used in a query */
            if (param_space_used + MIN_PARAM_BYTES > MAX_QUERY_PARAM_BYTES) {
                hc_test_log(LOG_WARN_LEVEL, NULL,
                "No room to add more metadata fields, "
                "skipping the rest\n");
                break;
            }
            switch (md_entry->type) {
                
                case HC_STRING_TYPE:
                    
                    type = HC_STRING_TYPE;                   
                    CALLOC(md_vals_str, char**, nb_md, sizeof(char*));
                    length = md_entry->size;
                    
                    // Adjust size
                    if (length > MAX_SIZE_STR_MD) {
                        length = MAX_SIZE_STR_MD;
                    }
                    while ((param_space_used + 2 + (length * 2)) > 
                            MAX_QUERY_PARAM_BYTES && length > 1) {
                        length = length /2;
                    }
                    param_space_used = param_space_used + 2 + (length * 2);

                    md_vals_str[size_idx] = create_random_uniform_string(length);
                    
                    hcerr = hc_nvr_add_string(nvr, md_entry->name,
                        md_vals_str[size_idx]);
                    
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "failed to create nvr from string, abort...\n ");
                        log_session_error_info(session, hcerr);
                        exit(1);
                    }
                    break;
                    
                case HC_CHAR_TYPE:
                    
                    type = HC_CHAR_TYPE;
                    CALLOC(md_vals_str, char**, nb_md, sizeof(char*));
                    length = md_entry->size;
                    // Adjust size
                    if (length > MAX_SIZE_STR_MD) {
                        length = MAX_SIZE_STR_MD;
                    }

                    while ((param_space_used + 2 + length) > 
                            MAX_QUERY_PARAM_BYTES && length > 1) {
                        length = length /2;
                    }
                    param_space_used = param_space_used + 2 + length;
                    
                    // todo - RFE XXX convert to use UTF8
                    md_vals_str[size_idx] = create_random_uniform_string(length);
                    hcerr = hc_nvr_add_string(nvr, md_entry->name,
                                md_vals_str[size_idx]);
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "cannot add char value to nvr, abort...\n");
                        log_session_error_info(session, hcerr);
                        exit(1);
                    }
                    break;
                    
                case HC_LONG_TYPE:
                    
                    /* Long Data Type */
                    type = HC_LONG_TYPE;
                    param_space_used = param_space_used + 2 + LONG_VALUE_BYTES;

                    md_vals_str = init_long_values(nb_md, &md_vals_long);
                    hcerr = hc_nvr_add_long(nvr, md_entry->name,
                            md_vals_long[size_idx]);
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "cannot add long value to nvr, abort...\n");
                        log_session_error_info(session, hcerr);
                        exit(1);
                    }
                    free(md_vals_long);
                    break;
                    
                case HC_DOUBLE_TYPE:
                    
                    /* Double Data Type */
                    type = HC_DOUBLE_TYPE;
                    param_space_used = param_space_used + 2 + DOUBLE_VALUE_BYTES;
                    md_vals_str = init_double_values(nb_md, &md_vals_double);
                    hcerr = hc_nvr_add_double(nvr, md_entry->name,
                            md_vals_double[size_idx]);
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "cannot add double value to nvr, abort...\n");
                        exit(1);
                    }
                    free(md_vals_double);
                    break;
                    
                case HC_DATE_TYPE:
                    
                    /* Date Data Type */
                    type = HC_DATE_TYPE;
                    param_space_used = param_space_used + 2 + DATE_VALUE_BYTES;
                    md_vals_str = init_date_values(nb_md, &md_vals_date);
                    hcerr = hc_nvr_add_date(nvr, md_entry->name,
                            &md_vals_date[size_idx]);
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "cannot add date value to nvr, abort...\n");
                        exit(1);
                    }
                    free(md_vals_date);
                    break;
                    
                case HC_TIME_TYPE:
                    
                    /* Time Data Type */
                    type = HC_TIME_TYPE;
                    param_space_used = param_space_used + 2 + TIME_VALUE_BYTES;
                    md_vals_str = init_time_values(nb_md, &md_vals_time);
                    hcerr = hc_nvr_add_time(nvr, md_entry->name,
                            md_vals_time[size_idx]);
                    param_space_used = param_space_used + 2 + 4;
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "cannot add time value to nvr, abort...\n");
                        exit(1);
                    }
                    free(md_vals_time);
                    break;
                    
                case HC_TIMESTAMP_TYPE:
                    
                    /* Timestamp Data Type */
                    type = HC_TIMESTAMP_TYPE;
                    param_space_used = param_space_used + 2 + 
                            TIMESTAMP_VALUE_BYTES;
                    md_vals_str = init_timestamp_values(nb_md, &md_vals_timestamp);
                    
                    hcerr = hc_nvr_add_timestamp(nvr, md_entry->name,
                            &md_vals_timestamp[size_idx]);
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "cannot add timestamp value to nvr, abort...\n");
                        exit(1);
                    }
                    free(md_vals_timestamp);
                    break;
                    
                case HC_BINARY_TYPE:
                    /* Binary Data Type */
                    type = HC_BINARY_TYPE;
                    length = md_entry->size;
                    // Adjust size
                    if (length > MAX_SIZE_STR_MD) {
                        length = MAX_SIZE_STR_MD;
                    }
                    while ((param_space_used + 2 + length) > 
                            MAX_QUERY_PARAM_BYTES && length > 1) {
                        length = length /2;
                    }
                    param_space_used = param_space_used + 2 + length;

                    md_vals_str = init_binary_values(schema,
                        &md_entry->name, nb_md, &md_vals_binary, length);
                    hcerr = hc_nvr_add_binary(nvr, md_entry->name,
                        md_vals_binary[index], md_vals_binary + 1);
                    if (hcerr != HCERR_OK) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL,
                        "cannot add binary value to nvr, abort...\n");
                        exit(1);
                    }
                    free(md_vals_binary);
                    break;
            }   // end switch (md_entry->type)
            
            // Add current field to query string
            if (i == 0) {
                query = get_query_string(&md_entry->name, md_vals_str, nb_md, type);
            } else {
                tmp_query = get_query_string(&md_entry->name, md_vals_str, nb_md, type);
                REALLOC(query, char*,  strlen(query) + strlen(" AND ") +
                strlen(tmp_query) + 1);
                (void) strcat(query, " AND ");
                (void) strcat(query, tmp_query);
                free(tmp_query);
                tmp_query = NULL;
            }
            free_array(md_vals_str, nb_md);
            md_entry = md_entry->next;
            
        }   //end for (all md fields)
        
        if (debug_flags)
            hc_test_log(LOG_DEBUG_LEVEL, NULL, "add_md: query = %s\n", query);
        
        /* Add metadata */
        cumul = &stat->res[size_idx];
        iteration = 0;
        if (debug_flags)
            hc_test_log(LOG_DEBUG_LEVEL, NULL,
            "start add_md for size = "LL_FORMAT", cumul = 0x%x\n",
            stat->metadata_sizes[size_idx], cumul);
        
        
        (void) clock_gettime(CLOCK_REALTIME, &init_perf);
        hcerr = hc_store_metadata_ez(session, &oid, nvr, &sys_record);
        if (hcerr != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to store metadata, size = "LL_FORMAT", iter = %d, OID=%.*s"
            " abort...\n", stat->metadata_sizes[size_idx], iteration, 
                OID_HEX_CHRSTR_LENGTH-1, oid);
            log_session_error_info(session, hcerr);
            hcerr = print_and_free_nvr("nvr: ", nvr);
            if (hcerr != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL, NULL, "problem printing nvr: %s\n",
                hc_decode_hcerr(hcerr));
            }
            exit(1);
        }
        (void) clock_gettime(CLOCK_REALTIME, &end_perf);
        update_cumul_op(cumul, LOAD_ADD_MD, 1, 0, 0, &init_perf, &end_perf);
        
        iteration++;
        if (debug_flags) {
            if (check_valid_oid(sys_record.oid)) {
                hc_test_log(LOG_DEBUG_LEVEL, NULL,
                "add_md: stored into OID %.*s digest %s\n",
                OID_HEX_CHRSTR_LENGTH-1, sys_record.oid, sys_record.data_digest);
            } else {
                hc_test_log(LOG_ERROR_LEVEL, NULL,
                "add_md: received invalid OID %.*s with ret=HCERR_OK!\n",
                OID_HEX_CHRSTR_LENGTH-1, sys_record.oid);
            }
        }
        
        /* Write query to query output file */
        update_query_file(addmd_out_fd, query, iteration, stat->metadata_sizes[0]);
        if (query != NULL) {
            free(query);
            query = NULL;
        }
        hcerr = hc_nvr_free(nvr);
        if (hcerr != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the name value record \n");
            log_session_error_info(session, hcerr);
            exit(1);
        }
        
        // free memory
        free_metadata_fields_list(md_list);
        md_list = NULL;
        
    } while (!is_stopped_loop(&init, time_loop));
    
    free(store_out_str);
}   // end perf_addmd


void start_perf_delete(stat_op_t* stat, char* test_vip, int port)
{
  hc_session_t *session = NULL;
  hcerr_t hcerr;
  cumul_op_t* cumul = NULL;
  int nb_sizes;
  int64_t* sizes;

  init_session(test_vip, port, &session);

  open_data_file(store_output, "rb", &nb_sizes, &sizes, 0, &retrieve_in_fd);
  if (nb_sizes != 0) {
    CALLOC(cumul, cumul_op_t*, nb_sizes, sizeof(cumul_op_t));
  }
  stat->nb_sizes = nb_sizes;
  stat->sizes = sizes;
  stat->res = cumul;

  perf_delete(stat, session);

  hcerr = hc_session_free(session);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to free the session\n");
    exit(1);
  }
}

void perf_delete(stat_op_t *stat, hc_session_t *session)
{
  struct timespec init_perf;
  struct timespec end_perf;
  int iteration;
  char* store_out_str = NULL;
  cumul_op_t* cumul = NULL;
  hc_oid oid;
  hcerr_t hcerr;
  hc_long_t fsize;

  MALLOC(store_out_str, char*, OUTPUT_STR_LEN);

  iteration = 0;
  while (get_oid_from_file(retrieve_in_fd, store_out_str, oid, NULL, &fsize)
         != -1) {
    cumul = &stat->res[get_size_res_index(stat, fsize, 0)];

    (void) clock_gettime(CLOCK_REALTIME, &init_perf);
    hcerr = hc_delete_ez(session, &oid);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "failed to delete file, iter = %d, "
              " abort...\n", iteration);
      log_session_error_info(session,hcerr);
      exit(1);
    }
    (void) clock_gettime(CLOCK_REALTIME, &end_perf);
    update_cumul_op(cumul, LOAD_DELETE, 1, 0, 0, &init_perf, &end_perf);
    iteration++;
  }
  free(store_out_str);
}

/*
 WARNING: The call to hc_nvr_convert_to_string_arrays frees the
 nvr structure. Do NOT call this unless you are done with
 nvr and want to free it.
 */
hcerr_t print_and_free_nvr(char *prefix, hc_nvr_t *nvr) {
  int i, nitems;
  char **names, **values;
  hcerr_t err;
  
  err = hc_nvr_convert_to_string_arrays(nvr, &names, &values, &nitems);
  if (err != HCERR_OK)
    return err;
  for (i=0; i<nitems; i++)
    hc_test_log(LOG_DEBUG_LEVEL, NULL,
        "%s %s = %s\n", prefix, names[i], values[i]);

  return HCERR_OK;
}
