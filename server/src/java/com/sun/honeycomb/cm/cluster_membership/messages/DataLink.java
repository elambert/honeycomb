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



package com.sun.honeycomb.cm.cluster_membership.messages;

import java.util.LinkedList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.Node;
import com.sun.honeycomb.cm.cluster_membership.NodeTable;

// ABI compatibility
import com.sun.honeycomb.cm.cluster_membership.messages.api.Register;
import com.sun.honeycomb.cm.cluster_membership.messages.api.C_NodeInfo;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;


/**
 * This class is the data link layer of CMM and enable to
 * send and receive messages.
 * It defines the header and MTU of a frame and guarantees that
 * only compatible messages are exchanged with a deterministics/small
 * latency.
 */
class DataLink {

    private static final int VERSION = 0x11223302;
    private static final int PROTOCOL_TIMEOUT = 100;
    private static final int FRAME_CACHE_SIZE = 16;
    private static final int DEST_BROADCAST = -1;
    private static final int DEST_PEER = -2;

    private static final LinkedList framesList = initCache();
    private static int UID = 1;

    private int version;
    private int uid;
    private int type;
    private int size;
    private int source;
    private int destination;
    private int requestId;
    private boolean isFromNetwork;

    private static final int FRAME_HEADER_SIZE = (8 * 4); // 8 integers

    DataLink() {
        type    = -1;
        size    = 0;
        version = VERSION;
        uid     = generateUID();
        source  = NodeTable.getLocalNodeId();
        destination = DEST_PEER;
        isFromNetwork = false;
        requestId = 0;
        
    }

    static private synchronized int generateUID() {
        return UID++;
    }

    private static LinkedList initCache() {
        LinkedList list = new LinkedList();
        for (int i = 0; i < FRAME_CACHE_SIZE; i++) {
            list.add(ByteBuffer.allocateDirect(CMM.FRAME_MTU));
        }
        return list;
    }

    private static ByteBuffer allocateBuffer() throws CMMException {
        try {
            synchronized (framesList) {
                while (framesList.size() == 0) {
                    framesList.wait();
                }
                return (ByteBuffer) framesList.removeFirst();
            }
        } catch (InterruptedException e) {
            throw new CMMException(e);
        }
    }

    private static void freeBuffer(ByteBuffer buffer) {
        synchronized(framesList) {
            framesList.add(buffer);
            framesList.notifyAll();
        }
    }

    private static void read(ByteBuffer buffer,
                             ReadableByteChannel channel,
                             int nbRetries) 
        throws CMMException {

        int currentRetries = nbRetries;

         do { 
             currentRetries--;
             try {
                 int count = channel.read(buffer);
                 if (count < 0) {
                     throw new IOException("DataLink read");
                 } else if (count == 0) {
                     Thread.sleep(PROTOCOL_TIMEOUT);
                 } else {
                     currentRetries = nbRetries;
                 }
             } catch (Throwable e) {
                 // we want to wrap all exceptions 
                 throw new CMMException(e);
             } 
         } while ((buffer.hasRemaining()) && (currentRetries>=0));

         if (currentRetries == -1) {
             throw new CMMException("DataLink read timed out ["+
                                    nbRetries+" retries]");
         }
    }
    
    private static void write(ByteBuffer buffer, WritableByteChannel channel)
        throws CMMException {

        boolean timeout = false;
        do {
            try { 
                int count = channel.write(buffer);
                if (count < 0) {
                    throw new IOException("DataLink write");
                } else if (count == 0) {
                    if (timeout) {
                        throw new IOException("DataLink write timed out"); 
                    }
                    timeout = true;
                    Thread.sleep(PROTOCOL_TIMEOUT);
                }
            } catch (Throwable e) {
                 // we want to wrap all exceptions 
                throw new CMMException(e);
            }
        } while (buffer.hasRemaining());
    }

    void setHeader(int version, 
                   int uid,
                   int type, 
                   int source, 
                   int destination, 
                   int size,
                   int requestId,
                   int isFromNetwork) 
    {
        this.version = version;
        this.uid     = uid;
        this.type    = type;
        this.source  = source;
        this.destination = destination;
        this.size    = size;
        this.requestId = requestId;
        this.isFromNetwork = (isFromNetwork != 0);
    }

    int getFrameDL() {
        return uid;
    }

    void nextFrameDL() {
        uid = generateUID();
    }

    int getTypeDL() {
        return type;
    }

