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
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemCells;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 *
 * @author  jb127219
 */
public class PnlSiloExpansion extends JPanel implements ContentPanel {
    private static String INITIALIZED_IP = "0.0.0.0";
    private ExplorerItem explItem = null;
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private HashMap cellIdToObjMap = new HashMap();
    private Integer[] cellIdOrderedArray = null;
    private Cell[] cells = null;
    
    /** Creates new form PnlSiloExpansion */
    public PnlSiloExpansion() {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {    
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
        // Cells combo box
        cboCells.setMessage(
                GuiResources.getGuiString("config.siloExpansion.unselected"));
        cboCells.setWidthBy(JComboBoxWithDefaultMsg.WIDTH_BY_BOTH);
        cboCells.addItemListener(
            new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED &&
                            rbtnRemoveCell.isSelected()) {
                            explItem.setIsModified(true);
                    }
                    if (e.getStateChange() == ItemEvent.SELECTED &&
                                        cboCells.getSelectedIndex() > 0) {
                        explItem.setIsModified(true);
                        rbtnRemoveCell.setSelected(true);
                        ipaAdmin.setText(INITIALIZED_IP);
                        ipaData.setText(INITIALIZED_IP);
                    }
                    
                }
            });
        
        btnGroup.add(rbtnAddCell);
        btnGroup.add(rbtnRemoveCell);
        /**
         * If the "add" radio button is selected, the combobox will display its
         * default message and the selection index will be reset.
         */
        rbtnAddCell.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ipaAdmin.setEnabled(true);
                    ipaData.setEnabled(true);  
                    // set initial values
                    cboCells.setSelectedIndex(-1);
                    ipaAdmin.setText(INITIALIZED_IP);
                    ipaData.setText(INITIALIZED_IP);
                    if (ipaAdmin.getText().compareTo(INITIALIZED_IP) == 0 &&
                            ipaData.getText().compareTo(INITIALIZED_IP) == 0) {
                        explItem.setIsModified(false);
                    }
                }
            });
        /**
         * If the "remove" radio button is selected, the admin and data IP's
         * are re-initialized.
         */
        rbtnRemoveCell.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    cboCells.setEnabled(true);
                    // set initial values
                    cboCells.setSelectedIndex(-1);
                    ipaAdmin.setText(INITIALIZED_IP);
                    ipaData.setText(INITIALIZED_IP);
                    if (cboCells.getSelectedIndex() < 0) {
                        explItem.setIsModified(false);
                    }
                }
            });
            
        ipaAdmin.addPropertyChangeListener(
            new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent e) {
                    rbtnAddCell.setSelected(true);
                }
        });
        
        ipaData.addPropertyChangeListener(
            new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent e) {
                    rbtnAddCell.setSelected(true);
                }
        });
    }
    
    // ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("config.siloExpansion.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_NONE; }
    public ButtonPanel getButtonPanel() {
        ButtonPanel bPanel = null;
        if (ObjectFactory.isAdmin()) {
            bPanel = BtnPnlApplyCancel.getButtonPanel();
        } else {
            bPanel = BtnPnlBlank.getButtonPanel();
        }
        return bPanel;
    }
    
    public void loadValues() throws UIException, HostException {
        if (!ObjectFactory.isAdmin()) {
            disableAllItems();
        }
        
        // initialization
        ipaAdmin.setText(INITIALIZED_IP);
        ipaData.setText(INITIALIZED_IP);
        cellIdToObjMap.clear();
        rbtnRemoveCell.setEnabled(true);
        cboCells.setEnabled(true);

        try {
            int numCells = 0;
            cells = hostConn.getCells();
            
            if (cells == null) {
                Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                        "info.noCells"));
                cboCells.setEnabled(false);
            } else {
                // set up the drop down
                cboCells.removeAllItems();
                cboCells.setSelectedIndex(-1);
                cboCells.addItem(GuiResources
                            .getGuiString("config.siloExpansion.unselected"));
                 
                // populate the drop down values IFF there is more than one
                // cell...if there is ONLY one cell, then can't remove it
                String cellIdName = null;
                numCells = cells.length;

                if (numCells > 1) {
                    for (int i = 0; i < numCells; i++) {
                        int id = cells[i].getID();
                        cellIdToObjMap.put(new Integer(id), cells[i]);
                    }
                    // Ensure that cells are listed from lowest to highed ID
                    // since the lowest cell id is ALWAYS the master cell
                    cellIdOrderedArray = new Integer[numCells];
                    TreeSet orderedCells = new TreeSet(cellIdToObjMap.keySet());
                    orderedCells.toArray(cellIdOrderedArray);
                    cboCells.addItem(GuiResources.getGuiString(
                                        "explorer.silo.cells.cell.master", 
                                            cellIdOrderedArray[0].toString()));
                    for (int idx = 1; idx < numCells; idx++) {
                        cellIdName = GuiResources.getGuiString(
                                        "explorer.silo.cells.cell", 
                                            cellIdOrderedArray[idx].toString());
                        cboCells.addItem(cellIdName);
                    }
                } else {
                    // disable the remove button and the drop down since the
                    // hive only has one cell and it can't be removed
                    rbtnRemoveCell.setEnabled(false);
                    cboCells.setEnabled(false);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }    
    }
    
    public void saveValues() throws UIException, HostException {
        String invalidMsg = "";
        try {
            if (rbtnAddCell.isSelected()) {
                String adminIP = ipaAdmin.getText();
                String dataIP = ipaData.getText();
                if (adminIP.equals(INITIALIZED_IP) || 
                                dataIP.equals(INITIALIZED_IP)) {
                    invalidMsg = GuiResources
                                .getGuiString("config.siloExpansion.error.ip");
                }
                if (invalidMsg.length() == 0) {
                    Log.logInfoMessage(GuiResources
                        .getGuiString("config.siloExpansion.add.message"));
                    // add the cell -- know adminIP and dataIP
                    hostConn.addCell(adminIP, dataIP);
                    Log.logInfoMessage(GuiResources
                        .getGuiString("config.siloExpansion.success.message"));
                }
            } else {
                int idx = cboCells.getSelectedIndex();
                int cellId = cellIdOrderedArray[idx-1].intValue();
                if (cellIdOrderedArray[0].equals(new Integer(cellId))) {
                    // this is the master cell so don't allow its removal and
                    // notify user
                    invalidMsg = GuiResources.getGuiString(
                                    "config.siloExpansion.master.cell",
                                    cellIdOrderedArray[0].toString());
                }
                if (invalidMsg.length() == 0) {
                    Log.logInfoMessage(GuiResources.getGuiString(
                                        "config.siloExpansion.remove.message", 
                                                      String.valueOf(cellId)));
                    // remove the cell -- cell ID specified via dropdown
                    hostConn.delCell((Cell)cellIdToObjMap
                                                .get(new Integer(cellId)));
                    Log.logInfoMessage(GuiResources
                         .getGuiString("config.siloExpansion.success.message"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }    
            
        if (invalidMsg.length() == 0) {
            ObjectFactory.clearCache(); // Flush cache

            SwingUtilities.invokeLater(new Runnable(){
                public void run() {
                    // refresh Cells node so it either displays the newly added
                    // cell or it displays cells minus the one that was removed
                    MainFrame mainFrame = MainFrame.getMainFrame();
                    mainFrame.refreshExplorerItemData(ExplItemCells.class);
                }
            }); 
        } else {
            JOptionPane.showMessageDialog(this, 
                    invalidMsg,
                    getTitle(),
                    JOptionPane.INFORMATION_MESSAGE);
            return;               
        }
    }

    public String getPageKey() {
        return "n/a"; // HelpFileMapping.ADDREMOVECELL;
    }
    
    // ************************************ 
    
    /**
     * Disable all items 
     */
    private void disableAllItems() {
        Component [] allComponents = this.getComponents();
        for (int i = 0; i < allComponents.length; i++) {
              allComponents[i].setEnabled(false);
        }
    }

    private void handleTxtIPAddressAdminPropertyChanged(PropertyChangeEvent evt)
    {
        if (evt.getPropertyName().equals(IpAddressField.PROP_IP_ADDRESS)) {
            explItem.setIsModified(true);
        }
    }
    
    private void handleTxtIPAddressDataPropertyChanged(PropertyChangeEvent evt)
    {
        if (evt.getPropertyName().equals(IpAddressField.PROP_IP_ADDRESS)) {
            explItem.setIsModified(true);
        }
    }

    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        btnGroup = new javax.swing.ButtonGroup();
        rbtnAddCell = new javax.swing.JRadioButton();
        rbtnRemoveCell = new javax.swing.JRadioButton();
        lblAdminIP = new javax.swing.JLabel();
        lblDataIP = new javax.swing.JLabel();
        cboCells = new com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg();
        ipaAdmin = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        ipaData = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();

        rbtnAddCell.setMnemonic(GuiResources.getGuiString("config.siloExpansion.add.mn").charAt(0));
        rbtnAddCell.setText(GuiResources.getGuiString("config.siloExpansion.add"));
        rbtnAddCell.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        rbtnAddCell.setMargin(new java.awt.Insets(0, 0, 0, 0));

        rbtnRemoveCell.setMnemonic(GuiResources.getGuiString("config.siloExpansion.remove.mn").charAt(0));
        rbtnRemoveCell.setText(GuiResources.getGuiString("config.siloExpansion.remove"));
        rbtnRemoveCell.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        rbtnRemoveCell.setMargin(new java.awt.Insets(0, 0, 0, 0));

        lblAdminIP.setText(GuiResources.getGuiString("silo.adminIp"));

        lblDataIP.setText(GuiResources.getGuiString("silo.dataIp"));

        cboCells.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        ipaAdmin.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaAddressAdminPropertyChange(evt);
            }
        });

        ipaData.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaAddressDataPropertyChange(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(rbtnAddCell)
                    .add(layout.createSequentialGroup()
                        .add(17, 17, 17)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblAdminIP)
                            .add(lblDataIP))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(ipaData, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 136, Short.MAX_VALUE)
                            .add(ipaAdmin, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 136, Short.MAX_VALUE)))
                    .add(layout.createSequentialGroup()
                        .add(rbtnRemoveCell)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 173, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(124, 124, 124)
                        .add(cboCells, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 136, Short.MAX_VALUE)))
                .add(63, 63, 63))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(24, 24, 24)
                .add(rbtnAddCell)
                .add(14, 14, 14)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblAdminIP)
                    .add(ipaAdmin, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblDataIP)
                    .add(ipaData, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(32, 32, 32)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(rbtnRemoveCell)
                    .add(cboCells, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(26, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void ipaAddressDataPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_ipaAddressDataPropertyChange
        handleTxtIPAddressDataPropertyChanged(evt);
    }//GEN-LAST:event_ipaAddressDataPropertyChange

    private void ipaAddressAdminPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_ipaAddressAdminPropertyChange
        handleTxtIPAddressAdminPropertyChanged(evt);
    }//GEN-LAST:event_ipaAddressAdminPropertyChange
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup btnGroup;
    private com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg cboCells;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField ipaAdmin;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField ipaData;
    private javax.swing.JLabel lblAdminIP;
    private javax.swing.JLabel lblDataIP;
    private javax.swing.JRadioButton rbtnAddCell;
    private javax.swing.JRadioButton rbtnRemoveCell;
    // End of variables declaration//GEN-END:variables
    
}
