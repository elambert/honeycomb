/*
 *  libznl2.h header file
 *  Copyright (C) 2004 Jamal Hadi Salim
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

#ifndef __LIBZNL2_H__
#define __LIBZNL2_H__ 1

#include <sys/types.h>
/* asm/types.h is very important! 
 * it fixes problems with missing senicolons
 * at end of structs etc
 need for glibc-bug fixes now
 * */

#ifdef Linux
#include <asm/types.h>
#endif

#define ZNL2_VER 0x1
#define MAX_MSG 1024
#define MAX_DEVS 256

/* Make sure this struct has size multiple of 32 bits */
typedef struct _znl2msghdr {
#if (PDK_NETWORK_ORDER == 1234)
    uint16_t znl2msg_len;          /* Length of message including header */
    uint16_t znl2msg_flags;        /* TopLevel flags */
    uint16_t znl2msg_type;         /* Message content */
    uint8_t  znl2msg_flags_e;      /* Additional flags */
    uint8_t  znl2msg_version;      /* Version */
    uint32_t znl2msg_seq;          /* Sequence number */
    uint32_t znl2msg_spid;         /* Source PID */
    uint32_t znl2msg_dpid;         /* Dest PID */
#else
    uint16_t znl2msg_flags;        /* TopLevel flags */
    uint16_t znl2msg_len;          /* Length of message including header */
    uint8_t  znl2msg_version;      /* Version */
    uint8_t  znl2msg_flags_e;      /* Additional flags */
    uint16_t znl2msg_type;         /* Message content */
    uint32_t znl2msg_seq;          /* Sequence number */
    uint32_t znl2msg_spid;         /* Source PID */
    uint32_t znl2msg_dpid;         /* Dest PID */
#endif
} znl2msghdr;

typedef struct _znl2msg {
    znl2msghdr n;
    char buf[MAX_MSG];
} znl2msg;

typedef struct _znl2msgerr {
    znl2msghdr msg;
    uint32_t  error;
} znl2msgerr;

typedef struct _znl2msgerrt {
    znl2msghdr msg;
#ifdef Solaris
    uint32_t   error;
    znl2msghdr omsg;
#else
    znl2msghdr omsg;
    uint32_t   error;
#endif
} znl2msgerrt;

/* error codes */
#define ZNL2MSG_NOK 1
#define ZNL2MSG_RUNT 2
#define ZNL2_BTTLV 3
#define ZNL2_NTTLV 4
#define ZNL2_BPTLV 5
#define ZNL2_BQH 6
#define ZNL2_BQL 7
#define ZNL2_BTCAO 8
#define ZNL2_BTCAOP 9
#define ZNL2_BFO  10
#define ZNL2_BFOP  11
#define ZNL2_BQHL 12
#define ZNL2_QSTR  13
#define ZNL2_BQT  14
#define ZNL2_BCMD 15
#define ZNL2_NORM 16
#define ZNL2_NOFIL 17
#define ZNL2_NOQD 18
#define ZNL2_NOCLS 19
#define ZNL2_SYS 20
#define ZNL2_XMUL 21

/*** PID Definitions ***/
#define ZNL2_ZFILTERD_PID 100
#define ZNL2_VSLB_PID 150
#define ZNL2_ZQOSD_PID           200
#define ZNL2_ZTMD_PID 1


/*** Extended Flag Definitions ***/
#define ZNL2M_F_SYN               0x1
#define ZNL2M_F_FIN               0x2
#define ZNL2M_F_ETLV              0x4
#define ZNL2M_F_PRIO              0x8
#define ZNL2M_F_ASTR              0x10


/*** Standard Flags Definitions ***/
#define ZNL2M_F_REQUEST           0x1
#define ZNL2M_F_MULTI             0x2
#define ZNL2M_F_ACK               0x4
#define ZNL2M_F_ECHO              0x8


/*** Flag bits for GET Requests ***/
#define ZNL2M_F_ROOT              0x10
#define ZNL2M_F_MATCH             0x20
#define ZNL2M_F_ATOMIC            0x40
#define ZNL2M_F_DUMP              (ZNL2M_F_ROOT | ZNL2M_F_MATCH)


