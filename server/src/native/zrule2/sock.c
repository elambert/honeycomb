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

static char id[] = "$Id: sock.c 10855 2007-05-19 02:54:08Z bberndt $";

/*
 * Setting up the socket ********************************************
 */

int create_sock(struct sockaddr_in *serv, int mport,
                const char *maddr, const char *laddr)
{
    struct in_addr mcast, myaddr;
    struct ip_mreq mreq;

    unsigned char loop = 0;
    int val = 1;
    int sock;

    if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        perror("socket(2)");
        return -1;
    }

    if (setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &val, sizeof val) < 0) {
        perror("client reuse failed");
    }

    memset(&myaddr, 0, sizeof (struct in_addr));
    serv->sin_family = AF_INET;
    serv->sin_port = htons(mport);
    serv->sin_addr.s_addr = htonl(INADDR_ANY);

    if (bind(sock, (struct sockaddr*)serv, sizeof(struct sockaddr_in)) < 0) {
        fprintf(stderr, "%s: cannot bind multicast socket to port %d.\n",
                progname, mport);
        perror("bind");
        return -1;
    }

    if (!inet_aton(maddr, &mcast)) {
        fprintf(stderr, "%s: error converting %s!\n", progname, maddr);
        return -1;
    }

    if (!inet_aton(laddr, &myaddr)) {
        fprintf(stderr, "%s: error converting %s!\n", progname, laddr);
        return -1;
    }

    mreq.imr_multiaddr.s_addr = mcast.s_addr;
    mreq.imr_interface.s_addr = myaddr.s_addr;

    if ((setsockopt (sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, (char *) &mreq,
                     sizeof (mreq))) < 0) {
        fprintf(stderr,  "%s: cannot join multicast group %s\n", progname,
                inet_ntoa(mcast));
        perror("create_sock group join ");
        return -1;
    }

    /* we dont wanna see our own messages */
    if (0 > (setsockopt (sock, IPPROTO_IP, IP_MULTICAST_LOOP, &loop,
                         sizeof (loop)))) {
        perror("create_sock loop disable ");
        return -1;
    }

    if (0 > (setsockopt (sock, IPPROTO_IP, IP_MULTICAST_IF, &myaddr,
                         sizeof (myaddr)))) {
        perror("create_sock interface selection ");
        return -1;
    }

    inet_aton(maddr, &serv->sin_addr);
    return sock;
}

/* Setup the multicast socket on the specified ip address */
int setup_multicast(const char *bind_addr, struct sockaddr_in* serv) {

    int sock = create_sock(serv, ZTMD_MCAST_PORT, ZTMD_MCAST_ADDR, bind_addr);

    if (sock < 0) {
        fprintf(stderr, "%s: error creating multicast socket.\n", progname);
        return -1;
    }

    return sock;
}
