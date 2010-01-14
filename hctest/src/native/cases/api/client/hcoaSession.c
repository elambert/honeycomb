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
#include <time.h>
#include <fcntl.h>
#include "platform.h"
#include "hcoa.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"


#define NOTE_BUFF_SIZE 4096
#define HANDLE_TYPE_DELETE "delete_session"
#define HANDLE_TYPE_STORE "store_session"
#define HANDLE_TYPE_RETRIEVE "retrieve_session"

void hcoaSession_load_tests(hctest_env_t *);
//void run(void);
int hc_init_all_good_exec(test_case_t *);
int hc_clean_up_exec(test_case_t *);
int hc_init_re_init_exec(test_case_t *);
int hcoa_get_platform_name_exec(test_case_t *);
int hcoa_sessions_in_progress_exec(test_case_t *);



void hcoaSession_load_tests(hctest_env_t *test_env) {
    char *test_vip =hash_get_value(CONTEXT_KEY_DATA_VIP,test_env->context_hash);
    char *test_vip_port =hash_get_value(CONTEXT_KEY_DATA_VIP_PORT,test_env->context_hash);
    char *val_plat_name_args[1] = {"4096"};
    char *inval_plat_name_args[1] = {"1"};
    char *store_args[] ={test_vip, test_vip_port, HANDLE_TYPE_STORE, "16"};
    char *delete_args[] ={test_vip, test_vip_port, HANDLE_TYPE_DELETE, "16"};
    char *ret_args[] ={test_vip, test_vip_port, HANDLE_TYPE_RETRIEVE, "16"};

    if (test_vip == NULL) {
	hc_test_log(LOG_ERROR_LEVEL,test_env, "No data vip has been specified. Will not load tests.\n");
	return;
    }
   
    //add_test_to_list(create_test_case("hcoa.hc_init()", 0, NULL, (int (*)(void *, void *,void *))hc_init_all_good_exec),test_env,FALSE);
    add_test_to_list(create_test_case("hcoa.hc_init()", 0, NULL, hc_init_all_good_exec),test_env,FALSE);
    add_test_to_list(create_test_case("hcoa.hc_cleanup()", 0, NULL, hc_clean_up_exec),test_env,FALSE);
    // exclude due to 6310672
    add_test_to_list(create_test_case("hcoa.hc_ init[duplicate_call]()", 0, NULL,hc_init_re_init_exec),test_env,FALSE);

    add_test_to_list(create_test_case("hcoa.get_platform_name()", 1, val_plat_name_args, hcoa_get_platform_name_exec),test_env,FALSE);
    // excluded due to 6310636 
    add_test_to_list(create_test_case("hcoa.get_platform_name()", 1, inval_plat_name_args, hcoa_get_platform_name_exec),test_env,TRUE);

    add_test_to_list(create_test_case("hcoa.hcoa_sessions_in_progress()[store]", 4, store_args, hcoa_sessions_in_progress_exec), 
					test_env,TRUE);
    store_args[3] = "0";
    add_test_to_list(create_test_case("hcoa.hcoa_sessions_in_progress()[store]", 4, store_args, hcoa_sessions_in_progress_exec),
					test_env,FALSE);

    add_test_to_list(create_test_case("hcoa.hcoa_sessions_in_progress()[del]", 4, delete_args, hcoa_sessions_in_progress_exec),
					test_env,TRUE);
    delete_args[3] = "0";
    add_test_to_list(create_test_case("hcoa.hcoa_sessions_in_progress()[del]", 4, delete_args, hcoa_sessions_in_progress_exec),
					test_env,FALSE);

    add_test_to_list(create_test_case("hcoa.hcoa_sessions_in_progress()[ret]", 4, ret_args, hcoa_sessions_in_progress_exec),
					test_env,TRUE);
    ret_args[3] = "0";
    add_test_to_list(create_test_case("hcoa.hcoa_sessions_in_progress()[ret]", 4, ret_args, hcoa_sessions_in_progress_exec),
					test_env,FALSE);
/*

    char *ret_args[] ={test_vip, test_vip_port, HANDLE_TYPE_RETRIEVE, "16"};
    test_case_t *sessions_in_progress_ret = create_test_case("hcoa.hcoa_sessions_in_progress()[ret]", 4, ret_args, (int (*)(void *, void *,void *))hcoa_sessions_in_progress_exec);
    sessions_in_progress_ret->excluded=TRUE;
    if (add_test_to_list(sessions_in_progress_ret) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", sessions_in_progress_ret->name, sessions_in_progress_ret->params);
    }
    ret_args[3] = "0";
    sessions_in_progress_ret = create_test_case("hcoa.hcoa_sessions_in_progress()[ret]", 4, ret_args, (int (*)(void *, void *,void *))hcoa_sessions_in_progress_exec);
    if (add_test_to_list(sessions_in_progress_ret) == NULL) {
	hc_test_log(LOG_ERROR_LEVEL, "Unable to add %s::%s to testlist\n", sessions_in_progress_ret->name, sessions_in_progress_ret->params);
    } */
}


