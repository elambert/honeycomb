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
 *  The RetrieveMetadata application retrieves a metadata record
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
	int count = 0;

	hcerr_t	hcError = 0;
	hc_session_t *session = NULL;
	hc_nvr_t *nvr = NULL;

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

	/* Retrieve metadata from the @HoneycombProductName@ server */
	hcError = hc_retrieve_metadata_ez(session, &cmdLine.oid, &nvr);
	if (hcError != HCERR_OK)
	{
		HandleError(session, hcError);
		hc_session_free(session);
		hc_cleanup();
		return RETURN_STORAGETEK_ERROR;
	}	/* if error occurred */

	hcError = hc_nvr_convert_to_string_arrays(nvr,
						  &names,
						  &values,
						  &count);

	if (hcError != HCERR_OK)
	{
		HandleError(session, hcError);
                hc_session_free(session);
                hc_cleanup();
		return RETURN_STORAGETEK_ERROR;
	}	/* if error occurred */

	if (count == 0)
	{
		hc_session_free(session);
		hc_cleanup();
		printf("No metadata returned.\n");
		return RETURN_STORAGETEK_ERROR;
	}	/* if count is 0 */

	/* Print out metadata record to standard output */
	if (printMetadataRecord(names, values, count) == 0)
	{
		printf("Null or invalid metadata returned.\n");
                hc_session_free(session);
                hc_cleanup();
		return RETURN_STORAGETEK_ERROR;
	}	/* if error printing out metadata record */

	hc_session_free(session);
	hc_cleanup();

	return RETURN_SUCCESS;
}	/* main */

void printUsage()
{
    printf("NAME\n");
    printf("       RetrieveMetadata - retrieve metadata\n");
    printf("\n");
    printf("SYNOPSIS\n");
    printf("       RetrieveMetadata <IP | HOST> <OID> [OPTIONS]\n");
    printf("\n");
    printf("DESCRIPTION\n");
    printf("       Retrieve metadata from @HoneycombProductName@. The OID specifies what data to retrieve.\n");
    printf("       Metadata is printed to stdout.\n");
    printf("\n");
    printf("OPTIONS\n");
    printf("       -h, --help\n");
    printf("              print this message\n");
    printf("\n");
    printf("EXAMPLES\n");
    printf("       RetrieveMetadata archivehost 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000\n");
    printf("       RetrieveMetadata 10.152.0.12 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000\n");
} /* printUsage */
