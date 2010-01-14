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
 * console.c -- new improved ASF client for serial-over-LAN
 * ASF is UDP to port 623. Data is sent in the char buffer at the
 * end of the packet.
 */

#include <signal.h>
#include <termios.h>

#include "asf.h"

#define BUFSIZE 256

struct termios tio_cooked;      /* For the signal handler */

const char* progname;
static void process(int s);
static void makecooked(int s);

void usage()
{
    fprintf(stderr, "Usage: %s hostname\n", progname);
}

int main(int argc, char* argv[])
{
    const char* hostname;
    int s;
    struct termios tio;

    progname = argv[0];
    hostname = argv[1];

    if (argc != 2) {
        usage();
        exit(1);
    }
    if ((s = asf_get_socket(hostname)) < 0)
        exit(2);

    /* Save terminal state; restore on any signal */
    tcgetattr(STDIN_FILENO, &tio);
    tio_cooked = tio;
    signal(SIGHUP, makecooked);
    signal(SIGQUIT, makecooked);
    signal(SIGTERM, makecooked);
    signal(SIGTSTP, makecooked);

    // Commented out since Solaris does not have cfmakeraw.
    //cfmakeraw(&tio);
    tcsetattr(STDIN_FILENO, TCSANOW, &tio);

    process(s);

    close(s);
    makecooked(0);
    exit(0);
}

/* Restore terminal to original state */
void makecooked(int s)
{
    tcsetattr(STDIN_FILENO, TCSANOW, &tio_cooked);
    write(STDOUT_FILENO, "\r", 1);
    exit(0);
}

/* Connect stdin/stdout to the socket s */
void process(int s)
{
    unsigned char buf[BUFSIZE];
    asf_packet_t* packet = asf_get_msg(buf, ASF_TEXT);;

    for (;;) {
        fd_set rds;
        FD_ZERO(&rds);
        FD_SET(s, &rds);
        FD_SET(STDIN_FILENO, &rds);
        
        if (select(s+1, &rds, 0, 0, 0) < 0) {
            perror("\rselect");
            continue;
        }

        if (FD_ISSET(s, &rds)) {
            unsigned char buffer[BUFSIZE];
            asf_packet_t* packet = (asf_packet_t*)buffer;
            int nread = recv(s, buffer, sizeof(buffer), 0);
            if (nread < 0) {
                perror("\rrecv");
                continue;
            }
            if (write(STDOUT_FILENO, packet->data, packet->data_len) < 0)
                perror("\rwrite");
        }

        if (FD_ISSET(STDIN_FILENO, &rds)) {
            unsigned char buffer[BUFSIZE];
            int nread = read(STDIN_FILENO, buffer, sizeof(buffer));
            if (nread < 0) {
                perror("\rread");
                continue;
            }

            /* Quit on EOF or Ctrl-] */
            if (nread == 0 || (nread == 1 && buffer[0] == CTRL(']')))
                return;
            
            packet->data_len = nread;
            memcpy(packet->data, buffer, nread);
            if (send(s, buffer, ASF_HDRSIZE + nread, 0) < 0)
                perror("\rsend");
        }
    }
}
