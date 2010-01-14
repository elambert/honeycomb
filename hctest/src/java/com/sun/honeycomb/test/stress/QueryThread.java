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



package com.sun.honeycomb.test.stress;

import java.util.Random;
import java.util.Date;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.QueryResultSet;

public class QueryThread extends Thread
{
    private NameValueObjectArchive archive;
    private String query;
    private long runtimeMillis;
    private int resultsGroupSize = 1024;
    private long numErrors = 0;
    private long numOps = 0;
    private Queue oids;

    private long startTime;

    public QueryThread(NameValueObjectArchive archive,
                       String query,
                       long runtimeMillis,
                       long numOps,
                       Queue oids)
    {
        this.archive=archive;
        this.query=query;
        this.runtimeMillis=runtimeMillis;
        this.oids=oids;
        this.numOps=numOps;
    }

    public void run()
    {
        startTime = System.currentTimeMillis();
        long queryCount = 0; // counts only successful queries
        String oid = null;
        String uid = "?";
        long sizeBytes = -1;
            
        // do queries until time expires, or numOps queries are done,
        // or the input OIDs have been exhausted.

        while (query != null && oids != null &&
               (runtimeMillis < 0 || 
                runtimeMillis > (System.currentTimeMillis() - startTime)) &&
               (numOps < 0 || numOps > (queryCount+numErrors))) {

            // Note that we've already filtered and skipped lines
            // that had errors during store or that start with #
            InputOutput line = (InputOutput) oids.pop();
            if (line == null) {
                query = null; // ran out of OIDs
                break; // nothing left to do
            }

            query = "system.test.type_string = '" + line.uid + "'";
            oid = line.oid;
            uid = line.uid;
            sizeBytes = line.sizeBytes;
            long t0 = 0, t1;
            String tag = null;
            boolean ok = false;
            for (int i=0; i<5; i++) {
                try {

                    tag = "## " + System.currentTimeMillis() + " " + oid;
                    System.out.println(tag);
                    t0 = System.currentTimeMillis();
                    QueryResultSet matches = archive.query(query, 
                                                           resultsGroupSize);

                    // Must loop through matches.  It is possible an interrupted
                    // store might match in addition to our successful store.
                    // Consider success only if we find our oid.
                    boolean found = false;
                    while (matches.next()) {
                        String _oid = matches.getObjectIdentifier().toString();

                        if (oid.equals(_oid)) {
                            found = true;
                            break;
                        }
                    } 

                    t1 = System.currentTimeMillis();
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                          t1,
                                          oid,
                                          uid,
                                          sizeBytes,
                                          InputOutput.QUERY,
                                          found);

                    if (!found) {
                        System.err.println(
                            (new Date()) + " " + System.currentTimeMillis() +
                            ": Could not find " + oid + " in query " + query +
                            "; total errors " + ++numErrors);
                    } else {
                        queryCount++; // counts successful queries
                        ok = true;
                        break;
                    }
                } catch (Throwable throwable) {
                    t1 = System.currentTimeMillis();
                    System.err.println(
                        (new Date()) + " " + System.currentTimeMillis() +
                        ": An unexpected error has occured on oid " +
                        oid + "; query " + query + "; total errors " + 
                        ++numErrors);
                    throwable.printStackTrace();

                    // log line about failure
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                            t1,
                                            oid,
                                            uid,
                                            sizeBytes,
                                            InputOutput.QUERY,
                                            false,true,throwable);
                }
            }
            if (!ok) {
                System.err.println("# FAIL " + query + " " + oid);
                System.out.println("# FAIL " + query + " " + oid);
            }
        }
    }
}
