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
 *  RetrieveSchema returns the schema of a specified @HoneycombProductName@ server
 *  to standard output.
 */

#include <stdlib.h>
#include <string.h>

/* Common header files */
#include "example_common.h"
#include "example_commandline.h"

/* Local functions definitions */
void printUsage();
int printSchema(hc_schema_t *current_schema);

int main(int argc, char* argv[])
{
	hc_session_t *session = NULL;
	hcerr_t	result = HCERR_OK;
        hc_schema_t *current_schema = NULL;
	int parametersUsed = USES_SERVERADDRESS;

	/* Initialize commandline structure */
	cmdLine.help = 0;
	cmdLine.storagetekServerAddress = NULL;

	/* Parse commandline (see example_commandline.c) */
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

	/* Create a session handle for @HoneycombProductName@ */
	result = hc_session_create_ez(	cmdLine.storagetekServerAddress, 
					(cmdLine.storagetekPort > 0 ? 
					 cmdLine.storagetekPort : 
					 STORAGETEK_PORT),
					&session);

	if (HCERR_OK != result)
	{
                HandleError(session, result);
                return result;
	}

	/* Get the schema from @HoneycombProductName@ server */
	result = hc_session_get_schema(session, &current_schema);

	if (result != HCERR_OK)
	{
		HandleError(session, result);
                hc_session_free(session);
                hc_cleanup();
		return RETURN_STORAGETEK_ERROR;
	}	/* if error occurred */

	/* Print out the schema */
	if (printSchema(current_schema) == 0)
	{
		printf("Invalid schema returned.\n");
                hc_session_free(session);
                hc_cleanup();
		return RETURN_STORAGETEK_ERROR;
	}	/* if printing schema failed */

	/* End this session */	
	hc_session_free(session);
	hc_cleanup();

	return 0;
}	/* main */

/**
* printSchema prints out the current schema to standard output.
*/
int printSchema(hc_schema_t *current_schema)
{
	hc_long_t recordIndex = 0;
	hc_long_t count;
	hcerr_t result;
	char *name;
	hc_type_t type;

	/* Validate pointers */
	if (current_schema == NULL)
	{
		return 0;
	}	/* if any pointers not valid */


	result = hc_schema_get_count(current_schema, &count);
	if (result != HCERR_OK) {
		return 0;
	}

	/* Loop through the list of attributes */
	for (recordIndex = 0; recordIndex < count; recordIndex++)
	{
		result = hc_schema_get_type_at_index(current_schema,recordIndex,&name,&type);
		if (result != HCERR_OK) 
		{
			return 0;
		}
		/* Print out name/type information */
		printf("%s [%s]\n", name, hc_decode_hc_type(type));
	}   /* loop through all name/value pairs */

	return 1;
}   /* printSchema */

/**
* printUsage prints out the command line help to standard output.
*/        
void printUsage()
{
    printf("NAME\n");
    printf("       RetrieveSchema - print metadata attributes\n");
    printf("\n");
    printf("SYNOPSIS\n");
    printf("       RetrieveSchema <IP | HOST> [OPTIONS]\n");
    printf("\n");
    printf("OPTIONS\n");
    printf("       -h, --help\n");
    printf("              print this message\n");
    printf("\n");
    printf("EXAMPLES\n");
    printf("       RetrieveSchema archivehost\n");
}   /* printUsage */
