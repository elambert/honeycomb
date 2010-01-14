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

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.test.util.HoneycombTestException;

public class Operation {
	
    public static final long NONE=0;
    public static final long DATASTORE=1;
    public static final long DATAFETCH=2;
    public static final long PAUSE=3;
    public static final long DELETE=4;
    public static final long METADATASTORE=5;
    public static final long METADATAFETCH=6;
	public static final long AUDITOBJECT = 7;
	public static final long DATASTOREWITHMD = 8;
	public static final long QUERYMETADATA = 9;
	
    private String oid = null;
    private long _storeSize;
    private long _pauseLength;
    // Set to -1 so it doesn't match with NONE
    private long _opRequested = -1;
    private CmdResult _result = null;
    private ArrayList thrown = new ArrayList();
    
    private HashMap mdMap = null;
    
    private String query = null;

    /**
       Creates an operation with one (and only one) operation
       set requested. This can be a data store, a metadata store 
       (not yet fully defined) or a pause. Delete and none are
       currently no-ops with undefined behaviour.
     */
    Operation (long opTypes) {            
        setRequestedOperation(opTypes);
    }        

    /**
       request an operation - only one may be set at a time.
     */
    public void setRequestedOperation(long op) {
        _opRequested=op;
    }
    
    /**
        returns teh bitfirls with a single bit set high for any given operation.
     */
    long getRequestedOperation() {
        return _opRequested;
    }

    CmdResult getResult() {
        return _result;
    }
    
    void setResult(CmdResult result) {
        _result = result;
    }  
    
    void setPauseLength(long length) throws HoneycombTestException{
        if(_opRequested != PAUSE) {
            throw new HoneycombTestException("attempted to set a pause length when the operation isn't a pause.");
        }
        _pauseLength=length;
    }

    long getPauseLength() {
        return _pauseLength;
    }

    void setStoreSize(long length) {
        _storeSize=length;
    }

    long getStoreSize() {
        return _storeSize;
    }

	public String getOID() {
		if (getRequestedOperation() == DATASTORE || getRequestedOperation() == DATASTOREWITHMD){
			if (getResult() != null)
				return getResult().dataoid;
			else 
				return "NO_OID";
		}
		return oid;
	}
	
	public void setOID(String oid) {
		this.oid = oid;
	}

	public boolean succeeded() {
		if (_result == null)
			return false;
		else {
			if (!_result.pass)
				return false;
		}
		return true;
	}
    
    public String getName(){
    	if (getRequestedOperation() == Operation.DATASTORE)
    		return "STORE";
    	else if (getRequestedOperation() == Operation.DATAFETCH)
    		return "FETCH";
    	else if (getRequestedOperation() == Operation.DELETE)
    		return "DELETE";
    	else if (getRequestedOperation() == Operation.METADATASTORE)
    		return "MDSTORE";
    	else if (getRequestedOperation() == Operation.AUDITOBJECT)
    		return "AUDITOBJ";
    	else if (getRequestedOperation() == Operation.DATASTOREWITHMD)
    		return "STOREWITHMD";
    	else if (getRequestedOperation() == Operation.METADATAFETCH)
    		return "FETCHMD";
    	else if (getRequestedOperation() == Operation.QUERYMETADATA)
    		return "QUERYMD";
    	else 
    		return "UNKNOWN";
    }

	public void setThrown(ArrayList thrown) {
		this.thrown = thrown;
	}

	public ArrayList getThrown() {
		return thrown;
	}
	
	public void setMetaData(HashMap mdMap){
		this.mdMap = mdMap;
	}

	public HashMap getMetaData() {
		return mdMap;
	}
	
	public String getQuery(){
		return query;
	}
	
	public void setQuery(String query){
		this.query = query;
	}
}
