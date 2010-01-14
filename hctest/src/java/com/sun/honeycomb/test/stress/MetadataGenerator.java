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



package com.sun.honeycomb.test.stress;

import java.util.Random;

import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.util.RandomUtil;
import com.sun.honeycomb.hctest.util.HCUtil;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;

/* 
 * This class generates metadata required to run EMI test.
 * 
 * generateMetadata(..) method is a copy of what's done by 
 * ..../hctest/cases/storepatterns/MDStaticOpGenerator.java for 
 * "Load Blockstore" tool
*/   
public class MetadataGenerator {
    
    private static Random rand;
    private static long timestart = System.currentTimeMillis();
    private static long iteration = 0;
        	
    private static int user_count = 0;
    private static String[] users = {"Ana","Bob","Cathy","Donald","Eugene","Frank","George",
                      "Henry","Irine","Joe","Karol"};
	
    private static int dir_count = 0; 
    private static String[] directories = {"alpha","beta","gamma","delta","epsilon","zeta",
                            "eta","theta","lota","kappa","lambda","mu","nu",
                            "xi","omicron","pi","rho","sigma","tau","upsilon",
                            "phi","chi","psi","omega"};

    public static NameValueRecord generateMetadata(NameValueObjectArchive archive,
            boolean isExtendedMetadata,
            String uid, long sizeBytes, double timeTag) 
    throws Throwable {
        
        NameValueRecord metadata = null;
    
        metadata = archive.createRecord();
        
        // store metadata into the "system.test" namespace
        metadata.put("system.test.type_string", uid);
        metadata.put("system.test.type_long", sizeBytes);
        metadata.put("system.test.type_double", timeTag);   
        
        if (isExtendedMetadata) {  
            
            // store metadata into the perf namespace
            metadata.put("perf_qa.date", new Long(System.currentTimeMillis())); 
            metadata.put("perf_qa.User_Comment", "Big user comment...");
            metadata.put("perf_qa.iteration", new Long(iteration++));
            metadata.put("perf_qa.test_id", "" + RandomUtil.getRandomString(64));
            metadata.put("perf_qa.user",users[user_count++%users.length]);
            
            metadata.put("perf_qafirst.client", HCUtil.getHostname());
            metadata.put("perf_qafirst.first",  getDir());
            metadata.put("perf_qafirst.second", getDir());
            metadata.put("perf_qafirst.third",  getDir());
            metadata.put("perf_qafirst.fourth", getDir());
            metadata.put("perf_qafirst.fifth",  getDir());
            metadata.put("perf_qafirst.sixth",  getDir());
            metadata.put("perf_qafirst.timestart", new Long(timestart));
            
            metadata.put("perf_qatypes.doublefixed", new Double(Math.random()));
            metadata.put("perf_qatypes.doublelarge", new Double(Math.random() * 
                                                                Math.random() * System.currentTimeMillis()));
            metadata.put("perf_qatypes.longsmall", new Long(System.currentTimeMillis()));
            metadata.put("perf_qatypes.longlarge", new Long(System.currentTimeMillis() * 
                                                            System.currentTimeMillis()));
            
            // 512B due to hadb index limitation
            metadata.put("perf_qatypes.stringlarge", RandomUtil.getRandomString(512)); 
        }
        
        return metadata;
    }    
    
    private static int getRandom(int max){ 
        rand = new Random(System.currentTimeMillis());
    	return (Math.abs(rand.nextInt()) % max);
    }

    private static String getDir() {
        return directories[getRandom(directories.length)];
    }
}
