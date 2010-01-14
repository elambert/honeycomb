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

/**
 * Validate that a streaming store and retrieve work. This can be used
 * to test files that are larger than the max disk space.
 */
public class StoreRetrieveStream extends HoneycombLocalSuite {

    private CmdResult storeResult;
    private HoneycombTestClient htc = null;
    private String cmdLineOid = null;
    private String cmdLineHash = null;
    private long retrieveFilesize = HoneycombTestConstants.INVALID_FILESIZE;

    // If the store fails, we will fail the other tests due to dependencies
    private boolean setupOK = false;

    public StoreRetrieveStream() {
        super();
    }

    public String help() {
        return(
            "\tValidate that streaming store and retrieve work\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_OID +
                "=stored_oid (retrieve the oid)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_HASH +
                "=hash_of_oid (hash of the data obj oid)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_SEED +
                "=n (use n as the seed)\n"
            );
    }

    /**
     * Store a file.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        htc = new HoneycombTestClient(testBed.dataVIP);

        String s = getProperty(HoneycombTestConstants.PROPERTY_OID);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_OID +
                " was specified. Will do retrieve and skip store");
            cmdLineOid = s;
        } else {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_OID +
                " was not specified.  Will do store and retrieve.");
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_HASH);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_HASH +
                " was specified. Will validate hash against this value");
            cmdLineHash = s;
        }

        if (cmdLineOid == null) {
            setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
            doStoreTestNow();
            retrieveFilesize = getFilesize();
        } else {
            Log.INFO("Skipping store, doing retrieve only");
            setFilesize(HoneycombTestConstants.MAX_FILESIZE);
            storeResult = new CmdResult();
            storeResult.mdoid = cmdLineOid;
            storeResult.datasha1 = cmdLineHash;
            setupOK = true;
            // don't know the filesize if just given oid
            retrieveFilesize = HoneycombTestConstants.INVALID_FILESIZE;
        }
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    private void doStoreTestNow() throws Throwable {
        // XXX add option to pass an oid that will be streamed back?

        TestCase self = createTestCase("setupStoreStream",
                        "filesize=" + getFilesize());
        self.addTag(Tag.REGRESSION);
        self.addTag(Tag.POSITIVE);
        self.addTag(Tag.QUICK);
        self.addTag(Tag.SMOKE);
        self.addTag(HoneycombTag.STOREDATA);

        if (self.excludeCase()) return;

        Log.INFO("creating a HCTestReadableByteChannel, size=" +
            getFilesize());
        HCTestReadableByteChannel hctrbc =
            new HCTestReadableByteChannel(getFilesize(), seed, self);

        Log.INFO("calling store with our stream " + hctrbc);
        storeResult = htc.storeObject(hctrbc, getFilesize(), null);

        storeResult.mdoid = storeResult.sr.getObjectIdentifier().toString();

        String retHash =  HCUtil.convertHashBytesToString(
                                              storeResult.sr.getDataDigest());

        Log.INFO("Stored file of size " + getFilesize() +
            " as oid " + storeResult.sr +
            "; hash returned=" + retHash +
            "; hash computed=" + storeResult.datasha1);

        // Compare what the channel calculated to what the api returned
        if (!(storeResult.datasha1).equals(retHash)) {
            throw new HoneycombTestException("Hash from api " +
                retHash +
                " doesn't match hash the channel computed " + storeResult.datasha1);
        }

        // successful result will only be posted if we didn't throw
        self.testPassed("Stored file via stream of size " + getFilesize() +
            " succeeded; hash matched.");
        setupOK = true;
    }

 
    /**
     * Retrieve a file.
     */
    public void testRetrieveAfterStore() throws Throwable {
        CmdResult cr;

        TestCase self = createTestCase(
            "StoreRetrieveStream::testRetrieveAfterStore",
            "filesize=" + retrieveFilesize);
        if (!setupOK) addTag(Tag.MISSINGDEP, "failed some dependency");

        // XXX account for filesize in tags
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
        addTag(Tag.POSITIVE);
        addTag(Tag.VARIABLELENGTH);
        addTag(HoneycombTag.RETRIEVEDATA);
       
        if (excludeCase()) return;

        try {
            HCTestWritableByteChannel hctwbc =
                new HCTestWritableByteChannel(getFilesize(), self);
            cr = htc.retrieveObject(storeResult.mdoid, hctwbc);
            Log.INFO("Retrieved oid " + storeResult.mdoid + " with hash=" +
                cr.datasha1);
        } catch (HoneycombTestException hte) {
            self.testFailed("Retrieve failed [oid " + storeResult.mdoid +
                "]: " + hte.getMessage());
            return;
        }

        if (cmdLineOid != null && cmdLineHash == null) {
            Log.INFO("Skippping hash validation because we weren't passed " +
                "the hash on the cmd line for the oid we were told to " +
                "retrieve");
            self.testPassed("Retrieve succeeded, no hash validation done");
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
}
