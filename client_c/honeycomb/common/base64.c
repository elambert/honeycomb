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



#include <ctype.h>
#include <stdio.h>



/*
#define TEST

    See RFC1521, section 5.2, Base64 Content-Transfer-Encoding
    http://rfc.net/rfc1521.html#p21

      decoding:

        - newlines are allowed at any point between blocks (and ok if none)
        - illegal characters generate errors

      encoding:

        - first byte indicates data type unless 0 is passed for tag
        - no newlines are used

                       Table 1: The Base64 Alphabet
                           [ascii codes added]

      Value Encoding  Value Encoding  Value Encoding  Value Encoding
           0 A (65)       17 R (82)       34 i (105)      51 z (122)
           1 B (66)       18 S   .        35 j   .        52 0 (48)
           2 C (67)       19 T   .        36 k   .        53 1   .
           3 D (68)       20 U   .        37 l   .        54 2   .
           4 E (69)       21 V   .        38 m   .        55 3   .
           5 F (70)       22 W   .        39 n   .        56 4   .
           6 G (71)       23 X   .        40 o   .        57 5   .
           7 H (72)       24 Y   .        41 p   .        58 6   .
           8 I (73)       25 Z (90)       42 q   .        59 7   .
           9 J (74)       26 a (97)       43 r   .        60 8   .
          10 K (75)       27 b   .        44 s   .        61 9   .
          11 L (76)       28 c   .        45 t   .        62 + (43)
          12 M (77)       29 d   .        46 u   .        63 / (47)
          13 N (78)       30 e   .        47 v   .
          14 O (79)       31 f   .        48 w   .     (pad) =
          15 P (80)       32 g   .        49 x   .
          16 Q (81)       33 h   .        50 y   .
*/

static const char bin_to_char[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
#define NOT -1
static const char char_to_code[] = {
    NOT,NOT,NOT,NOT,  NOT,NOT,NOT,NOT,  NOT,NOT,NOT,NOT,  NOT,NOT,NOT,NOT,
    NOT,NOT,NOT,NOT,  NOT,NOT,NOT,NOT,  NOT,NOT,NOT,NOT,  NOT,NOT,NOT,NOT,
    NOT,NOT,NOT,NOT,  NOT,NOT,NOT,NOT,  NOT,NOT,NOT, 62,  NOT,NOT,NOT, 63,
     52, 53, 54, 55,   56, 57, 58, 59,   60, 61,NOT,NOT,  NOT,NOT,NOT,NOT,
    NOT,  0,  1,  2,    3,  4,  5,  6,    7,  8,  9, 10,   11, 12, 13, 14,
     15, 16, 17, 18,   19, 20, 21, 22,   23, 24, 25,NOT,  NOT,NOT,NOT,NOT,
    NOT, 26, 27, 28,   29, 30, 31, 32,   33, 34, 35, 36,   37, 38, 39, 40,
     41, 42, 43, 44,   45, 46, 47, 48,   49, 50, 51,NOT,  NOT,NOT,NOT,NOT
};
#define DECODE_CHAR(c)  (isascii(c) ? char_to_code[c] : NOT)

char base64_errstr[80];

/*
 *  len: length of in_buf
 *  in_buf: bytes to encode
 *  out_buf: guaranteed to be 4 * (len / 3 +1) + 1(null) +1(datatype)
 *           null terminated
 */
void encode64(int len, const unsigned char *in_buf, char datatype, char *out_buf) {

    const unsigned char *cp = in_buf;
    char *cp2 = out_buf;
    int nblocks = len / 3;
    int partialblock = len % 3;
    int i;

    if (datatype != 0) {
        // mark the data type
        *cp2++ = datatype;
    }

    // encode all full blocks
    for (i=0; i<nblocks; i++, cp+=3) {
       *cp2++ = bin_to_char[cp[0] >> 2];
       *cp2++ = bin_to_char[((cp[0] << 4) & 0x30) | (cp[1] >> 4)];
       *cp2++ = bin_to_char[((cp[1] << 2) & 0x3c) | (cp[2] >> 6)];
       *cp2++ = bin_to_char[cp[2] & 0x3f];
    }
    if (partialblock) {
       char c;
       *cp2++ = bin_to_char[cp[0] >> 2];
       c = (cp[0] << 4) & 0x30;
       if (partialblock > 1)
           c |= cp[1] >> 4;
       *cp2++ = bin_to_char[c];
       if (partialblock == 1)
           *cp2++ = '=';
       else
           *cp2++ = bin_to_char[(cp[1] << 2) & 0x3c];
       *cp2++ = '=';
    }
    *cp2 = '\0';
}

/*
 *  in_buf: encoded data (no data type byte)
 *  out_buf: guaranteed to be 3/4 of len
 *
 *  return # bytes decoded if success, -1 if failure.
 */
int decode64(const char *in_buf, unsigned char *out_buf) {

    const char *cp = in_buf;
    char block[4];
    int j, k = 0;
    int pad = 0;
    
    for (;;) {
        int len = 0;

        // skip newlines
        while (*cp == '\n')
            cp++;

        if (*cp == '\0')
            break;
        if (pad) {
            sprintf(base64_errstr, "bad pad");
            return -1;
        }

        // translate chars in block
        for (j=0; j<4; j++) {
            if (*cp == '\0') {
                sprintf(base64_errstr, "incomplete block");
                return -1;
            }
            if (*cp == '=') {
                pad++;
            } else {
                if (pad) {
                    sprintf(base64_errstr, "bad pad");
                    return -1; // garbled block
                }
                block[j] = DECODE_CHAR(*cp);
                if (block[j] == NOT) {
                    sprintf(base64_errstr, "illegal char [%c]", *cp);
                    return -1;
                }
                len++;
            }
            cp++;
        }
        if (pad > 2) {
            sprintf(base64_errstr, "bad pad");
            return -1; 
        }

        // convert block to binary
        out_buf[k++] = block[0] << 2 | block[1] >> 4;
        if (pad < 2)
            out_buf[k++] = ((block[1] << 4) & 0xf0) | block[2] >> 2;
        if (pad == 0)
            out_buf[k++] = ((block[2] << 6) & 0xc0) | block[3];
    }

    return k;
}

#ifdef TEST

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>

int main(int argc, char *argv[]) {
    char *in_bytes;
    int fd;
    struct stat sbuf;

    fd = open(argv[2], O_RDONLY);
    if (fd == -1) {
        perror(argv[1]);
        exit(1);
    }
    if (fstat(fd, &sbuf) == -1) {
        perror("stat");
        exit(1);
    }
    in_bytes = (char *)malloc(sbuf.st_size);
    if (read(fd, in_bytes, sbuf.st_size) == -1) {
        perror("read");
        exit(1);
    }

    if (argv[1][0] == 'd') {
        int outsize = (3 * sbuf.st_size) / 4;
        char *outbuf = (char *) malloc(outsize);
        outsize = decode64(in_bytes, outbuf);
        if (outsize == -1)
            fprintf(stderr, "error: %s\n", base64_errstr);
        else
            fprintf(stderr, "%d\n", fwrite(outbuf, 1, outsize, stdout));
    } else {
        int outsize = sbuf.st_size / 3;
        if (sbuf.st_size % 3)
            outsize++;
        outsize *= 4;
        outsize++; // null
        char *outbuf = (char *) malloc(outsize);
        encode64(sbuf.st_size, in_bytes, outbuf);
        fprintf(stderr, "%d\n", fwrite(outbuf, 1, outsize-1, stdout));
    }
}
#endif
