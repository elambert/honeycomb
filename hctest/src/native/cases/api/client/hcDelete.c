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
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "hcclient.h"
#include "sha1.h"

#define TEST_FILESIZE 4096

int hc_delete_simpletest_exec(test_case_t *);
char *delete_tags[] = {"capi","capi_del"};


void hc_delete_load_tests(hctest_env_t *test_env) {
  char *argv[] = {"0"};
  add_test_to_list(create_and_tag_test_case("capi_del_simpledel",
					    1, argv,
					    (int (*)(test_case_t*))hc_delete_simpletest_exec,delete_tags,2,test_env),test_env,FALSE);

}


int hc_delete_simpletest_exec(test_case_t *tc) {	
	
	int res, test_res, port, expected_res;
	char * test_vip;
	hc_system_record_t sys_rec;
	hc_oid * oid;
	char **argv;
	hc_random_file_t * retrieved_r_file = NULL;
	hc_session_t *sessionp;
	hctest_env_t *env;
	
	env = tc->test_env;
	argv = tc->argv;
	test_vip=env->data_vip;
	port=env->data_vip_port;
	test_res = TEST_PASS;
	expected_res = atoi(argv[0]);
	if (store_tmp_data_file(test_vip,port,1024,&sys_rec) != TRUE)	 {
		hc_test_log(LOG_ERROR_LEVEL,env,"Unable to store file needed by delete test to %s, %d\n",test_vip,port);
		return TEST_ERROR;
	}
	oid = &(sys_rec.oid);
	hc_test_log(LOG_DEBUG_LEVEL, NULL,"delete simple test successfully stored a file. OID is %s\n", oid);

	if (hc_session_create_ez(test_vip,port,&sessionp) != HCERR_OK) {
		hc_test_log(LOG_INFO_LEVEL, NULL, "Unable to create session to %s:%d\n", test_vip,port);
		return TEST_FAIL;
	}
	
	res = hc_delete_ez(sessionp,oid);
	if (res != expected_res) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,"Result code mismatch. Expected %s, got %s \n", hc_decode_hcerr(expected_res), hc_decode_hcerr(res));
		log_session_error_info(sessionp,res);
		test_res = TEST_FAIL;
	}
	
	//try and retrieve it
	if (test_res != TEST_FAIL) {
		retrieved_r_file = create_random_file(TEST_FILESIZE);
		init_random_file(retrieved_r_file);
		res = hc_retrieve_ez(sessionp,
				     retrieve_data_to_rfile,
				     (void *)retrieved_r_file,oid);
		if (res == HCERR_OK) {
			sha1_finish(retrieved_r_file->sha_context,retrieved_r_file->digest);
			hc_test_log(LOG_ERROR_LEVEL,NULL,"Was able to retrieve oid %s after deleting it! \n", oid);
			test_res = TEST_FAIL;
		}
	}

	// try and delete it again
	if (test_res != TEST_FAIL) {
		res = hc_delete_ez(sessionp,oid);	
		if (res == HCERR_OK) {
			hc_test_log(LOG_ERROR_LEVEL,NULL,"Attempt to delete the same OID %s twice resulted in HCERR_OK\n");
			test_res = TEST_FAIL;
		}	
	}
	if (retrieved_r_file != NULL) {
	  free_r_file(retrieved_r_file);
	}
	hc_session_free(sessionp);
	return test_res;
}
