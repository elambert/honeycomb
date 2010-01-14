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

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.common.*;
import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.ArchiveException;

import java.util.ArrayList;
import java.util.Date;

/**
 * Basic tests of compliance features for retention & legal holds
 */
public class ComplianceBasic extends HoneycombLocalSuite {

    private CmdResult storeResult = null;
    public HoneycombTestClient htc;
    private long runId;

    public ComplianceBasic() {
        super();
    }

    public String help() {
        return("\tValidate basic compliance features\n");
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
 
        super.setUp();
    }

    public void init() throws HoneycombTestException {
        // Create the test client for all test cases
        htc = new HoneycombTestClient(testBed.dataVIPaddr);
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /********************************************************
     *
     * Bug 6554027 - hide retention features
     *
     *******************************************************/
    public void testRetentionTime() throws HoneycombTestException {
        Date cellTime;
        Date retentionTime;
        Date newRetentionTime;
        ObjectIdentifier oid;

        TestCase self = createTestCase("Compliance", "Test retention time");

        // Set test suite tags
	self.addTag(Tag.NORUN, "Compliance feature has been disabled for 1.1, tests do not apply"); 
        //self.addTag(Tag.REGRESSION);
        //self.addTag(HoneycombTag.COMPLIANCE);
        //self.addTag(HoneycombTag.EMULATOR);

        if (self.excludeCase()) return;

        // Get the data VIP
        String dataVIP = testBed.dataVIPaddr;
        Log.INFO("Data VIP: " + dataVIP);

        if (htc == null) {
            init();
        }

	/*
        // Get the cell compliance time
        try {
            cellTime = htc.getDate();
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to get the cell compliance time: " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Print out the times
        Log.INFO("Test client time is:     " + getDateString(new Date()));
        Log.INFO("Cell compliance time is: " + getDateString(cellTime));

        // Store an object and then set a retention time for it
        Log.INFO("Storing a test object for compliance testing");
        try {
            storeResult = store(1024);
            Log.INFO("Stored OID " + storeResult.mdoid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Store failed: " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Create an ObjectIdentifier object out of the result
        oid = new ObjectIdentifier(storeResult.mdoid);

        // Get the retention time
        try {
            retentionTime = htc.getRetentionTime(oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to get the retention time on OID " +
                            oid + " : " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Make sure the time on the stored object is 0
        if (!retentionTime.equals(htc.DATE_UNSET)) {
            self.testFailed("Got " + retentionTime.getTime() +
                            " instead of the expected retention " +
                            "time of 0 on OID " + oid);
            return;
        } else {
            Log.INFO("Retrieved the expected default retention time of " +
                     "0 for OID " + oid);
        }

       // Set the retention time to unspecified (infinite)
        try {
            htc.setRetentionTime(oid, htc.DATE_UNSPECIFIED);
            Log.INFO("Set an unspecified retention time (-1) on OID " + oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to set an unspecified retention time " +
                            "(-1) on OID " + oid + " : " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Get the retention time
        try {
            retentionTime = htc.getRetentionTime(oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to get the retention time on OID " +
                           oid + " : " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Make sure the time on the stored object is -1
        if (!retentionTime.equals(htc.DATE_UNSPECIFIED)) {
            self.testFailed("Got " + retentionTime + " instead of " +
                            "the expected unspecified (-1) retention time " +
                            "on OID " + oid);
            return;
        } else {
            Log.INFO("Retrieved the expected unspecified retention time " +
                     "(-1) for OID " + oid);
        }

        // Set the retention time 30 seconds from now
        try {
            newRetentionTime = htc.setRetentionTimeRelative(oid, 30);
            Log.INFO("Set a retention time 30 seconds from now " +
                     getDateString(newRetentionTime) +
                     " on OID " + oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to set a retention time 30 " +
                            "seconds from now on OID " + oid + " : " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Get the retention time
        try {
            retentionTime = htc.getRetentionTime(oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to get the retention time on OID " +
                            oid + " : " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Make sure the time on the stored object is the time set
        if (!retentionTime.equals(newRetentionTime)) {
            self.testFailed("Got " + getDateString(retentionTime) +
                            " instead of the expected retention time of " +
                            getDateString(newRetentionTime) +
                            " on OID " + oid);
            return;
        } else {
            Log.INFO("Retrieved the expected future retention time of " +
                     getDateString(newRetentionTime) + " for OID " + oid);
        }

        // Make sure we can't delete the object with the future retention time
        boolean deleteFailed = false;
        try {
            htc.delete(true, oid);
        } catch (HoneycombTestException hte) {
            deleteFailed = true;
            Log.INFO(Log.stackTrace(hte));
            Log.INFO("Delete of OID " + oid + " failed as epxected " +
                     "since the retention time has not yet expired");
        }

        // Fail the test if the delete succeeded
        if (deleteFailed == false) {
            self.testFailed("Unexpectedly succeeded in deleting OID " +
                            oid + " even though the retention time " +
                            "had not yet expired");
            return;
        }

        // Sleep 60 seconds so that the file retention time passes
        int sleepTime = 60;
        Log.INFO("Sleeping " + sleepTime +  " seconds to allow the " +
                 "retention time to expire on OID " + oid);
        try {
            Thread.sleep(sleepTime * 1000);
        } catch (InterruptedException e) {}

        // Try deleting the file after the retention time has expired
        try {
            Log.INFO("Deleting OID " + oid + " after the retention " +
                     "time has expired");                     
            htc.delete(true, oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Unexpectedly failed to delete OID " +
                            oid + " even though the retention time " +
                            "has expired");
            return;
        }

	*/

        // Passed all compliance tests!
        self.testPassed("Compliance retention time tests passed!");
    }

   public void testQueryLegalHolds() throws HoneycombTestException {
        ObjectIdentifier oid;
        CmdResult[] storeResultArray = null;
        ObjectIdentifier[] oidArray = null;
        String legalHold = "Query vs. Test " + getRunId();
        int numStoreObjects = 10;

        TestCase self = createTestCase("Compliance", "Test query of legal holds");

        // Set test suite tags
        //self.addTag(Tag.REGRESSION);
        self.addTag(HoneycombTag.COMPLIANCE);
        //self.addTag(HoneycombTag.EMULATOR);
        if (self.excludeCase()) return;

        if (htc == null) init();

	/*
        // Store an object with a legal hold
        storeResultArray = new CmdResult[numStoreObjects];
        oidArray = new ObjectIdentifier[numStoreObjects];
        Log.INFO("Storing " + numStoreObjects + " test objects for compliance testing");
        for (int i=0; i<numStoreObjects; i++) {
            try {
                storeResultArray[i] = store(1024);
                Log.INFO("Stored OID " + storeResultArray[i].mdoid);
                oidArray[i] = new ObjectIdentifier( storeResultArray[i].mdoid);
            } catch (HoneycombTestException hte) {
                self.testFailed("Store failed: " + hte);
                Log.DEBUG(Log.stackTrace(hte));
                return;
            }
        }

        // Add the same legal hold to all objects
        Log.INFO("Adding legal hold [" + legalHold + "] to the " +
                 numStoreObjects + " test objects");
        for (int i=0; i<numStoreObjects; i++) {
            try {
                htc.addLegalHold(oidArray[i], legalHold);                
                Log.INFO("Set a legal hold of [" + legalHold +
                         "] on OID " + oidArray[i]);
            } catch (HoneycombTestException hte) {
                self.testFailed("Failed to set a legal hold [" + legalHold +
                                "] on OID " + oidArray[i] + " : " + hte);
                Log.DEBUG(Log.stackTrace(hte));
                return;
            }
        }

        // Query the legal holds
        Log.INFO("Querying Honeycomb for legal hold [" + legalHold + "]");
        QueryResultSet results = htc.queryLegalHold(legalHold, numStoreObjects);
        ArrayList resultsArray = new ArrayList();
        try {
            while (results.next()) {
                resultsArray.add(results.getObjectIdentifier());
            }
        } catch (Exception e) {
            self.testFailed("Failed to read the QueryResultSet from " +
                            "querying for legal hold [" + legalHold +
                            "]:" + e);
            Log.DEBUG(Log.stackTrace(e));
            return;            
        }

        // For each of the OIDs stored, look for it in the query results
        for (int i=0; i<numStoreObjects; i++) {
            if (resultsArray.contains(oidArray[i])) {
                Log.INFO("Found OID " + oidArray[i] + " in the query result set");
            } else {
                self.testFailed("Query results do not contain OID " + oidArray[i]);
                return;
            }
        }

        // Remove the legal holds
        Log.INFO("Removing legal hold [" + legalHold + "] from the " +
                 numStoreObjects + " test objects");
        for (int i=0; i<numStoreObjects; i++) {
            try {
                htc.removeLegalHold(oidArray[i], legalHold);
                Log.INFO("Removed legal hold [" + legalHold +
                         "] from OID " + oidArray[i]);
            } catch (HoneycombTestException hte) {
                self.testFailed("Failed to remove legal hold [" + legalHold +
                                "] from OID " + oidArray[i] + " : " + hte);
                Log.DEBUG(Log.stackTrace(hte));
                return;
            }
        }

        // Delete the files after the legal hold has been removed
        for (int i=0; i<numStoreObjects; i++) {
            try {
                Log.INFO("Deleting OID " + oidArray[i] +
                         " after removal of the legal hold");                     
                htc.delete(true, oidArray[i]);
            } catch (HoneycombTestException hte) {
                self.testFailed("Unexpectedly failed to delete OID " +
                                oidArray[i] + " even though the legal " +
                                "hold was removed");
                return;
            }
        }
	*/

        // Passed all compliance tests!
        self.testPassed("Compliance query legal hold tests passed!");
   }

   public void testLegalHolds() throws HoneycombTestException {
        ObjectIdentifier oid;

        TestCase self = createTestCase("Compliance", "Test legal holds");

        // Set test suite tags
        //self.addTag(Tag.REGRESSION);
        self.addTag(HoneycombTag.COMPLIANCE);
        if (self.excludeCase()) return;

        if (htc == null) init();

	/*
        // Store an object
        Log.INFO("Storing a test object for compliance testing");
        try {
            storeResult = store(1024);
            Log.INFO("Stored OID " + storeResult.mdoid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Store failed: " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Create an ObjectIdentifier object out of the result
        oid = new ObjectIdentifier(storeResult.mdoid);

        // Set a legal hold
        String legalHold = "Dogs vs. Cats " + getRunId();
        try {
            htc.addLegalHold(oid, legalHold);
            Log.INFO("Set a legal hold of [" + legalHold +
                     "] on OID " + oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to set a legal hold [" + legalHold +
                            "] on OID " + oid + " : " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Make sure we can't delete the object with a legal hold present
        boolean deleteFailed = false;
        try {
            htc.delete(true, oid);
        } catch (HoneycombTestException hte) {
            deleteFailed = true;
            Log.INFO(Log.stackTrace(hte));
            Log.INFO("Delete of OID " + oid + " failed as epxected " +
                     "because it has a legal hold");
        }

        // Fail the test if the delete succeeded
        if (deleteFailed == false) {
            self.testFailed("Unexpectedly succeeded in deleting OID " +
                            oid + " even though it has a legal hold ");
            return;
        }

        // Remove the legal hold
        try {
            htc.removeLegalHold(oid, legalHold);
            Log.INFO("Removed legal hold [" + legalHold +
                     "] from OID " + oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Failed to remove legal hold [" + legalHold +
                            "] from OID " + oid + " : " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            return;
        }

        // Try deleting the file after the legal hold has been removed
        try {
            Log.INFO("Deleting OID " + oid + " after removal of the " +
                     "legal hold");                     
            htc.delete(true, oid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Unexpectedly failed to delete OID " +
                            oid + " even though the legal hold " +
                            "was removed");
            return;
        }
	*/

        // Passed all compliance tests!
        self.testPassed("Compliance legal hold tests passed!");
   }

    /**
     *  Print the given date in the following format: 
     *
     *  1157085578230 (Thu Aug 31 21:39:38 PDT 2006)
     */
    private String getDateString(Date date) {
        return date.getTime() + " (" + date.toString() + ")";
    }
}
