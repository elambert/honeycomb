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
import java.util.Random;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.platform.diskinit.Disk;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

public class OAClientStressTest implements Runnable {
    private static final int NUM_THREADS = 10;
    private static final int MAX_SIZE = 128*1024*1024;
    private static final int NUM_OBJECTS_PER_THREAD = 10;
    private static final ByteBufferPool pool = ByteBufferPool.getInstance();
    private static final Random random =
        new Random(System.currentTimeMillis());

    private OAClient client = null;
    private int threadID = 0;

    public OAClientStressTest(OAClient client, int threadID) {
        this.client = client;
        this.threadID = threadID;
    }

    public void run() {
        for (int i=0; i<NUM_OBJECTS_PER_THREAD; i++) {
            System.out.println("THREAD[" + threadID + "]: " +
                               "Processing object " + i);
            doRead(doWrite());
        }
    }

    private SystemMetadata[] doWrite() {
        Context writeCtx = new Context();
        try {
            client.createData(OAClient.UNKNOWN_SIZE, writeCtx);
        } catch (ArchiveException e) {
            System.out.println("THREAD[" + threadID + "] FAILED: " +
                               "Creating context");
            System.exit(1);
        }

        // Prepare the write buffer
        ByteBuffer writeBuffer =
            pool.checkOutBuffer(client.getWriteBufferSize());
        byte[] bytes = new byte[writeBuffer.capacity()];
        Arrays.fill(bytes, (byte)0xbe);
        writeBuffer.put(bytes);
        writeBuffer.rewind();
        ByteBufferList list = new ByteBufferList();

        int toWrite = random.nextInt(MAX_SIZE);
        System.out.println("THREAD[" + threadID + "]: Size = " + toWrite);
        int bytesWritten = 0;
        int writeSize = client.getWriteBufferSize();
        while (bytesWritten != toWrite) {
            if ((toWrite - bytesWritten) < client.getWriteBufferSize()) {
                writeSize = toWrite - bytesWritten;
                writeBuffer.limit(writeSize);
            }
            list.clear();
            list.appendBuffer(writeBuffer);
            try {
                client.write(list, 0, writeCtx);
            } catch (ArchiveException e) {
                System.out.println("THREAD[" + threadID + "] FAILED: " +
                                   "Writing...");
                System.exit(1);
            }
            writeBuffer.rewind();
            bytesWritten += writeSize;
        }
        list.checkInBuffers();
        pool.checkInBuffer(writeBuffer);

        SystemMetadata[] retval = null;

        try {
            retval =
                client.close(writeCtx,
                             new byte[FragmentFooter.METADATA_FIELD_LENGTH],
                             false);
        } catch (ArchiveException e) {
            System.out.println("THREAD[" + threadID + "] FAILED: " +
                               "Closing...");
            System.exit(1);
        }
        writeCtx.dispose();
        return retval;
    }

    private void doRead(SystemMetadata[] systemMetadatas) {
        Context readContext = new Context();
        long toRead = 0;
        try {
            NewObjectIdentifier oid = systemMetadatas[0].getOID();
            SystemMetadata openSms = client.open(oid, readContext);
            if (!openSms.getOID().equals(oid)) {
                System.out.println("THREAD[" + threadID + "] FAILED: " +
                                   "OIDS [" + openSms.getOID() + "] [" + oid +
                                   "] don't match");
                System.exit(1);
            }
            toRead = openSms.getSize();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        ByteBuffer readBuffer =
            pool.checkOutBuffer(client.getReadBufferSize());
        byte[] bytes = new byte[readBuffer.capacity()];
        Arrays.fill(bytes, (byte)0x00);
        readBuffer.put(bytes);
        readBuffer.rewind();

        long bytesRead = 0;
        int readSize = client.getReadBufferSize();
        while (bytesRead < toRead) {
            if ((toRead - bytesRead) < readSize) {
                readSize =
                    client.getLastReadBufferSize((int)(toRead - bytesRead));
            }
            readBuffer.limit(readSize);

            try {
                client.read(readBuffer,
                            bytesRead,
                            (long)readSize,
                            readContext);
            } catch (ArchiveException e) {
                System.out.println("THREAD[" + threadID + "] FAILED: " +
                                   "Error in reading: " + e);
                System.exit(1);
            }

            readBuffer.flip();
            for (int i=readBuffer.position(); i<readBuffer.limit(); i++) {
                if (readBuffer.get(i) != (byte)0xbe) {
                    System.out.println("THREAD[" + threadID + "] FAILED: " +
                                       "Error in reading. Expected " +
                                       "0xbe got " + readBuffer.get(i));
                    System.exit(1);
                }
            }

            bytesRead += readSize;
            readBuffer.clear();
        }
        pool.checkInBuffer(readBuffer);
        readContext.dispose();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("usage: java OAClientTest <root path>");
            System.exit(1);
        }

        OAClient client = OAClient.getTestInstance();
        ObjectReliability reliability = client.getReliability();

        // Prepare the test layout
        Disk[] testLayout = new Disk[reliability.getTotalFragCount()];
        for (int i=0; i<reliability.getTotalFragCount(); i++) {
            Disk d = new Disk(new String(args[0] + "/" + i));
            testLayout[i] = d;
        }
        client.setTestLayout(testLayout);

        // Create threads for the stress test
        Thread[] workers = new Thread[NUM_THREADS];
        for (int i=0; i<NUM_THREADS; i++) {
            System.out.println("Starting thread " + i);
            workers[i] = new Thread(new OAClientStressTest(client, i));
            workers[i].start();
        }

        // Wait to join all the threads
        for (int i=0; i<NUM_THREADS; i++) {
            workers[i].join();
        }
    }
}
