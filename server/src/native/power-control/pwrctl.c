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



#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#include "asf.h"

#define	BUF_SIZE  8

#define true 1
#define false 0

const char* progname;

static char rcsid[] = "$Id: pwrctl.c 10855 2007-05-19 02:54:08Z bberndt $";

static int asf_get_packet(asf_packet_t* packet, const char* cmd);
static int send_packet(int sock, asf_packet_t* packet);

void usage()
{
    fprintf(stderr, "Usage: %s host <cmd>\n", progname);
    fprintf(stderr, "    commands:\n");
    fprintf(stderr, 
            "        reset  soft reset\n"
            "        up     power up\n"
            "        down   power down\n"
            "        cycle  power cycle\n");
}

int
main(int argc, char *argv[])
{
    int s;
    asf_packet_t packet;

    const char* cmd;
    const char* host;

    progname = argv[0];

    if (argc != 3) {
        usage();
        exit(1);
    }
    cmd = argv[2];
    host = argv[1];

    if ((s = asf_get_socket(host)) < 0)
        exit(2);

    if (!asf_get_packet(&packet, cmd)) {
        fprintf(stderr, "Command \"%s\" unknown.\n", cmd);
        usage();
        exit(1);
    }

    if (!send_packet(s, &packet))
        exit(2);

    close(s);

    exit(0);
}

static int asf_get_packet(asf_packet_t* msg, const char* cmd)
{
    char **p;
    char *commands[] = {
        "up", "reset", "down", "cycle",
        0
    };

    for (p = commands; *p; p++) {
        if (strcmp(*p, cmd) == 0)
            break;
    }
    if (!*p)
        return false;

    asf_get_msg(msg, 0);

    switch (cmd[0]) {
    case 'r':                  // reset
        msg->msg_type = ASF_RESET;
        break;

    case 'u':                  // up
        msg->msg_type = ASF_POWERUP;
        break;

    case 'd':                  // down
        msg->msg_type = ASF_POWERDOWN;
        break;

    case 'c':                  // cycle
        msg->msg_type = ASF_POWERCYCLE;
        break;

    default:
        fprintf(stderr, "Internal error: bad cmd\n");
        exit(5);

    }
    return true;
}

static int send_packet(int s, asf_packet_t* msg)
{
    if (send(s, msg, sizeof(asf_packet_t), 0) < sizeof(asf_packet_t)) {
        perror("sendto");
        return false;
    }

    return rcsid != 0;          /* Bogus, to make rcsid used */
}
