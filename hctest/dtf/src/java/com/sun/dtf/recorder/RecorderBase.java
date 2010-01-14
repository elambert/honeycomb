package com.sun.dtf.recorder;

import com.sun.dtf.exception.RecorderException;

public abstract class RecorderBase {
   
    protected boolean _append = true;
    
    public RecorderBase(boolean append) {
        _append = append;
    }

    public abstract void record(Event counter) throws RecorderException;
    public abstract void start() throws RecorderException;
    public abstract void stop() throws RecorderException;
}
