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



#ifndef __HCHOST__
#define __HCHOST__

#include <winsock2.h>
#include <stdio.h>
#include <time.h>
#define WIN32

/* uint64_t */
#define uint16_t unsigned __int16 
#define uint32_t unsigned __int32
#define uint64_t unsigned __int64
#define int64_t __int64
#define int32_t __int32

#define LL_MODIFIER "I64"
#define LL_FORMAT "%I64d"
#define LLX_FORMAT "%I64x"

/*
 * Since Windows doesn't include timespec in time.h or timer_t,
 * make our own
 */

#ifndef HAVE_STRUCT_TIMESPEC
#define HAVE_STRUCT_TIMESPEC 1
struct timespec {
        long tv_sec;
        long tv_nsec;
};
#endif /* HAVE_STRUCT_TIMESPEC */

#ifndef	HONEYCOMB_EXTERN
#ifdef	COMPILING_HONEYCOMB
#define HONEYCOMB_EXTERN __declspec(dllexport)
#else	/* COMPILING_HONEYCOMB */
#define HONEYCOMB_EXTERN __declspec(dllimport)
#endif	/* COMPILING_HONEYCOMB */
#endif	/* HONEYCOMB_EXTERN */

#define snprintf _snprintf
#define mkgmtime _mkgmtime
#define ftruncate  _chsize_s
/*
 * WIN32 C runtime library had been made thread-safe
 * without affecting the user interface. Provide
 * mappings from the UNIX thread-safe versions to
 * the standard C runtime library calls.
 * Only provide function mappings for functions that
 * actually exist on WIN32.
 */
#define gmtime_r( _clock, _result ) \
        ( *(_result) = *gmtime( (_clock) ), \
          (_result) )

#define localtime_r( _clock, _result ) \
        ( *(_result) = *localtime( (_clock) ), \
          (_result) )

#endif	/* __HCHOST__ */
