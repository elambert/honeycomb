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



package com.sun.honeycomb.archivers;

import java.io.OutputStream;
import java.io.FileDescriptor;
import java.io.IOException;

// For debug and unit test only, for a util function String prMode(long)
import com.sun.honeycomb.webdav.FileProperties;

/**
 * This is the stub interface for the libarchive JNI
 *
 * There are three open methods: one that takes a filename, one
 * for already open files, and another that returns a writeable
 * stream that the archive should be written to by the library.
 * Obviously only one of them should be used.
 *
 * <b>Reading the archive from a stream instead of the filesystem</b>
 *
 * If the OutputStream way is chosen, the caller needs to make
 * sure that no operation that requires a rewind will be performed
 * -- only one pass through the file, so nextHeader() and
 * getContents() are interleaved, and if multiple getContents()
 * are required for a file, they are properly sequential, with
 * offsetBytes set to the current offset in the stream.  for a
 * file proceeds in order.
 *
 * Caveat: when nextHeader(), getContents() etc. are called, it
 * may block to read from the stream -- so the writer better not
 * be in the same thread.
 * <pre>
 *     HCXArchive ar = new ...;
 *     OutputStream os = ar.open();
 *
 *     Thread writer = spawnWriterThread(os);
 *
 *     Stat hdr;
 *     while ((hdr = t.nextHeader()) != null) {
 *
 *         t.getContents(...)
 *         ...
 *     }
 *
 *     t.close();
 *     writer.join();
 * </pre>
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */

public interface HCXArchive {

    // de facto standard values

    public static final int MODEMASK_TYPEM  = 0xf000; // 0170000
    public static final int MODEMASK_IFDIR  = 0x4000; //  040000
    public static final int MODEMASK_IFCHR  = 0x2000;
    public static final int MODEMASK_IFIFO  = 0x1000;
    public static final int MODEMASK_IFBLK  = 0x6000;
    public static final int MODEMASK_IFREG  = 0x8000;
    public static final int MODEMASK_IFLNK  = 0xA000;
    public static final int MODEMASK_IFSOCK = 0xC000;

    public static final int MODEMASK_SUID   = 04000;
    public static final int MODEMASK_SGID   = 02000;
    public static final int MODEMASK_STICKY = 01000;

    public static final int MODEMASK_IAMB   =  0777; // rwxrwxrwx

    public static final int MODEMASK_RWXU   =  0700;
    public static final int MODEMASK_RUSR   =  0400;
    public static final int MODEMASK_WUSR   =  0200;
    public static final int MODEMASK_XUSR   =  0100;

    public static final int MODEMASK_RWXG   =   070;
    public static final int MODEMASK_RGRP   =   040;
    public static final int MODEMASK_WGRP   =   020;
    public static final int MODEMASK_XGRP   =   010;

    public static final int MODEMASK_RWXO   =    07;
    public static final int MODEMASK_ROTH   =    04;
    public static final int MODEMASK_WOTH   =    02;
    public static final int MODEMASK_XOTH   =    01;

    public OutputStream open()
        throws IOException;
    public void open(String filename)
        throws IOException;
    public void open(FileDescriptor fd)
        throws IOException;

    public void close()
        throws IOException;

    // Archive query methods

    public boolean isCompressed()
        throws IOException;

    // Archive reading methods

    public Stat nextHeader()
        throws IOException;

    public byte[] getContents(long fileIndex,
                              long offsetBytes, int lengthBytes)
        throws IOException;

    // File metadata object

    public class Stat {
        private String name;
        private long size;
        private long index;
        private long uid;
        private long gid;
        private long mtime;
        private long atime;
        private long mode;

        public Stat() {
            this("", 0, 0, 0, 0, 0, 0, 0);
        }

        public Stat(String name, long size, long index, long uid, long gid,
                    long mtime, long atime, long mode) {
            this.name = name;
            this.size = size;
            this.index = index;
            this.uid = uid;
            this.gid = gid;
            this.mtime = mtime;
            this.atime = atime;
            this.mode = mode;
        }

        public void setName(String name) { this.name = name; }

        public String name() { return name; }
        public long size() { return size; }
        public long index() { return index; }
        public long uid() { return uid; }
        public long gid() { return gid; }
        public long mtime() { return mtime; }
        public long atime() { return atime; }
        public long mode() { return mode; }
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(name).append('{');
            sb.append(" size:").append(size());
            sb.append(" index:").append(index());
            sb.append(" uid:").append(uid());
            sb.append(" gid:").append(gid());
            sb.append(" mtime:").append(mtime());
            sb.append(" atime:").append(atime());
            sb.append(" mode:").append(FileProperties.prMode(mode()));
            return sb.append(' ').append('}').toString();
        }
    }
}
