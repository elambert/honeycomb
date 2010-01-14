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

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.common.NewObjectIdentifier;
import 
    com.sun.honeycomb.oa.upgrade.UpgraderFragmentFile.UpgraderTmpFragmentFile;
import 
    com.sun.honeycomb.oa.upgrade.UpgraderFragmentFile.UpgraderRepoFragmentFile;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.TmpWorker;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.RepoWorker;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.FragmentNameFilter;
import com.sun.honeycomb.oa.Common;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileFilter;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.List;

public class DiskUpgrader_t1 extends UpgradeTestcase {
    private static Logger log
        = Logger.getLogger(DiskUpgrader_t1.class.getName());
    private File workDir = new File(workDirectory);

    /**********************************************************************/
    public DiskUpgrader_t1(String name) {
        super(name);
    }

    /**********************************************************************/
    private void doUpgrade(DiskUpgrader du) throws Exception {
	assertEquals("Temp disk files", 0, getTempFilesCount());
        if (Upgrader.upgradeable(du.getDisk())) {
            du.start();
            du.join();
            du.checkException();
        }
	assertEquals("Temp disk files", 0, getTempFilesCount());
    }

    /**********************************************************************/
    public void testUpgrade() throws Exception {
        assertTrue("work directory", !workDir.isDirectory());
        DiskUpgrader du = new DiskUpgrader(disk);
        assertEquals("upgraded files", 0, du.getUpgradeCount());
        doUpgrade(du);
        assertEquals("upgraded files", 5, du.getUpgradeCount());
        assertTrue("work directory", workDir.isDirectory());
        // try again
        du = new DiskUpgrader(disk);
        doUpgrade(du);
        assertEquals("upgraded files", 0, du.getUpgradeCount());
        assertTrue("work directory", workDir.isDirectory());
    }

    private static class PartialFileFilter implements FileFilter {
	String name;
	NewObjectIdentifier oid; 

	public PartialFileFilter(String name) {
	    this.name = name;
	    this.oid = Common.extractOIDFromFilename(name);
	}
	public boolean accept(File pathname) {
	    if (pathname.getName().matches(".svn")) {
		return false;
	    } else if (pathname.getName().startsWith(oid.getUID().toString())) {
		return true;
	    }
	    return false;
	}
    }

    /**********************************************************************/
    public void testPartialUpgrade() throws Exception {
	// Create a 1.1 file with a 1.0 name in tmp-upgrade
	DiskUpgrader du = new DiskUpgrader(disk);
	doUpgrade(du);
	verifyFileCount(5, 0, du);
	File upgraded = (new File(FRAG_DIR).listFiles())[0];
	File[] original 
	    = new File(RESOURCE_DIR).listFiles(new PartialFileFilter(upgraded.getName()));
	File newName = new File(workDir.getPath() + File.separator 
				+ original[0].getName());
	upgraded.renameTo(newName);
	new File(disk.getPath() + File.separator + "VERSION").delete();
	du = new DiskUpgrader(disk);
	doUpgrade(du);
	verifyFileCount(1, 0, du);
    }
    
    /**********************************************************************/
    private void verifyFileCount(int count, int failed, DiskUpgrader du)
        throws Exception {
        // Account for temporary file that has the list of fragments.
        assertEquals("upgraded files", count, du.getUpgradeCount());

	String[] list = workDir.list();
	for (int i = 0; i < workDir.list().length; i++) {
	    log.warning(list[i]);
	}

	FilenameFilter filter = new FilenameFilter() { 
		public boolean accept(File dir, String name) {
		    return name.matches("(\\S{36})(\\.(\\d+)){7}_(\\d+)");
		}
	    };
	    
	String[] upgraded = workDir.list(filter);
        assertEquals("1.1 files in work directory", 0, upgraded.length);
	String[] failedFiles = workDir.list(new FragmentNameFilter());
        assertEquals("1.0 files in work directory", failed, failedFiles.length);
	assertEquals("1.0 files in tmp-close directory", 0, 
		     new File(tempDirectory).list(new FragmentNameFilter()).length);
	assertEquals("1.1 files in tmp-close directory", 4, 
		     new File(tempDirectory).list(filter).length);
    }

    /**********************************************************************/
    public void testUpgradeFailOneFragment() throws Exception {
        DiskUpgrader du = new FailingDiskUpgrader(disk, 1);
	doUpgrade(du);
        verifyFileCount(4, 1, du);
        // try again to upgrade remaining file. since version file is
        // already written, no fragments should be upgraded.
        du = new DiskUpgrader(disk);
        doUpgrade(du);
        verifyFileCount(0, 1, du);
    }

    /**********************************************************************/
    public void testUpgradeFailAllFragment() throws Exception {
        DiskUpgrader du = new FailingDiskUpgrader(disk, 7);
        doUpgrade(du);
        verifyFileCount(0, 3, du);
        // try again to upgrade files. since version file is
        // already written, no fragments should be upgraded.
        du = new DiskUpgrader(disk);
        doUpgrade(du);
        verifyFileCount(0, 3, du);
    }

    /**********************************************************************/
    public void testUpgradeFailOneFragmentInterrupt() throws Exception {
        DiskUpgrader du = new FailingDiskUpgrader(disk, 1, true);
        doUpgrade(du);
        verifyFileCount(4, 1, du);
        // try again
	du = new DiskUpgrader(disk);
        doUpgrade(du);
        verifyFileCount(1, 0, du);
    }

