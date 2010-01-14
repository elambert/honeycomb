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
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.AdminResourcesConstants;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.DiskHealth;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.diskmonitor.DiskControl;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.sysdep.SystemInfo;
import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.lib.MultiCellLib;
import com.sun.honeycomb.util.Ipmi;
import com.sun.honeycomb.util.Switch;
import com.sun.honeycomb.util.ServiceProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.text.DecimalFormat;
import java.lang.reflect.UndeclaredThrowableException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

/**
 * Platform service - generic class providing HA functionalities
 * at the OS/HW level.
 * It instantiates the actual implementation of the platform
 * service at run-time.
 */
public class HAPlatform implements PlatformService,PropertyChangeListener {

    private static final Logger logger
        = Logger.getLogger (HAPlatform.class.getName());

    /** max tolerated errors before escalation */
    private static final int MAX_ERRORS = 3;
    /** The package to use when instantiating a Platform */
    private static final String CLASS_PREFIX = "com.sun.honeycomb.platform";
    /** get the board id for this node */
    private static final String GET_BOARDID_SCRIPT = 
        "/opt/honeycomb/bin/dmidecode.pl -bC /opt/honeycomb/bin/mb";
    /** The name of the class we are looking for */
    private static final String CLASS_SUFFIX = "Platform";

    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY = 1000;
    private static final int MONITOR_INTERVAL = 5000;

    /** Holds the instance of the PlatformService once created */
    private static PlatformIntf platform = null;

    private volatile boolean keepRunning;
    private volatile int errors;
    private ArrayList services;
    private int nodeId;
    private final String boardId;
    private final String smdcVers;
    private String switchType;
    private String dataVip;
    private String localAddress;
    private String ifName;
    private PlatformService.Proxy myProxy;

    private static Thread platformThread = null;
    

    /**
     * Constructor called by the ServiceManager. Responsible for starting all
     * platform-specific processes that need to be started.
     */
    public HAPlatform() {
        logger.info ("going to init");
        keepRunning = true;
        errors = 0;

        NodeMgrService.Proxy nodeproxy =
            ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (nodeproxy == null) {
            throw new RuntimeException("Cannot get node mgr proxy");
        }
        nodeId = nodeproxy.nodeId();
        localAddress = nodeproxy.getHostname();

        // initialize the underlying platform implementation -
        // creating all the initrd-like services that we need to monitor
        services = new ArrayList();
        getPlatform().initialize(services);

        dataVip = getPlatform().getDataVip();
        switchType = getPlatform().getSwitchType();
        ifName = getPlatform().getInterfaceName();

        //
        // Subscribe to property change updates  so we can respond 
        // to a dataVip change.
        //
        MultiCellLib.getInstance().addPropertyListener(this);


        // start all monitored services if not already started.
        Iterator iter = services.listIterator();
        while (iter.hasNext()) {
            MonitoredService svc = (MonitoredService) iter.next();
            if (!svc.isRunning()) {
                svc.start();
            }
        }

        // get the board id
        BufferedReader output = null;
        String target = null;
        try {
            output = Exec.execRead(GET_BOARDID_SCRIPT);
            target = output.readLine();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            boardId = target;
            try {
                output.close();
            } catch (IOException e) {}
        }

        // get firmware revision (warning ipmitool)
        smdcVers = Ipmi.fwVersion();
 
        updateProxy();
        ServiceManager.publish (this);
        logger.info ("going to ready state");
        
        // get bios version
        try {
            String bios = myProxy.getBIOSInfo().
                        getPropertyValueString(BIOSInfo.PROPERTY_BIOS_VERSION);
            String str = BundleAccess.getInstance().getBundle().
                getString(AdminResourcesConstants.MSG_KEY_BIOS_VERSION);
            Object [] args = { Integer.toString(nodeId), bios };
            logger.info(MessageFormat.format(str, args));
        } catch (Exception ex) {
            logger.warning("Could not log bios version for node id " +
                    Integer.toString(nodeId) + ". Either error getting bios "
                    + "information or problems formatting message ");
        }
    }

