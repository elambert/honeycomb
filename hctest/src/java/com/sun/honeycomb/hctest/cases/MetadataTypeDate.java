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

import java.sql.Date;

/**
 * Validate that metadata functions if you use the type date.
 */
public class MetadataTypeDate extends HoneycombLocalSuite {

    private ArrayList dateMDValues = null;

    public MetadataTypeDate() {
        super();
    }

    public String help() {
        return(
            "\tValidate that metadata functions if you use the type date.\n" +
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
    public class DateTest {
        public Date d1;
        public String testName = null;
        public CmdResult storeResult;

        DateTest(Date date1) {
            d1 = date1;
            testName = d1.toString();
        }

        public String getTestParamString() {
            return ("case=" + testName + " d1=" + d1);
        }
    }

    /**
     * Store objects with metadata that has type date.  
     */
    public void testAStoreWithDateMetadata() throws HoneycombTestException {
        dateMDValues = new ArrayList();

        dateMDValues.add(new DateTest(Date.valueOf("1999-12-31")));
        dateMDValues.add(new DateTest(Date.valueOf("2000-01-01")));
        dateMDValues.add(new DateTest(Date.valueOf("0000-01-01")));
        dateMDValues.add(new DateTest(Date.valueOf("2999-12-31")));
        dateMDValues.add(new DateTest(Date.valueOf("3000-01-01")));
        dateMDValues.add(new DateTest(Date.valueOf("1969-12-31")));
        dateMDValues.add(new DateTest(Date.valueOf("1970-01-01")));

        // Iterate through all the dates and store them
        HashMap hm = new HashMap();
        ListIterator i = dateMDValues.listIterator();
        while (i.hasNext()) {
            DateTest dt = (DateTest) i.next();

            TestCase self = createTestCase("StoreWithDateMetadata",
                                           dt.getTestParamString());
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

            hm.put(HoneycombTestConstants.MD_DATE_FIELD1, dt.d1);

            try {
                dt.storeResult = store(getFilesize(), hm);
                self.testPassed(); 
            } catch (Throwable t) {
                self.testFailed("Failed to store file: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    /**
     * Verify we can query for the objects that have metadata of type date.
     */
    public void testCQueryAfterStoreWithDateMetadata() throws Throwable {
        // Iterate through all the dates and query for them
        ListIterator i = dateMDValues.listIterator();
        while (i.hasNext()) {
            DateTest dt = (DateTest) i.next();

            TestCase self = createTestCase("QueryAfterStoreWithDateMetadata", 
                                           dt.getTestParamString());
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
            String query = HoneycombTestConstants.MD_DATE_FIELD1 + 
                "= '" + dt.d1.toString() + "'";
            try {
                CmdResult cr = query(query);
                QueryResultSet qrs = (QueryResultSet) cr.rs;

                boolean found = false;
                while (qrs.next()) {
                    ObjectIdentifier oid = qrs.getObjectIdentifier();
                    if ((dt.storeResult.mdoid).equals(oid.toString())) {
                        self.testPassed("Found oid " + oid +
                                        " in query results");
                        found = true;
                    }
                }

                if (!found) {
                    self.testFailed("We didn't find our expected oid " +
                                    dt.storeResult.mdoid + " for query " 
                                    + query);
                }
            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage());
            }
        }
    }
}
