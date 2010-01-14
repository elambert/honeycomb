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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

import com.sun.honeycomb.client.NameValueObjectArchive;


public class QueryStress extends StressLauncher implements Runnable 
{
    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       QueryStress - Stress Honeycomb query ops");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java QueryStress <dataVIP[:port]> <num_threads> <runtime_seconds> <num_ops> <socket_timeout_sec> [<query>]");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("       Stress Honeycomb query operations.");
        System.err.println();
        System.err.println("ARGS");
        System.err.println("       dataVIP - Host or IP for data requests.");
        System.err.println("       num_threads - Number of concurrent threads.");
        System.err.println("       runtime_seconds - How long to run.  -1 means forever.");
        System.err.println("       socket_timeout_sec - 0 means no timeout");
        System.err.println("       num_ops - How many query operations to do.  -1 means forever.");
        System.err.println("       query - Default \"system.test.type_string like '%Stress%'\".");
        System.err.println("               If '-' is specified, QueryStress will read from STDIN.");
        System.err.println("               Use this to send the output from StoreStress as input");
        System.err.println("               into query stress.  QueryStress will issue a query for");
        System.err.println("               object it reads: \"system.test.type_string = '<uid>'\".");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("       java QueryStress data1 10 -1 -1 60");
    }
    
    public static void main(String [] args) throws Throwable
    {
        if (args.length < 5 || args.length > 6) {
            printUsage();
            System.err.println("error: insufficient arguments");
            System.exit(1);
        }

        String dataVIP = null;
        int numThreads = 0;
        long runtimeMillis = 0;
        long numOps = 0;
        int socketTimeoutMillis = 0;
        String query = "system.test.type_string like '%Stress%'";

        dataVIP=args[0];

        try {
            numThreads = Integer.parseInt(args[1]);
            if (numThreads < 0) {
                throw new Throwable();
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: num_threads: " + args[1]);
            System.exit(1);
        }

        try {
            runtimeMillis = Long.parseLong(args[2]) * 1000;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: runtime_seconds: " + args[2]);
            System.exit(1);
        }

        try {
            numOps = Long.parseLong(args[3]);
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: num_ops: " + args[3]);
            System.exit(1);
        }
        try {
            socketTimeoutMillis = Integer.parseInt(args[4]);
            socketTimeoutMillis *= 1000;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: socket_timeout_millis: " +
                               args[4]);
            System.exit(1);
        }

        if (args.length == 6) {
            query = args[5];
        }

        QueryStress queryStress = new QueryStress(dataVIP, query, numThreads, runtimeMillis, numOps, socketTimeoutMillis);
        queryStress.run();
    }

    private String dataVIP;
    private String query;
    private int numThreads;
    private long runtimeMillis;
    private long numOps;
    private int socketTimeoutMillis;

    public QueryStress(String dataVIP, 
                       String query,
                       int numThreads, 
                       long runtimeMillis,
                       long numOps,
                       int socketTimeoutMillis) 
        throws Throwable
    {
        this.dataVIP=dataVIP;
        this.query=query;
        this.numThreads=numThreads;
        this.runtimeMillis=runtimeMillis;
        this.numOps=numOps;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public void run() 
    {
        try {
            NameValueObjectArchive archive = getNameValueObjectArchive(dataVIP);
            archive.setSocketTimeout(socketTimeoutMillis);

            ArrayList threads = new ArrayList();

            Queue oids = null;
            if (query.equals("-")) {
                oids = new Queue(numThreads);
            }
            
            for (int i = 0; i < numThreads; i++) {
                QueryThread queryThread = new QueryThread(archive,
                                                          query,
                                                          runtimeMillis,
                                                          numOps,
                                                          oids);
                threads.add(queryThread);
            }
            
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < numThreads; i++) {
                ((QueryThread) threads.get(i)).start();
            }

            if (query.equals("-")) {
                BufferedReader in = 
                    new BufferedReader(new InputStreamReader(System.in));
                String line = null;
                while ((runtimeMillis < 0 || 
                       runtimeMillis > (System.currentTimeMillis() - startTime)) && 
                       (line = in.readLine()) != null) {

                    if (line.startsWith("#"))
                        continue;

                    InputOutput entry = InputOutput.readLine(line);
                    if (entry.ok) {
                        oids.push(entry);
                    } 
                }

                for (int i = 0; i < numThreads; i++) {
                    oids.push(null);
                }
            }

            for (int i = 0; i < numThreads; i++) {
                ((QueryThread) threads.get(i)).join();
            }
        }
        catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace();
        }
    }
}
