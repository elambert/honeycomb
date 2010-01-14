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



package com.sun.honeycomb.test;
import com.sun.honeycomb.test.util.NameValue;
import com.sun.honeycomb.test.util.QB;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import com.sun.honeycomb.test.util.TestConstants;

public class Tag extends TestConstants {

    /**
     * These tags are recommended for usage by testcase authors.
     * It is also possible to name your own tag, without using constants,
     * but this is the basic set of tags we agreed upon.
     * If you come up with a new useful tag, add it here.
     */

    // Tests with these tags do not get run by default

    // Use cmd-line args to override this behavior and run them anyway

    public static final String NORUN = "norun"; 
    public static final String MISSINGDEP = "missingdep";
    public static final String EXPERIMENTAL = "experimental";
    public static final String OBSOLETE = "obsolete";
    public static final String SAMPLE = "sample"; // sample code, not real test
    public static final String MANUAL = "manual"; // not automated - don't run
    // MANUAL tag is used for result submission from manually executed tests

    // Bottom-up classification by test type
    public static final String UNIT = "unit";
    public static final String SMOKE = "smoke";
    public static final String REGRESSION = "regression";
    public static final String NEGATIVE = "negative";
    public static final String POSITIVE = "positive";
    public static final String DISTRIBUTED = "distributed";
    public static final String WHITEBOX = "whitebox";   
    public static final String FAILING = "failing"; 
    public static final String FLAKY = "flaky"; 

    // classification of tests by expected runtime
    public static final String QUICK = "quick";
    public static final String HOURLONG = "hourlong";
    public static final String FEWHOURSLONG = "fewhourslong";
    public static final String DAYLONG = "daylong";
    public static final String FEWDAYSLONG = "fewdayslong";
    public static final String WEEKLONG = "weeklong";
    public static final String FOREVER = "forever"; // test does not exit
    // Length dependent on the args passed in
    public static final String VARIABLELENGTH = "variablelength"; 
    
    // do not run in emulator tag
    public static final String NOEMULATOR="noemulator";

    // Test does not use the API (e.g. webdav only)
    public static final String NOAPI = "noapi";

    // do not run in emulator tag
    public static final String EXAMPLE="example";

    // Tags that are not run by default
    public static final String[] DEFAULT_EXCLUDE = {
        NORUN, MISSINGDEP, EXPERIMENTAL, OBSOLETE, MANUAL, FOREVER, FAILING
    };


    private String tagId; // ID of this tag in database lookup table
    private String name; // nametag... 
    private int resultId; // to correlate tag with result
    private String notes; // explanatory comments: why this test has this tag
    private QB qb = null; // handle to QB repository

    public Tag(String name, String notes, int result) {
        super();
        this.name = name;
        this.notes = notes;
        if (this.notes == null) {
            this.notes = ""; // nSull is illegal
        }
        this.resultId = result;
        qb = QB.getInstance();


        NameValue[] propHelp = {
            new NameValue("TAG PROPERTIES: ", "Tags denote logical grouping of testcases, and can be used in include/exclude rules \n"),
            new NameValue("Default exclude set: ", "Tests with some tags are excluded by default. Such tags are: " + java.util.Arrays.asList(DEFAULT_EXCLUDE) + "\n"),
            new NameValue(NORUN,"Never run this test, for whatever reason."),
            new NameValue(MISSINGDEP,"Test has a failed dependency (known at runtime)"),
            new NameValue(EXPERIMENTAL,"Still under development/not ready to be run by anyone other than the test developer."),
            new NameValue(OBSOLETE, "Test is obsolete"),
            new NameValue(SAMPLE,"Sample code, not real test"),
            new NameValue(MANUAL,"not automated - don't run. Tag is used for result submission from manually executed tests"),
            new NameValue(UNIT,"Not a QA system test, but a developer's unit test"),
            new NameValue(SMOKE,"Test should be executed before any checkin to verify basic goodness"),
            new NameValue(REGRESSION,"Test should be executed in periodical regression runs"),
            new NameValue(NEGATIVE,"Negative test, executes illegal operations and expects them to fail."),
            new NameValue(POSITIVE,"Standard positive tests - used to be part of allPositiveTests"),
            new NameValue(DISTRIBUTED,"Distributed tests (using multiple clients with RMI)"),
            new NameValue(WHITEBOX,"Tests using whitebox hooks"),
            new NameValue(FAILING,"Test is known to fail (usually on a filed bug)"),
            new NameValue(FLAKY,"Test is known to fail intermittently"),
            new NameValue( QUICK , "Test runs quickly"),
            new NameValue( HOURLONG , "Test takes about 1 hour to run"),
            new NameValue( FEWHOURSLONG , "Test takes a few hours to run"),
            new NameValue( DAYLONG , "Test takes about 1 day to run"),
            new NameValue( FEWDAYSLONG , "Test takes a few days to run"),
            new NameValue( WEEKLONG , "Test takes about 1 week to run"),
            new NameValue( FOREVER , "Test does not exit, runs until killed by hand"),
            new NameValue( VARIABLELENGTH , "Tests runs for a variable length of time (random)")
        };
        propertyUsage=propHelp;
    }

    public Tag(String name, String notes) {
        this(name, notes, 0); // result id yet unknown
    }
    
    public Tag(String name, int result) {
        this(name, "", result); // notes are optional
    }

    public Tag(String name) {
        this(name, "", 0); // only nametag is available
    }
    
    //
    // This is just so that TestRunner can extract the 
    // test doc nameValues.
    // Hackey, but it works.
    //
    public Tag() {
        this("dummyTagThisShouldNEverShowUp");
    }
    // if tag was created first, before any results,
    // correlate tag with result once ID is known
    public void setResult(int result) {
        this.resultId = result;
    }
    public String toString() { return name; } 

    public String details() {
        StringBuffer sb = new StringBuffer("TAG=" + name);
        if (notes.length() != 0) {
            sb.append(" REASON: " + notes);
        }
        // Result ID is of no interest because at runtime,
        // Tag will be printed in the context of a testcase
        // (result ID only matters for DB queries)
        return sb.toString();
    }

    public void post() throws Throwable {
        if (qb.isOff()) { 
            return;
        }

        File postTag = qb.dataFile("tag");
        writeRecord(new BufferedWriter(new FileWriter(postTag)));
        qb.post("tag", postTag);
    }

    private void writeRecord(BufferedWriter out) throws Throwable {
        out.write("QB.name: " + name + "\n");
        out.write("QB.result: " + resultId + "\n");
        if (notes.length() != 0) out.write("QB.notes: " + notes + "\n");
        out.close();
    }
}
