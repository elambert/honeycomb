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
 * This is a unit test for the list implementation
 */

#include <stdlib.h>
#include <trace.h>

#include "list.h"

static int
test_comparison(void *elem1,
		void *elem2)
{
    char *s1 = (char*)elem1;
    char *s2 = (char*)elem2;

    if ((*s1) == (*s2)) {
	return(0);
    }

    return((*s1)<(*s2) ? -1 : 1);
}

static int
check(hc_list_opaque_t *list,
      char *check_string)
{
    char *elem = NULL;
    int i;

    for (i=0, elem=(char*)hc_list_extract_first_element(list);
	 elem;
	 i++, elem=(char*)hc_list_extract_first_element(list)) {
	if ((*elem) != check_string[i]) {
	    cm_trace(CM_TRACE_LEVEL_ERROR,
		     "Check failed !!! Expected %c, got %c",
		   check_string[i],
		   (*elem));
	    return(1);
	}
    }

    return(0);
}

static int
fifo_test()
{
    hc_list_opaque_t *list = NULL;
    int res;

    list = hc_list_allocate(NULL);
    if (!list) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "hc_list_allocate failed");
	return(1);
    }

    hc_list_add_element(list, "1");
    hc_list_add_element(list, "2");
    hc_list_add_element(list, "3");
    hc_list_add_element(list, "4");
    (void*)hc_list_extract_first_element(list);

    res = check(list, "234");
    hc_list_free(list, NULL);

    return(res);
}

static int
ordered_test()
{
    hc_list_opaque_t *list = NULL;
    int res;

    list = hc_list_allocate(test_comparison);
    if (!list) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "hc_list_allocate failed");
	return(1);
    }

    hc_list_add_element(list, "3");
    hc_list_add_element(list, "1");
    hc_list_add_element(list, "4");
    hc_list_add_element(list, "2");
    (void*)hc_list_extract_first_element(list);

    res = check(list, "234");
    hc_list_free(list, NULL);

    return(res);
}

int
main()
{
    cm_openlog("test_list", CM_TRACE_LEVEL_NOTICE);

    if (fifo_test()) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "The FIFO test failed");
	cm_closelog();
	return(1);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
	     "The FIFO test passed");

    if (ordered_test()) {
	cm_trace(CM_TRACE_LEVEL_ERROR,
		 "The ordered test failed");
	cm_closelog();
	return(1);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
	     "The ordered list test passed");
    
    cm_trace(CM_TRACE_LEVEL_NOTICE,
	     "The list test passed");

    cm_closelog();

    return(0);
}

    
    
    
