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

import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class BackupBadOrderRestore extends BackupBasic {

	public void setUp() throws Throwable {
		self = createTestCase("BackupBadOrderRestore", 
				              "bad order restore backup testcase.");
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
	
		// we just need to backup sessions with an object or two not a whole
		// lot of data
		loadTime = "30s";
	}
	
	public void performanceResults(long timeToBackup, long timeToRestore)
		   throws HoneycombTestException {
        Log.INFO("No performance measurements during failure scenarios.");
    }
	
	public void restoreSequence() throws HoneycombTestException {
		Log.INFO("Restore of backup 1.");
        restore(BK_LOCATION + 1);
        
		Log.INFO("Restore of backup 1. This should fail as expected by the tests.");
		try { 
			restore(BK_LOCATION + 1);
		} catch (HoneycombTestException e) { 
			Log.INFO("Caught the failure as expected." + Log.stackTrace(e));
			Log.INFO("Now proceeding to restore in the right order.");
			
			/*
			 * HACK: 
			 */
			Log.INFO("HACK: waiting 20 minutes because of socket binding issue.");
			try {
				Thread.sleep(1200000);
			} catch (InterruptedException ignore) { }
			super.restoreSequence();
			return;
		}
		throw new HoneycombTestException("Test should have failed because of bad order on restore!");
	}
}