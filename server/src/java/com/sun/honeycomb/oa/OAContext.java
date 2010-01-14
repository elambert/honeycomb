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
import java.io.UnsupportedEncodingException;
import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coordinator.Disposable;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.oa.hash.ContentHashContext;

/**
 * State OA needs between requests
 * TODO- SHould we split this into an OARead and OAWrite, with common parent?
 * TODO: Move fragment file enocde/decode into here from OAClient!!
 */
public class OAContext implements Codable, Disposable {
    
    public OAContext() {
        fragmentFileSet = null;
        contentHashContext = null;
        bufferedData = new ByteBufferList();
        noMoreWrites = false;
        actualDataBytesWritten = 0;
    }
    
    void setFragmentFileSet(FragmentFileSet fset) {
        fragmentFileSet = fset;
    }
    
    void setContentHashContext(ContentHashContext chashctx) {
        contentHashContext = chashctx;
    }

    void clearBufferedData() {
        bufferedData.clear();
        noMoreWrites = false;
    }

    void incActualDataBytesWritten(long incAmount) {
        actualDataBytesWritten += incAmount;
    }
    

    long getActualDataBytesWritten() {
        return actualDataBytesWritten;
    }
    
    void setObjectSize(long objectSize) {
        this.objectSize = objectSize;
    }
    
    long getObjectSize() {
        return objectSize;
    }

    void setContentHash(byte[] contentHash) {this.contentHash = contentHash;}
    byte[] getContentHash() {return contentHash;}

    void setFragmentSize(int fragmentSize) {this.fragmentSize = fragmentSize;}
    int getFragmentSize() {return fragmentSize;}

    void setBlockSize(int blockSize) {this.blockSize = blockSize;}
    int getBlockSize() {return blockSize;}

    void setChunkSize(long chunkSize) {this.chunkSize = chunkSize;}
    public long getChunkSize() {return chunkSize;}
        
    void AllowNoMoreWrites(){noMoreWrites = true;}

    void AllowMoreWrites(){noMoreWrites = false;}
    
    FragmentFileSet getFragmentFileSet() {return fragmentFileSet;}

    ContentHashContext getContentHashContext() {return contentHashContext;}

    /** Returns an ArrayList of ByteBufferLists */
    ByteBufferList getBufferedData() {return bufferedData;}

    boolean noMoreWrites() {return noMoreWrites;}


    // Codable Interface //

    public void encode(Encoder encoder) {
        encoder.encodeKnownClassCodable(fragmentFileSet);
        encoder.encodeKnownClassCodable(contentHashContext);
        encoder.encodeBoolean(noMoreWrites);
        encoder.encodeLong(actualDataBytesWritten);
        encoder.encodeLong(objectSize);
        encoder.encodeInt(fragmentSize);
        encoder.encodeInt(blockSize);
        encoder.encodeLong(chunkSize);
        encoder.encodeBytes(contentHash);
    }
    
    public void decode(Decoder decoder) {
        fragmentFileSet = new FragmentFileSet();
        decoder.decodeKnownClassCodable(fragmentFileSet);
        contentHashContext = new ContentHashContext();
        decoder.decodeKnownClassCodable(contentHashContext);
        noMoreWrites = decoder.decodeBoolean();
        actualDataBytesWritten = decoder.decodeLong();
        objectSize = decoder.decodeLong();
        fragmentSize = decoder.decodeInt();
        blockSize = decoder.decodeInt();
        chunkSize = decoder.decodeLong();
        contentHash = decoder.decodeBytes();
    }

    // Disposable Interface 

    public void dispose() {
        if(fragmentFileSet != null) {
            fragmentFileSet.dispose();
            fragmentFileSet = null;
        }
        
        if(contentHashContext != null) {
            contentHashContext.dispose();
            contentHashContext = null;
        }
        
        if(bufferedData != null) {
            bufferedData.clear();
            bufferedData = null;
        }
    }

    public static final String CTXTAG = "OAContext";
    private FragmentFileSet fragmentFileSet = null; // read and write
    private ContentHashContext contentHashContext = null; // write only
    private ByteBufferList bufferedData = null; // write only
    private boolean noMoreWrites = false; // write only
    private long actualDataBytesWritten = -1; // write only
    private long objectSize = -1; // read only
    private int fragmentSize = -1; // read and write
    private int blockSize = -1; // read only
    private long chunkSize = -1; // read and write
    private byte[] contentHash = null;
}
