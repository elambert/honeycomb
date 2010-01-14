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
#include <assert.h>

#include "platform.h"
#include "hcoa.h"
#include "hcnvoa.h"
#include "hcoaez.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

#define BUFFER_TARGET "buffer_target"
#define FILE_TARGET "file_target"


int hcez_getdata_simpletest_exec(test_case_t *);

char *ez_getdata_tags[] = {"capi","capiez_getdata"};

char * data_target_types [] = {BUFFER_TARGET,FILE_TARGET};


void hcez_getdata_load_tests(hctest_env_t *test_env) {

	int i,j;
	char * max_file_size = hash_get_value(CONTEXT_KEY_MAX_FILE_SIZE,test_env->context_hash);
	char **file_size_ptr = NULL;
	char **cur_file_size = NULL;
	int number_of_file_sizes;
	char *argv[3];
	if (max_file_size == NULL) {
		max_file_size = HCFILE_SIZE_TYPE_MAX;
	}
	number_of_file_sizes = generate_absolute_file_size_list(&file_size_ptr,get_file_size(max_file_size));
	for (i = 0; i < 2; i++) {
		cur_file_size = file_size_ptr;
		for(j = 0; j < number_of_file_sizes; j++) {
		  argv[0] = "0";
		  argv[1] = *cur_file_size;
		  argv[2] = data_target_types[i];
		  add_test_to_list(create_and_tag_test_case("capiez_getdata_simpleget",
							    3, argv,
							    (int (*)(test_case_t*))hcez_getdata_simpletest_exec,ez_getdata_tags,2,test_env),test_env,FALSE);
		  cur_file_size++;
		}
	}
}

int hcez_getdata_simpletest_exec(test_case_t *tc) {
	
	char * test_vip = NULL;
	char * errstr = NULL;
	char ** argv = NULL;
        char * data_source = NULL;
	char * file_name = NULL;
	int port, errstr_len = 4096;
	int32_t response_code;
	hc_long_t file_size;
	hcerr_t expected_res;
	hcerr_t res;
	hc_oid oid;
	hc_random_file_t * retrieved_r_file = NULL;
	hc_random_file_t * stored_r_file = NULL;
	hc_test_result_t test_res;
	hctest_env_t *env = NULL;  
	hc_session_t *session = NULL;
	hc_archive_t *archive = NULL;
	hc_system_record_t sys_rec;
	
	argv = tc->argv;
	expected_res = atoi(argv[0]);
	file_size = get_file_size(argv[1]);
	data_source = argv[2];
	env = tc->test_env;
	
	test_vip = env->data_vip;
	port = env->data_vip_port;
	errstr = (char *)malloc(errstr_len);
	stored_r_file = create_random_file(file_size);
	retrieved_r_file = create_random_file(0);
	test_res = TEST_PASS;
	
	hc_init(malloc,free,realloc);
	hc_session_create_ez(test_vip, port, &session);
	hc_session_get_archive(session, &archive);

	// generate file
	assert(stored_r_file->bytes_read == 0 && stored_r_file->sha1_init == FALSE);
	if ((hcoa_store_ez(archive,
				 &read_from_random_data_generator,
				 stored_r_file,
				 &sys_rec,
				 &response_code,
				 errstr,errstr_len)) == HCERR_OK) {

		if (strcmp(data_source,BUFFER_TARGET) == 0) {
			init_random_file(retrieved_r_file);
			res = hcoa_retrieve_ez(archive,
					retrieve_data_to_rfile, retrieved_r_file,
					&(sys_rec.oid), &response_code, errstr,errstr_len);
			sha1_finish(retrieved_r_file->sha_context,retrieved_r_file->digest);
		} else {
			file_name = hc_tmpnam();
			res = hcoa_retrieve_ez_download_file(archive,
							     file_name,&(sys_rec.oid),&response_code,errstr,errstr_len);
			file_to_r_file(file_name,retrieved_r_file);
			unlink(file_name);
		}
	} else {
		test_res = TEST_FAIL;
		hc_test_log(LOG_ERROR_LEVEL,env,"store failed.\n");
	}
	if (( compare_rfiles(stored_r_file,retrieved_r_file)) != TRUE ) {
		test_res = TEST_FAIL;
		hc_test_log(LOG_ERROR_LEVEL,env,"Stored file does not match recieved file.\n");
	}
	hc_session_free(session);
	free_r_file(retrieved_r_file);
	free_r_file(stored_r_file);
	free(errstr);
	return test_res;	
}
