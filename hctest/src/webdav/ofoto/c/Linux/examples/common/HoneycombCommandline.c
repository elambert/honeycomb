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



#include <stdio.h>
#include <string.h>
#include "HoneycombCommandline.h"

/* Commandline switch character */
const char *SWITCH_CHARACTER_LIST = "-/\0";

/* parseCommandline handles general commandline parsing.  It defers		*/
/* application specific parsing and verification to functions passed in.*/
/* These functions are passed in as the third and forth arguments of	*/
/* parseCommandline and are function pointers.  The first and second	*/
/* arguments are the commandline as received in the main function.		*/
int parseCommandline(	int argc, 
			char* argv[], 
			int (*pclp)(int, char*, char*), 
			int (*vcmd)())
{
	int onSwitch = 0;
	char *currentSwitch = 0;
	char *currentArgument = 0;

	/* Loop through all commandline arguments */
	int currentArgumentNumber = 1;
	for (currentArgumentNumber = 1; currentArgumentNumber <= argc; ++currentArgumentNumber)
	{
		currentArgument = argv[currentArgumentNumber];
		if (currentArgument == NULL)
		{
			break;
		}	/* if no current argument */

		/* If we are on a switch */
		if (strrchr(SWITCH_CHARACTER_LIST, *currentArgument) != NULL)
		{
			/* If we are already on a switch, process that switch with an empty argument */
			if (onSwitch == 1)
			{
				int result = (*pclp)(onSwitch, currentSwitch, 0);
				if (result != 0)
				{
					printf("Invalid commandline argument: %s\n", currentSwitch);
					return result;
				}	/* if parse failed */
			}	/* if on switch */

			/* Make this switch the current switch */
			++currentArgument;
			currentSwitch = currentArgument;
			onSwitch = 1;
		}	/* if this is a switch */
		else
		{
			/* Process current switch with the current command line argument */
			int result = (*pclp)(onSwitch, currentSwitch, currentArgument);
			if (result != 1)
			{
				printf("Invalid commandline arguments: %s %s\n", currentSwitch, currentArgument);
				return result;
			}	/* if parse failed */
			onSwitch = 0;
		}	/* else this is not a switch */
	}	/* loop through command line argument list */

	return vcmd();
}	/* parseCommandline */
