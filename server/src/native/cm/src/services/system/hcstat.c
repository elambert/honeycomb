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
 * Component = statistic server
 * Synopsis  = gather and publish system statistics for this node
 */
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/poll.h>
#include <string.h>
#include "mbox.h"
#include "trace.h"
#include "serialization.h"
#include "hcstat.h"


static const cm_trace_level_t log_level =
#ifdef DEBUG
  CM_TRACE_LEVEL_DEBUG;
#else
  CM_TRACE_LEVEL_NOTICE;
#endif

/*
 * Interval for heartbeating in seconds
 */
static const long hbt_interval = 2;

/*
 * Interval for fetching statistics in seconds
 */
static const long stat_interval = 10;

/*
 * Max number of consecutive errors before
 * exiting the server
 */
static const int max_errors = 10;

/*
 * locals
 */
static hcstat_sysinfo_t sysinfo;
static hcstat_meminfo_t meminfo;


/*
 * Mailbox callback -
 * State machine of the disk server
 */
static void
mbCallback(mb_id_t mbId, mb_action_t action)
{
    int ret;

    switch (action) {

    case ACT_INIT:
        /*
         * Init the server
         */
       ret = sysinfo_init(&sysinfo);
       if (!ret) {
           ret = meminfo_init(&meminfo);
       }
       if (!ret) {
           mb_setstate(mbId, SRV_READY);
       } else {
           cm_trace(CM_TRACE_LEVEL_ERROR, "sysinfo init failed");
           mb_setstate(mbId, SRV_DISABLED);
       }
       break;

    case ACT_START:
        /*
         * Start collecting statistics
         */
       mb_setstate(mbId, SRV_RUNNING);
       break;

    case ACT_STOP:
        /*
         * Stop the server
         */
        if (sysinfo_stop(&sysinfo) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "Error stopping sysinfo");
        }
        if (meminfo_stop(&meminfo) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "Error stopping meminfo");
        }
        mb_setstate(mbId, SRV_READY);
        break;

    case ACT_DESTROY:
        /*
         * Destroy the server
         */
        exit(0);
       
    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "unknown callback action %d", action);
        break;
    }
}

/*
 * Write and update mailbox object
 */
static int
updateMailbox(mb_id_t mbId)
{
    static int _first_write = 1;
    hc_serialization_t *hdl;
    int ret;

    hdl = hc_serialization_open(mbId, HCSTAT_MAILBOX_TYPE, 
                                HCSTAT_MAILBOX_VERSION, _first_write);
    if (hdl == NULL) {
        return (-1);
    }

    ret = hc_serialization_write_int(hdl, sysinfo.updays);
    if (!ret) {
        ret = hc_serialization_write_int(hdl, sysinfo.uphours);
    }
    if (!ret) {
        ret = hc_serialization_write_int(hdl, sysinfo.upminutes);
    }
    if (!ret) {
        ret = hc_serialization_write_float(hdl, sysinfo.user);
    }
   if (!ret) {
       ret = hc_serialization_write_float(hdl, sysinfo.system);
   }
   if (!ret) {
       ret = hc_serialization_write_float(hdl, sysinfo.nice);
   }
   if (!ret) {
       ret = hc_serialization_write_float(hdl, sysinfo.idle);
   }
   if (!ret) {
       ret = hc_serialization_write_float(hdl, sysinfo.intr);
   }
   if (!ret) {
       ret = hc_serialization_write_float(hdl, sysinfo.load1);
   }
   if (!ret) {
       ret = hc_serialization_write_float(hdl, sysinfo.load5);
   }
   if (!ret) {
       ret = hc_serialization_write_float(hdl, sysinfo.load15);
   }
   if (!ret) {
       ret = hc_serialization_write_int(hdl, meminfo.total);
   }
   if (!ret) {
       ret = hc_serialization_write_int(hdl, meminfo.free);
   }
   if (!ret) {
       ret = hc_serialization_write_int(hdl, meminfo.buffers);
   }
   if (!ret) {
       ret = hc_serialization_write_int(hdl, meminfo.cached);
   }
   if (!ret) {
       hc_serialization_commit(hdl);
   } else {
       hc_serialization_abort(hdl);
   }
   if (_first_write) {
       _first_write = 0;
   }
   return ret;
} 

/*
 * Fetch statistics from the system
 */
static int
fetchStats(mb_id_t mbId)
{
    static time_t lastUpdate = 0;
    time_t        now;
    mb_state_t    curState;

   if (mb_getstate(mbId, &curState) == MB_ERROR) {
       cm_trace(CM_TRACE_LEVEL_ERROR,
                "mb_getstat failed: %s", strerror(errno));
       return (-1);
    }
    if (curState != SRV_RUNNING) {
        return (0);
    }

    now = time(NULL);
    if (now - lastUpdate > stat_interval) {
        lastUpdate = now;
        if (sysinfo_curr(&sysinfo) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "sysinfo failed %d", errno);
        } 
        if (meminfo_curr(&meminfo) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "meminfo failed %d" , errno);
        }
        updateMailbox(mbId);
    }
    return (0);
}

    
int
main(int argc, char *argv[])
{
    char       *mboxName;
    mb_id_t    mboxId;
    int        isRunning = max_errors;

    cm_openlog("StatSystem", log_level);

    /*
     * Initialize mailbox
     */
    mboxName = getenv("HC_MAILBOX");
    if (mboxName == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "cannot get mailbox from environment");
        return (1);
    }

    mboxId = mb_init(mboxName, mbCallback);
    if (mboxId == MB_INVALID_ID) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mailbox %s initialization failed", mboxName);
        return (1);
    }

    /*
     * Start heartbeating, periodically fetching
     * system statistics
     */
    cm_trace(CM_TRACE_LEVEL_NOTICE, "stat server is initialized");
    while (isRunning) {

        mb_hbt(mboxId, NULL);

        if (poll(NULL, 0, hbt_interval * 1000) < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "poll system call failed: %s", strerror(errno));
            isRunning--;
        } else if (fetchStats(mboxId) < 0) {
            isRunning--;
        } else {
            isRunning = max_errors;
        }
    }
    return (1);
} 
