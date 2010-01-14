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

import com.sun.honeycomb.cm.cluster_membership.messages.*;
import com.sun.honeycomb.cm.cluster_membership.Node;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import java.nio.ByteBuffer;

/**
 * Node change notification message
 */
public class Notification extends Message {

    public static final int NODE_ELIGIBLE = 1;
    public static final int NODE_INELIGIBLE = 2;

    private int cause;

    public Notification() {
        cause = -1;
    }

    public Notification(int cause) {
        this.cause = cause;
        arm(CMM.latencyTimeout());
    }

    public int getCause() {
        return cause;
    }

    public FrameType getType() {
        return FrameType.NOTIFICATION;
    }

    public void copyInto(ByteBuffer buffer) throws CMMException {
        buffer.putInt(cause);
    }

    public void copyFrom(ByteBuffer buffer) throws CMMException {
        cause = buffer.getInt();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("Notification:");
        switch (cause) {
            case NODE_ELIGIBLE:
                sb.append(" [NODE_ELIGIBLE] ");
                break;
            case NODE_INELIGIBLE:
                sb.append(" [NODE_INELIGIBLE] ");
                break;
            default:
                sb.append( " [??] ");
                break;
        }
        sb.append("from " + super.toString());
        return sb.toString();
    }
}
