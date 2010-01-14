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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.sun.honeycomb.emd.common.*;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.lang.StringBuffer;
import java.io.RandomAccessFile;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.util.BitSetUtils;

public class SystemMetadata 
    implements CacheRecord {

    /**********************************************************************
     *
     * Constants and private fields
     *
     **********************************************************************/

    // Version-specific constants
    public static final byte CURRENT_VERSION = 1;
    public static final long HEADER_LENGTH = 195;

    // Field names

    public static final String FIELD_NAMESPACE          = "system";
    public static final String FIELD_OBJECTID           = "object_id";
    public static final String FIELD_CTIME              = "object_ctime";
    public static final String FIELD_DTIME              = "object_dtime";
    public static final String FIELD_RTIME              = "object_rtime";
    public static final String FIELD_DATA_HASH          = "object_hash";
    public static final String FIELD_HASH_ALG           = "object_hash_alg";
    public static final String FIELD_LAYOUTMAPID        = "object_layoutMapId";
    public static final String FIELD_N                  = "object_nDataFrags";
    public static final String FIELD_M                  = "object_mRedundantFrags";
    public static final String FIELD_LINK               = "object_link";
    public static final String FIELD_SIZE               = "object_size";
    public static final String FIELD_FRAGDATALEN        = "frag_datalen";
    public static final String FIELD_RELIABILITY        = "reliability";
    public static final String FIELD_RESTORED           = "object_restored";
    public static final String FIELD_REF_COUNT          = "object_refcount";
    public static final String FIELD_MAX_REF_COUNT      = "object_maxrefcount";
    public static final String FIELD_DELETED_REFS       = "object_deletedrefs";
    public static final String FIELD_EXTENSION_MTIME    = "extension_mtime";
    public static final String FIELD_CHKSUM_ALG         = "checksum_alg";
    public static final String FIELD_NUM_PREC_CHKSUMS   = "num_preceding_chksums";
    public static final String FIELD_METADATA           = "metadata";
    public static final String FIELD_QUERY_READY        = "query_ready";
    
    // Content Hash Algorithms
    public static final byte CONTENT_HASH_ALG_NONE = 0;
    public static final byte CONTENT_HASH_ALG_SHA1 = 1; 

    private NewObjectIdentifier oid;
    private long size;
    private long ctime;
    private long rtime;
    private long etime;
    private long aCtime;
    private long dtime;
    private byte shred;
    private short cksuAlg;
    private short precedingChksums;
    private byte version;                     // 1 from 0
    private int layoutMapId;                  // 4 from 5
    private ObjectReliability reliability;    // 8 from 9
    private NewObjectIdentifier link;         // 20 from 17
    private byte[] objectContentHash;
    private byte[] metadataField 
        = new byte[FragmentFooter.METADATA_FIELD_LENGTH];
    private String objectHashAlgorithm;
    private int refCount;
    private int maxRefCount;
    private BitSet deletedRefs;
    private long extensionModifiedTime;

    private boolean containsSystemAttributes;
    private boolean containsExtendedAttributes;
    
    private boolean restored;

    private boolean queryReady;

    /**********************************************************************
     *
     * Static methods
     *
     **********************************************************************/
    
    public static Field[] getAttributesDefinition(boolean systemCache)
        throws EMDConfigException {
        Field[] result = null;
        
        if (systemCache) {
            result = new Field[6];
        } else {
            result = new Field[6];
        }

        Namespace namespace = RootNamespace.getInstance().resolveNamespace(FIELD_NAMESPACE);

        result[0] = namespace.resolveField(FIELD_OBJECTID);
        result[1] = namespace.resolveField(FIELD_CTIME);
        result[2] = namespace.resolveField(FIELD_LAYOUTMAPID);
        
        if (systemCache) {
            // Build artificial fields
            result[3] = new Field(namespace, FIELD_N,
                                  Field.TYPE_BYTE, false);
            result[4] = new Field(namespace, FIELD_M,
                                  Field.TYPE_BYTE, false);
            result[5] = new Field(namespace, FIELD_LINK,
                                  Field.TYPE_STRING, false);
        } else {
            result[3] = namespace.resolveField(FIELD_SIZE);
            result[4] = namespace.resolveField(FIELD_DATA_HASH);
            result[5] = namespace.resolveField(FIELD_HASH_ALG);
        }
        
        return(result);
    }

    /**********************************************************************
     *
     * Constructors
     *
     **********************************************************************/
    
    public SystemMetadata() {
        setDefaultValues();
    }
    
    public SystemMetadata(SystemMetadata sm) {
        oid                             = sm.oid;
        size                            = sm.size;
        ctime                           = sm.ctime;
        dtime                           = sm.dtime;
        rtime                           = sm.rtime;
        version                         = sm.version;
        layoutMapId                     = sm.layoutMapId;
        reliability                     = sm.reliability;
        link                            = sm.link;
        containsSystemAttributes        = sm.containsSystemAttributes;
        containsExtendedAttributes      = sm.containsExtendedAttributes;
        objectContentHash               = sm.objectContentHash;
        objectHashAlgorithm             = sm.objectHashAlgorithm;
        refCount                        = sm.refCount;
        maxRefCount                     = sm.maxRefCount;
        deletedRefs                     = sm.deletedRefs;
        extensionModifiedTime           = sm.extensionModifiedTime;
        restored                        = sm.restored;
        cksuAlg                         = sm.cksuAlg;
        metadataField                   = sm.metadataField;
        precedingChksums                = sm.precedingChksums;
        queryReady                      = sm.queryReady;
    }

    /*
     * Constructor to populate only the system attributes
     */
    public SystemMetadata(NewObjectIdentifier nOid,
                          long nCtime,
                          int nLayoutMapId,
                          ObjectReliability nReliability,
                          NewObjectIdentifier nLink) {
        setDefaultValues();
        oid = nOid;
        ctime = nCtime;
        layoutMapId = nLayoutMapId;
        reliability = nReliability;
        link = nLink;
        containsSystemAttributes = true;
        containsExtendedAttributes = false;
        restored = true;
    }

    /*
     * Constructor to populate only the extended attributes
     */
    public SystemMetadata(NewObjectIdentifier nOid,
                          byte[] nObjectContentHash,
                          String nHashAlgorithm,
                          long nCtime,
                          int nLayoutMapId,
                          long nSize) {
        setDefaultValues();
        oid = nOid;
        objectContentHash = nObjectContentHash;
        objectHashAlgorithm = nHashAlgorithm;
        ctime = nCtime;
        layoutMapId = nLayoutMapId;
        size = nSize;
        containsSystemAttributes = false;
        containsExtendedAttributes = true;
        restored = true;
    }


    public SystemMetadata(NewObjectIdentifier nOid,
                          long nSize,
                          long nCtime,
                          long nRtime,
                          long nEtime,
                          long nACtime,
                          long nDtime,
                          byte nShred,
                          short nCksuAlg,
                          short nPrecedingChksums,
                          byte[] nMetadataField,
                          byte nVersion,
                          int nLayoutMapId,
                          ObjectReliability nReliability,
                          NewObjectIdentifier nLink,
                          byte[] nObjectContentHash,
                          int nRefCount,
                          int nMaxRefCount,
                          BitSet nDeletedRefs) {
        setDefaultValues();
        oid = nOid;
        size = nSize;
        ctime = nCtime;
        rtime = nRtime;
        etime = nEtime;
        aCtime = nACtime;
        dtime = nDtime;
        shred = nShred;
        cksuAlg = nCksuAlg;
        precedingChksums = nPrecedingChksums;
        setMetadataField(nMetadataField);
        version = nVersion;
        layoutMapId = nLayoutMapId;
        reliability = nReliability;
        link = nLink;
        objectContentHash = nObjectContentHash;
        containsSystemAttributes = true;
        containsExtendedAttributes = true;
        refCount = nRefCount;
        maxRefCount = nMaxRefCount;
        deletedRefs = new BitSet(FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH); 
        deletedRefs.or(nDeletedRefs);
        restored = true;
    }

    private void setDefaultValues() {
        oid = null;
        size = 0;
        ctime = 0;
        rtime = 0;
        etime = 0;
        aCtime = 0;
        dtime = 0;
        shred = 0;
        cksuAlg = 0;
        precedingChksums = 0;
        version = CURRENT_VERSION;
        layoutMapId = 0;
        reliability = null;
        link = null;
        objectContentHash = new byte[0];
        refCount = 0;
        maxRefCount = 0;
        deletedRefs = new BitSet(FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH); 
		extensionModifiedTime = 0;
        restored = true;
        queryReady = false;
    }

    public void setOID(NewObjectIdentifier oid) {
        this.oid = oid;
    }

    public void setLayoutMapId(int layoutMapId) {
        this.layoutMapId = layoutMapId;
    }
    
    public void setSize(long size) {
        this.size = size;
    }

    public void setContentHash(byte[] contentHash) {
        objectContentHash = contentHash;
    }
   
    public void setChecksumAlg (short chksumAlg) {
        cksuAlg = chksumAlg;
    }

    public void setNumPreceedingChksums (short chksums) {
        precedingChksums = chksums;
    }

    public void setMetadataField(byte[] metadata) {
        metadataField = metadata;
    }
    
    public void setQueryReady(boolean queryReady) {
        this.queryReady = queryReady;
    }

    public boolean isQueryReady() {
        return queryReady;
    }

    public void setSystemAttributes(int nLayoutMapId,
                                    ObjectReliability nReliability,
                                    NewObjectIdentifier nLink) {
        layoutMapId = nLayoutMapId;
        reliability = nReliability;
        link = nLink;
        containsSystemAttributes = true;
    }

    public void setExtendedAttributes(NewObjectIdentifier nOid,
                                      long nCtime,
                                      int nLayoutMapId,
                                      long nSize) {
        oid = nOid;
        ctime = nCtime;
        layoutMapId = nLayoutMapId;
        size = nSize;
        containsExtendedAttributes = true;
    }

    /**********************************************************************
     *
     * Class Methods
     *
     **********************************************************************/

    /**
     * The <code>populate</code> method inserts the available fields in
     * that class into the map
     *
     * @param map the <code>Map</code> to populate
     * @param systemCache defines if the attributes that go to the system
     * cache should be inserted or if only the user visible subset should
     * be.
     */

    public void populateStrings(Map map,
                         boolean systemCache) 
        throws EMDException {

        if (systemCache && !containsSystemAttributes) {
            throw new EMDException("Cannot extract the system attributes");
        }
        if (!systemCache && !containsExtendedAttributes) {
            throw new EMDException("Cannot extract the extended attributes");
        }

        map.put(FIELD_NAMESPACE+"."+FIELD_OBJECTID, oid.toHexString());
        map.put(FIELD_NAMESPACE+"."+FIELD_CTIME, Long.toString(ctime));
        map.put(FIELD_NAMESPACE+"."+FIELD_LAYOUTMAPID, Integer.toString(layoutMapId));
        map.put(FIELD_NAMESPACE+"."+FIELD_SIZE, Long.toString(size));
        map.put(FIELD_NAMESPACE+"."+FIELD_DATA_HASH, getContentHashString());
        map.put(FIELD_NAMESPACE+"."+FIELD_HASH_ALG,  getHashAlgorithmString());
        
        if (systemCache) {
            map.put(FIELD_NAMESPACE+"."+FIELD_DTIME, Long.toString(dtime));
            map.put(FIELD_NAMESPACE+"."+FIELD_RTIME, Long.toString(rtime));
            map.put(FIELD_NAMESPACE+"."+FIELD_N, Integer.toString(reliability.getDataFragCount()));
            map.put(FIELD_NAMESPACE+"."+FIELD_M, Integer.toString(reliability.getRedundantFragCount()));
            map.put(FIELD_NAMESPACE+"."+FIELD_LINK, link.toString());
            map.put(FIELD_NAMESPACE+"."+FIELD_MAX_REF_COUNT,Integer.toString(maxRefCount));
            map.put(FIELD_NAMESPACE+"."+FIELD_REF_COUNT,Integer.toString(refCount));
            map.put(FIELD_NAMESPACE+"."+FIELD_DELETED_REFS,BitSetUtils.toString(deletedRefs));
            map.put(FIELD_NAMESPACE+"."+FIELD_CHKSUM_ALG,Short.toString(cksuAlg));
            map.put(FIELD_NAMESPACE+"."+FIELD_METADATA,getMetadataHashString());
        }
    }
    
    /**
     * The <code>populateMD</code> method inserts the available fields in
     * that class into the map, using appropriate metadata types
     *
     * @param map the <code>Map</code> to populate
     * @param systemCache defines if the attributes that go to the system
     * cache should be inserted or if only the user visible subset should
     * be.
     */

    public void populateMD(Map map,
                           boolean systemCache) 
        throws EMDException {

        if (systemCache && !containsSystemAttributes) {
            throw new EMDException("Cannot extract the system attributes");
        }
        if (!systemCache && !containsExtendedAttributes) {
            throw new EMDException("Cannot extract the extended attributes");
        }

        map.put(FIELD_NAMESPACE+"."+FIELD_OBJECTID, oid.toExternalObjectID());
        map.put(FIELD_NAMESPACE+"."+FIELD_CTIME, new Long(ctime));
        map.put(FIELD_NAMESPACE+"."+FIELD_LAYOUTMAPID, new Long(layoutMapId));
        
        map.put(FIELD_NAMESPACE+"."+FIELD_SIZE, new Long(size));
        map.put(FIELD_NAMESPACE+"."+FIELD_DATA_HASH, objectContentHash);
        map.put(FIELD_NAMESPACE+"."+FIELD_HASH_ALG, getHashAlgorithmString());

        if (systemCache) {
            map.put(FIELD_NAMESPACE+"."+FIELD_DTIME, new Long(dtime));
            map.put(FIELD_NAMESPACE+"."+FIELD_RTIME, new Long(rtime));
            map.put(FIELD_NAMESPACE+"."+FIELD_N, new Long(reliability.getDataFragCount()));
            map.put(FIELD_NAMESPACE+"."+FIELD_M, new Long(reliability.getRedundantFragCount()));
            map.put(FIELD_NAMESPACE+"."+FIELD_LINK, link.toExternalObjectID());
            map.put(FIELD_NAMESPACE+"."+FIELD_MAX_REF_COUNT,new Long(maxRefCount));
            map.put(FIELD_NAMESPACE+"."+FIELD_REF_COUNT,new Long(refCount));
            map.put(FIELD_NAMESPACE+"."+FIELD_DELETED_REFS,BitSetUtils.toByteArray(deletedRefs));
            map.put(FIELD_NAMESPACE+"."+FIELD_CHKSUM_ALG,new Short(cksuAlg));
            map.put(FIELD_NAMESPACE+"."+FIELD_METADATA,metadataField);
        }
    }

    public void populateFrom(Map map, boolean systemCache) {
        //OID in Internal Hex format
        oid = NewObjectIdentifier.fromHexString((String)map.get(FIELD_NAMESPACE+"."+FIELD_OBJECTID));
        ctime = new Long((String)map.get(FIELD_NAMESPACE+"."+FIELD_CTIME)).longValue();
        layoutMapId = new Integer((String)map.get(FIELD_NAMESPACE+"."+FIELD_LAYOUTMAPID)).intValue();
        size = new Long((String)map.get(FIELD_NAMESPACE+"."+FIELD_SIZE)).longValue();
        String contentHash = (String) map.get(FIELD_NAMESPACE+"."+FIELD_DATA_HASH);
        setContentHash(ByteArrays.toByteArray(contentHash));
        objectHashAlgorithm = (String)map.get(FIELD_NAMESPACE+"."+FIELD_HASH_ALG);

        if (systemCache) {
            int dataFragCount = new Integer((String)map.get(FIELD_NAMESPACE+"."+FIELD_N)).intValue();
            int reliFragCount = new Integer((String)map.get(FIELD_NAMESPACE+"."+FIELD_M)).intValue();
            reliability = new ObjectReliability(dataFragCount,reliFragCount);
            link = new NewObjectIdentifier((String)map.get(FIELD_NAMESPACE+"."+FIELD_LINK));
            dtime = new Long((String)map.get(FIELD_NAMESPACE+"."+FIELD_DTIME)).longValue();
            rtime = new Long((String)map.get(FIELD_NAMESPACE+"."+FIELD_RTIME)).longValue();
            refCount = new Integer((String)map.get(FIELD_NAMESPACE+"."+FIELD_REF_COUNT)).intValue();
            maxRefCount = new Integer((String)map.get(FIELD_NAMESPACE+"."+FIELD_MAX_REF_COUNT)).intValue();
            deletedRefs = BitSetUtils.fromString((String)map.get(FIELD_NAMESPACE+"."+FIELD_DELETED_REFS));
            cksuAlg = new Short((String)map.get(FIELD_NAMESPACE+"."+FIELD_CHKSUM_ALG)).shortValue();
            setMetadataField(ByteArrays.toByteArray((String)map.get(FIELD_NAMESPACE+"."+FIELD_METADATA)));
        }
    }
    
    public QueryMap toQueryMap() throws EMDException {
        HashMap map = new HashMap();

        //Populate the map with typed metadata
        populateMD(map, true);
        
        String[] names = new String[map.size()];
        Object[] values = new Object[map.size()];
        
        map.keySet().toArray(names);
        map.values().toArray(values);
        
        QueryMap qmap = new QueryMap(names,values);
        return qmap;
    }

    public Object get(String attribute) {
        if (attribute.equals(FIELD_OBJECTID)) {
            return(oid);
        }
        if (attribute.equals(FIELD_CTIME)) {
            return(new Long(ctime));
        }
       
        if (attribute.equals(FIELD_RTIME)) {
            return(new Long(rtime));
        }
        
        if (attribute.equals(FIELD_LAYOUTMAPID)) {
            return(new Integer(layoutMapId));
        }
        if (attribute.equals(FIELD_N)) {
            return(new Integer(reliability.getDataFragCount()));
        }
        if (attribute.equals(FIELD_M)) {
            return(new Integer(reliability.getRedundantFragCount()));
        }
        if (attribute.equals(FIELD_LINK)) {
            return(link);
        }
        if (attribute.equals(FIELD_SIZE)) {
            return(new Long(size));
        }

        if (attribute.equals(FIELD_RELIABILITY)) {
            return(reliability);
        }
        
        if (attribute.equals(FIELD_DTIME)) {
            return(new Long(dtime));
        }
        
        if (attribute.equals(FIELD_MAX_REF_COUNT)) {
            return(new Integer(maxRefCount));
        }
        
        if (attribute.equals(FIELD_REF_COUNT)) {
            return(new Integer(refCount));
        }
        
        if (attribute.equals(FIELD_DELETED_REFS)) {
            return(deletedRefs);
        }

        if (attribute.equals(FIELD_EXTENSION_MTIME)) {
            return(new Long(extensionModifiedTime));
        }

        return(null);
    }

    // The following gets are shortcuts to avoid using get(String)
    public NewObjectIdentifier getOID(){return oid;}
    public int getLayoutMapId() { return(layoutMapId); }
    public long getSize() { return(size); }
    public ObjectReliability getReliability() { return(reliability); }
    public long getFragDataLen() { return(0); }
    public NewObjectIdentifier getLink() {return link;}
    public long getCTime() {return ctime;}
    public long getDTime() {return dtime;}
    public long getRTime() {return rtime;}
    public void setRTime(long nRtime) {rtime = nRtime;}
    public long getETime() {return etime;}
    public byte getShred() {return shred;}
    public void setHashAlgorithm(String nHashAlgorithm) { objectHashAlgorithm = nHashAlgorithm; }
    public String getHashAlgorithm() { return(objectHashAlgorithm); }
    public int getRefcount() {return refCount;}
    public void setRefcount(int nRefCount) {refCount = nRefCount;}
    public int getMaxRefcount() {return maxRefCount;}
    public void setMaxRefcount(int nMaxRefCount) {maxRefCount = nMaxRefCount;}
    public BitSet getDeletedRefs() {return deletedRefs;}
    public byte[] getContentHash() {return objectContentHash;}
    public short getChecksumAlg() {return cksuAlg;}
    public short getNumPreceedingChksums() { return precedingChksums; }
    public byte[] getMetadataField() {return metadataField;}
    public long getExtensionModifiedTime() {return extensionModifiedTime;}
        
    public boolean isRestored() { return restored; }
    public void setRestored(boolean nRestored) { restored = nRestored; }

    public void setExtensionModifiedTime(long nExtensionModifiedTime) {
        extensionModifiedTime = nExtensionModifiedTime;
    }

    public String getMetadataHashString() {
        String result = ByteArrays.toHexString(metadataField);
        return(result==null ? "0" : result);
    }

    public String getContentHashString() { 
        String result = ByteArrays.toHexString(objectContentHash);
        return(result==null ? "0" : result);
    }

    public String getHashAlgorithmString() {
        if (objectHashAlgorithm != null)
            return objectHashAlgorithm;
        else
            return "sha1";
    }
   
    public String decodeContentHashAlgorithm(byte alg) {
        switch (alg) {
        case CONTENT_HASH_ALG_SHA1:
            return "sha1";
        default:
            return "not set";
        }
    }
    
    public byte encodeContentHashAlgorithm() {
        if (objectHashAlgorithm.equals("sha1")) {
            return CONTENT_HASH_ALG_SHA1;
        } else 
            return CONTENT_HASH_ALG_NONE;
    }

    // set methods

    public void setFragDataLen(long nFragDataLen) {
    }

    // Some OA specific methods
    
    
    public void seekToMetadata(RandomAccessFile raf)
        throws IOException {
        raf.seek(HEADER_LENGTH);
    }
    

    public boolean equals(Object anObject) {
        if (anObject == null) {
            return(false);
        }
        if (anObject.getClass() != getClass()) {
            return(false);
        }

        SystemMetadata other = (SystemMetadata)anObject;

        return( other.oid.equals(oid)
                && other.size == size
                && other.ctime == ctime
                && other.version == version
                && other.layoutMapId == layoutMapId
                && other.reliability.equals(reliability)
                && other.link.equals(link) );
    }
    
    public void serialize(DataOutput dout) throws IOException {
        if(dout == null) {
            throw new IOException("No DataOutput!");
        }

        dout.writeByte(version);
        boolean hasOid = (oid != null);
        dout.writeBoolean(hasOid);
        if (hasOid) {
            oid.serialize(dout);
        }
        dout.writeLong(size);
        dout.writeLong(ctime);
        dout.writeLong(rtime);
        dout.writeLong(etime);
        dout.writeLong(aCtime);
        dout.writeLong(dtime);
        dout.writeByte(shred);
        dout.writeShort(cksuAlg);
        dout.writeShort(precedingChksums);

        boolean hasMetadataField = (metadataField != null);
        dout.writeBoolean (hasMetadataField);
        if (hasMetadataField) {
            dout.writeInt(metadataField.length);
            dout.write(metadataField);
        }

        dout.writeInt(layoutMapId);

        boolean hasReliability = (reliability != null);
        dout.writeBoolean(hasReliability);
        if (hasReliability) {
            reliability.serialize(dout);
        }

        boolean hasLink = (link != null);
        dout.writeBoolean(hasLink);
        if (hasLink) {
            link.serialize(dout);
        }

        boolean hasObjectContentHash = (objectContentHash != null);
        dout.writeBoolean(hasObjectContentHash);
        if (hasObjectContentHash) {
            dout.writeInt(objectContentHash.length);
            dout.write(objectContentHash);
        }
        
        dout.writeBoolean(containsSystemAttributes);
        dout.writeBoolean(containsExtendedAttributes);
        if (objectHashAlgorithm == null) {
            // If this field is not set, throw a NullPointerException.
            objectHashAlgorithm = "not set";
        }
        dout.writeUTF(objectHashAlgorithm);

        dout.writeInt(maxRefCount);
        dout.writeInt(refCount);
     
        byte[] bitset = BitSetUtils.toByteArray(deletedRefs);
        dout.writeInt(bitset.length);
        dout.write(bitset);
        dout.writeLong(extensionModifiedTime);
        dout.writeBoolean(restored);
    }

    public void encode(Encoder encoder) {
        encoder.encodeByte(version);
        boolean hasOid = (oid != null);
        encoder.encodeBoolean(hasOid);
        if (hasOid) {
            encoder.encodeCodable(oid);
        }
        encoder.encodeLong(size);
        encoder.encodeLong(ctime);
        encoder.encodeLong(rtime);
        encoder.encodeLong(etime);
        encoder.encodeLong(aCtime);
        encoder.encodeLong(dtime);
        encoder.encodeByte(shred);

        encoder.encodeShort(cksuAlg);
        // These are not being inserted into the Syscache because of size. 
        // On restore, they will be read directly from the system metadata
        // stream
        //encoder.encodeShort(precedingChksums);
        
        //boolean hasMetadataField = (metadataField != null);
        //encoder.encodeBoolean(hasMetadataField);
        //if (hasMetadataField) {
        //    encoder.encodeBytes(metadataField);
        //}

        encoder.encodeInt(layoutMapId);
        
        boolean hasReliability = (reliability != null);
        encoder.encodeBoolean(hasReliability);
        if (hasReliability) {
            encoder.encodeCodable(reliability);
        }

        boolean hasLink = (link != null);
        encoder.encodeBoolean(hasLink);
        if (hasLink) {
            encoder.encodeCodable(link);
        }

        boolean hasObjectContentHash = (objectContentHash != null);
        encoder.encodeBoolean(hasObjectContentHash);
        if (hasObjectContentHash) {
            encoder.encodeBytes(objectContentHash);
            encoder.encodeByte(encodeContentHashAlgorithm());
        }
        
        encoder.encodeBoolean(containsSystemAttributes);
        encoder.encodeBoolean(containsExtendedAttributes);

        encoder.encodeInt(maxRefCount);
        encoder.encodeInt(refCount);
        encoder.encodePackedBitSet(deletedRefs);
        encoder.encodeLong(extensionModifiedTime);    
        encoder.encodeBoolean(restored);
    }

    public void decode(Decoder decoder) {
        version = decoder.decodeByte();
        boolean hasOid = decoder.decodeBoolean();
        if (hasOid) {
            oid = (NewObjectIdentifier)decoder.decodeCodable();
        } else {
            oid = null;
        }

        size = decoder.decodeLong();
        ctime = decoder.decodeLong();
        rtime = decoder.decodeLong();
        etime = decoder.decodeLong();
        aCtime = decoder.decodeLong();
        dtime = decoder.decodeLong();
        shred = decoder.decodeByte();
        cksuAlg = decoder.decodeShort();
        // These are not being inserted into the Syscache because of size. 
        // On restore, they will be read directly from the system metadata
        // stream
        //precedingChksums = decoder.decodeShort();
        
        //boolean hasMetadataField = decoder.decodeBoolean();
        //if (hasMetadataField) {
        //    metadataField = decoder.decodeBytes();
        //} else {
        //    metadataField = null;
        //}

        layoutMapId = decoder.decodeInt();
        
        boolean hasReliability = decoder.decodeBoolean();
        if (hasReliability) {
            reliability = (ObjectReliability)decoder.decodeCodable();
        } else {
            reliability = null;
        }

        boolean hasLink = decoder.decodeBoolean();
        if (hasLink) {
            link = (NewObjectIdentifier)decoder.decodeCodable();
        } else {
            link = null;
        }

        boolean hasObjectContentHash = decoder.decodeBoolean();
        if (hasObjectContentHash) {
            objectContentHash = decoder.decodeBytes();
            objectHashAlgorithm = decodeContentHashAlgorithm(decoder.decodeByte());
        } else {
            objectContentHash = null;
            objectHashAlgorithm = "not set"; 
        }
        
        containsSystemAttributes = decoder.decodeBoolean();
        containsExtendedAttributes = decoder.decodeBoolean();

        maxRefCount = decoder.decodeInt();
        refCount = decoder.decodeInt();
        deletedRefs = decoder.decodePackedBitSet();
        extensionModifiedTime = decoder.decodeLong();
        restored = decoder.decodeBoolean();
    }
    
    public static SystemMetadata deserialize(DataInput din) 
        throws IOException {
        if(din == null) {
            throw new IOException ("No DataInput!");
        }

        byte nVersion = din.readByte();
        boolean hasOid = din.readBoolean();
        NewObjectIdentifier nOid = null;
        if (hasOid) {
            nOid = NewObjectIdentifier.deserialize(din);
        }
        long nSize = din.readLong();
        long nCtime = din.readLong();
        long nRtime = din.readLong();
        long nEtime = din.readLong();
        long nACtime = din.readLong();
        long nDtime = din.readLong();
        byte nShred = din.readByte();
        short nCksuAlg = din.readShort();
        short nPrecedingChksums = din.readShort();

        boolean hasMetadataField = din.readBoolean();
        byte[] nMetadataField = null;
        if (hasMetadataField) {
            int metadataFieldSize = din.readInt();
            nMetadataField = new byte[metadataFieldSize];
            din.readFully(nMetadataField);
        }

        int nLayoutMapId = din.readInt();
        ObjectReliability nReliability = null;
        boolean hasReliability = din.readBoolean();
        if (hasReliability) {
            nReliability = ObjectReliability.deserialize(din);
        }

        boolean hasLink = din.readBoolean();
        NewObjectIdentifier nLink = null;
        if (hasLink) {
            nLink = NewObjectIdentifier.deserialize(din);
        }

        boolean hasObjectContentHash = din.readBoolean();
        byte[] nObjectContentHash = null;
        if (hasObjectContentHash) {
            int objectContentHashSize = din.readInt();
            nObjectContentHash = new byte[objectContentHashSize];
            din.readFully(nObjectContentHash);
        }

        
        boolean nContainsSystemAttributes =  din.readBoolean();
        boolean nContainsExtendedAttributes = din.readBoolean();

        String nObjectHashAlgorithm = din.readUTF();
        
        int nMaxRefCount = din.readInt();
        int nRefCount = din.readInt();
        
        int length = din.readInt();
        byte[] b = new byte[length]; 
        din.readFully(b);
        BitSet nDeletedRefs = BitSetUtils.fromByteArray(b);
        long nExtensionModifiedTime = din.readLong();
        
        SystemMetadata result = new SystemMetadata(nOid,
                                                   nSize,
                                                   nCtime,
                                                   nRtime,
                                                   nEtime,
                                                   nACtime,
                                                   nDtime,
                                                   nShred,
                                                   nCksuAlg,
                                                   nPrecedingChksums,
                                                   nMetadataField,
                                                   nVersion,
                                                   nLayoutMapId,
                                                   nReliability,
                                                   nLink,
                                                   nObjectContentHash,
                                                   nMaxRefCount,
                                                   nRefCount,
                                                   nDeletedRefs);
        result.setHashAlgorithm(nObjectHashAlgorithm);
   
        result.containsSystemAttributes = nContainsSystemAttributes;
        result.containsExtendedAttributes = nContainsExtendedAttributes;
        result.extensionModifiedTime = nExtensionModifiedTime;
        
        result.restored = din.readBoolean();
        
        return(result);
    }

    public String toString() {
        StringBuffer result = new StringBuffer();

        result.append(FIELD_NAMESPACE+"."+FIELD_OBJECTID+" = "+oid.toHexString());
        result.append("\n"+FIELD_NAMESPACE+"."+FIELD_CTIME+" = "+Long.toString(ctime));
        result.append("\n"+FIELD_NAMESPACE+"."+FIELD_LAYOUTMAPID+" = "+Integer.toString(layoutMapId));
        result.append("\n"+FIELD_NAMESPACE+"."+FIELD_DTIME+" = "+Long.toString(dtime));
        result.append("\n"+FIELD_NAMESPACE+"."+FIELD_RTIME+" = "+Long.toString(rtime));
        
        if (containsSystemAttributes) {
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_MAX_REF_COUNT+" = "+Integer.toString(maxRefCount));
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_REF_COUNT+" = "+Integer.toString(refCount));
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_DELETED_REFS+" = "+deletedRefs);
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_N+" = "+Integer.toString(reliability.getDataFragCount()));
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_M+" = "+Integer.toString(reliability.getRedundantFragCount()));
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_LINK+" = "+link.toString());
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_EXTENSION_MTIME+" = "+Long.toString(extensionModifiedTime));
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_RESTORED+" = "+isRestored());
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_CHKSUM_ALG+" = "+cksuAlg);
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_NUM_PREC_CHKSUMS+" = " + precedingChksums);
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_METADATA+" = " + ByteArrays.toHexString(metadataField));
        }
        if (containsExtendedAttributes) {
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_SIZE+" = "+Long.toString(size));
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_DATA_HASH+" = "+ByteArrays.toHexString(objectContentHash));
            result.append("\n"+FIELD_NAMESPACE+"."+FIELD_HASH_ALG+" = "+ objectHashAlgorithm);
        }

        return(result.toString());
    }

    /**********************************************************************
     *
     * The CacheRecord interface
     *
     **********************************************************************/

    public String getCacheID() {
        return(CacheInterface.SYSTEM_CACHE);
    }
}
