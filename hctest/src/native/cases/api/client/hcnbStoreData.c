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
#include <fcntl.h>
#include "platform.h"
#include "hcoa.h"
#include "hcoaez.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"


void hcnb_storedata_load_tests(hctest_env_t *);
int hcnb_storedata_create_handle_exec(test_case_t *);
char *nb_storedata_tags[] = {"capi","capinb_storedata"};
void shutdown_test(hcoa_store_handle_t *, ...);
int hcnb_storedata_interesting_filesizes_exec (test_case_t *);
hcerr_t initialize(void *, char *, int , hc_long_t , hc_long_t, hc_session_t * );
char *test_vip = NULL;
char *max_file_size = NULL;
char *valid_vip_types[] = {VIP_TYPE_USER,VIP_TYPE_IP};
int valid_vip_cfgs = 2;
char *invalid_vip_types[] = {VIP_TYPE_EMPTY,VIP_TYPE_NULL};
int invalid_vip_cfgs = 2;
char *valid_vip_port_types[] = {VIP_PORT_TYPE_USER};
int valid_vip_port_cfgs = 1;
char *invalid_vip_port_types[] = {VIP_PORT_TYPE_INVALID};
int invalid_vip_port_cfgs = 1;
char *valid_fo_types[] = {FO_DEFAULT};
int valid_fo_cfgs = 1;
char *invalid_fo_types[] = {FO_NEGATIVE};
int invalid_fo_cfgs = 1;
int indx;
char **file_size_ptr;
int number_of_file_sizes;

void hcnb_storedata_load_tests(hctest_env_t *test_env) {
    test_vip = test_env->data_vip;
    if (test_vip == NULL) {
		hc_test_log(LOG_ERROR_LEVEL,test_env, "No data vip has been specified. Will not load tests.\n");
		return;
	}
	max_file_size = hash_get_value(CONTEXT_KEY_MAX_FILE_SIZE,test_env->context_hash);
	if (max_file_size == NULL) {
		max_file_size = HCFILE_SIZE_TYPE_MAX;
	}


    //////////////////
    // handle_tests //
    ///////////////// 
    
    for (indx = 0; indx < valid_vip_cfgs; indx++) {
      char *argv[] = {"0",valid_vip_types[indx],VIP_PORT_TYPE_USER,FO_DEFAULT};

    	add_test_to_list(create_and_tag_test_case("capinb_storedata_create_handle",
						  4, argv,
						  (int (*)(test_case_t*))hcnb_storedata_create_handle_exec,nb_storedata_tags,2,test_env),test_env,FALSE);
    }

	//excluded due to bug 6362849
    for (indx = 0; indx < invalid_vip_cfgs; indx++) {
      char *argv[] = {"6",invalid_vip_types[indx],VIP_PORT_TYPE_USER,FO_DEFAULT};
    	add_test_to_list(create_and_tag_test_case("capinb_storedata_create_handle",
			    		       	4, argv,
					       	(int (*)(test_case_t*))hcnb_storedata_create_handle_exec,nb_storedata_tags,2,test_env),test_env,TRUE);
    }

    for (indx = 0; indx < valid_vip_port_cfgs; indx++) {
      char *argv[] = {"0",VIP_TYPE_USER,valid_vip_port_types[indx],FO_DEFAULT};
    	add_test_to_list(create_and_tag_test_case("capinb_storedata_create_handle",
			    		       	4, argv,
					       	(int (*)(test_case_t*))hcnb_storedata_create_handle_exec,nb_storedata_tags,2,test_env),test_env,FALSE);
    }
	
	//excluded due to bug 6362849
    for (indx = 0; indx < invalid_vip_port_cfgs; indx++) {
        char *argv[] = {"6",VIP_TYPE_USER,invalid_vip_port_types[indx],FO_DEFAULT};

    	add_test_to_list(create_and_tag_test_case("capinb_storedata_create_handle",
			    		       	4, argv,
					       	(int (*)(test_case_t*))hcnb_storedata_create_handle_exec,nb_storedata_tags,2,test_env),test_env,TRUE);
    }

    for (indx = 0; indx < valid_fo_cfgs; indx++) {
	char *argv[] = {"0",VIP_TYPE_USER,VIP_PORT_TYPE_USER,valid_fo_types[indx]};

    	add_test_to_list(create_and_tag_test_case("capinb_storedata_create_handle",
			    		       	4, argv,
					       	(int (*)(test_case_t*))hcnb_storedata_create_handle_exec,nb_storedata_tags,2,test_env),test_env,FALSE);
    }
	
	//excluded due to bug 6362849
    for (indx = 0; indx < invalid_fo_cfgs; indx++) {
        char *argv[] = {"6",VIP_TYPE_USER,VIP_PORT_TYPE_USER,invalid_fo_types[indx]};

    	add_test_to_list(create_and_tag_test_case("capinb_storedata_create_handle",
			    		       	4, argv,
					       	(int (*)(test_case_t*))hcnb_storedata_create_handle_exec,nb_storedata_tags,2,test_env),test_env,TRUE);
    } 
		
	/*char * buffer_size_types_list [] = {
		HCFILE_SIZE_TYPE_EMPTY,
		HCFILE_SIZE_TYPE_ONEBYTE,
		HCFILE_SIZE_TYPE_SMALL,
		"1K",
		"4K"
	}; */
  
    ///////////////// 
    // STORE TESTS // 
    ///////////////// 
	file_size_ptr = NULL;
	number_of_file_sizes = generate_absolute_file_size_list(&file_size_ptr,get_file_size(max_file_size));
    for (indx = 0; indx < number_of_file_sizes; indx++) {
      char *argv[] = {*file_size_ptr,HCFILE_SIZE_TYPE_STORE_BUFFER};
    	add_test_to_list(create_and_tag_test_case("capinb_storedata_interesting_filesizes",
			    		       	2, argv,
					       	(int (*)(test_case_t*))hcnb_storedata_interesting_filesizes_exec,nb_storedata_tags,2,test_env),test_env,FALSE);
							file_size_ptr++;
    }

	number_of_file_sizes = generate_buffer_based_file_size_list(&file_size_ptr);
    for (indx = 0; indx <  number_of_file_sizes; indx++) {
      char *argv[] = {"4096",*file_size_ptr};
    	add_test_to_list(create_and_tag_test_case("capinb_storedata_interesting_buffersizes",
			    		       	2, argv,
					       	(int (*)(test_case_t*))hcnb_storedata_interesting_filesizes_exec,nb_storedata_tags,2,test_env),test_env,FALSE);
							file_size_ptr++;
    }
	
}


