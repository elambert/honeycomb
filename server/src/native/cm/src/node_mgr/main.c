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
 * This file is the main file for the node manager.
 *
 * It contains the main routine
 */

#include <unistd.h>
#include <stdio.h>
#include <trace.h>
#include <string.h>
#include <libgen.h>
#include <stdlib.h>
#include <errno.h>
#include <signal.h>
#include <mbox.h>

#include "config.h"
#include "scheduler.h"
#include "mailbox.h"
#include "cmm_interface.h"

static void
usage(char *progname)
{
    fprintf(stderr, "%s usage :\n"
            "\t%s [-f conf_file] [-p port] [-d] [-t]\n\n"
            "\tconf_file is the configuration file (default : %s)\n"
            "\tport is the port number for the server socket (default : %d)\n"
            "\t-d is for the DEBUG mode\n"
            "\t-t is a private flag for testing\n",
            progname, progname,
            DEFAULT_CONFIG_FILE,
            DEFAULT_PORT);
}

static int
stdlogger()
{
    int fds[2];
    char *const logger[] = { 
       "/usr/bin/logger", 
       "-s", 
       "-d", 
       "-t", 
       "stderr", 
       "-p", 
       "local0.err", 
       NULL };

    if (access(logger[0], X_OK) < 0) {
        fprintf(stderr, "Logger not found\n");
        return (1);
    }
    if (pipe(fds) < 0) {
        fprintf(stderr, "Cannot create logger pipe (%s)\n", strerror(errno));
        return (1);
    }
    switch (fork()) {
        case 0:
            dup2(fds[0], 0);
            close(fds[0]);
            close(fds[1]);
            execv(logger[0], logger);
            exit(1);
        case -1:
            close(fds[0]);
            close(fds[1]);
            fprintf(stderr, "Cannot fork logger (%s)\n", strerror(errno));
            return (1);
        default:
            dup2(fds[1], 1);
            dup2(fds[1], 2);
            close(fds[0]);
            close(fds[1]);
            return (0);
    }
}

static void*
daemonize_start()
{
    int fds[2];
    int c;

    if (pipe(fds) < 0) {
        fprintf(stderr, "failed to create daemon pipe\n");
        exit(1);
    }

    switch (fork()) { 
        case 0:
            if (stdlogger() != 0) {
                fprintf(stderr, "failed to redirect stderr & stdout\n");
            } else {
                fprintf(stderr, "Logging stdout & stderr to syslog\n");
            }
            return (void*) (fds[1]); 
        case -1:
            fprintf(stderr, "failed to daemonized\n");
            exit(1); 
        default:
            if (read(fds[0], &c, 1) != 1) {
                fprintf(stderr, "failed to deamonize. Aborting\n");
                exit(1);
            }
            exit(0);
    }
}

void
daemonize_end(hc_sequence_event_t event, 
              void *cookie)
{
    int fd = (int) cookie;
    int c = 0x55;

    if (write(fd, &c, 1) != 1) {
        close_mailbox();
        cm_closelog();
        exit(1);
    }
}


int
main(int argc,
     char *argv[])
{
    char c;
    int port = DEFAULT_PORT;
    char *config_file = DEFAULT_CONFIG_FILE;
    hc_tree_opaque_t *services = NULL;
    struct sigaction action;
    int err;
    int test_mode = 0;
    void* cookie;
    cm_trace_level_t trace_level = CM_TRACE_LEVEL_NOTICE;

    cookie = daemonize_start();

    while ((c=getopt(argc, argv, "f:p:dt")) != -1) {
        switch (c) {
        case 'f':
            config_file = argv[optind-1];
            break;

        case 'p':
            port = atoi(argv[optind-1]);
            break;

        case 'd':
            trace_level = CM_TRACE_LEVEL_DEBUG;
            break;
            
        case 't':
            test_mode = 1;
            break;

        default:
            usage(basename(argv[0]));
            return(1);
        }
    }

    cm_openlog("Node Mgr", trace_level);

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Config file is %s",
             config_file);

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Port is %d",
             port);

    /* Initialize the mailboxes */

    err = mb_init_mailboxes(get_local_nodeid(), 1-test_mode);
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "Couldn't initialize the mailboxes");
        cm_closelog();
        return(1);
    }

    /* Open the mailbox */

    err = open_mailbox();
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Failed to create the node manager mailbox");
        cm_closelog();
        return(1);
    }
    err = update_mailbox();
    if (err) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Failed to update the mailbox for the first time");
        cm_closelog();
        return(1);
    }

    /* Block the SIGPIPE signals */
    
    action.sa_handler = SIG_IGN;
    if (sigaction(SIGPIPE, &action, NULL)) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "sigaction failed");
        close_mailbox();
        cm_closelog();
        return(1);
    }

    /* Read the configuration file */

    services = hc_config_parse_file(config_file);
    if (!services) {
        cm_trace(CM_TRACE_LEVEL_ERROR,
                 "Couldn't read the configuration file [%s]",
                 config_file);
        close_mailbox();
        cm_closelog();
        return(1);
    }

    hc_scheduler_start(services, port, daemonize_end, cookie);

    cm_trace(CM_TRACE_LEVEL_NOTICE,
             "The node manager is exiting");

    /* Destroy the services tree */
    hc_config_free_config(services);

    /* Close the mailbox */
    close_mailbox();

    cm_closelog();

    return(0);
}
