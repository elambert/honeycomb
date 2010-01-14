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
 * Component = stat server -
 * Synopsis  = stat server structures and constants
 */

#ifndef _HC_STAT_H
#define _HC_STAT_H

#define HCSTAT_MAILBOX_TYPE	4
#define HCSTAT_MAILBOX_VERSION  1

typedef struct {
    int      updays;
    int      uphours;
    int      upminutes;
    float    user;
    float    system;
    float    nice;
    float    idle;
    float    intr;
    float    load1;
    float    load5;
    float    load15;
} hcstat_sysinfo_t;

typedef struct {
    int    total;
    int    free;
    int    buffers;
    int    cached;
} hcstat_meminfo_t;

extern int sysinfo_init(hcstat_sysinfo_t*);
extern int sysinfo_curr(hcstat_sysinfo_t*);
extern int sysinfo_stop(hcstat_sysinfo_t*); 

extern int meminfo_init(hcstat_meminfo_t*);
extern int meminfo_curr(hcstat_meminfo_t*);
extern int meminfo_stop(hcstat_meminfo_t*); 

#endif /* _HC_STAT_H */
