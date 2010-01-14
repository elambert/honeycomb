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
import com.sun.honeycomb.common.*;

import java.io.IOException;
import java.util.HashMap;

/**
 *  The <code>Query</code> class queries the @HoneycombProductName@ server 
 *  for Metadata Records matching the specified query string.  The results 
 *  of the query are returned as a list of OIDs of the records found, or 
 *  values of specified attributes.
 */
public class Query
{

/**
 *  Queries the @HoneycombProductName@ server for
 *  Metadata Records matching the specified query string.  The
 *  results are returned as a list of OIDs of the records
 *  found.
 */
    public static QueryResultSet query(String server, String query, String[] selectClause) 
        throws ArchiveException, IOException {

        // Create a NameValueObjectArchive as the main entry point into the @HoneycombProductName@
        NameValueObjectArchive archive = Util.getNameValueObjectArchive(server);
        if (selectClause == null || selectClause.length == 0)
            return archive.query(query, 500);
        else
            return archive.query(query, selectClause, 500);
    }


    public static void main(String [] argv) {

        try {
            // Require server and query string arguments.
            // The query string is like an SQL "where" clause
            CommandLine commandline = new CommandLine(Query.class, 2);
            // Look for fields to be returned by the query (like an SQL "select" clause)
            commandline.acceptFlag("s", false, true);
            commandline.acceptFlag("n", true);

            if (commandline.parse(argv) && !commandline.helpMode()){
                String server = commandline.getOrderedArg(0);
                String whereClause = commandline.getOrderedArg(1);
                String[] selectClause = commandline.getMultipleValues("s");
                String count = commandline.getSingleValue("n");
                int n = 1000;
                if (count != null)
                    n = Integer.parseInt(count);
                
                // Populate a table with the types of the selected attributes
                // so we can later use the typed accessors to print out their values
                NameValueObjectArchive archive = Util.getNameValueObjectArchive(server);

                NameValueSchema schema = archive.getSchema();
                NameValueSchema.Attribute[] attributes = schema.getAttributes();
                HashMap types = new HashMap();
                for (int i = 0; i < selectClause.length; i++) {
                    types.put(selectClause[i], selectClause[i]);
                }
                for (int i = 0; i < attributes.length; i++) {
                    if (types.get(attributes[i].getName()) != null) {
                        types.put(attributes[i].getName(), attributes[i].getType());
                    }
                }

                QueryResultSet results = query(server, whereClause, selectClause);
                int i = 0;

                while (results.next() && i++ < n) {
                    System.out.println(results.getObjectIdentifier());
                    if (selectClause != null) {
                        printSelectedAttributes(schema, types,
                                                selectClause, results);
                    }
                }
                System.out.println("Query Integrity Status: "+
                                   results.isQueryComplete()+" at time "+
                                   results.getQueryIntegrityTime());
            } else {
                if (!commandline.helpMode()) {
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.out.println("Operation failed " + e);
            System.exit(1);
        }
    }   // main

    private static void printSelectedAttributes(NameValueSchema schema, 
                             HashMap types, String[] selectClause, 
                             QueryResultSet results) {
        for (int i = 0; i < selectClause.length; i++) {
            System.out.print("  " + selectClause[i] + ": " );
            System.out.println(results.getAsString(selectClause[i]));
        }
    }
}   // class Query



/* $Id: Query.java 11389 2007-08-22 01:11:35Z pc198268 $ */

