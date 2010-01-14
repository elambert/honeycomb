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



#ifndef _HC_TRACE_H
#define _HC_TRACE_H

#include <syslog.h>
#include <sys/varargs.h>

#define MAX_TRACE_LEN 4096      /* Maximum length of a message */

#define log_err(...) hc_log_msg(LOG_ERR, __VA_ARGS__)
#define log_info(...) hc_log_msg(LOG_INFO, __VA_ARGS__)
#define log_debug(...) hc_log_msg(LOG_DEBUG, __VA_ARGS__)
#define log_notice(...) hc_log_msg(LOG_NOTICE, __VA_ARGS__)
#define log_warning(...) hc_log_msg(LOG_WARNING, __VA_ARGS__)

extern void hc_log_msg(int level, const char* fmt,  ...);
extern void set_logging_options(int level, FILE* log_output);

extern char* get_string(void* obj, size_t len);
extern char* get_hex_string(void* obj, size_t len);

#endif /* _HC_TRACE_H */

