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

 
package com.sun.honeycomb.hctest.cases.storepatterns;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;

/*
 * Dummy MetaData OperationGenerator
 * creates dumb MD objects to associate with existing Objects.
 */
public class MDStaticOpGenerator extends FetchOpGenerator {
	
	long timestart = System.currentTimeMillis();
	long iteration = 0;
        private Random rand;
    
	public MDStaticOpGenerator() throws Throwable {
		super();
                rand = new Random(System.currentTimeMillis());
	}
	
	int user_count = 0;
	String[] users = {"Ana","Bob","Cathy","Donald","Eugene","Frank","George",
			          "Henry","Irine","Joe","Karol"};
	
        int dir_count = 0; // unused, picking randomly instead
	String[] directories = {"alpha","beta","gamma","delta","epsilon","zeta",
			                "eta","theta","lota","kappa","lambda","mu","nu",
			                "xi","omicron","pi","rho","sigma","tau","upsilon",
			                "phi","chi","psi","omega"};

	private synchronized void updateOIDS() throws Throwable{
		if (oids.size() == 0) {
			Log.INFO("Retrieving oids from database");
			if (!isLocking())
				oids.addAll(auditor.getObjectsNoLock(maxFetchCount, getMinFileSize(), getMaxFileSize()));
			else
				oids.addAll(auditor.getAndLockRetrievableObjects(maxFetchCount, OperationGenerator.ADD_MD_LOCK_NAME, getMinFileSize(), getMaxFileSize()));
		} 
	}
    
    public Operation getOperation(ArrayList fileSizes) throws HoneycombTestException {
        Operation op = new Operation(Operation.METADATASTORE);
        op.setMetaData(generateMetadata());		
        setupOperation(op);
        return op;	
    }
    
    public HashMap generateMetadata() throws HoneycombTestException {
        HashMap mdMap = new HashMap();
        
        mdMap.put("client", HCUtil.getHostname()); // Hostname
        mdMap.put("date", new Long(System.currentTimeMillis())); // Current time in milliseconds
        mdMap.put("User_Comment", "Big user comment...");
        mdMap.put("timestart", new Long(timestart));
        mdMap.put("iteration", new Long(iteration++));
        mdMap.put("test_id", "" + Run.getInstance().getId());
        
        mdMap.put("stringlarge", RandomUtil.getRandomString(512)); // 512B due to hadb index limitation
        
        mdMap.put("user",users[user_count++%users.length]);
        
//        mdMap.put("doublefixed", new Double(Math.random()));
//        mdMap.put("doublelarge", new Double(Math.random() * Math.random() * System.currentTimeMillis()));
        // TODO: this is temporary until we fix the audit database for hadnling doubles...
        mdMap.put("longsmall", new Long(System.currentTimeMillis()));
        mdMap.put("longlarge", new Long(System.currentTimeMillis() * System.currentTimeMillis()));
        
        /* Must choose directories at random, cannot cycle through like for users,
         * because cycling results in highly uneven distribution, and our
         * ComplexQuery returns zero results for most first+second+third queries,
         * and a huge number of results for select few combinations.
         * Random should be evenly distributed because it's, well, random.
         */
        mdMap.put("first",  getDir());
        mdMap.put("second", getDir());
        mdMap.put("third",  getDir());
        mdMap.put("fourth", getDir());
        mdMap.put("fifth",  getDir());
        mdMap.put("sixth",  getDir());
        
        return mdMap;
    }

    private int  getRandom(int max){    	
    	return (Math.abs(rand.nextInt()) % max);
    }

    public String getDir() {
        return directories[getRandom(directories.length)];
    }
}
