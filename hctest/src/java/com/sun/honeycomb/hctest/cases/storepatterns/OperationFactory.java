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

 
       
package com.sun.honeycomb.hctest.cases.storepatterns;

import java.util.ArrayList;

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;


/**
   This factory generates a continuous stream of stores, fetches,
   and what-have you. Need to implement a small revolving queue of
   operations N deep so some degree of orthoganilty can be preserved.
   It would also be nice if we could use this to do same node
   and round robin store/fetches.
 
*/

public abstract class OperationFactory implements OperationFactoryInterface,Runnable {
    protected long _maxFileSize;
    protected long _minFileSize;
    protected long _startTime;
    protected long _runMilliseconds;
    protected long _totalDataAllowed;
    protected long _totalFilesAllowed;
    
    static boolean timeToStop = false;
    
    Audit auditor = null;
    
    private ThreadLauncher threadLauncher = null;
    
	protected TestCase self;
	protected Suite suite;
    
    public void init(Suite suite) throws HoneycombTestException {
    	
    	this.suite = suite;
    	
    	Runtime.getRuntime().addShutdownHook(new Thread(){
    		public void run() {
    			shutDownHook();
    		}});
    	
    	_maxFileSize=Settings.getSize(Settings.MAX_SIZE_PARAMETER,
                Settings.MAX_SIZE_PARAMETER_DEFAULT);
    	_minFileSize=Settings.getSize(Settings.MIN_SIZE_PARAMETER,
		                Settings.MIN_SIZE_PARAMETER_DEFAULT);
		_runMilliseconds=Settings.getTime(Settings.RUN_MILLISECONDS,
		                        (int)Settings.RUN_MILLISECONDS_DEFAULT);        
    	
        _totalDataAllowed =Settings.getSize(Settings.TOTAL_DATA_PARAMETER,
                                            Settings.TOTAL_DATA_PARAMETER_DEFAULT);
        _totalFilesAllowed =Settings.getSize(Settings.NUMFILES,
                                            Settings.NUMFILES_DEFAULT);
        _startTime= System.currentTimeMillis(); 
        
        // Auditor 
        try {
			auditor = OperationGenerator.getAuditor();
		} catch (Throwable e) {
			throw new HoneycombTestException(e);
		}
		
		new Thread(this).start();
    }
    
    public void setThreadLauncher(ThreadLauncher threadLauncher){
    	this.threadLauncher = threadLauncher;    	
    }
    
    public void shutDownHook(){
       timeToStop = true;
       stopUnLocking();
    }

    protected boolean totalFilesExceeded() {
        if(_totalFilesAllowed==-1) {
            return false;
        } else if (auditor.countOperations(Run.getInstance().getId()) >= _totalFilesAllowed) {
            return true;
        } else {
            return false;
        }
    }

    protected boolean totalDataExceeded() {
        if(_totalDataAllowed==-1) {
            return false;
        } else if (auditor.countStoredBytes(Run.getInstance().getId()) >= _totalDataAllowed) {
            return true;
        } else {
            return false;
        }
    }
    
    public boolean notTimeIsUp(long runlength){
    	if  ( (runlength!=-1) &&
                ( System.currentTimeMillis() - _startTime > runlength)) {
              Log.DEBUG("Time limit reached - runtime: " +
                       (System.currentTimeMillis() - _startTime) +
                       "ms " +
                       "Allowed milliseconds: " +
                       _runMilliseconds);
              return false;
          }
          
          // Just in case the machine goes back in time... 
          if ( System.currentTimeMillis() - _startTime < 0){
          	Log.ERROR("Somehow the _startTime is greater than currentTime!!! starttime: " + _startTime + " currentTime: " + System.currentTimeMillis());
          	return false;
          }
          return true;
    }
    