    /**
     * Shutdown all platform processes that we've been monitoring and
     * then shut ourself down.
     */
    public void shutdown() {
        logger.info ("notifying thread to shut down");
        keepRunning = false;
        if (platformThread != null)
            platformThread.interrupt();

        // shut all services down
        Iterator iter = services.listIterator();
        while (iter.hasNext()) {
            MonitoredService svc = (MonitoredService) iter.next();
            try {
                if (svc.doShutdownOnExit()) {
                    svc.stop();
                }
            }
            catch (Exception e) {
                continue; // keep shutting things down
            }
        }
    }

    /**
     * Execute initialization that needs to be done before we reach
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
    }


    /**
     * The meat and bones of this service. Ensure that all of our processes
     * are running, and if needed, restart those that have died.
     */
    public void run() {
        
        int monitor = 0;
        DecimalFormat d = new DecimalFormat ("#######0.0#");
        platformThread = Thread.currentThread();
        while (keepRunning) {
            // Ensure that all services are running:
            Iterator iter = services.listIterator();
            while (iter.hasNext()) {
                MonitoredService svc = (MonitoredService) iter.next();
                if (!svc.isRunning()) {
                    if (++errors > MAX_ERRORS)
                        // kill ourself - escalation
                        throw new RuntimeException("TOO MANY FAILURES");

                    logger.severe ("detected stopped service: " +
                                   svc + ". Attempting restart.");
                    svc.restart();
                }
            }

            updateProxy();
            ServiceManager.publish(this);

            if (monitor > 60) {
                SystemInfo sys = myProxy.getSystemInfo();
                logger.info("Load avg: "
                            + d.format (sys.get1MinLoad()) + " "
                            + d.format (sys.get5MinLoad()) + " "
                            + d.format (sys.get15MinLoad()));
                checkAdminVip();
                monitor = 0;
            }
            monitor++;

            try {
                Thread.sleep(MONITOR_INTERVAL);
            }
            catch (InterruptedException ie) {
                logger.info ("thread interrupted: " + ie.getMessage());
            }
        }

        logger.info ("shutting down");
        getPlatform().nfsCloseAll();

    }

    /**
     * When networking changes (e.g. switch failover) re init anything
     * we know like addresses
     */
    public void reInitAddresses() {
        getPlatform().reInitialize();

        // And the data in our proxy is probably obsolete now
        updateProxy();
        ServiceManager.publish(this);
    }

    /**********************************************************************
     * bug 6411982 workaround: if this is a non-master and admin ip is
     * configured on this node then unconfigure it.
     */
    private void checkAdminVip() {
        NodeMgrService.Proxy nodeMgrProxy
            = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (nodeMgrProxy == null) {
            throw new RuntimeException("Unable to get NodeMgr proxy");
        }
        if ((nodeMgrProxy.getMasterNode() != null) && 
            (nodeId != nodeMgrProxy.getMasterNode().nodeId)) {
            removeInterface(getPlatform().getInternalMasterVipInterface());
            removeInterface(getPlatform().getMasterMulticellVipInterface());
        } 
    }

    /**********************************************************************
     * Remove the interface if it is configured.
     */
    private void removeInterface(String intf) {
        BufferedReader output = null;
        String ifconfig = "/usr/sbin/ifconfig";
        boolean removed = false;
        String line = readFirstOutputLine(ifconfig + " " + intf);
        if ((line != null) && (line.startsWith(intf))) {
            try {
                Exec.exec(ifconfig + " " + intf + " unplumb", logger);
                removed = true;
            } catch (IOException ioe) {
                logger.severe("exec " + ioe);
            }
        }
        if (removed) {
            // paranoid, check if it is still hanging around
            line = readFirstOutputLine(ifconfig + " " + intf);
            if ((line != null) && (line.startsWith(intf))) {
                String msg = intf + " not removed";
                logger.severe(msg);
            }
        }
    }

