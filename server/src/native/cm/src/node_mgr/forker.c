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
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <errno.h>
#include <stdio.h>
#include <malloc.h>
#include <string.h>
#include <netinet/in.h>
#include <mbox.h>

#include "forker.h"
#include "scheduler.h"
#include "tree.h"

/*
 * Private routines
 */

static void
configure_hosted_services(hc_tree_opaque_t *service_node,
                          int propagate_pid,
                          int propagate_state)
{
    hc_tree_opaque_t *child = NULL;
    hc_service_t *service = (hc_service_t*)hc_tree_get_datas(service_node);
    hc_service_t *child_service = NULL;
    mb_state_t parent_state;
    mb_error_t mb_err;

    child = hc_tree_get_first_child(service_node);
    while (child) {
        child_service = (hc_service_t*)hc_tree_get_datas(child);
        if (child_service->service_type == HC_SERVICE_JAVA_SERVICE) {
            if (propagate_pid) {
                child_service->pid = service->pid;
            }
            if (propagate_state) {
                mb_err = MB_OK;
                if ((service->mailbox_id == MB_INVALID_ID)
                    || (child_service->mailbox_id == MB_INVALID_ID)) {
                    cm_trace(CM_TRACE_LEVEL_ERROR,
                             "Cannot propagate the state because the mailbox_id is invalid");
                    mb_err = MB_ERROR;
                }
                if (mb_err == MB_OK) {
                    mb_err = mb_getstate(service->mailbox_id,
                                         &parent_state);
                    if (mb_err != MB_OK) {
                        cm_trace(CM_TRACE_LEVEL_ERROR,
                                 "mb_getstate failed. Cannot propagate the state to the hosted services");
                    }
                }

                if (mb_err == MB_OK) {
                    mb_err = mb_setstate(child_service->mailbox_id,
                                         parent_state);
                    if (mb_err != MB_OK) {
                        cm_trace(CM_TRACE_LEVEL_ERROR,
                                 "Failed to propagate the state [mb_setstate failed]");
                    }
                }
            }
            child_service->dialog_pipe = service->dialog_pipe;
            configure_hosted_services(child, propagate_pid, propagate_state);
        }
        child = hc_tree_get_next_brother(child);
    }
}

static int
fork_process(hc_tree_opaque_t *service_node)
{
    int fd[2];
    char buffer[64];
    char *envp_local[2];
    char **envp;
    hc_service_t *service = NULL;

    service = (hc_service_t*)hc_tree_get_datas(service_node);

    if (service->service_type == HC_SERVICE_JVM) {
        if (pipe(fd)) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "pipe failed");
            return(1);
        }
    }

    service->pid = fork();
    switch (service->pid) {
    case 0:
        /* Child */
        break;

    case -1 :
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "fork failed");
        close(fd[0]);
        close(fd[1]);
        return(1);

    default:
        /* Father */
        if (service->service_type == HC_SERVICE_JVM) {
            service->dialog_pipe = fd[1];
            close(fd[0]);
        }
        configure_hosted_services(service_node, 0, 0);
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "The service %s has been forked",
                 service->start_cmd);
        return(0);
    }

    /* Implementation of the child code */

    /* Create a new session and put myself as a leader */
    if (setsid() == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "setsid failed [errno %d]",
                 errno);
    }

    if (service->service_type == HC_SERVICE_JVM) {
        close(fd[1]);
        dup2(fd[0], 0);
    }

    if (service->envp) {
        envp = service->envp;
    } else {
        envp = envp_local;
        envp_local[1] = NULL;
    }

    snprintf(buffer, 64, "HC_MAILBOX=%s",
             service->mailbox_tag);
    envp[0] = buffer;

    execle("/bin/sh", "/bin/sh", "-c", service->start_cmd, NULL, envp);
    
    cm_trace(CM_TRACE_LEVEL_ERROR,
             "exec failed. Child is exiting");

    _exit(1);
}

static hc_tree_opaque_t *
lookfor_service(hc_tree_opaque_t *node,
                pid_t pid)
{
    hc_tree_opaque_t *next_node = NULL;
    hc_tree_opaque_t *result = NULL;

    if (((hc_service_t*)hc_tree_get_datas(node))->pid == pid) {
        return(node);
    }

    next_node = hc_tree_get_next_brother(node);
    if (next_node) {
        result = lookfor_service(next_node, pid);
        if (result) {
            return(result);
        }
    }

    next_node = hc_tree_get_first_child(node);
    if (next_node) {
        result = lookfor_service(next_node, pid);
        if (result) {
            return(result);
        }
    }

    return(NULL);
}

