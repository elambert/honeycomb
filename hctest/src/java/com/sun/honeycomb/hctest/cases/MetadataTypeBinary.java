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

import com.sun.honeycomb.common.ByteArrays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.io.File;

/**
 * Validate that metadata functions if you use the type binary.
 */
public class MetadataTypeBinary extends HoneycombLocalSuite {

    private ArrayList binaryMDValues = null;

    public MetadataTypeBinary() {
        super();
    }

    public String help() {
        return(
            "\tValidate that metadata functions if you use the type binary.\n" +
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
    public class BinaryTest {
        public byte[] b1;
        public String testName = null;
        public String tempStr = null;
        public CmdResult storeResult;

        BinaryTest(byte[] binary1) {
            b1 = binary1;
            testName = ByteArrays.toHexString(b1);
            tempStr = ByteArrays.toHexString(b1);
            if (testName.length() > 40)
                testName = testName.substring(0,40);
        }

        public String getTestParamString() {
            return ("case=" + testName);
        }
    }

    /**
     * Store objects with metadata that has type binary.  
     */
    public void testAStoreWithBinaryMetadata() throws HoneycombTestException {
        binaryMDValues = new ArrayList();
        String longStr = "";
        for (int i=0; i < 16000; i++) {
                longStr = longStr + "a";
        }

        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("0")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("1")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("a")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("f")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("00")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("ab")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("abcde")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("abcdef")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("ff")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray("feedbeefcafe33")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray(
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray(
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef")));
        binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray(
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
            "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef")));
	binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray(longStr)));

//         binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray(
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef"+
//             "01234567890abcdef01234567890abcdef01234567890abcdef01234567890abcdef")));
//         binaryMDValues.add(new BinaryTest(ByteArrays.toByteArray(
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000"+
//             "00000000000000000000000000000000000000000000000000000000000000000000")));

        // Iterate through all the binarys and store them
        HashMap hm = new HashMap();
        ListIterator i = binaryMDValues.listIterator();
        while (i.hasNext()) {
            BinaryTest bt = (BinaryTest) i.next();

            TestCase self = createTestCase("StoreWithBinaryMetadata",
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

            hm.put(HoneycombTestConstants.MD_BINARY_FIELD3, bt.b1);

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
     * Verify we can query for the objects that have metadata of type binary.
     */
    public void testCQueryAfterStoreWithBinaryMetadata() throws Throwable {
        // Iterate through all the binarys and query for them
        ListIterator i = binaryMDValues.listIterator();
        while (i.hasNext()) {
            BinaryTest bt = (BinaryTest) i.next();

            TestCase self = createTestCase("QueryAfterStoreWithBinaryMetadata", 
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
            String query = HoneycombTestConstants.MD_BINARY_FIELD3 + 
                "= x'" + ByteArrays.toHexString(bt.b1) + "'";
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
}
