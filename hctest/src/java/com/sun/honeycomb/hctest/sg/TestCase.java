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



package com.sun.honeycomb.hctest.sg;

import java.io.*;
import java.lang.*;
import java.util.*;

/**
 * This a class to describe information about a test case.
 * This is not being extensively used.
 */
public class TestCase extends TestRun {
    public static String testCaseName = "";
    public static long testCaseStartTime = 0;
    public static String testCaseErrorReason = "";
    public static int testCaseNumErrors = 0;

    public TestCase() {
        super();
    }

    public TestCase(String testrun, String testcase) {
        super(testrun);
        testCaseName = testcase;
    }

    // reset for another testrun
    public static void testCaseInit(String s) {
        testCaseName = s;
        testCaseErrorReason = "";
        testCaseNumErrors = 0;
        testCaseStartTime = TestLibrary.msecsNow();
        printDate("\n---> Starting test case " +
            testCaseName + " at ");
    }

    // finalize the test case by printing status info
    public static void testCaseFini() {
        testCaseReportResult();
        printInfo("\n<--- Done with test case " + testCaseName);
    }

    // record a problem that happened during the test
    public static void addError(String s) {
        testRunError = true;  // note that this test run hit an error
        String newreason = "[Error " + ++testCaseNumErrors + ": " + s + "]";
        printError("ERROR: " + newreason);
        testCaseErrorReason += newreason;
    }

    // print a summary for this testcase
    public static void testCaseReportResult() { 
        printInfo("\n[HEADER]|TestName|TestCase|Result|ExecTime|Client|Reason|Date");
        printDate("[RESULT]|" +
        testRunName + "|" + testCaseName + "|" +
        (testCaseNumErrors == 0 ? PASS : FAIL) + "|" +
            (TestLibrary.msecsNow() - testCaseStartTime) + "|" +
            testRunClient + "|" +
            testCaseErrorReason + "|");

        // add summary info for test run
        addTestCaseResultToTestRunSummary();
    }

    public static void addTestCaseResultToTestRunSummary() {
        testRunResultsSummary += "\t"+ testCaseName + "\t" +
            (testCaseNumErrors == 0 ? PASS : FAIL) + "\n";
    }
}
