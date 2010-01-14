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

import com.sun.honeycomb.resources.ByteBufferPool;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.BitSet;

public class ByteBufferCoder implements Encoder, Decoder {

    private static final int MAGIC_NUMBER = 0xcafebabe;
    private static final int ARCHIVE_VERSION = 1;

    private static final int UNKNOWN_LENGTH = -1;
    private static final String UTF8_ENCODING = "UTF8";

    private static final byte BOOLEAN_TYPE = 'n';
    private static final byte BUFFER_TYPE = 'r';
    private static final byte BYTE_TYPE = 'c';
    private static final byte BYTES_TYPE = '*';
    private static final byte CODABLE_TYPE = '@';
    private static final byte DATE_TYPE = 't';
    private static final byte FLOAT_TYPE = 'f';
    private static final byte INT_TYPE = 'd';
    private static final byte LONG_TYPE = 'l';
    private static final byte NULL_TYPE = '0';
    private static final byte SHORT_TYPE ='k';
    private static final byte STRING_TYPE = 's';
    private static final byte BITSET_TYPE = 'm';
    private static final byte PACKEDBITSET_TYPE = 'p';

    private static final int NULL_VALUE = -1;
    private static final byte FALSE_VALUE = 0;
    private static final byte TRUE_VALUE = 1;

    private ByteBuffer buffer;
    private HashMap numbersByClass;
    private ArrayList classesByNumber;
    private boolean headerDone;
    private boolean checkTypes;
    private Decoder.Delegate delegate;

    public ByteBufferCoder(ByteBuffer newBuffer) {
        this(newBuffer, false, null);
    }

    public ByteBufferCoder(ByteBuffer newBuffer, Decoder.Delegate newDelegate) {
        this(newBuffer, true, newDelegate);
    }

    public ByteBufferCoder(ByteBuffer newBuffer, boolean check) {
        this(newBuffer, check, null);
    }

    public ByteBufferCoder(ByteBuffer newBuffer,
                           boolean check,
                           Decoder.Delegate newDelegate) {
        buffer = newBuffer;
        checkTypes = check;
        delegate = newDelegate;
        headerDone = false;
    }            

    public static void encodeCodable(Codable value, ByteBuffer buffer) {
        new ByteBufferCoder(buffer).encodeCodable(value);
    }

    public static Codable decodeCodable(ByteBuffer buffer) {
        return new ByteBufferCoder(buffer).decodeCodable();
    }

    private void checkHeaderEncoded() {
        if (checkTypes && !headerDone) {
            headerDone = true;
            encodeHeader();
        }
    }

    private void checkHeaderDecoded() {
        if (checkTypes && !headerDone) {
            headerDone = true;
            decodeHeader();
        }
    }

    private void encodeHeader() {
        buffer.putInt(MAGIC_NUMBER);
        buffer.putInt(ARCHIVE_VERSION);
    }

    private void decodeHeader() {
        int number = buffer.getInt();
        if (number != MAGIC_NUMBER) {
            throw new CodingException("not a ByteBufferCoder archive - " +
                                      "found number " +
                                      number +
                                      " but expected " +
                                      MAGIC_NUMBER);
        }

        int version = buffer.getInt();
        if (version != ARCHIVE_VERSION) {
            throw new CodingException("attempt to read archive version " +
                                      version +
                                      " with class version " +
                                      ARCHIVE_VERSION);
        }
    }

    private void encodeType(byte type) {
        if (checkTypes) {
            buffer.put(type);
        }
    }

    private void checkType(byte expected) {
        if (checkTypes) {
            byte actual = buffer.get();

            if (actual != expected) {
                throw new CodingException("attempt to read incorrect type " +
                                          "from data (expected = " +
                                          expected +
                                          ", actual = " +
                                          actual +
                                          ")");
            }
        }
    }

