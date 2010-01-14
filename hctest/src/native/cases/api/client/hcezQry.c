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

#include "hc.h"
#include "hcclient.h"
#include "hcoaez.h"
#include "hcinternals.h"

#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"


static char *hcez_qry_tags[] = {"capi","capiez_query"};

static hc_session_t *session = NULL;
static hc_oid data_oid;
static hc_schema_t *schema = NULL;

static int max_size_md_str = HC_MAX_STR_LENGTH_CLUSTER;

extern char *progname;

#define DEFAULT_STR_VALS_SIZE 6

// STEPH
#define GENTLE
#ifdef GENTLE
static char* DEFAULT_STR_VALS[] = {
  "my_first_string",
  "coucou",
  "bien sur",
  " ben voyons...",
  "d rather in english?",
  "last one!"
};
#else
static char* DEFAULT_STR_VALS[] = {
  "my_first_string",
  "hg%hjg",
  "213nb^",
  "uytiewrt_jkh",
  "hlhlkhl~2",
  "~kjhkjhjkhk++=*)"
};
#endif
static hc_double_t double_min = 4.9e-324;
static hc_double_t double_max = 1.7976931348623157e+308;

/*
 * List of test cases to be loaded and ran.
 */
int test_query_string(test_case_t* tc);
int test_query_long(test_case_t* tc);
int test_query_double(test_case_t* tc);
int test_query_double_limits(test_case_t* tc);

int test_long_operators(test_case_t* tc);
int test_string_operators(test_case_t* tc);
int test_boolean_operators(test_case_t* tc);
int test_logical_operators(test_case_t* tc);
int test_regular_expressions(test_case_t* tc);

static char* format_error()
{
  static char error_buf[1024];
  int32_t response_code;
  char* errstr;
  hc_session_get_status(session, &response_code, &errstr);
  memset(error_buf, 0, 1024);
  snprintf(error_buf, 1024, "error %s, response code %d\n",
           errstr, response_code);
  return error_buf;
}


static void
display_schema()
{
  hc_long_t i;
  char *type = NULL;
  hc_long_t count;
  char *sch_name;
  hc_type_t sch_type;


  if (schema == NULL) {
    return;
  }

  hc_schema_get_count(schema,&count);

  printf("SCHEMA HAS %d ENTRIES\n", (int)count);
  for (i = 0; i < count; i++) {
    hc_schema_get_type_at_index(schema,i,&sch_name,&sch_type);
    type = hc_decode_hc_type(sch_type);
    printf("name = %s, type = %s\n", sch_name, type);
  }
  printf("\n\n");
}


static void display_name_value_record(hc_nvr_t *nvr)
{
  char *prefix = "     ";
  hc_long_t nitems;
  hc_long_t index;
  char *name;
  hc_value_t value = HC_EMPTY_VALUE_INIT;
  hcerr_t res;

  res = hc_nvr_get_count(nvr,&nitems);
  if (res != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to retrieve count from nvr\n");
    return;
  }

  for (index = 0; index < nitems; index++) {
    res = hc_nvr_get_value_at_index(nvr,index,&name,&value);
    if (res != HCERR_OK) return;
    printf("%s%s = ",prefix,name);
    switch (value.hcv_type) {
    case HC_LONG_TYPE:
      printf(LL_FORMAT,value.hcv.hcv_long);
      break;
    case HC_DOUBLE_TYPE:
      printf("%.17lg", value.hcv.hcv_long);	/* Check this */
      break;
    case HC_STRING_TYPE:
    case HC_CHAR_TYPE:
      printf("%s",value.hcv.hcv_string);
      break;
    default:
      printf("ERROR: Unknown value type %d",value.hcv_type);
      break;
    }
    printf("\n");
  }
  return;
}

static int validate_double_limits_value_record(hc_nvr_t *nvr) {
    char *prefix = "     ";
    hc_long_t nitems;
    hc_long_t index;
    char *name;
    hc_value_t value = HC_EMPTY_VALUE_INIT;
    hcerr_t res;
    int num_double_attrs_found = 0;
    int num_double_attrs_expected = 2;
    
    res = hc_nvr_get_count(nvr, &nitems);
    if (res != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to retrieve count from nvr\n");
        return res;
    }
    
    for (index = 0; index < nitems; index++) {
        res = hc_nvr_get_value_at_index(nvr, index, &name, &value);
        if (res != HCERR_OK) return res;
        printf("%s%s = ", prefix, name);
        switch (value.hcv_type) {
            case HC_LONG_TYPE:
                printf(LL_FORMAT, value.hcv.hcv_long);
                break;
            case HC_DOUBLE_TYPE:
                printf("%.17G", value.hcv.hcv_double);
                if (strcmp(name, "doublenegative") == 0) {
                    num_double_attrs_found++;
                    if (value.hcv.hcv_double != double_min) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL, "Returned min double value does not match sent value. "
                        " Stored: %.17G   Retrieved: %.17G\n", double_min, value.hcv.hcv_double);
                        res = !HCERR_OK;
                    }
                }
                else if(strcmp(name, "doublelarge") == 0) {
                    num_double_attrs_found++;
                    if (value.hcv.hcv_double != double_max) {
                        hc_test_log(LOG_ERROR_LEVEL, NULL, "Returned max double value does not match sent value. "
                        " Stored: %.17G   Retrieved: %.17G\n", double_max, value.hcv.hcv_double);
                        res = !HCERR_OK;
                    }
                }
                break;
            case HC_STRING_TYPE:
            case HC_CHAR_TYPE:
                printf("%s", value.hcv.hcv_string);
                break;
            default:
                printf("Unknown value type %d", value.hcv_type);
                break;
        }
        printf("\n");
    }
    if (num_double_attrs_expected != num_double_attrs_found) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "Missing double metadata attributes"
        " Expected: %d  Found: %d\n",
        num_double_attrs_expected, num_double_attrs_found);
        res = !HCERR_OK;
    }
    return res;
}

