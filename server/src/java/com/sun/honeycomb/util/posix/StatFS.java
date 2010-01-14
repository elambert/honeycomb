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



package com.sun.honeycomb.util.posix;
import java.util.logging.Logger;

/**
 * Class for getting filesystem information or implementing
 * missing posix filesystem operations.
 */
public final class StatFS {

    protected static final Logger LOG =
        Logger.getLogger(StatFS.class.getName());

    static {
        try {
            System.loadLibrary("statfs");
            LOG.info("statfs jni library loaded");
        } catch(UnsatisfiedLinkError ule) {
            LOG.severe("Check LD_LIBRARY_PATH. Can't find " +
                       System.mapLibraryName("statfs") + " in " +
                       System.getProperty("java.library.path"));

        }
    }

    /** Strcture containing FS information */
    public static class Struct {

        public static final long MB = (1024 * 1024);
        public long f_type;
        public long f_bsize;
        public long f_blocks;
        public long f_bfree;
        public long f_bavail;
        public long f_files;
        public long f_ffree;
        public long f_namelen;
        public long[] f_spare; // NOT CURRENTLY USED //

        /** Inits structure and creates f_spare array */
        public void Struct() {
            f_type = f_bsize = f_blocks = f_bfree = f_bavail = f_files =
                f_namelen = -1;
            f_spare = null; // NOT CURRENTLY USED //
        }

        public int totalMBytes() {
            return (int) ((f_blocks * f_bsize) / MB);
        }

        public int availMBytes() {
            return (int) ((f_bavail * f_bsize) / MB);
        }
    }

    public static Struct statfs64(String path)
        throws ClassNotFoundException, IllegalArgumentException {
        Struct struct = new Struct();
        statfs64(path, struct);
        return struct;
    }

    private static native void statfs64(String path,
                                        Struct struct)
        throws IllegalArgumentException, ClassNotFoundException;

    /**********************************************************************
     * Return inode number of a file.
     */
    public static native long getInodeNumber(String file);

    /**********************************************************************
     * Return time of last file status change.
     */
    public static native long getCTime(String file);
    
    /**********************************************************************
     * Create a symbolic link - force semantic (ln -sf)
     * return the errno code or 0 in case of success
     */
    public static native int createSymLink(String target, String link);
}