//int hc_init_all_good_exec(int *argc, char **argv, char *note) {
int hc_init_all_good_exec(test_case_t *tc) {

  int return_code = hc_standalone_init();
    if (return_code == HCERR_OK) {
		hc_cleanup();
		return TEST_PASS;
    }
    else {
		hc_cleanup();
		sprintf(tc->note,"call to hc_init() results in unexpected hcerr_t return code. Value returned was %s", hc_decode_hcerr(return_code));
		return TEST_FAIL;
    }
}



int hc_init_re_init_exec(test_case_t *tc) {
    int initial_call = hc_init(malloc,free,realloc);
    int dup_call = hc_init(malloc,free,realloc);
    hc_cleanup();

    if (initial_call != HCERR_OK) {
		return TEST_ERROR;
    }
    if (dup_call == HCERR_ALREADY_INITED) {
		return TEST_PASS;
    } else {
		sprintf(tc->note,"duplicate calls to hc_init() did not result in return code if HCERR_ALREADY_INITED. Value returned was %d",dup_call);
		return TEST_FAIL;
    }

} 


int hc_clean_up_exec(test_case_t *tc) {
    int initial_call = hc_standalone_init();
    int dup_call;
    hc_cleanup();
    dup_call = hc_standalone_init();

    if (initial_call != HCERR_OK) {
      hc_cleanup();
      return TEST_ERROR;
    }
    if (dup_call == HCERR_OK) {
      hc_cleanup();
      return TEST_PASS;
    } else {
      sprintf(tc->note,"call to hc_cleanup() up followed by hc_init() did not result in HCERR_OK. Value returned was %d",dup_call);
      hc_cleanup();
      return TEST_FAIL;
    }
} 



//XXX I should have this fn test what happens when I pass in junk to 
// get_platform_name
int hcoa_get_platform_name_exec (test_case_t *tc) {
    char ** argv;
    char expected_name[NOTE_BUFF_SIZE];
	
	 argv = tc->argv; 
    size_t recieved_name_size = atoi(argv[0]);
    char *recieved_name = (char *)malloc(recieved_name_size);
    if (recieved_name == NULL) {
		sprintf(tc->note,"Unable to alloc memory needed for test. Attempted to malloc %d bytes and failed",recieved_name_size);
		return TEST_FAIL;
    }

    //platform_get_platform_name(expected_name,NOTE_BUFF_SIZE);
    // XXX THE STATEMENT BELOW IS A BAD HACK TO GET AROUND THE FACT THAT platform_get_platform_name IS NOT EXPORTED ON WINDOWS
    hcoa_get_platform_name(expected_name,NOTE_BUFF_SIZE);
    hcoa_get_platform_name(recieved_name,recieved_name_size);
    if (strcmp(recieved_name,expected_name) == 0) {
		return TEST_PASS;
    } else {
	sprintf(tc->note,"The name returned by hcoa_get_platform_name(): %s did not match that returned by platform.platform_get_name(): %s", 
	    recieved_name, expected_name);
		return TEST_FAIL;
    }

}

