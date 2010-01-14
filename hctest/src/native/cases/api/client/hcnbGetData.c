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
#include "hcnvoa.h"
#include "hcoaez.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

int hcnb_getdata_simpletest_exec(test_case_t *);
int hcnb_getdata_rangetest_exec(test_case_t *);

char *nb_getdata_tags[] = {"capi","capinb_getdata"};



void hcnb_getdata_load_tests(hctest_env_t *test_env) {
	int i;
	char * max_file_size = NULL;
	char **file_size_ptr = NULL;
	int number_of_file_sizes;
	max_file_size = hash_get_value(CONTEXT_KEY_MAX_FILE_SIZE,test_env->context_hash);
	if (max_file_size == NULL) {
		max_file_size = HCFILE_SIZE_TYPE_MAX;
	}
	number_of_file_sizes = generate_absolute_file_size_list(&file_size_ptr,get_file_size(max_file_size));
	for (i = 0; i < number_of_file_sizes; i++) {
			char *argv1[] = {"0",*file_size_ptr,"6MB"};
	  		add_test_to_list(create_and_tag_test_case("capinb_getdata_simpletest",
								  3, argv1,
								  (int (*)(test_case_t*))hcnb_getdata_simpletest_exec,nb_getdata_tags,2,test_env),test_env,FALSE);
		file_size_ptr++;
	}
	
	  do {
	    char *argv2[]= {"0",HCFILE_SIZE_TYPE_XXSMALL,"4KB",HC_RANGE_FIRST_BYTE};

	    add_test_to_list(create_and_tag_test_case("capinb_getdata_rangetest",
						  4, argv2,
						  (int (*)(test_case_t*))hcnb_getdata_rangetest_exec,nb_getdata_tags,2,test_env),test_env,TRUE);
	  } while(0);



}


int hcnb_getdata_simpletest_exec(test_case_t *tc) {
	//declare
	char * test_vip, * test_vip_port, **argv, *buf;
	int port;
	hcerr_t expected_res;
	hc_long_t buf_len, file_size;
	hc_random_file_t retrieved_r_file;
	//hcoa_retrieve_handle_t retrieve_handle;
	hc_system_record_t sys_rec;
	void *handle;
	hctest_env_t *env  = NULL;
	hc_session_t *sessionp;
	hc_archive_t *archive;
	hc_test_result_t test_res;		
	int errstr_len = 4096;
	char *errstr = malloc(errstr_len);
	int32_t response_code;
	
	//define
	env = tc->test_env;
	argv = tc->argv;
	expected_res = atoi(argv[0]);
	file_size = get_file_size(argv[1]);	
 	resolve_buffer_type(argv[2],&buf,&buf_len);
	test_vip = env->data_vip;
	port = env->data_vip_port;
	memset(&retrieved_r_file,'\0',sizeof(retrieved_r_file));
	init_random_file(&retrieved_r_file);

	// doit
	hc_init(malloc,free,realloc);
	store_tmp_data_file(test_vip,port,file_size,&sys_rec);
	hc_session_create_ez(test_vip,port, &sessionp);
	hc_session_get_archive(sessionp, &archive);
	// retrieve file
	
	if ((hcoa_retrieve_ez(archive,
			      retrieve_data_to_rfile, &retrieved_r_file,
			      &(sys_rec.oid), &response_code, errstr,errstr_len))
	    != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unable to retrieve data.\n");
		test_res = TEST_ERROR;
	} else {
	  sha1_finish(retrieved_r_file.sha_context,retrieved_r_file.digest);
	}
	
	if (compare_rfile_to_sys_rec(&retrieved_r_file,&sys_rec) == FALSE) {
		test_res = TEST_FAIL;
	}

	hc_session_free(sessionp);
	free(errstr);
	
	return test_res;
}

int hcnb_getdata_rangetest_exec(test_case_t *tc) {
	//declare
	char * test_vip, **argv, *buf,*errstr, *range_type, *exp_sha, *ret_sha;
	int port, errstr_len;
	int32_t response_code;
	hcerr_t expected_res;
	hc_long_t buf_len, file_size, range_firstbyte,range_lastbyte;
	hc_random_file_t *retrieved_r_file = NULL;
	hc_random_file_t *stored_r_file = NULL;
	hc_system_record_t sys_rec;
	hctest_env_t *env  = NULL;
	hc_session_t *sessionp = NULL;
	hc_archive_t *archive = NULL;
	void *retrieve_handle = NULL;
	void *store_handle = NULL;
	hc_test_result_t test_res;
	//define

	argv = tc->argv;
	buf_len = 4096;
	errstr_len = 4096;
	buf = (char *) malloc(buf_len);
	errstr = (char *)malloc(errstr_len);
	expected_res = atoi(argv[0]);
	file_size = get_file_size(argv[1]);	
	resolve_buffer_type(argv[2],&buf,&buf_len);
	range_type = argv[3];
	env=tc->test_env;
	test_vip = env->data_vip;
	port = env->data_vip_port;

	
	// doit
	hc_init(malloc,free,realloc);
	hc_session_create_ez(test_vip,port, &sessionp);
	hc_session_get_archive(sessionp, &archive);
    
	stored_r_file = create_random_file(file_size);
	retrieved_r_file = create_random_file(0);
	init_random_file(retrieved_r_file);

	resolve_range_type(range_type,stored_r_file,buf_len,&range_firstbyte,&range_lastbyte);
		
	
	
	if ((hcoa_store_ez(archive,
			   read_from_random_data_generator,
			   stored_r_file,
			   &sys_rec,
			   &response_code,errstr, errstr_len)) != HCERR_OK) {

		hc_test_log(LOG_ERROR_LEVEL,NULL,"Unable to create store randome data\n");
		test_res = TEST_ERROR;
	}
	
	// retrieve range with in the file
	if ((hcoa_range_retrieve_ez(archive,
				    retrieve_data_to_rfile, retrieved_r_file,
				    &sys_rec.oid, 
				    range_firstbyte, range_lastbyte,
				    &response_code,errstr, errstr_len)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL, "Unable to create retrieve handle. \n");
		test_res = TEST_ERROR;
	}
	sha1_finish(retrieved_r_file->sha_context,retrieved_r_file->digest);
	
	if (retrieved_r_file->file_size != (range_lastbyte - range_firstbyte + 1)) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,"Bytes retrieved does not match expected value."
			    " Expected "LL_FORMAT", got "LL_FORMAT"\n", range_lastbyte, retrieved_r_file->file_size);
		test_res = TEST_FAIL;
	}
	
	
	if (compare_rfile_to_sys_rec(retrieved_r_file,&sys_rec) == FALSE) {
		test_res = TEST_FAIL;
	} 
	exp_sha = (char *)malloc(41);
	ret_sha = (char *)malloc(41);
	digest_to_string(retrieved_r_file->digest,ret_sha);
	digest_to_string((unsigned char*)sys_rec.data_digest ,exp_sha);
	free_r_file(retrieved_r_file);
	free_r_file(stored_r_file);
	free(errstr);
	free(buf);
	hc_session_free(sessionp);
	return test_res;
}


