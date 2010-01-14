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



#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>

#include "hc.h"
#include "hcoa.h"
#include "hcnvoa.h"
#include "hctestutil.h"
#include "hctestcommon.h"


int write_qb_row(qb_row_t*, char*);
 
//TODO populate submitter field in QB
//TODO populate run ID in QB

// if adding or subtracting values to this array
// be sure to update the macro NUMBER_OF_ABSOLUTE_FILE_SIZE_TYPES
char * absolute_file_sizes_master_list [] = { 	
		HCFILE_SIZE_TYPE_EMPTY,
		HCFILE_SIZE_TYPE_XXSMALL,
		HCFILE_SIZE_TYPE_XSMALL,
		HCFILE_SIZE_TYPE_SMALL,
		HCFILE_SIZE_TYPE_MED,
		HCFILE_SIZE_TYPE_LARGE,
		HCFILE_SIZE_TYPE_XLARGE,
		HCFILE_SIZE_TYPE_MAX
};

// if adding or subtracting values to this array
// be sure to update the macro NUMBER_OF_BUFFER_BASED_FILE_SIZE_TYPES
char * buffer_based_file_sizes_master_list [] = {
		HCFILE_SIZE_TYPE_STORE_BUFFER,
		HCFILE_SIZE_TYPE_STORE_BUFFER_PLUS,
		HCFILE_SIZE_TYPE_STORE_BUFFER_MINUS,
		HCFILE_SIZE_TYPE_STORE_BUFFER_HALF
};


char * log_level_text[] = {
    "DBG", "INF", "WRN",
   "ERR","QUI"
};

void hc_test_log (int msg_lvl, hctest_env_t *env, char *fmt, ...) {
    static pid_t pid;
    pthread_t tid;
    long thread_id;

    if (!pid) pid = getpid();
    

    if (env == NULL || msg_lvl >= env->current_log_level) {
		va_list args;
		char time_string [HC_TIME_TEXT_SIZE]="\0";
		time_t now = time(NULL);
		time_to_text(now, time_string);

		tid = pthread_self();
#ifdef _MSC_VER
		thread_id = (long)tid.p;
#else
		thread_id = (long)tid;
#endif
		printf("%s (%ld:0x%lx) %s:",time_string,(long)pid,thread_id,log_level_text[msg_lvl]);


		va_start(args,fmt);
		vfprintf(stdout,fmt,args);
		va_end(args);
 fflush(stdout);
    }
    
}
void hc_debug_printf (const char *fmt, ...) {
    static pid_t pid;
    int msg_lvl = LOG_DEBUG_LEVEL;
    va_list args;
    pthread_t tid;
    long thread_id;
    char time_string [HC_TIME_TEXT_SIZE]="\0";
    time_t now = time(NULL);

    time_to_text(now, time_string);

    if (!pid) pid = getpid();

    tid = pthread_self();
#ifdef _MSC_VER
    thread_id = (long)tid.p;
#else
    thread_id = (long)tid;
#endif
    printf("%s (%ld:0x%lx) %s:",time_string,(long)pid,thread_id,log_level_text[msg_lvl]);

    va_start(args,fmt);
    vfprintf(stdout,fmt,args);
    va_end(args);
    printf("\n");
 fflush(stdout);
}

void free_qb_row(qb_row_t * row) {
	free(row->qb_taglist);
}


