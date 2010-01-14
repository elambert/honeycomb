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
#include <errno.h>
#include "platform.h"
#include "hcoa.h"
#include "hcnvoa.h"
#include "hcoaez.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

static char *ez_tags[] = {"capiez_queryusage"};
int hcez_querypstmt_simpletest_exec(test_case_t *);

extern char *progname;

void hcez_querypstmt_load_tests(hctest_env_t *test_env) {
	char **file_size_ptr = NULL;
	char **cur_file_size = NULL;
	char *max_file_size = NULL;
	int number_of_file_sizes;
	int i,j;
	char *argv[1];

	argv[0] = "0";
	add_test_to_list(create_and_tag_test_case("capiez_querypstmt",
		       	1, argv,
			(int (*)(test_case_t*))hcez_querypstmt_simpletest_exec,
			ez_tags,1,test_env),test_env,FALSE);
}

static long read_from_file(void*, char*, long);

int hcez_querypstmt_simpletest_exec(test_case_t *tc) {

	hctest_env_t *env;
        int port;
        char *host;
        int c, fd, fdout;
	int i=0;
	hc_long_t schema_count = 0;
	hc_type_t type;
	hc_long_t countp;
	hc_long_t nslots=10000;
        hc_session_t *session = NULL;
	hc_nvr_t *nvr = NULL;
	hc_nvr_t *ret_nvr = NULL;
	hc_pstmt_t *pstmt = NULL;
	hc_system_record_t system_record;
	hc_long_t long_value = 10000;
	hc_double_t double_value = 12345.6789;
	hc_string_t string_value = "?????? ???? ????????????";
	hc_string_t char_value = "latin-1 otnay igpay atinlay";
	unsigned char binary_value[10];
	int bin_len;
	unsigned char *binary_return;
	struct timespec timestamp_value, timestamp_return;
	struct tm date_value, date_return;
	time_t now, time_value, time_return;
	char string_large[1024];
	char query_string[1024];
	hc_long_t long_return = 0;
	hc_long_t firstbyte = 0;
	hc_long_t lastbyte = 0;
        hc_double_t double_return = 0;
        hc_string_t string_return;
	hc_long_t nvr_count = 0;
	hc_query_result_set_t *resp = NULL;
	hc_value_t valuep;
	hc_long_t count = 0;
	char *namep = NULL;
        char *selects[] = {"longsmall"};
	char *fields[] = {	"system.test.type_char",
				"system.test.type_string",
				"system.test.type_long",
				"system.test.type_double",
				"system.test.type_binary",
				"system.test.type_date",
				"system.test.type_time",
				"system.test.type_timestamp" };
        int n_selects = 1;
	int finished = 0;
	hc_oid returnOID;

        hcerr_t reth;
	int32_t response;
	char *errstr;
        int test_result=TEST_PASS;

        env=tc->test_env;
	port = env->data_vip_port;
        host = env->data_vip;
printf("host %s  port %d\n", host, port);
	/* hc_init has already been called, but we follow the
	   other examples */
	hc_cleanup();
	hc_init(malloc,free,realloc);

	printf("create session\n");
	if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_create_ez returned %s\n", 
                   	hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}

	/* Store some Data and Metadata */
	printf("nvr create/add\n");
	if ((reth = hc_nvr_create(session, nslots, &nvr)) != HCERR_OK) {
		printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		exit(1);
	}
	time(&now);
	long_value = now;
	if ((reth = hc_nvr_add_long(nvr, "system.test.type_long", long_value)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_add_long returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
        if ((reth = hc_nvr_add_double(nvr, "system.test.type_double", 
						double_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_add_double returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
        if ((reth = hc_nvr_add_string(nvr, "system.test.type_string", 
						string_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_add_string returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
//        if ((reth = hc_nvr_add_char(nvr, "system.test.type_char", 
//						char_value)) != HCERR_OK) {
        if ((reth = hc_nvr_add_string(nvr, "system.test.type_char", 
						char_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_add_string/char returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	for (i=0; i<10; i++)
		binary_value[i] = i;
	if ((reth = hc_nvr_add_binary(nvr, "system.test.type_binary", 10,
						binary_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_add_binary returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_nvr_add_date(nvr, "system.test.type_date",
						gmtime(&now))) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_add_date returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	time_value = now % 86400;
	if ((reth = hc_nvr_add_time(nvr, "system.test.type_time",
						time_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_add_time returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	timestamp_value.tv_sec = now;
	timestamp_value.tv_nsec = 123000000;
	if ((reth = hc_nvr_add_timestamp(nvr, "system.test.type_timestamp",
					&timestamp_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_add_timestamp returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}

	printf("store %s\n", progname);
	if ((fd=open(progname, O_RDONLY)) < 0) {
		perror(progname);
		exit(1);
	}
	if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, nvr, 
						&system_record)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_store_both_ez returned %s\n", 
			hc_decode_hcerr(reth));
		log_session_error_info(session, reth);
		test_result = TEST_FAIL;
		goto done;
	}
	close(fd);
	printf("Make pstmt for valid query\n");
	query_string[0] = '\0';
	for (i=0; i<8; i++) {
		strcat(query_string, fields[i]);
		strcat(query_string, " = ?");
		if (i < 7)
			strcat(query_string, " AND ");
	}
	if ((reth = hc_pstmt_create(session, query_string, &pstmt)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_create returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	// add fields in different order than query
	if ((reth = hc_pstmt_set_binary(pstmt, 5, binary_value, 10))
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_binary returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_pstmt_set_timestamp(pstmt, 8, &timestamp_value))
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_timestamp returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_pstmt_set_time(pstmt, 7, time_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_time returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	memcpy(&date_value, gmtime(&now), sizeof(date_value));
	// 0 unused vars for later comparison
	date_value.tm_sec = 0;
	date_value.tm_min = 0;
	date_value.tm_hour = 0;
	date_value.tm_wday = 0;
	date_value.tm_yday = 0;
	date_value.tm_isdst = 0;
	if ((reth = hc_pstmt_set_date(pstmt, 6, &date_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_date returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_pstmt_set_long(pstmt, 3, long_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_long returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_pstmt_set_double(pstmt, 4, double_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_double returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_pstmt_set_string(pstmt, 1, char_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_string (for char value) returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_pstmt_set_string(pstmt, 2, string_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_set_string returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	printf("Test valid query\n");
	if ((reth = hc_pstmt_query_ez(pstmt, NULL, 0, 100, &resp)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_query_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_qrs_next_ez(resp, &returnOID, NULL, &finished)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_next_ez returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (finished != 0) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
		    "hc_qrs_next_ez didn't find any records; finished = %d\n", 
		    finished);
                printf("expected %s\n", system_record.oid);
		hc_test_log(LOG_ERROR_LEVEL,NULL,
		    "hc_qrs_free returns %s\n",
		    hc_decode_hcerr(hc_qrs_free(resp)));
		test_result = TEST_FAIL;
		resp = NULL;
		goto done;
	}
	if ((reth = hc_qrs_free(resp)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		resp = NULL;
		goto done;
        }
	resp = NULL;

	printf("Test valid query plus\n");
	if ((reth = hc_pstmt_query_ez(pstmt, fields, 8, 100, &resp)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_pstmt_query_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_qrs_next_ez(resp, &returnOID, &ret_nvr, &finished)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_next_ez returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (finished != 0) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
		    "hc_qrs_next_ez didn't find any records; finished = %d\n", 
		    finished);
		test_result = TEST_FAIL;
		goto done;
	}

	printf("Check query result\n");
	if ((reth = hc_nvr_get_long(ret_nvr, "system.test.type_long", 
						&long_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_get_long returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (long_return != long_value) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"value returned by nvr_get_long incorrect\n");
		test_result = TEST_FAIL;
		goto done;
	}
        if ((reth = hc_nvr_get_double(ret_nvr, "system.test.type_double", 
						&double_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_get_double returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
	if (double_return != double_value) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"value returned by nvr_get_double incorrect\n");
		test_result = TEST_FAIL;
		goto done;
	}
        if ((reth = hc_nvr_get_string(ret_nvr, "system.test.type_string", 
						&string_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_get_string returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
	if (strcmp(string_return, string_value) != 0) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"String returned by hc_nvr_get_string incorrect.\n");
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_nvr_get_binary(ret_nvr, "system.test.type_binary",
				&bin_len, &binary_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_get_binary returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (bin_len != 10) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"Length returned by hc_nvr_get_binary incorrect.\n");
		test_result = TEST_FAIL;
		goto done;
	}
	if (memcmp(binary_value, binary_return, 10) != 0) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"Bytes returned by hc_nvr_get_binary incorrect.\n");
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_nvr_get_date(ret_nvr, "system.test.type_date",
						&date_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_get_date returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
        if (date_return.tm_sec != date_value.tm_sec ||
            date_return.tm_min != date_value.tm_min ||
            date_return.tm_hour != date_value.tm_hour ||
            date_return.tm_wday != date_value.tm_wday ||
            date_return.tm_yday != date_value.tm_yday ||
            date_return.tm_isdst != date_value.tm_isdst) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"Date returned by hc_nvr_get_date incorrect.\n");
		printf("set: %s", asctime(&date_value));
		printf("got: %s", asctime(&date_return));
                printf("wday %d/%d yday %d/%d isdst %d/%d\n",
	               date_value.tm_wday, date_return.tm_wday,
	               date_value.tm_yday, date_return.tm_yday,
	               date_value.tm_isdst, date_return.tm_isdst);
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_nvr_get_time(ret_nvr, "system.test.type_time",
						&time_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_get_time returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (time_value != time_return) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"Time returned by hc_nvr_get_time incorrect.\n");
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_nvr_get_timestamp(ret_nvr, "system.test.type_timestamp",
					&timestamp_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_get_timestamp returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (memcmp(&timestamp_value, &timestamp_return, sizeof(timestamp_value))
									!= 0) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"Timestamp returned by hc_nvr_get_timestamp wrong.\n");
		printf("exp: %ld", timestamp_value.tv_sec);
		printf(" %d\n", timestamp_value.tv_nsec);
		printf("got: %ld", timestamp_return.tv_sec);
		printf(" %d\n", timestamp_return.tv_nsec);
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_qrs_free(resp)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		resp = NULL;
		goto done;
        }
	resp = NULL;

done:

	if (resp != NULL) {
		hc_qrs_free(resp);
	}
	if (pstmt != NULL) {
		hc_pstmt_free(pstmt);
	}
	if (nvr != NULL) {
		hc_nvr_free(nvr);
	}
	if (ret_nvr != NULL) {
		hc_nvr_free(ret_nvr);
	}
	if ((reth = hc_session_free(session)) != HCERR_OK) {
		printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
	}
	hc_cleanup();
	if (test_result == TEST_FAIL) {
		printf("test_query_pstmt FAILED\n");
	} else {
		printf("test_query_pstmt PASSED\n");
	}

	return test_result;
}


static long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

