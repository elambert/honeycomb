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



/* Implementation of the election algorithm */

#include "election.h"
#include "cnt.h"
#include "trace.h"
#include "api.h"

void
start_new_election( fifo_t *to_sender,
                    char office )
{
    frame_t *frame;

    frame_arg_election_t *args;

    if ( (!(cnt_get_entry(0)->member.sflag & CMM_ELIGIBLE_MEMBER))
         || (cnt_get_entry(0)->member.sflag & CMM_FLAG_DISQUALIFIED) ) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "I am not a eligible (NMEN or disqualified). Don't trigger the election" );
        return;
    }
 
    if (office == 1) {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Trigger an election for the MASTER role" );
    } else {
        cm_trace(CM_TRACE_LEVEL_DEBUG,
                 "Trigger an election for the VICEMASTER role" );
    }

    frame = frame_allocate( FRAME_ELECTION, cnt_get_entry(0)->member.nodeid & 0xFF );
    frame->dest = DEST_NEXT_ONLY;
    
    args = (frame_arg_election_t*)frame->arg;

    args->office = office;
    args->request = 1;
    args->elected_node = cnt_get_entry(0)->member.nodeid & 0xFF;
    
    if ( fifo_add_elem( to_sender, frame ) != CMM_OK ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_add_elem failed" );
        frame_free( &frame );
    }
}
/* 
 * Apply for office, based on my current position and the vacancies available 
 */

static void
trigger_needed_elections( fifo_t *to_sender,
                          int office )
{
    uint32_t is_men;
    uint32_t is_master = cnt_get_entry(0)->member.sflag & CMM_MASTER;
    /*     uint32_t is_vice_master = cnt_get_entry(0)->member.sflag & CMM_VICEMASTER; */

    is_men = (cnt_get_entry(0)->member.sflag & CMM_ELIGIBLE_MEMBER) && (!(cnt_get_entry(0)->member.sflag & CMM_FLAG_DISQUALIFIED)) ? 1 : 0;

    switch (office) {
    case -2 :
        if ( (is_men) && (!is_master) ) {
            start_new_election(to_sender, 2);
        }
        break;

    case -1 :
        if ( is_men ) {
            start_new_election(to_sender, 1);
        }
        break;

    case 1 :
        if ( (is_men)
             && (!is_master) ) {
            start_new_election( to_sender, 2 );
        }
        break;

    case 2 :
        break;
    }
}

/* 
 * change the status of an elected (or demoted) node
 * A value of 1 means elected to CMM_MASTER, 2 means elected to CMM_VICEMASTER
 * Negative 1 and 2 means removed from the corresponding offices
 * Also apply for a vacant office if I am eligible.
 */

void
node_elected( fifo_t *to_sender,
              cmm_nodeid_t node,
              char office )
{
    int node_index;
        
    node_index = cnt_distance(node);

    switch (office) {
    case -2 :
        cnt_get_entry(node_index)->member.sflag &= ~CMM_VICEMASTER;
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Node %d has lost his VICEMASTER role",
                 node );
        api_dispatch_event( CMM_VICEMASTER_DEMOTED, node );
        break;

    case -1 :
        cnt_get_entry(node_index)->member.sflag &= ~CMM_MASTER;
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Node %d has lost his MASTER role",
                 node );
        api_dispatch_event( CMM_MASTER_DEMOTED, node );
        break;
        
    case 1 :
        cnt_get_entry(node_index)->member.sflag |= CMM_MASTER;
        cnt_get_entry(node_index)->member.sflag &= ~CMM_VICEMASTER;
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Node %d has been elected MASTER",
                 node );
        api_dispatch_event( CMM_MASTER_ELECTED, node );
        break;
        
    case 2 :
        cnt_get_entry(node_index)->member.sflag |= CMM_VICEMASTER;
        cnt_get_entry(node_index)->member.sflag &= ~CMM_MASTER;
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "Node %d has been elected VICEMASTER",
                 node );
        api_dispatch_event( CMM_VICEMASTER_ELECTED, node );
        break;
    }

    trigger_needed_elections(to_sender, office);
}

