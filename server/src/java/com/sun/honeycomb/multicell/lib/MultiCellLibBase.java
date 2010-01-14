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



package com.sun.honeycomb.multicell.lib;

import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagCellData;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;

import com.sun.honeycomb.multicell.lib.Rule.Interval;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.common.UID;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;


public abstract class MultiCellLibBase {

    public static final String PROP_SILO_CELLID = "honeycomb.silo.cellid";
    //
    // These properties don't exist in cluster_config.properties,
    // they are now part of the xml description of the cell. However
    // we keep the original name to use as a 'key' when generating
    // events or receiving updates.
    //
    public static final String PROP_DATA_VIP    = "honeycomb.cell.vip_data";
    public static final String PROP_ADMIN_VIP   = "honeycomb.cell.vip_admin";
    public static final String PROP_SP_VIP      = "honeycomb.cell.ip_sp";
    public static final String PROP_SUBNET      = "honeycomb.cell.vip_subnet";
    public static final String PROP_GATEWAY     = "honeycomb.cell.vip_gateway";

    public static final String SILO_INFO_FILE;
    static {
        String configDir = System.getProperty("honeycomb.config.dir", "/config");
        SILO_INFO_FILE = configDir + "/silo_info.xml";
    }
    
    public static final String PROP_SERVICETAG  = "honeycomb.serviceTagData";
    
    //
    // The 5 properties that can be changed through CLI-- does not include
    // the cellid which is only settabloe when running setupCell.
    //
    public static final int NB_PROPS_CELL       =  5;

    static protected Logger logger =
        Logger.getLogger(MultiCellLib.class.getName());

    static public final String MCLIB_LOG_PREFIX = "MCLIB: ";

    protected XMLParser xmlParser;
    protected Interval  majorInterval;
    protected long      timestamp;
    protected byte      cellid;
    protected String    dataVIP;
    protected String    adminVIP;
    protected String    spVIP;
    protected String    subnet;
    protected String    gateway;
    protected String    clusterName;
    protected List listeners = new ArrayList();

    //
    // APIs used by MultiCell to add/remove cells.
    //
    public void addCell(Cell newCell, long newVersionMajor) {
        refreshConfiguration("addCell");
        xmlParser.addCell(newCell);
        updateConfig("addCell", newVersionMajor);
    }

    public void rmCell(byte cellid, long newVersionMajor) {
        refreshConfiguration("rmCell");
        xmlParser.rmCell(cellid);
        updateConfig("rmCell", newVersionMajor);
    }

    public void addCells(List cells, long newVersionMajor) {
        refreshConfiguration("addCells");
        for (int i = 0; i < cells.size(); i++) {
            xmlParser.addCell((Cell) cells.get(i));
        }
        updateConfig("addCells", newVersionMajor);
    }

    public void rmCells(List cells) {
        refreshConfiguration("rmCells");
        for (int i = 0; i < cells.size(); i++) {
            xmlParser.rmCell(((Cell) cells.get(i)).getCellid());
        }
        updateConfig("rmCells", 1);
    }

    public void setMasterCellVersion(long newVersionMajor) {
        refreshConfiguration("setMasterCellVersion");
        updateConfig("setMasterCellVersion", newVersionMajor);
    }
    
