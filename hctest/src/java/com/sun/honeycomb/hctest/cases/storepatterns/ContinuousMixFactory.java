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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import com.sun.honeycomb.hctest.util.AuditStatsGenerator;
import com.sun.honeycomb.hctest.util.HCTestRegExpUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;

public class ContinuousMixFactory extends OperationFactory {

    private ArrayList _fileSizes = new ArrayList();
 
    private String _mixNames = null;
    private String _operations = null;
    
    private ArrayList percentage = new ArrayList();
    private ArrayList operationMap = new ArrayList();
    
    private NoneOpGenerator _noneOpGenerator = new NoneOpGenerator();
    
    private boolean nolocking = false;

	public void init(Suite suite) throws HoneycombTestException {		
        super.init(suite);
        
        _mixNames= Settings.getString(Settings.MIX_NAMES,
                Settings.MIX_NAMES_DEFAULT);
        
        // Verifies that the mixes is a property with comma separated elements (each element can only contain characters or numbers)
        HCTestRegExpUtil.verifyRegExp(_mixNames,"([a-zA-Z0-9_]*,?)+",Settings.MIX_NAMES);

        parseMixes();
        
        _operations= Settings.getString(Settings.OPERATIONS,
                Settings.OPERATIONS_DEFAULT);
        // Verifies that the operations is a property with comma separated elements 
        // (each element has to be a percentage%classname (classname extends OperationGenerator))
        HCTestRegExpUtil.verifyRegExp(_operations,"([0-9]*%[a-zA-Z0-9_]*,?)+",Settings.OPERATIONS);
        
        parseOperations();
        
        // Init Random...
        RandomUtil.initRandom();    
        
        // Just to verify auditing Needs
        OperationGenerator.verifyAuditingAvailable();
        
        if (OperationGenerator.isLocking()) {
	        Integer lockCount;
			try {
				lockCount = new Integer(auditor.countLocks());
			} catch (HoneycombTestException e){
				throw e;
			} catch (Throwable e){
				throw new HoneycombTestException(e);
			}
	        
	        if (lockCount.intValue() > 0) {
	        	Log.INFO("Attention: " + lockCount + " unlocked OID(s) left from previous run");
	        	Log.INFO("Unlocking these oids...");
	        	try {
					auditor.freeLocks();
				} catch (SQLException e) {
					throw new HoneycombTestException(e);
				} 
	        }
        }
        
        setupTestcase();
    }
	
    
    public String anotherArg(String extra_args){
        if (extra_args == null || extra_args.trim().length() == 0)
            return "";
        else
            return "-" + extra_args;
    }
    
	protected void setupTestcase() {		
	
		boolean store_ops = false;
		boolean store_withmd_ops = false;
		boolean retrieve_md = false;
		boolean retrieve_ops = false;
		boolean metadata_ops = false;
		boolean query_ops = false; 
		boolean delete_ops = false;
		
		for (int i = 0; i < operationMap.size(); i++){
			if (operationMap.get(i).getClass().equals(StoreOpGenerator.class))				
				store_ops = true;
			else if (operationMap.get(i).getClass().equals(StoreWithMDOpGenerator.class)) {
				store_withmd_ops = true;
			} else if (operationMap.get(i).getClass().equals(FetchOpGenerator.class) ||
					  operationMap.get(i).getClass().equals(FreshFetchOpGenerator.class))
				retrieve_ops = true;
			else if (operationMap.get(i).getClass().equals(FetchMDOpGenerator.class)) 
				retrieve_md = true;
			else if (operationMap.get(i).getClass().equals(MDOpGenerator.class) ||
					 operationMap.get(i).getClass().equals(MDRandomOpGenerator.class) || 
					 operationMap.get(i).getClass().equals(MDStaticOpGenerator.class))
				metadata_ops = true;
			else if (operationMap.get(i).getClass().equals(QueryMDOpGenerator.class) ||
					 operationMap.get(i).getClass().equals(QueryMDStaticOpGenerator.class))
				query_ops = true;
			else if (operationMap.get(i).getClass().equals(DeleteOpGenerator.class) ||
					 operationMap.get(i).getClass().equals(FreshDeleteOpGenerator.class))
				delete_ops = true;		
		}
		
		boolean larg_files = false;
		boolean tiny_files = false;
		boolean multichunk_files = false;
		
		for(int i = 0; i < _fileSizes.size(); i++){
			long value = ((Long)_fileSizes.get(i)).longValue(); 
	
			if ( value <= HoneycombTestConstants.ONE_MEGABYTE) {
				tiny_files = true;
			} else if (value > HoneycombTestConstants.ONE_MEGABYTE && value <= HoneycombTestConstants.ONE_GIGABYTE ) {
				larg_files = true;
			} else if (value > HoneycombTestConstants.ONE_GIGABYTE){
				multichunk_files = true;
			}
		}
		
		String filesizes = null;
		
		if (tiny_files && !larg_files && !multichunk_files)
			filesizes = "TinyFiles";
		else if (!tiny_files && larg_files && !multichunk_files)
			filesizes = "LargeFiles";
		else if (!tiny_files && !larg_files && multichunk_files)
			filesizes = "MultiChunk";
		else
			filesizes = "MixedSizes";
	
		String query_type = "Unknown";
		
		String scenario = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NODES) + "-Node";
				
