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

 
package com.sun.honeycomb.cm;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class DynTestService extends TestFaultyService implements SignalHandler {

	public DynTestService(String tag) {
		super(tag);
		install("USR1");
	}
	
	public SignalHandler oldHandler;
	
	// Static method to install the signal handler
	public SignalHandler install(String signalName) {
		Signal diagSignal = new Signal(signalName);
		SignalHandler diagHandler = this;
		oldHandler = Signal.handle(diagSignal, diagHandler);
		return diagHandler;
	}
	
	private boolean throwNow = false;
	private boolean exitNow = false;
	
	public void handle(Signal sig) {
		
		try {
			// Signal recieved do your stuff!
			logger.info("DynTestService recieved signal.");
			
			if (dothrow) {
				// Requested Action is to throw an Exception
				throwNow = true;
			} else if (doexit) {
				exitNow = true;
			}
			
			// Chain back to previous handler, if one exists
			if (oldHandler != SIG_DFL && oldHandler != SIG_IGN) {
				oldHandler.handle(sig);
			}

		} catch (Exception e) {
			logger.severe("Signal handler failed on DynTestService, reason " + e);
		}
	}
	
	/** Run routine of the service.
     */
    public void run() {
        logger.info(serviceName + " RUNNING");
        int count = 0;
        boolean quiet = true; // don't log frequently

        doSleep(runloop_pause, quiet);

        while (keeprunning) {

			if (throwNow) {
				throw new RuntimeException(serviceName + " INTENTIONAL FAILURE");
			}

			if (exitNow) {
				logger.warning(serviceName
						+ " INTENTIONAL EXIT FROM run() METHOD");
				return;
			}
			if (count % loginterval == 0)
				logger.info(serviceName + " OK at time: " + count);
			doSleep(runloop_pause, quiet);
			count += runloop_pause;
		}
        
        logger.info(serviceName + " RUNLOOP EXITING");
    }
}
