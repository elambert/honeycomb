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



package com.sun.honeycomb.common;

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.protocol.server.ProtocolService;
import com.sun.honeycomb.multicell.lib.Rule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.multicell.lib.MultiCellLibException;


/** TODO - this should probably move into oa, and common should have
 * an interface and maybe a basic implementation - but a ton of this
 * is now OA-specific */
/**
 * This class encapsulates an object identifier.  An object identifer
 * is returned when an object is stored, and it is subseqently used to
 * refer to that object when calling retrieve and other operations.
 *
 * TODO - JavaDoc
 */

public class NewObjectIdentifier implements Comparable, Codable, Serializable {
    private static Logger LOG =
      Logger.getLogger(NewObjectIdentifier.class.getName());

    public NewObjectIdentifier() {
    }

    public NewObjectIdentifier(int layoutMapId,
      byte objectType,
      int chunkNumber,
      NewObjectIdentifier linkOid) {
        switch (objectType) {
        case NULL_TYPE:
            // for unit test only
            init(getNextUID(), layoutMapId, objectType, chunkNumber,
              NULL_TYPE, UNDEFINED_RULE);
            return;

        case METADATA_TYPE:
            init(getNextUID(), layoutMapId, objectType, chunkNumber,
              linkOid.getRuleId(), linkOid.getSilolocation());
            return;

        default:
            throw new InternalException("OA object is not  NULL_TYPE or " +
              " METADATA_TYPE");
        }
    }


    public NewObjectIdentifier(int layoutMapId,
                               byte objectType,
                               int chunkNumber) {
        if (objectType == DATA_TYPE) {
            //
            // Right now there is only one rule for each cell so
            // rulenumber is hardcoded to one.
            //
            UID uid = getNextUID();
            init(uid, layoutMapId, objectType, chunkNumber, (byte) 1,
                 MultiCellLib.getInstance().getNextSiloLocation(uid));
            return;
        } else {
            throw new InternalException("OA object is not DATA_TYPE");
        }
    }

    public NewObjectIdentifier(UID uid,
      int layoutMapId,
      byte objectType,
      int chunkNumber,
      byte ruleId,
      short silolocation) {
        init(uid, layoutMapId, objectType, chunkNumber, ruleId, silolocation);
    }

    public NewObjectIdentifier(NewObjectIdentifier clone) {
        init(clone.getUID(),
          clone.getLayoutMapId(),
          clone.getObjectType(),
          clone.getChunkNumber(),
          clone.getRuleId(),
          clone.getSilolocation());
    }

    public UID getUID() {return uid;}
    public byte getVersion() {return version;}
    public int getLayoutMapId() {return layoutMapId;}
    public byte getObjectType() {return objectType;}
    public int getChunkNumber() {return chunkNumber;}
    public byte getRuleId() {return ruleId;}
    public byte getRepresentation() { return representation; }
    public short getSilolocation() { return silolocation; }



    //
    // Used by QueryPlus to patch the oid on the fly when we return results
    // to the clients.
    //
    // (Not the most efficient way... could patch hex directly...)
    //
    public static String convertExternalHexString(String oidStr) {
        NewObjectIdentifier o = NewObjectIdentifier.fromHexString(oidStr);
        return o.toExternalHexString();
    }

    public String toExternalHexString() {
        NewObjectIdentifier newo = new NewObjectIdentifier(this);
        newo.applyExternalForm();
        return newo.toHexString();
    }

    public static NewObjectIdentifier fromExternalHexString(String oidStr) {
        NewObjectIdentifier o = NewObjectIdentifier.fromHexString(oidStr);
        o.applyInternalForm();
        return o;
    }

    public ExternalObjectIdentifier toExternalObjectID() {
        NewObjectIdentifier newo = new NewObjectIdentifier(this);
        newo.applyExternalForm();
        return new ExternalObjectIdentifier(newo.getDataBytes());
    }

    public static NewObjectIdentifier fromExternalObjectID(ExternalObjectIdentifier eoid) {
        return NewObjectIdentifier.fromExternalHexString(eoid.toString());
    }

    //
    // Transformation from external form to internal form
    //
    private NewObjectIdentifier applyExternalForm() {
        if (representation == REPRESENTATION_EXT) {
            throw new InternalException("OID is already in its external form");
        }

        try {
            byte ruleNumber = ruleId;
            ruleId = MultiCellLib.getInstance().getOriginCellid(ruleId,
              silolocation);
            //
            // paranoia checking: until we have cros cell sloshing,
            // originCellid maps directly to the current cellid.
            //
            if (ruleId != CELL_ID) {

                String error = "Origin cellid should match " +
                  "current cellid: originCellid = " +
                  ruleId + ", cellid = " + CELL_ID +
                  ", (ruleNumber = " + ruleNumber +
                  ", siloLocation = " + silolocation +
                  ")";
                LOG.severe(error);
                throw new InternalException(error);
            }
        } catch(MultiCellLibException mce) {
            throw new InternalException(mce);
        }
        representation = REPRESENTATION_EXT;
        return this;
    }

