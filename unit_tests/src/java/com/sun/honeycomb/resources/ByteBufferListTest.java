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



package com.sun.honeycomb.resources;

import com.sun.honeycomb.resources.ByteBufferPool;
import java.nio.*;
import java.util.*;
import java.util.logging.*;
import junit.framework.*;

public class ByteBufferListTest extends TestCase {

    private static final Logger LOG =
        Logger.getLogger(ByteBufferListTest.class.getName());

    /** 
     * Constructor for this unit test.
     *
     * @param testName the name of the unit test
     */
    public ByteBufferListTest(String testName) {
        super(testName);
    }

    /**
     * Allows unit tests to be run together in a suite.
     * @return a test suite that contains a single test - this one
     */
    public static Test suite() {
        return new TestSuite(ByteBufferListTest.class);
    }

    /** Allows test to be run stand-alone from the command-line.
     *
     * java -Djava.library.path=/opt/honeycomb/lib
     *     -classpath test/lib/junit-3.8.1.jar:test/classes:classes
     *     com.sun.honeycomb.resources.ByteBufferListTest
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    ByteBufferList list;
    int count = 3;

    public void setUp() {
        list = new ByteBufferList();

        ByteBufferPool bufferPool = ByteBufferPool.getInstance();

        for (int i = 0; i < count; i++) {
//            ByteBuffer buffer = ByteBuffer.allocateDirect(4 * (i + 1) + i);
            ByteBuffer buffer = bufferPool.checkOutBuffer(4 * (i + 1) + i);
            int base = (i * (i + 1)) / 2;

            for (int j = 0; j < i + 1; j++) {
                buffer.putInt(base + j);
            }

            buffer.flip();
            list.appendBuffer(buffer);
        }
    }

    public void tearDown() {
        list.clear();
    }

    public void testCapacity() {
        int expectedCapacity = 4 * ((count * (count + 1)) / 2);
        int actualCapacity = list.remaining();

        // test capacity
        if (expectedCapacity != actualCapacity) {
            fail("capacity wrong: expected = " + expectedCapacity + " actual = " + actualCapacity);
        }
    }

    public void testValues() {
        ByteBuffer[] buffers = list.getBuffers();
        int expectedValue = 0;

        for (int i = 0; i < buffers.length; i++) {
            ByteBuffer buffer = buffers[i];

            while (buffer.hasRemaining()) {
                int actualValue = buffer.getInt();
                if (actualValue != expectedValue) {
                    fail("value wrong: expected = " + expectedValue + " actual = " + actualValue);
                }

                expectedValue++;
            }
        }
    }

    public void testRangeSlice() {
        int capacity = list.remaining();

        for (int i = 0; i < capacity; i+= 4) {
            for (int j = 0; j < capacity - i; j += 4) {
                ByteBufferList slice = list.slice(i, j);
                ByteBuffer[] buffers = slice.getBuffers();

                int expectedValue = i / 4;

                for (int k = 0; k < buffers.length; k++) {
                    ByteBuffer buffer = buffers[k];

                    while (buffer.hasRemaining()) {
                        int actualValue = buffer.getInt();
                        if (actualValue != expectedValue) {
                            fail("slice value wrong: expected = " + expectedValue + " actual = " + actualValue + "(slice(" + i + ", " + j + ")");
                        }

                        expectedValue++;
                    }
                }

                slice.clear();
            }
        }
    }

    public void testEvenSlice() {
        int sliceFactor = 4;
        ByteBufferList[] lists = list.slice(sliceFactor);
        int expectedValue = 0;

        for (int i = 0; i < lists.length; i++) {
            ByteBuffer[] buffers = lists[i].getBuffers();

            for (int j = 0; j < buffers.length; j++) {
                ByteBuffer buffer = buffers[j];

                while (buffer.hasRemaining()) {
                    int actualValue = buffer.getInt();

                    if (actualValue != expectedValue) {
                        fail("slice evenly value wrong: expected = " + expectedValue + " actual = " + actualValue + "(slice(" + sliceFactor + ")");
                    }

                    expectedValue++;
                }
            }

            lists[i].clear();
        }
    }

    public void testDuplicate() {
        ByteBufferList old = list;
        list = list.duplicate();

        testValues();
        testRangeSlice();
        testEvenSlice();

        old.clear();
    }

    public void testReadOnly() {
        ByteBufferList old = list;
        list = list.asReadOnlyBuffer();

        testValues();
        testRangeSlice();
        testEvenSlice();

        old.clear();
    }
}
