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

import com.sun.honeycomb.cm.cluster_membership.*;
import com.sun.honeycomb.cm.cluster_membership.messages.api.*;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The FrameType class is a factory of Messages that 
 * enumerates all defined well known frame types.
 */
public final class FrameType {

    private static final List TYPES = new ArrayList();

    /*
     * possible message types
     */
    public static final FrameType HEARTBEAT 
        = new FrameType(0x51, Heartbeat.class, "HEARTBEAT");

    public static final FrameType DISCOVERY 
        = new FrameType(0x52, Discovery.class, "DISCOVERY");

    public static final FrameType ELECTION 
        = new FrameType(0x53, Election.class, "ELECTION");

    public static final FrameType NOTIFICATION
        = new FrameType(0x54, Notification.class, "NOTIFICATIO");

    public static final FrameType REGISTER
        = new FrameType(0x01, Register.class, "REGISTER");

    public static final FrameType DISCONNECT
        = new FrameType(0x02, Disconnect.class, "DISCONNECT");

    public static final FrameType CONNECT 
        = new FrameType(0x03, Connect.class, "CONNECT");

    public static final FrameType CONNECT_RESPONSE 
        = new FrameType(0x04, ConnectResponse.class, "CONNECT_RESPONSE");

    public static final FrameType NODE_CHANGE
        = new FrameType(0x06, NodeChange.class, "NODE_CHANGE");

    public static final FrameType C_NODE_INFO
        = new FrameType(0x07, C_NodeInfo.class, "C_NODE_INFO");

    public static final FrameType NODE_INFO
        = new FrameType(0x08, NodeInfo.class, "NODE_INFO");

    public static final FrameType UPDATE
        = new FrameType (0x71, Update.class, "UPDATE");

    public static final FrameType COMMIT
        = new FrameType (0x72, Commit.class, "COMMIT");

    public static final FrameType CONFIG_CHANGE
        = new FrameType (0x09, ConfigChange.class, "CONFIG_CHANGE");

    public static final FrameType DISK_CHANGE
        = new FrameType (0x10, DiskChange.class, "DISK_CHANGE");

    public static final FrameType CLUSTER_INFO
        = new FrameType (0x11, ClusterInfo.class, "CLUSTER_INFO");

    public static final FrameType CONFIG_CHANGE_NOTIF
        = new FrameType (0x12, ConfigChangeNotif.class, "CONFIG_CHANGE_NOTIF");


    private final int type;
    private final Class messageClass;
    private final String label;
    
    private FrameType(int value, Class messageClass,
		      String nLabel) {
	
        if (!Message.class.isAssignableFrom(messageClass)) {
            throw new CMMError("Run-time check error in FrameType");
        }
        type = value;
        this.messageClass = messageClass;
        label = nLabel;

        try {
            Object msg = messageClass.newInstance();
        } catch (Throwable e) {
            throw new CMMError(e);
        }
        TYPES.add(this);
    }

    static Message createMessage(int value) throws CMMException {

        for (int i = 0; i < TYPES.size(); i++) {
            FrameType frame = (FrameType) TYPES.get(i);
            if (value == frame.type) {
                Object msg;
                try {
                    msg = frame.messageClass.newInstance();
                } catch (Throwable e) {
                    throw new CMMException(e);
                }
                return (Message) msg;
            }
        }
        throw new CMMException("Unknown frame type " + value);
    }

    public int getValue() {
        return type;
    }

    public String getLabel() {
	return(label);
    }
}
