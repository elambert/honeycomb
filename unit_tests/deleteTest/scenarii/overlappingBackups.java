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
import java.lang.Thread;
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


int test = 0;
ECHO("**********************************************************************");
ECHO("**********************************************************************");
ECHO("*******************                                *******************");
ECHO("*******************    Overlapping Backups Tests   *******************");
ECHO("*******************                                *******************");
ECHO("**********************************************************************");
ECHO("**********************************************************************");


//
// 1.1 - 2 overlap stores
// - Get t1
// - Do Stores
// - Get t2
// - Do Stores
// - Get t3
// - Do Stores
// - Get t4
// - Backup t1 -> t3
// - Backup t2 -> t4
// - Wipe Data
// - Restore
// - Verify
ECHO("\n");
ECHO("TEST: " + test);
ECHO("1.1 - 2 Overlapping Stores");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//1.2 - 2 overlap stores/deletes
//- Get t1
//- Do Store/Delete
//- Get t2
//- Do Store/Delete
//- Get t3
//- Do Store/Delete
//- Get t4
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("1.2 - 2 Overlapping Stores/Deletes");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);


t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);


t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);


t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//1.3 - 2 overlap stores/add metadata
//- Get t1
//- Do Stores/AddMD
//- Get t2
//- Do Stores/AddMD
//- Get t3
//- Do Stores/AddMD
//- Get t4
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("1.3 - 2 Overlapping Stores/AddMD");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);


t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][3]);


t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][4],mTagMatrix[test][5]);


t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//2.1 - 2 overlap stores with deletes inside overlap
//- Get t1
//- Do Stores
//- Get t2
//- Do Deletes
//- Get t3
//- Do Stores
//- Get t4
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("2.1 - 2 Overlapping Stores - Deletes inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//2.2 - 2 overlap stores with AddMD inside overlap
//- Get t1
//- Do Stores
//- Get t2
//- Do AddMD's
//- Get t3
//- Do Stores
//- Get t4
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("2.2 - 2 Overlapping Stores - AddMD inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][1],mTagMatrix[test][3]);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//2.3 - 2 overlap stores - delete stores made inside overlap
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- Delete Stores 2
//- Get t4
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("2.3 - 2 Overlapping Stores - Deletes stores made inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//2.4 - 2 overlap stores - addmd to stores made inside overlap
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- ADDMD to Stores 2
//- Get t4
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("2.4 - 2 Overlapping Stores - AddMD to stores made inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Adding metadata " + mTagMatrix[test][4]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][4]);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][3],mTagMatrix[test][5]);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//3.1 - 2 overlap stores / different backup location
//- Get t1
//- Do Stores
//- Get t2
//- Do Stores
//- Get t3
//- Backup t1 -> t3
//- Do Stores
//- Get t4
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("3.1 - 2 Overlapping Stores / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//3.2 - 2 overlap stores/deletes / different backup location
//- Get t1
//- Do Store/Delete
//- Get t2
//- Do Store/Delete
//- Get t3
//- Backup t1 -> t3
//- Do Store/Delete
//- Get t4
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("3.2 - 2 Overlapping Stores/Deletes / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//3.3 - 2 overlap stores/add metadata / different backup location
//- Get t1
//- Do Stores/AddMD
//- Get t2
//- Do Stores/AddMD
//- Get t3
//- Backup t1 -> t3
//- Do Stores/AddMD
//- Get t4
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("3.3 - 2 Overlapping Stores/AddMD / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][3]);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][4],mTagMatrix[test][5]);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//4.1 - 2 overlap stores with deletes inside overlap / different backup location
//- Get t1
//- Do Stores
//- Get t2
//- Do Deletes
//- Get t3
//- Backup t1 -> t3
//- Do Stores
//- Get t4
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("4.1 - 2 Overlapping Stores - Deletes inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//4.2 - 2 overlap stores with AddMD inside overlap / different backup location
//- Get t1
//- Do Stores
//- Get t2
//- Do AddMD's
//- Get t3
//- Backup t1 -> t3
//- Do Stores
//- Get t4
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("4.2 - 2 Overlapping Stores - AddMD inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][1],mTagMatrix[test][3]);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//4.3 - 2 overlap stores - delete stores made inside overlap / different backup location
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- Backup t1 -> t3
//- Delete Stores 2
//- Get t4
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("4.3 - 2 Overlapping Stores - Deletes stores made inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//4.4 - 2 overlap stores - addmd to stores made inside overlap / different backup location
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- Backup t1 -> t3
//- ADDMD to Stores 2
//- Get t4
//- Backup t2 -> t4
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("4.4 - 2 Overlapping Stores - AddMD to stores made inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Adding metadata " + mTagMatrix[test][4]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][4]);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][3],mTagMatrix[test][5]);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//5.1 - 3 overlap stores
//- Get t1
//- Do Stores
//- Get t2
//- Do Stores
//- Get t3
//- Do Stores
//- Get t4
//- Do Stores
//- Get t5
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.1 - 3 Overlapping Stores");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Storing object " + mTagMatrix[test][7]);
STORE(mTagMatrix[test][7], storesize);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//5.2 - 3 overlap stores/deletes
//- Get t1
//- Do Store/Delete
//- Get t2
//- Do Store/Delete
//- Get t3
//- Do Store/Delete
//- Get t4
//- Do Store/Delete
//- Get t5
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.2 - 3 Overlapping Stores/Deletes");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//5.3 - 3 overlap stores/add metadata
//- Get t1
//- Do Stores/AddMD
//- Get t2
//- Do Stores/AddMD
//- Get t3
//- Do Stores/AddMD
//- Get t4
//- Do Stores/AddMD
//- Get t5
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("5.3 - 3 Overlapping Stores/AddMD");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);


t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][3]);


