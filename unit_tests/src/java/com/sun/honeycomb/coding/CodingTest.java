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



package com.sun.honeycomb.coding;

import com.sun.honeycomb.coding.*;
import com.sun.honeycomb.coordinator.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.logging.*;
import junit.framework.*;

public class CodingTest extends TestCase {

    private static final Logger LOG =
        Logger.getLogger(CodingTest.class.getName());

    /** 
     * Constructor for this unit test.
     *
     * @param testName the name of the unit test
     */
    public CodingTest(String testName) {
        super(testName);
    }

    /**
     * Allows unit tests to be run together in a suite.
     * @return a test suite that contains a single test - this one
     */
    public static Test suite() {
        return new TestSuite(CodingTest.class);
    }

    /** Allows test to be run stand-alone from the command-line.
     *
     * java -Djava.library.path=/opt/honeycomb/lib
     *     -classpath test/lib/junit-3.8.1.jar:test/classes:classes
     *     com.sun.honeycomb.coding.CodingTest
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    public void setUp() {
    }

    public void tearDown() {
    }

    public void testByteBufferCoder() {
        Persistent3 three = new Persistent3(10,
                                            (float)4.444,
                                            555555555555L,
                                            true,
                                            "this is the fourth persistent string",
                                            new Date(),
                                            new byte[] {10, 20, 30, 40, 50, 60, 70, 80, 90, 100},
                                            null);

        Persistent2 two = new Persistent2(1,
                                          (float)2.222,
                                          333333333333L,
                                          false,
                                          "this is the first persistent string",
                                          new Date(),
                                          new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                                          three,
                                          4,
                                          "this is the second persistent string",
                                          (ByteBuffer)ByteBuffer.allocateDirect(17).putInt(1234).rewind());

        Persistent1 one = new Persistent1(2,
                                          (float)3.333,
                                          4444444444444L,
                                          true,
                                          "this is the third persistent string",
                                          new Date(),
                                          new byte[] {10, 9, 8, 7, 6, 5, 4, 3, 2, 1},
                                          two);

        ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024);
        ByteBufferCoder encoder = new ByteBufferCoder(buffer, true);

        encoder.encodeCodable(one);
        buffer.rewind();

        ByteBufferCoder decoder = new ByteBufferCoder(buffer, true, new Delegate());
        Persistent1 anotherOne = (Persistent1)decoder.decodeCodable();

        if (!one.equals(anotherOne)) {
            fail("unarchived object with type checking does not equal archived one");
        }

        buffer.clear();
        encoder = new ByteBufferCoder(buffer, false);

        encoder.encodeCodable(one);
        buffer.rewind();

        decoder = new ByteBufferCoder(buffer, false, new Delegate());
        anotherOne = (Persistent1)decoder.decodeCodable();

        if (!one.equals(anotherOne)) {
            fail("unarchived object without type checking does not equal archived one");
        }
    }

    public static class Persistent1 implements Codable, Serializable {
        private int int1;
        private float float1;
        private long long1;
        private boolean boolean1;
        private String string1;
        private Date date1;
        private byte[] bytes1;
        private Codable codable1;

        public Persistent1() {
        }

        public Persistent1(int newInt,
                           float newFloat,
                           long newLong,
                           boolean newBoolean,
                           String newString,
                           Date newDate,
                           byte[] newBytes,
                           Codable newCodable) {
            int1 = newInt;
            float1 = newFloat;
            long1 = newLong;
            boolean1 = newBoolean;
            string1 = newString;
            date1 = newDate;
            bytes1 = newBytes;
            codable1 = newCodable;
        }

        public void encode(Encoder encoder) {
            encoder.encodeInt(int1);
            encoder.encodeFloat(float1);
            encoder.encodeLong(long1);
            encoder.encodeBoolean(boolean1);
            encoder.encodeString(string1);
            encoder.encodeDate(date1);
            encoder.encodeBytes(bytes1);
            encoder.encodeCodable(codable1);
        }

        public void decode(Decoder decoder) {
            int1 = decoder.decodeInt();
            float1 = decoder.decodeFloat();
            long1 = decoder.decodeLong();
            boolean1 = decoder.decodeBoolean();
            string1 = decoder.decodeString();
            date1 = decoder.decodeDate();
            bytes1 = decoder.decodeBytes();
            codable1 = decoder.decodeCodable();
        }

        public boolean equals(Object other) {
            Persistent1 otherPersistent1 = (Persistent1)other;

            if (int1 != otherPersistent1.int1) {
                System.out.println("int1 failed");
                return false;
            }

            if (float1 != otherPersistent1.float1) {
                System.out.println("float1 failed");
                return false;
            }

            if (long1 != otherPersistent1.long1) {
                System.out.println("long1 failed");
                return false;
            }

            if (boolean1 != otherPersistent1.boolean1) {
                System.out.println("boolean1 failed");
                return false;
            }

            if (string1 != otherPersistent1.string1 &&
                (string1 != null && !string1.equals(otherPersistent1.string1) &&
                 otherPersistent1.string1 != null)) {
                System.out.println("string1 failed");
                return false;
            }

            if (date1 != otherPersistent1.date1 &&
                (date1 != null && !date1.equals(otherPersistent1.date1) &&
                 otherPersistent1.date1 != null)) {
                System.out.println("date1 failed");
                return false;
            }

            if (bytes1 != otherPersistent1.bytes1 &&
                (bytes1 != null && !Arrays.equals(bytes1, otherPersistent1.bytes1) &&
                 otherPersistent1.bytes1 != null)) {
                System.out.println("bytes1 failed");
                return false;
            }

            if (codable1 != otherPersistent1.codable1 &&
                (codable1 != null && !codable1.equals(otherPersistent1.codable1) &&
                 otherPersistent1.codable1 != null)) {
                System.out.println("codable1 failed");
                return false;
            }

            return true;
        }
    }

    private static class Persistent2 extends Persistent1 {
        private int int2;
        private String string2;
        private ByteBuffer buffer2;

        public Persistent2() {
        }

        public Persistent2(int newInt,
                           float newFloat,
                           long newLong,
                           boolean newBoolean,
                           String newString,
                           Date newDate,
                           byte[] newBytes,
                           Codable newCodable,
                           int newInt2,
                           String newString2,
                           ByteBuffer newBuffer2) {
            super(newInt,
                  newFloat,
                  newLong,
                  newBoolean,
                  newString,
                  newDate,
                  newBytes,
                  newCodable);

            int2 = newInt2;
            string2 = newString2;
            buffer2 = newBuffer2;
        }

        public void encode(Encoder encoder) {
            super.encode(encoder);

            encoder.encodeInt(int2);
            encoder.encodeString(string2);
            encoder.encodeInt(buffer2.remaining());
            encoder.encodeKnownLengthBuffer(buffer2);
        }

        public void decode(Decoder decoder) {
            super.decode(decoder);

            int2 = decoder.decodeInt();
            string2 = decoder.decodeString();

            int length = decoder.decodeInt();
            buffer2 = decoder.decodeKnownLengthBuffer(length, true);
        }

        public boolean equals(Object other) {
            if (!super.equals(other)) {
                return false;
            }

            Persistent2 otherPersistent2 = (Persistent2)other;

            if (int2 != otherPersistent2.int2) {
                System.out.println("int2 failed");
                return false;
            }

            if (string2 != otherPersistent2.string2 &&
                (string2 != null && !string2.equals(otherPersistent2.string2) &&
                 otherPersistent2.string2 != null)) {
                System.out.println("string2 failed");
                return false;
            }

            if (buffer2 != otherPersistent2.buffer2 &&
                (buffer2 != null && !buffer2.equals(otherPersistent2.buffer2) &&
                 otherPersistent2.buffer2 != null)) {
                System.out.println("buffer2 failed");
                return false;
            }

            return true;
        }
    }

    static class Persistent3 extends Persistent1 {

        Persistent3() {
        }

        Persistent3(int newInt,
                    float newFloat,
                    long newLong,
                    boolean newBoolean,
                    String newString,
                    Date newDate,
                    byte[] newBytes,
                    Codable newCodable) {
            super(newInt,
                  newFloat,
                  newLong,
                  newBoolean,
                  newString,
                  newDate,
                  newBytes,
                  newCodable);
        }
    }

    static class Delegate implements Decoder.Delegate {
        public Codable newInstance(String className)
            throws ClassNotFoundException,
                   IllegalAccessException,
                   InstantiationException {
            return (Codable)Class.forName(className).newInstance();
        }
    }
}
