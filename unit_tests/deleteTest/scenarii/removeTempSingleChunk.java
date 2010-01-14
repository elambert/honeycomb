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
import java.io.File;
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.FaultyNfsDAAL;

// Import Utils
new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "removeTempUtils.java",this.namespace);
initTemp();

new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "faultUtils.java", this.namespace);

// Single Chunk

ECHO("***********************************************************************");
ECHO("Store single chunk successfully leave <= " + NUM_PARITY_FRAGS +
     " tmp frags, RemoveTempFrag should remove all tmp fragments");

metaOID = 1000;
dataOID = 2000;
for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(1,NUM_PARITY_FRAGS);

    int[] frags = randomFrags(numFrags);
    Collection faults = addFragmentFaultAllChunks("failParityFrags(commit)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.COMMIT,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(frags), 1,
                                                  OAClient.OBJECT_TYPE_DATA);

    store(metaOID, 1024, true);
    assertTriggered(faults);

    DEREF(metaOID,dataOID);

    ECHO("Verifying that there are some tmps left in any tmp directory.");
    VERIFYTMPS(-1);

    ECHO("RemoveTmpFrags on data object: " + dataOID);
    REMOVETMP(dataOID,frags[0]);

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
ECHO("Store single chunk unsuccessfully, leave < " + NUM_DATA_FRAGS +
     " tmp frags, RemoveTempFrag should remove all tmp fragments, no data");

metaOID = 3000;
dataOID = 4000;

ECHO("Failing > " + NUM_DATA_FRAGS + " fragments on create for the next " + ITERATIONS + " stores.");
for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);
    ECHO("Inserting faults on " + numFrags + " fragments");
    int[] frags = randomFrags(numFrags);
    Collection faults = addFragmentFaultAllChunks("failManyFrags(commit)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.COMMIT,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(frags), 1,
                                                  OAClient.OBJECT_TYPE_DATA);
    store(metaOID, iteration*1024, false);

    assertTriggered(faults);
    removeAllFaults();
    metaOID++;
    dataOID++;
}

EXECREMOVETMP(0);
ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

ECHO("***********************************************************************");
ECHO("Store single chunk unsuccessfully, fail writes on " +
     (NUM_PARITY_FRAGS + 1) +
     " or more fragments. RemoveTempFrag should remove all tmp fragments and data");

metaOID = 5000;
dataOID = 6000;

ECHO("Failing > " + (NUM_PARITY_FRAGS + 1) + " fragments on write for the next "
     + ITERATIONS + " stores.");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);
    int[] frags = randomFrags(numFrags);
    Collection faults = addFragmentFaultAllChunks("failManyFrags(commit)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.COMMIT,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(frags),
                                                  1,
                                                  OAClient.OBJECT_TYPE_DATA);

    store(metaOID, iteration*1024, false);

    assertMinTriggered(faults, 3);
    removeAllFaults();

    // Should be able to verify some tmps exist
    VERIFYTMPS(-1);

    metaOID++;
    dataOID++;
}

EXECREMOVETMP(0);
ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

ECHO("***********************************************************************");
ECHO("Store single chunk unsuccessfully, fail closes on " +
     (NUM_PARITY_FRAGS + 1)
     + " or more fragments. RemoveTempFrag should remove all tmp fragments, no data");

metaOID = 7000;
dataOID = 8000;

ECHO("Failing > " + (NUM_PARITY_FRAGS + 1) + " fragments on close for the next "
     + ITERATIONS + " stores.");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(NUM_PARITY_FRAGS+1, NUM_ALL_FRAGS);
    int[] frags = randomFrags(numFrags);
    Collection faults = addFragmentFaultAllChunks("failManyFrags(commit)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.COMMIT,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(frags),
                                                  1,
                                                  OAClient.OBJECT_TYPE_DATA);

    store(metaOID, iteration*1024, false);

    assertTriggered(faults);
    removeAllFaults();

    verifyObjectIsInComplete(metaOID);
    // Should be able to verify some tmps exist
    VERIFYTMPS(-1);

    metaOID++;
    dataOID++;
}

EXECREMOVETMP(0);
ECHO("Verifying that there are no tmps left in any tmp directory.");
VERIFYTMPS(0);

//Data Loss Case

ECHO("Store single chunk successfully leave < " + NUM_PARITY_FRAGS +
     " in tmp, then hide enough frags data to make data object incomplete, " +
     " when RemoveTempFrags is executed, should scream DATA LOSS");

metaOID = 10000;
dataOID = 11000;

ECHO("Failing <= " + NUM_PARITY_FRAGS + " fragments on (write or close) for the next "
     + ITERATIONS + " stores.");

