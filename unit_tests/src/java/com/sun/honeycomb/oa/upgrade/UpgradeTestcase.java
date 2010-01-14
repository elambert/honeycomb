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
import com.sun.honeycomb.test.Testcase;
import com.sun.honeycomb.oa.Common;

import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.List;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;

public abstract class UpgradeTestcase extends Testcase {
    private static Logger log
        = Logger.getLogger(UpgradeTestcase.class.getName());
    private static final String BASE_DIR
        = System.getProperty("oa.upgrade.basedir", ".");
    protected static final String RESOURCE_DIR = BASE_DIR + "/share/upgrade";
    private static final String DATA_DIR = BASE_DIR + "/data";
    protected static final String FRAG_DIR = DATA_DIR + "/1/00/09";
    protected static final String FRAG_DIR2 = DATA_DIR + "/1/98/01";
    protected static final String TEMP_DIR = DATA_DIR + "/1/tmp-close";
    protected final Disk disk
        = new Disk(new DiskId(101, 1), "foo", DATA_DIR + "/1", "bar",
                   0, 0, 0, 0, 0, 0, 0, false);
    protected final String workDirectory
        = new String(disk.getPath() + File.separator + "tmp-upgrade");
    protected final String tempDirectory = Common.makeTmpDirName(disk);

    /**********************************************************************/
    public UpgradeTestcase(String name) {
        super(name);
    }

    /**********************************************************************/
    protected void setUp() throws Exception {
        super.setUp();
        // copy the pickled 1.0.1 fragment file to a suitable directory
        File dir = new File(FRAG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dir2 = new File(FRAG_DIR2);
        if (!dir2.exists()) {
            dir2.mkdirs();
        }
        File tempDir = new File(TEMP_DIR);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File resource = new File(RESOURCE_DIR);
        File[] files = resource.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    if (pathname.getName().matches(".svn")) {
                        return false;
                    }
                    return true;
                }
            });
        if (files == null) {
            throw new IllegalStateException("Resource files not found in "
                                            + resource.getPath());
        }
        for (int i = 0; i < files.length; i++) {
            try {
                if (files[i].getName().contains("9801")) {
                    copyFile(files[i], new File(FRAG_DIR2 + "/"
                                                + files[i].getName()));
                } else if (files[i].getName().contains("8263da4c")
			   || files[i].getName().contains("d2d83d14")) {
                    copyFile(files[i], new File(FRAG_DIR + "/"
                                                + files[i].getName()));
                } else {
		    // One fragment has no footer, one has corrupted footer
		    copyFile(files[i], new File(TEMP_DIR + "/"
						 + files[i].getName()));
		}
            } catch (Exception e) {
                log.severe(e.getMessage());
            }
        }
    }

    /**********************************************************************/
    protected void tearDown() throws Exception {
	removeDir(new File(DATA_DIR));
        super.tearDown();
    }

    /**********************************************************************/
    private void removeDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        removeDir(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
            }
        }
        dir.delete();
    }

    /**********************************************************************/
    private void copyFile(File in, File out) throws Exception {
        FileChannel sourceChannel = new FileInputStream(in).getChannel();
        FileChannel destinationChannel = new FileOutputStream(out).getChannel();
        sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);
        sourceChannel.close();
        destinationChannel.close();
    }

    /**********************************************************************/
    public int getTempFilesCount() {
	String[] tempFiles = new File(workDirectory).list(new FilenameFilter() {
		public boolean accept(File dir, String name) {
		    return (name.startsWith("UpgradeableList_")) ? true : false;
		}
	    });
	return (tempFiles != null) ? tempFiles.length : 0;
    }
}