void
election_deal_with_frame( fifo_t *to_sender,
                          frame_t *frame )
{
    frame_t *new_frame;
    frame_arg_election_t *args = (frame_arg_election_t*)frame->arg;
    frame_arg_election_t *new_args;
    char there_is_a_vicemaster;
    int i;

    cm_trace(CM_TRACE_LEVEL_DEBUG,
             "Election frame : office %d elected_node %d request %d",
             args->office,
             args->elected_node,
             args->request );

    if ( args->request == 0 ) {
        /* This is a notification and not a request */

        /* Check that there is no conflict with myself. Otherwise, give up my office */
        if ( ((cnt_get_entry(0)->member.sflag & CMM_MASTER)
              && (args->office == 1))
             || ((cnt_get_entry(0)->member.sflag & CMM_VICEMASTER)
                 && (args->office == 2)) ) {
            /* There is a conflict ... */
            if (args->elected_node < cnt_get_entry(0)->member.nodeid) {
                /* Give up */
                cm_trace(CM_TRACE_LEVEL_NOTICE,
                         "There is a role conflict with node %d. I give up my role",
                         args->elected_node);
                election_office_release(to_sender, -1*args->office);
                return;
            }
        }

        if ( cnt_distance(args->elected_node) != 0 ) {
            node_elected( to_sender, args->elected_node, args->office );
        }
        frame_free( &frame );
        return;
    }

    /* This is an election request */

    if ( cnt_distance( args->elected_node ) == 0 ) {
        /* I have been elected ! */
        node_elected( to_sender, args->elected_node, args->office );

        /* Send the notification */
        new_frame = frame_allocate( FRAME_ELECTION, cnt_get_entry(0)->member.nodeid & 0xFF );
        new_frame->dest = DEST_BROADCAST;
        
        new_args = (frame_arg_election_t*)new_frame->arg;
        
        new_args->office = args->office;
        new_args->request = 0;
        new_args->elected_node = args->elected_node;
        
        if ( fifo_add_elem( to_sender, new_frame ) != CMM_OK ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "fifo_add_elem failed" );
        }

        frame_free( &frame );
        return;
    }

    /* Look if there is already a vicemaster */
    there_is_a_vicemaster = 0;
    for (i=1; i<cnt_get_nb_nodes(); i++) {
        if ( cnt_get_entry(i)->member.sflag & CMM_VICEMASTER ) {
            there_is_a_vicemaster = 1;
            break;
        }
    }

    /* Overall, check that the node is not DISQUALIFIED and enforce it if needed */
    if ( (cnt_get_entry(cnt_distance(args->elected_node))->member.sflag & CMM_FLAG_DISQUALIFIED) ) {
        frame_t *old_frame = frame;
        frame_arg_qualif_change_t *args;

        /* Send a disqualify request */
        frame = frame_allocate( FRAME_QUALIF_CHANGE, cnt_get_entry(0)->member.nodeid & 0xFF );
        if (!frame) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "frame_allocate failed" );
            return;
        }

        frame->dest = ((frame_arg_election_t*)old_frame->arg)->elected_node & 0XFF;
        args = (frame_arg_qualif_change_t*)frame->arg;

        args->nodeid = ((frame_arg_election_t*)old_frame->arg)->elected_node;
        args->new_qualif = CMM_DISQUALIFIED_MEMBER + 150;
        args->request = 1;
        args->res = CMM_OK;

        if ( fifo_add_elem(to_sender, frame) != CMM_OK ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
                     "fifo_add_elem failed" );
            frame_free( &frame );
            frame_free( &old_frame );
            return;
        }
        
        frame_free( &old_frame );
        return;
    }
    
    /* Check if I am a better candidate. If this is the case, drop the frame */
    if ( 
        /* I have a better node id and I am not MASTER already */
        ( (cnt_get_entry(0)->member.sflag & CMM_ELIGIBLE_MEMBER)
          && (!(cnt_get_entry(0)->member.sflag & CMM_FLAG_DISQUALIFIED))
          && ( cnt_get_entry(0)->member.nodeid < args->elected_node)
          && !(cnt_get_entry(0)->member.sflag & CMM_MASTER) )
        
        /* The election concerns the MASTER and I am already MASTER or VICEMASTER */
        || ( ( (cnt_get_entry(0)->member.sflag & CMM_MASTER)
               || (cnt_get_entry(0)->member.sflag & CMM_VICEMASTER))
             && (args->office == 1) )
        
        /* The election concerns the VICEMASTER and I am already VICEMASTER */
        || ( (cnt_get_entry(0)->member.sflag & CMM_VICEMASTER)
             && (args->office == 2) )) {
        /* Check that there is no previous VICEMASTER for a MASTER election */
        
        if ( (args->office != 1)
             || (!there_is_a_vicemaster) ) {
            cm_trace(CM_TRACE_LEVEL_DEBUG, "Reject candidate 0x%x", cnt_get_entry(0)->member.sflag );
            
            frame_free( &frame );
            return;
        }
    }

    /* Forward the request */
    
    if ( fifo_add_elem( to_sender, frame ) != CMM_OK ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_add_elem failed" );
        frame_free( &frame );
    }
}

static void
election_office_release( fifo_t *to_sender,
                         char office )
{
    frame_t *frame;
    frame_arg_election_t *args;

    /* 1. send the frame notification */

    frame = frame_allocate( FRAME_ELECTION, cnt_get_entry(0)->member.nodeid & 0xFF );
    frame->dest = DEST_BROADCAST;
        
    args = (frame_arg_election_t*)frame->arg;
        
    args->office = office;
    args->request = 0;
    args->elected_node = cnt_get_entry(0)->member.nodeid & 0xFF;
        
    if ( fifo_add_elem( to_sender, frame ) != CMM_OK ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_add_elem failed" );
        frame_free( &frame );
        return;
    }

    /* 2. Notify the clients and trigger elections */

    node_elected( to_sender, cnt_get_entry(0)->member.nodeid & 0xFF, office );
}

