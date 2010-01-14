package com.sun.dtf.results;

import org.apache.log4j.Logger;

import com.sun.dtf.exception.ResultsException;


public class ConsoleResults extends ResultsBase  {

    private Logger _logger = null;
    
    public ConsoleResults(Logger logger) { _logger = logger; }
    
    public void start() throws ResultsException { }
    public void stop() throws ResultsException { }

    public void recordResult(Result result) throws ResultsException {
        StringBuffer message = new StringBuffer();
        
        if (result.isTestSuite()) { 
            message.append("Testsuite: ");
            message.append(result.getName());
            message.append((result.isPassResult() ? " passed." : " failed. "));
                    
        } else if (result.isTestCase()) { 
            message.append("Testcase: ");
            message.append(result.getName());
            message.append((result.isPassResult() ? " passed." : " failed."));
        }
        
        message.append(" ");
        
        if (result.getNumPassed() != 0) 
            message.append(result.getNumPassed() + " passed");
        
        if (result.getNumFailed() != 0) 
            message.append(", " + result.getNumFailed() + " failures");
        
        if (result.getNumSkipped() != 0)  
            message.append(", " + result.getNumSkipped() + " skipped"); 
        
        message.append(" testcases.");
                           
        if (message.length() != 0)
            _logger.info(message.toString());
    }
}
