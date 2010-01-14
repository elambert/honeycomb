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

import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.CellProps;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.ComboBoxEntryDisabler;
import com.sun.nws.mozart.ui.swingextensions.ComboEntry;
import com.sun.nws.mozart.ui.swingextensions.EntryDisabledComboBox;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.AsyncProxy;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.util.Vector;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;

public class PnlConfigCellIPs extends JPanel implements ContentPanel {
            
    private static String INITIALIZED_IP = "0.0.0.0";
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private String crtAdminIp = null,
            crtDataIp = null, crtSpIp = null;
    private String currentSubnetAddr = null;
    private String currentGatewayAddr = null;
    
    // Vector contains strings where a single entry is a cell's IpAddress
    private Vector admAddrs = null, dataAddrs = null, spAddrs = null;
    
    // Vector contains NetworkIP objects, which hold the subnet and gateway
    // ip addresses on a per cell basis
    private Vector ipObjects = null;
    
    // Array containing Cell objects
    private Cell[] cells = null;
    
    // Vector contains strings where a single cellIdName is a concatenation 
    // of the string, "Cell" with the cellId number (e.g. Cell 1)
    private Vector cellIdNames = null;
    
    // Vector contains Integers where a single value contains the cellid integer
    private Vector cellIds = null;

    private BtnPnlApplyCancel bPanel = new BtnPnlApplyCancel();
    
    public static class NetworkIP {
        public static final int GATEWAY_IP = 0;
        public static final int SUBNET_IP = 1;
        
        private int cellId;
        private String ipSubnet = null;
        private String ipGateway = null;
        
        public NetworkIP(int cellId, String ip, int ipType) {
            this.cellId = cellId;
            if (ipType == this.SUBNET_IP) {
                this.ipSubnet = ip;
            } else {
                this.ipGateway = ip;
            }
        }
        public NetworkIP(int cellId, String ipSubnet, String ipGateway) {
            this.cellId = cellId;
            this.ipSubnet = ipSubnet;
            this.ipGateway = ipGateway;
        }
        
        public int getCellId() { return cellId; }
        public String getSubnet() { return ipSubnet; }
        public String getGateway() { return ipGateway; }
        public void setSubnet(String ips) { ipSubnet = ips; }
        public void setGateway(String ipg) { ipGateway = ipg; }
    }
    
    
    /** Creates new form PanelAdmin */
    public PnlConfigCellIPs() {
        
        initComponents();
        initComponents2();
        
    }
    
