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



#include <linux/autoconf.h>
#include <linux/module.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/kernel.h>
#include <linux/mm.h>
#include <linux/slab.h>
#include <linux/socket.h>
#include <linux/config.h>
#include <linux/types.h>
#include <linux/fcntl.h>
#include <net/icmp.h>
#include <net/tcp.h>

#if defined(CONFIG_IPV6) || defined(CONFIG_IPV6_MODULE)
#define CONFIG_TCP_ABORT_IPV6
#endif

#ifdef CONFIG_TCP_ABORT_IPV6
#include <linux/in6.h>
#include <net/ipv6.h>
#endif

#include "tcp_abort.h"


static int debug = 0;
MODULE_PARM(debug, "i");
MODULE_PARM_DESC(debug, "display debugging information");

static int dryrun = 0;
MODULE_PARM(dryrun, "i");
MODULE_PARM_DESC(dryrun, "do not abort the TCP connections, but merely perform a dry run");


typedef struct tcp_abort_sock {
	struct list_head as_list;
	struct sock *as_sock;
} tcp_abort_sock_t;


/*
 * Macros used for accessing the different types of sockaddr
 * structures inside a tcp_ioc_abort_conn_t.
 */

/* Ripped from Solaris 9 tcp.c */
#define	TCP_AC_V4LADDR(acp) ((struct sockaddr_in *)&(acp)->ac_local)
#define	TCP_AC_V4RADDR(acp) ((struct sockaddr_in *)&(acp)->ac_remote)
#define	TCP_AC_V4LOCAL(acp) (TCP_AC_V4LADDR(acp)->sin_addr.s_addr)
#define	TCP_AC_V4REMOTE(acp) (TCP_AC_V4RADDR(acp)->sin_addr.s_addr)
#define	TCP_AC_V4LPORT(acp) (TCP_AC_V4LADDR(acp)->sin_port)
#define	TCP_AC_V4RPORT(acp) (TCP_AC_V4RADDR(acp)->sin_port)

#ifdef CONFIG_TCP_ABORT_IPV6
#define	TCP_AC_V6LADDR(acp) ((struct sockaddr_in6 *)&(acp)->ac_local)
#define	TCP_AC_V6RADDR(acp) ((struct sockaddr_in6 *)&(acp)->ac_remote)
#define	TCP_AC_V6LOCAL(acp) (TCP_AC_V6LADDR(acp)->sin6_addr)
#define	TCP_AC_V6REMOTE(acp) (TCP_AC_V6RADDR(acp)->sin6_addr)
#define	TCP_AC_V6LPORT(acp) (TCP_AC_V6LADDR(acp)->sin6_port)
#define	TCP_AC_V6RPORT(acp) (TCP_AC_V6RADDR(acp)->sin6_port)
#endif


#if 0
static int _tcp_state[] = 
{
	0,
	TCP_SYN_SENT,
	TCP_SYN_RECV,
	TCP_ESTABLISHED,
	TCP_CLOSE_WAIT,
	TCP_FIN_WAIT1,
	TCP_CLOSING,
	TCP_LAST_ACK,
	TCP_FIN_WAIT2,
	TCP_TIME_WAIT
};

#define TCP_SOCK_STATE(tcps)   _tcp_state[tcps] /* ac state -> sock state */
#endif


static int _tcps_state[] = {
	0,
	TCPS_ESTABLISHED,
	TCPS_SYN_SENT,
	TCPS_SYN_RCVD,
	TCPS_FIN_WAIT_1,
	TCPS_FIN_WAIT_2,
	TCPS_TIME_WAIT,
	0,
	TCPS_CLOSE_WAIT,
	TCPS_LAST_ACK,
	0,
	TCPS_CLOSING,
};

#define TCP_AC_STATE(tcp)  _tcps_state[tcp] /* sock state -> ac state */


static int tcp_abort_dev_ioctl(struct inode *, struct file *, unsigned int, unsigned long);
static int tcp_abort_dev_open(struct inode *ino, struct file *filp);
static int tcp_abort_dev_release(struct inode *ino, struct file *filp);

