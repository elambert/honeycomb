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
 * Component = disk server administration client
 * Synopsis  = client to change dynamically the state of a DiskServer
 *             (fault injection, start/stop).
 */

#include <sys/types.h>
#include <sys/param.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <netdb.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <ctype.h>
#include "trace.h"
#include "hcdisk_adm.h"


int 
get_host_address(char* host,
                 int port,
                 struct sockaddr_in* addr)
{
    bzero((char*)addr, sizeof (struct sockaddr_in));

    if (host == NULL) {
        addr->sin_addr.s_addr = INADDR_ANY;
        addr->sin_family = AF_INET;
    } else {
        struct hostent* hp;
        if ((hp = gethostbyname(host)) == NULL) {
            if (isdigit(host[0])) {
                addr->sin_addr.s_addr = inet_addr(host);
                addr->sin_family = AF_INET;
            } else {
                endhostent();
                return(1);
            }
        } else {
            addr->sin_family = hp->h_addrtype;
            (void) memcpy((char*) &addr->sin_addr,
                          hp->h_addr, hp->h_length);
        }
        endhostent();
    }
    addr->sin_port = htons(port);
    return(0);
}


static void
usage(char *progname)
{
    fprintf(stderr, "Usage: %s <cmd> -s <server> -d <disk>\n",
            progname);
    fprintf(stderr, "\t <cmd>    = start|stop|smart\n");
    fprintf(stderr, "\t <server> = server to contact (default localhost)\n");
    fprintf(stderr, "\t <disk>   = disk name (hda, vdiska,...)\n");
}


int
main(int argc, char *argv[])
{
    char *server;
    hcdisk_adm_cmd_t cmd;
    hcdisk_adm_status_t status;
    char c;
    int  ret;
    int  clen;
    int  s;
    struct sockaddr_in addr;

    cm_openlog("hcdisk_adm", CM_TRACE_LEVEL_NOTICE);
    if (argc < 2) {
        usage(argv[0]);
        return (1);
    }

    if (!strcmp(argv[1], "start")) {
        cmd.operation = HCDISK_ADM_START;
    } else if (!strcmp(argv[1], "stop")) {
        cmd.operation = HCDISK_ADM_STOP;
    } else if (!strcmp(argv[1], "smart")) {
        cmd.operation = HCDISK_ADM_SMART;
    } else {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "operation not supported: %s", argv[1]);
        usage(argv[0]);
        return (1);
    } 

    clen   = 0;
    server = NULL;
    while ((c = getopt(argc, argv, "s:d:")) != -1) {
        switch (c) {
  
        case 'd':
            ret = snprintf(&cmd.params[clen], 
                           sizeof(cmd.params) - clen, 
                           "%s ", 
                           optarg);
            if (ret < 0 || ret >= (int) sizeof(cmd) - clen) {
                cm_trace(CM_TRACE_LEVEL_ERROR, "too many disk defined");
                return (1);
            }
            clen += ret;
            break;

        case 's':
            server = strdup(optarg);
            break;

        default:
            usage(argv[0]);
            return (1);
        }
    }
    if (!server) {
        server = strdup("localhost");
    }
    if (!clen) {
        usage(argv[0]);
        return (1);
    }
    cm_trace(CM_TRACE_LEVEL_NOTICE, "request on %s cmd %d for %s", 
             server, cmd.operation, cmd.params);

    s = socket(AF_INET, SOCK_STREAM, 0);
    if (s == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "socket failed: %s\n", strerror(errno));
        return (1);
    }

    if (get_host_address(server, HCDISK_ADM_PORT, &addr)) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "get_host_address failed");
        close(s);
        return (1);
    }

    if (connect(s, (struct sockaddr*) &addr, sizeof(addr)) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "connect failed: %s\n", strerror(errno));
        close(s);
        return (1);
    }

    ret = write(s, &cmd, sizeof(cmd.operation) + strlen(cmd.params) + 1);
    if (ret < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "command failed: %s", strerror(errno)); 
        close(s);
        return (1);
    }
    ret = read(s, &status, sizeof(status));
    if (ret != sizeof(status)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "response error: %d - %d\n", ret, errno);
        close(s);
        return (1);
    }
    if (status == HCDISK_ADM_OK) {
        cm_trace(CM_TRACE_LEVEL_NOTICE, "The request succeeded");
    } else {
        cm_trace(CM_TRACE_LEVEL_ERROR, "The request failed %d", status);
    }

    close(s);
    return (status != HCDISK_ADM_OK);
}
