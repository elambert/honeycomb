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
 * Synopsis  = uptime
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


#define PROC_UPTIME   "/proc/uptime"
#define PROC_LOADAVG  "/proc/loadavg"
#define PROC_STAT     "/proc/stat"
#define MAX_ENTRY_LEN 1024

static FILE* fpuptime;
static FILE* fploadavg;
static FILE* fpstat;


int
sysinfo_init(hcstat_sysinfo_t *info)
{
    fpuptime = fopen(PROC_UPTIME, "r");
    if (fpuptime == NULL) {
        return (-1);
    }
    setvbuf(fpuptime, NULL, _IONBF, 0);

    fploadavg = fopen(PROC_LOADAVG, "r");
    if (fploadavg == NULL) {
        fclose(fpuptime);
        return (-1);
    }
    setvbuf(fploadavg, NULL, _IONBF, 0);

    fpstat = fopen(PROC_STAT, "r");
    if (fpstat == NULL) {
        fclose(fpuptime);
        fclose(fploadavg);
        return (-1);
    }
    setvbuf(fpstat, NULL, _IONBF, 0);

    sysinfo_curr(info);
    return (0);
}

int
sysinfo_stop(hcstat_sysinfo_t *info)
{
    (void) fclose(fpuptime);
    (void) fclose(fploadavg);
    (void) fclose(fpstat);

    return (0);
}

int 
sysinfo_curr(hcstat_sysinfo_t *info)
{
    static time_t       onow;
    static unsigned int ouser;
    static unsigned int onice;
    static unsigned int osystem;
    static unsigned int oidle;
    static unsigned int ointr;

    float         uptime;
    float         load1;
    float         load5;
    float         load15;
    time_t        now;
    time_t        delta;
    unsigned int  intr;
    unsigned int  user;
    unsigned int  nice;
    unsigned int  system;
    unsigned long idle;
    char          line[MAX_ENTRY_LEN];

    rewind(fpuptime);
    if (fscanf(fpuptime, "%f %*f", &uptime) == 1) {
        info->updays = (int) uptime / (60 * 60 * 24);
        info->upminutes = (int) uptime / 60;
        info->uphours = info->upminutes / 60;
        info->uphours %= 24;
        info->upminutes %= 60; 
    } 

    rewind(fploadavg);
    if (fscanf(fploadavg, "%f %f %f %*d/%*d %*d",
               &load1,
               &load5,
               &load15) == 3) {
        info->load1  = load1;
        info->load5  = load5;
        info->load15 = load15;
    } 
 
    now   = time(NULL);
    delta = now - onow;
    if (!delta) {
        delta = 1;
    }
    rewind(fpstat);
    while (fgets(line, sizeof(line), fpstat) != NULL) {
        if (!strncmp(line, "cpu ", 4)) {
            int tt = sscanf(line + 5, "%u %u %u %lu",
                            &user,
                            &nice,
                            &system,
                            &idle);
            if (tt == 4) {
                unsigned int tdelta = (user + nice + system + idle) - 
                                      (ouser + onice + osystem + oidle);
                if (!tdelta) {
                    tdelta =1;
                }
                info->user = (((float) (user - ouser)) / tdelta * HZ); 
                info->nice = (((float) (nice - onice)) / tdelta * HZ);
                info->system = (((float) (system - osystem)) / tdelta * HZ);
                info->idle = (((float) (idle - oidle)) / tdelta * HZ);

                ouser   = user;
                onice   = nice;
                osystem = system;
                oidle   = idle;
            }
        } else if (!strncmp(line, "intr ", 5)) {
            int tt = sscanf(line + 5, "%u", &intr);
            if (tt == 1) {
                info->intr = (((float) (intr - ointr)) / delta);
                ointr = intr;
            }
        }
    }
    onow = now;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "up %dd:%dh:%dmn load avg %.2f, %.2f, %.2f "
             "%.2f user %.2f system %.2f nice %.2f idle %.2f intr/s",
             info->updays,
             info->uphours,
             info->upminutes,
             info->load1,
             info->load5,
             info->load15,
             info->user,
             info->system,
             info->nice,
             info->idle,
             info->intr);

    return (0);
}
