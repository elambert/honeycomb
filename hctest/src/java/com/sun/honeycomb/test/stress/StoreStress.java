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


public class StoreStress extends StressLauncher implements Runnable
{
    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       StoreStress - Stress Honeycomb store ops");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java StoreStress <dataVIP[:port]> <num_threads> <min_size_bytes> <max_size_bytes> <pattern> <extended_metadata> <runtime_seconds> <num_ops> <poll_indexed> <socket_timeout_sec>");
        System.err.println();
        System.err.println("OPTIONS");
        System.err.println("       dataVIP - host or IP address of the cell's Data VIP");
        System.err.println("       num_threads - num store threads");
        System.err.println("       min_size_bytes - minimum file size in bytes");
        System.err.println("       max_size_bytes - maximum file size in bytes");
        System.err.println("       pattern - \"binary\" or \"beef\"");
        System.err.println("       extended_metadata - 1 or 0");
        System.err.println("       runtime_seconds - limit the runtme; -1 means run forever.");
        System.err.println("                         execution terminates when ither runtime_seconds or num_ops is exceeded.");
        System.err.println("       num_ops - limit the number of ops to execute; -1 means no limit");
        System.err.println("                 execution terminates when either runtime_seconds or num_ops is exceeded.");
        System.err.println("       poll_indexed - check isIndexed() and loop if not: 1 or 0");
        System.err.println("       socket_timeout_sec - 0 for no timeout");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("   * Run forever with 10 threads and random file sizes " +
                           "from 0 to 1MB, add system metadata only, store binary data, " +
                           "poll indexed and 1 minute socket timeout:");
        System.err.println("       java StoreStress data1 10 0 1048576 binary 0 -1 -1 0 1 60");
        System.err.println("   * Run for 10 minutes with 1 thread and file size 1KB, " +
                           "add both system & perf metadata, store \"DEADBEEF BYTES!\", " +
                           "don't poll indexed and no socket timeout:");
        System.err.println("       java StoreStress data1 1 1024 1024 beef 1 600 -1 0 0 0");
        System.err.println("   * Run with 10 threads until 5 mln files of size 1MB get stored:");
        System.err.println("       java StoreStress data1 10 1048576 1048576 binary 0 -1 5000000 0 1 60");
        System.err.println("   * Run with 15 threads storing 10-byte files and checking index status:");
        System.err.println("       java StoreStress data1 15 10 10 beef 0 -1 -1 1 1 60");
        System.err.println();
    }
    
    public static void main(String [] args) throws Throwable
    {
        if (args.length != 10) {
            printUsage();
            System.err.println("error: insufficient arguments");
            System.exit(1);
        }

        String dataVIP = null;
        int numThreads = 0;
        long minSizeBytes = 0;
        long maxSizeBytes = 0;
        int pattern = ChannelPatterns.BINARY;
        boolean isExtendedMetadata = false;
        long runtimeMillis = 0;
        long numOps = 0;
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
            minSizeBytes = Long.parseLong(args[arg_i++]);
            if (minSizeBytes < 0) {
                throw new Throwable();
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: min_size_bytes: " + args[arg_i-1]);
            System.exit(1);
        }

        try {
            maxSizeBytes = Long.parseLong(args[arg_i++]);
            if (maxSizeBytes < 0) {
                throw new Throwable();
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: max_size_bytes: " + args[arg_i-1]);
            System.exit(1);
        }

        if (minSizeBytes > maxSizeBytes) {
            printUsage();
            System.err.println("error: invalid arguments: min_size_bytes > max_size_bytes");
            System.exit(1);
        }

        String pattern_arg = args[arg_i++];
        if (pattern_arg.equals("binary")) {
            pattern = ChannelPatterns.BINARY;
        } else if (pattern_arg.equals("beef")) {
            pattern = ChannelPatterns.DEADBEEF;
        } else {
            printUsage();
            System.err.println("error: invalid argument: pattern: " + pattern_arg);
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
            runtimeMillis = Long.parseLong(args[arg_i++]) * 1000;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: runtime_seconds: " + args[arg_i-1]);
            System.exit(1);
        }

        try {
            numOps = Long.parseLong(args[arg_i++]);
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: num_ops: " + args[arg_i-1]);
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

        StoreStress storeStress = new StoreStress(dataVIP, 
                                                  numThreads, 
                                                  minSizeBytes, 
                                                  maxSizeBytes, 
                                                  pattern,
                                                  isExtendedMetadata, 
                                                  runtimeMillis, 
                                                  numOps,
                                                  pollIndexed,
                                                  socketTimeoutMillis);
        storeStress.run();
    }

    private String dataVIP;
    private int numThreads;
    private long minSizeBytes;
    private long maxSizeBytes;
    private int pattern;
    private boolean isExtendedMetadata; 
    private long runtimeMillis;
    private long numOps; // total number of ops to be done by the test
    private boolean pollIndexed;
    private int socketTimeoutMillis;
    
    public StoreStress(String dataVIP, 
                       int numThreads, 
                       long minSizeBytes, 
                       long maxSizeBytes,
                       int pattern,
                       boolean isExtendedMetadata,
                       long runtimeMillis,
                       long numOps,
                       boolean pollIndexed,
                       int socketTimeoutMillis)
        throws Throwable
    {
        this.dataVIP=dataVIP;
        this.numThreads=numThreads;
        this.minSizeBytes=minSizeBytes;
        this.maxSizeBytes=maxSizeBytes;
        this.pattern=pattern;
        this.isExtendedMetadata = isExtendedMetadata;
        this.runtimeMillis=runtimeMillis;
        this.numOps=numOps;
        this.pollIndexed=pollIndexed;
        this.socketTimeoutMillis = socketTimeoutMillis;
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
                StoreThread storeThread = new StoreThread(archive, 
                                                          timeTag,
                                                          minSizeBytes,
                                                          maxSizeBytes,
                                                          pattern,
                                                          isExtendedMetadata,
                                                          runtimeMillis,
                                                          opsPerThread,
                                                          pollIndexed);
                threads.add(storeThread);
            }
            
            for (int i = 0; i < numThreads; i++) {
                ((StoreThread) threads.get(i)).start();
            }

            for (int i = 0; i < numThreads; i++) {
                ((StoreThread) threads.get(i)).join();
            }

        }
        catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace();
        }
    }
}
