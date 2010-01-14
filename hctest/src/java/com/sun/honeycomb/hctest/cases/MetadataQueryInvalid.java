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

import com.sun.honeycomb.common.ArchiveException;

/**
 * Do some negative tests that invalid queries are detected and handled
 * properly.
 */
public class MetadataQueryInvalid extends HoneycombLocalSuite {

    public MetadataQueryInvalid() {
        super();
    }

    public String help() {
        return(
            "\tValidate invalid queries are detected and handled properly\n"
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
     * Perform invalid queries 'type mismatch'
     */
    public boolean testTypeMismatch()
        throws HoneycombTestException {
        
        ArrayList invalidQueries = new ArrayList();

        addTag(Tag.REGRESSION);
        addTag(Tag.NEGATIVE);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
        addTag(HoneycombTag.EMULATOR);
        if (excludeCase()) {
            return (true);
        }
        
        invalidQueries.add(null);
        invalidQueries.add("");
        invalidQueries.add("\"bogusField1\"='bogusValueFromAutomatedTest'");
        // Double
        invalidQueries.add(HoneycombTestConstants.MD_DOUBLE_FIELD1+"='dummy'");

        // Long
        invalidQueries.add(HoneycombTestConstants.MD_LONG_FIELD1+"='dummy'");

        ListIterator i = invalidQueries.listIterator();
        while (i.hasNext()) {

            String q = (String) i.next();

            try {
              CmdResult cr = query(q);
              Log.INFO("Query " + q + "succeeded...test failed");  
              return (false); 
            } catch (HoneycombTestException hte) {
                // ignore
                Log.INFO("Got exception as expected: "+hte);
            }
        }
        return (true);
    }

    /**
     * Perform invalid queries 'literal > field size'
     */
    public void testLiteralGrterThanFieldsize()
        throws Throwable {

        ArrayList validStore  = new ArrayList();
        StringBuilder longStr = new StringBuilder(512);
        StringBuilder longChar = new StringBuilder(512);
        StringBuilder longBinary = new StringBuilder(1024);

        for (int i=0; i < 512; i++) {
            longStr = longStr.append("a");
        }

        for (int i=0; i < 512; i++) {
            longChar = longChar.append("b");
        }

        for (int i=0; i < 1024; i++) {
            longBinary = longBinary.append("e");
        }
        HashMap hm = new HashMap();

        String metadata = longStr.toString();
        validStore.add(metadata);
        hm.put(HoneycombTestConstants.MD_VALID_FIELD1, metadata);

        metadata = longChar.toString();
        validStore.add(metadata);
        hm.put(HoneycombTestConstants.MD_CHAR_FIELD1, metadata);

        metadata = longBinary.toString();
        validStore.add(metadata);
        hm.put(HoneycombTestConstants.MD_BINARY_FIELD1,
            ByteArrays.toByteArray(metadata));

        try {
            Log.INFO("storing a valid data");
            CmdResult cmdres = store(getFilesize(), hm);
            Log.INFO("store succeeded");
        } catch (Throwable t) {
            TestCase datastore = createTestCase("testLiteralGrterThanFieldsize",
                                 "case= store data");
            datastore.testFailed("Failed to store file: " + t.getMessage());
            t.printStackTrace();
            return;
        }

        ArrayList invalidQueries = new ArrayList();

        // adding just one more char to each string
        // and using them in negative queries below
        longStr = longStr.append("a");
        longChar = longChar.append("b");
        longBinary = longBinary.append("e");

        // String
        invalidQueries.add(HoneycombTestConstants.MD_VALID_FIELD1+"='"+longStr+"'");

        // Char
        invalidQueries.add(HoneycombTestConstants.MD_CHAR_FIELD1+"='"+longChar+"'");

        // Binary
        invalidQueries.add(HoneycombTestConstants.MD_BINARY_FIELD1+
            "= x'"+longBinary+"'");
              
        ListIterator i = invalidQueries.listIterator();
        while (i.hasNext()) {

            String q = (String) i.next();
            String testCase =  (q == null) ? "null" : q;
            String testName = q.substring(0, 40) + "'";
            String queryLargeError = " SQL query failed .select objectid from.*";
            TestCase self = createTestCase("testLiteralGrterThanFieldsize",
                                "case=" + testName);
            addTag(Tag.REGRESSION);
            addTag(Tag.NEGATIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);
                      
            if (q.contains(longBinary)) { 
                self.addBug("6612017", "Test MetadataQueryInvalid::" + 
                    "testLiteralGrterThanFieldsize::case=" + testName+ 
                    "  fails in the regression suite"); 
                self.addTag(Tag.FAILING); 
            } 
            if (self.excludeCase()) {  
                continue;
            }             

            try {
                CmdResult cr = query(q);
                // making sure that cr contains the expected exception
                // if query fails to generate HoneycombTestException.
                // checkExceptions returns true if an exception doesn't
                //  match expected type.
                ArchiveException a = new ArchiveException("expected msg");
                if (cr.checkExceptions(a.getClass(), "expected msg")) {
                    self.testFailed("Unexpected error");
                } else {
                    self.testPassed("Got expected exception: ArchiveException");
                }
            } catch (HoneycombTestException hte) {
                Throwable t = hte.getCause();
                if (t == null) {
                    self.testFailed("Unexpected exception: no cause");
                } else if (! (t instanceof ArchiveException)) {
                    self.testFailed("Unexpected exception " + t);
                } else {
                    if (Util.exceptionMatches(queryLargeError, t)) {
                        self.testPassed("Got expected exception: "
                                    + t.getMessage());
                    } else {
                        self.testFailed("Did not get expected exception "
                                    + queryLargeError + " but got: "
                                    + t.getMessage());
                    }          
                }
            }
        }
    }


    /**
     * Perform invalid queries 'reserved keywords'
     */
    public boolean testReservedKeywords()
        throws HoneycombTestException {

        addTag(Tag.REGRESSION);
        addTag(Tag.NEGATIVE);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
        addTag(HoneycombTag.EMULATOR);
        
        if (excludeCase()) {
            return (true);
        }

        String q = "select='stephane'";
        try {
            CmdResult cr = query(q);
            return (false);
        } catch (HoneycombTestException hte) {
            return (true);
        }
    }


    /**
     * Perform invalid queries 'bad quoting'
     */
    public void testBadQuotingQuery()
        throws HoneycombTestException {


        String nullArg = "Query cannot be null";
        String bogusField = "No such attribute";
        String badQuotes = "Failed to parse the query";
	String forbiddenWord = "Query contains forbidden word";
        String shouldSucceed = "Should succeed, framework test";
	String queryFailed = "SQL query failed";
        ArrayList invalidQueries = new ArrayList();


        invalidQueries.add(null);
        invalidQueries.add("\"bogusField1\"='bogusValueFromAutomatedTest'");
        invalidQueries.add("\"badquote1='bogusValueFromAutomatedTest'");
        invalidQueries.add("badquotes2\"='bogusValueFromAutomatedTest'");
        invalidQueries.add("\"badquotes3\"=bogusValueFromAutomatedTest'");
        invalidQueries.add("\"badquotes4\"='bogusValueFromAutomatedTest");
        invalidQueries.add("badquotes5='bogusValueFromAutomatedTest'");
        invalidQueries.add("\"badquotes6\"=bogusValueFromAutomatedTest");

        
        // Iterate through all the bad queries and verify each fails
        ListIterator i = invalidQueries.listIterator();
        while (i.hasNext()) {

            String q = (String) i.next();
            String testCase =  (q == null) ? "null" : q;

            TestCase self = createTestCase("InvalidQuery",
                                           "query = " + testCase);

            addTag(Tag.REGRESSION);
            addTag(Tag.NEGATIVE);
            addTag(Tag.QUICK);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);
            addTag(Tag.SMOKE);
            if (self.excludeCase()) {
                continue;
            }

            try {
                Log.DEBUG("Attempt query with invalid query " + q);
                CmdResult cr = query(q);
                cr.logExceptions("pass = " + cr.pass);
                self.testFailed("Query succeeded...test failed");
            } catch (HoneycombTestException hte) {
                self.testPassed("Got expected exception: " + hte.getMessage()); 
            } 
        }
        addBug("6188641", "a query with an error in it should have " +
               "error propogated to the client");
    }
}
