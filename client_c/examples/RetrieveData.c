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
 *  The RetrieveData application retrieves data from a 
 *  specified @HoneycombProductName@ server to the specified file
 */

#define _LARGEFILE_SOURCE
#define _LARGEFILE64_SOURCE
#define _FILE_OFFSET_BITS 64

#include <fcntl.h>
#include <stdlib.h>

/* Common header files */
#include "example_common.h"
#include "example_commandline.h"

/* Local functions definitions */
void printUsage();

/* Callback function required by hc_retrieve_ez.  @HoneycombProductName@ wants */
/* a stream of data to store to its server.  In this case the stream */
/* of data is coming from a file. */
long write_to_file(void* stream, char* buff, long n)
{
	int pos = 0;
	while (pos < n)
	{
		int i = write ((int) stream, buff + pos, n - pos);
		if (i < 0)
			return i;
		if (i == 0)
			break;
		pos += i;
	}
	
	return pos;
}

int main(int argc, char* argv[])
{
	hcerr_t	hcError = 0;
	hc_session_t *session = NULL;

	int fileToRetrieve = -1;
	int parametersUsed = USES_SERVERADDRESS | USES_OID | USES_LOCAL_FILENAME;

	/* Initialize commandline structure */
	cmdLine.help = 0;	
	cmdLine.localFilename = NULL;
	cmdLine.storagetekServerAddress = NULL;
	*cmdLine.oid = 0;

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
		printf("Error %d occurred while initializing @HoneycombProductName@.\n", hcError);
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

	/* Open file that is going to contain the @HoneycombProductName@ data*/	
	if ((fileToRetrieve = open(cmdLine.localFilename, O_CREAT | O_WRONLY | FLAG_BINARY | FLAG_LARGEFILE, 0666)) == -1) 
	{
		hc_session_free(session);
		hc_cleanup();
		printf("Failed to open data file '%s'\n", cmdLine.localFilename);
		return RETURN_IOERROR;
	}	/* if file did not open */
	else
	{
		/* Download @HoneycombProductName@ data into file */
		hcError = hc_retrieve_ez(session,
					 &write_to_file, 
					 (void *)fileToRetrieve,
					 &cmdLine.oid);

		if (hcError != HCERR_OK)
		{
			HandleError(session, hcError);
                        hc_session_free(session);
                        hc_cleanup();
			return RETURN_IOERROR;
		}	/* if there was an error */
	}	/* else file opened */

	hc_session_free(session);
	hc_cleanup();

	return RETURN_SUCCESS;
}	/* main */

void printUsage()
{
    printf("NAME\n");
    printf("       RetrieveData - retrieve data (and metadata)\n");
    printf("\n");
    printf("SYNOPSIS\n");
    printf("       RetrieveData <IP | HOST> <OID> <FILE> [OPTIONS]\n");
    printf("\n");
    printf("DESCRIPTION\n");
    printf("       Retrieve data from @HoneycombProductName@. The OID specifies what data to retrieve.\n");
    printf("       Metadata is printed to stdout. Data is written to FILE\n");
    printf("\n");
    printf("OPTIONS\n");
    printf("       -h, --help\n");
    printf("              print this message\n");
    printf("\n");
    printf("EXAMPLES\n");
    printf("       RetrieveData storagetek 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 /archive/log.1\n");
} /* printUsage */
