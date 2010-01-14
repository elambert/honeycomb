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
#include "hcclient.h"

static char *ez_tags[] = {"capiez_compliance"};
int hcez_compliance_simpletest_exec(test_case_t *);

/********************************************************
 *
 * Bug 6554027 - hide retention features
 *
 *******************************************************
/*
void hcez_compliance_load_tests(hctest_env_t *test_env) {
  char **file_size_ptr = NULL;
  char **cur_file_size = NULL;
  char *max_file_size = NULL;
  int number_of_file_sizes;
  int i,j;
  char *argv[2];

  argv[0] = "0";
  argv[1] = "100";
  // segfaults here on linux:
  add_test_to_list(create_and_tag_test_case("capiez_compliance_simpletest",
             2, argv,
      (int (*)(test_case_t*))hcez_compliance_simpletest_exec,
      ez_tags,2,test_env),test_env,FALSE);
}

int hcez_compliance_simpletest_exec(test_case_t *tc) {
	char *test_vip;
	char *test_vip_port;
	hc_session_t *session;
	hc_archive_t *archive;
	hc_system_record_t sys_rec;
	hcerr_t res;  

    hc_nvr_t* nvr;
    char *names[] = { "stringnull" };
    char *values[] = { "Quux" };

	struct timeval tp;
	int32_t response_code;
	char * errstr, ** argv;
	int port, errstr_len = 4096;
	hc_long_t file_size;
	hcerr_t expected_res;
	hctest_env_t *env;
	hc_test_result_t test_res;
	hc_random_file_t * r_file;

	argv = tc->argv;
	expected_res = atoi(argv[0]);
	file_size = get_file_size(argv[1]);

	env=tc->test_env;
	hc_init(malloc,free,realloc);
	test_vip = env->data_vip;
	port = env->data_vip_port;
	errstr = (char *)malloc(errstr_len);
	r_file = create_random_file(file_size);
	test_res = TEST_PASS;

    printf ("creating session\n");
	hc_session_create_ez (test_vip, port, &session);
    printf ("creating archive\n");
	hc_session_get_archive (session, &archive);

    printf ("creating name/value record\n");
    res = hc_nvr_create_from_string_arrays (session, &nvr, names, values, 1);
    if (res != HCERR_OK) {
			hc_test_log(LOG_ERROR_LEVEL,NULL, 
                "failed to create nvr: %s",
                hc_decode_hcerr(res));
                test_res = TEST_FAIL;
                goto done;
    }
    
    printf ("storing data/metadata\n");
    res = hc_store_both_ez(session, 
				             read_from_random_data_generator, 
                             (void *)r_file, 
                             nvr,
				             &sys_rec);
	
	if (res != expected_res) {
			hc_test_log(LOG_ERROR_LEVEL,NULL, 
			"Result mismatch. Expected %s, got %s.\n",
			hc_decode_hcerr(expected_res),hc_decode_hcerr(res));
			test_res =  TEST_FAIL;
            goto done;
	}
	if(res == HCERR_OK && compare_rfile_to_sys_rec(r_file,&sys_rec) != TRUE) {
			hc_test_log(LOG_ERROR_LEVEL,NULL, "Stored file and generated file did not match.\n");
			test_res = TEST_FAIL;
            goto done;
	}
   
    printf ("testing set retention");
    gettimeofday(&tp, NULL);
    res = hcoa_set_retention_date_ez (archive,
                                      (hc_oid *)sys_rec.oid,
                                      (tp.tv_sec*36000), 
                                      &response_code,
                                      errstr, errstr_len);
    if (res != HCERR_OK) {
			hc_test_log(LOG_ERROR_LEVEL,NULL, 
			"Result mismatch. Expected %s, got %s.\n",
			hc_decode_hcerr(HCERR_OK),hc_decode_hcerr(res));
			test_res =  TEST_FAIL;
            goto done;
    }

    
done:
	hc_session_free(session);
	free_r_file(r_file);
	free(errstr);
	return test_res;
}
*/