int hcnb_storedata_create_handle_exec(test_case_t *tc) { 
	int res, expected_result, port;
	char *host = NULL;
	char **argv = NULL;
	void *handle = NULL;
	hc_long_t chunk_size, window_size;
	hctest_env_t *env = NULL;
	hc_session_t *sessionp = NULL;

	env = tc->test_env;
	argv = tc->argv;
	expected_result = atoi(argv[0]);
	resolve_vip(argv[1],&host,env);
	port = resolve_vip_port(argv[2],env);
	resolve_fail_over_values(argv[3],&chunk_size,&window_size);	
	res = initialize(handle,host,port,chunk_size,window_size,sessionp);
	if (res != expected_result) {
		sprintf(tc->note,"Expected a result code of %s, got a %s\n",hc_decode_hcerr(expected_result), hc_decode_hcerr(res));
		shutdown_test(handle);
		return TEST_FAIL;
	} else {
		shutdown_test(handle);
		return TEST_PASS;
	}
}



int hcnb_storedata_interesting_filesizes_exec (test_case_t *tc) { 
	hc_random_file_t * r_file = NULL;
	char **argv = NULL;
	char *host = NULL; 
	char *file_size_type = NULL;
	char *buffer = NULL;
	char *errstr = NULL;
	char *s_digest = NULL;
	int port = -1;
	hc_long_t chunk_size,window_size,file_size = 0;
	void *handle = NULL;
	size_t buffer_len = STORE_BUFFER_SIZE;
	hcerr_t res = 0;
	hc_system_record_t sys_rec;
	int32_t response_code =-1;
	hc_string_index_t errstrlen = 4096;
	hctest_env_t *env = NULL;
	hc_session_t *sessionp = NULL;
	hc_archive_t *archive = NULL;
	
	//char data_digest_string[41];
	env=tc->test_env;
	s_digest = (char *) malloc(41);
	memset(s_digest,'\0',41);
	argv = tc->argv;
	file_size_type = argv[0];
	buffer = (char *) malloc(buffer_len);
	if (buffer == NULL ) {
		sprintf(tc->note,"Unable to allocate enough buffer space for store buffer. Needed %ld .",buffer_len);
		return TEST_ERROR;
	}
	memset(buffer,'\0',buffer_len);
	errstr = (char *) malloc(errstrlen);
	if (errstr == NULL ) {
		sprintf(tc->note,"Unable to allocate enough buffer space for store buffer. Needed %d .",errstrlen);
	}
	memset(errstr,'\0',errstrlen);

	//resolve_vip(VIP_TYPE_USER,host);
	host = test_vip;
	port = resolve_vip_port(VIP_PORT_TYPE_USER, env);
	resolve_fail_over_values(FO_ZERO, &chunk_size, &window_size);
  	file_size = get_file_size(file_size_type);	
  	r_file = create_random_file(file_size);	

	hc_test_log(LOG_INFO_LEVEL,NULL, "About to store a file of size "LL_FORMAT"",file_size);
	hc_session_get_archive (sessionp, &archive);
fflush(stdout);
	res = hcoa_store_ez(archive,read_from_random_data_generator,r_file,
			    &sys_rec,
			    &response_code,errstr, errstrlen);
	if (res != HCERR_OK) {
		sprintf(tc->note,"Store operation returned unexpected result code. Got %s, expected %s\n",
			hc_decode_hcerr(res),hc_decode_hcerr(0));
		shutdown_test(handle, &buffer, &errstr);
		free_r_file(r_file);
		return TEST_FAIL;
	}
	if (compare_rfile_to_sys_rec(r_file,&sys_rec) != TRUE) {
		sprintf(tc->note,"Store operation resulted in unmatched files.");
		shutdown_test(handle, &buffer, &errstr);
		free_r_file(r_file);
		return TEST_FAIL; 
	}
	free_r_file(r_file);
	shutdown_test(handle, buffer, errstr);
	return TEST_PASS; 
	
}


