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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.cases.storepatterns.OperationFactory;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.SnapshotTool;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RunCommand;

public class BackupOverlaps extends BackupBasic {

	protected static final String SAFE_SNAPSHOT = "safe_bk_" + System.currentTimeMillis();
    protected static final String BK_SNAPSHOT = "bk_" + System.currentTimeMillis();
    protected static final String BK_LOCATION = "/mnt/test/backup-" + System.currentTimeMillis() + ".";
    
    protected static final String BK_OUTPUT_EXT = ".bk.output";
    protected static final String RT_OUTPUT_EXT = ".rt.output";
     
    protected String loadTime;
    protected String processes = "1";    // 5 threads
    protected long intervalTime = 60000; // 1 minute milliseconds
    protected boolean loadRunning = false;
    
    protected TimeZone tz = TimeZone.getTimeZone("UCT");
    protected SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yyyy HH:mm:ss");
  
    /*
     * Default: 75% stores, 5% Deletes and 20% addmd to existing oids
     */
//    protected String operations = "75%StoreOpGenerator,5%DeleteOpGenerator,20%MDStaticOpGenerator";
//    protected String operations = "75%StoreOpGenerator,25%MDStaticOpGenerator";
//    protected String operations = "20%StoreOpGenerator,5%MDStaticOpGenerator";
    public BackupOverlaps() {
    	super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("Backup Overlaps Testing\n");  
        
        return sb.toString();
    }
    
    
    protected boolean shouldRun = false;
    public void setUp() throws Throwable {
        self = createTestCase("BackupOverlaps","Backup Overlaps Testcase.");
        self.addTag("backup");
       
        shouldRun = self.excludeCase(); 
        if (shouldRun) // should I run
            return;
     
        init();
	}
    
