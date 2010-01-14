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

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.cluster_membership.*;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Abstract class common to all messages exchanged in the ring. 
 * It defines the contract API that all messages have to 
 * implement and includes volatile (non transmitted) data.
 * An internal timer useful for retransmission and a counter to keep 
 * track of the number of times a particular message has been sent.
 */
public abstract class Message extends DataLink {

    private long timeout = System.currentTimeMillis();
    private int  base = 0;
    private int  sent = 0;

    /**
     * Copy this message into the given buffer.
     * @param buffer to copy the message into.
     * @throws CMMException
     */
    public abstract void copyInto(ByteBuffer buffer) throws CMMException;

    /**
     * Initialize this message from the given buffer
     * @param buffer to copy the message from
     * @throws CMMException 
     */
    public abstract void copyFrom(ByteBuffer buffer) throws CMMException;

    /**
     * @return the frame type for this message
     */
    public abstract FrameType getType();


    /*******
     * API
     *******/

    public Node getNodeSource() {
        return NodeTable.getNode(getSource());
    }

    public void arm(int value) {
        timeout = System.currentTimeMillis() + value;
        base = value;
    }

    public long remainingTime() {
        long delta = timeout - System.currentTimeMillis();
        if (delta < 0) {
            return 0;
        }
        return delta;
    }

    public boolean hasExpired() {
        return (remainingTime() == 0);
    }

    public int sendCount() {
        return sent;
    }

    public int getFrameId() {
        return getFrameDL();
    }

    public void nextFrameId() {
        nextFrameDL();
    }
    
    public boolean isFromNetwork() {
        return super.isFromNetwork();
    }
    
    public void isFromNetwork (boolean b) {
        super.isFromNetwork(b);
    }
    
    public void setRequestId(int id) {
        super.setRequestId(id);
    }

    public int getRequestId() {
        return super.getRequestId();
    }
    
    public long retransmitIfExpired(WritableByteChannel channel) 
        throws CMMException 
    {
        long time = remainingTime();
        if (time == 0) {
            send(channel);
            return remainingTime();
        }
        return time;
    }

    public boolean send(WritableByteChannel channel) throws CMMException 
    {
        arm(base);
        if (channel != null) {
            DataLink.send(this, channel);
            sent++;
            return true;
        }
        return false;
    }

    public static Message receive(ReadableByteChannel channel) 
        throws CMMException 
    {
        return(receive(channel, 1));
    }
    
    public static Message receive(ReadableByteChannel channel, int nbRetries) 
        throws CMMException 
    {
        return DataLink.receive(channel, nbRetries);
    }


    public final boolean equals(Object obj) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            if (getFrameId() == msg.getFrameId() &&
                getSource() == msg.getSource()) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("[");
        Node source = getNodeSource();
        if (source == null) {
            sb.append("node ?? ");
        } else {
            sb.append("node:" + source.nodeId());
        }
        sb.append(" frameId:" + getFrameId());
        sb.append(" requestId:" + getRequestId());
        if (isFromNetwork()) {
            sb.append(" FROMNWK");
        } else {
            sb.append(" LOCAL");
        }
        sb.append("] ");
        return sb.toString();
    }
    
    /*
     * ABI compatibility with C code CMM protocol
     * Need to go away at one point.
     */
    
    public void compatSend(WritableByteChannel channel) 
        throws CMMException 
    {
        if (channel != null) {
            sent++;
            DataLink.compatSend(this, channel);
        }
        arm(base);
    }
    
    public static Message compatReceive(ReadableByteChannel channel) 
        throws CMMException 
    {
        return DataLink.compatReceive(channel);
    }
    
}
