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



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;


public class StanfordAnalyze
{
    
  private int numThreads;

    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       StanfordAnalyze - Analyze  output files");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java StanfordAnalyze <num_threads> ");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("       Reads an  output file from stdin.");
        System.err.println("       Prints analysis to stdout.");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("       cat outputfile | java com.sun.honeycomb.test.stress.StanfordAnalyze 2");
    }

    public static void main(String [] args) throws Throwable
    {

        int numThread = 0;

        if (args.length != 1) {
            printUsage();
            System.err.println("error: insufficient arguments");
            System.exit(1);
        }	

        try {
            numThread = Integer.parseInt(args[0]);
            if (numThread < 0) {
                throw new Throwable();
            }
        }
        catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: num_thread: " + args[0]);
            System.exit(1);
        }

     
          
        StanfordAnalyze analyzer = new StanfordAnalyze(numThread);
        analyzer.analyze(System.in,System.out);
    }

  

  public StanfordAnalyze(int numThread)
  {
    this.numThreads = numThread;
  }

  public void analyze(InputStream input,OutputStream output) throws Throwable
  {
      String hostName=null;

       try{

           hostName = InetAddress.getLocalHost().getHostName();
       }

       catch (Throwable t) {
           t.printStackTrace();
           System.err.println("error: unable to determine local hostname.");
           System.err.println("(abort)");
           System.exit(-1);
       }


       long[] totalStoreTime = new long[numThreads];
       long[] totalStoreOps = new long[numThreads];
       long[] totalStoreBytes = new long[numThreads];
       long[] aveStoreBytesPerSec = new long[numThreads];
       long storeBytesPerSec = 0;

       long[] totalRetrieveTime = new long[numThreads];
       long[] totalRetrieveOps = new long[numThreads];
       long[] totalRetrieveBytes = new long[numThreads];
       long[] aveRetrieveBytesPerSec = new long[numThreads];
       long retrieveBytesPerSec = 0;
  
       long[] totalQueryDelay = new long[numThreads];
       long[] totalQueryOps = new long[numThreads];
       long[] aveQueryDelay = new long[numThreads];
       long queryDelay = 0;
  
       int usedThreadIndex = 0;
       long[] threadId = new long[numThreads];

       for (int i=0; i<numThreads;i++){

    	   totalStoreTime[i]=0;
  	   totalStoreOps[i]=0;
    	   totalStoreBytes[i]=0;
           aveStoreBytesPerSec[i]=0;

           totalRetrieveTime[i]=0;
  	   totalRetrieveOps[i]=0;
           totalRetrieveBytes[i]=0;
           aveRetrieveBytesPerSec[i]=0;
  
           totalQueryDelay[i]=0;
  	   totalQueryOps[i]=0;
           aveQueryDelay[i]=0;

           threadId[i]=0;
       }

       BufferedReader reader = new BufferedReader(new InputStreamReader(input));
       PrintWriter writer = new PrintWriter(output);

       String s = null;
       int i = 0;
       
       /* Find all the different ThreadIds and put them into array threadId[]*/

       if ((s = reader.readLine()) == null){ 
	   System.err.println ("no result file");
           System.exit(1);
       }

       StanfordInputOutput line = StanfordInputOutput.readLine(s);
       threadId[usedThreadIndex] =  line.threadID;  
       
       if (line.op.equals("STR")){
    			totalStoreTime[0] = totalStoreTime[0] + (line.t1-line.t0);
  			totalStoreOps[0] ++;
    			totalStoreBytes[0] =totalStoreBytes[0] + line.bytes;
  	}
        else if (line.op.equals("RTV")){
			totalRetrieveTime[0] = totalRetrieveTime[0]+ line.t1-line.t0;
  			totalRetrieveOps[0] ++;
    			totalRetrieveBytes[0] =totalRetrieveBytes[0]+ line.bytes;
	}
        else if (line.op.equals("QRY")){
			totalQueryDelay[0] =totalQueryDelay[0] + line.t1-line.t0;
  			totalQueryOps[0] ++;
	}

        else {
 		 System.err.println("Cannot find the Op Type ");
        }


/*       System.err.println(" "+ line.threadID + " " + line.t0 + " " + line.t1 + " " +line.id + " " +line.numobj + " " +line.bytes + " " +
        line.op + " " + line.ok);*/
 
       while ((s = reader.readLine()) != null){

            StanfordInputOutput nextline = StanfordInputOutput.readLine(s);
 
            boolean alreadyIndexed = false;

/*       System.err.println(" "+ nextline.threadID + " " + nextline.t0 + " " + nextline.t1 + " " +nextline.id + " " +nextline.numobj + " " +nextline.bytes + " " +
        nextline.op + " " + nextline.ok);*/

            for (i = 0; i <= usedThreadIndex; i++){
		if (nextline.threadID == threadId[i]){
		    alreadyIndexed = true;     /*the ThreadID is already indexed*/
		    break;
                }
            }
                
            if (!alreadyIndexed){     /*the threadID is new*/
                usedThreadIndex++; 
                if (usedThreadIndex >=  numThreads){
 		   System.err.println("Cannot find the threadId in the array ");
                   System.exit(1);
	        }
                threadId[usedThreadIndex] =  nextline.threadID;               	        
             } 

        /*calculate the cumulative metrics*/
         if (line.ok){     
            if (nextline.op.equals("STR")){
    			totalStoreTime[i] = totalStoreTime[i] + (nextline.t1-nextline.t0);
  			totalStoreOps[i] ++;
    			totalStoreBytes[i] =totalStoreBytes[i] + nextline.bytes;
	    }
            else if (nextline.op.equals("RTV")){
			totalRetrieveTime[i] = totalRetrieveTime[i]+ nextline.t1-nextline.t0;
  			totalRetrieveOps[i] ++;
    			totalRetrieveBytes[i] =totalRetrieveBytes[i]+ nextline.bytes;
	    }
            else if (nextline.op.equals("QRY")){
			totalQueryDelay[i] =totalQueryDelay[i] + nextline.t1-nextline.t0;
  			totalQueryOps[i] ++;
	    }

            else {
 		 System.err.println("Cannot find the Op Type ");
	    }
          } 
          s = null;
            
        }     /*end of while*/  
    	 
/*	 System.err.println("All the data have been read, start calaculation");*/

       
        /* calculate the average metrics*/


	for (i=0; i< numThreads; i++){

 /*           System.err.println("totalStoreTime="+totalStoreTime[i]+" "+"totalStoreOps="+ totalStoreOps[i]
              +" "+ "totalStoreBytes=" + totalStoreBytes[i]);


            System.err.println("totalRetrieveTime="+totalRetrieveTime[i]+" "+"totalRetrieveOps="+ totalRetrieveOps[i]
              +" "+ "totalRetrieveBytes=" + totalRetrieveBytes[i]);


            System.err.println("totalQueryDelay="+totalQueryDelay[i]+" "+"totalQueryOps="+ totalQueryOps[i]);
*/

	    if (totalStoreTime[i]!=0)   
		aveStoreBytesPerSec[i] = 1000*totalStoreBytes[i]/totalStoreTime[i];
            if (totalRetrieveTime[i]!=0)  
		aveRetrieveBytesPerSec[i] = 1000*totalRetrieveBytes[i]/totalRetrieveTime[i]; 
            if (totalQueryOps[i]!=0)  
		aveQueryDelay[i] = totalQueryDelay[i]/totalQueryOps[i];

            storeBytesPerSec = storeBytesPerSec + aveStoreBytesPerSec[i];
	    retrieveBytesPerSec = retrieveBytesPerSec + aveRetrieveBytesPerSec[i]; 
            queryDelay = queryDelay + aveQueryDelay[i]; 

            System.err.println ("+++++++++++++++++++++++++++++");   
            System.err.println (hostName + " " + "Test results");        

            System.err.println(hostName + " " + "ThreadID: " + threadId[i]);
	    System.err.println(hostName + " " + "Total Store Ops:  " + totalStoreOps[i]);
            System.err.println(hostName + " " + "Toal Store Bytes:" + totalStoreBytes[i]/1000000 + "MB");
            System.err.println(hostName + " " + "Toal Store Time:" + totalStoreTime[i]/1000 + "sec");
            System.err.println(hostName + " " + "Average Store Bytes/sec:" + aveStoreBytesPerSec[i]/1000+"kB/s");

	    System.err.println(hostName + " " + "Total Retrieve Ops:  " + totalRetrieveOps[i]);
            System.err.println(hostName + " " + "Toal Retrieve Bytes:" + totalRetrieveBytes[i]/1000000 + "MB");
            System.err.println(hostName + " " + "Toal Retrieve Time:" + totalRetrieveTime[i]/1000+ "sec");
            System.err.println(hostName + " " + "Average Retrieve Bytes/sec:" + aveRetrieveBytesPerSec[i]/1000+"kB/s");

	    System.err.println(hostName + " " + "Total Query Ops:"  + totalQueryOps[i]);
            System.err.println(hostName + " " + "Toal Query Delay:" + totalQueryDelay[i]+ "ms");
            System.err.println(hostName + " " + "Average Query Delay: " + aveQueryDelay[i]+"ms");
       }

       writer.println("+++++++++++++++++++++++++++++");
       writer.println ("Results for "+ numThreads+" "+"threads on"+" "+ hostName);
       writer.println ("Store Bytes/sec=" + storeBytesPerSec/1000+ "kB/s");
       writer.println ("Retrieve Bytes/sec=" + retrieveBytesPerSec/1000+ "kB/s");
       writer.println ("Query Delay=" + queryDelay/numThreads+"ms");  

       writer.flush();  
   } /*end of analyze*/

} /*end of class*/
