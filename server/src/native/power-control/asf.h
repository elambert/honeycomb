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

#include <unistd.h>
#include <stdlib.h>
#include <string.h>
 
#include <errno.h>
#include <arpa/inet.h>
#include <netdb.h>   
#include <sys/socket.h> 
#include <sys/types.h>

#define	ASF_PORT  623
#define	ASF_IANA  4542

typedef struct {
    uint8_t version;
    uint8_t reserved1;
    uint8_t sequence;
    uint8_t msg_class;
    uint32_t iana;
    uint8_t msg_type;
    uint8_t msg_tag;
    uint8_t reserved2;
    uint8_t data_len;
    uint8_t data[1];            /* variable length */
} asf_packet_t;

#define ASF_HDRSIZE (sizeof(asf_packet_t)-1)

extern asf_packet_t* asf_get_msg(void* buf, int msg_type);
extern int asf_get_socket(const char* hostname);

/* Message types */
#define ASF_RESET	0x10
#define ASF_POWERUP	0x11
#define ASF_POWERDOWN	0x12
#define ASF_POWERCYCLE	0x13

#define ASF_TEXT	0x3f

#define ASF_PING	0x80
#define ASF_CAPREQ	0x81
#define ASF_STATEREQ	0x82
#define ASF_OPENSESSREQ 0x83
#define ASF_CLOSESESSREQ 0x84

#define ASF_PONG	0x40
#define ASF_CAPRESP	0x41
#define ASF_STATERESP	0x42
#define ASF_OPENSESSRESP	0x43
#define ASF_CLOSESESSRESP	0x44
