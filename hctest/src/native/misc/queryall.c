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



/*
 *  queryall queries all OIDs in an HC system
 *  to compare to hadb, on some node:
 *    $ /config/hadb_install/4/bin/clusql localhost:15005 system+superduper
 *    SQL: select count (*) from T_SYSTEM;
 */

#include <stdio.h>
#include <stdlib.h>

#include "hcclient.h"

int
main(int argc, char *argv[])
{
	hcerr_t hcerr;
	hc_session_t *session = NULL;
	hc_query_result_set_t *qrs = NULL;
	hc_oid returnOID;

	if (argc != 2) {
		fprintf(stderr, "usage: %s <datavip>\n", argv[0]);
		exit(1);
	}

	hcerr = hc_session_create_ez(argv[1], 8080, &session);
	if (hcerr != HCERR_OK) {
		fprintf(stderr, "hc_session_create_ez: %s\n", 
					hc_decode_hcerr(hcerr));
		exit(1);
	}

	hcerr = hc_query_ez(session, "system.object_id is not null", NULL, 0,
							2000, &qrs);
	if (hcerr != HCERR_OK) {
		fprintf(stderr, "hc_query_ez: %s\n", hc_decode_hcerr(hcerr));
		exit(1);
	}

	while (1) {
		int finished = 0;
		hcerr =  hc_qrs_next_ez(qrs, &returnOID, NULL, &finished);
		if (hcerr != HCERR_OK) {
			fprintf(stderr, "hc_qrs_next_ez: %s\n", 
					hc_decode_hcerr(hcerr));
			exit(1);
		}
		if (finished)
			break;
		printf("%s\n", returnOID);
	}
	hcerr = hc_qrs_free(qrs);
	if (hcerr != HCERR_OK) {
		fprintf(stderr, "hc_qrs_free: %s\n", 
					hc_decode_hcerr(hcerr));
		exit(1);
	}
	exit(0);
}
