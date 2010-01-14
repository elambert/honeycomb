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
 * Methods for maintaining and updating the cluster node table 
 */



#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#include "cnt.h"
#include "trace.h"
#include "fifo_mt.h"
#include "cmm_parameters.h"
#include "comm.h"

/*
 * Definition of the table
 */

typedef struct {
    cmm_domainid_t cluster_id;
    int nb_nodes;
    cnt_entry_t **nodes;
} cnt_table_t;

static cnt_table_t cnt_table;

static pthread_mutex_t cnt_mutex;

/*
 * Routines
 */

typedef enum {
    SKIPPING,
    COMMENT,
    READING,
    DONE
} read_status_t;

static int
readExcluding( int filefd,
               char *excludedChars,
               char *buf,
               int bufsize )
{
    int pos, i;
    char c;
    read_status_t status;
    char match;

    status = SKIPPING;
    pos = 0;
    c = 0;

    do {
        if ( read(filefd, &c, 1) != 1 ) {
            break;
        }

        if (pos+1 == bufsize) {
            status = DONE;
            cm_trace(CM_TRACE_LEVEL_ERROR, 
		     "Buffer is full" );
            break;
        }

        if (c == '#') {
            status = COMMENT;
        }
                

        match = 0;
        for (i=0; i<strlen(excludedChars); i++) {
            if (c == excludedChars[i]) {
                match = 1;
                break;
            }
        }

        switch (status) {
        case COMMENT :
            if (c == '\n') {
                status = SKIPPING;
            }
            break;

        case SKIPPING :
            if (!match) {
                status = READING;
                /* No break */
            } else {
                break;
            }

        case READING :
            if (match) {
                status = DONE;
            } else {
                buf[pos] = (char)c;
                pos++;
            }
            break;

	default:
	    cm_trace(CM_TRACE_LEVEL_DEBUG,
		     "Default handler in readExcluding");
        }
    } while (status != DONE);

    buf[pos] = '\0';

    if (pos == 0) {
        return(0);
    }

    return(1);
}

static cnt_entry_t *
readEntry( int filefd )
{
    cnt_entry_t *result;
    char nodeid_s[8];
    char name_s[CMM_MAX_NAME_SIZE];
    char unix_name_s[CMM_MAX_NAME_SIZE];
    char men_s[8];
    int read_value;

    if ( !readExcluding(filefd, " \t\n\r", nodeid_s, 8) ) {
        return(NULL);
    }

    if ( !readExcluding(filefd, " \t\n\r", name_s, CMM_MAX_NAME_SIZE) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "Couldn't read the node name (node id %s)",
		 nodeid_s );
        return(NULL);
    }

    if ( !readExcluding(filefd, " \t\r\n", unix_name_s, CMM_MAX_NAME_SIZE) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "Couldn't read the unix node name (node id %s)",
		 nodeid_s );
        return(NULL);
    }

    if ( !readExcluding(filefd, " \t\n\r", men_s, 8) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "Couldn't read the MEN flag (node id %s)",
		 nodeid_s );
        return(NULL);
    }

    result = (cnt_entry_t*)malloc(sizeof(cnt_entry_t));
    if (!result) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "malloc failed" );
        return(NULL);
    }

    sscanf( nodeid_s, "%d", &read_value);
    result->member.nodeid = read_value;
    strcpy( result->member.name, name_s);
    strcpy( result->unix_name, unix_name_s );
    if (strcmp(men_s, "MEN") == 0) {
        result->member.sflag = CMM_ELIGIBLE_MEMBER;
    } else {
        result->member.sflag = 0;
    }

    result->member.sflag |= CMM_OUT_OF_CLUSTER;

    sprintf( result->member.software_load_id, "%d", 1 );

    return(result);
}

static char
compare_node_ids( cmm_nodeid_t local_node,
                  cmm_nodeid_t node_a,
                  cmm_nodeid_t node_b )
{
    if (node_a == node_b) {
        return(0);
    }

    if ( (node_a < local_node)
         && (node_b >= local_node) ) {
        return(1);
    }
    
    if ( (node_a >= local_node)
         && (node_b < local_node) ) {
        return(-1);
    }

    return( node_a < node_b ? -1 : 1 );
}

/* 
 * Create a cnt_table sorted by clockwise distance from local node.
 * IN: local_node_id
 * IN: a fifo_t list of candidtae nodes of type cnt_entry_t
 * OUT: error code, CMM_OK if no error 
 */

static cmm_error_t
cnt_create_table( cmm_nodeid_t local_node_id,
                  fifo_t *elems )
{
    cnt_entry_t *entry;
    int i, j;

    cnt_table.nb_nodes = 0;
    entry = fifo_get_first( elems );
    while (entry) {
        entry = fifo_get_next( elems );
        cnt_table.nb_nodes++;
    }
    
    cnt_table.nodes = (cnt_entry_t**)
	malloc(cnt_table.nb_nodes*sizeof(cnt_entry_t*));
    if (!cnt_table.nodes) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "malloc failed" );
        return(CMM_ENOMEM);
    }

    for (i=0; i<cnt_table.nb_nodes; i++) {
        entry = fifo_extract_elem( elems );
        cnt_table.nodes[i] = entry;
    }

    /* Sort the nodes */
    for (i=0; i<cnt_table.nb_nodes-1; i++) {
        for (j=cnt_table.nb_nodes-1; j>i; j--) {
            if ( compare_node_ids( local_node_id,
                                   cnt_table.nodes[j-1]->member.nodeid,
                                   cnt_table.nodes[j]->member.nodeid ) == 1 ) {
                entry = cnt_table.nodes[j];
                cnt_table.nodes[j] = cnt_table.nodes[j-1];
                cnt_table.nodes[j-1] = entry;
            }
        }
    }

    return(CMM_OK);
}

