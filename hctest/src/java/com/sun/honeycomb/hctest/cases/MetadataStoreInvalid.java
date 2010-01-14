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
import java.util.ListIterator;
import java.util.HashMap;

/**
 * Validate that passing invalid metadata is detected and an error
 * is returned.
 */
public class MetadataStoreInvalid extends HoneycombLocalSuite {
    private CmdResult storeResult;
    
    public MetadataStoreInvalid() {
        super();
    }

    public String help() {
        return(
            "\tPassing invalid metadata is detected and handled properly\n" +
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
     * A convenience class for invalid metadata test cases.
     */
    public class MetadataAndException {
        HashMap metadata;
        String eRegexp;
	String testName;

        // XXX future: different types/coercion?
        MetadataAndException(String f, String v, String r) {
            metadata = new HashMap();
	    if (v.length() > 40)
	    	testName = v.substring(0,40);
            metadata.put(f, v);
            eRegexp = r;
        }
        public String getMDTestParamString() {
            return ("metadata=" + testName);
        }

    }

   /**
    * Iterate through the invalid metadata test cases and validate that a
    * store returns the expected error.
    */
    public void testInvalidMetadataStore()
    throws HoneycombTestException {

        String bogusField = "Field.*does not exist";
        String anyError = ".*";
	String charError = "Illegal type java.lang.String.* for char field " +
		"system.test.type_char";
	String charNQError = "Illegal type java.lang.String.* for char " +
		"field nonqueryable.test.type_char";
        String charLengthError = "Value too long for field " +
		"system.test.type_char";
	String binaryError = "Illegal type java.lang.String.* " +
		"for binary field system.test.type_binary";
	String binaryNQError = "Illegal type java.lang.String.* " +
		"for binary field nonqueryable.test.type_binary";
        String binaryLengthError = "Value too long for field " +
		"system.test.type_binary";
	String stringError = "Illegal type java.lang.String.* " +
		"for string field system.test.type_string";
	String stringNQError = "Illegal type java.lang.String.* " +
		"for string field nonqueryable.test.type_string";
        String stringLengthError = "Value too long for field " +
		"system.test.type_string";
        String longStrTest = "";
        String longCharBinaryTest = "";
        String illegalStringError = "UTF-8 encoding failed due to" + 
            " malformed or unmappable string input";


        int uniSupp[] = {0x10400};
        String uniSuppStr = new String(uniSupp, 0, 1);

        for (int i=0; i < 4001; i++) {
                longStrTest = longStrTest + "a";
        }

        for (int i=0; i < 8001; i++) {
                longCharBinaryTest = longCharBinaryTest + "a";
        }
	
        ArrayList invalidMetadata = new ArrayList();

        invalidMetadata.add(new MetadataAndException(
            "bogusField1", "value1", bogusField));
        invalidMetadata.add(new MetadataAndException(
            "bogusField2emptyvalue", "", bogusField));
        invalidMetadata.add(new MetadataAndException(
            "bogusField3 with spaces", "value3", bogusField));
        invalidMetadata.add(new MetadataAndException(
            "bogusField4\\ with\\ escaped\\ spaces", "value4", bogusField));
        invalidMetadata.add(new MetadataAndException(
            "", "empty_field", bogusField));
        invalidMetadata.add(new MetadataAndException(
            " ", "just_one_space", bogusField));
        invalidMetadata.add(new MetadataAndException(
            "=", "equals", bogusField));

	invalidMetadata.add(new MetadataAndException(
            "system.test.type_char", "\u038F", charError));
        invalidMetadata.add(new MetadataAndException(
            "system.test.type_binary", "M", binaryError));
        invalidMetadata.add(new MetadataAndException(
            "nonqueryable.test.type_char", "\u038F", charNQError));
        invalidMetadata.add(new MetadataAndException(
            "nonqueryable.test.type_binary", "M", binaryNQError));

        invalidMetadata.add(new MetadataAndException(
            "system.test.type_string", "\0", stringError));
        invalidMetadata.add(new MetadataAndException(
            "system.test.type_char", "\0", charError));

        invalidMetadata.add(new MetadataAndException(
            "system.test.type_string", "abc\0", stringError));
        invalidMetadata.add(new MetadataAndException(
            "system.test.type_char", "abcd\0", charError));
        
        invalidMetadata.add(new MetadataAndException(
            "system.test.type_string", "\uD800", illegalStringError));
        invalidMetadata.add(new MetadataAndException(
            "nonqueryable.test.type_string", "\uD800", illegalStringError));

        invalidMetadata.add(new MetadataAndException(
            "system.test.type_string", uniSuppStr, stringError));
        invalidMetadata.add(new MetadataAndException(
            "nonqueryable.test.type_string", uniSuppStr, stringNQError));

        invalidMetadata.add(new MetadataAndException(
            "system.test.type_string", "\uD801\uDC00", stringError));
        invalidMetadata.add(new MetadataAndException(
            "nonqueryable.test.type_string","\uD801\uDC00", stringNQError));
       
        invalidMetadata.add(new MetadataAndException(
            "system.test.type_string", "\uDC00\uD801", illegalStringError));
        invalidMetadata.add(new MetadataAndException(
            "nonqueryable.test.type_string", "\uDC00\uD801", 
                 illegalStringError));

        invalidMetadata.add(new MetadataAndException(
            "system.test.type_string", longStrTest, stringLengthError));
        invalidMetadata.add(new MetadataAndException(
            "system.test.type_binary", longCharBinaryTest, binaryLengthError));
        invalidMetadata.add(new MetadataAndException(
            "system.test.type_char", longCharBinaryTest, charLengthError));
        
        /* These 3 cases FAIL on 6512506
        invalidMetadata.add(new MetadataAndException(
            "bogusField5withnullval", null, anyError));
        invalidMetadata.add(new MetadataAndException(
            HoneycombTestConstants.MD_VALID_FIELD, null, anyError));
        invalidMetadata.add(new MetadataAndException(null,
            "null_field", bogusField));
        */

        // These test cases don't interact well with audit because they
        // are invalid, so we disable audit for this testcase
        disableAudit();

        // Iterate through all the bad metadata and verify store fails
        ListIterator i = invalidMetadata.listIterator();
        while (i.hasNext()) {
            MetadataAndException mae = (MetadataAndException)i.next();
            TestCase self = createTestCase("InvalidMetadataStore",  
		mae.getMDTestParamString());
            addTag(Tag.NEGATIVE);
            addTag(Tag.QUICK);
            addTag(Tag.REGRESSION);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.EMULATOR);
            addTag(HoneycombTag.STOREMETADATA);
            addTag(HoneycombTag.JAVA_API);

            if (self.excludeCase()) continue;
            
            try {
                Log.INFO("Attempt store with invalid MD " + mae.metadata);
                storeResult = store(getFilesize(), mae.metadata);
                Log.INFO("Stored file " + storeResult.filename +
                    " of size " + getFilesize());
                Log.INFO("OID returned was " + storeResult.mdoid);
            } catch (Exception e) {
                if (Util.exceptionMatches(mae.eRegexp, e)) {
                    self.testPassed("Got expected exception: " 
                                    + e.getMessage());
                } else {
                    self.testFailed("Did not get expected exception "
                                    + mae.eRegexp + " but got: " 
                                    + e.getMessage());
                }
                continue;
            } catch (Throwable t) {
                Log.INFO("Unexpected exception: " + Log.stackTrace(t));
                addBug("6512506", "passing null as MD value or field name in " +
                    "java produces RuntimeException instead of IllArgExcpn");
                self.testFailed("Unexpected throwable: " + t);
                continue;
            }

            addBug("6187840", "setting MD fields during store that " +
                   "don't exist should cause an excptn");
            
            addBug("6187936", "null for MD field/value should cause an " +
                   "excpn (not silently ignore)");
            
            self.testFailed("Invalid metadata " + mae.metadata +
                            " didn't throw an exception");
        }
    }
}
