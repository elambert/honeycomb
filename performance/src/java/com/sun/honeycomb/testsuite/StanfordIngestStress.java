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

import java.io.*;
import java.net.InetAddress;


public class StanfordIngestStress implements Runnable
{
    
    private String dataVIP;
    private int numThreads;
    private long runtimeMillis;


    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       StanfordIngestStress - Stress Stanford Honeycomb Ingest ops");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java StanfordIngestStress <dataVIP> <num_threads> <runtime_seconds>");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("       Stress Stanford Honeycomb ingest operations (including store, quiery and retrieve on DOuuid).");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("       java StanfordIngestStress data1 10 10000");
    }
    
    public static void main(String [] args) throws Throwable
    {
        if (args.length != 3) {
            printUsage();
            System.err.println("error: insufficient arguments");
            System.exit(1);
        }

        String dataVIP = null;
        int numThreads = 0;
        long runtimeMillis = 0;

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

        StanfordIngestStress stanfordIngestStress = new StanfordIngestStress(dataVIP, numThreads, runtimeMillis);
        stanfordIngestStress.run();
    }


    public StanfordIngestStress(String dataVIP, 
                       int numThreads, 
                       long runtimeMillis)
        throws Throwable
    {
        this.dataVIP=dataVIP;
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

                createIDfiles();
 
                ArrayList threads = new ArrayList();
            
                for (int i = 0; i < numThreads; i++) {
                    StanfordIngestThread stanfordIngestThread = new StanfordIngestThread(archive,
                                                          runtimeMillis);
                    threads.add(stanfordIngestThread);
  
                }
            
                for (int i = 0; i < numThreads; i++) {
                    ((StanfordIngestThread) threads.get(i)).start();
                
                }

                for (int i = 0; i < numThreads; i++) {
                    ((StanfordIngestThread) threads.get(i)).join();
                }

            }
            catch (Throwable throwable) {
                System.err.println("An unexpected error occurred.");
                throwable.printStackTrace();
                try { Thread.sleep(sleepTime); } catch (Throwable t) {}
            }
        }
    }

   private void createIDfiles(){

     File f1  = new File("/opt/performance/lib/last_douuid_file");
     File f2  = new File("/opt/performance/lib/first_douuid_file");
     File f3  = new File("/opt/performance/lib/last_douuid_file_verified");
 
     long firstId = generateFirstid(); 
     
     if (!f1.exists()){ 
          try {

	      PrintWriter printWrit = null;
	      printWrit = new PrintWriter(new FileOutputStream(f1));
	        
	      printWrit.println (""+firstId); 
	      printWrit.close();
    
   	  } 
          catch (FileNotFoundException e){ 
              System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
          }
          catch (IOException e) {
	      System.err.println("Caught IOException: "  +  e.getMessage());
	  } 
     }

     if (!f2.exists()){ 
          try {

	      PrintWriter printWrit = null;
	      printWrit = new PrintWriter(new FileOutputStream(f2));
	        
	      printWrit.println (""+firstId); 
	      printWrit.close();
    
   	  } 
          catch (FileNotFoundException e){ 
              System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
          }
          catch (IOException e) {
	      System.err.println("Caught IOException: "  +  e.getMessage());
	  } 
      }
   
      if (!f3.exists()){
          try {

              PrintWriter printWrit = null;
              printWrit = new PrintWriter(new FileOutputStream(f3));

              printWrit.println (""+firstId);
              printWrit.close();

          }
          catch (FileNotFoundException e){
              System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
          }
          catch (IOException e) {
              System.err.println("Caught IOException: "  +  e.getMessage());
          }
      }

   }


  /*DOuuid and uuid starts from 10000000*client number, e.g., for cl50, they start from 500000000. Each client can generate 10 million ids without duplication */

 
  private static long generateFirstid(){

    String hostName=null;
    long hostNumber;

    try{

      hostName = InetAddress.getLocalHost().getHostName();
    }

    catch (Throwable t) {
      t.printStackTrace();
      System.err.println("error: unable to determine local hostname.");
      System.err.println("(abort)");
      System.exit(-1);
    }

    hostNumber = Long.parseLong(hostName.substring(2));   /*get "50" from "cl50" */

    return (hostNumber*10000000);
  } 


}
