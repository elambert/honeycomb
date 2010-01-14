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



package com.sun.honeycomb.oa;

import java.nio.ByteBuffer;

import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;

public final class FragmentFooterTest {
    private static void testEncodingAndDecoding(FragmentFooter footer) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(footer.SIZE);
        ByteBufferCoder encoder = new ByteBufferCoder(buffer, false);

        System.out.println("Encoding context...");
        encoder.encodeKnownClassCodable(footer);
        buffer.flip();

        if (buffer.remaining() != footer.SIZE) {
            System.out.println("Bad FragmentFooter size. Expected [" +
                               footer.SIZE + "] got [" + buffer.remaining() +
                               "]");
            return;
        }

        System.out.println(footer);

        ByteBufferCoder decoder = new ByteBufferCoder(buffer, false);
        FragmentFooter anotherFooter = new FragmentFooter();
        decoder.decodeKnownClassCodable(anotherFooter);
        if (!anotherFooter.isConsistent()) {
            System.out.println("FAILED: Footer is corrupt");
        }

        System.out.println(anotherFooter);

        if (footer.equals(anotherFooter)) {
            System.out.println("PASSED: Both footers match...");
        } else {
            System.out.println("FAILED: The footers differ...");
        }
    }

    public static void main(String[] args) {
        FragmentFooter footer = new FragmentFooter
            (new NewObjectIdentifier(1, (byte)2, 3),
             new NewObjectIdentifier(4, (byte)5, 6),
             1024,
             1,
             System.currentTimeMillis(),
             System.currentTimeMillis() + 10000,
             System.currentTimeMillis() + 20000,
             System.currentTimeMillis() + 30000,
             System.currentTimeMillis() + 40000,
             (byte)9,
             ChecksumAlgorithm.ADLER32,
             new ObjectReliability(6, 4),
             64*1024,
             2173);

        testEncodingAndDecoding(footer);

        /*
        // Fill in some checksum blocks in the context and try again
        for (int i=0; i<10; i++) {
            context.checksumBlocks.add(algorithm.createChecksumBlock());
        }
        testContextEncodingAndDecoding(context);
        */
    }
}
