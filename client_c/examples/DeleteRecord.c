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
 *  DeleteRecord deletes an object with a specified by an object ID
 *  from a specified @HoneycombProductName@ server.
 */

#include <fcntl.h>
#include <stdlib.h>

/* Common header files */
#include "example_common.h"
#include "example_commandline.h"

/* Local functions definitions */
int exitApp(int returnCode);
int loadMetadataFromFile();
void printUsage();

int main(int argc, char* argv[])
{
	hc_session_t *session = NULL;
	hcerr_t	result = HCERR_OK;
	int parametersUsed = USES_SERVERADDRESS | USES_OID | USES_VERBOSE;

	/* Initialize commandline structure */
	cmdLine.help = 0;	
	cmdLine.storagetekServerAddress = NULL;
	*cmdLine.oid = 0;
	cmdLine.verbose = 0;

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
        result = hc_init(malloc,free,realloc);
        if (result != HCERR_OK)
        {
                printf("An error occurred while initializing the API.\n");
                return result;
        }  /* if error */
       if (cmdLine.debug_flags) {
		/* Internal debug flags */
		hc_set_global_parameter(HCOPT_DEBUG_FLAGS, cmdLine.debug_flags);
       }

	/* Create a session handle */
	result = hc_session_create_ez(	cmdLine.storagetekServerAddress, 
					(cmdLine.storagetekPort > 0 ? 
					 cmdLine.storagetekPort : 
					 STORAGETEK_PORT),
					&session);
	
	if (HCERR_OK != result)
	{
		printf("Error occurred while creating @HoneycombProductName@ session: %d %s.\n",
		       result, hc_decode_hcerr(result));
		return result;
	}
	/* Delete object from @HoneycombProductName@ server */
	result = hc_delete_ez(	session, 
				&cmdLine.oid);

	if (HCERR_OK != result)
	{
		HandleError(session, result);
                hc_session_free(session);
		hc_cleanup();
		return RETURN_STORAGETEK_ERROR;
	}	/* if error occurred */

	if (cmdLine.verbose == 1)
	{
		printf("Deleting %s\n", (char *)&cmdLine.oid);
	} 	/* if verbose mode */ 

	/* End session */
	hc_session_free(session);
	hc_cleanup();

	return RETURN_SUCCESS;
}  /* main */

/**
* printUsage prints out the command line help to standard output.
*/        
void printUsage()
{
    printf("NAME\n");
    printf("       DeleteRecord - Delete a record associated with an OID\n");
    printf("\n");
    printf("SYNOPSIS\n");
    printf("       DeleteRecord <IP | HOST> <OID> [OPTIONS]\n");
    printf("\n");
    printf("DESCRIPTION\n");
    printf("       Delete record.  The OID specifies which record to delete.\n");
    printf("       The record consists of all metadata associated with the OID\n");
    printf("       or the data if it is a data OID. The OID itself becomes\n");
    printf("       inaccessible. If this is the last OID associated with the\n");
    printf("       data, the data is deleted too.\n");
    printf("\n");
    printf("OPTIONS\n");
    printf("       -v\n");
    printf("              print deleted OID to stdout\n");
    printf("\n");
    printf("       -h\n");
    printf("              print this message\n");
    printf("\n");
    printf("EXAMPLES\n");
    printf("       DeleteRecord server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000\n");
}   /* printUsage */
