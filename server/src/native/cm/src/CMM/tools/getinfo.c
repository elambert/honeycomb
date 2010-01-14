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

int
main(int	argc,
     char *	argv[])
{
    cmm_nodeid_t	nodeid;
    cmm_member_t	member;
    cmm_error_t     err;
    timespec_t      timeout = {0,0};
    int read_value;

    if (argc != 2) {
        printf ("Usage : getinfo nodeid\n");
        return(1);
    }

    sscanf( argv[1], "%d", &read_value);
    nodeid = read_value;

    err = cmm_connect( timeout );
    if (err != CMM_OK) {
        fprintf(stderr, "cmm_connect failed [%d]\n",
                err );
        return(1);
    }

    err = cmm_member_getinfo( nodeid, &member);
    if (err != CMM_OK) {
        fprintf (stderr, "Error in cmm_get_memberinfo [%d]\n",
                 err);
        cmm_disconnect();
        return(1);
    }

    cmm_disconnect();

    printf("Here is the info on the requested node :\n\n"
           "Nodeid     : %d\n"
           "Name       : %s\n"
           "IP address : %s\n"
           "Status     : ",
           member.nodeid,
           member.name,
           member.addr);

    if (cmm_member_isqualified(&member)) {
        printf("qualified ");
    }

    if (cmm_member_isdisqualified(&member)) {
        printf("disqualified ");
    }

    if (cmm_member_iseligible(&member)) {
        printf("eligible ");
    } else {
        printf("ineligible\n");
    }

    if (cmm_member_ismaster(&member)) {
        printf ("master\n");
    }
        
    if (cmm_member_isvicemaster(&member)) {           
        printf ("vicemaster\n");
    }

    printf("\n\n");

    return(0);
}

