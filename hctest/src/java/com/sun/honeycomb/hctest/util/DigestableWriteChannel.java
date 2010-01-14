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

import com.sun.honeycomb.common.TestRequestParameters;

import java.util.LinkedList;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestableWriteChannel implements WritableByteChannel 
{
    private WritableByteChannel dest;
    private MessageDigest md;
    private long numBytes;
    private byte [] digest;
    private boolean calc_digest;
    private long digest_time = 0;
    //private long write_time = 0;
    private byte[] tmp_buf = null;
    private final static int TMP_BUF_SIZE = 1024 * 1024;

    protected long checkBytes;
    private boolean listed, done;

    private static LinkedList list = new LinkedList();

    {
        synchronized (list) {
            list.add(this);
        }
        ChannelMonitor.init();
    }

    public DigestableWriteChannel()
    {
        this.dest = null;
        init(true);
    }
    public DigestableWriteChannel(boolean calcDigest)
    {
        this.dest = null;
        init(calcDigest);
    }

    public DigestableWriteChannel(WritableByteChannel dest)
    {
        this.dest = dest;
        init(true);
    }
    public DigestableWriteChannel(WritableByteChannel dest, boolean calcDigest)
    {
        this.dest = dest;
        init(calcDigest);
    }

    private void init(boolean calcDigest)
    {
        this.calc_digest = calcDigest & TestRequestParameters.getCalcHash();
        if (calc_digest) {
            try {
                this.md = MessageDigest.getInstance("SHA-1");
            }
            catch (NoSuchAlgorithmException e) {
                // we assume that SHA-1 is ok.
            }
            if (dest == null)
                tmp_buf = new byte[TMP_BUF_SIZE];
        }
        this.numBytes = 0;
        this.digest = null;

        this.checkBytes = 0;
        this.done = false;
    }

    static protected LinkedList getList() {
        return list;
    }

    public int write(ByteBuffer buf)
        throws IOException
    {
        int numWritten = 0;
        
        if (this.dest == null) {
            if (calc_digest) {
                while (buf.hasRemaining()) {
                    int count = 0;
                    for (int i=0; i<TMP_BUF_SIZE; i++) {
                        if (!buf.hasRemaining())
                            break;
                        tmp_buf[count++] = buf.get();
                        numWritten++;
                    }
                    long t1 = System.currentTimeMillis();
                    for (int i=0; i<count; i++)
                        this.md.update(tmp_buf[i]);
                    digest_time += System.currentTimeMillis() - t1;
                }
            } else {
                while (buf.hasRemaining()) {
                    buf.get();
                    numWritten++;
                }
            }
        }
        else {
            int pos = buf.position();
            //long t0 = System.currentTimeMillis();
            numWritten = dest.write(buf);
            //write_time +=  (System.currentTimeMillis() - t0);
            if (calc_digest) {
                long t1 = System.currentTimeMillis();
                for (int i = 0; i < numWritten; i++) {
                    this.md.update(buf.get(pos+i));
                }
                digest_time += (System.currentTimeMillis() - t1);
            }
        }

        if (numWritten > 0) {
            this.numBytes += numWritten;
            this.checkBytes += numWritten;
        }

        return numWritten;
    }

    public void close()
        throws IOException
    {
        this.dest.close();
        this.done = true;
    }

    public boolean isOpen()
    {
        if (this.dest == null)
            return false;
        return this.dest.isOpen();
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
     *  Warning - digest time is meaningless when the
     *  source is a socket, because kernel buffering
     *  is occurring at the same time as digestion.
     */
    public long getDigestTime() {
        return digest_time;
    }

    public long getNumBytes() {
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
        FileChannel fic = fis.getChannel();
        FileOutputStream fos = new FileOutputStream(argv[0] + ".out");
        FileChannel foc = fos.getChannel();
        boolean calcHash = true;
        if (argv.length > 1)
            calcHash = false;
        DigestableWriteChannel dwc = new DigestableWriteChannel(foc, calcHash);
        ByteBuffer buf = ByteBuffer.allocateDirect(64*1024);
        long write_time = 0;
        int numRead = fic.read(buf);
        while (numRead > 0) {
            buf.flip();
            long t1 = System.currentTimeMillis();
            dwc.write(buf);
            write_time += (System.currentTimeMillis()-t1);
            buf.clear();
            numRead = fic.read(buf);
        }
        dwc.close();
        System.out.println(convertHashBytesToString(dwc.digest()));
        System.out.println("write time: " + write_time);
        System.out.println("hash time:  " + dwc.getDigestTime());
    }

    public static String convertHashBytesToString(byte[] rawHash) {
        StringBuffer sbHash = new StringBuffer();
        for(int i = 0; i < rawHash.length; i++) {
            sbHash.append(Character.forDigit((rawHash[i] & 0xF0) >> 4, 16));
            sbHash.append(Character.forDigit(rawHash[i] & 0x0F, 16));
        }
        return (sbHash.toString());
    }
}
