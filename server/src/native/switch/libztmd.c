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
 * Client applications which communicates over Netlink2 to interact
 * with ztmd on a ZNYX switch. This code originally came from
 * ztmd_sample.c obtained from ZNYX. This should be turned into a
 * client library which can be called from Java through JNI.
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <strings.h>
#include <ctype.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>

#include "libztmd.h"
#include <libznl2.h>

#define NONE -1
#define ADD 1
#define DELETE 2
#define ARP 3
#define IP 4

/* Maximum time to wait for reply, in ms */
#define MAX_WAIT 5000
#define MAX_TRIES 3

int ztmd_pid = 0;
int sequence = 0;
int otherpid = ZNL2_ZTMD_PID;

int verbose = 0;

static const char* progname = 0;

int send_synfin(int sock, struct sockaddr_in* serv, int16_t type);
void dump_packet(void* msgptr, size_t len);
void dump_msg(znl2msg* msg);

/*
 * Setting up the socket ********************************************
 */

int create_sock(struct sockaddr_in *serv, int mport, char *maddr, char *laddr)
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
int setup_multicast(char *bind_addr,
                    int multicast_port, struct sockaddr_in* serv) {
    int mport = (multicast_port == -1) ? ZTMD_MCAST_PORT : multicast_port;
    int sock = create_sock(serv, mport, ZTMD_MCAST_ADDR, bind_addr);
    if (sock < 0) {
        fprintf(stderr, "%s: error creating multicast socket.\n", progname);
        return -1;
    }

    if ((send_synfin(sock, serv, ZNL2_SYN)) < 0) {
        fprintf(stderr, "%s: could not send SYN\n", progname);
        return -1;
    }  
    return sock;
}

/*
 * Handling the reply *************************************************
 */

void handle_error(znl2msghdr *msg)
{
    znl2msgerr *err = (znl2msgerr *) ((char*)msg + sizeof(znl2msghdr));

    if (msg->znl2msg_len < sizeof (znl2msgerr)) {
        fprintf(stderr,  "%s: nack truncated\n", progname);
        return;
    }
    else {
        fprintf(stderr, "%s: NACK received from switch, error = 0x%x\n",
                progname, err->error);
    }
}

int process_packet(znl2msghdr *msg)
{
    if (msg->znl2msg_type == ZNL2MSG_ERROR) {
        znl2msgerrt *err = (znl2msgerrt *) msg;

        if (err->error != 0) {
            handle_error(msg);
        }
        else {
            if (err->omsg.znl2msg_type == ZNL2_SYN) {
                ztmd_pid = err->msg.znl2msg_spid;
                if (verbose > 1)
                    fprintf(stderr,
                            "%s: SYN/ACK received: ztmd from pid %d\n",
                            progname, ztmd_pid);
                return 0;
            }
            else {
                fprintf(stderr, "%s: Error received, omsg type %d\n",
                        progname, err->omsg.znl2msg_type);
            }
        }
    }
    else
        fprintf(stderr, "%s: Error -- received packet type %d\n", progname,
                msg->znl2msg_type);

    return -1;
}

int handle_msg(int length, znl2msghdr *msg)
{
    if (msg->znl2msg_dpid != ZTMD_ZRULE_PID) {
        /* Not meant for us, but we print helpful diagnostics anyway */

        if (msg->znl2msg_dpid == 0 &&
            msg->znl2msg_spid == (ztmd_pid?ztmd_pid:otherpid)) {

            switch (msg->znl2msg_type) {
            case ZNL2_SYN:
                /* ztmd must be starting or restarting, must start over */
                fprintf(stderr, "%s: received SYN from ztmd, must restart\n",
                        progname);
                ztmd_pid = 0;   /* Not registered */
                break;

            case ZNL2_FIN:
                /* ztmd must be quiting, wait for it to come back */
                fprintf(stderr, "%s: received FIN from ztmd\n",
                        progname);
                ztmd_pid = 0;   /* Not registered */
                break;

            default:
                fprintf(stderr,
                        "%s: received an unsupported message -- dropping\n",
                        progname);
                break; 
            }

        }
        else
            fprintf(stderr, "%s: pid %d not mine (%d) ignoring\n",
                    progname, msg->znl2msg_dpid, ZTMD_ZRULE_PID);
        return -1;
    }

    /* msg destined for us */
    return process_packet(msg);
}

int read_msg(int sock, znl2msg* msg)
{
    struct sockaddr_in cli;
    int ret, sock_len, i;
    uint32_t *store;

    memset(&cli, 0, sizeof (struct sockaddr_in));
    sock_len = sizeof (struct sockaddr_in);

    ret = recvfrom(sock, (char *) msg, MAX_MSG + 64, 0,
                   (struct sockaddr *) &cli, &sock_len); 

    if (ret < 0) {
        perror("recvfrom failed");
        return ret;
    }

    /* Endianess/byte ordering */
    store = (uint32_t *)msg;
    for (i = 0; i < ret; i++) {
        store[i] = psdb_pton(store[i]);
    }

    if (verbose > 1)
        fprintf(stderr, "%s: multicast message (%d bytes) received from %s\n", 
                progname, ret, inet_ntoa(cli.sin_addr));

    if (verbose > 3)
        dump_packet(msg, ret);

    return handle_msg(ret, (znl2msghdr*) &msg->n);
}