void postResult(test_case_t *tc, hctest_env_t *env) {
    char *result_text="";
    int result = tc->result;
    char *name = tc->name;
    char *params = tc->params;
    char *note = tc->note;
    char result_msg[MSG_BUFFER_SIZE];
    char time_buffer[HC_TIME_TEXT_SIZE];
    qb_row_t result_row = create_qb_row();
    qb_row_t *result_row_p = &result_row;
    char *tmp_file_name = NULL;
    char *command = NULL;
    if (strcmp(env->test_run_id,"UNKOWN") != 0) {
        result_row_p->qb_run=env->test_run_id;
    }
    result_row_p->qb_test=name;
    result_row_p->qb_params=params;
    time_to_text(tc->start_time, time_buffer);
    result_row_p->qb_start_time = time_buffer;
    time_to_text(tc->end_time, time_buffer);
    result_row_p->qb_end_time = time_buffer;
    link_list_to_string((hc_llist_t *)tc->tag_list,&(result_row_p->qb_taglist));
    switch (result) {
        case TEST_PASS:
            result_text="pass";
            env->testcases_passed++;
            break;
        case TEST_FAIL:
            result_text="fail";
            env->testcases_failed++;
			linkedlist_add_nodes(env->failed_tests_list,1,create_llist_node(create_test_scenario_name(tc)));
            break;
        case TEST_ERROR:
            result_text="error";
            env->testcases_errored++;
			linkedlist_add_nodes(env->error_tests_list,1,create_llist_node(create_test_scenario_name(tc)));
            break;
        case TEST_SKIP:
            result_text="skipped";
            env->testcases_skipped++;
            break;
        default:
            result_text="unkown";
            break;
    }
    result_row_p->qb_status = result_text;

    if (note != NULL && strcmp("", note) != 0) {
        hc_test_log(LOG_INFO_LEVEL,env,"%s::%s %s\n", name, params, note);
        result_row_p->qb_notes=note;
    }

    tmp_file_name = tmpnam(NULL);
    command = (char *)malloc(QB_COMMAND_LENGTH);

    if (write_qb_row(result_row_p, tmp_file_name) < 0) {
        hc_test_log(LOG_ERROR_LEVEL,env, "attempt to post results to qb failed!\n");
    }
    else {
        if (on_path(QB_POST_RESULTS_BIN) != TRUE) {
           hc_test_log(LOG_ERROR_LEVEL,env,"Can not find %s on path. Will not be able to post results to QB\n",QB_POST_RESULTS_BIN);
        }
        else {
            sprintf (command,"%s result %s", QB_POST_RESULTS_BIN, tmp_file_name);
            if (system(command) == 0) {;
                hc_test_log(LOG_DEBUG_LEVEL,env, "about to delete temp file %s\n",tmp_file_name);
               if(remove(tmp_file_name) != 0) {
                    hc_test_log(LOG_ERROR_LEVEL,env,"Unable to delete file %s\n",tmp_file_name);
                }
            }
            else {
                hc_test_log(LOG_ERROR_LEVEL,env, "attempt to post results to qb failed!\n");
            }
        }
    }

    sprintf(result_msg,"%s::%s  %s\n", name, params, result_text);
    hc_test_log(LOG_INFO_LEVEL,env,result_msg);
    hc_test_log(LOG_INFO_LEVEL,env,"\n");
    free_qb_row(result_row_p);
    free(command);

}


int write_qb_row(qb_row_t *row, char *file_name) {
    FILE *out_file = fopen(file_name, "w+");
    int write_result = 0;

    if (out_file == NULL || row == NULL ) {
		return -1;
    }
    if (row->qb_id != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_ID, QB_RESULT_DELIMITER, row->qb_id);
    }
    if (row->qb_test != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_TEST, QB_RESULT_DELIMITER, row->qb_test);
    }
    if (row->qb_params != NULL && write_result >= 0) {
		if (strcmp(row->qb_params,"") == 0) {
	    		write_result = fprintf(out_file, "%s%sNONE\n", QB_KEY_PARAMS, QB_RESULT_DELIMITER);
		}
		else {
	    		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_PARAMS, QB_RESULT_DELIMITER, row->qb_params);
		}
    }
    if (row->qb_run != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_RUN, QB_RESULT_DELIMITER, row->qb_run);
    }
    if (row->qb_start_time != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_START_TIME, QB_RESULT_DELIMITER, row->qb_start_time);
    }
    if (row->qb_end_time != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_END_TIME, QB_RESULT_DELIMITER, row->qb_end_time);
    }
    if (row->qb_status != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_STATUS, QB_RESULT_DELIMITER, row->qb_status);
    }
    if (row->qb_buglist != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_BUGLIST, QB_RESULT_DELIMITER, row->qb_buglist);
    }
    if (row->qb_taglist != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_TAGLIST, QB_RESULT_DELIMITER, row->qb_taglist);
    }
    if (row->qb_build != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_BUILD, QB_RESULT_DELIMITER, row->qb_build);
    }
    if (row->qb_submitter != NULL && write_result >= 0) {
		write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_SUBMITTER, QB_RESULT_DELIMITER, row->qb_submitter);
    }
    if (row->qb_log_url != NULL && write_result >= 0) {
	write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_LOG_URL, QB_RESULT_DELIMITER, row->qb_log_url);
    }
    if (row->qb_notes != NULL && write_result >= 0) {
	write_result = fprintf(out_file, "%s%s%s\n", QB_KEY_NOTES, QB_RESULT_DELIMITER, row->qb_notes);
    }

    write_result = fclose(out_file);

    return write_result;

}