static int tcp_abort_conn(unsigned long arg);
static int tcp_abort(tcp_ioc_abort_conn_t *conn);
static int tcp_abort_bucket(tcp_ioc_abort_conn_t *acp, int bucket, int *count, int exact);


static struct file_operations tcp_abort_dev_fops = {
	ioctl:		tcp_abort_dev_ioctl,
	open:		tcp_abort_dev_open,
	release:	tcp_abort_dev_release,
};


static int __init tcp_abort_dev_init(void)
{

	if (register_chrdev(TCP_ABORT_DEV_MAJOR, TCP_ABORT_DEV_NAME, &tcp_abort_dev_fops)) {
		printk(KERN_ERR "tcp_abort_dev_init: failed to register char device, major %d.\n",
		    TCP_ABORT_DEV_MAJOR);
		return -EINVAL;
	}

	return 0;
}


static void __exit tcp_abort_dev_cleanup(void)
{
	if (unregister_chrdev(TCP_ABORT_DEV_MAJOR, TCP_ABORT_DEV_NAME))
		printk(KERN_ERR "tcp_abort_dev_init: failed to unregister char device, major %d.\n",
		    TCP_ABORT_DEV_MAJOR);
}


#ifdef MODULE
int __init init_module(void)
{
	return tcp_abort_dev_init();
}


void __exit cleanup_module(void)
{
	tcp_abort_dev_cleanup();
}
#endif


static int tcp_abort_dev_open(struct inode *ino, struct file *filp)
{
	MOD_INC_USE_COUNT;
	return 0;
}


static int tcp_abort_dev_release(struct inode *ino, struct file *filp)
{
	MOD_DEC_USE_COUNT;
	return 0;
}


static int tcp_abort_dev_ioctl(struct inode *ino, struct file *filp,
	unsigned int msg, unsigned long arg)
{
	switch (msg) {
	case I_STR:
		return tcp_abort_conn(arg);
	}

	return -ENOSYS;
}


/* Negatively inspired by the Solaris TCP_AC_MATCH() macro. */
static inline int tcp_abort_match_v4(tcp_ioc_abort_conn_t *acp, struct sock *sk)
{
	if (sk->family != AF_INET)
		return 0;

	if ((TCP_AC_V4LOCAL(acp) != INADDR_ANY) &&
	    (TCP_AC_V4LOCAL(acp) != sk->rcv_saddr))
		return 0;

	if ((TCP_AC_V4REMOTE(acp) != INADDR_ANY) &&
	    (TCP_AC_V4REMOTE(acp) != sk->daddr))
		return 0;

	if ((TCP_AC_V4LPORT(acp) != 0) &&
	    (TCP_AC_V4LPORT(acp) != sk->sport))
		return 0;

	if ((TCP_AC_V4RPORT(acp) != 0) &&
	    (TCP_AC_V4RPORT(acp) != sk->dport))
		return 0;

	if (acp->ac_start > TCP_AC_STATE(sk->state))
		return 0;

	if (acp->ac_end < TCP_AC_STATE(sk->state))
		return 0;

	/* Matched. */
	return 1;
}


#ifdef CONFIG_TCP_ABORT_IPV6
static inline int tcp_abort_match_v6(tcp_ioc_abort_conn_t *acp, struct sock *sk)
{
	struct in6_addr *laddr;
	struct in6_addr *raddr;

	if (sk->family != AF_INET6)
		return 0;	

	if (sk->state != TCP_TIME_WAIT) {
		struct ipv6_pinfo *np = &sk->net_pinfo.af_inet6;
		laddr = &np->rcv_saddr;
		raddr = &np->daddr;
	}
	else {
		/* IPv6 TIME-WAIT buckets have a different format. */
		struct tcp_tw_bucket *tw = (struct tcp_tw_bucket *)sk;
		laddr = &tw->v6_rcv_saddr;
		raddr = &tw->v6_daddr;
	}

	if ((ipv6_addr_type(&TCP_AC_V6LOCAL(acp)) != IPV6_ADDR_ANY) &&
	    (ipv6_addr_cmp(&TCP_AC_V6LOCAL(acp), laddr) != 0))
		return 0;

	if ((ipv6_addr_type(&TCP_AC_V6REMOTE(acp)) != IPV6_ADDR_ANY) &&
	    (ipv6_addr_cmp(&TCP_AC_V6REMOTE(acp), raddr) != 0))
		return 0;

	if ((TCP_AC_V6LPORT(acp) != 0) &&
	    (TCP_AC_V6LPORT(acp) != sk->sport))
		return 0;

	if ((TCP_AC_V6RPORT(acp) != 0) &&
	    (TCP_AC_V6RPORT(acp) != sk->dport))
		return 0;	

	if (acp->ac_start > TCP_AC_STATE(sk->state))
		return 0;

	if (acp->ac_end < TCP_AC_STATE(sk->state))
		return 0;

	/* Matched. */
	return 1;
}
#endif


