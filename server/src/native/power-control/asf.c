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



#include <netinet/in.h>
#include "asf.h"

asf_packet_t* asf_get_msg(void* buf, int msg_type) 
{
    asf_packet_t* p = (asf_packet_t*)buf;

    p->version = 6;
    p->reserved1 = 0;
    p->sequence = 0xff;
    p->msg_class = 8;
    p->iana = htonl(ASF_IANA);
    p->msg_type = msg_type;
    p->msg_tag = 0xff;
    p->reserved2 = 0;
    p->data_len = 0;
    p->data[0] = 0;

    return p;
}

int asf_get_socket(const char* hostname)
{
    struct hostent* h;
    struct sockaddr_in dest;
    int s;

    if ((h = gethostbyname(hostname)) != 0)
        memcpy(&dest.sin_addr, h->h_addr, h->h_length);
    else {
        fprintf(stderr, "Host \"%s\": ", hostname);
        switch(h_errno) {

        case HOST_NOT_FOUND:
            fputs("no such name", stderr);
            break;

        case NO_ADDRESS:
            fputs("no address", stderr);
            break;

        case NO_RECOVERY:
            fputs("non-recoverable nameserver error", stderr);
            break;

        case TRY_AGAIN:
            fputs("temporary nameserver error (try again)", stderr);
            break;
        }
        fputs(".\n", stderr);
        return -1;
    }

    /* Create socket */
    if ((s = socket(PF_INET, SOCK_DGRAM, 0)) < 0) {
        perror("socket");
        return -1;
    }

#if defined(__FreeBSD__) || defined(BSD4_4)
    dest.sin_len = sizeof(dest);
#endif
    dest.sin_family = AF_INET;
    dest.sin_port = htons((u_short)ASF_PORT);

    /* Set the destination. This also sets a random inbound port for s. */
    if (connect(s, (struct sockaddr*)&dest, sizeof(dest)) < 0) {
        perror("connect");
        return -1;
    }

    return s;
}