cmm_error_t
election_mastership_release( fifo_t *to_sender )
{
    int index;

    if ( !(cnt_get_entry(0)->member.sflag & CMM_MASTER) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "Cannot call mastership_release on a non MASTER node" );
        return(CMM_EPERM);
    }

    /* Look if there is a VICEMASTER */

    for (index=0; index<cnt_get_nb_nodes(); index++) {
        if ( cnt_get_entry(index)->member.sflag & CMM_VICEMASTER ) {
            break;
        }
    }

    if ( index == cnt_get_nb_nodes() ) {
        /* There is no vicemaster */
        return(CMM_ECANCELED);
    }

    /* I am master : release the mastership */
    election_office_release(to_sender, -1);

    return(CMM_OK);
}
    
void
election_request_qualif_change( fifo_t *to_sender,
                                frame_t *frame )
{
    frame_t *duplicate;
    frame_arg_qualif_change_t *args = (frame_arg_qualif_change_t*)frame->arg;
    int cnt_index;

    /* Check the input parameters */
    cnt_index = cnt_distance(args->nodeid);

    if ( ! (cnt_get_entry(cnt_index)->member.sflag & CMM_ELIGIBLE_MEMBER) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "The node %d is not eligible. Not qualification operations allowed",
                 args->nodeid );
        args->res = CMM_EINVAL;
        return;
    }

    /* Check if the node is a local node */
    if ( cnt_index == 0 ) {
        election_qualif_change( to_sender, frame );
        return;
    }

    /* The request is not for the local node - I have to forward the request */
    duplicate = frame_duplicate( frame );
    if (!duplicate) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "frame_duplicate failed" );
        args->res = CMM_ENOTSUP;
        return;
    }

    duplicate->sender = cnt_get_entry(0)->member.nodeid & 0xFF;
    duplicate->dest = args->nodeid & 0XFF;

    if ( fifo_add_elem(to_sender, duplicate) != CMM_OK ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_add_elem failed" );
        frame_free( &duplicate );
        args->res = CMM_ENOTSUP;
        return;
    }

    args->res = CMM_OK;
}

void
election_qualif_change( fifo_t *to_sender,
                        frame_t *frame )
{
    frame_arg_qualif_change_t *args = (frame_arg_qualif_change_t*)frame->arg;
    frame_t *duplicate;
    char have_master, have_vicemaster;
    int i;

    if ( args->request == 0 ) {
        if ( cnt_distance(args->nodeid) == 0 ) {
            return;
        }
        /* This is an information frame */
        switch (args->new_qualif - 150) {
        case CMM_QUALIFIED_MEMBER :
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "Node %d has been qualified",
                     args->nodeid );
            cnt_get_entry(cnt_distance(args->nodeid))->member.sflag &= ~CMM_FLAG_DISQUALIFIED;
            break;

        case CMM_DISQUALIFIED_MEMBER :
            cm_trace(CM_TRACE_LEVEL_NOTICE,
                     "Node %d has been disqualified",
                     args->nodeid );
            cnt_get_entry(cnt_distance(args->nodeid))->member.sflag |= CMM_FLAG_DISQUALIFIED;
            break;
        }
        return;
    }

    /* I have been asked to change my qualification status */

    /* Send the notification thread */

    args->res = CMM_OK;

    duplicate = frame_duplicate( frame );
    if (!duplicate) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "frame_duplicate failed" );
        return;
    }

    duplicate->sender = cnt_get_entry(0)->member.nodeid;
    duplicate->dest = DEST_BROADCAST;
    ((frame_arg_qualif_change_t*)duplicate->arg)->request = 0;

    if ( fifo_add_elem(to_sender, duplicate) != CMM_OK ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
                 "fifo_add_elem failed" );
        frame_free(&duplicate);
        return;
    }

    switch (args->new_qualif - 150) {
    case CMM_QUALIFIED_MEMBER :
        /* I have been asked to qualify */
        cnt_get_entry(0)->member.sflag &= ~CMM_FLAG_DISQUALIFIED;
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "I have been QUALIFIED" );
        have_master = 0;
        have_vicemaster = 0;
        for (i=0; i<cnt_get_nb_nodes(); i++) {
            if ( cnt_get_entry(i)->member.sflag & CMM_MASTER ) {
                have_master = 1;
            }
            if ( cnt_get_entry(i)->member.sflag & CMM_VICEMASTER ) {
                have_vicemaster = 1;
            }
        }
        if (!have_master) {
            start_new_election( to_sender, 1 );
        }
        if ( (have_master)
             && (!have_vicemaster) ) {
            start_new_election(to_sender, 2);
        }
        break;

    case CMM_DISQUALIFIED_MEMBER :
        /* I have been asked to disqualify */
        cnt_get_entry(0)->member.sflag |= CMM_FLAG_DISQUALIFIED;
        cm_trace(CM_TRACE_LEVEL_NOTICE,
                 "I have been DISQUALIFIED" );
        if ( cnt_get_entry(0)->member.sflag & CMM_MASTER ) {
            election_office_release(to_sender, -1);
        }
        if ( cnt_get_entry(0)->member.sflag & CMM_VICEMASTER ) {
            election_office_release(to_sender, -2);
        }
        break;
    }
}
