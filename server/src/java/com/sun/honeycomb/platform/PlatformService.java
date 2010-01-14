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


package com.sun.honeycomb.platform;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.util.sysdep.SystemInfo;
import com.sun.honeycomb.util.sysdep.MemoryInfo;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertException;

import java.io.*;
import java.util.ArrayList;


/**
 * Public definition of the Platform service.
 * This interface is implemented by the Platform service and
 * is accessible cluster wide
 */
public interface PlatformService
    extends ManagedService.RemoteInvocation, ManagedService {

    /*
     * Remote API: These are implemented by Platform
     */
    public boolean nfsOpen(DiskId id) throws ManagedServiceException;

    public void nfsClose(DiskId id) throws ManagedServiceException;
    
    public boolean nfsCloseAll() throws ManagedServiceException;

    public boolean powerOff() throws ManagedServiceException;

    public boolean powerOn(int nodeid) throws ManagedServiceException;

    public IPMIInfo getIPMIInfo() throws ManagedServiceException;
    
    public void reInitAddresses() throws ManagedServiceException;

    /**
     * Platform service's proxy has OS/HW information.
     * A Proxy object is a self describing object.
     * Avoid declaring 'static' variables.
     */
    public class Proxy extends ManagedService.ProxyObject {

        private final SystemInfo sysinfo;
        private final MemoryInfo meminfo;
        private final BIOSInfo biosinfo;
        private final DIMMInfo dimminfo;
        private final CPUInfo cpuinfo;
        private final String datavip;
        private final String macAddress;
        private final String switchtype;
        private final String interfaceName;
        private final String boardId;
        private final String smdcVers;
        
        protected Proxy(String switchtype,
          String datavip,
          String macAddress,
          String interfaceName,
          SystemInfo sysinfo,
          MemoryInfo meminfo,
          BIOSInfo biosinfo,
          DIMMInfo dimminfo,
          CPUInfo cpuinfo,
          String boardId,
          String smdcVers) {
            
            this.sysinfo  = sysinfo;
            this.meminfo  = meminfo;
            this.biosinfo  = biosinfo;
            this.dimminfo  = dimminfo;
            this.cpuinfo  = cpuinfo;
            this.datavip = datavip;
            this.interfaceName = interfaceName;
            this.macAddress = macAddress;
            this.switchtype = switchtype;
            this.boardId = boardId;
            this.smdcVers =  smdcVers;
        }
        
        public SystemInfo getSystemInfo() {
            return sysinfo;
        }

        public MemoryInfo getMemoryInfo() {
            return meminfo;
        }

        public BIOSInfo getBIOSInfo() {
            return biosinfo;
        }

        public DIMMInfo getDIMMInfo() {
            return dimminfo;
        }

        public CPUInfo getCPUInfo() {
            return cpuinfo;
        }

        public String getDataVIPaddress() {
            return datavip;
        }

        public String getNetworkInterface() {
            return interfaceName;
        }

        public String getMACAddress() {
            return macAddress;
        }

        public String getSwitchType() {
            return switchtype;
        }

        public String getBoardId() {
            return boardId;
        }

        public static PlatformService.Proxy getProxy(int node) {
            ManagedService.ProxyObject obj;
            obj = ServiceManager.proxyFor(node, "PlatformService");            
            if (obj != null) {
                if (!obj.isReady()) {
                    return null;
                } else if (obj instanceof PlatformService.Proxy) {
                    return (PlatformService.Proxy) obj;
                }
            }
            return null;
        }

        public static PlatformService getApi(int nodeid) {
            ManagedService.ProxyObject obj = getProxy(nodeid);
            if (obj == null) {
                return null;
            }
            if (obj.getAPI() instanceof PlatformService) {
                return (PlatformService) obj.getAPI();
            }
            return null;
        }

        public static PlatformService.Proxy getProxy() {
            return getProxy(ServiceManager.LOCAL_NODE);
        }

        public static PlatformService getApi() {
            return getApi(ServiceManager.LOCAL_NODE);
        }

        /**
         * Alert API.
         * At this point we only export:
         * - switchType (direct child)
         * - boardId (direct child)
         * - ipmiFwVersion (direct child)
         * - memoryInfo
         * - systemInfo
         * - biosInfo
         * - dimmInfo
         * - cpuInfo
         */
        private static final AlertProperty[] alertProps = {
          new AlertProperty("switchType", AlertType.STRING),
          new AlertProperty("boardId", AlertType.STRING),
          new AlertProperty("ipmiFwVersion", AlertType.STRING),
          new AlertProperty("memoryInfo", AlertType.COMPOSITE),
          new AlertProperty("systemInfo", AlertType.COMPOSITE),
          new AlertProperty("biosInfo", AlertType.COMPOSITE),
          new AlertProperty("dimmInfo", AlertType.COMPOSITE),
          new AlertProperty("cpuInfo", AlertType.COMPOSITE)
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
            if (property.equals("switchType")) {
                return switchtype;
            } else if (property.equals("boardId")) {
                return boardId;
            } else if (property.equals("ipmiFwVersion")) {
                return smdcVers;
            } else {
                throw new AlertException("property " +
                                         property + " does not exit");
            }
        }
        public AlertComponent getPropertyValueComponent(String property)
         throws AlertException {
           if (property.equals("memoryInfo")) {
               return (AlertComponent) meminfo;
           } else if (property.equals("systemInfo")) {
               return (AlertComponent) sysinfo;
           } else if (property.equals("biosInfo")) {
               return (AlertComponent) biosinfo;
           } else if (property.equals("dimmInfo")) {
               return (AlertComponent) dimminfo;
           } else if (property.equals("cpuInfo")) {
               return (AlertComponent) cpuinfo;
           } else {
               throw new AlertException("property " +
                                         property + " does not exit");
          }
        }
    }

}
