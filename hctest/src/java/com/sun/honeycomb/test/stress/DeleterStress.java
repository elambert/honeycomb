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

import java.util.ArrayList;
import java.lang.Math;

import com.sun.honeycomb.client.NameValueObjectArchive;


public class DeleterStress extends StressLauncher implements Runnable
{
    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       DeleterStress - Stress Honeycomb store and delete ops");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java DeleterStress <dataVIP[:port]> <num_threads> " +
                "<min_size_bytes> <max_size_bytes> <runtime_seconds> " +
                "<num_ops> <extended_metadata> <socket_timeout_sec> [<sleep_seconds>]");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("       Stress Honeycomb store and delete operations.");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("   * Run forever with 10 threads and random file sizes " +
                "from 0 to 1MB and will add system metadata only and socket timeout of 1 minute:");
        System.err.println("       java DeleterStress data1 10 0 1048576 -1 -1 0 60");
        System.err.println("   * Run for 10 minutes with 1 thread and file size 1KB and " +
                "will add both system & perf metadata and socket timeout of 2 minutes:");
        System.err.println("       java DeleterStress data1 1 1024 1024 600 -1 1 120");
        System.err.println("   * Run with 10 threads until 5 mln files of size 1MB get stored, with no socket timeout, sleeping 2 seconds between ops:");
        System.err.println("       java DeleterStress data1 10 1048576 1048576 -1 5000000 0 0 2");
        System.err.println();
    }
    
    public static void main(String [] args) throws Throwable
    {
        if ((args.length < 8) || (args.length > 9)) {
            printUsage();
            System.err.println("error: 8 or 9 arguments required");
            System.exit(1);
        }

        String dataVIP = null;
        int numThreads = 0;
        long minSizeBytes = 0;
        long maxSizeBytes = 0;
        long runtimeMillis = 0;
        long numOps = 0;
        boolean isExtendedMetadata = false;
        int socketTimeoutMillis = 0;
        long sleepMillis = 0;
        
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
            minSizeBytes = Long.parseLong(args[2]);
            if (minSizeBytes < 0) {
                throw new Throwable();
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: min_size_bytes: " + args[2]);
            System.exit(1);
        }

        try {
            maxSizeBytes = Long.parseLong(args[3]);
            if (maxSizeBytes < 0) {
                throw new Throwable();
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: max_size_bytes: " + args[3]);
            System.exit(1);
        }

        if (minSizeBytes > maxSizeBytes) {
            printUsage();
            System.err.println("error: invalid arguments: min_size_bytes > max_size_bytes: " + args[2] + " > " + args[3]);
            System.exit(1);
        }

        try {
            runtimeMillis = Long.parseLong(args[4]) * 1000;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: runtime_seconds: " + args[4]);
            System.exit(1);
        }

        try {
            numOps = Long.parseLong(args[5]);
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: num_ops: " + args[5]);
            System.exit(1);
        }

        try {
            int num = Integer.parseInt(args[6]);
            if (num > 0)
                isExtendedMetadata = true;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: extended_metadata: " + 
                    args[6]);
            System.exit(1);
        }

        try {
            socketTimeoutMillis = Integer.parseInt(args[7]);
            socketTimeoutMillis *= 1000;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: socket_timeout_sec: " +
                                args[7]);
            System.exit(1);
        }
        try {
            if (args.length == 9) {
                sleepMillis = Long.parseLong(args[8]) * 1000;
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: sleep_seconds: " + args[8]);
            System.exit(1);
        }
        
        DeleterStress deleterStress = new DeleterStress(dataVIP, numThreads, 
                minSizeBytes, maxSizeBytes, runtimeMillis, numOps, 
                isExtendedMetadata, socketTimeoutMillis, sleepMillis);
        deleterStress.run();
    }

    private String dataVIP;
    private int numThreads;
    private long minSizeBytes;
    private long maxSizeBytes;
    private long runtimeMillis;
    private long numOps; // total number of ops to be done by the test
    private boolean isExtendedMetadata; 
    private int socketTimeoutMillis;
    private long sleepMillis;
    
    public DeleterStress(String dataVIP, 
                       int numThreads, 
                       long minSizeBytes, 
                       long maxSizeBytes,
                       long runtimeMillis,
                       long numOps,
                       boolean isExtendedMetadata,
                       int socketTimeoutMillis,
                       long sleepMillis)
        throws Throwable
    {
        this.dataVIP=dataVIP;
        this.numThreads=numThreads;
        this.minSizeBytes=minSizeBytes;
        this.maxSizeBytes=maxSizeBytes;
        this.runtimeMillis=runtimeMillis;
        this.numOps=numOps;
        this.isExtendedMetadata = isExtendedMetadata;
        this.socketTimeoutMillis = socketTimeoutMillis;
        this.sleepMillis=sleepMillis;
    }

    public void run() 
    {
        double timeTag = new Long(System.currentTimeMillis()).doubleValue();
        System.out.println("# Query.sh " + dataVIP + 
                              " system.test.type_double=" + timeTag);

        try {

            NameValueObjectArchive archive = getNameValueObjectArchive(dataVIP);
            archive.setSocketTimeout(socketTimeoutMillis);

            ArrayList threads = new ArrayList();
            
            long opsPerThread = (long) Math.ceil (numOps / numThreads);
            if (numOps == -1) 
                opsPerThread = -1; // unlimited

            for (int i = 0; i < numThreads; i++) {
                DeleterThread deleterThread = new DeleterThread(archive, timeTag,
                                                          minSizeBytes,
                                                          maxSizeBytes,
                                                          runtimeMillis,
                                                          opsPerThread,
                                                          isExtendedMetadata,
                                                          sleepMillis);
                threads.add(deleterThread);
            }
            
            for (int i = 0; i < numThreads; i++) {
                ((DeleterThread) threads.get(i)).start();
            }

            for (int i = 0; i < numThreads; i++) {
                ((DeleterThread) threads.get(i)).join();
            }

        }
        catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace();
        }
    }
}
