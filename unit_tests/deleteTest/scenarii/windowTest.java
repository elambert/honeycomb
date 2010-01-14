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



import java.util.BitSet;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.delete.Constants;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.common.ArchiveException;


//Import Utils
new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "windowTestUtils.java",this.namespace);


//Window Test
ECHO("**********************************************************************");
ECHO("**********************************************************************");
ECHO("*******************                                *******************");
ECHO("*******************  Backup Boundary Window Tests  *******************");
ECHO("*******************                                *******************");
ECHO("**********************************************************************");
ECHO("**********************************************************************");

/*
Simple Store - Delete MD
-------------------------
- Store
- Backup
- Delete MD from system cache
- Restore
- Make sure MD is back in system cache
*/


ECHO("\n");
ECHO("TEST: " + test);
ECHO("1.1 Simple Store - Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);

ECHO("Verifying system cache exists for " + dTagMatrix[test][0]);
SM3=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);
//
//ECHO("Verifying all restored fragments are identical to originals");
//COMPARE();

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);

ECHO("Verifying system cache exists for " + dTagMatrix[test][0]);
SM4=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM3, SM4, true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);

/*
Simple Store - Delete Data MD
---------------------------
- Store
- Backup
- Delete data entry from system cache
- Restore
- Make sure data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("1.2 Simple Store - Delete Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM1=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing data " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b1.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();

ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM2=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);

/*
Simple Store - Delete Data + MD
--------------------------------
- Store
- Backup
- Delete MD and data entry from system cache
- Restore
- Make sure MD and data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("1.3 Simple Store - Delete Data MD and  MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM3=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);

ECHO("Removing data " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM4=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);

ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM3, SM4, true);


/*
Multi Store - Delete MD
-------------------------
- Store many
- Backup
- Delete most recent MD from system cache
- Restore
- Make sure MD is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("2.1 Multi Store - Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);

ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM1=getSystemCache(mTagMatrix[test][2], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][2] + " from system cache");
REMOVEM(mTagMatrix[test][2]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][2]);
getSystemCache(mTagMatrix[test][2], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM2=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM1, SM2, true);


/*
Multi Store - Delete Data MD
---------------------------
- Store many
- Backup
- Delete most recent data entry from system cache
- Restore
- Make sure data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("2.2 Multi Store - Delete Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);

ECHO("Getting data for " + mTagMatrix[test][2] + ", tagging with " + dTagMatrix[test][2]);
DEREF(mTagMatrix[test][2],dTagMatrix[test][2]);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][2]);
SM1=getSystemCache(dTagMatrix[test][2], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + dTagMatrix[test][2] + " from system cache");
REMOVEM(dTagMatrix[test][2]);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][2]);
getSystemCache(dTagMatrix[test][2], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + dTagMatrix[test][2]);
SM2=getSystemCache(dTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][2]);
verifyMetadata(SM1, SM2, true);


/*
Multi Store - Delete Data + MD
--------------------------------
- Store many
- Backup
- Delete most recent MD and data entry from system cache
- Restore
- Make sure MD and data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("2.3 Multi Store - Delete MD and Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);

ECHO("Getting data for " + mTagMatrix[test][2] + ", tagging with " + dTagMatrix[test][2]);
DEREF(mTagMatrix[test][2],dTagMatrix[test][2]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM1=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][2]);
SM3=getSystemCache(dTagMatrix[test][2], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][2] + " from system cache");
REMOVEM(mTagMatrix[test][2]);

ECHO("Removing metadata " + dTagMatrix[test][2] + " from system cache");
REMOVEM(dTagMatrix[test][2]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][2]);
getSystemCache(mTagMatrix[test][2], false);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][2]);
getSystemCache(dTagMatrix[test][2], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM2=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache exists for " + dTagMatrix[test][2]);
SM4=getSystemCache(dTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM1, SM2, true);

ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][2]);
verifyMetadata(SM3, SM4, true);



/*
Multi Store - Multi Delete MD
-------------------------
- Store many
- Backup
- Delete multiple recent MD from system cache
- Restore
- Make sure MD is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("3.1 Multi Store - Multi Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);
ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Storing object " + mTagMatrix[test][7]);
STORE(mTagMatrix[test][7], storesize);
ECHO("Storing object " + mTagMatrix[test][8]);
STORE(mTagMatrix[test][8], storesize);

ECHO("Verifying system cache exists for " + mTagMatrix[test][6]);
SM1=getSystemCache(mTagMatrix[test][6], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][7]);
SM3=getSystemCache(mTagMatrix[test][7], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][8]);
SM5=getSystemCache(mTagMatrix[test][8], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][6] + " from system cache");
REMOVEM(mTagMatrix[test][6]);
ECHO("Removing metadata " + mTagMatrix[test][7] + " from system cache");
REMOVEM(mTagMatrix[test][7]);
ECHO("Removing metadata " + mTagMatrix[test][8] + " from system cache");
REMOVEM(mTagMatrix[test][8]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][6]);
getSystemCache(mTagMatrix[test][6], false);
ECHO("Verifying system cache missing for " + mTagMatrix[test][7]);
getSystemCache(mTagMatrix[test][7], false);
ECHO("Verifying system cache missing for " + mTagMatrix[test][8]);
getSystemCache(mTagMatrix[test][8], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][6]);
SM2=getSystemCache(mTagMatrix[test][6], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][7]);
SM4=getSystemCache(mTagMatrix[test][7], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][8]);
SM6=getSystemCache(mTagMatrix[test][8], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][6]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][7]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][8]);
verifyMetadata(SM5, SM6, true);



/*
Multi Store - Multi Delete Data MD
---------------------------
- Store many
- Backup
- Delete multiple recent data entry from system cache
- Restore
- Make sure data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("3.2 Multi Store - Multi Delete Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);
ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Storing object " + mTagMatrix[test][7]);
STORE(mTagMatrix[test][7], storesize);
ECHO("Storing object " + mTagMatrix[test][8]);
STORE(mTagMatrix[test][8], storesize);

ECHO("Getting data for " + mTagMatrix[test][6] + ", tagging with " + dTagMatrix[test][6]);
DEREF(mTagMatrix[test][6],dTagMatrix[test][6]);
ECHO("Getting data for " + mTagMatrix[test][7] + ", tagging with " + dTagMatrix[test][7]);
DEREF(mTagMatrix[test][7],dTagMatrix[test][7]);
ECHO("Getting data for " + mTagMatrix[test][8] + ", tagging with " + dTagMatrix[test][8]);
DEREF(mTagMatrix[test][8],dTagMatrix[test][8]);
ECHO("Verifying system cache exists for data" + dTagMatrix[test][6]);
SM1=getSystemCache(dTagMatrix[test][6], true);
ECHO("Verifying system cache exists for data" + dTagMatrix[test][7]);
SM3=getSystemCache(dTagMatrix[test][7], true);
ECHO("Verifying system cache exists for data" + dTagMatrix[test][8]);
SM5=getSystemCache(dTagMatrix[test][8], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + dTagMatrix[test][6] + " from system cache");
REMOVEM(dTagMatrix[test][6]);
ECHO("Removing metadata " + dTagMatrix[test][7] + " from system cache");
REMOVEM(dTagMatrix[test][7]);
ECHO("Removing metadata " + dTagMatrix[test][8] + " from system cache");
REMOVEM(dTagMatrix[test][8]);

ECHO("Verifying system cache missing for " + dTagMatrix[test][6]);
getSystemCache(dTagMatrix[test][6], false);
ECHO("Verifying system cache missing for " + dTagMatrix[test][7]);
getSystemCache(dTagMatrix[test][7], false);
ECHO("Verifying system cache missing for " + dTagMatrix[test][8]);
getSystemCache(dTagMatrix[test][8], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + dTagMatrix[test][6]);
SM2=getSystemCache(dTagMatrix[test][6], true);
ECHO("Verifying system cache exists for " + dTagMatrix[test][7]);
SM4=getSystemCache(dTagMatrix[test][7], true);
ECHO("Verifying system cache exists for " + dTagMatrix[test][8]);
SM6=getSystemCache(dTagMatrix[test][8], true);

ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][6]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][7]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][8]);
verifyMetadata(SM5, SM6, true);



/*
Multi Store - Multi Delete Data + MD
--------------------------------
- Store many
- Backup
- Delete multiple recent MD and data entry from system cache
- Restore
- Make sure MD and data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("3.3 Multi Store - Multi Delete Data MD and MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);
ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Storing object " + mTagMatrix[test][7]);
STORE(mTagMatrix[test][7], storesize);
ECHO("Storing object " + mTagMatrix[test][8]);
STORE(mTagMatrix[test][8], storesize);

ECHO("Verifying system cache exists for " + mTagMatrix[test][6]);
SM1=getSystemCache(mTagMatrix[test][6], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][7]);
SM3=getSystemCache(mTagMatrix[test][7], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][8]);
SM5=getSystemCache(mTagMatrix[test][8], true);
ECHO("Getting data for " + mTagMatrix[test][6] + ", tagging with " + dTagMatrix[test][6]);
DEREF(mTagMatrix[test][6],dTagMatrix[test][6]);
ECHO("Getting data for " + mTagMatrix[test][7] + ", tagging with " + dTagMatrix[test][7]);
DEREF(mTagMatrix[test][7],dTagMatrix[test][7]);
ECHO("Getting data for " + mTagMatrix[test][8] + ", tagging with " + dTagMatrix[test][8]);
DEREF(mTagMatrix[test][8],dTagMatrix[test][8]);
ECHO("Verifying system cache exists for data" + dTagMatrix[test][6]);
SM7=getSystemCache(dTagMatrix[test][6], true);
ECHO("Verifying system cache exists for data" + dTagMatrix[test][7]);
SM9=getSystemCache(dTagMatrix[test][7], true);
ECHO("Verifying system cache exists for data" + dTagMatrix[test][8]);
SM11=getSystemCache(dTagMatrix[test][8], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][6] + " from system cache");
REMOVEM(mTagMatrix[test][6]);
ECHO("Removing metadata " + mTagMatrix[test][7] + " from system cache");
REMOVEM(mTagMatrix[test][7]);
ECHO("Removing metadata " + mTagMatrix[test][8] + " from system cache");
REMOVEM(mTagMatrix[test][8]);
ECHO("Removing metadata " + dTagMatrix[test][6] + " from system cache");
REMOVEM(dTagMatrix[test][6]);
ECHO("Removing metadata " + dTagMatrix[test][7] + " from system cache");
REMOVEM(dTagMatrix[test][7]);
ECHO("Removing metadata " + dTagMatrix[test][8] + " from system cache");
REMOVEM(dTagMatrix[test][8]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][6]);
getSystemCache(mTagMatrix[test][6], false);
ECHO("Verifying system cache missing for " + mTagMatrix[test][7]);
getSystemCache(mTagMatrix[test][7], false);
ECHO("Verifying system cache missing for " + mTagMatrix[test][8]);
getSystemCache(mTagMatrix[test][8], false);
ECHO("Verifying system cache missing for " + dTagMatrix[test][6]);
getSystemCache(dTagMatrix[test][6], false);
ECHO("Verifying system cache missing for " + dTagMatrix[test][7]);
getSystemCache(dTagMatrix[test][7], false);
ECHO("Verifying system cache missing for " + dTagMatrix[test][8]);
getSystemCache(dTagMatrix[test][8], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][6]);
SM2=getSystemCache(mTagMatrix[test][6], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][7]);
SM4=getSystemCache(mTagMatrix[test][7], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][8]);
SM6=getSystemCache(mTagMatrix[test][8], true);
ECHO("Verifying system cache exists for " + dTagMatrix[test][6]);
SM8=getSystemCache(dTagMatrix[test][6], true);
ECHO("Verifying system cache exists for " + dTagMatrix[test][7]);
SM10=getSystemCache(dTagMatrix[test][7], true);
ECHO("Verifying system cache exists for " + dTagMatrix[test][8]);
SM12=getSystemCache(dTagMatrix[test][8], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][6]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][7]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][8]);
verifyMetadata(SM5, SM6, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][6]);
verifyMetadata(SM7, SM8, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][7]);
verifyMetadata(SM9, SM10, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][8]);
verifyMetadata(SM11, SM12, true);



/*
Single Store Delete - Delete MD
-------------------------------------------------
- Store one
- Delete one
- Backup
- Remove MD for the md object out from system cache
- Restore
- Make sure data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("4.1 Single Store Delete - Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);

/*
Single Store Delete - Delete Data MD
-------------------------------------------------
- Store one
- Delete one
- Backup
- Remove MD for the data object out from system cache
- Restore
- Make sure data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("4.2 Single Store Delete - Delete Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);

ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM1=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing data " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b1.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM2=getSystemCache(dTagMatrix[test][0], true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);




/*
Single Store Delete - Delete MD and Data MD
-------------------------------------------------------------
- Store one
- Delete one
- Backup
- Remove MD for the md and data object from system cache
- Restore
- Make sure data entry is back in system cache
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("4.3 Single Store Delete - Delete MD and Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);

ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);

ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM3=getSystemCache(dTagMatrix[test][0], true);


ECHO("1");
printSystemMetadata(SM1);
ECHO("2");
printSystemMetadata(SM2);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);
ECHO("Removing data " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);
ECHO("Verifying system cache missing for data " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM4=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
//ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
//verifyMetadata(SM3, SM4, true); //CR 6639939




/*
Simple Store - Add/Delete MD
-----------------------------------------------------------
- Store
- AddMD
- Backup
- Remove system record for the md object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.1 Simple Store - Add/Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][1] + " from system cache");
REMOVEM(mTagMatrix[test][1]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][1]);
getSystemCache(mTagMatrix[test][1], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);



/*
Simple Store - Add/Delete Data MD
---------------------------------------------------------
- Store
- AddMD
- Backup
- Remove system record for the data object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this md 
object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.2 Simple Store - Add/Delete Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);
ECHO("Verifying system cache exists for " + dTagMatrix[test][0]);
SM5=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM6=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM5, SM6, true);

/*
Simple Store - Add Multiple/Delete MD (Original MD)
-----------------------------------------------------------
- Store
- AddMD
- AddMD
- Backup
- Remove system record for the md object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.3 Simple Store - Add Multiple/Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM5, SM6, true);

/*
Simple Store - Add Multiple/Delete MD (middle MD)
-----------------------------------------------------------
- Store
- AddMD
- AddMD
- Backup
- Remove system record for the md object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.4 Simple Store - Add Multiple/Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][1] + " from system cache");
REMOVEM(mTagMatrix[test][1]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][1]);
getSystemCache(mTagMatrix[test][1], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM5, SM6, true);





/*
Simple Store - Add Multiple/Delete MD (most recent md)
-----------------------------------------------------------
- Store
- AddMD
- AddMD
- Backup
- Remove system record for the md object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.5 Simple Store - Add Multiple/Delete MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][2] + " from system cache");
REMOVEM(mTagMatrix[test][2]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][2]);
getSystemCache(mTagMatrix[test][2], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM5, SM6, true);




/*
Simple Store - Add Multiple/Delete Data MD
-----------------------------------------------------------
- Store
- AddMD
- AddMD
- Backup
- Remove system record for the dat object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.6 Simple Store - Add Multiple/Delete Data MD");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);
ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM7=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);
ECHO("Verifying system cache exists for data" + dTagMatrix[test][0]);
SM8=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM5, SM6, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM7, SM8, true);



/*
Simple Store - Add Multiple MD and Delete MD (2nd)
- Store
- AddMD
- AddMD
- Delete 2nd MD
- Backup
- Remove system record for the MD object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.7 Simple Store - Add Multiple MD and Delete MD (2nd)");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache missing for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM5, SM6, true);




/*
Simple Store - Add Multiple MD and Delete MD (3rd)
- Store
- AddMD
- AddMD
- Delete 3rd MD
- Backup
- Remove system record for the MD object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.8 Simple Store - Add Multiple MD and Delete MD (3rd)");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);


ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM5, SM6, true);




/*
Simple Store - Add Multiple MD and Delete MD (2nd and 3rd)
- Store
- AddMD
- AddMD
- Delete 2nd and 3rd MD
- Backup
- Remove system record for the MD object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.9 Simple Store - Add Multiple MD and Delete MD (2nd and 3rd)");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);


ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + mTagMatrix[test][0] + " from system cache");
REMOVEM(mTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + mTagMatrix[test][0]);
getSystemCache(mTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM5, SM6, true);






/*
Simple Store - Add Multiple MD and Delete Data MD (2nd)
- Store
- AddMD
- AddMD
- Delete 2nd MD
- Backup
- Remove system record for the data object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.10 Simple Store - Add Multiple MD and Delete Data MD (2nd)");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);
ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM7=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM8=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM5, SM6, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM7, SM8, true);

/*
Simple Store - Add Multiple MD and Delete Data MD (3rd)
- Store
- AddMD
- AddMD
- Delete 3rd MD
- Backup
- Remove system record for the data object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.11 Simple Store - Add Multiple MD and Delete Data MD (3rd)");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);
ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM7=getSystemCache(dTagMatrix[test][0], true);


ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for data " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM8=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM5, SM6, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM7, SM8, true);


/*
Simple Store - Add Multiple MD and Delete Data MD (2nd and 3rd)
- Store
- AddMD
- AddMD
- Delete 2nd and 3rd MD
- Backup
- Remove system record for the data object (and save it in memory)
- Restore
- Make sure system record is equal to the old system record entry for this 
data object.
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.12 Simple Store - Add Multiple MD and Delete Data MD (2nd and 3rd)");
ECHO("**********************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM3=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM5=getSystemCache(mTagMatrix[test][2], true);
ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM7=getSystemCache(dTagMatrix[test][0], true);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Removing metadata " + dTagMatrix[test][0] + " from system cache");
REMOVEM(dTagMatrix[test][0]);

ECHO("Verifying system cache missing for " + dTagMatrix[test][0]);
getSystemCache(dTagMatrix[test][0], false);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b1",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM2=getSystemCache(mTagMatrix[test][0], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM4=getSystemCache(mTagMatrix[test][1], true);
ECHO("Verifying system cache exists for " + mTagMatrix[test][2]);
SM6=getSystemCache(mTagMatrix[test][2], true);
ECHO("Verifying system cache exists for data " + dTagMatrix[test][0]);
SM8=getSystemCache(dTagMatrix[test][0], true);

ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][0]);
verifyMetadata(SM1, SM2, true);
ECHO("Verifying system cache entry is identical for " + mTagMatrix[test][1]);
verifyMetadata(SM3, SM4, true);
ECHO("Verifying" +
		" system cache entry is identical for " + mTagMatrix[test][2]);
verifyMetadata(SM5, SM6, true);
ECHO("Verifying system cache entry is identical for " + dTagMatrix[test][0]);
verifyMetadata(SM7, SM8, true);





/*
Trigger Refcount Failure
-------------------------
- Store object A
- Backup session 1
- AddMD to A creating object B
- Remove the system cache entry for object B
- Backup session 2
- Restore session 2 (with the special REPLAY_OPTION)
- Restore session 1 (without the REPLAY_OPTION) 
- Verify the reference counts, at this point there will be a failure
*/
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("6.1 Trigger Refcount Failure");
ECHO("******************************************************************");

