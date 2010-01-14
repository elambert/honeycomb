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

import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.io.IOException;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.FrameType;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;

/**
 * Config change notification message (API)
 */
public class ConfigChange extends Message {

    public static final int PENDING = -1;
    public static final int SUCCESS = 900;
    public static final int FAILURE = 901;
    /*
     * Error codes - should only be set when
     * Failure is set as status.
     */
    public static final int CRC_ERROR = 1;
    public static final int DIST_FAILURE = 2;
    public static final int NO_LINK = 3;
    public static final int BUSY = 4;

    private int    source;
    private byte fileToUpdate;
    private boolean clearMode;
    private long   version;
    private String md5sum;
    private int    status;
    private int    errorCode;
    
    // not transmitted data
    private SocketChannel socket;

    public ConfigChange() {
        this.source  = -1;
        this.fileToUpdate = CMMApi.UPDATE_UNDEFINED_FILE.val();
        this.clearMode = false;
        this.version = -1;
        this.status  = PENDING;
        this.errorCode  = 0;
        this.md5sum  = null;
        this.socket = null;
    }

    public ConfigChange (int nodeid, byte fileToUpdate, boolean clearMode,
                         long vers, String md5) {
        this.source  = nodeid;
        this.fileToUpdate = fileToUpdate;
        this.clearMode = clearMode;
        this.version = vers;
        this.md5sum  = md5;
        this.status  = PENDING;
        this.socket = null;
    }

    public int getStatus() {
        return this.status;
    }

    public void setStatus(int stat) {
        this.status = stat;
    }

    public void setSocket(SocketChannel socket) {
        this.socket = socket;
    }
    
    public SocketChannel getSocket() {
        return socket;
    }
    
    public boolean reply() throws CMMException {
        if (socket != null) {
            return this.send(socket);
        }
        return false;
    }
    public int getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(int stat) {
        this.errorCode = stat;
    }


    public byte getFileToUpdate() {
        return(fileToUpdate);
    }

    public boolean clearMode() {
        return(clearMode);
    }

    public long getVersion() {
        return this.version;
    }

    public int getSource() {
        return this.source;
    }

    public String getMD5Sum() {
        return this.md5sum;
    }

    public FrameType getType() {
        return FrameType.CONFIG_CHANGE;
    }

    public void copyInto(ByteBuffer buffer) throws CMMException {
        if (version == -1) {
            throw new CMMException("ConfigChange: version is not set");
        }
        buffer.putInt(status);
        buffer.putInt(errorCode);
        buffer.put(fileToUpdate);
        buffer.put(clearMode ? (byte)1 : (byte)0);
        buffer.putLong(version);
        buffer.putInt(source);
        buffer.put(md5sum.getBytes());
    }

    public void copyFrom(ByteBuffer buffer) throws CMMException {
        status  = buffer.getInt();
        errorCode  = buffer.getInt();
        fileToUpdate = buffer.get();
        clearMode = (buffer.get() == (byte)1);
        version = buffer.getLong();
        source  = buffer.getInt();
        byte buf[] = new byte[16];
        buffer.get (buf);
        md5sum = new String (buf);
        
        if (version == -1) {
            throw new CMMException("ConfigChange: unknown version");
        }
    }

    public String toString() {
        return "ConfigChange [" + version + "/" + source + "/" + md5sum + "]";
    }
}
    
