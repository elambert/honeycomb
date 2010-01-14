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
    Test write performance without threads.

    % cc simple_write_test.c
    % a.out <write_fname>
*/

#include <stdio.h>
#include <sys/time.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>

#define DATA_BUF_SIZE (64 * 1024)
#define WRITE_BUFS 1024

/********************************************************************/

static void
usage()
{
  fprintf(stderr, "Usage: iotest <write_fname>\n");
  exit(1);
}

long
get_system_millis()
{
  struct timeval t;
  gettimeofday(&t, NULL);

  return(t.tv_sec*1000 + t.tv_usec/1000);
}

int
main(int argc, char *argv[])
{
  char data_buf[DATA_BUF_SIZE];
  char *fname;
  long t1;
  int fd, i;

  if (argc != 2)
      usage();
  fname = argv[1];

  for (i=0; i<DATA_BUF_SIZE; i++)
    data_buf[i] = (char) i;

  t1 = get_system_millis();
  fd = open(fname, O_RDWR | O_CREAT, 0);
  if (fd == -1) {
    fprintf(stderr, "Failed to open file [%s] - %s\n",
	    fname, strerror(errno));
    exit(-1);
  }
  t1 = get_system_millis() - t1;
  printf("open time: %ld ms\n", t1);

  t1 = get_system_millis();
  for (i=0; i<WRITE_BUFS; i++) {
    size_t len = write(fd, data_buf, DATA_BUF_SIZE);
    if (len == -1) {
      perror("write");
      exit(1);
    }
    if (len != DATA_BUF_SIZE) {
      fprintf(stderr, "wrote %d sted %d: %s\n", len, DATA_BUF_SIZE, fname);
      exit(1);
    }
  }
  t1 = get_system_millis() - t1;
  printf("wrote %f MB\n", (float)(WRITE_BUFS * DATA_BUF_SIZE) / 
                          (float)(1024 * 1024));
  printf("write time: %ld ms\n", t1);
  printf("bandwidth: %f MB/s\n", (float)(WRITE_BUFS * DATA_BUF_SIZE) * 1000.0 /
                            ((float)t1 * (float)1024 * (float)1024));

  t1 = get_system_millis();

  if (close(fd) == -1) {
    perror("close");
    exit(-1);
  }
  t1 = get_system_millis() - t1;
  printf("close time: %ld ms\n", t1);

  if (unlink(fname)) {
    fprintf(stderr, "WARNING ! Failed to unlink [%s]\n", fname);
  }
}

