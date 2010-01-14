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



package com.sun.honeycomb.fs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.HashMap;

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.emd.cache.CacheInterface;
import java.util.logging.Logger;
import com.sun.honeycomb.common.SystemMetadata;

public class FSCacheEntry
    implements CacheRecord {

    private static final Logger LOG = Logger.getLogger(FSCacheEntry.class.getName());

    private static final String FS_FIELD_PARENT         = "parentOid";
    private static final String FS_FIELD_OID            = "oid";
    private static final String FS_FIELD_NAME           = "name";
    private static final String FS_FIELD_CTIME          = "ctime";
    private static final String FS_FIELD_MTIME          = "mtime";
    private static final String FS_FIELD_ISDIR         = "isdir";

    private NewObjectIdentifier parentOid;
    private NewObjectIdentifier oid;
    private String name;
    private long size;
    private long ctime;
    private long mtime;
    private boolean isDir;

    public FSCacheEntry() {
        parentOid = NewObjectIdentifier.NULL;
        oid = NewObjectIdentifier.NULL;
        name = null;
        size = -1;
        ctime = 0;
        mtime = 0;
        isDir = false;
    }
    
    public FSCacheEntry(NewObjectIdentifier nParentOid,
                        NewObjectIdentifier nOid,
                        String nName,
                        long nCtime,
                        boolean nIsDir) {
        parentOid = nParentOid;
        oid = nOid;
        name = nName;
        if (nIsDir) {
            size = 0;
        } else {
            size = -1;
        }
        ctime = nCtime;
        mtime = nCtime;
        isDir = nIsDir;
    }
    
    public FSCacheEntry(Map attributes) {
        parentOid = new NewObjectIdentifier((String)attributes.get(FS_FIELD_PARENT));
        oid = new NewObjectIdentifier((String)attributes.get(FS_FIELD_OID));
        name = (String)attributes.get(FS_FIELD_NAME);
        size = Long.parseLong((String)attributes.get(SystemMetadata.FIELD_SIZE));
        // Boolean.parseBoolean is available in Java 1.5 and later
        Boolean b = Boolean.valueOf((String)attributes.get(FS_FIELD_ISDIR));
        isDir = b.booleanValue();

        ctime = Long.parseLong((String)attributes.get(FS_FIELD_CTIME));
        mtime = Long.parseLong((String)attributes.get(FS_FIELD_MTIME));
    }
    
    public NewObjectIdentifier getParentOid() {
        return(parentOid);
    }

    public NewObjectIdentifier getOid() {
        return(oid);
    }

    public void setOid(NewObjectIdentifier nOid) {
        oid = nOid;
    }
    
    public String getName() {
        return(name);
    }

    public long getSize() {
        if (size == -1) {
            LOG.severe("Invalid size for "+name+" ["+oid+"]");
            throw new IllegalArgumentException("Size of "+name+" ["+
                                               oid+"] is unknown");
        }
        return(size);
    }

    public void setSize(long nSize) {
        size = nSize;
    }

    public long getCTime() {
        return(ctime);
    }

    public long getMTime() {
        return(mtime);
    }

    public void updateMTime() {
        mtime = System.currentTimeMillis();
    }

    public boolean getIsDir() {
        return(isDir);
    }
    
    public void createXML(OutputStream output) 
        throws EMDException {
        Map attributes = new HashMap();
        attributes.put(FS_FIELD_PARENT, parentOid.toString());
        attributes.put(FS_FIELD_OID, oid.toString());
        attributes.put(FS_FIELD_NAME, name);
        attributes.put(FS_FIELD_CTIME, Long.toString(ctime));
        attributes.put(FS_FIELD_MTIME, Long.toString(mtime));
        attributes.put(FS_FIELD_ISDIR, Boolean.toString(isDir));

        Exception ex = null;
        try {
            NameValueXML.createXML(attributes, output);
        } catch (IOException e) {
            ex = e;
        }

        if (ex != null) {
            EMDException newe = new EMDException("Failed to parse" +
                                                 " the metadata XML [" +
                                                 ex.getMessage() +
                                                 "]");
            newe.initCause(ex);
            throw newe;
        }
    }

    /**********************************************************************
     *
     * Codable interface
     *
     **********************************************************************/

    public void encode(Encoder encoder) {
        encoder.encodeCodable(parentOid);
        encoder.encodeCodable(oid);
        encoder.encodeString(name);
        encoder.encodeLong(size);
        encoder.encodeLong(ctime);
        encoder.encodeLong(mtime);
        encoder.encodeBoolean(isDir);
    }
    
    public void decode(Decoder decoder) {
        parentOid = (NewObjectIdentifier)decoder.decodeCodable();
        oid = (NewObjectIdentifier)decoder.decodeCodable();
        name = decoder.decodeString();
        size = decoder.decodeLong();
        ctime = decoder.decodeLong();
        mtime = decoder.decodeLong();
        isDir = decoder.decodeBoolean();
    }

    public String toString() {
        StringBuffer result = new StringBuffer("FSCacheEntry: [");
        result.append(parentOid.toString());
        result.append(" - "+oid.toString());
        result.append(" - "+name);
        result.append(" - "+size);
        result.append(" - "+ctime);
        result.append(" - "+mtime);
        result.append(" - "+isDir);
        result.append("]");
        return(result.toString());
    }
    
    /**********************************************************************
     *
     * The CacheRecord interface
     *
     **********************************************************************/
    
    public String getCacheID() {
        return(CacheInterface.FILESYSTEM_CACHE);
    }
}
