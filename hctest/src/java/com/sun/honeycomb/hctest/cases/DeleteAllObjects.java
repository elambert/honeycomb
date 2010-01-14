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
import java.util.ListIterator;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Query the system for all objects and call delete.
 */
public class DeleteAllObjects extends DeleteSuite {

    boolean setupOK = false;

    // size of the cookie
    private int howManyObjs =
        HoneycombTestConstants.DEFAULT_MAX_RESULTS_CONSERVATIVE;

    private boolean testMode = false; // for debugging the test

    // Allow for some skew between client and server
    public static final long TIME_SKEW_ALLOWED = 10000; // 10 secs

    public DeleteAllObjects() {
        super();
    }

    public String help() {
        return(
            "\tQuery the system for all objects and call delete\n" +
            "\tOptional -ctx args:\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                "=n (n is the value of MaxRes in query)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_TEST_MODE +
                " (modify the query to get only explicit test objects)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_RMI +
                "=yes (will do additional validation)\n"
            );
    }

    /**
     * Currently, setUp does the initialization of the shared
     * variables.
     */ 
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
        setThrowAPIExceptions(false);

        String s = getProperty(HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
        if (s != null) {
            Log.INFO("Property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                " was specified. Using " + s + " " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
            howManyObjs = Integer.parseInt(s);
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                " of " + howManyObjs);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_TEST_MODE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_TEST_MODE +
                " was specified.");
            testMode = true;
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

    /**
     * Query for all objs and delete them.
     */
    public boolean testDeleteAllObjects() throws Throwable {
        //addTag(Tag.REGRESSION);
        //addTag(Tag.QUICK);
        addTag(Tag.NORUN,
            "most people don't want to delete all objects on their cluster");
        addTag(Tag.POSITIVE);

        // XXX temporary remove this test from delete tests due to bug
        // 6279785, which doesn't exclude norun tests
        //addTag(HoneycombTag.DELETE);

        //addTag(Tag.SMOKE);
        if (excludeCase())
            return false;

        // object_id != null
        String q = HoneycombTestConstants.MD_OBJECT_ID + " is not null";

        // for unit testing this script, can use this query instead
        if (testMode) {
            q = "filename='testDeleteAllObjects'";
        }
        
        CmdResult cr;
        int count = 0;
        ArrayList oids = new ArrayList();
        HashSet nonDeletedSet = new HashSet();
        boolean done = false;
        long previousOriginalQueryTime = 0;
        long originalQueryTime = 0;
        long totalNumObjects = 0;
        long loopNumber = 0;
        boolean success = true;

        // Loop through trying to delete everything
        while (!done) {
            loopNumber++;
            previousOriginalQueryTime = originalQueryTime;
            originalQueryTime = System.currentTimeMillis();
            Log.INFO("Loop " + loopNumber + " query started at time " +
                originalQueryTime);

            Log.INFO("running query " + q + " with maxObjs " + howManyObjs);
            cr = query(q, howManyObjs);
            if (!cr.pass) {
                cr.logExceptions("query");
                return false;
            }
            Log.INFO("query returned; iterating through results");

            QueryResultSet qrs = (QueryResultSet) cr.rs;
            long numObjects = 0;

            while (qrs.next()) {
                ObjectIdentifier result = qrs.getObjectIdentifier();
                String oid = result.toString();
                boolean thisDeleteFailed = false;

                // If we delete all objects, it should be the case that 
                // all data objects referenced by the objects also get deleted.
                // We keep track of data objects that aren't deleted and verify
                // at the end of the test that they are deleted.
                cr = getMetadata(oid);
                if (!cr.pass) {
                    cr.logExceptions("getMetadata of " + oid);
                    // It is possible that another thread beat us to it, so if
                    // the error is NoSuchObject, let it go and skip to next
                    // object
                    if (cr.checkExceptions(
                        com.sun.honeycomb.common.NoSuchObjectException.class,
                        "getMetadata")) {
                        Log.ERROR("XXX Argh! Unexpected exception, probably " +
                            " due to API flakiness...will skip for now...");
                        // XXX return (false);
                    }
                    Log.INFO("We got back a NoSuchObjectException so another " +
                        "thread might have beaten us to the delete.  Skipping" +
                        " to next object");
                    continue;
                }

                SystemRecord sr = cr.sr;
                // XXX hack -- data oid can't be gotten per bug 6287089
                ObjectIdentifier resultDataOid = sr.getObjectIdentifier();
                //ObjectIdentifier resultDataOid = sr.getLinkIdentifier();
                String dataoid = resultDataOid.toString();

                // XXX hack to work around the above if we have RMI
                // try to get oidinfo
                if (testBed.rmiEnabled()) {
                    dataoid = findDataOid(oid);
                    Log.DEBUG("RMI datatoid is " + dataoid);
                    if (dataoid == null) {
                        // Ah, well, we still failed.  Just use the mdoid and do
                        // the check twice.  This will get better once the bug
                        // is fixed and we don't have to hack.
                        dataoid = oid;
                    }
                }

                // should only be newly stored objects in the second query
                if (loopNumber > 1) {
                    long createTime = sr.getCreationTime();

                    // An object can be added during the previous query.
                    // If its oid is alphabetically earlier than the current
                    // result being processed, it won't be processed until
                    // the next query.
                    long timeDiff = previousOriginalQueryTime - createTime;
                    if (timeDiff > TIME_SKEW_ALLOWED) {
                        Log.ERROR("Found object " + oid + " in loop " +
                            loopNumber + " that was created at time " +
                            createTime +
                            " but our original query was done at time " +
                            originalQueryTime + ".  That's a difference of " +
                            timeDiff + " msecs; we've only allowed a diff of " +
                            TIME_SKEW_ALLOWED + ".  Validate ntp settings.");
                        return (false);
                    } else {
                        Log.INFO(oid + " was stored at time " + createTime);
                    }
                }

                Log.INFO("Calling delete on " + oid + " (data oid " + dataoid +
                    "), object number " +
                    ++numObjects + " of loop " + loopNumber + " (total objs " +
                    ++totalNumObjects + ")");
                // XXX allow to fail if already deleted?  Allow for parallel
                // execution?  Validate these oids don't show up when queried
                // for again?
                cr = delete(oid);
                if (!cr.pass) {
                    cr.logExceptions("delete of " + oid);
                    Log.WARN("Maybe this is " +
                        "because a different thread beat us to the delete.");
                    thisDeleteFailed = true;
                    // XXX
                    // remember these oids?
                    // return false;
                } 

                // validate that the fragments appear deleted 
                if (testBed.rmiEnabled()) {
                    // check the MD oid is gone
                    if (!onDiskFragsLookDeleted(oid)) {
                        // XXX what does this mean?  Thread contention?
                        Log.WARN(
                            "frags don't look deleted as expected on disk; " +
                            "adding md oid " + oid +
                            " to the non-deleted set (size " +
                            nonDeletedSet.size() + "); " +
                            "XXX what does this mean for an MD oid???");
                        nonDeletedSet.add(oid);
                    } else {
                        if (thisDeleteFailed) {
                            Log.INFO(
                                "files look deleted on disk as expected for " +
                                oid + " even though delete said it failed");
                        } else {
                            Log.DEBUG(
                                "files look deleted on disk as expected for " +
                                oid);
                        }
                    }

                    // Check if the data oid is gone.  If it is not, then add 
                    // it to our list and we'll check it at the end
                    if (!onDiskFragsLookDeleted(dataoid)) {
                        nonDeletedSet.add(dataoid);
                        Log.INFO("Adding dataoid " + dataoid +
                            " to non-deleted set (size " +
                            nonDeletedSet.size() +
                            "); probably because we have " +
                            "multiple MD references to this data object"); 
                    } else {
                        Log.DEBUG("dataoid " + dataoid + " appears deleted");
                    }
                }
            }

            Log.INFO("Loop " + loopNumber + " ended after processing " +
                numObjects + ".  We have processed a total of " +
                totalNumObjects);

            if (numObjects == 0) {
                Log.INFO("Query returned no objects...must be done!");
                done = true;
            }

        }

        // Validate that by this point, all data objects we encountered
        // are in fact deleted
        if (testBed.rmiEnabled()) {
            Log.INFO("Verify all non-deleted oids we encountered " +
                "earlier are actually deleted now.");
            Iterator i = nonDeletedSet.iterator();
            while (i.hasNext()) {
                String oid = (String)i.next();
                if (!onDiskFragsLookDeleted(oid)) {
                    Log.ERROR("At end of the test, expected oid " +
                        oid + " to be deleted but it isn't.  Considering " +
                        "test a failure.");
                    success = false;
                } else {
                    Log.INFO(
                        "files look deleted on disk as expected for " +
                        oid);
                }
            }
        }

        // XXX df check filesize delete is reflected in df output
        // ... but what if stores happening at same time?


        return (success);
    }
}
