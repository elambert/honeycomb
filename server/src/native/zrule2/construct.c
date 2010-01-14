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

static char id[] = "$Id: construct.c 10855 2007-05-19 02:54:08Z bberndt $";

/*
 * Private members
 */

static int packet_type = PACKETTYPE_IP;
static int protocol = -1;
static int arp_opcode = -1;
static int icmp_type = -1;

static unsigned int src_mask;
static unsigned int src_value;
static unsigned int dest_mask;
static unsigned int dest_value;
static unsigned int srcport_mask;
static unsigned int srcport_value;
static unsigned int destport_mask;
static unsigned int destport_value;

void set_packet_type(int t) {
    if (t > 0)
        packet_type = t;
}
void set_protocol(int p) {
    if (p >= 0)
        protocol = p;
}
void set_arp_opcode(int code) {
    if (code > 0)
        arp_opcode = code;
}
void set_icmp_type(int t) {
    if (t >= 0)
        icmp_type = t;
}
void set_src(unsigned int mask, unsigned int value) {
    src_mask = mask;
    src_value = value;
}
void set_dest(unsigned int mask, unsigned int value) {
    dest_mask = mask;
    dest_value = value;
}
void set_srcport(unsigned int mask, unsigned int value) {
    srcport_mask = mask;
    srcport_value = value;
}
void set_destport(unsigned int mask, unsigned int value) {
    destport_mask = mask;
    destport_value = value;
}

/*
 * Fill the header and return a pointer to just after, for filling in TLVs
 */
char *fill_header(znl2msghdr *msg, uint16_t msg_type) 
{
    int i;
    attrp_t first_TLV;
    attrp_t name_tlv;

    char name[] = PROGRAM_NAME;
    int name_len = sizeof(name);

    uint32_t *store;

    msg->znl2msg_len = sizeof(znl2msghdr);
    msg->znl2msg_version = ZNL2_VER;
    msg->znl2msg_seq = sequence++;
    msg->znl2msg_type = msg_type;
    msg->znl2msg_dpid = ZNL2_ZTMD_PID;
    msg->znl2msg_spid = mypid;
    msg->znl2msg_flags = ZNL2M_F_ACK | ZNL2M_F_REQUEST | ZNL2M_F_ATOMIC;
    msg->znl2msg_flags_e = ZNL2M_F_ETLV;

    first_TLV = nl2m_addattr(msg, MAX_MSG, NL2_OPTIONS, NULL, 0);
    name_tlv = nl2m_addattr(msg, MAX_MSG, TLV_NAME_ID, (ptr_t)name, name_len);

    /*
     * Change byte ordering so the name goes out correctly.  It will
     * be changed back to original order just before the packet gets
     * sent.
     */
    store = (uint32_t *) (name_tlv + sizeof(znl2attr));
    for (i = 0; i < name_len/4; i++)
        store[i] = psdb_pton(store[i]);

    /* Now we can insert the length into the first TLV */
    first_TLV->znl2a_len = msg->znl2msg_len - sizeof(znl2msghdr);

    return (char *)msg + msg->znl2msg_len;
}

/*
 * build_packet()
 *
 * action: ADD | DELETE | RESET
 * swport: port packets get sent to
 *
 */
