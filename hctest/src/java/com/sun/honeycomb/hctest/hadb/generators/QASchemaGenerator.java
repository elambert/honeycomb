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



package com.sun.honeycomb.hctest.hadb.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import com.sun.honeycomb.hctest.cases.storepatterns.MDStaticOpGenerator;
import com.sun.honeycomb.hctest.hadb.MetadataAttribute;
import com.sun.honeycomb.hctest.hadb.schemas.HCQASchema;
import com.sun.honeycomb.hctest.hadb.schemas.MetadataSchema;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.RandomUtil;

public class QASchemaGenerator implements MetadataGenerator {
	
	public QASchemaGenerator () throws Throwable {
		rand = new Random(System.currentTimeMillis());
	}

	public void setAttributeSize(long size) {
		// TODO Auto-generated method stub
	}

	public long getAttributeSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	public Collection generateMetaData() {
		ArrayList list = new ArrayList();
		
		list.add(new MetadataAttribute("client",HCUtil.getHostname()));
		
		list.add(new MetadataAttribute("date", new Long(System.currentTimeMillis()).toString())); // Current time in milliseconds
		list.add(new MetadataAttribute("User_Comment", "Big user comment..."));
		list.add(new MetadataAttribute("timestart", new Long(timestart).toString()));
		list.add(new MetadataAttribute("iteration", new Long(iteration++).toString()));
		list.add(new MetadataAttribute("test_id", "" + getRunID()));
		try {
			list.add(new MetadataAttribute("stringlarge", RandomUtil.getRandomString(512))); // 512B due to hadb index limitation
		} catch (HoneycombTestException hcte) {
			
		}
		
                
		list.add(new MetadataAttribute("first",directories[getRandom(directories.length)]));
		list.add(new MetadataAttribute("second",directories[getRandom(directories.length)]));
		list.add(new MetadataAttribute("third",directories[getRandom(directories.length)]));
        list.add(new MetadataAttribute("fourth",directories[getRandom(directories.length)]));
        list.add(new MetadataAttribute("fifth",directories[getRandom(directories.length)]));
        list.add(new MetadataAttribute("sixth",directories[getRandom(directories.length)])); 
		return list;
	}
	
	public MetadataSchema getSchema() {
		return m_schema;
	}
	
	private String getRunID() {
		if (iteration % 24000 == 0) {
			runID++;
		}
		return "runID" + runID;
	}
	
	private int  getRandom(int max){    	
		return (Math.abs(rand.nextInt())%max);
    }
	
	MDStaticOpGenerator m_gen;
	HCQASchema m_schema = new HCQASchema();
	int user_count = 0;
	public String[] users = {"Ana","Bob","Cathy","Donald","Eugene","Frank","George",
			                 "Henry","Irine","Joe","Karol"};
	
    int dir_count = 0; // unused, picking randomly instead
	public String[] directories = {"alpha","beta","gamma","delta","epsilon","zeta",
			                "eta","theta","lota","kappa","lambda","mu","nu",
			                "xi","omicron","pi","rho","sigma","tau","upsilon",
			                "phi","chi","psi","omega"};
	long timestart = System.currentTimeMillis();
	long iteration = 0;
	private Random rand;
	long runID = 0;
}
