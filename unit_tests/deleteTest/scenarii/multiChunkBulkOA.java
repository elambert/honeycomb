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
CHUNKS=5;
chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);
storesize = chunksize*CHUNKS + 1;

ECHO("*** Bulk OA multi chunk tests ***");
ECHO("chunksize " + chunksize + "; storesize " + storesize);

/*
2.1. Simple multichunk backup (2-100 chunks):
        * Store(D,M);
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
        * backup(B1);
        * wipe();
        * restore(B1);
        * retrieve(D,M).
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
*/

M=1000;
D=2000; 

ECHO("");
ECHO("2.1. Simple multichunk backup (2-100 chunks)");
ECHO("*******************************");

ECHO("Storing object " + M);
STORE(M, storesize);
DEREF(M,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

ECHO("Backup b1");
BACKUP("b1",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring the currently backed up cluster.");
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Retrieving metadata for object " + M);
RETRIEVEM(M,true);

ECHO("Retrieving data for object " + M);
RETRIEVE(M,true);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

/*
2.2. Multiple references multichunk backup:
        * Store(D,M1); 
        * verifRfCount(D,1,1) // refCount=1, maxRefCount=1
        * backup(B1); 
        * add(D,M2); 
        * verifRfCount(D,2,2) // refCount=2, maxRefCount=2
        * incBackup(B2); 
        * wipe();
        * restore(B2); 
        * restore(B1); 
        * retrieve(D,M1,M2);
        * verifRfCount(D,2,2) // refCount=2, maxRefCount=2 
*/
M1=1001;
M2=1002;
D=2001; 

ECHO("");
ECHO("2.2. Multiple references multichunk backup");
ECHO("*******************************");

ECHO("Storing object " + M1);
STORE(M1, storesize);
DEREF(M1,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

ECHO("Backing b1");
BACKUP("b1",0);

ECHO("Adding metadata " + M2);
ADDM(M1,M2);

ECHO("Verifying refCount on " + D + " is 2, with maxRef 2.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 2, 2);

ECHO("IncBackup b2");
INCBACKUP("b2",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2.");
RESTORE("b2",0);

ECHO("Restoring b1.");
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
2.3.  Simple deleted multichunk object backup
        * Store(D,M);
        * verifRfCount(D,l,1) // refCount=1, maxRefCount=1
        * backup(B1); 
        * delete(M); 
        * verifRfCount(D,0,1) // refCount=0, maxRefCount=1
        * incBackup(B2); 
        * wipe();
        * restore(B2); 
        * verifRfCount(D,0,1) // refCount=0, maxRefCount=1
        * check-deleted(D,M); 
        * restore(B1); 
        * check-deleted(D,M)
        * verifRfCount(D,0,1) // refCount=0, maxRefCount=1
*/

M=1003;
D=2002; 

ECHO("");
ECHO("2.3.  Simple deleted multichunk object backup");
ECHO("*********************************************");

ECHO("Storing object " + M);
STORE(M, storesize);
DEREF(M,D);

ECHO("Verifying refCount on " + D + " is 1, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 1, 1);

ECHO("Backup b1");
BACKUP("b1",0);

ECHO("Deleting " + M);
DELETE(M,true);

ECHO("Verifying refCount on " + D + " is 0, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 0, 1);

ECHO("IncBackup b2");
INCBACKUP("b2",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restore b2");
RESTORE("b2",0);

ECHO("Verifying refCount on " + D + " is 0, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 0, 1);

ECHO("Restore b1");
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Retrieving metadata for object " + M + ", expecting failure.");
RETRIEVEM(M,false);

ECHO("Retrieving data for object " + M + ", expecting failure.");
RETRIEVE(M,false);

ECHO("Verifying refCount on " + D + " is 0, with maxRef 1.");
REFCNTCHECK(D, Constants.ALL_FRAGS, 0, 1);
