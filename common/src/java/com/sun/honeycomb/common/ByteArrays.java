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

import java.util.Arrays;

public class ByteArrays {

    private static final int HEX_RADIX = 16;
    private static final int BITS_PER_NIBBLE = 4;

    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuffer result = new StringBuffer(bytes.length * 2);

        for (int i = 0; i < bytes.length; i++) {
            result.append(Character.forDigit((bytes[i] & 0xF0) >> BITS_PER_NIBBLE,
                                          HEX_RADIX));
            result.append(Character.forDigit(bytes[i] & 0x0F, HEX_RADIX));
        }

        return result.toString();
    }

    public static byte[] toByteArray(String hexString) {
        if (hexString == null) {
            return null;
        }

        int stringLength = hexString.length();
        int dataLength = (stringLength + 1 )/ 2;

        byte[] result = new byte[dataLength];

        for (int i = 0; i < stringLength; i++) {
            char nibbleChar = hexString.charAt((stringLength - 1) - i);
            byte nibble = (byte)Character.digit(nibbleChar, HEX_RADIX);

            if (nibble < 0) {
                throw new IllegalArgumentException("invalid hex character: '" +
                                                   nibbleChar +
                                                   "'");
            }

            result[(dataLength - 1) - (i / 2)] += nibble << BITS_PER_NIBBLE * (i % 2);
        }

        return result;
    }

    public static byte[] toByteArrayLeftJustified(String hexString) {
        if (hexString == null) {
            return null;
        }

        int stringLength = hexString.length();
        int dataLength = (stringLength + 1 )/ 2;

        byte[] result = new byte[dataLength];

        for (int i = 0; i < stringLength; i++) {
            char nibbleChar = hexString.charAt(i);
            byte nibble = (byte)Character.digit(nibbleChar, HEX_RADIX);

            if (nibble < 0) {
                throw new IllegalArgumentException("invalid hex character: '" +
                                                   nibbleChar +
                                                   "'");
            }

            result[i / 2] += nibble << BITS_PER_NIBBLE * ((i+1) % 2);
        }

        return result;
    }

    public static byte[] copy(byte[] bytes) {
        byte[] result = null;

        if (bytes != null) {
            result = new byte[bytes.length];
            System.arraycopy(bytes, 0, result, 0, bytes.length);
        }

        return result;
    }

    public static byte[] copy(byte[] bytes, int start, int length) {
        byte[] result = null;
        
        if (bytes != null) {
            if (length > bytes.length) {
                length = bytes.length; // truncate
            }
            result = new byte[length];
            System.arraycopy(bytes, start, result, 0, length);
        }

        return result;
    }

    // Would be better to pass an argument telling how many of the bytes to
    // incorporate into the hash, then step across the array using only
    // every nth byte.
    public static int hashCode(byte[] bytes) {
        int result = 0;

        if (bytes != null) {
            for (int i = 0; i < bytes.length; i++) {
                result ^= ((int)(bytes[(bytes.length - 1) - i]) << 8 * (i % 4));
            }
        }

        return result;
    }

    public static boolean equals(byte[] bytes, byte[] otherBytes) {
        return ((bytes == null && otherBytes == null) ||
                (bytes != null && otherBytes != null &&
                 Arrays.equals(bytes, otherBytes)));
    }

    // Compares the two byte arrays as if they were a potentially zero-
    // padded number. Bytes decrease in significance from the beginning
    // to the end of the arrays.
    public static int compare(byte[] bytes, byte[] otherBytes) {
        if (bytes == null) {
            return (otherBytes == null) ? 0 : -1;
        } else if (otherBytes == null) {
            return 1;
        }

        int begin = 0;
        while (begin < bytes.length && bytes[begin] == 0) {
            begin++;
        }

        int otherBegin = 0;
        while (otherBegin < otherBytes.length && otherBytes[otherBegin] == 0) {
            otherBegin++;
        }

        int remaining = bytes.length - begin;
        int otherRemaining = otherBytes.length - otherBegin;
        if (remaining < otherRemaining) {
            return -1;
        } else if (remaining > otherRemaining) {
            return 1;
        }

        for (int i = 0; i < remaining; i++) {
            byte value = bytes[begin + i];
            byte otherValue = otherBytes[otherBegin + i];

            if (value < otherValue) {
                return -1;
            } else if (value > otherValue) {
                return 1;
            }
        }

        return 0;
    }

     public static void putShort(short value, byte[] bytes, int offset) {
         bytes[offset] = (byte)(value >> 8);
         bytes[offset + 1] = (byte)(value);
     }
    
     public static short getShort(byte[] bytes, int offset) {
         return (short) ((short)((bytes[offset] & 0xFF) << 8) +
                         (short)((bytes[offset + 1]) & 0xFF));
     }
    
    public static void putInt(int value, byte[] bytes, int offset) {
        bytes[offset] = (byte)(value >> 24);
        bytes[offset + 1] = (byte)(value >> 16);
        bytes[offset + 2] = (byte)(value >> 8);
        bytes[offset + 3] = (byte)(value);
    }

    public static int getInt(byte[] bytes, int offset) {
        return (int)((bytes[offset] & 0xFF) << 24) +
            (int)((bytes[offset + 1] & 0xFF) << 16) +
            (int)((bytes[offset + 2] & 0xFF) << 8) +
            (int)((bytes[offset + 3]) & 0xFF);
    }
}
