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
/* pointer arrays can be passed in to the Honeycomb API calls that	*/
/* take metadata.							*/


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "HoneycombMetadata.h"

void UpdatePointerArray(char **pointerArray, char *mapStartingLocation, const int arrayListSize);

/* initializes the passed in MetadataMap, allocating the initial size and capacity */
int initMetadataMap(struct MetadataMap *mdm)
{
	/* Allocate memory */
    mdm->mappedName = (char *)malloc(INITIAL_METADATA_NAME_SIZE);
    mdm->mappedValue = (char *)malloc(INITIAL_METADATA_VALUE_SIZE);
	mdm->namePointerArray = (char **)malloc(sizeof(char *) * INITIAL_METADATA_MAP_CAPACITY);
	mdm->valuePointerArray = (char **)malloc(sizeof(char *) * INITIAL_METADATA_MAP_CAPACITY);
    if (	(mdm->mappedName == NULL) || (mdm->mappedValue == NULL) 
		||	(mdm->namePointerArray == NULL) || (mdm->valuePointerArray == NULL))
    {
        return 0;
    }	/* if data is null */

	/* Initialize capacity and size */
	mdm->nameSize = 0;
	mdm->nameCapacity = INITIAL_METADATA_NAME_SIZE;

	mdm->valueSize = 0;
	mdm->valueCapacity = INITIAL_METADATA_VALUE_SIZE;

    	mdm->mapSize = 0;
	mdm->mapCapacity = INITIAL_METADATA_MAP_CAPACITY;

	*mdm->namePointerArray = 0;
	*mdm->valuePointerArray = 0;
    
	return 1;
}	/* initMetadataMap */

/* Free up memory used by the MetadataMap */
int destroyMetadataMap(struct MetadataMap *mdm)
{
	free(mdm->mappedName);
	free(mdm->mappedValue);
	free(mdm->namePointerArray);
	free(mdm->valuePointerArray);

	mdm->mapCapacity = 0;
	mdm->mapSize = 0;

	return 1;
}	/* destroyMetadataMap */

/* Add metadata to the MetadataMap */
int addToMetadataMap(struct MetadataMap *mdm, const char* name, const char* value)
{
	/* the length of the name and value string is the their number of characters */
	/* plus one for the NULL terminator. */
	int nameLength = strlen(name) + 1;
	int valueLength = strlen(value) + 1;
	
	long newValueSize = 0;

	/* Allocate more memory for name blob if needed */
	long newNameSize = mdm->nameSize + nameLength;
	if (mdm->nameCapacity < newNameSize)
	{
		/* Allocate the standard grow size or the amount needed, whichever is greater */
		long standardGrowCapacity = mdm->nameCapacity + METADATA_NAME_GROW_SIZE; 
		long newCapacity = (standardGrowCapacity > newNameSize) ? standardGrowCapacity : newNameSize;

		/* Reallocate */
		char *tmpName = (char *)realloc(mdm->mappedName, newCapacity);
		if (tmpName == NULL)
		{
			return 0;
		}
		mdm->nameCapacity = newCapacity;
		mdm->mappedName = tmpName;

		/* Pointer array no longer points to valid memory.  This needs to be updated. */
		UpdatePointerArray(mdm->namePointerArray, mdm->mappedName, mdm->mapSize); 
	}	// if more name memory needed
	
	/* Allocate more memory for value blob if needed */
	newValueSize = mdm->valueSize + valueLength;
	if (mdm->nameCapacity <= newValueSize)
	{
		/* Allocate the standard grow size or the amount needed, whichever is greater */
		long standardGrowCapacity = mdm->nameCapacity + METADATA_VALUE_GROW_SIZE; 
		long newCapacity = (standardGrowCapacity > newValueSize) ? standardGrowCapacity : newValueSize;

		char *tmpValue = (char *)realloc(mdm->mappedValue, newCapacity);
		if (tmpValue == NULL)
		{
			return 0;
		}
		mdm->mappedValue = tmpValue;
		mdm->valueCapacity = newCapacity;

		/* Pointer array no longer points to valid memory.  This needs to be updated. */
		UpdatePointerArray(mdm->valuePointerArray, mdm->mappedValue, mdm->mapSize); 
	}	/* if more name memory needed */
	
	/* Allocate more memory for name/value pointers if needed */
    if(mdm->mapSize >= mdm->mapCapacity)
    {
		char *tmpNameArray = NULL;
		char *tmpValueArray = NULL;
       /* We need more space.  Resize the metadata map. */
		mdm->mapCapacity = mdm->mapCapacity + METADATA_MAP_GROW_SIZE;
		tmpNameArray = (char *)realloc(mdm->namePointerArray, sizeof(char *) * mdm->mapCapacity);
		tmpValueArray = (char *)realloc(mdm->valuePointerArray, sizeof(char *) * mdm->mapCapacity);
		if ((tmpNameArray == NULL) || (tmpValueArray == NULL))
		{
			mdm->mapCapacity = mdm->mapCapacity - METADATA_MAP_GROW_SIZE;
			return 0;
		}	/* if tmpValue or tmpName are NULL */

		mdm->namePointerArray = (char **)tmpNameArray;
		mdm->valuePointerArray = (char **)tmpValueArray;
    }	/* if size is >= capacity */

	/* Add to map */
	strcpy(&mdm->mappedName[mdm->nameSize], name);
	strcpy(&mdm->mappedValue[mdm->valueSize], value);
	
	/* Add pointer to name and value pointer array */
	mdm->namePointerArray[mdm->mapSize] = &mdm->mappedName[mdm->nameSize];
	mdm->valuePointerArray[mdm->mapSize] = &mdm->mappedValue[mdm->valueSize];

	/* Update the size of the blobs and the map */
	mdm->nameSize = mdm->nameSize + nameLength;
	mdm->valueSize = mdm->valueSize + valueLength;
   	++mdm->mapSize;
    
	return 1;
}	/* addToMetadataMap */