    /**********************************************************************/
    public void testUpgradeFailAllFragmentInterrupt() throws Exception {
        DiskUpgrader du = new FailingDiskUpgrader(disk, 7, true);
        doUpgrade(du);
        verifyFileCount(0, 3, du);
        // try again
        du = new DiskUpgrader(disk);
        doUpgrade(du);
        verifyFileCount(3, 0, du);
    }

    /**********************************************************************/
    public void testWorkDirectory() throws Exception {
        workDir.mkdir();
        assertTrue("work directory", workDir.isDirectory());
        DiskUpgrader du = new DiskUpgrader(disk);
        assertEquals("upgraded files", 0, du.getUpgradeCount());
        doUpgrade(du);
        assertEquals("upgraded files", 5, du.getUpgradeCount());
        assertTrue("work directory", workDir.isDirectory());
    }

    /**********************************************************************/
    public class FailingDiskUpgrader extends DiskUpgrader {
        private int failCount;
	private int failRemaining;
        private boolean interrupt;
	private DiskUpgrader.Type type;
	
        public FailingDiskUpgrader(Disk disk, int failCount) {
            this(disk, failCount, false, DiskUpgrader.DATA);
        }
        public FailingDiskUpgrader(Disk disk, int failCount, 
				   boolean interrupt) {
	    this(disk, failCount, interrupt, DiskUpgrader.DATA);
        }
        public FailingDiskUpgrader(Disk disk, int failCount, 
				   boolean interrupt, DiskUpgrader.Type type) {
            super(disk);
            this.failCount = failCount;
	    this.failRemaining = failCount;
            this.interrupt = interrupt;
	    this.type = type;
        }
        protected void writeVersionFile() {
	    if (!interrupt) {
		super.writeVersionFile();
            }
        }
        protected Worker newWorker(String filename, DiskUpgrader.Type dtype) {
	    /* XXX Works only for failCount= 0, 1, or 7 XXX */
            if ((failCount <= 0) || (failRemaining <= 0)) {
                return super.newWorker(filename, dtype);
            }

	    if (failCount == 1) {
		if (dtype == type) {
		    failRemaining--;
		    if (dtype == DiskUpgrader.DATA) {
			return new FailingWorker(filename);
		    }
		    if (dtype == DiskUpgrader.TEMP) {
			return new FailingTmpWorker(filename);
		    } 
		    if (dtype == DiskUpgrader.REPO) {
			return new FailingRepoWorker(filename);
		    }
		    throw new IllegalStateException("Unknown type " + dtype.name);
		} else {
		    return super.newWorker(filename, dtype);
		}	    
	    }

	    failRemaining--;
	    if (dtype == DiskUpgrader.DATA) {
		return new FailingWorker(filename);
	    }
	    if (dtype == DiskUpgrader.TEMP) {
		return new FailingTmpWorker(filename);
	    } 
	    if (dtype == DiskUpgrader.REPO) {
		return new FailingRepoWorker(filename);
	    }
	    throw new IllegalStateException("Unknown type " + dtype.name);
	}

	public class FailingWorker extends Worker {
	    public FailingWorker(String filename) {
		super(filename);
	    }

	    protected UpgraderFragmentFile
		newFragmentFile(NewObjectIdentifier oid,
				int fragmentId, Disk disk,
				ByteBuffer readBuffer,
				ByteBuffer writeBuffer,
				Profile profile) {
		String workDir = disk.getPath() + File.separator
		    + "tmp-upgrade";
		return new UpgraderFragmentFile(oid, fragmentId,
						disk, readBuffer,
						writeBuffer, workDir,
						profile) {
			protected UpgraderFragmentFooter readFragmentFooter() 
			    throws UpgraderException {
			    throw new UpgraderException("expected");
			}

		    };
	    }
	}

	public class FailingTmpWorker extends TmpWorker {
	    public FailingTmpWorker(String filename) {
		super(filename);
	    }
	    
	    protected UpgraderFragmentFile
		newFragmentFile(NewObjectIdentifier oid,
				int fragmentId, Disk disk,
				ByteBuffer readBuffer,
				ByteBuffer writeBuffer,
				DiskUpgrader.Profile profile) {
		String workDir = disk.getPath() + File.separator
		    + "tmp-upgrade";
		return new UpgraderTmpFragmentFile(oid, fragmentId,
						   disk, 
						   readBuffer,
						   writeBuffer, 
						   workDir,
						   profile) {
			protected UpgraderFragmentFooter readFragmentFooter() 
			    throws UpgraderException {
			    throw new UpgraderException("expected");
			}
		    };
	    }
	}

	public class FailingRepoWorker extends RepoWorker {
	    public FailingRepoWorker(String filename) {
		super(filename);
	    }
	    
	    protected UpgraderFragmentFile
		newFragmentFile(NewObjectIdentifier oid,
				int fragmentId, Disk disk,
				ByteBuffer readBuffer,
				ByteBuffer writeBuffer,
				DiskUpgrader.Profile profile) {
		String workDir = disk.getPath() + File.separator
		    + "tmp-upgrade";
		return new UpgraderRepoFragmentFile(oid, fragmentId,
						   disk, 
						   readBuffer,
						   writeBuffer, 
						   workDir,
						   profile) {
			protected UpgraderFragmentFooter readFragmentFooter() 
			    throws UpgraderException {
			    throw new UpgraderException("expected");
			}
		    };
	    }
	}
	
    }
}
