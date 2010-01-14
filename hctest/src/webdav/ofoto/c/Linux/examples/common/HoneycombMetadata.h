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



/* HoneycombMetadata contains a group of functions to work with a	*/
/* metadata memory map that dynamically allocates memory for the	*/
/* name/value pairs of metadata.  They also keep pointer arrays		*/
/* that point to all the metadata names and metadata values.  These	*/
/* pointer arrays are passed in to the Honeycomb API calls that		*/
/* take metadata.							*/

/* Metadata map constants */
static const int INITIAL_METADATA_NAME_SIZE = 10000;
static const int INITIAL_METADATA_VALUE_SIZE = 20000;
static const int METADATA_NAME_GROW_SIZE = 10000;
static const int METADATA_VALUE_GROW_SIZE = 20000;

static const int INITIAL_METADATA_MAP_CAPACITY = 20;
static const int METADATA_MAP_GROW_SIZE = 20;

/* structure describing the dynamically allocated metadata map */
struct MetadataMap
{
	char *mappedName;			/* pointer to the memory allocated for metadata names */
	char *mappedValue;			/* pointer to the memory allocated for metadata valuse */
	int nameSize;				/* number of character the name memory blob currently holds */
	int nameCapacity;			/* the maximum number of character the name memory blob can hold */
	int valueSize;				/* number of character the value memory blob currently holds */
	int valueCapacity;			/* the maximum number of character the value memory blob can hold */
	int mapSize;				/* number of name/value pairs the map currently holds */
	int mapCapacity;
	
	char **namePointerArray;		/* an array of pointers that point to all the names in the map */
	char **valuePointerArray;		/* an array of pointers that point to all the values in the map */
};

/* Metadata map external functions */
extern int initMetadataMap(struct MetadataMap *mdm);
extern int destroyMetadataMap(struct MetadataMap *mdm);
extern int addToMetadataMap(struct MetadataMap *mdm, const char* name, const char* value);
extern int getFromMetadataMap(struct MetadataMap *mdm, int position, char* name, char* value);

/* General metadata external functions */
extern int printMetadataRecord(char **names, char **values, int numberOfRecords);
