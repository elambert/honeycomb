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



package com.sun.honeycomb.hctest;

import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;

import java.io.File;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;

/**
 *  Helper functions for test cases that address 
 *  Honeycomb clusters or other general Honeycomb needs. This class is
 *  extended by util.HoneycombAPISuite and rmi.HoneycombRMISuite.
 */
public class HoneycombSuite extends Suite {

    // access to singleton for HoneycombSuite and its subclasses
    protected TestBed testBed;

    // Variables for the common values
    private long filesize_ = HoneycombTestConstants.INVALID_FILESIZE;
    private ArrayList filesizeList_ = null;
    public boolean reuseFiles = false;
    public long seed = HoneycombTestConstants.INVALID_SEED;

    public HoneycombSuite() {
        super();
        testBed = TestBed.getInstance(); // access to singleton
    }

    public void setUp() throws Throwable {
        Log.DEBUG("com.sun.honeycomb.test.HoneycombSuite::setUp() called");
        super.setUp();

        // moving this past Suite::setUp so required properties get parsed.
        testBed.logStart(); // record HC-specific info when starting Suite

        //
        // initialize the common properties if they are set
        //
        //   filesize and filesizeList_ properties
        
        String s = getProperty(HoneycombTestConstants.PROPERTY_FILESIZE);
        if (s != null) {
            // this can be a comma separated list of sizes or a single size
            String[] elems = s.split(",");
            filesize_ = HCUtil.parseSize(elems[0]);
            filesizeList_ = new ArrayList();
            for (int i = 0; i < elems.length; i++) {
                filesizeList_.add(new Long(HCUtil.parseSize(elems[i])));
            }
            if (filesizeList_.size() == 1)
                Log.INFO("Passed in filesize " + filesize_);
            else
                Log.INFO("Passed in filesizes " + filesizeList_);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_REUSE_FILES);
        if (s != null) {
            reuseFiles = (Boolean.valueOf(s)).booleanValue();
            Log.INFO("Re-use files has value " + reuseFiles);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_SEED);
        if (s != null) {
            seed = Long.parseLong(s);
            Log.INFO("Using passed in seed " + seed);
        }
    }

    public void tearDown() throws Throwable {
        testBed.logEnd(); // record HC-specific info when finishing Suite
    }

    public long getFilesize() {
        return filesize_;
    }

    public void setFilesize(long size) {
        if (filesize_ == HoneycombTestConstants.INVALID_FILESIZE) {
            filesize_ = size;
            Log.DEBUG("Using default file size for this Suite: " + filesize_);
        }
    }

    public void setFilesizeList(ArrayList sizeList) {
        if (filesizeList_ == null) {
            filesizeList_ = sizeList;
            Log.DEBUG("Using default file sizes for this Suite: " +
                filesizeList_);
        }
    }

    public ArrayList getFilesizeList() {
        return filesizeList_;
    }


    /**
    ***  Checking of file data match by sha1sum. 
    ***  See HoneycombLocalSuite for a file-based version.
    **/
    public void verifyFilesMatch(CmdResult f1, CmdResult f2)
                                                throws HoneycombTestException {
        if (f1.datasha1 == null  ||  f2.datasha1 == null) {
            throw new HoneycombTestException("CmdResult(s) missing datasha1");
        }
        if (!f1.datasha1.equals(f2.datasha1)) {
            throw new HoneycombTestException(f1.filename + " has sha1sum " + 
                                            f1.datasha1 + "; " + 
                                            f2.filename  + " has sha1sum " + 
                                            f2.datasha1);
        }
    }

    /**
    ***  See if oid is in Honeycomb ResultSet.
    **/
    public boolean oidInQueryResults(String oid, ResultSet rs)
        throws HoneycombTestException {
        if (rs == null) {
            Log.DEBUG("Query results were null");
            return false;
        }
        // note: as of 1/05, ResultSets are iterate-only-once;
        // add iterator reset here when available
        // note: ResultSet is not quite like an SQL result set
        try {
            while (rs.next()) {
                // XXX PORT  We assume a QueryResultSet else we'll throw
                String qoid =
                    ((QueryResultSet)rs).getObjectIdentifier().toString();
                if (oid.equals(qoid)) {
                    Log.DEBUG("OID was returned in query results");
                    return true;
                }
            }
        } catch (Throwable t) {
            throw new HoneycombTestException("rs.next() failed: " + t);
        }

        Log.DEBUG("OID was not returned in query results");
        return (false);
    }

    /**
     *  Convenience sleep for main test thread.
     */
    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            Log.INFO("sleep interrupted: " + e);
        }
    }
}
