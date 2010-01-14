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



#ifndef __LIBZTMD_H__
#define __LIBZTMD_H__ 1

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <libgen.h>

#include <strings.h>
#include <ctype.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <errno.h>

static char libztmd_h_id[] =
    "$Id: libztmd.h 10855 2007-05-19 02:54:08Z bberndt $";

/*
 * When debugging packets, include libsaneznl2.h instead
 */
#include <libznl2.h>

/*
 * This is the public interface.
 *
 * Caveat: this is poor-man's OO -- no memory allocations are
 * performed, so the "object" should be treated as a singleton. Only
 * one instance of zrule can be running at any time, and only one
 * thread.
 */

/* Initialize the socket etc. */
extern int nl2_initialize(const char* my_address);

/* These functions set various parameters */
extern void set_name(const char* name);
extern void set_packet_type(int type);
extern void set_protocol(int proto);
extern void set_arp_opcode(int code);
extern void set_icmp_type(int type);
extern void set_src(unsigned int mask, unsigned int value);
extern void set_dest(unsigned int mask, unsigned int value);
extern void set_srcport(unsigned int mask, unsigned int value);
extern void set_destport(unsigned int mask, unsigned int value);
extern void set_timeout(unsigned long milliseconds);
extern void set_retries(unsigned long num_retries);
extern void more_verbose();

/* Construct and send a message */
extern int nl2_send_rule(int action, int swport);

/* Valid values for swport are 1-16, and these: */
#define UNSPEC		0xdeadace
#define CPU_PORT	24
#define NULL_PORT	-1
#define ACCEPT		42      /* What is nine times five? */

/* Valid action types */
#define NOOP		0
#define ADD		1
#define DELETE		2
#define RESET		3

/* Packet types (ethertypes) */
#define PACKETTYPE_IP   0x800
#define PACKETTYPE_ARP  0x806

/* ARP opcodes */
#define ARP_REQUEST     1
#define ARP_REPLY       2
#define RARP_REQUEST    3
#define RARP_REPLY      4
#define DRARP_REQUEST   5
#define DRARP_REPLY     6
#define DRARP_ERROR     7
#define INRARP_REQUEST  8
#define INRARP_REPLY    9
#define ARP_NACK        10
#define ARP_LAST        10

/* IP protocol numbers (not all) */
#define PROTO_IP              0               /* dummy for IP */
#define PROTO_HOPOPTS         0               /* Hop by hop header for IPv6 */
#define PROTO_ICMP            1               /* control message protocol */
#define PROTO_IGMP            2               /* group control protocol */
#define PROTO_GGP             3               /* gateway^2 (deprecated) */
#define PROTO_ENCAP           4               /* IP in IP encapsulation */
#define PROTO_TCP             6               /* tcp */
#define PROTO_EGP             8               /* exterior gateway protocol */
#define PROTO_PUP             12              /* pup */
#define PROTO_UDP             17              /* user datagram protocol */
#define PROTO_IDP             22              /* xns idp */
#define PROTO_LAST            22

/* ICMP types */
#define ICMP_ECHOREPLY  0       /*  Echo reply. */
#define ICMP_UNREACH    3       /*  Destination unreachable. */
#define ICMP_SRCQUENCH  4       /*  Source quench. */
#define ICMP_REDIR      5       /*  Redirect. */
#define ICMP_ALTADDR    6       /*  Alternate Host Address. */
#define ICMP_ECHOREQ    8       /*  Echo request. */
#define ICMP_ROUTERADV  9       /*  Router advertisement. */
#define ICMP_ROUTERSOL  10      /*  Router solicitation. */
#define ICMP_TIMEX      11      /*  Time exceeded. */
#define ICMP_PARAM      12      /*  Parameter problem. */
#define ICMP_TSREQ      13      /*  Timestamp request. */
#define ICMP_TSREPLY    14      /*  Timestamp reply. */
#define ICMP_INFOREQ    15      /*  Information request. */
#define ICMP_INFOREPLY  16      /*  Information reply. */
#define ICMP_MASKREQ    17      /*  Address mask request. */
#define ICMP_MASKREPLY  18      /*  Address mask reply. */
#define ICMP_TRACERT    30      /*  Traceroute. */
#define ICMP_CONVERR    31      /*  Conversion error. */
#define ICMP_MOBREDIR   32      /*  Mobile Host Redirect. */
#define ICMP_V6WHERERU  33      /*  IPv6 Where-Are-You. */
#define ICMP_V6IMHERE   34      /*  IPv6 I-Am-Here. */
#define ICMP_MOBREQ     35      /*  Mobile Registration Request. */
#define ICMP_MOBREPLY   36      /*  Mobile Registration Reply. */
#define ICMP_NAMEREQ    37      /*  Domain Name request. */
#define ICMP_NAMEREPLY  38      /*  Domain Name reply. */
#define ICMP_SKIPDISC   39      /*  SKIP Algorithm Discovery Protocol. */
#define ICMP_PHOTURIS   40      /*  Photuris, Security failures. */
#define ICMP_LAST       40

/*
 ****************************************************************
 * Users of libztmd should not have to look beyond this point.
 ****************************************************************
 */


#define ZTMD_ZRULE_PID 300
#define ZTMD_ZRULE_INGRESS_PID 301
#define ZTMD_MCAST_ADDR "239.0.0.1"
#define ZTMD_MCAST_PORT 2345
#define PROGRAM_NAME "zrule 2.1"

/* The outward-facing port */
#define PORT_EXT 22

/* The ifindex for external port, determined by running the command
 * "zfilterd -f -d 4" on the switch. When trunking is enabled, a
 * different PID is used for the zrl device.
 */
#define IFINDEX_PORT_EXT 314