    /**
     * API for HCCellAdapter/HCSetupCell adapters when properties
     * are updated through CLI calls.
     * @param whichCell the cell to perform the update of properties on
     * @param properties the properties to update
     * @throws MultiCellLibError 
     */
    public void updateProperties(byte whichCell, Map properties) {

        boolean setupCell = false;

        byte newCellid = 0;
        String newAdminVIP  = "unknown";
        String newDataVIP  = "unknown";
        String newSpVIP  = "unknown";
        String newSubnet  = "unknown";
        String newGateway  = "unknown";

        refreshConfiguration("updateProperties");

        if (properties != null) {
            if (properties.containsKey(PROP_SILO_CELLID)) {
                if (whichCell != cellid) {
                    throw new MultiCellLibError("updateProperties for " +
                      "setupcell can only happen the locale cell :" +
                      "locale cell = " + cellid + 
                      ", requested cell = " + whichCell);
                }
                setupCell = true;
            }
            Iterator it = properties.keySet().iterator();
            while (it.hasNext()) {
                String key = (String) it.next();
                String value = (String) properties.get(key);

                logInfo("property " + key + " updated to " + value);
                if (key.equals(PROP_DATA_VIP)) {
                    if (setupCell) {
                        newDataVIP = value;

                    } else {
                        xmlParser.setDataVIP(whichCell, value);
                    }
                } else if (key.equals(PROP_ADMIN_VIP)) {
                    if (setupCell) {
                        newAdminVIP = value;
                    } else {
                        xmlParser.setAdminVIP(whichCell, value);
                    }
                } else if (key.equals(PROP_SP_VIP)) {
                    if (setupCell) {
                        newSpVIP = value;
                    } else {
                        xmlParser.setSPVIP(whichCell, value);
                    }
                } else if (key.equals(PROP_SUBNET)) {
                    if (setupCell) {
                        newSubnet = value;
                    } else {
                        xmlParser.setSubnet(whichCell, value);
                    }
                } else if (key.equals(PROP_GATEWAY)) {
                    if (setupCell) {
                        newGateway = value;
                    } else {
                        xmlParser.setGateway(whichCell, value);
                    }
                } else if (key.equals(PROP_SILO_CELLID)) {
                    try {
                        newCellid = Byte.parseByte(value);
                    } catch (NumberFormatException exc) {
                        // Should be checked at the CLI level.
                        throw new MultiCellLibError("invalid cellid");
                    }
                }
            }
            if (setupCell) {
                xmlParser.setSetupCell(newCellid, 
                                       newAdminVIP, 
                                       newDataVIP,
                                       newSpVIP, 
                                       newSubnet, 
                                       newGateway);
                // We set the properties immediately.  Even though not
                // all the cells in the cluster may be updated to these
                // new settings.  This allows a subsequent call 
		// that updates the switches to use the cached values
		// Otherwise the old values are used instead.
		cellid = newCellid;
                adminVIP = newAdminVIP;
                dataVIP = newDataVIP;
                spVIP = newSpVIP;
                subnet = newSubnet;
                gateway = newGateway;
            }
            updateConfig("updateProperties", getMajorVersion() + 1);
        }
    }
    
    /**
     * Update the service tag data for the specified cell
     * @param cellId the cell to update the properties for
     * @param data the updated service tag data for th specified cell
     */
    public void updateServiceTagData(byte cellId, ServiceTagData data) {
        xmlParser.setServiceTagData(cellId, data);
        updateConfig("updateServiceTagProperties", getMajorVersion() + 1);
        
        if (cellId == this.cellid) {
            // The service tag data for this cell has changed.
            // Notify listeners that the service tag data for the cell has
            // changed.   This is necessary since this is the mechanism
            // that MultiCellConfig object uses.  MultiCellConfig
            // has a cached copy of the cell properties.  
            //
            // It's not necessary for us to cache the old service tag data
            // and send it back to the listeners since this file contains
            // the only supported methods for updating the service tag
            // data for a file.
            generateEventsIfPropertyChanged(PROP_SERVICETAG, null, data);
        }
    }
    
    /**
     * Update the service tag data for 1 or more cells
     * @param data the service tag data to update
     */
    public void updateServiceTagData(ServiceTagCellData[] cellServiceTag) {
        if (cellServiceTag == null || cellServiceTag.length == 0)
            return;
        for (int i=0; i < cellServiceTag.length; i++) {
            xmlParser.setServiceTagData(cellServiceTag[i].getCellId(), 
                    cellServiceTag[i].getServiceTagData());
            if (cellServiceTag[i].getCellId().byteValue() == this.cellid) {
                // The service tag data for this cell has changed.
                // Notify listeners that the service tag data for the cell has
                // changed.   This is necessary since this is the mechanism
                // that MultiCellConfig object uses.  MultiCellConfig
                // has a cached copy of the cell properties.
                //
                // It's not necessary for us to cache the old service tag data
                // and send it back to the listeners since this file contains
                // the only supported methods for updating the service tag
                // data for a file.
                generateEventsIfPropertyChanged(PROP_SERVICETAG, 
                        null, cellServiceTag[i].getServiceTagData());
            }
        }
        updateConfig("updateServiceTagProperties", getMajorVersion() + 1);
    }

    public boolean isCellMaster() {
        refreshConfiguration("isCellMaster");
        Cell [] cells = getCells();
        byte lowerCellid = Byte.MAX_VALUE;
        for (int i = 0; i < cells.length; i++) {
            Cell curCell = (Cell) cells[i];
            lowerCellid = (curCell.getCellid() < lowerCellid) ? 
             curCell.getCellid() : lowerCellid;
        }
        return (cellid == lowerCellid) ? true : false;
    }

    public boolean isCellStandalone() {
        refreshConfiguration("isCellStandalone");
        Cell [] cells = getCells();
        return (cells.length == 1) ? true : false;
    }

    public Cell [] getCells() {
        refreshConfiguration("getCells");
        return  (Cell []) xmlParser.getCells().toArray(new Cell[0]);
    }

    public long getMajorVersion() {
        refreshConfiguration("getMajorVersion");
        return xmlParser.getVersion();
    }

