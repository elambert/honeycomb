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
import com.sun.honeycomb.oa.ThreadInterruptFault;
import com.sun.honeycomb.oa.daal.nfs.FaultyNfsDAAL;

ECHO("Tests OA behaviour when creator threads are interrupted");

OAClient oac = OAClient.getInstance();
chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
storesize = chunksize * 3 + 1;

new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "removeTempUtils.java",this.namespace);
new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "faultUtils.java", this.namespace);

void failFrags(OAClient oac, int storesize) {
    oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);

    ECHO("--> Store multiChunk object");
    // It is not important if store succeeds or not. If it succeeds we
    // should be able to retrieve data.
    try {
        STORE(1, storesize, true);
    } catch (ArchiveException e) {
        ECHO("Store failed: " + e.getMessage());
        return;
    }
    DEREF(1,0);

    ECHO("Verify w/ retrieve");
    RETRIEVE(1, true);
}

/**********************************************************************/
void doTest(String type, int fragCount, int storeSize) {
    ECHO("--> " + type + " on " + fragCount + " frags");

    Collection faults = addInterruptFaults("InterruptFault(CloseThreads)",
                                           FragmentFaultEvent.STORE,
                                           FaultyNfsDAAL.WRITE,
                                           ThreadInterruptFault.INTERRUPT_CREATOR_ERROR,
                                           getRandomFragments(fragCount),
                                           3);
    failFrags(oac, storeSize);
    removeAllFaults();
}

/**********************************************************************/
for (int fragCount = 1; fragCount <= 4; fragCount++) {
    doTest("Interrupt I/O", fragCount, storesize);
}

ECHO("Success.");