void hcez_qry_load_tests(hctest_env_t *test_env) {
	char *argv1[]= {"0", "6"};
	char *argv2[]=  {"0", "4"};
	char *argv3[]=  {"0", "5"};
	char *argv4[]=  {"0"};
	char *argv5[]=  {"0"};
	char *argv6[]=  {"0"};
	char *argv7[]=  {"0"};
	char *argv8[]=  {"0"};
        char *argv9[]=  {"0"};
        
	add_test_to_list(create_and_tag_test_case("capiez_qry_string",
                                                  2, argv1,
						  (int (*)(test_case_t*))test_query_string,
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

 	add_test_to_list(create_and_tag_test_case("capiez_qry_long",
                                                  2, argv2,
						  (int (*)(test_case_t*))test_query_long, 
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

 	add_test_to_list(create_and_tag_test_case("capiez_qry_double",
                                                  2, argv3,
						  (int (*)(test_case_t*))test_query_double, 
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

 	add_test_to_list(create_and_tag_test_case("capiez_qry_long_operators",
                                                  1, argv4,
						  (int (*)(test_case_t*))test_long_operators, 
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

 	add_test_to_list(create_and_tag_test_case("capiez_qry_string_operators",
                                                  1, argv5,
						  (int (*)(test_case_t*))test_string_operators, 
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

 	add_test_to_list(create_and_tag_test_case("capiez_qry_boolean_operators",
                                                  1, argv6,
						  (int (*)(test_case_t*))test_boolean_operators, 
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

 	add_test_to_list(create_and_tag_test_case("capiez_qry_logical_operators",
                                                  1, argv7,
						  (int (*)(test_case_t*))test_logical_operators, 
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

 	add_test_to_list(create_and_tag_test_case("capiez_qry_regular_expressions",
                                                  1, argv8,
						  (int (*)(test_case_t*))test_regular_expressions, 
						  hcez_qry_tags, 2,test_env),
                         test_env, FALSE);

        add_test_to_list(create_and_tag_test_case("capiez_qry_double_limits",
                            1, argv9,
                            (int (*)(test_case_t*))test_query_double_limits,
                            hcez_qry_tags, 2, test_env),
                            test_env, FALSE);
}

#define INIT_TEST() \
    do {  \
	int _init_test_res  = 0; \
	if (session == NULL) { \
	    _init_test_res = init_test(tc->test_env); \
	    if (_init_test_res != TEST_PASS) { \
		return _init_test_res; \
	    } \
	} \
    } while (0)



#define PRINT_TEST_RESULT(test_name, test_result, test_param) \
    do { \
        if (test_result == TEST_PASS) { \
                  hc_test_log(LOG_INFO_LEVEL, NULL, \
                    "Test %s (%s) PASSED\n", test_name, test_param); \
         } else { \
                  hc_test_log(LOG_INFO_LEVEL, NULL, \
                    "Test %s (%s) FAILED\n", test_name, test_param); \
         } \
    } while (0)


  
static int
init_test(hctest_env_t *test_env)
{
    const int DEFAULT_SIZE_FILE = 1012;
    char *test_vip;
    char *test_vip_port;
    int port;
    hcerr_t hcerr;
    hc_system_record_t sys_record;
    hc_random_file_t *r_file = NULL;
    hc_nvr_t *nvr = NULL;
    int test_res = TEST_PASS;

    if (test_env->emulator) {
	hc_test_log(LOG_INFO_LEVEL,
	  test_env, "Running test on the emulator...");
	max_size_md_str = HC_MAX_STR_LENGTH_EMULATOR;	
    }
    
    test_vip = test_env->data_vip;
    port = test_env->data_vip_port;
    r_file = create_random_file(DEFAULT_SIZE_FILE);

    hcerr = hc_session_create_ez(test_vip, port, &session);
    if (hcerr != HCERR_OK) {
	hc_test_log(LOG_ERROR_LEVEL,
	  test_env, "Cannot init test, failed to create the session\n");
        goto finished;
    }

    // STEPH nvr cannot be null;
    hcerr = hc_nvr_create(session, 1, &nvr);
    if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL,
                  test_env,
                  "Cannot init test, failed to create name-value struct\n");
      test_res = TEST_ERROR;
      goto finished;
    }

    // STEPH
    hcerr = hc_nvr_add_string(nvr, "filename", "default");
    if (hcerr != HCERR_OK) {
	hc_test_log(LOG_ERROR_LEVEL,
	  test_env, "Cannot init test, failed to add string param\n");
      test_res = TEST_ERROR;
      goto finished;
    }
    

    hcerr = hc_store_both_ez(session, read_from_random_data_generator,
      (void *) r_file,  nvr, &sys_record);
    if (hcerr != HCERR_OK) {
	hc_test_log(LOG_ERROR_LEVEL,
	  test_env, "Cannot init test, failed to store file\n");
      test_res = TEST_ERROR;
      goto finished;
    }

    hcerr = hc_session_get_schema(session, &schema);
    if (hcerr != HCERR_OK) {
	hc_test_log(LOG_ERROR_LEVEL,
	  test_env, "Cannot init test, failed get schema file\n");
      test_res = TEST_ERROR;
      goto finished;
    }
    
    memcpy(data_oid, &sys_record.oid, sizeof(hc_oid));
  
    // STEPH
    display_schema();

 finished:
    if (r_file != NULL) {
      free_r_file(r_file);
    }
    if (nvr != NULL) {
      hc_nvr_free(nvr);
    }
    if (test_res == TEST_ERROR && session != NULL) {
      hc_session_free(session);
      session = NULL;
    }
    return test_res;
}


/*
 *
 *               BASIC TEST FOR QUERIES-- one for each type:
 *                    - store metadata
 *                    - query for metadata
 *                    - delete metadata
 *                    - check query fails
 */

static int
test_query_type(test_case_t* tc, hc_type_t type, char *test_name)
{
#define	ARGUMENT_LENGTH 64
  int default_str_nb;
  int max_str_nb;
  hc_system_record_t system_record;
  hc_long_t count = -1;
  hc_long_t count_after_delete = -1;
  hcerr_t hcerr;
  hcerr_t expected_res;
  int nb_md;
  hc_nvr_t *nvr = NULL;
  char **names = NULL;
  char **str_values = NULL;
  hc_long_t *long_values = NULL;
  hc_double_t *double_values = NULL;
  char *query_string = NULL;
  int test_res = TEST_PASS;
  char **argv = tc->argv;
  char arguments[ARGUMENT_LENGTH];
  int k;
  hc_query_result_set_t *qrs = NULL;
  hctest_env_t *test_env;
  int rs_finished = 0;
  hc_oid retrieved_oid;
  hc_hashlist_t *qrs_hashlist = NULL;
  hc_nvr_t *nvr_retrieve = NULL;

  INIT_TEST();

  expected_res = atoi(argv[0]);
  nb_md = atoi(argv[1]);
  memset(arguments, 0, ARGUMENT_LENGTH);
  sprintf(arguments, "nb metadata = %d", nb_md);
  test_env = tc->test_env;
  qrs_hashlist = create_hashlist(1024); //Should probably pick a more logical value than 1024  
  if (type == HC_STRING_TYPE) {
    // get fields from namespace w/ all long enough fields
    names = get_random_entry_from_schema(schema, type, nb_md, "perf_qafirst");
  } else {
    // NOTE: getting fields from different tables violates our
    // recommended practice, but works ok here for now
    names = get_random_entry_from_schema(schema, type, nb_md, NULL);
  }
  memset(retrieved_oid,'\0',sizeof(hc_oid));
  switch (type) {
  case HC_STRING_TYPE:
    if (nb_md > DEFAULT_STR_VALS_SIZE) {
      max_str_nb = nb_md - DEFAULT_STR_VALS_SIZE;
      default_str_nb =   DEFAULT_STR_VALS_SIZE;
    } else if (nb_md > 1) {
      max_str_nb = 1;
      default_str_nb =  nb_md - 1;
    } else {
      max_str_nb = 0;
      default_str_nb =  1;
    }
    str_values = init_str_values(schema, names, DEFAULT_STR_VALS, 
				default_str_nb, max_str_nb, max_size_md_str);

    hcerr = hc_nvr_create_from_string_arrays(session, &nvr, names, str_values,
                                             nb_md);
    if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to create name value rec\n");
      test_res = TEST_ERROR;
      goto finished;
    }
    break;
  case HC_LONG_TYPE:
    str_values = init_long_values(nb_md, &long_values);

    hcerr = hc_nvr_create(session, nb_md, &nvr);
    if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to create name value rec\n");
      test_res = TEST_ERROR;
      goto finished;
    }

    for (k = 0; k < nb_md; k++) {
      hcerr = hc_nvr_add_long(nvr, names[k], long_values[k]);
      if (expected_res != hcerr) {
        hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to add long value to nvr\n");
        test_res = TEST_ERROR;
        goto finished;
      }
    }
    break;
  case HC_DOUBLE_TYPE:
    str_values = init_double_values(nb_md, &double_values);

    hcerr = hc_nvr_create(session, nb_md, &nvr);
    if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to create name value rec\n");
      test_res = TEST_ERROR;
      goto finished;
    }

    for (k = 0; k < nb_md; k++) {
      hcerr = hc_nvr_add_double(nvr, names[k], double_values[k]);
      if (expected_res != hcerr) {
        hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to add long value to nvr\n");
        test_res = TEST_ERROR;
        goto finished;
      }
    }
    break;
  default:
    break;
  }

#if 0
  display_name_value_record(nvr);
#endif

  hcerr = hc_store_metadata_ez(session, &data_oid, nvr, &system_record);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to store metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }

  // STEPH
  hcerr = hc_retrieve_metadata_ez(session, &(system_record.oid), &nvr_retrieve);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to retrieve metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
#if 0
  display_name_value_record(nvr_retrieve);
#endif


  hc_test_log(LOG_INFO_LEVEL, test_env, "RETRIEVE OID = %s\n",
              system_record.oid);


  query_string = get_query_string(names, str_values, nb_md, type);
  hcerr = hc_query_ez(session, query_string, NULL, 0, 100, &qrs);

  if (hcerr != HCERR_OK) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to query [%s]: %s\n", 
                           query_string, hc_decode_hcerr(hcerr));
      test_res = TEST_FAIL;
      goto finished;
  }

  if (check_results_query_result_set(&system_record.oid, 1, qrs)) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to validate results\n");
      test_res = TEST_FAIL;
      goto finished;
  }
  printf("query result validated\n");

  hcerr = hc_qrs_free(qrs);
  if (hcerr != HCERR_OK) {
    hc_test_log(LOG_ERROR_LEVEL, test_env, "hc_qrs_free error\n");
    test_res = TEST_FAIL;
    qrs = NULL;
    goto finished;
  }
  qrs = NULL;

  printf("deleting oid\n");
  hcerr = hc_delete_ez(session, &(system_record.oid));
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to delete object %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }

  printf("querying again - EXPECTING FAILURE\n");
  hcerr = hc_query_ez(session, query_string, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, test_env, "Failed to validate query\n");
      test_res = TEST_FAIL;
      goto finished;
  }

  if (check_results_query_result_set(&system_record.oid, 1, qrs) == 0) {
      hc_test_log(LOG_ERROR_LEVEL,test_env, "Query succeeded after delete, and"
                  " should have failed...\n");
      test_res = TEST_FAIL;
      goto finished;
  }
  printf("got expected failure\n");


finished:
  if (qrs != NULL) {
	  hc_qrs_free(qrs);
  }
  if (str_values != NULL) {
      free_array(str_values, nb_md);
  }
  if (long_values != NULL) {
    free(long_values);
  }
  if (double_values != NULL) {
    free(double_values);
  }
  if (names != NULL) {
      free_array(names, nb_md);
  }
  if (query_string != NULL) {
      free(query_string);
  }
  if (nvr != NULL) {
    hc_nvr_free(nvr);
  }
  if (nvr_retrieve != NULL) {
    hc_nvr_free(nvr_retrieve);
  }
  if (qrs_hashlist != NULL) {
    free_hashlist(qrs_hashlist);
  }
  PRINT_TEST_RESULT(test_name, test_res, arguments);
  return test_res;
}



int
test_query_long(test_case_t* tc)
{
  return test_query_type(tc, HC_LONG_TYPE, "test_query_long");
}


int
test_query_string(test_case_t* tc)
{
  return test_query_type(tc, HC_STRING_TYPE, "test_query_string");
}


int
test_query_double(test_case_t* tc)
{
  return test_query_type(tc, HC_DOUBLE_TYPE, "test_query_double");
}

static long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

/*
 *   Tests the min/max values for double metadata values.
 *   1. Stores and object with metadata double min and max values.
 *   2. Retrieves the object's metadata and validates the double values.
 *   3. Does a query plus using a AND query on the min and value double
 *      values. Also returns and validates the min and max double values.
 */
#define DBL_LIMIT_QRY "doublenegative=4.9E-324 AND doublelarge=1.7976931348623157E+308"
int
test_query_double_limits(test_case_t* tc) {
    hctest_env_t *env;
    int port;
    char *host;
    int fd;
    int i=0;
    hc_long_t nslots=10000;
    hc_session_t *session = NULL;
    hc_query_result_set_t *rset = NULL;
    hc_nvr_t *store_nvr = NULL;
    hc_nvr_t *query_nvr = NULL;
    hc_system_record_t system_record;
    hc_string_t string_value = "double_limits_test";
    hc_double_t double_return = 0;
    hc_string_t string_return = "           ";
    hc_long_t store_nvr_count = 0;
    char *selects[] = {"doublenegative", "doublelarge"};
    int n_selects = 2;
    int finished = 0;
    int found_oid = FALSE;
    hc_oid returnOID;

    hcerr_t hcerr, expected_res;
    char **argv = tc->argv;
    int32_t response;
    char *errstr;
    int test_result=TEST_PASS;
    int goterr;
    hc_nvr_t *nvr_retrieve = NULL;
    int n_found;

    env=tc->test_env;
    port = env->data_vip_port;
    host = env->data_vip;
    printf("host %s  port %d\n", host, port);
    /* hc_init has already been called, but we follow the other examples */
    hc_cleanup();
    hc_init(malloc, free, realloc);

    printf("create session\n");
    if ((hcerr = hc_session_create_ez(host, port, &session)) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_session_create_ez returned %s\n", hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }

    /* Store some Data and Metadata */
    printf("store_nvr create/add/get\n");
    if ((hcerr = hc_nvr_create(session, nslots, &store_nvr)) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_create returned %s\n", hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_nvr_add_double(store_nvr, "doublenegative", double_min)) 
            != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_add_double for doublenegative returned %s\n",
            hc_decode_hcerr(hcerr));
        printf("Tried to add value: %.17G\n", double_min);
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_nvr_add_double(store_nvr, "doublelarge", double_max)) 
            != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_ncr_add_double for doublelarge returned %s\n",
            hc_decode_hcerr(hcerr));
        printf("tried to add value: %.17G\n", double_max);
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_nvr_add_string(store_nvr, "test_id", string_value)) 
            != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_add_string returned %s\n", hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_nvr_get_count(store_nvr, &store_nvr_count)) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_get_count returned %s\n", hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    if (store_nvr_count != 3) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_get_count returned nvr_count; should be 3\n",
            store_nvr_count);
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_nvr_get_double(store_nvr, "doublenegative", &double_return))
            != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_get_double doublenegative returned %s\n",
            hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    if (double_return != double_min) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "value returned by nvr_get_double for doublenegative incorrect\n");
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_nvr_get_double(store_nvr, "doublelarge", &double_return)) 
            != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
        "value returned by nvr_get_double for doublelarge incorrect\n");
        test_result = TEST_FAIL;
        goto finished;
    }
    if (double_return != double_max) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "value returned by nvr_get_double for doublelarge incorrect\n");
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_nvr_get_string(store_nvr, "test_id", &string_return)) 
            != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_nvr_get_string returned %s\n",
            hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    if (strcmp(string_return, string_value) != 0) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "String returned by hc_nvr_get_string incorrect.\n");
        test_result = TEST_FAIL;
        goto finished;
    }
    printf("store %s\n", progname);
    if ((fd=open(progname, O_RDONLY)) < 0) {
        perror(progname);
        exit(1);
    }
    if ((hcerr = hc_store_both_ez(session, &read_from_file,
        (void *)fd, store_nvr, &system_record) != HCERR_OK)) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_store_both_ez returned %s\n",
            hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    /* Store metadata for previous OID */
    printf("store md - min/max values for double attributes\n");
    if ((hcerr = hc_store_metadata_ez(session, &system_record.oid, store_nvr, 
            &system_record)) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_store_metadata_ez returned %s\n",
            hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    close(fd);
    printf("Test retrieve object & validate metadata double values\n");
    hcerr = hc_retrieve_metadata_ez(session, &(system_record.oid),
            &nvr_retrieve);
    if (HCERR_OK != hcerr) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to retrieve metadata %s\n",
            format_error());
        test_result = TEST_ERROR;
        goto finished;
    }

    hc_test_log(LOG_INFO_LEVEL, NULL, "RETRIEVE OID = %s\n", system_record.oid);
    if (validate_double_limits_value_record(nvr_retrieve) != HCERR_OK) {
        test_result = TEST_FAIL;
        goto finished;    
    }

    printf("\nTest & validate double limits query plus\n");
    if ((hcerr = hc_query_ez(session, DBL_LIMIT_QRY,
        selects, n_selects, 100, &rset)) != HCERR_OK) {
            
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_query_ez returned %s\n", hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        goto finished;
    }
    found_oid = FALSE;
    n_found = 0;
    while (!finished && !found_oid) {
        if ((hcerr = hc_qrs_next_ez(rset, &returnOID, &query_nvr, &finished)) 
                != HCERR_OK) {
                    
            hc_test_log(LOG_ERROR_LEVEL, NULL,
                "hc_qrs_next_ez returned %s\n", hc_decode_hcerr(hcerr));
            test_result = TEST_FAIL;
            goto finished;
        }
        if (finished)
            break;
        n_found++;
        // Validate data for stored OID
        if (strcmp(system_record.oid, returnOID) == 0) {
            found_oid = TRUE;
            if (validate_double_limits_value_record(query_nvr) != HCERR_OK) {
                test_result = TEST_FAIL;
                break;    
            }        
        }
    } // end while

    if (test_result == TEST_FAIL)
        goto finished;

    // Verify query returned object we just stored
    if (found_oid == FALSE) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_qrs_next_ez didn't find any matching records; found = %d\n",
            n_found);
        printf("Query: [%s]\n", DBL_LIMIT_QRY);
        printf("Expected: %s\n", system_record.oid);
        test_result = TEST_FAIL;
        goto finished;
    }
    if ((hcerr = hc_qrs_free(rset)) != HCERR_OK) {
        hc_test_log(LOG_ERROR_LEVEL, NULL,
            "hc_qrs_free returned %s\n", hc_decode_hcerr(hcerr));
        test_result = TEST_FAIL;
        rset = NULL;
        goto finished;
    }

