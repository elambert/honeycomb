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



package com.sun.honeycomb.oa.daal.nfs;

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
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.platform.NfsManager;
import com.sun.honeycomb.oa.FooterExtension;


/**
 * DAAL implementation over nfs
 */
public class NfsDAAL extends DAAL {

    public NfsDAAL(Disk disk, NewObjectIdentifier oid, Integer fragNum) {
        super(disk, oid, fragNum);
        updateReadTimeout();
    }

    protected void updateReadTimeout() {
        if (READ_TIMEOUT == -1) {
            String timeoutS = ClusterProperties.getInstance().getProperty("honeycomb.oa.readtimeout");
            int timeout = 1000;
            if (timeoutS != null) {
                try {
                    timeout = Integer.parseInt(timeoutS);
                } catch (NumberFormatException e) {
                }
            }
            READ_TIMEOUT = timeout;
            LOG.fine("The OA read timeout is ["+READ_TIMEOUT+"] ms" + this);
        }
    }

    public void create() throws DAALException {

        createInner();

        try {
            file = new RandomAccessFile(name, "rw");
        } catch (FileNotFoundException fnfe) {
            throw new DAALException(fnfe);
        }

        channel = file.getChannel();
        lock = null;
    }

    public  void open() throws DAALException, FragmentNotFoundException
    {
        if (namef == null) {
            // TODO - can we share this with Local's extractFooter somehow?
            name = makeNfsPath(Common.makeFilename(oid, disk, fragNum));
            namef = new File(name);

            /*
             if(fragNum == 0) {
                 LOG.info("frag0 final fragment file name is " + name);
             }
             */
        }

        // Open fragment file
        try {
            file = new RandomAccessFile(namef.getPath(), "r");
        } catch (FileNotFoundException fnfe) {
            /*
             LOG.log(Level.SEVERE, "Failed to open fragment [" +
                     namef.getPath() + "]", fnfe);
             */
            throw new FragmentNotFoundException(fnfe);
        }

        channel = file.getChannel();
    }

    public void rwopen() throws DAALException, FragmentNotFoundException
    {
        if (namef == null) {
            name = makeNfsPath(Common.makeFilename(oid, disk, fragNum));
            namef = new File(name);
        }
        if (!namef.exists()) {
            // check in temporary storage.
            name = makeNfsPath(Common.makeTmpFilename(oid, disk, fragNum));
            namef = new File(name);
            if (!namef.exists()) {
                throw new FragmentNotFoundException(this + " does not exist");
            }
        }
        try {
            file = new RandomAccessFile(namef.getPath(), "rw");
        } catch (FileNotFoundException fnfe) {
            throw new FragmentNotFoundException(fnfe);
        }

        channel = file.getChannel();
    }

    public void commit() throws DAALException
    {
        if (!namef.renameTo(finalnamef)) {
            String from = namef.getAbsolutePath();
            String to = finalnamef.getAbsolutePath();
            throw new DAALException("Rename failed [" + from + " -> " + to +"]");
        }
    }

    public void rollback() throws DAALException
    {
        // init both namef and finalenamef based on internal state
        finalname = makeNfsPath(Common.makeFilename(oid, disk, fragNum));
        finalnamef = new File(finalname);

        name =  makeNfsPath(Common.makeTmpFilename(oid, disk, fragNum));
        namef = new File(name);

        if (!finalnamef.renameTo(namef)) {
            throw new DAALException("Rename failed ["+
                                  namef.getAbsolutePath()+" -> "+
                                  finalnamef.getAbsolutePath()+"]"
                                    );
        }
    }

    public void delete() throws DAALException
    {
        boolean succeed = true;

        File f = new File(makeNfsPath(Common.makeFilename(oid, disk, fragNum)));
        if (f.exists()) {
            succeed &= f.delete();
        }
        f = new File(makeNfsPath(Common.makeTmpFilename(oid, disk, fragNum)));
        if (f.exists()) {
            succeed &= f.delete();
        }

       if (!succeed) {
           throw new DAALException("failed to delete " + this);
       }
    }

