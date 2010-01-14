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
#include <time.h>
#include "platform.h"
#include "hcoa.h"
#include "hcnvoa.h"
#include "hcoaez.h"
#include "hcclient.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

#define DATE_NAME "system.test.type_date"
#define TIME_NAME "system.test.type_time"
#define TIMESTAMP_NAME "system.test.type_timestamp"
#define BINARY_NAME "system.test.type_binary"
#define BINARY_SIZE 16

static char *ez_storertrvmdnew_tags[] = {"capi","capiez_storertrvmdnew"};
int hcez_storertrvmdnew_simpletest_exec(test_case_t *);


void hcez_storertrvmdnew_load_tests(hctest_env_t *test_env) {
	char *argv[] = {"0"};
	add_test_to_list(create_and_tag_test_case("capiez_storertrvmdnew",
		  1, argv,
		  (int (*)(test_case_t*))hcez_storertrvmdnew_simpletest_exec,
		  ez_storertrvmdnew_tags,2,test_env),
		test_env,FALSE);
}

int hcez_storertrvmdnew_simpletest_exec(test_case_t *tc) {
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
	hc_value_t value = HC_EMPTY_VALUE_INIT;
	hc_long_t long_return;
	int i, count = 0;
	time_t t, t1, t2;
        struct timespec ts, ts2;
        struct tm *date, date2;
	unsigned char *binary, *binary2 = NULL;

	argv = tc->argv;
	expected_res = atoi(argv[0]);
	test_res = TEST_PASS;
	stored_r_file  = create_random_file(16); /* small is good */

	/* Initialize test metadata values */
	time(&t);
	t1 = t % 86400;
        ts.tv_sec = t;
	ts.tv_nsec = 123000000;
        date = gmtime(&t);
	binary = (unsigned char *) malloc(BINARY_SIZE);
	for (i=0; i<BINARY_SIZE; i++)
		binary[i] = 'a' + i;

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

	res = hc_nvr_create(session, 4, &nvr); 
        res = hc_nvr_add_date(nvr, DATE_NAME, date); // days
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_date failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}

	res = hc_nvr_add_time(nvr, TIME_NAME, t1); // seconds
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_time: %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	res = hc_nvr_add_timestamp(nvr, TIMESTAMP_NAME, &ts); // millis
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_timestamp: %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	res = hc_nvr_add_binary(nvr, BINARY_NAME, BINARY_SIZE, binary);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_binary: %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	res = hc_store_both_ez(session, read_from_random_data_generator, 
				stored_r_file, nvr, &sys_rec);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"Store of file failed: %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	} 
	if (compare_rfile_to_sys_rec(stored_r_file, &sys_rec) != TRUE) {
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

	
	res = hc_retrieve_metadata_ez(session, &sys_rec.oid, &nvr);
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
	res = hc_nvr_get_date(nvr, DATE_NAME, &date2); 
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_get_date: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (date->tm_year != date2.tm_year ||
	    date->tm_mon != date2.tm_mon ||
	    date->tm_mday != date2.tm_mday) {
		hc_test_log(LOG_ERROR_LEVEL, test_env, 
			"hc_nvr_get_date: got %d-%d-%d expected %d-%d-%d\n",
			    date2.tm_year, date2.tm_mon, date2.tm_mday,
			    date->tm_year, date->tm_mon, date->tm_mday);
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_get_time(nvr, TIME_NAME, &t2);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_time: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (t2 != t1) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_time: got %d expected %d\n",
			t2, t1);
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_get_timestamp(nvr, TIMESTAMP_NAME, &ts2);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_timestamp: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (ts.tv_sec != ts2.tv_sec  ||
	    ts.tv_nsec != ts2.tv_nsec) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_timestamp: got %ld:%d expected %ld:%d\n",
			(long)ts2.tv_sec, ts2.tv_nsec,
			(long)ts.tv_sec, ts.tv_nsec);
		test_res = TEST_FAIL;
		goto done;
	}
	res = hc_nvr_get_binary(nvr, BINARY_NAME, &count, &binary2);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_binary: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	}
	if (count != BINARY_SIZE) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_binary: size %d expected %d\n",
			count, BINARY_SIZE);
		test_res = TEST_FAIL;
		goto done;
	}
	if (binary2 == NULL) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_binary: bytes NULL\n");
		test_res = TEST_FAIL;
		goto done;
	}
	if (memcmp(binary, binary2, BINARY_SIZE)) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_get_binary: bytes differ\n");
		test_res = TEST_FAIL;
		goto done;
	}
done:
	free(binary);
	if (nvr != NULL)
		hc_nvr_free(nvr);
	if (stored_r_file != NULL)
		free_r_file(stored_r_file);
	hc_session_free(session);
	return test_res;
}

