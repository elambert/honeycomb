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



import java.io.*;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;

/*This class stores DO, then query and retrieve the DO just retored */

public class StanfordIngestThread extends Thread
{
  private NameValueObjectArchive archive;
  private long runtimeMillis;
  
  public StanfordIngestThread(NameValueObjectArchive archive,
		            long runtimeMillis)
  {
    this.archive=archive;
    this.runtimeMillis=runtimeMillis;
  }
  
  public void run()
  {
    long startTime = System.currentTimeMillis();
    long DOuuid;
    long sizeBytes;
    long threadID = Thread.currentThread().getId();
       
    while (runtimeMillis < 0 || runtimeMillis > (System.currentTimeMillis() - startTime)) {

      /*Sequentially generate DOuuid  */
    
      DOuuid = getDOuuid(); 

      StanfordStoreDOuuid stanfordStoreDOuuid = new StanfordStoreDOuuid(archive,DOuuid,threadID);
      stanfordStoreDOuuid.run();

      StanfordQueryRetrieveDOuuid stanfordQueryRetrieveDOuuid = new StanfordQueryRetrieveDOuuid(archive,DOuuid,threadID);
      stanfordQueryRetrieveDOuuid.run();

    }/* end of while*/


  }/*end of run*/
  

 
  private long getDOuuid(){

     long lastid = 0;
     String line = null;
     File f;

     f  = new File("/opt/performance/lib/last_douuid_file");
   

     try {
	   BufferedReader bufRead = null;	
           bufRead = new BufferedReader(new FileReader(f));
           line = bufRead.readLine();
           lastid = Long.parseLong(line);
           bufRead.close();
         
      }catch (FileNotFoundException e){
            System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
      }catch (IOException e) {
             System.err.println("Caught IOException: "  +  e.getMessage());
      } 
    

  /*update the last_douuid_file*/

     lastid++;

     try {

        PrintWriter printWrit = null;
        printWrit = new PrintWriter(new FileOutputStream(f));
        
        printWrit.println (""+lastid); 
        printWrit.close();
   
     }
     catch (FileNotFoundException e){
            System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
     }
     catch (IOException e) {
        System.err.println("Caught IOException: "  +  e.getMessage());
     } 

     return (lastid); 
  }

}
