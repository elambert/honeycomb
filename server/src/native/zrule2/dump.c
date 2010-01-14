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

static char id[] = "$Id: dump.c 10855 2007-05-19 02:54:08Z bberndt $";

/*
 * The stupid bastards -- the values of types are *not* distinct, they
 * all overlap! Hence this stupid replication of fifty functions all
 * with identical case statements. Who the fuck taught the bozos to
 * write code?
 */

#define INDENT 3
#define XML 0

#if XML
#define tag(s) "<" s " />"
#define tag_open(s) "<" s ">"
#define tag_close(s) "</" s ">"
#else
#define tag(s) s
#define tag_open(s) s
#define tag_close(s) ""
#endif

static void dump_TLV(ptr_t p, int indent);
static const char* pad(int level);

#if XML
static int xml = 1;
#else
static int xml = 0;
#endif

const char *arp_types[] = {
    "",
    "arp-request", "arp-reply", "rarp-request", "rarp-reply",
    "drarp-request", "drarp-reply", "drarp-error", 
    "inrarp-request", "inrarp-reply", "arp-nack",
    0
};

const char *icmp_types[] = {
    "icmp-echoreply", "", "", "icmp-unreach", "icmp-srcquench", "icmp-redir",
    "icmp-altaddr", "", "icmp-echoreq", "icmp-routeradv", "icmp-routersol",
    "icmp-timex", "icmp-param", "icmp-tsreq", "icmp-tsreply",
    "icmp-inforeq","icmp-inforeply", "icmp-maskreq", "icmp-maskreply",
    "", "",  "",  "",  "",  "",  "",  "",  "",  "",  "",
    "icmp-traceroute", "icmp-converr", "icmp-mobredir", "icmp-v6whereru",
    "icmp-v6imhere", "icmp-mobreq", "icmp-mobreply", "icmp-namereq",
    "icmp-namereply", "icmp-skipdisc", "icmp-photuris",
    0
};

const char *proto_names[] = {
    "hop_opts", "icmp", "igmp", "ggp", "encap", "", "tcp", "", "egp",
    "", "", "", "pup", "", "", "", "", "udp", "", "", "", "", "idp",
    0
};

