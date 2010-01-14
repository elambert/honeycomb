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



import java.io.*;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.common.ArchiveException;

/**
 *  The <code>CheckIndexed</code> class tests return/exception status
 *  on the archive.checkIndexed() method.
 */
public class CheckIndexed
{

    public static NameValueObjectArchive 
        getNameValueObjectArchive(String server) 
        throws ArchiveException, IOException  {

        String [] res = null;
        int port = -1;

        res = server.split(":");
        if (res.length > 1) {
            server = res[0];
            port = Integer.parseInt(res[1]);
        }

        if (port == -1) {
            return new NameValueObjectArchive(server);
        } else {
            return new NameValueObjectArchive(server, port);
        }
    }

    /**
     *  Upload <code>file</code> to the specified StorageTek 5800 server.
     */
    public static SystemRecord storeFile(String server, String file) 
        throws ArchiveException, FileNotFoundException, IOException {

        // Create a NameValueObjectArchive as the main entry point into Honeycomb
        NameValueObjectArchive archive = getNameValueObjectArchive(server);

        // Store the file in the StorageTek 5800 server
        SystemRecord sr = archive.storeObject(
                               new FileInputStream(file).getChannel(), null);
        System.out.println("stored, checking archive.checkIndexed()");
        int count = 0;
        while (archive.checkIndexed(sr.getObjectIdentifier()) != 0) {
            if (++count == 70) {
                System.out.println(".");
                count = 0;
            } else {
                System.out.print(".");
            }
            try { Thread.sleep(1000);  } catch (Exception ignore) {}
        }
        System.out.println("archive.checkIndexed() returned 0");
        return sr;
    }


    public static void main(String [] argv) {     
        if (argv.length != 2) {
            System.err.println("Usage: CheckIndexed <datavip> <file>");
            System.exit(1);
        }
        try {
            SystemRecord sr = storeFile(argv[0], argv[1]);
            System.out.println(sr.getObjectIdentifier());
            System.out.println(sr);
        } catch (Exception e) {
            System.out.println("caught exception as expected: " + 
                               " should be in archive.checkIndexed: " + e);
            e.printStackTrace();
        }
    }
}

/* $Id: */
