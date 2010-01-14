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



import java.nio.channels.WritableByteChannel;
import java.io.FileOutputStream;
import java.io.IOException;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;

/**
 *  The <code>RetrieveData</code> class demonstrates retrieving data from 
 *  a StorageTek 5800 server.  If a file is specified, the
 *  data is stored to that file, otherwise it is written to standard output.
 */
public class RetrieveData {

    /**
     * Download data from StorageTek 5800 to the supplied channel.
     */
    public static void retrieve(String serverAddress, WritableByteChannel out, String oid)
        throws ArchiveException, IOException {

        // Create a NameValueObjectArchive as the main entry point into StorageTek 5800
        NameValueObjectArchive archive = Util.getNameValueObjectArchive(serverAddress);

        // Download data from StorageTek 5800 
        archive.retrieveObject(new ObjectIdentifier(oid), out);
    }


    public static void main(String [] argv) {
        try {
            // Acccept server name, OID, and an optional file name
            CommandLine commandline = new CommandLine(RetrieveData.class, 2, 1);
            if (commandline.parse(argv) && !commandline.helpMode()) {
                String serverAddress = commandline.getOrderedArg(0);
                String oid = commandline.getOrderedArg(1);
                String localFilename = commandline.getOrderedArg(2);
                if (localFilename != null) {
                    FileOutputStream fos = new FileOutputStream(localFilename);
                    retrieve(serverAddress, fos.getChannel(), oid);
                    fos.close();
                } else {
                    WritableByteChannel dest = new WritableByteChannel() {
                            private byte[] bytes = new byte[4096];
                            public int write(java.nio.ByteBuffer src) {
                                int n = Math.min(4096, src.remaining());
                                src.get(bytes, 0, n);
                                System.out.write(bytes, 0, n);
                                return n;
                            }
                            public void close(){}
                            public boolean isOpen(){return true;}
                        };
                    retrieve(serverAddress, dest, oid);
                }
            } else {
                if (!commandline.helpMode()) {
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.out.println("Operation failed " + e);
            System.exit(1);
        }
    }
}

/* $Id: RetrieveData.java 10844 2007-05-19 02:30:21Z bberndt $ */
