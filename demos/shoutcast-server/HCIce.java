/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



import java.nio.channels.Pipe;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import java.io.IOException;


public class HCIce 
    implements Runnable {
    private static final int BUFFER_SIZE = 0x40000;

    private static final Logger LOG = Logger.getLogger("HCIce");

    private Pipe pipe;
    private ByteBuffer buffer;

    public HCIce() {
        _init();
        pipe = null;
        buffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    public void close() 
        throws IOException {
        _close();
    }

    public synchronized WritableByteChannel getWritableByteChannel(String name) {
        while (pipe == null) {
            try {
                wait();
            } catch (InterruptedException ignored) {
            }

            setSong(name);
        }

        return(pipe.sink());
    }

    public void run() {
        int res = 0;
        
        LOG.info("Starting the broadcast");
        _initBroadcast();
        
        synchronized (this) {
            try {
                pipe = Pipe.open();
            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Failed to create pipe",
                        e);
                System.exit(1);
            }
            notify();
        }

        while (res != -1) {
            buffer.clear();
            try {
                do {
                    res = pipe.source().read(buffer);
                } while ((res != -1)
                         && (buffer.remaining() > 0));
            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Got an exception while reading",
                        e);
                res = 0;
            }
            
            if (buffer.position() > 0) {
                _broadcast(buffer.array(),
                           buffer.position());
            }
        }

        synchronized (this) {
            try {
                pipe.source().close();
            } catch (IOException ignored) {
            }
            pipe = null;
        }
            
        _endBroadcast();
        LOG.info("Broadcast is over");
    }

    /**********************************************************************
     *
     * Native methods
     *
     **********************************************************************/

    static {
        System.loadLibrary("HCIce");
    }

    public native void setSong(String name);

    private native void _init();
    private native void _close();

    private native void _initBroadcast();
    private native void _endBroadcast();
    
    private native void _broadcast(byte[] bytes,
                                   int length);
}
