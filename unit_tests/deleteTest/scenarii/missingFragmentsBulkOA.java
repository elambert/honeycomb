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

//Import Utils
new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") + 
                             File.separatorChar + "utils" + File.separatorChar + 
                             "removeTempUtils.java",this.namespace);

void rmFrags(int oid,int numFrags) { 
    int [] randFrags = randomFrags(numFrags);
    
    for (int i = 0; i < numFrags; i++) {
        ECHO("Hiding frag: " + randFrags[i] + " of object " + oid);
        FRAGREMOVE(oid,randFrags[i]);
    }
}

ECHO("*** Bulk OA missing fragment tests ***");
ECHO("chunksize " + chunksize + "; storesize " + storesize);

/*
3.1. Multiple references with a deleted Metadata object:
    * Store(D,M1); 
    * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
    * fragRemove(D,2) // hide any two fragments
    * backup(B1); 
    * add(D,M2); 
    * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
    * fragRemove(M2,2)  // hide any 2 fragments
    * incBackup(B2); 
    * delete(M1); 
    * verifRfCount(D,1,2) // refCount=1, maxRefCount=2
    * fragRemove(M1,2)  // hide any 2 fragments
    * incBackup(B3); 
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
ECHO("3.1. Multiple references with a deleted Metadata object");
ECHO("*******************************************************");

WIPECACHES();

ECHO("Storing object " + M1);
STORE(M1, storesize);
DEREF(M1,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

ECHO("Hiding 2 random fragments of " + D);
rmFrags(D,2);

ECHO("Backup b1");
BACKUP("b1",0);

ECHO("Adding metadata " + M2);
ADDM(M1,M2);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);

ECHO("Hiding 2 random fragments of " + M2);
rmFrags(M2,2);

ECHO("IncBackup b2");
INCBACKUP("b2",0);

ECHO ("Deleting " + M1);
DELETE(M1,true);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 2);

ECHO("Hiding 2 random fragments of " + M1);
rmFrags(M1,2);

ECHO("IncBackup b3");
INCBACKUP("b3",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3");
RESTORE("b3",0);

ECHO("Restoring b1");
RESTORE("b1",0);

ECHO("Restoring b2");
RESTORE("b2",0);

ECHO("Verifying object " + M1 + " is deleted.");
FRAGISDELETED(M1, Constants.ALL_FRAGS, true);

ECHO("Retrieving metadata for object " + M2);
RETRIEVEM(M2,true);

ECHO("Retrieving data for object " + M2);
RETRIEVE(M2,true);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 2);

/*
3.2. Delete during restore, expecting failure on delete:
    * Store(D,M1); 
    * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
    * fragRemove(M1,2)  // hide any 2 fragments
    * backup(B1); 
    * add(D,M2); 
    * fragRemove(M2,2)  // hide any 2 fragments
    * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
    * incBackup(B2); 
    * wipe();
    * restore(B2); 
    * delete(M2);  // can't delete we don't have D object
    * restore(B1); 
    * delete(M2);  
    * retrieve(M,D)
    * verifRfCount(D,1,2) // refCount=2, maxRefCount=2
*/

//M1=1005;
//M2=1006;
//D=2003;
//
//ECHO("");
//ECHO("3.2. Delete during restore, expecting failure on delete");
//ECHO("**************************");
//
//WIPECACHES();
//
//ECHO("Storing object " + M1);
//STORE(M1, storesize);
//DEREF(M1,D);
//
//ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
//REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);
//
//ECHO("Hiding 2 random fragments of " + M1);
//rmFrags(M1,2);
//
//ECHO("Backup b1"); 
//BACKUP("b1",0); 
//
//ECHO("Adding metadata " + M2);
//ADDM(M1,M2);
//
//ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
//REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);
//
//ECHO("Hiding 2 random fragments of " + M2);
//rmFrags(M2,2);
//
//ECHO("IncBackup b2");
//INCBACKUP("b2",0);
//
//ECHO("Moving data disks over and reinitializing data disks.");
//MOVEALL();
//
//ECHO("Restoring b2");
//RESTORE("b2",0);
//
//ECHO ("Deleting " + M2 + " and expecting to fail.");
//DELETE(M2,false);
//
//ECHO("Restoring b1");
//RESTORE("b1",0);
//
//ECHO ("Deleting " + M2);
//DELETE(M2,true);
//
//ECHO("Retrieving metadata for object " + M1);
//RETRIEVEM(M1,true);
//
//ECHO("Retrieving data for object " + M1);
//RETRIEVE(M1,true);
//
//ECHO("Verifying refCount on " + D + " is 1, with maxRef 2.");
//REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 2);

/*
3.3. Complex Metadata to Data Relation:
    * Store(D,M); 
    * add(D,M1); 
    * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
    * backup(B1)
    * Add(D,M2); 
    * verifRfCount(D,3,3) // refCount=3, maxRefCount=3
    * fragRemove(M2,2)  // hide any 2 fragments
    * incBackup(B2);
    * Delete(M1); 
    * verifRfCount(D,2,3) // refCount=2, maxRefCount=3
    * incBackup(B3);
    * Add(M3); 
    * verifRfCount(D,3,4) // refCount=3, maxRefCount=4
    * fragRemove(M3,2)  // hide any 2 fragments
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
ECHO("3.3. Complex Metadata to Data Relation");
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

ECHO("Hiding 2 random fragments of " + M2);
rmFrags(M2,2);

ECHO("IncBackup b2");
INCBACKUP("b2",0);

ECHO("Deleting " + M1);
DELETE(M1,true);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 3.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 3);

ECHO("IncBackup b3");
INCBACKUP("b3",0);

ECHO("Adding metadata " + M3);
ADDM(M2,M3);

ECHO("Verifying refCount on " + D + " is 3, with maxRef 4.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 3, 4);

ECHO("Hiding 2 random fragments of " + M3);
rmFrags(M3,2);

ECHO("IncBackup b4");
INCBACKUP("b4",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b4");
RESTORE("b4",0);

ECHO("Retrieving " + M3);
RETRIEVE(M3,false);

ECHO("Restoring b3");
RESTORE("b3",0);

ECHO("Checking deleted " + M1);
RETRIEVE(M1,false);

ECHO("Restoring b2");
RESTORE("b2",0);

ECHO("Retrieving " + M2);
RETRIEVE(M2,false);

ECHO("Restoring b1");
RESTORE("b1",0);

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

ECHO("Retrieving metadata for object " + M3);
RETRIEVEM(M2,true);

ECHO("Retrieving data for object " + M3);
RETRIEVE(M2,true);

ECHO("Checking deleted " + M1);
FRAGISDELETED(M1, Constants.ALL_FRAGS, true);

ECHO("Verifying refCount on " + D + " is 3, with maxRef 4.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 3, 4);

ECHO("Success.");
