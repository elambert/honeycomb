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

import java.util.ArrayList;
import java.util.ListIterator;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.cases.interfaces.*;

import com.sun.honeycomb.client.*;

/**
 * Validate that a streaming store and retrieve work for our "interesting"
 * file sizes.
 */
public class APIFileSize extends DeleteSuite {

    private CmdResult storeResult;
    private HoneycombTestClient htc = null;

    // property defaults
    public long startingFilesize = 0;
    public long endingFilesize = Long.MAX_VALUE;
    public boolean doRetrieve = true;
    public boolean doDelete = true;

    // If the store fails, we will fail the other tests due to dependencies
    private boolean setupOK = false;

    public APIFileSize() {
        super();
    }

    public String help() {
        return(
            "\tValidate that a streaming store and retrieve work\n" +
            "\tfor our interesting file sizes.\n" +
            "\tOptional -ctx args:\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes; it can be a list)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_STARTINGFILESIZE +
                "=n (n is size to start with in list of all filesizes)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_ENDINGFILESIZE +
                "=n (n is size to end with in list of all filesizes)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_RETRIEVE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_DELETE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_SEED +
                "=n (use n as the seed)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_RMI +
                "=yes (will do additional validation)\n"
            );
    }

    /**
     * Store a file.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        TestCase skip = createTestCase("testAPIFileSizes-norun");
        skip.addTag(Tag.NORUN, "test takes too long to be run accidentally");
        if (skip.excludeCase()) {
            Log.INFO("Aborting test case since we don't want to iterate " +
                "through all the file size cases");
            return;
        }

        // Define the sizes we care about per the test plan
        setFilesizeList(HCFileSizeCases.getSizeList());

        String s = getProperty(HoneycombTestConstants.PROPERTY_STARTINGFILESIZE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_STARTINGFILESIZE +
                " was specified. Starting from " + s);
            startingFilesize = Long.parseLong(s);
        } else {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_STARTINGFILESIZE +
                " was not specified. Allowing sizes from " + startingFilesize);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_ENDINGFILESIZE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_ENDINGFILESIZE +
                " was specified. Ending at " + s);
            endingFilesize = Long.parseLong(s);
        } else {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_ENDINGFILESIZE +
                " was not specified. Allowing sizes up to " + endingFilesize);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_RETRIEVE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_RETRIEVE +
                " was specified. Skipping retrieve");
            doRetrieve = false;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_DELETE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_DELETE +
                " was specified. Skipping delete");
            doDelete = false;
        }

        if (testBed.rmiEnabled()) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_RMI +
                " was specified. Will validate deleted fragments on cluster." +
                " RMI servers must be started for this to work");
        } else {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_RMI +
                " does not have value yes. " +
                "Will not validate deleted fragments on cluster");
        }
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    public void testAPIFileSizes() throws Throwable {
        // XXX we don't want to run this in the normal test runs 
        // but we don't want to have it print skipped results for each.
        TestCase skip = createTestCase("testAPIFileSizes-norun");
        skip.addTag(Tag.NORUN, "test takes too long to be run accidentally");
        if (skip.excludeCase()) {
            Log.INFO("Aborting test case since we don't want to iterate " +
                "through all the file size cases");
            return;
        }
        skip.testPassed("This is just a place holder result");

        ArrayList fileSizes = getFilesizeList();
        Log.INFO("Using sizes " + fileSizes);

        ListIterator i = fileSizes.listIterator();
        while (i.hasNext()) {
            long size = ((Long)i.next()).longValue();

            // check to see if we should skip this size due to the specified
            // startingFilesize
            if (size < startingFilesize) {
                Log.DEBUG("skipping size " + size + " because we are using " +
                    HoneycombTestConstants.PROPERTY_STARTINGFILESIZE + "=" +
                    startingFilesize);
                continue;
            }

            // check to see if we should skip this size due to the specified
            // endingFilesize
            if (size > endingFilesize) {
                Log.DEBUG("skipping size " + size + " because we are using " +
                    HoneycombTestConstants.PROPERTY_ENDINGFILESIZE + "=" +
                    endingFilesize);
                continue;
            }

            setupOK = false;
            TestCase self = createTestCase("StoreObject",
                "filesize=" + size);
            self.addTag(Tag.POSITIVE);
            self.addTag(HoneycombTag.DATAOPNOFAULT);
            self.addTag(HoneycombTag.STOREDATA);
            self.addTag(HoneycombTag.JAVA_API);
            // XXX These are sort of regression tests but there are so many
            // cases that it would be annoying to to have them all be run.
            // self.addTag(Tag.SMOKE);
            // self.addTag(Tag.REGRESSION);

            if (size < HoneycombTestConstants.MAX_QUICK_FILESIZE) {
                self.addTag(Tag.QUICK);
            } else {
                self.addTag(Tag.HOURLONG, "big file!");
            }

            if (self.excludeCase()) {
                // call retrieve to note the skipped result
                // XtestRetrieveAfterStore(size);
                continue;
            }

            try { 
                Log.DEBUG("creating a HCTestReadableByteChannel, size=" +
                    size);
                HCTestReadableByteChannel hctrbc =
                    new HCTestReadableByteChannel(size, seed, self);

                htc = new HoneycombTestClient(testBed.dataVIP);

                Log.DEBUG("calling store with our stream " + hctrbc);
                storeResult = htc.storeObject(hctrbc, size, null);

                String retHash = 
                    HCUtil.convertHashBytesToString(
                    storeResult.sr.getDataDigest());

                Log.INFO("Stored file of size " + size +
                    " as oid " + storeResult.sr +
                    " (data oid " + storeResult.dataoid + ")" +
                    "; hash returned=" + retHash +
                    "; hash computed=" + storeResult.datasha1);

                // Compare what the channel calculated to what the api returned
                if (!(storeResult.datasha1).equals(retHash)) {
                    throw new HoneycombTestException("Hash from api " +
                        retHash +
                        " doesn't match hash the channel computed " +
                        storeResult.datasha1);
                }

                // successful result will only be posted if we didn't throw
                self.testPassed("Stored file via stream of size " + size +
                    " succeeded; hash matched.");
                setupOK = true;
            } catch (Throwable t) {
                self.testFailed("Exception during store: " + t.getMessage());
            }

            if (doRetrieve) {
                // try retrieve  -- we'll note skipped result if above failed
                XtestRetrieveAfterStore(size);
            }

            if (doDelete) {
                XtestDeleteAfterStore(size);
            }
        }
    }


    /**
     * Retrieve a file.
     */
    public void XtestRetrieveAfterStore(long size) throws Throwable {
        CmdResult cr;

        TestCase self = createTestCase(
            "RetrieveObject",
            "filesize=" + size);
        if (!setupOK) addTag(Tag.MISSINGDEP, "failed some dependency");

        self.addTag(Tag.POSITIVE);
        self.addTag(HoneycombTag.DATAOPNOFAULT);
        self.addTag(HoneycombTag.RETRIEVEDATA);
        self.addTag(HoneycombTag.JAVA_API);

        if (size < HoneycombTestConstants.MAX_QUICK_FILESIZE) {
            self.addTag(Tag.QUICK);
            // self.addTag(Tag.SMOKE);
            // self.addTag(Tag.REGRESSION);
        } else {
            self.addTag(Tag.HOURLONG, "big file!");
        }

        if (self.excludeCase()) return;

        try {
            HCTestWritableByteChannel hctwbc =
                new HCTestWritableByteChannel(size, self);
            Log.INFO("calling retrieve with our stream " + hctwbc);
            cr = htc.retrieveObject(storeResult.mdoid, hctwbc);
            Log.INFO("Retrieved oid " + storeResult.mdoid + " with hash=" +
                cr.datasha1);
        } catch (HoneycombTestException hte) {
            self.testFailed("Retrieve failed [oid " + storeResult.mdoid +
                "]: " + hte.getMessage());
            return;
        }

        try {
            // This relies on the datahash being set in the two args,
            // which it should be.
            storeResult.filename = HoneycombTestConstants.DATA_FROM_STREAM;
            cr.filename = HoneycombTestConstants.DATA_FROM_STREAM;
            verifyFilesMatch(storeResult, cr);
            self.testPassed("Retrieved file matched stored file");
        } catch (HoneycombTestException hte) {
            self.testFailed("verifyFilesMatch failed: " + hte.getMessage());
        } 
    }

