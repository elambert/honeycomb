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

#define BUFFER_SOURCE "buffer"
#define FILE_SOURCE "file"


char *ez_storedata_tags[] = {"capi","capiez_storedata"};
int hcnb_storedata_simpletest_exec(test_case_t *);
char * data_source_types [] = {BUFFER_SOURCE,FILE_SOURCE};


void hcez_storedata_load_tests(hctest_env_t *test_env) {
	char **file_size_ptr = NULL;
	char **cur_file_size = NULL;
	char *max_file_size = NULL;
	int number_of_file_sizes;
	int i,j;
	char *argv[3];

	max_file_size = hash_get_value(CONTEXT_KEY_MAX_FILE_SIZE,test_env->context_hash);
	if (max_file_size == NULL) {
		max_file_size = HCFILE_SIZE_TYPE_MAX;
	}
	number_of_file_sizes = generate_absolute_file_size_list(&file_size_ptr,get_file_size(max_file_size));
	
	for (i = 0; i < 2; i++) {
		cur_file_size = file_size_ptr;
		for (j = 0; j < number_of_file_sizes; j++) {
		  argv[0] = "0";
		  argv[1] = *cur_file_size;
		  argv[2] = data_source_types[i];
		  add_test_to_list(create_and_tag_test_case("capiez_storedata_simplestore",
			       	3, argv,
				(int (*)(test_case_t*))hcnb_storedata_simpletest_exec,ez_storedata_tags,2,test_env),test_env,FALSE);
		  cur_file_size++;
		}
	}

}


int hcnb_storedata_simpletest_exec(test_case_t *tc) {	

	char * test_vip;
	char * test_vip_port;
	hc_system_record_t sys_rec;
	int32_t response_code;
	char * errstr, ** argv, *data_source,*file_name;
	int port, errstr_len = 4096;
	hc_long_t file_size;
	hcerr_t expected_res;
	hctest_env_t *env;
	hc_session_t *session;
	hc_archive_t *archive;
	hc_test_result_t test_res;
	hcerr_t res;  
	hc_random_file_t * r_file;
	
	argv = tc->argv;
	expected_res = atoi(argv[0]);
	file_size = get_file_size(argv[1]);
   	data_source = argv[2];

	env=tc->test_env;
	hc_init(malloc,free,realloc);
	test_vip = env->data_vip;
	port = env->data_vip_port;
	errstr = (char *)malloc(errstr_len);
	r_file = create_random_file(file_size);
	test_res = TEST_PASS;
	hc_session_create_ez (test_vip, port, &session);
	hc_session_get_archive (session, &archive);
	
	
	if (strcmp(data_source,BUFFER_SOURCE) == 0) {
		res = hcoa_store_ez(archive, 
				    read_from_random_data_generator,
				    r_file,&sys_rec,&response_code,errstr,errstr_len);
	} else { 
		file_name = hc_tmpnam();
		if ((write_random_data_to_file(r_file,file_name)) == -1) { 
			hc_test_log(LOG_ERROR_LEVEL,NULL,"An error occurred while writing tmp file\n");
			res=HCERR_IO_ERR;
		} else {
 			res = hcoa_store_ez_upload_file(archive, file_name,&sys_rec,
							&response_code,errstr,errstr_len);
			remove(file_name);
		} 
	}
	if (res != expected_res) {
			hc_test_log(LOG_ERROR_LEVEL,NULL, 
			"Result mismatch. Expected %s, got %s.\n",
			hc_decode_hcerr(expected_res),hc_decode_hcerr(res));
			test_res =  TEST_FAIL;
	}
	if(res == HCERR_OK && compare_rfile_to_sys_rec(r_file,&sys_rec) != TRUE) {
			hc_test_log(LOG_ERROR_LEVEL,NULL, "Stored file and generated file did not match.\n");
			test_res = TEST_FAIL;
	}
	hc_session_free(session);
	free_r_file(r_file);
	free(errstr);
	return test_res;

}