static inline int tcp_abort_match(tcp_ioc_abort_conn_t *acp, struct sock *sk)
{
	switch (acp->ac_local.ss_family) {
	case AF_INET:
		return tcp_abort_match_v4(acp, sk);

#ifdef CONFIG_TCP_ABORT_IPV6
	case AF_INET6:
		return tcp_abort_match_v6(acp, sk);
#endif
	}

	return 0;
}


static int tcp_abort_conn(unsigned long arg)
{
	struct strioctl ioc;
	tcp_ioc_abort_conn_t conn;

	if (copy_from_user(&ioc, (void*) arg, sizeof(ioc)))
		return -EFAULT;

	switch (ioc.ic_cmd) {
	case TCP_IOC_ABORT_CONN:

		if (ioc.ic_len != sizeof(conn))
			return -EINVAL;

		if (!capable(CAP_NET_ADMIN))
			return -EPERM;

		if (copy_from_user(&conn, (void*) ioc.ic_dp, sizeof(conn)))
			return -EFAULT;

		return tcp_abort(&conn);
	}

	return -ENOSYS;
}


/*
 * The following three functions are the only required ones
 * that are not exported by RedHat kernels (they actually
 * can't be exported because they are inlined). Thus, their
 * code is just locally duplicated below.
 *
 * Also note that stock kernels may not export a number of other
 * symbols required by the module, so the linker machinery
 * used to link against non-exported symbols remains required.
 * if the target kernel is not customized.
 */

/* Ripped from tcp_ipv4.c */
static inline int tcp_hashfn(u32 laddr, u16 lport, u32 faddr, u16 fport)
{
	int h = ((laddr ^ lport) ^ (faddr ^ fport));
	h ^= h>>16;
	h ^= h>>8;
	return h & (tcp_ehash_size - 1);
}


#ifdef CONFIG_TCP_ABORT_IPV6
/* Ripped from tcp_ipv6.c */
static inline int tcp_v6_hashfn(struct in6_addr *laddr, u16 lport,
                                struct in6_addr *faddr, u16 fport)
{
	int hashent = (lport ^ fport);

	hashent ^= (laddr->s6_addr32[3] ^ faddr->s6_addr32[3]);
	hashent ^= hashent>>16;
	hashent ^= hashent>>8;
	return (hashent & (tcp_ehash_size - 1));
}
#endif


/* Ripped from tcp_input.c */
static void tcp_reset(struct sock *sk)
{
	switch (sk->state) {
	case TCP_SYN_SENT:
		sk->err = ECONNREFUSED;
		break;
	case TCP_CLOSE_WAIT:
		sk->err = EPIPE;
		break;
	case TCP_CLOSE:
		return;
	default:
		sk->err = ECONNRESET;
	}

	if (!sk->dead)
		sk->error_report(sk);

	tcp_done(sk);
}


#ifdef CONFIG_TCP_ABORT_IPV6
static int tcp_abort_hash_v6(tcp_ioc_abort_conn_t *acp, int *bucket)
{
	struct in6_addr *laddr;
	struct in6_addr *raddr;
	int lport, rport;

	laddr = &TCP_AC_V6LOCAL(acp);
	raddr = &TCP_AC_V6REMOTE(acp);
	lport = TCP_AC_V6LPORT(acp);
	rport = TCP_AC_V6RPORT(acp);

	/* No wildcards? */
	if ((ipv6_addr_type(laddr) != IPV6_ADDR_ANY) &&
	    (ipv6_addr_type(raddr) != IPV6_ADDR_ANY) &&
	    (lport != 0) &&
	    (rport != 0)) {

		*bucket = tcp_v6_hashfn(laddr, lport, raddr, rport);
		return 1;
	}

	return 0;
}
#endif


