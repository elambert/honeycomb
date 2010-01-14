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



#ifndef _TCP_ABORT_H
#define _TCP_ABORT_H

#include <linux/socket.h>
#include <linux/types.h>


#define TCP_ABORT_DEV_MAJOR 122
#define TCP_ABORT_DEV_NAME  "tcpabort"


/*
 * TCP_IOC_ABORT_CONN is a non-transparent ioctl command used for aborting
 * TCP connections. To invoke this ioctl, a tcp_ioc_abort_conn_t structure
 * (defined in tcp.h) needs to be filled in and passed into the kernel
 * via an I_STR ioctl command (see streamio(7I)). The tcp_ioc_abort_conn_t
 * structure contains the four-tuple of a TCP connection and a range of TCP
 * states (specified by ac_start and ac_end). The use of wildcard addresses
 * and ports is allowed. Connections with a matching four tuple and a state
 * within the specified range will be aborted. The valid states for the
 * ac_start and ac_end fields are in the range TCPS_SYN_SENT to TCPS_TIME_WAIT,
 * inclusive.
 *
 * An application which has its connection aborted by this ioctl will receive
 * an error that is dependent on the connection state at the time of the abort.
 * If the connection state is < TCPS_TIME_WAIT, an application should behave as
 * though a RST packet has been received.  If the connection state is equal to
 * TCPS_TIME_WAIT, the 2MSL timeout will immediately be canceled by the kernel
 * and all resources associated with the connection will be freed.
 */


#define	STR		('S' << 8)
#define	I_STR		(STR | 010)


struct strioctl {
	int 	ic_cmd;			/* command */
	int	ic_timout;		/* timeout value */
	int	ic_len;			/* length of data */
	char	*ic_dp;			/* pointer to data */
};


#define	TCP_IOC_ABORT_CONN	(('T' << 8) + 91)

/*
 * Common superset of at least AF_INET, AF_INET6 and AF_LINK sockaddr
 * structures. Has sufficient size and alignment for those sockaddrs.
 */
typedef struct tcp_abort_sockaddr_storage {
	union {
		sa_family_t	    ss_family;	/* address family */
		struct sockaddr_in  ss_addr_in;
		struct sockaddr_in6 ss_addr_in6;
	} ss_u;
} tcp_abort_sockaddr_storage_t;

#define ss_family ss_u.ss_family
#define ss_addr_in ss_u.ss_addr_in
#define ss_addr_in6 ss_u.ss_addr_in6


typedef struct tcp_ioc_abort_conn_s {
	struct tcp_abort_sockaddr_storage ac_local;	/* local addr and port */
	struct tcp_abort_sockaddr_storage ac_remote;	/* remote addr and port */
	int32_t ac_start;				/* start state */
	int32_t ac_end;					/* end state  */
} tcp_ioc_abort_conn_t;


/* Solaris-like ordered TCP states. */
#define	TCPS_SYN_SENT		1	/* active, have sent syn */
#define	TCPS_SYN_RCVD		2	/* have received syn (and sent ours) */
/* states < TCPS_ESTABLISHED are those where connections not established */
#define	TCPS_ESTABLISHED	3	/* established */
#define	TCPS_CLOSE_WAIT		4	/* rcvd fin, waiting for close */
/* states > TCPS_CLOSE_WAIT are those where user has closed */
#define	TCPS_FIN_WAIT_1		5	/* have closed and sent fin */
#define	TCPS_CLOSING		6	/* closed, xchd FIN, await FIN ACK */
#define	TCPS_LAST_ACK		7	/* had fin and close; await FIN ACK */
/* states > TCPS_CLOSE_WAIT && < TCPS_FIN_WAIT_2 await ACK of FIN */
#define	TCPS_FIN_WAIT_2		8	/* have closed, fin is acked */
#define	TCPS_TIME_WAIT		9	


#endif /* _TCP_ABORT_H */
