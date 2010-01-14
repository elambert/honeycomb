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



package com.sun.honeycomb.oa.checksum;

import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

public class ChecksumAlgorithmTest {
    private static ByteBufferPool pool = ByteBufferPool.getInstance();

    private static ByteBufferList allocateBufferList(int numBuffers,
                                                     int bufferSize) {
        ByteBufferList list = new ByteBufferList();
        for (int i=0; i<numBuffers; i++) {
            //list.appendBuffer(ByteBuffer.allocateDirect(bufferSize));
            list.appendBuffer(pool.checkOutBuffer(bufferSize));
        }
        return list;
    }

    private static void clearBufferList(ByteBufferList bufferList) {
        ByteBuffer[] buffers = bufferList.getBuffers();
        for (int i=0; i<buffers.length; i++) {
            buffers[i].clear();
        }
    }

    private static void flipBufferList(ByteBufferList bufferList) {
        ByteBuffer[] buffers = bufferList.getBuffers();
        for (int i=0; i<buffers.length; i++) {
            buffers[i].flip();
        }
    }

    private static void padBufferList(ByteBufferList bufferList) {
        ByteBuffer[] buffers = bufferList.getBuffers();
        for (int i=0; i<buffers.length; i++) {
            while(buffers[i].hasRemaining()) {
                buffers[i].put((byte)0);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Open the files for IO
        FileInputStream inFile = null;
        try {
            inFile = new FileInputStream(args[0]);
        } catch (Exception e) {
            System.out.println("Failed to open input file " + args[0]);
            System.exit(1);
        }

        RandomAccessFile outFile = null;
        FileChannel outChannel = null;
        try {
            outFile = new RandomAccessFile(args[1], "rw");
            outChannel = outFile.getChannel();
        } catch (Exception e) {
            System.out.println("Failed to open output file " + args[1]);
            System.exit(1);
        }
        outChannel.position(0);

        // Create the checksum algorithm processor
        ChecksumAlgorithm algorithm =
            ChecksumAlgorithm.getInstance(ChecksumAlgorithm.ADLER32);
        ChecksumContext context = algorithm.createContext();

        // Allocate the buffers to read the data
        int numBuffers = 4;
        ByteBufferList bufferList =
            allocateBufferList(numBuffers, ChecksumAlgorithm.DATA_BLOCK_SIZE);

        // Read the input file and start generating checksums
        long bytesRead = 0;
        while (true) {
            // Read the data into the buffer list and pad if needed
            System.out.println(bufferList);
            clearBufferList(bufferList);
            System.out.println(bufferList);
            bytesRead = inFile.getChannel().read(bufferList.getBuffers());
            if (bytesRead == -1) {
                break;
            }
            System.out.println(bufferList);
            bufferList.flip();
            System.out.println("Bytes read = " + bytesRead +
                               " bytes remaining = " + bufferList.remaining());
            System.out.println(bufferList);

            // Calculate the checksums of the data
            algorithm.update(bufferList, context);
        }

        inFile.close();
        outFile.close();

        System.out.println(context);
    }
}
