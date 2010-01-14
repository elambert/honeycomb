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



/**
 *  The CheckIndexed application forces queryability of a metadata record
 *  from a specified @HoneycombProductName@ server.
 */

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>

/* Common header files */
#include "example_common.h"
#include "example_commandline.h"
#include "example_metadata.h"

/* Local functions definitions */
void printUsage();

int main(int argc, char* argv[])
{
	char **names = NULL;
	char **values = NULL;
	int indexed = 0;

	hcerr_t	hcError = 0;
	hc_session_t *session = NULL;

	int parametersUsed = USES_SERVERADDRESS | USES_OID;

	/* Initialize commandline structure */
	cmdLine.help = 0;	
	*cmdLine.oid = 0;
	cmdLine.storagetekPort = 0;
	cmdLine.storagetekServerAddress = NULL;

	/* Get commandline (see example_commandline.c) */
	if (parseCommandline(	argc, 
				argv,
				parametersUsed) == 0)
	{
		printUsage();
		return RETURN_COMMANDLINE_ERROR;
	}	/* if parseCommandline failed */
	else
	{
		if (cmdLine.help == 1)
		{
			printUsage();
			return RETURN_SUCCESS;
		}	/* if help requested */
	}	/* else parseCommandline succeeded */

	/* Initialize @HoneycombProductName@ API */
	hcError = hc_init(malloc,free,realloc);
	if (hcError != HCERR_OK)
	{
		printf("An error occurred while initializing @HoneycombProductName@: %s.\n", hc_decode_hcerr(hcError));
		return RETURN_STORAGETEK_ERROR;
	}	/* if error occurred */
       if (cmdLine.debug_flags) {
		/* Internal debug flags */
		hc_set_global_parameter(HCOPT_DEBUG_FLAGS, cmdLine.debug_flags);
       }

	hcError = hc_session_create_ez(cmdLine.storagetekServerAddress,
					(cmdLine.storagetekPort > 0 ? 
					 cmdLine.storagetekPort : 
					 STORAGETEK_PORT),
				       &session);
	
	if (HCERR_OK != hcError)
	{
		HandleError(session, hcError);
		return hcError;
	}

	/* Check Indexed status for this OID on the 
	   @HoneycombProductName@ server */
	hcError = hc_check_indexed_ez(session, &cmdLine.oid, &indexed);
	if (hcError != HCERR_OK)
	{
		HandleError(session, hcError);
		hc_session_free(session);
		hc_cleanup();
		return RETURN_STORAGETEK_ERROR;
	}	/* if error occurred */

	printf("Object %s %s\n",
	       &cmdLine.oid,
	       (indexed < 0 ? "was already indexed" :
	       (indexed > 0 ? "has now been indexed" :
	       "not yet indexed")));

	hc_session_free(session);
	hc_cleanup();

	return RETURN_SUCCESS;
}	/* main */

void printUsage()
{
    printf("NAME\n");
    printf("       CheckIndexed - check queryability and force queryability\n");
    printf("\n");
    printf("SYNOPSIS\n");
    printf("       CheckIndexed <IP | HOST> <OID> [OPTIONS]\n");
    printf("\n");
    printf("DESCRIPTION\n");
    printf("        Check queryability of an object in @HoneycombProductName@.\n");
    printf("        The OID specifies which object to check the queryability of.\n");
    printf("        Attempts to force the object to become queryable if not already.\n");
    printf("        Retry until the object is queryable.\n");
    printf("\n");
    printf("OPTIONS\n");
    printf("       -h, --help\n");
    printf("              print this message\n");
    printf("\n");
    printf("EXAMPLES\n");
    printf("       CheckIndexed archivehost 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000\n");
    printf("       CheckIndexed 10.152.0.12 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000\n");
} /* printUsage */
