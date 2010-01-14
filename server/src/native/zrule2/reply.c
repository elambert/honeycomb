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

static char id[] = "$Id: reply.c 10855 2007-05-19 02:54:08Z bberndt $";

/*
 * Handling the reply *************************************************
 */

static long timeout = MAX_WAIT;
void set_timeout(unsigned long milliseconds)
{
    timeout = milliseconds;
}

static int is_reply(znl2msghdr *msg, znl2msghdr *request)
{
    znl2msgerrt *err = (znl2msgerrt *) msg;
    znl2msghdr *orig = &err->omsg;

    if (msg->znl2msg_dpid != mypid) {
        /* Addressed to someone else */
        if (verbose >= 1)
            fprintf(stdout, "%s: message [%d] -> [%d]\n", progname,
                    msg->znl2msg_spid, msg->znl2msg_dpid);
        return 0;
    }

    if (request->znl2msg_seq != orig->znl2msg_seq) {
        /* Not in response to the message */
        if (verbose >= 1)
            fprintf(stdout, "%s: %s/ACK received from switch not a reply to "
                    "request %d\n    Is there another %s on the network?\n",
                    progname,
                    type2s(msg->znl2msg_type), request->znl2msg_seq,
                    basename(progname));
        return 0;
    }

    return 1;
}

static int get_error(znl2msghdr *reply, znl2msghdr *request)
{
    /* Replies from the switch always have a type of ZNL2MSG_ERROR */

    znl2msgerrt *err = (znl2msgerrt *) reply;
    int sender_pid = err->msg.znl2msg_spid;
    znl2msghdr *orig = &err->omsg;

    int errnum = -1;
    char errbuf[BUFSIZ];
    errbuf[0] = 0;

    if (reply->znl2msg_type != ZNL2MSG_ERROR)
        snprintf(errbuf, sizeof(errbuf),
                 "%s: oops! Received packet type %s\n", progname,
                 type2s(reply->znl2msg_type));

    else if (reply->znl2msg_len < sizeof(znl2msgerr))
        snprintf(errbuf, sizeof(errbuf),  "%s: reply truncated\n", progname);

    else if (err->error != 0) {
        snprintf(errbuf, sizeof(errbuf),
                 "%s: NACK received from switch, error = %s\n",
                 progname, err2s(err->error));
        errnum = err->error;
    }

    else if (request->znl2msg_type != orig->znl2msg_type)
        snprintf(errbuf, sizeof(errbuf),
                 "%s: message %d reply and request type mismatch: "
                 "expected %s but got %s\n", progname, request->znl2msg_seq, 
                 type2s(request->znl2msg_type), type2s(orig->znl2msg_type));

    if (errbuf[0] != 0) {
        fputs(errbuf, stderr);
        if (verbose > 1)
            fputc('\n', stdout);
        return errnum;
    }

    /* Now we know that it's a good reply to one of our requests */

    if (verbose >= 1)
        fprintf(stdout, "%s: %s/ACK from pid %d in reply to to message #%d\n",
                progname, type2s(request->znl2msg_type), sender_pid,
                request->znl2msg_seq);
    if (verbose > 1)
        fputc('\n', stdout);

    return 0;
}

static znl2msg* read_msg(int sock, znl2msg* msg)
{
    int i;
    struct sockaddr_in addr;
    int sock_len;
    ssize_t packet_size;
    uint32_t* store;

    memset(&addr, 0, sizeof (struct sockaddr_in));
    sock_len = sizeof(struct sockaddr_in);

    if ((packet_size = recvfrom(sock, (char *) msg, sizeof(znl2msg), 0,
                                (struct sockaddr *) &addr, &sock_len)) < 0) {
        perror("recvfrom");
        return NULL;
    }

    if (verbose >= 3)
      dump_packet(msg, packet_size);

    /* Endianess/byte ordering */
    store = (uint32_t*) msg;
    for (i = 0; i < packet_size; i++)
        store[i] = psdb_pton(store[i]);

    if (verbose >= 1)
        fprintf(stdout, "%s: received %d byte message from %s:%d\n", 
                progname, packet_size,
                inet_ntoa(addr.sin_addr), ntohs(addr.sin_port));

    return msg;
}

int get_reply(int sock, znl2msghdr* request)
{
    /*
     * Important: on Linux, select(2) modifies tv with the amount of time
     * left on the timer. This means we will wait a cumulative time of
     * timeout. On systems that don't do this, every packet we recv
     * that we decide not to handle resets the timer.
     */
    struct timeval tv;
    tv.tv_sec  = timeout / 1000;
    tv.tv_usec = timeout % 1000;

    for (;;) {                  /* Until timeout or we like the message */
        int ret;
        fd_set fds;

        FD_ZERO(&fds);
        FD_SET(sock, &fds);

        ret = select(sock+1, &fds, NULL, NULL, &tv);

        if (ret < 0) {
            perror("select");
            if (errno == EINTR)
                continue;
            /* We can only do something sane for EINTR */
            return -1;
        }

        if (ret == 0)
            /* We've waited long enough ! */
            return -2;

        if (FD_ISSET(sock, &fds)) {
            znl2msg msg;
            if (read_msg(sock, &msg) != NULL) {
                if (verbose >= 2)
                    dump_msg(&msg);

                if (is_reply(&msg.n, request))
                    return get_error(&msg.n, request);
            }
        }
    } /* for */
}
