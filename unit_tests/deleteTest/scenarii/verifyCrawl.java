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



import com.sun.honeycomb.delete.Constants;
import com.sun.honeycomb.oa.OAClient;

ECHO("Verify that recovery works on deleted objects and completes delete when necessary");

storesize = 10;
int object=0;

for(int f1 = 0; f1 < 7; f1++) {
	for(int f2 = 0; f2 < 7; f2++) {
		for(int f3 = 0; f3 < 7; f3++) {
	    	ECHO("--> Store object of size " + storesize);
			STORE(object, storesize);
			
			ECHO("Moving fragment " + f1 + "," + f2 + "," + f3 + " to another disk.");
			FRAGMOVE(object,f1);
			FRAGMOVE(object,f2);
			FRAGMOVE(object,f3);

			ECHO("Retrieving object, this will lead to crawling in order to find the fragments that have moved.");
			RETRIEVE(object,true);

			ECHO("Recovering fragment " + f1 + "," + f2 + "," + f3);
			RECOVER(object,f1);
			RECOVER(object,f2);
			RECOVER(object,f3);
			
			object++;
		}
	}
}
