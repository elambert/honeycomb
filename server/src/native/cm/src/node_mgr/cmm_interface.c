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



#include <trace.h>
#include <malloc.h>

#include "cmm_interface.h"
#include "mailbox.h"
#include "scheduler.h"

/*
 * Static variables
 */

static int _connected = 0;
static int _cmm_fd = -1;
static cmm_nodeid_t _node_id = CMM_INVALID_NODE_ID;
static int _is_master = -1;

/*
 * Private routines
 */

static int
connect()
{
    cmm_error_t err;
    timespec_t timeout = {0, 0};

    if (_connected) {
        return(0);
    }

    err = cmm_connect(timeout);
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_connect failed [%d]",
                 err);
        return(1);
    }

    _connected = 1;
    return(0);
}

static int
disconnect()
{
    cmm_error_t err;

    if (!_connected) {
        return(0);
    }

    err = cmm_disconnect();
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_disconnect failed [%d]",
                 err);
        return(1);
    }

    _connected = 0;
    return(0);
}

static void
cmm_callback(const cmm_cmc_notification_t *change_notification,
             void *client_data)
{
    /* Update the mailbox for now */
    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "A CMM event occured. Updating the mailbox");

    if (change_notification->nodeid == get_local_nodeid()) {
        switch (change_notification->cmchange) {
        case CMM_MASTER_ELECTED:
            /* Starting the MASTER services */
            _is_master = 1;
            hc_scheduler_change_master_services(1);
            break;

        case CMM_MASTER_DEMOTED:
            /* Stopping the MASTER services */
            _is_master = 0;
            hc_scheduler_change_master_services(0);
            break;

        default:
            break;
        }
    }

    update_mailbox();
}

/*
 * API implementation
 */

cmm_nodeid_t
get_local_nodeid()
{
    cmm_error_t err;
    
    if (_node_id != CMM_INVALID_NODE_ID) {
        return(_node_id);
    }

    if (connect()) {
        return(CMM_INVALID_NODE_ID);
    }

    err = cmm_node_getid(&_node_id);
    if (err != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_node_getid failed [%d]",
                 err);
        _node_id = CMM_INVALID_NODE_ID;
    }

    return(_node_id);
}

int
is_master()
{
    cmm_error_t err;
    cmm_member_t member;
    cmm_nodeid_t nodeid;

    if (_is_master == -1) {
        /*
         * Get the fd to register to the callbacks now, before having the
         * current state.
         */
        get_cmm_fd();

        nodeid = get_local_nodeid();
        if (nodeid == CMM_INVALID_NODE_ID) {
            return(-1);
        }

        err = cmm_member_getinfo(nodeid, &member);
        if (err != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "cmm_member_getinfo failed on myself [%d]",
                     err);
            _is_master = -1;
        } else {
            if (member.sflag & CMM_MASTER) {
                _is_master = 1;
            } else {
                _is_master = 0;
            }
        }
    }
        
    return(_is_master);
}

/*
 * Wait for the master - TEMPORARY workaround.
 * The node manager has problems to detect a master
 * election during the initialization of hc services.
 */
void
wait_for_master()
{
    cmm_error_t err;
    cmm_member_t member;
    
    if (connect()) {
        cm_trace(CM_TRACE_LEVEL_ERROR, "cannot connect to CMM");
        return;
    }
    do {
        err = cmm_master_getinfo(&member);
    } while (err != CMM_OK);
}

int
get_cmm_fd()
{
    if (_cmm_fd != -1) {
        return(_cmm_fd);
    }

    if (cmm_cmc_register(cmm_callback, NULL) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_cmc_register failed");
        return(-1);
    }

    if (cmm_notify_getfd(&_cmm_fd) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_notify_getfd failed");
        (cmm_error_t)cmm_cmc_unregister();
        _cmm_fd = -1;
        return(-1);
    }

    return(_cmm_fd);
}

int
cmm_update_mailbox(hc_serialization_t *handle)
{
    uint32_t count, count_read;
    cmm_member_t *members;
    int i;

    if (cmm_member_getcount(&count) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_member_getcount failed");
        return(1);
    }

    members = (cmm_member_t*)malloc(count*sizeof(cmm_member_t));
    if (!members) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "malloc failed");
        return(1);
    }

    if (cmm_member_getall(count, members, &count_read) != CMM_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_member_getall failed");
        free(members);
        return(1);
    }
    
    if (hc_serialization_write_int(handle, _node_id)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_serialization_write_int failed");
    }

    if (hc_serialization_write_int(handle, count_read)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_serialization_write_int failed");
    }

    for (i=0; i<count_read; i++) {
        if (hc_serialization_write_int(handle, members[i].nodeid)
            || (hc_serialization_write_string(handle, members[i].name))
            || (hc_serialization_write_string(handle, members[i].addr))
            || (hc_serialization_write_int(handle, members[i].sflag
                                           & (CMM_MASTER | CMM_OUT_OF_CLUSTER | CMM_FLAG_DISQUALIFIED)))) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "hc_serialization failed");
        }
    }

    free(members);
    members = NULL;

    return(0);
}

void
cmm_dispose()
{
    disconnect();
}
