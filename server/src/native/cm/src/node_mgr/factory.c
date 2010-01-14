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

#include "factory.h"
#include "action.h"

/*
 * Private types
 */

typedef struct {
    hc_tree_opaque_t *actions;
    hc_service_filter_t filter;
    hc_sequence_execution_t execution_model;
    sequence_creator_callback_t callback;
    void *params;
} factory_walker_t;

/*
 * Private routines
 */

static void
create_sequence_from_services_callback(hc_tree_opaque_t *service_node,
                                       void *cookie,
                                       void *anchor,
                                       void **brother_anchor,
                                       void **child_anchor)
{
    factory_walker_t *args = (factory_walker_t*)cookie;
    hc_tree_opaque_t *root_action = NULL;
    hc_tree_opaque_t *created_leaf = NULL;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(service_node);

    if (service_matches_mask(service->filter, args->filter)) {
        /* The service matches the filter we are building against */

        service->nb_ongoing_operations++;
/*         cm_trace(CM_TRACE_LEVEL_DEBUG, */
/*                  "Increasing the nb_ongoing_operations for %s [%d]", */
/*                  service->start_cmd, */
/*                  service->nb_ongoing_operations); */
    
        if (!anchor) {
            /* This is the first execution */
            root_action = args->actions;
        } else {
            root_action = (hc_tree_opaque_t*)anchor;
        }

        args->callback(root_action,
                       service_node,
                       &created_leaf,
                       args->execution_model,
                       args->params);

        if (!created_leaf) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "created leaf is NULL !");
        }

        (*brother_anchor) = (void*)root_action;
        (*child_anchor) = (void*)created_leaf;
    }
}

/*
 * API implementation
 */

hc_sequence_t *
create_sequence_from_services(hc_tree_opaque_t *services,
                              hc_service_domain_t domain,
                              hc_service_filter_t filter,
                              sequence_creator_callback_t callback,
                              hc_sequence_execution_t execution_model,
                              hc_sequence_callback_t sequence_callback,
                              void *callback_cookie,
                              void *creator_params)
{
    hc_sequence_t *result = NULL;
    hc_tree_opaque_t *actions = NULL;
    hc_tree_opaque_t *child = NULL;
    factory_walker_t walker_args;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Creating a new sequence");

    actions = hc_tree_allocate();
    if (!actions) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        return(NULL);
    }

    walker_args.actions = actions;
    walker_args.filter = filter;
    walker_args.execution_model = execution_model;
    walker_args.callback = callback;
    walker_args.params = creator_params;

    hc_config_walker(services, domain,
                     create_sequence_from_services_callback, (void*)&walker_args);

    if (hc_tree_get_next_brother(actions)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The root action has some brothers in create_sequence_from_services");
    }

    child = hc_tree_get_first_child(actions);
    hc_tree_remove(actions);
    hc_tree_free(actions);
    
    if (!child) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "A new created sequence would be empty");
        return(NULL);
    }

    result = hc_sequence_allocate(execution_model, services, domain, filter, child,
                                  sequence_callback, callback_cookie);
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "hc_sequence_allocate failed");
        hc_sequence_free_actions(child);
        return(NULL);
    }

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "A new sequence has been created");

    return(result);
}

int
start_components_creator(hc_tree_opaque_t *root,
                         hc_tree_opaque_t *service_node,
                         hc_tree_opaque_t **created_leaf,
                         hc_sequence_execution_t execution_model,
                         void *creator_params)
{
    hc_action_t *action = NULL;
    hc_tree_opaque_t *action_node = NULL;
    action_service_arg_t *fork_args = NULL;
    int err;

    /*
     * Allocate resources
     */

    fork_args = (action_service_arg_t*)malloc(sizeof(action_service_arg_t));

    if (!fork_args) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Not enough memory in start_components_creator");
        if (fork_args) free(fork_args);
        return(1);
    }

    /*
     * Creating the first action which forks the component
     */

    action = hc_action_allocate(ACTION_TYPE_FORK_SERVICE,
                                action_param_simple_free);
    if (!action) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_action_allocate failed");
        free(fork_args);
        return(1);
    }
    
    action_node = hc_tree_allocate();
    if (!action_node) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        hc_action_free(action);
        return(1);
    }

    fork_args->service_node = service_node;
    action->params = fork_args;

    hc_tree_set_datas(action_node, action);
    hc_tree_add_child(root, action_node);

    (*created_leaf) = action_node;

    /*
     * Creating the actions to change state
     */

    err = state_change_creator((*created_leaf), service_node, created_leaf,
                               execution_model, (void*)SRV_READY);
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "state_change_creator failed");
        free(fork_args);
    }

    return(err);
}