int get_reply(int sock)
{
    /*
     * Important: on Linux, select(2) modifies tv with the amount of time
     * left on the timer. This means we will wait a cumulative time of
     * MAX_WAIT. On systems that don't do this, every packet we recv
     * that we decide not to handle resets the timer.
     */
    struct timeval tv;
    tv.tv_sec  = MAX_WAIT / 1000;
    tv.tv_usec = MAX_WAIT % 1000;

    while (1) {                 /* Until timeout or we like the message */
        int ret;
        fd_set fds;

        FD_ZERO(&fds); FD_SET(sock, &fds);

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
            if (read_msg(sock, &msg) == 0) {
                if (verbose >= 2)
                    dump_msg(&msg);
                return 0;
            }
        }
    }
}

/*
 * Sending SYN/FIN **************************************************
 */
int send_synfin(int sock, struct sockaddr_in* serv, int16_t type)
{
    znl2msg msg;
    uint32_t *store;
    int ret,i,len;

    msg.n.znl2msg_dpid = otherpid;
    msg.n.znl2msg_spid = ZTMD_ZRULE_PID;
    msg.n.znl2msg_type = type;
    msg.n.znl2msg_seq = sequence++;
    msg.n.znl2msg_version = ZNL2_VER;
    msg.n.znl2msg_flags = ZNL2M_F_ACK;
    msg.n.znl2msg_len = sizeof(znl2msghdr);

    if (type == ZNL2_SYN) {
        znl2attr *tail;

        msg.n.znl2msg_flags_e = ZNL2M_F_ETLV;

        /* Add optional TLVs */
        tail = (znl2attr *) (((char *) &msg.n) +
                             ALIGN(msg.n.znl2msg_len));

        ret = znl2m_addattr_l(&msg.n, MAX_MSG, NL2_OPTIONS, NULL, 0);
        /* name identifier for us */
        {
            char *nom = PROGRAM_NAME;
            ret = znl2m_addattr_l(&msg.n, MAX_MSG, TLV_NAME_ID,
                                  nom, strlen(nom)+1);
        }

        tail->znl2a_len =
            (char *) &msg.n + ALIGN(msg.n.znl2msg_len) - (char *) tail;

        len = msg.n.znl2msg_len;
        store = (uint32_t *)&msg;
        for (i = 0; i < (sizeof(znl2msghdr)/4+2); i++) {
            store[i] = psdb_pton(store[i]);
        }
    } else {
        msg.n.znl2msg_flags_e = 0;
        len = msg.n.znl2msg_len;
        store = (uint32_t *)&msg;
        for (i = 0; i < (sizeof(znl2msghdr)/4); i++) {
            store[i] = psdb_pton(store[i]);
        }
    }

    ret = sendto(sock, &msg,len, 0, (struct sockaddr*)serv, sizeof(*serv));
    if (ret < 0) {
        fprintf(stderr, "%s: error sending %s.\n", progname,
                (type == ZNL2_SYN)?"SYN":"FIN");
        perror("sendto");
    }

    return ret;
}

/*
 * Constructing the packet **************************************
 */

/*
 * Fill the header and return a pointer to just after, for filling in TLVs
 */
char *fill_header(znl2msghdr *msghdr, int msg_type) 
{
    znl2attr *tail, *tail2;
    char* new_tail;
    int ret;

    unsigned int input_pbmp = 0;
    unsigned int output_pbmp = 0;

    int i, name_len;
    char nom[] = PROGRAM_NAME;
    uint32_t *store;

    time((time_t*)&sequence);   /* Unique per transaction */

    msghdr->znl2msg_len = sizeof(znl2msghdr);
    msghdr->znl2msg_version = ZNL2_VER;
    msghdr->znl2msg_seq = sequence;
    msghdr->znl2msg_type = msg_type;
    msghdr->znl2msg_dpid = ZNL2_ZTMD_PID;  
    msghdr->znl2msg_spid = ZTMD_ZRULE_PID;
    msghdr->znl2msg_flags = ZNL2M_F_ACK | ZNL2M_F_REQUEST | ZNL2M_F_ATOMIC;
    msghdr->znl2msg_flags_e = ZNL2M_F_ETLV;

    tail2 = tail = (znl2attr *) ((char *)msghdr + msghdr->znl2msg_len);

    ret = znl2m_addattr_l(msghdr, MAX_MSG, NL2_OPTIONS, NULL, 0);
    if (ret < 0) {
        fprintf(stderr, "%s: error adding NL2_OPTIONS add\n", progname);
        return NULL;
    }

    if (input_pbmp) {
        /* Add input ports */
        ret = znl2m_addattr_l(msghdr, MAX_MSG, TLV_INPUT_PORT_MAP,
                              &input_pbmp, sizeof (unsigned int));

        if (ret < 0) {
            fprintf(stderr, "%s: error TLV_INPUT_PORT_MAP add\n", progname);
            return NULL;
        }
    }

    if (output_pbmp) {
        /* Add output ports */
        ret = znl2m_addattr_l(msghdr, MAX_MSG, TLV_OUTPUT_PORT_MAP, 
                              &output_pbmp, sizeof (unsigned int));

        if (ret < 0) {
            fprintf(stderr, "%s: error TLV_OUTPUT_PORT_MAP add\n", progname);
            return NULL;
        }
    }

    name_len = ALIGN(sizeof(nom));
    ret = znl2m_addattr_l(msghdr, MAX_MSG, TLV_NAME_ID, nom, name_len);

    /*
     * Change byte ordering so the name goes out 
     * correctly.  It will be changed back to original order
     * just before the packet gets sent.
     */
    store = (uint32_t *) ((char *)msghdr + msghdr->znl2msg_len - name_len);
    for (i = 0; i < name_len/4; i++)
        store[i] = psdb_pton(store[i]);

    new_tail = (char *)msghdr + msghdr->znl2msg_len;
    tail->znl2a_len = new_tail - (char *) tail;
    tail2->znl2a_len = new_tail - (char *) tail2;

    return (char *)msghdr + msghdr->znl2msg_len;        
}