    public void close() throws DAALException
    {
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
            if (file != null) {
                file.close();
                file = null;
            }
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }
    }

    public boolean isTransient()
    {
        String tmpname = makeNfsPath(Common.makeTmpFilename(oid, disk, fragNum));
        File tmpf = new File(tmpname);

        if (tmpf.exists()) {
            LOG.fine("found file: " + tmpf + this);
            return true;
        } else {
            // not a warning - caller will take care of logging
            LOG.fine("did not find file: " + tmpf + this);
            return false;
        }
    }

    public boolean isCommitted()
    {
        String tmpname = makeNfsPath(Common.makeFilename(oid, disk, fragNum));
        File tmpf = new File(tmpname);
        return tmpf.exists();
    }

    public void replace(ByteBuffer buf) throws DAALException
    {
        // Write out just header to a new tmp file
        String tmpdelname = makeNfsPath(makeDelTmpFilename(oid, disk, fragNum));

        File delf = null;
        RandomAccessFile del = null;
        FileChannel delChannel = null;
        FileLock delLock = null;
        try {
            delf = new File(tmpdelname);
            del = new RandomAccessFile(delf, "rw");
            delChannel = del.getChannel();
            delLock = lock(delChannel, tmpdelname);
            write(delChannel, buf, 0);

            // Move the tmp file over the real file  *ACTUAL DELETE*
            // NOTE:  don't need any locking b/c fs ensures exactly
            // one client succeeds here
            if(!delf.renameTo(namef))
            {
                LOG.warning("Failed to delete due to failed rename (" +
                            delf + " -> " + namef +
                            ") - perhaps someone else beat us" + this
                            );
                if(!delf.delete()) { // try to cleanup tmp delete file
                    LOG.warning("Could not even delete tmp file " + delf + this);
                }
                throw new DAALException("Delete failed because of rename");
            }

        } catch (FileNotFoundException fnfe) {
            throw new DAALException(fnfe);

        } finally {
            if (delLock != null) {
                unlock(delLock);
            }
            if (delChannel != null) {
                try { delChannel.close(); } catch (IOException ie) {}
            }
            if (del != null) {
                try { del.close(); } catch (IOException ie) {}
            }
        }
    }

    public long read(ByteBuffer buf, long offset) throws DAALException {
        seek(file, offset);
        return read(channel, buf);
    }

    public long write(ByteBuffer buf, long offset) throws DAALException {
        return write(channel, buf, offset);
    }

    public long append(ByteBufferList buflist) throws DAALException {
        return write(channel, buflist);
    }

    public long append(ByteBuffer buf) throws DAALException
    {
        long res = 0;
        long n = 0;
        long tries = 0;
        IOException e = null;

        while(++tries <= maxRetries) {
            try {
                while(buf.hasRemaining() && ((n = channel.write(buf)) >= 0)) {
                    res += n;
                }
                return res;
            } catch (IOException ioe) {
                LOG.warning("write failed on try " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("write retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to write: " + e, e);
    }

    public void lock() throws DAALException {
        if (channel == null) {
            throw new DAALException("fragment not open " + this);
        }
        if (lock != null) {
            throw new DAALException("fragment already locked " + this);
        }
        lock = lock(channel, name);
    }

    public void unlock() throws DAALException {
        if (channel == null) {
            throw new DAALException("fragment not open " + this);
        }
        if (lock == null) {
            throw new DAALException("fragment not locked" + this);
        }
        unlock(lock);
    }

    public long length() throws DAALException {
        if (namef == null) {
            throw new DAALException("daal object not initialized");
        }
        return namef.length();
    }

    public void saveCtx(ByteBufferList ctxBuffers, boolean flush) throws DAALException
    {
        RandomAccessFile ctxfile = null;
        try {
            // Maybe flush data in buffer cache over NFS to server and to disk
            if(flush) {
                long pos = getFilePointer(file);
                file.close();
                file = new RandomAccessFile(name, "rw");
                channel = file.getChannel();
                seek(file, pos);
            }

            // Write (or over-write with newer) context file
            ctxfile = new RandomAccessFile(tmpctxname, "rw");
            write(ctxfile.getChannel(), ctxBuffers);
            ctxfile.close();
            ctxfile = null;

            if(!tmpctxnamef.renameTo(ctxnamef)) {
                String err = "rename failed" + this;
                LOG.warning(err);
                throw new DAALException(err);
            }

        } catch (FileNotFoundException fnfe) {
            // TODO: This should never happen
            LOG.warning("Failed to write ctxfile" + this + fnfe.toString());
            throw new DAALException(fnfe);

        } catch (IOException ioe) {
            LOG.warning("Failed to write ctxfile" + this + ioe.toString());
            throw new DAALException(ioe);

        } finally {
            if (ctxfile != null) {
                try {
                    ctxfile.close();
                } catch (IOException ioe) {
                    LOG.warning("Failed to close ctxfile" + ioe);
                }
            }
        }
    }

    public ByteBuffer restoreCtx(ByteBuffer ctxBuf) throws DAALException
    {
        // Build context file name
        String ctxFilename = makeNfsPath(makeCtxFilename(oid, disk, fragNum));

        File ctxfile = new File(ctxFilename);
        RandomAccessFile ctxrafile = null;
        try {
            ctxrafile = new RandomAccessFile(ctxFilename, "r");
        } catch (FileNotFoundException fnfe) {
            throw new DAALException(fnfe);
        }
        if (ctxrafile == null) {
            throw new DAALException("context not found");
        }

        // Read in ctxBuf size
        ByteBuffer archiveSizeBuffer =
            ByteBufferPool.getInstance().checkOutBuffer(4);

        read(ctxrafile.getChannel(), archiveSizeBuffer);

        archiveSizeBuffer.flip();

        int archiveSize = archiveSizeBuffer.getInt();

        ByteBufferPool.getInstance().checkInBuffer(archiveSizeBuffer);

        ctxBuf.limit(archiveSize);

        ByteBuffer bufferedData =
            ByteBufferPool.getInstance().
            checkOutBuffer((int)ctxfile.length() - archiveSize);

        ByteBuffer[] scatter = new ByteBuffer[2];
        scatter[0] = ctxBuf;
        scatter[1] = bufferedData;

        read(ctxrafile.getChannel(), scatter);

        return bufferedData;
    }

    public void deleteCtx() throws DAALException
    {
        if(tmpctxnamef != null) {
            tmpctxnamef.delete();
            tmpctxnamef = null;
        }
        if(ctxnamef != null) {
            ctxnamef.delete();
            ctxnamef = null;
        }
    }

    public void truncate(long offset) throws DAALException
    {
        long n = 0;
        long tries = 0;
        IOException e = null;

        while (++tries <= maxRetries) {
            try {
                channel.truncate(offset);
                file.seek(length());
                return;
            } catch (IOException ioe) {
                LOG.warning("truncate failed on try " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("sleep retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to truncate: " + e, e);
    }

    protected String makeNfsPath(String localPath) {
        return NfsManager.getNfsPath(disk, localPath);
    }

    // PRIVATE

    /*
     * Given the name of a tmp file, builds the ctx filename for it
     */
    private String ctxFilename(String tmpFilename) {
        return tmpFilename + ctxFileSuffix;
    }

    /*
     * Given the name of a tmp file, builds the tmp ctx filename for it
     */
    private String tmpCtxFilename(String tmpFilename) {
        return tmpFilename + tmpCtxFileSuffix;
    }

    /*
     * Convert the <OID, Disk, FragNum> to a context filename path on the
     * local filesystem.
     */
    private String makeCtxFilename(NewObjectIdentifier oid, Disk disk, int fragNum) {
        return ctxFilename(makeNfsPath(Common.makeTmpFilename(oid, disk, fragNum)));
    }

    /*
     * Convert the <OID, Disk, FragNum> to a temporary filename path
     */
    private String makeDelTmpFilename(NewObjectIdentifier oid, Disk disk, int fragNum) {
        String path = makeNfsPath(Common.makeTmpFilename(oid, disk, fragNum)) + delSuffix;
        return path;
    }

    private long getFilePointer(RandomAccessFile file) throws DAALException
    {
        long res = 0;
        long n = 0;
        long tries = 0;
        IOException e = null;

        while(++tries <= maxRetries) {
            try {
                res = file.getFilePointer();
                return res;
            } catch (IOException ioe) {
                LOG.warning("seek failed on getPos " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk  not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("getPos retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to getPos: " + e, e);
    }

    protected void seek(RandomAccessFile file, long offset) throws DAALException {
        long n = 0;
        long tries = 0;
        IOException e = null;

        while(++tries <= maxRetries) {
            try {
                file.seek(offset);
                return;
            } catch (IOException ioe) {
                LOG.warning("seek failed on try " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("sleep retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to seek: " + e, e);
    }

    protected long write(FileChannel ch, ByteBufferList bufList) throws DAALException
    {
        long res = 0;
        long n = 0;
        long tries = 0;
        long remaining = 0;
        IOException e = null;

        remaining = bufList.remaining();

        while(++tries <= maxRetries) {
            try {
                // TODO: Should we check all bufList for remaining()?
                // Maybe add up at beginning, and compare to res
                // But that extra array walk seems wasteful
                while(remaining > 0 &&
                      ((n = ch.write(bufList.getBuffers())) >= 0)) {
                    remaining -= n;
                    res += n;
                }
                return res;
            } catch (IOException ioe) {
                LOG.warning("write list failed on try " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("write list retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to [] write: " + e, e);
    }

    private long write(FileChannel ch, ByteBuffer buf, long offset) throws DAALException
    {
        long res = 0;
        long n = 0;
        long tries = 0;
        IOException e = null;
        long off = offset;

        while(++tries <= maxRetries) {
            try {
                while(buf.hasRemaining() && ((n = ch.write(buf, off)) >= 0)) {
                    res += n;
                    off += n;
                }
                return res;
            } catch (IOException ioe) {
                LOG.warning("write failed on try " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("write retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to write: " + e, e);
    }

    private long read(FileChannel ch, ByteBuffer buf) throws DAALException
    {
        long res = 0;
        long n = 0;
        long tries = 0;
        IOException e = null;
        OAWatchDog wd = OAWatchDog.getInstance();

        while(++tries <= maxRetries) {
            try {
                boolean continueReading = true;
                while (continueReading) {
                    if (!buf.hasRemaining()) {
                        continueReading = false;
                    }
                    if (continueReading) {
                        OAWatchDog.WatchDogTask task = wd.register(READ_TIMEOUT);
                        n = ch.read(buf);
                        task.cancel();
                        if (n < 0) {
                            continueReading = false;
                        } else {
                            res += n;
                        }
                    }
                }
                return res;
            } catch (InterruptedIOException ioe) {
                LOG.warning("The read operation has been interrupted ["+
                            ioe.getMessage()+"]. Aborting ..." + this);
                break;
            } catch (IOException ioe) {
                LOG.warning("read failed on try " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("read retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to read: " + e, e);
    }

    private long read(FileChannel ch, ByteBuffer[] bufList) throws DAALException
    {
        long res = 0;
        long n = 0;
        long tries = 0;
        long remaining = 0;
        IOException e = null;

        for(int i=0;i<bufList.length;i++) {
            remaining += bufList[i].remaining();
        }

        while(++tries <= maxRetries) {
            try {
                while(remaining > 0 &&
                      ((n = ch.read(bufList)) >= 0)) {
                    remaining -= n;
                    res += n;
                }
                return res;
            } catch (IOException ioe) {
                LOG.warning("read failed on try " + tries + this + ioe);
                e = ioe;
                if(!diskStillGood()) {
                    LOG.info("Disk not good anymore - aborting op" + this);
                    break;
                }
                try {
                    Thread.currentThread().sleep(retrySleep);
                } catch (InterruptedException ie) {
                    LOG.warning("read retry interrupted" + this + ie);
                }
            }
        }
        throw new DAALException("Failed to read: " + e, e);
    }

    private FileLock lock(FileChannel fchannel, String name) throws DAALException
    {
        FileLock lock = null;
        int lockTry = -1;

        for(lockTry = 1; lockTry <= maxLockRetries; lockTry++) {

            try {
                // TODO open a file channel to a lock file
                lock = fchannel.tryLock();
                if(lock != null) {
                    return lock;
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
                throw new DAALException("failed to lock " + name +
                                        " b/c AsynchronousCloseException: " +
                                        ace);
            } catch (ClosedChannelException cce) {
                throw new DAALException("failed to lock " + name +
                                        " b/c ClosedChannelException: " + cce);
            } catch (FileLockInterruptionException flie) {
                throw new DAALException("failed to lock " + name +
                                        " b/c FileLockInterruptionException: " +
                                        flie);
            } catch (OverlappingFileLockException ofle) {
                throw new DAALException("failed to lock " + name +
                                        " b/c OverlappingFileLockException: " +
                                        ofle);
            } catch (NonReadableChannelException nrce) {
                throw new DAALException("failed to lock " + name +
                                        " b/c NoneReadableChannelException: " +
                                        nrce);
            } catch (NonWritableChannelException nwce) {
                throw new DAALException("failed to lock " + name +
                                        " b/c NonWriteableChannelException: " +
                                        nwce);
            } catch (IOException ioe) {
                throw new DAALException("failed to lock " + name +
                                        " b/c IOException: " + ioe);
            }
        }
        LOG.warning("Failed to acquire lock after " + lockTry + " tries" + this);
        throw new DAALException("Failed to acquire lock.");
    }

    protected boolean diskStillGood() {
        Disk[] allDisks = DiskProxy.getClusterDisks();
        if(allDisks == null) {
            return false;
        }
        for(int d=0; d<allDisks.length; d++) {
            if(allDisks[d] != null && getDisk().equals(allDisks[d])) {
                if (allDisks[d].isEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Unlocks a filechannel. doesn't throw anything, just logs things*/
    private void unlock(FileLock flock)
    {
        if(flock == null) {
            // This is okay, but we'll warn about it
            // TODO
            return;
        }

        try {
            flock.release();
        } catch (ClosedChannelException cce) {
            // This is okay, but we'll warn about it
            // TODO
        } catch (IOException ioe) {
            // Not much we can do about this, so warn about it but
            // don't throw
            // TODO
        } // TODO: finally close the lock file
    }

    protected void createInner() {
        name = makeNfsPath(Common.makeTmpFilename(oid, disk, fragNum));
        namef = new File(name);

        ctxname = ctxFilename(name);
        ctxnamef = new File(ctxname);

        tmpctxname = tmpCtxFilename(name);
        tmpctxnamef = new File(tmpctxname);

        finalname = makeNfsPath(Common.makeFilename(oid, disk, fragNum));
        finalnamef = new File(finalname);
    }

    //
    // Public footer extension methods
    //

    // Footer Extension File (FEF) layout. A valid file has at least
    // the version, last modified time (also used to track retention
    // time upates), and a checksum. It will have zero or more slots
    // used to store legal hold and other data about an object which
    // cannot fit into the OA footer. The file format must be kept
    // consistent between HcNfsDAAL.java and NfsDAAL.java.
    //
    // -----------------------------------------------------------
    // version (2)
    // last modified time (8)
    // slot id (4) | type (2) | data length (4) | data (arbitrary)
    // checksum (4)
    // -----------------------------------------------------------

    // Read an existing footer extension file. If the file does not
    // exist, return an empty footer extension object.
    public FooterExtension readFooterExtension() throws DAALException {
        FooterExtension fe = null;
        checkIsExtensionNotOpen();

        try {
            openExtension();
        } catch (FileNotFoundException fnfe) {
            throw new DAALException(fnfe);
        }

        // Return with an empty FooterExtension if the file length is 0
        if (lengthExtension() == 0) {
            closeExtension();
            return new FooterExtension();
        }

        try {
            lockExtension();
            fe = new FooterExtension();
            ByteBuffer fileBuffer =
                ByteBuffer.allocateDirect((new Long(lengthExtension())).intValue());

            long bytesRead = readExtension(fileBuffer, 0);
            if (bytesRead < FooterExtension.MIN_FILE_SIZE) {
                throw new DAALException("Invalid footer extension file " +
                                        getExtensionFilename() + " is only " +
                                        bytesRead + " bytes long instead " +
                                        "of the miminum " +
                                        FooterExtension.MIN_FILE_SIZE +
                                        " bytes long");
            }
            fileBuffer.flip();

            // Read the version
            short version = fileBuffer.getShort();

            // Debug
            LOG.info("Read version: " + version);

            // Verify the version
            if (version < 1 || version > FooterExtension.VERSION) {
                throw new DAALException("Uknown footer extension file " +
                                        "version: " + version);
            }

            // Read the checksum
            int fileChecksum = fileBuffer.getInt((new Long(bytesRead)).intValue() -
                                                 FooterExtension.CHECKSUM_SIZE);

            // Debug
            LOG.info("Read checksum: " + fileChecksum);

            // Set the limit to exclude the checksum
            int bufferLimit = (new Long(bytesRead)).intValue() -
                FooterExtension.CHECKSUM_SIZE;
            fileBuffer.limit(bufferLimit);

            // Debug
            LOG.info("Set new buffer limit to avoid reading the checksum: " +
                     bufferLimit);

            // Footer extension reads the byte buffer
            fe.read(fileBuffer);

            // Calculate the checksum of the byte array
            int checksumValue = fe.checksum();

            // Compare the two checksums
            if (checksumValue != fileChecksum) {
                throw new DAALException("Calculated checksum (" +
                                        checksumValue +
                                        ") does not match file checksum (" +
                                        fileChecksum + ")");
            }
        } finally {
            try {
                unlockExtension();
            } catch (DAALException de) {}
            closeExtension();
        }

        return fe;
    }

    // Replace the existing file with the new footer extension
    public void writeFooterExtension(FooterExtension fe) throws DAALException {

        // Allocate the final byte buffer to write out
        int capacity = fe.size() + FooterExtension.VERSION_SIZE +
            FooterExtension.CHECKSUM_SIZE;
        ByteBuffer fileBuffer = ByteBuffer.allocateDirect(capacity);

        // Write the version
        fileBuffer.putShort(FooterExtension.VERSION);

        // Write the modified time and slot data
        fileBuffer.put(fe.asByteBuffer());

        // Write the checksum
        int checksum = fe.checksum();
        fileBuffer.putInt(checksum);

    // Print the contents
        LOG.info("Version: " + FooterExtension.VERSION);
        LOG.info("Checksum: " + checksum);
        LOG.info("File length: " + capacity);
        LOG.info(fe.toString());

        // Replace the file on disk
        fileBuffer.flip();
        replaceExtension(fileBuffer);
    }

    //
    // Private footer extension methods
    //

    private RandomAccessFile fef = null;
    private FileLock fefLock = null;

    // Use the same file name & path as the fragment + .fef
    private String getExtensionFilename() {
        return makeNfsPath(Common.makeFilename(oid, disk, fragNum)) + ".fef";
    }

    // Open the file for writing. This must be called instead of open
    // if we want to also lock the file.
    private void openExtension() throws DAALException,
                                          FileNotFoundException {
    checkIsExtensionNotOpen();
        fef = new RandomAccessFile(getExtensionFilename(), "rw");
    }

    // Close the file
    private void closeExtension() throws DAALException {
        if (fef == null) {
            throw new DAALException("Cannot close null file");
        }

        try {
            fef.close();
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        } finally {
            fef = null;
        }
    }

    // Get the length
    private long lengthExtension() throws DAALException {
        checkIsExtensionOpen();
        long length;
        try {
            length = fef.length();
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }
        return length;
    }

    // Make sure the extension is open before performing other ops
    private void checkIsExtensionOpen() throws DAALException {
        if (fef == null) {
            throw new DAALException("Footer extension file not open");
        }
    }

    // Make sure the extension is open before performing other ops
    private void checkIsExtensionNotOpen() throws DAALException {
        if (fef != null) {
            throw new DAALException("Footer extension file open");
        }
    }

    // Lock the file
    public void lockExtension() throws DAALException {
        checkIsExtensionOpen();
        try {
            fefLock = fef.getChannel().tryLock();
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }
        if (fefLock == null) {
            throw new DAALException("Cannot aquire lock on file " + fef.toString());
        }
    }

    // Lock the file
    public void unlockExtension() throws DAALException {
        checkIsExtensionOpen();
        if (fefLock != null) {
            try {
                fefLock.release();
            } catch (IOException ioe) {
                throw new DAALException(ioe);
            }
        }
    }

    // Read the file and put the contents into structured data
    private long readExtension(ByteBuffer buf, long offset) throws DAALException {
        FooterExtension fe;
        short version;
        long fileLength;
        int fileChecksum;

        checkIsExtensionOpen();

        // Get the length
        try {
            fileLength = fef.length();
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }

        // Don't read anything if the file length is 0
        if (fileLength == 0) {
            LOG.info("Empty file");
            return 0;
        }

        // Debug
        LOG.info("Footer extension file length: " + fileLength);

        // Check that the passed ByteBuffer is big enough
        int dataLength = (new Long(fileLength-offset)).intValue();
        if (buf.capacity() < dataLength) {
            throw new DAALException("Buffer size of " + buf.capacity() +
                                   "is too small to hold file of size " +
                                    dataLength);
        }

        // Read the contents into a byte array
        byte[] fileContents = new byte[dataLength];
        try {
            fef.readFully(fileContents, (new Long(offset)).intValue(), dataLength);
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }

        // Put this into the given ByteBuffer
        buf.put(fileContents);

        // Return the tree
        return dataLength;
    }

    // Write a new file
    private void replaceExtension(ByteBuffer buf) throws DAALException {
        checkIsExtensionNotOpen();

        if (!buf.isDirect()) {
            throw new DAALException("input buf not a direct buffer");
        }
        try {
            try {
                openExtension();
            } catch (FileNotFoundException fnfe) {
                throw new DAALException(fnfe);
            }
            lockExtension();

            try {
                // Truncate the file
                fef.setLength(0);

                // Write out the slot data
                byte[] fileBytes = new byte[buf.capacity()];
                buf.get(fileBytes);
                fef.write(fileBytes);
            } catch (IOException ioe) {
                throw new DAALException(ioe);
            }
        } finally {
            try {
                unlockExtension();
            } catch (DAALException de) {}
            closeExtension();
        }
    }

    // PRIVATE

    private static final Logger LOG = Logger.getLogger(NfsDAAL.class.getName());
    private static final int maxRetries = 3;
    private static final int maxLockRetries = 5;
    private static final int retrySleep = 500;

    private static final String ctxFileSuffix = ".ctx";
    private static final String tmpCtxFileSuffix = ".tmpctx";
    private static final String delSuffix = ".tmpdel";

    protected String name;
    protected File namef;
    private String tmpctxname;
    private File tmpctxnamef;
    private String ctxname;
    private File ctxnamef;
    private String finalname;
    private File finalnamef;
    protected RandomAccessFile file;
    protected FileChannel channel;
    protected FileLock lock;

    protected static int READ_TIMEOUT = -1; // 1 sec
}
