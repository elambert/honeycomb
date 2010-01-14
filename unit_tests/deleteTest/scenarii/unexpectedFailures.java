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
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.daal.nfs.FaultyNfsDAAL;

new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "faultUtils.java", this.namespace);

ECHO("Tests RuntimeExceptions during ops");
ECHO("Repro for bug 6393135 OA should handle unexpected exceptions when doing fragment ops and treat them as a single frag error");

OAClient oac = OAClient.getInstance();

// we want to be able to fail within a chunk read, so make this
// big enough.
chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize*10;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
storesize = 3*chunksize - 1;

frag = 0;

ECHO("--> Add monster ALL fault point for SINGLE frag " + frag + ": " + fp);
// XXX add other ops here as they become available

FaultyNfsDAAL.Operation[] allOps = { FaultyNfsDAAL.CREATE,
                                     FaultyNfsDAAL.CLOSE,
                                     FaultyNfsDAAL.READ,
                                     FaultyNfsDAAL.WRITE,
                                     FaultyNfsDAAL.SEEK,
                                     FaultyNfsDAAL.DELETE,
                                     FaultyNfsDAAL.RENAME,
                                     FaultyNfsDAAL.TRUNCATE };
FragmentFaultEvent[] events = { FragmentFaultEvent.STORE,
                                FragmentFaultEvent.RETRIEVE,
                                FragmentFaultEvent.DELETE,
                                FragmentFaultEvent.REFINC,
                                FragmentFaultEvent.REFDEC,
                                FragmentFaultEvent.NOOP };

for (int i = 0; i < events.length; i++) {
    for (int j = 0; j < allOps.length; j++) {
        addFragmentFaultAllChunks("uncaught", events[i],
                                  allOps[j],
                                  FragmentFault.UNDECLARED_ERROR,
                                  frag,
                                  4,
                                  OAClient.OBJECT_TYPE_DATA);
        addFragmentFaultAllChunks("uncaught", events[i],
                                  allOps[j],
                                  FragmentFault.UNDECLARED_ERROR,
                                  frag,
                                  4,
                                  OAClient.OBJECT_TYPE_METADATA);
    }
}

ECHO("--> Store multiChunk object");
STORE(1, storesize, true);
DEREF(1,0);

ECHO("--> Retrieve data obj");
RETRIEVE(0, true);

ECHO("Success.");
