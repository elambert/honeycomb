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




package com.sun.honeycomb.cm.cluster_membership.messages.api;

import java.nio.ByteBuffer;

import com.sun.honeycomb.cm.cluster_membership.Node;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.messages.FrameType;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Update;
import com.sun.honeycomb.cm.cluster_membership.messages.protocol.Commit;


public class ConfigChangeNotif extends Message {

    // Used by anybody interested in config status change
    public static final int CONFIG_UPDATED = 300;
    public static final int CONFIG_FAILED = 301;
    

    private int  cause;
    private int  nodeid;
    private byte fileUpdated;

    public ConfigChangeNotif() {
        super();
        this.cause = -1;
        this.nodeid = -1;
    }


    public ConfigChangeNotif(Node node, int cause, byte fileUpdated) {
        super();
        this.cause = cause;
        this.nodeid = node.nodeId();
        this.fileUpdated = fileUpdated;
    }


    public int getCause() {
        return cause;
    }

    public int getNodeid() {
        return nodeid;
    }

    public CMMApi.ConfigFile getFileUpdated() {
        return CMMApi.ConfigFile.lookup(fileUpdated);
    }

    public FrameType getType() {
        return FrameType.CONFIG_CHANGE_NOTIF;
    }

    public void copyInto(ByteBuffer buffer) throws CMMException {
        buffer.putInt(cause);
        buffer.putInt(nodeid);
        buffer.put(fileUpdated);
    }

    public void copyFrom(ByteBuffer buffer) throws CMMException {
        cause = buffer.getInt();
        nodeid = buffer.getInt();
        fileUpdated = buffer.get();
    }

    public String toString() {
	StringBuffer sb = new StringBuffer();
	exportString(sb);
	return(sb.toString());
    }
    

    public void exportString(StringBuffer sb) {
	sb.append("ConfigChangeNotif: ");
	sb.append(toStatus());
    }

    private String toStatus() {
        switch (cause) {
        case CONFIG_UPDATED:
            return "update config successfully";
        case CONFIG_FAILED:
            return "failed to update config";
        default:
            return "unknown";
        }
    }
}

