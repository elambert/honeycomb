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

 

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.Sensor;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.contentpanels.PnlEnvironmentStatus;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.AsyncProxy;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.EnvironmentValue;
import java.util.Vector;
import java.util.logging.Level;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;

/**
 *
 */
public class PnlMonitorEnvStatus extends PnlEnvironmentStatus
                                            implements ContentPanel {
    
    private ExplorerItem explItem = null;
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private Cell[] cells = null;
    private boolean initView = false;
    
    public PnlMonitorEnvStatus() {
        this(GuiResources.getGuiString("monitor.envstatus.headString"));
    }
    /** Creates a new instance of PnlMonitorEnvStatus */
    public PnlMonitorEnvStatus(String id) {
        super(id);
    }

    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("monitor.envstatus.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_BOTH; }
    public ButtonPanel getButtonPanel() {
        return BtnPnlBlank.getButtonPanel();
    }
    
    // Task which primes the cache by calling all necessary AdminApi methods.
    public class DataLoader implements Runnable {
        boolean err = false;
        public void run() {
            try {
                cells = hostConn.getCells();
            } catch (Exception e) {
                err = true;
                Log.logToStatusAreaAndExternal(Level.SEVERE,
                        GuiResources.getGuiString("silo.cell.error"), e);
            }
            if (!err) {
                try {
                    int numCells = cells.length;
                    for (int idx = 0; idx < numCells; idx++) {
                        Node[] nodes = null;
                        if (cells[idx].isAlive()) {
                            nodes = hostConn.getNodes(cells[idx]);
                            for (int nIdx = 0; nIdx < nodes.length; nIdx++) {
                                Sensor[] sensorList = 
                                            hostConn.getSensors(nodes[nIdx]);
                            } // get all sensor data for all nodes
                        }
                    } // get all of the nodes for the cell
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        public void runReturn() {
            if (err) {
                lblError.setText(GuiResources.getGuiString("silo.cell.error"));
            } else {
                initView = false;
                buildGui();
            }
        }
    }
        
    public void loadValues() throws UIException, HostException {
        initView = true;
        lblError.setText("");
        buildGui();
        if (ObjectFactory.isGetCellsError()) {
            lblError.setText(GuiResources.getGuiString("silo.cell.error"));
            Log.logToStatusAreaAndExternal(Level.SEVERE,
                    GuiResources.getGuiString("silo.cell.error"), null);
            return;
        }
        Log.logInfoMessage(GuiResources.getGuiString(
                            "environmentStatus.cell.refresh.message"));
        AsyncProxy.run(new DataLoader());
    }
    private void buildGui() {
        boolean noCellInfo = true;
         try {
            int numCells = 0;
            Node[] nodes = null;
            if (ObjectFactory.isGetCellsError() || initView) {
                numCells = hostConn.getNumOfCells();
            } else {
                cells = hostConn.getCells();
                if (cells == null) {
                    Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                        "info.noCells"));
                    return;
                }
                noCellInfo = false;
                // Number of cells in the silo
                numCells = cells.length;
            }
            
            Vector vValueObjects = new Vector(numCells);
            if (noCellInfo) {
                // if there isn't any cell information then create an empty
                // EnvironmentValue object in order to display an initial view
                // with disabled dropdowns
                vValueObjects.add(makeEnvValueObject(null, null, null, false));
                enableDropdowns(false);
            } else {
                for (int idx = 0; idx < numCells; idx++) {
                    if (cells[idx].isAlive()) {
                        nodes = hostConn.getNodes(cells[idx]);
                        for (int nIdx = 0; nIdx < nodes.length; nIdx++) {
                            Sensor[] sList = hostConn.getSensors(nodes[nIdx]);
                            for (int sIdx = 0; sIdx < sList.length; sIdx++) {
                                // create an EnvironmentValue object for each
                                // piece of sensor information
                               vValueObjects.add(makeEnvValueObject(nodes[nIdx],
                                                            cells[idx],
                                                            sList[sIdx],
                                                            false));
                            } // convert all Sensor obj to EnvironmentValue objs
                        } // get all sensor data for all nodes
                    } else {
                        // create an EnvironmentValue object which shows the
                        // right label in the dropdown (e.g. Cell 0 (down))
                        vValueObjects.add(makeEnvValueObject(null, cells[idx], 
                                                                   null, true));
                    }
                } // done getting all of the nodes for the cell if cell is alive
                enableDropdowns(true);
            }
            setData(vValueObjects);
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public void saveValues() throws UIException, HostException {
        // read-only panel -- not implemented
    }
    
    // ************************************
    private void enableDropdowns(boolean enable) {
        componentType.setEnabled(enable);
        status.setEnabled(enable);
        cell.setEnabled(enable);
        node.setEnabled(enable);
    }
    private int mapType(int id) {
        switch (id) {
            case (Sensor.SYSF1_F):
            case (Sensor.SYSF2_F):    
            case (Sensor.SYSF3_F):
            case (Sensor.SYSF4_F):
            case (Sensor.SYSF5_F):
                return EnvironmentValue.TYPE_FAN;
            case (Sensor.DDR_V):
            case (Sensor.CPU_V):
            case (Sensor.MB3V_V):
            case (Sensor.MB5V_V):
            case (Sensor.MB12V_V):
            case (Sensor.BAT_V):
                return EnvironmentValue.TYPE_VOLTAGE;
            case (Sensor.CPU_T):
            case (Sensor.SYS_T):
                return EnvironmentValue.TYPE_TEMPERATURE;
            default:
                return EnvironmentValue.TYPE_NO_VALUE;
        }
    }
    
    private int mapStatus(int status) {
        switch (status) {
            case (Sensor.OK):
                return EnvironmentValue.STATUS_OK;
            case (Sensor.OUT_OF_RANGE):    
                return EnvironmentValue.STATUS_OUTOFRANGE;
            case (Sensor.UNKNOWN):
                return EnvironmentValue.STATUS_UNKNOWN;
            default:
                return EnvironmentValue.STATUS_NO_VALUE;
        }
    }
    
    private String mapComponent(int id) {
        switch (id) {
            case (Sensor.SYSF1_F):
                return GuiResources.getGuiString("monitor.envstatus.SYSF1_F");
            case (Sensor.SYSF2_F):    
                return GuiResources.getGuiString("monitor.envstatus.SYSF2_F");
            case (Sensor.SYSF3_F):
                return GuiResources.getGuiString("monitor.envstatus.SYSF3_F");
            case (Sensor.SYSF4_F):
                return GuiResources.getGuiString("monitor.envstatus.SYSF4_F");
            case (Sensor.SYSF5_F):
                return GuiResources.getGuiString("monitor.envstatus.SYSF5_F");
            case (Sensor.DDR_V):
                return GuiResources.getGuiString("monitor.envstatus.DDR_V");
            case (Sensor.CPU_V):
                return GuiResources.getGuiString("monitor.envstatus.CPU_V");
            case (Sensor.MB3V_V):
                return GuiResources.getGuiString("monitor.envstatus.MB3V_V");
            case (Sensor.MB5V_V):
                return GuiResources.getGuiString("monitor.envstatus.MB5V_V");
            case (Sensor.MB12V_V):
                return GuiResources.getGuiString("monitor.envstatus.MB12V_V");
            case (Sensor.BAT_V):
                return GuiResources.getGuiString("monitor.envstatus.BAT_V");
            case (Sensor.CPU_T):
                return GuiResources.getGuiString("monitor.envstatus.CPU_T");
            case (Sensor.SYS_T):
                return GuiResources.getGuiString("monitor.envstatus.SYS_T");
            default:
                return "";
        }
    }

    private EnvironmentValue makeEnvValueObject(Node node, Cell c, 
                                            Sensor s, boolean deadCell) {
        if (deadCell) {
            // cell is not alive
            return new EnvironmentValue(GuiResources.getGuiString(
                      "cell.down.envStatus.error", Integer.toString(c.getID())),
                      EnvironmentValue.NO_VALUE, deadCell);
            
        } else if (!deadCell && c == null) {
            // no cell information so don't know if cell is alive or dead but
            // still need to create empty EnvironmentValue object
            return new EnvironmentValue();
        }
        // name of the component, its type and status
        String name = mapComponent(s.getID());
        int type = mapType(s.getID());
        int status = mapStatus(s.getStatus());
        
        return new EnvironmentValue(String.valueOf(c.getID()), node.getID(), 
                name, type, status, s.getValue(), s.getMin(), s.getMax());
    }
}
