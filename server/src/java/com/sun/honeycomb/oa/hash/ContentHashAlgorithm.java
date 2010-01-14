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

import java.nio.ByteBuffer;

import com.sun.honeycomb.resources.ByteBufferList;

/**
 *
 */
public abstract class ContentHashAlgorithm {
    /** Supported content hash algorithms */
    public static final int SHA1 = 1;

    /**
     * Factory method to create a content hash processor which can be used
     * along with a context to process the content hash of data.
     *
     * @param algorithm the algorithm to use
     * @return ContentHashAlgorithm the instance of the algorithm
     */
    public static ContentHashAlgorithm getInstance(int algorithm)
        throws IllegalArgumentException {
        if (algorithm == SHA1) {
            return Sha1Algorithm.getInstance();
        } else {
            throw new IllegalArgumentException("Invalid algorithm [" +
                                               algorithm + "]");
        }
    }

    /**
     * Factory method to create a content hash processor which can be used
     * along with a context to process the content hash of data.
     *
     * @param context the hash context
     * @return ContentHashAlgorithm the instance of the algorithm
     */
    public static ContentHashAlgorithm getInstance(ContentHashContext context)
        throws IllegalArgumentException {
        return getInstance(context.hashAlgorithm);
    }

    /*
     * Interface to create a context for the hash algorithm which represents
     * its internal state.
     *
     * @return ContentHashContext the codable internal state
     */
    public static ContentHashContext createContext(int algorithm)
        throws IllegalArgumentException {
        if (algorithm == SHA1) {
            return Sha1Algorithm.createContext();
        } else {
            throw new IllegalArgumentException("Invalid algorithm [" +
                                               algorithm + "]");
        }
    }

    /**
     * Interface to update the internal state of the algorithm with data.
     *
     * @param buffers the list of byte buffers that contains the data
     * @param offset the start offset of the data
     * @param length the number of bytes to process
     * @param context the context of the content hash
     */
    public void update(ByteBufferList buffers,
                       long offset,
                       long length,
                       ContentHashContext context) {
        // Return if the length is zero or less
        if (length <= 0) {
            return;
        }

        ByteBuffer[] array = buffers.getBuffers();

        // Call the update method with single ByteBuffers
        // TBD: The native method should take an array of byte buffers to
        //      reduce the number of native calls.
        long relativeOffset = 0;
        long numProcessed = 0;
        long fromOffset = 0;
        long toProcess = 0;
        for (int i=0; i<array.length; i++) {
            // Skip buffers that are completely before the offset to process
            if (offset >= (relativeOffset + array[i].remaining())) {
                relativeOffset += array[i].remaining();
                continue;
            }

            // Find out where in the buffer to start the processing
            if (relativeOffset < offset) {
                fromOffset = offset - relativeOffset;
            } else {
                fromOffset = 0;
            }

            // Find out how much of the buffer to process
            if ((array[i].remaining() - fromOffset)<(length - numProcessed)) {
                toProcess = array[i].remaining() - fromOffset;
            } else {
                toProcess = length - numProcessed;
            }

            // Process the current buffer
            update(array[i], fromOffset, toProcess, context.hashContext);

            numProcessed += toProcess;
            relativeOffset += array[i].remaining();

            // Break out of the loop if the required data has been processed
            if (numProcessed == length) {
                break;
            }
        }
    }

    /**
     * Interface to return the length of the content hash.
     *
     * @return int the number of bytes in the content hash
     */
    public abstract int getContentHashLength(ContentHashContext context);

    /**
     * Interface to return the final content hash digest.
     *
     * @param context the internal state of the content hash algorithm
     * @return byte[] the digest byte array
     */
    public abstract byte[] digest(ContentHashContext context);

    /**
     * Interface to rest the state of the content hash.
     *
     * @param context the internal state of the content hash algorithm
     */
    public abstract void resetContext(ContentHashContext context);

    /**
     * Interface to update the internal state of the algorithm with data.
     *
     * @param buffer the byte buffer that contains the data
     * @param offset the start offset of the data
     * @param length the number of bytes to process
     * @param context the context of the content hash
     */
    public abstract void update(ByteBuffer buffer,
                                long offset,
                                long length,
                                ByteBuffer context);

    /**
     * Interface to get a string that represents the hash algorithm name
     */
    public abstract String getHashAlgorithmName();
}
