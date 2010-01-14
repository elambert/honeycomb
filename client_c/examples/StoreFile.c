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



/* StoreFile.exe allows you to store a file and associated metadata to	*/
/* a @HoneycombProductName@ server.*/

#define _LARGEFILE_SOURCE
#define _LARGEFILE64_SOURCE
#define _FILE_OFFSET_BITS 64

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>

/* Common header files */
#include "example_common.h"
#include "example_commandline.h"

/* Local functions definitions */
int exitApp(int returnCode);
int loadMetadataFromFile();
void printUsage();

/* Callback function required by hc_store_both_ez.  @HoneycombProductName@ wants */
/* a stream of data to store to its server.  In this case the stream */
/* of data is coming from a file.										*/
long read_from_file(void* stream, char* buff, long n)
{
    long nbytes;

    nbytes = read((int) stream, buff, n);
    return nbytes;
}	/* read_from_file */

/* Main entry point for this application */
int main(int argc, char* argv[])
{
	int fileToStore = -1;
	hc_session_t *session = NULL;
	hc_nvr_t *nvr = NULL;

	int parametersUsed = USES_SERVERADDRESS | USES_CMDLINE_METADATA | USES_LOCAL_FILENAME;

	/* Initialize commandline structure */
	cmdLine.help = 0;	
	cmdLine.localFilename = NULL;
	cmdLine.storagetekServerAddress = NULL;

	if (initMetadataMap(&cmdLine.cmdlineMetadata) == 0)
	{
		return exitApp(RETURN_MAPINITERROR);
	}	/* if initMetadataMap == 0 */

	/*	 Get commandline */
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

	/* Open file that is going to be stored to @HoneycombProductName@ */	
	if ((fileToStore = open(cmdLine.localFilename, O_RDONLY | FLAG_BINARY | FLAG_LARGEFILE)) == -1) 
	{
		printf("The file '%s' could not be found.\n", cmdLine.localFilename);

		/* Exit */
		return exitApp(RETURN_IOERROR);
	}	/* if file did not open */
	else
	{
		/* Send file and metadata to @HoneycombProductName@ */
		hc_system_record_t system_record;

		hcerr_t	res;
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
			HandleError(session, res);
			return res;
		}  /* if error */
	
		res = hc_nvr_create_from_string_arrays(session, 
						       &nvr,
						       cmdLine.cmdlineMetadata.namePointerArray, 
						       cmdLine.cmdlineMetadata.valuePointerArray, 
						       cmdLine.cmdlineMetadata.mapSize);
		if (res != HCERR_OK)
		{
			HandleError(session, res);
			return res;
		}  /* if error */

		/* Store data and metadata to the @HoneycombProductName@ server */
		res = hc_store_both_ez (session, 
					&read_from_file, 
					(void *)fileToStore, 
					nvr,
					&system_record);
		close(fileToStore);

		if (res != 0)
		{
			HandleError(session, res);
                        hc_session_free(session);
                        hc_cleanup();
			return res;
		}  /* if error */

		/* Return the OID of the new @HoneycombProductName@ record */
		printf("%s\n", system_record.oid);
		printf("  <ctime=%lld size=%lld is_indexed=%d>\n",
		       system_record.creation_time,
		       system_record.size,
		       system_record.is_indexed);
	}	/* else file did open */

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

/* Prints out the command line help to standard output. */
void printUsage()
{
	printf("NAME\n");
	printf("       StoreFile - store a file\n");
	printf("\n");
	printf("SYNOPSIS\n");
	printf("       StoreFile <IP | HOST>  <FILE>  [OPTIONS]\n");
	printf("\n");
	printf("DESCRIPTION\n");
        printf("       Store a file and associated metadata record. If no -m options are\n");
        printf("       specified, a metadata record without user content is generated.\n");
	printf("       The OID of the metadata record is printed to stdout.\n");
	printf("\n");
	printf("OPTIONS\n");
	printf("       -m \"<name>=<value>\"\n");
	printf("              Any number of --metadata options can be specified. Each option\n");
	printf("              specifies a single (name,value) pair. <name> should be specified\n");
        printf("              in the format <namespace>.<attribute>. Use double\n");
        printf("              quotes if <value> is a string containing spaces.\n");
	printf("\n");
	printf("       -h\n");
	printf("              print this message\n");
	printf("\n");
	printf("EXAMPLES\n");
	printf("       StoreFile server /var/log/messages\n");
	printf("       StoreFile server ~/journal\n");
	printf("       StoreFile server myfile.jpg -m filesystem.mimetype=image/jpeg\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_char=\"do re mi\"\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_string=\"fa so la\"\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_long=123\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_double=1.23\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_binary=0789abcdef\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_date=2010-10-20\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_time=23:30:29\n");
        printf("       StoreFile 10.152.0.12 myfile -m system.test.type_timestamp=\"2010-10-20T23:30:29.999\"\n");
        printf("       StoreFile 10.152.0.12 myfile -m name1=value1 -m name2=\"value 2\"\n");
}	/* printUsage */
