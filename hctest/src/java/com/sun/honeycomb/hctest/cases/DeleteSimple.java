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
import com.sun.honeycomb.common.*;

import java.util.HashMap;

/**
 * Validate that a basic delete works.
 */
public class DeleteSimple extends DeleteSuite {

    boolean setupOK = false;

    private CmdResult storeResult1;
    private CmdResult storeResult2;
    private CmdResult storeResult3;
    private CmdResult storeResult4;
    private CmdResult storeResult4a;
    private CmdResult storeResult5;
    private CmdResult storeResult5a;
    private CmdResult storeResult6;
    private CmdResult storeResult6a;
    private CmdResult storeResult7;
    private CmdResult storeResult7a;
    private CmdResult storeResult8;
    private CmdResult storeResult8a;
    // XXX also test for invalid data oid?
    // and for oids that are too short.
    // See bug id 6328306
    private String invalidOid =
        //"0158342c72e36111d99d96080020e36996000023940200000000";
        // "1";
        "010001f511de232c7711da94a7080020e3694f00000b9a0200000000";
    private String md_value;
    private String md_value2;
    private String q;
    private String q2;
    private HashMap hm;
    private HashMap hm2;

    public DeleteSimple() {
        super();
    }

    public String help() {
        return(
            "\tSimple scenarios to validate delete functionality\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n"
            );
    }

