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



#include "utils.h"
#include "trace.h"

/*
 * Static routines
 */

static void
add_timespecs( struct timespec *a,
               struct timespec b )
{
    a->tv_sec += b.tv_sec;
    a->tv_nsec += b.tv_nsec;
    
    while ( a->tv_nsec >= 1000000000 ) {
        a->tv_nsec -= 1000000000;
        a->tv_sec += 1;
    }
}

/*
 * API implementation
 */

void
update_timer( struct timespec *next_timeout,
              unsigned int length /* ms */ )
{
    int res;
    struct timespec timeout;
    
    res = clock_gettime(CLOCK_REALTIME,
                        next_timeout );
    if (res) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "clock_gettime failed !");
        next_timeout->tv_sec = 0;
        next_timeout->tv_nsec = 0;
        return;
    }

    timeout.tv_sec = 0;
    timeout.tv_nsec = length*1000000;

    add_timespecs( next_timeout, timeout);
}

struct timespec
substract_timespec( struct timespec a,
                    struct timespec b ) {
    struct timespec result = {0, 0};
    
    if ( (a.tv_sec < b.tv_sec)
         || ( (a.tv_sec == b.tv_sec)
              && (a.tv_nsec < b.tv_nsec) )) {
        return(result);
    }
    
    while ( a.tv_nsec < b.tv_nsec ) {
        a.tv_sec--;
        a.tv_nsec += 1000000000;
    }

    result.tv_sec = a.tv_sec - b.tv_sec;
    result.tv_nsec = a.tv_nsec - b.tv_nsec;

    return(result);
}

struct timespec
remaining_time(struct timespec final_time)
{
    struct timespec result, now;
    int res;

    result.tv_sec = 0;
    result.tv_nsec = 0;

    res = clock_gettime( CLOCK_REALTIME,
                         &now );
    if (res) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "clock_gettime failed [%d]",
		 res );
        return(result);
    }

    result = substract_timespec(final_time, now);
    return(result);
}
