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
import java.util.ArrayList;

/**
 * Validate that a basic delete works if you add and remove a lot of MD.
 */
public class DeleteMetadata extends DeleteSuite {

    boolean setupOK = false;

    // Default property values
    private int howManyObjs = 10;
    public boolean strictChecking = true;

    private HashMap smallMD;
    private HashMap emptyMD;
    private HashMap bigMD;
    private String md_value;
    private String md_value2;
    private String q;
    private String q2;

    public DeleteMetadata() {
        super();
    }

    public String help() {
        return(
            "\tAdd MD for an object, delete the new md, do it again\n" +
            "\tand test that the object disappears when expected.\n" +
            "\tOptional -ctx args:\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                "=n (n is how many objects to add per iteration)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_STRICT +
                " (do strict checking; enabled by default now)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_RMI +
                "=yes (will do additional validation)\n"
            );
    }

    /**
     * Currently, setUp does the initialization of the shared
     * variables.  Actually stores, etc, are done in the cases.
     */ 
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
        setThrowAPIExceptions(false);

        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        md_value = RandomUtil.getRandomString(16);
        q = "\"" + HoneycombTestConstants.MD_VALID_FIELD + "\"='" + md_value + "'";
        smallMD = new HashMap();
        smallMD.put(HoneycombTestConstants.MD_VALID_FIELD, md_value);

        emptyMD = new HashMap();

        // XXX must populate this
        bigMD = new HashMap();

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

        /*
         * Commenting this out...bug is fixed, don't need to be non-strict.
        s = getProperty(HoneycombTestConstants.PROPERTY_STRICT);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_STRICT +
                " was specified. Using strict checking");
            strictChecking = true;
        } else {
            Log.INFO("Not using strict checking.  Pass the flag " +
                HoneycombTestConstants.PROPERTY_STRICT +
                " to enable strict checking");
        }
         */

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
     * store a lot of MD objs and then delete them.
     */
    public boolean test1_manyMetadataObjects() throws HoneycombTestException {
        //addTag(Tag.REGRESSION);
        //addTag(Tag.QUICK);
        // XXX
        addTag(Tag.POSITIVE);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.JAVA_API);
        //addTag(Tag.SMOKE);
        if (strictChecking) {
            addBug("6281934",
            "deleting a metadata object doesn't always decrement the refcnt " +
            "on data obj (data oid retrieve works); bloom filter hash " +
            "collisions cause data objects to not be deleted when expected");
        }
        if (excludeCase())
            return false;

        CmdResult cr;
        int count = 0;
        ArrayList oids = new ArrayList();

        cr = store(getFilesize(), smallMD);
        if (!cr.pass) {
            cr.logExceptions("store");
            return false;
        }
        String oidOrig = cr.mdoid;
        String dataoid = cr.dataoid;
        Log.INFO("store1 returned " + oidOrig +
            " (dataoid " + dataoid + ")");

        while (count < howManyObjs) {
            cr = addMetadata(oidOrig, smallMD);
            if (!cr.pass) {
                cr.logExceptions("addMetadata to " + oidOrig);
                return false;
            }
            String oid = cr.mdoid;
            Log.INFO("addMetadata " + ++count + " returned " + oid);
            oids.add(oid);
        }

        // Delete all but the orig obj
        ListIterator li = oids.listIterator();
        count = 0;
        while (li.hasNext()) {
            String oid = (String) li.next();
            cr = delete(oid);
            if (!cr.pass) {
                cr.logExceptions("delete of " + oid);
                return false;
            } else {
                Log.INFO("delete " + ++count + " succeeded on oid " + oid);
            }
        }

        // Add some more MD
        ArrayList moreoids = new ArrayList();
        count = 0;
        // add our original oid to the oid array
        moreoids.add(oidOrig);
        while (count < howManyObjs) {
            cr = addMetadata(oidOrig, smallMD);
            if (!cr.pass) {
                cr.logExceptions("addMetadata to " + oidOrig);
                return false;
            }
            String oid = cr.mdoid;
            Log.INFO("2nd round addMetadata " + ++count + " returned " + oid);
            moreoids.add(oid);
        }

        // Delete all including the orig obj
        li = moreoids.listIterator();
        count = 0;
        while (li.hasNext()) {
            String oid = (String) li.next();
            cr = delete(oid);
            if (!cr.pass) {
                cr.logExceptions("delete of " + oid);
                return false;
            } else {
                Log.INFO("2nd round delete " + ++count +
                    " succeeded on oid " + oid);
            }
        }

        // Verify retrieve doesn't work on all oids we've seen
        ArrayList allOids = new ArrayList();
        allOids.addAll(oids);
        allOids.addAll(moreoids);
        li = allOids.listIterator();
        count = 0;
        while (li.hasNext()) {
            String oid = (String) li.next();
            cr = retrieve(oid);
            if (cr.pass) {
                Log.ERROR("retrieve succeeded using deleted oid " + oid);
                return false;
            } else {
                // XXX correct exception?
                Log.INFO("retrieve " + ++count +
                    " failed as expected on oid " + oid);
            }

            if (testBed.rmiEnabled()) {
                // Verify files on-disk are deleted
                if (!onDiskFragsLookDeleted(oid)) {
                    Log.ERROR("frags don't look deleted as expected on disk");
                    return false;
                } else {
                    Log.INFO("files look deleted on disk as expected for " +
                        oid);
                }
            }
        }

        // also verify on-disk frags for data oid
        if (testBed.rmiEnabled()) {
            if (!onDiskFragsLookDeleted(dataoid)) {
                Log.ERROR("frags don't look deleted as expected on disk");
                return false;
            } else {
                Log.INFO("files look deleted on disk as expected for " +
                    dataoid);
            }
        }
 
        // exit test early if we aren't doing strict checking.  This is mainly
        // to work around bug 6281934 "bloom filter hash collisions cause data
        // objects to not be deleted when expected"
        if (!strictChecking) {
            Log.INFO("strict checking not enabled; skipping validation that " +
                "data object isn't retrieveable");
            return (true);
        }

        // Verify that the data object isn't retrievable
        cr = retrieveDataObject(dataoid);
        if (!cr.pass) {
            Log.INFO("retrieve data oid failed as expected for " + dataoid);
        } else {
            Log.ERROR("retrieve data oid succeeded when it shouldn't have for "
                + dataoid);
            return (false);
        }

        return (true);
    }
}
