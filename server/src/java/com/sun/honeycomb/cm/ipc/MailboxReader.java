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



package com.sun.honeycomb.cm.ipc;

import java.io.IOException;
import java.io.InputStream;

public final class MailboxReader extends InputStream {

    static {
        System.loadLibrary("jmbox");
    }

    private final String tag;
    private final int lid;
    private int uid;

    // copy of this mailbox in the JVM address space
    // Guarantee mailbox read atomiticity
    private byte[] copy;
    private int pos;
    private int mark;


    public MailboxReader(String tag) throws IOException {
        this.tag = tag;
        lid  = init(tag);
        mark = 0;
        pos  = 0;
        try {
            copy = new byte[size(lid)];
            uid  = read(lid, copy, 0, copy.length);
        } catch (Exception e) {
            close();
            throw new IOException("failed to init " + tag + " got " + e);
        }
    }

    public boolean isUpToDate() throws IOException {
        return (uid == version(lid));
    }
     
    public boolean isDisabled() {
        return isDisabled(lid);
    }
    
    public synchronized void update() throws IOException {
        mark = 0;
        pos  = 0;
        copy = new byte[size(lid)];
        uid  = read(lid, copy, 0, copy.length);
    }

    /*
     * Input stream
     */

    public synchronized int read() {
        return (pos < copy.length) ? (copy[pos++] & 0xff) : -1;
    }

    public synchronized int read(byte[] b, int off, int len) {
	if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0)) {
	    throw new IndexOutOfBoundsException();
	}
	if (pos >= copy.length) {
	    return -1;
	}
	if (pos + len > copy.length) {
	    len = copy.length - pos;
	}
	if (len <= 0) {
	    return 0;
	}
	System.arraycopy(copy, pos, b, off, len);
	pos += len;
	return len;
    }

    public synchronized long skip(long n) {
        if (pos + n > copy.length) {
            n = copy.length - pos;
        }
        if (n < 0) {
            return 0;
        }
        pos += n;
        return n;
    }

    public synchronized int available() {
        return copy.length - pos;
    }

    public boolean markSupported() {
        return true;
    }

    public void mark(int reada) {
        mark = pos;
    }

    public void reset() {
        pos = mark;
    }

    public void close() {
        close(lid);
    }

    private native int  init(String tag);
    private native int  read(int lid, byte[] b, int off, int len) throws IOException;
    private native int  size(int lid);
    private native void close(int lid);
    private native int  version(int lid) throws IOException; 
    private native boolean isDisabled(int lid);
}
