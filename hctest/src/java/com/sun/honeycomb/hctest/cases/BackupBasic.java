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



package com.sun.honeycomb.hctest.cases;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.SnapshotTool;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RunCommand;

public class BackupBasic extends BasicSloshing {
  
	protected static final String SAFE_SNAPSHOT = "safe_bk_" + System.currentTimeMillis();
    protected static final String BK_SNAPSHOT = "bk_" + System.currentTimeMillis();
    protected static final String BK_LOCATION = "/mnt/test/backup-" + System.currentTimeMillis() + ".";
    
    protected static final String BK_OUTPUT_EXT = ".bk.output";
    protected static final String RT_OUTPUT_EXT = ".rt.output";
    
    protected String loadTime  = "120s"; // 2 minutes of load
    protected String processes = "5";    // 5 threads
   
    /*
     * Default: 75% stores, 5% Deletes and 20% addmd to existing oids
     */
    protected String operations = "75%StoreOpGenerator,5%DeleteOpGenerator,20%MDStaticOpGenerator";
    
    protected int iterations = 3; // default to 3 iterations
    
    public BackupBasic() {
        super();
    }

    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("Basic Backup Testing\n");  
        sb.append("\t" + HoneycombTestConstants.PROPERTY_ITERATIONS + 
                  " - specifies the number of incremental backups to do.");
        
