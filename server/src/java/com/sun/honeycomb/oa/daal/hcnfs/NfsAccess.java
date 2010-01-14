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
import java.util.Map;
import java.util.HashMap;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskControl;
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
 * DAAL implementation using our own nfs client library.
 */
public class NfsAccess implements AccessIntf {
                
    private static final Logger LOG = Logger.getLogger(NfsAccess.class.getName());
    private static final int maxRetries = 16;
    private static final int retrySleep = 1000;
    
    protected static final int NODE_BASE_ID = 100;
    private static final int NUMDIR = Common.NUM_DIRS;
    private static final int ROOTMAP = Common.NUM_MAPS + 4999;
    private static final int TMPMAP = Common.NUM_MAPS + 5000;

    private static final int READ_WRITE = 0;
    private static final int READ_ONLY = 1;
    private static final int CREATE = 2;
    
    private final DiskId diskId;
    private final int mapId;
    private final String fragName;

    private static final Map lockContention = new HashMap();

    private long nativeCookie;
        
    public NfsAccess(Disk disk, NewObjectIdentifier oid, String fragName) {
        this.fragName = fragName;
        diskId = disk.getId();
        mapId  = oid.getLayoutMapId();        
        nativeCookie = 0;
    }

    public void create() throws DAALException {
        assert(nativeCookie == 0);
        nativeCookie = open(diskId.nodeId() - NODE_BASE_ID, 
                            diskId.diskIndex(), 
                            mapId, 
                            fragName, 
                            CREATE);
        if (nativeCookie == 0) {
            throw new DAALException("Failed to create frag " + this);
        }
    }

    public void open(boolean read_only) throws DAALException, FragmentNotFoundException {
        assert(nativeCookie == 0);    
        int mode = (read_only)? READ_ONLY : READ_WRITE;
        nativeCookie = open(diskId.nodeId() - NODE_BASE_ID, 
                            diskId.diskIndex(), 
                            mapId, 
                            fragName,
                            mode);
        if (nativeCookie == 0) {
            if (!read_only) {
                nativeCookie = open(diskId.nodeId() - NODE_BASE_ID, 
                                    diskId.diskIndex(),
                                    TMPMAP, 
                                    fragName, 
                                    READ_WRITE);
            }
            if (nativeCookie == 0) {
                throw new FragmentNotFoundException("Failed to open frag " + this);
            }
        }
    }
     
