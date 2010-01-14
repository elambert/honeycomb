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
import com.sun.honeycomb.oa.FragmentFooter;

OAClient oac = OAClient.getInstance();
chunksize = Constants.MAX_CHUNK_SIZE = 1 * oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);

storesize = 1024;
iterations = 5;

String HOLD_TAG = "Hold me";

String getHoldTag() {
    return HOLD_TAG + "-" + System.currentTimeMillis();
}

/*
 *
 */
void setHold (oid, String holdTag) {
    setHold (oid, holdTag, false);
}

/*
 *
 */
void setHold (oid, String holdTag, boolean shouldFail) {
    boolean threw = false;
    try {
        ADDHOLD (oid, holdTag);
    } catch (ArchiveException ae) {
        if (!shouldFail) {
            throw ae;
        }
        threw = true;
    }

    if (shouldFail && !threw) {
        throw new RuntimeException (
            "Expected exception not raised when setting legal hold");
    }
}

/**
 *
 */
void verifyHold (int oid, String holdTag, boolean shouldPass) {
    boolean foundHold = false;
    String[] holds = GETHOLDS(oid);

    if (holds == null) {
        if (shouldPass == true) {
            throw new RuntimeException ("Expected legal hold '" 
                                        + holdTag + "' not found");
        } else {
            return;
        }
    }
        
    for (int i = 0; i < holds.length; i++) {
        if (holds[i].equals(holdTag)) {
            foundHold = true;
            break;
        }
    }

    if (!foundHold && shouldPass == true) {
        throw new RuntimeException ("Expected legal hold '" 
            + holdTag + "' not found");
    }

    if (foundHold && shouldPass == false) {
        throw new RuntimeException ("Expected legal hold '" 
            + holdTag + "' exists where it shouldn't");
    }
}

/**
 *
 */
void removeHold (int oid, String holdTag) {
    removeHold (oid, holdTag, false);
}

/**
 *
 */
void removeHold (int oid, String holdTag, boolean shouldFail) {
    boolean threw = false;
    try {
        RMHOLD (oid, holdTag);
    } catch (ArchiveException ae) {
        if (!shouldFail) {
            throw ae;
        }
        threw = true;
    }

    if (shouldFail && !threw) {
        throw new RuntimeException (
            "Expected exception not raised when removing legal hold: '" 
                + holdTag + "'");
    }
}

void setRetDate (int oid, long retainUntil) {
    setRetDate (oid, retainUntil, false);
}

void setRetDate (int oid, long retainUntil, boolean shouldFail) {
    boolean threw = false;
    try {
        SETRETENTION (oid, retainUntil);
    } catch (ArchiveException ae) {
        if (!shouldFail) {
            throw ae;
        }
        threw = true;
    }

    if (shouldFail && !threw) {
        throw new RuntimeException (
            "Expected Exception not raised when setting invalid retention time");
    }
}

void verifyRetDate (int oid, long retainUntil) {
    long storedDate = GETRETENTION(oid).longValue();
    if (storedDate != retainUntil) {
        throw new RuntimeException ("stored retention date (" 
                    + storedDate + ") != expected value (" + retainUntil + ")");
    } 
}

ECHO("*** Bulk OA single chunk tests ***");
ECHO("storesize " + storesize);

/*
1.1. Simple single-chunk backup:
        * Store(D,M);
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
        * backup(B1); 
        * wipe(); 
        * restore(B1); 
        * retrieve(D,M).
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
*/
M=1000;
D=1999;

ECHO("");
ECHO("1.1. Simple single-chunk backup");
ECHO("*******************************");

WIPECACHES();

