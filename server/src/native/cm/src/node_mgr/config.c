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



#include <expat.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <malloc.h>
#include <string.h>
#include <dlfcn.h>
#include <trace.h>
#include <unistd.h>
#include <mbox.h>
#include <libgen.h>

#include "config.h"
#include "scheduler.h"
#include "cmm_interface.h"

#define XML_PARSER_BUF_SIZE 64

/*
 * Private type definitions
 */

typedef struct {
    int locked;
    hc_service_filter_t filter;
} hc_config_lock_walker_t;

/*
 * Global definitions
 */

/*
 * Private routines implementation
 */

static void
hc_service_set_default_values(hc_service_t *service)
{
    service->service_type = HC_SERVICE_UNKNOWN;
    service->filter = HC_FILTER_GROUP_USER | HC_FILTER_NODE_ANYNODE;
    service->heartbeat = 0;
    service->pid = -1;
    service->dialog_pipe = -1;

    service->start_cmd = NULL;
    service->envp = NULL;
    service->arg = NULL;

    service->restart_nb = 3;
    service->restart_current = 0;
    service->restart_window = 60;
    service->restart_times = NULL;
    service->locked = 1;
    service->nb_ongoing_operations = 0;

    service->mailbox_tag = NULL;
    service->mailbox_size = 0;
    service->mailbox_id = MB_INVALID_ID;

    service->waiting_action_node = NULL;
}

static hc_service_t *
hc_service_allocate_new(hc_tree_opaque_t *root_node,
                        hc_tree_opaque_t **p_new_node)
{
    hc_service_t *service = NULL;
    hc_tree_opaque_t *new_node = NULL;

    if (p_new_node) {
        (*p_new_node) = NULL;
    }

    new_node = hc_tree_allocate();
    if (!new_node) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        return(NULL);
    }

    service = (hc_service_t*)malloc(sizeof(hc_service_t));
    if (!service) {
        hc_tree_free(new_node);
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "malloc failed");
        return(NULL);
    }

    hc_tree_set_datas(new_node, service);
    hc_tree_add_child(root_node, new_node);
        
    hc_service_set_default_values(service);

    if (p_new_node) {
        (*p_new_node) = new_node;
    }

    return(service);
}

static void
hc_config_free_service(hc_service_t *service)
{
    if (service->start_cmd) {
        free(service->start_cmd);
        service->start_cmd = NULL;
    }

    /* Unlinking the mailbox */

    if (service->mailbox_id != MB_INVALID_ID) {
        (mb_error_t)mb_close(service->mailbox_id);
        service->mailbox_id = MB_INVALID_ID;
        (mb_error_t)mb_unlink(service->mailbox_tag);
    }

    if (service->mailbox_tag) {
        free(service->mailbox_tag);
        service->mailbox_tag = NULL;
    }
    if (service->envp) {
        if (service->envp[1]) {
            free(service->envp[1]);
        }
        free(service->envp);
        service->envp = NULL;
    }
    if (service->arg) {
        free(service->arg);
        service->arg = NULL;
    }
    if (service->restart_times) {
        free(service->restart_times);
        service->restart_times = NULL;
    }

    free(service);
}

static int
service_is_valid(hc_service_t *service)
{
    int validated = 1;

    switch (service->service_type) {
    case HC_SERVICE_PROBE:
        if (!service->start_cmd) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "The start_cmd property has to be defined for HC_SERVICE_PROBE services");
            validated = 0;
        }
        break;

    case HC_SERVICE_PROCESS:
    case HC_SERVICE_JVM:
        if (service->arg) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "The arg attribute is reserved for JAVA or PROBE services");
            validated = 0;
        }
        /* No break */

    case HC_SERVICE_JAVA_SERVICE:
        if ((validated)
            && ((!service->start_cmd)
                || (!service->heartbeat)
                || (!service->mailbox_tag)
                || (!service->mailbox_size))) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Either the start_cmd, hearbeat, mailbox_tag or mailbox_size haven't been defined (or set to 0)");
            validated = 0;
        }
        break;
	
    default:
        validated = 0;
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The service type %d is unexpected",
                 service->service_type);
    }

    if (!validated) {
        if (service->start_cmd) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "An invalid service has been detected [%s]",
                     service->start_cmd);
        } else {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "In invalid service has been detected [no start_cmd]");
        }
    }

    return(validated);
}