		String extra_args = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_EXTRA_ARGUMENTS);
		
		if (QueryMDStaticOpGenerator.getQueryType() == QueryMDStaticOpGenerator.SIMPLE_QUERY)
			query_type = "SimpleQuery";
		else if (QueryMDStaticOpGenerator.getQueryType() == QueryMDStaticOpGenerator.COMPLEX_QUERY)
			query_type = "ComplexQuery";

		if (store_ops && !store_withmd_ops && !retrieve_ops && !retrieve_md && !metadata_ops && !query_ops && !delete_ops) {
			self = suite.createTestCase("StoreData-" + filesizes + anotherArg(extra_args), scenario, false);
			self.addTag("load-store");
		} else if (!store_ops  && store_withmd_ops && !retrieve_ops && !retrieve_md && !metadata_ops && !query_ops && !delete_ops) {
			self = suite.createTestCase("StoreDataWithMD-" + filesizes + anotherArg(extra_args), scenario, false);
			self.addTag("load-store");
		} else if (!store_ops  && !store_withmd_ops && !retrieve_ops && !retrieve_md && metadata_ops && !query_ops && !delete_ops) {
			self = suite.createTestCase("AddMD" + anotherArg(extra_args), scenario, false);
			self.addTag("load-store-metadata");
		} else if (!store_ops && !store_withmd_ops && retrieve_ops && !retrieve_md && !metadata_ops && !query_ops && !delete_ops) {		
			self = suite.createTestCase("Retrieve-" + filesizes + anotherArg(extra_args), scenario, false);
			self.addTag("load-retrieve");
		} else if (!store_ops && !store_withmd_ops && retrieve_ops && !retrieve_md && !metadata_ops && !query_ops && !delete_ops) {
			self = suite.createTestCase("RetrieveMD" + anotherArg(extra_args), scenario, false);
			self.addTag("load-retrieve-metadata");
		} else if (!store_ops && !store_withmd_ops && !retrieve_ops && !retrieve_md && !metadata_ops && query_ops && !delete_ops) {
			self = suite.createTestCase("QueryMD-" + query_type + anotherArg(extra_args), scenario, false);
			self.addTag("load-query");
		} else { 
			if (!store_ops && !store_withmd_ops && (retrieve_ops || retrieve_md) && !metadata_ops && query_ops && !delete_ops) 
				self = suite.createTestCase("ConcurrentRetrieveQuery" + anotherArg(extra_args), scenario, false);
			else if ((store_ops || store_withmd_ops) && (retrieve_ops || retrieve_md) && !metadata_ops && !query_ops && !delete_ops)
				self = suite.createTestCase("ConcurrentStoreRetrieve" + anotherArg(extra_args), scenario, false);
			else if (!store_ops && !store_withmd_ops && !retrieve_ops && !retrieve_md && !metadata_ops && !query_ops && delete_ops)
				self = suite.createTestCase("BulkDelete" + anotherArg(extra_args), scenario, false);
			else if ((store_ops || store_withmd_ops) && (retrieve_ops || retrieve_md) && !metadata_ops && query_ops && !delete_ops)
				self = suite.createTestCase("ConcurrentStoreRetrieveQuery" + anotherArg(extra_args), scenario, false);
			else if ((store_ops || store_withmd_ops) && (retrieve_ops || retrieve_md) && !metadata_ops && !query_ops && delete_ops)
				self = suite.createTestCase("ConcurrentStoreRetrieveDelete" + anotherArg(extra_args), scenario, false);
			else 
				self = suite.createTestCase("MixedSimulation" + anotherArg(extra_args));
				
			self.addTag("load-mixed");
		}
		
	}
	
	private boolean calledExclude = false;
	private boolean excludeResult;
	
	protected void finishTestcase() {
		
		if (self == null) {
			Log.WARN("ContinuousMixFactory: finishTestcase: TestCase is null, must of been an exception during the init phase.");
			return;
		}
		
		if (calledExclude) {
			if (excludeResult)
				return;
		} else if (self.excludeCase())
				return;	
		
		if (AuditStatsGenerator.anyFailedOps())
			self.testFailed(AuditStatsGenerator.getStats());
		else
			self.testPassed(AuditStatsGenerator.getStats());		
	}
	
	public boolean excludeCase() {
		calledExclude = true;
		excludeResult = self.excludeCase();
		return excludeResult;
	}
	
	private boolean shutdownCalled = false;
    public void shutDownHook() {
    	if (shutdownCalled)
    		return;
    	shutdownCalled = true;
		super.shutDownHook();
		finishTestcase();
	}
	
	private void parseMixes() throws HoneycombTestException{
		StringTokenizer stringTok = new StringTokenizer(_mixNames,",");
        while(stringTok.hasMoreElements()){
        	String mixName = stringTok.nextToken();
        	ArrayList list = HCMixSizesFactory.getFileSizes(mixName);
        	for(int i = 0; i < list.size(); i++){
        		long value = ((Long)list.get(i)).longValue();
        		if (value >= _minFileSize && value <= _maxFileSize)
        			_fileSizes.add((Long)list.get(i));
        	}	        	
        }
        
        if (_fileSizes.size() == 0)
        	Log.WARN("No filesizes to execute Stores with... If you are attempting to do non store operations this is fine otherwise review the mixes and minsize/maxsize that you are applying to this run.");
	}
	
	private void parseOperations() throws HoneycombTestException{
        StringTokenizer stringTok = new StringTokenizer(_operations,",");
        
        long accPerc = 0;
        while(stringTok.hasMoreElements()){
        	String op = stringTok.nextToken();
        	String[] splits = op.split("%");
        	
        	Integer perc = new Integer(splits[0]);
        	accPerc = accPerc + perc.intValue();
        	Object opGenObject = null;
        	OperationGenerator generator = null;
        	Class c;
        	
			try {
				c = Class.forName("com.sun.honeycomb.hctest.cases.storepatterns." + splits[1]);
			
				opGenObject = c.newInstance();
				generator = null;			
			} catch (Throwable e) {
				throw new HoneycombTestException("OperationGenerator not initialized, with name: " + splits[1]);
			}
			
			if (opGenObject instanceof OperationGenerator){
				generator =  (OperationGenerator) opGenObject;
				generator.setMaxFileSize(_maxFileSize);
				generator.setMinFileSize(_minFileSize);
			} else {
				throw new HoneycombTestException("OperationGenerator not found, with name: " + splits[1]);
			}
			
			// Sort as we insert.. :)
			int i = 0;			
			for(i = 0; i < percentage.size(); i++){
				if (perc.intValue() <= ((Integer)percentage.get(i)).intValue())
					break;
			}
			
			// Always place on i except for if at end of the ArrayList
			if (i >= percentage.size()){
				percentage.add(perc);
	        	operationMap.add(generator);         	
			} else {
				percentage.add(i,perc);
	        	operationMap.add(i,generator);         	
			}
        }
        
        if (accPerc > 100)
        	throw new HoneycombTestException("Adding up percentages on operations adds up to more than 100%!");
	}
	    
    public OperationGenerator getNextOperationGenerator() throws HoneycombTestException{
    	int index = RandomUtil.randIndex(100);
   	
    	int acc = 0;
    	for(int i = 0; i < percentage.size(); i++){
    		acc += ((Integer)percentage.get(i)).intValue();
    		if (index <= acc){
    			return (OperationGenerator)operationMap.get(i);
    		}
    	}

    	// Dead Time throw the NoneOpGenerator!
    	return _noneOpGenerator;
    }

    public Operation next(int clientHostNum,int threadId)         
        throws HoneycombTestException {
    	OperationGenerator opGen = getNextOperationGenerator(); 	
        Operation op = opGen.getOperation(_fileSizes);
        return op;                        
    }
   
    public void done(Operation doneOp,int serverNum,int threadId) throws HoneycombTestException {       
        checkForException(doneOp, serverNum, threadId);
        
        if (!nolocking)
        	freeLocks(doneOp);
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append(super.help());
        sb.append("ContinuosMixFactory, properties:\n");
        sb.append("\tmixes - a coma separeted list of mix names to use, \n");
        sb.append("\t        currently mix1, mix2, mix3 and mix4 are supported. Defaults to mix1 \n");
        sb.append("\toperations - a coma separated list of OperationGenerators and their percentage of usage \n");
        sb.append("\t             like so:\n");
        sb.append("\t             operations=50%StoreOpGenerator,50%FetchOpGenerator. Defaults to 50%StoreOpGenerator,50%FetchOpGenerator \n");
        return sb.toString();
    }
}

