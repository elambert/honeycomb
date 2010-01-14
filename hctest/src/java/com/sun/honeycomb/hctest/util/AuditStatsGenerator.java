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

 
package com.sun.honeycomb.hctest.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class AuditStatsGenerator {

	private String auditHost = null;
	private String cluster = null;
	private AuditDBClient db;
    
    private static boolean failedops = false;
    
    public static boolean anyFailedOps(){
    	return failedops;
    }
    
    public static void setFailedOps(){
    	failedops = true;
    }
    
	public AuditStatsGenerator(String host, String cluster) throws HoneycombTestException {
		this.auditHost = host;
        this.cluster = cluster;
        try {
			this.db = new AuditDBClient(host, cluster);
		} catch (Throwable t) {
			throw new HoneycombTestException(t);
		}
	}
	
	public Long runSelect(String selectString) throws SQLException{
		try {	
			// Get Connection from connection pool.
		    ConnectionWrapper conn = db.getConnection();
		    String result = null;
		    Statement select = null;
		    ResultSet oidCursor = null;
		    
		    try {
		    	select = conn.createStatement();
		    	try {
		    		oidCursor = select.executeQuery(selectString);
		    		try{
		            	if (oidCursor.next()){
		    				result = oidCursor.getString(1);
		    			}
	            	}finally { try { oidCursor.close(); } catch (Throwable t) {} }
		    	}finally { try { select.close(); } catch (Throwable t) {} }
		    }finally { try { conn.commit(); } catch (Throwable t) {} }
		    
		    if (result == null)
		    	return new Long(0);
		    else
		    	return new Long(result);
		} catch (Throwable t) {
			Log.ERROR("Error running query: " + Log.stackTrace(t));
			return new Long(0);
		}	
	}
	
	private static StatsContainer stats = new StatsContainer();
	
	public static class StatsContainer {
		private Vector keys = new Vector();
		private Vector values = new Vector();
		
		public void put(Object key, Object value){
			keys.add(key);
			values.add(value);
		}
		
		public int size(){
			return keys.size();
		}
		
		public Object getkey(int index){
			return keys.get(index);
		}
		
		public Object getvalue(int index){
			return values.get(index);
		}
	}
	
	public static String selectString(String res, String table, String[] conditions){
		StringBuffer result = new StringBuffer();
		
		result.append("select " + res); 
		result.append(" from " + AuditDBClient.OP_TABLE);
		
		if (conditions.length >= 1) {
			result.append(" where " + conditions[0]);
			for(int i = 1; i < conditions.length; i++){
				result.append(" and " + conditions[i]);
			}
		}
				
		return result.toString();
	}
	
	public Long runSelect(String res, String table, String[] conditions) throws SQLException{
		return runSelect(selectString(res,table,conditions));
	}
	
	private static String run_id = null;
		
	public void generateStats(String run_id) {
		this.run_id = run_id;
        
		Log.INFO("Generating Stats for runID: " + run_id);
		
		try{ 
			
			Long duration = runSelect("extract (epoch from date_trunc('seconds',max(end_time)-min(start_time)))", AuditDBClient.OP_TABLE, new String[]{"run_id=" + run_id});
						
			if (duration.longValue() != 0) {
				Long numops = runSelect("count(*)", AuditDBClient.OP_TABLE,	new String[]{"run_id=" + run_id});
				Long numopsf = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"status=1","run_id=" + run_id});
				
				Long numstores = runSelect("count(*)",AuditDBClient.OP_TABLE, new String[]{"run_id=" + run_id, "op_type=" + AuditDBClient.OP_STORE});
				Long numstoresf = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"status=1","run_id=" + run_id, "op_type=" + AuditDBClient.OP_STORE});
				Long nummbstored = runSelect("trunc(sum(num_bytes)/(1024*1024))", AuditDBClient.OP_TABLE, new String[]{"status=0","run_id=" + run_id, "op_type=" + AuditDBClient.OP_STORE});
				
				Long numret = runSelect("count(*)",AuditDBClient.OP_TABLE,new String[]{"run_id=" + run_id,"op_type=" + AuditDBClient.OP_RETRIEVE});
				Long numretf = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"status=1","run_id=" + run_id, "op_type=" + AuditDBClient.OP_RETRIEVE});
				Long nummbret = runSelect("trunc(sum(num_bytes)/(1024*1024))", AuditDBClient.OP_TABLE, new String[]{"status=0","run_id=" + run_id, "op_type=" + AuditDBClient.OP_RETRIEVE});
				
				Long numdeletes = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"run_id=" + run_id, "op_type=" + AuditDBClient.OP_DELETE});
				Long numdeletesf = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"status=1", "run_id=" + run_id, "op_type=" + AuditDBClient.OP_DELETE});
				Long nummbdeleted = runSelect("trunc(sum(num_bytes)/(1024*1024))", AuditDBClient.OP_TABLE, new String[]{"status=0","run_id=" + run_id, "op_type=" + AuditDBClient.OP_DELETE});

				Long numMDadds = runSelect("count(*)",AuditDBClient.OP_TABLE, new String[]{"run_id=" + run_id, "op_type=" + AuditDBClient.OP_LINK});
				Long numMDaddsf = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"status=1","run_id=" + run_id, "op_type=" + AuditDBClient.OP_LINK});
				Long numMDmbadded = runSelect("trunc(sum(num_bytes)/(1024*1024))", AuditDBClient.OP_TABLE, new String[]{"status=0","run_id=" + run_id, "op_type=" + AuditDBClient.OP_LINK});

				Long numMDret = runSelect("count(*)",AuditDBClient.OP_TABLE, new String[]{"run_id=" + run_id, "op_type=" + AuditDBClient.OP_RETRIEVE_MD});
				Long numMDretf = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"status=1","run_id=" + run_id, "op_type=" + AuditDBClient.OP_RETRIEVE_MD});
				Long numMDmbret = runSelect("trunc(sum(num_bytes)/(1024*1024))", AuditDBClient.OP_TABLE, new String[]{"status=0","run_id=" + run_id, "op_type=" + AuditDBClient.OP_RETRIEVE_MD});

				Long numquery = runSelect("count(*)",AuditDBClient.OP_TABLE, new String[]{"run_id=" + run_id, "op_type=" + AuditDBClient.OP_QUERY});
				Long numqueryf = runSelect("count(*)", AuditDBClient.OP_TABLE, new String[]{"status=1","run_id=" + run_id, "op_type=" + AuditDBClient.OP_QUERY});
				Long nummbquery = runSelect("trunc(sum(num_bytes)/(1024*1024))", AuditDBClient.OP_TABLE, new String[]{"status=0","run_id=" + run_id, "op_type=" + AuditDBClient.OP_QUERY});
				
				
				if (numopsf.longValue() != 0 || numstoresf.longValue() != 0 || numretf.longValue() != 0 || 
					numdeletesf.longValue() != 0 || numMDaddsf.longValue() != 0 || numMDretf.longValue() != 0 || 
					numqueryf.longValue() != 0)
					setFailedOps();
				
                                // Note: Blockstore test driver script parses this output.
                                
				stats.put("Duration(seconds):       ", duration);			
				stats.put("Operations:              ", numops);
				stats.put("Operations Failed:       ", numopsf);
				stats.put("Avg.Ops/sec:             ", new Long(numops.longValue()/duration.longValue()));
                                
				stats.put("Total Store Ops:         ", numstores);
				stats.put("Total Store Failed:      ", numstoresf);
				stats.put("Total Store MBytes:      ", nummbstored);
				stats.put("Avg Store Ops/sec:       ", new Long(numstores.longValue()/duration.longValue()));			
				stats.put("Avg Store MB/sec:        ", new Long(nummbstored.longValue()/duration.longValue()));

				stats.put("Total MDAdd Ops:         ", numMDadds);
				stats.put("Total MDAdd Failed:      ", numMDaddsf);
				stats.put("Total MDAdd MBytes:      ", numMDmbadded);
				stats.put("Avg MDAdd Ops/sec:       ", new Long(numMDadds.longValue()/duration.longValue()));			
				stats.put("Avg MDAdd MB/sec:        ", new Long(numMDmbadded.longValue()/duration.longValue()));
                                
                                stats.put("Total Retrieve Ops:      ", numret);
                                stats.put("Total Retrieve Failed:   ", numretf);
                                stats.put("Total Retrieve MBytes:   ", nummbret);
                                stats.put("Avg Retrieve Ops/sec:    ", new Long(numret.longValue()/duration.longValue()));
				stats.put("Avg Retrieve MB/sec:     ", new Long(nummbret.longValue()/duration.longValue()));

                                stats.put("Total MDRetrieve Ops:    ", numMDret);
                                stats.put("Total MDRetrieve Failed: ", numMDretf);
                                stats.put("Total MDRetrieve MBytes: ", numMDmbret);
                                stats.put("Avg MDRetrieve Ops/sec:  ", new Long(numMDret.longValue()/duration.longValue()));
				stats.put("Avg MDRetrieve MB/sec:   ", new Long(numMDmbret.longValue()/duration.longValue()));
				
                                stats.put("Total Delete Ops:        ", numdeletes);
                                stats.put("Total Delete Failed:     ", numdeletesf);
                                stats.put("Total Delete MBytes:     ", nummbdeleted);
                                stats.put("Avg Delete Ops/sec:      ", new Long(numdeletes.longValue()/duration.longValue()));	    	
                                stats.put("Avg Delete MB/sec:       ", new Long(nummbdeleted.longValue()/duration.longValue()));

                                stats.put("Total Query Ops:         ", numquery);
                                stats.put("Total Query Failed:      ", numqueryf);
                                stats.put("Total Query MBytes:      ", nummbquery);
                                stats.put("Avg Query Ops/sec:       ", new Long(numquery.longValue()/duration.longValue()));	    	
                                stats.put("Avg Query MB/sec:        ", new Long(nummbquery.longValue()/duration.longValue()));

			} else {
                            Log.INFO("Duration is less than a second therefore stats make no sense.");
			}
	    	
		} catch(SQLException e){
			Log.ERROR("Exception trying to generate statistics:" + Log.stackTrace(e));
		}
	}
	
	public void printStats() {
		Log.INFO("*** Statistics for RunID: " + run_id);	
		for(int i = 0 ; i < stats.size(); i++){
			Log.INFO((String)stats.getkey(i) + stats.getvalue(i));
		}
	}
	
	public static String getStats() {
		String result = "*** Statistics for RunID: " + run_id + "\n";
		for(int i = 0 ; i < stats.size(); i++){
			result = result + ((String)stats.getkey(i) + stats.getvalue(i)) + "\n";
		}
		
		return result;
	}
	
	public static void main(String args[]){
		System.out.println("Select: " + selectString("count(*)",
				  AuditDBClient.OP_TABLE,
				  new String[]{"status=1","run_id=12962"})); 
	}
}