    public void commit() throws DAALException {
        assert(nativeCookie == 0);
        
        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _commit(diskId.nodeId() - NODE_BASE_ID, 
                          diskId.diskIndex(), 
                          mapId, 
                          fragName);
            if (res == 0) {
                return;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        throw new DAALException("failed to commit " + this);
    }
    
    public void rollback() throws DAALException {
        assert(nativeCookie == 0);

        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _rollback(diskId.nodeId() - NODE_BASE_ID, 
                            diskId.diskIndex(), 
                            mapId, 
                            fragName);
            if (res == 0) {
                return;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        throw new DAALException("failed to rollback " + this);
    }

    public void delete() throws DAALException {
        if (nativeCookie != 0) {
            // close it first.
            close();
        }
        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _delete(diskId.nodeId() - NODE_BASE_ID, 
                          diskId.diskIndex(), 
                          mapId, 
                          fragName);
            if (res == 0) {
                return;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        throw new DAALException("failed to delete " + this);
    }

    public void close() throws DAALException {
        if (nativeCookie == 0) {
            // we should log a warning here - 
            // code is calling close() without open
            LOG.fine("disk " + this + " is not open");
            return;
        }
        long cookie = nativeCookie;
        nativeCookie = 0;
        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _close(cookie, 0);
            if (res == 0) {
                return;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        // this close requests to free up resources
        res = _close(cookie, 1);
        if (res != 0) {
        throw new DAALException("failed to close " + this + " err " + res);
        }
    }
    
    public boolean isTransient() {
        long checkCookie = open(diskId.nodeId() - NODE_BASE_ID, 
                                diskId.diskIndex(),
                                TMPMAP, 
                                fragName, 
                                READ_ONLY);
        if (checkCookie == 0) {
            return false;
        }
        if (_close(checkCookie, 1) < 0) {
            LOG.severe("failed to close " + this);
        }
        return true;
    }
    
    public boolean isCommitted() {
        long checkCookie = open(diskId.nodeId() - NODE_BASE_ID, 
                                diskId.diskIndex(), 
                                mapId, 
                                fragName,
                                READ_ONLY);
        if (checkCookie == 0) {
            return false;
        }
        if (_close(checkCookie, 1) < 0) {
            LOG.severe("failed to close " + this);
        }
        return true;
    }

    public void replace(ByteBuffer buf) throws DAALException {
        assert(nativeCookie != 0);
        int pos = buf.position();
        int length = buf.remaining();
        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _replace(nativeCookie, buf, pos, length);
            if (res == 0) {
                buf.position(pos + length);
                return;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        throw new DAALException("error(" + res + ") replacing " + this);
    }

    public int read(ByteBuffer buf, long offset) throws DAALException {
        assert(nativeCookie != 0);        
        int pos = buf.position();
        int length = buf.remaining();
        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _read(nativeCookie, offset, buf, pos, length);
            if (res >= 0) {
                buf.position(pos + res);
                return res;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        throw new DAALException("read failed " + res);
    }

    public int write(ByteBuffer buf, long offset) throws DAALException {
        assert(nativeCookie != 0); 
        int pos = buf.position();
        int length = buf.remaining();
        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _write(nativeCookie, offset, buf, pos, length);
            if (res >= 0) {
                if (res != length) {
                    // short write not allowed
                    throw new DAALException("short write " + this);
                }
                buf.position(pos + res);
                return res;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        throw new DAALException("write failed " + res);
    }
     
    public int append(ByteBuffer buf) throws DAALException {
        assert(nativeCookie != 0); 
        int pos = buf.position();
        int length = buf.remaining();
        int res = -1;
        for (int retry = 1; retry <= maxRetries; retry++) {
            res = _append(nativeCookie, buf, pos, length);
            if (res >= 0) {
                if (res != length) {
                    // short write are not allowed.
                    throw new DAALException("short append " + this);
                }
                buf.position(pos + res);
                return res;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + " res = " + res +
                        " - retry left " + (maxRetries - retry));
        }
        throw new DAALException("append failed " + res + "[" + this + "]");
    }

    public void lock() throws DAALException {
        assert(nativeCookie != 0);

        Long myself = new Long(Thread.currentThread().getId());
        Long curHolder = null;

        for (int lockTry = 1; lockTry <= maxRetries; lockTry++) {

            synchronized(lockContention) {
                curHolder = (Long) lockContention.get(fragName);
                if (curHolder == null) {
                    curHolder = myself;
                    lockContention.put(fragName, myself);
                }
            }

            if (curHolder.longValue() == myself.longValue()) {
                if (_trylock(nativeCookie) == 0) {
                    return;
                }
                if (!diskStillGood()) {
                    LOG.warning("Disk not good anymore - aborting op" + this);
                    synchronized(lockContention) {
                        lockContention.remove(fragName);
                    }
                    return;
                }
            }

            LOG.info("Try " + lockTry + " to lock by thread " + 
              myself.longValue() +" failed.  " +
              "Current holder = " +  curHolder.longValue() +
              " Sleeping " + retrySleep + " msecs..." + this);            

            try {
                Thread.sleep(retrySleep);
            } catch (InterruptedException ie) {
                LOG.warning("lock retry sleep prematurely interrupted" + 
                  this + ie);
            }
        }
        if (curHolder.longValue() == myself.longValue()) {
            synchronized(lockContention) {
                lockContention.remove(fragName);
            }
        }
        
        LOG.warning("Failed to acquire lock " + this);
        LOG.log(ExtLevel.EXT_SEVERE, "Failed to acquire lock for object " +
            this + ", need to reboot the cell to aleviate the problem");
        throw new DAALException("Failed to acquire lock."); 
    }

    public boolean unlock() {

        Long myself = new Long(Thread.currentThread().getId());
        Long curHolder = null;

        synchronized(lockContention) {
            curHolder = (Long) lockContention.remove(fragName);
        }
        if (curHolder == null || 
          curHolder.longValue() != myself.longValue()) {
            LOG.warning("Trying to unlock fragment " + this +
              " from a different owner : " +
              "curHolder = " + 
              (curHolder == null ? "none" : curHolder.longValue()) +
              ", myself = " + myself.longValue());
        }
        
        if (nativeCookie != 0) {
            _unlock(nativeCookie);
            return true;
        }
        return false;
    }

    public long length() throws DAALException {
        assert(nativeCookie != 0);
        long res = _length(nativeCookie);
        if (res < 0) {
            throw new DAALException("failed to get fragment size " + this);
        }
        return res;
    }

    public String toString() {
        return " [disk " + diskId.toStringShort() + ", name " + fragName + "] ";
    }
    
    
    // PROTECTED
    
    protected void finalize() throws Throwable {
        try {            
            if (nativeCookie != 0) {
                LOG.severe("DAAL fragment leaking " + this);
                close();
            }
        } finally {
            super.finalize();
        }
    }
    
    // PRIVATE

    private boolean diskStillGood() {
        Disk[] allDisks = DiskProxy.getClusterDisks();
        if (allDisks == null) {
            return false;
        }   
        Disk disk = null;
        for (int d = 0; d < allDisks.length; d++) {
            if (allDisks[d] != null) {
                if (diskId.equals(allDisks[d].getId())) {
                    disk = allDisks[d];
                    break; 
                }   
            }   
        }   
        if (disk == null) {
            return false; 
        }   
        if (!disk.isEnabled()) {
            return false;
        }

        DiskControl api = DiskProxy.getAPI(disk.nodeId());
        if (api != null) {
            try {
                if (!api.isDiskConfigured(diskId)) {
                    return false;
                }
            } catch (ManagedServiceException me) {
                LOG.severe("check disk configured " + this + " got " + me);
            }
        } else {
            LOG.severe("failed to get DiskMonitor API for " + disk.nodeId());
        }
  
        try {
            DiskId id = disk.getId();
            if (!HcNfsMgmt.dbStillGood(id)) {
                HcNfsMgmt.dbRepair(id);
            }
        } catch (ManagedServiceException me) {
            LOG.severe("check db consistency " + this + " got " + me);
        }
        return true;        
    }

    private long open(int nodeid, int diskid, int mapid, String fragName, int mode) {
        for (int retry = 1; retry <= maxRetries; retry++) {
            long res;
            if (mode == CREATE) {
                res = _create(nodeid, diskid, mapid, fragName);
            } else {
                res = _open(nodeid, diskid, mapid, fragName, mode);
            }
            if (res != 0) {
                if (res == -1 && mode != CREATE) {
                    /* fragment does not exist */
                    return 0;
                }
                return res;
            }
            if (!diskStillGood()) {
                LOG.warning("Disk not good anymore - aborting op" + this);
                break;
            }
            LOG.warning("operation failed on " + this + 
                        " - retry left " + (maxRetries - retry));
        }
        return 0;
    }

    
    // NFS CLIENT NATIVE LIBRARY
    
    private native long _create(int nodeid, int diskid, int mapid, String fragName);
    private native long _open(int nodeid, int diskid, int mapid, String fragName, int readOnly);
    private native int _delete(int nodeid, int diskid, int mapid, String fragName);
    private native int _commit(int nodeid, int diskid, int mapid, String fragName);    
    private native int _rollback(int nodeid, int diskid, int mapid, String fragName);
    
    private native int _close(long cookie, int freeOnError);
    private native int _trylock(long cookie);
    private native void _unlock(long cookie);    
    private native int _replace(long cookie, ByteBuffer buf, int position, int remaining);
    private native int _read(long cookie, long offset, ByteBuffer buf, int position, int remaining);
    private native int _write(long cookie, long offset, ByteBuffer buf, int position, int remaining);
    private native int _append(long cookie,  ByteBuffer buf, int position, int length);
    private native long _length(long cookie);
    
    static {
        System.loadLibrary("hcnfs");
    }    
}