/* 
 * Get the host addresses for all the nodes in the cnt_table
 * Fails if a single element is unknown
 */

static cmm_error_t
cnt_update_dns()
{
    int i;
    cnt_entry_t *node;

    for (i=0; i<cnt_table.nb_nodes; i++) {
        node = cnt_table.nodes[i];
        if ( GetHostAddress( node->unix_name,
                             RING_PORT,
                             &(node->net_addr) ) != 1 ) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
		     "GetHostAddress failed for node %s",
		     node->unix_name );
            return( CMM_EOTHER );
        }

        /* Update the addr field */
        get_ip_from_hostname( node->unix_name, node->member.addr );
	cm_trace(CM_TRACE_LEVEL_DEBUG,
		 "Updated the DNS entry for %s. IP address is %s",
		 node->unix_name,
		 node->member.addr );
    }

    cm_trace(CM_TRACE_LEVEL_DEBUG,
	     "The DNS entries have been found successfully" );
    
    return(CMM_OK);
}

/* 
 * Read and init the cluster node table [cnt] and order it in terms of closeness
 * to the node id of local node (clockwise ring ordering with cnt[0] being the 
 * local node) 
 * IN: cnt filename: contains all the candidate nodes that may join the cluster
 * IN: local_node_id: the id of the local node 
 * OUT: cmm_error_t, CMM_OK if no error.
 */

cmm_error_t
cnt_init(char         *filename,
         cmm_nodeid_t local_node_id)
{
    int filefd;
    fifo_t *elems;
    cnt_entry_t *entry;
    cmm_error_t err = CMM_OK;

    if ( pthread_mutex_init( &cnt_mutex, NULL ) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "pthread_mutex_init failed" );
        return(CMM_EOTHER);
    }

    filefd = open( filename, O_RDONLY );
    if (filefd == -1) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "Failed to open file %s in O_RDONLY mode",
		 filename );
        return( CMM_EINVAL );
    }

    elems = fifo_init(NULL);
    if (!elems) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "fifo_init failed" );
        close(filefd);
        return( CMM_ENOMEM );
    }

    while ((entry = readEntry(filefd))) {
        err = fifo_add_elem( elems,
                             entry );
        if (err != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
		     "fifo_add_elem failed [%d]",
		     err );
            break;
        }
    }

    if (err == CMM_OK) {
        err = cnt_create_table( local_node_id,
                                elems );
        if (err != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
		     "cnt_create_table failed [%d]",
		     err );
        }
    }

    if (err == CMM_OK) {
        err = cnt_update_dns();
        if (err != CMM_OK) {
            cm_trace(CM_TRACE_LEVEL_ERROR, 
		     "cnt_update_dns failed [%d]",
		     err );
        }
    }

    if (err != CMM_OK) {
        while ((entry = fifo_extract_elem(elems))) {
            free(entry);
        }
    }

    fifo_destroy(elems);
    elems = NULL;

    if (err == CMM_OK) {
	cm_trace(CM_TRACE_LEVEL_DEBUG,
		 "The CNT initialized properly" );
    }

    return(err);
}

void
cnt_print()
{
    int i;
    
    cm_trace(CM_TRACE_LEVEL_DEBUG,
	     "Cluster node table :" );

    for (i=0; i<cnt_table.nb_nodes; i++) {
        if ( cnt_table.nodes[i]->member.sflag & CMM_ELIGIBLE_MEMBER ) {
	    cm_trace(CM_TRACE_LEVEL_DEBUG,
		     "Node %s (id %d, unix hostname %s) is a MEN",
		     cnt_table.nodes[i]->member.name,
		     cnt_table.nodes[i]->member.nodeid,
		     cnt_table.nodes[i]->unix_name );
        } else {
	    cm_trace(CM_TRACE_LEVEL_DEBUG,
		     "Node %s (id %d, unix hostname %s) is a NMEN",
		     cnt_table.nodes[i]->member.name,
		     cnt_table.nodes[i]->member.nodeid,
		     cnt_table.nodes[i]->unix_name );
        }
    }
}

int
cnt_get_nb_nodes()
{
    return( cnt_table.nb_nodes );
}

cnt_entry_t *
cnt_get_entry( int index )
{
    return( cnt_table.nodes[index] );
}

int
cnt_distance( cmm_nodeid_t nodeid )
{
    int distance;

    for (distance=0; distance<cnt_table.nb_nodes; distance++) {
        if ( cnt_table.nodes[distance]->member.nodeid == nodeid ) {
            break;
        }
    }

    if (distance == cnt_table.nb_nodes) {
        return(-1);
    }

    return(distance);
}

void
cnt_lock()
{
    while ( pthread_mutex_lock( &cnt_mutex ) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "pthread_mutex_lock failed" );
    }
}

void
cnt_unlock()
{
    while ( pthread_mutex_unlock( &cnt_mutex ) ) {
        cm_trace(CM_TRACE_LEVEL_ERROR, 
		 "pthread_mutex_lock failed" );
    }
}
