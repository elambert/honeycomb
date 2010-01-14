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



package com.sun.honeycomb.test.util;

import java.io.File;

/**
 *  Holder for a file, used by FileCache. Files are created with
 *  size/binary characteristics. SHA1 is calculated if desired.
 */
public class FileHandle {

    public File f = null;
    public boolean binary;
    public long size;
    private String sha = null;

    private static RunCommand shell = new RunCommand();

    public FileHandle(long size, boolean binary) throws HoneycombTestException {
        this.f = f;
        this.binary = binary;
        this.size = size;
        if (binary)
            f = FileUtil.createRandomByteFile(size);
        else
            f = FileUtil.createRandomCharFile(size);
    }

    /**
     *  Revamp file with added/overwritten bytes to make it
     *  unique and of the right size, and reset stored SHA1.
     */
    void recycle(long size) throws HoneycombTestException {
        if (size < this.size) {
            throw new HoneycombTestException("can't recycle down in size");
        } else if (size == this.size) {
            //
            //  change bytes
            //
            f = FileUtil.changeBytes(f, binary);
        } else {
            //
            //  extend
            //
            if (binary)
                f = FileUtil.extendBinaryFile(f, this.size, size);
            else
                f = FileUtil.extendCharFile(f, this.size, size);
            this.size = size;
        }

        //
        //  reset sha
        //
        sha = null;
    }

    /**
     *  Calculate SHA1 hash and save it in case it is needed again.
     */
    public String getSHA() throws HoneycombTestException {
        if (sha == null)
            sha = shell.sha1sum(f.getAbsolutePath());
        return sha;
    }
}