hctest_env_t * create_test_env() {
	hctest_env_t * test_env;
	hc_llist_t * hc_testcase_list;
	
	test_env = (hctest_env_t *)malloc(sizeof(hctest_env_t));
    memset(test_env,'\0', sizeof(hctest_env_t));
	hc_testcase_list = hc_create_linkedlist();

   	test_env->context_hash = create_hashlist(CONTEXT_HASH_SIZE);
    test_env->tests_to_run = create_hashlist(CONTEXT_HASH_SIZE);
    test_env->testcases_found = 0;
    test_env->testcases_passed = 0;
    test_env->testcases_failed = 0;
    test_env->testcases_errored = 0;
    test_env->testcases_skipped = 0;
    test_env->start_time = time(NULL);
    test_env->testcases_specified = FALSE;
    test_env->help_asked = FALSE;
    test_env->rseed = rand();
    test_env->current_log_level = LOG_DEBUG_LEVEL;
    test_env->test_run_id = "";
    test_env->data_vip_port = atoi(DEFAULT_VIP_PORT);
    test_env->data_vip = NULL;
    test_env->test_case_list=hc_testcase_list;
    return test_env;
    
}
// The reason this is here is that someday we may want to 
// have more interesting defaults than the empty string
qb_row_t create_qb_row() {
    qb_row_t row = { 
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL,
	NULL
    };
    return row;
}


int execute_test(test_case_t *tc, hctest_env_t *env) {
    char **argv = NULL;
    int argc;
    char token[MSG_BUFFER_SIZE] = "\0";
    char parms_text[MSG_BUFFER_SIZE] = "\0";
    //char *tok = NULL;
    char *test_selected =  hash_get_value(tc->name,env->tests_to_run);
    char *text_divider = "=========================================\n";
    int (*executor)(test_case_t*) = NULL;
    int result;

    if ((env->testcases_specified  == TRUE) && (test_selected == NULL)) {
		env->testcases_skipped++;
		hc_test_log(LOG_DEBUG_LEVEL,env,text_divider);
		hc_test_log(LOG_DEBUG_LEVEL,env,"TEST: %s::%s. SKIPPING! Test was not specified for execution.\n", tc->name, tc->params);
		hc_test_log(LOG_DEBUG_LEVEL,env,text_divider);
		return TEST_SKIP;
    }
    if ((env->testcases_specified == FALSE) && (tc->excluded == TRUE)) {
		env->testcases_skipped++;
		hc_test_log(LOG_INFO_LEVEL,env,text_divider);
		hc_test_log(LOG_INFO_LEVEL,env,"TEST: %s::%s. SKIPPING! Test was marked as execluded.\n", tc->name, tc->params);
		hc_test_log(LOG_INFO_LEVEL,env,text_divider);
		return TEST_SKIP;
    }
    argc = tc->argc;
    argv = tc->argv;
    executor = tc->exec_test;
    if (argc != 0) {
		int i;
		for (i = 0; i < argc; i++) {
			char *sep = (i == 0 ? "" : "::");
			if (argv[i] == NULL)
				sprintf(token,"%s%s",sep,"*NULL");
			else
				sprintf(token,"%s%s",sep,argv[i]);
			strncat(parms_text,token, strlen(token));
		}
    }
    tc->params = parms_text;

    hc_test_log(LOG_INFO_LEVEL,env,text_divider);
    hc_test_log(LOG_INFO_LEVEL,env,"TEST: %s::%s  Starting...\n", tc->name, tc->params);
    hc_test_log(LOG_INFO_LEVEL,env,text_divider);
    tc->start_time = time(NULL);
    result = tc->result = (*executor)(tc);
    tc->end_time = time(NULL);
    postResult(tc,env);
    hc_test_log(LOG_INFO_LEVEL,env,"\n");
    free_test_case(tc);

    return result;
}


