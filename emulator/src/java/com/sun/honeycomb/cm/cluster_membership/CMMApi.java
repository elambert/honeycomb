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




package com.sun.honeycomb.cm.cluster_membership;

import java.util.ArrayList;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.lib.MultiCellLibBase;
import com.sun.honeycomb.emd.config.RootNamespace;

/**
 * This class is included in the emulator for its constants, 
 */
public interface CMMApi {
    
    static final ArrayList CFGFILES = new ArrayList();

    public static final ConfigFile UPDATE_UNDEFINED_FILE = 
    new ConfigFile(1, null);
    
    public static final ConfigFile UPDATE_DEFAULT_FILE =
        new ConfigFile(2, ClusterProperties.CONFIG_FILE);
    
    public static final ConfigFile UPDATE_METADATA_FILE = 
        new ConfigFile(3, RootNamespace.userConfig.getAbsolutePath());
    
    public static final ConfigFile UPDATE_SILO_FILE =
        new ConfigFile(4, MultiCellLibBase.SILO_INFO_FILE);

    /**
     * Config file class
     * A config file is defined by a filename and a unique network byte
     */
    static class ConfigFile implements Comparable {
        
        private final byte val;
        private final String name;
        
        private ConfigFile(int val, String name) {
            this.name = name;
            this.val = new Integer(val).byteValue();
            if (val > 1) {
                CFGFILES.add(val - 2, this);
            }
        }
        
        public String name() { return name; }
        public byte val() { return val; }
        
        /* Implementation of Comparable interface */
        
        public boolean equals(final Object obj) {
            if (!(obj instanceof ConfigFile))
                return false;
            ConfigFile other = (ConfigFile) obj;
            return other.val() == val;
        }
        
        public int compareTo(final Object obj) {
            if (!(obj instanceof ConfigFile)) {
                throw new ClassCastException("cannot compare");
            }
            ConfigFile other = (ConfigFile) obj;
            return ((int)other.val()) - ((int)val);
        }
        
        public int hashCode() {
            return val;
        }
        
    }
    
}
