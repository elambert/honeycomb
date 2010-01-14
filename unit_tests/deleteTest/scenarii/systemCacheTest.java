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
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.ErrorInjectionState;
import com.sun.honeycomb.oa.FaultPoint;

OAClient oac = OAClient.getInstance();
CHUNKS=5;
chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
storesize = chunksize*CHUNKS + 1;


ECHO("Test that when we store a multichunk object there is only 1 system record for this object.");

ECHO("First lets wipe the caches so we don't have any other objects in the UT framework");
WIPECACHES();

ECHO("Storing object 0 of " + CHUNKS + " chunks.");
STORE(0, storesize);
DEREF(0,1);

ECHO("Retrieving system cache size and verifying there's only 2 objects.");
Integer size = SYSCACHESIZE();

if (size.intValue() != 2) {
    throw new ArchiveException("There should only be 2 system cache entry there are " + size);
}

ECHO("Success.");