    private void initComponents2() {
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
        cboCellId.setRenderer(new ComboBoxEntryDisabler());
        
        ipaSubnet.setIsSubnetMask(true);
        
        // cell id dropdown
        cboCellId.setWidthBy(JComboBoxWithDefaultMsg.WIDTH_BY_BOTH);
        cboCellId.addItemListener(
            new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                            handleCellSelection();
                    }
                }
            });
        bPanel.setShowApplyButton(true);
        bPanel.setShowCancelButton(true);
        // validates panel input at the time the Apply button is selected
        bPanel.setPanelValidator(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!ObjectFactory.switchesOK()) {
                    bPanel.setRefreshOnValidationFailure(true);
                    throw new RuntimeException(
                            new ClientException("data.validation.error"));
                }
                if (!validIPValues()) {
                    // do something different here with logging
                    bPanel.setRefreshOnValidationFailure(true);
                    throw new RuntimeException(
                            new ClientException("data.validation.error"));
                }
            }
        });
    }
    
    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("config.sysAccess.cellIPs.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_NONE; }
    public ButtonPanel getButtonPanel() {
        ButtonPanel bp = null;
        if (ObjectFactory.isAdmin()) {
            bp = bPanel;
        } else {
            bp = BtnPnlBlank.getButtonPanel();
        }
        return bp;
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
        }
        public void runReturn() {
            if (err) {
                lblError.setText(GuiResources.getGuiString("silo.cell.error"));
                try {
                    showErrView();
                } catch (Exception e) {
                    AsyncProxy.handleException(e);
                }
            }
        }
    }

    public void loadValues() throws UIException, HostException {
        if (ObjectFactory.isGetCellsError()) {
            showErrView();
            Log.logToStatusAreaAndExternal(Level.SEVERE,
                    GuiResources.getGuiString("silo.cell.error"), null);
            return;
        }
        Log.logInfoMessage(
                        GuiResources.getGuiString("cell.ips.refresh.message"));
        try {
            showCellInfo();
        } catch (Exception e) {
            AsyncProxy.handleException(e);
        }
        AsyncProxy.run(new DataLoader());
    }
    private void showErrView() throws UIException, HostException {
        try {
            lblError.setText(GuiResources.getGuiString("silo.cell.error"));
            ipaAddress.setText(INITIALIZED_IP);
            dataAddr.setText(INITIALIZED_IP);
            spAddr.setText(INITIALIZED_IP);
            NetworkIP nip = new NetworkIP(-1, INITIALIZED_IP, INITIALIZED_IP);
            ipaSubnet.setText(INITIALIZED_IP);
            ipaGateway.setText(INITIALIZED_IP);
            cboCellId.removeAllItems();
            enableAll(false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void showCellInfo() throws UIException, HostException {
        try {
            lblError.setText("");
            enableAll(true);
            int numCells = -1;
            try {
                cells = hostConn.getCells();
            } catch (Exception e) {
                showErrView();
                Log.logToStatusAreaAndExternal(Level.SEVERE,
                        GuiResources.getGuiString("silo.cell.error"), e);
                return;
            }
            if (cells == null) {
                showErrView();
                Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                        "info.noCells"));
                return;
            } 
            
            // set up the drop down
            cboCellId.removeAllItems();
            String cellIdName = null;
            
            // populate the cell props info
            CellProps cellProps = null;
            String aIpAddr, dIpAddr, spIpAddr, gIpAddr, sIpAddr = null;
            NetworkIP ipObj = null;
            
            // populate the drop down values
            numCells = cells.length;
            if (numCells > 0) {
                cellIds = new Vector(numCells);
                cellIdNames = new Vector(numCells);
                admAddrs = new Vector(numCells);
                dataAddrs = new Vector(numCells);
                spAddrs   = new Vector(numCells);
                ipObjects = new Vector(numCells);
            }
            for (int i = 0; i < numCells; i++) {
                aIpAddr = INITIALIZED_IP;
                dIpAddr = INITIALIZED_IP;
                spIpAddr = INITIALIZED_IP;
                gIpAddr = INITIALIZED_IP;
                sIpAddr = INITIALIZED_IP;
                int id = cells[i].getID();
                cellIds.add(new Integer(id));
                if (cells[i].isAlive()) {
                    cellIdName = GuiResources.getGuiString(
                        "explorer.silo.cells.cell", Integer.toString(id));
                    try {
                        cellProps = hostConn.getCellProps(cells[i].getID());
                        aIpAddr = cellProps.adminIP;
                        dIpAddr = cellProps.dataIP;
                        spIpAddr = cellProps.spIP;
                        sIpAddr = cellProps.subnet;
                        gIpAddr = cellProps.gatewayIP;
                    } catch (Exception e) {
                        // if connection is refused due to cell being down
                        // then we don't want to bomb out -- notify user
                        Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                GuiResources.getGuiString(
                                        "cellProps.refresh.error", 
                                Integer.toString(cells[i].getID())), null);                        
                    }
                } else {
                    cellIdName = GuiResources.getGuiString(
                           "cell.down.error", Integer.toString(id));
                }
                admAddrs.add(aIpAddr);
                dataAddrs.add(dIpAddr);
                spAddrs.add(spIpAddr);
                ipObj = new NetworkIP(id, sIpAddr, gIpAddr);
                cellIdNames.add(cellIdName);
                ipObjects.add(ipObj);
            }
            populateDropdown();
            int lastCellId = ObjectFactory.getLastCellId();
            if (lastCellId < 0) {
                // If not set, the last visited cell defaults to
                // the first cell
                lastCellId = ((Integer)cellIds.get(0)).intValue();
                ObjectFactory.setLastCellId(lastCellId);
            }
            // Check to see if the last visited cell is alive....if it isn't,
            // then set the dropdown selection to the first entry
            // (i.e. master cell)
            Cell selectedCell = findCell(cells, lastCellId);
            if (!selectedCell.isAlive()) {
                // master cell is always alive
                lastCellId = cells[0].getID();
            }
            // Set the ip address field with the value
            // from the last visited cell -- need to retrieve
            // the index at which the cell Id is located since
            // its ip address is found at the same index in the
            // vector of ip addresses.
            int lastCellIndex = cellIds.
                    indexOf(new Integer(lastCellId));
            ipaAddress.setText((String)admAddrs.get(lastCellIndex));
            dataAddr.setText((String)dataAddrs.get(lastCellIndex));
            spAddr.setText((String)spAddrs.get(lastCellIndex));
            NetworkIP nip = (NetworkIP)ipObjects.get(lastCellIndex);
            ipaSubnet.setText((String)nip.getSubnet());
            ipaGateway.setText((String)nip.getGateway());
            cboCellId.setSelectedIndex(lastCellIndex);  
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    
    public class SaveThread implements Runnable {
        public void run() {
            try {
                if (bPanel.getConfirmValue() == JOptionPane.OK_OPTION) {
                    int idx = cboCellId.getSelectedIndex();
                    Integer cellId = (Integer) cellIds.get(idx);
                    
                    // gateway
                    NetworkIP nip = (NetworkIP)ipObjects.get(idx);
                    String ips = ipaSubnet.getText();
                    String ipg = ipaGateway.getText();

                    // Only save values if they differ
                    if (!ipg.equals(nip.getGateway())) {
                        hostConn.setCellAddress(cellId.intValue(),
                                AdminApi.ADDRT_GTWAY_IP, ipg);
                        nip.setGateway(ipg);
                    }
                    if (!ips.equals(nip.getSubnet())) {
                        hostConn.setCellAddress(cellId.intValue(),
                                AdminApi.ADDRT_SUBNET, ips);
                        nip.setSubnet(ips);
                    }
                    
                    // admin, data and sp IPs
                    CellProps cp = new CellProps();
                    cp.adminIP = ipaAddress.getText();
                    cp.dataIP  = dataAddr.getText();
                    cp.spIP    = spAddr.getText();
                    hostConn.setCellProps(cellId.intValue(), cp);
                    Log.logInfoMessage(GuiResources.getGuiString(
                                        "config.sysAccess.cellIPs.saved"));
                    if (isSelectedCellMaster()) {
                        // Reboot and shutdown application if cell selected is
                        // the Master cell
                        System.exit(0);
                    }
                    // Flush cache
                    ObjectFactory.clearCache();
                }
            } catch (Exception e) {                
                throw new RuntimeException(e);
            }             
        }
        public void runReturn() {
        }
    }
    
    public void saveValues() throws UIException, HostException {
        // TODO: This will be enabled post-1.1 when all save calls
        // are ready.
        //  AsyncProxy.run(new SaveThread(), this);
        new SaveThread().run();
    }

    public String getPageKey() {
        return HelpFileMapping.CONFIGURECELLIPS;
    }

    // ************************************
    private void enableAll(boolean flag) {
        cboCellId.setEnabled(flag);
        ipaAddress.setEnabled(flag);
        dataAddr.setEnabled(flag);
        spAddr.setEnabled(flag);
        ipaSubnet.setEnabled(flag);
        ipaGateway.setEnabled(flag);
    }    
    private void populateDropdown() {
        Vector entries = new Vector(cellIdNames.size());
        for (int i = 0; i < cellIdNames.size(); i++) {
            ComboEntry entry = new ComboEntry((String)cellIdNames.get(i));
            if (entry.getText().indexOf(GuiResources.getGuiString("cell.down")) > -1) {
                // disable the combobox entry
                entry.setEnabled(false);
            }
            entries.add(entry);
        }
        cboCellId.setModel(new DefaultComboBoxModel(entries));
    }
    
    // 
    private boolean validIPValues() {
        boolean valid = false;
        if (Validate.isValidIpAddress(ipaSubnet.getText()) &&
                Validate.isValidIpAddress(ipaGateway.getText()) &&
                    Validate.isValidIpAddress(ipaAddress.getText()) &&
                        Validate.isValidIpAddress(dataAddr.getText()) &&
                            Validate.isValidIpAddress(spAddr.getText())) {
            valid = true;
        }
        return valid;
    }
    
    private Cell findCell(Cell[] cArray, int id) {
        Cell c = null;
        for (int idx = 0; idx < cArray.length; idx++) {
            c = cArray[idx];
            if (c.getID() == id) {
                break;
            }
        }
        return c;
    }
    
    // Finds the vector index which correlates to a NetworkIP object
    // containing the subnet and gateway IP values -- returns -1 if not found
    private int findCellIndex(int id) {
        int index = -1;
        for (int idx = 0; idx < ipObjects.size(); idx++) {
            NetworkIP nip = (NetworkIP)ipObjects.get(idx);
            if (id == nip.getCellId()) {
                index = idx;
                break;
            }
        }
        return index;
    }
    
    protected boolean isSelectedCellMaster() {
        boolean master = false;
        int idx = cboCellId.getSelectedIndex();
        if (idx >= 0) {
            Cell cObj = cells[idx];
            master = cObj.isMaster();
        }
        return master;
    }
    
    protected void handleCellSelection() {
        String msg = GuiResources.getGuiString(
                        "cell.operation.reboot.mandatory.warning", 
                                String.valueOf(ObjectFactory.getLastCellId()));
        int idx = cboCellId.getSelectedIndex();
        if (idx >= 0) {
            crtAdminIp = (String)admAddrs.get(idx);
            crtDataIp = (String)dataAddrs.get(idx);
            crtSpIp = (String)spAddrs.get(idx);
        }
        ipaAddress.setText(crtAdminIp);
        dataAddr.setText(crtDataIp);
        spAddr.setText(crtSpIp);
        ObjectFactory.setLastCellId(((Integer)cellIds.
                get(idx)).intValue());
        if (isSelectedCellMaster()) {
            msg = GuiResources.getGuiString(
                        "cell.operation.reboot.applClosing.mandatory.warning", 
                            String.valueOf(ObjectFactory.getLastCellId()));
        } 
        bPanel.setShowConfirmDialog(true, msg, 
                                        GuiResources.getGuiString("app.name"),
                                                JOptionPane.OK_CANCEL_OPTION);
    }

    // Display warning & confirmation message if user edits 
    // the IP address and presses Apply button
    private void handleIPAddressPropertyChanged(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(IpAddressField.PROP_IP_ADDRESS)) {
            String msg = GuiResources.getGuiString(
                        "cell.operation.reboot.mandatory.warning", 
                                String.valueOf(ObjectFactory.getLastCellId()));
            if (isSelectedCellMaster()) {
                msg = GuiResources.getGuiString(
                        "cell.operation.reboot.applClosing.mandatory.warning", 
                                String.valueOf(ObjectFactory.getLastCellId()));
            }
            bPanel.setShowConfirmDialog(true, msg, 
                                        GuiResources.getGuiString("app.name"),
                                                JOptionPane.OK_CANCEL_OPTION);
            explItem.setIsModified(true);
        }
    }
    
    /** 
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        lblAddress = new javax.swing.JLabel();
        ipaAddress = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        lblCellId = new javax.swing.JLabel();
        cboCellId = new EntryDisabledComboBox(new ComboEntry(GuiResources.getGuiString("cell.multicell.unselected")));
        lblAddress1 = new javax.swing.JLabel();
        dataAddr = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        lblAddress2 = new javax.swing.JLabel();
        spAddr = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        pnlGateway = new javax.swing.JPanel();
        lblGateway = new javax.swing.JLabel();
        ipaGateway = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        lblSubnet = new javax.swing.JLabel();
        ipaSubnet = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        lblError = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        lblAddress.setDisplayedMnemonic(GuiResources.getGuiString("config.sysAccess.adminIp.address.mn").charAt(0));
        lblAddress.setLabelFor(ipaAddress);
        lblAddress.setText(GuiResources.getGuiString("config.sysAccess.adminIp.address"));

        ipaAddress.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaAddressPropertyChange(evt);
            }
        });

        lblCellId.setDisplayedMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.multicell.mn").charAt(0));
        lblCellId.setLabelFor(cboCellId);
        lblCellId.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.multicell"));

        cboCellId.setMaximumRowCount(32);
        cboCellId.getAccessibleContext().setAccessibleName("Cell:");

        lblAddress1.setDisplayedMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.sysAccess.dataIp.address.mn").charAt(0));
        lblAddress1.setLabelFor(dataAddr);
        lblAddress1.setText(GuiResources.getGuiString("config.sysAccess.dataIp.address"));

        dataAddr.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaAddressPropertyChange(evt);
            }
        });

        lblAddress2.setDisplayedMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.sysAccess.nodeIp.address.mn").charAt(0));
        lblAddress2.setLabelFor(spAddr);
        lblAddress2.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.sysAccess.nodeIp.address"));

        spAddr.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaAddressPropertyChange(evt);
            }
        });

        pnlGateway.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        lblGateway.setDisplayedMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.network.ip.gateway.mn").charAt(0));
        lblGateway.setText(GuiResources.getGuiString("config.network.ip.gateway"));

        ipaGateway.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaGatewayPropertyChange(evt);
            }
        });

        lblSubnet.setDisplayedMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.network.ip.subnet.mn").charAt(0));
        lblSubnet.setText(GuiResources.getGuiString("config.network.ip.subnet"));

        ipaSubnet.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaSubnetPropertyChange(evt);
            }
        });

        org.jdesktop.layout.GroupLayout pnlGatewayLayout = new org.jdesktop.layout.GroupLayout(pnlGateway);
        pnlGateway.setLayout(pnlGatewayLayout);
        pnlGatewayLayout.setHorizontalGroup(
            pnlGatewayLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlGatewayLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlGatewayLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblGateway)
                    .add(lblSubnet))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 88, Short.MAX_VALUE)
                .add(pnlGatewayLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(ipaGateway, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 147, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(ipaSubnet, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 147, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(23, 23, 23))
        );
        pnlGatewayLayout.setVerticalGroup(
            pnlGatewayLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlGatewayLayout.createSequentialGroup()
                .add(20, 20, 20)
                .add(pnlGatewayLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblGateway)
                    .add(ipaGateway, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(16, 16, 16)
                .add(pnlGatewayLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblSubnet)
                    .add(ipaSubnet, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(23, Short.MAX_VALUE))
        );

        lblError.setForeground(new java.awt.Color(255, 0, 0));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(25, 25, 25)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblCellId)
                            .add(lblAddress2)
                            .add(lblAddress1)
                            .add(lblAddress))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 45, Short.MAX_VALUE)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(spAddr, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 147, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(dataAddr, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 147, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(ipaAddress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 147, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(cboCellId, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 147, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(26, 26, 26))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(pnlGateway, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(lblError, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {cboCellId, dataAddr, ipaAddress, spAddr}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(50, 50, 50)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblCellId, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(cboCellId, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(16, 16, 16)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblAddress)
                    .add(ipaAddress, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(14, 14, 14)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblAddress1)
                    .add(dataAddr, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(15, 15, 15)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblAddress2)
                    .add(spAddr, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(19, 19, 19)
                .add(pnlGateway, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(lblError, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {cboCellId, dataAddr, ipaAddress, spAddr}, org.jdesktop.layout.GroupLayout.VERTICAL);

    }// </editor-fold>//GEN-END:initComponents

    private void ipaSubnetPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_ipaSubnetPropertyChange
        handleIPAddressPropertyChanged(evt);
    }//GEN-LAST:event_ipaSubnetPropertyChange

    private void ipaGatewayPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_ipaGatewayPropertyChange
        handleIPAddressPropertyChanged(evt);
    }//GEN-LAST:event_ipaGatewayPropertyChange

    private void ipaAddressPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_ipaAddressPropertyChange
        handleIPAddressPropertyChanged(evt);
    }//GEN-LAST:event_ipaAddressPropertyChange
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg cboCellId;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField dataAddr;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField ipaAddress;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField ipaGateway;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField ipaSubnet;
    private javax.swing.JLabel lblAddress;
    private javax.swing.JLabel lblAddress1;
    private javax.swing.JLabel lblAddress2;
    private javax.swing.JLabel lblCellId;
    private javax.swing.JLabel lblError;
    private javax.swing.JLabel lblGateway;
    private javax.swing.JLabel lblSubnet;
    private javax.swing.JPanel pnlGateway;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField spAddr;
    // End of variables declaration//GEN-END:variables
    
}
