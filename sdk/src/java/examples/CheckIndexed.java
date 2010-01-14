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
import java.util.Map;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;

/**
 *  The <code>CheckIndexed</code> class demonstrates uploading a file 
 *  to a specified StorageTek 5800 server.
 */
public class CheckIndexed
{

    /**
     *  Upload <code>file</code> to the specified StorageTek 5800 server.
     */
    public static int checkIndexed(String server, String oid) 
        throws ArchiveException, IOException {

        // Create a NameValueObjectArchive as the main entry point into Honeycomb
        NameValueObjectArchive archive = Util.getNameValueObjectArchive(server);

        return archive.checkIndexed(new ObjectIdentifier(oid));
    }


    public static void main(String [] argv) { 
        try {
            CommandLine commandline = new CommandLine(CheckIndexed.class, 2);
            if (commandline.parse(argv) && !commandline.helpMode()) {
                String server = commandline.getOrderedArg(0);
                String oid = commandline.getOrderedArg(1);
                int isIndexed = checkIndexed(server, oid);
                if (isIndexed < 0) {
                    System.out.println("Object "+oid+" was already queryable.");
                } else if (isIndexed == 0) {
                    System.out.println("Object "+oid+" not yet queryable.");
                } else {
                    System.out.println("Object "+oid+" has now been made queryable.");
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

/* $Id: */
