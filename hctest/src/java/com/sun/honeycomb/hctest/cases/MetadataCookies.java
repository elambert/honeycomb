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
import java.util.HashMap;
import java.util.HashSet;

/**
 * Validate that queries can be done using cookies.  Note that now cookies
 * are transparent.  This test validates that you can pass in a small
 * max results and still get all results (through transparent round trips).
 */
public class MetadataCookies extends HoneycombLocalSuite {

    private static final int TOTAL_ADD_MD = 15;

    private CmdResult storeResult;
    private CmdResult addMetadataResult;
    private String filenameRetrieve;
    private boolean setupOK = false;
    private HashSet oids = null;

    private void addTags(TestCase tc) {
        if (tc == null) {
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);
        } else {
            tc.addTag(Tag.REGRESSION);
            tc.addTag(Tag.POSITIVE);
            tc.addTag(Tag.QUICK);
            tc.addTag(Tag.SMOKE);
            tc.addTag(HoneycombTag.QUERY);
            tc.addTag(HoneycombTag.JAVA_API);
            tc.addTag(HoneycombTag.EMULATOR);
        }
    }

    public MetadataCookies() {
        super();
    }

    public String help() {
        return(
            "\tTest queries with more than maxResults results.\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n"
            );
    }

    /**
     * Store a file and add some metadata so that we can later query for it
     * using cookies.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);

        doStoreTestNow();
    }


    private void doStoreTestNow() throws HoneycombTestException {
        TestCase self = createTestCase("setupMetadataAdd", "size=" +
                                       getFilesize());

        addTags(self);
        if (self.excludeCase())
            return;

        Log.DEBUG("Attempting to store a file of size " + getFilesize());

        // This must happen first so it is done in setup().
        // If the store fails, we abort the test case.
        storeResult = store(getFilesize());

        Log.INFO("Stored file of size " + getFilesize() +
            " as oid " + storeResult.mdoid);

        // Now, we add MD using the oid above as a unique value
        HashMap hm = new HashMap();
        hm.put(HoneycombTestConstants.MD_VALID_FIELD, storeResult.mdoid);

        oids = new HashSet();
        for (int i = 0; i < TOTAL_ADD_MD; i++) {
            addMetadataResult = addMetadata(storeResult.mdoid, hm);
            Log.INFO("add metadata returned " + addMetadataResult.mdoid);
            oids.add(addMetadataResult.mdoid);
        }

        // We will throw if there are errors, so mark this test
        // as passed if we have not thrown.
        self.testPassed("Stored files and added metadata in setup");
        setupOK=true;
    }


    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Do a query that uses cookies to validate that we can get each of our
     * expected results using a query that uses cookies.
     */
    public boolean testQueryWithCookies() throws Throwable {
        addTags(null);
        if (!setupOK)
            addTag(Tag.MISSINGDEP, 
                   "Couldn't store metadata objects; dependency failed");
        if (excludeCase())
            return false;

        try {

            String query = HoneycombTestConstants.MD_VALID_FIELD + "='" + 
                storeResult.mdoid + "'";
            Log.INFO("Running query " + query);

            addBug("6187944", "cookies in queries no longer seem to work");

            for (int numPerFetch=1; numPerFetch<TOTAL_ADD_MD+2; numPerFetch++) {
                int numTotalResults = 0;
                HashSet gotten = new HashSet();

                Log.INFO("resultsPerFetch = " + numPerFetch);
                CmdResult cr = query(query, numPerFetch);
                QueryResultSet qrs = (QueryResultSet) cr.rs;
                while (qrs.next()) {
                    ObjectIdentifier result = qrs.getObjectIdentifier();
                    Log.DEBUG("Found result " + result);
                    if (!oids.contains(result.toString())) {
                        Log.ERROR("oid " + result.toString() + 
                                  ", should not be " +
                                  "returned by this query");
                        return false;
                    }
                    gotten.add(result.toString());
                    numTotalResults++;
                    if (numTotalResults > TOTAL_ADD_MD) {
                        Log.ERROR("Too many results returned");
                        return false;
                    }
                }

                // Did we get the right number of results
                if (numTotalResults != TOTAL_ADD_MD) {
                    Log.ERROR("Query did not get exactly " + TOTAL_ADD_MD +
                        " objects that we stored.  Found " + numTotalResults);
                    return (false);
                }
                if (gotten.size() != TOTAL_ADD_MD) {
                    Log.ERROR("Query got duplicate results, total unique " +
                              gotten.size() + " expected " + TOTAL_ADD_MD);
                    return false;
                }

                // XXX Verify all of the results present (set compare)
            }
        } catch (HoneycombTestException hte) {
            Log.ERROR("Query failed: " + hte.getMessage());
            return (false);
        }
        return (true);
    }
}
