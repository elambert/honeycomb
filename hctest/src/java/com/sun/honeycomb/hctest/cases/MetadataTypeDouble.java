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

/**
 * Validate that metadata functions if you use the type double.
 */
public class MetadataTypeDouble extends HoneycombLocalSuite {

    private ArrayList doubleMDValues = null;
    private String filenameRetrieve;

    public MetadataTypeDouble() {
        super();
    }

    public String help() {
        return(
            "\tValidate that metadata functions if you use the type double.\n" +
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
    public class DoublePair {
        public double d1;
        public double d2;
        public String d1string = null;
        public String d2string = null;
        public String testName = null;
        public CmdResult storeResult;

        DoublePair(double double1, double double2, String name) {
            d1 = double1;
            d2 = double2;
            d1string = d1 + "";
            d2string = d2 + "";
            testName = name;
        }

        DoublePair(String double1,double double2, String name) {
            d1 = Double.parseDouble(double1);
            d2 = double2;
            d1string = double1;
            d2string = d2 + "";
            testName = name;
        }

        public DoublePair negate() {
            return new DoublePair(-d1, -d2, testName + "Negative");
        }

        public String getTestParamString() {
            return ("case=" + testName + " d1=" + d1 + " d2=" + d2);
        }
    }

    //DoubleMetadataMorePrecise,AfterStoreWithDoubleMetadata

    /**
     * Store objects with metadata that has type double.  We validate
     * different precisions and negative values.
     */
    public void testAStoreWithDoubleMetadata() throws HoneycombTestException {
        doubleMDValues = new ArrayList();

        DoublePair testCaseDouble = new DoublePair(
				RandomUtil.getLessPreciseRandomDouble(),
				RandomUtil.getLessPreciseRandomDouble(),
                                "testLessPrecise"); 
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());

        testCaseDouble = new DoublePair(
				RandomUtil.getDouble(), 
				RandomUtil.getDouble(),
                                "testRandomDouble");
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());


        testCaseDouble = new DoublePair(0.1, 1/3, "testI");
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());

        testCaseDouble = new DoublePair("211.2e3", 21.12, "testII");
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());

        testCaseDouble = new DoublePair("122.1E3", 12.21, "testIII");
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());

        testCaseDouble = new DoublePair("33.3d", 3.3, "testIV");
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());

        testCaseDouble = new DoublePair("66.6D", 6.6, "testV");
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());

        testCaseDouble = new DoublePair("3e3d", 1.1, "testVI");
        doubleMDValues.add(testCaseDouble);
        doubleMDValues.add(testCaseDouble.negate());

        // Iterate through all the pairs of doubles and store them
        HashMap hm = new HashMap();
        ListIterator i = doubleMDValues.listIterator();
        while (i.hasNext()) {
            DoublePair dp = (DoublePair)i.next();

            TestCase self = createTestCase("StoreWithDoubleMetadata",
                                           dp.getTestParamString());
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

            hm.put(HoneycombTestConstants.MD_DOUBLE_FIELD1, new Double(dp.d1));
            hm.put(HoneycombTestConstants.MD_DOUBLE_FIELD2, new Double(dp.d2));

            try {
                dp.storeResult = store(getFilesize(), hm);
                self.testPassed(); 
            } catch (Throwable t) {
                self.testFailed("Failed to store file: " + t.getMessage());
		t.printStackTrace();
            }
        }
    }

    /**
     * Verify we can query for the objects that have metadata of type double.
     */
    public void testCQueryAfterStoreWithDoubleMetadata() throws Throwable {
        // Iterate through all the pairs of doubles and query for them
        ListIterator i = doubleMDValues.listIterator();
        while (i.hasNext()) {

            DoublePair dp = (DoublePair)i.next();

            TestCase self = createTestCase("QueryAfterStoreWithDoubleMetadata", 
                                           dp.getTestParamString());
            if (dp.storeResult == null) 
                addTag(Tag.MISSINGDEP, "Store missing, so skip query");;

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
           
            String query = HoneycombTestConstants.MD_DOUBLE_FIELD1 + 
                "=" + dp.d1string;
            try {
	        CmdResult cr = query(query);
                QueryResultSet qrs = (QueryResultSet) cr.rs;

                boolean found = false;
                while (qrs.next()) {
                    ObjectIdentifier oid = qrs.getObjectIdentifier();
                    if ((dp.storeResult.mdoid).equals(oid.toString())) {
                        self.testPassed("Found oid " + oid +
                                        " in query results");
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    self.testFailed("We didn't find our expected oid " +
                                    dp.storeResult.mdoid + " for query " 
                                    + query);
                }
            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage());
            }
        }
    }
}
