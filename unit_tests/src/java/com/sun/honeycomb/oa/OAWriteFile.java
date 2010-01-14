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

import java.io.FileInputStream;
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

public class OAWriteFile {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("usage: java OAWriteFile <disk root> " +
                               "<input file>");
            System.exit(1);
        }

        // Open the input file for reading
        FileInputStream inFile = null;
        try {
            inFile = new FileInputStream(args[1]);
        } catch (Exception e) {
            System.out.println("Failed to open input file " + args[1] + e);
            System.exit(1);
        }

        // Prepare the client and the test layout
        OAClient client = OAClient.getTestInstance();
        ObjectReliability reliability = client.getReliability();
        Disk[] testLayout = new Disk[reliability.getTotalFragCount()];
        for (int i=0; i<reliability.getTotalFragCount(); i++) {
            Disk d = new Disk(new String(args[0] + "/" + i));
            testLayout[i] = d;
        }
        client.setTestLayout(testLayout);

        Context writeCtx = new Context();
        client.createData(OAClient.UNKNOWN_SIZE, writeCtx);

        // Prepare the buffer
        ByteBufferPool pool = ByteBufferPool.getInstance();
        ByteBufferList list = new ByteBufferList();

        //
        // WRITE PHASE
        //
        int bytesWritten = 0;
        int bytesRead = 0;
        long beforeWrite = System.currentTimeMillis();
        while (true) {
            ByteBuffer buffer =
                pool.checkOutBuffer(client.getWriteBufferSize());
            // Read the file
            bytesRead = read(inFile, buffer);
            if (bytesRead == 0) {
                pool.checkInBuffer(buffer);
                break;
            }
            buffer.flip();

            list.appendBuffer(buffer);
            pool.checkInBuffer(buffer);

            client.write(list, 0, writeCtx);
            list.clear();
            bytesWritten += bytesRead;
        }
        long afterWrite = System.currentTimeMillis();
        list.clear();
        inFile.close();

        SystemMetadata[] systemMetadatas =
            client.close(writeCtx,
                         new byte[FragmentFooter.METADATA_FIELD_LENGTH],
                         false);
        for (int i=0; i<systemMetadatas.length; i++) {
            System.out.println("sms[" + i + "] = " + systemMetadatas[i]);
        }
        writeCtx.dispose();

        System.out.println("PASSED");
        System.out.print("Write throughput = ");
        if (afterWrite > beforeWrite) {
            System.out.println((bytesWritten/(afterWrite - beforeWrite)) +
                               "KB/s");
        } else {
            System.out.println("NA");
        }
    }

    private static int read(FileInputStream in, ByteBuffer buffer)
        throws Exception {
        int bytesRead = 0;
        while (buffer.hasRemaining()) {
            int r = in.getChannel().read(buffer);
            if (r == -1) {
                break;
            }
            bytesRead += r;
        }
        return bytesRead;
    }
}
