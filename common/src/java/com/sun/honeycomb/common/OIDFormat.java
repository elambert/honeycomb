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

import com.sun.honeycomb.common.ByteArrays;

import java.io.UnsupportedEncodingException;
import java.text.StringCharacterIterator;

public class OIDFormat {

    // format definitions copied from NewObjectIdentifier class

    public static final int UID_NUMBYTES = 16; // copied from UID class!
    public static final int OID_LENGTH = 1 + 1 + 2 + 1 + UID_NUMBYTES + 4 + 1 + 4;
    public static final int  UNDEFINED = -1;
    
    private static final int VERSION_OFFSET = 0;
    private static final int REPRESENTATION_OFFSET = VERSION_OFFSET + 1;
    private static final int RULEID_OFFSET = REPRESENTATION_OFFSET + 1;
    private static final int SILOLOCATION_OFFSET = RULEID_OFFSET + 1;
    private static final int UID_OFFSET = SILOLOCATION_OFFSET + 2;
    private static final int LAYOUT_OFFSET = UID_OFFSET + UID_NUMBYTES;
    private static final int TYPE_OFFSET = LAYOUT_OFFSET + 4;
    private static final int CHUNK_OFFSET = TYPE_OFFSET + 1;

    private static String HEX = "0123456789abcdef";

    private static byte INTERNAL_REP = 1;
    private static byte EXTERNAL_REP = 0;

    private static byte RULE_ID_ONE = 1; // valid until we have multicell sloshing
    private static byte CELL_ID_ZERO = 0;

    // OID components

    protected byte[] uid = null;
    protected int layoutMapId = UNDEFINED;
    protected byte objectType = UNDEFINED;
    protected int chunkNumber = UNDEFINED;
    protected byte representation = UNDEFINED; 
    protected byte ruleId = UNDEFINED;
    protected byte cellId = UNDEFINED;
    protected short silolocation = UNDEFINED;
    protected byte version = UNDEFINED;

    public OIDFormat(byte[] uid,
                     int layoutMapId,
                     byte objectType,
                     int chunkNumber,
                     byte representation,
                     byte ruleId,
                     short silolocation,
                     byte version) {
        this.uid = uid;
        this.layoutMapId = layoutMapId;
        this.objectType = objectType;
        this.chunkNumber = chunkNumber;
        this.representation = representation;
        this.ruleId = ruleId;
        this.cellId = UNDEFINED;
        this.silolocation = silolocation;
        this.version = version;
    }


    public OIDFormat(String string) {
        initFromString(string);
    }

    public OIDFormat(String string, boolean hex) {
        if (hex) {
            initFromHexString(string);
        } else {
            initFromString(string);
        }
    }

    public OIDFormat(byte[] bytes) {
        initFromBytes(bytes);
    }

    // initializer methods convert to/from strings and byte arrays

    // input is the internal OID representation
    //
    private void initFromString(String string) {
        if (string == null) {
            throw new IllegalArgumentException("OID string cannot be null");
        }
        String[] fields = string.split("\\.");
        if (fields.length != 8) {
            throw new IllegalArgumentException("String is not of OID format: "
                                               + string);
        }
        uid = convertUid(fields[0]);
        representation = Byte.parseByte(fields[1]);
        ruleId = Byte.parseByte(fields[2]);
        cellId = UNDEFINED;
        silolocation = Short.parseShort(fields[3]);
        version = Byte.parseByte(fields[4]);
        objectType = Byte.parseByte(fields[5]);
        chunkNumber = Integer.parseInt(fields[6]);
        layoutMapId = Integer.parseInt(fields[7]);
    }

    private void initFromHexString(String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException("hexString cannot be null");
        }

