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
import java.security.MessageDigest;
import java.util.logging.Logger;

import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CfgUpdUtil;
import com.sun.honeycomb.cm.cluster_membership.messages.*;


/**
 * The Connect message. The payload of the Connect message is of variable
 * length. The 1st byte denotes the length of the variable-length software
 * version string that follows it. Immediately following this 8 bytes
 * which denote the version of configuration data this node is using.
 * WARNING - changing this message can break the compatibility between CMM.
 */
public class Connect extends Message {

    private static final Logger LOG = Logger.getLogger(Connect.class.getName());
    
    protected String _sw_version;
    protected ConfigVersion[] _cfg_versions;

    public Connect () {
        /*
         * null versions mean the version file was not present. That means 
         * this isn't an official "release" and we shouldn't bother trying to
         * do checking
         */
        if (CMM.getSWVersion() == null) {
            _sw_version = "undefined";
        }
        /*
         * since we only have one byte to represent the length of the version
         * string, we can only have version strings whose length is less than 
         * Byte.MAX_VALUE (2^7-1 = 127).
         */
        else if (CMM.getSWVersion().length() > Byte.MAX_VALUE) {
            LOG.severe("software version truncated");
            _sw_version = CMM.getSWVersion().substring (0, Byte.MAX_VALUE - 1);
        } else {
            _sw_version = CMM.getSWVersion();
        }
        /*
         * build an array of known configuration config files.
         */
        _cfg_versions = new ConfigVersion[CMMApi.CFGFILES.size()];
        for (int i = 0; i < CMMApi.CFGFILES.size(); i++) {
            CMMApi.ConfigFile cfg = (CMMApi.ConfigFile) CMMApi.CFGFILES.get(i);
            _cfg_versions[i] = getConfigVersion(cfg);
        }
    }

    private ConfigVersion getConfigVersion(CMMApi.ConfigFile fileToUpdate) {
        
        ConfigVersion cfgver = null;
        CfgUpdUtil cfgUpdUtil = CfgUpdUtil.getInstance();
        
        long version = cfgUpdUtil.getVersion(fileToUpdate);
        if (version != 0) {
            cfgver = new ConfigVersion(version, false);
        } else {
            version = cfgUpdUtil.getWipedVersion(fileToUpdate);
            if (version != 0) {
                cfgver = new ConfigVersion(version, true);
            }
        }
        return cfgver;
    }

    public FrameType getType() {
        return FrameType.CONNECT;
    }

    /**
     * Write the message payload into the frame
     */
    public void copyInto(ByteBuffer buffer) {
        buffer.put ((byte) _sw_version.length());
        buffer.put (_sw_version.getBytes());
        buffer.putInt(_cfg_versions.length);
        for (int i=0; i<_cfg_versions.length; i++) {
            buffer.put(_cfg_versions[i] == null ? (byte)0 : (byte)1);
            if (_cfg_versions[i] != null)
                _cfg_versions[i].copyInto(buffer);
        }
    }

    /**
     * Read the message payload from the frame buffer
     */
    public void copyFrom(ByteBuffer buffer) {
        byte len = buffer.get();
        byte[] buf = new byte[len];
        buffer.get (buf);
        _sw_version = new String (buf);
        int length = buffer.getInt();
        _cfg_versions = new ConfigVersion[length];
        for (int i=0; i<_cfg_versions.length; i++) {
            boolean exists = (buffer.get() == 1);
            if (exists) {
                _cfg_versions[i] = ConfigVersion.copyFrom(buffer);
            } else {
                _cfg_versions[i] = null;
            }
        }
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer("Connect [" + _sw_version + "/");
        for (int i=0; i<_cfg_versions.length; i++) {
            if (i>0)
                result.append("-");
            result.append(_cfg_versions[i]);
        }
        result.append("]");
        return(result.toString());
    }

    /**
     * Returns the version string. 
     */
    public String getSoftwareVersion () {
        return _sw_version;
    }

    /** 
     * Returns the config version
     */
    public ConfigVersion[] getConfigVersions() {
        return _cfg_versions;
    }

    public static class ConfigVersion {
        public long version;
        public boolean wiped;

        public ConfigVersion(long _version, boolean _wiped) {
            version = _version;
            wiped = _wiped;
        }

        public void copyInto(ByteBuffer buffer) {
            buffer.putLong(version);
            buffer.put(wiped ? (byte)1 : (byte)0);
        }

        public static ConfigVersion copyFrom(ByteBuffer buffer) {
            long _version = buffer.getLong();
            boolean _wiped = (buffer.get() == 1);
            return(new ConfigVersion(_version, _wiped));
        }

        public String toString() {
            return(version+"/"+wiped);
        }
    }
}
