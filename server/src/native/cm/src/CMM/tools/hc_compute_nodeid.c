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
 * This small utility look for a network interface and returns the last
 * byte of its IP address.
 * This is what is used by default for the node id.
 */

#include <unistd.h>
#include <string.h>
#include <malloc.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <libgen.h>

static void
usage(char *progname)
{
    printf("Usage : %s [-d] [-i interface]\n\n"
           "\t-d sets the debug traces\n"
           "\t-i asks the program to take the last byte of interface\n",
           basename(progname));
}

int
main(int argc,
     char *argv[])
{
    struct ifconf conf;
    int sock = -1;
    const char *err_msg = NULL;
    int count = -1;
    int i = -1;
    struct sockaddr_in *s_in = NULL;
    char *interface = "eth0";
    int debug_mode = 0;
    char c;

    while ((c=getopt(argc, argv, "di:")) != -1) {
        switch (c) {
        case 'd':
            debug_mode = 1;
            break;

        case 'i':
            interface = argv[optind-1];
            break;

        default:
            usage(argv[0]);
            return(1);
        }
    }

    if (debug_mode) {
        printf("Looking for interface %s\n",
               interface);
    }

    memset(&conf, 0, sizeof(conf));
    
    if (!err_msg) {
        if ((sock = socket(PF_INET, SOCK_DGRAM, 0)) == -1) {
            err_msg = "Couldn't create the socket\n";
        }
    }

    if (!err_msg) {
        conf.ifc_len = sizeof(struct ifreq) * 10;
        conf.ifc_buf = (char*)malloc(conf.ifc_len);
        if (!conf.ifc_buf) {
            err_msg = "Not enough memory\n";
        }
    }

    if (!err_msg) {
        if (ioctl(sock, SIOCGIFCONF, &conf) == -1) {
            err_msg = "The ioctl call failed\n";
        }
    }

    if (!err_msg) {
        count = conf.ifc_len / sizeof(struct ifreq);
 
        for (i=0; i<count; i++) {
            s_in = (struct sockaddr_in*)&conf.ifc_req[i].ifr_addr;
            if (debug_mode) {
                printf("Found a new interface [%s]\n",
                       conf.ifc_req[i].ifr_name);
            }
            if (!strcmp(conf.ifc_req[i].ifr_name, interface)) {
                break;
            }
        }

        if (i<count) {
            if (debug_mode) {
                printf("This interface matches the request\n");
            }
            
            printf("%d", ntohl(s_in->sin_addr.s_addr) & 0XFF);
        } else {
            err_msg = "Interface not found\n";
        }
    }

    if (sock != -1) {
        close(sock);
        sock = -1;
    }
    if (conf.ifc_buf) {
        free(conf.ifc_buf);
        conf.ifc_buf = NULL;
    }

    if (err_msg) {
        /* There has been an error */
        fprintf(stderr, "%s", err_msg);
        printf("-1");
        return(1);
    }
    
    return(0);
}
        


    
