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

import java.sql.Timestamp;
import com.sun.honeycomb.common.CanonicalEncoding;

/**
 * Validate that metadata functions if you use the type timestamp.
 */
public class MetadataTypeTimestamp extends HoneycombLocalSuite {

    private ArrayList timestampMDValues = null;

    public MetadataTypeTimestamp() {
        super();
    }

    public String help() {
        return(
            "\tValidate that metadata functions if you use the type timestamp.\n" +
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
    public class TimestampTest {
        public Timestamp t1;
        public String testName = null;
        public CmdResult storeResult;

        TimestampTest(Timestamp timestamp1) {
            t1 = timestamp1;
            testName = t1.toString();
        }

        public String getTestParamString() {
            return ("case=" + testName + " t1=" + t1);
        }
    }

    /**
     * Store objects with metadata that has type timestamp.  
     */
    public void testAStoreWithTimestampMetadata() throws HoneycombTestException {
        timestampMDValues = new ArrayList();

        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("1970-01-01 00:00:00.0000")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("1970-01-01 00:00:01")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("1970-01-01 00:00:01.001")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("1999-12-31 11:59:59")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("2000-01-01 00:00:00")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("2006-06-06 06:06:06.06")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("2999-12-31 11:59:59.999")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("2999-12-31 11:59:59.999")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("1583-01-01 00:00:00.000")));
        timestampMDValues.add(new TimestampTest(Timestamp.valueOf("1582-01-01 01:02:03.004")));
        // Cannot use the following test with equals as the test (Java Date yuckiness)
        //timestampMDValues.add(new TimestampTest(CanonicalEncoding.decodeTimestamp("0000-00-00T06:06:06.06Z")));

        // Iterate through all the timestamps and store them
        HashMap hm = new HashMap();
        ListIterator i = timestampMDValues.listIterator();
        while (i.hasNext()) {
            TimestampTest tt = (TimestampTest) i.next();

            TestCase self = createTestCase("StoreWithTimestampMetadata",
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

            Log.DEBUG(this.getClass().getName() + "tt.t1='"+tt.t1+"=("+tt.t1.getTime()+")");

            hm.put(HoneycombTestConstants.MD_TIMESTAMP_FIELD1, tt.t1);

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
     * Verify we can retrieve the metadata for  the objects 
     *  that have metadata of type timestamp. 
     */
    public void testBRetrieveAfterStoreWithTimestampMetadata() throws Throwable {
        // Iterate through all the timestamps and do RetrieveMetadata for them
        ListIterator i = timestampMDValues.listIterator();
        while (i.hasNext()) {
            TimestampTest tt = (TimestampTest) i.next();

            TestCase self = createTestCase("RetrieveAfterStoreWithTimestampMetadata", 
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
            try {
                CmdResult cr = getMetadata(tt.storeResult.mdoid);
                HashMap omd = cr.mdMap;
                Object value = 
                    omd.get(HoneycombTestConstants.MD_TIMESTAMP_FIELD1);
                if (value == null) {
                    throw new RuntimeException("field "+
                                               HoneycombTestConstants.MD_TIMESTAMP_FIELD1+
                                               " missing from returned value.");
                }
                if (! (value instanceof Timestamp)) {
                    throw new RuntimeException("field "+
                                               HoneycombTestConstants.MD_TIMESTAMP_FIELD1+
                                               " returned value '"+ value +
                                               " is not a timestamp (class="+
                                               value.getClass()+")");
                    
                } 
                Timestamp tt1 = (Timestamp)value;
                long t1 = tt1.getTime();
                // They should be equal out to units of milliseconds
                if (t1 / 1000 != tt.t1.getTime() / 1000) {
                    throw new RuntimeException("field "+
                                               HoneycombTestConstants.MD_TIMESTAMP_FIELD1+
                                               " returned value '"+ tt1 +
                                                "' ("+t1+") does not match stored value '"+
                                                tt.t1+
                                               "' ("+tt.t1.getTime()+")");
                }
                self.testPassed("Found correct value '"+tt1+
                                "' in retrieve metadata results");

            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage());
            }
        }
    }

    /**
     * Verify we can query for the objects that have metadata of type timestamp.
     */
    public void testCQueryAfterStoreWithTimestampMetadata() throws Throwable {
        // Iterate through all the timestamps and query for them
        ListIterator i = timestampMDValues.listIterator();
        while (i.hasNext()) {
            TimestampTest tt = (TimestampTest) i.next();

            TestCase self = createTestCase("QueryAfterStoreWithTimestampMetadata", 
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
            String query = HoneycombTestConstants.MD_TIMESTAMP_FIELD1 + 
                "={timestamp '" + CanonicalEncoding.encode(tt.t1) + "'}";
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
