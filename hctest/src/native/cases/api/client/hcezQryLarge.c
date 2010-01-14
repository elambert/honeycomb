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

int NUM_RESULTS = 11;
int FETCH_SIZE = 10;

static char *ez_tags[] = {"capiez_querylarge"};
int hcez_querylarge_simpletest_exec(test_case_t *);

extern char *progname;

void hcez_querylarge_load_tests(hctest_env_t *test_env) {
	char **file_size_ptr = NULL;
	char **cur_file_size = NULL;
	char *max_file_size = NULL;
	int number_of_file_sizes;
	int i,j;
	char *argv[1];

	argv[0] = "0";
	add_test_to_list(create_and_tag_test_case("capiez_querylarge",
		       	1, argv,
			(int (*)(test_case_t*))hcez_querylarge_simpletest_exec,
			ez_tags,1,test_env),test_env,FALSE);
}

static long read_from_file(void*, char*, long);

int hcez_querylarge_simpletest_exec(test_case_t *tc) {

	hctest_env_t *env;
        int port;
        char *host;
        int c, fd, fdout;
	int i=0;
        hc_session_t *session = NULL;
	hc_nvr_t *nvr = NULL;
	hc_system_record_t system_record;
	hc_long_t long_value;
	hc_query_result_set_t *resp = NULL;
        char *selects[] = {"longsmall"};
        int n_selects = 1;
	int finished = 0;
	hc_oid returnOID;
	time_t start, end;

        hcerr_t reth;
        int test_result=TEST_PASS;

        env=tc->test_env;
	port = env->data_vip_port;
        host = env->data_vip;

	/* hc_init has already been called, but we follow the
	   other examples */
	hc_init(malloc,free,realloc);

	printf("create session\n");
	if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_create_ez returned %s\n", 
                   	hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}

	printf("Store a file (%s)\n", progname);

        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_nvr_create(session, 1, &nvr)) != HCERR_OK) {
                printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }

        if ((reth=hc_store_both_ez(session, &read_from_file, (void *)fd, nvr,
                			&system_record)) != HCERR_OK) {
		printf("hc_store_both_ez returned %s\n", 
			  hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
	printf("Start inserting metadata - takes a while\n");
	time(&start);
	end = start + NUM_RESULTS;
	for (long_value=start; long_value<end; long_value++) {
		if ((reth = hc_nvr_add_long(nvr, "longsmall", long_value)) 
								!= HCERR_OK) {
			hc_test_log(LOG_ERROR_LEVEL,NULL,
                		"hc_nvr_add_long returned %s\n", 
				hc_decode_hcerr(reth));
			test_result = TEST_FAIL;
			goto done;
        	}
		if ((reth = hc_store_metadata_ez(session, &system_record.oid,
		 			nvr, &system_record)) != HCERR_OK) {
			hc_test_log(LOG_ERROR_LEVEL,NULL,
                        	"hc_store_metadata_ez returned %s\n", 
				hc_decode_hcerr(reth));
			test_result = TEST_FAIL;
			goto done;
		}
        }
	if (hc_session_free(session) !=0) {
                perror("hc_session_free");
                exit(1);
        }
	printf("Start retrieving results - takes a while\n");
	/* First with query plus */
        if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK) {
		printf("hc_session_create_ez returned %s\n",
				hc_decode_hcerr(reth));
		exit(1);
	}
	if ((reth = hc_query_ez(session, "longsmall>0", selects, n_selects, 
							FETCH_SIZE,
							&resp)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_query_plus_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
	for(long_value=0; long_value<NUM_RESULTS; long_value++) {
		if ((reth = hc_qrs_next_ez(resp, &returnOID, &nvr, &finished))
								!= HCERR_OK) {
			hc_test_log(LOG_ERROR_LEVEL,NULL,
			  	"hc_qrs_next_ez returned %s\n",
			  	hc_decode_hcerr(reth));
			test_result = TEST_FAIL;
			goto done;
		}
		if(finished != 0) {
			hc_test_log(LOG_ERROR_LEVEL,NULL,
				"hc_qrs_next_ez didn't find enough records - %d found\n",
				long_value);
			test_result = TEST_FAIL;
			goto done;
		}
	}
	if ((reth=hc_qrs_free(resp)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
		  	"hc_qrs_free returned %s\n",
		  	hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		resp = NULL;
		goto done;
	}
	resp = NULL;

done:
/*
hc_qrs_free causes a core dump in curl if session is freed 1st 
when "hc_qrs_next_ez didn't find enough records - 0 found"
See 6507353
*/
	if (resp != NULL)
		hc_qrs_free(resp);
        if ((reth = hc_session_free(session)) != HCERR_OK)
                printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
	if (nvr != NULL)
		hc_nvr_free(nvr);
	if (test_result != TEST_PASS) {
                printf("test_query_large FAILED\n");
        } else {
                printf("test_query_large PASSED\n");
        }
	return test_result;
}


static long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

