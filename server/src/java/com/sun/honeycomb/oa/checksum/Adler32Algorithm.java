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

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Adler32Algorithm extends ChecksumAlgorithm {
    private static final Logger logger = 
        Logger.getLogger(Adler32Algorithm.class.getName());

    private static final String nativeLibrary = "nativeadler32";

    static {
        try {
            System.loadLibrary(nativeLibrary);
        } catch(UnsatisfiedLinkError ule) {
            logger.log(Level.SEVERE, "Check LD_LIBRARY_PATH. Can't find " +
                       System.mapLibraryName(nativeLibrary) + " in " +
                       System.getProperty("java.library.path"));
        }
    }

    /** Size of the checksum in bytes */
    private static final int CHECKSUM_SIZE = 4;

    /** Instance of the checksum algorithm */
    private static ChecksumAlgorithm algorithmInstance = null;

    /**
     * Factory method to create an instance of the Adler32 algorithm.
     *
     * @return ChecksumAlgorithm the Adler32 algorithm instance
     */
    public static ChecksumAlgorithm getInstance() {
        synchronized(logger) {
            if (algorithmInstance == null) {
                // This is the first time getInstance has been called
                algorithmInstance = new Adler32Algorithm();
            }
        }
        return algorithmInstance;
    }

    /**
     * Interface to create a context for the checksum algorithm which
     * represents its internal state.
     *
     * @return ChecksumContext the codable internal state
     */
    public ChecksumContext createContext() {
        return new ChecksumContext(ChecksumAlgorithm.ADLER32);
    }

    public void initialize(AlgorithmState internalState) {
        internalState.state = nativeInitialize();
    }

    public void update(ByteBuffer buffer,
                       int length,
                       AlgorithmState internalState) {
        internalState.state = nativeUpdate(buffer,
                                           buffer.position(),
                                           length,
                                           internalState.state);
        // Increment the byte buffer's position as the native method does
        // not increment it.
        buffer.position(buffer.position() + length);
    }

    public long getLongValue(AlgorithmState internalState) {
        return internalState.state;
    }

    public int getIntValue(AlgorithmState internalState) {
        return (int)internalState.state;
    }

    public short getShortValue(AlgorithmState internalState) {
        return (short)internalState.state;
    }

    public byte getByteValue(AlgorithmState internalState) {
        return (byte)internalState.state;
    }

    public int getChecksumSize() {
        return CHECKSUM_SIZE;
    }

    private static native long nativeInitialize();
    private static native long nativeUpdate(ByteBuffer buffer,
                                            int offset,
                                            int length,
                                            long state);
}
