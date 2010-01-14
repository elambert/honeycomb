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
#include <string.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "hctestcommon.h"
#include "sha1.h"


static int parse_args(int, char **, hctest_env_t *);
static int load_tests(hctest_env_t *);
static void run (hctest_env_t *);
static void summarize (hctest_env_t *);
static void usage();
int hc_framework_test_exec(test_case_t *);
void framework_load_tests(hctest_env_t *);

void framework_load_tests(hctest_env_t *);
void hc_delete_load_tests(hctest_env_t *); 	
void hcez_qry_load_tests(hctest_env_t *);
void hcez_getdata_load_tests(hctest_env_t *); 
void hcez_storedata_load_tests(hctest_env_t *);
void hc_getschema_load_tests(hctest_env_t *);

void hcez_storertrvmdnew_load_tests(hctest_env_t *);
void hcez_storertrvmdutf8_load_tests(hctest_env_t *);
void hcez_storertrvmd_load_tests(hctest_env_t *);
void hcez_storeretrieve_load_tests(hctest_env_t *);
void hcez_sessions_load_tests(hctest_env_t *);
void hcez_queryusage_load_tests(hctest_env_t *);
void hcez_rangeretrieve_load_tests(hctest_env_t *);
void hcez_querylarge_load_tests(hctest_env_t *);
void hcez_querypstmt_load_tests(hctest_env_t *);
void hcez_storertrvmdlatin1_load_tests(hctest_env_t *);

char *progname = NULL;

int main(int argc, char *argv[]) {
	hctest_env_t * hc_current_env;

	// save progname for use in stores
	progname = argv[0];

	//create needed structures
	hc_current_env = create_test_env();
	
	//Initialize Honeycomb environment
	hc_init(malloc, free, realloc);
	hc_set_debug_function(hc_debug_printf);

	// setup
	if (parse_args(argc - 1, ++argv, hc_current_env) != 0) {
		hc_test_log(LOG_ERROR_LEVEL,hc_current_env,"A usage error was encountered.\n");
		usage();
		return 1;
	}

	if (hc_current_env->data_vip == NULL) {
		hc_test_log(LOG_ERROR_LEVEL,hc_current_env,"context must specify at least a vip (host).\n");
		usage();
		return 1;
	}

  	if (hc_current_env->help_asked == TRUE) {
  		usage();
		return 0;
  	}

	// run tests 
	load_tests(hc_current_env);
	run(hc_current_env);
	summarize(hc_current_env);
  	 
	if (hc_current_env->testcases_failed == 0 && hc_current_env->testcases_errored == 0) {
		return 0;
	} else {
		return 1;
	}
}

static int *parse_int_list(char *context_cell_ids, int *n_cells) {
    char *str = strtok(context_cell_ids, ",");
    int *buf = (int *) malloc(sizeof(int));

    buf[0] = atoi(str);
    *n_cells = 1;
    while ((str = strtok(NULL, ",")) != NULL) {
        buf = (int *) realloc(buf, sizeof(int) * (*n_cells + 1));
        buf[*n_cells] = atoi(str);
        (*n_cells)++;
    }
    return buf;
}