    public void setDestination(Node node) {
        destination = node.nodeId();
    }
    
    public void setBroadcast() {
        destination = DEST_BROADCAST;
    }
    
    public void setDestinationPeer() {
        destination = DEST_PEER;
    }
    
    public int getSource() {
        return source;
    }
    
    public int getDestination() {
        return destination;
    }
    
    boolean isFromNetwork() {
        return isFromNetwork;
    }
    
    void isFromNetwork (boolean b) {
        isFromNetwork = b;
    }
    
    void setRequestId(int id) {
        requestId = id;
    }
    
    public int getRequestId() {
        return requestId;
    }
    
    
    static void send(Message message, WritableByteChannel channel) 
        throws CMMException {

        ByteBuffer buffer = allocateBuffer();
        try {
            // copy the message
            buffer.clear().position(FRAME_HEADER_SIZE);
            message.copyInto(buffer);

            // insert the header
            buffer.flip();
            buffer.putInt(VERSION);
            buffer.putInt(message.getFrameId());
            buffer.putInt(message.getType().getValue());
            buffer.putInt(buffer.limit());
            buffer.putInt(message.getSource());
            buffer.putInt(message.getDestination());
            buffer.putInt(message.getRequestId());
            buffer.putInt((message.isFromNetwork())? 1:0);

            // send the frame
            buffer.rewind();
            write(buffer, channel);

        } finally {
            freeBuffer(buffer);
        }
    }

    static Message receive(ReadableByteChannel channel, int nbRetries) 
        throws CMMException 
    {
        ByteBuffer buffer = allocateBuffer();
        try {
            // read and check the frame header 
            buffer.clear().limit(FRAME_HEADER_SIZE);
            read(buffer, channel, nbRetries);
            buffer.rewind();
            int version = buffer.getInt();
            int uid     = buffer.getInt();
            int type    = buffer.getInt();
            int size    = buffer.getInt();
            int source  = buffer.getInt();
            int destination = buffer.getInt();
            int requestId = buffer.getInt();
            int isFromNetwork = buffer.getInt();
            
            if (version != VERSION) {
                throw new CMMException("bad frame version ["+version+"]");
            }
            if (size < 0 || size > CMM.FRAME_MTU) {
                throw new CMMException("bad frame size ["+size+"]");
            }

            // create the corresponding message
            Message message = FrameType.createMessage(type);
            message.setHeader(version, uid, type, source, destination, size,
                              requestId, isFromNetwork);

            // read and return the new message 
            buffer.mark().limit(size);
            read(buffer, channel, nbRetries);
            buffer.reset();
            message.copyFrom(buffer);
            return message;

        } finally {
            freeBuffer(buffer);
        }
    }

    /*
     * ABI compatibility with C code CMM protocol
     * Need to go away at one point.
     */

    static void compatSend(Message message, WritableByteChannel channel)
        throws CMMException {

        ByteBuffer buffer = allocateBuffer();
        ByteOrder order = buffer.order();
        try {
            buffer.order(ByteOrder.nativeOrder());
            buffer.clear().position(3);
            message.copyInto(buffer);
            buffer.flip();
            buffer.put((byte)message.getSource());
            buffer.put((byte)message.getDestination());
            buffer.put((byte)message.getType().getValue());
            buffer.rewind();
            write(buffer, channel);
        } finally {
            buffer.order(order);
            freeBuffer(buffer);
        }
    }

    static Message compatReceive(ReadableByteChannel channel) 
        throws CMMException {

        ByteBuffer buffer = allocateBuffer();
        ByteOrder order = buffer.order();
        try {
            buffer.order(ByteOrder.nativeOrder());
            buffer.clear().limit(3);
            read(buffer, channel, 1);
            buffer.rewind();
            int sender = (int)buffer.get();
            int dest   = (int)buffer.get();
            int type   = (int)buffer.get();

            Message msg = FrameType.createMessage(type);
            int size = 3;
            if (msg instanceof Register) {
                size += 1;
            } else if (msg instanceof C_NodeInfo) {
                size += 12;
            } else if (msg instanceof NodeChange) {
                size += 8;
            }
            msg.setHeader(VERSION, 0, type, sender, dest, size, 0, 0);
            buffer.mark().limit(size);
            read(buffer, channel, 1);
            buffer.reset();
            msg.copyFrom(buffer);
            return msg;

        } finally {
            buffer.order(order);
            freeBuffer(buffer);
        }
    }
}
