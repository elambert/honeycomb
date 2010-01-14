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

public class BackupBasicPerfSmallOps extends BackupBasic {

    protected double PERF_MARGIN = 0.20; // 15% margin of error
    private double EXPECTED_BACKUP_OBJ_PER_SECOND = 5.0;
    private double EXPECTED_RESTORE_OBJ_PER_SECOND = 7.0;
    
    public void setupProps() { 
        iterations = 1;
		processes = "1";
		loadTime = "600s";
		operations = "100%StoreOpGenerator";
    }
    
    public void printProps() { 
		Log.INFO("\t iterations = " + iterations);
		Log.INFO("\t processes = " + processes);
		Log.INFO("\t loadTime = " + loadTime);
		Log.INFO("\t operations = " + operations );
    }
    
	public void setUp() throws Throwable {
		self = createTestCase("BackupBasicPerfSmallOps", 
				              "backup/restore performance test.");
		self.addTag("backup");

		shouldRun = self.excludeCase();
		if (shouldRun) // should I run
			return;

		super.init();

		Log.INFO("PerfTests will hard code the test paramters to the following:");
		setupProps();
		printProps();
		Log.INFO("\t filesizes  <= 1K");
	
		/*
		 * Only store 1K files since mix1 smallest size is 1K
		 */
		TestRunner.setProperty("maxsize", "1K");
	}
	
	protected boolean withIn(double value, double expected, double margin) { 
	    return (value > expected*(1.0-margin) && value < expected*(1.0+margin));
	}
	
	protected void checkPerf() throws HoneycombTestException { 
	    if (!withIn(backupObjPerSecond,EXPECTED_BACKUP_OBJ_PER_SECOND,PERF_MARGIN)) { 
            throw new HoneycombTestException("Performance of backup was not close enough to " +
                                             EXPECTED_BACKUP_OBJ_PER_SECOND + "Obj/s");
        }
        
        if (!withIn(restoreObjPerSecond,EXPECTED_RESTORE_OBJ_PER_SECOND,PERF_MARGIN)) { 
            throw new HoneycombTestException("Performance of restore was not close enough to " + 
                                             EXPECTED_BACKUP_OBJ_PER_SECOND + "Obj/s");
        }
	}
	
	public void testBulkOA() throws HoneycombTestException {
	   
	    if (shouldRun) // should I run
            return;
	    
        super.testBulkOA();
        checkPerf();
    }
}