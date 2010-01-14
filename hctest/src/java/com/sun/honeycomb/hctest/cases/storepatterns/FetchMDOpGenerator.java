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

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class FetchMDOpGenerator extends OperationGenerator {
	
	protected long maxFetchCount = 0;
	Audit auditor = null;
	Vector oids = new Vector();
	
	protected boolean recycle_oids = false;
	private int oid_index = 0;

	public final void shutDownHook() {

		Log.DEBUG("Unlocking OIDS");
		for(int i=0; i < oids.size(); i++)
			try {
				auditor.freeLock((String)oids.get(i));
			} catch (Throwable e) {
				Log.DEBUG("Exceptions unlocking oids: " + Log.stackTrace(e));
			}
	}
	
	public FetchMDOpGenerator() throws Throwable {
		super();
		
		maxFetchCount=Settings.getSize(Settings.MAXFETCHROWCOUNT,
                		Settings.MAXFETCHROWCOUNT_DEFAULT);
		
		auditor = getAuditor();
		
		String recycle = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_RECYCLE_OIDS);
		
		if (recycle != null)
			recycle_oids = true;

	}
	
	private synchronized void updateOIDS() throws Throwable{
		if (oids.size() == 0) {
			Log.INFO("Retrieving oids from database");
			if (!isLocking())
				oids.addAll(auditor.getObjectsNoLock(maxFetchCount, getMinFileSize(), getMaxFileSize()));
			else
				oids.addAll(auditor.getAndLockRetrievableObjects(maxFetchCount, OperationGenerator.FETCH_MD_LOCK_NAME, getMinFileSize(), getMaxFileSize()));
		} 
	}
	
	protected final void setupOperation(Operation op) throws HoneycombTestException{
		try {		
			updateOIDS();
			if (oids.size() != 0){
				if (recycle_oids)
					op.setOID((String)oids.get(oid_index++%oids.size()));
				else 
					op.setOID((String)oids.remove(0));
			}
			else
				op.setRequestedOperation(Operation.NONE);				
		} catch (Throwable e) {
			throw new HoneycombTestException(e);
		}
	}
	
	public Operation getOperation(ArrayList fileSizes) throws HoneycombTestException {
		Operation op = new Operation(Operation.METADATAFETCH);	
		setupOperation(op);
		return op;	
	}
}
