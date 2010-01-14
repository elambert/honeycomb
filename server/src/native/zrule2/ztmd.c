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

static char id[] = "$Id: ztmd.c 10855 2007-05-19 02:54:08Z bberndt $";

#define SEQ_FILE "/var/tmp/zrule-seq"

int ztmd_pid = 0;
long sequence = 0;
int mypid = ZTMD_ZRULE_PID;
int otherpid = ZNL2_ZTMD_PID;

int verbose = 0;
char* progname = 0;

/** 
 *  Rules are added to all ports by default. When ingress=1, the rules
 *  are only applied to the ingress port(s), currently set to port
 *  zre22, IFINDEX 314. This is needed for filtering rules which only
 *  allow ingress traffic to the cell ip addresses.
 */ 
int ingress = 0;

static char namebuf[BUFSIZ];
static int sock = -1;
static struct sockaddr_in my_addr;

static int lock_fd = -1;
static long get_sequence();
static void save_sequence(long seq);

static int max_tries = MAX_TRIES;

static int send_msg(znl2msg* msg, int sock);

static long seq_init = 0;

static int pretend_count = 0;
static const int num_pretend_rules = 3;

void more_verbose() { verbose++; }
void set_name(const char* name) {
    strcpy(namebuf, name);
    progname = namebuf;
}

int nl2_initialize(const char* my_address)
{
    time_t now;
    time(&now);
    srandom((unsigned int)now);
    seq_init = random();

    sequence = get_sequence();

    if (my_address == NULL)     /* Pretending to send */
        return 0;

    if ((sock = setup_multicast(my_address, &my_addr)) < 0)
        return -1;

    return 0;
}

void set_retries(unsigned long num_retries)
{
    max_tries = 1 + (int) num_retries;
}

static int send_rule(int action, int swport)
{
    znl2msg msg;
    int i, len, rc, type;

    /* Handle reset */

    if (action == RESET) {
        /* First send a FIN to reset everything, then SYN */

        fill_header(&msg.n, ZNL2_FIN);

        /* don't test != 0 -- error is OK as long as we get a reply */
        if (send_msg(&msg, sock) < 0)
            return -1;

        sequence = seq_init = random();

        fill_header(&msg.n, ZNL2_SYN);
        if (send_msg(&msg, sock) != 0)
            return -1;

        return 0;
    }

    /* Handling for add and delete is identical */

    if (action == DELETE)
        get_nl2msg(&msg, ZNL2_DELTFILTER, swport);
    else if (action == ADD)
        get_nl2msg(&msg, ZNL2_NEWTFILTER, swport);
    else
        return -1;

    if (verbose >= 2)
        dump_msg(&msg);

    if (sock < 0) {
        /* Only pretending: debug */

        if (msg.n.znl2msg_type == ZNL2_DELTFILTER && swport == UNSPEC)
            if (++pretend_count > num_pretend_rules)
                return ZNL2_NOFIL;
        return 0;
    }

    return send_msg(&msg, sock);
}

int nl2_send_rule(int action, int swport)
{
    int rc;
#ifdef SANE_ZNL2
    int i;
    /*
     * Their stupid API has overlapping type values, which makes
     * packet debugging really suck. The "sane znl2" has distinct type
     * values, for use when debugging packet construction. Ordinarily
     * this section is disabled.
     */
    static const char* enum2s(int i);
    for (i = 0; i < TCF_A_LB_CONFORM; i++)
        fprintf(stderr, "    %2d 0x%02x %s\n", i, i, enum2s(i));
#endif

    pretend_count = 0;          /* debug only */

    /*
     * The Netlink2 delete API: if the switch port is left
     * unspecified, it's a wildcard and we have to delete all matching
     * rules. However, each NL2 message only deletes one rule.
     * Repeatedly send the delete request until we get back a
     * ZNL2_NOFIL, which indicates all matching rules have been
     * removed.
     */

    do {
        rc = send_rule(action, swport);
    } while (action == DELETE && swport == UNSPEC && rc == 0);

    save_sequence(sequence);
    return rc;
}

static int send_msg(znl2msg* msg, int sock)
{
    int i;
    int len;
    int err = 0;
    uint32_t* store;

    for (i = 0; i < max_tries; i++) {

        if (nl2_send(sock, &my_addr, msg) < 0) {
            perror("send-msg");
            return -2;
        }

        /* Wait for ACK */
        if ((err = get_reply(sock, &msg->n)) >= 0)
            return err;

        fprintf(stderr, "%s: %s; retrying...\n", progname,
                (err == -2)?"timeout":"error");
    }

    fprintf(stderr, "%s: no reply from the switch, ABORT\n", progname);
    return -1;
}

static long get_sequence()
{
    int nread;
    char buffer[100];

    if ((lock_fd = open(SEQ_FILE, O_RDWR | O_CREAT, 0600)) < 0) {
        perror(SEQ_FILE);
        return seq_init;
    }

    /* Lock the file -- block until we can acquire the lock */
    if (lockf(lock_fd, F_LOCK, 0) < 0) {
        perror("flock");
        return seq_init;
    }

    if ((nread = read(lock_fd, buffer, sizeof(buffer)-1)) < 0) {
        perror(SEQ_FILE);
        return seq_init;
    }

    if (nread == 0)
        return seq_init;

    buffer[nread] = 0;
    return strtoul(buffer, 0, 10);
}