/*
 * build_packet()
 *
 * action: "add"|"delete"
 * type: "arp"|"arp_request"|"arp_reply"|"ip"
 * bind: address to send multicast packets from
 * src_mask, src_value: mask and value for src address
 * dest_mask, dest_value: mask and value for dest address
 * srcport_mask, srcport_value: ditto, src port
 * destport_mask, destport_value: ditto, dest port
 * swport: port packets get sent to
 *
 * Remember that mask == 0 means we don't care about that value
 */
int build_packet(znl2msg* msg_ptr,
                 char* action, char* type, char* bind,
                 unsigned int src_mask, unsigned int src_value,
                 unsigned int dest_mask, unsigned int dest_value,
                 unsigned int srcport_mask, unsigned int srcport_value,
                 unsigned int destport_mask, unsigned int destport_value,
                 int swport)
{
    int i, ret, len = 0;
    char* tlvp = 0;
    qdisc_hdr *q = NULL;
    znl2attr *tail, *tail2, *tail3, *attr;
    int msg_type;
    int tcf_action;

    /* Create the filter structure */
    tcf_f_raw_val filter_add;

    /* All packet mask offsets are starting from the IP header */
    filter_add.base = BASE_L3_HDR;

    /* Set the action */
    if (strcmp(action, "delete") == 0)
        msg_type = ZNL2_DELTFILTER;
    else
        msg_type = ZNL2_NEWTFILTER;

    /* Build the header */
    memset(msg_ptr, 0, sizeof (znl2msg));
    if ((q = (qdisc_hdr *)fill_header(&msg_ptr->n, msg_type)) == NULL) {
        fprintf(stderr, "%s: error filling Znetlink2 header\n", progname);
        return -1;
    }

    len = msg_ptr->n.znl2msg_len + sizeof(qdisc_hdr);

    q->handle = 0;
    q->parent = TC_H_INGRESS;
    q->type = TCF_FILTER;
  
    /* Tail points here */
    tlvp = (char *)msg_ptr + len;
    attr = (znl2attr*) tlvp;

    /* Start nesting filter TLVs */
    ret = znl2a_addattr_l(attr, MAX_MSG, TCF_F_DATA, NULL, 0);
    tail = attr;

    len += attr->znl2a_len;
    tlvp += attr->znl2a_len;
    attr = (znl2attr*) tlvp;

    ret = znl2a_addattr_l(attr, MAX_MSG, TCF_F, NULL, 0);
    tail2 = attr;

    len += attr->znl2a_len;
    tlvp += attr->znl2a_len;
    attr = (znl2attr*) tlvp;

    ret = znl2a_addattr_l(attr, MAX_MSG, TCF_F_RAW, NULL, 0);
    tail3 = attr;

    len += attr->znl2a_len;
    tlvp += attr->znl2a_len;

    /* First: dest addr */
    if (dest_mask) {
        filter_add.offset = 16;
        filter_add.value = dest_value;
        filter_add.flags = 0;
        filter_add.mask = dest_mask;
        /* Copy this filter */
        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
        if (verbose > 1)
            fprintf(stderr, "%s: dest: 0x%x/0x%x\n", progname,
                    dest_value, dest_mask);
    }

    /* Src or dest ports */
    if (srcport_mask || destport_mask) {
        /* ports are 16 bits so both are combined into one filter */
        unsigned int mask = 0;
        unsigned int value = 0;
        if (srcport_mask) {
            mask |= (srcport_mask & 0xffff);
            value |= (srcport_value & 0xffff);
            if (verbose > 1)
                fprintf(stderr, "%s: src port: %d(0x%x)/0x%x\n", progname,
                        value, value, mask);
            mask <<= 16;
            value <<= 16;
        }
        if (destport_mask) {
            mask |= (destport_mask & 0xffff);
            value |= (destport_value & 0xffff);
            if (verbose > 1)
                fprintf(stderr, "%s: dest port: %d(0x%x)/0x%x\n", progname,
                        destport_value & 0xffff, destport_value & 0xffff,
                        destport_mask & 0xffff);
        }
        filter_add.offset = 20;
        filter_add.mask = mask;
        filter_add.value = value;
        filter_add.flags = 0;
        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
    }

    /* Src addr */
    if (src_mask) {
        filter_add.offset = 12;
        filter_add.value = src_value;
        filter_add.flags = 0;
        filter_add.mask = src_mask;
        /* Copy this filter */
        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
        if (verbose > 1)
            fprintf(stderr, "%s: src: 0x%x/0x%x\n", progname,
                    src_value, src_mask);
    }

    tail3->znl2a_len = tlvp - (char *)tail3;
    tail2->znl2a_len = tlvp - (char *)tail2;

    /*
     * Actions
     */

    switch (swport) {
    case CPU_PORT:
        tcf_action = TCF_A_COPY_CPU;
        break;

    case NULL_PORT:
        tcf_action = TCF_A_DROP;
        break;

    case ACCEPT:
      tcf_action = TCF_A_ACCEPT;
      swport = NULL_PORT;
      break;

    default:
        tcf_action = TCF_A_REDIRECT;
        break;
    }

    attr = (znl2attr*) tlvp;
    tail2 = attr;
    ret = znl2a_addattr_l(attr, MAX_MSG, TCF_A, NULL, 0);

    len += attr->znl2a_len;
    tlvp += attr->znl2a_len;
    attr = (znl2attr*) tlvp;

    tail3 = attr;
    ret = znl2a_addattr_l(attr, MAX_MSG, 0, NULL, 0);
    if (ret < 0) {
        fprintf(stderr, "%s: error TCF_A_ORDER(%d) add\n", progname, 0);
        return -2;
    }
  
    len += attr->znl2a_len;
    tlvp += attr->znl2a_len;
    attr = (znl2attr*) tlvp;

    /* Set arp actions */
    if (type != 0 && strncasecmp(type, "arp", 3) == 0) {
        int request = 0, reply = 0;
        if (type[3] == 0)               /* Just "arp" */
            request = reply = 1;
        else {
            if (type[3] == '_' && strcasecmp(type+4, "request") == 0)
                request = 1;
            if (type[3] == '_' && strcasecmp(type+4, "reply") == 0)
                reply = 1;
            if (!request && !reply) {
                /* Some different kind of ARP? */
                fprintf(stderr, "%s: unknown ARP type \"%s\"\n", progname,
                        type);
                return -3;
            }
        }

        /* ARP action */
        ret = znl2a_addattr32(attr, MAX_MSG, TCF_A_ARP, 0);
        len += attr->znl2a_len;
        tlvp += attr->znl2a_len;
        attr = (znl2attr*) tlvp;

        /* ARP request is broadcast so we have to use REDIRECT_EXCEPTION */
        if (request && tcf_action == TCF_A_REDIRECT)
            tcf_action = TCF_A_REDIRECT_EXCEPTION;
    }

    /* Set the action into the TLV */
    ret = znl2a_addattr32(attr, MAX_MSG, tcf_action, swport);
    len += attr->znl2a_len;
    tlvp += attr->znl2a_len;
    attr = (znl2attr*) tlvp;

    /* Set the attributes' lengths */
    tail3->znl2a_len = tlvp - (char *)tail3;
    tail2->znl2a_len = tlvp - (char *)tail2;
    tail->znl2a_len = tlvp - (char *)tail;

    /* We have the final packet length */
    msg_ptr->n.znl2msg_len = len;

    /* Byte ordering */
    for (i = 0; i < (len /4); i++) {
        ((uint32_t *) &msg_ptr->n)[i] =
            psdb_pton(((uint32_t *) &msg_ptr->n)[i]);
    }

    return len;
}