finished:
    if (store_nvr != NULL) {
        hc_nvr_free(store_nvr);
    }
    if (query_nvr != NULL) {
        hc_nvr_free(query_nvr);
    }
    if (nvr_retrieve != NULL) {
        hc_nvr_free(nvr_retrieve);
    }
    if (test_result == TEST_ERROR && session != NULL) {
        hc_session_free(session);
        session = NULL;
    }
    PRINT_TEST_RESULT("test_double_limits", test_result, "null");
    return test_result;
}
/*
 *
 *               TEST OPERATORS FOR QUERIES
 *
 */
int
test_long_operators(test_case_t* tc)
{
  const hc_long_t VAL = 234567;
  const int VAL_STR_LENGTH = 7;
  const char val[] = "234567";
  hc_system_record_t system_record;
  hcerr_t hcerr;
  hcerr_t expected_res;
  char** names = NULL;
  hc_nvr_t* nvr = NULL;
  int queryLength = -1;
  char* query = NULL;
  int test_res = TEST_PASS;
  char** argv = tc->argv;
  hc_query_result_set_t *qrs = NULL;

  INIT_TEST();
  expected_res = atoi(argv[0]);

  names = get_random_entry_from_schema(schema, HC_LONG_TYPE, 1, NULL);

  hcerr = hc_nvr_create(session, 1, &nvr);
  if (expected_res != hcerr) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to create name value rec\n");
    test_res = TEST_ERROR;
    goto finished;
  }
  
  hcerr = hc_nvr_add_long(nvr, names[0], -VAL);
  if (expected_res != hcerr) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to add long value to nvr\n");
        test_res = TEST_ERROR;
        goto finished;
  }


  hcerr = hc_store_metadata_ez(session, &data_oid, nvr, &system_record);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to store metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }

  queryLength = 10 + strlen(names[0]) + 5 + VAL_STR_LENGTH + 1;
  query = (char*) malloc(queryLength);
  CHECK_MEM(query);
 
  (void) strcpy(query, "{fn ABS(");
  (void) strcat(query, "\"");
  (void) strcat(query, names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query, ")}=");
  (void) strcat(query, val);

  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }


  if (check_results_query_result_set(&system_record.oid, 1, qrs)) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
      printf("Query: [%s]\n", query);
      test_res = TEST_ERROR;
      goto finished;
  }

