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
#include <sys/types.h>
#include <sys/stat.h>
#include "platform.h"

#include "hc.h"
#include "hcoa.h"
#include "hcnvoa.h"
#include "hcoaez.h"
#include "hctestcommon.h"
#include "hctestutil.h"
#include "sha1.h"

#define FILE_SIZE 2000000

static char *ez_tags[] = {"capiez_rangeretrieve"};
int hcez_rangeretrieve_simpletest_exec(test_case_t *);
/*
int hcnb_storedata_simpletest_exec(test_case_t *);
char * data_source_types [] = {BUFFER_SOURCE,FILE_SOURCE};
*/

void hcez_rangeretrieve_load_tests(hctest_env_t *test_env) {
	char **file_size_ptr = NULL;
	char **cur_file_size = NULL;
	char *max_file_size = NULL;
	int number_of_file_sizes;
	int i,j;
	char *argv[1];

	argv[0] = "0";
	add_test_to_list(create_and_tag_test_case("capiez_rangeretrieve",
		       	1, argv,
			(int (*)(test_case_t*))hcez_rangeretrieve_simpletest_exec,
			ez_tags,1,test_env),test_env,FALSE);
}

static long read_from_file(void*, char*, long);
static long write_to_file(void*, char*, long);

int hcez_rangeretrieve_simpletest_exec(test_case_t *tc) {	

	hctest_env_t *env;
        int port;
        char *host;
        int c, fd, fdout;
	int i=0;
	char *fname1, *fname2;
	char *alphabet="abcdefghijklmnopqrstuvwxyz";
	hc_type_t type;
        hc_session_t *session = NULL;
	hc_system_record_t system_record;
	hc_string_t string_value = "range_test";
	hc_long_t firstbyte = 0;
	hc_long_t lastbyte = 0;
	struct stat statbuf;
	char buf[1024];

        hcerr_t reth;
        int test_result=TEST_PASS;

        env=tc->test_env;
	port = env->data_vip_port;
        host = env->data_vip;
printf("host %s  port %d\n", host, port);
	/* hc_init has already been called, but we follow the
	   other examples */
	hc_init(malloc,free,realloc);

	if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_session_create_ez returned %s\n", 
                   	hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	/* Store some Data */
	fname1 = hc_tmpnam();
	printf("opening tmp file %s\n", fname1);
	if ((fd=open(fname1, O_CREAT|O_RDWR, 0644)) < 0) {
                perror(fname1);
                test_result = TEST_FAIL;
		goto done;
	}
	while (i<FILE_SIZE) {
		if (write(fd, alphabet, 26) == -1) {
			perror("write");
			exit(1);
		}
		i=i+26;
	}
	lseek(fd,0,0);
	if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, 
					NULL, &system_record)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_store_both_ez returned %s\n", 
			hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
		goto done;
	}
	close(fd);
	unlink(fname1);
	fname2 = hc_tmpnam();
	printf("Test valid range_retrieves to tmp file %s\n", fname2);
	//sprintf(fname2, "/tmp/harness_rr2.%d", getpid());
	if ((fdout = open(fname2, O_CREAT|O_RDWR, 0666)) == -1) {
		perror(fname2);
		exit(1);
	}
	if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout, &system_record.oid, 0, 25)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_range_retrieve_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (fstat(fdout, &statbuf) == -1) {
		perror("stat f2");
		exit(1);
	}
	if (statbuf.st_size != 26) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_range_retrieve_ez retrieved %d, expected 26\n",
			statbuf.st_size);
		test_result = TEST_FAIL;
		goto done;
	}
	lseek(fdout, 0,0);
	if (read(fdout, buf, 26) != 26) {
		perror("read f2");
		exit(1);
	}
	if (memcmp(buf, alphabet, 26)) {
		buf[26] = '\0';
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"expected alphabet, got %s\n", buf);
		test_result = TEST_FAIL;
		goto done;
	}

        /*
         * close and truncate on open cause using
         * ftruncate() does not work on all platforms
         */
        close(fdout);
        if ((fdout = open(fname2, O_TRUNC|O_RDWR, 0666)) == -1) {
                perror(fname2);
                exit(1);
        }

	if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout, &system_record.oid, 3, 25)) != HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_range_retrieve_ez returned %s\n", 
			hc_decode_hcerr(reth));
		test_result = TEST_FAIL;
		goto done;
	}
	if (fstat(fdout, &statbuf) == -1) {
		perror("stat f2");
		exit(1);
	}
	if (statbuf.st_size != 23) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"hc_range_retrieve_ez retrieved %d, expected 23\n",
			statbuf.st_size);
		test_result = TEST_FAIL;
		goto done;
	}
	lseek(fdout, 0,0);
	if (read(fdout, buf, 23) != 23) {
		perror("read f2");
		exit(1);
	}
	buf[23] = '\0';
	// printf("testing alphabet-abc, got [%s]\n", buf);
	if (memcmp(buf, alphabet+3, 23)) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
			"expected alphabet-abc, got %s\n", buf);
		test_result = TEST_FAIL;
		goto done;
	}

	printf("Test invalid range_retrieve\n");
        printf("Large negative startbyte\n");
  	firstbyte=-2;
	lastbyte=100;
        if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout,
 &system_record.oid, firstbyte, lastbyte)) == HCERR_OK) {
                hc_test_log(LOG_ERROR_LEVEL,NULL,
			"invalid hc_range_retrieve_ez returned %s\n", 
			hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
		goto done;
        }
/*
	this should be ok?
	printf("lastbyte beyond end of file\n");
  	firstbyte=0;
	lastbyte=FILE_SIZE+1000;
        if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout,
 &system_record.oid, firstbyte, lastbyte)) == HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"invalid hc_range_retrieve_ez returned %s\n", 
			hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
		goto done;
        }
*/
	printf("lastbyte less than firstbyte\n");
  	firstbyte=10;
	lastbyte=0;
        if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout,
 &system_record.oid, firstbyte, lastbyte)) == HCERR_OK) {
		hc_test_log(LOG_ERROR_LEVEL,NULL,
                	"invalid hc_range_retrieve_ez returned %s\n", 
			hc_decode_hcerr(reth));
                test_result = TEST_FAIL;
		goto done;
        }

done:
	if (test_result == TEST_FAIL) {
		printf("test_range_retrieve FAILED\n");
	} else {
		printf("test_range_retrieve PASSED\n");
	}
	unlink(fname2);
	hc_session_free(session);

	return test_result;

}


static long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

static long write_to_file(void* stream, char* buff, long n)
{
	        int pos = 0;
        while (pos < n)
        {
                int i = write ((int) stream, buff + pos, n - pos);
                if (i < 0)
                        return i;
                if (i == 0)
                        break;
                pos += i;
        }

        return pos;
}

