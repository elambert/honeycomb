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

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.ArrayList;

import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.config.ConfigException;
import com.sun.honeycomb.multicell.lib.MultiCellLibBase;
import com.sun.honeycomb.emd.config.RootNamespace;

/**
 * CMM API.
 * This interface describes the API exported by CMM.
 * A client uses {@link CMM#getApi()} to get an instance
 * of the API.
 */
public interface CMMApi {
    
    /**
     * Possible cluster config files.
     * Theses files are updated 'atomically' within the cluster using
     * a 2 phases commit mechanism (distributed concurrent update is 
     * not supported). To guarantee CMM compatibility, one can add
     * to the list but not change the existing order.
     * Filename should be determined without instanciating too many
     * classes in order to avoid polluting CMM.
     */
    public static final ArrayList CFGFILES = new ArrayList();
        
    public static final ConfigFile UPDATE_UNDEFINED_FILE = 
        new ConfigFile(1, null);
    
    public static final ConfigFile UPDATE_DEFAULT_FILE =
        new ConfigFile(2, ClusterProperties.CLUSTER_CONF_FILE);
    
    public static final ConfigFile UPDATE_METADATA_FILE = 
        new ConfigFile(3, RootNamespace.userConfig.getAbsolutePath());
    
    public static final ConfigFile UPDATE_SILO_FILE =
        new ConfigFile(4, MultiCellLibBase.SILO_INFO_FILE);
    
    public static final ConfigFile UPDATE_STRESS_TEST = 
        new ConfigFile(5, "/config/config.stress_test");
    
     
    /**
     * Returns the local node id.
     */
    int nodeId() throws CMMException;

    /**
     * Set the Master/ViceMaster eligibility state.
     * A node that is not eligible cannot become the master or
     * vice master node. If a node becomes ineligible, it automatically
     * releases its master/vicemaster role.
     */
    void setEligibility(boolean eligible) throws CMMException;

    /**
     * Get information for all nodes in the cell.
     * @return a NodeInfo table describing the state of all nodes
     * in the system.
     */
    Node[] getNodes() throws CMMException;

    /**
     * Get the information for the current master node.
     * @return NodeInfo of the current master node.
     */
    Node getMaster() throws CMMException;

    /**
     * Get the information for the current vice master node.
     * @return NodeInfo of the current vice master node.
     */
    Node getViceMaster() throws CMMException;

    /**
     * Register for notification when the CMM view
     * changes in the cluster.
     */
    SocketChannel register() throws CMMException;

    /**
     * Get the current pending notification
     * This method must be called to acknowledge and reset any
     * pending notification on the CMM channel.
     */
    Message getNotification(SocketChannel sc) throws CMMException;    

    /**
     * wipe Config is used to wipe the config related to a specific
     * configuration file
     */
    void wipeConfig(ConfigFile fileToUpdate, long version) 
        throws CMMException, ServerConfigException;

    /**
     * updateConfig is used to update a specifc configuration file.
     * The goal of this method is to provide a way to synchronize
     * config/update from various local JVMs, by passing the new properties
     * and letting CMM do the synchronization (used for DEFAULT_FILE)
     */
    void updateConfig(ConfigFile fileToUpdate, Map newProps)
        throws CMMException, ServerConfigException;

    /**
     * storeConfig is used for services let the user of this interface
     * create the new config file, and thefeore does not offer any
     * guarantees that two concurrent config/update on the same file
     * will not collide (used for METADATA_FILE and SILO_FILE)
     */
    void storeConfig(ConfigFile fileToUpdate, long version, String md5sum)
        throws CMMException, ServerConfigException;

    /**
     * API call to inform CMM how many disks are available on this node
     */
    public void setActiveDiskCount (int count) throws CMMException;

    /**
     * Retrieve the number of active disks
     */
    public int getActiveDiskCount () throws CMMException;

    /**
     * do we have quorum?
     */
    public boolean hasQuorum() throws CMMException;

    /**
     * This class describes the state of a node as seen by CMM.
     */
    static class Node implements java.io.Serializable, Cloneable {
            
        public int nodeId;
        public int activeDisks;
        public String hostName;

        public boolean isAlive;
        public boolean isEligible;
        public boolean isMaster;
        public boolean isViceMaster;
        public boolean isOff;

        public boolean isAlive() {
            return isAlive;
        }
        public boolean isEligible() {
            return isEligible;
        }
        public boolean isMaster() {
            return isMaster;
        }
        public boolean isViceMaster() {
            return isViceMaster;
        }
        public boolean isOff() {
            return isOff;
        }
        public String getName() {
            return hostName;
        }
        public String getAddress() {
            return hostName;
        }
        public int nodeId() {
            return nodeId;
        }
        public int getActiveDiskCount() {
            return activeDisks;
        }
        public void init(Node node) {
            nodeId       = node.nodeId();
            activeDisks  = node.getActiveDiskCount();
            hostName     = node.getName();
            isAlive      = node.isAlive();
            isEligible   = node.isEligible();
            isMaster     = node.isMaster();
            isViceMaster = node.isViceMaster();
            isOff        = node.isOff();
        }
    }
    
    /**
     * Config file class
     * A config file is defined by a filename and a unique network byte
     */
    static class ConfigFile implements Comparable {
        
        private final byte val;
        private final String name;

        public String name() { return name; }
        public byte val() { return val; }
                
        private ConfigFile(int val, String name) {
            this.name = name;
            this.val = new Integer(val).byteValue();
            // this order is required to keep backward CMM
            // compatibility (Connect msg)
            if (val >= 2) {
                CFGFILES.add(val - 2, this);
            }
        }

        public long version() {
            return CfgUpdUtil.getInstance().getVersion(this);
        }
        
        public String toString() {
            return name;
        }
        
        public static ConfigFile lookup(byte val) {
            for (int i = 0; i < CFGFILES.size(); i++) {
                ConfigFile cfg = (ConfigFile) CFGFILES.get(i);
                if (cfg.val() == val) {
                    return cfg;
                }
            }
            return UPDATE_UNDEFINED_FILE;
        }
        
        public static ConfigFile get(int index) {
            if (index >= 0 && index < CFGFILES.size()) {
                return (ConfigFile) CFGFILES.get(index);
            }
            return UPDATE_UNDEFINED_FILE;
        }
        
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
    
    /**
     * Logging prefix used by CMM
     */
    static final String LOG_PREFIX = "ClusterMgmt - ";
    
}