    public void init() throws HoneycombTestException {	
    	super.init();
    	sdf.setTimeZone(tz);
    }

    
    public void tearDown() throws Throwable {
        if (shouldRun) // should I run?
            return;
        if (!finished) {
            self.postResult(false);
        } else {
	        for (int i = 1; i <= 3; i++) {
	        	new File(BK_LOCATION + "b" + i).delete();
	        	// output files from backups
	        	new File(BK_LOCATION + "b" + i + BK_OUTPUT_EXT).delete();
	        	// output files from restores
	        	new File(BK_LOCATION + "b" + i + RT_OUTPUT_EXT).delete();
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
   
    public void testBulkOA() throws HoneycombTestException {
        // Just overwritten to not allow the execution of the backup basic code
    }
    
    
    public void test_Overlapping_Backups() throws HoneycombTestException {
        if (shouldRun) // should I run?
            return;
        Log.INFO("Running: \"test_2_Overlapping_Backups_Interleaved\"");
        Log.INFO(" - Run some load");
        Log.INFO(" - Backup from t1 to t3");
        Log.INFO(" - Run some load");
        Log.INFO(" - Backup from t2 to t4");
        Log.INFO(" - Run some load");
        Log.INFO(" - Backup from t3 to t5, where t1 < t2 < t3 < t4 < t5");
        Log.INFO(" - Wipe");
        Log.INFO(" - Restore");
        Log.INFO(" - Verify");

        cm.setQuorum(true);
        cm.initClusterState();
        
        Log.INFO("Waiting for cluster to start.");
        waitForClusterToStart(cm.getNumNodes());
        
        Log.INFO("Stopping cluster in dev mode.");
        stopCluster(cm.getNumNodes());
        waitForHCToDie(cm.getNumNodes()); 
     
        Log.INFO("Moving all current data over to a safe snapshot:" + SAFE_SNAPSHOT);
        snapshot_tool.saveSnapshot(SAFE_SNAPSHOT, SnapshotTool.DO_MOVE);

        Log.INFO("Starting cluster up.");
        startCluster(cm.getNumNodes());
        waitForClusterToStart(cm.getNumNodes());
        
       
        loadTime = "43200s"; //12 hrs to ensure it doesnt stop when doing backups"
        TestRunner.setProperty("time",loadTime);
        
        // Do Load
        Log.INFO("Running a Parallel Load Thread.");
        SimpleLoad load = new SimpleLoad();
        load.start();
        
        long t1 = System.currentTimeMillis();
        Log.INFO(" ---> t1: " + sdf.format(new Date(t1)));
        Log.INFO(" ---> Sleeping for " + formatTime(intervalTime));
        pause(intervalTime);
        long t2 = System.currentTimeMillis();
        Log.INFO(" ---> t2: " + sdf.format(new Date(t2)));
        Log.INFO(" ---> Sleeping for " + formatTime(intervalTime));
        pause(intervalTime);
        long t3 = System.currentTimeMillis();
        Log.INFO(" ---> t3: " + sdf.format(new Date(t3)));
        Log.INFO(" ---> Backing up interval (b1): " + sdf.format(new Date(t1)) + 
        		" -> " + sdf.format(new Date(t3)));
        backupInterval(t1, t3, "b1");
        
        Log.INFO(" ---> Sleeping for " + formatTime(intervalTime));
        pause(intervalTime);
        long t4 = System.currentTimeMillis();
        Log.INFO(" ---> t4: " + sdf.format(new Date(t4)));
        Log.INFO(" ---> Backing up interval (b2): " + sdf.format(new Date(t2)) + 
        		" -> " + sdf.format(new Date(t4)));
        backupInterval(t2, t4, "b2");
        
        Log.INFO(" ---> Sleeping for " + formatTime(intervalTime));
        pause(intervalTime);
        long t5 = System.currentTimeMillis();
        Log.INFO(" ---> t5: " + sdf.format(new Date(t5)));
        Log.INFO(" ---> Backing up interval (b3): " + sdf.format(new Date(t3)) + 
        		" -> " + sdf.format(new Date(t5)));
        backupInterval(t3, t5, "b3");
        
        
        Log.INFO(" ---> Stopping Load Thread...");
        load.interrupt();
        pause(10000);
        while (loadRunning) {
            Log.INFO(" ---> Sleeping for " + formatTime(60000));
            pause(60000);
        }
        	
        	
       
        Log.INFO("*** Disaster! We will simulate a disaster scenario now. ***");
        
        Log.INFO("Stopping cluster in dev mode.");
        stopCluster(cm.getNumNodes());
        waitForHCToDie(cm.getNumNodes());
          
        Log.INFO("Moving all current data over with snapshot tool.");
        snapshot_tool.saveSnapshot(BK_SNAPSHOT, SnapshotTool.DO_MOVE);
            
        Log.INFO("Starting cluster back up.");
        startCluster(cm.getNumNodes());
        waitForClusterToStart(cm.getNumNodes());
            
        //re init
        cm.setQuorum(true);
        cm.initClusterState();
      
        // Do Restores
        Log.INFO(" ---> Restoring (b3)");
        restoreInterval("b3");
        Log.INFO(" ---> Restoring (b2)");
        restoreInterval("b2");
        Log.INFO(" ---> Restoring (b1)");
        restoreInterval("b1");
       
        doAudit();
        
        self.postResult(true, "Backup Overlaps testcase completed successfully.");
        finished = true;
    }
    
    public void backupInterval(long t1, long t2, String name) throws HoneycombTestException { 

        Date date1 = new Date(t1);
        Date date2 = new Date(t2);
        String time1 = sdf.format(date1);
        String time2 = sdf.format(date2);

        backup(BK_LOCATION + name, time1, time2);
    }
   
    public void restoreInterval(String name) throws HoneycombTestException {     	
        restore(BK_LOCATION + name);
    }
    
    private static final Object [][] TIME_METRICS = new Object[][] {
        {new Double(1), "millisecond(s)"},
        {new Double(1000), "second(s)"},
        {new Double(60), "minute(s)"},
        {new Double(60), "hour(s)"}
    };
    
    public static String formatTime(long milli) {
        DecimalFormat df = new DecimalFormat( "##.##" );
        int metric = 0;
        while (milli >= 60 && metric < 3) {
            metric++;
            Double divisor = (Double) TIME_METRICS[metric][0];
            milli /= divisor.longValue();
        }
        return df.format(milli) + " " + TIME_METRICS[metric][1];
    }
        
    public class SimpleLoad extends Thread {    

    	public void run() {               
    		try {
    			Log.INFO("Load Thread Started.");
    			operations = "20%StoreOpGenerator,5%MDStaticOpGenerator";
    			loadRunning = true;
    			doLoad();
    			loadRunning = false;
    			Log.INFO("Load Thread Stopped.");
			} catch (HoneycombTestException e) {
				Log.stackTrace(e);
				Log.ERROR("Problem with doLoad");
			}
    	}
    	
    	public void interrupt() {
    		OperationFactory.forceStop();
    	}    
    }
}
