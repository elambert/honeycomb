package com.sun.dtf.actions.honeycomb.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

public class CorruptingWritableChannel implements WritableByteChannel {
 
    private WritableByteChannel _wbc = null;
    private Random _random = null;
    
    public CorruptingWritableChannel(WritableByteChannel wbc) {
        _wbc = wbc;
        _random = new Random(System.currentTimeMillis());
    }
   
    public int write(ByteBuffer src) throws IOException {
        // corrupt the nth byte
        src.position(Math.abs(_random.nextInt()) % (src.limit()-1));
        src.put((byte)(src.get()^src.get()));
        
        // fill the buffer to the exact same size as the src
        int written = _wbc.write(src);
        src.flip();
        
        return written;
    }

    public void close() throws IOException {
        if (_wbc != null) _wbc.close();
    }

    public boolean isOpen() {
        return _wbc.isOpen();
    }
}
