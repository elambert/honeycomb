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
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.checksum.ChecksumAlgorithm;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

public class OAClientTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("usage: java OAClientTest <root path> " +
                               "<size>");
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

        Context writeCtx = new Context();
        client.createData(OAClient.UNKNOWN_SIZE, writeCtx);

        long toWrite = Long.parseLong(args[1]);
        System.out.println("Writing " + toWrite + " bytes...");

        // Prepare the write buffer
        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBuffer buffer = pool.checkOutBuffer(client.getWriteBufferSize());
        byte[] bytes = new byte[buffer.capacity()];
        Arrays.fill(bytes, (byte)0xbe);
        buffer.put(bytes);
        buffer.rewind();
        ByteBufferList list = new ByteBufferList();

        //
        // WRITE PHASE
        //

        // Make sure that the client handles a zero length buffer
        list.clear();
        int saveLimit = buffer.limit();
        buffer.limit(0);
        list.appendBuffer(buffer);
        client.write(list, 0, writeCtx);
        buffer.limit(saveLimit);
        System.out.println("PASSED: Zero buffer write test");

        long bytesWritten = 0;
        int writeSize = client.getWriteBufferSize();
        long beforeWrite = System.currentTimeMillis();
        while (bytesWritten != toWrite) {
            if ((toWrite - bytesWritten) < client.getWriteBufferSize()) {
                writeSize = (int)(toWrite - bytesWritten);
                buffer.limit(writeSize);
            }
            list.clear();
            list.appendBuffer(buffer);
            client.write(list, 0, writeCtx);
            buffer.rewind();
            bytesWritten += writeSize;
        }
        long afterWrite = System.currentTimeMillis();
        list.checkInBuffers();
        pool.checkInBuffer(buffer);

        SystemMetadata[] systemMetadatas =
            client.close(writeCtx,
                         new byte[FragmentFooter.METADATA_FIELD_LENGTH],
                         false);
        for (int i=0; i<systemMetadatas.length; i++) {
            System.out.println("sms[" + i + "] = " + systemMetadatas[i]);
        }
        writeCtx.dispose();

        //
        // READ PHASE
        //

        Context readContext = new Context();
        long toRead = 0;
        try {
            NewObjectIdentifier oid = systemMetadatas[0].getOID();
            SystemMetadata openSms = client.open(oid, readContext);
            if (!openSms.getOID().equals(oid)) {
                System.out.println("FAILED: OIDS [" + openSms.getOID() +
                                   "] [" + oid + "] don't match");
                System.exit(1);
            }
            toRead = openSms.getSize();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        ByteBuffer readBuffer =
            pool.checkOutBuffer(client.getReadBufferSize());
        bytes = new byte[readBuffer.capacity()];
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

        System.out.println("PASSED");
        System.out.print("Write throughput = ");
        if (afterWrite > beforeWrite) {
            System.out.println((toWrite/(afterWrite - beforeWrite)) + "KB/s");
        } else {
            System.out.println("NA");
        }
        System.out.print("Read throughput = ");
        if (afterRead > beforeRead) {
            System.out.println((toRead/(afterRead - beforeRead)) + "KB/s");
        } else {
            System.out.println("NA");
        }
    }
}
