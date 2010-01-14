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



// Overview
// --------
//
// This test program is used for validating metadata.

package com.sun.honeycomb.hctest.sg;

import java.io.*;
import java.lang.*;
import java.util.*;
import com.sun.honeycomb.common.*;
// import com.sun.honeycomb.protocol.client.*;
import com.sun.honeycomb.client.*;
import java.text.SimpleDateFormat;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;
// import java.util.logging.Logger;
// import java.util.logging.Level;

/**
 * Class to validate metadata on the system. This is an old test but it still is
 * useful.  Maybe it should be ported to use the new interfaces. There was a lot
 * of functionality that I meant to add to this test but never got the time to.
 */
public class TestMetadata extends TestCase {

    private static final int DEFAULTPORT = 8080;
    private static final long MAXFILESIZE = 1000000;

    // In the future, when we can dynamically update the schema,
    // use new fields and add them dynamically
    // XXX add new fields to the default schema?
    private static final String STRINGFIELD1 = "stringlarge";

    private static final String USAGE =
        "Usage: (Note that args and options must be space separated)\n" +
        "For changing logging verbosity:\n" +
        "  -q (quiet mode, no info or debugging msgs)\n" +
        "  -v (verbose mode, info and debugging msgs)\n" +
        "For connecting to the server:\n" +
        "  -s server\n" +
        "  -p port (defaults to " + DEFAULTPORT + ")\n" +
        "For exit and log behavior:\n" +
        "  -T (run in context of test suite)\n" +
        "Help:\n" +
        "  -h (prints usage text)";

    private static String invocationArgs = null;

    // For EMD
    private static Random rand = null;
    private static long totalFilesStored = 0;
    private static long totalTestRunFilesStored = 0;

    // For the client API
    private static HoneycombTestClient htc = null;
    private static String server = null;
    private static String[] servers = null;
    private static int port = DEFAULTPORT;

    // File and object manipulation
    private static String testFile = null;
    private static ObjectIdentifier storedFileOID = null;

    // For query mode
    private static int maxResults =
        HoneycombTestConstants.DEFAULT_MAX_RESULTS_CONSERVATIVE;

    /**
     * This routine uploads and retrieves unique files to a honeycomb system,
     * depending on the options specified on the command line.
     */
    public static void main(String args[])
        throws IOException, InterruptedException, HoneycombTestException {

        try {
            parseArgs(args);
        } catch (ArgException e) {
            printError("ERROR: " + e.getMessage());
            printError(USAGE);
            die();
        }

        testRunName = "MetadataTestSuite";
        testRunInit();
        printInfo(getString());

        testMetadata();
        done();
    }

    public static void testMetadata() throws HoneycombTestException {
        if (server == null || servers == null) {
            printError("ERROR: Must specify a server with -s");
            die();
        }

        // Initialize the client library
        // We used to load balance, but not any more, so use the first entry
        htc = new HoneycombTestClient(servers[0], port);

        // create a test file to store
        try {
            testFile = TestLibrary.createRandomFileForTest(MAXFILESIZE);
        } catch (IOException e) {
            printError("ERROR: Failed to create file " + e.getMessage());
            die();
        }

        printDebug("Done creating test file " + testFile);

        // Store the object
        storedFileOID = storeFileWithAPI(testFile);

        // Just for debugging
        printInfo("Store returned " + storedFileOID + " and we got MD " +
            getMetadata(storedFileOID));
        
        testMetadataStrings();
        /*
        testMetadataLongs();
        testMetadataBlobs();
        testMetadataDoubles();
        testMetadataCombine(); // strings and longs, etc
        testMetadataGeneral(); // many clauses, etc
        */

        File f = new File(testFile);
        if (!f.delete()) {
            printError("ERROR: failed to delete test file " + f);
        } else {
            printDebug("Deleted test file " + f);
        }
    }

    // Test cases that deal with string values
    public static void testMetadataStrings() {
        testMetadataStringsLengthOne();
        testMetadataStringsNumericLengthOne();
        // testMetadataStringsLengthTwo();

        // longer strings
        // longer numeric
        //
        // multiple fields set (two strings, etc)
    }

    // Store a string with one char
    public static void testMetadataStringsLengthOne() {
        MetadataTestCase mtc;
        QueryExpression qe;

        mtc = new MetadataTestCase("metadata.string.alphabetic.length.one");

        mtc.addMD(STRINGFIELD1, "x");
        mtc.store();

        // Add queries to verify
        qe = new QueryEQUALS(STRINGFIELD1, "'x'");
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true, // expected result for just MD
            1, MetadataQuery.EXPECTEXACT, true); // expected res for TestRun MD