    public boolean keepGoing(int serverNum,int threadId) {
    	
    	if (timeToStop)
    		return false;
    	
        if( totalFilesExceeded() ) {
            Log.DEBUG("Total files limits reached - total files written: " +
            		  auditor.countOperations(Run.getInstance().getId()) +
                     " bytes " +
                     "Allowed files: " +
                     _totalFilesAllowed);
            return false;
        }
        if( totalDataExceeded() ) {
            Log.DEBUG("Total data limits reached - total data written: " +
            		  auditor.countStoredBytes(Run.getInstance().getId()) +
                     " bytes " +
                     "Allowed bytes: " +
                     _totalDataAllowed);
            return false;
        }
        
        return notTimeIsUp(_runMilliseconds);
    }
    
    
    public void logStopStatus(){
    	if( totalFilesExceeded() ) {
            Log.INFO("Total files limits reached - total files written: " +
            		  auditor.countOperations(Run.getInstance().getId()) +
                     " bytes " +
                     "Allowed files: " +
                     _totalFilesAllowed);
        }
        if( totalDataExceeded() ) {
            Log.INFO("Total data limits reached - total data written: " +
            		  auditor.countStoredBytes(Run.getInstance().getId()) +
                     " bytes " +
                     "Allowed bytes: " +
                     _totalDataAllowed);
        }
        if  ( (_runMilliseconds!=-1) &&
              ( System.currentTimeMillis() - _startTime > _runMilliseconds)) {
            Log.INFO("Time limit reached - runtime: " +
                     (System.currentTimeMillis() - _startTime) +
                     "ms " +
                     "Allowed milliseconds: " +
                     _runMilliseconds);
        }
    }
    

    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("OperationFactory current supported properties:\n");
        sb.append("\tminsize - if the tests supports it, this is the smallest \n");
        sb.append("\t          size object we will attempt to store\n");
        sb.append("\tmaxsize - if the tests supports it, this is the \n");
        sb.append("\t          largest size object we will attempt to store. Defaults to 1gb\n");
        sb.append("\ttime - run time in milliseconds, including startup. \n");
        sb.append("\t       (use S,H,M suffix for seconds, hours, and minutes) (defaults to 30 seconds)\n");
        sb.append("\ttotaldata - stops when this much total data has been written - \n");
        sb.append("\t            defaults to infinite (-1)\n");
        sb.append("\tretrycount - number of times the test is willing to retry for a server error.\n");
        sb.append("\t              -1 is infinite. \n");
        sb.append("\tnumfiles - number of files to write before stop, defaults to -1 (infinite).\n");
        sb.append("\ttimeout - internal timeout value - if a thread doesn't return from a store, this is aborted \n");
        return sb.toString();
    }

    public void logExceptions(ArrayList thrown){
    	for (int j=0; j < thrown.size(); j++) {
            Throwable t = (Throwable) thrown.get(j);
            Log.ERROR("\tException [" + j + "] " + Log.stackTrace(t));
        }
    }
    
	public void checkForException(Operation operation, int serverNum, int threadId) {
		
		String id;
		
		String logtag = null;
		
		if (operation.getResult() != null)
			logtag = operation.getResult().logTag;
		
		if (operation.getRequestedOperation() == Operation.QUERYMETADATA)
			if (operation.getResult() != null)
				id = " result count: " + operation.getResult().query_result_count;
			else
				id = " no result count.";
		else
			id = " OID: " + operation.getOID();
		
		if (!operation.succeeded()) {
	    	if (operation.getResult() != null) {
	    		if (operation.getResult().thrown != null && operation.getResult().thrown.size() != 0){
	    			Log.ERROR(operation.getName() + " FAIL on server:" + serverNum + " thread: " + threadId + id + " [" + logtag + "] with:");	    			
		            logExceptions(operation.getResult().thrown);
	    		}
	    	} else if (operation.getThrown() != null){
	    		Log.ERROR(operation.getName() + " FAIL on server:" + serverNum + " thread: " + threadId + id + " with:");
	    		logExceptions(operation.getThrown());
	    	} else {
	    		Log.ERROR(operation.getName() + " FAIL without result.");
	    	}
		} else {
			Log.INFO(operation.getName() + " OK on server:" + serverNum + " thread: " + threadId + id + " [" + logtag + "]");
		}			
	}
	
	private ArrayList oids_to_free = new ArrayList();
	
	public void freeLocks(Operation doneOp) throws HoneycombTestException {

		if (doneOp.getRequestedOperation() != Operation.DATASTORE)
			oids_to_free.add(doneOp.getOID());
		
		synchronized(this){
			notify();
		}
	}
	
	private boolean stopunlocking = false;
	public void stopUnLocking(){
		stopunlocking = true;
	}
	// Threaded unlocking mechanism
	public void run(){
		do {
			try{
				synchronized(this){
					wait(1000);
				}
			}catch(InterruptedException e){
				//ignore
			}
			
			// timeout + 5m
			if (_runMilliseconds != -1)
				if (!notTimeIsUp(_runMilliseconds + 300000)){
					
					try {
						Log.WARN("RMI controller is shutting down this run. This is not the normal behaviour.");
						Log.WARN("Finishing up testcase correctly and exiting the JVM to guarantee that testcase ends.");
						suite.tearDown();
						System.exit(-1);						
					} catch (Throwable e) {
						Log.ERROR("Error shutting down suite from OperationFactory: " + Log.stackTrace(e));
					}
					
				}
			
			while (oids_to_free.size() != 0) {
				String oid = (String)oids_to_free.remove(0);
			    	try {
						auditor.freeLock(oid);
					} catch (Throwable e){
						Log.ERROR("Unable to unlock oid: " + Log.stackTrace(e));
					}
			}
		} while (!stopunlocking);
	}
	
	public static void forceStop() {
		timeToStop = true;
	}
    
}
