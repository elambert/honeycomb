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



package com.sun.honeycomb.oa.daal;

import java.nio.ByteBuffer;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.FooterExtension;
import com.sun.honeycomb.disks.Disk;


/**
 * Data Access Abstraction Layer
 */
public abstract class DAAL {

    public static final String DAAL_PROPERTY = "honeycomb.oa.daal";
    
    final protected NewObjectIdentifier oid;
    final protected int fragNum;
    final protected Disk disk;
    
    public DAAL(Disk disk, NewObjectIdentifier oid, Integer fragNum) {
        this.disk = disk;
        this.oid = oid;
        this.fragNum = fragNum.intValue();
    }
    
    public Disk getDisk() {
        return disk;
    }
    
    public NewObjectIdentifier getOID() {
        return oid;
    }
    
    public int getFragNum() {
        return fragNum;
    }
    
    public String toString() {
        return " [oid " + oid 
            + ", frag " + fragNum 
            + ", disk " + disk.getId().toStringShort()
            + "] ";
    }
    
    /**
     * Create this fragment in a temporary location.
     * The fragment is open in read/write mode.
     * The fragment is not visible/accessible by other clients as long as 
     * it is not committed into persistent storage.
     *
     * @exception DAALException - an i/o error occurred or the fragment is 
     * not in a valid state
     */
    abstract public void create() throws DAALException;
    
    /**
     * Commit the fragment into persistent storage.
     * The fragment becomes visible cluster-wide and can be opened by other
     * clients. 
     *
     * @exception DAALException - an i/o error occurred or the fragment is 
     * not in a valid state
     */
    abstract public void commit() throws DAALException;

    /**
     * Rollback this fragment
     * The fragment disappears from persistent storage and is put back in a 
     * temporary location.
     *
     * @exception DAALException - an i/o error occurred or the fragment is 
     * not in a valid state.
     */
    abstract public void rollback() throws DAALException;
    
    /**
     * Delete this fragment on both the persistent and temporary storage.
     * The fragment is permanently deleted from the archive.
     *
     * @exception DAALException - an i/o error occurred or the fragment is 
     * not in a valid state
     */
    abstract public void delete() throws DAALException;
    
    /**
     * Put an advisory lock on the fragment.
     * Note lock must not be reentrant.
     *
     * @exception DAALException - the lock cannot be acquired because of
     * an i/o error condition, the lock is already taken, the fragment is
     * not opened or the operation timed out.
     */
    abstract public void lock() throws DAALException;
    
    /**
     * Release the advisory lock on the fragment.
     *
     * @exception DAALException - the lock is not taken or the fragment is 
     * not opened.
     */
    abstract public void unlock() throws DAALException;
    
    /**
     * return true if this fragment exists and has not been committed into
     * the persistent storage. false otherwise.
     */
    abstract public boolean isTransient();
    
    /**
     * return true if this fragment exists on stable storage. false otherwise.
     */
    abstract public boolean isCommitted();
    
    /**
     * Replace this fragment.
     * Atomically replace the content of the fragment with the given buffer.
     *
     * @param buf - the new content of the fragment
     * @exception DAALException - an i/o error occurred
     */
    abstract public void replace(ByteBuffer buf) throws DAALException;

    /**
     * Truncates this fragment to the given size and set the current position
     * at the end of the fragment.
     * If the given size is less than the fragment's current size then the 
     * fragment is truncated, discarding any bytes beyond the new end of the
     * fragment. If the given size is greater than or equal to the fragment's 
     * current size then the fragment is not modified.
     *
     * @param size - The new size, a non-negative byte count
     * @exception DAALException - an i/o error occurred
     */
    abstract public void truncate(long size) throws DAALException;
    
    /**
     * Open this fragment for reading and set the current position
     * at the beginning of the fragment.
     * This fragment must exist on persistent storage.
     *
     * @exception DAALException - an i/o error occurred or the fragment is not
     * in a valid state.
     * @exception FragmentNotFoundException - the fragment does not exist on 
     * stable storage.
     */
    abstract public void open() throws DAALException, FragmentNotFoundException;
    