static void
execute_probe(hc_tree_opaque_t *root_node,
              hc_service_t *probe_service)
{
    void *dl_handle = NULL;
    hc_config_probe_t probe_routine;

    dl_handle = dlopen(NULL, RTLD_NOW);
    if (!dl_handle) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "dlopen failed");
        return;
    }

    probe_routine = (hc_config_probe_t)dlsym(dl_handle,
                                             probe_service->start_cmd);
    if (probe_routine) {
        probe_routine(root_node, probe_service->arg);
    } else {
        char *error = NULL;

        printf("probe_routine not found [%s]\n",
               probe_service->start_cmd);

        error = dlerror();
        if (error) {
            printf("%s\n", error);
        }
    }

    (int)dlclose(dl_handle);
}

static void
XML_tag_start(void *data,
              const XML_Char *name,
              const XML_Char **atts)
{
    hc_tree_opaque_t **current_node = (hc_tree_opaque_t**)data;
    hc_tree_opaque_t *new_node = NULL;
    hc_service_t service;
    char *env = NULL;
    int i;
    hc_service_filter_t group = HC_FILTER_GROUP_USER;
    hc_service_filter_t location = HC_FILTER_NODE_ANYNODE;

    if (!(*current_node)) {
        /* Previous parsing failure */
        return;
    }

    hc_service_set_default_values(&service);

    if (strcmp(name, "service")) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "A tag different than service has been encountered [%s]",
                 name);
        return;
    }

    for (i=0; atts[i]; i+=2) {
        if (!strcmp(atts[i], "type")) {
            if (!strcmp(atts[i+1], "PROCESS")) {
                service.service_type = HC_SERVICE_PROCESS;
            }
            if (!strcmp(atts[i+1], "JVM")) {
                service.service_type = HC_SERVICE_JVM;
            }
            if (!strcmp(atts[i+1], "JAVA_SERVICE")) {
                service.service_type = HC_SERVICE_JAVA_SERVICE;
            }
            if (!strcmp(atts[i+1], "PROBE")) {
                service.service_type = HC_SERVICE_PROBE;
            }
        }

        if (!strcmp(atts[i], "group")) {
            group = HC_FILTER_NONE;
            if (!strcmp(atts[i+1], "SYSTEM")) {
                group = HC_FILTER_GROUP_SYSTEM;
            }
            if (!strcmp(atts[i+1], "USER")) {
                group = HC_FILTER_GROUP_USER;
            }
        }

        if (!strcmp(atts[i], "location")) {
            location = HC_FILTER_NONE;
            if (!strcmp(atts[i+1], "ANY")) {
                location = HC_FILTER_NODE_ANYNODE;
            }
            if (!strcmp(atts[i+1], "MASTER")) {
                location = HC_FILTER_NODE_MASTER;
            }
        }            

        if (!strcmp(atts[i], "heartbeat")) {
            sscanf(atts[i+1], "%u", &service.heartbeat);
        }
        
        if (!strcmp(atts[i], "start_cmd")) {
            service.start_cmd = (char*)atts[i+1];
        }

        if (!strcmp(atts[i], "env")) {
            env = (char*)atts[i+1];
        }

        if (!strcmp(atts[i], "arg")) {
            service.arg = (char*)atts[i+1];
        }

        if (!strcmp(atts[i], "restart_nb")) {
            service.restart_nb = atoi(atts[i+1]);
        }

        if (!strcmp(atts[i], "restart_window")) {
            service.restart_window = atoi(atts[i+1]);
        }

        if (!strcmp(atts[i], "mailbox_tag")) {
            service.mailbox_tag = (char*)atts[i+1];
        }

        if (!strcmp(atts[i], "mailbox_size")) {
            service.mailbox_size = atoi(atts[i+1]);
        }
    }
    
    if (group == HC_FILTER_NONE) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The group attribute is invalid for %s",
                 service.start_cmd);
        exit(1);
    }

    if (location == HC_FILTER_NONE) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The location attribute is invalid for %s",
                 service.start_cmd);
        exit(1);
    }

    service.filter = group | location;

    if (hc_service_create_new((*current_node),
                              &new_node,
                              service.service_type,
                              service.filter,
                              service.heartbeat,
                              service.start_cmd,
                              env,
                              service.arg,
                              service.restart_nb,
                              service.restart_window,
                              service.mailbox_tag,
                              service.mailbox_size) == NULL) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "XML_tag_start is failing because an invalid service has been detected. Exiting ...");
        exit(1);
    }

    (*current_node) = new_node;
}