    private void applyInternalForm() {
        if (representation == REPRESENTATION_INT) {
            throw new InternalException("OID is already in its internal form");
        }

        try {
            ruleId = MultiCellLib.getInstance().getRuleNumber(ruleId,
              silolocation);
        } catch(MultiCellLibException mce) {
            throw new InternalException(mce);
        }

        representation = REPRESENTATION_INT;
    }



    public int hashCode() {
        return uid.hashCode();
    }

    /** Used by OAClient to switch to right ctx for chunk to read */
    public void setChunkNumber(int chunkNumber) {
        this.chunkNumber = chunkNumber;
    }

    /** Used by OAClient to switch to right ctx for chunk to read */
    public void setLayoutMapId(int layoutMapId) {
        this.layoutMapId = layoutMapId;
    }

    public boolean equals(Object other) {
        return (compareTo(other) == 0);
    }

    public int compareTo(Object other) {
        NewObjectIdentifier oid = (NewObjectIdentifier)other;

        if(version != oid.getVersion()) {
            return version - oid.getVersion();
        }

        if(ruleId != oid.getRuleId()) {
            return ruleId - oid.getRuleId();
        }

        if(representation != oid.getRepresentation()) {
            return representation - oid.getRepresentation();
        }

        if (silolocation != oid.getSilolocation()) {
            return silolocation - oid.getSilolocation();
        }

        /*
         * WARNING: we are only doing toString().compareTo() so that 
         *          the comparison between OIDS in honeycomb stack is
         *          equal to the comparison being done in BDB. Now there
         *          is a way of changing the BDB Comparison function to 
         *          use NewObjectIdentifier.compareTo but the performance
         *          hit is unacceptable at this time so we'll have to fix
         *          all of this in the near future.
         */
        int uidComp = uid.toString().compareTo(oid.getUID().toString());
        if(uidComp != 0) {
            return uidComp;
        }

        if(objectType != (oid.getObjectType())) {
            return objectType - oid.getObjectType();
        }

        if(layoutMapId != (oid.getLayoutMapId())) {
            return layoutMapId - oid.getLayoutMapId();
        }

        if(chunkNumber != (oid.getChunkNumber())) {
            return chunkNumber - oid.getChunkNumber();
        }

        return 0;
    }

    private NewObjectIdentifier(String string, boolean isHex) {
        if (isHex) {
            initFromHexString(string);
        } else {
            initFromString(string);
        }
    }

    public NewObjectIdentifier(String string) {
        initFromString(string);
    }


    public String toString() {
        return uid.toString() + "." +
          representation + "." +
          ruleId + "." +
          silolocation  + "." +
          version + "." +
          objectType + "." +
          chunkNumber + "." +
          layoutMapId;
    }


    public String toHexString() {
        return ByteArrays.toHexString(getDataBytes());
    }

    public static NewObjectIdentifier fromHexString(String oidStr) {
        return new NewObjectIdentifier(oidStr,true);
    }

    public String toLegacyHexString() {
        return legacyAdaptor.toHexString();
    }

    public static NewObjectIdentifier fromLegacyHexString(String oidStr) {
        NewObjectIdentifier oid = new NewObjectIdentifier();
        oid.legacyAdaptor.initFromHexString(oidStr);
        return oid;
    }

    public void encode(Encoder encoder) {
        encoder.encodeByte(version);          // 1
        encoder.encodeByte(representation);   // 1
        encoder.encodeByte(ruleId);           // 1
        encoder.encodeShort(silolocation);    // 2
        encoder.encodeKnownLengthBytes(uid.getBytes());  // 16
        encoder.encodeInt(layoutMapId);       // 4
        encoder.encodeByte(objectType);       // 1
        encoder.encodeInt(chunkNumber);       // 4
    }

    public void decode(Decoder decoder) {
        byte checkVersion = decoder.decodeByte(); // 1
        if(checkVersion != version) {
            LOG.warning("Expected version " + version + " but got " +
              checkVersion);
            // TODO: Throw exception - bad version!
        }
        representation = decoder.decodeByte(); // 1
        ruleId = decoder.decodeByte(); // 1
        silolocation = decoder.decodeShort(); // 2
        uid = new UID(decoder.decodeKnownLengthBytes(UID.NUMBYTES)); // 16
        layoutMapId = decoder.decodeInt();  // 4
        objectType = decoder.decodeByte();  // 1
        chunkNumber = decoder.decodeInt();  // 4
    }