    /**
     * Open this fragment in read/write mode and set the current position
     * at the beginning of the fragment.
     * The fragment must exists either on persistent or temporary storage.
     * FIXME - this interface is used to update the footer, repair the fragment
     * or grab the temp fragments. We should clarify how to use it.
     *
     * @exception DAALException - an i/o error occurred or the fragment is not
     * in a valid state.
     * @exception FragmentNotFoundException - the fragment does not exist on 
     * stable or temporary storage.
     */
    abstract public void rwopen() throws DAALException, FragmentNotFoundException;
    
    /**
     * Close this fragment and releases any associated system resources.
     *
     * @exception DAALException - if an I/O error occurs.
     */
    abstract public void close() throws DAALException;

    /**
     * Reads a sequence of bytes from this fragment into the given buffer.
     * Bytes are read starting at the given offset.
     *
     * @param  buf - The buffer into which bytes are to be transferred
     * @param offset - The position within the fragment from which to read.
     * @return the number of bytes read, possibly zero if the fragment has 
     * reached end-of-stream
     * @exception DAALException - if an I/O error occurs.
     */
    abstract public long read(ByteBuffer buf, long offset) throws DAALException;
    
    /**
     * Writes a sequence of bytes to this fragment from the given buffer.
     * Bytes are written starting at the given offset rather then the current 
     * fragment's position. This method does not modify the fragment's
     * current position.
     *
     * @param  buf - The buffer from which bytes are to be retrieved
     * @param offset - The position within the fragment from which to write.
     * @return the number of bytes written, possibly zero
     * @exception DAALException - if an I/O error occurs.
     */
    abstract public long write(ByteBuffer buf, long offset) throws DAALException;
    
    /**
     * Append a sequence of bytes to this fragment from the given buffers list.
     * Bytes are written starting at the fragment current position and then the 
     * current fragment's position is updated with the number of bytes actually 
     * written.
     * FIXME - this interface should be removed from the DAAL as soon as 
     * FragmentFile handles completely the current position within the fragment
     *
     * @param bufList - the list of buffers from which bytes are to be retrieved.
     * @return the number of bytes written, possibly zero
     * @exception DAALException - if an i/o error occurs or if the fragment is
     * not in a valid state (not opened for writting).
     */
    abstract public long append(ByteBufferList buflist) throws DAALException;
    
    /**
     * Append a sequence of bytes to this fragment from the given buffer.
     * Bytes are written starting at the fragment current position and then the 
     * current fragment's position is updated with the number of bytes actually 
     * written.
     * FIXME - this interface should be removed from the DAAL as soon as 
     * FragmentFile handles completely the current position within the fragment
     *
     * @param buf - the buffer from which bytes are to be retrieved.
     * @return the number of bytes written, possibly zero
     * @exception DAALException - if an i/o error occurs or if the fragment is
     * not in a valid state (not opened for writting).
     */
    abstract public long append(ByteBuffer buf) throws DAALException;

    /**
     * Return the length of the underlying fragment in bytes.
     *
     * @exception DAALException - an i/o error occurred or the fragment is 
     * not in a valid state
     */
    abstract public long length() throws DAALException;
    
    /**
     * Read a FooterExtension object from an on-disk footer extension
     * (.fef) file. Footer extensions are used to store extra data
     * about an OA object (data or MD) that cannot fit into the
     * existing OA footer. This was initially developed for storing
     * compliance legal hold strings, but can be used to store
     * arbitrary data about an object. Footer extension files are
     * replicated N+M times and co-located on disk in the same data
     * directories at the corresponding N+M object fragment files.
     *
     * @return A FooterExtension object representing footer extension data
     * read from a footer extension file.
     */
    abstract public FooterExtension readFooterExtension() throws DAALException;

    /**
     * Write a FooterExtension object to an on-disk footer extension
     * (.fef) file, representing the data stored in a FooterExtension
     * object.
     *
     * @param fe - The FooterExtension object to write to a footer extension file
     */
    abstract public void writeFooterExtension(FooterExtension fe) throws DAALException;

    abstract public void saveCtx(ByteBufferList ctxBuffers, boolean flush) throws DAALException;
    abstract public ByteBuffer restoreCtx(ByteBuffer ctxBuf) throws DAALException;
    abstract public void deleteCtx() throws DAALException;
}