static int tcp_abort_hash_v4(tcp_ioc_abort_conn_t *acp, int *bucket)
{
	u32 laddr, raddr;
	int lport, rport;

	laddr = TCP_AC_V4LOCAL(acp);
	raddr = TCP_AC_V4REMOTE(acp);
	lport = TCP_AC_V4LPORT(acp);
	rport = TCP_AC_V4RPORT(acp);

	/* No wildcards? */
	if ((laddr != INADDR_ANY) &&
	    (raddr != INADDR_ANY) &&
	    (lport != 0) &&
	    (rport != 0)) {

		*bucket = tcp_hashfn(laddr, lport, raddr, rport);
		return 1;
	}

	return 0;
}


static int tcp_abort(tcp_ioc_abort_conn_t *acp)
{
	sa_family_t laf, raf;
	int i, bucket, start_bucket, end_bucket;
	int error = 0, exact = 0, count = 0;

	laf = acp->ac_local.ss_family;
	raf = acp->ac_remote.ss_family;

	if ((acp->ac_start < TCPS_SYN_SENT) ||
	    (acp->ac_end > TCPS_TIME_WAIT) ||
	    (acp->ac_start > acp->ac_end) ||
	    (laf != raf)) {
		return -EINVAL;
	}

	switch (laf) {
	case AF_INET:
		exact = tcp_abort_hash_v4(acp, &bucket);
		break;
#ifdef CONFIG_TCP_ABORT_IPV6
	case AF_INET6:
		exact = tcp_abort_hash_v6(acp, &bucket);
		break;
#endif
	default:
		return -EINVAL;
	}

	if (exact) {
		start_bucket = end_bucket = bucket;
	}
	else {
		start_bucket = 0;
		end_bucket = tcp_ehash_size;
	}

	for (i = start_bucket; i < end_bucket; i++) {
		error = tcp_abort_bucket(acp, i, &count, exact);
		if (error != 0)
			break;
	}

	if ((error == 0) && (count == 0))
		error = -ENOENT;

	return error;
}


#ifdef CONFIG_TCP_ABORT_IPV6
static char *print_ipv6(struct in6_addr *in6)
{
	static DECLARE_MUTEX(mutex);
	static int i, bufindex = 0;
	static char buf[4][48];

	down(&mutex);
	/* Supports at most 4 concurrent invocations. */
	if (++bufindex > 3)
		bufindex = 0;
	up(&mutex);

	for (i = 0; i < 16; i++)
		sprintf(buf[bufindex] + i * 2, "%02x", in6->s6_addr[i]);

	return buf[bufindex];
}


static void tcp_abort_dump_v6(struct sock *sk)
{
	u16 dstp, srcp;
	struct in6_addr *laddr;
	struct in6_addr *raddr;
	struct tcp_opt *tp = &sk->tp_pinfo.af_tcp;

	dstp = ntohs(sk->dport);
	srcp  = ntohs(sk->sport);

	if (sk->state != TCP_TIME_WAIT) {
		struct ipv6_pinfo *np = &sk->net_pinfo.af_inet6;
		laddr = &np->rcv_saddr;
		raddr = &np->daddr;
	}
	else {
		/* IPv6 TIME-WAIT buckets have a different format. */
		struct tcp_tw_bucket *tw = (struct tcp_tw_bucket *)sk;
		laddr = &tw->v6_rcv_saddr;
		raddr = &tw->v6_daddr;
	}

	printk("tcp_abort: IPv6 sock %p: src %s, srcp %d, dst %s, dstp %d, "
	    "state %d [tcps %d], retrans %u, timeout %lu\n",
	    sk, print_ipv6(laddr), srcp, print_ipv6(raddr), dstp, sk->state,
	    TCP_AC_STATE(sk->state), tp->retransmits, tp->timeout);
}
#endif


