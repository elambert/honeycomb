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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.test.util.Log;

import com.sun.honeycomb.common.TestRequestParameters;

import java.util.LinkedList;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestableReadChannel implements ReadableByteChannel 
{
    private ReadableByteChannel source;
    private MessageDigest md;
    private long numBytes;
    private byte [] digest;
    private boolean calc_digest;
    private long digest_time = 0;

    protected long checkBytes;
    private boolean listed, done;

    private static LinkedList list = new LinkedList();

    {
        synchronized (list) {
            list.add(this);
        }
        ChannelMonitor.init();
    }

    public DigestableReadChannel()
    {
        this.source = null;
        this.init();
    }

    public DigestableReadChannel(ReadableByteChannel source)
    {
        this.source = source;
        this.init();
    }

    private void init()
    {
        this.calc_digest = TestRequestParameters.getCalcHash();
        if (calc_digest) {
            try {
                this.md = MessageDigest.getInstance("SHA-1");
            }
            catch (NoSuchAlgorithmException e) {
                // we assume that SHA-1 is ok.
            }
        }
        this.numBytes = 0;
        this.digest = null;

        this.done = false;
        this.checkBytes = 0;
    }

    static protected LinkedList getList() {
        return list;
    }

    public int read(ByteBuffer buf)
        throws IOException
    {
        int numRead = 0;

        if (this.source == null) {
            if (calc_digest) {
                long t1 = System.currentTimeMillis();
                while (buf.hasRemaining()) {
                    this.md.update(buf.get());
                    numRead++;
                }
                digest_time += System.currentTimeMillis() - t1;
            } else {
                while (buf.hasRemaining()) {
                    buf.get();
                    numRead++;
                }
            }
        }
        else {
            int pos = buf.position();
            numRead = this.source.read(buf);
            if (calc_digest) {
                long t1 = System.currentTimeMillis();
                for (int i = 0; i < numRead; i++) {
                    this.md.update(buf.get(pos+i));
                }
                digest_time += System.currentTimeMillis() - t1;
            }
        }

        if (numRead > 0) {
            this.numBytes += numRead;
            this.checkBytes += numRead;
        } else if (numRead == -1) {
            this.done = true;
        }

        return numRead;
    }

    public void close()
        throws IOException
    {
        this.source.close();
        done = true;
    }


    public boolean isOpen()
    {
        if (this.source == null)
            return false;
        return this.source.isOpen();
    }

    public byte [] digest()
    {
        if (calc_digest)
            return 
                this.digest == null ?
                (this.digest = this.md.digest()) :
                this.digest;
        return new byte[0];
    }

    /**
     *  Warning - when source is socket, digest time includes
     *  time spent moving bytes.
     */
    public long getDigestTime() {
        return digest_time;
    }

    public long getNumBytes()
    {
        return this.numBytes;
    }

    public long getNumCheckBytes()
    {
        long ret = this.checkBytes;
        if (this.done  &&  this.listed) {
            synchronized (list) {
                list.remove(this);
            }
            this.listed = false;
        }
        this.checkBytes = 0;

        return ret;
    }

    public static void main(String [] argv) 
        throws Throwable
    {
        FileInputStream fis = new FileInputStream(argv[0]);
        FileChannel fc = fis.getChannel();
        DigestableReadChannel drc = new DigestableReadChannel(fc);
        ByteBuffer buf = ByteBuffer.allocateDirect(64*1024);
        long t1 = System.currentTimeMillis();
        int numRead = drc.read(buf);
        while (numRead > 0) {
            buf.clear();
            numRead = drc.read(buf);
        }
        System.out.println("time:      " + (System.currentTimeMillis() - t1));
        System.out.println("hash time: " + drc.getDigestTime());
        System.out.println("bytes: " + drc.getNumBytes());
        System.out.println(convertHashBytesToString(drc.digest()));
    }

    private static String convertHashBytesToString(byte[] rawHash) {
        StringBuffer sbHash = new StringBuffer();
        for(int i = 0; i < rawHash.length; i++) {
            sbHash.append(Character.forDigit((rawHash[i] & 0xF0) >> 4, 16));
            sbHash.append(Character.forDigit(rawHash[i] & 0x0F, 16));
        }
        return (sbHash.toString());
    }
}
