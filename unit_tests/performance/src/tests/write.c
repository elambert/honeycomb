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



#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>

int main (int argc, char **argv) {
    if (argc < 4) {
        printf ("usage: %s <file name> <write block size> <total write>\n", argv[0]);    
        printf ("usage: %s /tmp/d 1024 8192\n", argv[0]);    
        exit (1);
    }
    // get file name
    char *file = argv[1];
    int w_blk = atoi (argv[2]);
    int bytes_towrite = atoi (argv[3]);
    int fd = open (file, O_WRONLY|O_CREAT|O_TRUNC, 0666);
    if (fd < 0) {
       perror ("cant open file");
       exit (1);
    } 

    // write to a file
    int total_bytes = 0;
    int bytes_w = 0;
    int w_size = 0;
    while (total_bytes < bytes_towrite) {
       if (bytes_towrite - total_bytes < w_blk) w_size = bytes_towrite - total_bytes;
       else                                     w_size = w_blk;
       int size = w_size * sizeof(char); 
       char *buf = (char *) malloc (size);
       memset (buf, 0, size);
       if ((bytes_w = write (fd, buf, size)) < 0) break;
       total_bytes += bytes_w;
       free (buf); 
    } 
    printf ("written %d bytes to file %s\n", total_bytes, file);
    close (fd);
}

     
