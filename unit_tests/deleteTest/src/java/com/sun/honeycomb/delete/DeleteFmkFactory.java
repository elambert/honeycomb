

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



package com.sun.honeycomb.delete;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.IncompleteObjectException;
import com.sun.honeycomb.coordinator.MetadataCoordinator;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.datadoctor.ScanFrags;
import com.sun.honeycomb.resources.ByteBufferPool;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.RandomAccessFile;
import java.io.FileFilter;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.emd.InMemMetadata;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.SysCacheUtils;
import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.ReflectedAccess;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutProxy;
import com.sun.honeycomb.diskmonitor.DiskMonitor;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.datadoctor.RemoveTempFrags;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Decoder;
import java.util.BitSet;
import com.sun.honeycomb.common.ObjectReliability;

import java.util.logging.Logger;
import java.util.Random;
import java.io.File;
import java.io.FileNotFoundException;
import java.security.MessageDigest;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.TestFragmentFile;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.bulk.BackupRestoreMain;
import com.sun.honeycomb.oa.bulk.Session;
import com.sun.honeycomb.datadoctor.RemoveTempFrags;
import com.sun.honeycomb.delete.util.FragInfo;
import com.sun.honeycomb.delete.util.Util;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public class DeleteFmkFactory
    implements OpCodeFactory {

    private static final Logger LOG = Logger.getLogger(DeleteFmkFactory.class.getName());
    public static boolean verbose = false;

    public DeleteFmkFactory(boolean verboseRun) {
        verbose = verboseRun;
    }

    public OpCode allocateOpCode(int opCode,
                                 Object[] args) {
        return(new DeleteFmkOpCode(opCode, args));
    }

    private static class DeleteFmkOpCode
        extends OpCode {

        private DeleteFmkOpCode(int opCode,
                                Object[] args) {
            super(opCode, args);
        }

        // Function to log to the test log.  If verbose is true, the
        // output will also be logged to stdout.
        private void log(String s) {
            log(s, verbose);
        }

        // Tests that want to control if stdout is used regardless of
        // the value of verbose can use this method instead of the above.
        private void log(String s, boolean alwaysToStdout) {
            LOG.info(s);
            if (alwaysToStdout)
                System.out.println(s);
        }

        protected NewObjectIdentifier executeStore(int size, boolean shouldSucceed)
            throws ArchiveException {
            NewObjectIdentifier oid = NewObjectIdentifier.NULL;
            MetadataCoordinator coordinator = MetadataCoordinator.getInstance();
            CacheRecord record = new ExtendedCacheEntry();
            Context ctx = new Context();

            ByteBufferList extraMetadata = null;
            ByteBufferList dataList = null;
            ByteBuffer byteBuffer = null;
            SystemMetadata smd;

            Random random = new Random();
            boolean useRandomBytes = true; // nice to tweak this on/off for
                                           // analyzing corruption

            try {
                oid = coordinator.createObject(-1, record, 0, (long)0, (long)0, (byte)0, ctx);
                MessageDigest md =
                    MessageDigest.getInstance(Constants.CURRENT_HASH_ALG);
                dataList = new ByteBufferList();
                int remainingSize = size;
                // There's a limit to how much we can write at once
                int bufSize = (remainingSize > Constants.MAX_ALLOCATE ?
                               Constants.MAX_ALLOCATE : remainingSize);
                byte[] bytes = new byte[bufSize];
                if (useRandomBytes) {
                    // use random content
                    random.nextBytes(bytes);
                } else {
                    // use predictible content
                    for (int i = 0; i < bufSize; i++) {
                        bytes[i] = 'a';
                        if (i % 1000 == 0) {
                            // for readability
                            bytes[i] = '\n';
                        }
                    }
                }
                byteBuffer =
                    ByteBufferPool.getInstance().checkOutBuffer(bufSize);
                byteBuffer.put(bytes, 0, bufSize);
                byteBuffer.flip();
                dataList.appendBuffer(byteBuffer);
                ByteBufferList writelist;

                while (remainingSize > 0) {
                    if (remainingSize < Constants.MAX_ALLOCATE) {
                        writelist = dataList.slice(0, remainingSize);
                        md.update(bytes, 0, remainingSize);
                    } else {
                        writelist = dataList.slice(0, bufSize);
                        md.update(bytes);
                    }

                    coordinator.writeData(writelist, 0, ctx);
                    byteBuffer.rewind();
                    writelist.clear();
                    remainingSize -= bufSize;
                }

                extraMetadata = new ByteBufferList();
                smd = coordinator.closeData(extraMetadata, false, ctx);
                coordinator.writeMetadata(extraMetadata, 0, ctx);

                byte[] hashBytes = md.digest();
                String calcHash = ByteArrays.toHexString(hashBytes);

                // Verify this is correct per the hash stored in SysMD
                String storeHash = ByteArrays.toHexString(smd.getContentHash());
                if (!calcHash.equals(storeHash)) {
                    throw new ArchiveException("store corruption detected: " +
                                               "stored object had hash " + calcHash +
                                               " but oa thinks it was " + storeHash);
                } else {
                    log("hash on store was " + storeHash + " as expected");
                }

                smd = coordinator.closeMetadata(ctx);
            } catch (Exception e) {
                if(!shouldSucceed) {
                    return NewObjectIdentifier.NULL;
                } else {
                    throw new ArchiveException(e);
                }
            } finally {
                if (dataList != null) {
                    dataList.checkInBuffers();
                }
                if (byteBuffer != null) {
                    ByteBufferPool.getInstance().checkInBuffer(byteBuffer);
                }
                if (extraMetadata != null) {
                    extraMetadata.checkInBuffers();
                }
            }

            ctx.dispose();
            log("stored object of size " + size + " as oid " + oid);
            if (verbose) {
                log(FragInfo.getObjInfo(oid), true);
                // log(FragInfo.getFragInfo(oid, 0), true);
            }
            if(!shouldSucceed) {
                throw new ArchiveException("Store succeeded when it should have failed!");
            }
            return(oid);
        }

        protected NewObjectIdentifier executeAddMD(NewObjectIdentifier link, boolean shouldSucceed)
            throws ArchiveException {

            MetadataCoordinator coordinator = MetadataCoordinator.getInstance();
            CacheRecord record = new ExtendedCacheEntry();
            Context ctx = new Context();
            NewObjectIdentifier oid = null;
            ByteBufferList extraMetadata = null;
            SystemMetadata smd = null;

            try {
                oid = coordinator.createMetadata(link, record,
                        0, (long)0, (long)0, (byte)0,
                        ctx);

                extraMetadata = new ByteBufferList();
                smd = coordinator.closeData(extraMetadata, false, ctx);

                coordinator.writeMetadata(extraMetadata, 0, ctx);
                smd = coordinator.closeMetadata(ctx);
            } catch (ArchiveException e) {
                if(!shouldSucceed) {
                    return NewObjectIdentifier.NULL;
                } else {
                    throw new ArchiveException(e);
                }
            } finally {
                if (extraMetadata != null) {
                    extraMetadata.checkInBuffers();
                }
            }

            ctx.dispose();

            log("added metadata to oid " + link + " and got new oid " + oid);
            if (verbose) {
                log(FragInfo.getObjInfo(oid), true);
                // log(FragInfo.getFragInfo(oid, 0), true);
            }

            if(!shouldSucceed) {
                throw new ArchiveException("Add metadata succeeded when it should have failed!");
            }

            return(oid);
        }

        protected void executeDelete(NewObjectIdentifier oid,
                                     boolean shouldSucceed)
            throws ArchiveException {
            MetadataCoordinator coordinator = MetadataCoordinator.getInstance();
            NewObjectIdentifier dataOid = resolveLink(oid);

            try {
                log("calling delete on " + oid + " with shouldSuceed="
                    + shouldSucceed);

                coordinator.delete(oid, true, true);

                // On real cluster, this is called via api callback on local proxy;
                // in UT framework, must call explicitly. Clears block cache.
                Coordinator coord = Coordinator.getInstance();
                coord.deleteFromLocalCache(oid, dataOid);

                if (!shouldSucceed) {
                    throw new ArchiveException("Delete succeeded when it should have failed");
                }

            } catch (IncompleteObjectException e) {
                if (shouldSucceed) {
                    throw e;
                }
            } catch (ArchiveException e) {
                if (shouldSucceed) {
                    throw e;
                }

                String[] expectedTexts = {
                    "Error opening fragments",
                    "Cannot delete an object with an unexpired retention time or possession of legal holds",
                    "Failed to delete - only deleted",
                    "No oa ctx for"
                };

                int index = -1;
                for (int i = 0; i < expectedTexts.length; i++) {
                    if (e.getMessage().startsWith (expectedTexts[i])) {
                        index = i;
                        break;
                    }
                }

                if (index == -1) {
                    log("Unexpected exception text: \""
                                + e.getMessage() + "\"", true);
                    throw e;
                }
            }

            if (verbose) {
                log(FragInfo.getObjInfo(oid), true);
                // log(FragInfo.getFragInfo(oid, 0), true);
            }
        }

        protected void executeRecover(NewObjectIdentifier oid,
                                      int fragID)
            throws ArchiveException {

            int layoutMapId = oid.getLayoutMapId();
            Layout layout =
                LayoutClient.getInstance().getLayoutForRetrieve(layoutMapId);
            Disk disk = layout.getDisk(fragID);
            try {
                OAClient.getInstance().recover(oid, fragID, disk);
            } catch (OAException oae) {
                throw new ArchiveException("Recovery Failed: " + oae);
            }
        }

        protected void executeRemoveTmp(NewObjectIdentifier oid, int fragID)
                throws ArchiveException {
            RemoveTempFrags rmtf = new RemoveTempFrags();
            int layoutMapId = oid.getLayoutMapId();
            Layout layout =
                LayoutClient.getInstance().getLayoutForRetrieve(layoutMapId);
            Disk disk = layout.getDisk(fragID);
            rmtf.init("RemoveTempFrags", disk.getId());
            rmtf.processObject(oid);
        }

        protected void executeRemoveTmpTask(int removalTime) throws ArchiveException {
            RemoveTempFrags rmtf = new RemoveTempFrags();
            rmtf.setRemovalTime(removalTime);

            // It is necessary for RemoveTempFrags task to iterate ate least as
            // many times as there are chunks in order to guarantee a full clean
            // up of all tmp frags.
            for (int iterations = 0; iterations < Constants.MAX_TEST_CHUNKS; iterations++){
                Disk[] allDisks = DiskProxy.getClusterDisks();
                for (int diskID = 0; diskID < allDisks.length; diskID++) {
                    Disk disk = allDisks[diskID];
                    rmtf.init("RemoveTempFrags", disk.getId());
                    rmtf.newDiskMask(LayoutProxy.getCurrentDiskMask());
                    rmtf.step(0);
                }
            }
        }
        
        protected void executeRemoveM(NewObjectIdentifier oid) throws ArchiveException {
            MetadataCoordinator coordinator = MetadataCoordinator.getInstance();
            NewObjectIdentifier dataOid = resolveLink(oid);

            log("calling remove metadata on " + oid);
            // Remove MD from the system cache
            SysCacheUtils.removeRecord(oid);

	        // On real cluster, this is called via api callback on local proxy;
	        //in UT framework, must call explicitly. Clears block cache.
	        Coordinator coord = Coordinator.getInstance();
	        coord.deleteFromLocalCache(oid, dataOid);
                
            if (verbose) {
                log(FragInfo.getObjInfo(oid), true);
                // log(FragInfo.getFragInfo(oid, 0), true);
            }
        }

        protected void executeExistsTmp(NewObjectIdentifier oid, int fragID)
               throws ArchiveException {
            int layoutMapId = oid.getLayoutMapId();
            Layout layout =
                LayoutClient.getInstance().getLayoutForRetrieve(layoutMapId);
            Disk disk = layout.getDisk(fragID);

            File fragFile = new File(Common.makeTmpFilename(oid, disk, fragID));

            if (!fragFile.exists())
                throw new ArchiveException("Tmp Frag number: " + fragID +
                        " does not exist for object: " + oid);
        }

        protected void executeNotExistsTmp(NewObjectIdentifier oid, int fragID)
                throws ArchiveException {
            int layoutMapId = oid.getLayoutMapId();
            Layout layout = LayoutClient.getInstance().getLayoutForRetrieve(
                    layoutMapId);
            Disk disk = layout.getDisk(fragID);

            File fragFile = new File(Common.makeTmpFilename(oid, disk, fragID));

            if (fragFile.exists())
                throw new ArchiveException("Tmp Frag number: " + fragID
                        + " exists for object: " + oid);
        }

        protected void executeVerifyTmps(int howMany) throws ArchiveException {
            Disk[] disks = DiskProxy.getClusterDisks();
            int count = 0;
            for (int i = 0; i < disks.length; i++) {
                Disk disk = disks[i];
                File[] files = new File(disk.getPath() + Common.dirSep + Common.closeDir).listFiles();
                for (int j = 0; j < files.length; j++) {
                    if (Common.extractOIDFromFilename(files[j].getName())
                        != NewObjectIdentifier.NULL) {
                       count++;
                    }
                }
            }

            if (howMany == -1 && count == 0)
                throw new ArchiveException("No tmp frags found but was expecting some.");
            else if (howMany != -1 && howMany != count)
                throw new ArchiveException("Was expecting: " + howMany
                                       + " but found " + count + " tmp frags.");
        }

        protected void executeExistsData(NewObjectIdentifier oid, int fragID)
                throws ArchiveException {
            int layoutMapId = oid.getLayoutMapId();
            Layout layout = LayoutClient.getInstance().getLayoutForRetrieve(
                    layoutMapId);
            Disk disk = layout.getDisk(fragID);

            File fragFile = new File(Common.makeFilename(oid, disk, fragID));

            if (fragFile.exists())
                throw new ArchiveException("Data Frag number: " + fragID
                        + " exists for object: " + oid);
        }

        protected void executeRetrieveM(NewObjectIdentifier oid,
                                        long offsetToRead, 
                                        long lengthToRead, 
                                        boolean shouldSucceed)
                throws ArchiveException {
            Coordinator coordinator = Coordinator.getInstance();
            Context ctx = new Context();

            int bufferSize = coordinator.getReadBufferSize();
            long totalRead = 0;
            long offsetPassedIn = offsetToRead; // used to detect range retrieve
            int read;
            int readLength = (int) Math.min((long) bufferSize, lengthToRead
                    - totalRead);
            ByteBufferList bufferList = new ByteBufferList();
            byte[] bytes = new byte[bufferSize];

            FileOutputStream out = null;
            String outfile = Constants.getDiskRoot() + "/retrieve.out";

            try {
                out = new FileOutputStream(outfile);
            } catch (FileNotFoundException fnfe) {
                throw new ArchiveException("can't open " + outfile);
            }

            boolean opFailed = false;
            try {
                while (totalRead < lengthToRead) {

                    read = coordinator.readMetadata(oid, bufferList, offsetToRead,
                            readLength, readLength < bufferSize);

                    if (read < 1)
                        break;

                    writeBufferList(bufferList, bytes, out);
                    bufferList.clear();

                    totalRead += read;
                    offsetToRead += read;
                    readLength = (int) Math.min((long) bufferSize, lengthToRead
                            - totalRead);

                    if (read < bufferSize) {
                        break;
                    }
                }
            } catch (DeletedObjectException e) {
                log("DeletedObjectException for oid " + oid);
                if (shouldSucceed) {
                    throw e;
                } else {
                    opFailed = true;
                }
            } catch (IOException ioe) {
                log("IOException for oid " + oid);
                if (shouldSucceed) {
                    throw new ArchiveException("IOError: " + ioe, ioe);
                } else {
                    opFailed = true;
                }
            } catch (Throwable t) {
                // XXX should we catch this?
                log("Throwable for oid " + oid + ": " + t);
                if (shouldSucceed) {
                    throw new ArchiveException("Throwable caught: " + t, t);
                } else {
                    opFailed = true;
                }
            } finally {
                bufferList.clear();
                ctx.dispose();
            }

            if (opFailed == true && shouldSucceed == false) {
                log("retrieved oid " + oid + " failed as expected");
                return;
            }

            if (!shouldSucceed) {
                throw new ArchiveException("Retrieve oid " + oid
                        + "  succeeded when should have failed");
            }

            log("retrieved oid " + oid);

            // Don't change hash because we read metadata and we should compare
            // with the hash of the metadata object 
            NewObjectIdentifier oidForHash = oid;

            // If we are doing a full read, check the sha1.
            if (offsetPassedIn == 0 && lengthToRead == Long.MAX_VALUE
                    && oidForHash != null) {
                // calculate the sha1 of the retrieved file
                String retrievedHash = Util.computeHash(outfile);

                // Find out what the expected sha1 is by asking system MD.
                // We have to loop since we might be missing frags.
                FragInfo fi = null;
                for (int i = 0; i <= Constants.MAX_FRAG_ID; i++) {
                    fi = new FragInfo(oidForHash, i);
                    if (fi != null && fi.sm != null) {
                        String expectedHash = ByteArrays.toHexString((fi.sm
                                .getContentHash()));
                        break;
                    } else {
                        LOG.warning("Missing system MD for oid " + oidForHash
                                + " fragment " + i);
                    }
                }

                if (fi == null || fi.sm == null) {
                    LOG.warning("Did not find system MD for oid " + oidForHash
                            + " - skipping hash");
                    return;
                }

                String expectedHash = ByteArrays.toHexString((fi.sm
                        .getContentHash()));

                if (!expectedHash.equals(retrievedHash)) {
                    String msg = "corruption on retrieve detected for oid "
                            + oidForHash + ": stored object has hash "
                            + expectedHash + " but retrieved hash was "
                            + retrievedHash + " for file " + outfile;
                    throw new ArchiveException(msg);
                } else {
                    log("oid " + oidForHash + " has expected hash "
                            + retrievedHash);
                }
            } else {
                if (oidForHash == null) {
                    log("skipping hash calculation for oid " + oid);
                    log("maybe we couldn't access system md for this oid");
                } else {
                    log("skipping hash calculation for range retrieve offset="
                            + offsetPassedIn + "; length=" + lengthToRead);
                }
            }
        }

        protected void executeRetrieve(NewObjectIdentifier oid,
                                       long offsetToRead,
                                       long lengthToRead,
                                       boolean shouldSucceed)
            throws ArchiveException {

            Coordinator coordinator = Coordinator.getInstance();
            Context ctx = new Context();

            int bufferSize = coordinator.getReadBufferSize();
            long totalRead = 0;
            long offsetPassedIn = offsetToRead; // used to detect range retrieve
            int read;
            int readLength = (int)Math.min((long)bufferSize, lengthToRead - totalRead);
            ByteBufferList bufferList = new ByteBufferList();
            byte[] bytes = new byte[bufferSize];

            FileOutputStream out = null;
            String outfile = Constants.getDiskRoot() + "/retrieve.out";

            try {
                out = new FileOutputStream(outfile);
            } catch (FileNotFoundException fnfe) {
                throw new ArchiveException("can't open " + outfile);
            }

            boolean opFailed = false;
            try {
                while (totalRead < lengthToRead) {

                    read = coordinator.readData(oid,
                                                bufferList,
                                                offsetToRead,
                                                readLength,
                                                readLength < bufferSize);

                    if (read < 1)
                        break;

                    writeBufferList(bufferList, bytes, out);
                    bufferList.clear();

                    totalRead += read;
                    offsetToRead += read;
                    readLength = (int)Math.min((long)bufferSize, lengthToRead - totalRead);

                    if (read < bufferSize) {
                        break;
                    }
                }
            } catch (DeletedObjectException e) {
                log("DeletedObjectException for oid " + oid);
                if (shouldSucceed) {
                    throw e;
                } else {
                    opFailed = true;
                }
            } catch (IOException ioe) {
                log("IOException for oid " + oid);
                if (shouldSucceed) {
                    throw new ArchiveException("IOError: " + ioe, ioe);
                } else {
                    opFailed = true;
                }
            } catch (Throwable t) {
                // XXX should we catch this?
                log("Throwable for oid " + oid + ": " + t);
                if (shouldSucceed) {
                    throw new ArchiveException("Throwable caught: " + t, t);
                } else {
                    opFailed = true;
                }
            } finally {
                bufferList.clear();
                ctx.dispose();
            }

            if (opFailed == true && shouldSucceed == false) {
                log("retrieved oid " + oid + " failed as expected");
                return;
            }

            if (!shouldSucceed) {
                throw new ArchiveException("Retrieve oid " + oid  +
                                           "  succeeded when should have failed");
            }

            log("retrieved oid " + oid);

            // we must dereference the MD object to find the data obj
            // hash, since we retrieve the data in this call.
            NewObjectIdentifier oidForHash = resolveLink(oid);

            // If we are doing a full read, check the sha1.
            if (offsetPassedIn == 0 && lengthToRead == Long.MAX_VALUE &&
                oidForHash != null) {
                // calculate the sha1 of the retrieved file
                String retrievedHash = Util.computeHash(outfile);

                // Find out what the expected sha1 is by asking system MD.
                // We have to loop since we might be missing frags.
                FragInfo fi = null;
                for (int i = 0; i <= Constants.MAX_FRAG_ID; i++) {
                    fi = new FragInfo(oidForHash, i);
                    if (fi != null && fi.sm != null) {
                        String expectedHash =
                            ByteArrays.toHexString((fi.sm.getContentHash()));
                        break;
                    } else {
                        LOG.warning("Missing system MD for oid " + oidForHash +
                                    " fragment " + i);
                    }
                }

                if (fi == null || fi.sm == null) {
                    LOG.warning("Did not find system MD for oid " +
                                oidForHash + " - skipping hash");
                    return;
                }

                String expectedHash =
                    ByteArrays.toHexString((fi.sm.getContentHash()));

                if (!expectedHash.equals(retrievedHash)) {
                    String msg =
                        "corruption on retrieve detected for oid " +
                        oidForHash + ": stored object has hash " +
                        expectedHash + " but retrieved hash was " +
                        retrievedHash + " for file " + outfile;
                    throw new ArchiveException(msg);
                } else {
                    log("oid " + oidForHash +
                        " has expected hash " + retrievedHash);
                }
            } else {
                if (oidForHash == null) {
                    log("skipping hash calculation for oid " + oid);
                    log("maybe we couldn't access system md for this oid");
                } else {
                    log("skipping hash calculation for range retrieve offset=" +
                        offsetPassedIn + "; length=" + lengthToRead);
                }
            }
        }

        /* This method resolves MD or data OID to data OID.
         */
        private NewObjectIdentifier resolveLink(NewObjectIdentifier oid)
            throws ArchiveException {
            NewObjectIdentifier dataOid = null;

            if (oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE) {
                dataOid = executeDeref(oid);
            } else if (oid.getObjectType() == NewObjectIdentifier.DATA_TYPE) {
                dataOid = oid;
            } else {
                throw new ArchiveException("unrecognized type for oid " + oid);
            }
            return dataOid;
        }

        /* This method resolves metadata OID to data OID.
         */
        protected NewObjectIdentifier executeDeref(NewObjectIdentifier oid)
            throws ArchiveException {
            NewObjectIdentifier result = null;

            if (oid.getObjectType() != NewObjectIdentifier.METADATA_TYPE) {
                throw new ArchiveException("Not metadata, cannot deref oid " + oid);
            }

            // We have to loop since we might be missing frags.
            // Unlike open-based code below, this works even on objects with data loss.
            FragInfo fi = null;
            for (int i = 0; i <= Constants.MAX_FRAG_ID; i++) {
                fi = new FragInfo(oid, i);
                if (fi != null && fi.sm != null) {
                    result = (NewObjectIdentifier)
                        fi.sm.get(SystemMetadata.FIELD_LINK);
                    log("Found link OID " + result);
                    break;
                }
            }

            if (result == null) {
                throw new IllegalStateException("data oid not found");
            }

            /* Alternative deref, close to MetadataCoordinator.resolveLink
            Context ctx = new Context();
            try {
                SystemMetadata smd = OAClient.getInstance().open(oid, ctx);
                result = smd.getLink();
            } finally {
                ctx.dispose();
            }
            */

            return(result);
        }

        protected void executeFragAction(String action,
                                         NewObjectIdentifier oid,
                                         int fragID,
                                         long expectedSize)
            throws ArchiveException {
            int layoutMapId = oid.getLayoutMapId();
            Layout layout
                = LayoutClient.getInstance().getLayoutForRetrieve(layoutMapId);
            Disk d = layout.getDisk(fragID);
            String realName = Common.makeFilename(oid, d, fragID);
            File realFile = new File(realName);
            File hiddenFile = new File(realFile.getParent() + File.separator
                                        + "." + realFile.getName() + "-hidden");

            if (action.equals(Constants.FRAGACTION_SCAN)) {
                try {
                    Class[] paramClasses = new Class[] { FragmentFile.class };
                    Method scanFragmentMethod
                        = ReflectedAccess.getMethod(ScanFrags.class,
                                                    "scanFragment",
                                                    paramClasses);
                    ScanFrags scanFrags = new ScanFrags();
                    scanFrags.init("ScanFrags", null);
                    Object[] params
                        = new Object[] { new FragmentFile(oid, fragID, d) };
                    scanFragmentMethod.invoke(scanFrags, params);
                } catch (InvocationTargetException ite) {
                    throw new ArchiveException("" + ite.getTargetException());
                } catch(Exception e) {
                    throw new ArchiveException("Scan Failed: " + e);
                }
            } else if (action.equals(Constants.FRAGACTION_CORRUPT)) {
                RandomAccessFile fout;
                try {
                    fout = new RandomAccessFile(realFile, "rws"); // open rw sync
                } catch (FileNotFoundException fnfe) {
                    throw new ArchiveException ("Cannot find file to corrupt " +
                                                realFile);
                }
                System.out.println("Corrupting 1st " + expectedSize + " bytes of " + realFile);
                while(expectedSize-->0) {
                    try {
                        fout.writeByte((byte)0); // corrupt by writing 1s
                    } catch (IOException ioe) {
                        try {
                            fout.close();
                        } catch (IOException cioe) {
                        }
                        throw new ArchiveException("Failed to write to " + realFile);
                    }
                }

            } else if (action.equals(Constants.FRAGACTION_TRUNCATE)) {
                RandomAccessFile fout;
                try {
                    fout = new RandomAccessFile(realFile, "rws"); // open rw sync
                } catch (FileNotFoundException fnfe) {
                    throw new ArchiveException ("Cannot find file to corrupt " +
                                                realFile);
                }
                try {
                    if (fout.length() < expectedSize) {
                        throw new ArchiveException("Actual length " + fout.length() + " less than "
                                                   + expectedSize);
                    }
                    System.out.println("Truncating " + realFile + " to " + expectedSize + " bytes");
                    fout.setLength(expectedSize);
                } catch (IOException ioe) {
                    throw new ArchiveException("Failed to truncate " + realFile + " " + ioe);
                } finally {
                    try {
                        fout.close();
                    } catch (IOException cioe) {
                    }
                }

            } else if (action.equals(Constants.FRAGACTION_REMOVE)) {
                if (!realFile.exists()) {
                    throw new ArchiveException("Can't find original file " +
                                               realFile + " to remove.");
                }
                realFile.renameTo(hiddenFile);
            } else if (action.equals(Constants.FRAGACTION_MOVE)) {
                LayoutClient lc = LayoutClient.getInstance();

                // We'll move this fragment over to another disk where one of 
                // the other fragments reside :) 
                Disk newd = layout.getDisk((fragID + 1) % 6);
                
                File newFile = new File(Common.makeFilename(oid, newd, fragID));

                realFile.renameTo(newFile); 
                log("Moving fragment from: " + realFile +  " to " + newFile);
            } else if (action.equals(Constants.FRAGACTION_RESTORE)) {
                if (!hiddenFile.exists()) {
                    throw new ArchiveException("Can't find hidden file " +
                                               hiddenFile + " to restore.");
                }
                hiddenFile.renameTo(realFile);
            } else if (action.equals(Constants.FRAGACTION_NOTZERO)) {
                if (realFile.exists() && realFile.length() == 0) {
                    throw new ArchiveException("Unexpectedly, fragment file " +
                                               realFile + " has length " + realFile.length());
                }
            } else if (action.equals(Constants.FRAGACTION_FILESIZE)) {
                if (realFile.exists() && realFile.length() != expectedSize) {
                    throw new ArchiveException("Unexpectedly, fragment file " +
                                               realFile + " has length " + realFile.length() +
                                               "; expected " + expectedSize);
                }

            } else if (action.equals(Constants.FRAGACTION_ABSENT)) {
                if (realFile.exists()) {
                    throw new ArchiveException("Fragment file " + realFile + " found");
                }

            } else if (action.equals(Constants.FRAGACTION_INTERNALSIZE)) {
                TestFragmentFile ff = null;
                SystemMetadata sm = null;
                FragmentFooter footer = null;
                String errstring = "";
                try {
                    ff = new TestFragmentFile(oid, fragID, d);
                    sm = ff.readSystemMetadata();
                    footer = ff.getFooter();
                } catch (Throwable t) {
                    throw new ArchiveException("failed to check internal size"
                                               + " for file " +  realFile + ": " + t);
                }
                if (expectedSize != sm.getSize()) {
                    errstring += "Unexpectedly, fragment file " +
                        realFile + " System Metadata has size " + sm.getSize() +
                        ", expected " + expectedSize + "\n";
                }
                if (expectedSize != footer.size) {
                    errstring += "Unexpectedly, fragment file " +
                        realFile + " Footer has size " + footer.size +
                        ", expected " + expectedSize + "\n";
                }
                if (!errstring.equals("")) {
                    throw new ArchiveException(errstring);
                }
            } else {
                throw new ArchiveException("unknown action " + action);
            }

            // Only print size if it is meaningful.
            String sizeString = (expectedSize == Constants.SIZE_UNKNOWN ?
                                 "" : (" size " + expectedSize));
            log("executed action " + action + " for oid " + oid +
                " and frag " + fragID + sizeString);
        }

        private static final String DELETED_CHECK = "deleted_check";
        private static final String REFCNT_CHECK = "refcnt_check";

        protected void executeFragIsDeleted(NewObjectIdentifier oid,
                                            int fragID,
                                            boolean shouldBeDeleted)
            throws ArchiveException {
            executeCheckFragment(DELETED_CHECK, oid, fragID, shouldBeDeleted,
                                 Constants.REFCNT_UNKNOWN,
                                 Constants.REFCNT_UNKNOWN);
        }

        protected void executeRefcntCheck(NewObjectIdentifier oid,
                                          int fragID,
                                          int refcnt,
                                          int maxRefcnt)
            throws ArchiveException {
            executeCheckFragment(REFCNT_CHECK, oid, fragID,
                                 false, refcnt, maxRefcnt);
        }

        /**
         * Helper function for the fragment inspection type operations
         */
        private void executeCheckFragment(String action,
                                          NewObjectIdentifier oid,
                                          int fragID,
                                          boolean shouldBeDeleted,
                                          int refcnt,
                                          int maxRefcnt)
            throws ArchiveException {

            int start = fragID;
            int end = fragID;

            if (fragID == Constants.ALL_FRAGS) {
                start = 0;
                end = Constants.MAX_FRAG_ID;
            }

            // which check to perform?
            String s = action + " [frag " + start +
                " - frag " + end + "] for oid " + oid;
            if (action.equals(DELETED_CHECK)) {
                s += " shouldBeDeleted is " + shouldBeDeleted;
            }
            if (action.equals(REFCNT_CHECK)) {
                s += " refcnt " + refcnt + " maxRefcnt " + maxRefcnt;
            }
            log(s);

            // perform check on desired fragment(s)
            for (int i = start; i <= end; i++) {

                // multichunk support: walk all chunks for this frag number
                // XXX should we only do this in ALL_FRAGS case? [dmehra]
                //
                FragInfo fi = new FragInfo(oid, i);
                if (fi.chunkId != 0) {
                    throw new ArchiveException("Don't pass an oid for non-zero chunk to executeCheckFrag method! Your oid: " + oid);
                }
                int numChunks = fi.fieldsAreValid ? fi.numChunks : 1;
                NewObjectIdentifier chunkOid = oid;

                for (int j = 0; j < numChunks;) {
                    fi = new FragInfo(chunkOid, i);

                    int mapId = chunkOid.getLayoutMapId();
                    int nextMap = LayoutClient.getConsecutiveLayoutMapId(mapId);
                    NewObjectIdentifier nextChunk = new
                        NewObjectIdentifier(oid.getUID(), nextMap,
                                            oid.getObjectType(), ++j,
                                            oid.getRuleId(),
                                            oid.getSilolocation());
                    chunkOid = nextChunk; // for next iteration
                    // analyze frag in current chunk
                    if (fi.fragIsMissing) {
                        // XXX should we keep a failure count? [dmehra]
                        log("Fragment file " + fi.fragFileName +
                            " doesn't exist...skipping");
                        continue;
                    }

                    if (!fi.fieldsAreValid) {
                        log("fields aren't valid for " + fi, true);
                        continue;
                    }

                    if (action.equals(DELETED_CHECK)) {
                        // check length of file to verify files that should be
                        // deleted are the right length
                        if (shouldBeDeleted &&
                            fi.fragFileSize != Constants.OA_DELETED_SIZE) {
                            String msg = "fragment " + fi.fragFileName +
                                " was supposed to be deleted but had size " +
                                fi.fragFileSize + " (deleted size is " +
                                Constants.OA_DELETED_SIZE + ")";
                            throw new ArchiveException(msg);
                        }

                        if ((shouldBeDeleted && !fi.isDeleted) ||
                            (!shouldBeDeleted && fi.isDeleted)) {
                            String msg = fi.fragFileName +
                                " had expected deleted status of " +
                                shouldBeDeleted +
                                " but actual deleted status of " +
                                fi.isDeleted;
                            throw new ArchiveException(msg);
                        }
                    } else if (action.equals(REFCNT_CHECK)) {
                        if (refcnt != fi.refcnt) {
                            String msg = fi.fragFileName +
                                " had expected refcnt of " + refcnt +
                                " but actual refcnt of " + fi.refcnt;
                            throw new ArchiveException(msg);
                        }

                        if (maxRefcnt != fi.maxRefcnt) {
                            String msg = fi.fragFileName +
                                " had expected maxRefcnt of " + maxRefcnt +
                                " but actual maxRefcnt of " + fi.maxRefcnt;
                            throw new ArchiveException(msg);
                        }
                    } else {
                        throw new ArchiveException("Unknown fragment action " +
                                                   action);
                    }
                }
            }
        }

        /* Print out fragment file pathnames on disks for this OID
         */
        protected void executeLs(NewObjectIdentifier oid) {
            log("Fragments on disk for OID " + oid, true);
            String cmd = "find disks/ -name " + oid.getUID().toString() + ".* -ls";
            String s = null;
            try {
                Process p = Runtime.getRuntime().exec(cmd);
                BufferedReader stdin =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader stderr =
                    new BufferedReader(new InputStreamReader(p.getErrorStream()));
                while ((s = stdin.readLine()) != null) {
                    System.out.println(s);
                }
                while ((s = stderr.readLine()) != null) {
                    System.out.println(s);
                }
            } catch (IOException e) {
                log("Failed ls command [" + cmd + "]: " + e.getMessage(), true);
            }
        }

        protected void executeEcho(String s) {
            log(s, true);
        }

        protected void executeDisableDisk(int disk) throws ArchiveException {
            LayoutClient.disableDisk(disk);
        }

        protected void executeEnableDisk(int disk) throws ArchiveException {
            LayoutClient.enableDisk(disk);
        }
    
        private static long lastBackup = 0;
        
        protected void executeBackup(String filename, int options) 
                  throws ArchiveException {
            
            long t1 = 0;
            long t2 = System.currentTimeMillis();
            // ignore options just for now
            try {
                BackupRestoreMain.doOperation("backup", 
                                              filename, 
                                              t1, 
                                              t2,
                                              Session.OBJECT_BACKUP_OPTION);
            } catch (Throwable e) {
                throw new ArchiveException(e);
            }
            lastBackup = t2;
        }
        
        protected void executeBackupInterval(
        		String filename, long t1, long t2, int options) 
        		throws ArchiveException {
  
        	// ignore options just for now
        	try {
        		BackupRestoreMain.doOperation("backup", 
        									  filename, 
        									  t1, 
        									  t2,
        									  Session.OBJECT_BACKUP_OPTION);
        	} catch (Throwable e) {
        		throw new ArchiveException(e);
        	}
        	lastBackup = t2;
        }
        
       
        protected void executeIncBackup(String filename, int options) 
                  throws ArchiveException {
            
            long t1 = lastBackup;
            long t2 = System.currentTimeMillis();
            // ignore options just for now
            try {
                BackupRestoreMain.doOperation("backup", 
                                              filename, 
                                              t1, 
                                              t2,
                                              Session.OBJECT_BACKUP_OPTION);
            } catch (Throwable e) {
                throw new ArchiveException(e);
            }
            lastBackup = t2;
        }
       
        protected void executeRestore(String filename, int options)
                throws ArchiveException {
            // ignore options just for now
            try {
                BackupRestoreMain.doOperation("restore", filename, 0, 0, (byte) options);
            } catch (Throwable e) {
                throw new ArchiveException(e);
            }
        }
      
        public static boolean deleteDir(File dir) {
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (int i=0; i<children.length; i++) {
                    boolean success = deleteDir(new File(dir, children[i]));
                    if (!success) {
                        return false;
                    }
                }
            }
        
            // The directory is now empty so delete it
            return dir.delete();
        }

        protected void executeMoveAll() throws ArchiveException {
            String diskRoot = Constants.getDiskRoot();
            File dir = new File(diskRoot);
            File newDir = new File(diskRoot + "-moved");
            deleteDir(newDir);
            
            if (!newDir.mkdir())
                throw new ArchiveException("Failed to create data disks over to " + newDir);
            
            if (!dir.renameTo(newDir))
                throw new ArchiveException("Failed to move data disks over to " + newDir);
            
            try {
                Test.setupDisks();
            } catch (IOException e) {
                throw new ArchiveException(e);
            }
        }
        
        protected void executeCompare () throws ArchiveException {
            String diskRoot = Constants.getDiskRoot();
            File liveRoot = new File(diskRoot);
            File savedRoot = new File (diskRoot + "-moved");

            if (!savedRoot.exists()) {
                throw new ArchiveException (
                    "Directory does not exist: " + savedRoot);
            }

            byteScanFrags (liveRoot, liveRoot);
            System.out.println (" ok!");
        }

        protected void byteScanFrags (File liveRoot, File curDir) 
            throws ArchiveException {

            // now iterate each file in liveRoot and compare it to each file of
            // the same name in savedRoot

            File[] files = curDir.listFiles(new FileFilter() {
                public boolean accept (File f) {
                    if (f.getName().endsWith(".out")) return false;
                    if (f.getName().endsWith(".fef")) return false;
                    return true;
                }
            });

            int failedCount = 0;

            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    // we're a directory, so recurse until we find a fragment
                    byteScanFrags (liveRoot, files[i]);
                } else {
                    // woo, a fragment. now compare the bytes of the old and
                    // new fragments
                    String pathname = files[i].toString();
                    String movedPath 
                        = pathname.replaceAll (liveRoot.getName(),
                                               liveRoot.getName() + "-moved");
       
                    File newfile = new File (movedPath);
                    try {
                        checkFooterEquality (files[i], newfile);
                        compareFragments (files[i], new File (movedPath));
                    } catch (ArchiveException ae) {
                        log ("Footers differ: " + ae);
                        failedCount += 1;
                    }
                }
            }

            if (failedCount > 0) {
                throw new ArchiveException (
                    "File differences found after restore. Check the logs for the differences");
            }
        }

        protected void compareFragments (File f1, File f2) 
            throws ArchiveException {

            System.out.print (".");

            if (f1.length() != f1.length()) {
                throw new ArchiveException (f1.toString() + " and " 
                    + f2.toString() + " are not equal: lengths differ!");
            }

            FileInputStream in1 = null;
            FileInputStream in2 = null;

            byte[] buffer1 = new byte[4096];
            byte[] buffer2 = new byte[buffer1.length];
            boolean match = false;

            try {
                in1 = new FileInputStream(f1);
                in2 = new FileInputStream(f2);

                int numRead1;
                int numRead2;
                int totalRead = 0;

                do {
                    numRead1 = in1.read(buffer1);
                    //System.out.println ("numRead1=" + numRead1);
                    numRead2 = in2.read(buffer2);
                    //System.out.println ("numRead2=" + numRead1);
                    match = ((numRead1 == numRead2) 
                                && Arrays.equals (buffer1, buffer2));
                    //System.out.println ("match=" +match);
                    totalRead += numRead1;
                } while (match && (numRead1 > -1));

                if (!match) {
                    // scan the last buffer byte-by-byte to get the actual 
                    // offset so we can // print it.
                    
                    int diffOffset = 0;
                    for (int i = 0; i < numRead1; i++) {
                        if (buffer1[i] != buffer2[i]) {
                            diffOffset = totalRead - numRead1 + i;
                            break;
                        }
                    }

                    throw new ArchiveException ("Error: " +
                        f1.toString() + " and " 
                        + f2.toString() 
                        + " are not equal at offset " + diffOffset);
                }

            } catch (Exception e) {
                throw new ArchiveException ("Error: " + e.toString());
            } finally {
                try {
                    in1.close();
                    in2.close();
                } catch (Exception e) {
                    ;
                }
            }
        }

        /**
         * Given 2 paths to fragments that should be exactly the same,
         * parse the footers and check each field.  <p>This code does not use
         * the existing footer parsing, as there is no easy way to 
         * grab a FragmentFooter or to request an object not in the current
         * path (we copy off the contents of the unit test disk dir to another
         * location for the purposes of this comparison). This test will fail
         * and need updating if the format of the footers change, which isn't
         * really so bad anyway. The format is:
         *
         * bytes : field:
         * -------------------------------
         *   1   : footer header?!
         *   1   : version
         *   4   : fragNum
         *  30   : oid
         *  30   : link_oid
         *   8   : size
         *   8   : reliability
         *  20   : object_hash
         *   8   : ctime
         *   8   : rtime
         *   8   : etime
         *   8   : close_time
         *   8   : dtime
         *   1   :  shred
         *  64   : metadata_field
         *   2   : checksum_alg
         *   2   : num_prceeding_chksums
         *   4   : fragment_size
         *   4   : chunk_size
         *   4   : ref_count
         *   4   : max_ref_count
         * 132   : deleted_refs
         *   4   : footer_checksum
         *
         * The size of the footer is 376 bytes.
         */
        private static final int FOOTER_LEN = 376;

        public void checkFooterEquality (File frag1, File frag2) 
            throws ArchiveException {

            if (! frag1.exists())
                throw new ArchiveException (frag1 + " does not exist");
            if (! frag2.exists())
                throw new ArchiveException (frag2 + " does not exist");

            long header_offset = frag1.length() - FOOTER_LEN;

            //System.out.println ("FOOTER_LEN    = " + FOOTER_LEN);
            //System.out.println ("header_offset = " + header_offset);
            //System.out.println ("frag1         = " + frag1);
            //System.out.println ("frag1.length  = " + frag1.length());
            //System.out.println ("frag2         = " + frag2);
            //System.out.println ("frag2.length  = " + frag2.length());

            ByteBuffer footer1 = ByteBuffer.allocate(FOOTER_LEN);
            ByteBuffer footer2 = ByteBuffer.allocate(FOOTER_LEN);

            boolean footersDiffer = false;

            try {
                int bytesRead = -1;

                FileChannel in1 = new FileInputStream (frag1).getChannel();
                FileChannel in2 = new FileInputStream (frag2).getChannel();

                bytesRead = in1.read (footer1, header_offset);
                if (bytesRead != FOOTER_LEN)
                    throw new ArchiveException (" read " + bytesRead
                        + " bytes from " + frag1 + " footer. expected " 
                        + FOOTER_LEN);

                bytesRead = in2.read (footer2, header_offset);
                if (bytesRead != FOOTER_LEN)
                    throw new ArchiveException (" read " + bytesRead
                        + " bytes from " + frag1 + " footer. expected " 
                        + FOOTER_LEN);
            } catch (IOException ioe) {
                throw new ArchiveException (ioe);
            }

            //System.out.println ("HEXDUMP1: " 
            //    + ByteArrays.toHexString (footer1.array()));
            //System.out.println ("HEXDUMP2: " 
            //    + ByteArrays.toHexString (footer2.array()));

            // skip the 1st byte. Don't know what this byte is, but I assume 
            // it's a magic serialziation header of some sort
            footer1.position(1); 
            footer2.position(1);

            ByteBufferCoder decoder1 = new ByteBufferCoder (footer1);
            ByteBufferCoder decoder2 = new ByteBufferCoder (footer2);

            byte version1 = decoder1.decodeByte();
            byte version2 = decoder2.decodeByte();
            //System.out.println ("version1 = " + version1);
            //System.out.println ("version2 = " + version2);
            if (version1 != version2) {
                footersDiffer = true;
                log ("DIFF: version1 " + version1 + " / version2 " + version2);
            }

            int fragNum1 = decoder1.decodeInt();
            int fragNum2 = decoder2.decodeInt();
            //System.out.println ("fragnum1 = " + fragNum1);
            //System.out.println ("fragnum2 = " + fragNum2);
            if (fragNum1 != fragNum2) {
                footersDiffer = true;
                log ("DIFF: fragNum1 " + fragNum1 + " / fragNum2 " + fragNum2);
            }

            NewObjectIdentifier noi1 = decodeNewObjectIdentifier (decoder1);
            NewObjectIdentifier noi2 = decodeNewObjectIdentifier (decoder2);
            //System.out.println ("oid1 = " + noi1);
            //System.out.println ("oid2 = " + noi2);
            if (!noi1.equals(noi2)) {
                footersDiffer = true;
                log ("DIFF: noi1 = " + noi1 + " / noi2 " + noi2);
            }

            NewObjectIdentifier loi1 = decodeNewObjectIdentifier (decoder1);
            NewObjectIdentifier loi2 = decodeNewObjectIdentifier (decoder2);
            //System.out.println ("loid1 = " + loi1);
            //System.out.println ("loid2 = " + loi2);
            if (!loi1.equals(loi2)) {
                footersDiffer = true;
                log ("DIFF: loi1 = " + loi1 + " / loi2 " + loi2);
            }

            long size1 = decoder1.decodeLong();
            long size2 = decoder2.decodeLong();
            //System.out.println ("size1 = " + size1);
            //System.out.println ("size2 = " + size2);
            if (size1 != size2) {
                footersDiffer = true;
                log ("DIFF: size1 " + size1 + " / size2 " + size2);
            }

            ObjectReliability reli1 = new ObjectReliability();
            ObjectReliability reli2 = new ObjectReliability();
            decoder1.decodeKnownClassCodable(reli1);
            decoder2.decodeKnownClassCodable(reli2);
            //System.out.println ("reli1 = " + reli1);
            //System.out.println ("reli2 = " + reli2);
            if (! reli1.equals(reli2)) {
                footersDiffer = true;
                throw new ArchiveException ("reli diff: " +
                    reli1 + " != " + reli2);
            }

            byte[] hash1 = new byte[20];
            byte[] hash2 = new byte[20];
            hash1 = decoder1.decodeBytes ();
            hash2 = decoder2.decodeBytes ();
            //System.out.println ("hash1 = " + ByteArrays.toHexString(hash1));
            //System.out.println ("hash2 = " + ByteArrays.toHexString(hash2));
            if (! Arrays.equals (hash1, hash2)) {
                footersDiffer = true;
                log ("DIFF: reli1 " + reli1 + " / reli2 " + reli2);
            }

            long ctime1 = decoder1.decodeLong();
            long ctime2 = decoder2.decodeLong();
            //System.out.println ("ctime1 = " + ctime1);
            //System.out.println ("ctime2 = " + ctime2);
            if (ctime1 != ctime2) {
                footersDiffer = true;
                log ("DIFF: ctime1 " + ctime1 + " / ctime2 " + ctime2);
            }

            long rtime1 = decoder1.decodeLong();
            long rtime2 = decoder2.decodeLong();
            //System.out.println ("rtime1 = " + rtime1);
            //System.out.println ("rtime2 = " + rtime2);
            if (rtime1 != rtime2) {
                footersDiffer = true;
                log ("DIFF: rtime1 " + rtime1 + " / rtime2 " + rtime2);
            }

            long etime1 = decoder1.decodeLong();
            long etime2 = decoder2.decodeLong();
            //System.out.println ("etime1 = " + etime1);
            //System.out.println ("etime2 = " + etime2);
            if (etime1 != etime2) {
                footersDiffer = true;
                log ("DIFF: etime1 " + etime1 + " / etime2 " + etime2);
            }

            long atime1 = decoder1.decodeLong();
            long atime2 = decoder2.decodeLong();
            //System.out.println ("atime1 = " + atime1);
            //System.out.println ("atime2 = " + atime2);
            if (atime1 != atime2) {
                footersDiffer = true;
                log ("DIFF: atime1 " + atime1 + " / atime2 " + atime2);
            }

            long dtime1 = decoder1.decodeLong();
            long dtime2 = decoder2.decodeLong();
            //System.out.println ("dtime1 = " + dtime1);
            //System.out.println ("dtime2 = " + dtime2);
            if (dtime1 != dtime2) {
                footersDiffer = true;
                log ("DIFF: dtime1 " + dtime1 + " / dtime2 " + dtime2);
            }

            byte shred1 = decoder1.decodeByte();
            byte shred2 = decoder2.decodeByte();
            //System.out.println ("shred1 = " + shred1);
            //System.out.println ("shred2 = " + shred2);
            if (shred1 != shred2) {
                footersDiffer = true;
                log ("DIFF: shred1 " + shred1 + " / shred2 " + shred2);
            }

            byte[] metadata1 = new byte[FragmentFooter.METADATA_FIELD_LENGTH];
            byte[] metadata2 = new byte[FragmentFooter.METADATA_FIELD_LENGTH];
            metadata1 = decoder1.decodeBytes();
            metadata2 = decoder2.decodeBytes();
            //System.out.println ("metadata1 = " + ByteArrays.toHexString(metadata1));
            //System.out.println ("metadata2 = " + ByteArrays.toHexString(metadata2));
            if (! Arrays.equals (metadata1, metadata2)) {
                footersDiffer = true;
                log ("DIFF: metadata1 " + ByteArrays.toHexString(metadata1) 
                    + " / metadata2 " + ByteArrays.toHexString (metadata2));
            }

            short chksumAlg1 = decoder1.decodeShort();
            short chksumAlg2 = decoder2.decodeShort();
            if (chksumAlg1 != chksumAlg2) {
                footersDiffer = true;
                log ("DIFF: chksum1 " + chksumAlg1 +" / chksum2 " + chksumAlg2);
            }

            short numPreChksum1 = decoder1.decodeShort();
            short numPreChksum2 = decoder2.decodeShort();
            if (numPreChksum1 != numPreChksum2) {
                footersDiffer = true;
                log ("DIFF: numPreChksum1 " + numPreChksum1 
                        + " / numPreChksum2 " + numPreChksum2);
            }

            int fragSize1 = decoder1.decodeInt();
            int fragSize2 = decoder2.decodeInt();
            //System.out.println ("fragSize1 = " + fragSize1);
            //System.out.println ("fragSize2 = " + fragSize2);
            if (fragSize1 != fragSize2) {
                footersDiffer = true;
                log ("DIFF: fragSize1 " + fragSize1 
                        + " / fragSize2 " + fragSize2);
            }

            int chunkSize1 = decoder1.decodeInt();
            int chunkSize2 = decoder2.decodeInt();
            if (chunkSize1 != chunkSize2) {
                footersDiffer = true;
                log ("DIFF: chunkSize1 " + chunkSize1 
                    + " / chunkSize2 " + chunkSize2);
            }

            int refCount1 = decoder1.decodeInt();
            int refCount2 = decoder2.decodeInt();
            //System.out.println ("refcount1 = " + refCount1);
            //System.out.println ("refcount2 = " + refCount2);
            if (refCount1 != refCount2) {
                footersDiffer = true;
                log ("DIFF: refCount1 " + refCount1 
                        + " / refCount2 " + refCount2);
            }

            int maxRefCount1 = decoder1.decodeInt();
            int maxRefCount2 = decoder2.decodeInt();
            //System.out.println ("maxRefcount1 = " + maxRefCount1);
            //System.out.println ("maxRefcount2 = " + maxRefCount2);
            if (maxRefCount1 != maxRefCount2) {
                footersDiffer = true;
                log ("DIFF: maxRefCount1 " + maxRefCount1 
                        + " / maxRefCount2 " + maxRefCount2);
            }

            BitSet delRefs1 

                = new BitSet(FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH);
            BitSet delRefs2 
                = new BitSet(FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH);
            delRefs2 = decoder2.decodePackedBitSet();
            //System.out.println ("delRefs1 = " + delRefs2);
            //System.out.println ("delRefs1 = " + delRefs2);
            delRefs1 = decoder1.decodePackedBitSet();
            if (! delRefs1.equals(delRefs2)) {
                footersDiffer = true;
                log ("DIFF: delRefs1 " + delRefs1 + " / delRefs2 " + delRefs2);
            }

            int footChksum1 = decoder1.decodeInt();
            int footChksum2 = decoder2.decodeInt();
            //System.out.println ("footChksum1 = " + footChksum1);
            //System.out.println ("footChksum2 = " + footChksum2);
            if (footChksum1 != footChksum2) {
                footersDiffer = true;
                log ("DIFF: footChksum1 " + footChksum1 
                        + " / footChksum2 " + footChksum2);
            }

            if (footersDiffer) {
                throw new ArchiveException ("Footers for " + frag1 
                    + " and " + frag2 + " differ");
            }
        }

        protected NewObjectIdentifier 
                            decodeNewObjectIdentifier(Decoder decoder) {
            NewObjectIdentifier noi = new NewObjectIdentifier();
            decoder.decodeKnownClassCodable(noi);
            return noi;
        }

        protected Integer executeSysCacheSize() throws ArchiveException {
           return new Integer(InMemMetadata.getInstance().getSize()); 
        }
       
        protected void executeRestoreAll() throws ArchiveException {
            String diskRoot = Constants.getDiskRoot();
            File dir = new File(diskRoot);
            log("Attention current UT disks directory is going to be cleaned");
            dir.delete();
            File newDir = new File(diskRoot + "-moved");
            newDir.mkdir();
            newDir.renameTo(dir);
        }
        
        protected void executeDumpCaches() throws ArchiveException {
            MetadataClient mc = MetadataClient.getInstance();
            QueryResult result = mc.query(CacheClientInterface.SYSTEM_CACHE,
                    "getChanges 0 " + Long.MAX_VALUE, 1024);
           
            log("*** SystemCache Dump ***\n",true);
            for (int i = 0; i < result.results.size(); i++) {
                MDHit hit = (MDHit) result.results.get(i);
                log(hit.getExtraInfo() + "\n", true);
            }
        }
        
        protected SystemMetadata executeGetCacheData(NewObjectIdentifier oid)
    		throws ArchiveException {
        	MetadataClient mc = MetadataClient.getInstance();
        	SystemMetadata result = mc.retrieveMetadata(
        			CacheClientInterface.SYSTEM_CACHE, oid);
        	return result;
        }
        
        protected void executeWipeCaches() throws ArchiveException {
            InMemMetadata.getInstance().clear();
        }
        
        private void writeBufferList(ByteBufferList bufferList,
                                     byte[] bytes,
                                     OutputStream out) throws IOException {
            ByteBuffer[] buffers = bufferList.getBuffers();

            for (int i = 0; i < buffers.length; i++) {
                int toWrite = buffers[i].remaining();

                buffers[i].get(bytes, 0, toWrite);
                out.write(bytes, 0, toWrite);
            }
        }

        protected void executeSetRetention (NewObjectIdentifier oid,
                                           long timestamp)
                                                throws ArchiveException {
            MetadataCoordinator.getInstance().setRetentionTime(oid, timestamp);
        }

        protected Long executeGetRetention (NewObjectIdentifier oid)
                                                throws ArchiveException {
            return new Long(OAClient.getInstance().getRetentionTime (oid));
        }


        protected void executeAddHold (NewObjectIdentifier oid,
                                       String hold_tag)
                                                throws ArchiveException {
            OAClient.getInstance().addLegalHold (oid, hold_tag);
        }

        protected void executeRmHold (NewObjectIdentifier oid,
                                      String hold_tag)
                                                throws ArchiveException {
            OAClient.getInstance().removeLegalHold (oid, hold_tag);
        }

        protected String[] executeGetHolds (NewObjectIdentifier oid)
                                                throws ArchiveException {
            return OAClient.getInstance().getLegalHolds (oid);
        }

        protected NewObjectIdentifier[] executeGetHeld (String hold_tag)
                                                throws ArchiveException {
            // TODO: Implement
            LOG.warning ("executeGetHeld(hold_tag) not implemented");
            return null;
        }

        protected void executeFefRemove(NewObjectIdentifier oid, int fefID)
            throws ArchiveException {
            int layoutMapId = oid.getLayoutMapId();
            Layout layout
                = LayoutClient.getInstance().getLayoutForRetrieve(layoutMapId);
            Disk d = layout.getDisk(fefID);
            String realName = Common.makeFilename(oid, d, fefID) + ".fef";
            File realFile = new File(realName);
            File hiddenFile = new File(realFile.getParent() + File.separator
                                       + "." + realFile.getName() + "-hidden");

            // Debug
            LOG.warning("Moving " + realFile + " to " + hiddenFile);

            if (!realFile.exists()) {
                throw new ArchiveException("Can't find original file " +
                                           realFile + " to remove.");
            }
            
            realFile.renameTo(hiddenFile);
        }            

        protected void executeFefRestore(NewObjectIdentifier oid, int fefID)
            throws ArchiveException {
            int layoutMapId = oid.getLayoutMapId();
            Layout layout
                = LayoutClient.getInstance().getLayoutForRetrieve(layoutMapId);
            Disk d = layout.getDisk(fefID);
            String realName = Common.makeFilename(oid, d, fefID) + ".fef";
            File realFile = new File(realName);
            File hiddenFile = new File(realFile.getParent() + File.separator
                                       + "." + realFile.getName() + "-hidden");

            // Debug
            LOG.info("Warning " + hiddenFile + " to " + realFile);

            if (!hiddenFile.exists()) {
                throw new ArchiveException("Can't find hidden file " +
                                           hiddenFile + " to restore.");
            }
            hiddenFile.renameTo(realFile);
        }

        protected void executeFefRecover(NewObjectIdentifier oid)
            throws ArchiveException {
            OAClient.getInstance().getLegalHolds(oid);
        }

    } // class DeleteFmkOpCode
} // class DeleteFmkFactory
