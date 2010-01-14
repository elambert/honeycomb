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



/* the heartbeat starts an alarm interrupt to send a heartbeat to the predecessor node
 * and check for the heartbeats received from the successor node */

#include <signal.h>  /* sigaction() */
#include <unistd.h> /* alarm() */

#include "cmm_parameters.h"
#include "sender.h"
#include "lobby.h"
#include "heartbeat.h"
#include "trace.h"

static void sig_heartbeat(int signum);

cmm_error_t 
heartbeat_start()
{
    int err;
    struct sigaction heartbeat_action; /* for heartbeat generation */
    /* start the heartbeat */
    heartbeat_action.sa_handler = &sig_heartbeat;
    heartbeat_action.sa_flags = 0;
    heartbeat_action.sa_flags |= SA_RESTART; /* to restart interrupted calls */
    err = sigaction(SIGALRM, &heartbeat_action, NULL);
    if (err < 0) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Starting the heartbeat failed [%d]", err );
        return (CMM_EOTHER);
    }
    /* start the alarm */
    alarm(NODE_HEARTBEAT_INTERVAL); 
    return (CMM_OK);
}

static void 
sig_heartbeat(int signum)
{
    send_heartbeat(signum);
    check_heartbeat(signum);
    alarm(NODE_HEARTBEAT_INTERVAL);
}
