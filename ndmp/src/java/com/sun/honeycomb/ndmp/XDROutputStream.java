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



package com.sun.honeycomb.ndmp;

import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/**
 * This class handles the marshalling/unmarshalling of
 * primitive data types into and out of streams.
 * 
 */

public class XDROutputStream {
    private OutputStream os;
    private ByteArrayOutputStream baos = new ByteArrayOutputStream();

    /**
     * Build a new XDR object with input/output streams 
     *
     */
    public XDROutputStream(OutputStream os) {
        this.os = os;
    }

    void writeByte(int b) throws IOException{
        baos.write(b);
    }
    /**
     * write an integer 
     *
     * @param i Integer to write
     */
    public void writeInt(int i) throws IOException{
        baos.write(i >>> 24);
        baos.write(i >> 16);
        baos.write(i >> 8);
        baos.write(i);
    }


    public void writeShort(int i) throws IOException{
        baos.write(i >> 8);
        baos.write(i);
    }

    public void writeUnsignedShort(int i) throws IOException{
        // -- > is this padding right?
        baos.write(0);
        baos.write(0);
        baos.write(i >> 8);
        baos.write(i);
    }


    /**
     * Write unsigned integer 
     *
     * Note that Java has no unsigned integer
     * type so we must submit it as a long.
     *
     * @param i unsigned integer to write
     */
    public void writeUnsignedInt(long i) throws IOException{
        writeUnsignedInt(i, baos);
    }
    public void writeUnsignedInt(long i, OutputStream os) throws IOException{
        os.write((int) (i >> 24 & 0xff));
        os.write((int) (i >> 16 & 0xff));
        os.write((int) (i >> 8 & 0xff));
        os.write((int) (i & 0xff));
    }


    /**
     * write a long 
     *
     * @param i long to write
     */
    public void writeUnsignedLong(long i) throws IOException{
        if (i < 0)
            throw new IllegalArgumentException("Can't write a negative as unsigned " + i);
        baos.write((int) (i >>> 56 & 0xff));
        baos.write((int) (i >> 48 & 0xff));
        baos.write((int) (i >> 40 & 0xff));
        baos.write((int) (i >> 32 & 0xff));
        baos.write((int) (i >> 24 & 0xff));
        baos.write((int) (i >> 16 & 0xff));
        baos.write((int) (i >> 8 & 0xff));
        baos.write((int) (i & 0xff));
    }

    /*
     * Note: we have no XDR routines for encoding/decoding
     * unsigned longs.  They exist in XDR but not in Java
     * hence we can't represent them.
     * Best just to use readHyper() and hope the sign bit
     * isn't used.
     */

    /**
     * Write a boolean 
     *
     * @param b boolean
     */
    public void write(boolean b) throws IOException{
        writeInt(b ? 1 : 0);
    }

    /**
     * Write a floating point number 
     *
     * @param f float
     */
    public void write(float f) throws IOException{
        write(Float.floatToIntBits(f));
    }

    /**
     * Write a string
     *
     * @param s string
     */
    public void writeString(String s) throws IOException{
        byte[] b = s.getBytes();
        baos.write(b);
        // Strings are padded to word alignment
        int pad = (4 - ( b.length % 4)) % 4;
        for (int i = 0; i < pad; i++)
            baos.write(0);
    }

    void sendMessage() throws IOException{
        sendMessage(false);
    }

    void sendMessage(boolean echo) throws IOException{
        sendMessage(echo, false);
    }

    void sendMessage(boolean echo, boolean last) throws IOException{
        byte[] b = baos.toByteArray();
        long l = b.length;
        // Write XDR header with length
        if (last)
            writeUnsignedInt(l & 0x7fffffff, os);
        else
            writeUnsignedInt(l | 1l << 31, os);
        os.write(b, 0, (int) l);
        os.flush();
        if (echo){
            for (int i = 0; i < l; i++)
                System.err.print ((int)b[i] + " ");
            System.err.println ();
        }
        baos.reset();
    }

    public static void main (String[] argv) throws Exception{

        for (int j = 0; j < 10; j++){
            int pad = (4 - ( j % 4)) % 4;
            System.out.println(j + " padding with " + pad);
        }
        String message = "Honeycomb Sun Honeycomb accepted NDMP request";
        int pad = (4 - (message.length() % 4)) % 4;
        System.out.println(message + " " + message.length() + " padded with " + pad);

        if (true) return;
        long i = 32851;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write((int) (i >>> 56 & 0xff));
        baos.write((int) (i >> 48 & 0xff));
        baos.write((int) (i >> 40 & 0xff));
        baos.write((int) (i >> 32 & 0xff));
        baos.write((int) (i >> 24 & 0xff));
        baos.write((int) (i >> 16 & 0xff));
        baos.write((int) (i >> 8 & 0xff));
        baos.write((int) (i & 0xff));
        java.io.FileOutputStream fw = new java.io.FileOutputStream("/tmp/bytes");
        fw.write(baos.toByteArray());
        fw.close();
    }
}


// Unsigned right-shift >>> (JLS 15.19)

//     * identical to the right-shift operator only the left-bits are zero filled
//     * because the left-operand high-order bit is not retained, the sign value can change
//     * if the left-hand operand is positive, the result is the same as a right-shift
//     * if the left-hand operand is negative, the result is equivalent to the left-hand operand right-shifted by the number indicated by the right-hand operand plus two left-shifted by the inverted value of the right-hand operand
//       For example: -16 >>> 2 = (-16 >> 2 ) + ( 2 << ~2 ) = 1,073,741,820 