const char *nl2_errors[] = {
    /*  0: OK           */ "OK: No error",
    /*  1: ZNL2MSG_NOK  */ "ZNL2MSG_NOK: Generic failure code",
    /*  2: ZNL2MSG_RUNT */ "ZNL2MSG_RUNT: RUNT Packet - the Netlink2 message header was incomplete",
    /*  3: ZNL2_BTTLV   */ "ZNL2_BTTLV: BAD TLV - An intrface other than zhp, zre or zrl type was specified, or does not exist; A TCF_A_LB action was used along with another TCF_A action",
    /*  4: ZNL2_NTTLV   */ "ZNL2_NTTLV: NOT TLV - an expected TLV is missing, such as no NL2_OPTIONS when ZNL2M_F_ETLV is set in the znl2msg_flags",
    /*  5: ZNL2_BPTLV   */ "ZNL2_BPTLV: BAD PROTOCOL TLV - Inappropriate TLV type for the Command",
    /*  6: ZNL2_BQH     */ "ZNL2_BQH: BAD QUEUE HANDLE - A root qdisc handle which is not between 0x100 and 0x200, or is already in use when a RTM_NEWQDISC command is received for a root qdisc, or a parent qdisc of a PFIFO or BFIFO qdisc has a minor handle which is not in the range 0 - 8.",
    /*  7: ZNL2_BQL     */ "ZNL2_BQL: BAD QUE LENGTH - a TLV has extra bytes after processing",
    /*  8: ZNL2_BTCAO   */ "ZNL2_BTCAO: Not used */",
    /*  9: ZNL2_BTCAOP  */ "ZNL2_BTCAOP: BAD TCF_RAW OPTION - MATCH_INV and MATCH_OR are not supported",
    /* 10: ZNL2_BFO     */ "ZNL2_BFO: BAD FIELDS - A non IP ethertype is specified for a match with IP fields",
    /* 11: ZNL2_BFOP    */ "ZNL2_BFOP: BAD TCF_RAW_BASE - A Base value other than BASE_MAC_HDR or BASE_L3_HDR; an offset which does not correspond to a supported field; a TCF_F_IPT object specified an invf; a TCF_F_IPT object specified pinvf with an action which could not be inverted",
    /* 12: ZNL2_BQHL    */ "ZNL2_BQHL: BAD QUEUE HEADER LENGTH - An incomplete Qdisc and filter header was found",
    /* 13: ZNL2_QSTR    */ "ZNL2_QSTR: Not used",
    /* 14: ZNL2_BQT     */ "ZNL2_BQT: BAD QUEUE_TYPE - a RTM_NEWQDISC command specifies a type which is unknown or not supported for the platform",
    /* 15: ZNL2_BCMD    */ "ZNL2_BCMD: BAD COMMAND - unsupported Netlink2 Command",
    /* 16: ZNL2_NORM    */ "ZNL2_NORM: NO ROOM - A SYN packet was received with a new spid, and there were already MAX_CLIENTS (4); or all masks, rules or leaky bucket meters are in use for a port",
    /* 17: ZNL2_NOFIL   */ "ZNL2_NOFIL: The internal state of the IRULE table is corrupt",
    /* 18: ZNL2_NOQD    */ "ZNL2_NOQD: Not used",
    /* 19: ZNL2_NOCLS   */ "ZNL2_NOCLS: Not used",
    /* 20: ZNL2_SYS     */ "ZNL2_SYS: SYSTEM_ERROR - An attempt to read or write a switch register or memory failed",
    /* 21: ZNL2_XMUL    */ "ZNL2_XMUL: EXCESSIVE MATCHING RULES - More than two rules with the same key were specified",
    /* 22: ZNL2_BSEQ    */ "ZNL2_BSEQ: BAD SEQUENCE - sequence number of packet is less than the expected sequence number for the spid",
    /* 23: ZNL2_BPID    */ "ZNL2_BPID: BAD PID - source pid is unknown"
};


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
    sprintf(buf, "%d,%d", (v & 0xffff0000) >> 16, v & 0xffff);
    return buf;
}

static const char* to_int(uint32_t v)
{
    static char buf[50];
    sprintf(buf, "%d", v);
    return buf;
}

static const char* hexstring(ptr_t p, size_t len)
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

static int get_mask_offset(uint32_t mask)
{
    int n = 0;
    if (mask == 0)
        return 0;
    /* Left shift until MSB is !0 */
    while ((mask & 0xff000000) == 0) {
        n++;
        mask <<= 8;
    }
    return n;
}

#if 0
static int get_num_bits(uint32_t mask)
{
    int n = 0;
    while (mask != 0) {
        n += mask & 0x1;
        mask >>= 1;
    }
    return n;
}
#else
static int get_num_bits(uint32_t mask)
{
    int n = 0;
    while (mask != 0) {
        n++;
        mask &= mask - 1;       /* zeroes out the right-most 1 */
    }
    return n;
}
#endif

static ptr_t get_TLV(ptr_t p, uint16_t* type, uint16_t* length)
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

static void dump_pmaps(const char* msg, ptr_t p, size_t length, int indent)
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