    //
    // API for OA to retyreive next 'silolocation' when creating a new OID
    // for a 'data' object, and methods used by NewObjectIdentifier
    // to convert OIDS from their external <-> internal form.
    //

    // All defined as abstract, the real implementation is done in the
    // method defined below getNextSiloLocationImpl(), getOriginCellidImpl()
    // getRuleNumberImpl().
    // This is because we need the emulator to run both in multicell and
    // non multicell mode.

    public abstract short getNextSiloLocation(UID uid);
    public abstract byte getOriginCellid(byte ruleNumber, short siloLocation)
        throws MultiCellLibException;
    public abstract byte getRuleNumber(byte originCellid, short siloLocation)
        throws MultiCellLibException;

    //
    // API to retrieve latest dataVIP, adminVIP,... from
    // config file silo_config.xml
    //
    public String getAdminVIP() {
        refreshConfiguration("getAdminVIP");
        return adminVIP;
    }

    public String getDataVIP() {
        refreshConfiguration("getDataVIP");
        return dataVIP;
    }

    public String getSPVIP() {
        refreshConfiguration("getSPVIP");
        return spVIP;
    }

    public String getClusterName() {
        refreshConfiguration("getClusterName");
        return clusterName;
    }

    public String getGateway() {
        refreshConfiguration("getGateway");
        return gateway;
    }

    public String getSubnet() {
        refreshConfiguration("getSubnet");
        return subnet;
    }
    
    /**
     * @return ServiceTagData the service tag data for the cell associated
     * with this object.
     */
    public ServiceTagData getServiceTagData() {
        refreshConfiguration("getServiceTagData");
        return xmlParser.getServiceTagData(cellid);
    }
    
    /**
     * @return ServiceTagData the service tag data for the cell associated
     * with this object.
     */
    public ServiceTagCellData getServiceTagCellData() {
        refreshConfiguration("getServiceTagCellData");
        return new ServiceTagCellData(cellid, xmlParser.getServiceTagData(cellid));
    }
    
    /**
     * Fetch the service tag data for all the cells in the hive
     * @return ServiceTagCellData[] array of service tag data for all
     * the cells in the hive.
     */
    public ServiceTagCellData[] getServiceTagDataForAllCells() {
        refreshConfiguration("getAllServiceTagData");
        List<ServiceTagCellData> list = xmlParser.getServiceTagDataForAllCells();
        return (ServiceTagCellData[])list.toArray(
                    new ServiceTagCellData[list.size()]);
         
    }

    //
    // API for Protocol server to return latest MC XMl config to clients.
    //
    public abstract String getVersion()
        throws MultiCellLibException;

    public abstract byte [] getXMLConfig()
        throws IOException, MultiCellLibException;


    //
    // API used to debug content of what is printed to clients.
    // (not used)
    //
    public void printClientConfig(String file) {

        File tmpFile = new File(file);
        if (tmpFile.exists()) {
            tmpFile.delete();
        }

        try {
            FileOutputStream out = new FileOutputStream(tmpFile);
            String testVersion = getVersion();
            byte [] testConf = getXMLConfig();
            String v = "<!-- version: " + testVersion + "-->\n";
            out.write(v.getBytes());
            out.write(testConf);
            out.flush();
            out.close();
        } catch (Exception e) {
            logSevere("can;t retrieve XML client config ", e);
            return;
        }

    }

    public abstract String getConfigFileName();


    //
    // Protected
    //
    protected MultiCellLibBase() {
        ClusterProperties config = ClusterProperties.getInstance();
        try {
            cellid =  Byte.parseByte(config.getProperty(PROP_SILO_CELLID));
        } catch (Exception ignored) {
            // Because we need the emulator to support both multicell
            // and non multicell.If cellid is not set, bail out...
            cellid = (byte) 0;
            return;
        }
        xmlParser = new XMLParser(getConfigFileName());
        parseConfigFile();
    }

    
    protected void generateEventsIfPropertyChanged(String key,
                                                 Object oldValue,
                                                 Object newValue) {
        if (oldValue == null && newValue != null
                || !oldValue.equals(newValue)) {
            logInfo("property " + key +
                    " has been changed, notify listeners...");
            PropertyChangeEvent event
                = new PropertyChangeEvent(this, key, oldValue, newValue);
            synchronized (listeners) {
                for (Iterator i = listeners.iterator(); i.hasNext(); ) {
                    PropertyChangeListener l
                        = (PropertyChangeListener) i.next();
                    l.propertyChange(event);
                }
            }
        }
    }


