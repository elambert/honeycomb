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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;


public class BufferedChannel{

    IS is = new IS();
    OS os = new OS();
    boolean closed = false;
    boolean shared = false;
    private final SocketChannel socketChannel;
    private Selector selector = null;
    private SelectionKey key = null;
    //Reporter reporter = new Reporter();

    public BufferedChannel(SocketChannel socketChannel) throws IOException{
        this.socketChannel = socketChannel;
        socketChannel.configureBlocking(false);
        if (!shared){
            selector = Selector.open();
            key = socketChannel.register(selector, 0);
        }
        // reporter.start();
    }

    void summarize(){
        try{
            System.out.println("BufferedChannel ops : " + 
                               " readable: " + is.available() +
                               " writable: " + os.buffer.position() +
                               " channelReads: " + channelReads +
                               " streamReads: " + streamReads +
                               " channelWrites: " + channelWrites +
                               " streamWrites: " + streamWrites + 
                               " selects: " + selects);
        }
        catch (IOException ioe){
            ioe.printStackTrace();
            throw new RuntimeException(ioe.getMessage());
        }
    }

    private synchronized void close() throws IOException{
        //        summarize();
        //        System.err.println("closing");
        closed = true;
        //reporter.interrupt();
        socketChannel.close();
    }

    boolean share = false;
    
    final private void addOp(int op){
        key.interestOps(key.interestOps() | op);
        selector.wakeup();
    }
    
    final private void removeOp(int op){
        key.interestOps(key.interestOps() & ~op);
        selector.wakeup();
    }

    void select(int op) throws IOException{
        if (share){
            //SharedSelector.register(this, socketChannel, op);
        }
        addOp(op);
        int n = 0;
        while (n == 0 || (key.readyOps() & op) == 0){
            n = selector.select();
            selects++;
        }
        selector.selectedKeys().clear();
    }


    long selects = 0;
    long channelReads = 0; 
    long channelWrites = 0; 

    long streamReads = 0; 
    long streamWrites = 0; 

    long readyReads = 0; 
    long readyWrites = 0; 

    
    private class IS extends InputStream{
        boolean EOF = false;
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 64);
        IS(){
            buffer.limit(0);
        }
        // Returns the number of bytes that can be read (or skipped over) from this input stream 
        // without blocking by the next caller of a method for this input stream.
        public int available() throws IOException{
            return buffer.remaining();
        }

        public void close() throws IOException{
            if (!closed) 
                BufferedChannel.this.close();
        }

        public boolean markSupported(){
            return false;
        }

        private final void channelRead() throws IOException{
            if (EOF)
                return;
            int position = buffer.position();
            if (position == buffer.capacity()){
                buffer.clear();
                position = 0;
            }
            else {
                buffer.limit(buffer.capacity());
            }
            // implement blocking
            int n = 0;
            while (n == 0){
                select(SelectionKey.OP_READ);
                n = socketChannel.read(buffer);
                //                System.out.println("Control channel read after select: " + n);
            }
            if (n == -1){
                EOF = true;
                return;
            }
            channelReads += n;
            buffer.flip();
            buffer.position(position);
        }


        //  Reads the next byte of data from the input stream.
        public int read() throws IOException{
            if (EOF) 
                return -1;
            if  (!buffer.hasRemaining())
                channelRead();
            if (EOF) 
                return -1;
            
            int i = buffer.get() & 0xFF;

            // allow next channelRead full buffer    
            if (!buffer.hasRemaining()){
                buffer.limit(0);
                buffer.position(0);
            }
            streamReads++;
            return i;
        }

        //   Reads some number of bytes from the input stream and stores them into the buffer array b.
        public int read(byte[] b) throws IOException{
            if (EOF)
                return -1;
            if (b.length == 0)
                return 0;
            if  (!buffer.hasRemaining())
                channelRead();
            if (EOF) 
                return -1;
            int n = Math.min(buffer.remaining(), b.length);
            buffer.get(b, 0, n);
            return n;
        }
    }

    private class OS extends OutputStream {

        ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 64);

        public void close() throws IOException{
            if (!closed) 
                BufferedChannel.this.close();
        }

        public void flush() throws IOException{
            //System.out.println("Calling flush with " + buffer.position());
            buffer.flip();
            int i = socketChannel.write(buffer);
            channelWrites += i;
            //System.err.println("Control channel wrote " + i + " remaining: " + buffer.remaining());
            while (buffer.hasRemaining()){
                select(SelectionKey.OP_WRITE);
                i = socketChannel.write(buffer);
                channelWrites += i;
                //System.err.println("Control channel wrote after select " + i + " remaining: " + buffer.remaining());
            }
            //summarize();
            buffer.clear();
        }

        public void write(int b) throws IOException{
            // if full, wait for buffer to be fully drained
            if (!buffer.hasRemaining()){
                flush();
            }
            buffer.put((byte) b);
            streamWrites++;
        }
    }
    public InputStream getInputStream(){
        return is;
    }
    public OutputStream getOutputStream (){
        return os;
    }

    class Reporter extends Thread{
        Reporter(){super("Reporter");}
        public void run(){
            try{
                while (true){
                    Thread.sleep(52000);
                    summarize();
                }
            }
            catch (InterruptedException ie){
                if (!closed)
                    System.err.println("Reporter exited with " + ie);
            }
            catch (Exception e){
                System.err.println("Reporter exited with " + e);
            }
        }
    }
}
