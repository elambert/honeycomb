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

public class RetrieveStress extends StressLauncher implements Runnable
{
    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       RetrieveStress - Stress Honeycomb retrieve ops");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java RetrieveStress <dataVIP[:port]> <num_threads> <content_verification> <socket_timeout_sec>");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("       Stress Honeycomb retrieve operations.");
        System.err.println("       Reads OIDs from STDIN.  Terminates after reading EOF.");
        System.err.println();
        System.err.println("ARGS");
        System.err.println("       dataVIP - Host or IP for data requests.");
        System.err.println("       num_threads - Number of concurrent threads.");
        System.err.println("       content_verification - 0 or 1.");
        System.err.println("       socket_timeout_sec - 0 for no timeout");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("   * Do 10 threads with content verification of binary data with 1 minute socket timeout.");
        System.err.println("       java RetrieveStress data1 10 binary 1 60");
    }
    
    public static void main(String [] args) throws Throwable
    {
        if (args.length != 4) {
            printUsage();
            System.err.println("error: insufficient arguments");
            System.exit(1);
        }

        String dataVIP = null;
        int numThreads = 0;
        boolean doContentVerification = true;
        int socketTimeoutMillis = 0;

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
            int num = Integer.parseInt(args[2]);
            if (num == 0)
                doContentVerification = false;
        }
        catch (Throwable t) {
            doContentVerification = true;
        }

        try {
            socketTimeoutMillis = Integer.parseInt(args[3]);
            socketTimeoutMillis *= 1000;
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: socket_timeout_sec: " +
                               args[3]);
            System.exit(1);
        }

        RetrieveStress retrieveStress = 
                new RetrieveStress(dataVIP, numThreads, doContentVerification,
                                   socketTimeoutMillis);
        retrieveStress.run();
    }

    private String dataVIP;
    private int numThreads;
    private boolean doContentVerification;
    private int socketTimeoutMillis;
    
    public RetrieveStress(String dataVIP, 
                          int numThreads,
                          boolean doContentVerification,
                          int socketTimeoutMillis)
        throws Throwable
    {
        this.dataVIP=dataVIP;
        this.numThreads=numThreads;
        this.doContentVerification=doContentVerification;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public void run() 
    {
        try {
            Queue oids = new Queue(numThreads);

            NameValueObjectArchive archive = getNameValueObjectArchive(dataVIP);
            archive.setSocketTimeout(socketTimeoutMillis);

            ArrayList threads = new ArrayList();
            
            for (int i = 0; i < numThreads; i++) {
                RetrieveThread retrieveThread = 
                    new RetrieveThread(archive, oids, doContentVerification);
                threads.add(retrieveThread);
            }
            
            for (int i = 0; i < numThreads; i++) {
                ((RetrieveThread) threads.get(i)).start();
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
                ((RetrieveThread) threads.get(i)).join();
            }
            
        }
        catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace();
        }
    }
}