int znl2_send_rule(char* action, char* type, int multicast_port, char* bind,
                   unsigned int src_mask, unsigned int src_value,
                   unsigned int dest_mask, unsigned int dest_value,
                   unsigned int srcport_mask, unsigned int srcport_value,
                   unsigned int destport_mask, unsigned int destport_value,
                   int swport, const char* name, int debug, int dont_send)
{

    int sock;
    struct sockaddr_in serv; 
    znl2msg msg;
    int i, len, rc;

    verbose = debug;
    progname = name;

    /* Setup the multicast socket and send SYN */
    if (!dont_send)
      if ((sock = setup_multicast(bind, multicast_port, &serv)) < 0)
            return -1;

    /* Build the message */
    if ((len = build_packet(&msg, action, type, bind,
                            src_mask, src_value, dest_mask, dest_value,
                            srcport_mask, srcport_value,
                            destport_mask, destport_value, swport)) < 0) {
        /* Not a damn thing we can do! */
        fprintf(stderr, "%s: couldn't build packet (%d)\n", progname, len);
        return -1;
    }

    if (verbose >= 2)
        dump_msg(&msg);
    if (verbose >= 3)
        dump_packet(&msg, len);

    if (dont_send)
        return 0;

    for (i = 0; ; i++) {
        /* Send the message */
        if (sendto(sock, &msg, len, 0, (struct sockaddr *) &serv,
                   sizeof (serv)) < 0) {
            perror("sendto");
            /* Let's try again */
            continue;
        }

        /* Send the FIN */
        (void) send_synfin(sock, &serv, ZNL2_FIN);
  
        /* Wait for ACK */
        if ((rc = get_reply(sock)) == 0)
            /* Yay! */
            return 0;

        if (i >= MAX_TRIES)
          break;

        if (verbose > 0)
          fprintf(stderr, "%s: %s; retrying...\n", progname,
                  (rc == -2)?"timeout":"error");
    }

    fprintf(stderr, "%s: no reply from the switch, ABORT\n", progname);
    return -1;
}

/*
 * Re-implemented functions from libznl2 that we need
 */

int znl2m_addattr_l(znl2msghdr *hdr, int maxlen, int type, void *data, int alen)
{
    znl2attr *attr;
    int l = sizeof(znl2attr) + ALIGN(alen);

    long size = hdr->znl2msg_len + l;
    if (size > maxlen)
        return -1;

    attr = (znl2attr *)((char *)hdr + hdr->znl2msg_len);
    attr->znl2a_type = type;
    attr->znl2a_len = l;
    bcopy(data, (char*)attr + sizeof(znl2attr), alen);
    hdr->znl2msg_len = l + hdr->znl2msg_len;

    return 0;
}

int znl2a_addattr32(znl2attr *znl2, int maxlen, int type, uint32_t data)
{
    znl2attr *attr;
    int l = sizeof(znl2attr) + sizeof(data);

    long size = znl2->znl2a_len + l;
    if (size > maxlen)
        return -1;

    attr = (znl2attr *)((char *)znl2 + znl2->znl2a_len);
    znl2->znl2a_len = znl2->znl2a_len + l;
    attr->znl2a_type = type;
    attr->znl2a_len = l;
    bcopy(&data, (char*)attr + sizeof(znl2attr), sizeof(data));

    return 0;
}

int znl2a_addattr_l(znl2attr *znl2, int maxlen, int type, void *data, int alen)
{
    znl2attr *attr;
    int l =  sizeof(znl2attr) + ALIGN(alen);

    long size = znl2->znl2a_len + l;
    if (size > maxlen)
        return -1;

    attr = (znl2attr *)((char *)znl2 + znl2->znl2a_len);
    attr->znl2a_type = type;
    attr->znl2a_len = l;
    bcopy(data, (char*)attr + sizeof(znl2attr), alen);

    return 0;
}









