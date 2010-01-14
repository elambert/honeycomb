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

#define MAX_PER_CELL 5

#define LONG_NAME "system.test.type_long"
#define STRING_NAME "system.test.type_string"

// we declare this function here because it is "hidden" so not in .h
extern hcerr_t hc_store_both_cell_ez(hc_session_t *session,
                              int cellid,
                              read_from_data_source data_source_reader,
                              void *cookie,
                              hc_nvr_t *pubnvr,
                              hc_system_record_t *system_record);

static char *ez_mcellqry_tags[] = {"capi","capiez_mcellqry"};
int hcez_mcellqry_simpletest_exec(test_case_t *);


void hcez_mcellqry_load_tests(hctest_env_t *test_env) {
	char *argv[] = {"0"};
	add_test_to_list(create_and_tag_test_case("capiez_mcellqry",
		  1, argv,
		  (int (*)(test_case_t*))hcez_mcellqry_simpletest_exec,
		  ez_mcellqry_tags,2,test_env),
		test_env,FALSE);
}

int hcez_mcellqry_simpletest_exec(test_case_t *tc) {
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
	int i, j, nPerFetch, nStored, nGotten;
	time_t t, t1, t2;
	hc_hashlist_t *stored = NULL;
	char query[1024];
	hc_query_result_set_t *rset = NULL;
	int finished;

	argv = tc->argv;
	expected_res = atoi(argv[0]);
	test_res = TEST_PASS;

	test_env = tc->test_env;
        if (test_env->n_cells == 0) {
		printf("Multicell cells not defined: SKIPPING\n");
		return TEST_PASS;
        }
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

	// Initialize test metadata value 

	time(&t);

        res = hc_nvr_add_long(nvr, LONG_NAME, t);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_long failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	res = hc_nvr_add_string(nvr, STRING_NAME, "hcezMulticellQuery.c all");
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_string failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}

	sprintf(query, "%s=%d", LONG_NAME, t);

	// do stores

	printf("storing %d to each cell\n", MAX_PER_CELL);
	stored = create_hashlist(1024);
	nStored = 0;
	for (i=0; i<test_env->n_cells; i++) {
	    for (j=0; j<MAX_PER_CELL; j++) {
		
	        stored_r_file  = create_random_file(4); 

	        res = hc_store_both_cell_ez(session, 
                                test_env->cell_ids[i],
				read_from_random_data_generator, 
				stored_r_file, nvr, &sys_rec);
	        if (res != HCERR_OK) {
		    hc_test_log(LOG_ERROR_LEVEL, test_env,
			"Store of file to cell %d failed: %s\n",
	                test_env->cell_ids[i],
			hc_decode_hcerr(res));
		    test_res = TEST_ERROR;
		    goto done;
	        } 
	        hash_put(sys_rec.oid, "noval", stored);

	        if (j == 0)
	            printf("first in cell %d (hex %x): %s\n",
	                   test_env->cell_ids[i], test_env->cell_ids[i],
	                   sys_rec.oid);

		free_r_file(stored_r_file);
	        stored_r_file = NULL;
	        nStored++;
	    }
	}
	printf("Total stored %d\n", nStored);

	// do queries
	printf("Query: [%s]\n", query);
	for (nPerFetch=1; nPerFetch<MAX_PER_CELL+2; nPerFetch++) {
	    hc_hashlist_t *gotten = create_hashlist(1024);
	    printf("Fetch size = %d\n", nPerFetch);
	    nGotten = 0;

	    res = hc_query_ez(session, query, NULL, 0, nPerFetch, &rset);
	    if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "Query failed: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	    }
	    finished = 0;
	    while (1) {
	        hc_oid return_oid;
	        res = hc_qrs_next_ez(rset, &return_oid, NULL, &finished);
	        if (res != HCERR_OK) {
		    hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "hc_qrs_next_ez failed: %s\n", hc_decode_hcerr(res));
		    test_res = TEST_FAIL;
		    goto done;
	        }
	        if (finished)
	            break;
	        if (!hashlist_contains_key(return_oid, stored)) {
	            hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "Unexpected oid returned [%s]\n", return_oid);
		    test_res = TEST_FAIL;
		    goto done;
	        }
                //printf("got %s\n", return_oid);
	        if (hashlist_contains_key(return_oid, gotten)) {
	            hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "Duplicate oid returned (oid %d) [%s]\n", 
	                 nGotten, return_oid);
		    test_res = TEST_FAIL;
	        }
	        hash_put(return_oid, "noval", gotten);
	        nGotten++;
	    }
	    res = hc_qrs_free(rset);
	    if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "hc_qrs_free failed: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
	    }
	    free_hashlist(gotten);

	    if (nGotten != nStored) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "Stored %d but got %d\n", nStored, nGotten);
		test_res = TEST_FAIL;
	    }
	    if (test_res != TEST_PASS)
	        goto done;
	}
	free_hashlist(stored);
	stored = NULL;


	// Initialize test metadata value 

	time(&t);

        res = hc_nvr_add_long(nvr, LONG_NAME, t);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_long failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	res = hc_nvr_add_string(nvr, STRING_NAME, "hcezMulticellQuery.c odd");
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_string failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	sprintf(query, "%s=%d", LONG_NAME, t);

	// do stores

	printf("storing %d to odd cells\n", MAX_PER_CELL);
	stored = create_hashlist(1024);
	nStored = 0;
	for (i=0; i<test_env->n_cells; i++) {
	    if (i % 2 == 0)
	        continue;

	    for (j=0; j<MAX_PER_CELL; j++) {
		
	        stored_r_file  = create_random_file(4); 

	        res = hc_store_both_cell_ez(session, 
                                test_env->cell_ids[i],
				read_from_random_data_generator, 
				stored_r_file, nvr, &sys_rec);
	        if (res != HCERR_OK) {
		    hc_test_log(LOG_ERROR_LEVEL, test_env,
			"Store of file to cell %d failed: %s\n",
	                test_env->cell_ids[i],
			hc_decode_hcerr(res));
		    test_res = TEST_ERROR;
		    goto done;
	        } 
	        hash_put(sys_rec.oid, "noval", stored);

	        if (j == 0)
	            printf("first in cell %d (hex %x): %s\n",
	                   test_env->cell_ids[i], test_env->cell_ids[i],
	                   sys_rec.oid);

		free_r_file(stored_r_file);
	        stored_r_file = NULL;
	        nStored++;
	    }
	}
	printf("Total stored %d\n", nStored);

	// do queries
	printf("Query: [%s]\n", query);
	for (nPerFetch=1; nPerFetch<MAX_PER_CELL+2; nPerFetch++) {
	    hc_hashlist_t *gotten = create_hashlist(1024);
	    printf("Fetch size = %d\n", nPerFetch);
	    nGotten = 0;

	    res = hc_query_ez(session, query, NULL, 0, nPerFetch, &rset);
	    if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "Query failed: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	    }
	    finished = 0;
	    while (1) {
	        hc_oid return_oid;
	        res = hc_qrs_next_ez(rset, &return_oid, NULL, &finished);
	        if (res != HCERR_OK) {
		    hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "hc_qrs_next_ez failed: %s\n", hc_decode_hcerr(res));
		    test_res = TEST_FAIL;
		    goto done;
	        }
	        if (finished)
	            break;
	        if (!hashlist_contains_key(return_oid, stored)) {
	            hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "Unexpected oid returned [%s]\n", return_oid);
		    test_res = TEST_FAIL;
		    goto done;
	        }
                //printf("got %s\n", return_oid);
	        if (hashlist_contains_key(return_oid, gotten)) {
	            hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "Duplicate oid returned (oid %d) [%s]\n", 
	                 nGotten, return_oid);
		    test_res = TEST_FAIL;
	        }
	        hash_put(return_oid, "noval", gotten);
	        nGotten++;
	    }
	    free_hashlist(gotten);

	    res = hc_qrs_free(rset);
	    if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "hc_qrs_free failed: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
	    }
	    if (nGotten != nStored) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "Stored %d but got %d\n", nStored, nGotten);
		test_res = TEST_FAIL;
	    }
	    if (test_res != TEST_PASS)
	        goto done;
	}

	// Initialize test metadata value 

	time(&t);

        res = hc_nvr_add_long(nvr, LONG_NAME, t);
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_long failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	res = hc_nvr_add_string(nvr, STRING_NAME, "hcezMulticellQuery.c even");
	if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
			"hc_nvr_add_string failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		test_res = TEST_ERROR;
		goto done;
	}
	sprintf(query, "%s=%d", LONG_NAME, t);

	// do stores

	printf("storing %d to even cells\n", MAX_PER_CELL);
	stored = create_hashlist(1024);
	nStored = 0;
	for (i=0; i<test_env->n_cells; i++) {
	    if (i % 2 != 0)
	        continue;

	    for (j=0; j<MAX_PER_CELL; j++) {
		
	        stored_r_file  = create_random_file(4); 

	        res = hc_store_both_cell_ez(session, 
                                test_env->cell_ids[i],
				read_from_random_data_generator, 
				stored_r_file, nvr, &sys_rec);
	        if (res != HCERR_OK) {
		    hc_test_log(LOG_ERROR_LEVEL, test_env,
			"Store of file to cell %d failed: %s\n",
	                test_env->cell_ids[i],
			hc_decode_hcerr(res));
		    test_res = TEST_ERROR;
		    goto done;
	        } 
	        hash_put(sys_rec.oid, "noval", stored);

	        if (j == 0)
	            printf("first in cell %d (hex %x): %s\n",
	                   test_env->cell_ids[i], test_env->cell_ids[i],
	                   sys_rec.oid);

		free_r_file(stored_r_file);
	        stored_r_file = NULL;
	        nStored++;
	    }
	}
	printf("Total stored %d\n", nStored);

	// do queries
	printf("Query: [%s]\n", query);
	for (nPerFetch=1; nPerFetch<MAX_PER_CELL+2; nPerFetch++) {
	    hc_hashlist_t *gotten = create_hashlist(1024);
	    printf("Fetch size = %d\n", nPerFetch);
	    nGotten = 0;

	    res = hc_query_ez(session, query, NULL, 0, nPerFetch, &rset);
	    if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "Query failed: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
		goto done;
	    }
	    finished = 0;
	    while (1) {
	        hc_oid return_oid;
	        res = hc_qrs_next_ez(rset, &return_oid, NULL, &finished);
	        if (res != HCERR_OK) {
		    hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "hc_qrs_next_ez failed: %s\n", hc_decode_hcerr(res));
		    test_res = TEST_FAIL;
		    goto done;
	        }
	        if (finished)
	            break;
	        if (!hashlist_contains_key(return_oid, stored)) {
	            hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "Unexpected oid returned [%s]\n", return_oid);
		    test_res = TEST_FAIL;
		    goto done;
	        }
                //printf("got %s\n", return_oid);
	        if (hashlist_contains_key(return_oid, gotten)) {
	            hc_test_log(LOG_ERROR_LEVEL, test_env,
	                 "Duplicate oid returned (oid %d) [%s]\n", 
	                 nGotten, return_oid);
		    test_res = TEST_FAIL;
	        }
	        hash_put(return_oid, "noval", gotten);
	        nGotten++;
	    }
	    res = hc_qrs_free(rset);
	    if (res != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "hc_qrs_free failed: %s\n", hc_decode_hcerr(res));
		test_res = TEST_FAIL;
	    }
	    free_hashlist(gotten);

	    if (nGotten != nStored) {
		hc_test_log(LOG_ERROR_LEVEL, test_env,
	             "Stored %d but got %d\n", nStored, nGotten);
		test_res = TEST_FAIL;
	    }
	    if (test_res != TEST_PASS)
	        goto done;
	}

done:
	if (nvr != NULL)
		hc_nvr_free(nvr);
	if (stored_r_file != NULL)
		free_r_file(stored_r_file);
	free_hashlist(stored);
	hc_session_free(session);
	return test_res;
}

