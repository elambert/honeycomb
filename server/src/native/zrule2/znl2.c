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

static char id[] = "$Id: znl2.c 10855 2007-05-19 02:54:08Z bberndt $";

/*
 * Re-implemented functions from libznl2 that we need
 */

/* Return value: new value of size */
static uint32_t add_attr(ptr_t buf, uint32_t bufsize, int type, ptr_t data, int len)
{
    znl2attr *attr = (znl2attr *) buf;

    int l = sizeof(znl2attr) + len;
    long size = ALIGN(l);

    if (size > bufsize)
        return NULL;

    attr->znl2a_type = type;
    attr->znl2a_len = l;
    bcopy(data, buf + sizeof(znl2attr), len);

    return size;
}

attrp_t nl2m_addattr(znl2msghdr *hdr, int maxlen, int type, ptr_t data, int len)
{
    uint32_t l;

    ptr_t tail = (ptr_t)hdr + hdr->znl2msg_len;
    maxlen -= hdr->znl2msg_len;

    if ((l = add_attr(tail, maxlen, type, data, len)) < 0)
        return NULL;

    hdr->znl2msg_len += l;
    return (attrp_t) tail;
}

attrp_t make_attr_raw(ptr_t buf, int bufsize, int type, ptr_t data, int len)
{
    uint32_t l;

    if ((l = add_attr(buf, bufsize, type, data, len)) < 0)
        return NULL;

    return (attrp_t) buf;
}

attrp_t make_attr_int(ptr_t buf, int bufsize, int type, uint32_t data)
{
    return make_attr_raw(buf, bufsize, type, (ptr_t)&data, sizeof(data));
}
