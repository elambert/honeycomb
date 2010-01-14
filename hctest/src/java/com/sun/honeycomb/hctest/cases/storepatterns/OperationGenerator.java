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

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;

public abstract class OperationGenerator {
	
	public static final String FETCH_LOCK_NAME="FETCH";
	public static final String DELETE_LOCK_NAME="DELETE";
	public static final String FRESH_DELETE_LOCK_NAME = "FRESHDELETE";
	public static final String FRESH_FETCH_LOCK_NAME = "FRESHFETCH";
	public static final String FETCH_MD_LOCK_NAME="FETCHMD";
	public static final String ADD_MD_LOCK_NAME="MDSTATIC";
	public static final String QUERY_MD_LOCK_NAME="QUERYMD";
	
	protected long RANDOM_SELECTION_TYPE = 0;
	protected long ROUND_ROBIN_SELECTION_TYPE = 1;
	
	protected int indexCounter = 0;
	
	protected long indexSelectionType = ROUND_ROBIN_SELECTION_TYPE;
	
	private long maxFileSize = 0;
	private long minFileSize = 0;
	
	private static boolean locking = false;
	
	public abstract void shutDownHook();	
	public OperationGenerator(){
		
		Runtime.getRuntime().addShutdownHook(new Thread(){
    		public void run() {
    			shutDownHook();
    		}});
		
		String oidSelectionTypeString = Settings.getString(Settings.SELECTIONTYPE,
				Settings.SELECTIONTYPE_DEFAULT);

		if (oidSelectionTypeString.equalsIgnoreCase("random")){
			Log.INFO("OperationGenerator::Using Random Selection Type.");
			indexSelectionType = RANDOM_SELECTION_TYPE;
		} else {
			// Default behaviour for other cases...
			Log.INFO("OperationGenerator::Defaulting Selection Type to Round Robin Selection.");
			indexSelectionType = ROUND_ROBIN_SELECTION_TYPE;
		}
	}
	
	public static Audit getAuditor() throws Throwable{
		String cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
		
		if (cluster == null)
			throw new HoneycombTestException("Set property cluster in order to enable auditing and correct operation of the OperationGenerator class.");
		
		return Audit.getInstance(cluster);
	}
	
	public abstract Operation getOperation(ArrayList fileSizes) throws HoneycombTestException;

	protected final int getIndex(int size) throws HoneycombTestException {
		// Test against zero because if size == 0 then we can't divide by it...
		if (size > 0) {
			if (indexSelectionType == ROUND_ROBIN_SELECTION_TYPE){
				return indexCounter++%size;
			} else {
				return RandomUtil.randIndex(size);
			}
		} else 
			return 0;
	}

	public static void verifyAuditingAvailable() throws HoneycombTestException{
		try {
			getAuditor();
		} catch (Throwable e) {
			throw new HoneycombTestException(e);
		}    
	}
	
	public long getMaxFileSize() {
		return maxFileSize;
	}
	
	public void setMaxFileSize(long maxFileSize) {
		this.maxFileSize = maxFileSize;
	}
	
	public long getMinFileSize() {
		return minFileSize;
	}
	
	public void setMinFileSize(long minFileSize) {
		this.minFileSize = minFileSize;
	}
	
	public static void setLocking() {
		locking = true;
	}
	
	public static boolean isLocking(){
		return locking;
	}
}
