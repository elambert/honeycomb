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



package com.sun.honeycomb.delete.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.sun.honeycomb.delete.Constants;
import com.sun.honeycomb.common.ByteArrays;

/**
 * Misc Utilities
 */
public class Util {
    /**
     *  Calc hash on file using java (rather than shell to sha1sum).
     *  XXX copied from hctest HCUtil.java
     */
    public static String computeHash(String filename)
                                                throws RuntimeException {
        String hashAlg = Constants.CURRENT_HASH_ALG;
        String computedHash = null;

        try {

            MessageDigest md = MessageDigest.getInstance(hashAlg);

            File f = new File(filename);

            long len = f.length();

            // 0 byte file...
            if (len == 0)
                return ByteArrays.toHexString(md.digest());

            byte[] buf;
            if (f.length() > Constants.MAX_ALLOCATE)
                buf = new byte[Constants.MAX_ALLOCATE];
            else
                buf = new byte[(int)len];

            FileInputStream in = new FileInputStream(f);

            int bytesRead;

            while ((bytesRead = in.read(buf)) != -1) {
                md.update(buf, 0, bytesRead);
            }
            in.close();

            computedHash = ByteArrays.toHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Couldn't compute " + hashAlg +
                ":  No such algorithm");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't compute " + hashAlg +
                ": " + e.getMessage());
        }

        return computedHash;
    }
}
