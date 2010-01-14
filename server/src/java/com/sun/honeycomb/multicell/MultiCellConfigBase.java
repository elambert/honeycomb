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




package com.sun.honeycomb.multicell;

import com.sun.honeycomb.admin.mgmt.servicetags.ServiceTagData;
import java.util.List;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.multicell.lib.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class MultiCellConfigBase implements PropertyChangeListener {

    static protected final String   PROP_SILO_CELLID      =
      "honeycomb.silo.cellid";
    static protected final String   PROP_SILO_POT_REFRESH = 
      "honeycomb.cell.pot.refresh";
    static protected final String   PROP_LOG_LEVEL        =
      "honeycomb.multicell.loglevel";
    static public final String      PROP_MGMT_PORT        =
      "honeycomb.cell.mgmt.port";

    static protected long          POT_REFRESH_DEFAULT = 300000; // 5 min
    static protected int           MGMT_PORT_DEFAULT = 9000;


    protected ClusterProperties     config;
    protected MultiCellLib          multiCellLib;
    protected MultiCellBase         multiCellSvc;
    protected CellInfo              thisCell;
    protected XMLParser             xmlParser;
    protected long                  potRefresh;
    protected int                   mgmtPort;

    protected static int            logLevel = 
        MultiCellLogger.MULTICELL_LOG_LEVEL_UNINITIALIZED;

    public MultiCellConfigBase(MultiCellBase svc) {
        thisCell = null;
        multiCellSvc = svc;
        config = ClusterProperties.getInstance();
        multiCellLib = MultiCellLib.getInstance();
        xmlParser = new XMLParser(multiCellLib.getConfigFileName());
        readLogLevelFromConfig();
        config.addPropertyListener(this);
        multiCellLib.addPropertyListener(this);
    }

    public void init() {
        readPotRefreshFromConfig();
        readMgmtPort();
    }

    public CellInfo getThisCell() {
        return thisCell;
    }

    static public int getLogLevel() {
        return logLevel;
    }

    public long getPotRefresh() {
        return potRefresh;
    }

    public int getMgmtPort() {
        return mgmtPort;
    }

    public void loadSiloConfig() {
        
        byte thisCellID = -1;
        try {
            thisCellID = Byte.parseByte(config.getProperty(PROP_SILO_CELLID));
        } catch (Exception ex) {
            throw new MultiCellError("property " + PROP_SILO_CELLID +
                                         " does not exist or is invalid");
        }
        if (thisCellID > (byte) MultiCell.MAX_CELLS) {
            throw new MultiCellError("property " + PROP_SILO_CELLID +
                " needs to be lower than the max number of cells (" +
                MultiCell.MAX_CELLS + ")");
        }

        xmlParser.readConfig();
        List cells = xmlParser.getCells();
        for (int i = 0; i < cells.size(); i++) {
            Cell curCell = (Cell) cells.get(i);
            CellInfo cellInfo = null;
	    try {
                cellInfo = multiCellSvc.loadCell(curCell, thisCellID);
            } catch (MultiCellException mce) {
                throw new MultiCellError(mce.toString());
            }
            if (cellInfo.getCellid() == thisCellID) {
                thisCell = cellInfo;
            }
        }
    }

    protected void readMgmtPort() {
        try {
            mgmtPort = 
                Integer.parseInt(config.getProperty(PROP_MGMT_PORT));
        } catch (Exception ignore) {          
            multiCellSvc.getMCLogger().logSevere("invalid prorperty " + 
                                                 PROP_MGMT_PORT+ 
                                                 ", default to " +
                                                 MGMT_PORT_DEFAULT);
            mgmtPort = MGMT_PORT_DEFAULT;
        }
    }

    protected void readLogLevelFromConfig() {
        try {
            logLevel = Integer.parseInt(config.getProperty(PROP_LOG_LEVEL));
            logLevel = (logLevel <
                        MultiCellLogger.MULTICELL_LOG_LEVEL_EXTRA_VERBOSE) ?
                logLevel : 
                    MultiCellLogger.MULTICELL_LOG_LEVEL_EXTRA_VERBOSE;
        } catch (Exception ignore) {
            logLevel = MultiCellLogger.MULTICELL_LOG_LEVEL_DEFAULT;
        }
    }

    protected void readPotRefreshFromConfig() {
        try {
            potRefresh = 
                Long.parseLong(config.getProperty(PROP_SILO_POT_REFRESH));
        } catch (Exception ignore) {       
            multiCellSvc.getMCLogger().logSevere("invalid prorperty " + 
                                                 PROP_SILO_POT_REFRESH + 
                                                 ", default to " +
                                                 POT_REFRESH_DEFAULT);
            potRefresh = POT_REFRESH_DEFAULT;
        }
    }

    protected String printValue(Object obj) {
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Integer) {
            return String.valueOf((Integer) obj);
        }
        if (obj instanceof Long) {
            return String.valueOf((Long) obj);
        }
        if (obj instanceof ServiceTagData) {
            return ((ServiceTagData)obj).toString();
        }
        return "unknown";
    }

 
    /**
     * Listen to config/update changes from the MultiCell object.
     * @param event
     */
    public void propertyChange(PropertyChangeEvent event) {

        String prop = event.getPropertyName();
        multiCellSvc.getMCLogger().logDefault("property " + prop +
                                              " changed from " +
                                              printValue(event.getOldValue()) +
                                              " to " +
                                              printValue(event.getNewValue()));

        if (prop.equals(PROP_LOG_LEVEL)) {
            readLogLevelFromConfig();  
        }
        if (prop.equals(PROP_SILO_POT_REFRESH)) {
            readPotRefreshFromConfig();
        }
        if (prop.equals(PROP_MGMT_PORT)) {
            readMgmtPort();
        }
        if (prop.equals(MultiCellLib.PROP_DATA_VIP)) {
            thisCell.setDataVIP((String) event.getNewValue());
        }
        if (prop.equals(MultiCellLib.PROP_ADMIN_VIP)) {
            thisCell.setAdminVIP((String) event.getNewValue());
        } 
        if (prop.equals(MultiCellLib.PROP_SP_VIP)) {
            thisCell.setSPVIP((String) event.getNewValue());
        }
        if (prop.equals(MultiCellLib.PROP_SUBNET)) {
            thisCell.setSubnet((String) event.getNewValue());
        }
        if (prop.equals(MultiCellLib.PROP_GATEWAY)) {
            thisCell.setGateway((String) event.getNewValue());
        }
        if (prop.equals(MultiCellLib.PROP_SERVICETAG)) {
            // The service tag data for this cell has been updated
            // via a call to the updateServiceTagData routines in MultiCellLib
            // Update the cached copy of the cell data
            ServiceTagData tagData = (ServiceTagData)event.getNewValue();
            thisCell.setServiceTagData(tagData);
        }
   }
}
