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



#include "cmm.h"

#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>

static void
usage()
{
    printf( "Usage : cmm_qualif -n <nodeid> [-d]\n\n"
            "\tnodeid\tis the node id on which the operation is performed\n"
            "\t-d\tspecifies that this is a disqualification (the default is qualification)"
            "\n\n" );

    exit(1);
}

int
main( int argc,
      char *argv[] )
{
    int c;
    char qualification;
    cmm_nodeid_t nodeid = CMM_INVALID_NODE_ID;
    cmm_error_t err;

    qualification = 1;

    while ( (c=getopt(argc, argv, "n:d")) != EOF ) {
        switch (c) {
        case 'n' :
            nodeid = atoi( argv[optind-1] );
            break;

        case 'd' :
            qualification = 0;
            break;
        }
    }

    if (nodeid == CMM_INVALID_NODE_ID) {
        usage();
    }

    if (qualification) {
        err = cmm_member_setqualif( nodeid, CMM_QUALIFIED_MEMBER);
    } else {
        err = cmm_member_setqualif(nodeid, CMM_DISQUALIFIED_MEMBER);
    }

    cmm_disconnect();

    if (err != CMM_OK) {
        fprintf( stderr,
                 "cmm_member_setqualif failed [%d]\n",
                 err );
        exit(0);
    }

    printf( "The qualification operation has been performed correctly\n");
    return(0);
}
               
