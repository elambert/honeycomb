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
 * Validate that delete of big objects works. Setup could be done
 * in parallel for speedup. Also could use the byte patterns 
 * vs. files for speedup.
 */
public class DeleteChunks extends DeleteSuite {

    boolean setupOK = false;

    private CmdResult storeResult1;
    private CmdResult storeResult2;
    private CmdResult storeResult3;
    private CmdResult storeResult4;
    private CmdResult storeResult4a;
    private CmdResult storeResult5;
    private CmdResult storeResult5a;
    private CmdResult storeResult6;
    private String md_value;
    private String md_value2;
    private String q;
    private String q2;


    public DeleteChunks() {
        super();
    }

    /**
     * Currently, setUp does the stores and the test cases
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
        TestCase self = createTestCase("setupDeleteChunks", 
                               "size=1-4 chunks");
        self.addTag(HoneycombTag.DELETE);
        self.addTag(Tag.POSITIVE);
        self.addTag(Tag.HOURLONG);
        // self.addTag(Tag.SMOKE);
        if (self.excludeCase()) 
            return;

        //
        //  make simple metadata
        //
        md_value = RandomUtil.getRandomString(16);
        q = HoneycombTestConstants.MD_VALID_FIELD + "='" + md_value + "'";
        HashMap hm = new HashMap();
        hm.put(HoneycombTestConstants.MD_VALID_FIELD, md_value);

        HashMap hm2 = new HashMap();
        md_value2 = RandomUtil.getRandomString(16);
        q2 = HoneycombTestConstants.MD_VALID_FIELD + "='" + md_value2 + "'";
        hm2.put(HoneycombTestConstants.MD_VALID_FIELD, md_value2);

        // This must happen first so it is done in setup().
        // If the stores fail, we abort the test case.

        Log.INFO("storing 1 chunk");
        storeResult1 = store(HoneycombTestConstants.OA_MAX_CHUNK_SIZE, hm);
        Log.INFO("storing 1 chunk + 1 byte");
        storeResult2 = store(HoneycombTestConstants.OA_MAX_CHUNK_SIZE+1, hm);
        Log.INFO("storing 2 chunks");
        storeResult3 = store(HoneycombTestConstants.OA_MAX_CHUNK_SIZE*2, hm);
        Log.INFO("storing 2 chunks + 1 byte");
        storeResult4 = store(HoneycombTestConstants.OA_MAX_CHUNK_SIZE*2+1, hm);
        Log.INFO("storing 3 chunks");
        storeResult5 = store(HoneycombTestConstants.OA_MAX_CHUNK_SIZE*3, hm);
        Log.INFO("storing 3 chunks + 1 byte");
        storeResult6 = store(HoneycombTestConstants.OA_MAX_CHUNK_SIZE*3+1, hm);

        if (!queryAndCheckOid(storeResult1.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult1.mdoid +
                " did not show up in query " + q + " after store");
        }
        if (!queryAndCheckOid(storeResult2.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult2.mdoid +
                " did not show up in query " + q + " after store");
        }
        if (!queryAndCheckOid(storeResult3.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult3.mdoid +
                " did not show up in query " + q + " after store");
        }
        if (!queryAndCheckOid(storeResult4.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult4.mdoid +
                " did not show up in query " + q + " after store");
        }
        storeResult4a = addMetadata(storeResult4.mdoid, hm2);
        if (!queryAndCheckOid(storeResult4a.mdoid, q2)) {
            throw new HoneycombTestException("OID " + storeResult4a.mdoid +
                " did not show up in query " + q2 + " after store");
        }
        if (!queryAndCheckOid(storeResult5.mdoid, q)) {
            throw new HoneycombTestException("OID " + storeResult5.mdoid +
                " did not show up in query " + q + " after store");
        }
        storeResult5a = addMetadata(storeResult5.mdoid, hm2);
        if (!queryAndCheckOid(storeResult5a.mdoid, q2)) {
            throw new HoneycombTestException("OID " + storeResult5a.mdoid +
                " did not show up in query " + q2 + " after store");
        }
        Log.INFO("OIDs showed up in query results as expected");    

        setupOK = true;
        self.testPassed("Stored and queried in setup");
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }


    /**
     *  Basic case - delete then retrieve.
     */
    public boolean test1_DeleteAndRetrieve() throws HoneycombTestException {
        addTag(HoneycombTag.DELETE);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
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
        addTag(HoneycombTag.DELETE);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        CmdResult cr = delete(storeResult2.mdoid);
        if (!cr.pass) {
            cr.logExceptions("delete");
            return false;
        }

        // Verify query fails
        return (!queryAndCheckOid(storeResult2.mdoid, q));
    }

    /**
     * Verify that delete fails after delete.
     */
    public boolean test3_DeleteAndDelete() throws HoneycombTestException {
        // Verify retrieve fails
        addTag(HoneycombTag.DELETE);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        CmdResult cr = delete(storeResult3.mdoid);
        if (!cr.pass) {
            cr.logExceptions("delete");
            return false;
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
        Log.INFO("delete after delete failed correctly");

        return true;
    }

    /**
     *  Test deletion of added metadata.
     */
    public boolean test4_DeleteAddedMDOid() throws HoneycombTestException {
        addTag(HoneycombTag.DELETE);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting addmd oid");
        if (!deleteAndRetrieve(storeResult4a.mdoid, "deleteMD"))
            return false;
        Log.INFO("deleted added md oid ok");

        // should be able to retrieve the original oid
        CmdResult cr = retrieve(storeResult4.mdoid);
        if (cr.pass) {
            Log.INFO("Retrieved orig oid ok after delete of added oid");
        } else {
            cr.logExceptions("retrieve");
            Log.ERROR("Retrieve original oid failed after delete of addmd oid");
            return false;
        }

        Log.INFO("Deleting/retrieving original oid too");
        if (!deleteAndRetrieve(storeResult4.mdoid, "mdOID"))
            return false;

        return true;
    }

    /**
     *  Delete original oid and retrieve via added one.
     */
    public boolean test5_DeleteThenRetrieveAddedMDOid() 
                                                throws HoneycombTestException {
        addTag(HoneycombTag.DELETE);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        if (!setupOK) 
            addTag(Tag.MISSINGDEP, "failed some dependency");
        if (excludeCase()) 
            return false;

        Log.INFO("Deleting original oid");
        if (!deleteAndRetrieve(storeResult5.mdoid, "deleteOrigOid"))
            return false;
        Log.INFO("Deleted original oid ok");

        // should be able to retrieve the addmd oid
        Log.INFO("Retrieving addmd oid");
        CmdResult cr = retrieve(storeResult5a.mdoid);
        if (cr.pass) {
            Log.INFO("Retrieve added oid succeeded after delete of orig oid");
        } else {
            cr.logExceptions("retrieve");
            Log.ERROR("Retrieve of added oid failed after delete of orig");
            return false;
        }

        // delete addmd oid
        Log.INFO("Deleting addmd oid");
        if (!deleteAndRetrieve(storeResult5a.mdoid, "addMD"))
            return false;

        return true;
    }
}
