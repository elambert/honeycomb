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
 *  Query allows you to query metadata on a @HoneycombProductName@ server.  The results
 *  are returned to standard output.
 */

#include <fcntl.h>
#include <stdlib.h>

/* Common header files */
#include "example_common.h"
#include "example_commandline.h"
#include "example_metadata.h"

/* Local functions definitions */
void printUsage();
void printOIDResults(char *oid);
int printMetadataResults(hc_nvr_t *nvr, hc_session_t *session);
int exitApp(int returnCode);

int main(int argc, char* argv[])
{
	hc_session_t *session = NULL;

	hc_oid returnedOid;
	hc_long_t count = 0;
	int finished = 0;
	int32_t response_code = RETURN_SUCCESS;
	hc_query_result_set_t *rset = NULL;
	hc_nvr_t *nvr = NULL;
	hc_long_t query_integrity_time;
	int query_complete;

	
	hcerr_t	res = HCERR_OK;

	int parametersUsed = USES_SERVERADDRESS | USES_QUERY | USES_SELECT_METADATA | USES_MAXRESULTS;

	/* Initialize commandline structure */
	cmdLine.storagetekServerAddress = NULL;
	cmdLine.query = NULL;
	cmdLine.help = 0;
	cmdLine.debug_flags = 0;
	cmdLine.storagetekPort = 0;
	cmdLine.maxResults = DEFAULT_MAX_RESULTS;

	/* Initialize metadata map.  The metadata map structure is not part of the API but common */
	/* code written for the convenience of these examples.  See example_metadata.c. */
        if (initMetadataMap(&cmdLine.cmdlineMetadata) == 0)
        {
                return exitApp(RETURN_MAPINITERROR);
        }       /* if initMetadataMap == 0 */

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
        res = hc_init(malloc,free,realloc);
        if (res != HCERR_OK)
        {
                printf("An error occurred while initializing the API.\n");
                return res;
        }  /* if error */

       if (cmdLine.debug_flags) {
		/* Internal debug flags */
		hc_set_global_parameter(HCOPT_DEBUG_FLAGS, cmdLine.debug_flags);
       }

	res = hc_session_create_ez(cmdLine.storagetekServerAddress, 
				(cmdLine.storagetekPort > 0 ? 
				 cmdLine.storagetekPort : 
				 STORAGETEK_PORT),
				&session);
	
	if (res != HCERR_OK)
	{
		HandleError(session,res);
		return RETURN_STORAGETEK_ERROR;
	}	/* if initialization failed */

	/* Run queryplus if we have a select clause (-s option on the commandline), otherwise run query */
	if (cmdLine.cmdlineMetadata.mapSize > 0)
	{
		res = hc_query_ez(session,
				cmdLine.query,
				cmdLine.cmdlineMetadata.namePointerArray,	
				cmdLine.cmdlineMetadata.mapSize,
                                100,
				&rset);
	}	/* if outputing metadata */
	else
	{	
		res = hc_query_ez(session,
				cmdLine.query,
                                NULL,
                                0,
                                100,
				&rset);
	}

	if (res != HCERR_OK)
        {
                HandleError(session, res);
                hc_session_free(session);
                hc_cleanup();
                return RETURN_STORAGETEK_ERROR;
        }       /* if not successful */

	/* Loop up until the maximum result size */
	for (count = 0; count < cmdLine.maxResults; count++) 
	{
		/* Get the next result */
		res = hc_qrs_next_ez(rset, &returnedOid, &nvr, &finished);	

 		if (res != HCERR_OK)
   		{
			HandleError(session, res);
			hc_session_free(session);
			hc_cleanup();
			return RETURN_STORAGETEK_ERROR;
    		}	/* if not successful */

                if (finished)
                        break;

		/* Print the next result to standard output */
		if (cmdLine.cmdlineMetadata.mapSize > 0)
		{
			printMetadataResults(nvr, session);
		}
		else
		{
    			printOIDResults((char *) &returnedOid);
		}
	}	/* loop through results */

	res = hc_qrs_is_query_complete(rset,&query_complete);
	if (res != HCERR_OK) 
	{
    		HandleError(session, res);
    		response_code = RETURN_STORAGETEK_ERROR;
	}
	res = hc_qrs_get_query_integrity_time(rset,&query_integrity_time);
	if (res != HCERR_OK) 
	{
    		HandleError(session, res);
    		response_code = RETURN_STORAGETEK_ERROR;
	}

	printf("Query Integrity Status %s at time %lld\n",
	       query_complete ? "true" : "false",
	       query_integrity_time);

        res = hc_qrs_free(rset);

	if (res != HCERR_OK) 
	{
    		HandleError(session, res);
    		response_code = RETURN_STORAGETEK_ERROR;
	}

	hc_session_free(session);
	hc_cleanup();

        return exitApp(response_code);
}	/* main */