static void save_sequence(long seq)
{
    char buffer[100];
    size_t len;

    if (lock_fd < 0) {
        fprintf(stderr, "Warning: couldn't save sequence number\n");
        return;
    }

    (void) lseek(lock_fd, 0, SEEK_SET);
    len = snprintf(buffer, sizeof(buffer), "%ld\n", seq);
    write(lock_fd, buffer, len);
    
    close(lock_fd);                  /* releases the lock */
    lock_fd = -1;
}

#ifdef SANE_ZNL2
static const char* enum2s(int i)
{
    switch (i) {
    case ZNL2MSG_NOOP: return "ZNL2MSG_NOOP";
    case ZNL2MSG_ERROR: return "ZNL2MSG_ERROR";
    case ZNL2MSG_DONE: return "ZNL2MSG_DONE";
    case ZNL2_NEWQDISC: return "ZNL2_NEWQDISC";
    case ZNL2_DELQDISC: return "ZNL2_DELQDISC";
    case ZNL2_GETQDISC: return "ZNL2_GETQDISC";
    case ZNL2_NEWTFILTER: return "ZNL2_NEWTFILTER";
    case ZNL2_DELTFILTER: return "ZNL2_DELTFILTER";
    case ZNL2_GETTFILTER: return "ZNL2_GETTFILTER";
    case ZNL2_NEWCLASS: return "ZNL2_NEWCLASS";
    case ZNL2_DELCLASS: return "ZNL2_DELCLASS";
    case ZNL2_GETCLASS: return "ZNL2_GETCLASS";
    case ZNL2_SYN: return "ZNL2_SYN";
    case ZNL2_FIN: return "ZNL2_FIN";
    case NL2_OPTIONS: return "NL2_OPTIONS";
    case NL2_UNSPEC: return "NL2_UNSPEC";
    case TLV_CHECKSUM: return "TLV_CHECKSUM";
    case TLV_MSG_PRIO: return "TLV_MSG_PRIO";
    case TLV_INPUT_PORT_MAP: return "TLV_INPUT_PORT_MAP";
    case TLV_OUTPUT_PORT_MAP: return "TLV_OUTPUT_PORT_MAP";
    case TLV_NAME_ID: return "TLV_NAME_ID";
    case QTMIN: return "QTMIN";
    case TCQ_Q_PRIO: return "TCQ_Q_PRIO";
    case TCQ_Q_WRR: return "TCQ_Q_WRR";
    case TCQ_Q_HTB: return "TCQ_Q_HTB";
    case TCQ_Q_RED: return "TCQ_Q_RED";
    case TCQ_Q_PFIFO: return "TCQ_Q_PFIFO";
    case TCQ_Q_BFIFO: return "TCQ_Q_BFIFO";
    case TCF_FILTER: return "TCF_FILTER";
    case QTMAX: return "QTMAX";
    case TCF_F_RAW: return "TCF_F_RAW";
    case TCF_F_IPT: return "TCF_F_IPT";
    case ZNL2M_OPTIONS: return "ZNL2M_OPTIONS";
    case TCF_F_DATA: return "TCF_F_DATA";
    case TCF_F: return "TCF_F";
    case TCF_A: return "TCF_A";
    case TCF_O: return "TCF_O";
    case TCF_I: return "TCF_I";
    case OPTL_O_TLV: return "OPTL_O_TLV";
    case OPTL_I_TLV: return "OPTL_I_TLV";
    case FILT_I_TLV: return "FILT_I_TLV";
    case FILT_O_TLV: return "FILT_O_TLV";
    case BASE_MAC_HDR: return "BASE_MAC_HDR";
    case BASE_L3_HDR: return "BASE_L3_HDR";
    case TCF_A_SET_PRIO: return "TCF_A_SET_PRIO";
    case TCF_A_USE_PRIO: return "TCF_A_USE_PRIO";
    case TCF_A_SET_TOS: return "TCF_A_SET_TOS";
    case TCF_A_COPY_CPU: return "TCF_A_COPY_CPU";
    case TCF_A_DROP: return "TCF_A_DROP";
    case TCF_A_REDIRECT: return "TCF_A_REDIRECT";
    case TCF_A_MIRROR: return "TCF_A_MIRROR";
    case TCF_A_COUNT: return "TCF_A_COUNT";
    case TCF_A_CHANGE_PRIO: return "TCF_A_CHANGE_PRIO";
    case TCF_A_CHANGE_PREC: return "TCF_A_CHANGE_PREC";
    case TCF_A_SET_DSCP: return "TCF_A_SET_DSCP";
    case TCF_A_REDIRECT_EXCEPTION: return "TCF_A_REDIRECT_EXCEPTION";
    case TCF_A_ACCEPT: return "TCF_A_ACCEPT";
    case TCF_A_ARP: return "TCF_A_ARP";
    case TCF_A_LB: return "TCF_A_LB";
    case TCF_A_UNTAG: return "TCF_A_UNTAG";
    case TCF_A_LB_PARAMS: return "TCF_A_LB_PARAMS";
    case TCF_A_LB_EXCEED: return "TCF_A_LB_EXCEED";
    case TCF_A_LB_CONFORM: return "TCF_A_LB_CONFORM";
    }
}
#endif
