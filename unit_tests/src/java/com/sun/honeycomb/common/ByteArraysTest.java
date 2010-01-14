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



package com.sun.honeycomb.common;

import java.util.logging.*;
import junit.framework.*;

public class ByteArraysTest extends TestCase {

    private static final Logger LOG =
        Logger.getLogger(ByteArraysTest.class.getName());

    /** 
     * Constructor for this unit test.
     *
     * @param testName the name of the unit test
     */
    public ByteArraysTest(String testName) {
        super(testName);
    }

    /**
     * Allows unit tests to be run together in a suite.
     * @return a test suite that contains a single test - this one
     */
    public static Test suite() {
        return new TestSuite(ByteArraysTest.class);
    }

    /** Allows test to be run stand-alone from the command-line.
     *
     * java -Djava.library.path=/opt/honeycomb/lib
     *     -classpath test/lib/junit-3.8.1.jar:test/classes:classes
     *     com.sun.honeycomb.resources.ByteArraysTest
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    public void setUp() {
    }

    public void tearDown() {
    }

    public void testPutInt() {
        byte[] bytes = new byte[8];

        int first = 12345;
        int second = 67890;

        ByteArrays.putInt(first, bytes, 0);
        ByteArrays.putInt(second, bytes, 4);

        int firstRead = ByteArrays.getInt(bytes, 0);
        int secondRead = ByteArrays.getInt(bytes, 4);

        if (firstRead != first) {
            fail("read value doesn't match written: written = " +
                 first +
                 " read = " +
                 firstRead);
        }

        if (secondRead != second) {
            fail("read value doesn't match written: written = " +
                 second +
                 " read = " +
                 secondRead);
        }
    }
}
