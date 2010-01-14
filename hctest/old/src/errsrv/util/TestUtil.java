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



package com.sun.honeycomb.errsrv.util;

import java.security.*;
import org.doomdark.uuid.*;

public class TestUtil {

    // Get the uid from the oid
    public static String getUid(String oid) {
        return oid.substring(0, oid.indexOf('.'));
    }

    // Calculate the SHA1 id of the given string
    // SHA1 calculation
    private static final int HEX_RADIX = 16;
    private static final int BITS_PER_NIBBLE = 4;
    public static String getUidSHA1(String message) throws Exception {
                                                                                
        String computedSHA1 = null;
                                                                                
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
                                                                                
            // convert the bytes to a string.
            // XXX This code was copied from OA.
            UUID uuid = new UUID(message);
            byte[] rawSHA1 = md.digest(uuid.toByteArray());
            StringBuffer sbSHA1 = new StringBuffer();
                                                                                
            // Maybe we could replace this with a call to new
            // String(byte[])?
            for(int i = 0; i < rawSHA1.length; i++) {
                sbSHA1.append(Character.forDigit((rawSHA1[i] & 0xF0) >> BITS_PER_NIBBLE, HEX_RADIX));
                sbSHA1.append(Character.forDigit(rawSHA1[i] & 0x0F, HEX_RADIX));            }
            computedSHA1 = sbSHA1.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new Exception(e);
        }
                                                                                
        return (computedSHA1);
    }

    public static void main(String args[]) {
        try {
            System.out.println("Hash: " + getUidSHA1(getUid(args[0])));
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