static void dump_NL2(ptr_t p, size_t size, int indent)
{
    while (size >= sizeof(znl2attr)) {
        uint16_t type;
        uint16_t length;
        ptr_t value = get_TLV(p, &type, &length);

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

static int get_l3_offset(tcf_f_raw_val* tcf_f_raw)
{
    /* Convert the offset into L3-based */
    int field_offset = tcf_f_raw->offset;
    int mask_offset = get_mask_offset(tcf_f_raw->mask);
    int mask_bits = get_num_bits(tcf_f_raw->mask);

    if (tcf_f_raw->base == BASE_MAC_HDR)
        field_offset -= MAC_HDRSIZE;

    if (mask_bits == 8 || mask_bits == 16)
        field_offset += mask_offset;

    return field_offset;
}

/*
 * A note on figuring out how to decode te offsets: just by looking at
 * a tcf_f_raw value it's not possible to figure out what the offset
 * means. However we know that the protocol filters are before the ICM
 * type, TCP ports etc. filters. So when we see a protocol filter, we
 * remember it, so later when we come to the ICMP type or TCP ports,
 * we know which it is.
 */
static int filter_protocol = -1;

static const char* get_offset_name(tcf_f_raw_val* tcf_f_raw)
{
    int field_offset = get_l3_offset(tcf_f_raw);
    int mask_bits = get_num_bits(tcf_f_raw->mask);

    if (field_offset == OFFSET_L3_ICMP_TYPE) {
        /* Both ICMP type and TCP/UDP ports have the same offset */
        if (filter_protocol == PROTO_ICMP)
            return " (icmp type)";
        else if (filter_protocol == PROTO_TCP)
            return " (src/dest ports)";
        else
            return 0;
    }

    switch (field_offset) {
    case OFFSET_L3_DESTPORT: return " (dest port)";
    case OFFSET_L3_ICMP_CODE: return " (icmp code)";
    case OFFSET_L3_IPPROTOCOL: return " (protocol)";
    case OFFSET_L3_ETHERTYPE: return " (ethertype)";
    case OFFSET_L3_ARP_PROTOSRC: return " (arp src)";
    case OFFSET_L3_ARP_OPCODE: return " (arp opcode)";
    case OFFSET_L3_ARP_PROTODEST: return " (arp dest)";
    case OFFSET_L3_IPSRC_ADDR: return " (src)";
    case OFFSET_L3_IPDEST_ADDR: return " (dest)";
    default: return "";
    }
}

static const char* decode_value(tcf_f_raw_val* tcf_f_raw)
{
    int offset = get_l3_offset(tcf_f_raw);
    int mask_bits = get_num_bits(tcf_f_raw->mask);
    uint32_t v = tcf_f_raw->value;

    v >>= 32 - ((offset - tcf_f_raw->offset)*8 + mask_bits);

    /* Remember OFFSET_L3_ICMP_TYPE == OFFSET_L3_PORTS */

    if (offset == OFFSET_L3_ICMP_TYPE && filter_protocol == PROTO_ICMP &&
            v <= ICMP_LAST)
        return icmp_types[v];

    else if (offset == OFFSET_L3_PORTS && filter_protocol == PROTO_TCP)
        return to_shorts(tcf_f_raw->value);

    else if (offset == OFFSET_L3_DESTPORT && filter_protocol == PROTO_TCP)
        return to_int(tcf_f_raw->value);

    else if (offset == OFFSET_L3_IPPROTOCOL && v <= PROTO_LAST) {
        filter_protocol = v;
        return proto_names[v];
    }

    else if (offset == OFFSET_L3_ARP_OPCODE && v <= ARP_LAST)
        return arp_types[v];

    else if (offset == OFFSET_L3_IPSRC_ADDR || OFFSET_L3_IPDEST_ADDR)
        return to_octets(tcf_f_raw->value);


    return 0;
}

static void print_raw_val(tcf_f_raw_val* tcf_f_raw, int indent)
{
    const char* vstring = decode_value(tcf_f_raw);

    fprintf(stdout, "%s", pad(indent));
    if (xml)
        fputc('<', stdout);

    fputs("TCF_F_RAW base = ", stdout);
    switch (tcf_f_raw->base) {
    case BASE_MAC_HDR: fprintf(stdout, "BASE_MAC_HDR"); break;
    case BASE_L3_HDR: fprintf(stdout, "BASE_L3_HDR"); break;
    default:  fprintf(stdout, "%d", tcf_f_raw->base);
    }


    fprintf(stdout, ", flags = 0x%02x,\n", tcf_f_raw->flags);
    fprintf(stdout, "%soffset = %d, ", pad(indent + 2), tcf_f_raw->offset);
    fprintf(stdout, "mask = 0x%08x,%s\n",  tcf_f_raw->mask,
            get_offset_name(tcf_f_raw));

    fprintf(stdout, "%svalue = 0x%08x", pad(indent + 2), tcf_f_raw->value);
    if (vstring != 0 && *vstring != 0)
        fprintf(stdout, " (%s)", vstring);
    else
        fprintf(stdout, " (%s, %s, %d)",
                to_octets(tcf_f_raw->value), to_shorts(tcf_f_raw->value),
                tcf_f_raw->value);
    
    if (xml) fputs(" />", stdout);
    fputs("\n", stdout);
}

static void dump_TCF_F_TLVs(ptr_t p, size_t len, int indent)
{
    uint16_t type;
    uint16_t length;
    ptr_t value;

    if (p == 0 || len <= 0)
        return;

    value = get_TLV(p, &type, &length);
    p += length;

    if (type == TCF_F_RAW) {
        while (length > sizeof(tcf_f_raw_val)) {
            print_raw_val((tcf_f_raw_val*) value, indent);

            length -= sizeof(tcf_f_raw_val);
            value += sizeof(tcf_f_raw_val);
        }

        value = get_TLV(p, &type, &length);
        p += length;
    }
    else
        fprintf(stdout, "Expected TCF_F_RAW, found %d\n", type);

    if (type == TCF_F_IPT) {
        while (length > sizeof(tcf_f_ipt_val)) {
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

            length -= sizeof(tcf_f_ipt_val);
            value += sizeof(tcf_f_ipt_val);
        }
    }
}

static void dump_TCF_A_TLVs(ptr_t p, size_t len, int indent)
{
    char orderBuffer[BUFSIZ];

    if (p == 0 || len <= 0)
        return;

    /*
     * We have a sequence of TLVs, each of which has a type field of
     * index and the value is a TLV that's the action
     */

    while (len > 0) {
        uint16_t order;
        uint16_t type;
        uint16_t length;
        ptr_t value;

        value = get_TLV(p, &order, &length);

        p += length;
        len -= length;
        if (len < 0)
            break;

        snprintf(orderBuffer, sizeof(orderBuffer), "Order%d", order);
# if XML
        fprintf(stdout, "%s<%s>\n", pad(indent), orderBuffer);
# else
        fprintf(stdout, "%s%s\n", pad(indent), tag_open(orderBuffer));
# endif

        value = get_TLV(value, &type, &length);

        switch (type) {

        case TCF_A_COPY_CPU:
            fprintf(stdout, "%s%s\n", pad(indent+1), tag("TCF_A_COPY_CPU"));
            break;

        case TCF_A_DROP:    
            fprintf(stdout, "%s%s\n", pad(indent+1), tag("TCF_A_DROP"));
            break;

        case TCF_A_ACCEPT:  
            fprintf(stdout, "%s%s\n", pad(indent+1), tag("TCF_A_ACCEPT"));
            break;

        case TCF_A_REDIRECT: 
            fprintf(stdout, "%s%s\n", pad(indent+1),tag_open("TCF_A_REDIRECT"));
            fprintf(stdout, "%s%d\n", pad(indent+2), *(int*)value);
            if (xml)
                fprintf(stdout, "%s%s\n", pad(indent+1),
                        tag_close("TCF_A_REDIRECT"));
            break;

        case TCF_A_ARP:    
            fprintf(stdout, "%s%s\n", pad(indent+1), tag_open("TCF_A_ARP"));
            fprintf(stdout, "%s%s\n", pad(indent+2),
                    hexstring(value, length - sizeof(znl2attr)));
            if (xml)
                fprintf(stdout, "%s%s\n", pad(indent+1),tag_close("TCF_A_ARP"));
            break;

        case TCF_A_REDIRECT_EXCEPTION: 
            fprintf(stdout, "%s%s\n", pad(indent+1),
                    tag_open("TCF_A_REDIRECT_EXCEPTION"));
            fprintf(stdout, "%s%d\n", pad(indent+2), *(int*)value);
            if (xml)
                fprintf(stdout, "%s%s\n", pad(indent+1),
                        tag_close("TCF_A_REDIRECT_EXCEPTION"));
            break;

        default:
            fprintf(stdout, "Unexpected TCF_A_ value %d\n", type);
            break;
        }

        if (xml)
            fprintf(stdout, "%s</%s>\n", pad(indent), orderBuffer);
    }
}

/* Filters are actions */
static void dump_qdisc(ptr_t p, size_t len, int indent)
{
    uint16_t type;
    uint16_t length;
    ptr_t value;
    int packet_type = -1;

    qdisc_hdr* qdisc = (qdisc_hdr*) p;

    if (p == 0)
        return;

    p += sizeof(qdisc_hdr);
    len -= sizeof(qdisc_hdr);

    fprintf(stdout, pad(indent));
    if (xml)
        fputc('<', stdout);
    fprintf(stdout,
            "QDisc handle = 0x%08x, parent = 0x%08x,\n"
            "%stcm_info = 0x%08x, type = ",
            qdisc->handle, qdisc->parent, pad(indent+2), qdisc->tcm_info);
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

    packet_type = qdisc->tcm_info & 0xffff;

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
    
        length -= sizeof(znl2attr);

        switch (type) {

        case TCF_F:
            fprintf(stdout, "%s%s\n", pad(indent + 2), tag_open("TCF_F"));
            dump_TCF_F_TLVs(value, length, indent + 3);
            if (xml)
                fprintf(stdout, "%s</TCF_F>\n", pad(indent + 2));
            break;

        case TCF_A:
            fprintf(stdout, "%s%s\n", pad(indent + 2), tag_open("TCF_A"));
            dump_TCF_A_TLVs(value, length, indent + 3);
            if (xml)
                fprintf(stdout, "%s</TCF_A>\n", pad(indent + 2));
            break;

        case TCF_I:
            dump_pmaps("TCF_I", value, length, indent + 2);
            break;

        case TCF_O:
            dump_pmaps("TCF_O", value, length, indent + 2);
            break;

        }
    }
    if (xml) {
        fprintf(stdout, "%s</TCF_F_DATA>\n", pad(indent + 1));
        fprintf(stdout, "%s</QDisc>\n", pad(indent));
    }
}

static void dump_TLVs(ptr_t p, size_t size, int indent)
{
    uint16_t type;
    uint16_t length;
    ptr_t value;

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
    fprintf(stdout, "%s%sHeader seq = %d,\n", pad(indent), (xml? "<" : ""),
            hdr->znl2msg_seq);
    indent += 2;
    fprintf(stdout, "%slen = %d, type = %s,\n", pad(indent),
            hdr->znl2msg_len, type2s(hdr->znl2msg_type));
    fprintf(stdout, "%sflags_e = %s,\n", pad(indent),
            flags_e2s(hdr->znl2msg_flags_e));
    fprintf(stdout, "%sflags = %s,\n", pad(indent),
            flags2s(hdr->znl2msg_flags));
    fprintf(stdout, "%ssPID = %d, dPID = %d%s\n", pad(indent), 
            hdr->znl2msg_spid, hdr->znl2msg_dpid,  (xml? " />" : ""));
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
    if (err < 0 || err > ZNL2_BPID) {
        static char buffer[1000];
        sprintf(buffer, "0x%02x", err);
        return buffer;
    }
    return nl2_errors[err];
}

void dump_msg(znl2msg* msg)
{
    unsigned char* p;
    znl2msghdr* hdr = &msg->n;
    int payload_size;
    int indent = 0;

    p = (ptr_t)&msg->buf;
    payload_size = hdr->znl2msg_len - sizeof(znl2msghdr);

    fprintf(stdout, "%s%s\n", pad(indent), tag_open("Message"));

    dump_header(hdr, ++indent);

    if (hdr->znl2msg_type == ZNL2MSG_ERROR) {
        /* it's a reply */
        znl2msgerrt* reply = (znl2msgerrt*) msg;

        fprintf(stdout, "%s%sError code = %d%s\n", pad(indent),
                (xml? "<" : ""), reply->error, (xml? ">" : ""));

        fprintf(stdout, "%s%s\n",
                pad(indent + 1), err2s(reply->error));

        if (xml)
            fprintf(stdout, "%s%s\n", pad(indent), tag_close("Error"));

        dump_header(&reply->omsg, indent);
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

