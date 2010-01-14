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

import com.sun.honeycomb.coordinator.ContextConsumer;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.coordinator.Disposable;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.oa.hash.ContentHashContext;
import com.sun.honeycomb.cm.NodeMgr;
import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.emd.Derby;
import com.sun.honeycomb.emd.DerbyAttributes;
import com.sun.honeycomb.emd.common.EMDException;

import java.util.BitSet;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import com.sun.honeycomb.multicell.SpaceRemaining;

public class OAClient implements ContextConsumer {

    private static final int PAGE_SIZE = 0x10000; // 64k
    public static final byte OBJECT_TYPE_DATA = (byte)1;
    public static final byte OBJECT_TYPE_METADATA = (byte)2;

    private static final String OID_FIELD = "oaOid";
    private static final String STREAM_FIELD = "oaStream";
    private static final String DBENTRY_FIELD = "oaDBEntry";
    private static final String CONTENT_HASH_FIELD = "contentHash";

    private static final String OADB_NAME = "oadb";
    private static final String OATABLE_NAME = "main";
    private static final String HOLDTABLE_NAME = "hold";
    private static final String RETENTIONTABLE_NAME = "retention";

    public static final int NOT_RECOVERY = -1;

    // COMPLIANCE MEMBERS
    private static final int RETENTION_UNSET = 0;
    private static final int RETENTION_UNSPECIFIED = -1;

    protected static final Logger LOG = 
        Logger.getLogger(OAClient.class.getName());

    private static OAClient oaclient = null;

    public static OAClient getInstance() {
        synchronized(LOG) {
            if (oaclient == null) {
                // This is the first time getInstance has been called
                oaclient = new OAClient();
            }
        }
        return oaclient;
    }

    private String oaPath = null;