    private void encodeUTF(String string, boolean encodeLength) {
	byte[] bytes = null;

        try {
	    bytes = string.getBytes(UTF8_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new CodingException("unsupported encoding: " + UTF8_ENCODING);
        }
	
        if (encodeLength) {
            buffer.putInt(bytes.length);
        }
	buffer.put(bytes);
    }
    
    private String decodeUTF(int length) {
        int actualLength = (length == UNKNOWN_LENGTH) ? buffer.getInt() : length;
        byte[] bytes = new byte[actualLength];

        buffer.get(bytes);

        try {
            return new String(bytes, 0, actualLength, UTF8_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new CodingException("unsupported encoding: " + UTF8_ENCODING);
        }
    }

    private void encodeClass(Class claz) {
        if (claz == null) {
            buffer.putInt(NULL_VALUE);
            return;
        }

        Integer number = null;

        if (numbersByClass == null) {
            numbersByClass = new HashMap();
        } else {
            number = (Integer)numbersByClass.get(claz);
        }

        if (number != null) {
            buffer.putInt(number.intValue());            
        } else {
            number = new Integer(numbersByClass.size());
            numbersByClass.put(claz, number);

            buffer.putInt(number.intValue());
            encodeUTF(claz.getName(), true);
        }
    }

    private Class decodeClass() {
        return (Class)decodeClassOrName();
    }

    private String decodeClassName() {
        return (String)decodeClassOrName();
    }

    private Object decodeClassOrName() {
        int number = buffer.getInt();

        if (number == NULL_VALUE) {
            return null;
        }

        if (classesByNumber == null) {
            classesByNumber = new ArrayList();
        }

        Object result;

        if (number < classesByNumber.size()) {
            result = classesByNumber.get(number);
        } else {
            String name = decodeUTF(UNKNOWN_LENGTH);

            if (delegate != null) {
                result = name;
            } else {
                try {
                    result = Class.forName(name);
                } catch (ClassNotFoundException e) {
                    throw new CodingException("class not found: " + name);
                }
            }

            classesByNumber.add(result);
        }

        return result;
    }    

    public void encodeCodable(Codable value) {
        checkHeaderEncoded();
        encodeType(CODABLE_TYPE);

        if (value == null) {
            encodeClass(null);
        } else {
            encodeClass(value.getClass());
            value.encode(this);
        }
    }

    public Codable decodeCodable() {
        checkHeaderDecoded();
        checkType(CODABLE_TYPE);

        String className = null;
        Codable result = null;

        try {
            if (delegate != null) {
                className = decodeClassName();

                if (className != null) {
                    result = delegate.newInstance(className);
                }
            } else {
                Class claz = decodeClass();

                if (claz != null) {
                    className = claz.getName();
                    result = (Codable)claz.newInstance();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new CodingException("instantiation failed for class " +
                                      className);
        } catch (InstantiationException e) {
            throw new CodingException("instantiation failed for class " +
                                      className);
        } catch (IllegalAccessException e) {
            throw new CodingException("access failed for class " +
                                      className);
        }

        if (result != null) {
            result.decode(this);
        }

        return result;
    }

    public void encodeKnownClassCodable(Codable value) {
        checkHeaderEncoded();
        encodeType(CODABLE_TYPE);

        if (value == null) {
            buffer.put(FALSE_VALUE);
        } else {
            buffer.put(TRUE_VALUE);
            value.encode(this);
        }
    }

    public void decodeKnownClassCodable(Codable value) {
        checkHeaderDecoded();
        checkType(CODABLE_TYPE);

        if (buffer.get() == TRUE_VALUE) {
            value.decode(this);
        }
    }

    public void encodeString(String value) {
        checkHeaderEncoded();
        encodeType(STRING_TYPE);

        encodeUTF(value, true);
    }

    public String decodeString() {
        checkHeaderDecoded();
        checkType(STRING_TYPE);

        return decodeUTF(UNKNOWN_LENGTH);
    }

    public void encodeKnownLengthString(String value) {
        checkHeaderEncoded();
        encodeType(STRING_TYPE);

        encodeUTF(value, false);
    }

    public String decodeKnownLengthString(int length) {
        checkHeaderDecoded();
        checkType(STRING_TYPE);
        
        return decodeUTF(length);
    }

    public void encodeDate(Date value) {
        checkHeaderEncoded();
        encodeType(DATE_TYPE);

        buffer.putLong(value.getTime());
    }

    public Date decodeDate() {
        checkHeaderDecoded();
        checkType(DATE_TYPE);

        return new Date(buffer.getLong());
    }
    
    public void encodeShort(short value) {
        checkHeaderEncoded();
        encodeType(SHORT_TYPE);

        buffer.putShort(value);
    }

    public short decodeShort() {
        checkHeaderDecoded();
        checkType(SHORT_TYPE);
        
        return buffer.getShort();
    }

    public void encodeInt(int value) {
        checkHeaderEncoded();
        encodeType(INT_TYPE);

        buffer.putInt(value);
    }

    public int decodeInt() {
        checkHeaderDecoded();
        checkType(INT_TYPE);

        return buffer.getInt();
    }

    public void encodeLong(long value) {
        checkHeaderEncoded();
        encodeType(LONG_TYPE);

        buffer.putLong(value);
    }

    public long decodeLong() {
        checkHeaderDecoded();
        checkType(LONG_TYPE);

        return buffer.getLong();
    }

    public void encodeFloat(float value) {
        checkHeaderEncoded();
        encodeType(FLOAT_TYPE);

        buffer.putFloat(value);
    }

    public float decodeFloat() {
        checkHeaderEncoded();
        checkType(FLOAT_TYPE);

        return buffer.getFloat();
    }

    public void encodeBoolean(boolean value) {
        checkHeaderEncoded();
        encodeType(BOOLEAN_TYPE);

        buffer.put(value ? TRUE_VALUE : FALSE_VALUE);
    }

    public boolean decodeBoolean() {
        checkHeaderDecoded();
        checkType(BOOLEAN_TYPE);

        return (buffer.get() == TRUE_VALUE);
    }

    public void encodeByte(byte value) {
        checkHeaderEncoded();
        encodeType(BYTE_TYPE);

        buffer.put(value);
    }

    public byte decodeByte() {
        checkHeaderDecoded();
        checkType(BYTE_TYPE);

        return buffer.get();
    }

    public void encodeBytes(byte[] value) {
        encodeBytes(value, 0, (value != null) ? value.length : 0, true);
    }

    public void encodeBytes(byte[] value, int off, int len) {
        encodeBytes(value, off, len, true);
    }

    public byte[] decodeBytes() {
        return decodeBytes(UNKNOWN_LENGTH);
    }

    public void encodeKnownLengthBytes(byte[] value) {
        encodeBytes(value, 0, (value != null) ? value.length : 0, false);
    }

    public void encodeKnownLengthBytes(byte[] value, int off, int len) {
        encodeBytes(value, off, len, false);
    }

    public byte[] decodeKnownLengthBytes(int length) {
        return decodeBytes(length);
    }

    private void encodeBytes(byte[] value, int off, int len, boolean encodeLength) {
        checkHeaderEncoded();
        encodeType(BYTES_TYPE);

        if (encodeLength) {
            if (value == null) {
                buffer.put(FALSE_VALUE);
            } else {
                buffer.put(TRUE_VALUE);
                buffer.putInt(len);
            }
        }

        if (value != null) {
            buffer.put(value, off, len);
        }
    }

    private byte[] decodeBytes(int length) {
        checkHeaderDecoded();
        checkType(BYTES_TYPE);

        if (length == UNKNOWN_LENGTH && buffer.get() == TRUE_VALUE) {
            length = buffer.getInt();
        }

        byte[] result = null;
        if (length != UNKNOWN_LENGTH) {
            result = new byte[length];

            if (length > 0) {
                buffer.get(result);
            }
        }

        return result;
    }

    public void encodeBuffer(ByteBuffer value) {
        encodeBuffer(value, true);
    }

    public ByteBuffer decodeBuffer(boolean isDirect) {
        return decodeBuffer(UNKNOWN_LENGTH, isDirect);
    }

    public void encodeKnownLengthBuffer(ByteBuffer value) {
        encodeBuffer(value, false);
    }

    public ByteBuffer decodeKnownLengthBuffer(int length, boolean isDirect) {
        return decodeBuffer(length, isDirect);
    }

    private void encodeBuffer(ByteBuffer value, boolean encodeLength) {
        checkHeaderEncoded();
        encodeType(BUFFER_TYPE);

        if (value == null) {
            buffer.put(FALSE_VALUE);
        } else {
            buffer.put(TRUE_VALUE);
            if (encodeLength) {
                buffer.putInt(value.remaining());
            }

            buffer.put(value);
            value.rewind();
        }
    }

    private ByteBuffer decodeBuffer(int length, boolean isDirect) {
        checkHeaderDecoded();
        checkType(BUFFER_TYPE);

        ByteBuffer result = null;
        if (buffer.get() == TRUE_VALUE) {
            if (length == UNKNOWN_LENGTH) {
                length = buffer.getInt();
            }

            result = (isDirect)
                   ? ByteBufferPool.getInstance().checkOutBuffer(length)
                   : ByteBuffer.allocate(length);

            if (length > 0) {
                byte[] bytes = new byte[length];
                buffer.get(bytes);

                result.put(bytes);
                result.rewind();
            }
        }

        return result;
    }

    public void encodeBitSet(BitSet value) {
        checkHeaderEncoded();
        encodeType(BITSET_TYPE);
        encodeInt(value.length());
        for(int i=0;i<value.length();i++) {
            encodeBoolean(value.get(i));
        }
    }

    public BitSet decodeBitSet() {
        checkHeaderDecoded();
        checkType(BITSET_TYPE);
        int length = decodeInt();
        BitSet result = new BitSet(length);
        for(int i=0;i<length;i++) {
            result.set(i, decodeBoolean());
        }
        return result;
    }
    
    // value.size() must be a multiple of 8 - no padding
    public void encodePackedBitSet(BitSet value) {
	if(value.size() % 8 != 0) {
	    throw new IllegalArgumentException("encodePackedButSet requires value.size() % 8 = 0");
	}
        checkHeaderEncoded();
        encodeType(PACKEDBITSET_TYPE);
        encodeInt(value.size() / 8); // size is in bytes, not bits!
        int bi = 0;
	int b = 0;
	for(int i = 0; i < value.size(); i++) { // for each bit...
	    
	    // pack 8 bits at a time together...
	    int bv = value.get(i) ? 1 : 0;
	    b = b | (bv << (7 - bi));

	    // and every 8th, write them out as one byte
	    if(++bi == 8) { 
		buffer.put((byte)b);
		b = 0;
		bi = 0;
	    }
        }
    }
  
    public BitSet decodePackedBitSet() {
        checkHeaderDecoded();
        checkType(PACKEDBITSET_TYPE);
        int size = decodeInt();
	int bitSetSize = size * 8;
	if (bitSetSize < 0) {
	    throw new IllegalStateException("Negative array size " + size);
	}
        BitSet result = new BitSet(bitSetSize);
	result.clear();
        for(int i = 0; i < size; i++) {
	    
	    // read in one byte at a time...
	    int read = (int) buffer.get(); 
	    int bitoffset = i*8;
	    
	    // unpack it into 8 bits, and set the 1s in the result
	    for(int biti = 7; biti >= 0; biti--) { 
		if(((read & (1 << biti)) != 0)) {
		    result.set(bitoffset + (7-biti));
		}
	    }
        }
        return result;
    }
    
    public ByteBuffer getBuffer() {
	return buffer;
    }
}
