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
import com.sun.honeycomb.common.ObjectExistsException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.protocol.client.ObjectArchive;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

public class SingleFileTest {

    public static void main(String[] args) {
        if (args.length != 2) {
            exitUsage();
        }

        ObjectArchive archive = new ObjectArchive(new String[] {args[1]});
        FileInputStream fileIn;
        try {
            fileIn = new FileInputStream(args[0]);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("file not found: " + args[0]);
        }

        FileChannel channel = fileIn.getChannel();
        NewObjectIdentifier oid = null;
        try {
            System.out.println("storing file " + args[0]);
            oid = archive.store(channel, ObjectArchive.UNKNOWN_SIZE);
            System.out.println("       got oid " + oid);
        } catch (ObjectExistsException e) {
            oid = e.getExistingIdentifier();
            System.out.println("       object exists - got oid " + oid);
        } catch (ArchiveException e) {
            throw new RuntimeException("store failed", e);
        }

        FileOutputStream fileOut;

        try {
            fileOut = new FileOutputStream(args[0] + ".new");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file not found: "
                                       + args[0] + ".new");
        }

        channel = fileOut.getChannel();
        try {
            System.out.println("retrieving oid " + oid);
            long length = archive.retrieve(channel, oid);
            System.out.println("got length: " + length);
        } catch (ArchiveException e) {
            throw new RuntimeException("retrieve failed", e);
        }
    }

    private static void exitUsage() {
        System.out.println("usage: LargeFileTest <filename> <server>");
        System.exit(1);
    }
}