extern const char* type2s(int type);
extern const char* flags2s(unsigned int flags);
extern const char* flags_e2s(unsigned int flags_e);
extern const char* err2s(int err);



#define INDENT 3
/* #define XML */

#ifdef XML
#define tag(s) ("<" s " />")
#define tag_open(s) ("<" s ">")
#define tag_close(s) ("</" s ">")
#else
#define tag(s) (s)
#define tag_open(s) (s)
#define tag_close(s) ""
#endif

typedef unsigned char* ptr;

static void dump_TLV(ptr p, int indent);
static const char* pad(int level);

#ifdef XML
static int xml = 1;
#else
static int xml = 0;
#endif

static const char* to_octets(uint32_t v)
{
    static char buf[50];
    sprintf(buf, "%d.%d.%d.%d",
            (v & 0xff000000) >> 24,  (v & 0xff0000) >> 16,
            (v & 0xff00) >> 8, v & 0xff);
    return buf;
}

static const char* to_shorts(uint32_t v)
{
    static char buf[50];
    sprintf(buf, "%d.%d", (v & 0xffff0000) >> 16, v & 0xffff);
    return buf;
}

static const char* hexstring(ptr p, size_t len)
{
    static char buf[100], *s = buf;
    int i;

    if (len < 0)
        len = 0;
    if (len > sizeof(buf)/2)
        len = sizeof(buf)/2;

    for (i = 0; i < len; i++) {
        sprintf(s, "%02x", p[i] & 0xff);
        s += 2;
    }

    buf[sizeof(buf)-1] = 0;
    return buf;
}

static ptr get_TLV(ptr p, uint16_t* type, uint16_t* length)
{
    znl2attr* tlv = (znl2attr*) p;

    *type = tlv->znl2a_type;
    *length = tlv->znl2a_len;
    return p + sizeof(znl2attr);
}

static const char* pad(int level)
{
    static char buf[] = "                                                    "
        "                                   ";
    int len = sizeof(buf) - 1;
    if (level < 0)
        return "";
    return buf + (len - level*INDENT);
}

static void dump_pmaps(const char* msg, ptr p, size_t length, int indent)
{
    if (xml)
        fprintf(stdout, "%s<%s>\n", pad(indent), msg);
    else
        fprintf(stdout, "%s%s\n", pad(indent), msg);
    
    while (length >= sizeof(uint32_t)) {
        fprintf(stdout, "%s0x%08x\n", pad(indent + 1), *(uint32_t*)p);
        p += sizeof(uint32_t);
        length -= sizeof(uint32_t);
    }
    if (xml)
        fprintf(stdout, "%s</%s>\n", pad(indent), msg);
}

static void dump_NL2(ptr p, size_t size, int indent)
{
    while (size >= sizeof(znl2attr)) {
        uint16_t type;
        uint16_t length;
        ptr value = get_TLV(p, &type, &length);

        if (length == 0)
            break;

        p += length;
        size -= length;

        switch (type) {

        case TLV_INPUT_PORT_MAP:    /* 2 */
            dump_pmaps("TLV_INPUT_PORT_MAP", value, length, indent);
            break;
        
        case TLV_OUTPUT_PORT_MAP:   /* 3 */
            dump_pmaps("TLV_OUTPUT_PORT_MAP", value, length, indent);
            break;

        case TLV_NAME_ID:           /* 4 */
            fprintf(stdout, "%s%s\n%s\"%s\"\n",
                    pad(indent), tag_open("TLV_NAME_ID"),
                    pad(indent + 1), value);
            if (xml)
                fprintf(stdout, "%s</TLV_NAME_ID>\n", pad(indent));

            break;
        }
    }
}

static void dump_TCF_F_TLVs(ptr p, size_t len, int indent)
{
    uint16_t type;
    uint16_t length;
    ptr value;

    value = get_TLV(p, &type, &length);
    p += length;
    if (type == TCF_F_RAW) {

        for (; length > sizeof(tcf_f_raw_val); length -= sizeof(tcf_f_raw_val)) {
            tcf_f_raw_val* tcf_f_raw = (tcf_f_raw_val*) value;
            value += sizeof(tcf_f_raw_val);

            fprintf(stdout, "%s", pad(indent));
            if (xml)
                fputc('<', stdout);
            fputs("TCF_F_RAW base = ", stdout);
            switch (tcf_f_raw->base) {
            case BASE_MAC_HDR: fprintf(stdout, "BASE_MAC_HDR\n"); break;
            case BASE_L3_HDR: fprintf(stdout, "BASE_L3_HDR\n"); break;
            default:  fprintf(stdout, "%d\n", tcf_f_raw->base);
            }
            fprintf(stdout, "%s    offset = %d", pad(indent),
                    tcf_f_raw->offset);
            if (tcf_f_raw->base == BASE_L3_HDR)
                switch (tcf_f_raw->offset) {
                case 12: fprintf(stdout, " (src)"); break;
                case 16: fprintf(stdout, " (dest)"); break;
                case 20: fprintf(stdout, " (s/d ports)"); break;
                }
            fprintf(stdout, ", flags = 0x%02x\n", tcf_f_raw->flags);
            fprintf(stdout, "%s    value = %d (%s, %s), mask = 0x%08x%s\n",
                    pad(indent), tcf_f_raw->value,
                    to_octets(tcf_f_raw->value), to_shorts(tcf_f_raw->value),
                    tcf_f_raw->mask, (xml? " />" : ""));
        }

        value = get_TLV(p, &type, &length);
        p += length;
    }
    else
        fprintf(stdout, "Expected TCF_F_RAW, found %d\n", type);

    if (type == TCF_F_IPT) {
        for (; length > sizeof(tcf_f_ipt_val); length -= sizeof(tcf_f_ipt_val)){
            tcf_f_ipt_val* tcf_f_ipt = (tcf_f_ipt_val*) value;
            fprintf(stdout, "%", pad(indent));
            if (xml)
                fputc('<', stdout);
            fprintf(stdout, "TCF_F_IPT proto 0x%04x/0x%04x\n", pad(indent),
                    tcf_f_ipt->proto, tcf_f_ipt->proto_mask);
            fprintf(stdout, "%s    src %s/0x%08x:%d dest %s/0x%08x:%d\n",
                    pad(indent),
                    to_octets(tcf_f_ipt->src_ip), tcf_f_ipt->src_mask,
                    tcf_f_ipt->src_port0,
                    to_octets(tcf_f_ipt->dst_ip), tcf_f_ipt->dst_mask,
                    tcf_f_ipt->dst_port0);
            fprintf(stdout, "%s    src_port1: 0x%04x\tdst_port1: 0x%04x\n",
                    pad(indent), tcf_f_ipt->src_port1, tcf_f_ipt->dst_port1);
            fprintf(stdout, "%s    invf: 0x%02x\tpinvf: 0x%02x\t"
                    "tcpf: 0x%02x\ttcpf_mask: 0x%02x\n", pad(indent),
                    tcf_f_ipt->invf, tcf_f_ipt->pinvf, tcf_f_ipt->tcpf,
                    tcf_f_ipt->tcpf_mask);
            if (xml)
                fputs(tag_close("TCF_F_IPT"), stdout);
        }
    }
}

