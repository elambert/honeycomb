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

import java.util.ArrayList;

/**
 * Validate that the simplest store and retrieve work.
 */
public class StoreRetrieveSimple extends HoneycombLocalSuite {

    private CmdResult storeResult;

    // If the store fails, we will fail the other tests due to dependencies
    private boolean setupOK = false;

    public StoreRetrieveSimple() {
        super();
    }

    public String help() {
        return(
            "\tValidate that the simplest store and retrieve work\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n"
            );
    }

    /**
     * Store a file.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);

        doStoreTestNow();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    private void doStoreTestNow() throws HoneycombTestException {
        TestCase self = createTestCase("setupStore",
            "filesize=" + getFilesize());
        self.addTag(Tag.SMOKE);
        self.addTag(Tag.REGRESSION);
        self.addTag(Tag.POSITIVE);
        self.addTag(Tag.SMOKE);
        self.addTag(HoneycombTag.STOREDATA);
        self.addTag(HoneycombTag.JAVA_API);
        self.addTag(HoneycombTag.EMULATOR);

        if (self.excludeCase()) return;

        storeResult = store(getFilesize());

        // successful result will only be posted if we didn't throw
        self.testPassed("Stored file of size " + getFilesize() +
                        " as oid " + storeResult.mdoid +
                        " @ " + (storeResult.filesize * 1000 / storeResult.time) + " bytes/sec");
        setupOK = true;
    }

    /**
     * Validate basic store return values.
     */
    public boolean testAStoreReturnedSystemRecord() {
        if (!setupOK) addTag(Tag.MISSINGDEP, "failed some dependency");
        addTag(Tag.SMOKE);
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);

        addBug("6187594", "client API isn't returning data hash; " +
            "can't query on data hash (md hash not supported by design)");

        addBug("6216444", "On successful store or storeMetadata, " +
            "SystemRecord.getDigestAlgorithm() returns null");

        if (excludeCase()) return false;

        String errors = HoneycombTestClient.validateSystemRecord(storeResult.sr,
            getFilesize());
        if (!errors.equals("")) {
            Log.ERROR("Test failed for the following reasons: " + errors);
            return (false);
        }

        Log.INFO("Successfully verified the return result from store");
        return (true);
    }

    /**
     * Retrieve a file.
     */    
    public boolean testBRetrieveAfterStore() {
        if (!setupOK) addTag(Tag.MISSINGDEP, "failed some dependency");
        addTag(Tag.SMOKE);
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.RETRIEVEDATA);
        addTag(HoneycombTag.JAVA_API);
        addTag(HoneycombTag.EMULATOR);

        if (excludeCase()) return false;

        CmdResult cr;
        try {
            cr = retrieve(storeResult.mdoid);
            Log.INFO("Retrieved oid " + storeResult.mdoid +
                     " @ " + (cr.filesize * 1000 / cr.time) + " bytes/sec");
        } catch (HoneycombTestException hte) {
            Log.ERROR("Retrieve failed [oid " + storeResult.mdoid + "]: " + 
	    						hte.getMessage());
            Log.DEBUG(Log.stackTrace(hte));
            return (false);
        }

        if (TestBed.doHash) {
            try {
                verifyFilesMatch(storeResult, cr);
                Log.INFO("Retrieved file matched stored file");
            } catch (HoneycombTestException hte) {
                Log.ERROR("verifyFilesMatch failed: " + hte.getMessage());
                return (false);
            } 
        } else
            Log.INFO("skipping hash check");

        return (true);
    }
}
