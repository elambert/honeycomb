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

import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class BackupBasicPerfBigOps extends BackupBasicPerfSmallOps {

    private double EXPECTED_BACKUP_MB_PER_SECOND = 5.0;
	private double EXPECTED_RESTORE_MB_PER_SECOND = 11.0;
	
	public void setUp() throws Throwable {
		self = createTestCase("BackupBasicPerfBigOps", 
				              "backup/restore performance tests");
		self.addTag("backup");

		shouldRun = self.excludeCase();
		if (shouldRun) // should I run
			return;

		super.init();

		Log.INFO("PerfTests will hard code the test paramters to the following:");
		setupProps();
		printProps();
		Log.INFO("\t filesizes  >= 50M");
        
		/*
		 * Through put tests should only store big files.
		 * In this case the mix1 only has 1K, 1MB and 50MB files so we're only
		 * going to store 50MB files.
		 */
		TestRunner.setProperty("minsize", "50M");
	}
	
	protected void checkPerf() throws HoneycombTestException {
	    if (!withIn(backupMBPerSecond,EXPECTED_BACKUP_MB_PER_SECOND,PERF_MARGIN)) { 
	        throw new HoneycombTestException("Performance of backup was not close enough to " +
	                                         EXPECTED_BACKUP_MB_PER_SECOND + "MB/s");
	    }
	    
	    if (!withIn(restoreMBPerSecond,EXPECTED_RESTORE_MB_PER_SECOND,PERF_MARGIN)) { 
	        throw new HoneycombTestException("Performance of restore was not close enough to " + 
	                                         EXPECTED_RESTORE_MB_PER_SECOND + "MB/s");
	    }
	}
}