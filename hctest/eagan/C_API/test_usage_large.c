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

int NUM_RESULTS = 1000;  /* How many is large */

void usage()
{
        printf("usage: test_usage_large host port\n" );
        exit(0);
}

long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

void main(argc, argv)
        int argc;
        char **argv;

{
	int c, fd;
	int finished = 0;
	hc_session_t *session = NULL;
	int test_fail = 0;
	hc_long_t nslots=NUM_RESULTS;
	hc_long_t long_value = 0;
	hc_oid returnOID;
	hc_nvr_t *nvr = NULL;
	hc_system_record_t system_record;
	hc_query_result_set_t *resp;
	hcerr_t reth;
	char *selects[] = {"longsmall"};
	int n_selects = 1;
	hc_long_t count = 0;
	hc_value_t valuep;
	char *namep = NULL;

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

        if ((reth = hc_init(malloc,free,realloc)) != HCERR_OK)
                printf("hc_init returned %s\n", hc_decode_hcerr(reth));
        if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK)
                printf("hc_session_create_ez returned %s\n", 
		  hc_decode_hcerr(reth));

        if ((fd=open("/etc/passwd", O_RDONLY)) < 0) {
                perror("open");
                exit(1);
        }

	printf("Start storing results\n");
        if ((reth = hc_nvr_create(session, nslots, &nvr)) != HCERR_OK) {
                printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
                exit(1);
        }


        if (hc_store_both_ez(session, &read_from_file, (void *)fd, nvr,
                &system_record) != HCERR_OK) {
                        printf("hc_store_both_ez returned %s\n", 
			  hc_decode_hcerr(reth));
                        exit(1);
        }
	printf("Start inserting metadata\n");
	for (long_value=1; long_value<NUM_RESULTS; long_value++) {
		if ((reth = hc_nvr_add_long(nvr, "longsmall", long_value)) 
		  != HCERR_OK) {
                	printf("hc_nvr_add_long returned %s\n", 
			  hc_decode_hcerr(reth));
                	test_fail++;
        	}
		hc_nvr_get_count(nvr, &count);
		if ((reth = hc_nvr_get_value_at_index(nvr, long_value, 
		  &namep, &valuep)) != HCERR_OK) {
			printf("hc_nvr_get_value_at_index returned %s\n",
			  hc_decode_hcerr(reth));
			test_fail++;
		}
		if ((reth = hc_store_metadata_ez(session, &system_record.oid,
		   nvr, &system_record)) != HCERR_OK) {
                        printf("hc_store_metadata_ez returned %s\n", 
			  hc_decode_hcerr(reth));
                        test_fail++;
		}
        }
	if (hc_session_free(session) !=0) {
                perror("hc_session_free");
                test_fail++;
        }
	printf("Start retrieving results\n");
	/* First with query plus */
        if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK)
               printf("hc_session_create_ez returned %s\n",
                 hc_decode_hcerr(reth));
	if ((reth = hc_query_plus_ez(session, "longsmall>0", selects, n_selects,
                &resp)) != HCERR_OK) {
                printf("hc_query_plus_ez returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
	for(long_value=1; long_value<NUM_RESULTS; long_value++) {
		if ((reth = hc_qprs_next_ez(resp, &nvr, &finished))
		  != HCERR_OK) {
			  printf("hc_qprs_next_ez returned %s\n",
			    hc_decode_hcerr(reth));
			  test_fail++;
		} else {
			if(finished != 0) {
			  printf("hc_qprs_next_ez didn't find enough records - %d found\n",
			    long_value);
			  test_fail++;
			}
		}
	}
	if (hc_session_free(session) !=0) {
                printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }

        if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK)
                printf("hc_session_create_ez returned %s\n", 
		  hc_decode_hcerr(reth));
        if ((reth = hc_query_ez(session, "longsmall>0", &resp)) != HCERR_OK) {
                printf("hc_query_ez returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }

	for (long_value=1; long_value<NUM_RESULTS; long_value++) {
		if ((reth = hc_qrs_next_ez(resp, &returnOID, &finished)) 
		  != HCERR_OK) {
   	            printf("hc_qrs_next_ez returned %s\n", hc_decode_hcerr(reth));
       	            test_fail++;
		  printf("%d\n", long_value);
        	} else {
                	if (finished != 0) {
                          printf("hc_qrs_next_ez only found %d records\n",
				long_value);
                          printf("finished = %d\n", finished);
                          test_fail++;
                	}
		}
		if ((reth = hc_delete_ez(session, &returnOID)) != HCERR_OK) {
			printf("hc_delete_ez returned %s\n", 
			  hc_decode_hcerr(reth));
			test_fail++;
		}
        }
        if ((reth = hc_session_free(session)) != HCERR_OK)
                printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
	if (test_fail > 0) {
                printf("test_usage_large FAILED\n");
                exit(1);
        } else {
                printf("test_usage_large PASSED\n");
                exit(0);
        }
}
