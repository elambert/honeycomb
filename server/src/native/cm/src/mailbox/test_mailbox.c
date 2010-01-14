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
 * Component = Mailbox service
 * Synopsis  = unit test
 */

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <signal.h>
#include <sys/errno.h>
#include <sys/wait.h>
#include "trace.h"
#include "mbox.h"


#define MB_LEN    100
#define MB_OFFSET 10
#define MB_CHECK  0x55AA55AA

static void
act_callback(mb_id_t mbid, mb_action_t act)
{
    unsigned long mbcheck = MB_CHECK;

    switch (act) {
    case ACT_INIT:
       if (mb_setstate(mbid, SRV_READY) == MB_ERROR) {
           cm_trace(CM_TRACE_LEVEL_ERROR,
                    "failed to set state SRV_READY");
           exit(1);
       }
       break;

    case ACT_START:
       if (mb_write(mbid, &mbcheck, MB_OFFSET, sizeof(mbcheck)) == MB_ERROR) {           cm_trace(CM_TRACE_LEVEL_ERROR, 
                    "mb_write failed: %s", strerror(errno));
           exit(1);
       } 
       if (mb_setstate(mbid, SRV_RUNNING) == MB_ERROR) {
           cm_trace(CM_TRACE_LEVEL_ERROR,
                    "failed to set state SRV_RUNNING");
           exit(1);
       }
       break;

    case ACT_STOP:
       if (mb_setstate(mbid, SRV_READY) == MB_ERROR) {
           cm_trace(CM_TRACE_LEVEL_ERROR,
                    "failed to set state SRV_READY");
           exit(1);
       }
       break;

    case ACT_DESTROY:
       exit(0);
       break;

    default:
       cm_trace(CM_TRACE_LEVEL_ERROR,
                "act_callback unknown action %d", act);
       exit(1);
       break;
    }
}

static int
wait_for(mb_id_t mbid, mb_state_t state)
{
   mb_state_t cur_state;
   int nb_loops = 0;

    if (mb_setexpectedstate(mbid, state) == MB_ERROR) {
        return (-1);
    }
    do {
        if (mb_getstate(mbid, &cur_state) == MB_ERROR) {
            return (-1);
        }
        if (cur_state == state) {
            return (0);
        }
        usleep(100000);
    } while (nb_loops++ < 5);

    return (-1);
}
        

int
main()
{
    mb_id_t       mbid;
    pid_t         pid;
    int           nb_loops;
    int           status;
    unsigned long mbcheck;
    const char    *tagmb = "test_mailbox";

    cm_openlog("test_mailbox", CM_TRACE_LEVEL_NOTICE);

    mbid = mb_create(tagmb, MB_LEN); 
    if (mbid == MB_INVALID_ID) {
        return (1);
    }

    pid = fork();
    if (pid == 0) {
        /*
         * Child - process callback 
         */
        mb_id_t child_mbid = mb_init(tagmb, act_callback);
        if (mb_len(mbid) != MB_LEN) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "mb_len returns bad result");
            exit(1);
        }
        while (1) {
           if (mb_hbt(child_mbid, NULL) != MB_OK) {
               cm_trace(CM_TRACE_LEVEL_ERROR,
                        "mb_hbt failed %s", strerror(errno));
               exit(1);
           }
           usleep(100000);
        }
    } else if (pid == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "fork failed");
        exit(1);
    }

    /*
     * Father - check service state machine
     */
    if (wait_for(mbid, SRV_READY) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "failed to init");
        kill(pid, SIGKILL);
        return (1);
    }
    if (wait_for(mbid, SRV_RUNNING) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "failed to start");
        kill(pid, SIGKILL);
        return (1);
    }
    if (mb_read(mbid, &mbcheck, MB_OFFSET, sizeof(mbcheck)) == MB_ERROR) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "mb_read failed");
        kill(pid, SIGKILL);
        return (1);
    }
    if (mbcheck != MB_CHECK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "mb_read returns wrong result");
        kill(pid, SIGKILL);
        return (1);
    }
    if (wait_for(mbid, SRV_READY) < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "failed to stop");
        kill(pid, SIGKILL);
        return (1);
    }

    if (mb_setexpectedstate(mbid, SRV_DESTROY) == MB_ERROR) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "failed to destroy");
        kill(pid, SIGKILL);
        return (1);
    }
    nb_loops = 0;
    do {
        pid_t wpid = waitpid(pid, &status, WNOHANG);
        if (wpid == pid) {
            break;
        } else if (wpid < 0) {
            cm_trace(CM_TRACE_LEVEL_ERROR, "waitpid failed");
            kill(pid, SIGKILL);
            return (1);
        }
        usleep(100000);
    } while (nb_loops++ < 5);
 
    if (nb_loops >= 5) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "destroy failed");
        kill(pid, SIGKILL);
        return (1);
    }
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "bad exit status");
        kill(pid, SIGKILL);
        return (1);
    }
        
    cm_trace(CM_TRACE_LEVEL_NOTICE, "mailbox test passed");
    cm_closelog();
    return (0);
}