void tag_test(test_case_t *tc, char ** tags, int num_tags) {
    int i;

    if (tc == NULL) {
		hc_test_log(LOG_WARN_LEVEL,NULL,"Attempted to add tag(s) to a null TestCase. Skipping tag op!");
		return;
    }

    if (tags == NULL) {
		hc_test_log(LOG_WARN_LEVEL,NULL,"Attempted to add null tag(s) to a TestCase %s::%s. Skipping tag op!", tc->name,tc->params);
		return;
    }

    for (i = 0; i < num_tags; i++) {
		char *cur_tag_value = NULL;
		cur_tag_value = (char *) malloc(strlen(tags[i]) + 1);
		if (cur_tag_value == NULL) {
			hc_test_log(LOG_ERROR_LEVEL,NULL,"Dont have enough memory to add tag(s) to a TestCase %s::%s. Skipping tag op!\n", tc->name,tc->params);
			return;
		}
		strcpy(cur_tag_value,tags[i]);
		linkedlist_add_nodes(tc->tag_list, 1,cur_tag_value);
    }

}


void
add_test_to_list(test_case_t *tc, hctest_env_t *env,int skip) {
	hc_llist_t *test_case_list;
	char * tags;

	test_case_list = env->test_case_list;
    if (test_case_list == NULL) {
		test_case_list = hc_create_linkedlist();
    }
    linkedlist_add_nodes(test_case_list,1,tc);
    env->testcases_found++;
    if (skip == TRUE) {
      tc->excluded = TRUE;
      hc_test_log(LOG_DEBUG_LEVEL, env, "Testcase %s::%s marked as skipped.\n", tc->name, tc->params);
    }
    hc_test_log(LOG_DEBUG_LEVEL, env,"Added test %s::%s to test list\n", tc->name, tc->params);
    //link_list_to_string(tc->tag_list,&tags);
}

test_case_t *create_and_tag_test_case (char *name, int argc, char **argv, int (*exec_fn) (test_case_t *), char ** tagsv, int tagsc, hctest_env_t *env ) {
    test_case_t *new_test_case = create_test_case(name,argc,argv,exec_fn, env);
    tag_test(new_test_case,tagsv,tagsc);
    return new_test_case;
}

test_case_t 
*create_test_case (char *name, 
				  int argc, 
				  char **argv,
				  int (*exec_fn) (test_case_t *), 
				  hctest_env_t *env) 
{
    test_case_t *new_test_case = (test_case_t *)calloc(1,sizeof (test_case_t));
    new_test_case->test_env = env;
    if (argc > 0) {
		char **arg_array = (char **)calloc(argc,sizeof (char *)); // an array of pointers to chars
		char *value;
		int i;
		new_test_case->argc = argc;
		new_test_case->argv = arg_array;
		for (i = 0; i < argc; i++) {
	    		if (argv[i] != NULL) {
				value = (char *)malloc(strlen(argv[i]) + 1); // create safe loc
				strcpy(value,argv[i]); //copy data to safe loc
	    		} else {
				value = NULL;
	    		}
	    		*arg_array = value;
	    		arg_array++;
		}
    	} else {
		new_test_case->argc = 0;
		new_test_case->argv = NULL;
    }
    new_test_case->name = name;
    new_test_case->params = "";
    new_test_case->result = TEST_ERROR;
    new_test_case->exec_test = exec_fn;
    new_test_case->excluded = FALSE;
    new_test_case->tag_list = hc_create_linkedlist();
    return new_test_case;
}

void free_test_case(test_case_t *tc) {
    if (tc->argc > 0 ) {
		int i;
		for (i = 0; i < tc->argc; i++) {
			free(tc->argv[i]);
		}
		free(tc->argv);
    }
    free_linkedlist(tc->tag_list,free);
	/*free(tc->name);
	free(tc->note);
	free(tc->params);*/
    free(tc);
}




