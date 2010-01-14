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



package com.sun.honeycomb.oa.daal.hcnfs;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.ByteBuffer;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.FooterExtension;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;

/**
 * DAAL implementation using our own nfs client library.
 */
public class HcNfsDAAL extends DAAL {
                
    private static final Logger LOG = Logger.getLogger(HcNfsDAAL.class.getName());
    private static final int localNodeId;
    
    static {
        localNodeId = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).nodeId();
    }    
    
    private AccessIntf inode;
    private boolean isOpen;
    private boolean isLocked;

    
    public HcNfsDAAL(Disk disk, NewObjectIdentifier oid, Integer fragNum) {
        super(disk, oid, fragNum);

        String fragName = Common.makeFragmentName(oid, fragNum.intValue());
        boolean succeed = false;
        try {
            if (disk.isNullDisk()) {
                // we are called with an invalid disk -
                // we don't want to fail to construct the object
                // but all operations will fail.
                inode = null;
            } else if (disk.getId().nodeId() == localNodeId) {
                inode = new FileAccess(disk, oid, fragName);
            } else {
                inode = new NfsAccess(disk, oid, fragName);
            }
        } catch (Exception e) {
            // failed to instanciate the underlying inode.
            // disk is probably invalid - all operations will fail
            LOG.warning("DAAL invalid object: " + e);
            inode = null;
        }
        isOpen = false;
        isLocked = false;
    }

    public void create() throws DAALException {
        checkIsNotOpen();
        inode.create();
        isOpen = true;
    }

    public void open() throws DAALException, FragmentNotFoundException {
        checkIsNotOpen();
        inode.open(true);
        isOpen = true;
    }
    
    public void rwopen() throws DAALException, FragmentNotFoundException {
        checkIsNotOpen();
        inode.open(false);
        isOpen = true;
    }
    
    public void commit() throws DAALException {
        checkIsNotOpen();
        inode.commit();
    }
    
    public void rollback() throws DAALException {
        checkIsNotOpen();
        inode.rollback();
    }

    public void delete() throws DAALException {
        checkIsValid();
        if (isOpen) {
            // close it first.
            close();
        }
        inode.delete();
    }

    public void close() throws DAALException {
        checkIsValid();
        if (isOpen) {
            try {
                inode.close();
            } finally {
                isOpen = false;
            }
        } else if (LOG.isLoggable(Level.FINE)) {
            // we should log a warning here - 
            // code is calling close() without open
            LOG.fine("disk " + this + " is not open");
        }
    }
    
    public boolean isTransient() {
        if (inode != null) {
            return inode.isTransient();
        }
        return false;
    }
    
    public boolean isCommitted() {
        if (inode != null) {
            return inode.isCommitted();
        }
        return false;
    }

    public void replace(ByteBuffer buf) throws DAALException {
        checkIsNotOpen();
                
        if (!buf.isDirect()) {
            throw new DAALException("input buf not a direct buffer");
        }
        try {
            try {
                // the file must be writeable in order to be locked.
                rwopen();
            } catch (FragmentNotFoundException fnfe) {
                throw new DAALException(fnfe);
            }
            lock();
            inode.replace(buf);
        } finally {
            try {
                unlock();
            } catch (DAALException de) {}
            close();
        }
    }

    public long read(ByteBuffer buf, long offset) throws DAALException {
        checkIsOpen();
        
        if (!buf.isDirect()) {
            throw new DAALException("input buf not a direct buffer");
        }
        return inode.read(buf, offset);
    }

    public long write(ByteBuffer buf, long offset) throws DAALException {
        checkIsOpen();
        
        if (!buf.isDirect()) {
            throw new DAALException("input buf not a direct buffer");
        }
        return inode.write(buf, offset);
    }
     
    public long append(ByteBuffer buf) throws DAALException {
        checkIsOpen();
        
        if (!buf.isDirect()) {
            throw new DAALException("input buf is not a direct buffer");
        }
        return inode.append(buf);
    }

    public long append(ByteBufferList buflist) throws DAALException {
        ByteBuffer[] buffers = buflist.getBuffers();
        int res = 0;
        for (int i = 0; i < buffers.length; i++) {
            res += append(buffers[i]);
        }
        return res;
    }
    
    public void lock() throws DAALException {
        checkIsOpen();
        if (isLocked) {
            throw new DAALException("fragment already locked " + this);
        }
        inode.lock();
        isLocked = true;
    }

    public void unlock() throws DAALException {
        if (inode != null && isOpen && isLocked) {
            inode.unlock();
            isLocked = false;
            
        } else if (LOG.isLoggable(Level.FINE)) {
            // we should log a warning here - 
            // code is calling unlock() without open or lock (finally clause)
            LOG.fine("fragment is either not opened or not locked " + this);
        }
    }

    public long length() throws DAALException {
        checkIsOpen();
        return inode.length();
    }

    //
    // Public footer extension methods
    //

    public FooterExtension readFooterExtension() throws DAALException {
        checkIsValid();
        
        AccessIntf finode;
        String fragName = Common.makeFragmentName(oid, fragNum) + FooterExtension.SUFFIX;
        try {
            if (disk.getId().nodeId() == localNodeId) {
                finode = new FileAccess(disk, oid, fragName);
            } else {
                finode = new NfsAccess(disk, oid, fragName);
            }
        } catch (Exception e) {
            throw new DAALException(e);
        }
        try {
            // note - the file must be writable in order to be locked.
            finode.open(false);
        } catch (FragmentNotFoundException fe) {
            // Return an empty FooterExtension if the file does not exist
            return new FooterExtension();
        }
        
        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer buf = null;
        try {
            finode.lock();
            int length = new Long(finode.length()).intValue();
            buf = pool.checkOutBuffer(length);
            int res = finode.read(buf, 0);
            if (res != length) {
                throw new DAALException("Invalid footer extension file " +
                                        fragName + " read failed: got " + res +
                                        " expected " + length);
            }
            buf.flip();            
            
            // Read the version
            short version = buf.getShort();
            
            // Verify the version
            if (version < 1 || version > FooterExtension.VERSION) {
                throw new DAALException("Uknown footer extension file " +
                                        "version: " + version);
            }
            
            // Read the checksum
            int checksum = buf.getInt(length - FooterExtension.CHECKSUM_SIZE);
            
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Read version from disk: " + version);
                LOG.fine("Read checksum from disk: " + checksum);
            }
            
            // Set the limit to exclude the checksum
            // and read the byte buffer
            FooterExtension fe = new FooterExtension();
            buf.limit(length - FooterExtension.CHECKSUM_SIZE);
            fe.read(buf);
            
            // Calculate and verify the checksum
            int checksumValue = fe.checksum();
            if (checksumValue != checksum) {
                throw new DAALException("Calculated checksum (" +
                                        checksumValue +
                                        ") does not match file checksum (" +
                                        checksum + ")");
            }
            
            return fe;
            
        } finally {
            if (buf != null) {
                pool.checkInBuffer(buf);
            }
            finode.unlock();
            finode.close();
        }
    }

    // Replace the existing file with the new footer extension
    public void writeFooterExtension(FooterExtension fe) throws DAALException {
        checkIsValid();
        
        // create or open the footer file
        AccessIntf finode;
        String fragName = Common.makeFragmentName(oid, fragNum) + FooterExtension.SUFFIX;
        try {
            if (disk.getId().nodeId() == localNodeId) {
                finode = new FileAccess(disk, oid, fragName);;
            } else {
                finode = new NfsAccess(disk, oid, fragName);
            }
        } catch (Exception e) {
            throw new DAALException(e);
        }
        try {
            finode.open(false);
        } catch (FragmentNotFoundException de) {
            finode.create();
        }
                
        // Allocate the final byte buffer to write out
        int capacity = fe.size() + FooterExtension.VERSION_SIZE +
            FooterExtension.CHECKSUM_SIZE;        
        ByteBuffer buf = ByteBufferPool.getInstance().checkOutBuffer(capacity);

        // Write the version
        buf.putShort(FooterExtension.VERSION);
        
        // Write the modified time and slot data
        buf.put(fe.asByteBuffer());
        
        // Write the checksum
        int checksum = fe.checksum();
        buf.putInt(checksum);
        
        // Print the contents
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Version: " + FooterExtension.VERSION);
            LOG.fine("Checksum: " + checksum);
            LOG.fine("File length: " + capacity);
            LOG.fine(fe.toString());
        }
        
        // Replace the file on disk
        buf.flip();
        try {
            finode.lock();
            finode.replace(buf);
        } finally {
            ByteBufferPool.getInstance().checkInBuffer(buf);
            finode.unlock();
            finode.close();
        }
    }


    //
    // TODO - the following methods are currently not implemented.
    // There are used for write fail-safe and are not hooked up
    // in the current implementation (never called)
    //
    public void saveCtx(ByteBufferList ctxBuffers, boolean flush) throws DAALException
    {
        throw new RuntimeException("frag context not implemented");
    }
    
    public ByteBuffer restoreCtx(ByteBuffer ctxBuf) throws DAALException
    {
        throw new RuntimeException("frag context not implemented");
    }
    
    public void deleteCtx() throws DAALException
    {
        // throw new RuntimeException("frag context not implemented");
    }
        
    public void truncate(long offset) throws DAALException
    {
        throw new RuntimeException("truncate not implemented");
    }

    
    // PROTECTED
    
    protected void finalize() throws Throwable {
        try {            
            if (isOpen) {
                LOG.severe("DAAL fragment leaking " + this);
                close();
            }
        } finally {
            super.finalize();
        }
    }
    
    // PRIVATE
        
    private void checkIsValid() throws DAALException {
        if (inode == null) {
            throw new DAALException("Disk is invalid");
        }
    }
    
    private void checkIsNotOpen() throws DAALException {
        checkIsValid();
        if (isOpen) {
            throw new DAALException("Fragment already opened");
        }
    }
    
    private void checkIsOpen() throws DAALException {
        checkIsValid();
        if (!isOpen) {
            throw new DAALException("Fragment not open");
        }
    }
}
