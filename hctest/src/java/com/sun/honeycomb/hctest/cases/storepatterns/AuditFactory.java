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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.sun.honeycomb.hctest.util.AuditResult;
import com.sun.honeycomb.hctest.util.ConnectionPool;
import com.sun.honeycomb.hctest.util.ConnectionWrapper;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class AuditFactory extends OperationFactory {
   
	private boolean nolocking = false;
	
	private Long countLocks() throws HoneycombTestException{
		Long lockCount;
		try {
			lockCount = new Long(auditor.countLocks(lockid));
		} catch (HoneycombTestException e){
			throw e;
		} catch (Throwable e){
			throw new HoneycombTestException(e);
		}
		return lockCount;
	}
	
	private Long countOIDS(String condition) throws HoneycombTestException{
		Long lockCount;
		try {
			lockCount = new Long(auditor.countOIDs(condition));
		} catch (HoneycombTestException e){
			throw e;
		} catch (Throwable e){
			throw new HoneycombTestException(e);
		}
		return lockCount;
	}
	
	public void init(Suite suite) throws HoneycombTestException {
		
        super.init(suite);
        
        // Always run when auditing ignoring time and any data limits.
        _runMilliseconds = -1;
        _totalDataAllowed = -1;
        _totalFilesAllowed = -1;
        
        String noLocking = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_LOCKING);
        
        if (noLocking != null) nolocking = true;
        
        if (!nolocking) {
	        Long lockCount = countLocks();
	        
	        if (lockCount.intValue() > 0) {
	        	Log.INFO("Attention: " + lockCount + " unlocked OID(s) left from previous run");
	        	Log.INFO("Unlocking these oids...");
	        	try {
					auditor.freeLocks(lockid);
				} catch (SQLException e) {
					throw new HoneycombTestException(e);
				} 
	        }
        }
        
        try {
			startUp();
		} catch (HoneycombTestException e) {
			throw e;
		} catch (SQLException e) {
			throw new HoneycombTestException(e);
		}
		
		self = suite.createTestCase("AuditFactory");
		self.addTag("audit");		
    }
	
	
	private Statement oidStmt = null; 
	private ResultSet oidCursor = null;
	private ConnectionPool connpool = null;
	private ConnectionWrapper conn = null;
	private float totalOIDs = 0;
	
	private float numAudits = 0;
	private long numErrors = 0;
	
	private String lockid = "PARALLEL_AUDIT";
	
	// TODO: clean up this locking/select statements...
	public void startUp() throws HoneycombTestException, SQLException{
    	String cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
    	
    	String run_id = TestRunner.getProperty(Settings.RUN_ID);
    	String oid = TestRunner.getProperty(Settings.OID);
    	
    	String appendString = " where lock is null ";
    	
    	String runString = "";
    	
    	if (run_id != null)
    		runString = "intersect ((select api_oid from op where run_id=" + run_id + " and not api_oid is null)" + 
    					" union (select sr_oid as api_oid from op where run_id=" + run_id + " and not sr_oid is null))";
    	
    	if (oid != null)
    		appendString += " and api_oid='" + oid + "'";
    	
        // Get Connection from connection pool.	
    	connpool = ConnectionPool.getInstance(cluster);
        conn = connpool.getConnection();
        
        oidStmt = conn.createStatement();
        StringBuffer sb = new StringBuffer();
        
        if (!nolocking) {
	        sb.append("update obj set lock='"+ lockid + "' where api_oid in ((select api_oid from obj "+ appendString + ")" + runString + ")");
	        
	        auditor.tryConcurrentUpdate(sb,oidStmt,conn);
	        
	        conn.commit();
			oidStmt.close();
			
			totalOIDs = countLocks().floatValue();
			
			oidStmt = conn.createStatement();		
	        
	        sb = new StringBuffer();
	        sb.append("select api_oid from obj where lock='" + lockid + "'");
        } else {
        	
        	totalOIDs = countOIDS(runString).floatValue();
        	if (run_id == null)
        		sb.append("select api_oid from obj " + appendString);
        	else
        		sb.append("(select api_oid from obj " + appendString + ") " + runString);
        }
        
        oidCursor = oidStmt.executeQuery(sb.toString());   
        
        Log.INFO("Number of objects to audit: " + totalOIDs);
	}
	
	public String getNextOID() throws SQLException{
       	if (oidCursor.next()) {
            return (String) oidCursor.getString(1);                        
        } else {
        	return null;
        }
	}
	
	public void cleanUp(){
		try {
			conn.commit();
			oidStmt.close();			
			oidCursor.close();
			connpool.returnConnection(conn);
		} catch (SQLException e) {
			Log.ERROR("Error cleaning up...");
		}
		connpool.returnConnection(conn);
	}

    public Operation next(int clientHostNum,int threadId) throws HoneycombTestException {
    	Operation op = new Operation(Operation.AUDITOBJECT);
    	
    	String oid;
		try {
			oid = getNextOID();
		} catch (SQLException e) {
			throw new HoneycombTestException("Error retrieving next oid.", e);
		}
    	
    	if (oid == null){
    		// time to end;
    		timeToStop = true;
    		op.setRequestedOperation(Operation.NONE);
    	} else {
    		op.setOID(oid);
    	}
    	
        return op;                      
    }

    public void shutDownHook() {
    	super.shutDownHook();
    	cleanUp();
    	
    	Log.INFO("*** Auditing Stats ***");
    	Log.INFO("Number of Audits:          " + numAudits);
    	Log.INFO("Number of Errors Auditing: " + numErrors);
    	
    	if (numErrors != 0)
    		self.testFailed();
    	else
    		self.testPassed();
    }
   
    public void done(Operation doneOp,int serverNum,int threadId) throws HoneycombTestException {       
        checkForException(doneOp, serverNum, threadId);
        freeLocks(doneOp);
    }
    
    private long counter = 0;
    
    public void checkForException(Operation operation, int serverNum, int threadId) {
    	numAudits++;
    		
    	if (operation.getResult() == null) {
    		numErrors++;
			Log.ERROR(operation.getName() + " FAIL on server:" + serverNum + " thread: " + threadId + " OID: " + operation.getOID() + " no result returned.");
                return;
    	}
    	
    	if (operation.getResult().auditResult == null) {
    		numErrors++;
			Log.ERROR(operation.getName() + " FAIL on server:" + serverNum + " thread: " + threadId + " OID: " + operation.getOID() + " no auditresult returned.");
    	} else {       
    	
	    	if (!operation.getResult().auditResult.pass) {
				numErrors++;
				Log.ERROR(operation.getName() + " FAIL on server:" + serverNum + " thread: " + threadId + " OID: " + operation.getOID());
			} else {
				Log.INFO(operation.getName() + " OK on server:" + serverNum + " thread: " + threadId + " OID: " + operation.getOID());
			}
	    	
	    	if (counter++ == 10){
	    		counter = 0;
				Log.INFO("Percentage of Audit Complete: " + (int)((numAudits/totalOIDs)*100) + "%");
	    	}
	    		
			AuditResult auditresult = operation.getResult().auditResult;
			
	    	if (auditresult.error != null){
	    		for(int i = 0; i < auditresult.error.size(); i++){
	    			Log.ERROR((String)auditresult.error.get(i));
	    		}
	    	}
	    	
	    	if (auditresult.info != null){
	    		for(int i = 0; i < auditresult.info.size(); i++){
	    			Log.INFO((String)auditresult.info.get(i));
	    		}
	    	}
	    	
	    	if (auditresult.warn != null){
	    		for(int i = 0; i < auditresult.warn.size(); i++){
	    			Log.WARN((String)auditresult.warn.get(i));
	    		}
	    	}    	
    	}    	
    	
    	// This will only print out framework bugs...
    	if (operation.getResult().thrown != null && operation.getResult().thrown.size() != 0){
			Log.ERROR(operation.getName() + " FAIL on server:" + serverNum + " thread: " + threadId + " OID: " + operation.getOID() + " with:");	    			
            logExceptions(operation.getResult().thrown);
		}
	}
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append(super.help());
        sb.append("AuditingFactory, properties\n");
        sb.append("\trunid - audit all oids generated during the specified runid\n");
        sb.append("\toid    - audit this specific oid\n");
        return sb.toString();
    }

	public boolean excludeCase() {
		return self.excludeCase();
	}
}