static void
XML_tag_end(void *data,
            const XML_Char *name)
{
    hc_tree_opaque_t **current_node = (hc_tree_opaque_t**)data;
    hc_tree_opaque_t *next_current_node = NULL;
    hc_service_t *service = NULL;

    if (!(*current_node)) {
        return;
    }

    service = (hc_service_t*)hc_tree_get_datas(*current_node);

    next_current_node = hc_tree_get_father(*current_node);

    /* Check if a probe has to be executed */
    if ((service)
        && (service->service_type == HC_SERVICE_PROBE)) {
        hc_tree_remove(*current_node);
        hc_tree_free(*current_node);
        (*current_node) = NULL;
        execute_probe(next_current_node, service);
        hc_config_free_service(service);
        service = NULL;
    }
	
    (*current_node) = next_current_node;
}

static void
hc_config_free_tree(hc_tree_opaque_t *node)
{
    hc_service_t *service = NULL;

    if (hc_tree_get_first_child(node)) {
        hc_config_free_tree(hc_tree_get_first_child(node));
    }

    if (hc_tree_get_next_brother(node)) {
        hc_config_free_tree(hc_tree_get_next_brother(node));
    }

    service = (hc_service_t*)hc_tree_get_datas(node);
    if (service) {
        hc_config_free_service(service);
    }
    hc_tree_set_datas(node, NULL);

    hc_tree_free(node);
}    

static void
hc_config_walker_rec(hc_tree_opaque_t *root,
                     hc_service_domain_t domain, 
                     walker_callback_t callback,
                     void *cookie,
                     void *anchor)
{
    hc_tree_opaque_t *child = NULL;
    hc_tree_opaque_t *brother = NULL;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(root);
    hc_service_domain_t next_domain;
    void *brother_anchor = NULL;
    void *child_anchor = NULL;

    callback(root, cookie, anchor, &brother_anchor, &child_anchor);

    child = hc_tree_get_first_child(root);
    brother = hc_tree_get_next_brother(root);
    
    next_domain = domain;

    switch (domain) {
    case HC_DOMAIN_ALL:
        /* The brother and the child are executed */
        break;

    case HC_DOMAIN_CHILDREN:
        /* Trigger only the child */
        next_domain = HC_DOMAIN_ALL;
        brother = NULL;
        break;

    case HC_DOMAIN_PROCESS:
        /* Trigger all the children which are HC_SERVICE_JAVA_SERVICE */
        brother = NULL;
        while (child) {
            service = (hc_service_t*)hc_tree_get_datas(child);
            if (service->service_type == HC_SERVICE_JAVA_SERVICE) {
                hc_config_walker_rec(child, domain, callback, cookie, child_anchor);
            }
            child = hc_tree_get_next_brother(child);
        }
        /* child is NULL */
        break;

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Unexpected domain type [%d]",
                 domain);
        return;
    }

    if (brother) {
        hc_config_walker_rec(brother, domain, callback, cookie, brother_anchor);
    }
    if (child) {
        hc_config_walker_rec(child, domain, callback, cookie, child_anchor);
    }
}

static void
hc_config_lock_walker(hc_tree_opaque_t *service_node,
                      void *cookie,
                      void *anchor,
                      void **brother_anchor,
                      void **child_anchor)
{
    hc_config_lock_walker_t *args = (hc_config_lock_walker_t*)cookie;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(service_node);

    if (service_matches_mask(service->filter, args->filter)) {
        service->locked = args->locked;
    }
}

static int
hc_config_group_sanity_check(hc_tree_opaque_t *service_node,
                             hc_service_filter_t filter)
{
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(service_node);
    hc_tree_opaque_t *next_node;

    if (!service_matches_mask(service->filter, filter)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "There is a filter sanity error for service %s",
                 service->start_cmd);
        return(1);
    }

    next_node = hc_tree_get_next_brother(service_node);
    if (next_node) {
        if (hc_config_group_sanity_check(next_node, filter)) {
            return(1);
        }
    }
    
    next_node = hc_tree_get_first_child(service_node);
    if (next_node) {
        
        /* Once a USER group is met, SYSTEM group is no longer allowed */
        if (service->filter & HC_FILTER_GROUP_USER) {
            filter &= ~HC_FILTER_GROUP_SYSTEM;
        }

        /* Once a MASTER service is met, only MASTER services are allowed */
        if (service->filter & HC_FILTER_NODE_MASTER) {
            filter = (filter & HC_FILTER_GROUP_ALL) | HC_FILTER_NODE_MASTER;
        }

        if (hc_config_group_sanity_check(next_node, filter)) {
            return(1);
        }
    }

    return(0);
}    

