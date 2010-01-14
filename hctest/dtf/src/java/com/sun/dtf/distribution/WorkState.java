package com.sun.dtf.distribution;

import com.sun.dtf.exception.DTFException;

public abstract class WorkState {
   
    private DTFException _exception = null;
    private int _workerCount = 0;
   
    public WorkState(int workerCount) { 
        _workerCount = workerCount;
    }
   
    /**
     * 
     * @throws DTFException
     */
    public synchronized final void waitForFinish() throws DTFException {
        while (_workerCount > 0) { 
            try {
                wait();
            } catch (InterruptedException e) { }
        }
        checkForException();
    }
  
    /**
     * 
     * @throws DTFException
     */
    public final void checkForException() throws DTFException { 
        if (_exception != null)
            throw _exception;
    }
    
    /**
     * 
     * @param exception
     */
    public final void reportException(DTFException exception) {
        _exception = exception;
    }

    /**
     * 
     */
    public synchronized final void allDone() {
        _workerCount--;
        notify();
    }
    
    /**
     * 
     * @return
     * @throws DTFException
     */
    public abstract boolean doWork(int workerId) throws DTFException;
}