t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][4],mTagMatrix[test][5]);


t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Adding metadata " + mTagMatrix[test][7]);
ADDM(mTagMatrix[test][6],mTagMatrix[test][7]);


t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//6.1 - 3 overlap stores with deletes inside overlap
//- Get t1
//- Do Stores
//- Get t2
//- Do Deletes
//- Get t3
//- Do Deletes
//- Get t4
//- Do Stores
//- Get t5
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("6.1 - 3 Overlapping Stores - Deletes inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//6.2 - 3 overlap stores with AddMD inside overlap
//- Get t1
//- Do Stores
//- Get t2
//- Do AddMD's
//- Get t3
//- Do AddMD's
//- Get t4
//- Do Stores
//- Get t5
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("6.2 - 3 Overlapping Stores - AddMD inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][1],mTagMatrix[test][3]);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Adding metadata " + mTagMatrix[test][4]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][4]);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][1],mTagMatrix[test][5]);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Storing object " + mTagMatrix[test][7]);
STORE(mTagMatrix[test][7], storesize);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//6.3 - 3 overlap stores - delete stores made inside overlap
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- Do Stores 3
//- Get t4
//- Delete Stores 2 and 3
//- Get t5
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("6.3 - 3 Overlapping Stores - Deletes stores made inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);
ECHO("Deleting object " + mTagMatrix[test][4]);
DELETE(mTagMatrix[test][4], true);
ECHO("Deleting object " + mTagMatrix[test][5]);
DELETE(mTagMatrix[test][5], true);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//6.4 - 3 overlap stores - addmd to stores made inside overlap
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- Do Stores 3
//- Get t4
//- ADDMD to Stores 2 and 3
//- Get t5
//- Backup t1 -> t3
//- Backup t2 -> t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("6.4 - 3 Overlapping Stores - AddMD to stores made inside overlap");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Adding metadata " + mTagMatrix[test][6]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][6]);
ECHO("Adding metadata " + mTagMatrix[test][7]);
ADDM(mTagMatrix[test][3],mTagMatrix[test][7]);
ECHO("Adding metadata " + mTagMatrix[test][8]);
ADDM(mTagMatrix[test][4],mTagMatrix[test][8]);
ECHO("Adding metadata " + mTagMatrix[test][9]);
ADDM(mTagMatrix[test][5],mTagMatrix[test][9]);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//7.1 - 3 overlap stores / different backup locations
//- Get t1
//- Do Stores
//- Get t2
//- Do Stores
//- Get t3
//- Backup t1 -> t3
//- Do Stores
//- Get t4
//- Backup t2 -> t4
//- Do Stores
//- Get t4
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("7.1 - 2 Overlapping Stores / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Storing object " + mTagMatrix[test][7]);
STORE(mTagMatrix[test][7], storesize);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);
ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//7.2 - 3 overlap stores/deletes / different backup location
//- Get t1
//- Do Store/Delete
//- Get t2
//- Do Store/Delete
//- Get t3
//- Backup t1 -> t3
//- Do Store/Delete
//- Get t4
//- Backup t2 -> t4
//- Do Store/Delete
//- Get t5
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("7.2 - 3 Overlapping Stores/Deletes / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();





//
//7.3 - 3 overlap stores/add metadata / different backup location
//- Get t1
//- Do Stores/AddMD
//- Get t2
//- Do Stores/AddMD
//- Get t3
//- Backup t1 -> t3
//- Do Stores/AddMD
//- Get t4
//- Backup t2 -> t4
//- Do Stores/AddMD
//- Get t5
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("7.3 - 2 Overlapping Stores/AddMD / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Adding metadata " + mTagMatrix[test][1]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][1]);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][3]);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][4],mTagMatrix[test][5]);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);

ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Adding metadata " + mTagMatrix[test][7]);
ADDM(mTagMatrix[test][6],mTagMatrix[test][7]);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//8.1 - 3 overlap stores with deletes inside overlap / different backup location
//- Get t1
//- Do Stores
//- Get t2
//- Do Deletes
//- Get t3
//- Backup t1 -> t3
//- Do Deletes
//- Get t4
//- Backup t2 -> t4
//- Do Stores
//- Get t5
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("8.1 - 3 Overlapping Stores - Deletes inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Deleting object " + mTagMatrix[test][0]);
DELETE(mTagMatrix[test][0], true);
ECHO("Deleting object " + mTagMatrix[test][1]);
DELETE(mTagMatrix[test][1], true);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//8.2 - 3 overlap stores with AddMD inside overlap / different backup location
//- Get t1
//- Do Stores
//- Get t2
//- Do AddMD's
//- Get t3
//- Backup to -> t3
//- Do AddMD's
//- Get t4
//- Backup t2 -> t4
//- Do Stores
//- Get t5
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("8.2 - 3 Overlapping Stores - AddMD inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Adding metadata " + mTagMatrix[test][2]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][2]);
ECHO("Adding metadata " + mTagMatrix[test][3]);
ADDM(mTagMatrix[test][1],mTagMatrix[test][3]);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Adding metadata " + mTagMatrix[test][4]);
ADDM(mTagMatrix[test][0],mTagMatrix[test][4]);
ECHO("Adding metadata " + mTagMatrix[test][5]);
ADDM(mTagMatrix[test][1],mTagMatrix[test][5]);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Storing object " + mTagMatrix[test][6]);
STORE(mTagMatrix[test][6], storesize);
ECHO("Storing object " + mTagMatrix[test][7]);
STORE(mTagMatrix[test][7], storesize);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();