/* Gets a name and value of the position'th metatdata out of the Metadata map */
int getFromMetadataMap(struct MetadataMap *mdm, int position, char* name, char* value)
{
	if (position > mdm->mapSize)
	{
		return 0;
	}	/* if position > mapsize */

	name = mdm->namePointerArray[position];
	value = mdm->valuePointerArray[position];

	return 1;
}	/* getFromMetadataMap */

void UpdatePointerArray(char **pointerArray, char *mapStartingLocation, const int arrayListSize)
{
	char **pointerArrayIterator = pointerArray;
	char *currentMapPosition = mapStartingLocation;

	long currentElementSize = 0;
	int arrayLocation = 1;
	for (arrayLocation = 1; arrayLocation <= arrayListSize; ++arrayLocation)
	{
		/* Size the current memory address and update it to the new map */
		long currentArrayMemoryAddress = (int)*pointerArrayIterator;
		*pointerArrayIterator = currentMapPosition;

		if (arrayLocation < arrayListSize)
		{
			/* Get the size of the current element in the array */
			char **nextElement = pointerArrayIterator;
			nextElement++;
			currentElementSize = (long)*nextElement - currentArrayMemoryAddress;

			/* Move the map position to the next element */
			currentMapPosition += currentElementSize;
		}	/* if the index of the pointer array < the size of the array list */

		pointerArrayIterator++;
	}	/* loop */
}	/* UpdateNamePointerArray */

/* Prints a metadata record to standard output. */
int printMetadataRecord(char **names, char **values, int numberOfRecords)
{
	int recordIndex = 0;

	/* Validate pointers */
	if ((names == NULL) || (values == NULL))
	{
		return 0;
	}	/* if pointers not valid */

	/* Loop through the list of metadata name/values */
	for (recordIndex = 0; recordIndex < numberOfRecords; recordIndex++)
	{
		/* Print out name/value pair */
		printf("%s=%s\n", *names, *values);

		/* Increment pointers */
		names++;
		values++;
	}   /* loop through all name/value pairs */

	return 1;
}   /* printMetadataRecord */
