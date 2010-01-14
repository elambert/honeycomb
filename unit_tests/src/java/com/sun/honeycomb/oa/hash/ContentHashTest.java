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



package com.sun.honeycomb.oa.hash;

import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.Arrays;

import junit.framework.*;

import com.sun.honeycomb.oa.hash.ContentHashAlgorithm;
import com.sun.honeycomb.oa.hash.ContentHashContext;

import com.sun.honeycomb.resources.ByteBufferList;

public class ContentHashTest extends TestCase {
    private static final Logger logger =
        Logger.getLogger(ContentHashTest.class.getName());

    public ContentHashTest(String testName) {
        super(testName);
    }

    /**
     * Allows unit tests to be run together in a suite.
     * @return a test suite that contains a single test - this one
     */
    public static Test suite() {
        return new TestSuite(ContentHashTest.class);
    }

    /** Allows test to be run stand-alone from the command-line.
     *
     * java -Djava.library.path=/opt/honeycomb/lib
     *     -classpath test/lib/junit-3.8.1.jar:test/classes:classes
     *     com.sun.honeycomb.oa.hash.ContentHashTest
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    public void setUp() {
    }

    public void tearDown() {
    }

    public void testInstance() {
        logger.info("Test instance");

        ContentHashContext context =
            ContentHashAlgorithm.createContext(ContentHashAlgorithm.SHA1);
        ContentHashAlgorithm algorithm =
            ContentHashAlgorithm.getInstance(context);

        algorithm.resetContext(context);

        ByteBufferList buffers = new ByteBufferList();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        byte[] byteArray = new byte[1024];
        Arrays.fill(byteArray, (byte)0);
        buffer.put(byteArray);
        buffers.appendBuffer(buffer);

        algorithm.update(buffers, 0, 1024, context);

        byte[] digest = algorithm.digest(context);
        logger.info(new String(digest));
    }
}
