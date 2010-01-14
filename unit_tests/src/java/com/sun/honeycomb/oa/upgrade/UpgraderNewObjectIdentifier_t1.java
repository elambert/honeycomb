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

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.test.Testcase;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;

import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.List;
import java.nio.ByteBuffer;

public class UpgraderNewObjectIdentifier_t1 extends Testcase {
    private static Logger log
        = Logger.getLogger(UpgraderNewObjectIdentifier_t1.class.getName());
    /**********************************************************************/
    public UpgraderNewObjectIdentifier_t1(String name) {
        super(name);
    }

    /**********************************************************************/
    public void testFilename() throws Exception {
        String name = "140edcd4-360b-11db-a0ae-00e0815959cd.1.1.1.0.9_3";
        UpgraderNewObjectIdentifier oid
            = UpgraderNewObjectIdentifier.fromFilename(name);
        assertEquals("representation", 1, oid.getRepresentation());
        assertEquals("layoutMapId", 9, oid.getLayoutMapId());
        assertEquals("object type", 1, oid.getObjectType());
        assertEquals("chunk number", 0, oid.getChunkNumber());
        assertEquals("rule id", 1, oid.getRuleId());
        assertEquals("version", 2, oid.getVersion());
        assertFalse("silolocation", 0 == oid.getSilolocation());

        String name2 = "1e383920-360c-11db-a3e4-00e0815970a1.1.1.2.0.9_3";
        UpgraderNewObjectIdentifier oid2
            = UpgraderNewObjectIdentifier.fromFilename(name2);
        assertEquals("object type", 2, oid2.getObjectType());
        assertTrue("silolocation", 0 != oid2.getSilolocation());
        assertTrue("silolocation",
                    oid.getSilolocation() != oid2.getSilolocation());

        String name3 = "12313-assdfasdf-11db-a3e4";
        try {
            UpgraderNewObjectIdentifier.fromFilename(name3);
            fail("Incorrect filename format");
        } catch (UpgraderException expected) {
        }
    }

    /**********************************************************************/
    private void checkStringFormat(String str, int expected) throws Exception {
        String[] splitStrs = str.split("\\.");
        if ((splitStrs == null) || (splitStrs.length != expected)) {
            fail("wrong format");
        }
    }

    /**********************************************************************/
    public void testStringFormat() throws Exception {
        String name = "140edcd4-360b-11db-a0ae-00e0815959cd.1.1.1.0.9_3";
        UpgraderNewObjectIdentifier oid
            = UpgraderNewObjectIdentifier.fromFilename(name);
        checkStringFormat(oid.toString(), 6);
        oid.setNewVersion();

        String newStr = oid.toString();
        log.info(newStr);
        checkStringFormat(newStr, 8);
    }

    /**********************************************************************/
    private ByteBuffer encodeOid(boolean asNewVersion) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        Encoder coder = new ByteBufferCoder(buffer, false);
        String name = "140edcd4-360b-11db-a0ae-00e0815959cd.1.1.1.0.9_3";
        UpgraderNewObjectIdentifier oid
            = UpgraderNewObjectIdentifier.fromFilename(name);
        if (asNewVersion) {
            oid.setNewVersion();
        }
        coder.encodeKnownClassCodable(oid);
        buffer.flip();
        return buffer;
    }

    /**********************************************************************/
    public void testEncodeFail() throws Exception {
        ByteBuffer buffer = encodeOid(true);
        try {
            ByteBufferCoder decoder = new ByteBufferCoder(buffer);
            UpgraderNewObjectIdentifier decodedOid
                = new UpgraderNewObjectIdentifier();
            decoder.decodeKnownClassCodable(decodedOid);
            fail("decoded new format");
        } catch (IllegalStateException expected) {
        }
    }

    /**********************************************************************/
    public void testEncode() throws Exception {
        ByteBuffer buffer = encodeOid(false);
        ByteBufferCoder decoder = new ByteBufferCoder(buffer);
        UpgraderNewObjectIdentifier decodedOid
            = new UpgraderNewObjectIdentifier();
        decoder.decodeKnownClassCodable(decodedOid);
    }
}
