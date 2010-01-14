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

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.common.ArchiveException;

/**
 *  The <code>DeleteObject</code> class demonstrates how to delete a 
 * StorageTek 5800 Record.
 */
public class DeleteRecord {
   
    /**
     * Delete the StorageTek 5800 Record identified by <code>oid</code>.
     */ 
    public static void delete(String honeycombServerAddress, String oid) 
        throws ArchiveException, IOException {

        // Create a NameValueObjectArchive as the main entry point into StorageTek 5800
        NameValueObjectArchive archive = new NameValueObjectArchive(honeycombServerAddress);
            
        // Delete Record with the supplied Object Identifier.
        archive.delete(new ObjectIdentifier(oid));
    }


    public static void main(String [] argv) {
        CommandLine commandline = new CommandLine(DeleteRecord.class, 2);
        commandline.acceptFlag("v");
        String oid = null;
        try {
            if (commandline.parse(argv) && !commandline.helpMode()) {

                String honeycombServerAddress = commandline.getOrderedArg(0);
                oid = commandline.getOrderedArg(1);

                if (commandline.flagPresent("v")) {
                    System.out.println("Deleting " + oid);
                } 
                delete(honeycombServerAddress, oid);
            } else {
                if (!commandline.helpMode()) {
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.out.println(e + " error: deleting " + oid);
            System.exit(1);
        } 
    }
    
}   // DeleteObject


/* $Id: DeleteRecord.java 10844 2007-05-19 02:30:21Z bberndt $ */