static int parse_args(int argc, char ** argv, hctest_env_t *test_env) {
    char * context_data_vip_port = NULL;
    char * context_data_vip = NULL;
    char * context_cell_ids = NULL;
    char * context_emulator = NULL;
    char * context_failearly = NULL;

    int i;

    test_env->n_cells = 0;
    test_env->cell_ids = NULL;

    //got nothing to do
    if (argc == 0) {
		return 0;
    }
    //iter through the args, bail if i have a problem
    for (i = 0; i < argc; i++) {
	char *current_opt = argv[i];

	if ( strcmp(current_opt,CL_ARG_LOG_LEVEL) == 0  || 
	     strcmp(current_opt,CL_ARG_LOG_LEVEL_LONG) == 0) {

	    char *level;
	    if (++i < argc) {
			level = argv[i];
	    } else {
			hc_test_log(LOG_ERROR_LEVEL,test_env,"You must specify a log level <debug|info|warn|error|quiet> with %s\n", current_opt);
			return 1;
	    }

	    if (strcmp(level,"debug") == 0) {
			test_env->current_log_level = LOG_DEBUG_LEVEL;	
	    }
	    else if (strcmp(level,"info") == 0) {
			test_env->current_log_level = LOG_INFO_LEVEL;	
	    }
	    else if (strcmp(level,"warn") == 0) {
			test_env->current_log_level = LOG_WARN_LEVEL;	
	    }
	    else if (strcmp(level,"error") == 0) {
			test_env->current_log_level = LOG_ERROR_LEVEL;	
	    }
	    else if (strcmp(level,"quiet") == 0) {
			test_env->current_log_level = LOG_QUIET_LEVEL;	
	    }
	    else {
			hc_test_log(LOG_ERROR_LEVEL,test_env,"Unrecognized log level %s. Valid levels are <debug|info|warn|error|quiet> \n", level);
			return 1;
	    }
	}
	else if (strcmp(current_opt,CL_ARG_HELP) == 0  || 
	         strcmp(current_opt,CL_ARG_HELP_LONG) == 0 ) {
	    test_env->help_asked = TRUE;
	}
	else if (strcmp(current_opt,CL_ARG_CONTEXT) == 0 ||
	         strcmp(current_opt,CL_ARG_CONTEXT_LONG) == 0 ) {
	    char *ctx_args;
	    if (++i < argc) {
		ctx_args = argv[i];
		hc_test_log(LOG_DEBUG_LEVEL,test_env,"Context arg %s was found on the CLI.\n",ctx_args);
		if(parse_context(ctx_args, test_env) != TRUE) {
		    hc_test_log(LOG_ERROR_LEVEL,test_env,"Can not parse the context args you supplied on the command line. %s\n",ctx_args);
                    return 1;
                }
            } else {
                hc_test_log(LOG_ERROR_LEVEL,test_env,"You must specify a context values with with %s\n", current_opt);
                return 1;
            }
        }
        else if (strncmp(current_opt, "-",1) != 0) {
            hc_test_log(LOG_DEBUG_LEVEL,test_env,"Test %s was specified for running.\n",current_opt);
            hash_put(current_opt, CONTEXT_NAME_IS_SET, test_env->tests_to_run);
            test_env->testcases_specified = TRUE;
        }
        else if (strcmp(current_opt,CL_ARG_QB_RUN_ID) == 0 ) {
            if (++i < argc) {
                test_env->test_run_id=argv[i];
            } else {
                hc_test_log(LOG_ERROR_LEVEL,test_env,"You must specify a run id with %s\n", current_opt);
                return 1;
            }
        }
	else if ( strcmp(current_opt,CL_ARG_HC_DEBUG_LEVEL) == 0  || 
		  strcmp(current_opt,CL_ARG_HC_DEBUG_LEVEL_LONG) == 0) {
		hc_long_t debug = 0;
		if (++i < argc) {
			debug = atol(argv[i]);
		} else {
			hc_test_log(LOG_ERROR_LEVEL,test_env,"You must specify a numeric value with %s\n", current_opt);
			return 1;
		}
		if (debug) {
		  hc_test_log(LOG_DEBUG_LEVEL,test_env,"Setting hc_debug_flags to "LLX_FORMAT,debug);
		  hc_set_debug_flags(debug);
		}
	}
        else {
            hc_test_log(LOG_ERROR_LEVEL, test_env,"Unkown option %s\n", current_opt);
            return 1;
        }
    }

    context_data_vip = hash_get_value(CONTEXT_KEY_DATA_VIP,test_env->context_hash);
    if (context_data_vip != NULL) {
        test_env->data_vip = context_data_vip;
    }
    context_data_vip_port = hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,test_env->context_hash);
    if (context_data_vip_port != NULL) {
        test_env->data_vip_port = atoi(context_data_vip_port);
    }

    context_cell_ids = hash_get_value(CONTEXT_KEY_CELL_IDS, test_env->context_hash);
    if (context_cell_ids != NULL) {
        test_env->cell_ids = parse_int_list(context_cell_ids, 
                                            &test_env->n_cells);
    }

    context_emulator = hash_get_value(CONTEXT_KEY_EMULATOR,test_env->context_hash);
    if (context_emulator != NULL && (strcmp(context_emulator, "true") == 0
				      || strcmp(context_emulator, CONTEXT_NAME_IS_SET) == 0 )) {
	hc_test_log(LOG_INFO_LEVEL, test_env, "Running test on the emulator...");
	test_env->emulator = 1;
    }
    context_failearly = hash_get_value(CONTEXT_KEY_FAILEARLY,test_env->context_hash);
    if (context_failearly != NULL && (strcmp(context_failearly, "true") == 0 
				      || strcmp(context_failearly, CONTEXT_NAME_IS_SET) == 0 )) {
	hc_test_log(LOG_INFO_LEVEL, test_env, "Running test in failearly mode for debugging...");
	test_env->failearly = 1;
    }
     // no problems found 
    return 0;
}


