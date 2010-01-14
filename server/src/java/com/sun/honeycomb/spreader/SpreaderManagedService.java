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



package com.sun.honeycomb.spreader;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.common.InternalException;

/**
 * Public definition of the Spreader managed service.
 *
 * @author Shamim Mohamed
 * @version $Id: SpreaderManagedService.java 11000 2007-06-08 22:23:45Z ks202890 $
 */
public interface SpreaderManagedService
    extends ManagedService.RemoteInvocation, ManagedService {
    /**
     * The spreader managed service exports the following public API.
     */

    public static final int SWITCH_OTHER = 0;
    public static final int SWITCH_ZNYX = 1;

    // Type of switch being used
    public int getSwitchType() throws ManagedServiceException;

    // The active switch (0 or 1)
    public int getActiveSwitch() throws ManagedServiceException;

    // If the switch is not a Znyx, the service will be disabled
    public boolean isDisabled() throws ManagedServiceException;

    // Whether this node is up (according to the Spreader Service)
    public boolean isNodeUp(int nodeId) throws ManagedServiceException;

    // The size of the mask used on the src address to get the hash value
    public int getSrcAddrMaskSize() throws ManagedServiceException;

    // The size of the mask used on the src port to get the hash value
    public int getSrcPortMaskSize() throws ManagedServiceException;

    // The switch port a certain hash value (srcHostMaskedBits) and
    // dest port is being sent to
    public int getSwitchPort(int srcHostMaskedBits, int srcAddrMaskedBits,
                             int destIpPort) throws ManagedServiceException;

    // The port the master node is connected to
    public int getMasterPort() throws ManagedServiceException;

    // Get the port a node is connected to	
    public int getPortFromId(int nodeId) throws ManagedServiceException;
    public int getPortFromMAC(String mac) throws ManagedServiceException;

    // Get the nodeID by switch port or MAC address
    public int nodeIdFromPort(int port) throws ManagedServiceException;
    public int nodeIdFromMAC(String mac) throws ManagedServiceException;

    // Get MAC address by node ID or by switch port
    public String getMacFromPort(int port) throws ManagedServiceException;
    public String getMacFromNodeId(int nodeId) throws ManagedServiceException;

    // The address to use while talking to the switch
    public String getMyAddress() throws ManagedServiceException;

    // Total number of nodes connected
    public int getNumNodes() throws ManagedServiceException;


    /**
     * The spreader managed service exports this following proxy object.
     *
     * 
     */
    public class Proxy extends ManagedService.ProxyObject {

        private static final int DATA_VIP = 0;
        private static final int ADMIN_VIP = 1;
        private static final int IP_ADDRESS = 2;
        private static final int ACTIVE_SWITCH = 3;
        private static final int BACKUP_SWITCH_ALIVE = 4;
        private static final int SP_ALIVE = 5;

        private String dataVIP;
        private String adminVIP;
        private String myAddress;
        private int activeSwitch;
        private boolean isBackupSwitchAlive;
        private boolean isSPAlive;

        Proxy(String dataVIP, String adminVIP, String address,
              int activeSwitch, 
              boolean isBackupSwitchAlive, boolean isSPAlive) { 
            this.dataVIP = dataVIP;
            this.adminVIP = adminVIP;
            this.myAddress = address;
            this.activeSwitch = activeSwitch;
            this.isBackupSwitchAlive = isBackupSwitchAlive;
            this.isSPAlive = isSPAlive;
        }

        public String getAdminVIP() { return adminVIP; }
        public String getDataVIP() { return dataVIP; }
        public String getAddress() { return myAddress; }
        public int getActiveSwitch() { return activeSwitch; }
        public boolean getBackupSwitchStatus() { return isBackupSwitchAlive; }
        public boolean getSPStatus() { return isSPAlive; }
  
        public String toString() {
            StringBuffer sb = new StringBuffer();

            sb.append("Proxy: network ").append(getActiveSwitch());
            sb.append(" (").append(getAddress()).append(")");
            sb.append(" A=").append(getAdminVIP());
            sb.append(" D=").append(getDataVIP());

            return sb.toString();
        }

        /*
         * Alert private API.
         *
         * Currently exports the following:
         * - dataVip
         * - adminVip
         * - ipAddress
         * - activeSwitch
         * - isBackupSwitchAlive 
         * - isSPAlive 
         */

        private static final AlertProperty[] alertProps = {
            new AlertProperty("dataVip", AlertType.STRING),
            new AlertProperty("adminVip", AlertType.STRING),
            new AlertProperty("ipAddress", AlertType.STRING),
            new AlertProperty("activeSwitch", AlertType.INT),
            new AlertProperty("backupSwitchStatus", AlertType.BOOLEAN),
            new AlertProperty("spStatus", AlertType.BOOLEAN),
        };

        public int getNbChildren() {
            return alertProps.length;
        }

        public AlertProperty getPropertyChild(int index)
                throws AlertException {

            try {
                return alertProps[index]; 
            }
            catch (ArrayIndexOutOfBoundsException e) {
                throw new AlertException("index " + index + "does not exist");
            }
        }

        public String getPropertyValueString(String property) 
                throws AlertException {

            int i = getPropertyIndex(property);

            if (alertProps[i].getType() != AlertType.STRING)
                throw new AlertException("property " + property +
                                         " is not a string");

            switch (i) {
            case DATA_VIP: return getDataVIP();
            case ADMIN_VIP: return getAdminVIP();
            case IP_ADDRESS: return getAddress();

            default: throw new InternalException("property index OOB");
            }
        }

        public int getPropertyValueInt(String property)
                throws AlertException {

            int i = getPropertyIndex(property);

            if (alertProps[i].getType() != AlertType.INT)
                throw new AlertException("property " + property +
                                         " is not an integer");
            if (i != ACTIVE_SWITCH)
                throw new InternalException("property index OOB");

            return getActiveSwitch();
        }

        public boolean getPropertyValueBoolean(String property) 
                throws AlertException {
            int i = getPropertyIndex(property);

            if (alertProps[i].getType() != AlertType.BOOLEAN)
                throw new AlertException("property " + property +
                                         " is not a boolean");

            switch(i) {
            case BACKUP_SWITCH_ALIVE: return getBackupSwitchStatus(); 
            case SP_ALIVE: return getSPStatus();
            default: throw new InternalException("property index OOB");
            }
        }

        private int getPropertyIndex(String name) throws AlertException {
            for (int i = 0; i < alertProps.length; i++)
                if (alertProps[i].getName().equals(name))
                    return i;
            throw new AlertException("property " + name + " does not exist");
        }
    }
}
// 46789 123456789 123456789 123456789 123456789 123456789 123456789 123456789 