// should test with 0 sessions, 1 session, X sessions, 
// I should drop and add sessions to see what happens
// arg 0 = VIP 
// arg 1 = PORT
// arg 3 = session types (store, retrieve, delete,vary)
// arg 4 = max session
int hcoa_sessions_in_progress_exec(test_case_t *tc) {
    int infile = -1;
    int i = 0;
    int j = 0;
    int do_work = TRUE;
	char **argv;
	
	argv = tc->argv;
    char *vip = argv[0];
    int port = atoi(argv[1]); 
    char *session_types = argv[2];
    int max_session = atoi(argv[3]);
    void **handles_array = NULL;
    void **handles_array_ptr = NULL;
    hc_oid oid = "0";
    int return_code = TEST_PASS;
    hc_standalone_init();


    if (max_session == 0) {
	int num_sessions = -1;
	if (hcoa_sessions_in_progress(&num_sessions) != 0) {
	    sprintf(tc->note,"Number of sessions returned hcoa_sessions_in_progress(int*) does not match what I expected. Expected: %d, Got: %d",
		    0,num_sessions);
	    return_code = TEST_FAIL;
	}
    } else {
      	handles_array = (void **)malloc(sizeof (void *) * max_session);
        handles_array_ptr = handles_array;
    }

    // build it up

    for (i = 0; i < max_session; i++ ) {
	int num_sessions = -1;
	hcerr_t err = -1;
	if (strcmp(session_types, HANDLE_TYPE_STORE) == 0) {
	    hcoa_store_handle_t *handle = NULL;
	    err = hcoa_store_object_create(handle, vip, port,0,0);
	    *handles_array = handle;
            handles_array++;
	}
	else if (strcmp(session_types, HANDLE_TYPE_DELETE) == 0) {
	    hcoa_delete_handle_t *handle = NULL;
	    err = hcoa_delete_object_create(handle, vip, port,oid);
	    *handles_array = (void *)handle;
            handles_array++;
	}
	else if (strcmp(session_types, HANDLE_TYPE_RETRIEVE) == 0) {
	    hcoa_retrieve_handle_t *handle = NULL;
	    err = hcoa_retrieve_object_create(handle, vip, port,oid);
	    *handles_array = handle;
            handles_array++;
	}
	else {
	    sprintf(tc->note,"Unkown handle type %s", session_types);
	    return_code = TEST_ERROR;
	    break;
	}	

	if (err != HCERR_OK) {
	    char *err_msg_buf;
	    err_msg_buf = hc_decode_hcerr(err);
	    sprintf(tc->note,"Attempt to create a %s session resulted in an Error code being returned. Return code was %s",
		session_types, err_msg_buf);
	    return_code = TEST_ERROR;
	    break;
	}
	hcoa_sessions_in_progress(&num_sessions);
	if (num_sessions != i + 1) {
	    sprintf(tc->note,"Number of sessions returned hcoa_sessions_in_progress(int*) does not match what I expected. Expected: %d, Got: %d",
		    i+1,num_sessions);
	    return_code = TEST_FAIL;
	    break;
	}
    }

    // do work (if asked for)
    if (do_work == TRUE) {
        int k = 0;
	for (k = 0; max_session != 0 && k < max_session; k++) {
    	    void **our_handle_ptr = handles_array_ptr;
	    if (strcmp(session_types, HANDLE_TYPE_STORE) == 0) {
		int err = 0;
		int written = 0;
		char filename[1024];
		FILE *outfile;
           	hc_system_record_t sys_rec;
		printf("ABOUT TO WRITE TO HC!\n");
		written = file_generator(1024, rand(),filename, outfile);
		if((infile = open(filename, O_RDONLY | O_BINARY)) == -1) {
		    printf("Failed to open data file '%s' for store: %d\n", filename, infile);
		    return 2;
		}
		err = test_store (&qa_read_from_file_data_source, (void *)infile,(hcoa_store_handle_t *)our_handle_ptr,&sys_rec );
	        our_handle_ptr++;
		close(infile);
		remove(filename);

		if (err = TRUE) {
		    printf( "Write did succeed.\n ");
		}
		else {
		    printf("Write did not succeed\n");
		}
	    }
	    else if (strcmp(session_types, HANDLE_TYPE_RETRIEVE) == 0) {
		//store junk file then retrieve it
	    }
	    else if (strcmp(session_types, HANDLE_TYPE_DELETE) == 0) {
		//store junk file then delete it
	    }
	}
    }

    // count it down
    for (j = max_session - 1; return_code == TEST_PASS  && j >=0; j--) {
    	void **our_handle_ptr = handles_array_ptr;
	hc_system_record_t sys_rec;
	long response = -1;
	char errstr[NOTE_BUFF_SIZE];
	hcerr_t err = -1;
	int num_sessions = -1;
	if (strcmp(session_types, HANDLE_TYPE_STORE) == 0) {
	    hcoa_store_handle_t *handle = (hcoa_store_handle_t*)our_handle_ptr;
	    our_handle_ptr++;
	    err = hcoa_store_close(handle, &response, errstr,NOTE_BUFF_SIZE, &sys_rec);
	}
	else if (strcmp(session_types, HANDLE_TYPE_DELETE) == 0) {
	    hcoa_delete_handle_t *handle = (hcoa_delete_handle_t*)our_handle_ptr;
	    our_handle_ptr++;
	    err = hcoa_delete_close(handle, &response, errstr,NOTE_BUFF_SIZE);
	}
	else if (strcmp(session_types, HANDLE_TYPE_RETRIEVE) == 0) {
	    hcoa_retrieve_handle_t *handle = (hcoa_retrieve_handle_t*)our_handle_ptr;
	    our_handle_ptr++;
	    err = hcoa_retrieve_close(handle, &response, errstr,NOTE_BUFF_SIZE);
	}
	if (err != HCERR_OK ) {
	    char *err_msg_buf;
	    err_msg_buf = hc_decode_hcerr(err);
	    sprintf(tc->note,"Attempt to close a %s session resulted in an Error code being returned. Return code was %s",
		session_types, err_msg_buf );
	    return_code = TEST_ERROR;
	    break;
	}
	hcoa_sessions_in_progress(&num_sessions);
	if (num_sessions != j ) {
	    sprintf(tc->note,"Number of sessions returned hcoa_sessions_in_progress(int*) does not match what I expected. Expected: %d, Got: %d",
		    j,num_sessions);
	    return_code = TEST_FAIL;
	    break;
	}
    }
    hc_cleanup();
    return return_code;

} 
