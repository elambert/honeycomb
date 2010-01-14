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
 * Component = Mailbox deamon
 * Synopsis  = process in charge of distributing the mailboxes view
 */

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/poll.h>
#include <string.h>
#include <errno.h>
#include <trace.h>

#include "conf.h"
#include "cmm.h"


static const cm_trace_level_t log_level =
#ifdef DEBUG
  CM_TRACE_LEVEL_DEBUG;
#else
  CM_TRACE_LEVEL_NOTICE;
#endif

/*
 * Interval for heartbeating. In seconds
 */
static const long hbt_mboxd_interval = 2;

/*
 * Interval for distributing mailboxes. In seconds.
 */
static const long publish_time = 10;

/*
 * Timeout for service initialization. In seconds
 */
static const long startup_time = 120;

/*
 * Max number of errors before giving up
 */
static const int max_errors = 7;

/*
 * Multicast socket and CMM file descriptor
 */
static int mcast_fd;
static int cmm_fd;


static void
mboxd_callback(mb_id_t mbid, mb_action_t action)
{
    switch (action) {

    case ACT_INIT:
        /*
         * Initialization.
         */
        cm_trace(CM_TRACE_LEVEL_DEBUG, "init requested");
        mcast_fd = mb_net_init();
        if (mcast_fd < 0) {
            mb_setstate(mbid, SRV_DISABLED);
        } else {
            mb_setstate(mbid, SRV_READY);
        }
        break;

    case ACT_START:
        /*
         * Start the process - 
         * RUNNING is delayed until we received a copy
         * of all the mailboxes
         */
        cm_trace(CM_TRACE_LEVEL_DEBUG, "start requested");
        break;
 
    case ACT_STOP:
        /*
         * Stop processing requests
         */
        cm_trace(CM_TRACE_LEVEL_DEBUG, "stop requested");
        mb_net_disconnect(mcast_fd);
        mcast_fd = -1;
        mb_setstate(mbid, SRV_READY);
        break;

    case ACT_DESTROY:
       /*
        * Destroy process
        */
       cm_trace(CM_TRACE_LEVEL_DEBUG, "exit requested");
       exit(0);
   
    default:
       cm_trace(CM_TRACE_LEVEL_ERROR, 
                "unkown callback action %d", action);
       break;
    }
}


static void
cmm_callback(const cmm_cmc_notification_t *notif, void *cookie)
{
    switch (notif->cmchange) {

    case CMM_MEMBER_LEFT:
        mb_net_delete(notif->nodeid);
        break;

    default:
        break;
    }
}

int
main(int argc, char* argv[])
{
    char           *mbox_name;
    mb_id_t        mbid;
    long           last_publish = 0;
    long           start_time;
    timespec_t     timeout = {0, 0};
    int            force_publish;
    int            process_running = max_errors;
    struct timeval tv;
    cmm_error_t    err;

    mcast_fd = -1;
    force_publish = 0;

    cm_openlog("hcmboxd", log_level);

    /* get and initialize mailbox */
    mbox_name = getenv("HC_MAILBOX");
    if (mbox_name == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cannot find mbox environment. Exiting");
        return (1);
    }

    mbid = mb_init(mbox_name, mboxd_callback);
    if (mbid == MB_INVALID_ID) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mailbox %s initialization failed. Exiting",
                 mbox_name);
        return (1);
    }

    /* connect to cmm and ask for notification */
    err = cmm_connect(timeout);
    if (err != CMM_OK) { 
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_connect failed %d. Exiting", err);
        return (1);
    }

    if (cmm_cmc_register(cmm_callback, NULL)) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "cmm_cmc_register failed");
        return (1);
    }

    err = cmm_notify_getfd(&cmm_fd);
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "can get cmm file descriptor");
        return (1);
    }
    
    /*
     * Main loop -
     * Process mailboxes distribution, CMM notification
     * and periodically heartbeats.
     */ 

    gettimeofday(&tv, NULL);
    start_time = tv.tv_sec;
    while (process_running) {

        struct pollfd  ufd[2];
        unsigned int   nfds;
        mb_state_t     cur_state;
        int            ret;

        mb_hbt(mbid, NULL); 
        if (mb_getstate(mbid, &cur_state) == MB_ERROR) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "mb_get_state fails: %s", strerror(errno));
            process_running--;
        }

        ufd[0].fd      = cmm_fd;
        ufd[0].events  = POLLIN | POLLERR;
        ufd[0].revents = 0;
        ufd[1].fd      = mcast_fd;
        ufd[1].events  = POLLIN | POLLERR;
        ufd[1].revents = 0;

        if (mcast_fd > 0) {
            nfds = 2;
        } else nfds = 1;

        ret = poll(ufd, nfds, hbt_mboxd_interval * 1000);
        if (ret < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "poll failed %s", strerror(errno));
            process_running--;
        }

        /* process CMM notification */
        if (ufd[0].revents & POLLIN) {
            if (cmm_notify_dispatch() != CMM_OK) { 
                cm_trace(CM_TRACE_LEVEL_ERROR, "cmm_notify_dispatch failed");
                process_running--;
            }
        }

        /* process received mailboxes */
        if (ufd[1].revents & POLLIN) {
            int ret = mb_net_update(mcast_fd);
            if (ret < 0) {
                cm_trace(CM_TRACE_LEVEL_ERROR, "mb_net_update fails: %s", 
                         strerror(errno));
            } else if (ret > 0) {
                force_publish = 1;
            }
        }

        /* time to publish ? */
        gettimeofday(&tv, NULL);
        if (force_publish || (tv.tv_sec - last_publish) > publish_time) {
            if (mb_net_publish() == -1) {
                cm_trace(CM_TRACE_LEVEL_ERROR, "mb_net_publish fails: %s", 
                         strerror(errno));
            }
            last_publish  = tv.tv_sec;
            force_publish = 0;
        }
      
        /* check and sync mailboxes */
        if (cur_state == SRV_READY) {
            int set_running;
            if ((tv.tv_sec - start_time) > startup_time) {
                cm_trace(CM_TRACE_LEVEL_NOTICE, "failed to sync up mailboxes");
                set_running = 1;
            } else if (mb_net_sync() == 0) {
                set_running = 1;
            } else {
                set_running = 0;
            }
            if (set_running) {
                if (mb_getexpectedstate(mbid, &cur_state) == MB_OK) {
                    if (cur_state == SRV_RUNNING) {
                        mb_setstate(mbid, SRV_RUNNING);
                    }
                } else {
                    process_running--;
                }
            }
        }
    }
    return (1);
}
