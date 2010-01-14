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



#include <signal.h>
#include <setjmp.h>
#include <stdio.h>
#include <errno.h>
#include <stdlib.h>

#include <hc.h>
#include <hcclient.h>

char *host;
char *port_char;
int port;
int test_fail=0;

void usage()
{
	printf("usage: test_usage_sessions host port\n" );
	exit(0);
}

int main(argc, argv)
        int argc;
        char **argv;

{
	int c;
	hc_session_t *session = NULL;
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

/* 	Try to free session pointer without ever doing
	hc_init or hc_session_create */
        if ((reth = hc_session_free(session)) != HCERR_NULL_SESSION)
                printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
        /* Try to create session without first doing hc_init
	   Should do implicit hc_init then create session */
        if ((reth = hc_session_create_ez (host, port, &session)) != HCERR_OK) {
		printf("hc_session_create_ez returned %s\n",
			hc_decode_hcerr(reth));
		printf("TEST FAIL - unable to create session w/o hc_init\n");
		test_fail++;
	}
	/* free session then try to status it */
	if ((reth = hc_session_free(session)) != HCERR_OK) 
		printf("hc_session_free returned %s\n", hc_decode_hcerr(reth));
	if ((reth = hc_session_get_status (session, &response, &errstr)) == 
	  HCERR_OK) {
		if (response == 200) {
		  printf("hc_session_get_status returned HCERR_OK and response = 200 on a freed session\n", 
		    hc_decode_hcerr(reth));
		  test_fail++;
		}
	}
	
	/* try to create session with incorrect port number */
        if ((reth = hc_session_create_ez (host, port+1, &session)) == HCERR_OK){
		printf("hc_session_create_ez returned %s\n", 
		  hc_decode_hcerr(reth));
                printf("TEST FAIL - able to create session with bad port\n");
		test_fail++;
        }
	/* Free any session that may have been created */
	hc_session_free(session);

/* Create Session and free it*/
	/* if (reth = (hc_init(malloc, free, realloc)) != HCERR_OK) {
		printf("hc_init returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	} */
	if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK) {
		printf("hc_session_create_ez returned %s\n", 
		  hc_decode_hcerr(reth));
		test_fail++;
	}
	if ((reth = hc_session_get_host(session, &host, &port)) != HCERR_OK) {
		printf("hc_session_create_ez returned %s\n", 
		  hc_decode_hcerr(reth));
		test_fail++;
	} else {
		printf("hc_session_get_host returned host=%s port=%d\n",
			host, port);
	}
	if ((reth = hc_session_get_status (session, &response, &errstr)) != 
	  HCERR_OK) {
		printf("hc_session_get_status returned %s\n", 
		  hc_decode_hcerr(reth));
		test_fail++;
	} else {
		if (response != 200) {
		  printf("hc_session_get_status returned response=%d errstr=%s\n",
			response, errstr);
		}

	}
	if ((reth = hc_session_free(session)) !=HCERR_OK) {
		printf("hc_session_free retruned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	}
	if (test_fail > 0) {
		printf("test_usage_sessions FAILED\n");
		exit(1);
	}
	else {
		printf("test_usage_sessions PASSED\n");
	}
}
