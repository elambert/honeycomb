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



#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
 

/*
 *  buffer is 1k
 */
#define BUF_LEN 256
#define BUF_INT 0xf0f0f0f0

int of_check_file(const char *fname, int n_bufs);

void
of_create_file(const char *fname, int n_bufs)
{
    int buf[BUF_LEN * sizeof(int)];
    int i;
    int fd = open(fname, O_CREAT|O_EXCL|O_WRONLY, 0644);
    if (fd == -1) {
        if (errno == EEXIST) {
            if (of_check_file(fname, n_bufs)) {
                fprintf(stderr, "ERROR - existing file is bad\n");
                exit(1);
            }
            /*
             *  assume file is already created correctly
             */
            return;
        }
        perror(fname);
        fprintf(stderr, "ERROR\n");
        exit(1);
    }
    for (i=0; i<BUF_LEN; i++)
        buf[i] = BUF_INT;

    if (n_bufs < 5)
        n_bufs = 1;
    else
        n_bufs /= 4;

    for (i=0; i<n_bufs; i++) {
        ssize_t ret;

        /*
         *  label this block
         */
        buf[BUF_LEN-1] = i;

        ret = write(fd, buf, sizeof(buf));
        if (ret == -1) {
            perror(fname);
            fprintf(stderr, "ERROR\n");
            exit(1);
        }
        if (ret != sizeof(buf)) {
            fprintf(stderr, "ERROR - bad write on %s: %d\n", fname, ret);
            exit(1);
        }
    }
    if (of_check_file(fname, n_bufs)) {
        fprintf(stderr, "ERROR - file is bad\n");
        exit(1);
    }
}

int
of_check_file(const char *fname, int n_bufs)
{
    int rd_buf[BUF_LEN * sizeof(int)];
    int i, j, fd;

    fd = open(fname, O_RDONLY);
    if (fd == -1) {
        perror(fname);
        return 1;
    }
    if (n_bufs < 5)
        n_bufs = 1;
    else
        n_bufs /= 4;

    for (i=0; i<n_bufs; i++) {
        ssize_t ret;

        ret = read(fd, rd_buf, sizeof(rd_buf));
        if (ret == -1) {
            perror(fname);
            return 1;
        }
        if (ret != sizeof(rd_buf)) {
            fprintf(stderr, "bad read size on buf %d: %d (expected %d)\n", 
                    i, ret, sizeof(rd_buf));
            fprintf(stderr, "file: %s\n", fname);
            return 1;
        }

        for (j=0; j<BUF_LEN-1; j++) {
            if (rd_buf[j] != BUF_INT) {
                fprintf(stderr, "bad buf %d @ %d: 0x%x expected 0x%x\n", 
                        i, j, rd_buf[j], BUF_INT);
                fprintf(stderr, "file: %s\n", fname);
                return 1;
            }
        }
        if (rd_buf[BUF_LEN-1] != i) {
            fprintf(stderr, "bad buf %d counter is %d expected %d\n",
                    i, rd_buf[BUF_LEN-1], i);
            fprintf(stderr, "file: %s\n", fname);
            return 1;
        }
    }
    return 0;
}

int
of_check_file_range(const char *fname, off_t start, off_t end)
{
    struct stat stat_buf;
    off_t i;
    int fd, expected_buf;

    /*
     *  open & check file size
     */
    fd = open(fname, O_RDONLY);
    if (fd == -1) {
        perror(fname);
        return 1;
    }
    if (fstat(fd, &stat_buf) == -1) {
        perror(fname);
        return 1;
    }
    if (stat_buf.st_size != end - start + 1) {
        fprintf(stderr, "file size is %ld expected %ld\n", stat_buf.st_size,
                end - start + 1);
        return 1;
    }

    expected_buf = (start / 1024) + 1;

    for (i=start; i<=end; i++) {
        off_t buf_offset = i % 1024;
        ssize_t ret;

        if (buf_offset > 1019) {
            int buf_num;
            if (buf_offset == 1020) {

                /*
                 *  at the beginning of a block label
                 */
                if (end - i < 4) {
                    fprintf(stderr, "skipping partial block count field at end\n");
                    close(fd);
                    return 0;
                }
                ret = read(fd, &buf_num, sizeof(buf_num));
                if (ret == -1) {
                    perror(fname);
                    close(fd);
                    return 1;
                }
                if (ret != sizeof(buf_num)) {
                    fprintf(stderr, "partial read of block number\n");
                    close(fd);
                    return 1;
                }
                /* update index for extra bytes read */
                i += sizeof(buf_num)-1;
                if (buf_num != expected_buf) {
                    fprintf(stderr, "unexpected buf_num: %d expected %d\n",
                            buf_num, expected_buf);
                    close(fd);
                    return 1;
                }
            } else {
                /* starting in the middle of a block label */
                int toread = 1024 - buf_offset;
                fprintf(stderr, "skipping partial block number\n");
                ret = read(fd, &buf_num, toread);
                if (ret == -1) {
                    perror(fname);
                    close(fd);
                    return 1;
                }
                if (ret != toread) {
                    fprintf(stderr, "partial read of block number\n");
                    close(fd);
                    return 1;
                }
                i += toread - 1;
            }
        } else {
            char c;
            ret = read(fd, &c, sizeof(c));
            if (ret == -1) {
                perror(fname);
                close(fd);
                return 1;
            }
            
            if (ret != sizeof(c)) {
                fprintf(stderr, "bad read size on char %ld: %d (expected %d)\n", 
                        i, ret, sizeof(c));
                fprintf(stderr, "file: %s\n", fname);
                close(fd);
                return 1;
            }
            if (i % 2) {
                if (c != 0xf) {
                    fprintf(stderr, "bad char at %ld\n", i);
                    close(fd);
                    return 1;
                }
            } else {
                if (c != 0) {
                    fprintf(stderr, "bad char at %ld\n", i);
                    close(fd);
                    return 1;
                }
            }
        }
    }
    return 0;
}
