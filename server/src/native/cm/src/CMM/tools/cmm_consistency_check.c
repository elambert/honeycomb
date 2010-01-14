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
 * This utility asks CMM for the list of nodes and returns :
 * - the number of nodes
 * - the name of the master
 * - the name of the vicemaster
 *
 * A shell script wraps the results and checks it is consistent across the
 * cluster
 */

#include <stdio.h>
#include <malloc.h>

#include <cmm.h>

int
main()
{
    uint32_t nb_nodes = 0;
    uint32_t table_size = 0;
    uint32_t i;
    cmm_member_t *master = NULL;
    cmm_member_t *vicemaster = NULL;
    cmm_member_t *members = NULL;
    cmm_error_t err = CMM_OK;

    err = cmm_member_getcount(&table_size);
    if (err != CMM_OK) {
        fprintf(stderr, "cmm_member_getcount failed [%d]\n",
                err);
    }

    if (err == CMM_OK) {
        members = (cmm_member_t*)malloc(table_size*sizeof(cmm_member_t));
        if (!members) {
            fprintf(stderr, "Not enough memory to allocate members\n");
            err = CMM_ENOMEM;
        }
    }

    if (err == CMM_OK) {
        err = cmm_member_getall(table_size,
                                members,
                                &nb_nodes);
        if (err != CMM_OK) {
            fprintf(stderr, "cmm_member_getall failed [%d]\n",
                    err);
        }
    }

    if (err != CMM_OK) {
        if (members) {
            free(members);
            members = NULL;
        }
        return(1);
    }

    /* Look for the master & vicemaster */

    for (i=0; i<nb_nodes; i++) {
        if (members[i].sflag & CMM_MASTER) {
            master = members + i;
        }
        if (members[i].sflag & CMM_VICEMASTER) {
            vicemaster = members + i;
        }
    }

    printf("%d", nb_nodes);

    if (master) {
        printf(" %d",
               master->nodeid);
    } else {
        printf(" NULL");
    }

    if (vicemaster) {
        printf(" %d",
               vicemaster->nodeid);
    } else {
        printf(" NULL");
    }

    printf("\n");

    free(members);
    members = NULL;

    return(0);
}
