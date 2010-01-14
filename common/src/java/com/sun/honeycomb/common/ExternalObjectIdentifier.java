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

import com.sun.honeycomb.common.ByteArrays;

import java.io.Serializable;
import java.io.DataOutput;
import java.io.DataInput;
import java.io.IOException;

public class ExternalObjectIdentifier implements Serializable, Comparable {

    private final byte[] oid;

    public ExternalObjectIdentifier(String oidStr) {
        this.oid = ByteArrays.toByteArrayLeftJustified(oidStr);
    }

    public ExternalObjectIdentifier(byte[] oidBytes) {
        this.oid = oidBytes;
    }

    public String toString() {
        return ByteArrays.toHexString(oid);
    }

    public byte[] toByteArray() {
        return oid;
    }

    public boolean equals(Object other) {
        return oid.toString().equals(other.toString());
    }

    public int compareTo(Object other) {
        ExternalObjectIdentifier oid = (ExternalObjectIdentifier)other;
        
        return oid.toString().compareTo(other.toString());
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public void serialize(DataOutput dout)
        throws IOException {
        byte[] bytes = toByteArray();
        dout.writeInt(bytes.length);
        if (bytes.length > 0) {
            dout.write(bytes);
        }
    }


    public static ExternalObjectIdentifier deserialize(DataInput din)
        throws IOException {
        int length = din.readInt();
        byte[] bytes = new byte[length];
        if (length > 0) {
            try {
                din.readFully(bytes);
            } catch (IOException e) {
                throw (e);
            }
        }

        return new ExternalObjectIdentifier(bytes);
    }

}
