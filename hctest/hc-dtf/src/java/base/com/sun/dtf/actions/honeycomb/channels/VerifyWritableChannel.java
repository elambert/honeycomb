package com.sun.dtf.actions.honeycomb.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

import com.sun.dtf.exceptions.CorruptionException;


/**
 * VerifyWritableChannel will verify the data being written to it against 
 * the ReadableChannel that generated the original data to begin with.
 * This WritableByteChannel can also be used to write out the same data to a 
 * file for safe keeping.
 * 
 * @author Rodney Gomes
 *
 */
public class VerifyWritableChannel implements WritableByteChannel {
 
    private ReadableByteChannel _rbc = null;
    private long _offset = 0;
    
    public VerifyWritableChannel(ReadableByteChannel rbc) { 
        _rbc = rbc;
    }
    
    public int write(ByteBuffer src) throws IOException {
        ByteBuffer buff = ByteBuffer.allocateDirect(src.remaining());

        // fill the buffer to the exact same size as the src
        int read = _rbc.read(buff);
        
        if (read != -1)
            _offset += read;
        
        buff.flip();
        
        if (src.compareTo(buff) != 0)  {
            //Charset charset = Charset.forName("UTF-8");
            //System.out.println("src: " + charset.decode(src));
            //System.out.println("dst: " + charset.decode(buff));
            throw new CorruptionException("Byte mis-match stream at: " + 
                                          _offset);
        }
        
        src.flip();
        return read;
    }

    public void close() throws IOException {
        if (_rbc != null) 
            _rbc.close();
    }

    public boolean isOpen() {
        return _rbc.isOpen();
    }
}
