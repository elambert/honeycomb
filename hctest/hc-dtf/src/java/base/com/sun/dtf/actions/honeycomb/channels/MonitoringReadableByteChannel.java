package com.sun.dtf.actions.honeycomb.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Iterator;

public class MonitoringReadableByteChannel implements ReadableByteChannel {
    private long _read = 0;
   
    private ReadableByteChannel _rbc = null;
    private long _lastByteRead = -1;
    
    private ArrayList _bandwidth = null;
    private long _lastMeasurement = -1;
    private long _accumulatedBytes = 0;
    
    public MonitoringReadableByteChannel(ReadableByteChannel rbc) {
        _bandwidth = new ArrayList();
        _rbc = rbc;
    }
    
    public int read(ByteBuffer dst) throws IOException {
        // we need to set the first time we've ever started reading...
        if (_lastMeasurement == -1)
            _lastMeasurement = System.currentTimeMillis();
        
        int read = _rbc.read(dst);
        
        if (read != -1) {
            _accumulatedBytes += read;
            _read += read;
        }
        
        if (System.currentTimeMillis() - _lastMeasurement >= 5000) { 
            _lastMeasurement = System.currentTimeMillis();
            _bandwidth.add(new Long(_accumulatedBytes));
            _accumulatedBytes = 0;
        }
        
        if (read == -1) {
            _lastByteRead = System.currentTimeMillis();
            _bandwidth.add(new Long(_accumulatedBytes));
        }
        
        return read;
    }
    
    public ArrayList getBandWidthUsage() { 
        return _bandwidth;
    }
    
    public String getBandWidthUsageString() { 
        Iterator iterator = _bandwidth.iterator();
        StringBuffer result = new StringBuffer();
       
        while (iterator.hasNext()) 
            result.append(iterator.next() + ",");
        
        result.replace(result.length()-1, result.length(), "");
        return result.toString();
    }
    
    public long getLastByteRead() { return _lastByteRead; } 
    public void close() throws IOException { }
    public boolean isOpen() { return _rbc.isOpen(); }  
}