int parse_context(char *context_args, hctest_env_t *env) {

    int ret_code = TRUE;
    char *copy = (char *)malloc(strlen(context_args) +1);
    char *tok = NULL;
    if (copy == NULL) {
		return FALSE;
    }
    strcpy(copy,context_args);

    //while tokens
    tok = strtok(copy, CONTEXT_TOKEN_SEP);
    while (tok != NULL) {
		char *name = NULL;
		char *value = NULL;
		size_t seploc = strcspn(tok,CONTEXT_NV_SEP);
		if (seploc == strlen(tok)) {
			hash_put(tok,CONTEXT_NAME_IS_SET,env->context_hash);
		} else {
			if (seploc == 0) {
				ret_code = FALSE;
				break;
			}
			name = (char *)malloc(seploc + 1);
			memset(name,'\0',seploc+1);
			value = (char *)malloc(strlen(tok) - seploc);
			if (name == NULL || value == NULL) {
				free(name);
				free(value);
				ret_code = FALSE;
			break;
			}
			strncpy(name,tok,seploc);
			tok = tok + seploc + 1;
			strcpy(value,tok);
			hash_put(name,value,env->context_hash);
			free(name);
			free(value);
		}
		tok = strtok(NULL,CONTEXT_TOKEN_SEP);
	}
	free(copy);
    return ret_code;

}

void print_test_scenarios (hc_llist_t *tests) {
	hc_llist_node_t *cur_node;
	cur_node = tests->head;
	while (cur_node != NULL) {
		char **name = (char**)cur_node->entry;
		hc_test_log(LOG_INFO_LEVEL,NULL,"%s\n", *name);
		cur_node = cur_node->next;
	 }
}

int resolve_vip_port(char *vip_port_type, hctest_env_t *hc_current_env ) {
	if (strcmp(vip_port_type,VIP_PORT_TYPE_USER) == 0) {
    		char *port =  hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,hc_current_env->context_hash);
		if (port != NULL) {
			return atoi(port);
		} else {
			hc_test_log(LOG_ERROR_LEVEL,NULL, "Unable to detemine vip port. Using default of 8080");
			return DEFAULT_BASE_PORT;
		}
	} else if (strcmp(vip_port_type,VIP_PORT_TYPE_INVALID) == 0) {
		return 0;
	} else {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unrecognized VIP port type: %s\n",vip_port_type);
		return -1;
	}
}

void resolve_vip(char *vip_type, char **vip, hctest_env_t *hc_current_env) {
	if (strcmp(vip_type,VIP_TYPE_USER) == 0) {
		*vip = hash_get_value(CONTEXT_KEY_DATA_VIP,hc_current_env->context_hash);
	} else if (strcmp(vip_type,VIP_TYPE_EMPTY) == 0) {
		*vip = EMPTY_STRING;
	} else if(strcmp(vip_type,VIP_TYPE_NULL) == 0) {
		*vip = NULL;
	} else if (strcmp(vip_type,VIP_TYPE_IP) == 0) {
		*vip = LOOP_BACK;
	} else {
		hc_test_log(LOG_ERROR_LEVEL,hc_current_env, "Unrecognized VIP type: %s\n",vip_type);
	}
}

void resolve_fail_over_values(char *fail_over_config, hc_long_t *chunk_size, hc_long_t *window_size) {
	if (strcmp(fail_over_config,FO_ZERO) == 0) {
		*chunk_size = 0;
		*window_size = 0;
	} else if (strcmp(fail_over_config,FO_NEGATIVE) == 0) {
		*chunk_size = -1;
		*window_size = -1;
	} else if (strcmp(fail_over_config,FO_DEFAULT) == 0) {
		*chunk_size = 1048576;
		*window_size = 9999;
	} else {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unrecognized Failover type configuration: %s\n",fail_over_config);
	}
}

void resolve_oid_type(char *oid_type, hc_oid **oid) {
	if (strcmp(oid_type,OID_TYPE_VALID) == 0) {
		// go out and create one and then then return it
		
	} else if (strcmp(oid_type,OID_TYPE_INVALID) == 0) {		
	} else if (strcmp(oid_type,OID_TYPE_NULL) == 0) {
		*oid = NULL;
	} else {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unrecognized oid type configuration: %s\n",oid_type);
	}
}

