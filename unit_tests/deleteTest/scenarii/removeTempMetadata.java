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



// XXX Some tests fail because refcount is incremented before
// renaming fragments. Even if rename fails and (>2) tmp frags are
// left behind, data frags won't get deleted since refcount is not 0.
// This is a bug in OA.

import com.sun.honeycomb.oa.OAClient;
import java.util.Random;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.daal.nfs.FaultyNfsDAAL;

//Import Utils
new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "removeTempUtils.java",this.namespace);
initTemp();

new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "faultUtils.java", this.namespace);

// Metadata

ECHO("***********************************************************************");
ECHO("Add metadata successfully leave <= " + NUM_PARITY_FRAGS +
     " tmp frags, RemoveTempFrag should remove all tmp " +
     " fragments, but not touch metadata or data object");

metaOID1 = 1000;
metaOID2 = 2000;
dataOID =  3000;

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    store(metaOID1, 1024, true);
    DEREF(metaOID1,dataOID);

    numFrags = random(1,NUM_PARITY_FRAGS);

    int[] frags = randomFrags(numFrags);
    Collection faults = addFragmentFaults("failAddMetadata(write)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          0,
                                          OAClient.OBJECT_TYPE_METADATA);

    addmd(metaOID2, metaOID1, true);
    assertTriggered(faults);

    // Verify that 2 Frags where left behind in tmp directories.
    for (int frag = 0; frag < frags.length ; frag++) {
        existsTmp(metaOID2,frags[frag]);
    }

    ECHO("RemoveTmpFrags on data object: " + dataOID);
    REMOVETMP(metaOID2,frags[0]);

    ECHO("Verifying that there are no tmps left in any tmp directory.");
    VERIFYTMPS(0);

    // Verify that 2 Frags where left behind in tmp directories.
    for (int frag = 0; frag < frags.length ; frag++) {
        notExistsTmp(metaOID2,frags[frag]);
    }

    verifyObjectIsComplete(dataOID);
    verifyObjectIsComplete(metaOID1);
    verifyObjectIsComplete(metaOID2);

    removeAllFaults();
    metaOID1++;
    metaOID2++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

ECHO("***********************************************************************");
ECHO("Add metadata failed, leave > " + NUM_PARITY_FRAGS +
     " tmp frags, RemoveTempFrag should remove all " +
     " metadata and tmp fragments, shouldn't be capable of dereferencing data object");

metaOID = 4000;
dataOID = 5000;

ECHO("Failing > " + NUM_PARITY_FRAGS + " fragments on write for the next "
     + ITERATIONS + " metatadata stores.");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);

    int[] frags = randomFrags(numFrags);
    Collection faults = addFragmentFaults("failMetadata(write)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          0,
                                          OAClient.OBJECT_TYPE_METADATA);

    store(metaOID, iteration*1024, false);
    assertMinTriggered(faults, 3);

    // Should be able to verify some tmps exist
    VERIFYTMPS(-1);

    // Metadata object should have been removed from tmp but data object should
    // still be on disk, as lost space.

    removeAllFaults();
    metaOID++;
    dataOID++;
}

EXECREMOVETMP(0);
ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

ECHO("***********************************************************************");
ECHO("Add metadata failed, leave > " + NUM_PARITY_FRAGS +
     " tmp frags (fail rename, have footer), RemoveTempFrag should remove " +
     " all metadata and tmp fragments, should dereference data object, but " +
     " deleting it since it stil holds previous reference.");

metaOID1 = 6000;
metaOID2 = 7000;
dataOID =  8000;

