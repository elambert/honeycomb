package com.sun.dtf.results;

import java.util.ArrayList;
import java.util.Properties;

import com.sun.dtf.util.Counter;


public class Result extends Counter {
    
    private final static int TESTCASE_RESULT     = 0; 
    private final static int TESTSUITE_RESULT    = 1; 
    
    private final static int PASS_RESULT = 0;
    private final static int FAIL_RESULT = 1;
    private final static int SKIP_RESULT = 2;
    
    private int _passed  = 0;
    private int _failed  = 0;
    private int _skipped = 0;
    private int _total   = 0;
    
    private String _name = null;
    private Properties _props = null; 
    
    private ArrayList _results = null;
   
    private int _type = -1;
    private int _result = PASS_RESULT;
    
    private String _output = null;
    
    public Result(String name) {
        _name = name;
        _results = new ArrayList();
    }
    
    public ArrayList getResults() { return _results; } 
    public void addResult(Result result) { 
        
        if (result.isTestCase()) {
            if (result.isFailResult()) 
                _failed++;
            else if (result.isPassResult())
                _passed++;
            else if (result.isSkipResult())
                _skipped++;
            
            _total++;
        } else if (result.isTestSuite()) {
            _passed+=result.getNumPassed();
            _failed+=result.getNumFailed();
            _skipped+=result.getNumSkipped();
            _total+=result.getTotalTests();
        }
            
        _results.add(result); 
    } 
    
    public int getResult() { return _result; }
   
    public void setPassResult() { _result = PASS_RESULT; }
    public void setPassResult(Object note) { 
        setPassResult();
        _output = note.toString();
    }
    
    public void setFailResult() { _result = FAIL_RESULT; }
    public void setFailResult(Object note) { 
        setFailResult();
        _output = note.toString();
    }
    
    public void setSkipResult() { _result = SKIP_RESULT; }
    public void setSkipResult(Object note) { 
        setSkipResult();
        _output = note.toString();
    }
    
    public int getNumPassed() { return _passed; } 
    public int getNumFailed() { return _failed; } 
    public int getNumSkipped() { return _skipped; } 
    public int getTotalTests() { return _total; } 
    
    public String getOutput() { return _output; } 
    
    public boolean isPassResult() { return (_result == PASS_RESULT); } 
    public boolean isFailResult() { return (_result == FAIL_RESULT); } 
    public boolean isSkipResult() { return (_result == SKIP_RESULT); } 
    
    public int getType() { return _type; } 
    
    public void setTestcase() { _type = TESTCASE_RESULT; } 
    public void setTestsuite() { _type = TESTSUITE_RESULT; } 
    
    public boolean isTestSuite() { return (_type == TESTSUITE_RESULT); } 
    public boolean isTestCase() { return (_type == TESTCASE_RESULT); } 
    
    public String getName() { return _name; }
    public void setName(String name) { this._name = name; } 
    
    public void setProperties(Properties properties) { _props = properties; }
    public Properties getProperties() { return _props; } 
    
    public String toString() {
        StringBuffer result = new StringBuffer();
       
        if (isTestSuite()) 
            result.append("Testsuite: ");
        
        if (isTestCase()) 
            result.append("Testcase: ");
       
        result.append(getName());
        result.append(" ");
        
        if (isPassResult())
            result.append("passed.");
        
        if (isFailResult())
            result.append("failed.");
        
        if (isSkipResult())
            result.append("skipped.");
        
        if (isTestSuite()) { 
            result.append("\n");
            StringBuffer subResults = new StringBuffer();
            for(int i = 0; i < _results.size(); i++) {
                subResults.append(_results.get(i).toString());
                subResults.append("\n");
            }
            result.append(subResults);
        }
        
        return result.toString();
    }
}
