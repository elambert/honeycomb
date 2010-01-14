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
 * This file contains the inteface with the sequence management module
 */

#ifndef _SEQUENCE_H_
#define _SEQUENCE_H_

#include "tree.h"
#include "list.h"
#include "config.h"

typedef enum {
    SEQUENCE_STATUS_UNKNOWN =0,
    SEQUENCE_STATUS_RUNNING,
    SEQUENCE_STATUS_COMPLETED
} sequence_status_t;

typedef enum {
    HC_SEQUENCE_EXECUTION_NORMAL =1,
    HC_SEQUENCE_EXECUTION_BOTTOMUP
} hc_sequence_execution_t;

typedef enum {
    HC_SEQUENCE_EVENT_STARTED =1,
    HC_SEQUENCE_EVENT_COMPLETED
} hc_sequence_event_t;

typedef void (*hc_sequence_callback_t)(hc_sequence_event_t event,
                                       void *cookie);

typedef struct {
    sequence_status_t status;
    hc_sequence_execution_t execution;

    hc_tree_opaque_t *service_node;
    hc_service_domain_t domain;
    hc_service_filter_t filter;

    hc_tree_opaque_t *actions;
    hc_list_opaque_t *ready_actions;
    int nb_waiting_actions;

    int first_run;
    int had_failures;
    
    hc_sequence_callback_t callback;
    void *callback_cookie;
} hc_sequence_t;

/*
 * Allocate / free don't deal with the tree of actions. Once can use the
 * hc_sequence_free_actions to free the tree of actions before calling
 * hc_sequence_free.
 */

hc_sequence_t *hc_sequence_allocate(hc_sequence_execution_t execution,
                                    hc_tree_opaque_t *service_node,
                                    hc_service_domain_t domain,
                                    hc_service_filter_t filter,
                                    hc_tree_opaque_t *actions,
                                    hc_sequence_callback_t callback,
                                    void *cookie);



void hc_sequence_free(hc_sequence_t *sequence);

void hc_sequence_free_actions(hc_tree_opaque_t *actions);

/*
 * hc_simple_sequence_free can be used to free a simple sequence.
 *
 * It calls hc_sequence_free_actions and hc_sequence_free
 */

void hc_sequence_simple_free(void *datas);

/*
 * hc_sequence_run returns the number of actions that have be executed during
 * the last run.
 */

int hc_sequence_run(hc_sequence_t *sequence);

/*
 * The following routine is for waiting actions, when the wait is over.
 *
 * If the sequence parameter is NULL, then the current sequence is used.
 */

void hc_sequence_stop_waiting(hc_sequence_t *sequence,
                              hc_tree_opaque_t *action_node,
                              int waiting_successful);

void hc_sequence_disable_service(hc_service_t *service);

#endif /* _SEQUENCE_H_ */