    private OAClient() {
        oaPath = NodeMgr.getEmulatorRoot()+
            File.separator+"var"+File.separator+"data";
        File oaDir = new File(oaPath);
        if (!oaDir.exists()) {
            oaDir.mkdir();
        }

        DerbyAttributes attrs = new DerbyAttributes();
        attrs.add("oid", "char(61)");
        attrs.add("blob", "blob(2k)");

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
        // DerbyAttributes holdAttrs = new DerbyAttributes();
        // holdAttrs.add("oid", "char(61)");
        // holdAttrs.add("hold", "varchar(4096)");

        try {
            Derby.getInstance().checkTable(OADB_NAME, OATABLE_NAME,
                                           attrs);

            // Derby.getInstance().checkTable(OADB_NAME, HOLDTABLE_NAME,
            //                                holdAttrs);

        } catch (EMDException e) {
            RuntimeException newe = new RuntimeException("Failed to initialize OA ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    private void putToDB(NewObjectIdentifier oid,
                         DBEntry entry, boolean update)
        throws SQLException, IOException {
        Connection connection = null;
        PreparedStatement statement = null;

        try {

            connection = Derby.getInstance().getConnection(OADB_NAME);
            byte[] bytes = entry.getBytes();
            StringBuffer sb;

            if (update) {
                sb = new StringBuffer("update ");
                sb.append(OATABLE_NAME);
                sb.append(" set blob=? where oid='");
                sb.append(oid.toHexString());
                sb.append("'");
            } else {
                sb = new StringBuffer("insert into ");
                sb.append(OATABLE_NAME);
                sb.append(" (oid, blob) values ('");
                sb.append(oid.toHexString());
                sb.append("', ?)");
            }

            statement = connection.prepareStatement(sb.toString());
            statement.setBinaryStream(1, new ByteArrayInputStream(bytes), bytes.length);
            statement.executeUpdate();

        } finally {
            if (statement != null)
                statement.close();
            if (connection != null)
                connection.close();
        }
    }

    private DBEntry getFromDB(String oid)
        throws SQLException, IOException, NoSuchObjectException {

        Connection connection = null;
        Statement statement = null;
        ResultSet results = null;

        try {
            connection = Derby.getInstance().getConnection(OADB_NAME);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("select blob from ");
            sb.append(OATABLE_NAME);
            sb.append(" where oid='");
            sb.append(oid);
            sb.append("'");
            results = statement.executeQuery(sb.toString());

            if (!results.next()) {
                throw new NoSuchObjectException("Oid ["+
                                                oid+"] not found");
            }

            return(DBEntry.convertBytes(results.getBlob(1)));
        } finally {
            if (results != null)
                results.close();
            if (statement != null)
                statement.close();
            if (connection != null)
                connection.close();
        }
    }

    private void deleteFromDB(String oid)
        throws SQLException, IOException {

        Connection connection = null;
        Statement statement = null;

        try {
            connection = Derby.getInstance().getConnection(OADB_NAME);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("delete from ");
            sb.append(OATABLE_NAME);
            sb.append(" where oid='");
            sb.append(oid);
            sb.append("'");
            statement.executeUpdate(sb.toString());
        } finally {
            if (statement != null)
                statement.close();
            if (connection != null)
                connection.close();
        }
    }

    /**
     * This static method can be used to see whether the OAClient
     * singleton has been instantiated yet or not (in other words,
     * whether getInstance has ever been called before or not).
     */
    public static boolean isInstantiated() {
        return !(oaclient == null);
    }


    public NewObjectIdentifier create(long size, NewObjectIdentifier link,
                                      int layoutMapId, boolean isMetadata,
                                      boolean isRefRoot, int autoCloseMillis,
                                      long retentionTime, long expirationTime,
                                      byte shred, Context ctx)
        throws ArchiveException {
        NewObjectIdentifier result;
        byte objectType = isMetadata ? OBJECT_TYPE_METADATA : OBJECT_TYPE_DATA;
        if (objectType == OBJECT_TYPE_DATA) {
            result = new NewObjectIdentifier(0, objectType, 0);
        } else {
            result = new NewObjectIdentifier(0, objectType, 0, link);
        }
        create(size, link, result, layoutMapId, isMetadata,
               isRefRoot, autoCloseMillis,
               System.currentTimeMillis(), -1, 
               retentionTime, expirationTime,
               shred, 
               (short)0, 0, ctx, null);
        return result;
    }


     
    public void create(long size,
                       NewObjectIdentifier link,
                       NewObjectIdentifier oid,
                       int layoutMapId,
                       boolean isMetadata,
                       boolean isRefRoot,
                       long autoCloseMillis,
                       long createTime,
                       long deleteTime,
                       long retentionTime,
                       long expirationTime,
                       byte shred,
                       short checksumAlgorithm,
                       int recoverFrag,
                       Context ctx,
                       ContentHashContext hashContext)
        throws ArchiveException {

        double highWatermark = ClusterProperties.getInstance().
            getPropertyAsInt("honeycomb.emulator.usage.cap",80)*1.0;

	long total = SpaceRemaining.getInstance().getTotalCapacityBytes();
	long used = SpaceRemaining.getInstance().getUsedCapacityBytes();
       
	double adjustedTotal = total * highWatermark/100;
	double adjustment = adjustedTotal - used;
	if (adjustment < 0) {
            throw new ArchiveException("Emulated storage area full");
        }

        String fileName = oaPath+File.separator+oid.toHexString();
        DBEntry entry = new DBEntry(fileName,
                                    link.toHexString(), null, null);
        FileChannel stream = null;
        try {
            stream = new RandomAccessFile(fileName, "rw").getChannel();
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to create the OA file ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        ctx.registerTransientObject(OID_FIELD, oid);
        ctx.registerTransientObject(STREAM_FIELD, new OAStream(stream));
        ctx.registerTransientObject(DBENTRY_FIELD, entry);

        try {
            ctx.registerTransientObject(CONTENT_HASH_FIELD, MessageDigest.getInstance("SHA-1"));
        } catch (NoSuchAlgorithmException e) {
            ArchiveException newe = new ArchiveException("The JVM does not contain the needed packages to compute SHA-1 checksums");
            newe.initCause(e);
            throw newe;
        }
    }

    public void commit(Context ctx) throws ArchiveException {
        // This is a no op in the emulator
    }

    private void updateDigest(MessageDigest digest,
                              ByteBufferList bufferList) {
        ByteBuffer[] buffers = bufferList.getBuffers();
        for (int i=0; i<buffers.length; i++) {
            byte[] buf = new byte[buffers[i].remaining()];
            buffers[i].get(buf);
            digest.update(buf);

        }
    }

    public void write(ByteBufferList newList, long offset, Context ctx)
        throws ArchiveException {

        FileChannel stream = ((OAStream)ctx.getTransientObject(STREAM_FIELD)).channel;
        MessageDigest digest = (MessageDigest)ctx.getTransientObject(CONTENT_HASH_FIELD);

        try {
            updateDigest(digest, newList);
            newList.rewind();
            newList.writeToChannel(stream);
        } catch (IOException e) {
            NewObjectIdentifier oid = (NewObjectIdentifier)ctx.getTransientObject(OID_FIELD);
            ArchiveException newe = new ArchiveException("Failed to write data in ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    public void write(ByteBuffer buf, long offset, Context ctx)
        throws ArchiveException {

        FileChannel stream = ((OAStream)ctx.getTransientObject(STREAM_FIELD)).channel;
        MessageDigest digest = (MessageDigest)ctx.getTransientObject(CONTENT_HASH_FIELD);

        try {
            digest.update(buf);
            buf.rewind();
            while (buf.remaining() != 0){
                long n = stream.write(buf, offset);
                offset += n;
            }
        } catch (IOException e) {
            NewObjectIdentifier oid = (NewObjectIdentifier)ctx.getTransientObject(OID_FIELD);
            ArchiveException newe = new ArchiveException("Failed to write data in ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]", e);
            throw newe;
        }
    }

    public void incRefCount(NewObjectIdentifier oid) throws ArchiveException {
        // No op for the emulator, though we should implement a file-based 
        // mechanism for a subset of Systemrecord
    }

    // RETRIEVE METHODS //

    public SystemMetadata open(NewObjectIdentifier oid, Context ctx)
        throws ArchiveException {

        DBEntry entry = null;
        FileChannel channel = null;

        try {
            entry = getFromDB(oid.toHexString());
            channel = new RandomAccessFile(entry.path, "r").getChannel();
        } catch (SQLException e) {
            ArchiveException newe = new ArchiveException("Unknown oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to open oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        ctx.registerTransientObject(OID_FIELD, oid);
        ctx.registerTransientObject(DBENTRY_FIELD, entry);
        ctx.registerTransientObject(STREAM_FIELD, new OAStream(channel));

        return(entry.smd);
    }

    public SystemMetadata[] close(Context ctx, byte[] metadataField)
        throws ArchiveException {

        SystemMetadata[] result = new SystemMetadata[1];
        NewObjectIdentifier oid = (NewObjectIdentifier)ctx.getTransientObject(OID_FIELD);
        DBEntry entry = (DBEntry)ctx.getTransientObject(DBENTRY_FIELD);
        FileChannel stream = ((OAStream)ctx.getTransientObject(STREAM_FIELD)).channel;
        MessageDigest digest = (MessageDigest)ctx.getTransientObject(CONTENT_HASH_FIELD);
        long time = System.currentTimeMillis();

        try {
            result[0] = new SystemMetadata(oid,
                                           stream.size(),
                                           time, (long)0, (long)0, time, time,
                                           (byte)0, (short)0, (short) 0, new byte[0], (byte)0, 1, new ObjectReliability(1,0),
                                           NewObjectIdentifier.fromHexString(entry.link),
                                           new byte[0],
                                           0,
                                           0,
                                           new BitSet(FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH));
            result[0].setHashAlgorithm("sha1");
            result[0].setContentHash(digest.digest());
            entry.smd = result[0];
            entry.mdField = metadataField;

            putToDB(oid, entry, false);

            SpaceRemaining.getInstance().addBytes(result[0].getSize());
        } catch (SQLException e) {
            ArchiveException newe = new ArchiveException("Failed to close oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to close oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        return(result);
    }

    public long read(ByteBuffer buf, long offset,  long length,
                     Context ctx)
        throws ArchiveException {

        if(length > buf.remaining()) {
            throw new ArchiveException("The given buffer is too big");
        }

        NewObjectIdentifier refid = (NewObjectIdentifier)ctx.getTransientObject(OID_FIELD);

        FileChannel stream = ((OAStream)ctx.getTransientObject(STREAM_FIELD)).channel;
        long result = -1;

        try {
            stream.position(offset);
            result = stream.read(buf);
        } catch (IOException e) {
            NewObjectIdentifier oid = (NewObjectIdentifier)ctx.getTransientObject(OID_FIELD);
            ArchiveException newe = new ArchiveException("Failed to read oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        return(result);
    }

    public void delete(NewObjectIdentifier oid, boolean hard, int wipe)
        throws ArchiveException {

        // To throw if the object does not exist
        String path = null;

        try {
            DBEntry entry = getFromDB(oid.toHexString());
            path = entry.path;
            deleteFromDB(oid.toHexString());
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to delete ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (SQLException e) {
            ArchiveException newe = new ArchiveException("Failed to delete ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        // Actually delete the file
        if (path.startsWith(oaPath)) {
            new File(path).delete();
        }
    }

    public void restoreContextForStore(NewObjectIdentifier oid,
                                       boolean explicitClose,
                                       Context ctx)
        throws ArchiveException {
        throw new RuntimeException("Emulator failover not implemented");
    }

    public void restoreContextForRetrieve(NewObjectIdentifier oid,
                                          Context ctx)
        throws ArchiveException {
        throw new RuntimeException("Emulator failover not implemented");
    }

    public void acquireResourcesForStore(Context ctx)
        throws ArchiveException {
        throw new RuntimeException("Emulator failover not implemented");
    }

    public void acquireResourcesForRetrieve(Context ctx)
        throws ArchiveException {
        throw new RuntimeException("Emulator failover not implemented");
    }

    public int getWriteBufferSize() {
        return(PAGE_SIZE);
    }

    public int getReadBufferSize() {
        return(PAGE_SIZE);
    }

    public int getReadBufferSize(Context ctx) {
        return(PAGE_SIZE);
    }

    public int getLastReadBufferSize(int length) {
        return(length);
    }

    public SystemMetadata compressChunkSM(SystemMetadata[] sms) {
        SystemMetadata sm = new SystemMetadata(sms[0]);
        sm.setSize(sms[sms.length-1].getSize());
        sm.setContentHash(sms[sms.length-1].getContentHash());
        sm.setHashAlgorithm(sms[sms.length-1].getHashAlgorithm());
        return sm;
    }

    public byte[] getMetadataField(NewObjectIdentifier oid)
        throws ArchiveException{

        try {
            DBEntry entry = getFromDB(oid.toHexString());
            return(entry.mdField);
        } catch (SQLException e) {
            ArchiveException newe = new ArchiveException("Unknown oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("getMetadataField failed for oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }


    public SystemMetadata getSystemMetadata(NewObjectIdentifier oid,
                                            boolean ignoreDeleted,
                                            boolean ignoreIncomplete)
           throws NoSuchObjectException, OAException {
        // on emulator return empty system metadata object because there is not system cache
        // so this is not going to be inserted anywhere
        return new SystemMetadata();
    }

    public void shutdown() {
        try {
            Derby.getInstance().stop(OADB_NAME);
            LOG.info("OA DB has been closed");
        } catch (EMDException e) {
            LOG.warning("Failed to close the OA database ["+
                        e.getMessage()+"]");
        }
    }

    private static class OAStream
        implements Disposable {

        public FileChannel channel;

        private OAStream(FileChannel nChannel) {
            channel = nChannel;
        }

        public void dispose() {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
            channel = null;
        }
    }

    // Add a legal hold tag
    public void addLegalHold(NewObjectIdentifier oid, String legalHold)
        throws ArchiveException {

        Connection connection = null;
        Statement statement = null;

        try {

            connection = Derby.getInstance().getConnection(OADB_NAME);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("insert into ");
            sb.append(HOLDTABLE_NAME);
            sb.append(" (oid, hold) values ('");
            sb.append(oid.toHexString());
            sb.append("', '" + legalHold + "')");
            statement.executeUpdate(sb.toString());

        } catch (SQLException e) {
            ArchiveException newe =
                new ArchiveException("Failed to add legal hold [" +
                                     legalHold + "] for oid ["+
                                     oid.toHexString()+"] - ["+
                                     e.getMessage()+"]");
            newe.initCause(e);
            throw newe;        
        } finally {
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }

        // Debug
        LOG.info("Added legal hold [" + legalHold + "] to oid " +
                 oid.toHexString());
    }

    // Remove a legal hold tag
    public void removeLegalHold(NewObjectIdentifier oid, String legalHold)
        throws ArchiveException {

        Connection connection = null;
        Statement statement = null;

        try {

            connection = Derby.getInstance().getConnection(OADB_NAME);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("delete from ");
            sb.append(HOLDTABLE_NAME);
            sb.append(" where oid='");
            sb.append(oid.toHexString());
            sb.append("' and hold='");
            sb.append(legalHold);
            sb.append("'");

            // Debug
            LOG.info("Deleting legal hold with: " + sb.toString());

            statement.executeUpdate(sb.toString());

        } catch (SQLException e) {
            ArchiveException newe =
                new ArchiveException("Failed to remove legal hold [" +
                                     legalHold + "] for oid ["+
                                     oid.toHexString()+"] - ["+
                                     e.getMessage()+"]");
            newe.initCause(e);
            throw newe;  
        } finally {
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }

        // Debug
        LOG.info("Removed legal hold [" + legalHold + "] from oid " + oid);
    }

    // Get all legal hold tags for an oid
    public String[] getLegalHolds(NewObjectIdentifier oid)
        throws ArchiveException {
        Connection connection = null;
        Statement statement = null;
        ResultSet results = null;
        String[] legalHolds = null;
        ArrayList holdsArray = new ArrayList();

        try {
            connection = Derby.getInstance().getConnection(OADB_NAME);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("select hold from ");
            sb.append(HOLDTABLE_NAME);
            sb.append(" where oid='");
            sb.append(oid.toHexString());
            sb.append("'");
            results = statement.executeQuery(sb.toString());

            while (results.next()) {
                holdsArray.add(results.getString(1));
            }

            if (!holdsArray.isEmpty()) {
                legalHolds = new String[holdsArray.size()];
                legalHolds = (String[])holdsArray.toArray(legalHolds);

                for (int i=0; i<legalHolds.length; i++) {
                    LOG.info(oid.toHexString() + " has legal hold [" +
                             legalHolds[i] + "]");
                }
            }
            
        } catch (SQLException e) {
            ArchiveException newe =
                new ArchiveException("Failed to retrieve legal " +
                                     "holds for oid ["+
                                     oid.toHexString()+"] - ["+
                                     e.getMessage()+"]");
            newe.initCause(e);
            throw newe;  
        } finally {
            if (results != null)
                try { results.close(); } catch (SQLException e) {}
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }

        return legalHolds;
    }

    // Set the retention time
    public void setRetentionTime(NewObjectIdentifier oid,
                                 long newRetentionTime)
        throws ArchiveException {

        // Get the current date
        long currentTime = (new Date()).getTime();

        // Date cannot be less than -1
        if (newRetentionTime < RETENTION_UNSPECIFIED) {
            throw new
                ArchiveException("Cannot set retention time for oid " +
                                 oid + " because the specified date " +
                                 newRetentionTime + " is invalid");
        }

        // Date cannot be in the past
        if ((newRetentionTime) != RETENTION_UNSPECIFIED &&
            (newRetentionTime < currentTime)) {
            throw new
                ArchiveException("Cannot set retention time for oid " +
                                 oid + " because the specified date " +
                                 newRetentionTime + " is in the past");
        }

        // Get the old date from the footer
        long oldRetentionTime = getRetentionTime(oid);

        // Cannot set to -1 if we already have a real date
        if ((newRetentionTime == RETENTION_UNSPECIFIED) &&
            (oldRetentionTime > RETENTION_UNSET)) {
            throw new
                ArchiveException("Cannot set retention time for oid " +
                                 oid + " because an uspecific time " +
                                 "cannot be set when a specific one" +
                                 "is already set");
        }

        // Cannot set to an earlier date than already set
        if ((oldRetentionTime > RETENTION_UNSET) && 
            (newRetentionTime < oldRetentionTime)) {
            throw new
                ArchiveException("Cannot set retention time for oid " + oid +
                                 " because an ealier time " +
                                 newRetentionTime +
                                 " cannot be set when the later time" +
                                 oldRetentionTime + " is already set");
        }
            
        // Get the SystemMetadata object from the db and update the
        // retention time.
        DBEntry entry = null;

        try {
            entry = getFromDB(oid.toHexString());
        } catch (SQLException e) {
            ArchiveException newe = new ArchiveException("Unknown oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to open oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        entry.smd.setRTime(newRetentionTime);

        try {
            putToDB(oid, entry, true);
        } catch (SQLException e) {
            ArchiveException newe = new ArchiveException("Failed to update " +
                                                         "retention time " +
                                                         "for oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;        
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to update oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        // Debug
        LOG.info("Updated retention time from " + oldRetentionTime +
                 " to " + newRetentionTime + " for " + oid);
    }

     // Get the existing date from the footer
    public long getRetentionTime(NewObjectIdentifier oid)
        throws ArchiveException {
        DBEntry entry = null;

        try {
            entry = getFromDB(oid.toHexString());
        } catch (SQLException e) {
            ArchiveException newe = new ArchiveException("Unknown oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to open oid ["+
                                                         oid.toHexString()+"] - ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        long retentionTime = entry.smd.getRTime();

        // Debug
        LOG.info("Retrieved OID " + oid.toHexString() +
                 " retention time " + retentionTime);

        return retentionTime;
    }

    // Replace the current set of legal holds for the oid with the
    // given set. Use with caution! This will delete anything not in
    // the given set! This implementation could be optimized to work
    // on the tree object directly.
    public void setLegalHolds(NewObjectIdentifier oid, String newHolds[])
        throws ArchiveException {

        // Check args
        if (oid == null || newHolds == null) {
            throw new ArchiveException("invalid arguments");
        }

        // Get the current holds
	String currentHolds[] = getLegalHolds(oid);

        if (currentHolds == null) {
            currentHolds = new String[]{};
        }

        // Iterate over and compare ArrayLists
        ArrayList addArray = new ArrayList();
        ArrayList removeArray = new ArrayList();
        ArrayList newArray = new ArrayList(Arrays.asList(newHolds));
        ArrayList currentArray = new ArrayList(Arrays.asList(currentHolds));

        // Find the legal holds to add. For each new legal hold, if
        // the existing set does not contain it, add it.
        for (int i=0; i<newArray.size(); i++) {
            if (!currentArray.contains(newArray.get(i))) {
                addArray.add(newArray.get(i));
            }
        }

        // Find the legal holds to remove. For each existing legal
        // hold, if the new set does not contain it, remove it.
        for (int i=0; i<currentArray.size(); i++) {
            if (!newArray.contains(currentArray.get(i))) {
                removeArray.add(currentArray.get(i));
            }
        }
        
        // Debug
        LOG.info("Current holds:  " + currentArray.toString());
        LOG.info("New holds:      " + newArray.toString());
        LOG.info("Removing holds: " + removeArray.toString());
        LOG.info("Adding holds:   " + addArray.toString());
        
        // Remove holds
        for (int i=0; i<removeArray.size(); i++) {
            removeLegalHold(oid, (String)removeArray.get(i));
        }
            
        // Add holds
        for (int i=0; i<addArray.size(); i++) {
            addLegalHold(oid, (String)addArray.get(i));
        }
    }

    /**
     *  Check to see whether an object is deletable by checking for a
     *  past retention time and no legal hold tags.
     */
    public boolean isComplianceDeletable(NewObjectIdentifier oid)
        throws ArchiveException {

        // Read the retention time from the footer
        long retentionTime = getRetentionTime(oid);

        // Cannot delete if the time is unspecified
        if (retentionTime == RETENTION_UNSPECIFIED) {
            LOG.info("Cannot delete oid " + oid.toHexString() +
                     " because retention time is -1");
            return false;
        }

        // Get the current time
        long currentDate = System.currentTimeMillis();

        // Cannot delete if the time is in the future
        if (retentionTime > currentDate) {
            LOG.info("Cannot delete oid " + oid.toHexString() +
                     " because retention time is in the future");
            return false;
        }

        // Cannot delete if there are legal holds present.
        if (getNumLegalHolds(oid) > 0) {
            LOG.info("Cannot delete oid " + oid.toHexString() +
                     " because legal holds are present");
            return false;
        }
 
        // Return true if the retention time is unset or is in the
        // past, or there are no legal holds.
        return true;
    }

    /**
     *  Count the number of legal holds by retrieving all legal holds
     *  and then counting them up.
     */
    public int getNumLegalHolds(NewObjectIdentifier oid)
        throws ArchiveException {
        String[] holds = getLegalHolds(oid);
        if (holds == null) {

            // Debug
            LOG.info("0 legal holds found for oid " + oid.toHexString());

            return 0;
        }

        // Debug
        LOG.info(holds.length + " legal holds found for oid " + oid.toHexString());

        return holds.length;
    }

    // Get the Footer Extension modified time for an object
    public long getExtensionModifiedTime(NewObjectIdentifier oid)
        throws ArchiveException {
        return 0;
    }
}