static void dump_TCF_A_TLVs(ptr p, size_t len, int indent)
{
    if (p == 0)
        return;

    /* We have a sequence of TLVs */
    while (len > 0) {
        uint16_t type;
        uint16_t length;
        ptr value;

        value = get_TLV(p, &type, &length);

        p += length;
        len -= length;
        if (len < 0)
            break;

        switch (type) {

        case TCF_A_SET_PRIO:
            fprintf(stdout, "%s%s\n", pad(indent), tag_open("TCF_A_SET_PRIO"));
            dump_TCF_A_TLVs(value, length - sizeof(znl2attr), indent + 1);
            if (xml)
                fprintf(stdout, "%s%s\n", pad(indent),
                        tag_close("TCF_A_SET_PRIO"));
            break;

        case TCF_A_COPY_CPU:
            fprintf(stdout, "%s%s\n", pad(indent), tag("TCF_A_COPY_CPU"));
            break;

        case TCF_A_DROP:    
            fprintf(stdout, "%s%s\n", pad(indent), tag("TCF_A_DROP"));
            break;

        case TCF_A_ACCEPT:  
            fprintf(stdout, "%s%s\n", pad(indent), tag("TCF_A_ACCEPT"));
            break;

        case TCF_A_REDIRECT: 
            fprintf(stdout, "%s%s\n", pad(indent), tag_open("TCF_A_REDIRECT"));
            fprintf(stdout, "%s%d\n", pad(indent + 1), *(int*)value);
            if (xml)
                fprintf(stdout, "%s%s\n", pad(indent), tag_close("TCF_A_REDIRECT"));
            break;

        case TCF_A_ARP:    
            fprintf(stdout, "%s%s\n", pad(indent), tag_open("TCF_A_ARP"));
            fprintf(stdout, "%s%s\n", pad(indent + 1),
                    hexstring(value, length - sizeof(znl2attr)));
            if (xml)
                fprintf(stdout, "%s%s\n", pad(indent), tag_close("TCF_A_ARP"));
            break;

        case TCF_A_REDIRECT_EXCEPTION: 
            fprintf(stdout, "%s%s\n", pad(indent),
                    tag_open("TCF_A_REDIRECT_EXCEPTION"));
            fprintf(stdout, "%s%d\n", pad(indent + 1), *(int*)value);
            if (xml)
                fprintf(stdout, "%s%s\n", pad(indent),
                        tag_close("TCF_A_REDIRECT_EXCEPTION"));
            break;

        default:
            fprintf(stdout, "Unexpected TCF_A_ value %d\n", type);
            break;
        }
    }
}

