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



package com.sun.honeycomb.client;

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.InvalidOidException;
import java.io.DataInput;
import java.io.IOException;
import java.io.Serializable;

/**
 * Instances of <code>ObjectIdentifier</code> uniquely represent objects
 * in a @HoneycombProductName@ store. They are created by the @HoneycombProductName@
 * when objects are stored or metadata records are added to an object, and are
 * returned to the client as part of the result of the store or storeMetadata
 * operation.
 * <p>
 * <code>ObjectIdentifiers</code> can be stored outside of the @HoneycombProductName@ and
 * used later for retrieving objects. External storage can be accomplished
 * using an <code>ObjectIdentifier</code>'s <code>String</code>,
 * representation by invoking the <code>toString</code> method. An
 * instance of <code>ObjectIdentifier</code> can be reconstituted from these
 * externalized forms by using the appropriate constructor.
 */
public class ObjectIdentifier implements Cloneable, Comparable, Serializable {

    // STEPH : Kludge : Already defined in NewObjectIdentifier
    private static final int REPRESENTATION_OFFSET = 1; // 1 byte
    private static final int CELLID_OFFSET         = 2;  // 1 byte
    private static final int SILOLOCATION_OFFSET   = 3;  // 2 bytes
    private byte[] data;

    public final static int OID_LEN = 30;


    /**
     * Initializes a new <code>ObjectIdentifier</code> with a copy of the
     * byte array.
     */
    public ObjectIdentifier(byte[] bytes) {
        this(bytes, true);
    }

    ObjectIdentifier(byte[] bytes, boolean copy) {
        if (bytes == null) {
            throw new InvalidOidException("bytes cannot be null");
        }
        if (bytes.length != OID_LEN) {
            throw new InvalidOidException("Wrong number of bytes in OID " + new String(bytes));
        }
        // Version
        if (bytes[0] != 2) {
            throw new InvalidOidException("Invalid version of OID " + new String(bytes));
        }
        // REPRESENTATION_EXT = 0;
        if (bytes[REPRESENTATION_OFFSET] != 0) {
            throw new InvalidOidException("Invalid format of OID " + new String(bytes));
        }

        data = (copy) ? ByteArrays.copy(bytes) : bytes;
    }

    /**
     * Initializes a new <code>ObjectIdentifier</code> with the byte array
     * represented by the hex string.
     *
     * @throws InvalidOidException if the string isn't a valid OIS.
     */
    public ObjectIdentifier(String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException("string cannot be null");
        }

        data = ByteArrays.toByteArray(hexString);
        if (data.length != OID_LEN) {
            throw new InvalidOidException("Wrong number of bytes in OID (" + 
                                        data.length + ") expected " + OID_LEN);
        }
    }


    private ObjectIdentifier() {};
    public static final ObjectIdentifier ObjectIdentifierEOF  = new ObjectIdentifier();

    static ObjectIdentifier deserialize(DataInput dataIn)
        throws IOException {

        int length = dataIn.readInt();
	if (length == -1){
	    return ObjectIdentifierEOF;
	}
        byte[] bytes = new byte[length];
        dataIn.readFully(bytes);

        return new ObjectIdentifier(bytes, false);
    }

    /**
     * Returns a copy of this identifier's underlying byte array.
     */
    public byte[] getBytes() {
        return ByteArrays.copy(data);
    }


    /**
     * Returns the Cell Id
     */
    protected byte cellId() {
        return data[CELLID_OFFSET];
    }

    protected short siloLocation() {
        return ByteArrays.getShort(data, SILOLOCATION_OFFSET);
    }

    /**
     * Returns hexadecimal string representation of this identifier's
     * underlying byte array.  Returns identical string as <code>toString</code>.
     *
     * @deprecated Use {@link #toString()}
     */
    public String toHexString() {
        return ByteArrays.toHexString(data);
    }

    /**
     * Returns a string representation of this identifier.  Returns identical
     * string as <code>toHexString</code>.
     */
    public String toString() {
        return ByteArrays.toHexString(data);
    }

    /**
     * Returns a hash code value for this identifier.
     */
    public int hashCode() {
        int result = 0;

        for (int i = 0; i < data.length; i++) {
            result ^= ((int)(data[(data.length - 1) - i]) << 8 * (i % 4));
        }

        return result;
    }

    /**
     * Indicates whether the other identifier is "equal to" this one.
     */
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        return (this == other ||
                (other instanceof ObjectIdentifier &&
                 ByteArrays.equals(data, ((ObjectIdentifier)other).data)));
    }

    /**
     * Compares this identifier with the one specified for order. Returns a
     * negative integer, zero, or a positive integer as this identifier is less
     * than, equal to, or greater than the specified one.
     */
    public int compareTo(Object other) {
        if (other == null) {
            return 1;
        }

        if (this == other) {
            return 0;
        }

        if (!(other instanceof ObjectIdentifier)) {
            return -1;
        }

        return ByteArrays.compare(data, ((ObjectIdentifier)other).data);
    }

    /**
     * Returns a copy of this identifier.
     */
    public Object clone() {
        return new ObjectIdentifier(data);
    }
}