//
//8.3 - 3 overlap stores - delete stores made inside overlap / different backup location
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- Backup t1 -> t3
//- Do Stores 3
//- Get t4
//- Backup t2 -> t4
//- Delete Stores 2 and 3
//- Get t5
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("8.3 - 3 Overlapping Stores - Deletes stores made inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t2,t4,0);

ECHO("Deleting object " + mTagMatrix[test][2]);
DELETE(mTagMatrix[test][2], true);
ECHO("Deleting object " + mTagMatrix[test][3]);
DELETE(mTagMatrix[test][3], true);
ECHO("Deleting object " + mTagMatrix[test][4]);
DELETE(mTagMatrix[test][4], true);
ECHO("Deleting object " + mTagMatrix[test][5]);
DELETE(mTagMatrix[test][5], true);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();




//
//8.4 - 3 overlap stores - addmd to stores made inside overlap / different backup location
//- Get t1
//- Do Stores 1
//- Get t2
//- Do Stores 2
//- Get t3
//- Backup t1 -> t3
//- Do Stores 3
//- Get t4
//- Backup t2 -> t4
//- ADDMD to Stores 2 and 3
//- Get t5
//- Backup t3 -> t5
//- Wipe Data
//- Restore
//- Verify
ECHO("\n");
test++;
ECHO("TEST: " + test);
ECHO("8.4 - 3 Overlapping Stores - AddMD to stores made inside overlap / Different backup location");
ECHO("**********************************************************************");

t1 = System.currentTimeMillis();
ECHO("Current Time: " + t1);
ECHO("Storing object " + mTagMatrix[test][0]);
STORE(mTagMatrix[test][0], storesize);
ECHO("Storing object " + mTagMatrix[test][1]);
STORE(mTagMatrix[test][1], storesize);

t2 = System.currentTimeMillis();
ECHO("Current Time: " + t2);
ECHO("Storing object " + mTagMatrix[test][2]);
STORE(mTagMatrix[test][2], storesize);
ECHO("Storing object " + mTagMatrix[test][3]);
STORE(mTagMatrix[test][3], storesize);

t3 = System.currentTimeMillis();
ECHO("Current Time: " + t3);
ECHO("Interval backup: " + t1 + " -> " + t3);
BACKUPINTERVAL("b1",t1,t3,0);

ECHO("Storing object " + mTagMatrix[test][4]);
STORE(mTagMatrix[test][4], storesize);
ECHO("Storing object " + mTagMatrix[test][5]);
STORE(mTagMatrix[test][5], storesize);

t4 = System.currentTimeMillis();
ECHO("Current Time: " + t4);
ECHO("Interval backup: " + t2 + " -> " + t4);
BACKUPINTERVAL("b2",t3,t4,0);

ECHO("Adding metadata " + mTagMatrix[test][6]);
ADDM(mTagMatrix[test][2],mTagMatrix[test][6]);
ECHO("Adding metadata " + mTagMatrix[test][7]);
ADDM(mTagMatrix[test][3],mTagMatrix[test][7]);
ECHO("Adding metadata " + mTagMatrix[test][8]);
ADDM(mTagMatrix[test][4],mTagMatrix[test][8]);
ECHO("Adding metadata " + mTagMatrix[test][9]);
ADDM(mTagMatrix[test][5],mTagMatrix[test][9]);

t5 = System.currentTimeMillis();
ECHO("Current Time: " + t5);

ECHO("Interval backup: " + t3 + " -> " + t5);
BACKUPINTERVAL("b3",t3,t5,0);

ECHO("Moving data disks over and reinitializing data disks.");
MOVEALL();

ECHO("Restoring b3: " + t3 + " -> " + t5);
RESTORE("b3",com.sun.honeycomb.oa.bulk.Session.REPLAY_BACKUP_OPTION);

ECHO("Restoring b2: " + t2 + " -> " + t4);
RESTORE("b2",0);

ECHO("Restoring b1: " + t1 + " -> " + t3);
RESTORE("b1",0);

ECHO("Verifying all restored fragments are identical to originals");
COMPARE();
