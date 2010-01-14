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

import java.util.Iterator;

import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class BackupRestoreRetry extends BackupBasic {

	private static long KILL_TIMEOUT = 10000; // 10 seconds before we kill restore
	
	public void setUp() throws Throwable {
		self = createTestCase("BackupRestoreRetry", 
				              "restore abort and retry backup testcase.");
		self.addTag("backup");

		shouldRun = self.excludeCase();
		if (shouldRun) // should I run
			return;

		super.init();

		if (iterations < 2) {
			Log.WARN("Setting number of backup iterations to 2, because it "
					+ "is necessary for bad order of restore testing.");

			iterations = 2;
		}
	}
	
	public void restoreSequence() throws HoneycombTestException {
		try { 
			RestoreKiller rs = new RestoreKiller(KILL_TIMEOUT);
			rs.start();
			Log.INFO("Restore of backup " + (iterations-1) + 
					 ". This should fail as expected by the tests.");
			restore(BK_LOCATION + (iterations-1));
			throw new HoneycombTestException(
			     "Previous restore should have been killed before succeeding.");
		} catch (HoneycombTestException e) { 
			/*
			 * HACK: 
			 */
			Log.INFO("HACK: waiting 20 minutes because of socket binding issue.");
			try {
				Thread.sleep(1200000);
			} catch (InterruptedException ignore) { }
			
			Log.INFO("Broke restore 0, will now retry.");
			restore(BK_LOCATION + (iterations-1));
		}
        
        /*
         * Sequential restore of all other sessions.
         */
        int numBks = 0;
        while (numBks < iterations-1) {
            restore(BK_LOCATION + numBks);
            numBks++;
        }
	}
	
	public void performanceResults(long timeToBackup, long timeToRestore)
			throws HoneycombTestException {
        Log.INFO("No performance measurements during failure scenarios.");
	}
	
	protected class RestoreKiller extends Thread {
		private HoneycombTestException _exception = null;
		private long _sleeptime = 10000; // 10s default
		
		public RestoreKiller(long sleeptime) { 
			_sleeptime = sleeptime;
		}
		
		public void run() {
			try {
				try {
					Thread.sleep(_sleeptime);
				} catch (InterruptedException ignore) { }
				killRestore();
			} catch (HoneycombTestException e) {
				_exception = e;
			}
		} 
		
		public HoneycombTestException getException() { 
			return _exception;
		}
	}
	
	protected void killRestore() throws HoneycombTestException { 
		ExitStatus es = runCmd.exec("/usr/bin/pkill -9 -f com.sun.honeycomb.ndmp.Client");
   	
   		int rc = es.getReturnCode();
    	
   		if (rc  != 0) {
   		    if (es.getErrStrings() != null) { 
       			Iterator iter =  es.getErrStrings().iterator();
       			while (iter.hasNext()) 
    				Log.ERROR((String)iter.next());
   		    }
			
			Log.ERROR("killRestore didn't succeed with return code: " + rc);
		}
	}
 
}