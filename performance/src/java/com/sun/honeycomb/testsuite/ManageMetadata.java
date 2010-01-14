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



import java.net.InetAddress;
import java.util.Random;

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.test.util.RandomUtil;

public class ManageMetadata 
{

  private static int objCount = 0;
  private static int objId;

  private static Random rand = new Random(System.currentTimeMillis());

  
  private static int user_count = 0;
  private static String[] users = {"Ana","Bob","Cathy","Donald","Eugene","Frank","George",
			    "Henry","Irine","Joe","Karol"};
  
  private static int dir_count = 0;
  private static String[] directories = {"alpha","beta","gamma","delta","epsilon","zeta",
				  "eta","theta","lota","kappa","lambda","mu","nu",
				  "xi","omicron","pi","rho","sigma","tau","upsilon",
				  "phi","chi","psi","omega"};

    private static String hostname;

    static {
	try {
	    hostname = InetAddress.getLocalHost().getHostName();
	}
	catch (Throwable t) {
	    t.printStackTrace();
	    System.err.println("error: unable to determine local hostname.");
	    System.err.println("setting hostname attribute to 'Default Host'");
	    hostname = "Default Host";
	}
    }
  
  public static String addMetadata(NameValueRecord metadata,
				 long startTime,
				 long storeCount) {
    String uid = null;
    /* random string to add to end of formerly static values */
    String randappend = null;


    try {
      synchronized (ManageMetadata.class) {
	objId = objCount++;
      }

      uid = "Stress-" + hostname + "." + objId + "-" + startTime + "." + storeCount;
      randappend = "" + System.currentTimeMillis() + getRandom(1024);
      
      /*Metadata added similar to Blockstore MD */
      metadata.put("perf_qa.date",
                   new Long(System.currentTimeMillis()).longValue());
      metadata.put("perf_qa.User_Comment", "Big user comment..." + randappend);
      metadata.put("perf_qa.iteration", storeCount);
      metadata.put("perf_qa.test_id","Aggregate Test" + randappend);
      metadata.put("perf_qa.user", users[user_count++%users.length]);
      metadata.put("perf_qafirst.timestart", startTime);
      metadata.put("perf_qafirst.client", hostname);
      metadata.put("perf_qatypes.stringlarge", RandomUtil.getRandomString(512));
      
      /* metadata added to match the new blockstore - wasn't there in 
	 previous tests */
      metadata.put("perf_qatypes.doublefixed", 
                   new Double(Math.random()).doubleValue());
      metadata.put("perf_qatypes.doublelarge", 
                   new Double(Math.random() * Math.random() * 
                              System.currentTimeMillis()).doubleValue());

      metadata.put("perf_qafirst.first",
                   directories[getRandom(directories.length)]);
      metadata.put("perf_qafirst.second",
                   directories[getRandom(directories.length)]);
      metadata.put("perf_qafirst.third",
                   directories[getRandom(directories.length)]);
      metadata.put("perf_qafirst.fourth",
                   directories[getRandom(directories.length)]);
      metadata.put("perf_qafirst.fifth",
                   directories[getRandom(directories.length)]);
      metadata.put("perf_qafirst.sixth",
                   directories[getRandom(directories.length)]);
      
      /* One additional metadata field to fit Josh's infrastructure*/
      metadata.put("system.test.type_string", uid);
      
    }    
    catch (com.sun.honeycomb.test.util.HoneycombTestException hte) {
      System.out.println(hte.getMessage());
    }
    return uid;
  }
  
  private static int  getRandom(int max){    	
    return (Math.abs(rand.nextInt())%max);
  }

}
