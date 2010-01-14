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



import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.ObjectIdentifier;

/*This class do query on DOuuid, then retrieve all the objects belong to the DOuuid*/

public class StanfordQueryRetrieveDOuuid 
{
  private NameValueObjectArchive archive;
  private long DOuuid;
  private long threadID;

  public StanfordQueryRetrieveDOuuid(NameValueObjectArchive archive,
			     	   long DOuuid, long threadID)
  {
    this.archive = archive;
    this.DOuuid = DOuuid;
    this.threadID = threadID;

  }

  public void run()
  {
    long t0 = 0;
    long t1 = 0;
    long t2 = 0;
    String query = null;
    long totalfetches = 0;
    long totalBytes = 0;
    long sizeBytes; 
    long expectedobjects;
    int resultsGroupSize = 1500;  /*The maximal # of objects for an DOuuid in Stanford case*/

    /* call StanfordIdClues class to get the size and number of objects according to the DOuuid */

    StanfordIdClues stanfordIdClues = new StanfordIdClues(DOuuid);

    sizeBytes = stanfordIdClues.getObjSizeForDOuuid();
    expectedobjects =  stanfordIdClues.getObjNumForDOuuid();

    System.err.println("Start to Q/R DO " + DOuuid +  " "  + "expobjs= " +  expectedobjects + "  " + "sizeBytes = "+ sizeBytes);

    query = generateQuery1(String.valueOf(DOuuid));

    try {
        
        t0 = System.currentTimeMillis();

	QueryResultSet matches = archive.query(query, resultsGroupSize);

	t1 = System.currentTimeMillis();

	while (matches.next()) {
	   totalfetches++;

            /*retrieve  objects*/
	  
           StanfordRetrieveOid stanfordRetrievenext =new StanfordRetrieveOid(archive, matches.getObjectIdentifier(), sizeBytes);
           stanfordRetrievenext.retrieve();

           totalBytes = totalBytes + sizeBytes;
	}
        
        t2 = System.currentTimeMillis(); 
         
    } catch (Throwable throwable) {
	System.err.println("An unexpected error has occured");
	throwable.printStackTrace();
    }
    
    /* check if the number of objects is correct */  
    
    if (expectedobjects != totalfetches) System.err.println("Number of Objects not matched");
    
    /* performance results for query  */
 
    System.err.println("Q/R DO " + DOuuid +  " "  + "Done");

    StanfordInputOutput.printLine(threadID, 
				      t0,
	   		              t1,
			              DOuuid,
			              totalfetches,	
			              totalBytes,
			              StanfordInputOutput.QUERY,
			      	      expectedobjects == totalfetches);


  /* performance results for retrieval  */
 
    StanfordInputOutput.printLine(threadID, 
				      t1,
	   		              t2,
			              DOuuid,
			              totalfetches,	
			              totalBytes,
			              StanfordInputOutput.RETRIEVE,
			      	      expectedobjects == totalfetches);
  }
  
  private String generateQuery1(String id) { 
    return  attr("sdr.DOuuid") + "= " + val(id)
              +"AND"+ 
            attr("sdr.isMetadata") + "= " + '0' 
              +"AND"+ 
            attr("sdr.isSDRFile")  + "= " + '1';  
  } 

  private String attr(String attrName)  
  {
      return "\"" + attrName + "\"";  
   }

/* Value in query must be in single quotes    */

  private String val(String value) {
    return "'" + value + "'";
  }  
}