/*

//capinb_storedata_invalid_ops_order ensure that api behaves when an store sub-operation is done out of order
int hcnb_storedata_invalid_ops_order_exec (test_case_t *tc) {
}

//capinb_storedata_badcluster ensure that API will gracefully disallow stores to a invalid or full cluster
int hcnb_storedata_badcluster_exec (test_case_t *tc) {
/XXX TO DO 
}

//capinb_storedata_usage ensure that API gracefully handles usage errors (Invalid Order of ops,Improper Close --invalid/null handle, bad error string length size, NP for response_code, errstr, sysrec)
int hcnb_storedata_usage_exec (test_case_t *tc) {
}
*/

hcerr_t initialize(void *handle, char *host, int port, hc_long_t chunk_size, hc_long_t window_size, hc_session_t *sessionp) {
	hc_archive_t *archive = NULL;
	hcerr_t res;
	
	res = hc_session_create_ez (host, port, &sessionp);

	if (res != HCERR_OK && res != HCERR_ALREADY_INITED ) {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unable to initialize honeycomb session. hc_session_create() returned %s.\n",hc_decode_hcerr(res));
		goto finished;
	} 
	res = hc_session_get_archive (sessionp, &archive);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unable to initialize honeycomb session. hc_session_get_archive () returned %s.\n",hc_decode_hcerr(res));
		goto finished;
	}
//	res = hcoa_store_object_create(&handle,archive,chunk_size,window_size);

	finished: 
		return res; 
}


void shutdown_test(hcoa_store_handle_t *handle, ...) {
	
	va_list ap;
	void * obj_p;

	hc_cleanup();

	va_start(ap,handle);
	obj_p = va_arg(ap,void *);
	if (obj_p != NULL) {
		free(obj_p);
	}
	va_end(ap);
	free(handle); 
}
