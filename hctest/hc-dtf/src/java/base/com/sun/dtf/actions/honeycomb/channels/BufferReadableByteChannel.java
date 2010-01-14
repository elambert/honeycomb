package com.sun.dtf.actions.honeycomb.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public abstract class BufferReadableByteChannel implements ReadableByteChannel {

    private byte[] _buffer = null;
    
    private long _length = 0;
    private int _offset = 0;
    private long _read = 0;
    
    private long _lastByteRead = -1;
    
    public BufferReadableByteChannel(long offset, byte[] buffer, long length) {
        // do the offset with ints only and just calculate it the same nasty 
        // way from the beggining.
        _offset = Math.abs((int)offset);
        _buffer = buffer;
        _length = length;
    }
    
    public void init(byte[] buffer, long length) {
        _buffer = buffer;
        _length = length;
    }
    
    public int read(ByteBuffer dst) throws IOException {
        if (_read >= _length) 
            return -1;

        // Populate as many remaining bytes that the dst will take.
        while ((dst.remaining() > 0) && ((_length - _read) > 0)) {
            _offset = _offset % _buffer.length;
            dst.put(_buffer[_offset++]);
            _read++;
        }
        
        if (_length == _read)
            _lastByteRead = System.currentTimeMillis();
        
        return dst.position();
    }
    
    public long getLastByteRead() { return _lastByteRead; } 

    public void close() throws IOException { }
    public boolean isOpen() { return  ((_length - _read) > 0); }
}