/*
 * API implementation
 */

int
service_matches_mask(hc_service_filter_t service_filter,
                     hc_service_filter_t mask)
{
    hc_service_filter_t service;
    hc_service_filter_t m;

    /* Check the group */
    service = service_filter & HC_FILTER_GROUP_ALL;
    m = mask & HC_FILTER_GROUP_ALL;

    if (!(service & mask)) {
        return(0);
    }

    /* Check the location */
    service = service_filter & HC_FILTER_NODE_ALL;
    m = mask & HC_FILTER_NODE_ALL;

    if (!(service & mask)) {
        return(0);
    }

    return(1);
}

hc_tree_opaque_t *
hc_config_parse_file(char *filename)
{
    char buffer[XML_PARSER_BUF_SIZE];
    int fd;
    int nb_read;
    XML_Parser parser;
    hc_tree_opaque_t *current_node = NULL;
    hc_tree_opaque_t *root_node = NULL;
    hc_tree_opaque_t *result = NULL;
    enum XML_Status status;

    root_node = hc_tree_allocate();
    if (!root_node) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_tree_allocate failed");
        return(NULL);
    }
    current_node = root_node;

    parser = XML_ParserCreate(NULL);
    if (!parser) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "XML_ParserCreate failed");
        return(NULL);
    }

    XML_SetUserData(parser, &current_node);
    
    XML_SetElementHandler(parser,
                          XML_tag_start,
                          XML_tag_end);

    fd = open(filename, O_RDONLY);
    if (fd == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "open failed");
        return(NULL);
    }

    while ((nb_read = read(fd, buffer, XML_PARSER_BUF_SIZE)) > 0) {
        status = XML_Parse(parser,
                           buffer,
                           nb_read,
                           0);
        if (status != XML_STATUS_OK) {
            hc_config_free_tree(root_node);
            XML_ParserFree(parser);
            close(fd);
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "XML_Parse failed [%d]",
                     status);
            return(NULL);
        }
    }

    close(fd);

    if (nb_read != 0) {
        /* There has been an error in the reading */
        hc_config_free_tree(root_node);
        XML_ParserFree(parser);
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "There has been an error reading the XML file");
        return(NULL);
    }

    status = XML_Parse(parser, NULL, 0, 1);
    if (status != XML_STATUS_OK) {
        hc_config_free_tree(root_node);
        XML_ParserFree(parser);
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "XML_Parse failed [%d]",
                 status);
        return(NULL);
    }

    XML_ParserFree(parser);

    if (hc_tree_get_next_brother(root_node)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The root node has a brother in hc_config_parse_file");
    }

    result = hc_tree_get_first_child(root_node);
    
    hc_tree_remove(root_node);
    hc_tree_free(root_node);

    if (hc_config_group_sanity_check(result,
                                     HC_FILTER_GROUP_ALL | HC_FILTER_NODE_ALL)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "The sanity check for group specifications did not pass");
        hc_config_free_tree(result);
        return(NULL);
    }

    return(result);
}

void
hc_config_free_config(hc_tree_opaque_t *config)
{
    hc_config_free_tree(config);
}

