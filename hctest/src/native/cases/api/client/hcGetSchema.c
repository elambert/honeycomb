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
#include "hcclient.h"
#include "hcoaez.h"
#include "hc.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

char *hcez_getschema_tags[] = {"capi","capiez_getmd"};
int hcez_getschema_simpletest_exec (test_case_t *);

void hc_getschema_load_tests(hctest_env_t *test_env) {
  char *argv[] = {"0"};
	add_test_to_list(create_and_tag_test_case("capiez_getschema_simpletest",
			    		       	1, argv,
								(int (*)(test_case_t*))hcez_getschema_simpletest_exec,hcez_getschema_tags,2,test_env),test_env,FALSE);

}

int hcez_getschema_simpletest_exec (test_case_t *tc) {
	char **argv, *test_vip, *test_vip_port, *errstr;
	hcerr_t expected_res;
	int port, errstr_len; 
	int res = TEST_PASS;
	long response_code = -1;
	long count = -1;
	hctest_env_t *env;

	hc_session_t *session;
	env=tc->test_env;
	argv = tc->argv;
	errstr_len=4096;
	errstr = (char *)malloc(errstr_len);
	expected_res = atoi(argv[0]);
	test_vip = env->data_vip;
	port = env->data_vip_port;
	

	res = hc_session_create_ez(test_vip, port, &session);
	if (expected_res != res) {
		hc_test_log(LOG_ERROR_LEVEL,env,"result code mismatch. Expected %s, got %s\n", hc_decode_hcerr(expected_res),hc_decode_hcerr(res));
		res = TEST_FAIL;
	}
	//TODO: IMPLEMENT ME!!!!
	free(errstr);
	hc_session_free(session);
	return res;
	
}
