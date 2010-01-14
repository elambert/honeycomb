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



package com.sun.honeycomb.oa.hash;

import java.io.FileInputStream;
import java.nio.ByteBuffer;

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.oa.hash.ContentHashAlgorithm;
import com.sun.honeycomb.oa.hash.ContentHashContext;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

public class CalculateHash {
    private static final int BUFFER_SIZE = 64*1024;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("usage: java CalculateHash <input file>");
            System.exit(1);
        }

        // Open the input file for reading
        FileInputStream inFile = null;
        try {
            inFile = new FileInputStream(args[0]);
        } catch (Exception e) {
            System.out.println("Failed to open file " + args[0] + " E =" + e);
            System.exit(1);
        }

        // Prepare the buffer
        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer buffer = pool.checkOutBuffer(BUFFER_SIZE);
        ByteBufferList list = new ByteBufferList();

        // Prepare the content hash state
        ContentHashContext context =
            ContentHashAlgorithm.createContext(ContentHashAlgorithm.SHA1);
        ContentHashAlgorithm algorithm =
            ContentHashAlgorithm.getInstance(context);

        int bytesProcessed = 0;
        int bytesRead = 0;
        long before = System.currentTimeMillis();
        while (true) {
            // Read the file
            bytesRead = read(inFile, buffer);
            if (bytesRead == 0) {
                break;
            }
            buffer.flip();

            list.appendBuffer(buffer);

            algorithm.update(list, 0, bytesRead, context);

            list.clear();
            buffer.clear();
            bytesProcessed += bytesRead;
        }
        long after = System.currentTimeMillis();
        list.checkInBuffers();
        pool.checkInBuffer(buffer);
        inFile.close();

        System.out.print(ByteArrays.toHexString(algorithm.digest(context)) +
                         "\t" + args[0] +
                         "\nProcessing throughput= ");
        if (after > before) {
            System.out.println((bytesProcessed/(after-before)) + "KB/s");
        } else {
            System.out.println("NA");
        }
    }

    private static int read(FileInputStream in, ByteBuffer buffer)
        throws Exception {
        int bytesRead = 0;
        while (buffer.hasRemaining()) {
            int r = in.getChannel().read(buffer);
            if (r == -1) {
                break;
            }
            bytesRead += r;
        }
        return bytesRead;
    }
}