/*
 * This function will populate the linked list of tests to be executed.
 * By convention, all test source files should define a function called 
 * XXXX_load_tests(hctest_env_t *) which is responsible for adding the 
 * test cases "exported" by that source file into the list of test cases 
 * that can be run. To link those tests into the harness, add a call to
 * the XXX_load_tests function in the function below.
 */
static int load_tests(hctest_env_t * hc_current_env) {
	// run tests 
	hc_test_log(LOG_INFO_LEVEL,hc_current_env,"Loading Test Run.\n");

	/* new tests */
        // hcez_compliance_load_tests segfaults on linux
	//hcez_compliance_load_tests (hc_current_env);
        //hcez_storertrvmdlatin1_load_tests(hc_current_env);

	hcez_mcellqry_load_tests(hc_current_env);
        hcez_querypstmt_load_tests(hc_current_env);
	hcez_storertrvmdnew_load_tests(hc_current_env);
	hcez_storertrvmdutf8_load_tests(hc_current_env);
        hcez_storertrvmd_load_tests(hc_current_env);
        hcez_storeretrieve_load_tests(hc_current_env);
        hcez_storertrvmdlatin1_load_tests(hc_current_env);

	/* adapted eagan tests */
	hcez_sessions_load_tests(hc_current_env);
	hcez_queryusage_load_tests(hc_current_env);
	hcez_rangeretrieve_load_tests(hc_current_env);
	hcez_querylarge_load_tests(hc_current_env);

	/* old tests */

        framework_load_tests(hc_current_env);
	hc_delete_load_tests(hc_current_env); 	
	//hcez_getdata_load_tests(hc_current_env); uses hcoa_
	//hcez_storedata_load_tests(hc_current_env); uses hcoa_
	//hc_getschema_load_tests(hc_current_env); No longer relevant
	hcez_qry_load_tests(hc_current_env);

	//--THE FOLLOWING TESTS ARE COMMENTED OUT BECAUSE THEY TEST
	//THE non-blocking API, and have not been updated to the new
	//version of that API.
	/*
    	hcoaSession_load_tests(hc_current_env);
    	hcnb_storedata_load_tests(hc_current_env);
	hcnb_getdata_load_tests(hc_current_env); //range tests are failing	
	hcnb_qry_load_tests(hc_current_env);
	*/
	
	return 0;
}


/*
 * This function drives the execution of the test cases "discovered"
 * by the load_tests function. 
 */
