package com.sun.dtf.results;

import com.sun.dtf.exception.ResultsException;

public class TestSuiteResults extends ResultsBase  {

    private Result _result = null;
    
    public TestSuiteResults(Result result) { _result = result; } 

    public void start() throws ResultsException { }
    public void stop() throws ResultsException { }

    public void recordResult(Result result) throws ResultsException {
        _result.addResult(result);
    }
}
