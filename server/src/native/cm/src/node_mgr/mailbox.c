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



#include <mbox.h>
#include <serialization.h>
#include <trace.h>
#include <stdio.h>

#include "mailbox.h"
#include "scheduler.h"
#include "cmm_interface.h"

/*
 * Global variables
 */

static int _initialized = 0;
static mb_id_t _mailbox_id = MB_INVALID_ID;
static int _first_write = 1;
static char _mailbox_tag[MAXPATHLEN];

/*
 * Private routines
 */

static int
update_mailbox_rec(hc_serialization_t *handle,
                   hc_tree_opaque_t *service_node)
{
    hc_tree_opaque_t *next = NULL;
    hc_service_t *service = NULL;
    mb_state_t state;
    mb_error_t err;
    int err_code;

    service = (hc_service_t*)hc_tree_get_datas(service_node);

    if ((service->filter & HC_FILTER_NODE_ANYNODE)
        || (is_master())) {
        /* Put the service in the mailbox in that case */

        if (service->mailbox_id != MB_INVALID_ID) {
            err = mb_getstate(service->mailbox_id, &state);
            if (err != MB_OK) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "Couldn't retrieve the state of the service %s",
                         service->start_cmd);
                return(1);
            }
        } else {
            state = SRV_INVALID;
        }

        /* Write the service */
        hc_serialization_write_string(handle, service->start_cmd);
        hc_serialization_write_string(handle, service->mailbox_tag);
        hc_serialization_write_int(handle, state);
    }

    next = hc_tree_get_next_brother(service_node);
    if (next) {
        err_code = update_mailbox_rec(handle, next);
        if (err_code) {
            return(err_code);
        }
    }

    next = hc_tree_get_first_child(service_node);
    if (next) {
        err_code = update_mailbox_rec(handle, next);
        if (err_code) {
            return(err_code);
        }
    }

    return(0);
}

int
service_count(hc_tree_opaque_t *service_node)
{
    hc_tree_opaque_t *next = NULL;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(service_node);
    int result = 0;

    if ((service->filter & HC_FILTER_NODE_ANYNODE)
        || (is_master())) {
        result = 1;
    }

    next = hc_tree_get_next_brother(service_node);
    if (next) {
        result += service_count(next);
    }

    next = hc_tree_get_first_child(service_node);
    if (next) {
        result += service_count(next);
    }

    return(result);
}    

/*
 * API implementation
 */

int
open_mailbox()
{
    if (!_initialized) {
        snprintf(_mailbox_tag, sizeof(_mailbox_tag), "%d/%s",
                 get_local_nodeid(), NODEMGR_MAILBOX_TAG);
        _initialized = 1;
    }

    _mailbox_id = mb_create(_mailbox_tag,
                            NODEMGR_MAILBOX_SIZE);
    if (_mailbox_id == MB_INVALID_ID) {
        return(1);
    }

    _first_write = 1;

    return(0);
}

int
update_mailbox()
{
    hc_tree_opaque_t *service_node = NULL;
    hc_serialization_t *ser_handle = NULL;
    int err;
    int count;

    if (_mailbox_id == MB_INVALID_ID) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The mailbox id is invalid [update_mailbox]");
        return(1);
    }

    ser_handle = hc_serialization_open(_mailbox_id,
                                       NODEMGR_MAILBOX_TYPE,
                                       NODEMGR_MAILBOX_VERSION,
                                       _first_write);
    if (!ser_handle) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_serialization_open failed");
        return(1);
    }

    if (cmm_update_mailbox(ser_handle)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "cmm_update_mailbox failed");
        hc_serialization_abort(ser_handle);
        return(1);
    }

    service_node = hc_scheduler_get_services();
    
    if (service_node) {
        count = service_count(service_node);
    } else {
        count = 0;
    }
    (int)hc_serialization_write_int(ser_handle, count);

    if (service_node) {
        err = update_mailbox_rec(ser_handle, service_node);
        if (err) {
            /* There has been an error. Don't write to the mailbox */
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't flush all the services to the mailbox. Mailbox write aborted");
            hc_serialization_abort(ser_handle);
            ser_handle = NULL;
            return(err);
        }
    }

    hc_serialization_commit(ser_handle);
    mb_broadcast(_mailbox_id);
    ser_handle = NULL;
    if (_first_write) {
        _first_write = 0;
    }

    return(0);
}

void
close_mailbox()
{
    mb_error_t err;

    if (_mailbox_id ==MB_INVALID_ID) {
        return;
    }

    err = mb_close(_mailbox_id);
    if (err != MB_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mb_close failed");
        _mailbox_id = MB_INVALID_ID;
        return;
    }

    _mailbox_id = MB_INVALID_ID;

    err = mb_unlink(_mailbox_tag);
    if (err != MB_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mb_unlink failed");
        return;
    }
}