/*** Flag bits for NEW Requests ***/
#define ZNL2M_F_REPLACE           0x100
#define ZNL2M_F_EXCL              0x200
#define ZNL2M_F_CREATE            0x400
#define ZNL2M_F_APPEND            0x800

/*** Commands/Type ***/
enum {
    ZNL2MSG_NOOP,
    ZNL2MSG_ERROR,
    ZNL2MSG_DONE,
    ZNL2_NEWQDISC,
    ZNL2_DELQDISC,
    ZNL2_GETQDISC,
    ZNL2_NEWTFILTER,
    ZNL2_DELTFILTER,
    ZNL2_GETTFILTER,
    ZNL2_NEWCLASS,
    ZNL2_DELCLASS,
    ZNL2_GETCLASS,
    ZNL2_SYN,
    ZNL2_FIN
};


/*** Macros to Manipulate Qdisc Hdr ***/
#define TC_H_MAJ_MASK (0xFFFF0000U)
#define TC_H_MIN_MASK (0x0000FFFFU)
#define TC_H_MAJ(h) ((h)&TC_H_MAJ_MASK)
#define TC_H_MIN(h) ((h)&TC_H_MIN_MASK)

#ifndef TC_H_ROOT
#define TC_H_ROOT (0xFFFFFFFFU)
#define TC_H_INGRESS (0xFFFFFFF1U)
#endif

typedef struct _qdisc_hdr {
    uint32_t    handle;
    uint32_t    parent;
    uint32_t    tcm_info; /* This value may disappear! */
    uint32_t    type;
} qdisc_hdr;


/*** Topmost TLV ***/
enum {
    NL2_OPTIONS, 
    NL2_UNSPEC
};

#define TLV_TOP_MAX NL2_UNSPEC

/*** Options ***/
enum {
    TLV_CHECKSUM,
    TLV_MSG_PRIO,
    TLV_INPUT_PORT_MAP,
    TLV_OUTPUT_PORT_MAP,
    TLV_NAME_ID
};

#define TLV_NL2_MAX TLV_NAME_ID 


/*** Qdisc Types ***/
enum {
    QTMIN,
    TCQ_Q_PRIO,
    TCQ_Q_WRR,
    TCQ_Q_HTB,
    TCQ_Q_RED,
    TCQ_Q_PFIFO,
    TCQ_Q_BFIFO,
    TCF_FILTER,
    QTMAX
};



/*** TLVs for different types of filter ***/
enum {
    TCF_F_RAW,
    TCF_F_IPT
};

#define TCF_F_T_MAX TCF_F_IPT

/*** QDisc / Filter Info ***/
enum {
    ZNL2M_OPTIONS,
    TCF_F_DATA
};

#define TCA_TCF_MAX TCF_F_DATA 

/*** TLVs for TCF_F_DATA ***/
enum {
    TCF_F,
    TCF_A,
    TCF_O,
    TCF_I
};

#define TCF_MAX TCF_I

enum {
    OPTL_O_TLV,
    OPTL_I_TLV,
    FILT_I_TLV,
    FILT_O_TLV
};

#define PRIOWRR_NO_QUEUES 16    /* Needs to be a multiple of 4 */

/*** TCQ_Q_PRIOWRR Value ***/
typedef struct _tcq_q_priowrr_val {
#if (PDK_NETWORK_ORDER == 1234)
    uint16_t       bands;       /* = PRIOWRR_NO_QUEUES */
    uint16_t       wrr_flag:1, reserved:15;
    uint8_t        map[PRIOWRR_NO_QUEUES];
    uint32_t       weights[PRIOWRR_NO_QUEUES];
#else
    uint16_t       reserved:15, wrr_flag:1;
    uint16_t       bands;       /* = PRIOWRR_NO_QUEUES */
    uint8_t        map[PRIOWRR_NO_QUEUES];
    uint32_t       weights[PRIOWRR_NO_QUEUES];
#endif
} tcq_q_priowrr_val;

/*** TCQ_Q_PFIFO Value ***/
typedef struct _tcq_q_pfifo_val {
    uint32_t       queue_limit;
} tcq_q_pfifo_val;


/*** TCQ_Q_RED Value ***/
typedef struct _tcq_q_red_val {
    uint32_t       RandomDropMin;
    uint32_t       RandomDropMax;
    uint32_t       RandomDropProb;
    uint32_t       RandomDropWeight;
    uint32_t       RandomDropLimit;
} tcq_q_red_val;

