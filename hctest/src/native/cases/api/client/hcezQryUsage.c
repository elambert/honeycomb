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
int hcez_queryusage_simpletest_exec(test_case_t *);

extern char *progname;

void hcez_queryusage_load_tests(hctest_env_t *test_env) {
	char **file_size_ptr = NULL;
	char **cur_file_size = NULL;
	char *max_file_size = NULL;
	int number_of_file_sizes;
	int i,j;
	char *argv[1];

	argv[0] = "0";
	add_test_to_list(create_and_tag_test_case("capiez_queryusage",
		       	1, argv,
			(int (*)(test_case_t*))hcez_queryusage_simpletest_exec,
			ez_tags,1,test_env),test_env,FALSE);
}

static long read_from_file(void*, char*, long);

int hcez_queryusage_simpletest_exec(test_case_t *tc) {

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
	hc_query_result_set_t *rset = NULL;
	hc_nvr_t *store_nvr = NULL;
	hc_nvr_t *store_nvr2 = NULL;
	hc_nvr_t *query_nvr = NULL;
	hc_system_record_t system_record;
	hc_long_t long_value = 10000;
	hc_double_t double_value = 10000;
	hc_string_t string_value = "range_test";
	char string_large[1000], query_string[1000];
	char leg_query_string[100];
	char illeg_query_string[100];
	char illeg_utf8_query_string[100];
	hc_long_t long_return = 0;
	hc_long_t firstbyte = 0;
	hc_long_t lastbyte = 0;
        hc_double_t double_return = 0;
        hc_string_t string_return = "           ";
	hc_long_t store_nvr_count = 0;
	hc_value_t valuep;
	hc_long_t count = 0;
	char *namep = NULL;
        char *selects[] = {"longsmall"};
        int n_selects = 1;
	int finished = 0;
	hc_oid returnOID;

        hcerr_t reth;
	int32_t response;
	char *errstr;
        int test_result=TEST_PASS;
	int goterr;

        // each unicode supplemental char is represented in utf-16 as
        // a surrogate pair consisiting of two code points and and in
        // utf-8 as four code points. For example the unicode
        // supplemental char, U+10400 is  represented in utf-8 as 
	// F0, 90, 90, 80. 
        char legal_uni_supp_string[5] = {0xF0, 0x90, 0x90, 0x80, '\0'};

        // C0, C1 is outside the unicode definition range and hence
        // illegal 
        char utf8_string[3] = {0xC0, 0xC1, '\0'};

        // Since unicode supplemental chars are represented in utf-16
        // as a surrogate pair, if we take just one codepoint out of a
        // surrogate pair instead of both and so F0 90 becomes a
        // illegal unicode supplemental char. 
        char illegal_uni_supp_string[3] = {0xF0, 0x90, '\0'};

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
	printf("store_nvr create/add/get\n");
	if ((reth = hc_nvr_create(session, nslots, &store_nvr)) != HCERR_OK) {
		printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		exit(1);
	}
	if ((reth = hc_nvr_create(session, nslots, &store_nvr2)) != HCERR_OK) {
		printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		exit(1);
	}
	if ((reth = hc_nvr_add_long(store_nvr, "longsmall", long_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_add_long returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
        if ((reth = hc_nvr_add_double(store_nvr, "doublelarge", double_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_add_double returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
        if ((reth = hc_nvr_add_string(store_nvr, "test_id", string_value)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_add_string returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
        }
	if ((reth = hc_nvr_get_count(store_nvr, &store_nvr_count)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_get_count returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (store_nvr_count != 3) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_get_count returned nvr_count; should be 3\n",
			store_nvr_count);
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_nvr_get_long(store_nvr, "longsmall", &long_return)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_nvr_get_long returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (long_return != long_value) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"value returned by nvr_get_long incorrect\n");
		test_result = TEST_FAIL;
		goto done;
	}
        if ((reth = hc_nvr_get_double(store_nvr, "doublelarge", &double_return)) != HCERR_OK) {
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
        if ((reth = hc_nvr_get_string(store_nvr, "test_id", &string_return)) != HCERR_OK) {
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
	printf("store %s\n", progname);
	if ((fd=open(progname, O_RDONLY)) < 0) {
		perror(progname);
		exit(1);
	}
	if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, store_nvr, 
						&system_record) != HCERR_OK)) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_store_both_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
       
        /* Store metadata for previous OID */
	printf("store md\n");
	if ((reth = hc_store_metadata_ez(session, &system_record.oid, store_nvr, &system_record)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_store_metadata_ez returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
        close(fd);
        
        printf("Test valid query\n");
	if ((reth = hc_query_ez(session, "longsmall>0", NULL, 0, 100, &rset)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_query_ez returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_qrs_next_ez(rset, &returnOID, NULL, &finished)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_next_ez returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (finished != 0) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_next_ez didn't find any records; finished = %d\n", finished);
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_qrs_free(rset)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		rset = NULL;
		goto done;
        }
	rset = NULL;
	/* Attempt to query for attribute that does not exist in schema
	   this should generate an error but currently doesn't until qrs_free
	   except for multicell
        */
	goterr = 0;
	printf("Test attribute that does not exist in schema\n");
	if (hc_query_ez(session, "attributedoesnotexist=notthere", NULL, 0, 
                                                  100, &rset) != HCERR_OK) {
		goterr++;
	}
	if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
		goterr++;
	}
	rset = NULL;
	if (!goterr) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"Error should be returned %s",
			"for attribute that does not exist in schema\n");
		test_result = TEST_FAIL;
		goto done;
	}

	printf("Test various usages..\n");
	goterr = 0;
	/* Attempt to use reserved word in query */
	if (hc_query_ez(session, "test_id=select", NULL, 0, 100, &rset) 
								!= HCERR_OK) {
		goterr++;
	}
	if (rset != NULL &&((reth = hc_qrs_free(rset)) != HCERR_OK)) {
		goterr++;
	}
	rset = NULL;
	if (!goterr) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_free did not detect error for reserved word\n");
		test_result = TEST_FAIL;
		goto done;
	}

	/* Attempt query that has invalid boolean (=> instead of >=)
           Errors are caught on call to hc_qrs_free which seems late but
	   error can't be caught on hc_query_ez without a redesign */
	/* ?? should return error ?? */
	goterr = 0;
	if (hc_query_ez(session, "longsmall=>0", NULL, 0, 100, &rset) 
								!= HCERR_OK) {
		goterr++;
	}

	if (rset != NULL &&((reth = hc_qrs_free(rset)) != HCERR_OK)) {
		goterr++;
	}
	rset = NULL;
	if (!goterr) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_free got no error for incorrect boolean op.\n");
		test_result = TEST_FAIL;
		goto done;
	}

	printf ("Testing 'query plus'\n");
	if ((reth = hc_query_ez(session, "longsmall>0", selects, n_selects, 100,
							&rset)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_query_plus_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_qrs_next_ez(rset, &returnOID, &query_nvr, &finished)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_next_ez returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (finished != 0) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_qrs_next_ez didn't find any records\n");
		test_result = TEST_FAIL;
		goto done;
	}
	if ((reth = hc_qrs_free(rset)) != HCERR_OK) {
		hc_test_log(LOG_DEBUG_LEVEL,NULL,
		    "hc_qrs_free returned error after correct query: err=%s\n", 
		    hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		rset = NULL;
		goto done;
	}
	rset = NULL;

	/* negative case for incorrect boolean on hc_query_plus_ez */
	goterr = 0;
	if ((reth = hc_query_ez(session, "longsmall=>0", selects, n_selects, 
                                            100, &rset)) != HCERR_OK) {
		goterr++;
	}
	if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
		goterr++;
	}
	rset = NULL;
	if (!goterr) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"hc_query_ez with invalid boolean returned no err\n");
		test_result = TEST_FAIL;
		goto done;
        }

	//hc_session_get_status(session, &response, &errstr);
	//printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);

	printf("Testing store of too-large character string\n");
	string_large[0] = '\0';
	for(i=0; i<32; i++) {
		strcat(string_large, "eightchr");
	}
	query_string[0] = '\0';
	strcat(query_string, "test_id='");
	strcat(query_string, string_large);
	strcat(query_string, "'");
	if ((reth = hc_nvr_add_string(store_nvr, "test_id", string_large)) 
								!= HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_add_string returned %s\n",
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	//printf("string: %s\n", string_large);
	//hc_session_get_status(session, &response, &errstr);
	//printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);
	goterr = 0;

	printf("store %s\n", progname);
        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, store_nvr,
                                                &system_record) == HCERR_OK)) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "hc_store_both_ez of too large string succeeded %s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }  else {
                goterr++;
        }
        close(fd);

	if (hc_query_ez(session, query_string, NULL, 0, 100, &rset) 
								!= HCERR_OK) {
		goterr++;
	}
	if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
		goterr++;
	}
	rset = NULL;
	if (!goterr) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"No error returned for query of too-big string\n");
		test_result = TEST_FAIL;
		goto done;
        }

        printf("Testing store of legal unicode supplemental character string\n");

        leg_query_string[0] = '\0';
        strcat(leg_query_string, "test_id='");
        strcat(leg_query_string, legal_uni_supp_string);
        strcat(leg_query_string, "'");
        if ((reth = hc_nvr_add_string(store_nvr, "test_id", legal_uni_supp_string))
                                                                != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "hc_nvr_add_string returned %s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        //printf("legal uni supp string: %s\n", legal_uni_supp_string);
        //hc_session_get_status(session, &response, &errstr);
        //printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);
        goterr = 0;
	printf("store %s\n", progname);
        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, store_nvr,
                                                &system_record) == HCERR_OK)) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for store of legal suppl string%s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        } else {
                goterr++;
        }
        close(fd);
        if (hc_query_ez(session, leg_query_string, NULL, 0, 100, &rset)
                                                                != HCERR_OK) {
                goterr++;
        }

        if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
                goterr++;
        }
        rset = NULL;
        if (!goterr) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for query of legal unicode supp string\n");
                goto done;

        }
	
        printf("store of legal unicode suppl string to nonqueryable\n");

        leg_query_string[0] = '\0';
        strcat(leg_query_string, "nonqueryable.test.type_string='");
        strcat(leg_query_string, legal_uni_supp_string);
        strcat(leg_query_string, "'");
        if ((reth = hc_nvr_add_string(store_nvr2,
                                      "nonqueryable.test.type_string",
                                      legal_uni_supp_string)) != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "hc_nvr_add_string returned %s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        //printf("legal uni supp string: %s\n", legal_uni_supp_string);
        //hc_session_get_status(session, &response, &errstr);
        //printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);
        goterr = 0;
	printf("store %s\n", progname);
        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, 
                                     store_nvr2,
                                     &system_record) == HCERR_OK)) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for store of legal suppl string%s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        } else {
                goterr++;
        }
        close(fd);
        if (hc_query_ez(session, leg_query_string, NULL, 0, 100, &rset)
                                                                != HCERR_OK) {
                goterr++;
        }

        if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
                goterr++;
        }
        rset = NULL;
        if (!goterr) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error for query of legal unicode supp string\n");
                goto done;
       }


        printf("Testing store of illegal utf-8 character string\n");

        illeg_utf8_query_string[0] = '\0';
        strcat(illeg_utf8_query_string, "test_id='");
        strcat(illeg_utf8_query_string, utf8_string);
        strcat(illeg_utf8_query_string, "'");
        if ((reth = hc_nvr_add_string(store_nvr, "test_id", utf8_string))
                                                                != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "hc_nvr_add_string returned %s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        //printf("utf8 string: %s\n", utf8_string);
        //hc_session_get_status(session, &response, &errstr);
        //printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);
	printf("store %s\n", progname);
        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, store_nvr,
                                                &system_record) == HCERR_OK)) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for store of illegal utf-8 string%s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        close(fd);
     
        goterr = 0;
        if (hc_query_ez(session, illeg_utf8_query_string, NULL, 0, 100, &rset)
                                                                != HCERR_OK) {
                goterr++;
        }
        if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
                goterr++;
        }
        rset = NULL;
        if (!goterr) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for query of illegal utf-8 string\n");
                test_result = TEST_FAIL;
                goto done;
        }

        printf("Testing store of illegal utf-8 character to nonqueryable\n");

        illeg_utf8_query_string[0] = '\0';
        strcat(illeg_utf8_query_string, "nonqueryable.test.type_string='");
        strcat(illeg_utf8_query_string, utf8_string);
        strcat(illeg_utf8_query_string, "'");
        if ((reth = hc_nvr_add_string(store_nvr2,
                                      "nonqueryable.test.type_string", 
                                      utf8_string)) != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "hc_nvr_add_string returned %s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        //printf("utf8 string: %s\n", utf8_string);
        //hc_session_get_status(session, &response, &errstr);
        //printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);
	printf("store %s\n", progname);
        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, 
                                     store_nvr2, &system_record) == HCERR_OK)) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                      "No error returned for store of illegal utf-8 string%s\n",
                      hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        close(fd);
     
        goterr = 0;
        if (hc_query_ez(session, illeg_utf8_query_string, NULL, 0, 100, &rset)
                                                                != HCERR_OK) {
                goterr++;
        }
        if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
                goterr++;
        }
        rset = NULL;
        if (!goterr) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for query of illegal utf-8 string\n");
                test_result = TEST_FAIL;
                goto done;
        }


        printf("Testing store of illegal unicode supplemental character\n");

        illeg_query_string[0] = '\0';
        strcat(illeg_query_string, "test_id='");
        strcat(illeg_query_string, illegal_uni_supp_string);
        strcat(illeg_query_string, "'");
        if ((reth = hc_nvr_add_string(store_nvr, "test_id", illegal_uni_supp_string))
                                                                != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "hc_nvr_add_string returned %s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        //printf("utf8 string: %s\n", illegal_uni_supp_string);
        //hc_session_get_status(session, &response, &errstr);
        //printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);
	printf("store %s\n", progname);
        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, store_nvr,
                                                &system_record) == HCERR_OK)) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for store of illegal unicode supp str%s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }

        close(fd);	

        goterr = 0;
        if (hc_query_ez(session, illeg_query_string, NULL, 0, 100, &rset)
                                                                != HCERR_OK) {
                goterr++;
        }
        if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
                goterr++;
        }
        rset = NULL;
        if (!goterr) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for query of illegal unicode supp str\n");
                test_result = TEST_FAIL;
                goto done;

       }

        printf("Testing store of illegal unicode suppl char to nonqueryable\n");

        illeg_query_string[0] = '\0';
        strcat(illeg_query_string, "nonqueryable.test.type_string='");
        strcat(illeg_query_string, illegal_uni_supp_string);
        strcat(illeg_query_string, "'");
        if ((reth = hc_nvr_add_string(store_nvr2,
                                      "nonqueryable.test.type_string", 
                                      illegal_uni_supp_string)) != HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "hc_nvr_add_string returned %s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }
        //printf("utf8 string: %s\n", illegal_uni_supp_string);
        //hc_session_get_status(session, &response, &errstr);
        //printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response);
	printf("store %s\n", progname);
        if ((fd=open(progname, O_RDONLY)) < 0) {
                perror(progname);
                exit(1);
        }

        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, 
                                     store_nvr2, &system_record) == HCERR_OK)) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                        "No error returned for store of illegal unicode supp str%s\n",
                        hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
                goto done;
        }

        close(fd);	

        goterr = 0;
        if (hc_query_ez(session, illeg_query_string, NULL, 0, 100, &rset)
                                                                != HCERR_OK) {
                goterr++;
        }
        if (rset != NULL && ((reth = hc_qrs_free(rset)) != HCERR_OK)) {
                goterr++;
        }
        rset = NULL;
        if (!goterr) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
                   "No error returned for query of illegal unicode supp str\n");
                test_result = TEST_FAIL;
                goto done;

       }

done:
	if (query_nvr != NULL && ((reth = hc_nvr_free(query_nvr)) != HCERR_OK)) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
	}
	query_nvr = NULL;

	if (store_nvr != NULL && ((reth = hc_nvr_free(store_nvr)) != HCERR_OK)) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
	}
	store_nvr = NULL;
	if (store_nvr2 != NULL && ((reth = hc_nvr_free(store_nvr2)) != HCERR_OK)) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_nvr_free returned %s\n", hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
	}
	store_nvr2 = NULL;

/*
hc_qrs_free leads to seg fault in curl-land when session is freed 1st
and "hc_qrs_next_ez didn't find any records"
See 6507353
*/
	if (rset != NULL) {
		hc_qrs_free(rset);
	}
	if ((reth = hc_session_free(session)) != HCERR_OK) {
		printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
	}
	hc_cleanup();
	if (test_result == TEST_FAIL) {
		printf("test_query_usage FAILED\n");
	} else {
		printf("test_query_usage PASSED\n");
	}

	return test_result;
}


static long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

