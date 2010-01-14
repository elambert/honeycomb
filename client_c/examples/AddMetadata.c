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



/* AddMetadata adds a metadata record to existing data.	*/
/* a @HoneycombProductName@ server. */

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>

/* Common header files */
#include "example_commandline.h"
#include "example_common.h"
#include "example_metadata.h"

static char METADATA_SEPERATOR_CHARACTERS[] = "=";

/* Local functions definitions */
int exitApp(int returnCode);
int loadMetadataFromFile();
void printUsage();

/* Main entry point for this application */
int main(int argc, char* argv[])
{
	hc_session_t *session = NULL;
	hc_nvr_t *nvr = NULL;

	/* Send metadata to @HoneycombProductName@ */
	hc_system_record_t system_record;

	hcerr_t	res;
	int parametersUsed = USES_SERVERADDRESS | USES_CMDLINE_METADATA | USES_OID;

	/* Initialize commandline structure */
	cmdLine.help = 0;	
	*cmdLine.oid = 0;
	cmdLine.storagetekServerAddress = NULL;
	cmdLine.metadataFilename = NULL;

        /* Initialize metadata map. The metadata map structure is not part of the API but common */
        /* code written for the convenience of these examples. See example_metadata.c. */
	if (initMetadataMap(&cmdLine.cmdlineMetadata) == 0)
	{
		return exitApp(RETURN_MAPINITERROR);
	}	/* if initMetadataMap == 0 */

	/* Get commandline (see example_commandline.c) */
	if (parseCommandline(	argc, 
				argv,
				parametersUsed) == 0)
	{
		printUsage();

		/* Exit */
		return exitApp(RETURN_COMMANDLINE_ERROR);
	}	/* if parseCommandline failed */
	else if (cmdLine.help == 1)
	{
		printUsage();

		/* Exit */
		return exitApp(RETURN_SUCCESS);
	}	/* else if help requested */

        /* Initialize @HoneycombProductName@ API */
	res = hc_init(malloc,free,realloc);
	if (res != HCERR_OK)
	{
		printf("An error occurred while initializing the API.\n");
		return res;
	}  /* if error */
	res = hc_session_create_ez(cmdLine.storagetekServerAddress, 
				   (cmdLine.storagetekPort > 0 ? 
				    cmdLine.storagetekPort : 
				    STORAGETEK_PORT),
				   &session);

	if (res != HCERR_OK)
	{
		HandleError(session, res);	
		return res;
	}  /* if error */

	/* Create a name-value record from the names and values passed in on the commandline */	
	res = hc_nvr_create_from_string_arrays(	session, 
						&nvr,
						cmdLine.cmdlineMetadata.namePointerArray, 
						cmdLine.cmdlineMetadata.valuePointerArray, 
						cmdLine.cmdlineMetadata.mapSize);
	if (res != HCERR_OK)
	{
		HandleError(session, res);
                hc_session_free(session);
                hc_cleanup();
		return res;
	}  /* if error */

	/* Send metadata to the @HoneycombProductName@ server */
	res = hc_store_metadata_ez (	session, 
					&cmdLine.oid,
					nvr,
					&system_record);

	if (res != 0)
	{
		HandleError(session, res);
       		hc_session_free(session);
        	hc_cleanup();
		return res;
	}  /* if error */

	/* Return the OID of the new @HoneycombProductName@ record */
	printf("%s\n", system_record.oid);

	/* Let @HoneycombProductName@ clean up */
	hc_session_free(session);
	hc_cleanup();

	/* Exit */
	return exitApp(RETURN_SUCCESS);
}	/* main */

int exitApp(int returnCode)
{
	/* Clean up metadata map */
	if (returnCode != RETURN_MAPINITERROR)
	{
		destroyMetadataMap(&cmdLine.cmdlineMetadata);
	}	/* if no map init error */

	return returnCode;
}	/* exitApp */

/* Loads metadata from the file specified on the commandline into */
/* cmdLine.cmdlineMetadata */
int loadMetadataFromFile()
{
	/* Arbitrary maximum line size */
	char fileLine[MAX_LINESIZE];

	/* Open file */
	FILE *metadataFile = fopen(cmdLine.metadataFilename, "r");
	if (metadataFile == NULL)
	{
		return 0;
	}	/* if metadataFile NULL */
	
	/* Read file metadata into metadata map */
	while (fgets(fileLine, MAX_LINESIZE, metadataFile) != NULL)
	{
		char *name = strtok(fileLine, METADATA_SEPERATOR_CHARACTERS);

		/* Get rid of any end of line characters */
		char *endOfLine = strchr(fileLine, '\n');
		if (endOfLine)	
		{
			*endOfLine = '\0';
		}	/* if endofline found */

		/* Break apart into name and value */
		if (name != NULL)
		{
			char *value = strtok(NULL, METADATA_SEPERATOR_CHARACTERS);
			if (value != NULL)
			{
				/* Add to metadata map */
				addToMetadataMap(&cmdLine.cmdlineMetadata, name, value);
			}	/* if value not null */
			else
			{
				return 0;
			}	/* else value null */
		}	/* if name not null */
		else
		{
			return 0;
		}	/* if name null */
	}	/* While text retrieved from the file is not NULL */

	return 1;
}	/* loadMetadata */

/* Prints out the command line help to standard output. */
void printUsage()
{
	printf("NAME\n");
	printf("       AddMetadata - Adds a metadata record to existing data\n");
	printf("\n");
	printf("SYNOPSIS\n");
	printf("       AddMetadata <IP | HOST>  <OID>  [OPTIONS]\n");
	printf("\n");
	printf("DESCRIPTION\n");
	printf("       Adds a metadata record to existing data.\n");
	printf("\n");
	printf("OPTIONS\n");
	printf("       -m <name>=<value>\n");
	printf("              Any number of --metadata options can be specified. Each option\n");
	printf("              specifies a single (name,value) pair. <name> should be specified\n");
        printf("              in the format <namespace>.<attribute> . Use double\n");
        printf("              quotes if <value> is a string containing spaces.\n");
	printf("\n");
	printf("       -h\n");
	printf("              print this message\n");
	printf("\n");
	printf("EXAMPLES\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m filesystem.mimetype=image/jpeg\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m system.test.type_char=\"do re mi\"\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m system.test.type_string=\"fa so la\"\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m system.test.type_long=123\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m system.test.type_double=1.23\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m system.test.type_date=1992-10-27\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m system.test.type_time=23:30:29\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m system.test.type_timestamp=\"1992-10-27T23:30:29\"\n");
	printf("       AddMetadata server 0200004f75ee01094cc13e11dbbad000e08159832d000024d40200000000 -m name1=value1 -m name2=\"value 2\"\n");
}	/* printUsage */
