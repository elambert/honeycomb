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



package com.sun.honeycomb.hctest.cases;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.client.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.ListIterator;
import java.io.File;

/**
 * Basic test to validate that a file can be found and retrieved in a view.
 */
public class NFSRetrieve extends HoneycombLocalSuite {

    private CmdResult storeResult;
    private String filenameRetrieve;
    private long size;

    public NFSRetrieve() {
        super();
    }

    public String help() {
        return(
            "\tTest retrieving over NFS views works\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes; it can be a list)\n"
            );
    }

    /**
     * Mount the NFS share before running the tests.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Store an object with MD and verify that we can retrieve it from the view.
     * The stores and retrieves use this view:
     *  honeycomb.emd.view.sizeOid = fileorigsize, filecurrsize | %object_id%
     */
    public void testANFSRetrieve() throws HoneycombTestException{

        //
        // Moved out of setup; I don't know of an elegant way to
        // do this using the setup mechanism; needs to be
        // a test case. (we need 'createtestcase' before we can
        // add a NORUN tag.
        //
        // Do this in setup because if we fail we should abort
        // addBug("XXX", "Must be root to mount NFS");

        TestCase myself = createTestCase("NFSSetup");

        
        addTag(Tag.REGRESSION);
        addTag(Tag.POSITIVE);
        addTag(Tag.QUICK);
        addTag(Tag.NORUN,
               "Bug 6200155: sometimes files don't show up as quickly as expected under NFS");
        if (myself.excludeCase()) {
            return;
        }
        mountNFS();


        ArrayList sizeList;

        // If the user specified sizes on the command line use them
        if (getFilesizeList() != null) {
            sizeList = getFilesizeList();
        } else {
            sizeList =  new ArrayList();
            sizeList.add(
	    	new Long(HoneycombTestConstants.DEFAULT_FILESIZE_XSMALL));
            sizeList.add(
	    	new Long(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL));
            sizeList.add(
	    	new Long(HoneycombTestConstants.DEFAULT_FILESIZE_MEDIUMSMALL));
	    //            sizeList.add(
	    //	    	new Long(HoneycombTestConstants.DEFAULT_FILESIZE_MEDIUM));
            // sizeList.add(new Long(DEFAULT_FILESIZE_LARGE));
            // sizeList.add(new Long(DEFAULT_FILESIZE_XLARGE));
            // sizeList.add(new Long(DEFAULT_FILESIZE_XXLARGE));
        }

        // Loop through all sizes and store a file with that size
        // with metadata for a known view
        ListIterator i = sizeList.listIterator();
        HashMap hm = new HashMap();
        while (i.hasNext()) {
            size = ((Long) i.next()).longValue();

            TestCase self = createTestCase("NFSRetrieve", 
                                           HoneycombTestConstants.PROPERTY_FILESIZE + "=" + size);
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.NORUN,
                   "Bug 6200155: sometimes files don't show up as quickly as expected under NFS");
            if (self.excludeCase()) {
                continue;
            }

            hm.put(HoneycombTestConstants.MD_ATTR_SIZE1, size + "");
            hm.put(HoneycombTestConstants.MD_ATTR_SIZE2, size + "");

            addBug("6200155",
                   "sometimes files don't show up as quickly as expected under NFS");
	    
            try {
                storeResult = store(size, hm);
            } catch (Throwable t) {
                self.testFailed("Failed to store file of size " + size);
                continue;
            }

            // XXX test both copy down and in place read
            // filenameRetrieve = storeResult.filename + "-retrieve";
            // tmpFiles.add(filenameRetrieve);
            
            filenameRetrieve = nfsMountPoint() + File.separator +
				HoneycombTestConstants.NFS_VIEWPATH_SIZEOID + 
				File.separator + size + File.separator + 
                		size + File.separator + storeResult.mdoid;
            try {
                if (!doesFileExist(filenameRetrieve, 5)) { // give it 5 sec
                    self.testFailed("verifyFilesMatch failed: file not there");
                } else {
		    String sha1 = testBed.shell.sha1sum(filenameRetrieve);
		    if (storeResult.datasha1.equals(sha1)) {
                        self.testPassed("File read from NFS matches stored file");
		    } else {
                        self.testFailed("verifyFilesMatch failed: shas do not agree");
		    }
		}
            } catch (HoneycombTestException hte) {
                self.testFailed("verifyFilesMatch failed: " 
                                + hte.getMessage());
            }
        }
    }

    /**
     * Verify that umount works.
     */
    public boolean testBNFSRetrieveUmount() throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.POSITIVE);
        addTag(Tag.QUICK);
        addTag(Tag.NORUN,
               "Bug 6200155: sometimes files don't show up as quickly as expected under NFS");
        if (excludeCase()) return false;
        umountNFS();
        return (true);
    }
}
