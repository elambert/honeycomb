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


import java.util.logging.Logger;
import java.nio.ByteBuffer;

import com.sun.honeycomb.oa.hash.ContentHashAlgorithm;
import com.sun.honeycomb.oa.hash.ContentHashContext;
import com.sun.honeycomb.oa.erasure.ErasureAlgorithm;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
import com.sun.honeycomb.oa.checksum.ChecksumContext;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

/**
 * The Fragmenter class breaks data into fragments for storage to
 * disks, and defragments data read from disk.  It encasulates the
 * Reed Solomon erasure encoding, checksum and SHA1 calculation.  
 * TODO  - Checksums need to actually be written
 */
public class Fragmenter {
    
    
    // SINGLETON METHODS //
    
    /**
     * Fragmenter is a singleton.  The first time this method is
     * called, an instance is created and returned.  Subsequent calls
     * return a reference to the same object.
     */
    public static Fragmenter getInstance() {
        synchronized(LOG) {
            if (fragmenter == null) {
                // This is the first time getInstance has been called
                fragmenter = new Fragmenter();
            }
        }
        return fragmenter;
    }

    /** 
     * This static method can be used to see whether the Fragmenter
     * singleton has been instantiated yet or not (in other words,
     * whether getInstance has ever been called before or not).
     */
    public static boolean isInstantiated() {
        return !(fragmenter == null);
    }

    public void fragmentAndAppend(ByteBufferList buf,
                                  FragmentFileSet fset,
                                  long blockSize,
                                  long pad,
                                  OAContext oactx) throws OAException {
        // Check arguments
        
        // TODO - check for nulls

        long size = buf.remaining();
        int n = fset.getReliability().getDataFragCount();
        int m =  fset.getReliability().getRedundantFragCount();

        if(size > blockSize) {
            throw new IllegalArgumentException("fragmenter can't handle size "+
                                               size +" larger than blocksize "+
                                               blockSize);
        }

        if(size % n > 0) {
            throw new 
                IllegalArgumentException("n = " + n + "is not factor of size"+
                                         size);
        }
        
        // Okay, size is either a block, or smaller, and a factor of n
        
        int fragSize = -1;
        boolean firstFragFull = false;
        
        if(size < blockSize) {
            // This is short write - the end of an object - we need small frags
            fragSize = (int) (size / (long)n);
            if((size <= MAX_BOTROS_BLOCK_SIZE) && SMALL_FIRST_FRAG_FULL) {
                firstFragFull = true;
            }
        } else {
            // This is a complete block
            fragSize = OAClient.OA_FRAGMENT_SIZE;
        }
        
        // Compute the running content hash and update the context
        ContentHashContext hashContext = oactx.getContentHashContext();
        ContentHashAlgorithm hashAlgorithm =
            ContentHashAlgorithm.getInstance(hashContext);
        hashAlgorithm.update(buf, 0, size-pad, hashContext);

        buf.rewind();

        ErasureAlgorithm erasureAlgorithm =
            ErasureAlgorithm.getInstance(ErasureAlgorithm.REED_SOLOMON);
        
        ByteBufferList fullFrag = null;

        if(firstFragFull) {
            fullFrag = buf.slice(0, buf.remaining());
        }
        
        ByteBufferList[] frags =
            erasureAlgorithm.getDataAndParityBuffers(buf, fragSize, n, m);
        
        if(firstFragFull) {
            ByteBufferPool.getInstance().
                checkInBufferList(frags[frags.length-1]);
            frags[frags.length-1] = null; // to be clear we mean to drop ref.
            for(int f = frags.length-1; f > 0; f--) {
                frags[f] = frags[f-1];
            }
            frags[0] = fullFrag;
        }

        for(int i=0; i<frags.length;i++) {
            frags[i].rewind();
        }

        // Calculate the checksums for all the fragments. Pass each fragment
        // and its context to the checksum algorithm.
        for (int i=0; i<frags.length; i++) {
            ChecksumContext checksumContext =
                fset.getFragmentChecksumContext(i);

            // Calculate checksums if required. The context will be null if
            // there are no checksums to be calculated
            if (checksumContext != null) {
                ChecksumAlgorithm checksumAlgorithm =
                    ChecksumAlgorithm.getInstance(checksumContext);

                // Calculate the checksum for the fragments but do not insert
                // them in the checksum blocks.
                checksumAlgorithm.update(frags[i], checksumContext);
                frags[i].rewind();
            }
        }

        // Write the array
        fset.append(frags);

        // Free the bufferLists that were created in ReedSolomon class
        for(int f=0; f<frags.length; f++) {
            ByteBufferPool.getInstance().checkInBufferList(frags[f]);
        }
    }
    
