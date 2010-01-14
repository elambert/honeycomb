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
 * This file defines the actions within a sequence
 */

#ifndef _ACTION_H_
#define _ACTION_H_

#include "config.h"
#include "tree.h"

typedef enum {
    ACTION_STATUS_UNKNOWN =0,
    ACTION_STATUS_READY,
    ACTION_STATUS_WAITING,
    ACTION_STATUS_FAILED,
    ACTION_STATUS_EXECUTED
} action_status_t;
    
typedef enum {
    ACTION_TYPE_TEST =1,
    ACTION_TYPE_FORK_SERVICE,
    ACTION_TYPE_KILL_SERVICE,
    ACTION_TYPE_CHANGE_STATE,
    ACTION_TYPE_WAITFOR_STATE
} action_type_t;

typedef void (*action_param_free_t)(void *params);

typedef struct {
    action_type_t type;
    action_status_t status;
    void *params;
    action_param_free_t param_free;
} hc_action_t;

void
action_param_simple_free(void *params);

/*
 * The following routine allocate an action structure. The params are not
 * allocated and this is the responsability of the caller to allocate it.
 *
 * To free it, one can either use the param_free callback or free it
 * himself.
 */

hc_action_t *hc_action_allocate(action_type_t type,
				action_param_free_t param_free);

void hc_action_free(hc_action_t *action);

void hc_action_execute(hc_action_t *action);

/*
 * The declarations above define the format of the action parameters
 */

typedef struct {
    int should_fail;
    int should_wait;
    int *test_arg;
} action_test_arg_t;

typedef struct {
    hc_tree_opaque_t *service_node;
} action_service_arg_t;

typedef struct {
    hc_tree_opaque_t *action_node;
    hc_tree_opaque_t *service_node;
    mb_state_t expected_state;
} action_change_state_arg_t;

#endif /* _ACTION_H_ */
