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
#include <stdio.h>
#include <stdlib.h>

int
main()
{
    cmm_error_t     err;
    uint32_t        i, res, nbnodes;
    cmm_member_t	*members;
    cmm_nodeid_t	nodeid;
    timespec_t      timeout = {0,0};

    err = cmm_connect( timeout );
    if (err != CMM_OK) {
        fprintf(stderr, "cmm_connect failed [%d]\n",
                err );
        return(1);
    }

    err = cmm_member_getcount(&res);
    if (err == -1) {
        fprintf (stderr, "Error in cmm_get_member_count\n");
        return(1);
    }

    printf ("There are %d nodes in the cluster\n", res);  

    members=(cmm_member_t*)malloc(res *sizeof(cmm_member_t));
    if (!members) {
        fprintf (stderr, "Not enought memory\n");
        return(1);
    }

    err = cmm_member_getall( res, members, &nbnodes);
    if (err) {
        fprintf (stderr, "Error while getting all members\n");
        free(members);
        return(1);
    }

    for (i=0; i<nbnodes; i++) {
        printf ("Name %s\tNodeid %d (%s)\tStatus ",
                members[i].name,
                members[i].nodeid,
                members[i].addr );

        printf("sflag: %x ", members[i].sflag);

        if (cmm_member_isqualified(&members[i])) {
            printf("qualified ");
        }

        if (cmm_member_isdisqualified(&members[i])) {
            printf("disqualified ");
        }

        if (cmm_member_iseligible(&members[i])) {
            printf("eligible ");
        } else {
            printf("ineligible\n");
            continue;
        }

        if (cmm_member_ismaster(&members[i])) {
            printf ("master\n");
            continue;
        }
        
        if (cmm_member_isvicemaster(&members[i])) {           
            printf ("vicemaster\n");
            continue;
        }
        printf("\n");
    }

    free(members);

    err = cmm_node_getid(&nodeid);

    printf ("\nMy node id is %d\n",nodeid);

    cmm_disconnect();

    return(0);
}

  
