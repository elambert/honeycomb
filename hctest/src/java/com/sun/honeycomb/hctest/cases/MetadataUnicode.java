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
 * Validate that metadata functions if you use the type string.
 */
public class MetadataUnicode extends HoneycombLocalSuite {

    private ArrayList stringMDValues = null;
    private CmdResult schemaResult = null;

    public MetadataUnicode() {
        super();
    }

    public String help() {
        return(
            "\tValidate that metadata functions if you use the type string.\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n"
            );
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        try {
            schemaResult = getSchema();
        } catch (Exception e) {
            TestCase self = createTestCase("MetadataUnicode", "getSchema");
            self.testFailed("Getting schema: " + e);
        }
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    private void checkForSchema(TestCase self) {
        if (schemaResult == null  ||  !schemaResult.pass) {
            self.addTag(Tag.MISSINGDEP, "failed to get schema");
            Log.INFO("failed to get schema");
            return;
        }

        if (!HCUtil.schemaHasAttribute(schemaResult.nvs, 
		HoneycombTestConstants.MD_UNICODE_FIELD5)) {
            self.addTag(Tag.MISSINGDEP, "utf8 schema not loaded");
            Log.INFO("utf8 schema not loaded");
        }

    }
    /**
     * Convenience class for the Metadata double tests.
     */
    public class StringTest {
        public String b1;
        public String testName = null;
        public CmdResult storeResult;

        StringTest(String string1) {
            b1 = string1;
            testName = b1;
            if (testName.length() > 40)
                testName = testName.substring(0,40);
        }

        public String getTestParamString() {
            return ("case=" + testName);
        }
    }

    /**
     * Store objects with metadata that has type string.  
     */
    public void testAAAStoreWithStringMetadata() throws HoneycombTestException {
        stringMDValues = new ArrayList();
	String longStr = "";
	for (int i=0; i < 4000; i++) {
		longStr = longStr + "a";
	}
        stringMDValues.add(new StringTest(""));
        stringMDValues.add(new StringTest("A"));
        stringMDValues.add(new StringTest("ab "));
        stringMDValues.add(new StringTest(" ab"));
        stringMDValues.add(new StringTest(" ab "));
        stringMDValues.add(new StringTest("ab\r"));
        stringMDValues.add(new StringTest("ab\rcd"));
        stringMDValues.add(new StringTest("abc\n"));
        stringMDValues.add(new StringTest("abc\ndef"));
        stringMDValues.add(new StringTest("abcd\t"));
        stringMDValues.add(new StringTest("abcd\tabcd"));
        stringMDValues.add(new StringTest("\u043c\u043d\u0435 \u043d\u0435 \u0432\u0440\u0435\u0434\u0438\u0442"));
        stringMDValues.add(new StringTest("\u0986\u09ae\u09be\u09b0 \u0995\u09cb\u09a8\u09cb"));
        stringMDValues.add(new StringTest("\u305d\u308c\u306f\u79c1\u3092\u50b7\u3064\u3051\u307e\u305b\u3093"));
        stringMDValues.add(new StringTest("\u6211\u80fd\u541e\u4e0b\u73bb\u7483\u800c\u4e0d\u4f24\u8eab\u4f53"));
	stringMDValues.add(new StringTest(longStr));

        // Iterate through all the strings and store them
        HashMap hm = new HashMap();
        ListIterator i = stringMDValues.listIterator();
        while (i.hasNext()) {
            StringTest st = (StringTest) i.next();

            TestCase self = createTestCase("StoreWithStringMetadata",
                                           st.getTestParamString());
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.STOREMETADATA);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);

            checkForSchema(self);

            if (self.excludeCase()) {
                continue;
            }
	    /**
	     * None of the following variables could hold the string 
	     * values when the length of the string > 4000
	     */
	    /**
             * hm.put(HoneycombTestConstants.MD_UNICODE_FIELD1, st.b1);
             * hm.put(HoneycombTestConstants.MD_UNICODE_FIELD2, st.b1+".2");
             * hm.put(HoneycombTestConstants.MD_UNICODE_FIELD3, st.b1+".3");
             * hm.put(HoneycombTestConstants.MD_UNICODE_FIELD4, st.b1+".4");
	     */
	    // MD_UNICODE_FIELD5 can handle large strings
            hm.put(HoneycombTestConstants.MD_UNICODE_FIELD5, st.b1);

            try {
                st.storeResult = store(getFilesize(), hm);
                self.testPassed(); 
            } catch (Throwable t) {
                self.testFailed("Failed to store file: " + t.getMessage() +
                                "\n"+Log.stackTrace(t));
                t.printStackTrace();
            }
        }
    }

    /**
     * Verify we can query for the objects that have metadata of type string.
     */
    public void testCCCQueryWithStringLiterals() throws Throwable {
        // Iterate through all the strings and query for them
        ListIterator i = stringMDValues.listIterator();
        while (i.hasNext()) {
            StringTest st = (StringTest) i.next();

            TestCase self = createTestCase("QueryWithStringLiterals", 
                                           st.getTestParamString());
            if (st.storeResult == null) 
                addTag(Tag.MISSINGDEP, "Store missing, so skip query");;

            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);

            checkForSchema(self);

            if (self.excludeCase()) {
                continue;
            }
            String query = "\""+ HoneycombTestConstants.MD_UNICODE_FIELD5 +
                "\" = '" + sqlQuote(st.b1) + "'";
            try {
                CmdResult cr = query(query);
                QueryResultSet qrs = (QueryResultSet) cr.rs;

                boolean found = false;
                while (qrs.next()) {
                    ObjectIdentifier oid = qrs.getObjectIdentifier();
                     if ((st.storeResult.mdoid).equals(oid.toString())) {
                        self.testPassed("Found oid " + oid +
                                        " in query results");
                        found = true;
                    }
                }

                if (!found) {
                    self.testFailed("We didn't find our expected oid " +
                                    st.storeResult.mdoid + " for query " 
                                    + query);
                }
            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage() +
                                "\n"+Log.stackTrace(hte));
            }
        }
    }

    /**
     * Verify we can query for the objects using Honeycomb PreparedStatements
     */
    public void testDDDQueryWithStringDynamicParameters() throws Throwable {
        // Iterate through all the strings and query for them
        ListIterator i = stringMDValues.listIterator();
        while (i.hasNext()) {
            StringTest st = (StringTest) i.next();

            TestCase self = createTestCase("QueryWithStringDynamicParameters", 
                                           st.getTestParamString());
            if (st.storeResult == null) 
                addTag(Tag.MISSINGDEP, "Store missing, so skip query");;
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);

            checkForSchema(self);

            if (self.excludeCase()) {
                continue;
            }
            String query = "\""+HoneycombTestConstants.MD_UNICODE_FIELD5+"\"=?";
            try {
                PreparedStatement stmt = new PreparedStatement(query);
                stmt.bindParameter(st.b1,1);
                CmdResult cr = query(stmt);
                QueryResultSet qrs = (QueryResultSet) cr.rs;

                boolean found = false;
                while (qrs.next()) {
                    ObjectIdentifier oid = qrs.getObjectIdentifier();
                    if ((st.storeResult.mdoid).equals(oid.toString())) {
                        self.testPassed("Found oid " + oid +
                                        " in query results");
                        found = true;
                    }
                }

                if (!found) {
                    self.testFailed("We didn't find our expected oid " +
                                    st.storeResult.mdoid + " for query " 
                                    + query);
                }

            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage() +
                                "\n"+Log.stackTrace(hte));
            }
        }
    }
    /**
     * Verify we can query for the values and objects using Honeycomb PreparedStatements
     */
    public void testEEEQueryPlusWithStringDynamicParameters() 
        throws Throwable {
        // Iterate through all the strings and query for them
        ListIterator i = stringMDValues.listIterator();
        while (i.hasNext()) {
            StringTest st = (StringTest) i.next();

            TestCase self = createTestCase("QueryPlusWithStringDynamicParameters", 
                                           st.getTestParamString());
            if (st.storeResult == null) 
                addTag(Tag.MISSINGDEP, "Store missing, so skip query");;

            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);

            checkForSchema(self);

            if (self.excludeCase()) {
                continue;
            }
            String query = "\""+HoneycombTestConstants.MD_UNICODE_FIELD5+"\"=?";
            try {
                PreparedStatement stmt = new PreparedStatement(query);
                stmt.bindParameter(st.b1,1);
                String[] keys = {HoneycombTestConstants.MD_UNICODE_FIELD5 };
                CmdResult cr = query(stmt, keys);
                QueryResultSet qrs = (QueryResultSet) cr.rs;

                boolean found = false;
                while (qrs.next()) {
                    ObjectIdentifier oid = qrs.getObjectIdentifier();
                    if ((st.storeResult.mdoid).equals(oid.toString())) {
                        found = true;
                        String result = 
                            qrs.getString(HoneycombTestConstants.MD_UNICODE_FIELD5);
                        if (result.equals(st.b1)) {
                            self.testPassed("Found oid " + oid +
                                            " in query results"+
                                            " AND selected key-value matches");
                        } else {
                            self.testFailed("Value mismatch on queryPlus: stored='"+
                                            st.b1+"' retrieved='"+result+"'");
                        } // if/else (result matches)
                    } // if (oid matches)
                } // while (qrs.next())

                if (!found) {
                    self.testFailed("We didn't find our expected oid " +
                                    st.storeResult.mdoid + " for query " 
                                    + query);
                }

            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage() +
                                "\n"+Log.stackTrace(hte));
            }
        }
    }

    /** Replace single quotes with doubled single quotes */
    private static String sqlQuote(String s) {
        StringBuilder ret = new StringBuilder();

        int pos = 0;
        for (;;) {
            int c = s.indexOf('\'', pos);
            if (c < 0)
                break;

            c++;
            ret.append(s.substring(pos, c)).append("'");
            pos = c;
        }

        return ret.append(s.substring(pos)).toString();
    }

}