    /**
     * Delete a file.
     */
    public void XtestDeleteAfterStore(long size) throws Throwable {
        CmdResult cr;

        TestCase self = createTestCase(
            "DeleteObject",
            "filesize=" + size + ",numrefs=1");
        if (!setupOK) addTag(Tag.MISSINGDEP, "failed some dependency");

        self.addTag(Tag.POSITIVE);
        self.addTag(HoneycombTag.DATAOPNOFAULT);
        self.addTag(HoneycombTag.DELETEDATA);
        self.addTag(HoneycombTag.JAVA_API);

        if (size < HoneycombTestConstants.MAX_QUICK_FILESIZE) {
            self.addTag(Tag.QUICK);
            // self.addTag(Tag.SMOKE);
            // self.addTag(Tag.REGRESSION);
        } else {
            self.addTag(Tag.HOURLONG, "big file!");
        }

        if (self.excludeCase()) return;

        try {
            Log.INFO("calling delete with " + storeResult.mdoid);
            cr = htc.delete(storeResult.mdoid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Delete failed [oid " + storeResult.mdoid +
                "]: " + hte.getMessage());
            return;
        }

        if (testBed.rmiEnabled()) {
            // Verify files on-disk are deleted
            if (!onDiskFragsLookDeleted(storeResult.mdoid) ||
                !onDiskFragsLookDeleted(storeResult.dataoid)) {
                self.testFailed("frags don't look deleted as expected on disk");
                return;
            } else {
                Log.INFO("files look deleted on disk as expected for oids " +
                    storeResult.mdoid + " and " + storeResult.dataoid);
            }
        }

        // Verify delete worked by trying a retrieve, which should fail
        try {
            HCTestWritableByteChannel hctwbc =
                new HCTestWritableByteChannel(size, self);
            Log.INFO("calling retrieve with our stream " + hctwbc);
            cr = htc.retrieveObject(storeResult.mdoid, hctwbc);
        } catch (HoneycombTestException hte) {
            // XXX validate exception
            self.testPassed("retrieve after delete failed as expected: " +
                hte.getMessage());
        } 
    }
}
