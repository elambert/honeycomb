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
 * Implementation of the actions code
 */

#include <malloc.h>
#include <trace.h>
#include <sys/types.h>
#include <signal.h>

#include "action.h"
#include "forker.h"
#include "tree.h"
#include "mbox.h"

/*
 * Type definitions
 */

typedef enum {
    ACTION_EXIT_FAILED =0,
    ACTION_EXIT_SUCCEEDED,
    ACTION_EXIT_WAITING
} action_exit_code_t;

typedef action_exit_code_t (*action_routine_t)(void *params);

typedef struct {
    action_type_t type;
    action_routine_t routine;
} action_tab_t;

/*
 * Action routine declarations
 */

static action_exit_code_t action_test(void *params);
static action_exit_code_t action_fork_service(void *params);
static action_exit_code_t action_kill_service(void *params);
static action_exit_code_t action_change_state(void *params);
static action_exit_code_t action_waitfor_state(void *params);

/*
 * Global variables
 */

char *mbox_state_strings[] = {
    "INVALID",
    "INIT",
    "READY",
    "RUNNING",
    "DISABLED",
    "DESTROY"
};

static action_tab_t action_tab[] = {
    {ACTION_TYPE_TEST, action_test},
    {ACTION_TYPE_FORK_SERVICE, action_fork_service},
    {ACTION_TYPE_KILL_SERVICE, action_kill_service},
    {ACTION_TYPE_CHANGE_STATE, action_change_state},
    {ACTION_TYPE_WAITFOR_STATE, action_waitfor_state}
};

/*
 * Action routine implementation
 */

static action_exit_code_t
action_test(void *params)
{
    action_test_arg_t *args = (action_test_arg_t*)params;

    (*args->test_arg) = 1;

    if (args->should_fail) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "action_test executed. Returns ACTION_EXIT_FAILED");
        return(ACTION_EXIT_FAILED);
    }

    if (args->should_wait) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "action_test executed. Returns ACTION_EXIT_WAITING");
        return(ACTION_EXIT_WAITING);
    }

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "action_test executed. Returns ACTION_EXIT_SUCCEEDED");

    return(ACTION_EXIT_SUCCEEDED);
}

static action_exit_code_t
action_fork_service(void *params)
{
    action_service_arg_t *args = (action_service_arg_t*)params;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(args->service_node);
    int res;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Entering action_fork_service [%s]",
             service->start_cmd);

    if (service->pid == -1) {
        res = hc_forker_fork_service(args->service_node);
        if (res) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't start the service");
            return(ACTION_EXIT_FAILED);
        }
    } else {
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "The service %s is already running.",
                 service->start_cmd);
    }

    return(ACTION_EXIT_SUCCEEDED);
}
	     
static action_exit_code_t
action_kill_service(void *params)
{
    action_service_arg_t *args = (action_service_arg_t*)params;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(args->service_node);

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Entering action action_kill_service [%s]",
             service->start_cmd);

    if (hc_forker_kill_service(args->service_node)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Couldn't kill the service");
        return(ACTION_EXIT_FAILED);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "The service %s has been stopped",
             service->start_cmd);

    return(ACTION_EXIT_SUCCEEDED);
}

