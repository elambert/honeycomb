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

#define EXIT_CODE_FOR_MASTER         1
#define EXIT_CODE_FOR_VICEMASTER     2
#define EXIT_CODE_FOR_SATELLITE      3

#define EXIT_CODE_FOR_FAILURE        255

int
main()
{
    cmm_error_t err;
    cmm_nodeid_t id;
    cmm_member_t member;

    err = cmm_node_getid( &id );
    if (err != CMM_OK) {
        fprintf (stderr, "cmm_node_getid failed [%d]",
                 err );
        return(EXIT_CODE_FOR_FAILURE);
    }

    err = cmm_potential_getinfo( id,
                                 &member );
    if (err != CMM_OK) {
        fprintf( stderr, "cmm_potential_getinfo failed [%d]",
                 err );
        return(EXIT_CODE_FOR_FAILURE);
    }

    if (member.sflag & CMM_MASTER) {
        return(EXIT_CODE_FOR_MASTER);
    }

    if (member.sflag & CMM_VICEMASTER) {
        return(EXIT_CODE_FOR_VICEMASTER);
    }

    return(EXIT_CODE_FOR_SATELLITE);
}
