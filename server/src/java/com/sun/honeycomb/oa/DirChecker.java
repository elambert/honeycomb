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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.common.InternalException;

import java.io.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * This class knows about Honeycomb's directory structure but nothing else.
 * Directory operations are run in a separate thread and must complete
 * before the timeout expires; if not, the thread is cancelled and the
 * operation is considered to have failed.
 */
public class DirChecker {
    private static final long RTIMEOUT = 300000;  // 5mn
    private static final long WTIMEOUT = 1800000; // 30min

    private static final int OP_CHECK_DATA = 0;
    private static final int OP_MAKE_DATA = 1;
    static final int NUM_DIRS = 99;

    private static final String CONFIG_DIR = "config";

    private static final Logger log =
        Logger.getLogger(DirChecker.class.getName());

    public static boolean checkAndRepair(String root) {
	if (!checkDataDirs(root))
	    return makeDataDirs(root);
	return true;
    }

    private static boolean checkDataDirs(String root) {
        return new DirChecker().runThread(OP_CHECK_DATA, root, RTIMEOUT);
    }
    private static boolean makeDataDirs(String root) {
        return new DirChecker().runThread(OP_MAKE_DATA, root, WTIMEOUT);
    }

    private static boolean checkData(String root) {
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
            throw new InternalException("Root directory \"" + root + 
                                        "\" doesn't exist");
        // check tmp-close
        File tmpclose = new File(root + Common.dirSep + Common.closeDir);
        if (!tmpclose.exists()) {
            log.info("Tmp dir missing.");
            return false;
        }
            
        // check all layout dirs structure to recover from file system failure
        int start = 0;
        for (int i = start; i <= NUM_DIRS; i++) {
            String ii = Integer.toString(i);
            // prepend a "0" to single digits
            if (ii.length() == 1) {
                ii = "0" + i;
            }
            String dirpath = root + "/" + ii;
            File dir = new File(dirpath);
            if (!dir.exists()) {
                log.info("Dir \"" + dirpath + "\" missing.");
                return false;
            }
            
            for (int j = start; j <= NUM_DIRS; j++) {
                String jj = Integer.toString(j);
                // prepend a "0" to single digits
                if (jj.length() == 1) {
                    jj = "0" + jj;
                }
                String subdirpath = dirpath + "/" + jj;
                File subdir = new File(subdirpath);
                if (!subdir.exists()) {
                    log.info("Dir \"" + subdirpath + "\" missing.");
                    return false;
                }
            }
        }    
        return true;
    }

    private static boolean makeData(String root) {
        // Make the tmp directories OA needs
        File tmpclose =
            new File(root + Common.dirSep + Common.closeDir);
        if (!tmpclose.exists() && !tmpclose.mkdir())
            log.severe("Couldn't make " + Common.closeDir + " in " + root);
 
        for (int i = 0; i <= NUM_DIRS; i++) {
            String ii = Integer.toString(i);
            // prepend a "0" to single digits
            if (ii.length() == 1) {
                ii = "0" + ii;
            }
            String dirpath = root + "/" + ii;
            File dir = new File(dirpath);
            // create if it does not exist
            if (!dir.exists() && !dir.mkdir())
                log.severe("Couldn't make directory \"" + dirpath + "\"");

            for (int j=0; j <= NUM_DIRS; j++) {
                String jj = Integer.toString(j);
                // prepend a "0" to single hex digits
                if (jj.length() == 1) {
                    jj = "0" + jj;
                }
                String subdirpath = dirpath + "/" + jj;
                File subdir = new File(subdirpath);
                // create if it does not exist
                if (!subdir.exists() && !subdir.mkdir())
                     log.severe("Couldn't make directory \"" + subdirpath +
                                 "\"");
            }
        }

        return true;
    }

    private volatile boolean success = false;

    private boolean runThread(int op, String root, long timeout) {
        Thread t = new Thread(new Monitor(op, root, this));
        t.start();
        try {
            t.join(timeout);

            if (t.isAlive()) {
                String sOp = null;
                switch (op) {
                case OP_CHECK_DATA: sOp = "checkDataDirs(\""; break;
                case OP_MAKE_DATA: sOp = "makeDataDirs(\""; break;
                }
                log.warning("Operation " + sOp + root +
                            "\") taking too long: possible disk problem");
            }
        }
        catch(InterruptedException ign){}

        return success;
    }

    private class Monitor implements Runnable {
        private DirChecker parent;
        private String root;
        private int op;

        public Monitor(int op, String root, DirChecker parent) {
            this.parent = parent;
            this.root = root;
            this.op = op;
        }

        public void run() {
	    switch(op) {
	    case OP_CHECK_DATA:
		if (checkData(root))
		    parent.success = true;
		break;
	    case OP_MAKE_DATA:
		if (makeData(root))
		    parent.success = true;
		break;
	    }
        }
    }

}
