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

import com.sun.honeycomb.oa.Fault;
import com.sun.honeycomb.oa.FaultManager;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.TestFragmentFile;
import com.sun.honeycomb.oa.OAUtils;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.resources.ByteBufferList;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

public class FaultyNfsDAAL extends NfsDAAL
    implements FragmentFault.FragmentProxy {

    private static final Logger log
        = Logger.getLogger(FaultyNfsDAAL.class.getName());

    private ByteBuffer faultyBuffer;
    private boolean faultyRead;
    private FragmentFault.FragmentInfo fragmentInfo;
    private TestFragmentFile testFragmentFile;

    public static final Operation CREATE = new Operation("Create");
    public static final Operation CLOSE = new Operation("Close");
    public static final Operation COMMIT = new Operation("Commit");
    public static final Operation READ = new Operation("Read");
    public static final Operation WRITE = new Operation("Write");
    public static final Operation SEEK = new Operation("Seek");
    public static final Operation DELETE = new Operation("Delete");
    public static final Operation REPLACE = new Operation("Replace");
    public static final Operation TRUNCATE = new Operation("Truncate");

    /**********************************************************************/
    public FaultyNfsDAAL(Disk disk, NewObjectIdentifier oid,
                         Integer fragNum) {
        super(disk, oid, fragNum);
        // Create dynamically the layout map dirs for this oid.
        new File(Common.makeDir(oid, disk)).mkdirs();
    }

    /**********************************************************************/
    public FaultyNfsDAAL(Disk disk, NewObjectIdentifier oid, Integer fragNum,
                         TestFragmentFile tff) {
        this(disk, oid, fragNum);
        testFragmentFile = tff;
    }

    /**********************************************************************
     * XXX Following method is called from many places in
     * FragmentFile. It appears that tests under scenarii do not
     * excercise all possibilities.
     */
    public void create() throws DAALException {
        Fault.FaultType triggered
            = FaultManager.triggerFault(CREATE.toString(),
                                        testFragmentFile.getFaultEvent(),
                                        this);
        if (triggered != null) {
            log.info("create(), triggered " + triggered + ", "
                     + getFragmentInfo());
            createInner();
            throw new DAALException("create()");
        }
        super.create();
    }

    /**********************************************************************/
    public long read(ByteBuffer buf, long offset) throws DAALException {
        seek(file, offset);
        try {
            getFragmentInfo().start = channel.position();
        } catch (IOException unexpected) {
            throw new IllegalStateException(unexpected);
        }
        getFragmentInfo().offset = buf.limit();
        faultyBuffer = buf;
        faultyRead = true;

        Fault.FaultType triggered
            = FaultManager.triggerFault(READ.toString(),
                                        testFragmentFile.getFaultEvent(), this);
        if (triggered != null) {
            log.info("read(), triggered " + triggered + ", "
                     + getFragmentInfo());
            ((FragmentFault.IOFaultType) triggered).evaluate("read()");
        }
        return super.read(buf, offset);
    }

    /**********************************************************************/
    protected long write(FileChannel ch, ByteBufferList bufList)
        throws DAALException {
        try {
            getFragmentInfo().start = ch.position();
        } catch (IOException unexpected) {
            throw new IllegalStateException(unexpected);
        }
        long offset = 0;
        // *** Begin: Legacy stuff
        // calculate offset based on how much data is in all bufs in list
        for (int i = 0; i < bufList.getBuffers().length; i++) {
            offset += bufList.getBuffers()[i].limit();
        }
        getFragmentInfo().offset = offset;
        // *** End: Legacy stuff

        Fault.FaultType triggered
            = FaultManager.triggerFault(WRITE.toString(),
                                        testFragmentFile.getFaultEvent(), this);
        if (triggered != null) {
            log.info("write(), triggered " + triggered + ", "
                     + getFragmentInfo());
            log.warning("XXX not honoring fault offsets for bufList");
            ((FragmentFault.IOFaultType) triggered).evaluate("write()");
        }
        return super.write(ch, bufList);
    }

    /**********************************************************************/
    public long write(ByteBuffer buf, long offset) throws DAALException {
        getFragmentInfo().start = offset;
        getFragmentInfo().offset = buf.limit();

        Fault.FaultType triggered
            = FaultManager.triggerFault(WRITE.toString(),
                                        testFragmentFile.getFaultEvent(), this);
        if (triggered != null) {
            log.info("write(), triggered " + triggered + ", "
                     + getFragmentInfo());
            if (triggered != FragmentFault.INCOMPLETE_WRITE_ERROR) {
                faultyBuffer = buf;
                faultyRead = false;
            }
            ((FragmentFault.IOFaultType) triggered).evaluate("write()");
        }
        return super.write(buf, offset);
    }

    /**********************************************************************/
    public void commit() throws DAALException {
        Fault.FaultType triggered
            = FaultManager.triggerFault(COMMIT.toString(),
                                        testFragmentFile.getFaultEvent(), this);
        if (triggered != null) {
            log.info("commit(), triggered " + triggered + ", "
                     + getFragmentInfo());
            ((FragmentFault.IOFaultType) triggered).evaluate("commit()");
        }
        testFragmentFile.abortCreate = false;
        super.commit();
    }

    /**********************************************************************/
    public void truncate(long offset) throws DAALException {
        Fault.FaultType triggered
            = FaultManager.triggerFault(TRUNCATE.toString(),
                                        testFragmentFile.getFaultEvent(), this);
        if (triggered != null) {
            log.info("truncate(), triggered " + triggered + ", "
                     + getFragmentInfo());
            ((FragmentFault.IOFaultType) triggered).evaluate("truncate()");
        }
        super.truncate(offset);
    }

    /**********************************************************************/
    protected void seek(RandomAccessFile file, long offset) throws DAALException {
        Fault.FaultType triggered
            = FaultManager.triggerFault(SEEK.toString(),
                                        testFragmentFile.getFaultEvent(), this);
        if (triggered != null) {
            log.info("seek, triggered " + triggered + ", "
                     + getFragmentInfo());
            ((FragmentFault.IOFaultType) triggered).evaluate("seek()");
        }
        super.seek(file, offset);
    }

    /**********************************************************************/
    public void close() throws DAALException {
        Fault.FaultType triggered
            = FaultManager.triggerFault(CLOSE.toString(),
                                        testFragmentFile.getFaultEvent(), this);
        if (triggered != null) {
            log.info("close, triggered " + triggered + ", "
                     + getFragmentInfo());
            ((FragmentFault.IOFaultType) triggered).evaluate("close()");
        }
        super.close();
    }

    /**********************************************************************/
    public void advanceTriggerPosition(int length) {
        if ((faultyBuffer != null) && (length > 0)) {
            // Begin - legacy
            log.info("ByteBuffer position: " + faultyBuffer.position() +
                     ", limit: " + faultyBuffer.limit() +
                     ", remaining: " + faultyBuffer.remaining());
            int limit = faultyBuffer.limit();
            log.info("Changing limit from " + limit + " to " + length);

            faultyBuffer.limit(length);
            try {
                if (faultyRead) {
                    channel.read(faultyBuffer);
                } else {
                    channel.write(faultyBuffer);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Test error: " + e.getMessage());
            }

            log.info("Changing limit from " + length + " to " + limit);
            faultyBuffer.limit(limit);
            log.info("Final ByteBuffer position: " + faultyBuffer.position() +
                     ", limit: " + faultyBuffer.limit()
                     + ", remaining: " + faultyBuffer.remaining());

            faultyBuffer = null;
        }
    }

    /**********************************************************************/
    public FragmentFault.FragmentInfo getFragmentInfo() {
        if (fragmentInfo == null) {
            fragmentInfo
                = new FragmentFault.FragmentInfo(getFragNum(),
                                                 getOID().getChunkNumber(),
                                                 getOID().getObjectType());
        }
        return fragmentInfo;
    }

    /**********************************************************************/
    public void unlock() throws DAALException {
        if ((channel != null) &&  (lock != null)) {
            super.unlock();
        }
    }

    /**********************************************************************/
    public static class Operation {
        String name;
        public Operation(String name) {
            this.name = name;
        }
        public String toString() {
            return FaultyNfsDAAL.class.getName() + "." + name;
        }
    }
}
