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

import java.util.Date;
import java.nio.ByteBuffer;
import java.util.BitSet;

public interface Encoder {

    public void encodeCodable(Codable value);
    public void encodeKnownClassCodable(Codable value);

    public void encodeString(String value);
    public void encodeKnownLengthString(String value);

    public void encodeDate(Date value);

    public void encodeShort(short value);
    public void encodeInt(int value);
    public void encodeLong(long value);
    public void encodeFloat(float value);
    public void encodeBoolean(boolean value);

    public void encodeByte(byte b);
    public void encodeBytes(byte[] value);
    public void encodeBytes(byte[] value, int off, int len);
    
    public void encodeKnownLengthBytes(byte[] value);
    public void encodeKnownLengthBytes(byte[] value, int off, int len);

    public void encodeBuffer(ByteBuffer buffer);
    public void encodeKnownLengthBuffer(ByteBuffer buffer);

    public void encodeBitSet(BitSet value);
    public void encodePackedBitSet(BitSet value);

    public ByteBuffer getBuffer();
}
