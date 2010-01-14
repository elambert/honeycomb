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
import com.sun.honeycomb.client.ObjectIdentifier;

import com.sun.honeycomb.common.ArchiveException;


/** This class demonstrates how to remove a legal hold from an existing 
 * object on a StorageTek 5800 server
 */
public class RemoveLegalHold
{

    /** Remove a legal hold from an existing object on a StorageTek 5800 server
     */
    public static void removeLegalHold(String address, String oid, String legalHold)
    throws ArchiveException, IOException {

        NameValueObjectArchive archive = new NameValueObjectArchive(address);

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
	/*
        archive.removeLegalHold((new ObjectIdentifier(oid)), legalHold);
	*/
    }


    public static void main(String [] argv) {
        try {
            CommandLine commandline = new CommandLine(RemoveLegalHold.class, 3, true);
            commandline.acceptFlag("m", true, true);
            if (commandline.parse(argv) && !commandline.helpMode()){
                String server = commandline.getOrderedArg(0);
                String oid = commandline.getOrderedArg(1);
                String legalHold = commandline.getOrderedArg(2);
                removeLegalHold(server, oid, legalHold);
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


/* $Id: RemoveLegalHold.java 10888 2007-05-21 23:59:49Z mgoff $*/
