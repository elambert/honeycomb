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
import com.sun.honeycomb.hctest.sg.*;

import com.sun.honeycomb.client.*;

import java.util.ArrayList;

/**
 * Uses the old StoreRetrieve test to validate that the basic
 * cases tested by that test still pass.
 */
public class WrappedStoreRetrieve extends HoneycombLocalSuite {

    private String uploadFilename = null;
    private int numObjects = 5;
    private boolean deleteSucceeded = false;
    private boolean setupOK=false;
    private long filesize; 
    private boolean runAgainstEmulator = false;
    public boolean doRetrieve = true;
    public boolean doQuery = true;
    public boolean doDelete = true;

    private void addTags(com.sun.honeycomb.test.TestCase tc) {
        if (tc == null) {
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
        } else {
            tc.addTag(Tag.REGRESSION);
            tc.addTag(Tag.POSITIVE);
            tc.addTag(Tag.QUICK);
            tc.addTag(Tag.SMOKE);
        }
    }

    public WrappedStoreRetrieve() {
        super();
        String emulator = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_CLUSTER);
        if (emulator != null) {
            runAgainstEmulator = true;
        }
    }

    public String help() {
        return(
            "\tUses the old StoreRetrieve test to validate that the basic\n" +
            "\tcases tested by that test still pass.\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_RETRIEVE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_QUERY + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_DELETE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                "=n (n is the number of objects to store)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n"
            );
    }

    /**
     * Do a handful of stores.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();

        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_MEDIUMSMALL);
        filesize = getFilesize();

        String s = getProperty(HoneycombTestConstants.PROPERTY_NO_RETRIEVE);
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

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_QUERY);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_QUERY +
                " was specified. Skipping query");
            doQuery = false;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
        if (s != null) {
            Log.INFO("Property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                " was specified. Using " + s + " " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
            numObjects = Integer.parseInt(s);
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                " of " + numObjects);
        }

        if (filesize < TestStoreRetrieve.minBytesWithEMD) {
            Log.WARN("Filesize " + filesize +
                " is too small to store MD, which takes more space");
            Log.WARN("Filesize must be at least " +
                TestStoreRetrieve.minBytesWithEMD);

            filesize = HoneycombTestConstants.DEFAULT_FILESIZE_MEDIUMSMALL;
            Log.WARN("Using default size of " + filesize + " instead");
        }

        doStoreTestNow();
   }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    private void doStoreTestNow() throws Throwable {
        com.sun.honeycomb.test.TestCase self = createTestCase(
            "setupWrappedStoreRetrieve", "size=" +getFilesize());
        addTags(self);
        // To allow the delete test to run later as part of the delete suite,
        // add this tag now.
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREDATA);
        addTag(HoneycombTag.JAVA_API);
        if (self.excludeCase()) return;

        // This must happen first so it is done in setup().
        // If the store fails, we abort the test case.
        String tmpFilename = FileUtil.getTmpFilename();
        tmpFiles.add(tmpFilename);
        TestStoreRetrieve StoreRetrieve = new TestStoreRetrieve();
        String[] args = null;
        if (runAgainstEmulator) {
            args = new String[12];
        } else {
            args = new String[11];
        }
        args[0] = "-S";
        args[1] = "-s";
        args[2] = testBed.dataVIP;
        args[3] = "-i";
        args[4] = "" + numObjects;
        args[5] = "-b";
        args[6] = "" + filesize;
        args[7] = "-k";
        args[8] = tmpFilename;
        args[9] = "-O";
        args[10] = "-T";
        if (runAgainstEmulator) {
            args[11] = "-e";
        }

        try {
            StoreRetrieve.regressionMode = true;
            StoreRetrieve.main(args);
        } catch (HoneycombTestException hte) {
            // Look at the exception message to see if it passed or failed
            String rc = hte.getMessage();
            Log.DEBUG("Result is " + rc);
            if (!rc.equals(TestRun.PASS)) {
                throw new HoneycombTestException("Test failed...see stdout");
            }
        }

        // The store succeeded...let's get some info
        Log.DEBUG("Filename used for upload was " +
            StoreRetrieve.uploadFilename);
        uploadFilename = StoreRetrieve.uploadFilename;

        self.testPassed("stored " + numObjects + " objects");
        setupOK=true;
    }

    /**
     * Verify that the query results are as expected after the stores.
     */
    public boolean testAQueryAfterStore() throws Throwable {
        addTags(null);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
        // Avoid setting the Tag.MISSINGDEP flag twice else DB gets mad
        if (!setupOK) {
            addTag(Tag.MISSINGDEP, "failed some dependency");
        } else if (!doQuery) {
            addTag(Tag.MISSINGDEP, "cli cmd said to skip case");
        }
        if (excludeCase()) return false;

        TestStoreRetrieve StoreRetrieve = new TestStoreRetrieve();
        String[] args = null;
        if (runAgainstEmulator) {
            args = new String[7];
        } else {
            args = new String[6];
        }
        args[0] = "-Q";
        args[1] = "-s";
        args[2] = testBed.dataVIP;
        args[3] = "-f";
        args[4] = uploadFilename;
        args[5] = "-T";
        if (runAgainstEmulator) {        
            args[6] = "-e";
        }

        try {
            StoreRetrieve.reinitialize();
            StoreRetrieve.regressionMode = true;
            StoreRetrieve.main(args);
        } catch (HoneycombTestException hte) {
            // Look at the exception message to see if it passed or failed
            String rc = hte.getMessage();
            Log.DEBUG("Result is " + rc);
            if (!rc.equals(TestRun.PASS)) {
                Log.ERROR("Query test failed");
                return (false);
            }
        }

        return (true);
    }

    /**
     * Verify that the retrieve results are as expected after the stores.
     */
    public boolean testARetrieveAfterStore() throws Throwable {
        addTags(null);
        addTag(HoneycombTag.RETRIEVEDATA);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) {
            addTag(Tag.MISSINGDEP, "failed some dependency");
        } else if (!doRetrieve) {
            addTag(Tag.MISSINGDEP, "cli cmd said to skip case");
        }
        if (excludeCase()) return false;

        TestStoreRetrieve StoreRetrieve = new TestStoreRetrieve();
        String args[] = {
            "-R",
            "-s", testBed.dataVIP,
            "-i", "" + numObjects,
            "-f", uploadFilename,
            "-T" };

        try {
            StoreRetrieve.reinitialize();
            StoreRetrieve.regressionMode = true;
            StoreRetrieve.main(args);
        } catch (HoneycombTestException hte) {
            // Look at the exception message to see if it passed or failed
            String rc = hte.getMessage();
            Log.DEBUG("Result is " + rc);
            if (!rc.equals(TestRun.PASS)) {
                Log.ERROR("Retrieve test failed");
                return (false);
            }
        }

        return (true);
    }

    /**
     * Verify that delete works after the stores.
     */
    public boolean testBDeleteAfterStore() throws Throwable {
        addTags(null);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) {
            addTag(Tag.MISSINGDEP, "failed some dependency");
        } else if (!doDelete) {
            addTag(Tag.MISSINGDEP, "cli cmd said to skip case");
        }
        if (excludeCase()) return false;

        TestStoreRetrieve StoreRetrieve = new TestStoreRetrieve();
        String args[] = {
            "-D",
            "-s", testBed.dataVIP,
            "-i", "" + numObjects,
            "-f", uploadFilename,
            "-T" };

        addBug("6187592",
            "after deleting an object, a retrieve still works due to caching");
        addBug("6271940",
            "delete failed with \"Failed to delete - only deleted 0 " +
            "fragments sucessfully\"");

        try {
            StoreRetrieve.reinitialize();
            StoreRetrieve.regressionMode = true;
            StoreRetrieve.main(args);
        } catch (HoneycombTestException hte) {
            // Look at the exception message to see if it passed or failed
            String rc = hte.getMessage();
            Log.DEBUG("Result is " + rc);
            if (!rc.equals(TestRun.PASS)) {
                Log.ERROR("Delete test failed");
                return (false);
            }
        }

        deleteSucceeded = true;
        return (true);
    }

    /**
     * Validate that query after delete does not find our objects.
     * Delete mode already does this but do it again.
     */
    public boolean testCQueryAfterStoreAndDelete() throws Throwable {
        addTags(null);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
        if (!deleteSucceeded) {
            addTag(Tag.MISSINGDEP, "delete test failed");
        } else if (!setupOK) {
            addTag(Tag.MISSINGDEP, "failed some dependency");
        }
        if (excludeCase()) return false;

        TestStoreRetrieve StoreRetrieve = new TestStoreRetrieve();
        String args[] = {
            "-Q",
            "-s", testBed.dataVIP,
            "-f", uploadFilename,
            "-T" };

        try {
            StoreRetrieve.reinitialize();
            StoreRetrieve.regressionMode = true;
            StoreRetrieve.main(args);
        } catch (HoneycombTestException hte) {
            // Look at the exception message to see if it passed or failed
            String rc = hte.getMessage();
            Log.DEBUG("Result is " + rc);
            if (rc.equals(TestRun.PASS)) {
                Log.ERROR("Query after delete should have failed!");
                return (false);
            } else {
                // XXX verify failure reason?
                Log.INFO("The query test failed as expected after delete");
            }
        }

        return (true);
    }

    /**
     * Validate that retrieve after delete does not find our objects.
     * Delete mode already does this but do it again.
     */
    public boolean testCRetrieveAfterStoreAndDelete() throws Throwable {
        addTags(null);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.RETRIEVEDATA);
        addTag(HoneycombTag.JAVA_API);
        if (!deleteSucceeded) {
            addTag(Tag.MISSINGDEP, "delete test failed");
        } else if (!setupOK) {
            addTag(Tag.MISSINGDEP, "failed some dependency");
        }
        if (excludeCase()) return false;

        TestStoreRetrieve StoreRetrieve = new TestStoreRetrieve();
        String args[] = {
            "-R",
            "-s", testBed.dataVIP,
            "-i", "" + numObjects,
            "-f", uploadFilename,
            "-T" };

        addBug("6187592",
            "after deleting an object, a retrieve still works due to caching");

        try {
            StoreRetrieve.reinitialize();
            StoreRetrieve.regressionMode = true;
            StoreRetrieve.main(args);
        } catch (HoneycombTestException hte) {
            // Look at the exception message to see if it passed or failed
            String rc = hte.getMessage();
            Log.DEBUG("Result is " + rc);
            if (rc.equals(TestRun.PASS)) {
                Log.ERROR("Retrieve after delete should have failed!");
                return (false);
            } else {
                // XXX verify failure reason?
                Log.INFO("The retrieve test failed as expected after delete");
            }
        }

        return (true);
    }
}