/* Filters are actions */
static void dump_qdisc(ptr p, size_t len, int indent)
{
    uint16_t type;
    uint16_t length;
    ptr value;

    qdisc_hdr* qdisc = (qdisc_hdr*) p;

    if (p == 0)
        return;

    p += sizeof(qdisc_hdr);
    len -= sizeof(qdisc_hdr);

    fprintf(stdout, pad(indent));
    if (xml)
        fputc('<', stdout);
    fprintf(stdout, "QDisc handle = 0x%08x, parent = 0x%08x, type = ",
            qdisc->handle, qdisc->parent);
    switch (qdisc->type) {
    case QTMAX: fprintf(stdout, "QTMAX"); break;
    case QTMIN: fprintf(stdout, "QTMIN"); break;
    case TCF_FILTER: fprintf(stdout, "TCF_FILTER"); break;
    case TCQ_Q_BFIFO: fprintf(stdout, "TCQ_Q_BFIFO"); break;
    case TCQ_Q_HTB: fprintf(stdout, "TCQ_Q_HTB"); break;
    case TCQ_Q_PFIFO: fprintf(stdout, "TCQ_Q_PFIFO"); break;
    case TCQ_Q_PRIO: fprintf(stdout, "TCQ_Q_PRIO"); break;
    case TCQ_Q_RED: fprintf(stdout, "TCQ_Q_RED"); break;
    case TCQ_Q_WRR: fprintf(stdout, "TCQ_Q_WRR"); break;
    default: fprintf(stdout, "0x%08x", qdisc->type);
    }
    if (xml)
        fputc('>', stdout);
    fputc('\n', stdout);

    if (qdisc->type != TCF_FILTER)
        return;

    value = get_TLV(p, &type, &length);
    if (type != TCF_F_DATA) {
        fprintf(stderr, "Error: expected TCF_F_DATA\n");
        return;
    }
    else
        fprintf(stdout, "%s%s\n", pad(indent + 1), tag_open("TCF_F_DATA"));

    if (length > len) {
        fprintf(stdout, "Buffer too short: should have %d but only have %d\n",
                length, len);
        return;
    }
    len = length;
    p = value;

    /* Now we expect groups of TCF_F, TCF_A, TCF_I, and TCF_O TLVs */

    while (len > 0) {
        value = get_TLV(p, &type, &length);
        if (length == 0)
            break;
        p += length; len -= length;
    
        switch (type) {

        case TCF_F:
            fprintf(stdout, "%s%s\n", pad(indent + 2), tag_open("TCF_F"));
            dump_TCF_F_TLVs(value, length - sizeof(znl2attr), indent + 3);
            if (xml)
                fprintf(stdout, "%s</TCF_F>\n", pad(indent + 2));
            break;

        case TCF_A:
            fprintf(stdout, "%s%s\n", pad(indent + 2), tag_open("TCF_A"));
            dump_TCF_A_TLVs(value, length - sizeof(znl2attr), indent + 3);
#ifdef XML
            fprintf(stdout, "%s</TCF_A>\n", pad(indent + 2));
#endif
            break;

        case TCF_I:
            dump_pmaps("TCF_I", value, length - sizeof(znl2attr), indent + 2);
            break;

        case TCF_O:
            dump_pmaps("TCF_O", value, length - sizeof(znl2attr), indent + 2);
            break;

        }
    }
#ifdef XML
    fprintf(stdout, "%s</TCF_F_DATA>\n", pad(indent + 1));
    fprintf(stdout, "%s</QDisc>\n", pad(indent));
#endif
}

static void dump_TLVs(ptr p, size_t size, int indent)
{
    uint16_t type;
    uint16_t length;
    ptr value;

    if (size < sizeof(znl2attr))
        return;

    value = get_TLV(p, &type, &length);
    p += length;
    size -= length;

    if (type == NL2_OPTIONS) {
        fprintf(stdout, "%s%s\n", pad(indent), tag_open("NL2_OPTIONS"));
        dump_NL2(value, length - sizeof(znl2attr), indent + 1);
        if (xml)
            fprintf(stdout, "%s</NL2_OPTIONS>\n", pad(indent));
    }
    else {
        fprintf(stdout, "%s%s%d (size=%d)%s\n", pad(indent), (xml? "<" : ""),
                type, length, (xml? " />" : ""));
    }

    /* NL2_OPTIONS are followed by qdisc (Sec 4.0 in Netlink2 doc) */

    if (size >= sizeof(qdisc_hdr))
        dump_qdisc(p, size, indent);
}

static void dump_header(znl2msghdr* hdr, int indent)
{
    fprintf(stdout, "%s%sMessage seq = %d,\n", pad(indent), (xml? "<" : ""),
            hdr->znl2msg_seq);
    fprintf(stdout, "%slen = %d, type = %s,\n", pad(indent + 2),
            hdr->znl2msg_len, type2s(hdr->znl2msg_type));
    fprintf(stdout, "%sflags_e = %s,\n", pad(indent + 2),
            flags_e2s(hdr->znl2msg_flags_e));
    fprintf(stdout, "%sflags = %s,\n", pad(indent + 2),
            flags2s(hdr->znl2msg_flags));
    fprintf(stdout, "%ssPID = %d, dPID = %d%s\n", pad(indent + 2), 
            hdr->znl2msg_spid, hdr->znl2msg_dpid,  (xml? ">" : ""));
}

const char* flags2s(unsigned int f)
{
    static char buf[1024];
    char *p = buf;

    if (f == 0)
        return "0";

    if (f & ZNL2M_F_REQUEST)
        p += sprintf(p, " | ZNL2M_F_REQUEST");
    if (f & ZNL2M_F_MULTI)
        p += sprintf(p, " | ZNL2M_F_MULTI");
    if (f & ZNL2M_F_ACK)
        p += sprintf(p, " | ZNL2M_F_ACK");
    if (f & ZNL2M_F_ECHO)
        p += sprintf(p, " | ZNL2M_F_ECHO");
    if (f & ZNL2M_F_ROOT)
        p += sprintf(p, " | ZNL2M_F_ROOT");
    if (f & ZNL2M_F_MATCH)
        p += sprintf(p, " | ZNL2M_F_MATCH");
    if (f & ZNL2M_F_ATOMIC)
        p += sprintf(p, " | ZNL2M_F_ATOMIC");
    if (f & ZNL2M_F_REPLACE)
        p += sprintf(p, " | ZNL2M_F_REPLACE");
    if (f & ZNL2M_F_EXCL)
        p += sprintf(p, " | ZNL2M_F_EXCL");
    if (f & ZNL2M_F_CREATE)
        p += sprintf(p, " | ZNL2M_F_CREATE");
    if (f & ZNL2M_F_APPEND)
        p += sprintf(p, " | ZNL2M_F_APPEND");

    return buf + 3;
}

