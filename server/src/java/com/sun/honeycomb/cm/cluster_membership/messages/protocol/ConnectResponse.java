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
import java.nio.ByteBuffer;


/**
 * The connection response. This message is subclassed from Message because it
 * will also contain version information, but that of the remote host. This
 * information will be key in determining what actions are needed to be taken
 * by this host when there is a conflict.
 * WARNING - changing this message can break the compatibility between CMM.
 */
public class ConnectResponse extends Connect {

    public static final int CONNECT_UNK         = -1;
    public static final int CONNECT_OK          = 0;
    public static final int SW_MISMATCH         = 0x1;
    public static final int CFG_MISMATCH        = 0x2;
    
    protected Connect connectMsg;
    protected int responseCode;

    public ConnectResponse () {
        connectMsg = null;
        responseCode = CONNECT_UNK;
    }
    
    public ConnectResponse (Connect connect) {
        connectMsg = connect;

        responseCode = CONNECT_OK;
        if (!connectMsg.getSoftwareVersion().equals(getSoftwareVersion())) {
            responseCode |= SW_MISMATCH;
        }
        
        ConfigVersion[] local = getConfigVersions();
        ConfigVersion[] remote = connectMsg.getConfigVersions();
        for (int i = 0; i < local.length && i < remote.length; i++) {
            if (local[i] != null && remote[i] != null) {
                if ((local[i].wiped && remote[i].wiped) ||
                    (local[i].version == remote[i].version)) {
                    continue;
                }
                responseCode |= CFG_MISMATCH;
                
            } else if (local[i] == null) {
                if (remote[i] != null) {
                    responseCode |= CFG_MISMATCH;
                }
            } else if (remote[i] == null) {
                responseCode |= CFG_MISMATCH;
            }
        }        
    }

    public FrameType getType() {
        return FrameType.CONNECT_RESPONSE;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer ("ConnectResponse [");
        sb.append (responseCode);
        sb.append ("/").append (super.toString());
        sb.append ("/").append(connectMsg.toString());
        sb.append ("]");
        return sb.toString();
    }

    /**
     * Returns the response code which is a bit set. 0 means the versions
     * matched and connection succeeded, anything else means badness has ensued
     * and one of the nodes needs to take some corrective action.
     */
    public int getResponse () {
        return responseCode;
    }

    public Connect getConnectMsg() {
        return(connectMsg);
    }

    /*
     * Serialization / Deserialization operations
     */

    public void copyInto (ByteBuffer buffer) {
        buffer.putInt (responseCode);
        super.copyInto(buffer);
        connectMsg.copyInto(buffer);
    }

    public void copyFrom (ByteBuffer buffer) {
        responseCode = buffer.getInt();
        super.copyFrom(buffer);
        connectMsg = new Connect();
        connectMsg.copyFrom(buffer);
    }
}
