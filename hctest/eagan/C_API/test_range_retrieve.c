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

#define FILE_SIZE 2000000

char *host;
char *port_char;
int port;

void usage()
{
        printf("usage: test_range_retrieve host port\n" );
	exit(0);
}

long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}       /* read_from_file */

long write_to_file(void* stream, char* buff, long n)
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

int main(argc, argv)
        int argc;
        char **argv;

{
        int c, fd, fdout;
	int i=0;
	char *alphabet="abcdefghijklmnopqrstuvwxyz";
	hc_long_t schema_count = 0;
	hc_type_t type;
	hc_long_t countp;
	hc_long_t nslots=10000;
        hc_session_t *session = NULL;
	hc_query_result_set_t *rset;
	hc_nvr_t *nvr = NULL;
	hc_system_record_t system_record;
	hc_long_t long_value = 10000;
	hc_double_t double_value = 10000;
	hc_string_t string_value = "range_test";
	hc_long_t long_return = 0;
	hc_long_t firstbyte = 0;
	hc_long_t lastbyte = 0;
        hc_double_t double_return = 0;
        hc_string_t string_return = "           ";
	hc_long_t nvr_count = 0;
	hc_query_result_set_t *resp;
	hc_value_t valuep;

        hcerr_t reth;
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

	if ((reth = hc_init(malloc,free,realloc)) != HCERR_OK)
		printf("hc_init returned %s\n", hc_decode_hcerr(reth));
	if ((reth = hc_session_create_ez(host, port, &session)) != HCERR_OK)
		printf("hc_session_create_ez returned %s\n", 
                   hc_decode_hcerr(reth));
	/* Store some Data and Metadata */
	
	if ((reth = hc_nvr_create(session, nslots, &nvr)) != HCERR_OK) {
		printf("hc_nvr_create returned %s\n", hc_decode_hcerr(reth));
		exit(1);
	}
        if ((reth = hc_nvr_add_string(nvr, "test_id", string_value)) != HCERR_OK) {
                printf("hc_nvr_add_string returned %s\n", 
		  hc_decode_hcerr(reth));
                test_fail++;
        }
	if ((fd=open("/tmp/filetostore", O_CREAT|O_RDWR, 0755)) < 0) {
		perror("open filetostore");
		exit(1);
	}
	while (i<FILE_SIZE) {
		if (write(fd, alphabet, 26) == -1) {
			perror("write");
			exit(1);
		}
		i=i+26;
	}
	lseek(fd,0,0);
	if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, nvr, 
		&system_record) != HCERR_OK)) {
			printf("hc_store_both_ez returned %s\n", 
			  hc_decode_hcerr(reth));
			exit(1);
	}
	close(fd);
	printf("Test valid range_retrieve\n");
	if ((fdout = open("/tmp/rangefile", O_CREAT | O_WRONLY, 0666)) == -1) {
		perror("open rangefile");
		exit(1);
	}
	for (firstbyte=-1; firstbyte<2; firstbyte++) {
	  lseek(fdout, 0,0);
	  lastbyte=1;
	  if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout, &system_record.oid, firstbyte, lastbyte)) != HCERR_OK) {
		printf("hc_range_retrieve_ez returned %s\n", hc_decode_hcerr(reth));
		test_fail++;
	  }
	}

	printf("Test invalid range_retrieve\n");
        printf("Large negative startbyte\n");
  	firstbyte=-2;
	lastbyte=100;
        if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout,
 &system_record.oid, firstbyte, lastbyte)) == HCERR_OK) {
                printf("hc_range_retrieve_ez returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }

	printf("lastbyte beyond end of file\n");
  	firstbyte=0;
	lastbyte=FILE_SIZE+1000;
        if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout,
 &system_record.oid, firstbyte, lastbyte)) == HCERR_OK) {
                printf("hc_range_retrieve_ez returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
	printf("lastbyte less than firstbyte\n");
  	firstbyte=10;
	lastbyte=0;
        if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout,
 &system_record.oid, firstbyte, lastbyte)) == HCERR_OK) {
                printf("hc_range_retrieve_ez returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
 	printf("sparse file\n");
	if (unlink("/tmp/filetostore") == -1) {
		perror("unlink");
		exit(1);
  	}
	if (unlink("/tmp/rangefile") == -1) {
                perror("unlink");
                exit(1);
        }
	if ((fd=open("/tmp/filetostore", O_CREAT|O_RDWR, 0755)) < 0) {
                perror("open filetostore");
                exit(1);
        }
        if ((fdout = open("/tmp/rangefile", O_CREAT | O_WRONLY, 0666)) == -1) {
                perror("open rangefile");
                exit(1);
        }
	if (write(fd, "start", 5) == -1) {
		perror("write");
		exit(1);
	}
	if (ftruncate(fd, FILE_SIZE) != 0) {
		perror("ftruncate");
		exit(1);
	}
	if (lseek(fd, 0, SEEK_END) == -1) {
		perror("lseek");
		exit(1);
	}
	if (write(fd, "end", 3) == -1) {
		perror("write");
		exit(1);
	}
	lseek(fd,0,0);
        if ((reth = hc_store_both_ez(session, &read_from_file, (void *)fd, nvr,
                &system_record) != HCERR_OK)) {
                        printf("hc_store_both_ez returned %s\n",
                          hc_decode_hcerr(reth));
                        exit(1);
        }
	firstbyte=0;
	lastbyte=1100000000;
	if ((reth = hc_range_retrieve_ez(session, &write_to_file, (void *)fdout, &system_record.oid, firstbyte, lastbyte)) != HCERR_OK) {
                printf("hc_range_retrieve_ez returned %s\n", hc_decode_hcerr(reth));
                test_fail++;
        }
        close(fd);
        if ((fdout = open("/tmp/rangefile", O_CREAT | O_WRONLY, 0666)) == -1) {
                perror("open rangefile");
                exit(1);
        }

	hc_cleanup();
	if (test_fail > 0) {
		printf("test_range_retrieve FAILED\n");
		exit(1);
	} else {
		printf("test_range_retrieve PASSED\n");
		exit(0);
	}
}
