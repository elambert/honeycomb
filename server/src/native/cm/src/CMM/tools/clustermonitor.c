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



#include <cmm.h>

#include <poll.h>
#include <stdio.h>
#include <signal.h>

static int running;

void
signal_handler(int value)
{
    if (cmm_membership_remove()) {
        fprintf (stderr,"Failed to remove membership\n");
        signal( 15, signal_handler);
        return;
    }
    printf ("Membership removed\n");
    running = 0;
}

void
callback(const cmm_cmc_notification_t	*notif,
         void				*cookie)
{
    cmm_member_t	member;
    cmm_error_t     err;

    err = cmm_member_getinfo(notif->nodeid, &member);
    if ( err != CMM_OK) {
        fprintf ( stderr,"cmm_get_memberinfo failed [%d]\n",
                  err );
        return;
    }

    printf( "[%04x] ",
            member.sflag );

    switch (notif->cmchange) {
        case CMM_MASTER_ELECTED:
            printf ("%s has been elected master\n", member.name); 
            break;

        case CMM_VICEMASTER_ELECTED:
            printf ("%s has been elected vicemaster\n", member.name); 
            break;
        
        case CMM_MASTER_DEMOTED:
            printf ("master %s has been demoted\n", member.name); 
            break;

        case CMM_VICEMASTER_DEMOTED:
            printf ("vicemaster %s has been demoted\n", member.name); 
            break;

        case CMM_MEMBER_JOINED:
            printf ("%s joined the cluster\n", member.name); 
            break;

        case CMM_MEMBER_LEFT:
            printf ("%s left the cluster\n", member.name);
            break;

        default:
            fprintf (stderr, "Notification of bad type (%d)\n", notif->cmchange);
    }
}

int
main()
{
    int		    fd;
    cmm_error_t   err;
    struct pollfd	polls[1];
    timespec_t  timeout = {0,0};

    err = cmm_connect(timeout);
    if (err != CMM_OK) {
        printf ( "cmm_connect failed [%d]\n", err );
        return(1);
    }

    printf( "Connected to the CMM\n" );

    if ( cmm_cmc_register(callback, NULL)) {
        fprintf (stderr, "cmm_cmc_register failed\n");
        return(1);
    }

    err = cmm_notify_getfd(&fd);
    if (err != CMM_OK) {
        fprintf (stderr, "Can't get file descriptor\n");
        cmm_cmc_unregister();
        return(1);
    }

    polls[0].fd = fd;
    polls[0].events = POLLIN;

    signal( 15, signal_handler);

    printf ("Cluster monitor up and running ...\nSend signal 15 to cleanly stop the program\n\n");

    running = 1;
  
    while (running) {
        if ( poll(polls, 1, -1) <0) {
            fprintf (stderr,"Poll failed\n");
        } else {
            err = cmm_notify_dispatch();

            if (err != CMM_OK) {
                fprintf(stderr, "cmm_notify_dispatch failed\n");
                cmm_cmc_unregister();
                return(1);
            }
        }
    }

    if (poll(polls, 1, 0)>=0) {
        cmm_notify_dispatch();
    }

    err = cmm_disconnect();
    if (err != CMM_OK) {
        printf ( "cmm_disconnect failed [%d]\n", err );
        return(1);
    }
    
    printf( "Disconnected from CMM. Exiting ...\n" );

    return(0);
}
