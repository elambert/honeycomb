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

import java.util.logging.Logger;
import java.util.logging.Level;

import java.security.MessageDigest;

/**
 * This is the hashing algorithm described in RFC 2617, HTTP
 * Authentication. This is H(A1), which is what we store -- not the
 * cleartext password as jetty would have us do. It is used by
 * the CLI when the admin changes the compliance password, and by
 * auth.HCUserRealm.
 */
public class HttpPassword {
    private static final Logger logger =
        Logger.getLogger(HttpPassword.class.getName());

    // Calculate H(A1)
    public static byte[] makeHash(String realm, String user, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            md.reset();
            md.update(user.getBytes("ISO-8859-1"));
            md.update((byte)':');
            md.update(realm.getBytes("ISO-8859-1"));
            md.update((byte)':');
            md.update(password.getBytes("ISO-8859-1"));
            byte[] hash = md.digest();

            logger.info("H(\"" + user + ":" + realm + ":" + password +
                        "\") = 0x" + ByteArrays.toHexString(hash));

            return hash;
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't make passwd hash", e);
        }

        return null;
    }
}
