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
 * This is the unit test for the sequence implementation
 */

#include <trace.h>
#include <stdlib.h>

#include "sequence.h"
#include "action.h"

#define NB_ACTIONS 4

static int test_nb = 0;

typedef struct {
    int started;
    int completed;
} sequence_callback_data_t;

static void
sequence_callback(hc_sequence_event_t event,
                  void *data)
{
    sequence_callback_data_t *args = (sequence_callback_data_t*)data;

    switch (event) {
    case HC_SEQUENCE_EVENT_STARTED:
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "The start sequence callback has been called");
        args->started = 1;
        break;

    case HC_SEQUENCE_EVENT_COMPLETED:
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "The completed sequence callback has been called");
        args->completed = 1;
        break;
    }
}

static int
check(int nb_actions_executed,
      int expected_nb_actions_executed,
      int test_int[],
      char *expected_int,
      sequence_status_t sequence_status,
      sequence_status_t expected_sequence_status)
{
    int i;

    ++test_nb;

    if (nb_actions_executed != expected_nb_actions_executed) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The number of executed actions is incorrect [%d-%d]",
                 nb_actions_executed,
                 expected_nb_actions_executed);
        return(1);
    }

    for (i=0; i<NB_ACTIONS; i++) {
        if (test_int[i] != expected_int[i]-'1'+1) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "The array of ints is incorrect at %d [%d-%c]",
                     i, test_int[i], expected_int[i]);
            return(1);
        }
    }

    if (sequence_status != expected_sequence_status) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "The sequence status is incorrect [%d-%d]",
                 sequence_status,
                 expected_sequence_status);
        return(1);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "The test %d passed",
             test_nb);

    return(0);
}	

int
main()
{
    hc_action_t *actions[NB_ACTIONS];
    hc_tree_opaque_t *action_nodes[NB_ACTIONS];
    hc_sequence_t *sequence = NULL;
    action_test_arg_t test_arg[NB_ACTIONS];
    int test_int[NB_ACTIONS];
    int i;
    sequence_callback_data_t callback_data;

    cm_openlog("test sequence", CM_TRACE_LEVEL_NOTICE);

    /* Create the actions */

    for (i=0; i<NB_ACTIONS; i++) {
        actions[i] = hc_action_allocate(ACTION_TYPE_TEST,
                                        NULL);
        if (!actions[i]) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "hc_action_allocate failed");
            return(1);
        }
        test_arg[i].should_fail = 0;
        test_arg[i].should_wait = 0;
        test_int[i] = 0;
        test_arg[i].test_arg = test_int+i;

        actions[i]->params = test_arg+i;

        action_nodes[i] = hc_tree_allocate();
        if (!action_nodes[i]) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "hc_tree_allocate failed");
            return(1);
        }
        hc_tree_set_datas(action_nodes[i], actions[i]);
    }

    /* Test specific code */

    test_arg[1].should_wait = 1;
    callback_data.started = 0;
    callback_data.completed = 0;

    hc_tree_add_child(action_nodes[0], action_nodes[2]);
    hc_tree_add_child(action_nodes[0], action_nodes[1]);
    hc_tree_add_child(action_nodes[1], action_nodes[3]);

    /*
     * HC_SEQUENCE_EXECUTION_NORMAL tests
     */

    sequence = hc_sequence_allocate(HC_SEQUENCE_EXECUTION_NORMAL,
                                    NULL,
                                    HC_DOMAIN_ALL,
                                    HC_FILTER_GROUP_ALL | HC_FILTER_NODE_ANYNODE,
                                    action_nodes[0],
                                    sequence_callback, &callback_data);
    if (!sequence) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_sequence_allocate failed");
        return(1);
    }

    i = hc_sequence_run(sequence);
    if (check(i, 3, test_int, "1110", sequence->status, SEQUENCE_STATUS_RUNNING)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Test %d failed",
                 test_nb);
        return(1);
    }

    if (!callback_data.started) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The start callback hasn't been called");
        return(1);
    }

    i = hc_sequence_run(sequence);
    if (check(i, 0, test_int, "1110", sequence->status, SEQUENCE_STATUS_RUNNING)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Test %d failed",
                 test_nb);
        return(1);
    }

    if (actions[1]->status != ACTION_STATUS_WAITING) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Action 4 is not waiting");
        return(1);
    }

    hc_sequence_stop_waiting(sequence,
                             action_nodes[1],
                             1);
    i = hc_sequence_run(sequence);
    if (check(i, 1, test_int, "1111", sequence->status, SEQUENCE_STATUS_COMPLETED)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Test %d failed",
                 test_nb);
        return(1);
    }
    
    if (!callback_data.completed) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The start callback hasn't been called");
        return(1);
    }

    hc_sequence_free(sequence);

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "All the HC_SEQUENCE_EXECUTION_NORMAL tests passed");

    /*
     * HC_SEQUENCE_EXECUTION_BOTTOMUP tests
     */

    for (i=0; i<NB_ACTIONS; i++) {
        actions[i]->status = ACTION_STATUS_READY;
        test_int[i] = 0;
    }

    callback_data.started = 0;
    callback_data.completed = 0;

    sequence = hc_sequence_allocate(HC_SEQUENCE_EXECUTION_BOTTOMUP,
                                    NULL,
                                    HC_DOMAIN_ALL,
                                    HC_FILTER_GROUP_ALL | HC_FILTER_NODE_ANYNODE,
                                    action_nodes[0],
                                    sequence_callback, &callback_data);
    if (!sequence) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_sequence_allocate failed");
        return(1);
    }

    i = hc_sequence_run(sequence);
    if (check(i, 3, test_int, "0111", sequence->status, SEQUENCE_STATUS_RUNNING)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Test %d failed",
                 test_nb);
        return(1);
    }
    
    if (!callback_data.started) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The start callback hasn't been called");
        return(1);
    }

    hc_sequence_stop_waiting(sequence,
                             action_nodes[1],
                             0);
    i = hc_sequence_run(sequence);
    if (check(i, 1, test_int, "1111", sequence->status, SEQUENCE_STATUS_COMPLETED)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Test %d failed",
                 test_nb);
        return(1);
    }

    if (!callback_data.completed) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The start callback hasn't been called");
        return(1);
    }

    hc_sequence_free_actions(sequence->actions);
    hc_sequence_free(sequence);

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "All the HC_SEQUENCE_EXECUTION_BOTTOMUP tests passed");

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "All the tests passed");

    return(0);
}
