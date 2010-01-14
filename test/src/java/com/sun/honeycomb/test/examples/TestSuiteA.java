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



package com.sun.honeycomb.test.examples;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.sql.Time;

/**
 *  Simple demo of testcases which extend Suite:
 *  A generic test must extend Suite and have test methods of form:
 *
 * <code>
 *  public void testXXX(...) {...}
 * </code>
 *
 * A generic test is expected to manage its own Results;
 * test framework will not create Results for it, 
 * like it does for simple tests.
 * Results can be for any testcase name (not necessarily matching XXX),
 * and multiple Results can be generated from the same test method.
 * Testcases can be identified by procedure name and parameters.
 *
 * Any test (simple or generic) which extends Suite has access to APIs
 * to post test-related bugs, metrics, declare tags and dependencies.
 *
 * Note: it's a good idea not to mix generic and simple tests
 * in the same class, to avoid confusion. This class is just a demo.
 */

public class TestSuiteA extends Suite {

    public TestSuiteA() {
        Log.DEBUG("TestSuiteA() - in constructor");
    }

    /** setUp gets run once, before execution of any test methods 
     */
    public void setUp() throws Throwable {
        Log.INFO("Running TestSuiteA setUp()");
        super.setUp();
    }

    /** tearDown gets run once, after execution of all test methods
     */
    public void tearDown() throws Throwable {
        Log.INFO("Running TestSuiteA tearDown()");
        super.tearDown();
    }

    /** Demonstrates a generic test with parameterized testcases,
     *  multiple Results, bugs and notes
     */
    public void testAGenericTest() throws Throwable {
        Log.INFO("I am a GENERIC test: testAGenericTest()");
        // create a few Results just because we can!
        for (int i = 0; i < 3; i++) {
        
            boolean willPass = true;
            TestCase self = createTestCase("DemoGeneric", "option=" + i);
            self.addTag(Tag.SAMPLE, "An example of how a Generic Test works");
            if (i%2 == 0) {
                willPass = false;
                self.addTag(Tag.FAILING);
            }
            if (self.excludeCase()) continue; // should i even run?

            // post some individual metrics of int and double datatypes
            self.postMetric(new Metric("fun level", "berserks", 42));
            self.postMetric(new Metric("difficulty", "stones", 3.14));
            
            Time tval = Util.duration(0, 20, 5); // 00:20:05
            Metric[] mGroup = { new Metric("speech content", "words", "Cool!"),
                                new Metric("duration", "HH:MM:SS", tval)
            }; // post a group of metrics of string and time datatypes
            self.postMetricGroup(mGroup);

            if (willPass) {
                self.testPassed("I passed becaues i=" + i + " is even");
            } else {
                self.addBug("6219898", "Demo bug: QA infra");
                self.testFailed("I failed because i=" + i + " is odd");
            }
        }
    }
    
    /** Demonstrates a simple test in a Suite extension
     *  no control over Results, but can still add bugs
     */
    public boolean testBSimpleTest() {
        Log.INFO("I am a SIMPLE test: testBSimpleTest()");
        addBug("123", "bogus bug");
        addBug("567", "bogus bug");
        addBug("890", "bogus bug");
        addTag(Tag.SAMPLE, "An example of a Simple Suite-based test");
        if (excludeCase()) return false;

        int sleepytime = 5;
        Log.INFO("Will sleep for " + sleepytime + " seconds");
        try {
            Thread.sleep(sleepytime * 1000);
        } catch (InterruptedException e) {
            Log.ERROR("Woke up too early!");
            return false;
        }
        return true;
    }

    public void testCManyResults() {
        TestCase self = createTestCase("DemoGeneric", "many results");
        self.addTag(Tag.SAMPLE, "An example of generic test with several results");
        if (self.excludeCase()) return;
        
        Log.INFO("My purpose is to demonstrate multiple results");
        self.testPassed("Functional sub-test passes");
        self.testPassed("Performance sub-test passes too");

        Log.INFO("Doing some more stuff");
        try {
            Thread.sleep(2 * 1000);
        } catch (InterruptedException e) {;}

        // creating a new Result object will restart time counter,
        // so this result's runtime will be from createResult() to postResult()
        // and not from the beginning of this test.
        Result anotherRes = self.createResult();
        // post a result-specific metric
        anotherRes.postMetric(new Metric("code coverage", "%", 1.5));
        self.postResult(anotherRes, true, "Another sub-test passes...");
    }

}