finished:
  if (qrs != NULL) {
	  hc_qrs_free(qrs);
  }
  if (nvr != NULL) {
    hc_nvr_free(nvr);
  }
  if (names != NULL) {
    free_array(names, 1);
  }
  if (query != NULL) {
    free(query);
  }
  PRINT_TEST_RESULT("test_long_operators", test_res, "null");
  return test_res;
}

int
test_string_operators(test_case_t* tc)
{
  hc_system_record_t system_record;
  hc_long_t count = -1;
  hcerr_t hcerr;
  int k;
  int test_res = TEST_PASS;
  int queryLength = 0;
  hcerr_t expected_res;
  hc_nvr_t* nvr = NULL;
  char* query = NULL;
  char** names = NULL;
  char** values = NULL;
  int nb_metadata = 1;
  char** argv = tc->argv;
  hc_query_result_set_t *qrs = NULL;

  INIT_TEST();
  expected_res = atoi(argv[0]);

  names = get_random_entry_from_schema(schema, HC_STRING_TYPE, 1, 
                                       "perf_qafirst");
  printf("attribute used: %s\n", *names);
  
  values = calloc(sizeof(char*), 1);
  CHECK_MEM(values);
  values[0] = strdup("testUpperLower");
  CHECK_MEM(values[0]);

  hcerr = hc_nvr_create(session, 1, &nvr);
  if (expected_res != hcerr) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to create name value rec\n");
    test_res = TEST_ERROR;
    goto finished;
  }

  hcerr = hc_nvr_add_string(nvr, names[0], values[0]);
  if (expected_res != hcerr) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to add long value to nvr\n");
        test_res = TEST_ERROR;
        goto finished;
  }

  hcerr = hc_store_metadata_ez(session, &data_oid, nvr, &system_record);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to store metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }

  /* UCASE */
  queryLength = 12 + strlen(names[0]) + 6 + strlen("testUpperLower") + 2;
  query = (char*) malloc(queryLength);
  CHECK_MEM(query);

  (void) strcpy(query,"{fn UCASE(");
  (void) strcat(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query,")}='");
  (void) strcat(query,"TESTUPPERLOWER");
  (void) strcat(query,"'");


  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
 
  if (check_results_query_result_set(&system_record.oid, 1, qrs)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
    printf("Query: [%s]\n", query);
    test_res = TEST_ERROR;
    goto finished;
  }
  hc_qrs_free(qrs); qrs = NULL;
  free(query); query=NULL;

  /* LCASE */
  queryLength = 12 + strlen(names[0]) + 6 + strlen("testUpperLower") + 2;
  query = (char*) malloc(queryLength);
  CHECK_MEM(query);

  (void) strcpy(query,"{fn LCASE(");
  (void) strcat(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query,")}='");
  (void) strcat(query,"testupperlower");
  (void) strcat(query,"'");

  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
 
  if (check_results_query_result_set(&system_record.oid, 1, qrs)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
    printf("Query: [%s]\n", query);
    test_res = TEST_ERROR;
    goto finished;
  }
  hc_qrs_free(qrs); qrs = NULL;
  free(query); query = NULL;
  free_array(values, 1); values = NULL;
  free_array(names, 1); names = NULL;

  /* CONCACTENATION */
  nb_metadata = 2;
  names = get_random_entry_from_schema(schema, HC_STRING_TYPE, 2, 
                                       "perf_qafirst");

  values = calloc(sizeof(char*), 2);
  CHECK_MEM(values);
  values[0] = strdup("test");
  values[1] = strdup("concatenation");
  CHECK_MEM(values[0]);
  CHECK_MEM(values[1]);

  for (k = 0; k < 2; k++) {
    hcerr = hc_nvr_add_string(nvr, names[k], values[k]);
    if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to add string to nvr\n");
      test_res = TEST_ERROR;
      goto finished;
    }
  }

  hcerr = hc_store_metadata_ez(session, &data_oid, nvr, &system_record);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to store metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }


  queryLength = 3 + strlen(names[0]) + 8 + strlen(names[1]) + 2 + strlen(")='") +
    strlen("testconcatenation") + strlen("'") + 1;
                                                                    
  query = (char*) malloc(queryLength);
  CHECK_MEM(query);

  (void) strcpy(query,"(");
  (void) strcat(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query," || ");
  (void) strcat(query, "\"");
  (void) strcat(query,names[1]);
  (void) strcat(query, "\"");
  (void) strcat(query,")='");
  (void) strcat(query,"testconcatenation");
  (void) strcat(query,"'");

  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
 
  if (check_results_query_result_set(&system_record.oid, 1, qrs)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
    printf("Query: [%s]\n", query);
    test_res = TEST_ERROR;
    goto finished;
  }

 finished:
  if (qrs != NULL) {
	  hc_qrs_free(qrs);
  }
  if (query != NULL) {
    free(query);
  }
  if (values != NULL) {
    free_array(values, nb_metadata);
  }
  if (names != NULL) {
    free_array(names, nb_metadata);
  }
  if (nvr != NULL) {
    hc_nvr_free(nvr);
  }
  PRINT_TEST_RESULT("test_string_operators", test_res, "null");
  return test_res;
}


