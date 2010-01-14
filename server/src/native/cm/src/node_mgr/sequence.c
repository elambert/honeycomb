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
 * Implementation of the sequences
 */

#include <malloc.h>
#include <trace.h>

#include "sequence.h"
#include "action.h"
#include "scheduler.h"

/*
 * Private routines
 */

static void
schedule_next(hc_sequence_t *sequence,
              hc_tree_opaque_t *action_node)
{
    hc_tree_opaque_t *node = NULL;
    sequence_status_t status;
	
    switch (sequence->execution) {
    case HC_SEQUENCE_EXECUTION_NORMAL:
        /* Put the children in the queue */
        node = hc_tree_get_first_child(action_node);
        while (node) {
            hc_list_add_element(sequence->ready_actions,
                                node);
            node = hc_tree_get_next_brother(node);
        }
        break;

    case HC_SEQUENCE_EXECUTION_BOTTOMUP:
        /* Check that all the children have been executed */
        if (!hc_tree_get_father(action_node)) {
            break;
        }

        node = hc_tree_get_first_child(hc_tree_get_father(action_node));
        while (node) {
            status = ((hc_action_t*)hc_tree_get_datas(node))->status;
            if ( (status != ACTION_STATUS_EXECUTED)
                 && (status != ACTION_STATUS_FAILED) ) {
                /* One child has not been executed */
                break;
            }
            node = hc_tree_get_next_brother(node);
        }

        if (!node) {
            /* All the brother have been executed */
            hc_list_add_element(sequence->ready_actions,
                                hc_tree_get_father(action_node));
        }
        break;
    }
}

static void
fill_queue_with_last_actions(hc_tree_opaque_t *root,
                             hc_list_opaque_t *ready_actions)
{
    hc_tree_opaque_t *child = NULL;
    hc_tree_opaque_t *brother = NULL;

    child = hc_tree_get_first_child(root);
    brother = hc_tree_get_next_brother(root);

    if (child) {
        fill_queue_with_last_actions(child, ready_actions);
    } else {
        /* There is no child : This is a leaf that has to be put in the queue */
        hc_list_add_element(ready_actions, root);
    }

    if (brother) {
        fill_queue_with_last_actions(brother, ready_actions);
    }
}

/*
 * API implementation
 */

hc_sequence_t *
hc_sequence_allocate(hc_sequence_execution_t execution,
                     hc_tree_opaque_t *service_node,
                     hc_service_domain_t domain,
                     hc_service_filter_t filter,
                     hc_tree_opaque_t *actions,
                     hc_sequence_callback_t callback,
                     void *callback_cookie)
{
    hc_sequence_t *result = NULL;
    hc_tree_opaque_t *action_node;

    result = (hc_sequence_t*)malloc(sizeof(hc_sequence_t));
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "malloc failed");
        return(NULL);
    }

    result->ready_actions = hc_list_allocate(NULL);
    if (!result->ready_actions) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_list_allocate failed");
        free(result);
        return(NULL);
    }

    result->status = SEQUENCE_STATUS_RUNNING;
    result->execution = execution;

    result->service_node = service_node;
    result->domain = domain;
    result->filter = filter;

    result->actions = actions;
    result->nb_waiting_actions = 0;

    result->first_run = 1;
    result->had_failures = 0;

    result->callback = callback;
    result->callback_cookie = callback_cookie;

    /* Fill the ready_actions queue */

    switch (result->execution) {
    case HC_SEQUENCE_EXECUTION_NORMAL:
        action_node = actions;
        while (action_node) {
            hc_list_add_element(result->ready_actions,
                                action_node);
            action_node = hc_tree_get_next_brother(action_node);
        }
        break;

    case HC_SEQUENCE_EXECUTION_BOTTOMUP:
        fill_queue_with_last_actions(result->actions, result->ready_actions);
        break;
    }

    return(result);
}

void
hc_sequence_free(hc_sequence_t *sequence)
{
    if (sequence->status == SEQUENCE_STATUS_RUNNING) {
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Freeing a sequence which is in the SEQUENCE_STATUS_RUNNING state");
    }

    hc_list_free(sequence->ready_actions, NULL);
    sequence->ready_actions = NULL;

    sequence->actions = NULL;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "A sequence has been freed");

    free(sequence);
}

