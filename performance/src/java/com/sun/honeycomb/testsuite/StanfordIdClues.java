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
import java.lang.reflect.*;

import java.io.*;

public class StanfordIdClues 
{

  private long[] DOpercent = {70,10,15,5};          /* Percentage for different types of DO */
  private long[] DOpercentaccu = {70,80,95,100} ;
  private long[] DOobjnum = {100,15,10,1500} ;	    /* # of Obj for different types of DO */
  private long[] DOobjbytes = {10485760, 1048576*150, 10485760, 10485760};  /* Obj Size */
  private int DOTYPE = Array.getLength(DOpercent);
  private long mdsize = 50*1024;		     /*Metadata obj size*/
  private long id;

 
  public StanfordIdClues(){}
 
  public StanfordIdClues(long callid){
     this.id = callid;
  }  
    
  public long getMdsize(){
     return mdsize;
  }  

  public long getTotalObjNum(){

     long totalNumObj =0;
     int j;

     for (j=0; j<DOTYPE; j++)
        totalNumObj =  totalNumObj + DOpercent[j]*(DOobjnum[j]+1);   /*plus 1 metadata object for each DO*/   
     
     return totalNumObj;    
  }

  public long getObjSizeForDOuuid(){

     int j=0;
     while (((id-1) % 100) >= DOpercentaccu[j] && j<DOTYPE-1)j++;
     return DOobjbytes[j];
  } 
     

  public long getObjNumForDOuuid(){

     int j=0;
     while (((id-1) % 100) >= DOpercentaccu[j] && j<DOTYPE-1) j++;
     return DOobjnum[j];
  } 


  public long getFirstUuidForDOuuid(){

     long uuid;
     int j= 0;
     long whichRound = (id - getFirstid()-1)/100;
 
     uuid = getFirstid() + 1 + getTotalObjNum()*whichRound;

     while (((id-1) % 100) >= DOpercentaccu[j] && j<DOTYPE-1){
        uuid = uuid +  DOpercent[j]*(DOobjnum[j] +1);
        j++;
     }
     
     if (j==0) uuid = uuid +  (((id-1) % 100))*(DOobjnum[j] +1);
     else uuid = uuid +  ((((id-1) % 100 )-DOpercentaccu[j-1]))*(DOobjnum[j] +1);
     return uuid;
  } 

  public long getObjSizeForUuid(){
 
     long objSize;
     int j= 0;
     long uuidMod = (id - getFirstid()) % getTotalObjNum(); 

     while ( uuidMod > DOpercent[j]*(DOobjnum[j] +1) && j<DOTYPE-1){
        uuidMod = uuidMod -  DOpercent[j]*(DOobjnum[j] +1);
        j++;
     }
    
     if ((uuidMod % (DOobjnum[j] +1))==0) objSize = mdsize; 
     else objSize = DOobjbytes[j];
 
     return objSize;
  }


   private long getFirstid(){

     long firstid = 0;

     File f  = new File("/opt/performance/lib/first_douuid_file");
     
     String line = null;
      
     /*read the first_DOuuid from file*/

     try {

         BufferedReader bufRead = null;	
         bufRead = new BufferedReader(new FileReader(f));
         line = bufRead.readLine();
         bufRead.close();
         firstid = Long.parseLong(line);
         
     }catch (FileNotFoundException e){
         System.err.println("Caught FileNotFoundException: "  +  e.getMessage());
     }catch (IOException e) {
         System.err.println("Caught IOException: "  +  e.getMessage());
     } 
     return firstid; 
  }

}
