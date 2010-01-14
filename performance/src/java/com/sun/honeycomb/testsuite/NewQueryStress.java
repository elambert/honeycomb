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



import java.util.ArrayList;

import com.sun.honeycomb.client.NameValueObjectArchive;


public class NewQueryStress implements Runnable
{
    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       NewQueryStress - Stress Honeycomb query ops");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java NewQueryStress <dataVIP> <num_threads> <runtime_seconds> <query_type>");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("       Stress Honeycomb query operations.");
        System.err.println();
        System.err.println("ARGS");
        System.err.println("       dataVIP - Host or IP for data requests.");
        System.err.println("       num_threads - Number of concurrent threads.");
        System.err.println("       runtime_seconds - How long to run.  -1 means forever.");
        System.err.println("       query_type - type of query to be performed.");
	System.err.println("               If 'UNIQUE' is specified, QueryStress will read from STDIN.");
        System.err.println("               Use this to send the output from StoreStress as input");
        System.err.println("               into query stress.  QueryStress will issue a query for");
        System.err.println("               object it reads: \"system.test.type_string = '<uid>'\".");
	System.err.println("               If 'SIMPLE' is specified, QueryStress will generate a");
	System.err.println("               query string of the form \"user = 'Some value'\".");
	System.err.println("               If 'COMPLEX2' through 'COMPLEX6' are specified, QueryStress");
	System.err.println("               will generate a query string of the form");
	System.err.println("               \"first = 'x' AND second = 'y'\" up to the specified number of clauses");
	System.err.println("               If 'EMPTY' is specified, the query will be for an impossible value");
	System.err.println("               If 'ALL' is specified, \"system.test.type_string like '%Stress%'\".");
	System.err.println("               'SIMPLE' is the default.");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("       java NewQueryStress data1 10 -1 SIMPLE");
    }
    
    public static void main(String [] args) throws Throwable
    {
        if (args.length < 3 || args.length > 4) {
            printUsage();
            System.err.println("error: insufficient arguments");
            System.exit(1);
        }

        String dataVIP = null;
        int numThreads = 0;
        long runtimeMillis = 0;
        String query = "SIMPLE";

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

        if (args.length == 4) {
            query = args[3];
        }

        NewQueryStress queryStress = new NewQueryStress(dataVIP, query, numThreads, runtimeMillis);
        queryStress.run();
    }

    private String dataVIP;
    private String query;
    private int numThreads;
    private long runtimeMillis;

    public NewQueryStress(String dataVIP, 
                       String query,
                       int numThreads, 
                       long runtimeMillis)
        throws Throwable
    {
        this.dataVIP=dataVIP;
        this.query=query;
        this.numThreads=numThreads;
        this.runtimeMillis=runtimeMillis;
    }

    public void run() 
    {

        long sleepTime = 1000; // msecs
        long timeout = 60000; //msec
        NameValueObjectArchive archive = null;
        long t0 = System.currentTimeMillis();

        while (archive==null && (System.currentTimeMillis() - t0) < timeout) {

            try {
                archive = new NameValueObjectArchive(dataVIP);

	        int connectionTimeout = 600000; // 10 minutes in ms
	        int socketTimeout = 600000; // 10 minutes in ms

	        archive.setConnectionTimeout(connectionTimeout);
	        archive.setSocketTimeout(socketTimeout);

                Queue oids = null;
	        ReadFile readFile = null;
                if (query.equalsIgnoreCase("UNIQUE")) {
                    oids = new Queue(numThreads);
		    readFile = new ReadFile(oids, numThreads, runtimeMillis);
		    readFile.start();
                }

                ArrayList threads = new ArrayList();
            
                for (int i = 0; i < numThreads; i++) {
                    NewQueryThread queryThread = new NewQueryThread(archive,
                                                          query,
                                                          runtimeMillis,
                                                          oids);
                    threads.add(queryThread);
                }
            
                for (int i = 0; i < numThreads; i++) {
                    ((NewQueryThread) threads.get(i)).start();
                }

                for (int i = 0; i < numThreads; i++) {
                    ((NewQueryThread) threads.get(i)).join();
                }

	        if (readFile != null) {
	            readFile.cease();
	        }
            }
            catch (Throwable throwable) {
                System.err.println("An unexpected error occurred.");
                throwable.printStackTrace();
                try { Thread.sleep(sleepTime); } catch (Throwable t) {}
            }
        }
    }
}
