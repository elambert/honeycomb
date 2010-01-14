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

static char *ez_storertrvmdlatin1_tags[] = {"capi", "capiez_storertrvmdlatin1"};
int hcez_storertrvmdlatin1_simpletest_exec(test_case_t *);
hc_test_result_t query_test(hc_session_t* session, hctest_env_t *test_env,
char* query, hc_oid expected_oid, int n_selects, char** selects,
char** expected_values);

hc_test_result_t pstmt_query_test(hc_session_t* session,
hctest_env_t *test_env, char** md_str_names, char** md_str_values,
char* query, hc_oid expected_oid, int n_selects, char** selects,
char** expected_values);


const unsigned char UTF8_ENCODE_1 = 0xC0;
const unsigned char UTF8_ENCODE_2 = 0x80;
const unsigned char UTF8_MASK_1 = 0xC0; // 11000000 in binary
const unsigned char UTF8_MASK_2 = 0x3F; // 00111111 in binary

void hcez_storertrvmdlatin1_load_tests(hctest_env_t *test_env) {
    char *argv[] = {"0"};
    add_test_to_list(create_and_tag_test_case("capiez_storertrvmdlatin1",
    1, argv, (int (*)(test_case_t*))hcez_storertrvmdlatin1_simpletest_exec,
    ez_storertrvmdlatin1_tags, 2, test_env), test_env, FALSE);
}

static char* convert_latin1_to_utf8(unsigned char* latin1_string) {
    unsigned char* utf8_string = NULL;
    int i, j, slen;
    slen = strlen((char*)latin1_string);
    utf8_string = malloc((slen * sizeof(char) * 2) + 1);
    j = 0;
    for (i = 0; i < slen; i++) {
        if (latin1_string[i] < 0x80) {
            utf8_string[j++] = latin1_string[i];
        } else {
            utf8_string[j++] = UTF8_ENCODE_1 | (latin1_string[i] >> 6);
            utf8_string[j++] = UTF8_ENCODE_2 | (latin1_string[i] & UTF8_MASK_2);
        }
    }
    utf8_string[j] = NULL;
    return (char *)utf8_string;
}

static char* create_utf8_all_latin1_chars(int query_only) {
    char* utf8_string = NULL;
    int i, j = 0;
    // size is # of latin1 chars plus room for extra char for query and
    // terminating null. Double size to allow room for 2 char representation
    // in UTF-8 for some chars.
    utf8_string = malloc(((255 + 2) * sizeof(char)) * 2);
    // loop through all Latin1 char values
    // 0x0 - 0x7F  same value for Latin1 and UTF-8
    for (i = 1; i < 128; i++) {
        utf8_string[j++] = i;
        // if creating query string write out ' character twice
        if (query_only && i == 39)
            utf8_string[j++] = i;
    }
    // 0x80 - 0xFF convert Latin1 to UTF-8 representation
    for (i = 128; i < 256; i++) {
        utf8_string[j++] = UTF8_ENCODE_1 | (i >> 6);
        utf8_string[j++] = UTF8_ENCODE_2 | (i & UTF8_MASK_2);
    }
    utf8_string[j] = NULL;
    return utf8_string;
}

int validate_string_values(int num_values, char** names,
char **expected_values, hc_nvr_t *nvr) {
    int values_match = 0;
    int i;
    char* string_return = NULL;
    hcerr_t res = HCERR_OK;
    for (i = 0; i < num_values; i++) {
        if ((res = hc_nvr_get_string(nvr, names[i], &string_return))
        != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_get_string: %s\n",
            hc_decode_hcerr(res));
            break;
        }
        if (strcmp(string_return, expected_values[i])) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_get_string wrong value returned for %s."
            "  Returned value:'%s' Expected value:'%s'\n",
            names[i], string_return, *expected_values[i]);
            break;
        } else {
            values_match++;
        }
        //printf("Returned string for: %s   value: %s\n", names[i],
        //        string_return);
    }
    return (values_match == num_values);
}

/*
 * Tests Latin1 character set for character metadata using the string datatype:
 * 1. Stores latin1 metadata with object
 * 2. Retrieves & verifies metadata for the object
 * 3. Adds metadata to stored object
 * 4. Retrieves & validates latin1 char metadata of stored object
 * 5. Query plus - uses latin1 character string to query for data and
 *    verifies values of latin1 characters returned
 */