static void run (hctest_env_t *env) {
    hc_llist_t *test_case_list = NULL;
    hc_llist_node_t *curr_testlist_node;

    hc_test_log(LOG_INFO_LEVEL,env,"Starting Test Run.\n");
	env->failed_tests_list = hc_create_linkedlist();
	env->error_tests_list = hc_create_linkedlist();
    if (env->test_case_list == NULL) {
    		hc_test_log(LOG_DEBUG_LEVEL, env, "Test case list is empty.\n");
	    return;
    }
	else { 
		test_case_list=env->test_case_list;
	}
	
    curr_testlist_node = test_case_list->head;
    
    hc_test_log(LOG_DEBUG_LEVEL, env, "About to execute tests.\n");
    while (curr_testlist_node != NULL) {
        hc_test_result_t result =
              execute_test((test_case_t *)curr_testlist_node->entry,env);
	if ((result == TEST_ERROR || result == TEST_FAIL) && env->failearly) {
	    hc_test_log(LOG_INFO_LEVEL,env,"Test Run Finishing Early due to error\n");
	    break;
	}
        curr_testlist_node = curr_testlist_node->next;
    }
    hc_test_log(LOG_INFO_LEVEL,env,"Test Run Finished.\n");
}

static void summarize(hctest_env_t *env) {
	hc_test_log(LOG_INFO_LEVEL,env,"\n");
	hc_test_log(LOG_INFO_LEVEL,env,"LIST OF FAILED TESTS: \n");
	print_test_scenarios(env->failed_tests_list);
	hc_test_log(LOG_INFO_LEVEL,env,"\n");
	hc_test_log(LOG_INFO_LEVEL,env,"LIST OF ERRORED TESTS: \n");
	print_test_scenarios(env->error_tests_list);
	hc_test_log(LOG_INFO_LEVEL,env,"\n");
    hc_test_log(LOG_INFO_LEVEL, env,"TEST RESULTS FOR TEST RUN %s: FOUND (%ld) PASSED (%ld) FAILED (%ld) ERROR (%ld) SKIPPED (%ld)\n",
		env->test_run_id, env->testcases_found, env->testcases_passed, env->testcases_failed, env->testcases_errored, env->testcases_skipped);	
}

static void usage() {
    printf("\nusage:\n\n");
    printf("     %s, %s <name>[=<value>][:<name>[=<value>]]\n", CL_ARG_CONTEXT,
							CL_ARG_CONTEXT_LONG);
    printf("     Add properties to this run context. \n");
    printf("     Example properties \n");
    printf("       vip=<hostname or IP address> \n");
    printf("       vip_port=<data_vip port number> \n");
    printf("       cells=1,2,3 <give cellids if multicell>\n");
    printf("       emulator=true <if running against the emulator> \n");
    printf("       failearly=true <stop as soon as one error encountered> \n");
    printf("       max_file=<max_file_size_spec, e.g. med_file or 10MB> \n");
    printf("\n");

    printf("     %s, %s <debug|info|warn|error|quiet|>\n", CL_ARG_LOG_LEVEL, 
							CL_ARG_LOG_LEVEL_LONG);
    printf("     Set the output log level. Default = \'debug\'.\n");
    printf("\n");

    printf("     %s, %s <flags-value(decimal)>\n", CL_ARG_HC_DEBUG_LEVEL, 
						CL_ARG_HC_DEBUG_LEVEL_LONG);
    printf("     Set the Honeycomb Debug level. Default = 0.\n");
    printf("\n");

    printf("     %s, %s\n", CL_ARG_HELP, CL_ARG_HELP_LONG);
    printf("     Print this message.\n");
    printf("\n");

}


//tags,num_tags,args,fun
void framework_load_tests(hctest_env_t *env) {
				char *frame_tags[] = {"capi"};
				add_test_to_list(
					create_and_tag_test_case
					("framework_test",
					1, 
					frame_tags,
					(int (*)(test_case_t*))hc_framework_test_exec,
					frame_tags,
					1,env),
				env,FALSE);
}