int
stop_components_creator(hc_tree_opaque_t *root,
                        hc_tree_opaque_t* service_node,
                        hc_tree_opaque_t **created_leaf,
                        hc_sequence_execution_t execution_model,
                        void *creator_params)
{
    hc_action_t *action = NULL;
    hc_tree_opaque_t *action_node = NULL;
    action_service_arg_t *kill_args = NULL;
    int err;
    
    /*
     * Allocate resources
     */

    kill_args = (action_service_arg_t*)malloc(sizeof(action_service_arg_t));

    if (!kill_args) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Not enough memory in stop_components_creator");
        if (kill_args) free (kill_args);
        return(1);
    }

    /*
     * Creating the first action which kills the component
     */

    action = hc_action_allocate(ACTION_TYPE_KILL_SERVICE,
                                action_param_simple_free);
    if (!action) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_action_allocate failed");
        free (kill_args);
        return(1);
    }
    
    action_node = hc_tree_allocate();
    if (!action_node) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        hc_action_free(action);
        return(1);
    }

    kill_args->service_node = service_node;
    action->params = kill_args;

    hc_tree_set_datas(action_node, action);
    hc_tree_add_child(root, action_node);

    (*created_leaf) = action_node;

    /*
     * Creating the actions which change the state
     */

    err = state_change_creator((*created_leaf), service_node, created_leaf,
                               execution_model, (void*)SRV_READY);
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "state_change_creator failed");
        free(kill_args);
    }

    return(0);
}

int
state_change_creator(hc_tree_opaque_t *root,
                     hc_tree_opaque_t *service_node,
                     hc_tree_opaque_t **created_leaf,
                     hc_sequence_execution_t execution_model,
                     void *params)
{
    hc_action_t *action = NULL;
    hc_tree_opaque_t *change_action_node = NULL;
    hc_tree_opaque_t *waitfor_action_node = NULL;
    action_change_state_arg_t *change_state_args = NULL;
    action_service_arg_t *waitfor_state_args = NULL;
    mb_state_t final_state = (mb_state_t)params;

    /*
     * Allocating the resources
     */

    change_state_args = (action_change_state_arg_t*)malloc(sizeof(action_change_state_arg_t));
    waitfor_state_args = (action_service_arg_t*)malloc(sizeof(action_service_arg_t));

    if ((!change_state_args)
        || (!waitfor_state_args)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Failed to allocate resources in state_change_creator");
        if (change_state_args) free(change_state_args);
        if (waitfor_state_args) free(waitfor_state_args);
        return(1);
    }

    /*
     * Creating the first action which requests a state change
     */

    action = hc_action_allocate(ACTION_TYPE_CHANGE_STATE,
                                action_param_simple_free);
    if (!action) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_action_allocate failed");
        free(change_state_args);
        free(waitfor_state_args);
        return(1);
    }
    
    change_action_node = hc_tree_allocate();
    if (!change_action_node) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        hc_action_free(action);
        return(1);
    }
    
    change_state_args->service_node = service_node;
    change_state_args->action_node = change_action_node;
    change_state_args->expected_state = final_state;
    action->params = change_state_args;

    hc_tree_set_datas(change_action_node, action);

    /*
     * Creating the second action which waits or the completion of the state
     * change
     */

    action = hc_action_allocate(ACTION_TYPE_WAITFOR_STATE,
                                action_param_simple_free);
    if (!action) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_action_allocate failed");
        free(change_state_args);
        free(waitfor_state_args);
        return(1);
    }
    
    waitfor_action_node = hc_tree_allocate();
    if (!waitfor_action_node) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        hc_action_free(action);
        return(1);
    }
    
    waitfor_state_args->service_node = service_node;
    action->params = waitfor_state_args;

    hc_tree_set_datas(waitfor_action_node, action);

    switch (execution_model) {
    case HC_SEQUENCE_EXECUTION_NORMAL:
        hc_tree_add_child(root, change_action_node);
        hc_tree_add_child(change_action_node, waitfor_action_node);
        (*created_leaf) = waitfor_action_node;
        break;
	
    case HC_SEQUENCE_EXECUTION_BOTTOMUP:
        hc_tree_add_child(root, waitfor_action_node);
        hc_tree_add_child(waitfor_action_node, change_action_node);
        (*created_leaf) = change_action_node;
        break;

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Unknown execution model [%d]",
                 execution_model);
        return(1);
    }

    return(0);    
}
