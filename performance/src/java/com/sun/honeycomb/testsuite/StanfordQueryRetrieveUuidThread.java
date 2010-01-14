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
import java.util.Random;
import com.sun.honeycomb.test.util.RandomUtil;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.ObjectIdentifier;

/*This class do query on uuid, and then retrieve the object */

public class StanfordQueryRetrieveUuidThread extends Thread 
{

  private NameValueObjectArchive archive;
  private long runtimeMillis;

  private Random rand; 
  
  public StanfordQueryRetrieveUuidThread(NameValueObjectArchive archive,
			     	   long runtimeMillis)
  {
    this.archive = archive;
    this.runtimeMillis = runtimeMillis;
  }

  public void run()
  {
    long t0;
    long t1;
    long t2;
    long sizeBytes; 
    long startTime = System.currentTimeMillis();
    long uuid;
    long uuidmod;
    long mdsize;
    long lastuuid;
    long DOuuid;
    long lastDOuuid;
    boolean  retrieveok = false;
    long threadID = Thread.currentThread().getId();
    int resultsGroupSize = 1;  /*uuid should be unique*/

    rand = new Random(threadID + (long) this.hashCode() + System.currentTimeMillis());
 
    StanfordIdClues getmdsize = new StanfordIdClues();
    mdsize = getmdsize.getMdsize();

    DOuuid = getLastVerifiedDOuuid();   
    lastDOuuid = getLastDOuuid();
 
    while ((runtimeMillis < 0 || runtimeMillis > (System.currentTimeMillis() - startTime))
      && DOuuid < lastDOuuid){ 

       uuid = getFirstUuid(DOuuid);
       lastuuid = getLastUuid(DOuuid);

       while (uuid <= lastuuid){
 
           StanfordIdClues stanfordIdClues = new StanfordIdClues(uuid);
           sizeBytes = stanfordIdClues.getObjSizeForUuid();
       
           System.err.println("Thread"+ " " + threadID + " "+ "Query and Retrieve uuid = " + uuid + 
             " "+ "sizeBytes=" + sizeBytes);
      
           String query = generateQuery2(String.valueOf(uuid));

           long totalfetches = 0;

           try {
        
              t0 = System.currentTimeMillis();
	      QueryResultSet matches = archive.query(query, resultsGroupSize);
	      t1 = System.currentTimeMillis();
 
              while (matches.next()) {

	      /*retrieve the object*/ 
	          totalfetches++;
                  StanfordRetrieveOid stanfordRetrieveOid =new 
                               StanfordRetrieveOid(archive,matches.getObjectIdentifier(),sizeBytes);
                  stanfordRetrieveOid.retrieve(); 
                  retrieveok = stanfordRetrieveOid.retrieveOk();
          
	      }          

              t2 = System.currentTimeMillis();


 	     /* performance results for query  */

    	      StanfordInputOutput.printLine(threadID, 
				      t0,
	   		              t1,
			              uuid,
			              totalfetches,	
			              sizeBytes,
			              StanfordInputOutput.QUERY,
			      	      totalfetches==1);

              /* performance results for retrieval  */
 
              StanfordInputOutput.printLine(threadID, 
				      t1,
	   		              t2,
			              uuid,
			              totalfetches,	
			              totalfetches*sizeBytes,
			              StanfordInputOutput.RETRIEVE,
			      	      retrieveok);

         } catch (Throwable throwable) {
   	      System.err.println("An unexpected error has occured");
   	      throwable.printStackTrace();
         }

         uuid = uuid +1;
       }

       DOuuid = getLastVerifiedDOuuid();
     } 
  }
 
  /*define query*/ 

  private String generateQuery2(String id) {
 
    return  attr("sdr.uuid") + "= " + val(id);
  } 

  private String attr(String attrName) 
  {
      return "\"" + attrName + "\"";
   }

  

/* Value in query must be in single quotes 
   */


  private String val(String value) {
    return "'" + value + "'";
  }
  

  /*generate one random uuid. */

  private long getLastDOuuid(){

     long lastDOuuid = 0;

     File f1  = new File("/opt/performance/lib/last_douuid_file");
     
     String line = null;

     /*read the last_DOuuid from file*/
     /*DOuuid and uuid start from same number, so the first_douuid_file can be used to */
     /*figure out the range of existing id for both uuid and DOuuid*/

     try {

         BufferedReader bufRead = null;	
         bufRead = new BufferedReader(new FileReader(f1));
         line = bufRead.readLine();
         bufRead.close();
         lastDOuuid = Long.parseLong(line);
         
     }catch (FileNotFoundException e){
         System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
     }catch (IOException e) {
         System.err.println("Caught IOException: "  +  e.getMessage());
     } 
     
     return (lastDOuuid);
   }

    private long getLastVerifiedDOuuid(){

     long lastVDOuuid = 0;

     File f3  = new File("/opt/performance/lib/last_douuid_file_verified");

     String line = null;

     try {
           BufferedReader bufRead = null;
           bufRead = new BufferedReader(new FileReader(f3));
           line = bufRead.readLine();
           lastVDOuuid = Long.parseLong(line);
           bufRead.close();

      }catch (FileNotFoundException e){
            System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
      }catch (IOException e) {
             System.err.println("Caught IOException: "  +  e.getMessage());
      }


  /*update the last_douuid_file_verified*/

     if (lastVDOuuid < getLastDOuuid())lastVDOuuid++;

     try {

        PrintWriter printWrit = null;
        printWrit = new PrintWriter(new FileOutputStream(f3));

        printWrit.println (""+lastVDOuuid);
        printWrit.close();

     }
     catch (FileNotFoundException e){
            System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
     }
     catch (IOException e) {
        System.err.println("Caught IOException: "  +  e.getMessage());
     }

     return (lastVDOuuid);
 
   }
   
   private long getFirstUuid(long id){

     StanfordIdClues stanfordIdClues = new StanfordIdClues(id);

     return (stanfordIdClues.getFirstUuidForDOuuid());


   }

   private long getLastUuid(long id){

     StanfordIdClues stanfordIdClues = new StanfordIdClues(id+1);

     return (stanfordIdClues.getFirstUuidForDOuuid()-1);


   }
 
}
