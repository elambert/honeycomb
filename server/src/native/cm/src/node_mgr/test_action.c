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
 * This is the unit test for the action module
 */

#include <trace.h>
#include <stdlib.h>

#include "action.h"

static int test_nb = 0;

static int
check(int should_fail,
      hc_action_t *action)
{
    action_test_arg_t *args = (action_test_arg_t*)action->params;
    int test;

    ++test_nb;
    
    args->test_arg = &test;
    args->should_fail = should_fail;
    args->should_wait = 0;
    test = 0;

    hc_action_execute(action);

    if (test != 1) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "The test_arg parameter has not been reset");
	return(1);
    }

    if (should_fail) {
	if (action->status != ACTION_STATUS_FAILED) {
	    cm_trace(CM_TRACE_LEVEL_ERROR,
		     "The action status is incorrect. Expected %d, got %d",
		     ACTION_STATUS_FAILED,
		     action->status);
	    return(1);
	}
    } else {
	if (action->status != ACTION_STATUS_EXECUTED) {
	    cm_trace(CM_TRACE_LEVEL_ERROR,
		     "The action status is incorrect. Expected %d, got %d",
		     ACTION_STATUS_EXECUTED,
		     action->status);
	    return(1);
	}
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
	     "Test nb %d passed",
	     test_nb);
    
    return(0);
}

int
main()
{
    hc_action_t *action = NULL;
    action_test_arg_t args;

    cm_openlog("test action", CM_TRACE_LEVEL_NOTICE);

    action = hc_action_allocate(ACTION_TYPE_TEST, NULL);
    if (!action) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "hc_action_allocate failed");
	return(1);
    }

    action->params = &args;

    if (check(0, action)) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "Test %d failed",
		 test_nb);
	return(1);
    }

    if (check(1, action)) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "Test %d failed",
		 test_nb);
	return(1);
    }
    
    cm_trace(CM_TRACE_LEVEL_NOTICE,
	     "All the tests passed");

    hc_action_free(action);
    cm_closelog();

    return(0);
}
    
    
