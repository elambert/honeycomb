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
 * Definitions used in loadbalance.c. This came from ztmd_sample.h
 * included with ZYNX sample client code.
 */

#ifndef __LOADBALANCE_H__
#define __LOADBALANCE_H__ 1

#define CPU_PORT  24
#define NULL_PORT -1
#define ACCEPT 42               /* What is nine times five? */

#define ZTMD_ZRULE_PID 300
#define ZTMD_MCAST_ADDR "239.0.0.1"
#define ZTMD_MCAST_PORT 2345
#define PROGRAM_NAME "zrule"

/* PSDB data byte order is opposite to network byte order */
#if (PDK_NETWORK_ORDER == 1234)
#    define psdb_pton(l) (((((uint32_t)(l))>>24&0xff))|((((uint32_t)(l))>>8)&0xff00)|((((uint32_t)(l))<<8)&0xff0000)|((((uint32_t)(l))<<24)&0xff000000))
#else
#    define psdb_pton(l) (l)
#endif /* PDK_NETWORK_ORDER */

#endif
