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



import java.util.Date;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

/** This class demonstrates how to set a compliance retention time on
 *  an existing object on a StorageTek 5800 server.
 */
public class SetRetentionTimeRelative
{

    /** Set the retention time on an existing object on a StorageTek
     * 5800 server to a relative time from now.
     */
    public static void setRetentionTimeRelative(String address, String oid,
                                                long retentionLength)
        throws ArchiveException, IOException {

        NameValueObjectArchive archive = new NameValueObjectArchive(address);

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
	/*
	Date date = archive.setRetentionTimeRelative(
                            new ObjectIdentifier(oid), retentionLength);
        System.out.println("Set retention time to " + date.toString() +
                           " (" + date.getTime() + ")");
	*/
    }

    public static void main(String [] argv) {
        try {
            CommandLine commandline =
                new CommandLine(SetRetentionTimeRelative.class,
                                3, true);
            if (commandline.parse(argv) && !commandline.helpMode()){
                String server = commandline.getOrderedArg(0);
                String oid = commandline.getOrderedArg(1);
                long retentionLength =
                    (Long.valueOf(commandline.getOrderedArg(2))).longValue();
                setRetentionTimeRelative(server, oid, retentionLength);
            } else {
		if (!commandline.helpMode()) {
                    System.exit(1);
		}
            } 
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