    /***********************************************************************/
    private String readFirstOutputLine(String cmd) {
        BufferedReader output = null;
        String line;
        try {
            output = Exec.execRead(cmd, logger);
            line = output.readLine();
        } catch (IOException ioe) {
            logger.severe("execRead " + ioe);
            line = null;
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException ioe) {
            }
        }
        return line;
    }

    public ManagedService.ProxyObject getProxy() {
        return myProxy;
    }

    /*******************************************
     * Remote API
     *******************************************/

    public boolean powerOff () {
        return getPlatform().powerOff();
    }

    public boolean powerOn (int nodeid) {
        return getPlatform().powerOn(nodeid);
    }

    /**
     * NFS unmount a disk
     */
    public void nfsClose(DiskId id) {
        try {
            // Can't rely on there being a node proxy for the disk
            // being unmounted!
            getPlatform().nfsClose(id);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't close disk " + id, e);
        }
    }

    /**
     * NFS mount a remote disk
     * Return true if successful, false otherwise.
     */
    public boolean nfsOpen(DiskId id) {
        int nodeId = id.nodeId();

        Disk disk = DiskProxy.getDisk(id);
        if (disk == null) {
            // Definitely a bug! Where does it come from?
            logger.warning("Couldn't get disk " + id);
            if (logger.isLoggable(Level.INFO)) {
                DiskProxy dp = DiskProxy.getProxy(id.nodeId());
                if (dp == null)
                    logger.info("Couldn't get DiskProxy for " + id.nodeId());
                else
                    logger.info("Known disks on " + id.nodeId() + ": " + dp);
            }
            return false;
        }

        if (!disk.isEnabled()) {
            logger.warning("Trying to open disabled disk " + id);
            return false;
        }

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                if (getPlatform().nfsOpen(disk))
                    return true;
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "NFS mount", e);
            }

            logger.warning("Try #" + i + ": failed to NFS-mount " + id);
            reExport(id);

            try {
                Thread.currentThread().sleep(RETRY_DELAY); // 1 second
            }
            catch (InterruptedException ie) {
                logger.severe("sleep interrupted");
                break;
            }
        }

        logger.severe("Failed to NFS-mount " + id + " after " +
                      MAX_RETRIES + " tries");
        return false;
    }

    public boolean nfsCloseAll() {
        try {
            getPlatform().nfsCloseAll();
            return true;
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Unmount all NFS", e);
        }
        return false;
    }

    public IPMIInfo getIPMIInfo() {
        return getPlatform().getIPMIInfo();
    }
    
    private void reExport(DiskId diskId) {
        Disk disk = DiskProxy.getDisk(diskId);        
        if (disk == null) {
            logger.warning("Couldn't find disk" + diskId);
            return;
        }
        
        if (!disk.isEnabled()) {
            logger.warning("Almost certainly a bug: reexport disabled disk");
        }
        
        DiskOps diskOps = DiskOps.getDiskOps();
        try {
            diskOps.unexport(disk.getPath());
            diskOps.export(disk.getPath());
        } catch (IOException oe) {
            logger.warning("failed to re-export disk " + diskId);
        }
    }

    // Package API -

    /** Creates and/or returns the appropriate Platform class */
    protected synchronized static PlatformIntf getPlatform() {

        if (platform == null)
            platform = new Platform();

        return platform;
    }

    // Private

    private DiskProxy getDiskProxy(int nodeId) {
        ManagedService.ProxyObject obj =
            ServiceManager.proxyFor(nodeId, "DiskMonitor");

        if (obj != null && (obj instanceof DiskProxy))
            return (DiskProxy) obj;

        return null;
    }

    private synchronized void updateProxy() {
         myProxy =
            new PlatformService.Proxy(switchType,
              dataVip,
              getPlatform().getMACAddress(),
              ifName,
              getPlatform().getSystemInfo(),
              getPlatform().getMemoryInfo(),
              getPlatform().getBIOSInfo(),
              getPlatform().getDIMMInfo(),
              getPlatform().getCPUInfo(),
              boardId,
              smdcVers);
    }

    /** 
     * Implementation of PropertyChangeListener interface
     * We're watching to see if the dataVip changes, so we 
     * can Do The Right Thing in that case.
     */
    synchronized public void propertyChange (PropertyChangeEvent event) {
        // check if the event involved any properties we care about
        String prop = event.getPropertyName();

        if (prop.equals(MultiCellLib.PROP_DATA_VIP)) {

            /*
              public void reInitAddresses() {
              getPlatform().reInitialize();
              
              // And the data in our proxy is probably obsolete now
              updateProxy();
              ServiceManager.publish(this);
              }
            */

            //            dataVip = getPlatform().DataVip();
            //            updateProxy();
            dataVip = (String)event.getNewValue();
            getPlatform().resetNetwork();
            reInitAddresses();


        }
        

    }

}
