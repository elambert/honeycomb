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
 * This is a trace implementation that writes the traces on stdout
 */

#include <stdio.h>
#include <stdarg.h>
#include <time.h>
#include <string.h>

#include "trace.h"

static const char *_cm_ident = NULL;
static cm_trace_level_t _cm_level = CM_TRACE_LEVEL_DEBUG;

void
cm_openlog(const char *ident,
           cm_trace_level_t level)
{
    _cm_ident = ident;
    _cm_level = level;
}

void
cm_closelog()
{
    _cm_ident = NULL;
}

void
cm_trace(cm_trace_level_t level,
         const char *format, ...)
{
    char buffer[256];
    char *tag = NULL;
    va_list ap;
    struct tm time_tm, *res;
    time_t sec_time;
    int buffer_offset = 0;

    if (!_cm_ident) {
        return;
    }

    if (level < _cm_level) {
        return;
    }

    time(&sec_time);
    
    res = localtime_r(&sec_time, &time_tm);

    sprintf(buffer, "[%s] %02d/%02d/%02d %02d:%02d:%02d - ",
            _cm_ident,
            time_tm.tm_mon+1,
            time_tm.tm_mday, time_tm.tm_year-100,
            time_tm.tm_hour, time_tm.tm_min, time_tm.tm_sec);
    buffer_offset = strlen(buffer);

    switch (level) {
    case CM_TRACE_LEVEL_DEBUG:
        tag = "DEBUG - ";
        break;

    case CM_TRACE_LEVEL_NOTICE:
        tag = "";
        break;

    case CM_TRACE_LEVEL_ERROR:
        tag = "ERR - ";
        break;

    default:
        tag = "???";
    }

    sprintf(buffer+buffer_offset, "%s", tag);
    buffer_offset += strlen(tag);

    va_start(ap, format);
    vsnprintf(buffer+buffer_offset, 256-buffer_offset, format, ap);
    va_end(ap);

    printf ("%s\n", buffer);
}
