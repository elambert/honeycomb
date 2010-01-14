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

#include "hcclient.h"

#include "hctestutil.h"

#define LONG_NAME "system.test.type_long"
#define STRING_NAME "system.test.type_string"

// we declare this function here because it is "hidden" so not in .h
extern hcerr_t hc_store_both_cell_ez(hc_session_t *session,
                              int cellid,
                              read_from_data_source data_source_reader,
                              void *cookie,
                              hc_nvr_t *pubnvr,
                              hc_system_record_t *system_record);

int max_oids_cell = 5;
int ncells = 0;
int *cell_ids = NULL;
int *counts = NULL;
int endLevel = 0;
hcerr_t res;
hc_session_t *session;
hc_nvr_t *nvr = NULL;
hc_long_t millis;

static void usage(char *pname) {
	fprintf(stderr,
		"Usage: %s <datavip> <cell-list> [<max_oid_per_cell>]\n", 
		pname);
	fprintf(stderr, "\tmax_oid_per_cell default %d (1.5 hr on 4 cells)\n",
		max_oids_cell);
	fprintf(stderr, " e.g.  %s dev303-data 3,12,21,26\n", pname);
	exit(1);
}

static void recurse(int level);
static void doit();

int main(int argc, char *argv[]) {

	char *str;
	time_t t;

	if (argc < 3  ||  argc > 4)
	    usage(argv[0]);

	// parse cell list

	str = strtok(argv[2], ",");
	cell_ids = (int *) malloc(sizeof(int));

	cell_ids[0] = atoi(str);
	ncells = 1;
	while ((str = strtok(NULL, ",")) != NULL) {
		cell_ids = (int *) realloc(cell_ids, 
					sizeof(int) * (ncells + 1));
		cell_ids[ncells] = atoi(str);
		ncells++;
	}
	counts = (int *) malloc(ncells * sizeof(int));
	endLevel = ncells - 1;

	// see if max_oids_cell set

	if (argc == 4)
		max_oids_cell = atoi(argv[3]);

	// initialize 'unique' key for query

	time(&t);
	millis = (hc_long_t) t * 1000;

	// init hc

	hc_init(malloc,free,realloc);
	res = hc_session_create_ez(argv[1], 8080, &session);
	if (res != HCERR_OK) {
		fprintf(stderr,
			"Session Create failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		usage(argv[0]);
	}

	res = hc_nvr_create(session, 2, &nvr); 
	res = hc_nvr_add_string(nvr, STRING_NAME, "mcellQuery.c");

	// start recursion

	recurse(0);
}

static void recurse(int level) {
	int i;

	for (i=0; i<max_oids_cell+1; i++) {
		counts[level] = i;
		if (level == endLevel)
			doit();
		else
			recurse(level+1);
	}
}

static char *itoa(int num) {
	char buf[1024];

	sprintf(buf, "%d", num);

	return buf;
}

static void doit() {

	hc_hashlist_t *stored = NULL;
	char query[1024];
	char count_str[1024];
	hc_query_result_set_t *rset = NULL;
	hc_system_record_t sys_rec;
	int finished;
	int max_fetch = 0;
	int i, j, nStored, nPerFetch, nGotten;
	hc_random_file_t *stored_r_file = NULL;

	// Initialize test metadata value 

	millis++;

        res = hc_nvr_add_long(nvr, LONG_NAME, millis);
	if (res != HCERR_OK) {
		fprintf(stderr,
			"hc_nvr_add_long failed. Returned code of %s\n",
			hc_decode_hcerr(res));
		exit(1);
	}
	sprintf(query, "%s="LL_FORMAT, LONG_NAME, millis);

	count_str[0] = '\0';
	for (i=0; i<ncells; i++) {
		if (i)
			strcat(count_str, ", ");
		strcat(count_str, itoa(counts[i]));
	}

	// do stores

	printf("storing [%s]\n", count_str);
	stored = create_hashlist(1024);
	nStored = 0;
	for (i=0; i<ncells; i++) {
	    if (counts[i] > max_fetch)
		max_fetch = counts[i];
	    for (j=0; j<counts[i]; j++) {
		
	        stored_r_file  = create_random_file(4); 

	        res = hc_store_both_cell_ez(session, 
                                cell_ids[i],
				read_from_random_data_generator, 
				stored_r_file, nvr, &sys_rec);
	        if (res != HCERR_OK) {
		    fprintf(stderr,
			"Store of file to cell %d failed: %s\n",
	                cell_ids[i],
			hc_decode_hcerr(res));
		    exit(1);
	        } 
	        hash_put(sys_rec.oid, "noval", stored);

		free_r_file(stored_r_file);
	        stored_r_file = NULL;
	        nStored++;

	    }
	}
	if (nStored == 0)
		return;
	max_fetch += 2;

	printf("Total stored %d\n", nStored);

	// do queries
	printf("Query: [%s]\n", query);
	for (nPerFetch=1; nPerFetch<max_fetch; nPerFetch++) {
	    hc_hashlist_t *gotten = create_hashlist(1024);
	    printf("Fetch size = %d\n", nPerFetch);
	    nGotten = 0;

	    res = hc_query_ez(session, query, NULL, 0, nPerFetch, &rset);
	    if (res != HCERR_OK) {
		fprintf(stderr,
	             "Query failed: %s\n", hc_decode_hcerr(res));
		exit(1);
	    }
	    finished = 0;
	    while (1) {
	        hc_oid return_oid;
	        res = hc_qrs_next_ez(rset, &return_oid, NULL, &finished);
	        if (res != HCERR_OK) {
		    fprintf(stderr,
	                 "hc_qrs_next_ez failed: %s\n", hc_decode_hcerr(res));
		    exit(1);
	        }
	        if (finished)
	            break;
	        if (!hashlist_contains_key(return_oid, stored)) {
		    fprintf(stderr,
	                 "Unexpected oid returned [%s]\n", return_oid);
		    exit(1);
	        }
                //printf("got %s\n", return_oid);
	        if (hashlist_contains_key(return_oid, gotten)) {
		    fprintf(stderr,
	                 "Duplicate oid returned (oid %d) [%s]\n", 
	                 nGotten, return_oid);
		    exit(1);
	        }
	        hash_put(return_oid, "noval", gotten);
	        nGotten++;
	    }
	    res = hc_qrs_free(rset);
	    if (res != HCERR_OK) {
		fprintf(stderr,
	             "hc_qrs_free failed: %s\n", hc_decode_hcerr(res));
		exit(1);
	    }
	    free_hashlist(gotten);

	    if (nGotten != nStored) {
		fprintf(stderr,
	             "Stored %d but got %d\n", nStored, nGotten);
		exit(1);
	    }
	}
	free_hashlist(stored);
	stored = NULL;
}