void resolve_buffer_type(char *buffer_type, char ** buffer, hc_long_t *buff_size) {
	hc_long_t requested_size = 0;
	char * buffer_ptr = NULL;
	if (strcmp(buffer_type,BUFFER_TYPE_NULL) == 0) {
		*buff_size = 0;
		*buffer = NULL;
	} else { 
		requested_size = translate_size(buffer_type);
		if (requested_size > 0 ) {
			buffer_ptr = (char *)malloc(requested_size);
			if (buffer_ptr == NULL) {
				hc_test_log(LOG_ERROR_LEVEL, NULL, "Unable to malloc enough memory for buffer. Was asked for "LL_FORMAT".\n", requested_size);
			} else {
				*buffer = buffer_ptr;
				*buff_size = requested_size;
			}
		} else {
			hc_test_log(LOG_ERROR_LEVEL, NULL, "Invalid Buffer Size %s\n", buffer_type);
		}
	}
}


char * create_test_scenario_name (test_case_t *tc) {
	char * sep = "::";
	int testname_len, testparams_len, sep_len;
	char *name,*ptr;
	testname_len = strlen(tc->name);
	testparams_len = strlen(tc->params);
	sep_len = strlen(sep);
	if (testparams_len > 0) {
		name = (char *)malloc(testname_len + testparams_len + sep_len + 2);
	} else {
		name = (char *)malloc(testname_len + 2);
	}
	ptr = name;
	strncpy(ptr,tc->name,testname_len);
	ptr += testname_len;
	if (testparams_len > 0) {
		strncpy(ptr,sep,sep_len);
		ptr += sep_len;
		strncpy(ptr, tc->params, testparams_len);
		ptr += testparams_len;
	}
	strncpy(ptr, "\n", 1);
	ptr++;
	*ptr = '\0';
	return name;
}

void resolve_range_type (char * range_type, hc_random_file_t *r_file, hc_long_t buffer_size, hc_long_t *offset, hc_long_t *length) {
	if (strcmp(range_type,HC_RANGE_ALL_FILE) == 0) {
		*offset = 0;
		*length = r_file->file_size;
	} else if (strcmp(range_type, HC_RANGE_FIRST_BYTE) == 0) {
		*offset = 0;
		*length = 1;
	} else if (strcmp(range_type, HC_RANGE_LAST_BYTE) == 0) {
		*offset = r_file->file_size  - 1;
		*length = 1;
	} else if (strcmp(range_type, HC_RANGE_ENTIRE_BUFFER) == 0) {
		*offset = 0;
		*length = buffer_size;
	} else if (strcmp(range_type, HC_RANGE_BUFFER_MINUS_ONE) == 0) {
		*offset = 0;
		*length = buffer_size -1;
	}  else if (strcmp(range_type, HC_RANGE_BUFFER_PLUS_ONE) == 0) {
		*offset = 0;
		*length = buffer_size + 1;
	}
	
	  
	r_file->sha_offset = *offset;
	r_file->sha_length = *length;

}

void free_test_env (hctest_env_t *env) {
	free(env->data_vip);
	free_hashlist(env->context_hash);// herexxx
	free_linkedlist(env->error_tests_list,free);
	free_linkedlist(env->failed_tests_list,free);//
	free(env->test_run_id); //here
	free_hashlist(env->tests_to_run); //here
	free(env);
}

int generate_buffer_based_file_size_list (char *** array_ptr) {
	*array_ptr = &buffer_based_file_sizes_master_list[0];
	return NUMBER_OF_BUFFER_BASED_FILE_SIZE_TYPES;
}

int generate_absolute_file_size_list(char *** array_ptr, hc_long_t file_size) {
	int i = 0;
	hc_long_t file_size_max = -1;

	*array_ptr = &absolute_file_sizes_master_list[0];
	while ((i < NUMBER_OF_ABSOLUTE_FILE_SIZE_TYPES)) {
		file_size_max = get_filesize_type_max(absolute_file_sizes_master_list[i]);
		if (file_size_max > file_size) {
			break;
		}
		i++;
	}
	if ( i > NUMBER_OF_ABSOLUTE_FILE_SIZE_TYPES) {
		return 0;
	} else {
		return i;
	}
}