void
hc_sequence_free_actions(hc_tree_opaque_t *actions)
{
    hc_tree_opaque_t *brother = NULL;
    hc_tree_opaque_t *child = NULL;
    hc_action_t *action = NULL;

    brother = hc_tree_get_next_brother(actions);
    child = hc_tree_get_first_child(actions);

    action = (hc_action_t*)hc_tree_get_datas(actions);
    hc_tree_set_datas(actions, NULL);
    hc_action_free(action);
    hc_tree_remove(actions);
    hc_tree_free(actions);

    if (child) {
        hc_sequence_free_actions(child);
    }

    if (brother) {
        hc_sequence_free_actions(brother);
    }
}

void
hc_sequence_simple_free(void *datas)
{
    hc_sequence_t *sequence = (hc_sequence_t*)datas;

    if (sequence->actions) {
        hc_sequence_free_actions(sequence->actions);
    }
    hc_sequence_free(datas);
}

int
hc_sequence_run(hc_sequence_t *sequence)
{
    int nb_actions_executed = 0;
    hc_tree_opaque_t *action_node = NULL;
    hc_action_t *action = NULL;

    if (sequence->status == SEQUENCE_STATUS_COMPLETED) {
        return(0);
    }

    if (sequence->first_run) {
        sequence->first_run = 0;
        if (sequence->callback) {
            sequence->callback(HC_SEQUENCE_EVENT_STARTED,
                               sequence->callback_cookie);
        }
    }

    while ((action_node = 
            (hc_tree_opaque_t*)hc_list_extract_first_element(sequence->ready_actions))) {
        action = (hc_action_t*)hc_tree_get_datas(action_node);
        hc_action_execute(action);
        ++nb_actions_executed;

        switch (action->status) {
        case ACTION_STATUS_WAITING:
            sequence->nb_waiting_actions++;
            break;

        case ACTION_STATUS_FAILED:
            /*
             * We dont't execute the actions that depend on that one when
             * it has failed ... except in the
             * HC_SEQUENCE_EXECUTION_BOTTOMUP case
             */
            sequence->had_failures = 1;
            if (sequence->execution == HC_SEQUENCE_EXECUTION_BOTTOMUP) {
                schedule_next(sequence, action_node);
            }
            break;

        case ACTION_STATUS_EXECUTED:
            /* Schedule the next actions */
            schedule_next(sequence, action_node);
            break;

        default:
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Found an action with an inappropriate status [%d]",
                     action->status);
        }
    }

    if ((!hc_list_get_first_element(sequence->ready_actions))
        && (sequence->nb_waiting_actions == 0)) {
        sequence->status = SEQUENCE_STATUS_COMPLETED;
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "A sequence is moving to the SEQUENCE_STATUS_COMPLETED state");
        if (sequence->callback) {
            sequence->callback(HC_SEQUENCE_EVENT_COMPLETED,
                               sequence->callback_cookie);
        }
    }
    
    return(nb_actions_executed);
}

void
hc_sequence_stop_waiting(hc_sequence_t *sequence,
                         hc_tree_opaque_t *action_node,
                         int waiting_successful)
{
    hc_action_t *action = NULL;
    
    if (!sequence) {
        sequence = hc_scheduler_get_running_sequence();
        if (!sequence) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't retrieve the current running sequence");
            return;
        }
    }

    action = (hc_action_t*)hc_tree_get_datas(action_node);
    
    if (waiting_successful) {
        /* The action has to be rescheduled */
        action->status = ACTION_STATUS_EXECUTED;
        schedule_next(sequence, action_node);
    } else {
        /* The action failed */
        action->status = ACTION_STATUS_FAILED;
        sequence->had_failures = 1;
        if (sequence->execution == HC_SEQUENCE_EXECUTION_BOTTOMUP) {
            schedule_next(sequence, action_node);
        }
    }
    sequence->nb_waiting_actions--;
}


void
hc_sequence_disable_service(hc_service_t *service)
{
    if (service->mailbox_id != MB_INVALID_ID) {
        mb_setstate(service->mailbox_id,
                    SRV_DISABLED);
        mb_setexpectedstate(service->mailbox_id,
                            SRV_INVALID);
    }

    if (service->waiting_action_node) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Cancelling an action for the service %s",
                 service->start_cmd);
        hc_sequence_stop_waiting(NULL,
                                 service->waiting_action_node,
                                 0);
        service->waiting_action_node = NULL;
    }
}
