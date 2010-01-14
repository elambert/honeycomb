package com.sun.dtf.results;

import com.sun.dtf.exception.ResultsException;

public abstract class ResultsBase {
   
    /**
     * 
     * @param result
     * @throws ResultsException
     */
    public abstract void recordResult(Result result) throws ResultsException;
    
    /**
     * 
     * @throws ResultsException
     */
    public abstract void start() throws ResultsException;
    
    /**
     * 
     * @throws ResultsException
     */
    public abstract void stop() throws ResultsException;
}
