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

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class handles the unmarshalling 
 * of XDR data types out of a stream.
 */

public class XDRInputStream {
    private static int XDRUNIT = 4;
    private InputStream is;
    boolean debug = false;
    private long readTo = Long.MAX_VALUE;
    private ByteArrayOutputStream baos = null;
    Logger LOGGER = Logger.getLogger(getClass().getName());

    /**
     * Build a new XDR object with input/output streams 
     *
     */
    public XDRInputStream(final InputStream is) {
        if (debug){
            baos = new ByteArrayOutputStream();
            this.is = new InputStream(){
                    public int read() throws IOException{
                        int i = is.read();
                        if (i != -1)
                            baos.write((byte)i);
                        return i;
                    }
                };
        }
        else{
            this.is = is;
        }
    }

    /**
     * Skip a number of bytes.
     * <br>Note that the count is
     * rounded up to the next XDRUNIT.
     *
     */
    public void skip(int count) throws IOException {
        for (int i = 0; i < count; i++)
            read();
    }

    boolean eof = false;

    boolean eof() throws IOException{
        return eof;
    }

    private long count = 0;
    void setReadTo(long n) throws IOException{
        if (readTo != Long.MAX_VALUE && readTo != count){
            
        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("Read only " + count + " of " + readTo +", Skipping " + (readTo - count));
            skip((int) (readTo - count));
        }
        count = 0;
        readTo = n;
    }

    private int read() throws IOException {
        if (count > readTo){
            return -1;
        }
        else{
            int i = is.read();
            if (debug)
                System.err.println(Integer.toHexString(i));
            if (i == -1){
                if (debug){
                    LOGGER.info("dumping to /tmp/debug");
                    FileOutputStream fos = new FileOutputStream("/tmp/debug");
                    fos.write(baos.toByteArray());
                    baos.close();
                }
                throw new RuntimeException ("Read past EOF");
            }
            count++;
            return i;
        }
    }



    byte readByte() throws IOException {
        return (byte) read();
    }

    void readByteArray(byte[] b) throws IOException {
        int len = b.length;
        if (len == 0)
            return;
        int offset = 0;

        while (offset < len){
            int i = is.read(b, offset, len-offset);
            if (i == -1)
                throw new RuntimeException("Read past EOF");
            offset += i;
        }
    }

    int readUnsignedByte() throws IOException {
        return read();
    }

    /**
     * Get an unsigned short 
     *
     * @return integer
     */
    public int readUnsignedShort() throws IOException{
        read();
        read();
        int i =  ((read() & 0xff) << 8  |
                  (read() & 0xff));
        return i;
    }

    /**
     * Get a short 
     *
     * @return integer
     */
    public int readShort() throws IOException{
        int b = read();
        int i = ((b & 0x7f) << 8)  |
            read() & 0xff;
        if ((b & 0x80) == 0)
            return i;
        else
            return -i;
    }

    /**
     * Get an integer 
     *
     * @return integer
     */
    public int readInt() throws IOException{
        int first = read();
        int i = (first & 0x7f) << 24 |
                (read() & 0xff) << 16 |
                (read() & 0xff) << 8  |
                (read() & 0xff);
        if ((first & 0x80) == 0)
            return i;
        else
            return -i;
    }

    /**
     * Read an unsigned integer 
     *
     * <br>Note that Java has no unsigned integer
     * type so we must return it as a long.
     *
     * @return long
     */
    public long readUnsignedInt() throws IOException{
        long l = 0;
        for (int i = 1; i <= 4; i++){
            l = l << 8;
            l |= read();
        }
        return l;
    }



    /**
     * Get a long 
     *
     * @return long
     */
    public long readUnsignedLong() throws IOException{
        long l = 0;
        for (int i=0; i<8; i++){
            l = l << 8;
            l |= read();
        }
        return l;
    }


    /*
     * Note: we have no XDR routines for encoding/decoding
     * unsigned longs.  They exist in XDR but not in Java
     * hence we can't represent them.
     * Best just to use readHyper() and hope the sign bit
     * isn't used.
     */

    /**
     * Get a boolean 
     *
     * @return boolean
     */
    public boolean readBool() throws IOException{
        return (readInt() != 0);
    }

    /**
     * Get a floating point number 
     *
     * @return float
     */
    public float readFloat() throws IOException{
        //--> should this be an unsigned Int?
        return (Float.intBitsToFloat(readInt()));
    }

    /**
     * Read a string
     *
     * @return string
     */
    public String readString() throws IOException{
        //debug = true;
        long len = readUnsignedInt();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // --> Optimize this
        for (long i=0; i< len; i++)
            baos.write((byte)read());
        // Strings are padded to word alignment
        int pad = (int) ((4 - (len % 4)) % 4);
        //System.err.println("Read " + baos.toString() + " " + len + " skipping " + pad);
        skip(pad);
        return baos.toString();
    }
}
