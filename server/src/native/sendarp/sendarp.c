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
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <synch.h>
#include <syslog.h>
#include <netconfig.h>
#include <time.h>
#include <kstat.h>
#include <errno.h>
#include <stropts.h>
#include <fcntl.h>
#include <stdarg.h>
#include <pthread.h>
#include <procfs.h>

#include <sys/stat.h>
#include <sys/types.h>
#include <sys/resource.h>
#include <sys/ddi.h>
#include <sys/utsname.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <sys/fcntl.h>
#include <sys/kstat.h>
#include <sys/errno.h>
#include <sys/strstat.h>
#include <sys/sysmacros.h>
#include <sys/socket.h>
#include <sys/sockio.h>
#include <netinet/in.h>
#include <net/if.h>

#include <stdio.h>
#include <fcntl.h>
#include <netdb.h>
#include <errno.h>
#include <unistd.h>
#include <signal.h>
#include <string.h>
#include <stropts.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/stropts.h>
#include <sys/resource.h>
#include <sys/sockio.h>
#include <sys/dlpi.h>
#include <net/if.h>
#include <netinet/in.h>
#include <netinet/in_systm.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <arpa/inet.h>
#include <inet/common.h>
#include <inet/arp.h>

#define	B_BSZ 1024
#define	BOFF(x) (((char *) &x) - ((char *) &b))

int
sendarp(int afd, char *interfacename, u_long myipaddr, u_long destipaddr, int ntimes)
{
	int	i;
	int	inamelen;
	u_long	myipaddr_netbyteorder;
	u_long	destipaddr_netbyteorder;
	char	*myipaddr_str;
	char	*destipaddr_str;

	static struct bstruct {
		areq_t	    b_a;
		u_long	    b_target_addr;
		u_long	    b_sender_addr;
		char	    b_name[256];
		u_char	    b_pad[B_BSZ];
	} b;
	static struct strioctl si;

	inamelen = strlen(interfacename);

	myipaddr_str = inet_ntoa(*(struct in_addr *)&myipaddr);
	myipaddr_netbyteorder = htonl(myipaddr);

	destipaddr_str = inet_ntoa(*(struct in_addr *)&destipaddr);
	destipaddr_netbyteorder = htonl(destipaddr);

	b.b_target_addr = destipaddr_netbyteorder;
	b.b_sender_addr = myipaddr_netbyteorder;
	(void) strncpy(b.b_name, interfacename, sizeof (b.b_name));

	b.b_a.areq_cmd = AR_XMIT_RESPONSE;
	b.b_a.areq_name_offset = BOFF(b.b_name[0]);
	b.b_a.areq_name_length = inamelen + 1;
	b.b_a.areq_proto = 0x0800;
	b.b_a.areq_target_addr_offset = BOFF(b.b_target_addr);
	b.b_a.areq_target_addr_length = 4;
	b.b_a.areq_flags = 0;
	b.b_a.areq_sender_addr_offset = BOFF(b.b_sender_addr);
	b.b_a.areq_sender_addr_length = 4;
	b.b_a.areq_xmit_count = 3;

	si.ic_cmd = b.b_a.areq_cmd;
	si.ic_timout = 120;	/* seconds */
	si.ic_len = B_BSZ;
	si.ic_dp = (char *) &b;

	for (i = 0; i < ntimes; i++) {
		if (ioctl(afd, I_STR, &si) < 0) {
			printf("AR_XMIT_REQUEST %s on %s",
			    myipaddr_str, interfacename);
			return (-1);
		}
	}
	return (0);
}

int main(int argc, char *argv[]) {
  int		arpfd;
  int		status;
  in_addr_t	ipaddr;
  in_addr_t	dipaddr;

  char *adp = argv[1];
  char *host = argv[2];
  char *dest = "255.255.255.255";

  /*
   * In case we want to take it as an arg instead
   */
  /* char *dest = argv[3]; */

  /*
   * Check args
   */
  if (argc != 3) {
    printf("\nUsage: sendarp <adapter> <data vip>\n");
    printf("Usage: sendarp bge1000 10.7.224.82\n");
    exit(1);
  }

  /*
   * Create a fd to /dev/arp to do ioctl's
   */
  if ((arpfd = open("/dev/arp", O_RDWR, 0)) < 0) {
    printf("Failed to open /dev/arp!\n");
    return (-1);
  }

  ipaddr = htonl(inet_addr(host));
  dipaddr = htonl(inet_addr(dest));
  (void) sendarp(arpfd, adp, ipaddr, dipaddr, 1);
  (void) close(arpfd);
}