#define NONE -1
#define ADD 1
#define DELETE 2
#define ARP 3
#define IP 4
/*

 Packet headers for various protocols

 http://www.networksorcery.com/enp/protocol/ip.htm
 http://www.networksorcery.com/enp/protocol/arp.htm
 http://www.networksorcery.com/enp/protocol/tcp.htm
 http://www.networksorcery.com/enp/protocol/icmp.htm


 MAC header:

        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    0  |  Src MAC address                                              |
       +                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    4  |                               |   Dest MAC address            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               |
    8  |                                                               |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   12  |  Ether type                   |   802.1q VLAN tag
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   16     802.1q VLAN tag              |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

 TCP/IP (IP ether type is 0x800):

        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    0  |version| IHL   |      TOS      |     total length              |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    4  |        identification         |flags|     fragment offset     |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    8  |     TTL       |     protocol  |     header checksum           |
IP     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
^  12  |                       src IP address                          |
|      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  16  |                       dest IP address                         |
+--    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  20  |         src port              |         dest port             |
|      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
v  24  |                              seq                              |
TCP    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   28  |                              ack                              |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   32  |dataoff|     | ECN |  control  |         window                |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   36  |       checksum                |         urgent ptr            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

 ARP (ether type 0x806):

        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    0  |  Hardware Type                |   Protocol type               |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    4  | hw addrlen=6  | IP addrlen=4  |   Opcode                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    8  |  Src MAC address                                              |
       +                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   12  |                               |   Src IP address               
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   16     Src IP address               |   Dest MAC address            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               |
   20  |                                                               |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   24  |                  Dest IP address                              |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

 ICMP 

       | IP header
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   20  |     type      |     code      |         checksum              |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+


*/

#define MAC_HDRSIZE             18

/* Offsets from start of L3 header (end of MAC header) */
#define OFFSET_L3_ETHERTYPE     (12-MAC_HDRSIZE)
#define OFFSET_L3_ARP_OPCODE     6
#define OFFSET_L3_ARP_PROTOSRC  14
#define OFFSET_L3_ARP_PROTODEST 24
#define OFFSET_L3_IPPROTOCOL    9
#define OFFSET_L3_IPSRC_ADDR    12
#define OFFSET_L3_IPDEST_ADDR   16
#define OFFSET_L3_PORTS         20
#define OFFSET_L3_ICMP_TYPE     20
#define OFFSET_L3_ICMP_CODE     21
#define OFFSET_L3_SRCPORT       20
#define OFFSET_L3_DESTPORT      22
/* Offsets from start of MAC header (14 + L2 offsets) */
#define OFFSET_L2_ETHERTYPE     (OFFSET_L3_ETHERTYPE+MAC_HDRSIZE)
#define OFFSET_L2_ARP_OPCODE    (OFFSET_L3_ARP_OPCODE+MAC_HDRSIZE)
#define OFFSET_L2_ARP_PROTOSRC  (OFFSET_L3_ARP_PROTOSRC+MAC_HDRSIZE)
#define OFFSET_L2_ARP_PROTODEST (OFFSET_L3_ARP_PROTODEST+MAC_HDRSIZE)
#define OFFSET_L2_IPSRC_ADDR    (OFFSET_L3_IPSRC_ADDR+MAC_HDRSIZE)
#define OFFSET_L2_IPDEST_ADDR   (OFFSET_L3_IPDEST_ADDR+MAC_HDRSIZE)
#define OFFSET_L2_PORTS         (OFFSET_L3_PORTS+MAC_HDRSIZE)
#define OFFSET_L2_ICMP_TYPE     (OFFSET_L3_ICMP_TYPE+MAC_HDRSIZE)
#define OFFSET_L2_ICMP_CODE     (OFFSET_L3_ICMP_CODE+MAC_HDRSIZE)
#define OFFSET_L2_SRCPORT       (OFFSET_L3_SRCPORT+MAC_HDRSIZE)
#define OFFSET_L2_DESTPORT      (OFFSET_L3_DESTPORT+MAC_HDRSIZE)

/* If even then port+4 else port+6. If port > 12, +300 */
#define IFINDEX(port)  ((((port)>12)?300:0)+(((port)%2)?((port)+6):((port)+4)))

/* Maximum time to wait for reply, in ms */
#define MAX_WAIT 5000
#define MAX_TRIES 3

extern long sequence;
extern int verbose;
extern char* progname;
extern int ingress;
extern int mypid;

typedef znl2attr* attrp_t;
typedef unsigned char* ptr_t;

extern attrp_t
nl2m_addattr(znl2msghdr *n, int maxlen, int type, ptr_t data, int len);
extern attrp_t
make_attr_int(ptr_t buf, int bufsize, int type, uint32_t data);
extern attrp_t
make_attr_raw(ptr_t buf, int bufsize, int type, ptr_t data, int len);

extern void dump_msg(znl2msg* msg);
extern char *fill_header(znl2msghdr *msg, uint16_t msg_type);
extern int get_synfin(znl2msg* msg, int16_t type);
extern int nl2_send(int sock, struct sockaddr_in *addr, znl2msg *msg);
extern void dump_packet(void* msgptr, size_t len);
extern int get_reply(int sock, znl2msghdr* request);
extern int setup_multicast(const char *bind_addr, struct sockaddr_in* serv);
extern int get_nl2msg(znl2msg* msg_ptr, int type, int swport);

extern const char* type2s(int type);
extern const char* flags2s(unsigned int flags);
extern const char* flags_e2s(unsigned int flags_e);
extern const char* err2s(int err);

extern const char *arp_types[];
extern const char *icmp_types[];
extern const char *proto_names[];
extern const char *nl2_errors[];

#endif

/*       1         2         3         4         5         6         7         8
12345678901234567890123456789012345678901234567890123456789012345678901234567890
*/
