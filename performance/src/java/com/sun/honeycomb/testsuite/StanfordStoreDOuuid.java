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
import java.util.Random;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.test.util.RandomUtil;

public class StanfordStoreDOuuid 
{
  private NameValueObjectArchive archive;
  private long DOuuid;
  private long threadID;
  private Random rand;
  
  public StanfordStoreDOuuid(NameValueObjectArchive archive,
			    long DOuuid, long threadID)
  {
    this.archive=archive;
    this.DOuuid = DOuuid;
    this.threadID = threadID;
  }
  
  public void run()
  {
    long startTime = System.currentTimeMillis();
    long uuid;
    long uuidfake;
    long sizeBytes;
    long numberofObjects;
    long mdsize;
    long t0, t1;
    long bytes;	
    long isMetadata;
    long SDRFile;
    String version;
    int resultsGroupSize = 1;  /*uuid should be unique*/

      
    rand = new Random(System.currentTimeMillis());

    SDRFile = isSDRFile();
    version = getVersion();

    StanfordIdClues getmdsize = new StanfordIdClues();

    mdsize = getmdsize.getMdsize();

    StanfordIdClues stanfordIdClues = new StanfordIdClues(DOuuid);

    sizeBytes = stanfordIdClues.getObjSizeForDOuuid();
    numberofObjects =  stanfordIdClues.getObjNumForDOuuid();
    
    uuid = stanfordIdClues.getFirstUuidForDOuuid();

    StoreChannel storeChannel = new StoreChannel();

    System.err.println("Start to store  DO " + DOuuid +  " "  + "NumObj= " +  numberofObjects + "  " + "sizeBytes = "+ sizeBytes + "  " + "FirstUuid=" + uuid);
    
     /* Objects from same DO have same DOuuid, different uuid, and countdown from numberofObjects to 1 */

      t0 = System.currentTimeMillis();

      for (int j = 0 ; j < numberofObjects; j++){

        
       /* Before insert, customer does a query to make sure their UUID to be inserted is not already stored.
       /* Here in simulation, we generate a fake UUID that is known to be not stored, and query for it. 
       /*first run a query on a fake uuid*/

       try{

          uuidfake = uuid + 100;  /*generate one uuid that does not exist */
          String query = generateQuery2(String.valueOf(uuidfake));
          QueryResultSet matches = archive.query(query, resultsGroupSize);
  
        } 

        catch (Throwable throwable) {
   	  throwable.printStackTrace();
        }

        storeChannel.reset(sizeBytes);
 
        try {
       
          SystemRecord systemRecord;
  
          isMetadata = 0; 

    	  NameValueRecord metadata = archive.createRecord();
	  StanfordMetadata.addMetadata(metadata, DOuuid, uuid, isMetadata, SDRFile, version);
	
	  systemRecord = archive.storeObject(storeChannel, metadata);

         } 
         catch (Throwable throwable) {
	    System.err.println("An unexpected error has occured when storing" + DOuuid);
	    throwable.printStackTrace();
         }
        
         uuid++;


      }  /*end of for*/
 
      /* Store 1 MD file for each DO, size: 50k xml; same DOuuid as data, different uuid */

      storeChannel.reset(mdsize);
   
      try {
       	
         SystemRecord systemRecord;
 
         isMetadata = 1; 

    	 NameValueRecord metadata = archive.createRecord();
	 StanfordMetadata.addMetadata(metadata, DOuuid, uuid, isMetadata, SDRFile, version);
	 
	 systemRecord = archive.storeObject(storeChannel, metadata);

       } 
       catch (Throwable throwable) {
	  System.err.println("An unexpected error has occured when storing md");
	  throwable.printStackTrace();
       }

       t1 = System.currentTimeMillis();

       bytes = numberofObjects*sizeBytes + mdsize;
       StanfordInputOutput.printLine(threadID, 
					 t0,
	   		                 t1,
			            	 DOuuid,
			      		 numberofObjects,	
			      		 bytes,
			        	 StanfordInputOutput.STORE,
			      		 true);

      System.err.println ("Store DO " + " " + DOuuid + " " + "Done");

  }/*end of run*/
  
   /*99.99% is SDR File*/

  private long isSDRFile (){
  
  long isSDR = 1;
  if (getRandom(0, 9999)<1) isSDR = 0;
  return isSDR;

  }

   /*90% version "A", 5% version "B", 5% version "C" */

  private String getVersion () {
    
    String version;
    long random = getRandom(0, 99); 
    if (random<90) version = "A";
    else if (random>95) version = "B";
    else version = "C";
    return version;
  
  }



  /*define query*/ 

  private String generateQuery2(String id) {
 
    return  attr("sdr.uuid") + "= " + val(id)
              +"AND"+ 
            attr("sdr.isMetadata") + "= " + '0' 
              +"AND"+ 
            attr("sdr.isSDRFile")  + "= " + '1';
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

  private long getRandom(int min, int max){    	
    return (min + (Math.abs(rand.nextLong())%(max - min + 1)));
  }
 

}
