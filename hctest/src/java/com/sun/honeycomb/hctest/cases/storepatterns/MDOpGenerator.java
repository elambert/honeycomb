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

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.util.MdGenerator;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;


/*
 * Dummy MetaData OperationGenerator
 * creates dumb MD objects to associate with existing Objects.
 */
public class MDOpGenerator extends FetchOpGenerator {

    private MdGenerator mdGenerator = null;

    public MDOpGenerator() throws Throwable {
        super();	

        RandomUtil.initRandom();

        // Use schema to generate MetaData
        NameValueObjectArchive nvoa = new
            NameValueObjectArchive(TestBed.getInstance().getDataVIP());

        try {
            mdGenerator = new MdGenerator(nvoa.getSchema());
        } catch (HoneycombTestException e) {
            // Ignore for now just means that the BLOB_TYPE is
            // not supported
            Log.INFO("Failed to create the metadata " + e);
        }
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

        int index = RandomUtil.randIndex(mdGenerator.getNbAttributes(null));        
        mdGenerator.createRandomFromSchema(index);
        op.setMetaData(mdGenerator.getMdMap());		
        setupOperation(op);
        return op;	
    }
}
