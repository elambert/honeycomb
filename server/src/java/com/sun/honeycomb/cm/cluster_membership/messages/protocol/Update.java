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

import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.FrameType;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CfgUpdUtil;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChange;

/**
 * The Update message is sent to begin the config update two-phase commit
 * process. The payload of this message contains the version of the file to
 * fetch, the node from which to fetch (typically the master), the checksum
 * of the file for validation, and an array of the nodes in the system which
 * contains an ACK or a NACK, depending on whether the update operation was
 * successful.
 */
public class Update extends ErrorCodedMessage {

    protected byte   _fileToUpdate;
    protected boolean _clearMode;
    protected String _md5sum;
    private byte[] _buffer;

    /** 
     * Default constructor. Used by the CMM messaging framework to construct 
     * messages received over a socket.
     */
    public Update () {
        super();
        _fileToUpdate = CMMApi.UPDATE_UNDEFINED_FILE.val();
        _md5sum  = null;
        _buffer = null;
    }

    /**
     * Explicit constructor. Invoked by a CMM Api client on the master node to
     * begin a config update
     */
    public Update (ConfigChange msg) {
        super(msg.getSource(), msg.getVersion(), msg.getRequestId());
        
        _fileToUpdate = msg.getFileToUpdate();
        _clearMode = msg.clearMode();
        _md5sum = msg.getMD5Sum();
        _buffer = null;
        
        /*
         * if the file is small, try to include it in the
         * message. Otherwise the file will be fetched during the 
         * update.
         */
        CMMApi.ConfigFile cfg = CMMApi.ConfigFile.lookup(_fileToUpdate);
        File f = CfgUpdUtil.getInstance().getFile(cfg, msg.getVersion());
        if (f != null) {
            if (f.length() <= (CMM.FRAME_MTU / 2)) {
                
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(f);
                    byte[] buf = new byte[(int)f.length()];
                    int count = fis.read(buf);
                    if (count == buf.length) {
                        _buffer = buf;
                    }
                } catch (Exception e) {
                    /*
                     * in this case we don't use the internal buffer
                     * of the message but will probably fail when fetching
                     * the config file
                     */
                } finally {
                    if (fis != null) {
                        try { fis.close(); } catch (IOException ioe) {}
                    }
                }
            }
        }
    }

    public void copyInto (ByteBuffer buffer) {
        super.copyInto(buffer);
        buffer.put(_fileToUpdate);
        buffer.put(_clearMode ? (byte)1 : (byte)0);
        buffer.put (_md5sum.getBytes()); // always 16 bytes
        if (_buffer != null) {
            buffer.putInt(_buffer.length);
            buffer.put(_buffer);
        } else {
            buffer.putInt(-1);
        }
    }

    public void copyFrom (ByteBuffer buffer) {
        super.copyFrom(buffer);
        _fileToUpdate = buffer.get();
        _clearMode = (buffer.get() == 1);
        byte[] buf = new byte[16];
        buffer.get (buf);
        _md5sum = new String (buf);
        int size = buffer.getInt();
        if (size != -1) {
            _buffer = new byte[size];
            buffer.get(_buffer);
        } else {
            _buffer = null;
        }        
    }

    public String toString() {
        StringBuffer update 
            = new StringBuffer ("Update [").append(_fileToUpdate).append("/");
        update.append (_version).append ("/");
        update.append(_clearMode).append("/");
        update.append (_master).append ("/").append (_md5sum).append ("]");
        update.append ( "(");
        toString(update);
        update.append (")x");
        return update.toString();
    }

    public FrameType getType() {
        return FrameType.UPDATE;
    }

    /**
     * Returns the file to update
     */
    public byte getFileToUpdate() {
        return(_fileToUpdate);
    }

    public boolean clearMode() {
        return(_clearMode);
    }
    
    /**
     * Return the content of the update if it is included.
     */
    public byte[] getContent() {
        return _buffer;
    }
    
    /**
     * Returns the checksum embedded in the config file to be retreived. This
     * is used by the config engine to validate that the file is undamaged and
     * correct.
     */
    public String getChecksum () {
        return _md5sum;
    }

}
