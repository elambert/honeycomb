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



// Utils functions used by removeTempXXX.java unit tests.

// TODO:
// Need to add to the framework oids for failed stores.
// Need disk mask change to test out scenarios that need all disks online.
// Need to figure out how to set disks offline in unit tests...

// Aux Functions
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.delete.Constants;
import com.sun.honeycomb.common.ArchiveException;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

import com.ten60.netkernel.urii.aspect.IAspectString;

ITERATIONS=10;
// Chunks in testing is always +1 of this number
CHUNKS = 5;
Random random = new Random(System.currentTimeMillis());
OAClient oac = OAClient.getInstance();

NUM_DATA_FRAGS = oac.getReliability().getDataFragCount();
NUM_PARITY_FRAGS = oac.getReliability().getRedundantFragCount();
NUM_ALL_FRAGS = oac.getReliability().getTotalFragCount();

oac.setMaxChunkSize(1024*1024*1024); // 1GB, will make smaller for serious

void store(int oid, int size, boolean shouldSucceed) {
    int chunks = (size/oac.getMaxChunkSize()) + 1;
    ECHO("Storing obj " + oid + " of size " + size + " bytes in " + chunks + " chunk(s).");
    STORE(oid, size, shouldSucceed);
}

void addmd(int oid, int linkoid, boolean shouldSucceed) {
    ECHO("Adding MD " + oid + " to object: " + linkoid);
    ADDM(linkoid, oid, shouldSucceed);
}

void delete(int oid, boolean shouldSucceed) {
    ECHO("Deleting " + oid);
    DELETE(oid, shouldSucceed);
}

void existsTmp(int oid, int fragID) {
    ECHO("Verifying that fragID: " + fragID + " exists for " + oid);
    EXISTSTMP(oid,fragID);
}

void notExistsTmp(int oid, int fragID) {
    ECHO("Verifying that fragID: " + fragID + " does not exist for " + oid);
    NOTEXISTSTMP(oid,fragID);
}

// Utility functions

// random number between min and max including min and max.
int random(int min, int max) {
    return min + random.nextInt((max-min)+1);
}

// verify that the integer num does not appear in the array set
boolean notIn(int num, int[] set) {
    return notIn(num, set, set.length);
}

boolean notIn(int num, int[] set, int limit) {
    for (int i = 0; i < Math.min(limit, set.length); i++){
        if (num == set[i]) {
            return false;
        }
    }
    return true;
}

int[] randomFrags(int howMany) {
    int[] result = new int[howMany];

    for (int i = 0; i < howMany; ){
        int rand = random(0,NUM_ALL_FRAGS-1);
        boolean chosen = false;
        while (!chosen) {
            if (notIn(rand,result, i)) {
                chosen = true;
                result[i++] = rand;
            } else
                rand = random(0,NUM_ALL_FRAGS-1);
        }
    }

    return result;
}

/**********************************************************************/
Set getRandomFragments(int count) {
    int[] frags = randomFrags(count);
    return getCollectionFromArray(frags);
}

/**********************************************************************/
Collection getCollectionFromArray(int[] frags) {
    Set fragSet = new HashSet();
    for (int i = 0; i < frags.length; i++) {
        fragSet.add(new Integer(frags[i]));
    }
    return fragSet;
}
/**********************************************************************/

void verifyObjectIsComplete(int oid) {
    ECHO("Verifying object " + oid + " is complete.");
    RETRIEVE(oid, true);
}

void verifyObjectIsInComplete(int oid) {
    ECHO("Verifying object " + oid + " is incomplete.");
    RETRIEVE(oid, false);
}

void verifyObjectIsDeleted(int oid) {
    ECHO("Verifying object " + oid + " is deleted.");
    FRAGISDELETED(oid, Constants.ALL_FRAGS, true);
}

// Simple method to make sure that we have no tmps when we start running
// RemoveTempFrags unit tests...
void initTemp() {

    ECHO("Running RemoveTempFrags task to clean up any tmp frags generated " +
         "by other unit tests.");
    EXECREMOVETMP(0);

    ECHO("Verifying that there are no tmps left in any tmp directory.");
    VERIFYTMPS(0);
}
