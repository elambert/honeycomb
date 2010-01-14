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



import com.sun.honeycomb.oa.OAClient;
import java.util.Random;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.daal.nfs.FaultyNfsDAAL;

// XXX Some tests fail because they creates one extra chunk that does not get
// cleaned up (tmp frags remain). By the time commit triggers from
// OAThread, OAClient would have created next chunk.

//Import Utils
new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "removeTempUtils.java",this.namespace);
initTemp();

new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "faultUtils.java", this.namespace);

int CHUNKS = 5;

// Multi Chunk (2 chunks)
// CURRENTLY NOT SUPPORTED: Multi Chunk Metadata

// Each of the following need to have the RemoveTempFrags called on each chunk
// in a different order, and this is guaranteed because the RemoveTempFrags task
// is run over the currently alive disks and the layout is generated randomly
// at the beginning of the test so that guarantees that the order in which
// fragments are picked up by the task is different every time this test is
// executed.

ECHO("***********************************************************************");
ECHO("Store multi chunk successfully leave <= " + NUM_PARITY_FRAGS +
     " tmp frags for each chunk, RemoveTempFrag " +
     " should remove all tmp fragments");

chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
storesize = chunksize*CHUNKS + 1;

metaOID = 1000;
dataOID = 2000;

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(1,NUM_PARITY_FRAGS);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaultAllChunks("faultAllChunks(commit)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.COMMIT,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(frags),
                                                  CHUNKS + 1,
                                                  OAClient.OBJECT_TYPE_DATA);

    store(metaOID, storesize, true);
    DEREF(metaOID,dataOID);

    assertTriggered(faults);

    VERIFYTMPS(-1);

    ECHO("Running RemoveTempFrags Task, should only cleanup tmp frags.");
    EXECREMOVETMP(0);

    ECHO("Verifying that there are no tmps left in any tmp directory.");
    VERIFYTMPS(0);

    verifyObjectIsComplete(dataOID);
    verifyObjectIsComplete(metaOID);

    removeAllFaults();
    metaOID++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

ECHO("***********************************************************************");
ECHO("Store multi chunk successfully leave <= " + NUM_PARITY_FRAGS +
     " tmp frags for only one of the chunks, " +
     " RemoveTempFrag should remove all tmp fragments");

chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
storesize = chunksize * CHUNKS + 1;

metaOID = 3000;
dataOID = 4000;
for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    chunk = random(0,(int)(storesize/oac.getMaxChunkSize()));
    ECHO("Failing only on chunk " + chunk);

    numFrags = random(1,NUM_PARITY_FRAGS);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaults("faultOneChunk(commit)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          chunk,
                                          OAClient.OBJECT_TYPE_DATA);

    store(metaOID, storesize, true);
    DEREF(metaOID,dataOID);
    assertTriggered(faults);

    ECHO("Verifying that there are some tmps left in any tmp directory.");
    VERIFYTMPS(-1);

    ECHO("Running RemoveTempFrags Task, should only cleanup tmp frags.");
    EXECREMOVETMP(0);

    ECHO("Verifying that there are no tmps left in any tmp directory.");
    VERIFYTMPS(0);

    verifyObjectIsComplete(dataOID);
    verifyObjectIsComplete(metaOID);

    removeAllFaults();
    metaOID++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

ECHO("***********************************************************************");
ECHO("Store multi chunk failed leave <= " + NUM_PARITY_FRAGS +
     " tmp frags for first chunk, leave > " + NUM_PARITY_FRAGS +
     " tmp frags for the second chunk, RemoveTempFrag should remove all tmp " +
     " fragments and data fragments for all chunks");

chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
storesize = chunksize * CHUNKS + 1;

metaOID = 8000;
dataOID = 9000;

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);

    int numChunks = storesize/oac.getMaxChunkSize();

    chunk = random(0, numChunks);
    ECHO("Failing " + numFrags + " frags in chunk " + chunk);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaults("faultManyFrags(commit)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          chunk,
                                          OAClient.OBJECT_TYPE_DATA);

    numFrags = random(1,NUM_PARITY_FRAGS);
    ECHO("Failling " + numFrags + " frags on all other chunks.");

    for (int chunkID = 0; chunkID < numChunks; chunkID++) {
        int[] frags = randomFrags(numFrags);
        if (chunkdID != chunk) {
            faults.addAll(addFragmentFaults("faultManyFrags(commit)",
                                            FragmentFaultEvent.STORE,
                                            FaultyNfsDAAL.COMMIT,
                                            FragmentFault.IO_ERROR,
                                            getCollectionFromArray(frags),
                                            chunkID,
                                            OAClient.OBJECT_TYPE_DATA));
        }
    }

    store(metaOID, storesize, false);
    assertMinTriggered(faults, 3);

    ECHO("Verifying that there are some tmps left in any tmp directory.");
    VERIFYTMPS(-1);

    ECHO("Running RemoveTempFrags Task, should only cleanup tmp frags.");
    EXECREMOVETMP(0);

    ECHO("Verifying that there are no tmps left.");
    VERIFYTMPS(0);

    removeAllFaults();
    metaOID++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

