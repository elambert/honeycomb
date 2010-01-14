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
 * Component = stat server
 * Synopsis  = meminfo
 */

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <time.h>
#include <string.h>
#include <sys/param.h>
#include "hcstat.h"
#include "trace.h"

#define PROC_MEMINFO  "/proc/meminfo"
#define MAX_ENTRY_LEN 1024

static FILE* fpmeminfo;

int
meminfo_init(hcstat_meminfo_t *info)
{
    fpmeminfo = fopen(PROC_MEMINFO, "r");
    if (fpmeminfo == NULL) {
        return (-1);
    }
    setvbuf(fpmeminfo, NULL, _IONBF, 0);
    meminfo_curr(info);
    return (0);
}

int
meminfo_stop(hcstat_meminfo_t *info)
{
    (void) fclose(fpmeminfo);
    return (0);
}

int
meminfo_curr(hcstat_meminfo_t *info)
{
    unsigned long memTotal;
    unsigned long memFree;
    unsigned long memBuffer;
    unsigned long memCached;
    char line[MAX_ENTRY_LEN];

    rewind(fpmeminfo);
    while (fgets(line, sizeof(line), fpmeminfo) != NULL) {
 
        if (!strncmp(line, "MemTotal:", 9)) {
            int tt = sscanf(line + 10, "%lu", &memTotal);
            if (tt == 1) {
                info->total = memTotal;
            }
        } else if (!strncmp(line, "MemFree:", 8)) {
            int tt = sscanf(line + 10, "%lu", &memFree);
            if (tt == 1) {
                info->free = memFree;
            }
        } else if (!strncmp(line, "Buffers:", 8)) {
            int tt = sscanf(line + 10, "%lu", &memBuffer);
            if (tt == 1) {
                info->buffers = memBuffer;
            }
        } else if (!strncmp(line, "Cached:", 7)) {
            int tt = sscanf(line + 10, "%lu", &memCached);
            if (tt == 1) {
                info->cached = memCached;
            }
        }
    }
    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "total %d free %d buffers %d cached %d",
             info->total,
             info->free,
             info->buffers,
             info->cached);

    return (0);
}
