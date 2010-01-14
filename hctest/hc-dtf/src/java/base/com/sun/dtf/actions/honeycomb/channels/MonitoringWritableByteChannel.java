package com.sun.dtf.actions.honeycomb.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Iterator;

public class MonitoringWritableByteChannel implements WritableByteChannel {
 
    private WritableByteChannel _wbc = null;
    private long _firstByteWritten = -1;
    
    private ArrayList _bandwidth = null;
    private long _lastMeasurement = -1;
    private long _accumulatedBytes = 0;
    
    public MonitoringWritableByteChannel(WritableByteChannel wbc) {
        _wbc = wbc;
        _bandwidth = new ArrayList();
    }
    
    public int write(ByteBuffer src) throws IOException {
        if (_lastMeasurement == -1)
            _lastMeasurement = System.currentTimeMillis();
        
        int write = _wbc.write(src);
        
        if (write == -1)  {
            _bandwidth.add(new Long(_accumulatedBytes));
            _accumulatedBytes = 0;
        } else
            _accumulatedBytes += write;

        if (_firstByteWritten == -1 && write > 0)  {
            _firstByteWritten = System.currentTimeMillis();
            _lastMeasurement = _firstByteWritten;
        }
        
        if (System.currentTimeMillis() - _lastMeasurement >= 5000) { 
            _bandwidth.add(new Long(_accumulatedBytes));
            _lastMeasurement = System.currentTimeMillis();
            _accumulatedBytes = 0;
        }
        
        return write;
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
    
    public long getFirstByteWritten() { return _firstByteWritten; }

    public void close() throws IOException {
        /*
         * Get the last write from the close ;)
         */
        if (_accumulatedBytes != 0) { 
            _bandwidth.add(new Long(_accumulatedBytes));
        }
        
        if (_wbc != null) 
            _wbc.close();
    }

    public boolean isOpen() { return _wbc.isOpen(); }
}