for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(1,NUM_PARITY_FRAGS);

    int[] frags = randomFrags(numFrags);
    Collection faults = addFragmentFaultAllChunks("failParityFrags(write)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.COMMIT,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(frags),
                                                  1,
                                                  OAClient.OBJECT_TYPE_DATA);

    store(metaOID, iteration*1024, true);
    assertTriggered(faults);

    DEREF(metaOID, dataOID);

    numFragsToHide = ((NUM_PARITY_FRAGS+1)-numFrags);
    ECHO("Hiding " + numFragsToHide  + " inorder to provoke data-loss");

    count = 0;
    for (int frag = 0; frag < NUM_ALL_FRAGS && count < numFragsToHide; frag++) {
        if (notIn(frag,frags)) {
            FRAGREMOVE(dataOID, frag);
            count++;
        }
    }

    ECHO("Running RemoveTempFrags Task, should scream DATA LOSS.");
    try {
        REMOVETMP(dataOID,frags[0]);
        ECHO("ERROR: should of screamed data loss.");
        throw new ArchiveException("RemoveTempFrags task did not scream DATA LOSS correctly.");
    } catch (ArchiveException e) {
        ECHO("Correctly screamed: " + e.getMessage());
    }

    ECHO("Verifying that there are still tmps left.");
    VERIFYTMPS(-1);

    ECHO("Bringing the hidden fragments back and running the task again.");

    count = 0;
    for (int frag = 0; frag < NUM_ALL_FRAGS && count < numFragsToHide; frag++) {
        if (notIn(frag,frags)) {
            FRAGRESTORE(dataOID, frag);
            count++;
        }
    }

    ECHO("Running RemoveTempFrags Task, should cleanup object correctly.");
    REMOVETMP(dataOID,frags[0]);

    ECHO("Verifying that there no tmps left.");
    VERIFYTMPS(0);

    removeAllFaults();
    metaOID++;
    dataOID++;
}


// Create tmps, disable disk and force crawl to not be able to run leaving
// behind the current tmps

ECHO("***********************************************************************");
ECHO("Store single chunk unsuccessfully leave " + NUM_ALL_FRAGS +
     " tmp frags, disable a disk in the layout, " +
     " RemoveTempFrag should not remove all tmp fragments, then enable the " +
     " same disk and have RemoveTempFrag task clean up tmps.");

metaOID = 11000;
dataOID = 12000;
for (int iteration = 0 ; iteration < ITERATIONS; iteration++ ) {
    numFrags = random(1,NUM_PARITY_FRAGS);
    int[] createFrags = randomFrags(numFrags);
    Collection faults = addFragmentFaultAllChunks("failParityFrags(create)",
                                                  FragmentFaultEvent.STORE,
                                                  FaultyNfsDAAL.CREATE,
                                                  FragmentFault.IO_ERROR,
                                                  getCollectionFromArray(createFrags),
                                                  1,
                                                  OAClient.OBJECT_TYPE_DATA);

    // We need to make sure we create at least 3 tmp frags taking into 
    // acount the errrors triggered by the create (overlap with commit failure
    // below).
    numFrags = NUM_PARITY_FRAGS+3;
    int[] writeFrags = randomFrags(numFrags);
    faults.addAll(addFragmentFaultAllChunks("failParityFrags(write)",
                                            FragmentFaultEvent.STORE,
                                            FaultyNfsDAAL.COMMIT,
                                            FragmentFault.IO_ERROR,
                                            getCollectionFromArray(writeFrags),
                                            1,
                                            OAClient.OBJECT_TYPE_DATA));

    store(metaOID, 1024, false);
    removeAllFaults();

    ECHO("Verifying that there are some tmps left in any tmp directory.");
    VERIFYTMPS(-1);

    ECHO("Disabling disk 0 so RemoveTmpFrags can not clean up the previous tmps.");
    DISABLEDISK(0);

    ECHO("Running RemoveTmpFrags task, and it shouldn't be able to do anything since one disk is offline.");
    EXECREMOVETMP(0);

    ECHO("Verifying that there are some tmps left in any tmp directory.");
    VERIFYTMPS(-1);

    ECHO("Enabling disk 0 so RemoveTmpFrags can do it's correct clean up.");
    ENABLEDISK(0);

    ECHO("Running RemoveTmpFrags task, and now it should clean up tmps.");
    EXECREMOVETMP(0);

    ECHO("Verifying that there are no tmps left in any tmp directory.");
    VERIFYTMPS(0);

    metaOID++;
    dataOID++;
}