ECHO("Storing object " + M);
STORE(M, storesize);
DEREF(M,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

/********************************************************
 *
 * Bug 6554027 - hide retention features
 *
 *******************************************************/
/*
String H = getHoldTag();
ECHO("Adding legal hold [" + H + "] to " + M);
setHold (M, H);

ECHO("Verifying legal hold [" + H + "] on " + M);
verifyHold (M, H, true);

R = System.currentTimeMillis()+(30*Constants.SECONDS);
ECHO("Setting retention date [" + R + "] on " + M);
setRetDate (M, R);

ECHO("Verifying retention date [" + R + "] on " + M);
verifyRetDate (M, R);
*/

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b1.");
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Verifying all objects previously stored by retrieving them.");

ECHO("Retrieving metadata for object " + M);
RETRIEVEM(M,true);

ECHO("Retrieving data for object " + D);
RETRIEVE(M,true);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

/*
ECHO("Verifying legal hold [" + H + "] on " + M);
verifyHold (M, H, true);

ECHO("Verifying retention date [" + R + "] on " + M);
verifyRetDate (M, R);
*/

/*
1.2. Multiple references:
        * Store(D,M1); 
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
        * backup(B1); 
        * add(D,M2); 
        * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
        * backup(B2); 
        * wipe();
        * restore(B2); 
        * restore(B1); 
        * retrieve(D,M1,M2);
        * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
*/
M1=1001;
M2=1002;
D=2000;

ECHO("");
ECHO("1.2. Multiple references");
ECHO("************************");

WIPECACHES();

ECHO("Storing object " + M1);
STORE(M1, storesize);
DEREF(M1,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

/*
R1 = System.currentTimeMillis()+(30*Constants.SECONDS);
ECHO("Setting retention date [" + R1 + "] on " + M1);
setRetDate (M1, R1);

ECHO("Verifying retention date [" + R1 + "] on " + M1);
verifyRetDate (M1, R1);

String H1 = getHoldTag();
ECHO("Adding legal hold [" + H1 + "] to " + M1);
setHold (M1, H1);

ECHO("Verifying legal hold [" + H1 + "] on " + M1);
verifyHold (M1, H1, true);
*/

ECHO("Full Backup b1");
BACKUP("b1",0);

ECHO("Adding metadata " + M2);
ADDM(M1,M2);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);

/*
R2 = System.currentTimeMillis()+(30*Constants.SECONDS);
ECHO("Setting retention date [" + R2 + "] on " + M2);
setRetDate (M2, R2);

ECHO("Verifying retention date [" + R2 + "] on " + M2);
verifyRetDate (M2, R2);

String H2 = getHoldTag();
ECHO("Adding legal hold [" + H2 + "] to " + M2);
setHold (M2, H2);

ECHO("Verifying legal hold [" + H2 + "] on " + M2);
verifyHold (M2, H2, true);
*/

ECHO("Backup b2");
INCBACKUP("b2",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2");
RESTORE("b2",0);

ECHO("Restoring b1");
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Retrieving metadata for object " + M1);
RETRIEVEM(M1,true);

ECHO("Retrieving data for object " + M1);
RETRIEVE(M1,true);

ECHO("Retrieving metadata for object " + M2);
RETRIEVEM(M2,true);

ECHO("Retrieving data for object " + M2);
RETRIEVE(M2,true);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);

/*
ECHO("Verifying legal hold [" + H1 + "] on " + M1);
verifyHold (M1, H1, true);

ECHO("Verifying legal hold [" + H2 + "] on " + M2);
verifyHold (M2, H2, true);

ECHO("Verifying retention date [" + R1 + "] on " + M1);
verifyRetDate (M1, R1);

ECHO("Verifying retention date [" + R2 + "] on " + M2);
verifyRetDate (M2, R2);
*/

/*
1.3.  Simple deleted object backup:
        * Store(D,M);
        * verifRfCount(D,l,1) // refCount=1, maxRefCount=1
        * backup(B1); 
        * delete(M); 
        * verifRfCount(D,0,1) // refCount=0, maxRefCount=1
        * backup(B2); 
        * wipe();
        * restore(B2); 
        * verifRfCount(D,0,1) // refCount=0, maxRefCount=1
        * check-deleted(D,M); 
        * restore(B1); 
        * check-deleted(D,M)
        * verifRfCount(D,0,1) // refCount=0, maxRefCount=1
*/
M=1002;
D=2001;

ECHO("");
ECHO("1.3.  Simple deleted object backup");
ECHO("**********************************");

WIPECACHES();

ECHO("Storing object " + M);
STORE(M, storesize);
DEREF(M,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

/*
H = getHoldTag();
ECHO("Adding legal hold [" + H + "] to " + M);
setHold (M, H);

ECHO("Verifying legal hold [" + H + "] on " + M);
verifyHold (M, H, true);
*/

ECHO("Backup b1");
BACKUP("b1",0);

/*
ECHO("Removing legal hold [" + H + "] from " + M);
removeHold(M, H);
*/

ECHO("Deleting object " + M);
DELETE(M,true);

ECHO("Verifying refCount on " + D + " is 0, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 0, 1);

ECHO("Backup b2");
INCBACKUP("b2",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2");
RESTORE("b2",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Verifying the object is deleted.");
FRAGISDELETED(M, Constants.ALL_FRAGS, true);
FRAGISDELETED(D, Constants.ALL_FRAGS, true);

ECHO("Restoring b1");
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Verifying the object is deleted.");
FRAGISDELETED(M, Constants.ALL_FRAGS, true);
FRAGISDELETED(D, Constants.ALL_FRAGS, true);

ECHO("Verifying refCount on " + D + " is 0, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 0, 1);

/*
ECHO("Verifying legal hold [" + H + "] does not exist on " + M);
verifyHold (M, H, false);
*/

/*
1.4. Multiple references with a deleted Metadata object:
        * Store(D,M1); 
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
        * backup(B1); 
        * add(D,M2); 
        * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
        * backup(B2); 
        * delete(M1); 
        * verifRfCount(D,1,2) // refCount=1, maxRefCount=2
        * backup(B3); 
        * wipe();        
        * restore(B3); 
        * restore(B1); 
        * restore(B2); 
        * check-deleted(M1); 
        * retrieve(M2,D)
        * verifRfCount(D,1,2) // refCount=1, maxRefCount=2
*/

M1=1003;
M2=1004;
D=2002;
T1=0;
T2=0;

ECHO("");
ECHO("1.4. Multiple references with a deleted Metadata object");
ECHO("*******************************************************");

WIPECACHES();

ECHO("Storing object " + M1);
STORE(M1, storesize);
DEREF(M1,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

ECHO("Backup b1");
BACKUP("b1",0);

ECHO("Adding metadata " + M2);
ADDM(M1,M2);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);

ECHO("Backup b2");
INCBACKUP("b2",0);

ECHO ("Deleting " + M1);
DELETE(M1,true);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 2);

ECHO("Backup b3");
INCBACKUP("b3",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3");
RESTORE("b3",0);

ECHO("Restoring b1");
RESTORE("b1",0);

ECHO("Restoring b2");
RESTORE("b2",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Verifying object " + M1 + " is deleted.");
FRAGISDELETED(M1, Constants.ALL_FRAGS, true);

ECHO("Retrieving metadata for object " + M2);
RETRIEVEM(M2,true);

ECHO("Retrieving data for object " + M2);
RETRIEVE(M2,true);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 2);

/*
 1.5. Delete during restore, expecting failure on delete:
        * Store(D,M1); 
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
        * backup(B1); 
        * add(D,M2); 
        * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
        * backup(B2); 
        * wipe();
        * restore(B2); 
        * delete(M2);  // can't delete we don't have D object
        * restore(B1); 
        * delete(M2);  
        * retrieve(M,D)
        * verifRfCount(D,1,2) // refCount=2, maxRefCount=2
 */
M1=1005;
M2=1006;
D=2003;

ECHO("");
ECHO("1.5. Delete during restore, expecting failure on delete");
ECHO("**************************");

WIPECACHES();

ECHO("Storing object " + M1);
STORE(M1, storesize);
DEREF(M1,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

ECHO("Backup b1"); 
BACKUP("b1",0); 

ECHO("Adding metadata " + M2);
ADDM(M1,M2);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);

ECHO("Backup b2");
INCBACKUP("b2",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2");
RESTORE("b2",0);

ECHO ("Deleting " + M2 + " and expecting to fail.");
DELETE(M2,false);

ECHO("Restoring b1");
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO ("Deleting " + M2);
DELETE(M2,true);

ECHO("Retrieving metadata for object " + M1);
RETRIEVEM(M1,true);

ECHO("Retrieving data for object " + M1);
RETRIEVE(M1,true);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 2);

/*
 1.6. Complex Metadata to Data Relation:
        * Store(D,M); 
        * add(D,M1); 
        * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
        * backup(B1)
        * Add(D,M2); 
        * verifRfCount(D,3,3) // refCount=3, maxRefCount=3
        * incBackup(B2);
        * Delete(M1); 
        * verifRfCount(D,2,3) // refCount=2, maxRefCount=3
        * incBackup(B3);
        * Add(M3); 
        * verifRfCount(D,3,4) // refCount=3, maxRefCount=4
        * incBackup(B4);
        * wipe();
        * Restore(B4);
        * retrieve(M3); // can't get data D
        * Restore(B3);
        * check-deleted(M1);
        * Restore(B2);
        * retrieve(M2); // can't get data D
        * Restore(B1);
        * retrieve(M,M2,M3,D); // check-deleted(M1)
        * verifRfCount(D,3,4) // refCount=3, maxRefCount=4
*/
M=1007;
M1=1008;
M2=1009;
M3=1010;
D=2004;

ECHO("");
ECHO("1.6. Complex Metadata to Data Relation");
ECHO("**************************************");

ECHO("Storing object " + M);
STORE(M, storesize);
DEREF(M,D);

ECHO("Adding metadata " + M1);
ADDM(M,M1);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);

ECHO("Backup b1");
BACKUP("b1",0);

ECHO("Adding metadata " + M2);
ADDM(M1,M2);

ECHO("Verifying refCount on " + D + " is 3, with maxRef 3.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 3, 3);

ECHO("IncBackup b2");
INCBACKUP("b2",0);

ECHO("Deleting " + M1);
DELETE(M1,true);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 3.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 3);

ECHO("Backup b3");
INCBACKUP("b3",0);

ECHO("Adding metadata " + M3);
ADDM(M2,M3);

ECHO("Verifying refCount on " + D + " is 3, with maxRef 4.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 3, 4);

ECHO("IncBackup b4");
INCBACKUP("b4",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b4");
RESTORE("b4",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Retrieving metadata for object " + M3);
RETRIEVEM(M3,false);

ECHO("Retrieving data for object " + M3);
RETRIEVE(M3,false);

ECHO("Restoring b3");
RESTORE("b3",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Checking deleted " + M1);
FRAGISDELETED(M1, Constants.ALL_FRAGS, true);

ECHO("Restoring b2");
RESTORE("b2",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Retrieving metadata for object " + M2);
RETRIEVEM(M2,false);

ECHO("Retrieving metadata for object " + M2);
RETRIEVE(M2,false);

ECHO("Restoring b1");
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Retrieving metadata for object " + M);
RETRIEVEM(M,true);

ECHO("Retrieving data for object " + M);
RETRIEVE(M,true);

ECHO("Retrieving metadata for object " + M2);
RETRIEVEM(M2,true);

ECHO("Retrieving data for object " + M2);
RETRIEVE(M2,true);

ECHO("Retrieving metadata for object " + M3);
RETRIEVEM(M3,true);

ECHO("Retrieving data for object " + M3);
RETRIEVE(M3,true);

ECHO("Checking deleted " + M1);
FRAGISDELETED(M1, Constants.ALL_FRAGS, true);

ECHO("Verifying refCount on " + D + " is 3, with maxRef 4.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 3, 4);

ECHO("Success.");

/*
 * 1.7 AddMD a
 * 
 * 1. store data D with metadata M1 (they only have oid for M1).
 * 2. Backup B1.
3. Later add M2 to M1; we now have M1 and M2 pointing at D, refcount(D)=2.
4. Delete M1. Now we have only M2 and refcount(D)=1.
5. Incr. backup B2.
4. Disaster; start restoring from B2.
5. Customer does addMD to add new M3 to M2. Data is not on disk yet! Add 
MD should fail, otherwise we are in the same boat of having to do 
refcounting on the system cache instead of on the data object on disk.
*/
