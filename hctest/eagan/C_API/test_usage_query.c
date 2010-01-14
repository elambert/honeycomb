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


#include <fcntl.h>
#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include <hc.h>
#include <hcclient.h>

char *host;
char *port_char;
int port;

void usage()
{
        printf("usage: test_usage_query host port\n" );
	exit(0);
}

long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

int main(argc, argv)
        int argc;
        char **argv;

{
        int c, fd, index, i;
	hc_long_t schema_count = 0;
	int finished = 0;
	char *name;
	hc_type_t type;
	hc_long_t countp;
	hc_long_t nslots=10000;
        hc_session_t *session = NULL;
	hc_schema_t *current_schema = NULL;
	hc_query_result_set_t *rset;
	hc_nvr_t *nvr = NULL;
	hc_system_record_t system_record;
	char typeName[20];
	hc_long_t long_value = 10000;
	hc_double_t double_value = 10000;
	hc_string_t string_value = "stringvalue";
	hc_long_t long_return = 0;
        hc_double_t double_return = 0;
        hc_string_t string_return = "           ";
	hc_long_t nvr_count = 0;
	hc_query_result_set_t *resp;
	hc_oid returnOID;
	hc_value_t valuep;
	char *selects[] = {"test_id", "longsmall"};
	int n_selects = 2;
	char *string_large;
	char *query_string;

        hcerr_t reth;
        int32_t response;
        char *errstr;
        int test_fail=0;

        while ((c = getopt(argc, argv, "h")) != -1)
                switch (c) {
                case 'h':
                        usage();
                        break;
                default:
                        usage();
        }
        argc -= optind;
        argv += optind;
        if (argc != 2)
                usage();
        host = argv[0];
        argc -= optind;
        argv += optind;
        if (argc != 1)
                usage();
        port_char = argv[0];
        port = atoi(port_char);

	string_large=(char *)malloc(1000);
	query_string=(char *)malloc(1000);
	if ((reth = hc_init(malloc,free,realloc)) != HCERR_OK)
		printf("hc_init returned %s\n", hc_decode_hcerr(reth));
	if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK)
		printf("hc_session_create_ez returned %s\n", 
                   hc_decode_hcerr(reth));
	/* check that qa schema is loaded */
        if ((reth = hc_session_get_schema(session, &current_schema)) != HCERR_OK) {
		printf("hc_session_get_schema returned %s\n",
		   hc_decode_hcerr(reth));
		test_fail++;
	} else {
		if ((reth = hc_schema_get_count(current_schema, &schema_count))
		  != HCERR_OK)
			printf("hc_schema_get_count returned %s\n",
			  hc_decode_hcerr(reth));
		for (index = 0; index < schema_count; index++) {
		   if ((reth = hc_schema_get_type_at_index
		      (current_schema, index, &name, &type)) != HCERR_OK) 
		        printf("hc_schema_get_type_at_index returned %s\n",
		        hc_decode_hcerr(reth));
		    switch(type) {
			case HC_LONG_TYPE:
                                strcpy(typeName, "long");
                                break;

                        case HC_DOUBLE_TYPE:
                                strcpy(typeName, "double");
                                break;

                        case HC_STRING_TYPE:
                                strcpy(typeName, "string");
                                break;

                        default:
                                strcpy(typeName, "unknown");
		     }
		     printf("name=%s, type=%s\n", name, typeName);
		}
	}
	/* Store some Data and Metadata */
	
	if ((reth = hc_nvr_create(session, nslots, &nvr)) != HCERR_OK) {
		printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		exit(1);
	}
	if ((reth = hc_nvr_add_long(nvr, "longsmall", long_value)) != HCERR_OK) {
                printf("hc_nvr_add_long returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
        if ((reth = hc_nvr_add_double(nvr, "doublelarge", double_value)) != HCERR_OK) {
                printf("hc_nvr_add_double returned %s\n", 
		   hc_decode_hcerr(reth));
                test_fail++;
        }
        if ((reth = hc_nvr_add_string(nvr, "test_id", string_value)) != HCERR_OK) {
                printf("hc_nvr_add_string returned %s\n", 
		  hc_decode_hcerr(reth));
                test_fail++;
        }
	if ((reth = hc_nvr_get_count(nvr, &nvr_count)) != HCERR_OK) {
		printf("hc_nvr_get_count returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	if (nvr_count != 3) {
		printf("hc_nvr_get_count returned nvr_count; should be 3\n",
			nvr_count);
		test_fail++;
	}
	if ((reth = hc_nvr_get_long(nvr, "longsmall", &long_return)) != HCERR_OK) {
                printf("hc_nvr_get_long returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	} else {
		if (long_return != long_value) {
			printf("value returned by nvr_get_long incorrect\n");
			test_fail++;
		}
	}
        if ((reth = hc_nvr_get_double(nvr, "doublelarge", &double_return)) != HCERR_OK) {
                printf("hc_nvr_get_double returned %s\n", 
		  hc_decode_hcerr(reth));
                test_fail++;
        } else {
		if (double_return != double_value) {
			printf("value returned by nvr_get_double incorrect\n");
			test_fail++;
		}
	}
        if ((reth = hc_nvr_get_string(nvr, "test_id", &string_return)) != HCERR_OK) {
                printf("hc_nvr_get_string returned %s\n", 
		  hc_decode_hcerr(reth));
                test_fail++;
        } else {
		if (strcmp(string_return, string_value) != 0) {
			printf("String returned by hc_nvr_get_string incorrect.\n");
			test_fail++;
		}
	}
	if ((fd=open("/etc/passwd", O_RDONLY)) < 0) {
		perror("open");
		exit(1);
	}
	if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, nvr, 
		&system_record) != HCERR_OK)) {
			printf("hc_store_both_ez returned %s\n", 
			  hc_decode_hcerr(reth));
			exit(1);
	}
	/* Store metadata for previous OID */
	if ((reth = hc_store_metadata_ez(session, &system_record.oid, nvr, &system_record))
		!= HCERR_OK) {
			printf("hc_store_metadata_ez returned %s\n",
			  hc_decode_hcerr(reth));
			test_fail++;
	}
	close(fd);
	printf("Test valid query\n");
	if ((reth = hc_query_ez(session, "longsmall>0", &resp)) != HCERR_OK) {
		printf("hc_query_ez returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	if ((reth = hc_qrs_next_ez(resp, &returnOID, &finished)) != HCERR_OK) {
		printf("hc_qrs_next_ez returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	} else {
		if (finished != 0) {
			printf("hc_qrs_next_ez didn't find any records\n");
			printf("finished = %d\n", finished);
			test_fail++;
		}
	}
	if ((reth = hc_qrs_free(resp)) != HCERR_OK) {
                printf("hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
/* For release 1.0, hc_unique_values_ez should return HCERR_NOT_YET_IMPLEMENTED
*/
	if ((reth = hc_unique_values_ez(session, "longsmall>0", "longsmall",
		&resp)) != HCERR_NOT_YET_IMPLEMENTED) {
		printf("hc_unique_values_ez returned %s\n", 
		  hc_decode_hcerr(reth));
		test_fail++;
	}
	if ((reth = hc_uvrs_next_ez(resp, &valuep, &finished)) != 
		HCERR_NOT_YET_IMPLEMENTED) {
		  printf("hc_uvrs_next_ez returned %s; should return HCERR_NOT_YET_IMPLEMENTED\n",
		  hc_decode_hcerr(reth));
		  test_fail++;
	}
	if ((reth = hc_uvrs_free(resp)) != HCERR_NOT_YET_IMPLEMENTED) {
		printf("hc_uvrs_free returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	/* Attempt to query for attribute that does not exist in schema
	   this should generate an error but currently doesn't until qrs_free
        */
	if (hc_query_ez(session, "attributedoesnotexist=notthere", &rset) != 
           HCERR_OK) {
		printf("No error returned for attribute that does not exist\n");
		test_fail++;
	}
	if ((reth = hc_qrs_free(rset)) != HCERR_BAD_REQUEST) {
		printf("hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	/* Attempt to use reserved word in query */
	if (hc_query_ez(session, "test_id=select", &rset) != HCERR_OK) {
		printf("query with reserved word fails\n");
		test_fail++;
	}

	/* Attempt query that has invalid boolean 
           Errors are caught on call to hc_qrs_free which seems late but
	   error can't be caught on hc_query_ez without a redesign */
	if (hc_query_ez(session, "longsmall=>0", &rset) != HCERR_OK) {
		printf("Error returned for invalid boolean %s\n",
			hc_decode_hcerr(reth));
		test_fail++;
	}
	if ((reth = hc_qrs_free(rset)) != HCERR_BAD_REQUEST) {
		printf("hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	printf ("Testing query plus\n");
	if ((reth = hc_query_plus_ez(session, "longsmall>0", selects, n_selects,
		&resp)) != HCERR_OK) {
		printf("hc_query_plus_ez returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	if ((reth = hc_qprs_next_ez(resp,&nvr,&finished)) != HCERR_OK) {
		printf("hc_qprs_next_ez returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	} else {
		if (finished != 0) {
		printf("hc_qprs_next_ez didn't find any records\n");
		test_fail++;
		}
	}
	/* negative case for incorrect boolean on hc_query_plus_ez */
	if ((reth = hc_query_plus_ez(session, "longsmall=>0", selects, n_selects,
                &resp)) != HCERR_OK) {
                printf("hc_query_plus_ez with invalid boolean returned %s\n", 
			hc_decode_hcerr(reth));
                test_fail++;
        }
	/* if ((reth = hc_nvr_free(nvr)) != HCERR_OK) {
		printf("hc_nvr_free returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	if ((reth = hc_nvr_create(session, nslots, &nvr)) != HCERR_OK) {
		printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	} */
	hc_session_get_status(session, &response, &errstr);
/*	printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response); */
	printf("Testing 256 character string\n");
	for(i=0; i<32; i++) {
		strcat(string_large, "eightchr");
	}
	strcat(query_string, "test_id='");
	strcat(query_string, string_large);
	strcat(query_string, "'");
	if ((reth = hc_nvr_add_string(nvr, "test_id", string_large)) != HCERR_OK) {
		printf("hc_nvr_add_string returned %s\n",
			hc_decode_hcerr(reth));
		test_fail++;
	}
	printf("%s\n", string_large);
	hc_session_get_status(session, &response, &errstr);
/*	printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response); */
	if ((reth = hc_store_metadata_ez(session, &system_record.oid, nvr,
		&system_record) != HCERR_OK)) {
			printf("hc_store_metadata_ez returned %s\n",
				hc_decode_hcerr(reth));
	}
	if (hc_query_ez(session, query_string, &rset) != HCERR_OK) {
                printf("Error returned for 256 char query string\n");
                test_fail++;
        }
	if ((reth = hc_qrs_free(rset)) != HCERR_OK) {
                printf("hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
	if ((reth = hc_nvr_free(nvr)) != HCERR_OK) {
		printf("hc_nvr_free returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	if ((reth = hc_nvr_create(session, nslots, &nvr)) != HCERR_OK) {
		printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	printf("Testing 512 character string\n");
	hc_session_get_status(session, &response, &errstr);
/*	printf("reth = %s; response = %d\n", hc_decode_hcerr(reth), response); */
	strcpy(string_large, "");
	strcpy(query_string, "test_id='");
	for(i=0; i<64; i++) {
		strcat(string_large, "eightchr");
		strcat(query_string, "eightchr");
	}
	strcat(query_string, "'");
	if ((reth = hc_nvr_add_string(nvr, "test_id", string_large)) != HCERR_OK) {
		printf("hc_nvr_add_string returned %s\n",
			hc_decode_hcerr(reth));
		test_fail++;
	}
	printf("%s\n", string_large);
	if ((reth = hc_store_metadata_ez(session, &system_record.oid, nvr,
		&system_record) != HCERR_OK)) {
			printf("hc_store_metadata_ez returned %s\n",
				hc_decode_hcerr(reth));
	}
	if (hc_query_ez(session, query_string, &rset) != HCERR_OK) {
                printf("Error returned for 512 char query string\n");
                test_fail++;
        }
	if ((reth = hc_qrs_free(rset)) != HCERR_OK) {
                printf("hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
	printf("Testing 513 character string\n");
        strcpy(string_large, "");
        strcpy(query_string, "test_id='");
        for(i=0; i<64; i++) {
                strcat(string_large, "eightchr");
                strcat(query_string, "eightchr");
        }
	strcat(string_large, "1");
	strcat(query_string, "1'");
	if ((reth = hc_nvr_add_string(nvr, "test_id", string_large)) != HCERR_OK) {
		printf("hc_nvr_add_string returned %s\n",
			hc_decode_hcerr(reth));
		test_fail++;
	}
	printf("%s\n", string_large);
	if ((reth = hc_store_metadata_ez(session, &system_record.oid, nvr,
		&system_record) != HCERR_OK)) {
			printf("hc_store_metadata_ez returned %s\n",
				hc_decode_hcerr(reth));
	}
	if (hc_query_ez(session, query_string, &rset) != HCERR_OK) {
                printf("Error returned for 513 char query string\n");
                test_fail++;
        }
	if ((reth = hc_qrs_free(rset)) != HCERR_OK) {
                printf("hc_qrs_free returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
	if ((reth = hc_session_free(session)) != HCERR_OK)
		printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
	hc_cleanup();
	if (test_fail > 0) {
		printf("test_usage_query FAILED\n");
		exit(1);
	} else {
		printf("test_usage_query PASSED\n");
		exit(0);
	}
}
