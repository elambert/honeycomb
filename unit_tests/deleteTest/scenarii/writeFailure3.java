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

ECHO("Tests that behaviour in the presence of incomplete writes and retries");

OAClient oac = OAClient.getInstance();
chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
storesize = chunksize + 1;

new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "removeTempUtils.java",this.namespace);
new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "faultUtils.java", this.namespace);

void failFrags(OAClient oac, int storesize, boolean expectFailure) {
    oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);

    ECHO("--> Store multiChunk object w/ chunkSize = blockSize");
    if (!expectFailure) {
        STORE(1, storesize, true);
        DEREF(1,0);
        ECHO("Verify w/ retrieve");
        RETRIEVE(1, true);
    } else {
        STORE(1, storesize, false);
    }
}

/**********************************************************************/
void doTest(String type, Fault.FaultType faultType, int fragCount, int storeSize) {
    ECHO("--> " + type + " on " + fragCount + " frags");
    Collection faults;
    if (faultType == FragmentFault.INCOMPLETE_WRITE_ERROR) {
        faults = addFragmentFaultAllChunks("fault(truncate)",
                                           FragmentFaultEvent.STORE,
                                           FaultyNfsDAAL.WRITE,
                                           faultType,
                                           getRandomFragments(fragCount),
                                           2,
                                           OAClient.OBJECT_TYPE_DATA, 5);
        failFrags(oac, storeSize, false);
        assertTriggered(faults);
    } else {
        faults = addFragmentFaultAllChunks("fault(truncate)",
                                           FragmentFaultEvent.STORE,
                                           FaultyNfsDAAL.WRITE,
                                           faultType,
                                           getRandomFragments(fragCount),
                                           2,
                                           OAClient.OBJECT_TYPE_DATA);
        if (fragCount > 2 ) {
            failFrags(oac, storeSize, true);
            assertMinTriggered(faults, 3);
        } else {
            failFrags(oac, storeSize, false);
            assertTriggered(faults);
        }
    }
    removeAllFaults();
}

/**********************************************************************/
for (int fragCount = 1; fragCount <= 7; fragCount++) {
    doTest("IO fault", FragmentFault.IO_ERROR, fragCount, storesize);
    doTest("Incomplete write", FragmentFault.INCOMPLETE_WRITE_ERROR,
           fragCount, storesize);
}

ECHO("Success.");