int 
test_regular_expressions(test_case_t* tc)
{
  hcerr_t hcerr;
  hc_system_record_t system_record;
  hc_long_t count = -1;
  hcerr_t expected_res;
  int queryLength;
  int test_res = TEST_PASS;
  hc_nvr_t* nvr = NULL;
  char* query = NULL;
  char** names = NULL;
  char** values = NULL;
  char** argv = tc->argv;
  hc_query_result_set_t *qrs = NULL;

  INIT_TEST();
  expected_res = atoi(argv[0]);

  names = get_random_entry_from_schema(schema, HC_STRING_TYPE, 1, 
                                       "perf_qafirst");
  
  values = calloc(sizeof(char*), 1);
  CHECK_MEM(values);
  values[0] = strdup("testlike");
  CHECK_MEM(values[0]);

  hcerr = hc_nvr_create(session, 1, &nvr);
  if (expected_res != hcerr) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to create name value rec\n");
    test_res = TEST_ERROR;
    goto finished;
  }

  hcerr = hc_nvr_add_string(nvr, names[0], values[0]);
  if (expected_res != hcerr) {
        hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to add long value to nvr\n");
        test_res = TEST_ERROR;
        goto finished;
  }

  hcerr = hc_store_metadata_ez(session, &data_oid, nvr, &system_record);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to store metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }

  /* LIKE using wildcard character */
  queryLength = 2 +strlen(names[0]) + 2 + strlen(" like '") +
    strlen("test%") + 2;
  query = (char*) malloc(queryLength);
  CHECK_MEM(query);

  (void) strcpy(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query," like '");
  (void) strcat(query,"test%");
  (void) strcat(query,"'");

  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
 
  if (check_results_query_result_set(&system_record.oid, 1,qrs)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
    printf("Query: [%s]\n", query);
      test_res = TEST_ERROR;
      goto finished;
  }
  hc_qrs_free(qrs);

  free(query); query = NULL;

  /* IN syntax */
  queryLength = 2 + strlen(names[0]) + 2 + strlen(" in ('") + strlen(values[0])
    + strlen("', '") + strlen("dummy") + strlen("')") + 1;
  query = (char*) malloc(queryLength);
  CHECK_MEM(query);
 
  (void) strcpy(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query, " in ('");
  (void) strcat(query, values[0]);
  (void) strcat(query,"', '");
  (void) strcat(query,"dummy");
  (void) strcat(query,"')");

  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
 
  if (check_results_query_result_set(&system_record.oid, 1, qrs)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
    printf("Query: [%s]\n", query);
    test_res = TEST_ERROR;
    goto finished;
  }