const char* flags_e2s(unsigned int f)
{
    static char buf[1024];
    char *p = buf;

    if (f == 0)
        return "0";

    if (f & ZNL2M_F_SYN)
        p += sprintf(p, " | ZNL2M_F_SYN");
    if (f & ZNL2M_F_FIN)
        p += sprintf(p, " | ZNL2M_F_FIN");
    if (f & ZNL2M_F_ETLV)
        p += sprintf(p, " | ZNL2M_F_ETLV");
    if (f & ZNL2M_F_PRIO)
        p += sprintf(p, " | ZNL2M_F_PRIO");
    if (f & ZNL2M_F_ASTR)
        p += sprintf(p, " | ZNL2M_F_ASTR");

    return buf + 3;
}

const char* type2s(int type)
{
    static char buffer[1000];

    switch (type) {
    case ZNL2MSG_DONE: return "ZNL2MSG_DONE";
    case ZNL2MSG_ERROR: return "ZNL2MSG_ERROR";
    case ZNL2MSG_NOOP: return "ZNL2MSG_NOOP";
    case ZNL2_DELCLASS: return "ZNL2_DELCLASS";
    case ZNL2_DELQDISC: return "ZNL2_DELQDISC";
    case ZNL2_DELTFILTER: return "ZNL2_DELTFILTER";
    case ZNL2_FIN: return "ZNL2_FIN";
    case ZNL2_GETCLASS: return "ZNL2_GETCLASS";
    case ZNL2_GETQDISC: return "ZNL2_GETQDISC";
    case ZNL2_GETTFILTER: return "ZNL2_GETTFILTER";
    case ZNL2_NEWCLASS: return "ZNL2_NEWCLASS";
    case ZNL2_NEWQDISC: return "ZNL2_NEWQDISC";
    case ZNL2_NEWTFILTER: return "ZNL2_NEWTFILTER";
    case ZNL2_SYN: return "ZNL2_SYN";
    default: break;
    }

    sprintf(buffer, "0x%02x", type);
    return buffer;
}

const char* err2s(int err)
{
    static char buffer[1000];

    switch(err) {
    case 0: return "0";
    case ZNL2MSG_NOK: return "ZNL2MSG_NOK";
    case ZNL2MSG_RUNT: return "ZNL2MSG_RUNT";
    case ZNL2_BTTLV: return "ZNL2_BTTLV";
    case ZNL2_NTTLV: return "ZNL2_NTTLV";
    case ZNL2_BPTLV: return "ZNL2_BPTLV";
    case ZNL2_BQH: return "ZNL2_BQH";
    case ZNL2_BQHL: return "ZNL2_BQHL";
    case ZNL2_BQL: return "ZNL2_BQL";
    case ZNL2_BTCAO: return "ZNL2_BTCAO";
    case ZNL2_BTCAOP: return "ZNL2_BTCAOP";
    case ZNL2_BFO: return "ZNL2_BFO";
    case ZNL2_BFOP: return "ZNL2_BFOP";
    case ZNL2_QSTR: return "ZNL2_QSTR";
    case ZNL2_BQT: return "ZNL2_BQT";
    case ZNL2_BCMD: return "ZNL2_BCMD";
    case ZNL2_NORM: return "ZNL2_NORM";
    case ZNL2_NOFIL: return "ZNL2_NOFIL";
    case ZNL2_NOQD: return "ZNL2_NOQD";
    case ZNL2_NOCLS: return "ZNL2_NOCLS";
    case ZNL2_SYS: return "ZNL2_SYS";
    case ZNL2_XMUL: return "ZNL2_XMUL";
    }

    sprintf(buffer, "0x%02x", err);
    return buffer;
}

void dump_msg(znl2msg* msg)
{
    unsigned char* p;
    znl2msghdr* hdr = &msg->n;
    int payload_size;
    int indent = 0;

    p = (ptr)&msg->buf;
    payload_size = hdr->znl2msg_len - sizeof(znl2msghdr);

    dump_header(hdr, indent++);

    if (hdr->znl2msg_type == ZNL2MSG_ERROR) {
        /* it's a reply */
        znl2msgerrt* reply = (znl2msgerrt*) msg;

        fprintf(stdout, "%s%s\n%s%s\n",
                pad(indent), tag_open("ErrorCode"),
                pad(indent + 1), err2s(reply->error));
        if (xml)
            fprintf(stdout, "%s%s\n", pad(indent), tag_close("ErrorCode"));

        dump_header(&reply->omsg, indent);
        if (xml)
            fprintf(stdout, "%s</Message>\n", pad(indent));
    }
    else
        dump_TLVs(p, payload_size, indent);

    if (xml)
        fprintf(stdout, "</Message>\n");
}


#define LINE 16
static int offset = 0;
static void dump_buf(unsigned char* buf, size_t len)
{
    int i;
    
    fprintf(stdout, "%8d  ", offset);
    offset += len;

    for (i = 0; i < len; i++)
        fprintf(stdout, "%02x ", buf[i]);
    for ( ; i < LINE; i++)
        fprintf(stdout, "   ");

    fputs("    ", stdout);

    for (i = 0; i < len; i++)
        if (isprint(buf[i]))
            fprintf(stdout, "%c", buf[i]);
        else
            fputs(" ", stdout);

    fputs("\n", stdout);
}

void dump_packet(void* msgptr, size_t len)
{
    unsigned char* msg = (unsigned char*) msgptr;

    if (len < sizeof(znl2msghdr))
        fprintf(stdout, "Warning: message 0x%08x too short (%d)\n", msg, len);

    fprintf(stdout, "Raw message buf. 0x%08x (size %d):\n", msg, len);

#   if (PDK_NETWORK_ORDER == 1234)
        fprintf(stdout, "PDK_NETWORK_ORDER = 1234\n");
#   endif

    offset = 0;
    while (len > LINE) {
        dump_buf(msg, LINE);
        len -= LINE;
        msg += LINE;
    }

    dump_buf(msg, len);

}