ECHO("Failing > " + NUM_PARITY_FRAGS + " fragments on rename for the next "
     + ITERATIONS + " metatadata add operations");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {

    store(metaOID1, iteration*1024, true);
    DEREF(metaOID1,dataOID);

    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaults("failAddMetadata(rename)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          0,
                                          OAClient.OBJECT_TYPE_METADATA);

    addmd(metaOID2, metaOID1, false);
    assertTriggered(faults);

    ECHO("Verifying that there are no tmps left in any tmp directory.");
    VERIFYTMPS(-1);

    ECHO("Running RemoveTempFrags Task...");
    EXECREMOVETMP(0);

    verifyObjectIsComplete(dataOID);
    verifyObjectIsComplete(metaOID1);

    // Metadata object should have been removed from tmp but data object should
    // still be on disk, as lost space.

    removeAllFaults();
    metaOID1++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

// Delete Case 1

ECHO("***********************************************************************");
ECHO("Add metadata failed, leave > " + NUM_PARITY_FRAGS +
     " tmp frags (fail rename, have footer)");
ECHO("Delete original metadata object");
ECHO("Call RemoveTempFrags, and see data object gets deleted");

metaOID1 =  9000;
metaOID2 = 10000;
dataOID =  11000;

ECHO("Failing > " + NUM_PARITY_FRAGS + " fragments on rename for the next "
     + ITERATIONS + " metatadata add operations");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {

    store(metaOID1, iteration*1024, true);
    DEREF(metaOID1,dataOID);

    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaults("failMetadata(rename)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          0,
                                          OAClient.OBJECT_TYPE_METADATA);

    addmd(metaOID2, metaOID1, false);
    assertTriggered(faults);
    removeAllFaults();

    ECHO("Deleting original metadata for data: " + dataOID);
    delete(metaOID1, true);

    ECHO("Verifying that there are tmps left in any tmp directory.");
    VERIFYTMPS(-1);

    verifyObjectIsComplete(dataOID);

    ECHO("Running RemoveTempFrags Task... it should clean up both MD and original data object.");
    EXECREMOVETMP(0);

    verifyObjectIsDeleted(dataOID);

    metaOID2++;
    metaOID1++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

// Delete Case 2
ECHO("***********************************************************************");
ECHO("Store single chunk successfully");
ECHO("Add metadata successfull, leave <= " + NUM_PARITY_FRAGS +
     " tmp frags (fail rename, have footer)");
ECHO("Delete original metadata object");
ECHO("Call RemoveTempFrags, and see tmp frags deleted.");
ECHO("Retrieve on second metadata oid");

metaOID1 = 12000;
metaOID2 = 13000;
dataOID =  14000;

ECHO("Failing <= " + NUM_PARITY_FRAGS + " fragments on rename for the next "
     + ITERATIONS + " metatadata add operations");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {

    store(metaOID1, iteration*1024, true);
    DEREF(metaOID1,dataOID);

    numFrags = random(0, NUM_PARITY_FRAGS);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaults("failMetadata(rename)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          0,
                                          OAClient.OBJECT_TYPE_METADATA);

    addmd(metaOID2, metaOID1, true);
    assertTriggered(faults);

    verifyObjectIsComplete(dataOID);
    verifyObjectIsComplete(metaOID1);
    verifyObjectIsComplete(metaOID2);

    ECHO("Deleting original metadata for data: " + dataOID);
    delete(metaOID1, true);

    verifyObjectIsComplete(metaOID2);
    // Currently can't do lookup on data oid since the UT framework deletes
    // ref in previous delete operation
    // TOOD: fix this!
    // verifyObjectIsComplete(dataOID);

    ECHO("Running RemoveTempFrags Task... it should clean up both MD and original data object.");
    EXECREMOVETMP(0);

    verifyObjectIsComplete(metaOID2);
    // Currently can't do lookup on data oid since the UT framework deletes
    // ref in previous delete operation
    // TOOD: fix this!
    // verifyObjectIsComplete(dataOID);

    removeAllFaults();
    metaOID2++;
    metaOID1++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

// Delete Case 3
ECHO("***********************************************************************");
ECHO("Store multi chunk successfully");
ECHO("Add metadata failed, leave > " + NUM_PARITY_FRAGS +
     " tmp frags (fail rename, have footer)");
ECHO("Delete original metadata object");
ECHO("Call RemoveTempFrags, and see data object gets deleted");

metaOID1 = 15000;
metaOID2 = 16000;
dataOID =  17000;

ECHO("Failing > " + NUM_PARITY_FRAGS + " fragments on rename for the next "
     + ITERATIONS + " metatadata add operations");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    store(metaOID1, iteration*1024, true);
    DEREF(metaOID1,dataOID);

    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaults("failAddMetadata(rename)",
                                          FragmentFaultEvent.STORE,
                                          FaultyNfsDAAL.COMMIT,
                                          FragmentFault.IO_ERROR,
                                          getCollectionFromArray(frags),
                                          0,
                                          OAClient.OBJECT_TYPE_METADATA);

    addmd(metaOID2, metaOID1, false);
    assertTriggered(faults);

    verifyObjectIsComplete(dataOID);
    verifyObjectIsComplete(metaOID1);

    ECHO("Delete original metadata object");
    delete(metaOID1,true);

    ECHO("RemoveTempFrags Task... it should remove MD and original data object.");
    EXECREMOVETMP(0);

    verifyObjectIsDeleted(dataOID);
    verifyObjectIsDeleted(dataOID);

    removeAllFaults();
    metaOID2++;
    metaOID1++;
    dataOID++;
}

ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

// Delete Case 4

ECHO("***********************************************************************");
ECHO("Store multi chunk successfully");
ECHO("Add metadata successfull, leave <= " + NUM_PARITY_FRAGS +
     " tmp frags (fail rename, have footer)");
ECHO("Delete original metadata object");
ECHO("Call RemoveTempFrags, and see tmp frags deleted.");
ECHO("Retrieve on second metadata oid");

metaOID1 = 18000;
metaOID2 = 19000;
dataOID =  20000;

ECHO("Failing <= " + NUM_PARITY_FRAGS + " fragments on rename for the next "
     + ITERATIONS + " metatadata add operations");

chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
storesize = chunksize*CHUNKS + 1;

for (int iteration = 1 ; iteration <= ITERATIONS; iteration++ ) {
    ECHO("Iteration: " + iteration);
    store(metaOID1, storesize, true);
    DEREF(metaOID1,dataOID);

    numFrags = random(0,NUM_PARITY_FRAGS);

    int[] frags = randomFrags(numFrags);

    Collection faults = addFragmentFaultAllChunks("failMetadataAllChunks(rename)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.COMMIT,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(frags),
                                                  CHUNKS + 1,
                                                  OAClient.OBJECT_TYPE_METADATA);

    addmd(metaOID2, metaOID1, true);
    assertMinTriggered(faults, numFrags);

    verifyObjectIsComplete(dataOID);
    verifyObjectIsComplete(metaOID1);
    verifyObjectIsComplete(metaOID2);

    ECHO("Delete original metadata object");
    delete(metaOID1,true);

    ECHO("Running RemoveTempFrags Task, should only cleanup tmp frags.");
    EXECREMOVETMP(0);
    removeAllFaults();

    verifyObjectIsComplete(metaOID2);

    metaOID2++;
    metaOID1++;
    dataOID++;
}