int hcez_storertrvmdlatin1_simpletest_exec(test_case_t *tc) {
    char **argv = NULL;
    char *test_vip = NULL;
    hcerr_t expected_res, res;
    hc_test_result_t test_res;
    int port;
    hc_system_record_t sys_rec, sys_rec2;
    hc_random_file_t *stored_r_file = NULL;
    hctest_env_t *test_env;
    hc_nvr_t *nvr = NULL;
    hc_session_t *session;
    char query[2048];
    char *md_str_names[] = {"charweirdchars", "charlarge"};
    char *md_str_values[2];
    hc_value_t value = HC_EMPTY_VALUE_INIT;
    hc_string_t string_return;
    hc_double_t double_return;
    hc_long_t long_return;
    char **ret_names = NULL;
    char **ret_values = NULL;
    int i, j = 0;
    hc_query_result_set_t *rset = NULL;
    hc_nvr_t *query_nvr = NULL;
    char *selects[] = {"charweirdchars", "charlarge"};
    int n_selects = 2;
    char* utf8_string = NULL;
    int finished, found_oid = FALSE;
    hc_oid returnOID;
    hc_pstmt_t *pstmt = NULL;
    
    printf("\n>>>>Start: Test Store, Metadata & Query for Latin1 char metadata\n");
    // Allocate space for test values
    md_str_values[0] = malloc(8000);
    md_str_values[1] = malloc(512);
    
    // create test values using entire set of latin1 characters
    utf8_string = create_utf8_all_latin1_chars(FALSE);
    strcpy(md_str_values[0], utf8_string);
    strcat(md_str_values[0], utf8_string);
    strcpy(md_str_values[1], utf8_string);
    free(utf8_string);
    
    argv = tc->argv;
    expected_res = atoi(argv[0]);
    test_res = TEST_PASS;
    stored_r_file  = create_random_file(1012); /* small is good */
    
    test_env = tc->test_env;
    test_vip = test_env->data_vip;
    port = test_env->data_vip_port;
    
    /* Initialize session */
    hc_init(malloc, free, realloc);
    res = hc_session_create_ez(test_vip, port, &session);
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "Session Create failed. Returned code of %s\n",
        hc_decode_hcerr(res));
        test_res = TEST_ERROR;
        goto done;
    }
    // Create metadata
    res = hc_nvr_create_from_string_arrays(session, &nvr,
    md_str_names, md_str_values, 2);
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_nvr_create_from_string_arrays: %s\n",
        hc_decode_hcerr(res));
        test_res = TEST_ERROR;
        goto done;
    }
    
    printf("++++Storing file and Latin1 metadata\n");
    res = hc_store_both_ez(session, read_from_random_data_generator,
    stored_r_file, nvr, &sys_rec);
    
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "Store of file failed. Returned code of %s\n",
        hc_decode_hcerr(res));
        test_res = TEST_ERROR;
        goto done;
    }
    if (compare_rfile_to_sys_rec(stored_r_file , &sys_rec) != TRUE) {
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
    
    printf("++++Retrieving and validating metadata\n");
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
    
    for (i = 0; i < 2; i++) {
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
            "hc_nvr_get_string returned %s expected %s\n",
            string_return, md_str_values[i]);
            test_res = TEST_FAIL;
            goto done;
        }
    }
    res = hc_nvr_free(nvr);
    nvr = NULL;
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_nvr_free: %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto done;
    }
    
    printf("++++Storing additional metadata\n");
    // This time use hc_nvr_add_string to create the char metadata
    if ((res = hc_nvr_create(session, 1000, &nvr)) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
        "hc_nvr_create returned %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto done;
    }
    res = hc_nvr_add_string(nvr, md_str_names[0], md_str_values[0]);
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_nvr_add_string: %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto done;
    }
    res = hc_nvr_add_string(nvr, md_str_names[1], md_str_values[1]);
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_nvr_add_string: %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto done;
    }
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
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_nvr_free: %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto done;
    }
    
    printf("++++Retrieving and validating metadata\n");
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
    
    // Get Latin1 char metadata using string routines
    for (i = 0; i < 2; i++) {
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
            "hc_nvr_get_string returned %s expected %s\n",
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
        "hc_nvr_get_long (longlarge): %s\n",
        hc_decode_hcerr(res));
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
        "hc_nvr_get_long (wordlength): %s\n",
        hc_decode_hcerr(res));
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
    
    res = hc_nvr_free(nvr);
    nvr = NULL;
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_nvr_free: %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto done;
    }
    
    printf("++++Query using Latin1 metadata values\n");
    memset(query, 0, sizeof(query));
    utf8_string = create_utf8_all_latin1_chars(TRUE);
    sprintf(query, "charlarge=\'%s\'", utf8_string);
    free(utf8_string);
    
    test_res = query_test(session, test_env, query, sys_rec2.oid,
                            0, NULL, NULL);
    if (test_res != TEST_PASS)
        goto done;
    
    printf("++++Query (plus) using Latin1 metadata values\n");
    memset(query, 0, sizeof(query));
    utf8_string = create_utf8_all_latin1_chars(TRUE);
    sprintf(query, "charlarge=\'%s\'", utf8_string);
    free(utf8_string);
    test_res = query_test(session, test_env, query, sys_rec2.oid,
                            n_selects, selects, md_str_values);
    if (test_res != TEST_PASS)
        goto done;
    
    printf("++++Pstmt Query using Latin1 metadata values\n");
    test_res = pstmt_query_test(session, test_env, md_str_names,
            md_str_values, query, sys_rec2.oid, 0, NULL, NULL);
    if (test_res != TEST_PASS)
        goto done;
    
    printf("++++Pstmt Query (plus) using Latin1 metadata values\n");
    test_res = pstmt_query_test(session, test_env, md_str_names,
            md_str_values, query, sys_rec2.oid, n_selects, selects,
            md_str_values);
    if (test_res != TEST_PASS)
        goto done;
    