WIPECACHES(); MOVEALL();

ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);

ECHO("Verifying system cache exists for " + mTagMatrix[test][0]);
SM1=getSystemCache(mTagMatrix[test][0], true);

ECHO("Getting data for " + mTagMatrix[test][0] + ", tagging with " + dTagMatrix[test][0]);
DEREF(mTagMatrix[test][0],dTagMatrix[test][0]);

ECHO("Verifying refCount on " + dTagMatrix[test][0] + " is 1, with maxRef 1.");
REFCNTCHECK(dTagMatrix[test][0], Constants.ALL_FRAGS, 1, 1);

ECHO("Full Backup.");
BACKUP("b1",0);

ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);

ECHO("Verifying refCount on " + dTagMatrix[test][0] + " is 2, with maxRef 2.");
REFCNTCHECK(dTagMatrix[test][0], Constants.ALL_FRAGS, 2, 2);

ECHO("Verifying system cache exists for " + mTagMatrix[test][1]);
SM1=getSystemCache(mTagMatrix[test][1], true);

ECHO("Removing metadata " + mTagMatrix[test][1] + " from system cache");
REMOVEM(mTagMatrix[test][1]);

ECHO("Verifying refCount on " + dTagMatrix[test][0] + " is 2, with maxRef 2.");
REFCNTCHECK(dTagMatrix[test][0], Constants.ALL_FRAGS, 2, 2);

ECHO("Incremental Backup.");
INCBACKUP("b2",0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring.");
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring.");
RESTORE("b1", 0);

ECHO("Verifying refCount on " + dTagMatrix[test][0] + " is 2, with maxRef 2.");
REFCNTCHECK(dTagMatrix[test][0], Constants.ALL_FRAGS, 2, 2);





ECHO("\n");
ECHO("End of unit test.");
ECHO("No exceptions encountered.");
ECHO("Success.");