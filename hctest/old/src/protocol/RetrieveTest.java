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



package com.sun.honeycomb.protocol;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.protocol.client.ObjectArchive;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

public class RetrieveTest {

    public static void main(String[] args) throws ArchiveException, IOException {
        NewObjectIdentifier oid = null;
        WritableByteChannel channel = null;
        String host = null;

        if (args.length > 1 && args.length < 4) {
            try {
                oid = new NewObjectIdentifier(args[0]);
            } catch (IllegalArgumentException e) {
                System.err.println("invalid identifier " + args[0]);
                System.exit(1);
            }

            host = args[1];

            if (args.length == 3) {
                channel = new FileOutputStream(args[2]).getChannel();
            } else {
                channel = Channels.newChannel(System.out);
            }
        } else {
            exitUsage();
        }

        ObjectArchive archive = new ObjectArchive(new String[] {host});

        long before = System.currentTimeMillis();
        long size = archive.retrieve(channel, oid);
        long after = System.currentTimeMillis();
        long time = after - before;

        System.out.println("read " +
                           size +
                           " bytes in " +
                           time +
                           " ms (" +
                           ((size * 1000) / (time * 1024)) +
                           " KB/s)");
    }

    private static void exitUsage() {
        System.err.println("Usage: RetrieveTest <oid> <host> [output file]");
        System.exit(1);
    }
}