    public byte[] getDataBytes() {
        byte[] result = new byte[OID_LENGTH];

        result[VERSION_OFFSET] = version;
        result[REPRESENTATION_OFFSET] = representation;
        result[RULEID_OFFSET] = ruleId;
        ByteArrays.putShort(silolocation, result, SILOLOCATION_OFFSET);
        uid.getBytes(result, UID_OFFSET);
        ByteArrays.putInt(layoutMapId, result, LAYOUT_OFFSET);
        result[TYPE_OFFSET] = objectType;
        ByteArrays.putInt(chunkNumber, result, CHUNK_OFFSET);

        return result;
    }

    public static NewObjectIdentifier readFromBytes(byte[] input) {
        NewObjectIdentifier result = new NewObjectIdentifier();
        result.initFromBytes(input);

        return result;
    }

    public void serialize(DataOutput dout)
        throws IOException {
        byte[] bytes = getDataBytes();
        dout.writeInt(bytes.length);
        if (bytes.length > 0) {
            dout.write(bytes);
        }
    }

    //Serialize the external OID format
    // called by MetadataClient.serializeOids.
    public void serializeExternal(DataOutput dout)
        throws IOException {
        NewObjectIdentifier newo = new NewObjectIdentifier(this);
        newo.applyExternalForm();
        byte[] bytes = newo.getDataBytes();
        dout.writeInt(bytes.length);
        if (bytes.length > 0) {
            dout.write(bytes);
        }
    }

    public static NewObjectIdentifier deserialize(DataInput din)
        throws IOException {
        int length = din.readInt();
        byte[] bytes = new byte[length];
        if (length > 0) {

            try {
                din.readFully(bytes);
            } catch (IOException e) {
                throw (e);
            }
        }

        return readFromBytes(bytes);
    }

    // PRIVATE METHODS //

    private void init(UID uid, int layoutMapId,  byte objectType,
      int chunkNumber, byte ruleId,
      short silolocation) {
        this.uid = uid;
        this.layoutMapId = layoutMapId;
        this.objectType = objectType;
        this.chunkNumber = chunkNumber;
        this.ruleId = ruleId;
        this.silolocation = silolocation;
        this.representation = REPRESENTATION_INT;
    }

    protected void initFromString(String string) {
        String[] fields = string.split("\\.");
        uid = new UID(fields[0]);
        representation = Byte.parseByte(fields[1]);
        ruleId = Byte.parseByte(fields[2]);
        silolocation = Short.parseShort(fields[3]);
        byte read_version = Byte.parseByte(fields[4]);
        objectType = Byte.parseByte(fields[5]);
        chunkNumber = Integer.parseInt(fields[6]);
        layoutMapId = Integer.parseInt(fields[7]);
    }

    protected void initFromHexString(String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException("hexString cannot be null");
        }

