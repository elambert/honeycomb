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


import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.HoneycombRemoteSuite;
import com.sun.honeycomb.hctest.ThreadedAudit;
import com.sun.honeycomb.hctest.util.AuditStatsGenerator;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.HCLocale;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
                              
public class ContinuousStore extends HoneycombRemoteSuite {
	
	private ThreadLauncher launcher;
	private OperationFactoryInterface factory = null;
	
    int _numProcesses=0;
    String _factoryName = "ContinuousMixFactory";
	
	public void setUp() throws Throwable{
		
        TestCase self = createTestCase("LoadTests");
        if (self.excludeCase()) 
            return;
        
        super.setUp();
		
        _numProcesses=Settings.getValue(Settings.NUM_PENDING_PROCESSES,
                Settings.NUM_PENDING_PROCESSES_DEFAULT);
		_factoryName=Settings.getString(Settings.FACTORY_NAME,
				Settings.FACTORY_NAME_DEFAULT);
		
        try {
            SuiteHolder.createHolder(this);
        } catch (HoneycombTestException e) {
            Log.ERROR("Fatal: Failed to setup ContinuousStore suite:: "+e.getMessage());
        }
	}
	    
    private void finishUp() throws HoneycombTestException {      
        String audithost = System.getProperty(HCLocale.PROPERTY_DBHOST);
        if (audithost == null) {
            throw new HoneycombTestException(
                                             "System property not defined: " +
                                             HCLocale.PROPERTY_DBHOST);
        }

    	String cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        
        try {
            if (ThreadedAudit.getInstance(audithost,cluster) instanceof ThreadedAudit){
		ThreadedAudit audit = ((ThreadedAudit)ThreadedAudit.getInstance(audithost,cluster));
		audit.timeToStop();
		Log.INFO("Waiting for threaded audit to end...");
		while (!audit.isAllDone()){
			Thread.sleep(1000);
		}
	    } else {
		Log.INFO("Normal Audit being used...");
	    }
	} catch (Throwable e) {
		Log.ERROR("Error waiting for theaded audit: " + Log.stackTrace(e));
	}
		
        AuditStatsGenerator stats = new AuditStatsGenerator(audithost,TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER));
        stats.generateStats("" + Run.getInstance().getId());
        
        factory.shutDownHook();		
    }

    public void testRandomAccess() throws HoneycombTestException { 
        if (_numProcesses == 0) {
            // This test was never setUp.   Don't run it.
            return;
        }


        factory = getOperationFactory();
        
        try {
			factory.init(this);
		} catch (HoneycombTestException e) {
			throw new HoneycombTestException("Factory init error: " + e.getMessage());
		}
		
		try {			
			if (factory.excludeCase())
				return;
			
			String nodes = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NODES);
			if (nodes == null || nodes.trim().length() == 0) {
				AuditStatsGenerator.setFailedOps();
				throw new HoneycombTestException("Property 'nodes' must be set!");
			}
	        
	        launcher = new ThreadLauncher(testBed.clientCount(),
	                _numProcesses,
	                factory);
	        
	        factory.setThreadLauncher(launcher);
	        launcher.execute();
	        
	        factory.logStopStatus();
		} catch (Throwable t){
			// catch these exceptions and log under factory testcase
			Log.ERROR(Log.stackTrace(t));
			AuditStatsGenerator.setFailedOps();
		} finally {
			finishUp();
		}
    }
    
    private OperationFactoryInterface getOperationFactory() throws HoneycombTestException{
    	// Only accept factories within this same current package!!!
        try {
			Class c = Class.forName("com.sun.honeycomb.hctest.cases.storepatterns." + _factoryName);
			
			Object factoryObject = c.newInstance();
			OperationFactoryInterface factory = null;
			
			if (factoryObject instanceof OperationFactory){
				factory =  (OperationFactory) factoryObject;
			} else {
				throw new HoneycombTestException("Factory not instance of OperationFactory.");
			}
			
			Log.INFO(_factoryName + " loaded.");
			
			return factory;			
	    } catch (ClassNotFoundException e) {
	    	throw new HoneycombTestException("Factory not found: " + _factoryName + " in package: com.sun.honeycomb.hctest.cases.storepatterns.*");
		} catch (InstantiationException e) {
			throw new HoneycombTestException("Factory error: " + e.getMessage());
		} catch (IllegalAccessException e) {
			throw new HoneycombTestException("Factory error: " +e.getMessage());
		}
    }

    
    public String help() {

        StringBuffer sb = new StringBuffer();

        sb.append("\tCurrent arguoments supported:\n");
        sb.append("\tprocesses - number of processes per supplied \n");
        sb.append("\t            client node that we support, defaults to one.\n");
        sb.append("\tfactory - the name of the factory to use during this execution. default: ContinuousMixFactory\n");
        
        sb.append(getFactoryHelp());
        
        return sb.toString();
    }

    public String getFactoryHelp(){
    	try {
        	// Need factory name in order to instantiate factory to access help method...
        	_factoryName=Settings.getString(Settings.FACTORY_NAME,
                    Settings.FACTORY_NAME_DEFAULT);
			return getOperationFactory().help();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			return "No help from factory " + _factoryName + ".\nReason: " + e.getMessage();
		}
    }   
}
