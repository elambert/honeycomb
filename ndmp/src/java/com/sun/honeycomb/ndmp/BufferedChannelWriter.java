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



package com.sun.honeycomb.ndmp;

import java.nio.channels.WritableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.io.IOException;


public class BufferedChannelWriter implements WritableByteChannel{

    private final ByteBuffer buffer;
    private final SocketChannel socketChannel;
    private Selector selector = Selector.open();


    private long backgroundSelects = 0;
    private long foregroundSelects = 0;
    private long emptyWrites = 0;
    SelectionKey key;


    int bufSize = Integer.parseInt(System.getProperty("buffer", "65536"));
    int minOwnWrite = Integer.parseInt(System.getProperty("minOwnWrite", "65536"));


    BufferedChannelWriter(SocketChannel socketChannel) throws IOException, ClosedChannelException{
        this.socketChannel = socketChannel;
        socketChannel.configureBlocking(false);
        buffer = ByteBuffer.allocateDirect(bufSize);
        key = socketChannel.register(selector, SelectionKey.OP_WRITE, this);
    }

    public synchronized int write(ByteBuffer src)
        throws IOException{
        int n = src.remaining();
        if (src.remaining() < buffer.remaining() && src.remaining() < minOwnWrite){
            buffer.put(src);
        }
        else{
            flush();
            channelWrite(src);
        }
        return n;
    }


    public void	flush() throws IOException {
        if (buffer.position() != 0){
            buffer.flip();
            channelWrite(buffer);
            buffer.clear();
        }
    }

    private void channelWrite(ByteBuffer bb) throws IOException {
        int i = socketChannel.write(bb);
        //System.err.println("Data channel wrote " + i + " remaining: " + bb.remaining());
        while (bb.hasRemaining()){
             int n = selector.select();
             if (n == 0)
                 continue;
             //            if (SharedSelector.register(this, socketChannel, SelectionKey.OP_WRITE))
             i = socketChannel.write(bb);
             //System.err.println("Data channel wrote after select " + i + " remaining: " + bb.remaining());
            //SharedSelector.register(this, socketChannel, 0);
            selector.selectedKeys().clear();
        }
    }

    public void	close() 
        throws IOException{
        flush();
        //SharedSelector.deregister(socketChannel);
        key.cancel();
        selector.wakeup();
        socketChannel.close();
    }

    public boolean 	isOpen(){
        return socketChannel.isOpen();
    }
}
