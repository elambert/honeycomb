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



package com.sun.honeycomb.oa.upgrade;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.oa.FooterExtension;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.coding.Decoder;

import java.util.logging.Logger;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**********************************************************************/
public class UpgraderDAAL extends DAAL {
    private static Logger log
        = Logger.getLogger(UpgraderDAAL.class.getName());
    protected File file;
    protected RandomAccessFile raf;
    protected FileChannel channel;

    /**********************************************************************/
    public UpgraderDAAL(Disk disk, NewObjectIdentifier oid,
                        Integer fragmentId) {
        super(disk, oid, fragmentId);
    }

    /**********************************************************************/
    public void rwopen() throws DAALException, FragmentNotFoundException {
        if (file == null) {
            throw new DAALException("file is not initialized");
        }
        // Open fragment file for rw
        try {
            raf = new RandomAccessFile(file.getPath(), "rw");
        } catch (FileNotFoundException fnfe) {
            throw new FragmentNotFoundException(fnfe.getMessage());
        }
        channel = raf.getChannel();
    }

    /**********************************************************************/
    public String toString() {
        return "[OID " + oid + ", fragmentNumber " + fragNum
            + ", Disk " + disk.getPath() + "]";
    }

    /**********************************************************************/
    public long write(ByteBuffer buf, long offset) throws DAALException {
        long res = 0;
        long n = 0;
        try {
            while(buf.hasRemaining() && ((n = channel.write(buf, offset)) >= 0)) {
                res += n;
                offset += n;
            }
            return res;
        } catch (IOException ioe) {
            String msg = "Failed to write " + this + " " + ioe.getMessage();
            log.severe(msg);
            throw new DAALException(msg);
        }
    }

    /**********************************************************************/
    private void seek(RandomAccessFile raf, long offset) throws DAALException {
        try {
            raf.seek(offset);
            return;
        } catch (IOException ioe) {
            String msg = "Seek failed on " + this + " " + ioe.getMessage();
            log.severe(msg);
            throw new DAALException(msg);
        }
    }

    /**********************************************************************/
    private long read(FileChannel ch, ByteBuffer buf) throws DAALException {
        long res = 0;
        long n = 0;

        try {
            while (buf.hasRemaining()) {
                n = ch.read(buf);
                if (n < 0) {
                    return n;
                } else {
                    res += n;
                }
            }
            return res;
        } catch (IOException ioe) {
            String msg = "Read failed on " + this + " " + ioe.getMessage();
            log.severe(msg);
            throw new DAALException(msg);
        }
    }

    /**********************************************************************/
    public long read(ByteBuffer buf, long offset) throws DAALException {
        seek(raf, offset);
        return read(channel, buf);
    }

    /**********************************************************************/
    public void close() throws DAALException {
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
            if (raf != null) {
                raf.close();
                raf = null;
            }
        } catch (IOException ioe) {
            throw new DAALException(ioe);
        }
    }

    /**********************************************************************/
    public void setFile(File file) {
        this.file = file;
    }

    /**********************************************************************/
    public long length() throws DAALException {
        if (file == null) {
            throw new DAALException("file is not initialized");
        }
        return file.length();
    }

    /**********************************************************************/
    public void create() throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void commit() throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void rollback() throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void lock() throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void unlock() throws DAALException {
        throw new UnsupportedOperationException();
    }
    public boolean isTransient() {
        throw new UnsupportedOperationException();
    }
    public boolean isCommitted() {
        throw new UnsupportedOperationException();
    }
    public void replace(ByteBuffer buf) throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void truncate(long size) throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void open() throws DAALException, FragmentNotFoundException {
        throw new UnsupportedOperationException();
    }
    public long append(ByteBufferList buflist) throws DAALException {
        throw new UnsupportedOperationException();
    }
    public long append(ByteBuffer buf) throws DAALException {
        throw new UnsupportedOperationException();
    }
    public FooterExtension readFooterExtension() throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void writeFooterExtension(FooterExtension fe) throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void saveCtx(ByteBufferList ctxBuffers, boolean flush) throws DAALException {
        throw new UnsupportedOperationException();
    }
    public ByteBuffer restoreCtx(ByteBuffer ctxBuf) throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void delete() throws DAALException {
        throw new UnsupportedOperationException();
    }
    public void deleteCtx() throws DAALException {
        throw new UnsupportedOperationException();
    }
}