done:
    printf("<<<<End: Test Store, Metadata & Query for Latin1 char metadata\n\n");
    if (nvr != NULL)
        hc_nvr_free(nvr);

    if (query_nvr != NULL)
        hc_nvr_free(query_nvr);

    if (stored_r_file != NULL)
        free_r_file(stored_r_file);

    if (session != NULL)
        hc_session_free(session);

    return test_res;
}
/*
 * Performs Query or Query Plus operation.
 */
hc_test_result_t query_test(hc_session_t* session, hctest_env_t *test_env,
char* query, hc_oid expected_oid, int n_selects, char** selects,
char** expected_values) {
    hcerr_t res = HCERR_OK;
    hc_test_result_t test_res = TEST_PASS;
    hc_query_result_set_t* rset = NULL;
    hc_nvr_t* query_nvr = NULL;
    hc_oid return_oid;
    int found_oid, finished = FALSE;
    int n_found;

    if (res = hc_query_ez(session, query, selects, n_selects, 1000, &rset)
    != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_query_ez: returned %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto query_test_done;
    }
    
    found_oid = FALSE;
    n_found = 0;
    while (!finished && !found_oid) {
        if ((res = hc_qrs_next_ez(rset, &return_oid, &query_nvr, &finished))
        != HCERR_OK) {
            
            hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_qrs_next_ez returned %s\n", hc_decode_hcerr(res));
            test_res = TEST_FAIL;
            break;
        }
        if (finished)
            break;

        n_found++;
        if (strcmp(expected_oid, return_oid) == 0) {
            found_oid = TRUE;
            // Validate metadata returned for matching OID
            // if query plus
            if (selects != NULL) {
                if (validate_string_values(2, selects,
                expected_values, query_nvr) != TRUE) {
                    test_res = TEST_FAIL;
                    break;
                }
            }
        }
    } // end while
    
    if (test_res == TEST_FAIL)
        goto query_test_done;
    
    // Verify query returned object we just stored
    if (found_oid == FALSE) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
          "hc_qrs_next_ez didn't find any matching records; found = %d\n",
          n_found);
        printf("Expected OID: %s\n", expected_oid);
        printf("Query: [%s]\n", query);
        test_res = TEST_FAIL;
        goto query_test_done;
    }
    if (rset != NULL) {
        res = hc_qrs_free(rset);
        rset = NULL;
        if (res != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_qrs_free returned %s\n", hc_decode_hcerr(res));
            test_res = TEST_FAIL;
            goto query_test_done;
        }
    }
    
    if (query_nvr != NULL) {
        res = hc_nvr_free(query_nvr);
        query_nvr = NULL;
    }
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, test_env,
        "hc_nvr_free: %s\n", hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto query_test_done;
    }
    