int hc_framework_test_exec(test_case_t *tc) {
	int res, test_res, port, expected_res;
	char * test_vip = NULL;
	hc_system_record_t sys_rec;
	hc_oid * oid = NULL;
	char **argv = NULL;
	hc_random_file_t * retrieved_r_file = NULL;
	hc_session_t *sessionp = NULL;
	hctest_env_t *env = NULL;
	int32_t response_code;
	char * errstr = NULL;
	int errstr_len =4092;
	hc_archive_t *archive = NULL;
	
	env = tc->test_env;
	argv = tc->argv;
	test_vip=env->data_vip;
	port=env->data_vip_port;
	test_res = TEST_PASS;
	
	retrieved_r_file = create_random_file(0);
	init_random_file(retrieved_r_file);

	if (store_file_with_md(test_vip,port,1024,&sys_rec) != TRUE) {
		hc_test_log(LOG_ERROR_LEVEL,env,
				   "Unable to store file needed by delete test to %s, %d\n",test_vip,port);
		test_res = TEST_FAIL;
		goto finished;
	}
	
	oid = &(sys_rec.oid);
	hc_test_log(LOG_INFO_LEVEL, env,
				"harness test successfully stored a file. OID is %s, size is "LL_FORMAT" \n", oid, sys_rec.size);

	if (hc_session_create_ez(test_vip,port,&sessionp) != HCERR_OK) {
		hc_test_log(LOG_INFO_LEVEL, env, "Unable to create session to %s:%d\n", test_vip,port);
		test_res = TEST_FAIL;
		goto finished;
	}
	if (hc_session_get_archive(sessionp, &archive) != HCERR_OK) {
		hc_test_log(LOG_INFO_LEVEL, env, "Unable to create archive to %s:%d\n", test_vip,port);
		test_res = TEST_FAIL;
		goto finished;		
	}
	
	//retrieve
	res = hc_retrieve_ez(sessionp, 
			     retrieve_data_to_rfile, 
			     retrieved_r_file,
			     &(sys_rec.oid));
	if (res != HCERR_OK) {
		hc_test_log(LOG_INFO_LEVEL, env, "Unable to retrieve  OID %s from %s:%d\n",sys_rec.oid, test_vip,port);
		test_res = TEST_FAIL;
		goto finished;			
	} else {
		sha1_finish(retrieved_r_file->sha_context,retrieved_r_file->digest);
		if (( compare_rfile_to_sys_rec(retrieved_r_file, &sys_rec)) != TRUE ) {
			hc_test_log(LOG_ERROR_LEVEL,env,"retrieved file and stored file do not match\n");
			test_res = TEST_FAIL;
			goto finished;
		}
	}
	hc_test_log(LOG_INFO_LEVEL, env,"harness test successfully retrieved a file. OID is %s, size is "LL_FORMAT" \n", 
				sys_rec.oid, retrieved_r_file->file_size);
	//delete
	res = hc_delete_ez(sessionp,(hc_oid*)sys_rec.oid);
	if (res != HCERR_OK) {
                printf("hc_delete_ez FAILED\n");
                log_session_error_info(sessionp, res);
                test_res = TEST_FAIL;
		goto finished;
	}
	
	//try and retrieve it
	if (test_res != TEST_FAIL) {
		res = hc_retrieve_ez(sessionp,
				     retrieve_data_to_rfile,
				     (void *)retrieved_r_file,(hc_oid *)sys_rec.oid);
		if (res == HCERR_OK) {
			sha1_finish(retrieved_r_file->sha_context,retrieved_r_file->digest);
			hc_test_log(LOG_ERROR_LEVEL,env,"Was able to retrieve oid %s after deleting it! /n", sys_rec.oid);
			test_res = TEST_FAIL;
			goto finished;
		}
	}
	
	hc_test_log(LOG_INFO_LEVEL, env,"harness test successfully deleted a file. OID is %s\n", 
				sys_rec.oid);
	
finished:
	if (retrieved_r_file != NULL) {
	  free_r_file(retrieved_r_file);
	}
	if (sessionp != NULL) {
	  hc_session_free(sessionp);
	}
	return test_res;
}	



