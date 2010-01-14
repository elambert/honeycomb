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



#ifndef __HCCOMMON__
#define __HCCOMMON__

#include <time.h>
#include "hc.h"
#include "hctestutil.h"

#define QB_KEY_ID "QB.id"
#define QB_KEY_TEST "QB.testproc"
#define QB_KEY_PARAMS "QB.parameters"
#define QB_KEY_RUN "QB.run"
#define QB_KEY_START_TIME "QB.start_time"
#define QB_KEY_END_TIME "QB.end_time"
#define QB_KEY_STATUS "QB.status"
#define QB_KEY_BUGLIST "QB.buglist"
#define QB_KEY_TAGLIST "QB.taglist"
#define QB_KEY_BUILD "QB.build"
#define QB_KEY_SUBMITTER "QB.submitter"
#define QB_KEY_LOG_URL "QB.logs_url"
#define QB_KEY_NOTES "QB.notes"
#define QB_RESULT_DELIMITER " : "
#define QB_POST_RESULTS_BIN "qb_cli.sh"
#define QB_COMMAND_LENGTH (strlen(QB_POST_RESULTS_BIN) + strlen(tmp_file_name) + 10)
#define MSG_BUFFER_SIZE 4096
#define ARG_MODE 0
#define TAG_MODE 1
#define TAG_CAPI  "capi"
#define TAG_SESSION_SUITE  "capi_session"
#define TAG_NBSTORE_DATA_SUITE  "capinb_storedata"
#define TAG_NBSTORE_METADATA_SUITE  "capinb_storemd"
#define TAG_EZSTORE_DATA_SUITE  "capiez_storedata"
#define TAG_EZSTORE_METADATA_SUITE  "capiez_storemd"
#define TAG_NBRETRIEVE_DATA_SUITE  "capinb_getdata"
#define TAG_NBRETRIEVE_METADATA_SUITE  "capinb_getmd"
#define TAG_EZRETRIEVE_DATA_SUITE  "capiez_getdata"
#define TAG_EZRETRIEVE_METADATA_SUITE  "capiez_getmd"
#define TAG_DELETE_SUITE "capi_del"
#define TAG_NBQUERY_SUITE "capinb_qry"
#define TAG_EZQUERY_SUITE "capiez_qry"
#define TAG_HIGHPERF_SUITE "capi_hiperf"
#define TAG_JNIBRIDGE_SUITE "capi_jnibridge"
#define TAG_MEMORY_SUITE "capi_mem"
#define TAG_LOADPERF_SUITE "capi_lp"
#define TAG_CROSSPLATFORM_SUITE "capi_cp"

#define DEFAULT_VIP_PORT "8080"

#define CL_ARG_LOG_LEVEL "-L"
#define CL_ARG_LOG_LEVEL_LONG "--log-level"
#define CL_ARG_HC_DEBUG_LEVEL "-d"
#define CL_ARG_HC_DEBUG_LEVEL_LONG "--hc-debug-level"
#define CL_ARG_HELP "-h"
#define CL_ARG_HELP_LONG "--help"
#define CL_ARG_CONTEXT "-x"
#define CL_ARG_CONTEXT_LONG "--ctx"
#define CL_ARG_QB_RUN_ID "-qbRunID"
#define BUFFER_TYPE_NULL "null_buffer"
#define HANDLE_TYPE_NULL "null_handle"
#define HANDLE_TYPE_STORE_DATA "store_data_handle"
#define HANDLE_TYPE_STORE_METADATA "store_md_handle"
#define HANDLE_TYPE_STORE_BOTH "store_both_handle"
#define VIP_TYPE_USER "user_vip"
#define VIP_TYPE_EMPTY "empty_vip"
#define VIP_TYPE_NULL "null_vip"
#define VIP_TYPE_IP "ip_vip"
#define VIP_PORT_TYPE_USER "user_vip_port"
#define VIP_PORT_TYPE_INVALID "invalid_vip_port"
#define OID_TYPE_NULL "null_oid"
#define OID_TYPE_VALID "valid_oid"
#define OID_TYPE_INVALID "invalid_oid"
#define FO_ZERO "zero-window-zero-chunk"
#define FO_NEGATIVE "neg-window-neg-chunk"
#define FO_DEFAULT "default-window-default-chunk"
#define EMPTY_STRING ""
#define LOOP_BACK "127.0.0.1"
#define CONTEXT_HASH_SIZE 1024
#define CONTEXT_TOKEN_SEP ":"
#define CONTEXT_NV_SEP "="
#define CONTEXT_NAME_IS_SET "SET"
#define CONTEXT_KEY_DATA_VIP "vip"
#define CONTEXT_KEY_DATA_VIP_PORT "vip_port"
#define CONTEXT_KEY_CELL_IDS "cells"
#define CONTEXT_KEY_SEED "rseed"
#define CONTEXT_KEY_EMULATOR "emulator"
#define CONTEXT_KEY_MAX_FILE_SIZE "max_file"
#define	CONTEXT_KEY_FAILEARLY "failearly"
#define HC_RANGE_ALL_FILE "range_all"
#define HC_RANGE_FIRST_BYTE "range_first"
#define HC_RANGE_LAST_BYTE "range_last"
#define HC_RANGE_ENTIRE_BUFFER "range_buffer"
#define HC_RANGE_BUFFER_MINUS_ONE "range_buffer_minus"
#define HC_RANGE_BUFFER_PLUS_ONE "range_buffer_plus"
#define HC_RANGE_NO "range_no"
#define NUMBER_OF_ABSOLUTE_FILE_SIZE_TYPES 8
#define NUMBER_OF_BUFFER_BASED_FILE_SIZE_TYPES 4
#define DEFAULT_BASE_PORT 8080

