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
import java.util.HashMap;
import java.util.Random;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.MdGenerator;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

/*
 * Dummy MetaData OperationGenerator
 * creates dumb MD objects to associate with existing Objects.
 */
public class MDRandomOpGenerator extends FetchOpGenerator {
	
    // Use schema to generate MetaData
    private NameValueSchema nvs;
    
    private static int mdfields_int_min = 2; // default to min 2 fields...
    private static int mdfields_int_max = 5; // default to max 2 fields...
    
    private Random random;
    
    public int getMinMDFields(){
    	return mdfields_int_min;
    }
    
    public int getMaxMDFields(){
    	return mdfields_int_max;
    }
 
    public MDRandomOpGenerator() throws Throwable {
        super();	

        String min_mdfields = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_MIN_MD_FIELDS);
        String max_mdfields = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_MAX_MD_FIELDS);
           
        if (min_mdfields != null)
        	mdfields_int_min = new Integer(min_mdfields).intValue();
        
        if (max_mdfields != null)
        	mdfields_int_max = new Integer(max_mdfields).intValue();
        
        NameValueObjectArchive nvoa = new NameValueObjectArchive(TestBed.getInstance().getDataVIP());
        
        nvs = nvoa.getSchema();
        
        random = new Random(System.currentTimeMillis());
    }
    
    private synchronized void updateOIDS() throws Throwable{
		if (oids.size() == 0) {
			Log.INFO("Retrieving oids from database");
			if (!isLocking())
				oids.addAll(auditor.getObjectsNoLock(maxFetchCount, getMinFileSize(), getMaxFileSize()));
			else
				oids.addAll(auditor.getAndLockRetrievableObjects(maxFetchCount, OperationGenerator.ADD_MD_LOCK_NAME, getMinFileSize(), getMaxFileSize()));
		} 
	}
	
    public Operation getOperation(ArrayList fileSizes)
        throws HoneycombTestException {
        Operation op = new Operation(Operation.METADATASTORE);
        HashMap mdMap = null;
    
        MdGenerator mdGenerator = new MdGenerator(nvs);
        int size = getRandom(mdfields_int_min,mdfields_int_max);
        mdGenerator.createRandomFromSchema(size);
        mdMap = mdGenerator.getMdMap();        
     
        op.setMetaData(mdMap);
        setupOperation(op);
        return op;	
    }
    
    private int  getRandom(int min, int max){    	
    	return (Math.abs(random.nextInt())%(max-min) + min);
    }
}