        return sb.toString();
    }

    protected boolean shouldRun = false;
    public void setUp() throws Throwable {
        self = createTestCase("BackupBasic","Simple Backup testcase.");
        self.addTag("backup");
       
        shouldRun = self.excludeCase(); 
        if (shouldRun) // should I run
            return;
     
        init();
	}
    
    public void init() throws HoneycombTestException {
    	super.init();
        
        cm.setQuorum(true);
        cm.initClusterState();
        
        verifyPackage(SUNWHCWBCLUSTER_PACKAGE);
        
        String s = getProperty(HoneycombTestConstants.PROPERTY_ITERATIONS);
        
        if (s != null) 
            iterations = new Integer(s).intValue();
    }
    
    public void tearDown() throws Throwable {
        if (shouldRun) // should I run?
            return;
        
        if (!finished) {
           self.postResult(false); 
        } else {
        	int numBks = 0;
            while (numBks < iterations) { 
            	new File(BK_LOCATION + numBks).delete();
            	// output files from backups
            	new File(BK_LOCATION + numBks + BK_OUTPUT_EXT).delete();
            	// output files from restores
            	new File(BK_LOCATION + numBks + RT_OUTPUT_EXT).delete();
                numBks++;
            }
         
        	// clean up backup snapshot in case it was already created and 
        	// we audited everything nice and cleanly
            snapshot_tool.deleteSnapshot(BK_SNAPSHOT);
        	
            Log.INFO("Stopping cluster in dev mode.");
            stopCluster(cm.getNumNodes());
            waitForHCToDie(cm.getNumNodes()); 
           
            Log.INFO("Cleaning data created during test.");
            snapshot_tool.deletedata(); 
            
            Log.INFO("Reverting back to old snapshot.");
            snapshot_tool.restoreSnapshot(SAFE_SNAPSHOT, SnapshotTool.DO_MOVE);
            
            Log.INFO("Starting cluster backup up.");
            startCluster(cm.getNumNodes());
            waitForClusterToStart(cm.getNumNodes());
        }
    }
    
    public void testSloshing() throws HoneycombTestException {
        // Just overwritten to not allow the execution of the sloshing code
    }
  
    public void backupSequence() throws HoneycombTestException { 
        TimeZone tz = TimeZone.getTimeZone("UCT");
        SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yyyy HH:mm:ss");
        sdf.setTimeZone(tz);
    
        String t1 = "", t2 = "";
        Date date = getDate();
        t2 = sdf.format(date);
        int numBks = 0;
            
        while (numBks < iterations) { 
	        t1 = t2;
            doLoad();
	        t2 = sdf.format(getDate());
    	    
        	backup(BK_LOCATION + numBks, t1, t2);
            numBks++;
        }
    }
   
    public void restoreSequence() throws HoneycombTestException { 
        Log.INFO("Restore of last backup.");
        restore(BK_LOCATION + (iterations-1));
        
        /*
         * Sequential restore of all other sessions.
         */
        int numBks = 0;
        while (numBks < iterations-1) {
            restore(BK_LOCATION + numBks);
            numBks++;
        }
    }
    
    public void testBulkOA() throws HoneycombTestException {
        if (shouldRun) // should I run?
            return;
       
        cm.setQuorum(true);
        cm.initClusterState();
       
        Log.INFO("Stopping cluster in dev mode.");
        stopCluster(cm.getNumNodes());
        waitForHCToDie(cm.getNumNodes()); 
     
        Log.INFO("Moving all current data over to a safe snapshot:" + SAFE_SNAPSHOT);
        snapshot_tool.saveSnapshot(SAFE_SNAPSHOT, SnapshotTool.DO_MOVE);

        Log.INFO("Starting cluster up.");
        startCluster(cm.getNumNodes());
        waitForClusterToStart(cm.getNumNodes());
         
        long start = System.currentTimeMillis();
        backupSequence(); 
        long stop = System.currentTimeMillis();
        long timeToBackup = (stop-start);
           
        Log.INFO("*** Disaster! We will simulate a disaster scenario now. ***");
        
        Log.INFO("Stopping cluster in dev mode.");
        stopCluster(cm.getNumNodes());
        waitForHCToDie(cm.getNumNodes());
          
        Log.INFO("Moving all current data over with snapshot tool.");
        snapshot_tool.saveSnapshot(BK_SNAPSHOT, SnapshotTool.DO_MOVE);
            
        Log.INFO("Starting cluster backup up.");
        startCluster(cm.getNumNodes());
        waitForClusterToStart(cm.getNumNodes());
            
        //re init
        cm.setQuorum(true);
        cm.initClusterState();
      
        start = System.currentTimeMillis();
        restoreSequence();
        stop = System.currentTimeMillis();
        long timeToRestore = (stop-start);
        
        doAudit();
        
        performanceResults(timeToBackup, timeToRestore);
       
        self.postResult(true, "BasicBackup testcase completed successfully.");
        finished = true;
    }

    protected double backupMBPerSecond = 0;
    protected double backupObjPerSecond = 0;
    protected double restoreMBPerSecond = 0;
    protected double restoreObjPerSecond = 0;
    
    public void performanceResults(long timeToBackup, long timeToRestore) 
           throws HoneycombTestException { 
    	Audit audit;
    	
		try {
			audit = Audit.getInstance(cluster);
		} catch (Throwable e) {
			throw new HoneycombTestException("Unable to get auditor.",e);
		}
    
        int run_id = Run.getInstance().getId();
    	double storedBytes = audit.countStoredBytes(run_id);
    	double storedMB = storedBytes/HoneycombTestConstants.ONE_MEGABYTE;
    	double storedObjects = audit.countOperations(run_id);
    	
    	timeToBackup = (timeToBackup/1000); 
    	timeToRestore = (timeToRestore/1000); 
  
    	Log.INFO("Stored: " + storedMB + " MB");
    	Log.INFO("Stored: " + storedObjects + " objects");
    	
    	Log.INFO("Total backup time: " + timeToBackup + "s in " + iterations + 
    			 " sessions.");
    	Log.INFO("Total restore time: " + timeToRestore + "s in " + iterations + 
    			 " sessions.");
    
    	backupMBPerSecond = storedMB/timeToBackup;
    	backupObjPerSecond = storedObjects/timeToBackup;
    	
    	restoreMBPerSecond = storedMB/timeToRestore;
    	restoreObjPerSecond = storedObjects/timeToRestore;
    	
    	Log.INFO("Average MB/s on backup:   " + backupMBPerSecond);
    	Log.INFO("Average Obj/s on backup:  " + backupObjPerSecond);
    	Log.INFO("Average MB/s on restore:  " + restoreMBPerSecond);
    	Log.INFO("Average Obj/s on restore: " + restoreObjPerSecond);
    }
   
    private String BACKUP_CMD_LINE = "/opt/test/bin/ndmpClient";
    protected RunCommand runCmd = new RunCommand();
   
    protected void backup(String filename, String startDate, String endDate) 
              throws HoneycombTestException { 
    	try {
    		Log.INFO("backup to " + filename + " from " + startDate + 
    				 " to " + endDate);
    		ExitStatus es = runCmd.exec(BACKUP_CMD_LINE + " " + cluster + 
    				                    "-admin " + filename + " \"" + 
    				                    startDate + "\" \"" + endDate + 
    				                    "\" > " + filename + BK_OUTPUT_EXT +
    				                    " 2>&1");
    
    		int rc = es.getReturnCode();
    		
    		if (rc != 0)  {
    			Log.ERROR("Backup failed with return code: " + rc);
    			Log.ERROR("check " + filename + BK_OUTPUT_EXT + 
    					  " for more information.");
    		
        		throw new HoneycombTestException("Backup failed");
    		}
		} catch (Exception e) {
			throw new HoneycombTestException("Backup failed",e);
		}
	}

    protected void restore(String filename) throws HoneycombTestException { 
    	try {
    		Log.INFO("restore from " + filename);
    		ExitStatus es = runCmd.exec(BACKUP_CMD_LINE + " " + cluster + 
    				                    "-admin " + filename +
    				                    " > " + filename + RT_OUTPUT_EXT +
    				                    " 2>&1");
    	
    		int rc = es.getReturnCode();
    	
    		if (rc  != 0)  {
    			Log.ERROR("Restore failed with return code: " + rc);
    			Log.ERROR("check " + filename + RT_OUTPUT_EXT + 
    					  " for more information.");
        		throw new HoneycombTestException("Restore failed");
    		}
		} catch (Exception e) {
			throw new HoneycombTestException("Restore failed",e);
		}
	}

	public void doAudit() throws HoneycombTestException {
		Log.INFO("Auditing all data...");
        /*
         * Default Stores are done with current client and using 10 processes
         * with 100% Stores with max data size of 1M and for 5m
         * 
         */
        TestRunner.setProperty("factory","AuditFactory");
        
        if (TestRunner.getProperty("clients") == null) {
            String hostname = com.sun.honeycomb.test.util.Util.localHostName();
            TestRunner.setProperty("clients",hostname);
        }
        
        if (TestRunner.getProperty("processes") == null)
            TestRunner.setProperty("processes","20");

        TestRunner.setProperty(HoneycombTestConstants.PROPERTY_NO_LOCKING,"");
        
        TestRunner.setProperty("runid", new Integer(Run.getInstance().getId()).toString());
        TestRunner.setProperty("nodes", new Integer(cm.getNumNodes()).toString());
        
        // TestBed must be re-initiazlied in order to make sure that the new
        // settings above take effect.
        TestBed.getInstance().init();
        
        Suite suite;
        try {
            suite = Suite.newInstance("com.sun.honeycomb.hctest.cases.storepatterns.ContinuousStore");
        } catch (Throwable e) {
            throw new HoneycombTestException(e);
        }
        
        try {
            suite.setUp();
            suite.run();
            suite.tearDown();        
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }
    } 
    
    public void doLoad() throws HoneycombTestException {
        TestRunner.setProperty("factory","ContinuousMixFactory");
        
        if (TestRunner.getProperty("clients") == null) {
            String hostname = com.sun.honeycomb.test.util.Util.localHostName();
            TestRunner.setProperty("clients",hostname);
        }
       
        /*
         * Default:  5 Client threads
         */
        if (TestRunner.getProperty("processes") == null)
            TestRunner.setProperty("processes",processes);
       
        if (TestRunner.getProperty("operations") == null)
            TestRunner.setProperty("operations",operations);
       
        TestRunner.setProperty("nodes", new Integer(cm.getNumNodes()).toString());
        
        if (TestRunner.getProperty("time") == null)
            TestRunner.setProperty("time",loadTime);
        
        // TestBed must be re-initiazlied in order to make sure that the new
        // settings above take effect.
        TestBed.getInstance().init();
        
        Suite suite;
        try {
            suite = Suite.newInstance("com.sun.honeycomb.hctest.cases.storepatterns.ContinuousStore");
        } catch (Throwable e) {
            throw new HoneycombTestException(e);
        }
        
        try {
            suite.setUp();
            suite.run();
            suite.tearDown();        
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }
    }
}
