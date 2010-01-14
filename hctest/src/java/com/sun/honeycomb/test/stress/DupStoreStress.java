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


public class DupStoreStress extends StressLauncher implements Runnable
{
    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       DupStoreStress - Stress Honeycomb store ops");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       cat store.out | java DupStoreStress <dataVIP[:port]> <num_threads> <extended_metadata> <poll_indexed> <socket_timeout_sec>");
        System.err.println();
        System.err.println("OPTIONS");
        System.err.println("       dataVIP - host or IP address of the cell's Data VIP");
        System.err.println("       num_threads - num store threads");
        System.err.println("       extended_metadata - 1 or 0");
        System.err.println("       poll_indexed - check isIndexed() and loop if not: 1 or 0");
        System.err.println("       socket_timeout_sec - 0 for no timeout");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("   * Run with 10 threads add system metadata only poll indexed");
        System.err.println("     and 1 minute socket timeout:");
        System.err.println("       java DupStoreStress data1 10 0 1 60");
        System.err.println();
    }
    
    public static void main(String [] args) throws Throwable
    {
        if (args.length != 5) {
            printUsage();
            System.err.println("error: insufficient arguments");
            System.exit(1);
        }

        String dataVIP = null;
        int numThreads = 0;
        boolean isExtendedMetadata = false;
        boolean pollIndexed = false;
        int socketTimeoutMillis = 0;

        int arg_i = 0;
        
        dataVIP=args[arg_i++];

        try {
            numThreads = Integer.parseInt(args[arg_i++]);
            if (numThreads < 0) {
                throw new Throwable();
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: num_threads: " + args[arg_i-1]);
            System.exit(1);
        }

        try {
            int num = Integer.parseInt(args[arg_i++]);
            if (num > 0)
                isExtendedMetadata = true;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: extended_metadata: " + args[arg_i-1]);
            System.exit(1);
        }

        try {
            int tmp = Integer.parseInt(args[arg_i++]);
            if (tmp == 1)
                pollIndexed = true;
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: pollIndexed: " + 
                                   args[arg_i-1]);
            System.exit(1);
        }

        try {
            socketTimeoutMillis = Integer.parseInt(args[arg_i++]);
            socketTimeoutMillis *= 1000;
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: socket_timeout_sec: " +
                                   args[arg_i-1]);
            System.exit(1);
        }

        DupStoreStress storeStress = new DupStoreStress(dataVIP, 
                                                        numThreads, 
                                                        isExtendedMetadata, 
                                                        pollIndexed,
                                                        socketTimeoutMillis);
        storeStress.run();
    }

    private String dataVIP;
    private int numThreads;
    private boolean isExtendedMetadata; 
    private boolean pollIndexed;
    private int socketTimeoutMillis;
    
    public DupStoreStress(String dataVIP, 
                          int numThreads, 
                          boolean isExtendedMetadata,
                          boolean pollIndexed,
                          int socketTimeoutMillis)
        throws Throwable
    {
        this.dataVIP=dataVIP;
        this.numThreads=numThreads;
        this.isExtendedMetadata = isExtendedMetadata;
        this.pollIndexed=pollIndexed;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public void run() 
    {
        try {
            double timeTag = new Long(System.currentTimeMillis()).doubleValue();
            System.out.println("# Query.sh " + dataVIP + 
                               " system.test.type_double=" + timeTag);

            Queue oids = new Queue(numThreads);

            NameValueObjectArchive archive = getNameValueObjectArchive(dataVIP);
            archive.setSocketTimeout(socketTimeoutMillis);

            ArrayList threads = new ArrayList();
            
            for (int i = 0; i < numThreads; i++) {
                DupStoreThread storeThread = new DupStoreThread(archive, 
                                                                timeTag,
                                                                isExtendedMetadata,
                                                                pollIndexed,
                                                                oids);
                threads.add(storeThread);
            }
            
            for (int i = 0; i < numThreads; i++) {
                ((DupStoreThread) threads.get(i)).start();
            }

            BufferedReader in = 
                new BufferedReader(new InputStreamReader(System.in));
            String line = null;
            while ((line = in.readLine()) != null) {
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

            for (int i = 0; i < numThreads; i++) {
                ((DupStoreThread) threads.get(i)).join();
            }

        }
        catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace();
        }
    }
}
