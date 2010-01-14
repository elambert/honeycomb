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
#include "hcclient.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

#define DOUBLESMALL_VAL 1.23456
#define LONGLARGE_VAL   123456
#define WORDLENGTH_VAL  987654

static char *ez_storertrvmd_tags[] = {"capi","capiez_storertrvmd"};
int hcez_storertrvmd_simpletest_exec(test_case_t *);


void hcez_storertrvmd_load_tests(hctest_env_t *test_env) {
  char *argv[] = {"0"};
	add_test_to_list(create_and_tag_test_case("capiez_storertrvmd",
						  1, argv,
						  (int (*)(test_case_t*))hcez_storertrvmd_simpletest_exec,ez_storertrvmd_tags,2,test_env),test_env,FALSE);
}

int hcez_storertrvmd_simpletest_exec(test_case_t *tc) {
	char **argv = NULL;
	char *test_vip = NULL; 
	hcerr_t expected_res, res;
	hc_test_result_t test_res;
	int port;
	int32_t response_code;
	hc_system_record_t sys_rec, sys_rec2;
	hc_random_file_t *stored_r_file = NULL; 
	hctest_env_t *test_env;
	hc_nvr_t *nvr = NULL;
	hc_session_t *session;
	hc_oid oid;	
	char *md_str_names[] = {"third","fourth"};
	char *md_str_values[] = {"avalue","bvalue"};
	hc_value_t value = HC_EMPTY_VALUE_INIT;
	hc_string_t string_return;
	hc_double_t double_return;
	hc_long_t long_return;
	char **ret_names = NULL;
	char **ret_values = NULL;
	int i, count = 0;

	argv = tc->argv;
	expected_res = atoi(argv[0]);
	test_res = TEST_PASS;
	stored_r_file  = create_random_file(1012); /* small is good */

	test_env = tc->test_env;
	test_vip = test_env->data_vip;
	port = test_env->data_vip_port;
	
	hc_init(malloc,free,realloc);
	res = hc_session_create_ez(test_vip,port,&session);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"Session Create failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	res = hc_nvr_create_from_string_arrays(session, &nvr, 
						md_str_names, md_str_values, 2);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_create_from_string_arrays: %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	
	res = hc_store_both_ez(session, read_from_random_data_generator, 
				stored_r_file, nvr, &sys_rec);

	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"Store of file failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	} 
	if (compare_rfile_to_sys_rec(stored_r_file ,&sys_rec) != TRUE) {
		hc_test_log(LOG_ERROR_LEVEL,  test_env, 
			"An error occurred while storing test file.");
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_free(nvr);
	nvr = NULL;
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_free: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}

	/* recreate nvr for addmd */
	res = hc_nvr_create_from_string_arrays(session, &nvr, 
						md_str_names, md_str_values, 2);
	/* add more md */
        res = hc_nvr_add_double(nvr, "doublesmall", DOUBLESMALL_VAL);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_add_double: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_add_long(nvr, "longlarge", LONGLARGE_VAL);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_add_long: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	value.hcv_type = HC_LONG_TYPE;
	value.hcv.hcv_long = WORDLENGTH_VAL;
	res = hc_nvr_add_value(nvr, "wordlength", value);

	/* store the nvr linking to the original oid */
	res = hc_store_metadata_ez(session, &sys_rec.oid, nvr, &sys_rec2);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_store_metadata_ez: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_free(nvr);
	nvr = NULL;
	
	res = hc_retrieve_metadata_ez(session, &sys_rec2.oid, &nvr);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_retrieve_metadata_ez: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (nvr == NULL) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_retrieve_metadata_ez returned null nvr\n");
		test_res = TEST_FAIL;
		goto done;
	}
	for (i=0; i<2; i++) {
		if ((res = hc_nvr_get_string(nvr, md_str_names[i], &string_return)) 
								!= HCERR_OK) {
			hc_test_log(LOG_ERROR_LEVEL, test_env, 
				"hc_nvr_get_string: %s\n", 
				hc_decode_hcerr(res));
			test_res = TEST_FAIL;
			goto done;
		}
		if (strcmp(string_return, md_str_values[i])) {
			hc_test_log(LOG_ERROR_LEVEL, test_env, 
				"hc_nvr_get_string returned %s expected '%s'\n",
				string_return, md_str_values[i]);
			test_res = TEST_FAIL;
			goto done;
		}
	}
	res = hc_nvr_get_double(nvr, "doublesmall", &double_return);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_get_double: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (double_return != DOUBLESMALL_VAL) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_get_double: got %f expected %f\n",
			double_return, DOUBLESMALL_VAL);
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_get_long(nvr, "longlarge", &long_return);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_get_long: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (long_return != LONGLARGE_VAL) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_long: got %lld, expected %lld",
			long_return, LONGLARGE_VAL);
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_get_long(nvr, "wordlength", &long_return);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_get_long: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (long_return != WORDLENGTH_VAL) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_long: got %lld, expected %lld",
			long_return, WORDLENGTH_VAL);
		test_res = TEST_FAIL;
		goto done;
	}

done:
	if (nvr != NULL)
		hc_nvr_free(nvr);
	if (stored_r_file != NULL)
		free_r_file(stored_r_file);
	hc_session_free(session);
		
	return test_res;
}

