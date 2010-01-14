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



package com.sun.honeycomb.oa.upgrade;

import com.sun.honeycomb.test.Testcase;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.UID;
import com.sun.honeycomb.oa.upgrade.UpgradeableList.OutboardList;
import com.sun.honeycomb.oa.upgrade.UpgradeableList.FileInfo;
import com.sun.honeycomb.test.ReflectedAccess;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;

import java.io.File;
import java.util.logging.Logger;
import java.util.Random;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;

public class UpgradeableList_t1 extends UpgradeTestcase {
    private static Logger log
        = Logger.getLogger(UpgradeableList_t1.class.getName());

    /**********************************************************************/
    public UpgradeableList_t1(String name) {
        super(name);
    }

    /**********************************************************************/
    protected void setUp() throws Exception {
        super.setUp();
        // create a work directory
        File workDir = new File(workDirectory);
        if ((!workDir.exists()) && (!workDir.mkdir())) {
            fail("Failed to create " + workDir.getName());
        }
    }

    /**********************************************************************/
    public void testOutboardList() throws Exception {
        Random random = new Random();
        UpgradeableList ul = new UpgradeableList(disk);
        List list = new LinkedList();
        NewObjectIdentifier oid1
            = new UpgraderNewObjectIdentifier(new UID(),
                                              random.nextInt(10000),
                                              (byte) 0, 0);
        FileInfo fi1 = new FileInfo(oid1.toString() + "_1", new Long(1));
        NewObjectIdentifier oid2
            = new UpgraderNewObjectIdentifier(new UID(),
                                              random.nextInt(10000),
                                              (byte) 0, 0);
        FileInfo fi2 = new FileInfo(oid2.toString() + "_1", new Long(2));
        list.add(fi2);
        ul.add(list);
        list.clear();
        list.add(fi1);
        ul.add(list);
        assertEquals("count", 2, ul.count());

        Iterator it = ul.iterator();
        assertTrue("hasNext", it.hasNext());
        FileInfo fi = (FileInfo) ul.next();
        UpgraderNewObjectIdentifier uoid
            = UpgraderNewObjectIdentifier.fromFilename(fi.getFile());

        assertEquals("oid", oid2.toString(), uoid.toString());
        assertEquals("inode", fi2, fi);
        assertTrue("hasNext", it.hasNext());
        fi = (FileInfo) ul.next();
        uoid = UpgraderNewObjectIdentifier.fromFilename(fi.getFile());
        assertEquals("oid", oid1.toString(), uoid.toString());
        assertEquals("inode", fi1, fi);
    }

    /**********************************************************************/
    public void testOutboardListMany() throws Exception {
        verifyOutboardList(1);
        verifyOutboardList(2);
        verifyOutboardList(10);
        verifyOutboardList(15);
        verifyOutboardList(20);
        verifyOutboardList(31);
        verifyOutboardList(1000);
    }

    /**********************************************************************/
    private void verifyOutboardList(int backingLimit) throws Exception {
        Field backingLimitField
            = ReflectedAccess.getField(UpgradeableList.OutboardList.class,
                                       "BACKING_LIMIT");
        backingLimitField.set(null, new Integer(1));
        Random random = new Random();
        UpgradeableList ul = new UpgradeableList(disk);
        int count = 30;
        NewObjectIdentifier[] oids = new NewObjectIdentifier[count];
        FileInfo[] fis = new FileInfo[count];
        for (int i = 0; i < count; i++) {
            oids[i]
                = new UpgraderNewObjectIdentifier(new UID(),
                                                  random.nextInt(10000),
                                                  (byte) (i % 2), 0);
            fis[i] = new FileInfo(oids[i].toString() + "_1", new Long(i + 1));
        }
        int chance = random.nextInt(3);
        List list = new LinkedList();
        for (int i = 0; i < 3; i++) {
            int start = i * 10;
            for (int j = start; j < start + 10; j++) {
                list.add(fis[j]);
            }
            ul.add(list);
            list.clear();
        }
        assertEquals("count", count, ul.count());
        int index = 0;
        for (Iterator it = ul.iterator(); it.hasNext(); ) {
            FileInfo fi = (FileInfo) ul.next();
            UpgraderNewObjectIdentifier uoid
                = UpgraderNewObjectIdentifier.fromFilename(fi.getFile());
            assertEquals("oid", oids[index].toString(), uoid.toString());
            assertEquals("inode", fis[index], fi);
            index++;
        }
        assertEquals("count", count, index);
    }

    /**********************************************************************/
    // modify the script in
    // build/unit_tests/dist/run_tests.sh. specify java params -Xms32m -Xmx32m
    // (for example) to test memory usage.
    // performance: this test should take 10-13 minutes. A little over
    // 1 million files (per node) is the upgrade target for now
    // (Stanford cluster).
    public void xtestPerformance() throws Exception {
        Random random = new Random();
        UpgradeableList ul = new UpgradeableList(disk);
        int count = 1000000;
        long start = System.currentTimeMillis();
        List list = new LinkedList();
        for (int i = 0; i < count; i++) {
            NewObjectIdentifier oid
                = new UpgraderNewObjectIdentifier(new UID(),
                                                  random.nextInt(10000),
                                                  (byte) (i % 2), 0);
            FileInfo fi = new FileInfo(oid.toString() + "_1", new Long(i + 1));
            list.add(fi);
            if ((i % 10000) == 0) {
                long start1 = System.currentTimeMillis();
                ul.add(list);
                System.out.println("i is " + i + ", "
                                   + (System.currentTimeMillis() - start1));
                list.clear();
                long memUsage
                    = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                // memory usage is not very revealing, since garbage
                // collector runs at its own pace.
                System.out.println("memory usage for " + i + " files is "
                                   + memUsage);
            }
        }
        System.out.println("Total time for " + count + " files: "
                           + ((System.currentTimeMillis() - start) / 1000) + " seconds");
    }
}