typedef enum hc_test_log_level_ {
    LOG_DEBUG_LEVEL,
    LOG_INFO_LEVEL,
    LOG_WARN_LEVEL,
    LOG_ERROR_LEVEL,
    LOG_QUIET_LEVEL, 
} hc_test_log_level;

typedef enum hc_test_result_ {
    TEST_PASS,
    TEST_FAIL,
    TEST_ERROR,
    TEST_SKIP
} hc_test_result_t;

typedef struct qb_row_ {
    char *qb_id;
    char *qb_test;
    char *qb_params;
    char *qb_run;
    char *qb_start_time;
    char *qb_end_time;
    char *qb_status;
    char *qb_buglist;
    char *qb_taglist;
    char *qb_build;
    char *qb_submitter;
    char *qb_log_url;
    char *qb_notes;
} qb_row_t;



typedef struct hctest_env_ {
    int rseed; 						//int used to seed the psuedo random random function
    int emulator;				//TRUE if target is the emulator
    int failearly;				//TRUE if should stop test as soon as one failure is seen
    long testcases_found;				//number of testcases found 
    long testcases_passed;  			//number of testcases executed and passed
    long testcases_failed;    		//number of testcases executed and failed
    long testcases_errored;   		//number of testcases executed and errored
    long testcases_skipped;   		//number of testcases found but not executed
    time_t start_time;				//time the test run started
    time_t stop_time;					//time the test run stopped
    char *data_vip;					//host name of data_vip 
    int data_vip_port;				//port number upon which data_vip listens
    // XXX adding cell info here caused core dump w/ no other change
    // and rm *.o in bin/build_*
    hc_hashlist_t *context_hash;		//a hashlist containing the context args passed in on the command line
    hc_hashlist_t *tests_to_run;		//the lists of test to run as specified on the command line
    char *test_run_id;				//the id for this test run, does not appear like this is being populated				
    int testcases_specified;			//"boolean" set to true if used specified one or more tests on the cl
    int help_asked;					//did user ask for help on the command line (currently not used)
    hc_test_log_level current_log_level; //log level 
	hc_llist_t *failed_tests_list;	//a pointer to a linked-list of test_cases executed and failed
	hc_llist_t *error_tests_list; 	//a pointer to a linked-list of test_cases executed and errored
	hc_llist_t *test_case_list;		//a pointer to a linked-list of test_cases "loaded" by the harness
    int *cell_ids;                              // cell ids for multicell
    int n_cells;                                // number of cell ids
} hctest_env_t;

typedef struct test_case_ {
    char *name;
    char *params;
    hc_test_result_t result; 
    char note[MSG_BUFFER_SIZE];
    int (*exec_test)(struct test_case_ *);
    time_t start_time;
    time_t end_time;
    char **argv;
    int argc;
    int excluded;
    hc_llist_t *tag_list;
    hctest_env_t *test_env;
} test_case_t;

hctest_env_t *create_test_env();
int execute_test(test_case_t *, hctest_env_t *);
void hc_test_log(int, hctest_env_t *,char *, ...);
void postResult(test_case_t *, hctest_env_t *);
qb_row_t create_qb_row();
void add_test_to_list(test_case_t *, hctest_env_t *,int);
void free_test_case(test_case_t *);
test_case_t *create_test_case (char *, int , char **, int (*exec_fn) (test_case_t *),hctest_env_t *);
test_case_t *create_and_tag_test_case (char *, int , char **, int (*exec_fn) (test_case_t *), char ** , int, hctest_env_t * );
int parse_context(char *, hctest_env_t *);
void resolve_buffer_type(char *, char **, hc_long_t *);
void resolve_handle_type(char *, void **);
int resolve_vip_port(char *vip_port_type,hctest_env_t *);
void resolve_vip(char *, char **,hctest_env_t *);
void resolve_fail_over_values(char *, hc_long_t *, hc_long_t *);
void resolve_oid_type(char *, hc_oid **);
char * create_test_scenario_name (test_case_t *);
void free_test_env (hctest_env_t *);
void print_test_scenarios (hc_llist_t *);
hc_long_t get_filesize_type_max(char *);
int generate_buffer_based_file_size_list (char ***);
int generate_absolute_file_size_list(char ***, hc_long_t);
void resolve_range_type (char *, hc_random_file_t *, hc_long_t, hc_long_t *, hc_long_t *);
void free_qb_row(qb_row_t *);
void hc_debug_printf(const char *fmt,...);
#endif
