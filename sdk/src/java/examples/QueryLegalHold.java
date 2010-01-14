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



import com.sun.honeycomb.client.*;

/**
 *  The <code>Query</code> class queries the @HoneycombProductName@ server 
 *  for metadata records matching the specified query string. The results 
 *  of the query are returned as a list of OIDs of the records found, or 
 *  values of specified attributes.
 */

public class QueryLegalHold {

    /**
     *  Queries the @HoneycombProductName@ server for
     *  metadata records matching the specified query string. The
     *  results are returned as a list of OIDs of the records
     *  found.
     */

    public static void main(String [] argv) {

        try {
            // Require server and legal hold string arguments.
            CommandLine commandline = new CommandLine(QueryLegalHold.class, 2);

            // Look for number of fields to be returned by the query
            commandline.acceptFlag("n", true);

            if (commandline.parse(argv) && !commandline.helpMode()){
                String server = commandline.getOrderedArg(0);
                String legalHold = commandline.getOrderedArg(1);
                String count = commandline.getSingleValue("n");
                int n = 1000;
                if (count != null)
                    n = Integer.parseInt(count);

                NameValueObjectArchive archive = new NameValueObjectArchive(server);

		/********************************************************
		 *
		 * Bug 6554027 - hide retention features
		 *
		 *******************************************************/
		/*
                QueryResultSet results = archive.queryLegalHold(legalHold, 500);

                int i = 0;
                while (results.next() && i++ < n) {
                    System.out.println(results.getObjectIdentifier());
                }
		*/
            } else {
                if (!commandline.helpMode()) {
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.out.println("Operation failed: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
