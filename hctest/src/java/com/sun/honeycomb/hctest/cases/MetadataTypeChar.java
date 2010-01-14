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
 * Validate that metadata functions if you use the type char.
 */
public class MetadataTypeChar extends HoneycombLocalSuite {

    private ArrayList charMDValues = null;

    public MetadataTypeChar() {
        super();
    }

    public String help() {
        return(
            "\tValidate that metadata functions if you use the type char.\n" +
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
    public class CharTest {
        public String b1;
        public String testName = null;
        public CmdResult storeResult;

        CharTest(String char1) {
            b1 = char1;
            testName = b1;
            if (testName.length() > 40)
                testName = testName.substring(0,40);
        }

        public String getTestParamString() {
            return ("case=" + testName);
        }
    }

    /**
     * Store objects with metadata that has type char.  
     */
    public void testAStoreWithCharMetadata() throws HoneycombTestException {
        charMDValues = new ArrayList();
	String longStr = "";
        for (int i=0; i < 8000; i++) {
                longStr = longStr + "a";
        }

        charMDValues.add(new CharTest(""));
        charMDValues.add(new CharTest("A"));
        charMDValues.add(new CharTest("a"));
        charMDValues.add(new CharTest("ab "));
        charMDValues.add(new CharTest(" ab"));
        charMDValues.add(new CharTest(" ab "));
        charMDValues.add(new CharTest("ab\r"));
        charMDValues.add(new CharTest("ab\rab"));
        charMDValues.add(new CharTest("abc\t"));
        charMDValues.add(new CharTest("abc\tabc"));
        charMDValues.add(new CharTest("abcd\n"));
        charMDValues.add(new CharTest("abcd\nabcd"));
        //        charMDValues.add(new CharTest("\u0000"));
        //        charMDValues.add(new CharTest("\u0001"));
        charMDValues.add(new CharTest("\u00c3"));
        charMDValues.add(new CharTest("ab"));
        charMDValues.add(new CharTest("ff"));
        charMDValues.add(new CharTest("feedbeefcafe33"));
        charMDValues.add(new CharTest(
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"));
        charMDValues.add(new CharTest(
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"));
        if (false) {
            charMDValues.add(new CharTest(
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"));
        }
	charMDValues.add(new CharTest(longStr));

        // Iterate through all the chars and store them
        HashMap hm = new HashMap();
        ListIterator i = charMDValues.listIterator();
        while (i.hasNext()) {
            CharTest bt = (CharTest) i.next();

            TestCase self = createTestCase("StoreWithCharMetadata",
                                           bt.getTestParamString());
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

            hm.put(HoneycombTestConstants.MD_CHAR_FIELD3, bt.b1);

            try {
                bt.storeResult = store(getFilesize(), hm);
                self.testPassed(); 
            } catch (Throwable t) {
                self.testFailed("Failed to store file: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    /**
     * Verify we can query for the objects that have metadata of type char.
     */
    public void testCQueryAfterStoreWithCharMetadata() throws Throwable {
        // Iterate through all the chars and query for them
        ListIterator i = charMDValues.listIterator();
        while (i.hasNext()) {
            CharTest bt = (CharTest) i.next();

            TestCase self = createTestCase("QueryAfterStoreWithCharMetadata", 
                                           bt.getTestParamString());
            if (bt.storeResult == null) 
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
            String query = HoneycombTestConstants.MD_CHAR_FIELD3 + 
                "= '" + sqlQuote(bt.b1) + "'";
            try {
                CmdResult cr = query(query);
                QueryResultSet qrs = (QueryResultSet) cr.rs;

                boolean found = false;
                while (qrs.next()) {
                    ObjectIdentifier oid = qrs.getObjectIdentifier();
                    if ((bt.storeResult.mdoid).equals(oid.toString())) {
                        self.testPassed("Found oid " + oid +
                                        " in query results");
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    self.testFailed("We didn't find our expected oid " +
                                    bt.storeResult.mdoid + " for query " 
                                    + query);
                }
            } catch (HoneycombTestException hte) {
                self.testFailed("Query failed: " + hte.getMessage());
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
