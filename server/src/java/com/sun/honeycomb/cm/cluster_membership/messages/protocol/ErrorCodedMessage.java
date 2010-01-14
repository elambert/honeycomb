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


                                                                                
package com.sun.honeycomb.cm.cluster_membership.messages.protocol;

import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.FrameType;
import java.nio.ByteBuffer;

/**
 * The ErrorCodedMessage message provides an array of nodes in the
 * system which gives us a mechanism to communicate failure on any
 * one of them back to the master for reporting.
 * 
 *  Currently returns a binary pass/fail. Could be enhanced to
 * carry a more useful message.
 */
public abstract class ErrorCodedMessage extends Message {

    public static final int CRC_FAILURE = 1;

    protected long   _version;
    protected int    _master;
    protected int[]  _nodes = new int[] { -1, -1, -1, -1, -1, -1, -1, -1, 
                                          -1, -1, -1, -1, -1, -1, -1, -1 };

    /** 
     * Default constructor. Used by the CMM messaging framework to construct 
     * messages received over a socket.
     */
    public ErrorCodedMessage () {
        _master  = -1;
        _version = -1;
    }

    /**
     * Explicit constructor. Invoked by a CMM Api client on the master node to
     * begin a config update
     */
    public ErrorCodedMessage (int master, long version, int requestId) {
        _master  = master;
        _version = version;
        _nodes[master - 101] = 1; // we're the master, so ack our slot
        arm (CMM.latencyTimeout());
        setRequestId(requestId);
    }


    public void copyInto (ByteBuffer buffer) {
        buffer.putLong (_version);
        buffer.putInt (_master);
        for (int i = 0; i < _nodes.length; i++) {
            buffer.putInt (_nodes[i]);
        }
    }

    public void copyFrom (ByteBuffer buffer) {
        _version = buffer.getLong ();
        _master = buffer.getInt();
        for (int i = 0; i < _nodes.length; i++) {
            _nodes[i] = buffer.getInt();
        }
    }



    /** 
     * Returns the version identifier contained in this update message
     */
    public long getVersion() {
        return _version;
    }



    /**
     * Returns the node from which nodes receiving this message should retreive
     * a new config file.
     */
    public int getMaster() {
        return _master;
    }


    /**
     * Method called by each node as it processes this message. Calling this
     * method signifies the fetch operation was successful
     */
    public void ack (int nodeid) {
        _nodes[nodeid-101] = 1;
    }

    /**
     * Method called by each node as it processes this message. Calling this
     * method signifies the fetch operation failed.
     */
    public void nack (int nodeid) {
        _nodes[nodeid-101] = 0;
    }

    /**
     * If phase two of the config update can begin this method returns true
     */
    public boolean isValid () {
        // Possible values are -1, 0, 1. -1 means the node was not visited
        // by this message, so we should not check it, 1 means the node was
        // visited and succeded in the update, a 0 means the node failed to
        // update. isValid returns true if every node visited was successful
        // in performing the update. Note that  quorum check is still needed
        // before Commit.
        for (int i = 0; i < _nodes.length; i++) {
            if (_nodes[i] == 0) {
                return false;
            }
        }
        return true;
    }

    public void toString(StringBuffer sb) {
        for (int i = 0; i < _nodes.length; i++) {
            sb.append ("n").append(i+101).append("=").append (_nodes[i])
                .append("/");
        }
    }
}