    /* Contract:
     *   - offset is block aligned
     *   - lenght is n-byte aligned
     *   - shortBlock indiciates whether the read is from < blockSize block
     *   - fullBlockSize is the current OA blockSize (n*fragSize)
     *   - we return # bytes read _including_ padding
     *      NOTE: It is up to OAClient to screen off padding from its result
     *            length - we always work on n-byte boundries
     */  
    public long readAndDefragment(ByteBuffer buf, long offset, long length,
                                  FragmentFileSet fset, boolean shortBlock,
                                  long fullBlockSize, OAContext oactx)
        throws OAException {
        // TODO:only read parity when one or more data fragments fail
        //       exclude pad

        int n = fset.getReliability().getDataFragCount();
        int m =  fset.getReliability().getRedundantFragCount();

        // Check arguments
        
        if(offset % fullBlockSize != 0) {
            throw new 
                IllegalArgumentException("fragmenter can't read at offset " +
                                         offset + " - must be aligned to"  +
                                         fullBlockSize);
        }
        
        // Okay, offset is normal blockSize (fragSize*n) aligned
        
        if(length > fullBlockSize) {
            throw new IllegalArgumentException("fragmenter can't read length "+
                                               length +
                                               " larger than blocksize "+
                                               fullBlockSize);
        }
        
        if(length % n != 0) {
            throw new 
                IllegalArgumentException("n = " + n + "is not factor of size"+
                                         length);
        }
        
        
        // Okay, length is either a reg. block, or small and a factor of n
        
        // TODO - check for nulls
        
        int fragSize = -1;
        boolean firstFragFull = false;
        long blockNum = offset / fullBlockSize;

        if(shortBlock) {
            // This is short block at the end of an object, we need small frags
            fragSize = (int) (length/n);
              if((length <= MAX_BOTROS_BLOCK_SIZE) && SMALL_FIRST_FRAG_FULL) {
                firstFragFull = true;
            }
        } else {
            // This is a complete block
            fragSize = OAClient.OA_FRAGMENT_SIZE;
        }
       
        ByteBuffer[] dataFrags = new ByteBuffer[n];
        ByteBuffer[] parityFrags = new ByteBuffer[m];
        
        // If firstFragFull - try to read it directly as shortcut
        
        int shift = 0;
        if(firstFragFull) {
            shift = 1;
            long fastRead = 0;
            try {
                fastRead = 
                    fset.readSingle(buf, 
                                    OAClient.OA_FRAGMENT_SIZE * blockNum, 
                                    0);
                if(fastRead < length) {
                    // Log the error and reset as though we never read anything
                    LOG.warning("OID [" + fset.getOID() + "] Failed fast " +
                                "BOTROS read, only got " + fastRead +
                                " bytes");
                    buf.position((int)(buf.position()-fastRead)); 
                } else {
                    return length;
                }
            } catch (OAException ie) {
                // Log the error and reset as though we never read anything
                LOG.warning("OID [" + fset.getOID() + "] Failed fast BOTROS " +
                            "read, trying normal: " + ie);
            }
        }
        
        // Fragment buf into n data fragments to recieve data
        for(int d=0; d<n; d++) {
            dataFrags[d] = 
                ByteBufferPool.getInstance().checkOutSlice(buf);
            dataFrags[d].limit(fragSize);
            buf.position(buf.position() + fragSize);
        };
        
        // Allocate buffers for parity fagments
        for(int p=0; p<m; p++) {
            parityFrags[p] = 
                ByteBufferPool.getInstance().checkOutBuffer(fragSize);
        }
        
        // Construct an M+N array
        ByteBuffer[] frags = new ByteBuffer[n + m];
        int f=0;
        
        for(int d=0; d<n;d++,f++) {
            frags[f] = dataFrags[d];
        }
        for(int p=0; p<m;p++,f++) {
            frags[f] = parityFrags[p];
        }
        
        // Set offsets (today they are the same for all frags
        long offsets[] = new long[m+n];
        for(f=0; f<m+n; f++) {
            offsets[f] = OAClient.OA_FRAGMENT_SIZE * blockNum;
        }
        
        // Actually read in, including parity data
        long actuallyRead =  fset.read(frags, offsets, shift);
      
        int errors = fset.getErrors();
        if(firstFragFull) {
            // We already know that the first fragment is an error
            errors++;
        }

        // Throw an exception if there are more than m+1 errors
        if(errors > m+1) {
            // Free up the buffer resources
            for(int d=0; d < dataFrags.length; d++) {
                ByteBufferPool.getInstance().checkInBuffer(dataFrags[d]);
            }
            for(int p=0; p < parityFrags.length; p++) {
                ByteBufferPool.getInstance().checkInBuffer(parityFrags[p]);
            }
            // TODO - throw unreadable object exception
            throw new OAException("OID [" + fset.getOID() + "] Too many " +
                                  "fragment read errors: " + errors);
        }
              
        // If Any errors, do ReedSolomon
        if(errors > 0) {
            
            LOG.info("OID [" + fset.getOID() + "] Using RS to restore " +
                     errors + " fragments");

            boolean[] validFrags = fset.getGoodFragments(shift);
            boolean[] validData = new boolean[n];
            boolean[] validParity = new boolean[m];
            
            f=0;
            for(int d=0; d<n;d++,f++) {
                validData[d] = validFrags[f];
            }
            for(int p=0; p<m;p++,f++) {
                validParity[p] = validFrags[f];
            }
        
            ErasureAlgorithm erasureAlgorithm =
                ErasureAlgorithm.getInstance(ErasureAlgorithm.REED_SOLOMON);
            erasureAlgorithm.reconstructDataFragments(dataFrags, parityFrags,
                                                      validData, validParity,
                                                      fragSize);   

            // It would be nice to cue the DataDoctor to repair this block, 
            // since we have the bits on hand...
        }
        
        for(int d=0; d < dataFrags.length; d++) {
            ByteBufferPool.getInstance().checkInBuffer(dataFrags[d]);
        }
        for(int p=0; p < parityFrags.length; p++) {
            ByteBufferPool.getInstance().checkInBuffer(parityFrags[p]);
        }

        // return how much user data we read + padding (excluding parity)
        return length; // No errors, so we read right amounnt of data
    }
        
    // PRIVATE MEMBERS //
    
    protected static final Logger LOG = 
        Logger.getLogger(Fragmenter.class.getName());
    private static Fragmenter fragmenter = null;
    
    // A Botros block is a block w/ first frag full
    public static final int MAX_BOTROS_BLOCK_SIZE = 24*1024;
    private static final boolean SMALL_FIRST_FRAG_FULL = true;
}
