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



package com.sun.honeycomb.platform.diskinit;

import java.io.File;
import java.io.IOException;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * A JUnit-based unit test for DiskInit.
 */
public class DiskInitTest extends TestCase {
    
    private HardwareProfile profile = null;

    private DiskInitImpl diskinit = null;
    
    /**
     * Constructor for this unit test.
     * @param testName the name of the unit test
     */
    public DiskInitTest(java.lang.String testName) {
        super(testName);
    }
    
    /**
     * Allows unit tests to be run together in a suite.
     * @return a test suite that contains a single test - this one
     */
    public static Test suite() {
        TestSuite suite = new TestSuite(DiskInitTest.class);
        return suite;
    }
    
    /**
     * Allows test to be run stand-alone from the command-line.
     * @param args command-line args (ignored)
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }
    
    public void setUp() {
	// customize this as needed
	profile = 
	    HardwareProfileFactory.getProfile(HardwareProfile.LAPTOP_PROFILE);

        String os = System.getProperties().getProperty("os.name");
        if ("Linux".equals(os)) {
            diskinit = new DiskInitImpl(profile, 0);
        } else if ("SunOS".equals(os)) {
            // TODO: enable Solaris when needed
            // diskinit =  new SolarisDiskInit(useVirtualDisks);
            fail("Currently unsupported OS: " + os);
        }
    }
    
    public void tearDown() {
	profile = null;
	diskinit = null;
    }

    public void testDiskInit() throws IOException {
        String[] disks = diskinit.probe();
        // unmount disk if already mounted
        for (int i=0; i < disks.length; i++) {
            try {
                diskinit.unmount(disks[i]);
            } catch (IOException ignore) {
                // ignore exception if the disk is not mounted
            }
        }

        for (int i=0; i < disks.length; i++) {
            diskinit.partition(disks[i]);
            diskinit.checkPartitionTable(disks[i]);
            diskinit.mkfs(disks[i]);
            diskinit.fsck(disks[i], DiskInit.BOOT_PARTITION, false);
            diskinit.fsck(disks[i], DiskInit.DATA_PARTITION, false);
            diskinit.mount(disks[i]);
            diskinit.checkDirs(disks[i], DiskInit.BOOT_PARTITION);
            diskinit.checkDirs(disks[i], DiskInit.DATA_PARTITION);
        }
    }

    public void testDiskStates() throws IOException {
        Disk di = null;
        String[] disks = diskinit.probe();
        for (int i=0; i < disks.length; i++) {
	    di = diskinit.getInfo(disks[i]);
	    System.out.println(di);
	    assertEquals(DiskInit.STATE_OFFLINE, di.getStatus());

            diskinit.enable(disks[i]);
	    di = diskinit.getInfo(disks[i]);
	    assertEquals(DiskInit.STATE_ENABLED, di.getStatus());

            diskinit.disable(disks[i]);
	    di = diskinit.getInfo(disks[i]);
	    assertEquals(DiskInit.STATE_DISABLED, di.getStatus());

            diskinit.enable(disks[i]);
	    di = diskinit.getInfo(disks[i]);
	    assertEquals(DiskInit.STATE_ENABLED, di.getStatus());

            diskinit.lock(disks[i]);
	    di = diskinit.getInfo(disks[i]);
	    assertEquals(DiskInit.STATE_INUSE, di.getStatus());

            diskinit.unlock(disks[i]);
	    di = diskinit.getInfo(disks[i]);
	    assertEquals(DiskInit.STATE_ENABLED, di.getStatus());
        }
    }
}
