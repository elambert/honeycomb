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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.resources.ByteBufferPool;


/**
 * Wrapper class for native implementation of the SHA1 digest
 */
public final class Sha1Algorithm extends ContentHashAlgorithm {
    private static final Logger logger = 
        Logger.getLogger(Sha1Algorithm.class.getName());

    /** The length of the SHA1 digest in bytes */
    private static final int digestLength = 20;

    /** Native library name */    
    private static final String nativeLibrary = "nativesha1";

    /**
     * Static section to load the native library.
     */
    static {
        try {
            System.loadLibrary(nativeLibrary);
        } catch(UnsatisfiedLinkError ule) {
            logger.log(Level.SEVERE, "Check LD_LIBRARY_PATH. Can't find " +
                       System.mapLibraryName(nativeLibrary) + " in " +
                       System.getProperty("java.library.path"));
        }
    }

    /** Instance of the hash algorithm */
    private static ContentHashAlgorithm algorithmInstance = null;

    /**
     * Factory method to create an instance of the SHA1 algorithm.
     *
     * @return ContentHashAlgorithm the SHA1 algorithm instance
     */
    public static ContentHashAlgorithm getInstance() {
        synchronized(logger) {
            if (algorithmInstance == null) {
                // This is the first time getInstance has been called
                algorithmInstance = new Sha1Algorithm();
            }
        }
        return algorithmInstance;
    }

    /**
     * Interface to create a context for the hash algorithm which represents
     * its internal state.
     *
     * @return ContentHashContext the codable internal state
     */
    public static ContentHashContext createContext() {
        // Allocate and initialize the native context
        ByteBuffer nativeHashContext = null;
        
        nativeHashContext = 
            ByteBufferPool.getInstance().
            checkOutBuffer(getNativeContextSize());
        
        initializeContext(nativeHashContext);
        
        ContentHashContext res = 
            new ContentHashContext(ContentHashAlgorithm.SHA1,
                                   nativeHashContext);
        
        // NOTE: Context checks out a copy of this buffer so we check this in
        ByteBufferPool.getInstance().checkInBuffer(nativeHashContext);

        return res;
    }
    
    /**
     * Interface to return the length of the content hash.
     *
     * @return int the number of bytes in the content hash
     */
    public int getContentHashLength(ContentHashContext context) {
        return digestLength;
    }
    
    /**
     * Interface to return the final content hash digest.
     *
     * @return byte[] the digest byte array
     */
    public byte[] digest(ContentHashContext context) {
        byte[] result = new byte[digestLength];
        digest(result, context.hashContext);
        return result;
    }

    /**
     * Interface to rest the state of the content hash.
     *
     * @param context the internal state of the content hash algorithm
     */
    public void resetContext(ContentHashContext context) {
        initializeContext(context.hashContext);
    }

    /**
     * Interface to update the internal state of the algorithm with data.
     *
     * @param buffer the byte buffer that contains the data
     * @param offset the start offset of the data
     * @param length the number of bytes to process
     * @param context the context of the content hash
     */
    public native void update(ByteBuffer buffer,
                              long offset,
                              long length,
                              ByteBuffer context);

    /**
     * Native method to get the size of the native context.
     *
     * @return int the size of the native context
     */
    private static native int getNativeContextSize();

    /**
     * Native method to initialize the context.
     *
     * @param nativeContext the memory to use for initializing the context
     */
    private static native void initializeContext(ByteBuffer nativeContext);

    /**
     * Native method to get the digest of the native hash context.
     *
     * @param result the byte buffer to get the context in
     * @param nativeContext the native context to use for the digest
     */
    private static native void digest(byte[] result, ByteBuffer nativeContext);

    public String getHashAlgorithmName() { return("sha1"); }
}
