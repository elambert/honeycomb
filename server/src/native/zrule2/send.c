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



#include "libztmd.h"

static char id[] = "$Id: send.c 10855 2007-05-19 02:54:08Z bberndt $";

/*
 * Sending SYN/FIN **************************************************
 */



int nl2_send(int sock, struct sockaddr_in *addr, znl2msg *msg)
{
    int i;
    int len;
    uint32_t* store;
    znl2msg *flipped;

    if (verbose >= 1)
      fprintf(stdout, "%s: sending %d bytes (message #%d %s) to %s:%d\n",
              progname,
              msg->n.znl2msg_len,
              msg->n.znl2msg_seq, type2s(msg->n.znl2msg_type),
              inet_ntoa(addr->sin_addr), ntohs(addr->sin_port));

    /* 
     * Make a copy of msg before byte flipping it. We're preserving the
     * original so that we can compare the request & reply headers.
     */
    len = msg->n.znl2msg_len;
    flipped = malloc(len);
    memcpy(flipped, msg, len);

    /* Endianess/byte ordering */
    store = (uint32_t*) flipped;
    if (flipped->n.znl2msg_type == ZNL2_SYN || flipped->n.znl2msg_type == ZNL2_FIN) {
        for (i = 0; i < (sizeof(znl2msghdr)/4+2); i++)
            store[i] = psdb_pton(store[i]);
    } else {
        for (i = 0; i < (len/4); i++)
            store[i] = psdb_pton(store[i]);
    }

    if (verbose >= 3)
        dump_packet(flipped, len);

    return sendto(sock, flipped, len, 0, (struct sockaddr *)addr,
                  sizeof(struct sockaddr_in));
}
