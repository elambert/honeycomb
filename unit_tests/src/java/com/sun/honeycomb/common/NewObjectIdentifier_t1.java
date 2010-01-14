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

import com.sun.honeycomb.test.Testcase;

import java.util.logging.Logger;

public class NewObjectIdentifier_t1 extends Testcase {
    private static Logger log
        = Logger.getLogger(NewObjectIdentifier_t1.class.getName());

   /**********************************************************************/
    public NewObjectIdentifier_t1(String name) {
        super(name);
    }

   /**********************************************************************/
    public void testLegacyCheck() {
        try {
            NewObjectIdentifier.fromLegacyHexString(null);
            fail("null string");
        } catch (IllegalArgumentException expected) {
        }
        try {
            NewObjectIdentifier.fromLegacyHexString("0123ff2348aa");
            fail("invalid string");
        } catch (IllegalArgumentException expected) {
        }
        try {
            String version2
                = "020001cfcfa32d8fb711db8ce5cafecafecafe000000000200000000";
            NewObjectIdentifier.fromLegacyHexString(version2);
            fail("invalid version");
        } catch (IllegalArgumentException expected) {
        }
    }

   /**********************************************************************/
    public void testLegacyCompatibility() {
        String oidString
            = "010001cfcfa32d8fb711db8ce5cafecafecafe000000000200000000";
        NewObjectIdentifier oid
            = NewObjectIdentifier.fromLegacyHexString(oidString);
        //oid is cfcfa32d-8fb7-11db-8ce5-cafecafecafe.1.1.2.0.0
        assertEquals("uid", new UID("cfcfa32d-8fb7-11db-8ce5-cafecafecafe"),
                     oid.getUID());
        assertEquals("version", (byte) 2, oid.getVersion());
        assertEquals("layout id", 0, oid.getLayoutMapId());
        assertEquals("object type", 2, oid.getObjectType());
        assertEquals("chunk number", 0, oid.getChunkNumber());
        assertEquals("rule id", (byte) 1, oid.getRuleId());
        assertEquals("representation", (byte) 1, oid.getRepresentation());
        assertEquals("silo location", 14128, oid.getSilolocation());

        NewObjectIdentifier oid2
            = NewObjectIdentifier.fromLegacyHexString(oidString);
        assertEquals("oids", oid, oid2);

        String encoded = oid.toLegacyHexString();
        assertEquals("oid string", oidString, encoded);
    }
}
