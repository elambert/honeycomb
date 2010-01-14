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
import java.util.ArrayList;

/**
 * A simple test is any Java class which has a set of "test methods".
 * Test methods are any test of the form,
 *
 * <code>
 *  public boolean testXXX() {...}
 * </code>
 *
 * where "XXX" is the name of the test, and the return value is the test's
 * result: PASS (true) or FAIL (false). There is exactly one result per method.
 * Tests methods in simple tests cannot have parameters. 
 * If you need parameters or the power of managing results, go generic.
 *
 * Simple tests, in practice, extend the Suite class to get access to
 * functionality of declaring tags, dependencies, reporting bugs. 
 *
 * Unit tests are based on simple tests, but do not extend the Suite class.
 *
 */

public class DemoSimpleTest extends Suite
{
    public void setUp() {
        System.out.println("*** Simple setUp() ***");
    }

    public void tearDown() {
        System.out.println("*** Simple tearDown() ***");
    }

    /** Demonstrates a simple test which fails
     */
    public boolean test1SimpleDemo() {
        System.out.println("*** I am 1SimpleDemo() ***");
        boolean negative = true;
        boolean result = true;
        if (negative) {
            addTag(Tag.FAILING, "I always fail");
            if (excludeCase()) return false;
            
            System.out.println("*** Why do I always fail? ***");
            result = false;
        }
        return result;
    }
    
    /** Demonstrates a simple test which passes, and has a metric
     */
    public boolean test2SimpleDemo() {
        System.out.println("*** I am 2SimpleDemo() ***");
        boolean result = false;
        try {
            ArrayList l = new ArrayList(-1);
        } catch (Throwable t) {
            System.out.println("*** Caught exception as expected ***");
            result = true;
        }
        postMetric(new Metric("simplicity", "dumb units", 5));
        postMetric(new Metric("simplicity", "dumb units", 7));
        postMetric(new Metric("simplicity", "dumb units", 11));
        return result;
    }

    /** Demonstrates a test which throws an exception. 
     *  The exception will be caught by the test framework,
     *  which will generate a failed Result and log the stack trace.
     */
    public boolean test3SimpleDemo() throws Throwable {
        boolean negative = true;
        if (negative) {
            addTag(Tag.FAILING, "I am negative");
            if (excludeCase()) return false;
            
            throw new Throwable("BEWARE OF PROGRAMMER ERROR!");
        }
        return true;
    }
}
