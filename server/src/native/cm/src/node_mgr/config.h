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
 * This file contains the code needed to parse the node configuration file
 */

#ifndef _CONFIG_H_
#define _CONFIG_H_

#include <sys/types.h>
#include <time.h>
#include <mbox.h>

#include "tree.h"

extern char *mbox_state_strings[];

typedef enum {
    HC_SERVICE_UNKNOWN =0,
    HC_SERVICE_PROCESS,
    HC_SERVICE_JVM,
    HC_SERVICE_JAVA_SERVICE,
    HC_SERVICE_PROBE
} hc_service_type_t;

typedef enum {
    HC_DOMAIN_ALL =1,
    HC_DOMAIN_PROCESS,
    HC_DOMAIN_CHILDREN
} hc_service_domain_t;

#define HC_FILTER_NONE           0x00
#define HC_FILTER_GROUP_SYSTEM   0x01
#define HC_FILTER_GROUP_USER     0x02
#define HC_FILTER_GROUP_ALL      0x0F

#define HC_FILTER_NODE_MASTER    0x10
#define HC_FILTER_NODE_ANYNODE   0x20
#define HC_FILTER_NODE_ALL       0xF0

typedef unsigned char hc_service_filter_t;

int service_matches_mask(hc_service_filter_t service_filter,
                         hc_service_filter_t mask);

typedef struct {
    hc_service_type_t service_type;
    hc_service_filter_t filter;
    unsigned int heartbeat;
    pid_t pid;
    int dialog_pipe;

    char *start_cmd;
    char **envp;
    char *arg;

    int restart_nb;
    int restart_current;
    time_t restart_window;
    time_t *restart_times;
    int locked;
    int nb_ongoing_operations;

    char *mailbox_tag;
    size_t mailbox_size;
    mb_id_t mailbox_id;

    hc_tree_opaque_t *waiting_action_node;
} hc_service_t;

hc_tree_opaque_t *
hc_config_parse_file(char *filename);

void
hc_config_free_config(hc_tree_opaque_t *config);

/*
 * The following routines are for the probe routines
 */

typedef void (*hc_config_probe_t)(hc_tree_opaque_t *root_node, const char* arg);

/*
 * This routine creates a complete service entry. It attaches it to the
 * service tree and allocate some internal structures.
 *
 * This call should be called from the probed routines
 *
 * The call returns a pointer to the service and if new_node is not NULL,
 * sets it to the created node to host the service.
 */

hc_service_t *
hc_service_create_new(hc_tree_opaque_t *root_node,
                      hc_tree_opaque_t **new_node,
                      hc_service_type_t service_type,
                      hc_service_filter_t filter,
                      unsigned int heartbeat,
                      char *start_cmd,
                      char *env,
                      char *arg,
                      int restart_nb,
                      time_t restart_window,
                      char *mailbox_tag,
                      size_t mailbox_size);

void
hc_config_lock(hc_tree_opaque_t *service_node,
               hc_service_domain_t domain,
               hc_service_filter_t filter,
               int locked);

/* Walker through the service */

typedef void (*walker_callback_t)(hc_tree_opaque_t *service_node,
                                  void *cookie,
                                  void *anchor,
                                  void **brother_anchor,
                                  void **child_anchor);

void hc_config_walker(hc_tree_opaque_t *service_node,
                      hc_service_domain_t domain,
                      walker_callback_t walker_callback,
                      void *cookie);

#endif /* _CONFIG_H_ */