        initFromBytes(ByteArrays.toByteArray(hexString));
    }

    // input is the external OID representation
    //
    private void initFromBytes(byte[] bytes) {
        if (bytes.length != OID_LENGTH) {
            throw new IllegalArgumentException("OID byte array is of length " +
                                               bytes.length + " not " + OID_LENGTH);
        }
        version = bytes[VERSION_OFFSET];
        representation = bytes[REPRESENTATION_OFFSET];
        cellId = bytes[RULEID_OFFSET];
        ruleId = cell2rule(cellId);
        silolocation = ByteArrays.getShort(bytes, SILOLOCATION_OFFSET);
        uid = ByteArrays.copy(bytes, UID_OFFSET, UID_NUMBYTES);
        layoutMapId = ByteArrays.getInt(bytes, LAYOUT_OFFSET);
        objectType = bytes[TYPE_OFFSET];
        chunkNumber = ByteArrays.getInt(bytes, CHUNK_OFFSET);
    }

    // output is the external OID representation
    //
    public static byte[] getDataBytes(byte version,
                                      byte representation,
                                      byte cellId,
                                      short silolocation,
                                      byte[] uid,
                                      int layoutMapId,
                                      byte objectType,
                                      int chunkNumber) {
        
        byte[] result = new byte[OID_LENGTH];

        result[VERSION_OFFSET] = version;
        result[REPRESENTATION_OFFSET] = EXTERNAL_REP;
        result[RULEID_OFFSET] = cellId;
        ByteArrays.putShort(silolocation, result, SILOLOCATION_OFFSET);
        System.arraycopy(uid, 0, result, UID_OFFSET, UID_NUMBYTES);
        ByteArrays.putInt(layoutMapId, result, LAYOUT_OFFSET);
        result[TYPE_OFFSET] = objectType;
        ByteArrays.putInt(chunkNumber, result, CHUNK_OFFSET);

        return result;
    }

    // output is the external OID representation
    //
    private byte[] getDataBytes() {
        return getDataBytes(version, representation, rule2cell(ruleId),
                            silolocation, uid, layoutMapId, 
                            objectType, chunkNumber);
    }


    // convert rule ID (internal) to cell ID (external)
    //
    private byte rule2cell(byte ruleId) {
        if (cellId != UNDEFINED) {
            return cellId;
        } else {
            return CELL_ID_ZERO; // default
        }
    }

    // convert cell ID (external) to rule ID (internal)
    //
    private static byte cell2rule(byte cellId) {
        // HACK: today the only rule ID in use is rule 1.
        return RULE_ID_ONE; 
    }

    public void setCellId(byte cellId) {
        this.cellId = cellId;
    }

    // convert UID from byte array to dash-separated hex,
    // analogous to the way doomdark library does it
    // (we don't want to link to doomdark from here to 
    // avoid cross-platform issues with native code)

    private String convertUid(byte[] uid){
        if (uid.length != UID_NUMBYTES) {
            throw new IllegalArgumentException("UID byte array is of length " +
                                               uid.length + " not " + UID_NUMBYTES);
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < uid.length; i++){
            int c = ((uid[i] >> 4) & 0xf);
            sb.append(HEX.charAt(c));
            c = (uid[i] & 0xf);
            sb.append(HEX.charAt(c));
            if (i == 3 || i == 5 || i == 7 || i == 9)
                sb.append('-');
        }
        return sb.toString();
    }

    // convert in opposite direction, removing the dashes

    private int char_to_int(char x) {
        if ((x >= 'a') && (x <= 'f')) {
            return (x - 'a' + 10);
        } else if ((x >= '0') && (x <= '9')) {
            return (x - '0');
        } else {
            throw new IllegalArgumentException("Not a hex char: " + x);
        }
    }

    private byte[] convertUid(String uid) {
        byte[] bytes = new byte[UID_NUMBYTES];
        StringCharacterIterator it = new StringCharacterIterator(uid);
        int i = 0;
        int c_int = 0;
        byte val = 0;
        for (char c = it.first(); c != StringCharacterIterator.DONE; ) {
            if (c == '-') {
                c = it.next();
                continue;
            }
            val = 0;
            c_int = char_to_int(c);
            val |= ((c_int << 4) & 0xF0);
            c = it.next();
            c_int = char_to_int(c);
            val |= (c_int & 0xf);
            c = it.next();
            bytes[i++] = val;
        }
        return bytes;
    }


    // print in human-readable dot notation (internal representation)

    public String toString() {

        return convertUid(uid) + "." +
            INTERNAL_REP + "." +
            ruleId + "." +
            silolocation  + "." +
            version + "." +
            objectType + "." +
            chunkNumber + "." +
            layoutMapId;
    }

    // print as hex

    public String toHexString() {
        return ByteArrays.toHexString(getDataBytes());
    }

    // XXX: Do not provide access to representation and ruleId,
    // they are WRONG and unavailable outside of multicell.
    // Feel free to access other fields. 

    public int getLayoutMapId() {
        return layoutMapId;
    }
    
    public String getUID() {
        return convertUid(uid);
    }

    // given this oid (assuming it's for chunk zero) and
    // a chunk number, produce the oid for that chunk
    //
    public OIDFormat getOidForChunk(int newChunkNumber) {
        return new OIDFormat(uid,
                             layoutMapId+newChunkNumber,
                             objectType,
                             newChunkNumber,
                             INTERNAL_REP,
                             ruleId,
                             silolocation,
                             version);
    }
}