        qe = new QueryNOTEQUALS(STRINGFIELD1, "'x'");
        mtc.addQuery(qe,
            0, MetadataQuery.EXPECTUNKNOWN, false,
            0, MetadataQuery.EXPECTEXACT, false); // relies on test order
        
        qe = new QueryGREATERTHAN(STRINGFIELD1, "'x'");
        mtc.addQuery(qe,
            0, MetadataQuery.EXPECTUNKNOWN, false,
            0, MetadataQuery.EXPECTEXACT, false); // relies on test order

        qe = new QueryLESSTHAN(STRINGFIELD1, "'x'");
        mtc.addQuery(qe,
            0, MetadataQuery.EXPECTUNKNOWN, false,
            0, MetadataQuery.EXPECTEXACT, false); // relies on test order

        qe = new QueryGREATERTHAN(STRINGFIELD1, "'w'");
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTEXACT, true); // relies on test order
        
        qe = new QueryLESSTHAN(STRINGFIELD1, "'y'");
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTEXACT, true); // relies on test order

        qe = new QueryOR(new QueryEQUALS(STRINGFIELD1, "'x'"),
            new QueryEQUALS(STRINGFIELD1, "'z'"));
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTMINIMUM, true);

        qe = new QueryPARENTHESES(new QueryOR(new QueryEQUALS(STRINGFIELD1, "'x'"),
            new QueryEQUALS(STRINGFIELD1, "'z'")));
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTEXACT, true); // relies on test order

        qe = new QueryAND(new QueryEQUALS(STRINGFIELD1, "'x'"),
            new QueryEQUALS(STRINGFIELD1, "'z'"));
        mtc.addQuery(qe,
            0, MetadataQuery.EXPECTEXACT, false,
            0, MetadataQuery.EXPECTEXACT, false);

        // execute all the queries for this testcase
        mtc.query();
        mtc.logResult();
    }

    // use a single number for a string
    public static void testMetadataStringsNumericLengthOne() {
        MetadataTestCase mtc;
        QueryExpression qe;

        mtc = new MetadataTestCase("metadata.string.numeric.length.one");
        mtc.addMD(STRINGFIELD1, "3");
        mtc.store();

        qe = new QueryEQUALS(STRINGFIELD1, "'3'");
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTEXACT, true);

        qe = new QueryNOTEQUALS(STRINGFIELD1, "'3'");
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, false, // relies on test order
            0, MetadataQuery.EXPECTEXACT, false); // relies on test order

        qe = new QueryGREATERTHAN(STRINGFIELD1, "'2'");
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTEXACT, true);

        qe = new QueryLESSTHAN(STRINGFIELD1, "'4'");
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTEXACT, true);

        qe = new QueryGREATERTHAN(STRINGFIELD1, "'3'");
        mtc.addQuery(qe,
            0, MetadataQuery.EXPECTMINIMUM, false,
            0, MetadataQuery.EXPECTEXACT, false);

        qe = new QueryLESSTHAN(STRINGFIELD1, "'2'");
        mtc.addQuery(qe,
            0, MetadataQuery.EXPECTUNKNOWN, false,
            0, MetadataQuery.EXPECTEXACT, false); // relies on test order

        qe = new QueryOR(new QueryLESSTHAN(STRINGFIELD1, "'2'"),
            new QueryGREATERTHAN(STRINGFIELD1, "'4'"));
        mtc.addQuery(qe,
            0, MetadataQuery.EXPECTUNKNOWN, false,
            0, MetadataQuery.EXPECTEXACT, false); // relies on test order

        qe = new QueryAND(new QueryPARENTHESES(
                            new QueryOR(
                                new QueryEQUALS(STRINGFIELD1, "'1'"),
                                new QueryEQUALS(STRINGFIELD1, "'3'"))),
                          new QueryPARENTHESES(
                            new QueryOR(
                                new QueryGREATERTHAN(STRINGFIELD1, "'1'"),
                                new QueryNOTEQUALS(STRINGFIELD1, "'3'"))));
        mtc.addQuery(qe,
            1, MetadataQuery.EXPECTMINIMUM, true,
            1, MetadataQuery.EXPECTEXACT, true); // relies on test order

        mtc.query();
        mtc.logResult();
    }

    // This holds the results of the different styles of queries we
    // do.  The results should all be equivalent.
    public static class QueryResults {
        public QueryResultSet qrs = null;
        public int numQueryResults = -1;
        public long queryTime = 0;

        public List cookieQueryList = new ArrayList();
        public int numQueryCookieResults = -1;
        public long cookieQueryTime = 0;

        public List cookieUniqueQueryList = new ArrayList();
        public int numUniqueValuesCookieResults = -1;
        public long cookieUniqueQueryTime = 0;

        public QueryResults() {
        }

        public QueryResults(QueryResultSet q) {
            qrs = q;
        }
    }

    // Run the query in a variety of ways
    public static QueryResults executeQuery(String q, int maxRes) {
        QueryResults qr = new QueryResults();
        CmdResult cr = null;
        QueryResultSet cookieQueryResults = null;
        long callStartTime = 0;

        try {
            printInfo("Calling query(" + q + ", " + maxRes + ")");
            callStartTime = TestLibrary.msecsNow();
            cr = htc.query(q, maxRes);
            qr.queryTime = TestLibrary.msecsNow() - callStartTime;
            qr.qrs = (QueryResultSet)cr.rs;
            printDebug("Done calling query(" + q + ", " + maxRes + ") after " +
                qr.queryTime + " msecs");
            printInfo("Counting results...");
            qr.numQueryResults = htc.queryResultCount(cr);
            printInfo("Done counting results...found " + qr.numQueryResults);


            // try using cookies -- which aren't in the api anymore...so just
            // test we can pass small values of maxRes
            // XXX randomize max?  make it smaller?  use heuristics based
            // on how many results are above?
            int cookieMaxRes = maxRes - 1; // try a different number
            int i = 0; // for periodic printing
            printInfo("Calling query(" + q + ", " + cookieMaxRes +
                ") - cookie test - might take a while");
            callStartTime = TestLibrary.msecsNow();

            cr = htc.query(q, cookieMaxRes);
            cookieQueryResults = (QueryResultSet)cr.rs;
            while (cookieQueryResults.next()) {
                qr.cookieQueryList.add(cookieQueryResults.getObjectIdentifier());

                // print occasional status since this can take a while
                if ((i++ % 20000) == 0) {
                   printInfo("Still doing cookie query, currently have " +  
                       qr.cookieQueryList.size() + " results");
                }
            }
            printInfo("Done with cookie query."); 
            // XXX PORT...not really counting the results here...
            printInfo("Counting results...");
            qr.numQueryCookieResults = htc.queryResultCount(cr);
            // XXX Could just use the count from above...
            // qr.numQueryResults = qr.numQueryResults;
            printInfo("Done counting results...found " + qr.numQueryResults);

            qr.cookieQueryTime = TestLibrary.msecsNow() - callStartTime;
            printDebug("Done calling query(" + q + ", " + cookieMaxRes +
                ") after " + qr.queryTime + " msecs");

            // XXX cookies also for select unique

        } catch (HoneycombTestException e) {
            printError("ERROR: failed to query: " + e.getMessage());
            e.printStackTrace();
        } catch (Throwable e) {
            printError("ERROR: failed to query (unexpected exception): " +
                e.getMessage());
            e.printStackTrace();
        }

        // XXX this doesn't work yet...need iterator and toString() op
        printDebug("Got " + resultSetToString(qr.qrs) + " from query " + q);
        return (qr);
     }

    public static HashMap getMetadata(ObjectIdentifier oid) {
        HashMap hm = null;
        CmdResult cr = null;
        try {
            printInfo("Calling retrieveMetadata(" + oid + ")");
            cr = htc.getMetadata(oid.toString());
            hm = cr.mdMap;
            printDebug("Done calling retrieveMetadata(" + oid + ")");
        } catch (HoneycombTestException e) {
            printError("ERROR: failed to get MD: " + e.getMessage());
            e.printStackTrace();
        } catch (Throwable e) {
            printError("ERROR: failed to get MD (unexpected exception): " +
                e.getMessage());
            e.printStackTrace();
        }

        printDebug("Got " + hm + " from getMD of " + oid);
        return (hm);
    }

    public static ObjectIdentifier addEMDToExistingObject(
        ObjectIdentifier oid, HashMap hm) {
        SystemRecord sr = null;
        CmdResult cr = null;
        try {
            printInfo("Calling storeMetadata(" + oid + ", " + hm + ")");
            cr = htc.addMetadata(oid.toString(), hm);
            sr = cr.sr;
            printDebug("Done calling storeMetadata(" + oid + ", " + hm + ")");
        } catch (HoneycombTestException e) {
            printError("ERROR: failed to store MD: " + e.getMessage());
            e.printStackTrace();
        } catch (Throwable e) {
            printError("ERROR: failed to store MD (unexpected exception): " +
                e.getMessage());
            e.printStackTrace();
        }

        printInfo("Got " + sr + " from store of MD " + hm);
        if (sr != null) {
            return (sr.getObjectIdentifier());
        } else {
            return (null);
        }
    }

    public static ObjectIdentifier storeFileWithAPI(String file) {
        SystemRecord sr = null;
        CmdResult cr = null;

        try {
            // Store an object with no MD...we'll add it later during the
            // test cases

            printInfo("Calling storeObject(channel)");
            cr = htc.store(file, null);
            sr = cr.sr;
            printDebug("Done calling storeObject(channel)");

            /*
            printInfo("XXX Calling archive.storeObject(channel, bogusmd)");
            printInfo("XXX must pass MD else we'll hang!");
            HashMap hm = new HashMap();
            hm.put(STRINGFIELD1, "XXX hang avoidance");
            sr = archive.storeObject(channel, hm);
            printDebug("XXX Done with archive.storeObject(channel, bogusmd)");
            */

        } catch (HoneycombTestException e) {
            printError("ERROR: failed to store file: " + e.getMessage());
            e.printStackTrace();
        } catch (Throwable e) {
            printError("ERROR: failed to store file (unexpected exception): " +
                e.getMessage());
            e.printStackTrace();
        }

        printInfo("Got " + sr + " from store of file " + file);
        if (sr != null) {
            return (sr.getObjectIdentifier());
        } else {
            return (null);
        }
    }

    public static String resultSetToString(ResultSet rs) {
        StringBuffer s = new StringBuffer();

        if (rs == null) {
            return (null);
        }

        s.append("{");
        s.append(" XXX need iterator reset and ResultSet.toString() "); 
        // XXX can't reset an iterator yet, so can't use this call
        // and make further iterations, and we need this for checking
        // if an oid is in the results
        /*
        for (int i = 0; i < rs.getResultCount(); i++) {
            s.append("[" + i + "]=" + rs.get(i) + " ");
        }
        */
        s.append("}");

        return (s.toString());
    }

    public static class MetadataTestCase {
        // XXX option to create small file each time versus using same file

        public String testcase;
        public long teststart;
        public HashMap emd; // extended MD to store
        public HashMap emdTestRun; // EMD to store for this specific run
        public ObjectIdentifier oidMD;
        public ObjectIdentifier oidTestRunMD;
        public ArrayList qList; // list of MetadataQuery objects to validate
        public int numerrors = 0;  // once this is set to > 0
                                   // the code does nothing further
        public String reason = "";  // If we fail, we can say why here

        public MetadataTestCase(String s) {
            testcase = s;
            teststart = TestLibrary.msecsNow();
            oidMD = null;
            oidTestRunMD = null;
            emd = new HashMap();
            qList = new ArrayList();

            testCaseInit(s);
        }

        // XXX must we pass strings for longs, etc? XXX Can put(String, Long)
        // Add a field/value to our MD object
        public void addMD(String field, String value) {
            if (numerrors > 0) {
                return;
            }

            emd.put(field, value);
        }

        // Add the fields that are unique to this testrun
        public void addTestRunMD() {
            if (numerrors > 0) {
                return;
            }

            // XXX  need copy constructor
            printInfo("XXX need copy constructor NameValueRecord--manually adding MD until then");
            emdTestRun = new HashMap();
            // XXX emdTestRun = new NameValueRecord(emd);
            emdTestRun.put(STRINGFIELD1, (String)emd.get(STRINGFIELD1));
            // XXX remove
            emdTestRun.put("filename", testFile);
            emdTestRun.put("client", testRunClient);
            // XXX pass as long...need coercion
            printInfo("XXX need type coercion [not any more?]");
            emdTestRun.put("timestart", new Long(testCaseStartTime));
        }

        public QueryExpression getTestRunQuerySinceStart() {
            return (new QueryAND(
                new QueryAND(
                    new QueryEQUALS("filename", "'" + testFile + "'"),
                    new QueryEQUALS("client", "'" + testRunClient + "'")),
                // XXX coercion!
                new QueryGREATERTHAN("timestart", ""+testRunStartTime)));
        }

         public QueryExpression getTestRunQuery() {
            if (numerrors > 0) {
                return (null);
            }

            return (new QueryAND(
                new QueryAND(
                    new QueryEQUALS("filename", "'" + testFile + "'"),
                    new QueryEQUALS("client", "'" + testRunClient + "'")),
                // XXX coercion!
                new QueryEQUALS("timestart", ""+testCaseStartTime)));
        }

        public void store() {
            if (numerrors > 0) {
                return;
            }

            // we do two stores: one with MD specific to the test run
            // and one with only the MD given
            printInfo("\nStoring file with basic MD");
            oidMD = addEMDToExistingObject(storedFileOID, emd);
            if (oidMD == null) {
                addError("Failed to add MD to existing OID");
                return;
            }
            validateEMD(oidMD, emd);
            totalFilesStored++;

            printInfo("\nStoring file with test-run specific MD");
            addTestRunMD();
            oidTestRunMD = addEMDToExistingObject(storedFileOID, emdTestRun);
            if (oidTestRunMD == null) {
                addError("Failed to add test run MD to existing OID");
                return;
            }
            validateEMD(oidTestRunMD, emdTestRun);
            totalTestRunFilesStored++;
            totalFilesStored++;
        }

        public void validateEMD(ObjectIdentifier oid, HashMap hm) {
            printInfo("Validating the EMD of object " + oid);

            HashMap servermd = getMetadata(oid);
            if (!hm.equals(servermd)) {
                addError("ERROR: Mismatch in MD; Passed in EMD: " + hm +
                    "; Stored EMD: " + servermd);
                return;
            }
            printDebug("Done validating EMD for oid " + oid);
/**
The following code has been comparing nvr to itself,
and since manual comparison of two HashMaps is different
from NVR comparison, just doing a quick-n-dirty compare
for now

            // Get the keys we passed into the API
            String keys[] = nvr.getKeys();

            // Get the keys the server thinks we stored
            HashMap servernvr = getMetadata(oid);
            String serverkeys[] = nvr.getKeys();

            if (serverkeys == null && keys == null) {
                return;
            }

            if ((serverkeys == null && keys != null) ||
                (serverkeys != null && keys == null) ||
                serverkeys.length != keys.length) {
                addError("ERROR: Mismatch in MD; Passed in EMD: " + nvr +
                    "; Stored EMD: " + servernvr);
            }

            printDebug("We store the following MD for " + oid);
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];

                for (int j = 0; j < serverkeys.length; j++) {
                    // Make sure this key shows up in the server map
                    // Since maps are same length, if all keys in one are
                    // in the other, that's enough for equivalence
                    // Note that we can't use nvr.equals because the system
                    // metadata won't match
                    if (serverkeys[j].equals(key)) {
                        // also check the values are the same
                        if ((nvr.getAsString(key)).equals(servernvr.getAsString(key))) {
                            printDebug("Found " + key + "=" + nvr.getAsString(key));
                        } else {
                            addError("ERROR: key " + key + " mismatch in MD; "
                                + "Passed in EMD: " + nvr + "; Stored EMD: " +
                                servernvr);
                        }
                    }
                    // XXX add code to detect if a key wasn't found and
                    // consider it a failure
                }
            }
            printDebug("Done validating EMD for oid " + oid);
*/
        }


        // Add a query without the limit on this test run.
        public void addQuery(QueryExpression qe,
            long expectedResults,
            int resultVerification,
            boolean expectNewObjInResults) {

            if (numerrors > 0) {
                return;
            }
            
            MetadataQuery mq = new MetadataQuery(qe,
                expectedResults, resultVerification, expectNewObjInResults,
                oidMD);

            printDebug("\nAdding query " + mq);
            qList.add(mq);
        }

        // Add a query with and without the limit on this test run.
        public void addQuery(QueryExpression qe,
            long expectedResults,
            int resultVerification,
            boolean expectNewObjInResults,
            long expectedResultsTestRun,
            int resultVerificationTestRun,
            boolean expectNewObjInResultsTestRun) {
            
            if (numerrors > 0) {
                return;
            }

            MetadataQuery mq = new MetadataQuery(qe,
                expectedResults, resultVerification, expectNewObjInResults,
                oidMD);

            printDebug("\nAdding query " + mq);
            qList.add(mq);

            mq = new MetadataQuery(new QueryAND(qe, getTestRunQuery()),
                expectedResultsTestRun, resultVerificationTestRun,
                expectNewObjInResultsTestRun, oidTestRunMD);

            printDebug("\nAdding query " + mq);
            qList.add(mq);
        }

        public void query() {
            if (numerrors > 0) {
                return;
            }
            
            // Add one more query for items added in just this test run
            MetadataQuery mq = new MetadataQuery(getTestRunQuerySinceStart(),
                totalTestRunFilesStored, MetadataQuery.EXPECTEXACT, true,
                oidTestRunMD);
            qList.add(mq);

            Iterator i = qList.iterator();
            while (i.hasNext()) {
                MetadataQuery q = (MetadataQuery)i.next();
                try {
                    printInfo(""); // add a newline for ease of log reading
                    q.query();
                } catch (QueryException e) {
                    addError("Query " + q + " failed: " + e.getMessage());
                }
            }
        }

        public void logResult() {
            testCaseFini();
        }
    }

    public static class MetadataQuery {
        // For resultVerification values
        public static final int EXPECTEXACT = 0;
        public static final int EXPECTMINIMUM = 1;
        public static final int EXPECTMAXIMUM = 2;
        public static final int EXPECTUNKNOWN = 3; // no expectation on results

        public QueryExpression q; // Query to run
        public long expectedResults; // number of results to expect for query
        public int resultVerification; // expect minimum, maximum, exact, etc
        public boolean expectNewObjInResults; // should the newly stored
                                              // object be in the result set
        public ObjectIdentifier oid; // Oid to find or not find in results

        public MetadataQuery(QueryExpression query, long results,
            int verification, boolean expectNewObj,
            ObjectIdentifier oidToTest) {
            q = query;
            expectedResults = results;
            resultVerification = verification;
            expectNewObjInResults = expectNewObj;
            oid = oidToTest;
        }

        public String toString() {
            return ("MetadataQuery: query[" + q + "]; expectedResults[" +
                expectedResults + "]; resultVerification[" +
                getVerificationString(resultVerification) + "]; " +
                "expectNewObjInResults[" + expectNewObjInResults + "]; " +
                "oid[" + oid + "]");
        }

        public String toExpectedResultsString() {
            return ("expectedResults[" +
                expectedResults + "]; resultVerification[" +
                getVerificationString(resultVerification) + "]; " +
                "expectNewObjInResults[" + expectNewObjInResults + "]; " +
                "oid[" + oid + "]");
        }

        public String getVerificationString(int i) {
            if (i == EXPECTEXACT) {
                return ("EXPECTEXACT");
            } else if (i == EXPECTMINIMUM) {
                return ("EXPECTMINIMUM");
            } else if (i == EXPECTMAXIMUM) {
                return ("EXPECTMAXIMUM");
            } else if (i == EXPECTUNKNOWN) {
                return ("EXPECTUNKNOWN");
            } else {
                throw new IllegalArgumentException("Invalid verification " +
                    " value " + i);
            }
        }

        public boolean isOidInResults(Object oid, ResultSet results)
            throws Throwable {
            ObjectIdentifier oidresult = null;
            String valueresult;
            if (results == null) {
                return (false);
            }

            while (results.next()) {
                if (results instanceof QueryResultSet) {
                    oidresult = ((QueryResultSet)results).getObjectIdentifier();
                    if (oidresult.compareTo(oid) == 0) {
                        return (true);
                    }
                } else {
                    throw new ClassCastException("invalid ResultSet");
                }
            }

            return (false);
        }

        public void query() throws QueryException {
            long numResults = 0;
            long numUniqueResults = 0;
            long numCookieResults = 0;
            long numCookieUniqueResults = 0;

            printDebug("Executing query " + q);
            QueryResults qr = executeQuery(q.toString(), maxResults);

            QueryResultSet results = qr.qrs;
            if (results == null) {
                throw new QueryException("Query returned null");
            }
            numResults = qr.numQueryResults;
            printInfo("Query returned " + numResults +
                " results and we expected " + toExpectedResultsString() +
                ".  Execution took " + qr.queryTime + " msecs");

            // Verify cookies results are the same as the non-cookies results
            numCookieResults = qr.numQueryCookieResults;
            if (numCookieResults != numResults) {
                addError("ERROR: results for normal and cookie query " +
                    "have different results; " +
                    "CookieQueryResults " + numCookieResults +
                    "; QueryResultSet " + numResults);
            } else {
                printDebug("Results for normal and cookie query are the same: "
                    + numCookieResults);
            }

            // Verify that we got the right number of results
            switch (resultVerification) {
            case EXPECTEXACT:
                if (numResults != expectedResults) {
                    throw new QueryException("Expected exactly " +
                        expectedResults + " but got " + numResults);
                } else {
                    printInfo("Got exactly " + numResults + " as expected");
                }
                break;
            case EXPECTMINIMUM:
                if (numResults < expectedResults) {
                    throw new QueryException("Expected >= " +
                        expectedResults + " but got " + numResults);
                } else {
                    printInfo("Got " + numResults +
                        " results, which is >= " + expectedResults +
                        " as expected");
                }
                break;
             case EXPECTMAXIMUM:
                if (numResults > expectedResults) {
                    throw new QueryException("Expected <= " + expectedResults +
                    " but got " + numResults);
                } else {
                    printInfo("Got " + numResults +
                        " results, which is <= " + expectedResults +
                        " as expected");
                }
                break;
             case EXPECTUNKNOWN:
                printInfo("Got " + numResults +
                    " results with no expectation");
                break;
             default:
                throw new QueryException("Invalid result verification type " +
                    resultVerification);
            }

            // Check if the oid we stored is in the results or not
            boolean foundoid = false;
            try {
                foundoid = isOidInResults(oid, results);
            } catch (Throwable t) {
                throw new QueryException("Failed to look for oid in results " +
                    t);
            }

            if (expectNewObjInResults && foundoid) {
                printInfo("As expected, found oid " + oid + " in results");
            } else if (expectNewObjInResults && !foundoid) {
                // XXX maybe this is case because don't use cookies
                // to get more results
                String s = "";
                if (maxResults == numResults) {
                    s = "--XXX this error might be okay because we " +
                        "don't use cookies yet and didn't get all results";
                }
                throw new QueryException("Didn't find oid " + oid +
                    " in results, but we expected it" + s);
            } else if (!expectNewObjInResults && !foundoid) {
                // XXX use cookies
                if (maxResults == numResults) {
                    printError("XXX might have more results--false negative?");
                }
                printInfo("As expected, didn't find oid " + oid +
                    " in results");
            } else if (!expectNewObjInResults && foundoid) {
                throw new QueryException("Didn't expect oid " + oid +
                    " in results, but we found it");
            }

            // Verify that results are sorted

            // Verify that there are no duplicates in the results

            // Verify that all results returned match the query
            // for all results
            //    md = getMD(oid);
            //    if (!objectMatchesQuery(md)
            //          error!
            //

         }
    }
 
    /**
     * Exception reporting errors in queries
     */
    public static class QueryException extends Exception {
        QueryException(String s) {
            super(s);
        }

        QueryException(String s, Exception e) {
            super(s, e);
        }
    }

    // XXX Must test illegal syntax some other way!
    public static class QueryExpression {
        QueryExpression qe = null;

        public QueryExpression() {
        }

        public QueryExpression(QueryExpression q) {
            qe = q;
        }

        public boolean objectMatchesQuery(HashMap hm) {
            return (false);
        }

        public String toString() {
            return (null);
        }
    }

    public static class QueryClauseConnector extends QueryExpression {
        QueryExpression qe1 = null;
        QueryExpression qe2 = null;
        String connector = null;
        public QueryClauseConnector(QueryExpression q1, QueryExpression q2) {
            qe1 = q1;
            qe2 = q2;
            if (qe1 == null || qe2 == null) {
                throw new IllegalArgumentException("Can't have null for " +
                    " queryElements in QueryClauseConnector");
            }
        }

        public String toString() {
            return (qe1 + " " + connector + " " + qe2);
        }
    }

    public static class QueryPARENTHESES extends QueryExpression {
        public QueryPARENTHESES(QueryExpression q) {
            super (q);
        }

        public String toString() {
            return ("(" + qe + ")");
        }

        public boolean objectMatchesQuery(HashMap hm) {
            return (qe.objectMatchesQuery(hm)); 
        }
    }

    public static class QueryAND extends QueryClauseConnector {
        public QueryAND(QueryExpression q1, QueryExpression q2) {
            super (q1, q2);
            connector = "AND";
        }

        public boolean objectMatchesQuery(HashMap hm) {
            return (qe1.objectMatchesQuery(hm) &&
                    qe2.objectMatchesQuery(hm)); 
        }
    }

    public static class QueryOR extends QueryClauseConnector {
        public QueryOR(QueryExpression q1, QueryExpression q2) {
            super (q1, q2);
            connector = "OR";
        }

        public boolean objectMatchesQuery(HashMap hm) {
            return (qe1.objectMatchesQuery(hm) || 
                    qe2.objectMatchesQuery(hm)); 
        }
      }

    public static class QueryClause extends QueryExpression {
        // XXX are these needed if we do coercion?
        public static final int TYPE_INVALID = 0;
        public static final int TYPE_STRING = 1;
        public static final int TYPE_LONG = 2;
        public static final int TYPE_DOUBLE = 3;
        public static final int TYPE_BLOB = 4;
        public String field = null;
        public String op = null;
        public Object value = null;
        public int type = 0;

        // QueryClause(String f, Object v, int t) 
        QueryClause(String f, Object v) {
            field = f;
            value = v;
            type = 0;
            //printInfo("QueryClause Object type was " + v.getClass().getName());
        }

        public String toString() {
            return (field + op + value);
        }
    }

    public static class QueryEQUALS extends QueryClause {
        public QueryEQUALS(String f, String v) {
            super(f, v);
            op = "=";
        }

        public boolean objectMatchesQuery(HashMap hm) {
            String v = (String) hm.get(field);
            // XXX convert type!
            if (v == null) {
                printError("ERROR: field " + field + " was null");
                return (false);
            }
            // toString() for java5
            return (v.compareTo(value.toString()) == 0);
        }
    }

    public static class QueryNOTEQUALS extends QueryClause {
        public QueryNOTEQUALS(String f, String v) {
            super(f, v);
            op = "<>";
        }

        public boolean objectMatchesQuery(HashMap hm) {
            String v = (String) hm.get(field);
            // XXX convert type!
            if (v == null) {
                printError("ERROR: field " + field + " was null");
                return (false);
            }
            // toString() for java5
            return (v.compareTo(value.toString()) != 0);
        }
    }

    public static class QueryLESSTHAN extends QueryClause {
        public QueryLESSTHAN(String f, String v) {
            super(f, v);
            op = "<";
        }

        public boolean objectMatchesQuery(HashMap hm) {
            String v = (String) hm.get(field);
            if (v == null) {
                printError("ERROR: field " + field + " was null");
                return (false);
            }
            // XXX > or <   XXX added toString() for java5
            return (v.compareTo(value.toString()) < 0);
        }
    }

    public static class QueryGREATERTHAN extends QueryClause {
        public QueryGREATERTHAN(String f, String v) {
            super(f, v);
            op = ">";
        }

        public boolean objectMatchesQuery(HashMap hm) {
            String v = (String) hm.get(field);
            if (v == null) {
                printError("ERROR: field " + field + " was null");
                return (false);
            }
            // XXX > or <   XXX added toString() for java5
            return (v.compareTo(value.toString()) > 0);
        }
    }

    /**
     * Exception class for handling the parsing of arguments.
     */
    public static class ArgException extends Exception {
        ArgException(String s) {
            super(s);
        }

        ArgException(String s, Exception e) {
            super(s, e);
        }
    }

    /**
     * Examine the arguments passed to this program and set variables
     * accordingly.
     */
    public static void parseArgs(String args[]) throws ArgException {
        // XXX Maybe use some form of getopt instead?
        String opt;
        int ival = 0;
        long lval = 0;
        String sval = null;
        char c = '\0';
        String[] saval = {};

        // For reference, print out the arg string.  This is useful primarily
        // when output is redirected to a log file.
        String argString = new String("");
        for (int i = 0; i < args.length; i++) {
            argString += args[i] + " ";

            // Hack to use Log class if -T is an arg before we print anything
            if (args[i].equals("-T")) {
                useLogClass = true;
                TestLibrary.useLogClass = true;
            }
        }
        invocationArgs = argString;
        printInfo("TestMetadata called with args '" + argString + "'");

        Vector optsWithNoOperands = new Vector();
        Vector optsWithIntOperands  = new Vector();
        Vector optsWithLongOperands  = new Vector();
        Vector optsWithStringOperands = new Vector();
        Vector optsWithCharOperands = new Vector();
        Vector optsWithStringArrayOperands = new Vector();

        // Initialize these lists
        optsWithNoOperands.add("-q");
        optsWithNoOperands.add("-v");
        optsWithNoOperands.add("-T");

        optsWithIntOperands.add("-p");

        optsWithStringArrayOperands.add("-s");

        for (int i = 0; i < args.length; i++) {
            opt = args[i];

            if (optsWithIntOperands.contains(opt)) {
                try {
                    ival = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    throw new ArgException("failed to parse arg for " + opt +
                        " as an int", e);
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithLongOperands.contains(opt)) {
                try {
                    lval = Long.parseLong(args[++i]);
                } catch (NumberFormatException e) {
                    throw new ArgException("failed to parse arg for " + opt +
                        " as a long", e);
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithStringOperands.contains(opt)) {
                try {
                    sval = args[++i];
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithCharOperands.contains(opt)) {
                try {
                    sval = args[++i];
                    if (sval.length() != 1) {
                        throw new ArgException(opt + " must be a single char");
                    }
                    c = sval.charAt(0);
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithStringArrayOperands.contains(opt)) {
                try {
                    sval = args[++i];
                    saval = sval.split(",");
                    if (saval.length < 1) {
                        throw new ArgException(opt + " needs >= 1 entry");
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
             }

            // No args
            if (opt.equals("-q")) { // disable logging
                testRunLogDebugMsgs = false;
                testRunLogInfoMsgs = false;
                TestLibrary.logDebugMsgs = false;
                TestLibrary.logInfoMsgs = false;
            } else if (opt.equals("-v")) { // enable verbose logging
                testRunLogDebugMsgs = true;
                testRunLogInfoMsgs = true;
                TestLibrary.logDebugMsgs = true;
                TestLibrary.logInfoMsgs = true;
            } else if (opt.equals("-T")) { // run in context of test suite
                throwInsteadOfExit = true;
                useLogClass = true;
                TestLibrary.useLogClass = true;
            } else if (opt.equals("-h")) { // help
                // XXX avoid printing "error" in main
                throw new ArgException("Help text is as follows");

            // Int args
            } else if (opt.equals("-p")) { // port
                port = ival;

            // String array args
            } else if (opt.equals("-s")) { // server
                server = sval; // flat string
                servers = saval; // array

            // Error case
            } else {
                throw new ArgException("invalid argument: " + opt);
            }
        }
    }
}
