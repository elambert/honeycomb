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



package com.sun.honeycomb.hctest;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import sun.security.action.GetBooleanAction;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.client.QAClient;

import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.hctest.cases.storepatterns.OperationGenerator;
import com.sun.honeycomb.hctest.util.AuditDBClient;
import com.sun.honeycomb.hctest.util.AuditResult;
import com.sun.honeycomb.hctest.util.ConnectionWrapper;
import com.sun.honeycomb.hctest.util.DigestableWriteChannel;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.HCLocale;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

/**
 ** Provides mechanisms for recording operations performed against a cluster
 ** and for querying the expected state of a cluster.
 **
 **/
public class Audit
{
    private static HashMap instances = new HashMap();

    private String auditHost;
    private String cluster;

    private AuditDBClient db;
    private static boolean  threaded = false;

    protected Audit(String host, String cluster)
        throws Throwable
    {
        this.auditHost = host;
        this.cluster = cluster;
        this.db = new AuditDBClient(host, cluster);
    }

    public static Audit getInstance(String host,
                                   String cluster)
        throws Throwable
    {

		String threaded_audit = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_THREADED_AUDIT);
		
		if (threaded_audit != null)
			threaded = true;

        String key = host + cluster;
        Audit instance = null;
        synchronized (instances) {
            instance = (Audit) Audit.instances.get(key);
            if (instance == null) {
            	if (threaded)
            		instances.put(key, (instance = new ThreadedAudit(host, cluster)));
            	else
            		instances.put(key, (instance = new Audit(host, cluster)));
            }
        }
        return instance;
    }
    
    public static Audit getInstance(String cluster) throws Throwable {
        String host = System.getProperty(HCLocale.PROPERTY_DBHOST);
        if (host == null) {
            throw new HoneycombTestException("System property not defined: " +
                                             HCLocale.PROPERTY_DBHOST);
        }
        return getInstance(host, cluster);
    }

    /**
     ** Record a store operation.
     **/
    public void recordStoreOp(int status,
                              long startTime,
                              Long endTime,
                              long api_time,
                              String info,
                              String sha1,
                              NameValueRecord metadata,
                              Long numBytes,
                              String logtag,
                              SystemRecord systemRecord)
        throws Throwable
    {
        String oid = null;
        String linkOID = null;
        Long srSize = null;
        Long srCtime= null;
        Long srDtime = null;
        String srDigest = null;

        if (systemRecord != null) {
            oid = systemRecord.getObjectIdentifier().toString();
            linkOID = 
                QAClient.getLinkIdentifier(systemRecord) == null ? null :
                QAClient.getLinkIdentifier(systemRecord).toString();
            srSize = new Long(systemRecord.getSize());
            srCtime = new Long(systemRecord.getCreationTime());
            srDtime = new Long(systemRecord.getDeleteTime());
            srDigest = 
                HCUtil.convertHashBytesToString(systemRecord.getDataDigest());
        }
        
        // Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
            
        try {
            this.db.insertStoreOp(status,
                                  startTime,
                                  endTime,
                                  api_time,
                                  new Long((long) (Run.getInstance().getId())),
                                  info,
                                  sha1,
                                  metadata,
                                  numBytes,
                                  oid,
                                  linkOID,
                                  srSize,
                                  srCtime,
                                  srDtime,
                                  srDigest,
                                  logtag,
                                  conn);
            if (systemRecord != null) {
                this.db.insertObj(oid, 
                                  null,
                                  sha1, 
                                  metadata,
                                  numBytes.longValue(),
                                  linkOID, 
                                  srCtime.longValue(),
                                  srDtime.longValue(),
                                  false,
                                  conn);
            }
            conn.commit();
        }
        catch (Throwable t) {
            conn.rollback();
            throw t;
        } finally {
        	db.returnConnection(conn);
        }
    }

    /**
     ** Record a retrieve operation.
     **/
    public void recordRetrieveOp(int status,
                                 long startTime,
                                 Long endTime,
                                 long api_time,
                                 String info,
                                 ObjectIdentifier oid,
                                 String sha1,
                                 Long numBytes,
                                 Long offset,
                                 Long length,
                                 String log_tag)
        throws Throwable
    {
    	// Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
        
        try {
        	this.db.insertRetrieveOp(status,
                                     startTime,
                                     endTime,
                                     api_time,
                                     new Long((long) (Run.getInstance().getId())),
                                     info,
                                     oid.toString(),
                                     sha1,
                                     numBytes,
                                     offset,
                                     length,
                                     log_tag,
                                     conn);
        	conn.commit();
        }
        catch (Throwable t) {
        	conn.rollback();
            throw t;
        } finally {
        	db.returnConnection(conn);
        }
    }
    
    public void recordRetrieveMDOp(int status,
		            long startTime,
		            Long endTime,
		            long api_time,
		            String info,
		            ObjectIdentifier oid,
		            Long numBytes,
		            Long offset,
		            Long length,
		            String log_tag)
		throws Throwable
	{
		// Get Connection from connection pool.
		ConnectionWrapper conn = db.getConnection();
		
		try {
		this.db.insertRetrieveMDOp(status,
		                startTime,
		                endTime,
		                api_time,
		                new Long((long) (Run.getInstance().getId())),
		                info,
		                oid.toString(),
		                numBytes,
		                offset,
		                length,
		                log_tag,
		                conn);
		conn.commit();
		}
		catch (Throwable t) {
		conn.rollback();
		throw t;
		} finally {
		db.returnConnection(conn);
		}
	}
    
    public void recordQueryOp(int status, long startTime, Long endTime,
			long api_time, String info, String log_tag) throws Throwable {
		// Get Connection from connection pool.
		ConnectionWrapper conn = db.getConnection();

		try {
			this.db.insertQueryOp(status, startTime, endTime, api_time,
					new Long((long) (Run.getInstance().getId())), info, log_tag,
					conn);
			conn.commit();
		} catch (Throwable t) {
			conn.rollback();
			throw t;
		} finally {
			db.returnConnection(conn);
		}
	}    

    /**
	 * * Record a delete operation.
	 */
    public void recordDeleteOp(int status,
                               long startTime,
                               Long endTime,
                               long api_time,
                               String info,
                               ObjectIdentifier oid,
                               String log_tag,
                               boolean noSuchObjectException)
        throws Throwable
    {
    	// Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
        
        try {
            this.db.insertDeleteOp(status,
                                   startTime,
                                   endTime,
                                   api_time,
                                   new Long((long) (Run.getInstance().getId())),
                                   info,
                                   oid.toString(),
                                   log_tag,
                                   conn);
            if (status == 0) {
                this.db.updateObjDeleted(oid.toString(), conn);
            } else if (status == 1 && !noSuchObjectException) {
                // Deletion ended with an exception other than NoSuchObjectException
            	this.db.updateObjDeletedUnkown(oid.toString(), conn);
            }
            
            // if noSuchObjectException than we craete the op entry for this operation but we don't 
            // touch the object table
            
            conn.commit();
        }
        catch (Throwable t) {
        	conn.rollback();
            throw t;
        } finally {
        	db.returnConnection(conn);
        }
    }

    /**
     ** Record a succesful add metadata operation.
     **/
    public void recordLinkOp(int status,
                             long startTime,
                             Long endTime,
                             long api_time,
                             String info,
                             ObjectIdentifier linkOID,
                             NameValueRecord metadata,
                             String log_tag,
                             SystemRecord systemRecord)
        throws Throwable
    {
        String srOID = null;
        String srLinkOID = null;
        Long srSize = null;
        Long srCtime= null;
        Long srDtime = null;
        String srDigest = null;

        if (systemRecord != null) {
            srOID = systemRecord.getObjectIdentifier().toString();
            srLinkOID = QAClient.getLinkIdentifier(systemRecord).toString();
            srSize = new Long(systemRecord.getSize());
            srCtime = new Long(systemRecord.getCreationTime());
            srDtime = new Long(systemRecord.getDeleteTime());
            srDigest = 
                HCUtil.convertHashBytesToString(systemRecord.getDataDigest());
        }
            
    	// Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
     
        try {
            this.db.insertLinkOp(status,
                                 startTime,
                                 endTime,
                                 api_time,
                                 new Long((long) (Run.getInstance().getId())),
                                 info,
                                 linkOID.toString(),
                                 metadata,
                                 srOID,
                                 srLinkOID,
                                 srSize,
                                 srCtime,
                                 srDtime,
                                 srDigest,
                                 log_tag,
                                 conn);
            if (systemRecord != null) {
                this.db.insertObj(srOID, 
                                  null,
                                  srDigest, 
                                  metadata,
                                  srSize.longValue(),
                                  srLinkOID,
                                  srCtime.longValue(),
                                  srDtime.longValue(),
                                  false,
                                  conn);
            }
            conn.commit();
        }
        catch (Throwable t) {
            conn.rollback();
            throw t;
        } finally {
        	db.returnConnection(conn);
        }
    }

    public void auditAll()
        throws Throwable
    {
        int numAudits = 0;
        int numErrors = 0;

        NameValueObjectArchive nvoa = new TestNVOA(this.cluster + "-data");
        Statement oidStmt = null;

        // Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
        
        try {
            oidStmt = conn.createStatement();
            try {
                StringBuffer sb = new StringBuffer();
                sb.append("select api_oid from obj\n");
                
                ResultSet oidCursor = oidStmt.executeQuery(sb.toString());
                try {
                    while (oidCursor.next()) {
                        String oid = oidCursor.getString(1);
                        if (!auditObject(nvoa, oid)) {
                            numErrors++;
                        } else {
                            numAudits++;
                        }
                    }
                    Log.INFO("audit complete");
                    Log.INFO("num audits: " + Long.toString(numAudits));
                    Log.INFO("num errors: " + Long.toString(numErrors));
                }
                finally { try { oidCursor.close(); } catch (Throwable t) {} }
            }
            finally { try { oidStmt.close(); } catch (Throwable t) {} }
        }
        finally { try { conn.commit(); } catch (Throwable t) {} }
        
        db.returnConnection(conn);
    }

    public void auditRun(int runID)
        throws Throwable
    {
        int numAudits = 0;
        int numErrors = 0;

        NameValueObjectArchive nvoa = new TestNVOA(this.cluster + "-data");
        Statement oidStmt = null;

        // Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
        
        try {
            oidStmt = conn.createStatement();
            try {
                StringBuffer sb = new StringBuffer();
                sb.append("select distinct oid\n");
                sb.append("from\n");
                sb.append("  ((select api_oid as oid\n");
                sb.append("    from op\n");
                sb.append("    where status = 0 and not api_oid is null and run_id = " + Integer.toString(runID) + ")\n");
                sb.append("   union\n");
                sb.append("   (select sr_oid as oid\n");
                sb.append("    from op\n");
                sb.append("    where status = 0 and not sr_oid is null and run_id = " + Integer.toString(runID) + "))\n");
                sb.append("as foo");
                
                ResultSet oidCursor = oidStmt.executeQuery(sb.toString());
                try {
                    while (oidCursor.next()) {
                        String oid = oidCursor.getString(1);
                        if (!auditObject(nvoa, oid)) {
                            numErrors++;
                        } else {
                            numAudits++;
                        }
                    }
                    Log.INFO("audit complete");
                    Log.INFO("num audits: " + Long.toString(numAudits));
                    Log.INFO("num errors: " + Long.toString(numErrors));
                }
                finally { try { oidCursor.close(); } catch (Throwable t) {} }
            }
            finally { try { oidStmt.close(); } catch (Throwable t) {} }
        }
        finally { try { conn.commit(); } catch (Throwable t) {} }
        
        db.returnConnection(conn);
    }
    
    public String getTimeStampFromDB() throws SQLException, HoneycombTestException{
    	StringBuffer sb = new StringBuffer();
        sb.append("select now()");
        ConnectionWrapper conn = db.getConnection();
        Statement oidStmt = conn.createStatement();
        ResultSet oidCursor = oidStmt.executeQuery(sb.toString());
        oidCursor.next();
        return oidCursor.getString(1);        
    }	

	public void freeLocks() throws SQLException, HoneycombTestException{
		// Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    try{
	    	StringBuffer sb = new StringBuffer();
            sb.append("update obj set lock=null where not lock is null;");
        	Statement oidStmt = conn.createStatement();
        	oidStmt.executeUpdate(sb.toString());
        	conn.commit();
	    } finally { 
	    	conn.rollback();
	    	db.returnConnection(conn);
		}
	}
	
	public void freeLocks(String lockid) throws SQLException, HoneycombTestException{
		// Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    try{
	    	StringBuffer sb = new StringBuffer();
            sb.append("update obj set lock=null where lock='" + lockid + "'");
        	Statement oidStmt = conn.createStatement();
        	oidStmt.executeUpdate(sb.toString());
        	conn.commit();
	    } finally { 
	    	conn.rollback();
	    	db.returnConnection(conn);
		}
	}
	
	public void freeLock(String oid) throws SQLException, HoneycombTestException{
		// Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    try{
			PreparedStatement upObjLock = conn.getUpObjUnLocked();
	        int col = 1;
	        upObjLock.setString(col++, oid);
	        upObjLock.executeUpdate();
	        conn.commit();
	    } finally { 
	    	conn.rollback();
	    	db.returnConnection(conn);
		}
	}

	public String getInfo(String oid) throws SQLException, HoneycombTestException {
		// Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    String result = null;
	    Statement select = null;
	    ResultSet oidCursor = null;
	    
	    try {
	    	select = conn.createStatement();
	    	try {
	    		oidCursor = select.executeQuery("select info from " +  AuditDBClient.OP_TABLE + " where api_oid='" + oid + "'");
	    		try{
	            	if (oidCursor.next()){
	    				result = oidCursor.getString(1);
	    			}
            	}finally { try { oidCursor.close(); } catch (Throwable t) {} }
	    	}finally { try { select.close(); } catch (Throwable t) {} }
	    }finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);
	    
	    return result;
	}
	

	public String countLocks() throws SQLException, HoneycombTestException {
		// Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    String result = null;
	    Statement select = null;
	    ResultSet oidCursor = null;
	    
	    try {
	    	select = conn.createStatement();
	    	try {
	    		oidCursor = select.executeQuery("select count(*) from " +  AuditDBClient.OBJ_TABLE + " where not lock is null");
	    		try{
	            	if (oidCursor.next()){
	    				result = oidCursor.getString(1);
	    			}
            	}finally { try { oidCursor.close(); } catch (Throwable t) {} }
	    	}finally { try { select.close(); } catch (Throwable t) {} }
	    }finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);
	    
	    return result;
	}
	
	public String countLocks(String lockid) throws SQLException, HoneycombTestException {
		// Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    String result = null;
	    Statement select = null;
	    ResultSet oidCursor = null;
	    
	    try {
	    	select = conn.createStatement();
	    	try {
	    		oidCursor = select.executeQuery("select count(*) from " +  AuditDBClient.OBJ_TABLE + " where lock='" + lockid + "'");
	    		try{
	            	if (oidCursor.next()){
	    				result = oidCursor.getString(1);
	    			}
            	}finally { try { oidCursor.close(); } catch (Throwable t) {} }
	    	}finally { try { select.close(); } catch (Throwable t) {} }
	    }finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);
	    
	    return result;
	}
	
	public String countOIDs(String condition) throws SQLException, HoneycombTestException {
		// Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    String result = null;
	    Statement select = null;
	    ResultSet oidCursor = null;
	    
	    if (condition == null)
	    	condition = "";
	    
	    try {
	    	select = conn.createStatement();
	    	try {
	    		oidCursor = select.executeQuery("select count(*) from (select api_oid from " +  AuditDBClient.OBJ_TABLE + " " + condition + ") as foo");
	    		try{
	            	if (oidCursor.next()){
	    				result = oidCursor.getString(1);
	    			}
            	}finally { try { oidCursor.close(); } catch (Throwable t) {} }
	    	}finally { try { select.close(); } catch (Throwable t) {} }
	    }finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);
	    
	    return result;
	}
	
	public int countOperations(int run_id) {
		
		ConnectionWrapper conn = null;
		try {
			// Get Connection from connection pool.
		    conn = db.getConnection();
		    String result = null;
		    Statement select = null;
		    ResultSet oidCursor = null;
		    
		    try {
		    	select = conn.createStatement();
		    	try {
		    		oidCursor = select.executeQuery("select count(*) from " +  AuditDBClient.OP_TABLE + " where status=0 and run_id=" + run_id);
		    		try{
		            	if (oidCursor.next()){
		    				result = oidCursor.getString(1);
		    			}
	            	}finally { try { oidCursor.close(); } catch (Throwable t) {} }
		    	}finally { try { select.close(); } catch (Throwable t) {} }
		    }finally { try { conn.commit(); } catch (Throwable t) {} }
		    
		    db.returnConnection(conn);
		    return new Integer(result).intValue();
		} catch (Throwable t) {
			Log.ERROR("Errors auditing store count: " + Log.stackTrace(t));
			try{
				db.returnConnection(conn);
			}catch(HoneycombTestException e){}
			return 0;
		}
	}
	
	public long countStoredBytes(int run_id) {
		ConnectionWrapper conn = null;
		try {	
			// Get Connection from connection pool.
		    conn = db.getConnection();
		    String result = null;
		    Statement select = null;
		    ResultSet oidCursor = null;
		    
		    try {
		    	select = conn.createStatement();
		    	try {
		    		oidCursor = select.executeQuery("select sum(num_bytes) from " +  AuditDBClient.OP_TABLE + " where status=0 and run_id=" + run_id);
		    		try{
		            	if (oidCursor.next()){
		    				result = oidCursor.getString(1);
		    			}
	            	}finally { try { oidCursor.close(); } catch (Throwable t) {} }
		    	}finally { try { select.close(); } catch (Throwable t) {} }
		    }finally { try { conn.commit(); } catch (Throwable t) {} }
		    
		    db.returnConnection(conn);
		    return new Long(result).longValue();
		} catch (Throwable t) {
			Log.ERROR("Errors auditing stored bytes count: " + Log.stackTrace(t));
			try{
				db.returnConnection(conn);
			}catch(HoneycombTestException e){}
			return 0;
		} 
	}
	
	private static long counter = 0;
	public synchronized long getNextID(){
		return counter++;
	}
	
	
	// TODO: make a better retry scheme and only retry if the error being thrown is that
	//       it was not possible to serialize concurrent update.
	public void tryConcurrentUpdate(StringBuffer sb, Statement stmt, ConnectionWrapper conn) throws SQLException{
		// Updates and locks all of these rows for me
        int retry = 0;
        boolean passed = false;
        
        while (!passed && retry < 6){
            try{
            	stmt.executeUpdate(sb.toString());		            	
            	passed = true;
            } catch(SQLException e){
            	conn.rollback();
            	try { 
            		Thread.sleep(1000);
            	} catch (InterruptedException ignore) {}
            	retry++;
            }
        }
	}
	
	public ArrayList getAndLockRetrievableObjects(long limit, String lockID, long minSize, long maxSize)
    	   throws Throwable
	{
	    ArrayList oids = new ArrayList();
	    Statement oidStmt = null;
	
	    // Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();
	    
	    // Unique LockID everytime we fetch OIDS.
	    lockID = lockID + getNextID();	    
	    
	    try {
	        oidStmt = conn.createStatement();
	        try {
	            StringBuffer sb = new StringBuffer();
	            sb.append("update obj set lock='"+ lockID + "'");
	            sb.append(" where obj.oid in (select oid from obj where deleted is false ");
	            sb.append(" and lock is null ");
	            sb.append(" limit " + limit + ")");
	
	            tryConcurrentUpdate(sb,oidStmt,conn);
	            	            
	            sb = new StringBuffer();
	            sb.append("select api_oid from obj where lock='" + lockID + "' order by random()");
	            	            	            
	            ResultSet oidCursor = oidStmt.executeQuery(sb.toString());
	            try {
	                while (oidCursor.next()) {
	                    String oid = oidCursor.getString(1);
	                    oids.add(oid);
	                }
	            }	            
	            finally { try { oidCursor.close(); } catch (Throwable t) {} }
	        }
	        finally { try { oidStmt.close(); } catch (Throwable t) {} }
	    }
	    finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);
	    return oids;
	}
	
	public ArrayList getObjectsNoLock(long limit, long minSize, long maxSize)
    throws Throwable
	{
	    ArrayList oids = new ArrayList();
	    Statement oidStmt = null;
	
	    // Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();

	    try {
	        oidStmt = conn.createStatement();
	        try {
	       	
	            StringBuffer sb = new StringBuffer();
	            sb.append("select api_oid from obj\n");
                    sb.append("  where deleted=false\n");
                    sb.append(" order by random()\n");
	            sb.append(" LIMIT " + limit);

	            ResultSet oidCursor = oidStmt.executeQuery(sb.toString());
	            try {
	                while (oidCursor.next()) {
	                    String oid = oidCursor.getString(1);
	                    oids.add(oid);
	                }
	            }	            
	            finally { try { oidCursor.close(); } catch (Throwable t) {} }
	        }
	        finally { try { oidStmt.close(); } catch (Throwable t) {} }
	    }
	    finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);
	    return oids;
	}
	
	
	public ArrayList getSimpleQueries(String table, long limit, long minSize, long maxSize)
    throws Throwable
	{
	    ArrayList queries = new ArrayList();
	    Statement queryStmt = null;
	
	    // Get Connection from connection pool.
	    ConnectionWrapper conn = db.getConnection();

	    try {
	    	queryStmt = conn.createStatement();
	        try {
	       	
	            StringBuffer sb = new StringBuffer();
	            sb.append("select name,value from (");
	            sb.append("select count(value),name,value from " + table + " group by name,value)");
	            sb.append(" as foo where count >= " + minSize);
	            sb.append(" and count <= " + maxSize + " limit " + limit);	            

	            ResultSet queryCursor = queryStmt.executeQuery(sb.toString());
	            try {
	                while (queryCursor.next()) {
	                    String name = queryCursor.getString("name");
	                    String value = queryCursor.getString("value");
	                    if (table.equalsIgnoreCase(AuditDBClient.MD_STRING_TABLE))
	                    	queries.add(name + "='" + value + "'");
	                    else
	                    	queries.add(name + "=" + value);
	                }
	            }	            
	            finally { try { queryCursor.close(); } catch (Throwable t) {} }
	        }
	        finally { try { queryStmt.close(); } catch (Throwable t) {} }
	    }
	    finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);
	    return queries;
	}	
	
	public ArrayList getAndLockRetrievableObjects(long limit, long freshInterval, String lockID, long minSize, long maxSize)
    throws Throwable
    {
	    ArrayList oids = new ArrayList();
	    Statement oidStmt = null;
	
	    // Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
        
        // Unique LockID everytime we fetch OIDS.
	    lockID = lockID + getNextID();	    
   
	    try {
	        oidStmt = conn.createStatement();
	        try {
	            StringBuffer sb = new StringBuffer();
	            sb.append("update obj set lock='"+ lockID + "' where api_oid in (select distinct oid\n");
	            sb.append("from\n");
	            sb.append("  ( ((select api_oid as oid\n");
	            sb.append("    from op\n");
	            sb.append("    where status = 0 and not api_oid is null");
	            sb.append(" and NOW() - interval '"+ freshInterval +" seconds' <= start_time and num_bytes >= "+ minSize + " and num_bytes <= " + maxSize + ")\n");
	            sb.append("   union\n");
	            sb.append("   (select sr_oid as oid\n");
	            sb.append("    from op\n");
	            sb.append("    where status = 0 and not sr_oid is null ");
	            sb.append(" and NOW() - interval '"+ freshInterval +" seconds' <= start_time and num_bytes >= "+ minSize + " and num_bytes <= " + maxSize + "))\n");
	            sb.append("   intersect\n");
	            sb.append("   (select api_oid as oid from " + AuditDBClient.OBJ_TABLE + " where deleted=false and lock is null) )\n");	            
	            sb.append("order by random() as foo LIMIT " + limit + ")");

	            tryConcurrentUpdate(sb,oidStmt,conn);
	            
	            // Select locked rows 
	            sb = new StringBuffer();
	            sb.append("select api_oid from obj order by random() where lock='" + lockID + "' order by random()");
	            	            	            
	            ResultSet oidCursor = oidStmt.executeQuery(sb.toString());
	            
	            try {
	                while (oidCursor.next()) {
	                    String oid = oidCursor.getString(1);
	                    oids.add(oid);
	                }
	            }
	            finally { try { oidCursor.close(); } catch (Throwable t) {} }
	        }
	        finally { try { oidStmt.close(); } catch (Throwable t) {} }
	    }
	    finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);	    
	    return oids;
	}
	
	public ArrayList getObjectsNoLock(long limit, long freshInterval, long minSize, long maxSize)
    throws Throwable
    {
	    ArrayList oids = new ArrayList();
	    Statement oidStmt = null;
	
	    // Get Connection from connection pool.
        ConnectionWrapper conn = db.getConnection();
           
	    try {
	        oidStmt = conn.createStatement();
	        try {
	            StringBuffer sb = new StringBuffer();
	            sb.append("select distinct oid\n");
	            sb.append("from\n");
	            sb.append("  ( ((select api_oid as oid\n");
	            sb.append("    from op\n");
	            sb.append("    where status = 0 and not api_oid is null");
	            sb.append(" and NOW() - interval '"+ freshInterval +" seconds' <= start_time and num_bytes >= "+ minSize + " and num_bytes <= " + maxSize + ")\n");
	            sb.append("   union\n");
	            sb.append("   (select sr_oid as oid\n");
	            sb.append("    from op\n");
	            sb.append("    where status = 0 and not sr_oid is null ");
	            sb.append(" and NOW() - interval '"+ freshInterval +" seconds' <= start_time and num_bytes >= "+ minSize + " and num_bytes <= " + maxSize + "))\n");
	            sb.append("   intersect\n");
	            sb.append("   (select api_oid as oid from " + AuditDBClient.OBJ_TABLE + " where deleted=false) )\n");	            
	            sb.append("order by random() as foo LIMIT " + limit);
	            	            	            
	            ResultSet oidCursor = oidStmt.executeQuery(sb.toString());
	            
	            try {
	                while (oidCursor.next()) {
	                    String oid = oidCursor.getString(1);
	                    oids.add(oid);
	                }
	            }
	            finally { try { oidCursor.close(); } catch (Throwable t) {} }
	        }
	        finally { try { oidStmt.close(); } catch (Throwable t) {} }
	    }
	    finally { try { conn.commit(); } catch (Throwable t) {} }
	    
	    db.returnConnection(conn);	    
	    return oids;
	}
	
	 public AuditResult auditObjectAndReturnMessages(NameValueObjectArchive nvoa, String oid)
     throws Throwable
	 {
		 AuditResult result = new AuditResult();
		 result.pass = true;
	     	
		 result.addInfo("auditObject: oid(" + oid  +")");
	
	     // Get Connection from connection pool.
	     ConnectionWrapper conn = db.getConnection();
	
	     Statement objStmt = conn.createStatement();
	
	     StringBuffer sb = new StringBuffer();
	     sb.append("select\n");
	     sb.append("  api_oid,\n");
	     sb.append("  sys_oid,\n");
	     sb.append("  sha1,\n");
	     sb.append("  has_metadata,\n");
	     sb.append("  size,\n");
	     sb.append("  api_link_oid,\n");
	     sb.append("  ctime,\n");
	     sb.append("  dtime,\n");
	     sb.append("  deleted\n");
	     sb.append("from obj\n");
	     sb.append("where api_oid = '" + oid + "'");
	
	     ResultSet objCursor = objStmt.executeQuery(sb.toString());
	     if (!objCursor.next()) {
	             db.returnConnection(conn);
	         result.addError("AUDIT BUG: an oid was found in the operation table, but not in the obj table: OID:" + oid);
	         result.pass = false;
	         return result;
	     }
	
	     String api_oid = objCursor.getString(1);
	     String sha1 = objCursor.getString(3);
	     boolean has_metadata = objCursor.getBoolean(4);
	     long size = objCursor.getLong(5);
	     boolean _deleted = objCursor.getBoolean(9);
	     Boolean deleted = objCursor.wasNull() ? null : new Boolean(_deleted);
	
	     result.addInfo("auditObject: size(" + Long.toString(size) + ") deleted(" + (deleted == null ? "undef" : deleted.toString()) + ") has_metadata(" + Boolean.toString(has_metadata) + ") OID:" + oid);
	
	     DigestableWriteChannel c = new DigestableWriteChannel();
	
	     Throwable caught = null;
	     try {
	         nvoa.retrieveObject(new ObjectIdentifier(oid), c);
	     }
	     catch (Throwable t) {
	         caught = t;
	     }
	
	     if (deleted == null) {
	         result.addWarning("audit data: oid(" + oid + "): Deleted status is currently undefined in the audit DB");
	         if (caught instanceof NoSuchObjectException)
	        	 result.addWarning("audit data: oid(" + oid + "): retrieveObject() threw NoSuchObjectException");
	         else
	        	 result.addWarning("audit data: oid(" + oid + "): retrieveObject() did not throw a NoSuchObjectException");
	         result.addWarning("audit data: oid(" + oid + "): Object's deleted status will remain undefined in the audit DB");
	     }
	     else if (deleted.equals(Boolean.TRUE)) {
	         if (!(caught instanceof NoSuchObjectException)) {
	        	 result.addError(Log.stackTrace(caught));
	        	 result.addError("audit data failed: oid(" + oid + "): expected NoSuchObjectException: received the above...");
	             result.pass = false;
	         }
	     }
	     else {
	         if (caught != null) {
	        	 result.addError("unable to retrieve undeleted object: OID:" + oid);
	        	 result.addError(Log.stackTrace(caught));
	        	 result.pass = false;
	         }
	         else {
	             byte [] digest = c.digest();
	             String sha1Audit = HCUtil.convertHashBytesToString(digest);
	             long numBytes = c.getNumBytes();
	             if (numBytes != size) {
	            	 result.addError("audit failed: oid(" + oid + "): expected size (" + Long.toString(size) + "): received size (" + Long.toString(numBytes) + ")");
	            	 result.pass = false;
	             }
	             if (!sha1.equals(sha1Audit)) {
	            	 result.addError("audit failed: oid(" + oid + "): expected sha1 (" + sha1 + "): received sha1 (" + sha1Audit + ")");
	            	 result.pass = false;
	             }
	         }
	     }
	     
	     result.addInfo("audit data: retrieveObject: " + (result.pass ? "PASS" : "FAIL") + " OID:" + oid);
	
	     NameValueRecord md = null;
	     caught = null;
	     try {
	         md = nvoa.retrieveMetadata(new ObjectIdentifier(oid));
	     }
	     catch (Throwable t) {
	         caught = t;
	     }
	
	     if (deleted == null) {
	    	 result.addError("audit metadata: oid(" + oid + "): Deleted status is currently undefined in the audit DB");
	         if (caught instanceof NoSuchObjectException)
	        	 result.addError("audit metadata: oid(" + oid + "): retrieveMetadata() threw NoSuchObjectException");
	         else
	        	 result.addError("audit metadata: oid(" + oid + "): retrieveMetadata() did not throw a NoSuchObjectException");
	         result.addWarning("audit metadata: oid(" + oid + "): Object's deleted status will remain undefined in the audit DB");
	     }
	     else if (deleted.equals(Boolean.TRUE)) {
	         if (!(caught instanceof NoSuchObjectException)) {
	        	 result.addError(Log.stackTrace(caught));
	        	 result.addError("audit metadata failed: oid(" + oid + "): expected NoSuchObjectException: received the above...");
	        	 result.pass = false;
	         }
	     }
	     else if (has_metadata) {
	         Statement stmt;
	         ResultSet cursor;
	         
	         try {
	             stmt = conn.createStatement();
	             try {
	                 
	                 sb = new StringBuffer();
	                 sb.append("select\n");
	                 sb.append("  name,\n");
	                 sb.append("  value\n");
	                 sb.append("from md_long\n");
	                 sb.append("where api_oid = '" + oid + "'");
	                 
	                 cursor = stmt.executeQuery(sb.toString());
	                 try {
	                     while (cursor.next()) {
	                         String name = cursor.getString(1);
	                         long value = cursor.getLong(2);
	                         long value2;
	                         try {
	                             value2 = md.getLong(name);
	                             if (value != value2) {
	                            	 result.addError("audit failed: oid(" + oid + "): expected md_long(" + name + ", " + Long.toString(value) + "): received value (" + Long.toString(value2) + ")");
	                            	 result.pass = false;
	                             }
	                         }
	                         catch (IllegalArgumentException iae) {
	                        	 result.addError("audit failed: oid(" + oid + "): expected md_long(" + name + ", " + Long.toString(value) + "), : received IllegalArgumentException");
	                        	 result.addError(Log.stackTrace(iae));
	                        	 result.pass = false;
	                         }
	                     }
	                 }
	                 finally { try { cursor.close(); } catch (Throwable t) {} }
	             }
	             finally { try { stmt.close(); } catch (Throwable t) {} }
	         }
	         finally { try { conn.commit(); } catch (Throwable t) {} }
	         
	         try {
	             stmt = conn.createStatement();
	             try {
	                 
	                 sb = new StringBuffer();
	                 sb.append("select\n");
	                 sb.append("  name,\n");
	                 sb.append("  value\n");
	                 sb.append("from md_string\n");
	                 sb.append("where api_oid = '" + oid + "'");
	                 
	                 cursor = stmt.executeQuery(sb.toString());
	                 try {
	                     while (cursor.next()) {
	                         String name = cursor.getString(1);
	                         String value = cursor.getString(2);
	                         String value2;
	                         try {
	                             value2 = md.getString(name);
	                             if (!value.equals(value2)) {
	                            	 result.addError("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + value + "): received value (" + value2 + ")");
	                            	 result.pass = false;
	                             }
	                         }
	                         catch (IllegalArgumentException iae) {
	                        	 result.addError("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + value + "), : received IllegalArgumentException");
	                        	 result.addError(Log.stackTrace(iae));
	                        	 result.pass = false;
	                         }
	                     }
	                 }
	                 finally { try { cursor.close(); } catch (Throwable t) {} }
	             }
	             finally { try { stmt.close(); } catch (Throwable t) {} }
	         }
	         finally { try { conn.commit(); } catch (Throwable t) {} }
	         
	         try {
	             stmt = conn.createStatement();
	             try {
	                 
	                 sb = new StringBuffer();
	                 sb.append("select\n");
	                 sb.append("  name,\n");
	                 sb.append("  value\n");
	                 sb.append("from md_double\n");
	                 sb.append("where api_oid = '" + oid + "'");
	                 
	                 cursor = stmt.executeQuery(sb.toString());
	                 try {
	                     while (cursor.next()) {
	                         String name = cursor.getString(1);
	                         double value = cursor.getDouble(2);
	                         double value2;
	                         try {
	                             value2 = md.getDouble(name);
	                             if (value != value2) {
	                            	 result.addError("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + Double.toString(value) + "): received value (" + Double.toString(value2) + ")");
	                            	 result.pass = false;
	                             }
	                         }
	                         catch (IllegalArgumentException iae) {
	                        	 result.addError("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + Double.toString(value) + "), : received IllegalArgumentException");
	                        	 result.addError(Log.stackTrace(iae));
	                        	 result.pass = false;
	                         }
	                     }
	                 }
	                 finally { try { cursor.close(); } catch (Throwable t) {} }
	             }
	             finally { try { stmt.close(); } catch (Throwable t) {} }
	         }
	         finally { try { conn.commit(); } catch (Throwable t) {} }
	         

	         
	     }
	     result.addInfo("auditObject: retrieveMetadata: " + (result.pass ? "PASS" : "FAIL") + " OID:" + oid);
	     
	     db.returnConnection(conn);
	     
	     return result;
	 }

	 public boolean auditObject(NameValueObjectArchive nvoa, String oid)
     throws Throwable
	 {
	     boolean ok = true;
	
	     Log.INFO("auditObject: oid(" + oid  +")");
	
	     // Get Connection from connection pool.
	     ConnectionWrapper conn = db.getConnection();
	
	     Statement objStmt = conn.createStatement();
	
	     StringBuffer sb = new StringBuffer();
	     sb.append("select\n");
	     sb.append("  api_oid,\n");
	     sb.append("  sys_oid,\n");
	     sb.append("  sha1,\n");
	     sb.append("  has_metadata,\n");
	     sb.append("  size,\n");
	     sb.append("  api_link_oid,\n");
	     sb.append("  ctime,\n");
	     sb.append("  dtime,\n");
	     sb.append("  deleted\n");
	     sb.append("from obj\n");
	     sb.append("where api_oid = '" + oid + "'");
	
	     ResultSet objCursor = objStmt.executeQuery(sb.toString());
	     if (!objCursor.next()) {
	             db.returnConnection(conn);
	         Log.ERROR("AUDIT BUG: an oid was found in the operation table, but not in the obj table: " + oid);
	         return false;
	     }
	
	     String api_oid = objCursor.getString(1);
	     String sha1 = objCursor.getString(3);
	     boolean has_metadata = objCursor.getBoolean(4);
	     long size = objCursor.getLong(5);
	     boolean _deleted = objCursor.getBoolean(9);
	     Boolean deleted = objCursor.wasNull() ? null : new Boolean(_deleted);
	
	     Log.INFO("auditObject: size(" + Long.toString(size) + ") deleted(" + (deleted == null ? "undef" : deleted.toString()) + ") has_metadata(" + Boolean.toString(has_metadata) + ")" );
	
	     DigestableWriteChannel c = new DigestableWriteChannel();
	
	     Throwable caught = null;
	     try {
	         nvoa.retrieveObject(new ObjectIdentifier(oid), c);
	     }
	     catch (Throwable t) {
	         caught = t;
	     }
	
	     if (deleted == null) {
	         Log.WARN("audit data: oid(" + oid + "): Deleted status is currently undefined in the audit DB");
	         if (caught instanceof NoSuchObjectException)
	             Log.WARN("audit data: oid(" + oid + "): retrieveObject() threw NoSuchObjectException");
	         else
	             Log.WARN("audit data: oid(" + oid + "): retrieveObject() did not throw a NoSuchObjectException");
	         Log.WARN("audit data: oid(" + oid + "): Object's deleted status will remain undefined in the audit DB");
	     }
	     else if (deleted.equals(Boolean.TRUE)) {
	         if (!(caught instanceof NoSuchObjectException)) {
	             Log.ERROR(Log.stackTrace(caught));
	             Log.ERROR("audit data failed: oid(" + oid + "): expected NoSuchObjectException: received the above...");
	             ok = false;
	         }
	     }
	     else {
	         if (caught != null) {
	             Log.ERROR("unable to retrieve undeleted object: " + oid);
	             Log.ERROR(Log.stackTrace(caught));
	             ok = false;
	         }
	         else {
	             byte [] digest = c.digest();
	             String sha1Audit = HCUtil.convertHashBytesToString(digest);
	             long numBytes = c.getNumBytes();
	             if (numBytes != size) {
	                 Log.ERROR("audit failed: oid(" + oid + "): expected size (" + Long.toString(size) + "): received size (" + Long.toString(numBytes) + ")");
	                 ok = false;
	             }
	             if (!sha1.equals(sha1Audit)) {
	                 Log.ERROR("audit failed: oid(" + oid + "): expected sha1 (" + sha1 + "): received sha1 (" + sha1Audit + ")");
	                 ok = false;
	             }
	         }
	     }
	     Log.INFO("audit data: retrieveObject: " + (ok ? "PASS" : "FAIL"));
	
	     NameValueRecord md = null;
	     caught = null;
	     try {
	         md = nvoa.retrieveMetadata(new ObjectIdentifier(oid));
	     }
	     catch (Throwable t) {
	         caught = t;
	     }
	
	     if (deleted == null) {
	         Log.WARN("audit metadata: oid(" + oid + "): Deleted status is currently undefined in the audit DB");
	         if (caught instanceof NoSuchObjectException)
	             Log.WARN("audit metadata: oid(" + oid + "): retrieveMetadata() threw NoSuchObjectException");
	         else
	             Log.WARN("audit metadata: oid(" + oid + "): retrieveMetadata() did not throw a NoSuchObjectException");
	         Log.WARN("audit metadata: oid(" + oid + "): Object's deleted status will remain undefined in the audit DB");
	     }
	     else if (deleted.equals(Boolean.TRUE)) {
	         if (!(caught instanceof NoSuchObjectException)) {
	             Log.ERROR(Log.stackTrace(caught));
	             Log.ERROR("audit metadata failed: oid(" + oid + "): expected NoSuchObjectException: received the above...");
	             ok = false;
	         }
	     }
	     else if (has_metadata) {
	         Statement stmt;
	         ResultSet cursor;
	         
	         try {
	             stmt = conn.createStatement();
	             try {
	                 
	                 sb = new StringBuffer();
	                 sb.append("select\n");
	                 sb.append("  name,\n");
	                 sb.append("  value\n");
	                 sb.append("from md_long\n");
	                 sb.append("where api_oid = '" + oid + "'");
	                 
	                 cursor = stmt.executeQuery(sb.toString());
	                 try {
	                     while (cursor.next()) {
	                         String name = cursor.getString(1);
	                         long value = cursor.getLong(2);
	                         long value2;
	                         try {
	                             value2 = md.getLong(name);
	                             if (value != value2) {
	                                 Log.ERROR("audit failed: oid(" + oid + "): expected md_long(" + name + ", " + Long.toString(value) + "): received value (" + Long.toString(value2) + ")");
	                                 ok = false;
	                             }
	                         }
	                         catch (IllegalArgumentException iae) {
	                             Log.ERROR("audit failed: oid(" + oid + "): expected md_long(" + name + ", " + Long.toString(value) + "), : received IllegalArgumentException");
	                             Log.ERROR(Log.stackTrace(iae));
	                             ok = false;
	                         }
	                     }
	                 }
	                 finally { try { cursor.close(); } catch (Throwable t) {} }
	             }
	             finally { try { stmt.close(); } catch (Throwable t) {} }
	         }
	         finally { try { conn.commit(); } catch (Throwable t) {} }
	         
	         try {
	             stmt = conn.createStatement();
	             try {
	                 
	                 sb = new StringBuffer();
	                 sb.append("select\n");
	                 sb.append("  name,\n");
	                 sb.append("  value\n");
	                 sb.append("from md_string\n");
	                 sb.append("where api_oid = '" + oid + "'");
	                 
	                 cursor = stmt.executeQuery(sb.toString());
	                 try {
	                     while (cursor.next()) {
	                         String name = cursor.getString(1);
	                         String value = cursor.getString(2);
	                         String value2;
	                         try {
	                             value2 = md.getString(name);
	                             if (!value.equals(value2)) {
	                                 Log.ERROR("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + value + "): received value (" + value2 + ")");
	                                 ok = false;
	                             }
	                         }
	                         catch (IllegalArgumentException iae) {
	                             Log.ERROR("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + value + "), : received IllegalArgumentException");
	                             Log.ERROR(Log.stackTrace(iae));
	                             ok = false;
	                         }
	                     }
	                 }
	                 finally { try { cursor.close(); } catch (Throwable t) {} }
	             }
	             finally { try { stmt.close(); } catch (Throwable t) {} }
	         }
	         finally { try { conn.commit(); } catch (Throwable t) {} }
	         
	         try {
	             stmt = conn.createStatement();
	             try {
	                 
	                 sb = new StringBuffer();
	                 sb.append("select\n");
	                 sb.append("  name,\n");
	                 sb.append("  value\n");
	                 sb.append("from md_double\n");
	                 sb.append("where api_oid = '" + oid + "'");
	                 
	                 cursor = stmt.executeQuery(sb.toString());
	                 try {
	                     while (cursor.next()) {
	                         String name = cursor.getString(1);
	                         double value = cursor.getDouble(2);
	                         double value2;
	                         try {
	                             value2 = md.getDouble(name);
	                             if (value != value2) {
	                                 Log.ERROR("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + Double.toString(value) + "): received value (" + Double.toString(value2) + ")");
	                                 ok = false;
	                             }
	                         }
	                         catch (IllegalArgumentException iae) {
	                             Log.ERROR("audit failed: oid(" + oid + "): expected md_string(" + name + ", " + Double.toString(value) + "), : received IllegalArgumentException");
	                             Log.ERROR(Log.stackTrace(iae));
	                             ok = false;
	                         }
	                     }
	                 }
	                 finally { try { cursor.close(); } catch (Throwable t) {} }
	             }
	             finally { try { stmt.close(); } catch (Throwable t) {} }
	         }
	         finally { try { conn.commit(); } catch (Throwable t) {} }
	         

	     }
	     Log.INFO("auditObject: retrieveMetadata: " + (ok ? "PASS" : "FAIL"));
	     
	     db.returnConnection(conn);
	     return ok;
	 }

    public static void main(String [] argv) throws Throwable
    {
        TestRunner r = new TestRunner(argv);
        
        String host = System.getProperty(HCLocale.PROPERTY_DBHOST);
        if (host == null) {
            throw new HoneycombTestException("System property not defined: " +
                                             HCLocale.PROPERTY_DBHOST);
        }
        Audit audit = 
            Audit.getInstance(host,
                       r.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER));
        String run = r.getProperty(HoneycombTestConstants.PROPERTY_RUN);
        long errors = 0;
        if (run != null) {
            audit.auditRun(Integer.parseInt(run));
        }
        else {
            audit.auditAll();
        }
    }
}
