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



#include <syslog.h>
#include <stdarg.h>

#include "trace.h"

static cm_trace_level_t _cm_level = CM_TRACE_LEVEL_NOTICE;

void
cm_openlog(const char *ident,
           cm_trace_level_t level)
{
    _cm_level = level;
    openlog(ident,
            0, LOG_LOCAL0);
}

void
cm_closelog()
{
    closelog();
}

void
cm_trace(cm_trace_level_t level,
         const char *format, ...)
{
    va_list ap;
    int priority = LOG_NOTICE;

    if (level < _cm_level) {
        return;
    }

    switch (level) {
    case CM_TRACE_LEVEL_DEBUG:
        /* DEBUG logs are filtered out 
        priority = LOG_DEBUG;
        */
        priority = LOG_NOTICE;
        break;

    case CM_TRACE_LEVEL_NOTICE:
        priority = LOG_NOTICE;
        break;

    case CM_TRACE_LEVEL_ERROR:
        priority = LOG_ERR;
        break;
    }

    va_start(ap, format);
    vsyslog(priority, format, ap);
    va_end(ap);
}
