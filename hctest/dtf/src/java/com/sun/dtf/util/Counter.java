package com.sun.dtf.util;

public class Counter {

    private long _start = -1;
    private long _stop = -1;

    public long getStart() { return _start; }
    public void setStart(long start) { _start = start;}
    
    public long getStop() { return _stop; }
    public void setStop(long stop) { _stop = stop;}
    
    public void start() { _start = System.currentTimeMillis(); } 
    public void stop() { _stop = System.currentTimeMillis(); } 
   
    /**
     * 
     * @return returns the duration of this counter from the time the start 
     *         method was invoked to the time the stop method was invoked.
     */
    public long getDurationInMilliSeconds() { 
        assert(_start != -1);
        assert(_stop != -1);
        return (_stop - _start);
    } 

    public double getDurationInSeconds() { 
        return ((double)getDurationInMilliSeconds())/1000.0f;
    }
    
}
