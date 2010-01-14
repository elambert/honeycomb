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


#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <time.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>

#include "tcp_abort.h"


typedef unsigned short ushort_t;


int
abort_conn_v4(in_addr_t v4local, in_addr_t v4remote,
    ushort_t v4lport, ushort_t v4rport, int32_t start, int32_t end)
{
	int fd;
	struct strioctl ioc;
	struct sockaddr_in *local, *remote;
	tcp_ioc_abort_conn_t conn;
	
	local = (struct sockaddr_in*)&conn.ac_local;
	local->sin_family = AF_INET;
	local->sin_addr.s_addr = v4local;
	local->sin_port = htons(v4lport);
	
	remote = (struct sockaddr_in*)&conn.ac_remote;
	remote->sin_family = AF_INET;
	remote->sin_addr.s_addr = v4remote;
	remote->sin_port = htons(v4rport);

	conn.ac_start = start;
	conn.ac_end = end;

	ioc.ic_cmd = TCP_IOC_ABORT_CONN;
	ioc.ic_timout = -1; /* infinite timeout */
	ioc.ic_len = sizeof(conn);
	ioc.ic_dp = (char *)&conn;

	if ((fd = open("/dev/tcp", O_RDONLY)) < 0) return (-1);

	return(ioctl(fd, I_STR, &ioc));
}


int
abort_conn_v6(struct in6_addr *v6local, struct in6_addr *v6remote,
    ushort_t v6lport, ushort_t v6rport, int32_t start, int32_t end)
{
	int fd;
	struct strioctl ioc;
	struct sockaddr_in6 *local, *remote;
	tcp_ioc_abort_conn_t conn;
	
	local = (struct sockaddr_in6*)&conn.ac_local;
	local->sin6_family = AF_INET6;
	local->sin6_addr = *v6local;
	local->sin6_port = htons(v6lport);
	
	remote = (struct sockaddr_in6*)&conn.ac_remote;
	remote->sin6_family = AF_INET6;
	remote->sin6_addr = *v6remote;
	remote->sin6_port = htons(v6rport);

	conn.ac_start = start;
	conn.ac_end = end;

	ioc.ic_cmd = TCP_IOC_ABORT_CONN;
	ioc.ic_timout = -1; /* infinite timeout */
	ioc.ic_len = sizeof(conn);
	ioc.ic_dp = (char *)&conn;

	if ((fd = open("/dev/tcp", O_RDONLY)) < 0) return (-1);

	return(ioctl(fd, I_STR, &ioc));
}



char *progname;




void usage(void)
{
    fprintf(stderr,
	"%s: [-6] [-a local addr] [-p local port] [-A remote addr] [-P remote port] "
	"[-s start state] [-S end state]\n",
	progname);
    exit(-1);
}


int main(int argc, char *argv[])
{
	int ipv6 = 0;
	int lport = 0;
	int rport = 0;
	in_addr_t laddr = INADDR_ANY;
	in_addr_t raddr = INADDR_ANY;
	struct in6_addr laddrv6 = { { { 0 } } };
	struct in6_addr raddrv6 = { { { 0 } } };
	int start = TCPS_SYN_SENT;
	int end = TCPS_TIME_WAIT;
	int rc;
	    
	progname = *(argv++);

	while (argc > 1) {
		if ((*argv)[0] == '-') {
			switch ((*argv)[1]) {
			case 'p':
				if (argc-- < 3)
					usage();
				lport = atoi(*(++argv));
				break;
			case 'P':
				if (argc-- < 3)
					usage();
				rport = atoi(*(++argv));
				break;
			case 'a':
				if (argc-- < 3)
					usage();
				if (ipv6 == 0)
					laddr = inet_addr(*(++argv));
				else
					inet_pton(AF_INET6, *(++argv), &laddrv6);
				break;
			case 'A':
				if (argc-- < 3)
					usage();
				if (ipv6 == 0)
					raddr = inet_addr(*(++argv));
				else
					inet_pton(AF_INET6, *(++argv), &raddrv6);
				break;
			case 's':
				if (argc-- < 3)
					usage();
				start = atoi(*(++argv));	
				break;
			case 'S':
				if (argc-- < 3)
					usage();
				end = atoi(*(++argv));
				break;
			case '6':
				ipv6 = 1;
				break;
			default:
				usage();
				break;
			}
		}
		else {
			usage();
		}
		argc--;
		argv++;
	}
	
	if (ipv6 == 0)
		rc = abort_conn_v4(laddr, raddr, lport, rport, start, end);
	else
		rc = abort_conn_v6(&laddrv6, &raddrv6, lport, rport, start, end);
	if (rc < 0) {
		perror(ipv6 == 0 ? "abort_conn_v4()" : "abort_conn_v6()");
	}

	exit(rc);
}