        initFromBytes(ByteArrays.toByteArray(hexString));
    }

    private void initFromBytes(byte[] bytes) {
        if (bytes[VERSION_OFFSET] != version) {
            throw new IllegalArgumentException("version must be " + version);
        }
        representation = bytes[REPRESENTATION_OFFSET];
        ruleId = bytes[RULEID_OFFSET];
        silolocation = ByteArrays.getShort(bytes, SILOLOCATION_OFFSET);
        uid = new UID(bytes, UID_OFFSET);
        layoutMapId = ByteArrays.getInt(bytes, LAYOUT_OFFSET);
        objectType = bytes[TYPE_OFFSET];
        chunkNumber = ByteArrays.getInt(bytes, CHUNK_OFFSET);
    }

    private synchronized UID getNextUID() {
        UID uid = new UID();
        return uid;
    }

    private NewObjectIdentifier(UID uid,
      int layoutMapId,
      byte objectType,
      int chunkNumber) {
        init(uid, layoutMapId, objectType, chunkNumber,
          NULL_TYPE, UNDEFINED_RULE);
    }

    // PRIVATE MEMBERS //
    protected static final byte REPRESENTATION_EXT = 0;
    protected static final byte REPRESENTATION_INT = 1;

    private static final int VERSION_OFFSET = 0;
    private static final int REPRESENTATION_OFFSET = VERSION_OFFSET + 1;
    private static final int RULEID_OFFSET = REPRESENTATION_OFFSET + 1;
    private static final int SILOLOCATION_OFFSET = RULEID_OFFSET + 1;
    private static final int UID_OFFSET = SILOLOCATION_OFFSET + 2;
    private static final int LAYOUT_OFFSET = UID_OFFSET + UID.NUMBYTES;
    private static final int TYPE_OFFSET = LAYOUT_OFFSET + 4;
    private static final int CHUNK_OFFSET = TYPE_OFFSET + 1;

    public static final int OID_LENGTH = 1 + 1 + 2 + 1 + UID.NUMBYTES + 4 + 1 + 4;
    public static final byte NULL_TYPE = 0;
    public static final byte DATA_TYPE = 1;
    public static final byte METADATA_TYPE = 2;
    public static final int  UNDEFINED = -1;
    public static final short UNDEFINED_RULE = -1;

    private static final byte[] nullBytes = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    public static final NewObjectIdentifier NULL =
      new NewObjectIdentifier(new UID(nullBytes), UNDEFINED,
        NULL_TYPE, UNDEFINED);

    private static final byte version = 2;

    static private final String PROP_CELLID = "honeycomb.silo.cellid";
    private static byte CELL_ID;
    static {
        try {
            CELL_ID = (byte) 
              ClusterProperties.getInstance().getPropertyAsInt(PROP_CELLID, 0);
        } catch (Exception abort) {
            throw new InternalException(abort.getClass().getName() + 
              ", error reading " + PROP_CELLID + ", " + abort.getMessage());
        }
    }


    protected UID uid = null;
    protected int layoutMapId = UNDEFINED;
    protected byte objectType = UNDEFINED;
    protected int chunkNumber = UNDEFINED;
    protected byte representation = UNDEFINED;
    protected byte ruleId = UNDEFINED;
    protected short silolocation = UNDEFINED;

    protected LegacyAdaptor legacyAdaptor = new LegacyAdaptor();

    /**********************************************************************
     * Adapt to older version (1.0.x).
     **/
    protected class LegacyAdaptor implements Serializable {
        private static final byte version = (byte) 1;
        private static final int VersionOffset = 0;
        private static final int CellIdOffset = VersionOffset + 1;
        private static final int UidOffset = CellIdOffset + 2;
        private static final int LayoutOffset = UidOffset + UID.NUMBYTES;
        private static final int TypeOffset = LayoutOffset + 4;
        private static final int ChunkOffset = TypeOffset + 1;
        public static final int LENGTH = 1 + 2 + UID.NUMBYTES + 4 + 1 + 4;

        private short cellId = 1;

        /**********************************************************************/
        public void initFromHexString(String hexString) {
            if ((hexString == null)
                || (hexString.length() != (LENGTH * 2))) {
                throw new IllegalArgumentException("invalid hex string");
            }

            byte[] bytes = ByteArrays.toByteArray(hexString);

            if (bytes[VersionOffset] != version) {
                throw new IllegalArgumentException("version expected is "
                                                   + version + ", but found "
                                                   + bytes[VERSION_OFFSET]);
            }
            cellId = ByteArrays.getShort(bytes, CellIdOffset);
            uid = new UID(bytes, UidOffset);
            layoutMapId = ByteArrays.getInt(bytes, LayoutOffset);
            objectType = bytes[TypeOffset];
            chunkNumber = ByteArrays.getInt(bytes, ChunkOffset);
            adapt();
        }

        /**********************************************************************/
        public void adapt() {
            representation = REPRESENTATION_INT;
            ruleId = (byte) 1;
            silolocation = MultiCellLib.getInstance().getNextSiloLocation(uid);
        }

        /**********************************************************************/
        public String toHexString() {
            byte[] result = new byte[LENGTH];

            result[VersionOffset] = this.version;
            ByteArrays.putShort(cellId, result, CellIdOffset);
            uid.getBytes(result, UidOffset);
            ByteArrays.putInt(layoutMapId, result, LayoutOffset);
            result[TypeOffset] = objectType;
            ByteArrays.putInt(chunkNumber, result, ChunkOffset);
            return ByteArrays.toHexString(result);
        }

        /**********************************************************************/
        public void initializeFields(String oid) {
            String[] fields = oid.split("\\.");
            UID uid = new UID(fields[0]);
            short cellId = Short.parseShort(fields[1]);
            byte read_version = Byte.parseByte(fields[2]);
            byte objectType = Byte.parseByte(fields[3]);
            int chunkNumber = Integer.parseInt(fields[4]);
            int layoutMapId = Integer.parseInt(fields[5]);
            init(uid, layoutMapId, objectType, chunkNumber, (byte) 1, (short) 0);
            adapt();
        }
    }
}