void
hc_config_walker(hc_tree_opaque_t *service_node,
                 hc_service_domain_t domain,
                 walker_callback_t walker_callback,
                 void *cookie)
{
    hc_tree_opaque_t *root = service_node;
    hc_service_t *service = NULL;

    /* Find the root service */

    if (!root) {
        root = hc_scheduler_get_services();
    }

    if (domain == HC_DOMAIN_PROCESS) {
        do {
            service = (hc_service_t*)hc_tree_get_datas(root);
            if (service->service_type == HC_SERVICE_JAVA_SERVICE) {
                root = hc_tree_get_father(root);
            }
        } while (service->service_type == HC_SERVICE_JAVA_SERVICE);
    }

    /* Execute the recursive walker */

    hc_config_walker_rec(root, domain, walker_callback, cookie, NULL);
}

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
                      size_t mailbox_size)
{
    hc_service_t *service = NULL;
    hc_tree_opaque_t *stack_new_node = NULL;
    hc_tree_opaque_t **new_node_p = NULL;
    int size = 1;
    int i;
    char *token;
    char *buffer;

    if (new_node) {
        new_node_p = new_node;
    } else {
        new_node_p = &stack_new_node;
    }

    service = hc_service_allocate_new(root_node, new_node_p);
    if (!service) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "hc_service_allocate_new failed");
        return(NULL);
    }
    
    /* Fill the fields */

    service->service_type = service_type;
    service->filter = filter;
    service->heartbeat = heartbeat;
    
    if (start_cmd) {
        service->start_cmd = strdup(start_cmd);
    }

    /* Compute the environment */

    if (env) {
        token = strchr(env, ';');
        while (token) {
            size++;
            token = strchr(token+1, ';');
        }

        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Analysing environment %s. Found %d variable(s)",
                 env, size);

        service->envp = (char**)malloc((size+2)*sizeof(char*));
        if (!service->envp) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "malloc failed to allocate the environment");
            hc_config_free_service(service);
            hc_tree_remove(*new_node_p);
            hc_tree_free(*new_node_p);
            *new_node_p = NULL;
            return(NULL);
        }

        buffer = strdup(env);
        if (!buffer) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "strdup failed. Cannot allocate the environment");
            service->envp[1] = NULL;
            hc_config_free_service(service);
            hc_tree_remove(*new_node_p);
            hc_tree_free(*new_node_p);
            *new_node_p = NULL;
            return(NULL);
        }

        service->envp[0] = NULL; /* Reserved to put the HC_MAILBOX env variable */
        service->envp[size+1] = NULL;
        service->envp[1] = strtok(buffer, ";");

        for (i=1; i<size; i++) {
            service->envp[i+1] = strtok(NULL, ";");
            if (!service->envp[i+1]) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "The %d-th environment is NULL - Unexpected !",
                         i);
            }
        }
    }
    
    /* End of the environment computation */

    if (arg) {
        service->arg = strdup(arg);
    }

    service->restart_nb = restart_nb;
    service->restart_window = restart_window;
    
    if (mailbox_tag) {
        /* We are adding a string ???/ before the mailbox tag */
        service->mailbox_tag = (char*)malloc(strlen(mailbox_tag)+5);
        if (!service->mailbox_tag) {
            hc_config_free_service(service);
            hc_tree_remove(*new_node_p);
            hc_tree_free(*new_node_p);
            *new_node_p = NULL;
            return(NULL);
        }

        sprintf(service->mailbox_tag, "%d/%s",
                get_local_nodeid(), mailbox_tag);
    }

    service->mailbox_size = mailbox_size;

    /* Check that the service is correct */

    if (!service_is_valid(service)) {
        hc_config_free_service(service);
        hc_tree_remove(*new_node_p);
        hc_tree_free(*new_node_p);
        *new_node_p = NULL;
        return(NULL);
    }

    if (service->service_type == HC_SERVICE_PROBE) {
        return(service);
    }

    service->restart_times = (time_t*)malloc(service->restart_nb*sizeof(time_t));
    if (!service->restart_times) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Failed to allocate the restart times buffer.");
        hc_config_free_service(service);
        service = NULL;
        hc_tree_remove(*new_node_p);
        hc_tree_free(*new_node_p);
        *new_node_p = NULL;
        return(NULL);
    }

    for (i=0; i<service->restart_nb; i++) {
        service->restart_times[i] = 0;
    }

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Allowing %d restart within %d seconds for service %s",
             service->restart_nb,
             service->restart_window,
             service->start_cmd);
    
    /* Open the mailbox */

    if (service->mailbox_id == MB_INVALID_ID) {
        service->mailbox_id = mb_create(service->mailbox_tag,
                                        service->mailbox_size);
        if (service->mailbox_id == MB_INVALID_ID) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Failed to create a mailbox for %s",
                     service->start_cmd);
            hc_config_free_service(service);
            service = NULL;
            hc_tree_remove(*new_node_p);
            hc_tree_free(*new_node_p);
            *new_node_p = NULL;
            return(NULL);
        }
    } else {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Unexpected ! Service %s already has a mailbox",
                 service->start_cmd);
    }

    return(service);
}

void
hc_config_lock(hc_tree_opaque_t *service_node,
               hc_service_domain_t domain,
               hc_service_filter_t filter,
               int locked)
{
    hc_config_lock_walker_t args;

    args.locked = locked;
    args.filter = filter;

    hc_config_walker(service_node,
                     domain,
                     hc_config_lock_walker,
                     &args);
}
