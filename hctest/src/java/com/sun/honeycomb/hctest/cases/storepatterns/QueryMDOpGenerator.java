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
import java.util.Vector;

import com.sun.honeycomb.hctest.util.AuditDBClient;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

/*
 * Dummy Query Metadata OperationGenerator
 * creates dumb MD objects to associate with existing Objects.
 */
public class QueryMDOpGenerator extends MDStaticOpGenerator {
	
	private Vector queries = new Vector();
	private int query_index = 0;
	private String[] mdtypes;
	
	public QueryMDOpGenerator() throws Throwable {
		String md_types = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_MD_TYPES);
		
		if (md_types != null)
			mdtypes = md_types.split(",");
		else
			mdtypes = (AuditDBClient.MD_DOUBLE_TABLE + "," + AuditDBClient.MD_LONG_TABLE +  "," + AuditDBClient.MD_STRING_TABLE ).split(",");
	}
	
	protected final void setupQuery(Operation op) throws HoneycombTestException{
		try {		
			updateQueries();
			if (queries.size() != 0){
				String query = null;
				if (recycle_oids)
					query = (String)queries.get(query_index++%queries.size());
				else 
					query = (String)queries.remove(0);
				Log.DEBUG("Query: " + query);
				op.setQuery(query);
			}
			else
				op.setRequestedOperation(Operation.NONE);				
		} catch (Throwable e) {
			throw new HoneycombTestException(e);
		}
	}
	
    private synchronized void updateQueries() throws Throwable{
		// no oids... we need queries.. :)
    	if (queries.size() == 0) {
			Log.INFO("Retrieving queries from database, be patient...");
			for (int i = 0; i < mdtypes.length; i++)
				queries.addAll(auditor.getSimpleQueries(mdtypes[i],maxFetchCount,getMinFileSize(),getMaxFileSize()));
		}     	 	
	}
	
	public Operation getOperation(ArrayList fileSizes) throws HoneycombTestException {
		Operation op = new Operation(Operation.QUERYMETADATA);
		setupQuery(op);
		return op;	
	}
}
