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
 * Unit test for the config.c file
 */

#include <stdlib.h>
#include <string.h>
#include <trace.h>

#include "config.h"

#define TEST_FILE "config_test.xml"

static int test_nb =0;

/*
 * Implementation of the probe routine
 */

void
config_test_probe(hc_tree_opaque_t *root_node)
{
    hc_service_t *new_service = NULL;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "The probe routine is called");

    new_service = hc_service_create_new(root_node,
                                        NULL,
                                        HC_SERVICE_PROCESS,
                                        HC_FILTER_GROUP_USER | HC_FILTER_NODE_ANYNODE,
                                        1,
                                        "probed",
                                        NULL,
                                        NULL,
                                        0, 0,
                                        "probeMailbox", 1);
    if (!new_service) {
        return;
    }
}

static int
check(hc_tree_opaque_t *node,
      char *start_cmd)
{
    hc_service_t *service = NULL;

    ++test_nb;

    if (!node) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Test %d failed : node is NULL !",
                 test_nb);
        return(1);
    }

    service = (hc_service_t*)hc_tree_get_datas(node);

    if (strcmp(service->start_cmd, start_cmd)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Test %d failed. Expected %s, got %s",
                 test_nb, start_cmd, service->start_cmd);
        return(1);
    }

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "Test %d passed", test_nb);

    return(0);
}

int
main()
{
    hc_tree_opaque_t *config = NULL;
    hc_tree_opaque_t *cur_node = NULL;

    cm_openlog("test_config", CM_TRACE_LEVEL_NOTICE);

    config = hc_config_parse_file(TEST_FILE);
    if (!config) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_config_parse_file failed");
        cm_closelog();
        return(1);
    }

    cur_node = config;
    if (check(cur_node, "1")) {
        cm_closelog();
        return(1);
    }
    
    cur_node = hc_tree_get_next_brother(cur_node);
    if (check(cur_node, "2")) {
        cm_closelog();
        return(1);
    }

    cur_node = hc_tree_get_first_child(cur_node);
    if (check(cur_node, "3")) {
        cm_closelog();
        return(1);
    }

    cur_node = hc_tree_get_next_brother(cur_node);
    if (check(cur_node, "4")) {
        cm_closelog();
        return(1);
    }

    cur_node = hc_tree_get_next_brother(hc_tree_get_father(cur_node));
    if (check(cur_node, "probed")) {
        cm_closelog();
        return(1);
    }

    hc_config_free_config(config);

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "The config tests passed");

    cm_closelog();
    return(0);
}
    
