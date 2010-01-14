package com.sun.dtf.actions.honeycomb.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class NullWritableByteChannel implements WritableByteChannel {
    
    public int write(ByteBuffer src) throws IOException {
        int written = src.remaining();
        src.flip();
        return written; 
    }

    public void close() throws IOException {
        
    }

    public boolean isOpen() {
        return false;
    }

}
