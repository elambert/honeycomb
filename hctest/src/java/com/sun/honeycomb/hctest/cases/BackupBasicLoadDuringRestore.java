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


public class BackupBasicLoadDuringRestore extends BackupBasic {

	public void setUp() throws Throwable {
		self = createTestCase("BackupBasicLoadDuringRestore", 
				              "restore with load tests.");
		self.addTag("backup");

		shouldRun = self.excludeCase();
		if (shouldRun) // should I run
			return;

		super.init();
	
		// We need to have enough load and what not generated to correctly 
		// have backup/restore during load hitting the cluster.
		loadTime = "300s";
	}
	
	public void restoreSequence() throws HoneycombTestException {
		// sleep for the value of 1 load iteration this way we'll be past the
		// first tape before we start pointing load at the cluster
		LoadThread load = new LoadThread();
		loadTime=300*3 + "s";
		Log.INFO("Starting load during restore.");
		load.start();
		try { 
			super.restoreSequence();
		} finally { 
			try {
				load.join();
				Log.INFO("Finished load during restore.");
			} catch (InterruptedException e) {
				new HoneycombTestException("Unable to join on LoadThread.",e);
			}
		}
	}

	protected class LoadThread extends Thread {
		private HoneycombTestException _exception = null;
		
		public LoadThread() { 
		}
		
		public void run() {
			try {
				doLoad();
			} catch (HoneycombTestException e) {
				_exception = e;
			}
		} 
		
		public HoneycombTestException getException() { 
			return _exception;
		}
	}
}