    /**
     * Currently, setUp does the store and the test cases
     * in this suite validate that the delete actually makes the object
     * not retrievable and not queriable.  (Not sure if this is the best
     * way to structure this test.)
     */ 
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
        doStoreSetupNow();    
        setThrowAPIExceptions(false);
    }

    private void doStoreSetupNow() throws HoneycombTestException {
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        TestCase self = createTestCase("setupDeleteSimple", "size=" +getFilesize());
        self.addTag(Tag.REGRESSION);
        self.addTag(Tag.POSITIVE);
        self.addTag(Tag.QUICK);
        self.addTag(Tag.SMOKE);
        self.addTag(HoneycombTag.EMULATOR);
        self.addTag(HoneycombTag.DELETE);
        self.addTag(HoneycombTag.JAVA_API);
        if (self.excludeCase()) 
            return;

        md_value = RandomUtil.getRandomString(16);
        q = "\"" + HoneycombTestConstants.MD_VALID_FIELD + "\"='" + md_value + "'";
        hm = new HashMap();
        hm.put(HoneycombTestConstants.MD_VALID_FIELD, md_value);

        hm2 = new HashMap();
        md_value2 = RandomUtil.getRandomString(16);
        q2 = "\"" + HoneycombTestConstants.MD_VALID_FIELD + "\"='" + md_value2 + "'";
        hm2.put(HoneycombTestConstants.MD_VALID_FIELD, md_value2);

        // This must happen first so it is done in setup().
        // If the stores fail, we abort the test case.
        storeResult1 = store(getFilesize(), hm);
        Log.INFO("store1 returned " + storeResult1.mdoid);
        storeResult2 = store(getFilesize(), hm);
        Log.INFO("store2 returned " + storeResult2.mdoid);
        storeResult3 = store(getFilesize(), hm);
        Log.INFO("store3 returned " + storeResult3.mdoid);
        storeResult4 = store(getFilesize(), hm);
        Log.INFO("store4 returned " + storeResult4.mdoid);
        storeResult5 = store(getFilesize(), hm);
        Log.INFO("store5 returned " + storeResult5.mdoid);
        storeResult6 = store(getFilesize(), hm);
        Log.INFO("store6 returned " + storeResult6.mdoid);
        storeResult7 = store(getFilesize(), hm);
        Log.INFO("store7 returned " + storeResult7.mdoid);
        storeResult8 = store(getFilesize(), hm);
        Log.INFO("store8 returned " + storeResult8.mdoid);

        /*
         * For now, don't do these queries.  They take a long time when the
         * cluster is full and they might have undesired caching interaction
         * that should be tested separately.
         *
        if (!queryAndCheckOid(storeResult1.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult1.mdoid +
                " did not show up in query " + q + " after store");
        } else {
            Log.INFO("found1 " + storeResult1.mdoid + " in query " + q);
        }

        if (!queryAndCheckOid(storeResult2.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult2.mdoid +
                " did not show up in query " + q + " after store");
        } else {
            Log.INFO("found2 " + storeResult2.mdoid + " in query " + q);
        }

        if (!queryAndCheckOid(storeResult3.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult3.mdoid +
                " did not show up in query " + q + " after store");
        } else {
            Log.INFO("found3 " + storeResult3.mdoid + " in query " + q);
        }

        if (!queryAndCheckOid(storeResult4.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult4.mdoid +
                " did not show up in query " + q + " after store");
        } else {
            Log.INFO("found4 " + storeResult4.mdoid + " in query " + q);
        }
         */

        storeResult4a = addMetadata(storeResult4.mdoid, hm2);
        Log.INFO("addMetadata4 returned " + storeResult4a.mdoid);

        /*
        if (!queryAndCheckOid(storeResult4a.mdoid, q2)) {
            throw new HoneycombTestException("OID " + storeResult4a.mdoid +
                " did not show up in query " + q2 + " after store");
        } else {
            Log.INFO("found4a " + storeResult4a.mdoid + " in query " + q2);
        }

        if (!queryAndCheckOid(storeResult5.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult5.mdoid +
                " did not show up in query " + q + " after store");
        } else {
            Log.INFO("found5 " + storeResult5.mdoid + " in query " + q);
        }
         */

        storeResult5a = addMetadata(storeResult5.mdoid, hm2);
        Log.INFO("addMetadata5 returned " + storeResult5a.mdoid);

        /*
        if (!queryAndCheckOid(storeResult5a.mdoid, q2)) {
            throw new HoneycombTestException("OID " + storeResult5a.mdoid +
                " did not show up in query " + q2 + " after store");
        } else {
            Log.INFO("found5a " + storeResult5a.mdoid + " in query " + q2);
        }

        if (!queryAndCheckOid(storeResult6.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult6.mdoid +
                " did not show up in query " + q + " after store");
        } else {
            Log.INFO("found6 " + storeResult6.mdoid + " in query " + q);
        }
         */

        storeResult6a = addMetadata(storeResult6.mdoid, hm2);
        Log.INFO("addMetadata6 returned " + storeResult6a.mdoid);

        /*
        if (!queryAndCheckOid(storeResult6a.mdoid, q2)) {
            throw new HoneycombTestException("OID " + storeResult6a.mdoid +
                " did not show up in query " + q2 + " after store");
        } else {
            Log.INFO("found6a " + storeResult6a.mdoid + " in query " + q2);
        }

        if (!queryAndCheckOid(storeResult7.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult7.mdoid +
                " did not show up in query " + q + " after store");
        } else {
            Log.INFO("found7 " + storeResult7.mdoid + " in query " + q);
        }
         */

        storeResult7a = addMetadata(storeResult7.mdoid, hm2);
        Log.INFO("addMetadata7 returned " + storeResult7a.mdoid);

        /*
        if (!queryAndCheckOid(storeResult7a.mdoid, q2)) {
            throw new HoneycombTestException("OID " + storeResult7a.mdoid +
                " did not show up in query " + q2 + " after store");
        } else {
            Log.INFO("found7a " + storeResult7a.mdoid + " in query " + q2);
        }

        Log.INFO("OIDs showed up in query results as expected");    
         */

        setupOK = true;
        self.testPassed("Stored objects in setup");
        //self.testPassed("Stored and queried in setup");
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }


    /**
     *  Basic case - delete then retrieve.
     */
    public boolean test1_DeleteAndRetrieve() throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK)
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase())
            return false;

        return deleteAndRetrieve(storeResult1.mdoid, "DeleteThenRetrieve");
    }

    /**
     * Verify that query fails after delete.
     */
    public boolean test2_DeleteAndQuery() throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        CmdResult cr = delete(storeResult2.mdoid);
        if (!cr.pass) {
            cr.logExceptions("delete");
            return false;
        } else {
            Log.INFO("deleted " + storeResult2.mdoid);
        }

        // Verify query fails
        return (!queryAndCheckOid(storeResult2.mdoid, q));
    }

    /**
     * Verify that delete fails after delete.
     */
    public boolean test3_DeleteAndDelete() throws HoneycombTestException {
        // Verify retrieve fails
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        CmdResult cr = delete(storeResult3.mdoid);
        if (!cr.pass) {
            cr.logExceptions("delete of " + storeResult3.mdoid);
            return false;
        } else {
            Log.INFO("deleted " + storeResult3.mdoid);
        }

        cr = delete(storeResult3.mdoid);
        if (cr.pass) {
            Log.ERROR("Delete succeeded after delete");
            return (false);
        }
        if (cr.thrown == null) {
            Log.ERROR("No delete exception [?]");
            return (false);
        }

        if (cr.checkExceptions(NoSuchObjectException.class, "delete/delete")) {
            addBug("6187583", "if object has been deleted, we shouldn't " +
                    "mention its deleted status");
           return (false);
        }

        // also check that we don't say improper things in the error msg
        if (!cr.checkExceptionStringContent()) {
            /* XXX
            addBug("6273390", "OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status)");
            return (false);
            */
            Log.WARN("Bad error message...ignoring until bug 6273390 is fixed");
        }

        Log.INFO("delete after delete failed correctly");

        return true;
    }

    /**
     *  Test deletion of added metadata.
     */
    public boolean test4_DeleteAddedMDOid() throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting addmd oid " + storeResult4a.mdoid);
        if (!deleteAndRetrieve(storeResult4a.mdoid, "deleteMD"))
            return false;
        Log.INFO("deleted added md oid ok");

        // should be able to retrieve the original oid
        CmdResult cr = retrieve(storeResult4.mdoid);
        if (cr.pass) {
            Log.INFO("Retrieved orig oid ok after delete of added oid");
        } else {
            cr.logExceptions("retrieve of " + storeResult4.mdoid);
            Log.ERROR("Retrieve original oid failed after delete of addmd oid");
            return false;
        }

        Log.INFO("Deleting/retrieving original oid too " + storeResult4.mdoid);
        if (!deleteAndRetrieve(storeResult4.mdoid, "mdOID"))
            return false;

        // Also verify that retrieveMetadata also fails for both deleted MD objs
        Log.INFO("Calling RetrieveMetadata on " + storeResult4.mdoid);
        cr = getMetadata(storeResult4.mdoid);
        if (cr.pass) {
            Log.ERROR("RetrieveMetadata worked on deleted oid " + 
                storeResult4.mdoid);
            return false;
        }

        if (cr.checkExceptions(NoSuchObjectException.class, "delete/getMD")) {
            addBug("6187583", "if object has been deleted, we shouldn't " +
                    "mention its deleted status");
           return (false);
        }

        // also check that we don't say improper things in the error msg
        if (!cr.checkExceptionStringContent()) {
            /* XXX
            addBug("6273390", "OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status)");
            return (false);
            */
            Log.WARN("Bad error message...ignoring until bug 6273390 is fixed");
        }

        Log.INFO("RetrieveMetadata failed as expected after delete");


        // Also verify that retrieveMetadata also fails for both deleted MD objs
        Log.INFO("Calling RetrieveMetadata on " + storeResult4a.mdoid);
        cr = getMetadata(storeResult4a.mdoid);
        if (cr.pass) {
            Log.ERROR("RetrieveMetadata worked on deleted oid " + 
                storeResult4a.mdoid);
            return false;
        }

        if (cr.checkExceptions(NoSuchObjectException.class, "delete/getMD")) {
            addBug("6187583", "if object has been deleted, we shouldn't " +
                    "mention its deleted status");
           return (false);
        }

        // also check that we don't say improper things in the error msg
        if (!cr.checkExceptionStringContent()) {
            /* XXX
            addBug("6273390", "OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status)");
            return (false);
            */
            Log.WARN("Bad error message...ignoring until bug 6273390 is fixed");
        }

        Log.INFO("RetrieveMetadata failed as expected after delete");

        return true;
    }

    /**
     *  Delete original oid and retrieve via added one.
     */
    public boolean test5_DeleteThenRetrieveAddedMDOid() 
                                                throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.RETRIEVEDATA);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting original oid " + storeResult5.mdoid);
        if (!deleteAndRetrieve(storeResult5.mdoid, "deleteOrigOid"))
            return false;
        Log.INFO("Deleted original oid ok");

        // should be able to retrieve the addmd oid
        Log.INFO("Retrieving addmd oid " + storeResult5a.mdoid);
        CmdResult cr = retrieve(storeResult5a.mdoid);
        if (cr.pass) {
            Log.INFO("Retrieve added oid succeeded after delete of orig oid");
        } else {
            cr.logExceptions("retrieve of " + storeResult5a.mdoid);
            Log.ERROR("Retrieve of added oid failed after delete of orig");
            return false;
        }

        // delete addmd oid
        Log.INFO("Deleting addmd oid " + storeResult5a.mdoid);
        if (!deleteAndRetrieve(storeResult5a.mdoid, "addMD"))
            return false;

        return true;
    }

    /**
     *  Test deletion of added metadata like test4, but leaving 
     *  original oid for later audit to verify.
     */
    public boolean test6_DeleteAddedMDOid() throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.RETRIEVEDATA);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting addmd oid " + storeResult6a.mdoid);
        if (!deleteAndRetrieve(storeResult6a.mdoid, "deleteMD"))
            return false;
        Log.INFO("deleted added md oid ok");

        // should be able to retrieve the original oid
        CmdResult cr = retrieve(storeResult6.mdoid);
        if (cr.pass) {
            Log.INFO("Retrieved orig oid ok after delete of added oid");
        } else {
            cr.logExceptions("retrieve of " + storeResult6.mdoid);
            Log.ERROR("Retrieve original oid failed after delete of addmd oid");
            return false;
        }

        return true;
    }

    /**
     *  Delete original oid and retrieve via added one, like 
     *  test5, but leaving added oid for later audit to verify..
     */
    public boolean test7_DeleteThenRetrieveAddedMDOid() 
                                                throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.RETRIEVEDATA);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting original oid " + storeResult7.mdoid);
        if (!deleteAndRetrieve(storeResult7.mdoid, "deleteOrigOid"))
            return false;
        Log.INFO("Deleted original oid ok");

        // should be able to retrieve the addmd oid
        Log.INFO("Retrieving addmd oid " + storeResult7a.mdoid);
        CmdResult cr = retrieve(storeResult7a.mdoid);
        if (cr.pass) {
            Log.INFO("Retrieve added oid succeeded after delete of orig oid");
        } else {
            cr.logExceptions("retrieve of " + storeResult7a.mdoid);
            Log.ERROR("Retrieve of added oid failed after delete of orig");
            return false;
        }

        return true;
    }
    
    /**
     *  Delete original oid and try to addMD.  Verify correct error.
     */
    public boolean test8_DeleteThenAddMD() 
                                                throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting original oid " + storeResult8.mdoid);
        if (!deleteAndRetrieve(storeResult8.mdoid, "deleteOrigOid"))
            return false;
        Log.INFO("Deleted original oid ok");

        storeResult8a = addMetadata(storeResult8.mdoid, hm2);
        if (storeResult8a.pass) {
            Log.ERROR("Added MD to deleted object " + storeResult8.mdoid);
            return false;
        }

        if (!storeResult8a.checkExceptionStringContent()) {
            /*
            addBug("6273390", "OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status)");
            return false;
            */
            Log.WARN("Bad error message...ignoring until bug 6273390 is fixed");
        }

        return true;
    }

    /**
     *  Verify we do the right thing if we pass an OID that doesn't exist (but
     *  is well formed).  Other api testing will validate malformed oids.
     */
    public boolean test9_InvalidOID() 
                                                throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.EMULATOR);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.JAVA_API);
        if (!setupOK) {
            addTag(Tag.MISSINGDEP, "failed some dependency");
        } else {
            addTag(Tag.MISSINGDEP, "Blocking: " +
                "6273390: OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status) and 6328306 if malformed OID is passed, " +
                "should not get IOException or ArrayIndexOutOfBounds");
        }
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting invalid oid " + invalidOid);
        CmdResult cr = delete(invalidOid);
        if (cr.pass) {
            Log.ERROR("delete on an invalid oid passed " + invalidOid);
            return (false);
        }

        if (cr.checkExceptions(NoSuchObjectException.class,
            "delete invalid oid")) {
            /*
            addBug("6273390", "OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status)");
           return (false);
                    */
            Log.WARN("Bad excpn...ignoring until bug 6273390 is fixed");
        }

        if (!cr.checkExceptionStringContent()) {
            /*
            addBug("6273390", "OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status)");
            return false;
            */
            Log.WARN("Bad error message...ignoring until bug 6273390 is fixed");
        }

        return true;
    }
}
