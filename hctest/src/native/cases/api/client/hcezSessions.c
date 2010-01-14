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

static char *ez_tags[] = {"capiez_sessions"};
int hcez_sessions_simpletest_exec(test_case_t *);

void hcez_sessions_load_tests(hctest_env_t *test_env) {
	char **file_size_ptr = NULL;
	char **cur_file_size = NULL;
	char *max_file_size = NULL;
	int number_of_file_sizes;
	int i,j;
	char *argv[1];

	argv[0] = "0";
	add_test_to_list(create_and_tag_test_case("capiez_sessions",
		       	1, argv,
			(int (*)(test_case_t*))hcez_sessions_simpletest_exec,
			ez_tags,1,test_env),test_env,FALSE);
}

int hcez_sessions_simpletest_exec(test_case_t *tc) {

	hctest_env_t *env;
        int port;
        char *host;
        int test_result=TEST_PASS;
	int c;
	hc_session_t *session = NULL;
	hcerr_t reth;
	int32_t response;
	char *errstr;

        env=tc->test_env;
	port = env->data_vip_port;
        host = env->data_vip;
printf("host %s  port %d\n", host, port);

/* 	Try to free session pointer without ever doing hc_session_create */
	hc_cleanup(); /* undo hc_init() */
        if ((reth = hc_session_free(session)) != HCERR_NULL_SESSION) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_session_free(NULL) returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
        /* Try to create session without first doing hc_init
	   Should do implicit hc_init then create session */
        if ((reth = hc_session_create_ez (host, port, &session)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_create_ez after hc_cleanup returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	/* free session then try to status it */
	if ((reth = hc_session_free(session)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_session_get_status (session, &response, &errstr)) == 
	  							HCERR_OK) {
		if (response == 200) {
			hc_test_log(LOG_ERROR_LEVEL,NULL,
		  		"hc_session_get_status returned HCERR_OK and response = 200 on a freed session\n", hc_decode_hcerr(reth));
			test_result = TEST_FAIL;
			goto done;
		}
	}
	
	/* try to create session with incorrect port number */
        if ((reth = hc_session_create_ez(host, port+1, &session)) == HCERR_OK){
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_create_ez returned %s w/ bad port\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
	/* Free any session that may have been created */
	hc_session_free(session);

/* Create Session and free it*/
	/* if (reth = (hc_init(malloc, free, realloc)) != HCERR_OK) {
		printf("hc_init returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	} */
	if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_create_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_session_get_host(session, &host, &port)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_get_host returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	printf("hc_session_get_host returned host=%s port=%d\n", host, port);

	if ((reth = hc_session_get_status(session, &response, &errstr)) != 
	  							HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_get_status returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	/* ?? is this an error ?? */
	if (response != 200) {
		printf("hc_session_get_status returned response=%d errstr=%s\n",
			response, errstr);
	}
	if ((reth = hc_session_free(session)) !=HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
done:

	hc_cleanup();
	if (test_result == TEST_FAIL) {
		printf("test_sessions FAILED\n");
	} else {
		printf("test_sessions PASSED\n");
	}

	return test_result;
}