int get_nl2msg(znl2msg* msg_ptr, int type, int swport)
{
    int len = 0, rem;
    ptr_t tlvp;
    qdisc_hdr *q;

    attrp_t attr_a, attr_f, attr_f_data, attr_f_raw, attr_arp, attr_i;
    attrp_t attr_action, obj_zero = 0, obj_one = 0;

    int tcf_action;
    tcf_f_raw_val filter_add;

    /* Fill header */

    memset(msg_ptr, 0, sizeof (znl2msg));
    if ((q = (qdisc_hdr *)fill_header(&msg_ptr->n, type)) == NULL) {
        fprintf(stderr, "%s: error filling Znetlink2 header\n", progname);
        return -1;
    }

    /* qdisc: queuing discipline, Sec 4.0 */
    q->handle = 0;
    q->parent = TC_H_INGRESS;
    q->type = TCF_FILTER;
    q->tcm_info = packet_type & 0xffff;
  
    len = msg_ptr->n.znl2msg_len + sizeof(qdisc_hdr);
    tlvp = (ptr_t)msg_ptr + len;
    rem = sizeof(znl2msg) - len;

    /* Start nesting filter TLVs */

    attr_f_data = make_attr_raw(tlvp, rem, TCF_F_DATA, NULL, 0);

    len += attr_f_data->znl2a_len;
    tlvp += attr_f_data->znl2a_len;
    rem = sizeof(znl2msg) - len;

    attr_f = make_attr_raw(tlvp, rem, TCF_F, NULL, 0);

    len += attr_f->znl2a_len;
    tlvp += attr_f->znl2a_len;
    rem = sizeof(znl2msg) - len;

    attr_f_raw = make_attr_raw(tlvp, rem, TCF_F_RAW, NULL, 0);

    len += attr_f_raw->znl2a_len;
    tlvp += attr_f_raw->znl2a_len;
    rem = sizeof(znl2msg) - len;

    /*
     * Since the offset in a tcf_f_raw MUST be divisible by 4, we use
     * BASE_MAC_HDR for ethertype and ARP protocol source address,
     * and BASE_L3_HDR for everything else.
     */
    filter_add.base = BASE_L3_HDR;

    /* IP protocols */
    if (packet_type == PACKETTYPE_IP && protocol >= 0) {
        int off;
        filter_add.flags = 0;
        filter_add.value = protocol & 0xff;
        filter_add.offset = OFFSET_L3_IPPROTOCOL;
        if ((off = filter_add.offset % 4) != 0)
            filter_add.offset -= off;
        filter_add.mask = 0xff << ((3 - off) * 8);
        filter_add.value <<= ((3 - off) * 8);

        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
        rem = sizeof(znl2msg) - len;

        if (protocol == PROTO_ICMP && icmp_type >= 0) {
            filter_add.value = icmp_type & 0xff;
            filter_add.offset = OFFSET_L3_ICMP_TYPE;
            if ((off = filter_add.offset % 4) != 0)
                filter_add.offset -= off;
            filter_add.mask = 0xff << ((3 - off) * 8);
            filter_add.value <<= ((3 - off) * 8);

            memcpy(tlvp, &filter_add, sizeof(filter_add));
            tlvp += sizeof(filter_add);
            len += sizeof(filter_add);
            rem = sizeof(znl2msg) - len;
        }
    }

    /* Dest addr */
    if (dest_mask) {
        if (packet_type == PACKETTYPE_IP)
            filter_add.offset = OFFSET_L3_IPDEST_ADDR;
        else if (packet_type == PACKETTYPE_ARP)
            filter_add.offset = OFFSET_L3_ARP_PROTODEST;
        else {
            fprintf(stderr, "%s: internal error -- unknown type 0x%x\n",
                    packet_type);
            return -1;
        }
        filter_add.value = dest_value;
        filter_add.flags = 0;
        filter_add.mask = dest_mask;
        /* Copy this filter */
        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
        rem = sizeof(znl2msg) - len;
    }

    /* Src or dest ports */
    if (srcport_mask || destport_mask) {
        /* ports are 16 bits so both are combined into one filter */
        unsigned int mask = 0;
        unsigned int value = 0;

        if (!(packet_type == PACKETTYPE_IP &&
              (protocol == PROTO_TCP || protocol == PROTO_UDP))) {
            fprintf(stderr, "%s: Can only specify ports for TCP and UDP\n",
                    progname);
            return -1;
        }

        if (srcport_mask) {
            mask |= (srcport_mask & 0xffff);
            value |= (srcport_value & 0xffff);
            mask <<= 16;
            value <<= 16;
        }
        if (destport_mask) {
            mask |= (destport_mask & 0xffff);
            value |= (destport_value & 0xffff);
        }
        filter_add.offset = OFFSET_L3_PORTS;
        filter_add.mask = mask;
        filter_add.value = value;
        filter_add.flags = 0;
        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
        rem = sizeof(znl2msg) - len;
    }

    /* Src addr */
    if (src_mask) {
        if (packet_type == PACKETTYPE_IP)
            filter_add.offset = OFFSET_L3_IPSRC_ADDR;
        else {
            filter_add.base = BASE_MAC_HDR;
            filter_add.offset = OFFSET_L2_ARP_PROTOSRC;
        }
        filter_add.value = src_value;
        filter_add.flags = 0;
        filter_add.mask = src_mask;
        /* Copy this filter */
        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
        rem = sizeof(znl2msg) - len;
    }

    if (packet_type == PACKETTYPE_ARP && arp_opcode > 0) {
        /* Add a filter for the ARP opcode also */
        int off;
        filter_add.value = arp_opcode & 0xffff;
        filter_add.mask = 0xffff;
        filter_add.offset = OFFSET_L3_ARP_OPCODE;
        if ((off = filter_add.offset % 4) != 0) {
            if (off == 3) {
                /* Need to split across RAWs -- too hard! */
                fprintf(stderr, "Internal error: ARP opcode offset is 3\n");
                return -1;
            }
            filter_add.offset -= off;
        }
        filter_add.mask <<= (2 - off) * 8;
        filter_add.flags = 0;
        /* Copy this filter */
        memcpy(tlvp, &filter_add, sizeof(filter_add));
        tlvp += sizeof(filter_add);
        len += sizeof(filter_add);
        rem = sizeof(znl2msg) - len;
     }

    attr_f_raw->znl2a_len = tlvp - (ptr_t)attr_f_raw;
    attr_f->znl2a_len = tlvp - (ptr_t)attr_f;

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

    case UNSPEC:
        if (type != ZNL2_DELTFILTER) {
            fprintf(stderr, "Switch port to redirect to unspecified\n");
            return -1;
        }

    default:
        tcf_action = TCF_A_REDIRECT;
        break;
    }

    /* If the switch port is unspecified, no TCF_A object */
    if (swport != UNSPEC) {

        attr_a = make_attr_raw(tlvp, rem, TCF_A, NULL, 0);

        len += attr_a->znl2a_len;
        tlvp += attr_a->znl2a_len;
        rem = sizeof(znl2msg) - len;

        /* Object 0 */
        obj_zero = make_attr_raw(tlvp, rem, 0, NULL, 0);

        len += obj_zero->znl2a_len;
        tlvp += obj_zero->znl2a_len;
        rem = sizeof(znl2msg) - len;

        attr_action = make_attr_int(tlvp, rem, tcf_action, swport);

        len += attr_action->znl2a_len;
        tlvp += attr_action->znl2a_len;
        rem = sizeof(znl2msg) - len;

        /* Set the attributes' lengths */
        obj_zero->znl2a_len = tlvp - (ptr_t)obj_zero;

        if (tcf_action == TCF_A_REDIRECT) {
            /* Add a REDIRECT_EXCEPTION action also */
            obj_one =  make_attr_raw(tlvp, rem, 1, NULL, 0);

            len += obj_one->znl2a_len;
            tlvp += obj_one->znl2a_len;
            rem = sizeof(znl2msg) - len;

            attr_action =
                make_attr_int(tlvp, rem, TCF_A_REDIRECT_EXCEPTION, swport);

            len += attr_action->znl2a_len;
            tlvp += attr_action->znl2a_len;
            rem = sizeof(znl2msg) - len;

            /* Set the attributes' lengths */
            obj_one->znl2a_len = tlvp - (ptr_t)obj_one;
        }

        /* Done with TCF_A */
        attr_a->znl2a_len = tlvp - (ptr_t)attr_a;
    }

    /*
     * Set TCF_I with our ingress port (optional -- leaving it out
     * means "all ports" which should also work just fine)
     */
    if (ingress == 1) {
      attr_i = make_attr_int(tlvp, rem, TCF_I, IFINDEX_PORT_EXT);
      len += attr_i->znl2a_len;
      tlvp += attr_i->znl2a_len;
    }

    /* Set lengths for TCF_F_DATA and the whole message */
    attr_f_data->znl2a_len = tlvp - (ptr_t)attr_f_data;
    msg_ptr->n.znl2msg_len = len;

    return len;
}