static void tcp_abort_dump_v4(struct sock *sk)
{
	u32 dst, src;
	u16 dstp, srcp;
	struct tcp_opt *tp = &sk->tp_pinfo.af_tcp;

	dst  = sk->daddr;
	src   = sk->rcv_saddr;
	dstp = ntohs(sk->dport);
	srcp  = ntohs(sk->sport);

	printk("tcp_abort: IPv4 sock %p: src %08x, srcp %d, dst %08x, dstp %d, "
	    "state %d [tcps %d], retrans %u, timeout %lu\n",
	    sk, src, srcp, dst, dstp, sk->state,
	    TCP_AC_STATE(sk->state), tp->retransmits, tp->timeout);
}


static void tcp_abort_dump(struct sock *sk)
{
	switch (sk->family) {
	case AF_INET:
		tcp_abort_dump_v4(sk);
#ifdef CONFIG_TCP_ABORT_IPV6
	case AF_INET6:
		tcp_abort_dump_v6(sk);
#endif
	}
}


static int tcp_abort_reset(struct list_head *list)
{
	struct tcp_abort_sock *as;
	struct sock *sk;

	while (!list_empty(list)) {

		as = list_entry(list->next, struct tcp_abort_sock, as_list);
		list_del(&as->as_list);

		sk = as->as_sock;
		
		if (debug)
			tcp_abort_dump(sk);

		if (sk->state == TCP_TIME_WAIT) {

			if (!dryrun) {
				local_bh_disable();
				tcp_tw_deschedule((struct tcp_tw_bucket *)sk);
				tcp_timewait_kill((struct tcp_tw_bucket *)sk);
				local_bh_enable();
			}

			tcp_tw_put((struct tcp_tw_bucket *)sk);
		}
		else {
			if (!dryrun) {
				bh_lock_sock(sk);
				tcp_reset(sk);
				bh_unlock_sock(sk);
			}

			sock_put(sk);
		}

		kfree(as);
	}

	return 0;
}


static int tcp_abort_queue(struct list_head *list, struct sock *sk)
{
	struct tcp_abort_sock *as;

	as = (struct tcp_abort_sock*) kmalloc(sizeof(struct tcp_abort_sock), GFP_ATOMIC);
	if (as == NULL)
		return -ENOMEM;

	/* Called under head->lock. */
	sock_hold(sk);

	as->as_sock = sk;

	list_add(&as->as_list, list);

	return 0;
}


static int tcp_abort_bucket(tcp_ioc_abort_conn_t *acp, int bucket, int *count, int exact)
{
	struct list_head list;
	struct tcp_ehash_bucket *head;
	struct sock *sk;
	int match, error = 0;

	INIT_LIST_HEAD(&list);

	head = &tcp_ehash[bucket];

	local_bh_disable();

	read_lock(&head->lock);

	for (sk = head->chain; sk != NULL; sk = sk->next) {

		match = tcp_abort_match(acp, sk);
		if (match) {
			/*
			 * Nothing clever shall be made under the above
			 * locks so just queue the matching sockets and
			 * abort them only after having released the locks.
			 */
			error = tcp_abort_queue(&list, sk);
			if (error != 0)
				break;
			(*count)++;

			if (exact)
				goto out_unlock;
		}
	}

	/* Also deal with TIME-WAIT'ers. */
	if (acp->ac_end >= TCPS_TIME_WAIT) {

		for (sk = (head + tcp_ehash_size)->chain; sk != NULL; sk = sk->next) {

			match = tcp_abort_match(acp, sk);
			if (match) {
				error = tcp_abort_queue(&list, sk);
				if (error != 0)
					break;
				(*count)++;

				if (exact)
					goto out_unlock;
			}
		}
	}

out_unlock:
	read_unlock(&head->lock);

	local_bh_enable();

	if (*count > 0) {
		/* Dequeue and abort all the matched sockets. */
		tcp_abort_reset(&list);
	}

	return error;
}

#ifdef MODULE
#ifdef MODULE_LICENSE
MODULE_LICENSE("Proprietary"); 
#endif
MODULE_AUTHOR("Sun Microsystems, Inc.");
#endif
