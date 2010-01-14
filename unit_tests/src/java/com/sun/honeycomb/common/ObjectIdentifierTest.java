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

public class ObjectIdentifierTest extends TestCase {

    private static final Logger LOG =
        Logger.getLogger(ByteArraysTest.class.getName());

    /**
     * Constructor for this unit test.
     *
     * @param testName the name of the unit test
     */
    public ObjectIdentifierTest(String testName) {
        super(testName);
    }

    /**
     * Allows unit tests to be run together in a suite.
     * @return a test suite that contains a single test - this one
     */
    public static Test suite() {
        return new TestSuite(ObjectIdentifierTest.class);
    }

    /** Allows test to be run stand-alone from the command-line.
     *
     * java -Djava.library.path=/opt/honeycomb/lib
     *     -classpath test/lib/junit-3.8.1.jar:test/classes:classes
     *     com.sun.honeycomb.resources.ObjectIdentifierTest
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    public void setUp() {
    }

    public void tearDown() {
    }

    public void testPutToHexString() {
        NewObjectIdentifier first = new NewObjectIdentifier(0, (byte)0, 0);

        String hexString = first.toHexString();
        NewObjectIdentifier second = NewObjectIdentifier.fromHexString(hexString);

        if (!first.equals(second)) {
            fail("oids not equal");
        }

        if (!hexString.equals(second.toHexString())) {
            fail("strings not equal");
        }
    }
}
