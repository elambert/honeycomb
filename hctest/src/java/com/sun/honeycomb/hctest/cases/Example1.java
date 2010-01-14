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

import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.client.*;

/**
 * A template for writing a simple test.
 */
public class Example1 extends Suite {

    public String help() {
        return "Help! I need somebody.  Help!";
    }

    public void setUp() {
        Log.INFO("setting up...");
    }

    public void tearDown() {
        Log.INFO("tearing down...");
    }

    public void runTests() {
        TestCase tc;

        tc = createTestCase("pass");
        tc.addTag(Tag.EXAMPLE);
        tc.addTag(Tag.POSITIVE);
        if (!tc.excludeCase()) {
            tc.testPassed("this test case always passes");
        } else {
            tc.testSkipped("this test has been skipped");
        }

        tc = createTestCase("fail");
        tc.addTag(Tag.EXAMPLE);
        tc.addTag(Tag.NEGATIVE);
        if (!tc.excludeCase()) {
            tc.testFailed("this test case always fails");
        } else {
            tc.testSkipped("this test has been skipped");
        }

        tc = createTestCase("skipped");
        tc.addTag(Tag.EXAMPLE);
        tc.addTag(Tag.NORUN); // always skip
        if (!tc.excludeCase()) {
            tc.testPassed("XXX: should never get here.");
        } else {
            tc.testSkipped("this test always skips");
        }

    }
}
