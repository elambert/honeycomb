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



package com.sun.honeycomb.oa;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.platform.diskinit.Disk;
import com.sun.honeycomb.resources.ByteBufferPool;

public class OAClientRead {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("usage: java OAClientRead <root path> <oid>");
            System.exit(1);
        }

        OAClient client = OAClient.getTestInstance();
        ObjectReliability reliability = client.getReliability();

        System.out.println(reliability);

        // Prepare the test layout
        Disk[] testLayout = new Disk[reliability.getTotalFragCount()];
        for (int i=0; i<reliability.getTotalFragCount(); i++) {
            Disk d = new Disk(new String(args[0] + "/" + i));
            testLayout[i] = d;
        }
        client.setTestLayout(testLayout);

        //
        // READ PHASE
        //

        Context readContext = new Context();
        long toRead = 0;
        try {
            NewObjectIdentifier oid = new NewObjectIdentifier(args[1]);
            SystemMetadata openSms = client.open(oid, readContext);
            toRead = openSms.getSize();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer readBuffer =
            pool.checkOutBuffer(client.getReadBufferSize());
        byte[] bytes = new byte[readBuffer.capacity()];
        Arrays.fill(bytes, (byte)0x00);
        readBuffer.put(bytes);
        readBuffer.rewind();

        long bytesRead = 0;
        int readSize = client.getReadBufferSize();
        long beforeRead = System.currentTimeMillis();
        while (bytesRead < toRead) {
            if ((toRead - bytesRead) < readSize) {
                readSize =
                    client.getLastReadBufferSize((int)(toRead - bytesRead));
                System.out.println("Last read: size = " + readSize);
            }
            readBuffer.limit(readSize);
            try {
                client.read(readBuffer, bytesRead, (long)readSize, readContext);
            } catch (ArchiveException e) {
                System.out.println("FAILED: Error in reading: " + e);
                System.exit(1);
            }

            readBuffer.flip();
            for (int i=readBuffer.position(); i<readBuffer.limit(); i++) {
                if (readBuffer.get(i) != (byte)0xbe) {
                    System.out.println("FAILED: Error in reading. Expected " +
                                       "0xbe got " + readBuffer.get(i));
                    System.exit(1);
                }
            }

            bytesRead += readSize;
            readBuffer.clear();
        }
        long afterRead = System.currentTimeMillis();
        pool.checkInBuffer(readBuffer);
        readContext.dispose();

        System.out.println("PASSED: read throughput = " +
                           (toRead/(afterRead - beforeRead)));
    }
}