query_test_done:
    if (query_nvr != NULL)
        hc_nvr_free(query_nvr);

    if (rset != NULL)
        hc_qrs_free(rset);

    return test_res;
}

/*
 * Performs PSTMT Query or Query Plus test
 */
hc_test_result_t pstmt_query_test(hc_session_t* session,
            hctest_env_t *test_env, char** md_str_names, char** md_str_values,
            char* query, hc_oid expected_oid, int n_selects, char** selects,
            char** expected_values) {
    hcerr_t res = HCERR_OK;
    hc_test_result_t test_res = TEST_PASS;
    hc_query_result_set_t* rset = NULL;
    hc_nvr_t* query_nvr = NULL;
    hc_oid return_oid;
    int found_oid, finished = FALSE;
    int i, n_found;
    hc_pstmt_t *pstmt = NULL;
    
    memset(query, NULL, sizeof(query));
    for (i = 0; i < 2; i++) {
        strcat(query, md_str_names[i]);
        strcat(query, " = ?");
        if (i < 1)
            strcat(query, " AND ");
    }
    if ((res = hc_pstmt_create(session, query, &pstmt)) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
        "hc_pstmt_create returned %s\n",
        hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto pstmt_query_test_done;
    }
    if ((res = hc_pstmt_set_string(pstmt, 1, md_str_values[0])) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
        "hc_pstmt_set_string (for char value) returned %s\n",
        hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto pstmt_query_test_done;
    }
    if ((res = hc_pstmt_set_string(pstmt, 2, md_str_values[1])) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
        "hc_pstmt_set_string (for char value) returned %s\n",
        hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto pstmt_query_test_done;
    }
    
    if ((res = hc_pstmt_query_ez(pstmt, selects, n_selects, 1000, &rset))
                        != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
        "hc_pstmt_query_ez returned %s\n",
        hc_decode_hcerr(res));
        test_res = TEST_FAIL;
        goto pstmt_query_test_done;
    }
    found_oid = FALSE;
    n_found = 0;
    while (!finished && !found_oid) {
        if ((res = hc_qrs_next_ez(rset, &return_oid, &query_nvr, &finished))
                            != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_qrs_next_ez returned %s\n", hc_decode_hcerr(res));
            test_res = TEST_FAIL;
            break;
        }
        if (finished)
            break;
        n_found++;
        if (strcmp(expected_oid, return_oid) == 0) {
            found_oid = TRUE;
            // Validate data for stored OID
            if (selects != NULL) {
                if (validate_string_values(2, selects,
                    expected_values, query_nvr) != TRUE) {
                    test_res = TEST_FAIL;
                    break;
                }
            }
        }
    } // end while
    
    if (test_res == TEST_FAIL)
        goto pstmt_query_test_done;
    
    // Verify query returned object we just stored
    if (found_oid == FALSE) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
          "hc_qrs_next_ez didn't find any matching records; found = %d\n",
          n_found);
        test_res = TEST_FAIL;
        goto pstmt_query_test_done;
    }
    if (rset !=NULL) {
        res = hc_qrs_free(rset);
        rset = NULL;
        if (res != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_qrs_free returned %s\n", hc_decode_hcerr(res));
            test_res = TEST_FAIL;
            goto pstmt_query_test_done;
        }
    }
    
    if (query_nvr != NULL) {
        res = hc_nvr_free(query_nvr);
        query_nvr = NULL;
        if (res != HCERR_OK) {
            hc_test_log(LOG_ERROR_LEVEL, test_env,
            "hc_nvr_free: %s\n", hc_decode_hcerr(res));
            test_res = TEST_FAIL;
            goto pstmt_query_test_done;
        }
    }
    
pstmt_query_test_done:
    if (query_nvr != NULL)
        hc_nvr_free(query_nvr);
    if (rset != NULL)
        hc_qrs_free(rset);

    return test_res;
}