int exitApp(int returnCode)
{
        /* Clean up metadata map */
        if (returnCode != RETURN_MAPINITERROR)
        {
                destroyMetadataMap(&cmdLine.cmdlineMetadata);
        }       /* if no map init error */

        return returnCode;
}       /* exitApp */

void printOIDResults(char *oidResult)
{
	/* print OIDs to std output */
	printf("%s\n", oidResult);	
}  /* printOIDResults */

/**
* Prints out the name value records of the matching results.
*/
int printMetadataResults(hc_nvr_t *nvr, hc_session_t *session)
{
	char **names = NULL;
	char **values = NULL;
	int count = 0;

	hcerr_t hcError = hc_nvr_convert_to_string_arrays(nvr,
							  &names,
							  &values,
							  &count);

	if (hcError != HCERR_OK)
	{
		HandleError(session, hcError);	
		hc_session_free(session);
		return RETURN_STORAGETEK_ERROR;
	}	/* if error occurred */

	/* Print metadata record. printMetadataRecord is example specific code (in example_metadata.h) */
	/* and is not part of the @HoneycombProductName@ API. */
	printMetadataRecord(names, values, count);
	
	return RETURN_SUCCESS;
}   /* printMetadataResults */

/**
* printUsage prints out the command line help to standard output.
*/        
void printUsage()
{
    printf("NAME\n");
    printf("       Query - Query for metadata records\n");
    printf("\n");
    printf("SYNOPSIS\n");
    printf("       Query <IP | HOST>  <QUERY>  [OPTIONS]\n");
    printf("\n");
    printf("DESCRIPTION\n");
    printf("       Query for metadata records. QUERY is of the form:\n");
    printf("              \"<name1>='<value1>' AND <name2>='<value2>' OR ...\"\n");
    printf("       The OID of files which match the query are printed to stdout.\n");
    printf("       <name> should be specified in the format <namespace>.<attribute>\n");
    printf("\n");
    printf("       Note that names that are keywords need to be enclosed in\n");
    printf("       escaped double quotes (\"\\\"<name>\\\"='<value>'\"). (See list of\n");
    printf("       keywords in Chapter 4 of the Client API Reference Guide.) Also\n");
    printf("       note that some shells (csh) may not accept the escaped quotes\n");
    printf("       because they are embedded in other quotes.\n");
    printf("\n");
    printf("OPTIONS\n");
    printf("       -s <FIELD>, \n");
    printf("              Print out results as metadata name/value records.\n");
    printf("              Use as many -s switches as needed to define all\n");
    printf("              fields that will be printed to stdout.\n");
    printf("       -r   <number of results>\n");
    printf("              The maximum number of metadata records or OIDs\n");
    printf("              that will be returned. The default is 1000.\n");
    printf("       -h\n");
    printf("              print this message\n");
    printf("\n");
    printf("EXAMPLES\n");
    printf("       Note: 'first' and 'do' are keywords.\n\n");
    printf("       Query archivehost \"book.author='King'\"\n");
    printf("       Query archivehost \"\\\"first\\\"='a'\"\n");
    printf("       Query archivehost system.test.type_char=\"'do re mi'\"\n");
    printf("       Query archivehost system.test.type_string=\"'fa so la'\"\n");
    printf("       Query archivehost system.test.type_long=123\n");
    printf("       Query archivehost system.test.type_double=1.23\n");
    printf("       Query archivehost system.test.type_binary=\"x'0789abcdef'\"\n");
    printf("       Query archivehost system.test.type_date=\"'2010-10-20'\"\n");
    printf("       Query archivehost system.test.type_time=\"'22:10:29'\"\n");
    printf("       Query archivehost system.test.type_timestamp=\"{timestamp'2010-10-20T23:30:29.123Z'}\"\n");
    printf("       Query 10.152.0.12 \"mp3.artist='The Beatles' AND mp3.album='Abbey Road'\"\n");
    printf("       Query 10.152.0.12 \"mp3.artist='The Beatles'\" -s mp3.album -s mp3.title\n");
    printf("       Query 10.152.0.12 \"system.test.type_timestamp={timestamp '1952-10-27T08:30:29.999Z'}\"\n");
}   /* printUsage */
