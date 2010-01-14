package com.sun.dtf.recorder;

import com.sun.dtf.exception.RecorderException;

public class NullRecorder extends RecorderBase {
    
    public NullRecorder(boolean append) {
        super(append);
    }
    
    public void stop() throws RecorderException {}
    public void start() throws RecorderException {}
    public void record(Event counter) throws RecorderException {}
}
