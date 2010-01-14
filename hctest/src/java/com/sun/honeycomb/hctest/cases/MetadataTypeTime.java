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
import java.util.ListIterator;
import java.io.File;

import java.sql.Time;

/**
 * Validate that metadata functions if you use the type time.
 */
public class MetadataTypeTime extends HoneycombLocalSuite {

    private ArrayList timeMDValues = null;

    public MetadataTypeTime() {
        super();
    }

    public String help() {
        return(
            "\tValidate that metadata functions if you use the type time.\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n"
            );
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }


    /**
     * Convenience class for the Metadata double tests.
     */
    public class TimeTest {
        public Time t1;
        public String testName = null;
        public CmdResult storeResult;

        TimeTest(Time time1) {
            t1 = time1;
            testName = t1.toString();
        }

        public String getTestParamString() {
            return ("case=" + testName + " t1=" + t1);
        }
    }

    /**
     * Store objects with metadata that has type time.  
     */
    public void testAStoreWithTimeMetadata() throws HoneycombTestException {
        timeMDValues = new ArrayList();

        timeMDValues.add(new TimeTest(Time.valueOf("00:00:00")));
        timeMDValues.add(new TimeTest(Time.valueOf("00:00:01")));
        timeMDValues.add(new TimeTest(Time.valueOf("11:59:59")));
        timeMDValues.add(new TimeTest(Time.valueOf("12:00:00")));
        timeMDValues.add(new TimeTest(Time.valueOf("12:00:01")));
        timeMDValues.add(new TimeTest(Time.valueOf("23:59:59")));
        timeMDValues.add(new TimeTest(Time.valueOf("23:59:60")));

        // Iterate through all the times and store them
        HashMap hm = new HashMap();
        ListIterator i = timeMDValues.listIterator();
        while (i.hasNext()) {
            TimeTest tt = (TimeTest) i.next();

            TestCase self = createTestCase("StoreWithTimeMetadata",
                                           tt.getTestParamString());
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.STOREMETADATA);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);

            if (self.excludeCase()) {
                continue;
            }

            hm.put(HoneycombTestConstants.MD_TIME_FIELD1, tt.t1);

            try {
                tt.storeResult = store(getFilesize(), hm);
                self.testPassed(); 
            } catch (Throwable t) {
                self.testFailed("Failed to store file: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    /**
     * Verify we can query for the objects that have metadata of type time.
     */
    public void testCQueryAfterStoreWithTimeMetadata() throws Throwable {
        // Iterate through all the times and query for them
        ListIterator i = timeMDValues.listIterator();
        while (i.hasNext()) {
            TimeTest tt = (TimeTest) i.next();

            TestCase self = createTestCase("QueryAfterStoreWithTimeMetadata", 
                                           tt.getTestParamString());
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);
            if (self.excludeCase()) {
                continue;
            }
            String query = HoneycombTestConstants.MD_TIME_FIELD1 + 
                "= '" + tt.t1.toString() + "'";
            try {
                CmdResult cr = query(query);
                QueryResultSet qrs = (QueryResultSet) cr.rs;

                boolean found = false;
                while (qrs.next()) {
                    ObjectIdentifier oid = qrs.getObjectIdentifier();
                    if ((tt.storeResult.mdoid).equals(oid.toString())) {
                        self.testPassed("Found oid " + oid +
                                        " in query results");
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    self.testFailed("We didn't find our expected oid " +
                                    tt.storeResult.mdoid + " for query " 
                                    + query);
                }
            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage());
            }
        }
    }
}
