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
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.FileLock;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.FooterExtension;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.util.ExtLevel;


/**
 * Local fragment access
 */
public class FileAccess implements AccessIntf {
                
    private static final Logger LOG = Logger.getLogger(FileAccess.class.getName());
    private static final String delSuffix = ".tmpdel";
    private static final int maxLockRetries = 5;
    private static final int retrySleep = 500;
    
    private final File namef;
    private final File tmpf;
    
    private RandomAccessFile file;
    private FileLock lock;
    
    public FileAccess(Disk disk, NewObjectIdentifier oid, String fragName) {
        
        String path = Common.mapIdToDir(oid.getLayoutMapId(), disk.getId());
        namef = new File(path + Common.dirSep + fragName);
        tmpf = new File(Common.mapIdToTmpDir(disk.getId()) + Common.dirSep + fragName);
        file = null;
        lock = null;
    }
    
    public void create() throws DAALException {
        assert(file == null);
        try {
            file = new RandomAccessFile(tmpf, "rw");
        } catch (FileNotFoundException fnfe) {
            throw new DAALException(fnfe);
        }
    }

    public void open(boolean read_only) throws DAALException, FragmentNotFoundException {
        assert(file == null);
        String mode = (read_only)? "r" : "rw";        
        if (namef.exists()) {
            try {
                file = new RandomAccessFile(namef, mode);
            } catch (FileNotFoundException fnfe) {
                throw new DAALException(fnfe);
            }
        } else if (!read_only && tmpf.exists()) {
            try {
                file = new RandomAccessFile(tmpf, mode);
            } catch (FileNotFoundException fnfe) {
                throw new FragmentNotFoundException(fnfe);
            }
        }
        if (file == null) {
            throw new FragmentNotFoundException("Failed to open frag " + this);
        }
    }
     
    public void commit() throws DAALException {
        assert(file == null);
        if (!tmpf.renameTo(namef)) {
            String from = tmpf.getAbsolutePath();
            String to = namef.getAbsolutePath();
            throw new DAALException("Rename failed [" + from + " -> " + to +"]");
        }
    }
    
    public void rollback() throws DAALException {
        assert(file == null);
        if (!namef.renameTo(tmpf)) {
            String from = namef.getAbsolutePath();
            String to = tmpf.getAbsolutePath();
            throw new DAALException("Rename failed [" + from + " -> " + to +"]");
        }
    }

    public void delete() throws DAALException {
        boolean succeed = true;
        
        if (namef.exists()) {
            succeed &= namef.delete();
        }
        if (tmpf.exists()) {
            succeed &= tmpf.delete();
        }
        if (!succeed) {
            throw new DAALException("failed to delete " + this);
        }
    }

    public void close() throws DAALException {
        if (file != null) {
            try {
                file.getChannel().force(false);
                file.close();
                file = null;
            } catch (IOException ioe) {
                throw new DAALException(ioe);
            } finally {
                if (file != null) {
                    try { file.close(); } catch (IOException ignore) {}
                }
                file = null;
            }
        }
    }
    
    public boolean isTransient() {
        return tmpf.exists();
    }
    
    public boolean isCommitted() {
        return namef.exists();
    }

    public void replace(ByteBuffer buf) throws DAALException {
        assert(file != null);
        assert(lock != null);
        
        File delf = new File(tmpf.getAbsolutePath() + delSuffix);
        RandomAccessFile del = null;
        try {
            del = new RandomAccessFile(delf, "rw");
            del.getChannel().write(buf, 0);
            
            // Move the tmp file over the real file  *ACTUAL DELETE*
            // NOTE:  don't need any locking b/c fs ensures exactly
            // one client succeeds here
            if(!delf.renameTo(namef))
            {
                LOG.warning("Failed to delete due to failed rename (" +
                            delf + " -> " + namef +
                            ") - perhaps someone else beat us" + this
                            );
                throw new DAALException("Delete failed because of rename");
            }
            
        } catch (FileNotFoundException fnfe) {
            throw new DAALException(fnfe);
            
        } catch (IOException ioe) {
            throw new DAALException(ioe);
            
        } finally {
            if (del != null) {
                try { del.close(); } catch (IOException ie) {}
            }
            if (delf.exists()) {
                if(!delf.delete()) { 
                    // try to cleanup tmp delete file
                    LOG.warning("Fail to delete tmp file " + delf.getPath());
                }
            }
        }
    }

    public int read(ByteBuffer buf, long offset) throws DAALException {
        assert(file != null);
        try {
            file.seek(offset);
            return file.getChannel().read(buf);
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }        
    }

    public int write(ByteBuffer buf, long offset) throws DAALException {
        assert(file != null); 
        try {
            return file.getChannel().write(buf, offset);
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }
    }
     
    public int append(ByteBuffer buf) throws DAALException {
        assert(file != null); 
        try {
            return file.getChannel().write(buf);
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }
    }

    public void lock() throws DAALException {
        assert(file != null);
        int lockTry;
        for (lockTry = 1; lockTry <= maxLockRetries; lockTry++) {
            try {
                lock = file.getChannel().tryLock();
                if (lock != null) {
                    return;
                }
                LOG.info("Try " + lockTry + " to lock failed.  Sleeping " +
                         retrySleep + " msecs..." + this);
                try {
                    Thread.sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("lock retry sleep prematurely interrupted" +
                                this + ie);
                }
            }  catch (AsynchronousCloseException ace) {
                throw new DAALException("failed to lock " + namef +
                                        " b/c AsynchronousCloseException: " +
                                        ace);
            } catch (ClosedChannelException cce) {
                throw new DAALException("failed to lock " + namef +
                                        " b/c ClosedChannelException: " + cce);
            } catch (FileLockInterruptionException flie) {
                throw new DAALException("failed to lock " + namef +
                                        " b/c FileLockInterruptionException: " +
                                        flie);
            } catch (OverlappingFileLockException ofle) {
                throw new DAALException("failed to lock " + namef +
                                        " b/c OverlappingFileLockException: " +
                                        ofle);
            } catch (NonReadableChannelException nrce) {
                throw new DAALException("failed to lock " + namef +
                                        " b/c NoneReadableChannelException: " +
                                        nrce);
            } catch (NonWritableChannelException nwce) {
                throw new DAALException("failed to lock " + namef +
                                        " b/c NonWriteableChannelException: " +
                                        nwce);
            } catch (IOException ioe) {
                throw new DAALException("failed to lock " + namef +
                                        " b/c IOException: " + ioe);
            }
        }
        LOG.warning("Failed to acquire lock after " + lockTry + " tries" + this);
        LOG.log(ExtLevel.EXT_SEVERE, "Failed to acquire lock for object " +
            this + ", need to reboot the cell to aleviate the problem");
        throw new DAALException("Failed to acquire lock.");
    }

    public boolean unlock() {
        if (lock != null) {
            try {
                lock.release();
                return true;
            } catch (ClosedChannelException cce) {
                // This is okay, but we'll warn about it
                // TODO
            } catch (IOException ioe) {
                // Not much we can do about this, so warn about it but
                // don't throw
                // TODO
            } finally {
                lock = null;
            }
        }
        return false;
    }

    public long length() throws DAALException {
        assert(file != null);
        try {
            return file.length();
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }
    }

    public String toString() {
        return namef.getAbsolutePath();
    }
    
    
    // PROTECTED
    
    protected void finalize() throws Throwable {
        try {            
            if (file != null) {
                LOG.severe("DAAL fragment leaking " + this);
                close();
            }
        } finally {
            super.finalize();
        }
    }
}