static void
output_UTF_string(int fd,
                  char *string)
{
    union {
        uint16_t i;
        char c[2];
    } length;
    int string_length;

    string_length = strlen(string);
    length.i = htons(string_length);
    
    write(fd, length.c, 1);
    write(fd, length.c+1, 1);
    write(fd, string, string_length);
}

/*
 * API implementation
 */

int
hc_forker_fork_service(hc_tree_opaque_t *service_node)
{
    int res = 1;
    hc_service_t *service = NULL;
    hc_tree_opaque_t *father = NULL;
    hc_service_t *father_service = NULL;
    
    service = (hc_service_t*)hc_tree_get_datas(service_node);

    if (service->mailbox_id != MB_INVALID_ID) {
        (mb_error_t)mb_setstate(service->mailbox_id,
                                SRV_INIT);
    }

    switch (service->service_type) {
    case HC_SERVICE_PROCESS:
    case HC_SERVICE_JVM:
        res = fork_process(service_node);
        if (res) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "fork_process failed");
        }
	
        return(res);

    case HC_SERVICE_JAVA_SERVICE:
        output_UTF_string(service->dialog_pipe,
                          service->mailbox_tag);
        output_UTF_string(service->dialog_pipe,
                          service->start_cmd);
        if (service->arg) {
            output_UTF_string(service->dialog_pipe,
                              service->arg);
        } else {
            output_UTF_string(service->dialog_pipe,
                              "<null>");
        }
        
        father = service_node;
        do {
            father = hc_tree_get_father(father);
            father_service = (hc_service_t*)hc_tree_get_datas(father);
        } while ((father) && (father_service->service_type != HC_SERVICE_JVM));

        if (!father) {
            cm_trace(CM_TRACE_LEVEL_ERROR,
                     "Couldn't retrieve the JVM service for %s",
                     service->start_cmd);
        } else {
            service->pid = father_service->pid;
        }

        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Request to start service %s has been sent to the JVM",
                 service->start_cmd);
        return(0);

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Don't know how to start a service of type %d",
                 service->service_type);
        return(1);
    }

    return(1);
}

int
hc_forker_kill_service(hc_tree_opaque_t *service_node)
{
    int res = 1;
    hc_service_t *service = NULL;

    service = (hc_service_t*)hc_tree_get_datas(service_node);

    switch (service->service_type) {
    case HC_SERVICE_PROCESS:
    case HC_SERVICE_JVM:
        if (service->pid == -1) {
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "The service %s was not running. No taken action",
                     service->start_cmd);
            return(0);
        }

        res = kill(-1*service->pid, SIGKILL);
        service->pid = -1;
        if (service->dialog_pipe != -1) {
            close(service->dialog_pipe);
        }
        service->dialog_pipe = -1;
        mb_setstate(service->mailbox_id,
                    SRV_DISABLED);
        configure_hosted_services(service_node, 0, 1);
        return(res);
	
    case HC_SERVICE_JAVA_SERVICE:
        if (service->pid == -1) {
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "The service %s was not running. No taken action",
                     service->start_cmd);
            return(0);
        }
        /* Should call destroy */
        service->pid = -1;
        return(0);

    default:
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Don't know how to kill a service of type %d",
                 service->service_type);
        return(1);
    }

    return(1);
}

void
hc_forker_check(hc_list_opaque_t *errors)
{
    pid_t res;
    int status;
    hc_tree_opaque_t *service_node = NULL;
    hc_service_t *service = NULL;

    do {
        res = waitpid(-1, &status, WNOHANG);
        if (res == 0) {
            /* There is no dead child */
            return;
        }

        if (res < 0) {
            if (errno != ECHILD) {
                cm_trace(CM_TRACE_LEVEL_ERROR,
                         "waitpid failed [%d]",
                         errno);
            }
            return;
        }
	
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Detected the death of pid %d",
                 res);

        service_node = lookfor_service(hc_scheduler_get_services(), res);
        if (!service_node) {
            cm_trace(CM_TRACE_LEVEL_DEBUG,
                     "Couldn't find the service with the pid %d",
                     res);
            return;
        }

        service = (hc_service_t*)hc_tree_get_datas(service_node);
	
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "!!! The service %s has exited (pid %d) !!!",
                 service->start_cmd,
                 res);
	
        service->pid = -1;
        if (service->dialog_pipe != -1) {
            close(service->dialog_pipe);
        }
        service->dialog_pipe = -1;
        hc_sequence_disable_service(service);
        configure_hosted_services(service_node, 1, 1);
    } while (res > 0);
}