static action_exit_code_t
action_change_state(void *params)
{
    action_change_state_arg_t *args = (action_change_state_arg_t*)params;
    hc_service_t *service = NULL;
    mb_state_t state;

    service = (hc_service_t*)hc_tree_get_datas(args->service_node);
    
    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Entering action action_change_state [%s -> %s]",
             service->start_cmd,
             mbox_state_strings[args->expected_state]);

    if (service->mailbox_id == MB_INVALID_ID) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The service %s does not have any mailbox. Cannot change its state",
                 service->start_cmd);
        return(ACTION_EXIT_FAILED);
    }

    if (service->pid == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The service %s cannot go to state %s since it is not running",
                 service->start_cmd, mbox_state_strings[args->expected_state]);
        return(ACTION_EXIT_FAILED);
    }

    if (mb_getstate(service->mailbox_id, &state) != MB_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "Failed to retrieve the state of %s",
                 service->start_cmd);
        return(ACTION_EXIT_FAILED);
    }

    if (state == args->expected_state) {
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "The service %s is already in the state %d. Request aborted",
                 service->start_cmd, args->expected_state);
        return(ACTION_EXIT_SUCCEEDED);
    }

    if (state == SRV_DISABLED) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Service %s cannot move to state %s since it is DISABLED",
                 service->start_cmd,
                 mbox_state_strings[args->expected_state]);
        return(ACTION_EXIT_FAILED);
    }

    if (mb_setexpectedstate(service->mailbox_id, args->expected_state) != MB_OK) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Couldn't set the expected state to %d for %s",
                 args->expected_state,
                 service->start_cmd);
        return(ACTION_EXIT_FAILED);
    }

    service->waiting_action_node = args->action_node;

    return(ACTION_EXIT_WAITING);
}

static action_exit_code_t
action_waitfor_state(void *params)
{
    action_service_arg_t *args = (action_service_arg_t*)params;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(args->service_node);
    mb_state_t state;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Entering action action_waitfor_state [%s]",
             service->start_cmd);

    if (service->mailbox_id == MB_INVALID_ID) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "The service %s has an invalid mailbox. Couldn't wait for state",
                 service->start_cmd);
        return(ACTION_EXIT_FAILED);
    }

    if ((mb_getstate(service->mailbox_id, &state) != MB_OK)
        || (mb_setexpectedstate(service->mailbox_id, SRV_INVALID) != MB_OK)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "mailbox management failed in action_waitfor_state");
        return(ACTION_EXIT_FAILED);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "Service %s managed to go to state %s",
             service->start_cmd,
             mbox_state_strings[state]);

    return(ACTION_EXIT_SUCCEEDED);
}

/*
 * Private routines
 */

static action_routine_t
hc_action_get_action_routine(action_type_t type)
{
    int i, nb;

    nb = sizeof(action_tab) / sizeof(action_tab_t);

    for (i=0; i<nb; i++) {
        if (action_tab[i].type == type) {
            return(action_tab[i].routine);
        }
    }

    return(NULL);
}

/*
 * API implementation
 */

void
action_param_simple_free(void *params)
{
    free(params);
}

hc_action_t *
hc_action_allocate(action_type_t type,
                   action_param_free_t param_free)

{
    hc_action_t *result = NULL;

    result = (hc_action_t*)malloc(sizeof(hc_action_t));
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "malloc failed");
        return(NULL);
    }

    result->type = type;
    result->status = ACTION_STATUS_READY;
    result->params = NULL;
    result->param_free = param_free;

    return(result);
}

void
hc_action_free(hc_action_t *action)
{
    if (action->status == ACTION_STATUS_READY) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Freeing an action which is in status ACTION_STATUS_READY");
    }
    if (action->status == ACTION_STATUS_WAITING) {
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Freeing an action which is in status ACTION_STATUS_WAITING");
    }

    if (action->param_free) {
        action->param_free(action->params);
    }
    action->params = NULL;
    free(action);
}

void
hc_action_execute(hc_action_t *action)
{
    action_exit_code_t exit_code;
    action_routine_t routine;

    routine = hc_action_get_action_routine(action->type);
    if (!routine) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The action routine for type [%d] has not been found",
                 action->type);
        action->status = ACTION_STATUS_FAILED;
        return;
    }

    exit_code = routine(action->params);
    switch (exit_code) {
    case ACTION_EXIT_FAILED:
        action->status = ACTION_STATUS_FAILED;
        break;

    case ACTION_EXIT_SUCCEEDED:
        action->status = ACTION_STATUS_EXECUTED;
        break;

    case ACTION_EXIT_WAITING:
        action->status = ACTION_STATUS_WAITING;
        break;
    }
}

