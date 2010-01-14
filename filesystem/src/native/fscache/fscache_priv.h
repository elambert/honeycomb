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



#ifndef _FSCACHE_PRIV_H
#define _FSCACHE_PRIV_H

#include <jni.h>
#include <stdio.h>
#include <db.h>
#include <string.h>
#include <stdlib.h>

#include <sys/types.h>
#include <netinet/in.h>
#include <sys/param.h>
#include <inttypes.h>
#include <errno.h>

#include <fscache.h>

#include "trace.h"

#define ROOTFILETYPE com_sun_honeycomb_fscache_BDBNativeFileCache_ROOTFILETYPE
#define DIRECTORYTYPE com_sun_honeycomb_fscache_BDBNativeFileCache_DIRECTORYTYPE
#define FILELEAFTYPE com_sun_honeycomb_fscache_BDBNativeFileCache_FILELEAFTYPE
#define FTYPE_OFFSET com_sun_honeycomb_fscache_BDBNativeFileCache_FTYPE_OFFSET
#define ATIME_OFFSET com_sun_honeycomb_fscache_BDBNativeFileCache_ATIME_OFFSET
#define ATIME_LEN com_sun_honeycomb_fscache_BDBNativeFileCache_ATIME_LEN
#define HC_INDEX_OFFSET com_sun_honeycomb_fscache_BDBNativeFileCache_HC_INDEX_OFFSET
#define HC_INDEX_LEN com_sun_honeycomb_fscache_BDBNativeFileCache_HC_INDEX_LEN
#define OID_OFFSET com_sun_honeycomb_fscache_BDBNativeFileCache_OID_OFFSET
#define OID_LEN com_sun_honeycomb_fscache_BDBNativeFileCache_OID_LEN
#define CURSOR_NONE com_sun_honeycomb_fscache_BDBNativeFileCache_CURSOR_NONE
#define CURSOR_MAIN com_sun_honeycomb_fscache_BDBNativeFileCache_CURSOR_MAIN
#define CURSOR_CHILDREN com_sun_honeycomb_fscache_BDBNativeFileCache_CURSOR_CHILDREN
#define CURSOR_ATIME com_sun_honeycomb_fscache_BDBNativeFileCache_CURSOR_ATIME
#define CURSOR_INDEX com_sun_honeycomb_fscache_BDBNativeFileCache_CURSOR_INDEX
#define CURSOR_OID com_sun_honeycomb_fscache_BDBNativeFileCache_CURSOR_OID
#define NAME_OFFSET com_sun_honeycomb_fscache_BDBNativeFileCache_NAME_OFFSET
#define NAMELEN_OFFSET com_sun_honeycomb_fscache_BDBNativeFileCache_NAMELEN_OFFSET

static int64_t ntohll(void* buf);
static const char* toHex(void* v, int len);

#endif