/*** TCQ_Q_HTB Value ***/
typedef struct _tcq_q_htb_val {
    uint32_t       Rate;
    uint32_t       Ceiling;
    uint32_t       Priority;
    uint32_t       Quantum;
} tcq_q_htb_val;

/*** for base field in tcf_f_raw_val value ***/
enum {
    BASE_MAC_HDR,
    BASE_L3_HDR
};


/*** TCF_F_RAW Value ***/
typedef struct _tcf_f_raw_val {
#if (PDK_NETWORK_ORDER == 1234)
    uint8_t        flags;
    uint8_t        base;
    int16_t        offset;
    uint32_t       value;
    uint32_t       mask;
#else
    int16_t        offset;
    uint8_t        base;
    uint8_t        flags;
    uint32_t       value;
    uint32_t       mask;
#endif
} tcf_f_raw_val;


/*** TCF_F_IPT Value ***/
typedef struct _tcf_f_ipt_val {
#if (PDK_NETWORK_ORDER == 1234)
    uint32_t       src_ip;
    uint32_t       src_mask;
    uint32_t       dst_ip;
    uint32_t       dst_mask;
    uint16_t       proto;
    uint16_t       proto_mask;
    uint16_t       src_port0;
    uint16_t       src_port1;
    uint16_t       dst_port0;
    uint16_t       dst_port1;
    uint8_t        invf;
    uint8_t        pinvf;
    uint8_t        tcpf;
    uint8_t        tcpf_mask;
#else
    uint32_t       src_ip;
    uint32_t       src_mask;
    uint32_t       dst_ip;
    uint32_t       dst_mask;
    uint16_t       proto_mask;
    uint16_t       proto;
    uint16_t       src_port1;
    uint16_t       src_port0;
    uint16_t       dst_port1;
    uint16_t       dst_port0;
    uint8_t        tcpf_mask;
    uint8_t        tcpf;
    uint8_t        pinvf;
    uint8_t        invf;
#endif
} tcf_f_ipt_val;


/*** TLV TCF_F_RAW Flags ***/
#define MATCH_INV              0x1
#define MATCH_ROUTED           0x2
#define MATCH_OR               0x4

/*** TLV Action Order ***/
#define          TCF_A_ORDERMAX 16

/*** TLV Action ***/
enum {
    TCF_A_SET_PRIO,             /* 0 */
    TCF_A_USE_PRIO,
    TCF_A_SET_TOS,
    TCF_A_COPY_CPU,
    TCF_A_DROP,
    TCF_A_REDIRECT,             /* 5 */
    TCF_A_MIRROR,
    TCF_A_COUNT,
    TCF_A_CHANGE_PRIO,
    TCF_A_CHANGE_PREC,
    TCF_A_SET_DSCP,             /* 10 */
    TCF_A_REDIRECT_EXCEPTION,
    TCF_A_ACCEPT,
    TCF_A_ARP,
    TCF_A_LB,
    TCF_A_UNTAG                 /* 15 */
};

#define TCF_A_MAX TCF_A_UNTAG

/*** TLV Leaky Bucket ***/
enum {
    TCF_A_LB_PARAMS,
    TCF_A_LB_EXCEED,
    TCF_A_LB_CONFORM 
};

/*** TCF_A_LB_PARAMS Value ***/
typedef struct _tcf_a_lb_params {
    uint32_t       burst;
    uint32_t       index;
    uint32_t       rate;
} tcf_a_lb_params;

typedef struct _znl2attr {
#if (PDK_NETWORK_ORDER == 1234)
    uint16_t znl2a_type;
    uint16_t znl2a_len;
#else
    uint16_t znl2a_len;
    uint16_t znl2a_type;
#endif
} znl2attr;

/* To align a pointer/value to a 4-byte boundary */
#define ALIGN(ptr) (((ptr) + 3) & 0xfffffc)

extern int 
znl2m_addattr_l(znl2msghdr *n, int maxlen, int type, void *data, int alen);
extern int
znl2a_addattr32(znl2attr *znl2, int maxlen, int type, uint32_t data);
extern int
znl2a_addattr_l(znl2attr *znl2, int maxlen, int type, void *data, int alen);

#endif /* __LIBZNL2_H__ */
