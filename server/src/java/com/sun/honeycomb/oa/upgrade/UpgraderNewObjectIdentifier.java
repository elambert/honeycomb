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

import com.sun.honeycomb.common.UID;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;

import java.util.logging.Logger;

public class UpgraderNewObjectIdentifier extends NewObjectIdentifier {
    private static Logger log
        = Logger.getLogger(UpgraderNewObjectIdentifier.class.getName());
    private boolean asOldVersion = true;

    /**********************************************************************/
    public UpgraderNewObjectIdentifier() {
        super();
    }

    /**********************************************************************/
    public void setOldVersion() {
        asOldVersion = true;
    }

    /**********************************************************************/
    public void setNewVersion() {
        asOldVersion = false;
    }

    /**********************************************************************/
    public static UpgraderNewObjectIdentifier fromFilename(String filename)
        throws UpgraderException {
        String[] splitStrs = filename.split("_", 2);
        if ((splitStrs == null) || (splitStrs.length < 2)) {
            throw new UpgraderException("Wrong format: " + filename);
        }
        UpgraderNewObjectIdentifier oid = new UpgraderNewObjectIdentifier();
        oid.legacyAdaptor.initializeFields(splitStrs[0]);
        return oid;
    }

    /**********************************************************************/
    public UpgraderNewObjectIdentifier(UID uid, int layoutMapId,
                                       byte objectType, int chunkNumber) {
        super(uid, layoutMapId, objectType, chunkNumber, (byte) 1, (short) 0);
        legacyAdaptor.adapt();
    }

    /**********************************************************************/
    public String toString() {
        if (asOldVersion) {
            return uid.toString() + "." + ruleId + ".1."
                + objectType + "." + chunkNumber + "." + layoutMapId;
        } else {
            return super.toString();
        }
    }

    /**********************************************************************/
    public void decode(Decoder decoder) {
        if (asOldVersion) {
            byte currentVersion = decoder.decodeByte();
            if (currentVersion != (byte) 0x1) {
                String msg = "Expected version 1 but got " + currentVersion;
                log.severe(msg);
		throw new IllegalStateException(msg);
            }
            short cellId = decoder.decodeShort(); // ignore
            uid = new UID(decoder.decodeKnownLengthBytes(UID.NUMBYTES));
            layoutMapId = decoder.decodeInt();
            objectType = decoder.decodeByte();
            chunkNumber = decoder.decodeInt();
            legacyAdaptor.adapt();
        } else {
            super.decode(decoder);
        }
    }

    /**********************************************************************/
    public void encode(Encoder encoder) {
        if (asOldVersion) {
            encoder.encodeByte((byte) 0x1);
            encoder.encodeShort((short) 1);
            encoder.encodeKnownLengthBytes(uid.getBytes());
            encoder.encodeInt(layoutMapId);
            encoder.encodeByte(objectType);
            encoder.encodeInt(chunkNumber);
        } else {
            super.encode(encoder);
        }
    }
}