    protected synchronized void refreshConfiguration(String caller) {


        if (xmlParser.getLastModified() != timestamp) {
            logger.info("File " + getConfigFileName() +
                    " has changed, refresh values, caller = " + caller);

            String oldDataVIP = dataVIP;
            String oldAdminVIP = adminVIP;
            String oldSPVIP = spVIP;
            String oldSubnet = subnet;
            String oldGateway = gateway;

            String key;
            parseConfigFile();
            if (!oldDataVIP.equals(dataVIP)) {               
                key = PROP_DATA_VIP;
                generateEventsIfPropertyChanged(key, oldDataVIP, dataVIP);
            }
            if (!oldAdminVIP.equals(adminVIP)) {
                key = PROP_ADMIN_VIP;
                generateEventsIfPropertyChanged(key, oldAdminVIP, adminVIP);
            }
            if (!oldSPVIP.equals(spVIP)) {
                key = PROP_SP_VIP;
                generateEventsIfPropertyChanged(key, oldSPVIP, spVIP);
            }
            if (!oldSubnet.equals(subnet)) {
                key = PROP_SUBNET;
                generateEventsIfPropertyChanged(key, oldSubnet, subnet);
            }
            if (!oldGateway.equals(gateway)) {
                key = PROP_GATEWAY;
                generateEventsIfPropertyChanged(key, oldGateway, gateway);
            }
        }
    }

    protected synchronized void parseConfigFile() {
        boolean updateConfig = xmlParser.readConfig();
        if (updateConfig) {

            timestamp = xmlParser.getLastModified();
            logInfo("parseConfigFile new timestamp =" + timestamp);
            majorInterval = xmlParser.getMajorInterval(cellid);
            dataVIP = xmlParser.getDataVIP(cellid);
            adminVIP = xmlParser.getAdminVIP(cellid);
            spVIP = xmlParser.getSPVIP(cellid);
            subnet = xmlParser.getSubnet(cellid);
            gateway = xmlParser.getGateway(cellid);
            clusterName = xmlParser.getClusterName(cellid);

            logInfo("parseConfigFile dataVIP = " + dataVIP +
             ", adminVIP = " + adminVIP +
              ", spVIP = " + spVIP +
              ", subnet = " + subnet +
              ", gateway = " + gateway);
        } else {
            timestamp = xmlParser.getLastModified();
            logInfo("parseConfigFile did not read new config, cellid changed");
        }
    }

    protected abstract void updateConfig(String type, long newVersionMajor);

    protected static void logInfo(String trace) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(MCLIB_LOG_PREFIX + trace);
        }
    }

    protected short getNextSiloLocationImpl(UID uid) {
        refreshConfiguration("getNextSiloLocationImpl");
        return majorInterval.getNextSiloLocation(uid);
    }

    protected byte getOriginCellidImpl(byte ruleNumber, short siloLocation)
        throws MultiCellLibException {
        refreshConfiguration("getOriginCellidImpl");
        return xmlParser.getOriginCellid(cellid, ruleNumber, siloLocation);
    }

    protected byte getRuleNumberImpl(byte originCellid, short siloLocation)
        throws MultiCellLibException {
        refreshConfiguration("getRuleNumberImpl");
        return xmlParser.getRuleNumber(originCellid, siloLocation);
    }

    protected static void logWarning(String trace) {
        logger.warning(MCLIB_LOG_PREFIX + trace);
    }

    protected static void logSevere(String trace) {
        logger.severe(MCLIB_LOG_PREFIX + trace);
    }

    protected static void logSevere(String trace, Throwable th) {
        logger.log(Level.SEVERE, MCLIB_LOG_PREFIX + trace +
                   ", Exception ", th);
    }


    static public class MultiCellVersionHeader implements Comparable {

        private int major;
        private int minor;

        public MultiCellVersionHeader(String in) {

            String [] versionElements = in.split("\\.");
            if (versionElements.length != 2) {
                throw new MultiCellLibError("wrong header format, input = " + 
                  in);
            }
            
            try {
                major = Integer.parseInt(versionElements[0]);
                minor = Integer.parseInt(versionElements[1]);
            } catch (NumberFormatException nfe) {
                throw new MultiCellLibError("unexpected format for header, " +
                  "input = " + in);
            }
        }

        public String toString() {
            String res = major + "." + minor;
            return res;
        }

        public int compareTo(Object obj) {
            if (! (obj instanceof MultiCellVersionHeader)) {
                throw new MultiCellLibError("unexpected type of object " +
                    "to compare with");
            }

            MultiCellVersionHeader vHeader = (MultiCellVersionHeader) obj;
            if (major > vHeader.major) {
                return 1;
            } else if (major < vHeader.major) {
                return -1;
            } else {
                if (minor > vHeader.minor) {
                    return 1;
                } else if (minor < vHeader.minor) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }

}
