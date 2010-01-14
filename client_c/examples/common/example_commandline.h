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



/* parseCommandline handles commandline parsing for the @HoneycombProductName@ C */
/* API examples. */

#ifndef _EXAMPLE_COMMANDLINE
#define _EXAMPLE_COMMANDLINE

#include "example_common.h"
#include "example_metadata.h"

static const int USES_SERVERADDRESS = 1;
static const int USES_METADATA_FILENAME = 2;
static const int USES_QUERY = 4;
static const int USES_LOCAL_FILENAME = 8;
static const int USES_OID = 16;
static const int USES_VERBOSE = 32;
static const int USES_MAXRESULTS = 64;
static const int USES_SELECT_METADATA = 128;
static const int USES_CMDLINE_METADATA = 256;

/* Contains the information extracted from the command line */
struct Commandline
{
        char *storagetekServerAddress;
	int  storagetekPort;
        char *metadataFilename;
        char *query;
        char *localFilename;
        char oid[61];
        int verbose;
        int maxResults;
        int outputMetadata;
        int help;
	int debug_flags;
        struct MetadataMap cmdlineMetadata;
};      /* struct Commandline */

struct Commandline cmdLine;

extern int parseCommandline(	int argc,
       		                char* argv[],
				int acceptedParameters);

#endif
