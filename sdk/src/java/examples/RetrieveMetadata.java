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



import java.io.IOException;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.common.ArchiveException;

/**
 *  The <code>RetrieveMetadata</code> class retrieves a metadata record from a 
 *  specified  StorageTek 5800 server and prints it to standard output.
 */
public class RetrieveMetadata {

    /**
     * Download a metadata record from StorageTek 5800 
     */
    public static NameValueRecord retrieve(String serverAddress, String oid)
        throws ArchiveException, IOException {

        // Create a NameValueObjectArchive as the main entry point into StorageTek 5800
        NameValueObjectArchive archive = Util.getNameValueObjectArchive(serverAddress);

        // Download data from StorageTek 5800 
        return archive.retrieveMetadata(new ObjectIdentifier(oid));
    }


    public static void main(String [] argv) { 
        try {
            CommandLine commandline = new CommandLine(RetrieveMetadata.class, 2);
            if (commandline.parse(argv) && !commandline.helpMode()) {
                String server = commandline.getOrderedArg(0);
                String oid = commandline.getOrderedArg(1);
                NameValueRecord metadataRecord = retrieve(server, oid);
                printMetadataRecord(metadataRecord);
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


    /**
     * Print a metadata record to standard output.
     */
    private static void printMetadataRecord(NameValueRecord metadataRecord) {
        // Get list of metadata names
        String [] names = metadataRecord.getKeys();

        // Loop through the list of metadata names
        for (int recordIndex = 0; recordIndex < names.length; recordIndex++) {
            System.out.println(names[recordIndex] + "=" + metadataRecord.getAsString(names[recordIndex]));
        } 

	// Get the system metadata
	SystemRecord sysrec = metadataRecord.getSystemRecord();
	System.out.println(sysrec.toString());
    }   //  printMetadataRecord
  

}


/* $Id: RetrieveMetadata.java 11208 2007-07-13 14:28:19Z bberndt $ */