finished:
  if (qrs != NULL) {
	  hc_qrs_free(qrs);
  }
  if (query != NULL) {
    free(query);
  }
  if (values != NULL) {
    free_array(values, 1);
  }
  if (names != NULL) {
    free_array(names, 1);
  }
  if (nvr != NULL) {
    hc_nvr_free(nvr);
  }
  PRINT_TEST_RESULT("test_regular_expressions", test_res, "null");
  return test_res;
}

int
test_logical_operators(test_case_t* tc)
{
  hcerr_t hcerr;
  hc_system_record_t system_record;
  hc_long_t count = -1;
  hcerr_t expected_res;
  hc_nvr_t* nvr = NULL;
  int k;
  int test_res = TEST_PASS;
  char* query = NULL;
  char** names = NULL;
  char** values = NULL;
  char** argv = tc->argv;
  int queryLength;
  hc_query_result_set_t *qrs = NULL;

  INIT_TEST();
  expected_res = atoi(argv[0]);

  names = get_random_entry_from_schema(schema, HC_STRING_TYPE, 3, 
                                       "perf_qafirst");
  
  values = (char**) calloc(sizeof(char*), 3);
  CHECK_MEM(values);
  values[0] = strdup("value_0");
  values[1] = strdup("value_1");
  values[2] = strdup("value_2");
  CHECK_MEM(values[0]);
  CHECK_MEM(values[1]);
  CHECK_MEM(values[2]);


  hcerr = hc_nvr_create(session, 1, &nvr);
  if (expected_res != hcerr) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to create name value rec\n");
    test_res = TEST_ERROR;
    goto finished;
  }

  for (k = 0; k < 3; k++) {
    hcerr = hc_nvr_add_string(nvr, names[k], values[k]);
    if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to add string value to nvr\n");
      test_res = TEST_ERROR;
      goto finished;
    }
  }

  hcerr = hc_store_metadata_ez(session, &data_oid, nvr, &system_record);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to store metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }


   queryLength = 2 + strlen(names[0]) + 2 + strlen("='") + strlen(values[0]) +
    strlen("'") + strlen(" OR ") +
    2 + strlen(names[0]) + 2 + strlen("='") + strlen(values[1]) +
    strlen("'") + strlen(" AND ") +
    2 + strlen(names[1]) + 2 + strlen("='") + strlen(values[1]) +
    strlen("'") + strlen(" AND ") +
    2 + strlen(names[2]) + 2 + strlen("='") + strlen(values[2]) +
    strlen("'") + 1;

  query = (char*) malloc(queryLength);
  CHECK_MEM(query);

  (void) strcpy(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query,"='");
  (void) strcat(query,values[0]);
  (void) strcat(query,"'");
  (void) strcat(query," OR ");
  (void) strcat(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query,"='");
  (void) strcat(query,values[1]);
  (void) strcat(query,"'");
  (void) strcat(query," AND ");
  (void) strcat(query, "\"");
  (void) strcat(query,names[1]);
  (void) strcat(query, "\"");
  (void) strcat(query,"='");
  (void) strcat(query,values[1]);
  (void) strcat(query,"'");
  (void) strcat(query," AND ");
  (void) strcat(query, "\"");
  (void) strcat(query,names[2]);
  (void) strcat(query, "\"");
  (void) strcat(query,"='");
  (void) strcat(query, values[2]);
  (void) strcat(query,"'");


  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
 
  if (check_results_query_result_set(&system_record.oid, 1,qrs)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
    printf("Query: [%s]\n", query);
    test_res = TEST_ERROR;
    goto finished;
  }

finished:
  if (qrs != NULL) {
    hc_qrs_free(qrs);
  }
  if (query != NULL) {
    free(query);
  }
  if (values != NULL) {
    free_array(values, 3);
  }
  if (names != NULL) {
    free_array(names, 3);
  }
  if (nvr != NULL) {
    hc_nvr_free(nvr);
  }
  PRINT_TEST_RESULT("test_logical_operators", test_res, "null");
  return test_res;
}

int
test_boolean_operators(test_case_t* tc)
{
  hcerr_t hcerr;
  hcerr_t expected_res;
  hc_system_record_t system_record;
  int k;
  int test_res = TEST_PASS;
  hc_nvr_t* nvr = NULL;
  hc_long_t count = -1;
  char** names = NULL;
  char** values = NULL;
  char** argv = tc->argv;
  int queryLength;
  hc_query_result_set_t *qrs = NULL;
  char* query;

  INIT_TEST();
  expected_res = atoi(argv[0]);

  names = get_random_entry_from_schema(schema, HC_STRING_TYPE, 2, 
                                       "perf_qafirst");
  
  values = (char**) calloc(sizeof(char*), 2);
  CHECK_MEM(values);

  values[0] = strdup("str_val1");
  values[1] = strdup("str_val22");
  CHECK_MEM(values[0]);
  CHECK_MEM(values[1]);

  hcerr = hc_nvr_create(session, 1, &nvr);
  if (expected_res != hcerr) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to create name value rec\n");
    test_res = TEST_ERROR;
    goto finished;
  }

  for (k = 0; k < 2; k++) {
    hcerr = hc_nvr_add_string(nvr, names[k], values[k]);
    if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to add string value to nvr\n");
      test_res = TEST_ERROR;
      goto finished;
    }
  }

  hcerr = hc_store_metadata_ez(session, &data_oid, nvr, &system_record);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to store metadata %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }

   queryLength = 2+  strlen(names[0]) + 2 + strlen("<'") +
    strlen("str_val11") + strlen("' AND ") + 2 + strlen(names[1]) + 2 +
    strlen(">'")  + strlen("str_val2") + strlen("'") + 1;
  
  query = (char*) malloc(queryLength);
  CHECK_MEM(query);

  (void) strcpy(query, "\"");
  (void) strcat(query,names[0]);
  (void) strcat(query, "\"");
  (void) strcat(query,"<'");
  (void) strcat(query,"str_val11");
  (void) strcat(query,"' AND ");
  (void) strcat(query, "\"");
  (void) strcat(query,names[1]);
  (void) strcat(query, "\"");
  (void) strcat(query,">'");
  (void) strcat(query,"str_val2");
  (void) strcat(query,"'");

  hcerr = hc_query_ez(session, query, NULL, 0, 100, &qrs);
  if (expected_res != hcerr) {
      hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to query %s\n",
                  format_error());
      test_res = TEST_ERROR;
      goto finished;
  }
 
  if (check_results_query_result_set(&system_record.oid, 1, qrs)) {
    hc_test_log(LOG_ERROR_LEVEL, NULL, "Failed to validate query\n");
    printf("Query: [%s]\n", query);
    test_res = TEST_ERROR;
    goto finished;
  }

finished:
  if (qrs != NULL) {
	  hc_qrs_free(qrs);
  }
  if (query != NULL) {
    free(query);
  }
  if (values != NULL) {
    free_array(values, 2);
  }
  if (names != NULL) {
    free_array(names, 2);
  }
  if (nvr != NULL) {
    hc_nvr_free(nvr);
  }
  PRINT_TEST_RESULT("test_boolean_operators", test_res, "null");
  return test_res;
}
