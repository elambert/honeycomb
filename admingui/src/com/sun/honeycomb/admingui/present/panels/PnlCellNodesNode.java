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



/*
 * PanelSilo.java
 *
 * Created on February 8, 2006, 10:54 AM
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Disk;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.client.Versions;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemSilo;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg;
import com.sun.nws.mozart.ui.utility.CacheOperations;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import java.util.Arrays;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import java.util.HashMap;
import java.util.List;

/**
 *
 * @author  dp127224
 */
public class PnlCellNodesNode extends JPanel implements ContentPanel {
    
    private static boolean isShowingMoreDetails = false;

    private DiskSummaryTable tablDisksSummary;
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    
    private Versions versions = null;
    private String biosVer = "";
    private String smdcVer = "";
    
    public PnlCellNodesNode() {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {
        
        // Remove default grey border
        setBorder(null);

        tablDisksSummary = new DiskSummaryTable(DiskSummaryTable.TYPE_NODE);
        pnlDisksTable.add(tablDisksSummary);
                
        DiskSummaryTableModel model = (DiskSummaryTableModel) 
                                        tablDisksSummary.getTable().getModel();
        
        // Model listener
        model.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                explItem.setIsModified(true);
            }
        });
       
        // Centering the contents of Node Summary Table
        tablDisksSummary.getTable().getColumnModel().getColumn(model.DISK_ID)
            .setCellRenderer(new CenteredRenderer());
        
        tablDisksSummary.getTable().getColumnModel()
                            .getColumn(model.TOTAL_CAPACITY)
                                .setCellRenderer(new CenteredRenderer());
        
//        // Setup and hide services
//        ToolBox.setupJTable(tablServices, scrlServices, 
//                            new PnlCellNodesNodeServicesTableModel());
//        showHideDetails(isShowingMoreDetails); 

        
// Running mode combo box for 1.1 is going to be hidden         
        // Running combo
        cboRunningMode.setVisible(false);
        lblRunningMode.setVisible(false);
//        cboRunningMode.setMessage(
//                GuiResources.getGuiString("cell.node.runningMode.unselected"));
//        // WIDTH_BY_BOTH is more expensive, but the number of cbo items is small
//        // so its ok.
//        cboRunningMode.setWidthBy(JComboBoxWithDefaultMsg.WIDTH_BY_BOTH);

    }
    
    // *******************************************
    // ContentPanel Impl
    
    public String getTitle() {
        Node theNode = (Node) explItem.getPanelPrimerData();
        ObjectFactory.setLastCellId(theNode.getCell().getID());
        String id = theNode.getID();
        String status = theNode.isAlive() 
                            ? GuiResources.getGuiString("common.online")
                            : GuiResources.getGuiString("common.offline");
        return GuiResources.getGuiString("cell.node.title", 
                                         new String[] {id, status});
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_NORTHWEST; }    
    public int getFillType() { return SizeToFitLayout.FILL_BOTH; }
    public ButtonPanel getButtonPanel() {
        return BtnPnlBlank.getButtonPanel();
    }
    private void showHomePage() {
        SwingUtilities.invokeLater(new Runnable(){
            public void run() {
                try{
                    ExplItemSilo homePage = 
                                new ExplItemSilo(ObjectFactory.isAdmin());
                    MainFrame.getMainFrame().selectExplorerItem(homePage);
                    Log.logToStatusAreaAndExternal(Level.SEVERE,
                        GuiResources.getGuiString("silo.cell.error"), null);
                } catch (Exception e) {}
            }
        });
    }

    public void loadValues() throws UIException, HostException {
        try {
            if (ObjectFactory.isGetCellsError()) {
                showHomePage();
                return;
            }
            lblIdValue.setText("");
            lblBiosVer.setText("");
            lblSmdcVer.setText("");
            lblFRUValue.setText("");
            Node theNode = (Node) explItem.getPanelPrimerData();            
            lblIdValue.setText(theNode.getID());
            theNode.refresh();       
            lblFRUValue.setText(theNode.getFRU());
                        
            // Removing the Services Panels coz it is not 
            // being supported in this version            
            pnlServices.setVisible(false);
            btnShowHideAdv.setVisible(false);
        
            // Disks table
            tablDisksSummary.populate(theNode);
            
            // Firmware Versions stuff
            loadVersionInfo(theNode);            
            if (theNode.isAlive()) {
                lblBiosVer.setText(biosVer);            
                lblSmdcVer.setText(smdcVer);               
            } else {
                lblBios.setVisible(false);
                lblBiosVer.setVisible(false);
                lblSmdc.setVisible(false);
                lblSmdcVer.setVisible(false);
            }
            
// Running mode combo box for 1.1 is going to be hidden            
//            // Running mode combo
//            cboRunningMode.removeAllItems();
//
//            if (theNode.isAlive()) {
//                cboRunningMode.addItem(GuiResources.getGuiString(
//                        "cell.node.runningMode.unselected"));
//                cboRunningMode.addItem(GuiResources.getGuiString(
//                        "cell.node.runningMode.restart"));
//                cboRunningMode.addItem(GuiResources.getGuiString(
//                        "cell.node.runningMode.powerdown"));
//            } else {
//                cboRunningMode.addItem(GuiResources.getGuiString(
//                        "cell.node.runningMode.unselected"));                
//                cboRunningMode.addItem(GuiResources.getGuiString(
//                        "cell.node.runningMode.powerup"));
//            }        
//
//            cboRunningMode.setSelectedIndex(-1);
            
//            // Services table
//            Service[] services = hostConn.getServices(theNode);
//            PnlCellNodesNodeServicesTableModel model = 
//                   (PnlCellNodesNodeServicesTableModel) tablServices.getModel();
//            model.removeAll();
//            if (theNode.isAlive() && services != null && services.length > 0) {
//                for (int i = 0; i < services.length; i++) {
//                    Service service = services[i];
//                    model.addRow(new Object[] {
//                        service.getName(),
//                        service.isRunning() 
//                            ? GuiResources.getGuiString(
//                                "cell.node.services.state.running")
//                            : GuiResources.getGuiString(
//                                "cell.node.services.state.notRunning")
//                    });
//                }
//            }
//            showHideDetails(isShowingMoreDetails);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // disable running mode drop down if user is in read only
        if (!ObjectFactory.isAdmin()) {
            cboRunningMode.setEnabled(false);
            tablDisksSummary.getTable().setEnabled(false);
        }
    }
    
    public void saveValues() throws HostException, UIException {
        try {            
            Node theNode = (Node) explItem.getPanelPrimerData();
            
//////////////////     Enable / disable disks    /////////////////////////////
            // only do the following if the NODE itself is enabled
            if (theNode.isAlive()) {
                DiskSummaryTableModel model = 
                 (DiskSummaryTableModel) tablDisksSummary.getTable().getModel();
                Disk[] disks = theNode.getDisks();
                int cnt = model.getRowCount();
                boolean changed = false;
                for (int i = 0; i < cnt; i++)
                {
                    Disk theDisk = theNode.getDisk(
                            (String) model.getValueAt(i, model.DISK_ID));
                    if (((Boolean)model.getValueAt(i, model.ENABLED))
                                                            .booleanValue()) {
                        // Should be enabled                   
                        if (theDisk.getStatus() != Disk.ENABLED) {
                            theDisk.enable();
                            changed = true;
                        }
                    } else {
                        // Should be disabled
                        if (theDisk.getStatus() != Disk.DISABLED) {
                            theDisk.disable();
                            changed = true;
                        }
                    }
                }   

                if (changed) {
                    Log.logInfoMessage(
                            GuiResources.getGuiString("cell.node.saved"));
                }
                // Flush cache
                ObjectFactory.clearCache();
            }
            
            
//////////////////     Reboot, shutdown or power up node     /////////////////
            
            
// Running mode combo box for 1.1 is going to be hidden             
//            String runningMode = (String) cboRunningMode.getSelectedItem();
//            if (runningMode == null) {
//                return;
//            }
//
//            String confirm;
//            if (runningMode.equals(
//                GuiResources.getGuiString("cell.node.runningMode.powerdown"))) {
//                confirm = GuiResources.getGuiString(
//                                    "cell.node.runningMode.powerdown.confirm", 
//                                    theNode.getID());
//            } else if (runningMode.equals(GuiResources.getGuiString(
//                                        "cell.node.runningMode.powerup"))) {
//                confirm = GuiResources.getGuiString(
//                                        "cell.node.runningMode.powerup.confirm",
//                                        theNode.getID());
//            } else {
//                confirm = GuiResources.getGuiString(
//                                        "cell.node.runningMode.restart.confirm",
//                                        theNode.getID());
//            }
//            int retVal = JOptionPane.showConfirmDialog(MainFrame.getMainFrame(),
//                                       confirm,
//                                       GuiResources.getGuiString("app.name"),
//                                       JOptionPane.YES_NO_OPTION);
//            if (retVal == JOptionPane.YES_OPTION) {
//                String submitted;
//                if (runningMode.equals(GuiResources.getGuiString(
//                                        "cell.node.runningMode.powerdown"))) {
//                    hostConn.powerNodeOff(theNode);
//                    submitted = GuiResources.getGuiString(
//                                "cell.node.runningMode.powerdown.submitted",
//                                theNode.getID());
//                } else if (runningMode.equals(GuiResources.getGuiString(
//                                            "cell.node.runningMode.powerup"))) {
//                           hostConn.powerNodeOn(theNode);
//                           submitted = GuiResources.getGuiString(
//                                    "cell.node.runningMode.powerup.submitted",
//                                    theNode.getID());
//                } else {
//                    hostConn.rebootNode(theNode);
//                    submitted = GuiResources.getGuiString(
//                                    "cell.node.runningMode.restart.submitted",
//                                    theNode.getID());
//                }
//             // Log.logAndDisplayInfoMessage(submitted);
//                Log.logInfoMessage(submitted);                            
//                // Flush cache
//                ObjectFactory.clearCache();
//            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }   
    }

    public String getPageKey() {
        return HelpFileMapping.NODESTATUS;
    }

    //
    // *******************************************
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
    private void loadVersionInfo(Node node)
                throws ClientException, ServerException{
        // Cell object from panel primer data is not updated -- thus, need
        // to use recent cell data to determine logic
        Cell[] cells = null;
        try {
            cells = hostConn.getCells();
        } catch (Exception e) {
            showHomePage();
            return;
        }
        Cell theCell = this.findCell(cells, node.getCell().getID());
        if (!theCell.isAlive()) {
            lblFRUValue.setText(
                        GuiResources.getGuiString("cellProps.unavailable"));
            biosVer = GuiResources.getGuiString("cellProps.unavailable");
            smdcVer = GuiResources.getGuiString("cellProps.unavailable");
            return;
        }
        versions = hostConn.getFwVersions(theCell);        
        String[] biosArray = versions.getBios();
        String[] smdcArray = versions.getSmdc(); 
        Node[] nodes = hostConn.getNodes(theCell);
        
        // assumption is that the order of the array is preserved in the list
        List nodeList = Arrays.asList(nodes);
        int versionIndex = nodeList.indexOf(node);
        if (versionIndex != -1) {
            // found node -- use the same index to retrieve bios and smdc info
            biosVer = biosArray.length == 0 ? 
                        GuiResources.getGuiString("cellProps.unavailable") : 
                                                        biosArray[versionIndex];
            smdcVer = smdcArray.length == 0 ?
                        GuiResources.getGuiString("cellProps.unavailable") : 
                                                        smdcArray[versionIndex];
        }
    }
    
//    private void showHideDetails(boolean show) {
//        isShowingMoreDetails = show;
//        if (show) {
//            btnShowHideAdv.setText(
//                    GuiResources.getGuiString("cell.node.fewerDetails"));
//            btnShowHideAdv.setMnemonic(
//                    GuiResources.getGuiChar("cell.node.fewerDetails.mn"));
//            pnlServices.setVisible(true);
//        } else {
//            btnShowHideAdv.setText(
//                    GuiResources.getGuiString("cell.node.moreDetails"));
//            btnShowHideAdv.setMnemonic(
//                    GuiResources.getGuiChar("cell.node.moreDetails.mn"));
//            pnlServices.setVisible(false);
//        }
//    }
//    
//    private void handleBtnShowHideAdvActionPerformed(ActionEvent evt) {
//        showHideDetails(!isShowingMoreDetails);
//    } 
    
    /** 
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        buttonGroup1 = new javax.swing.ButtonGroup();
        lblDisks = new javax.swing.JLabel();
        lblRunningMode = new javax.swing.JLabel();
        pnlDisksTable = new javax.swing.JPanel();
        pnlServices = new javax.swing.JPanel();
        scrlServices = new javax.swing.JScrollPane();
        tablServices = new javax.swing.JTable();
        btnShowHideAdv = new javax.swing.JButton();
        cboRunningMode = new com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg();
        lblId = new javax.swing.JLabel();
        lblIdValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        lblBios = new javax.swing.JLabel();
        lblBiosVer = new javax.swing.JLabel();
        lblSmdc = new javax.swing.JLabel();
        lblSmdcVer = new javax.swing.JLabel();
        lblFRU = new javax.swing.JLabel();
        lblFRUValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();

        lblDisks.setDisplayedMnemonic(GuiResources.getGuiString("cell.node.disks.mn").charAt(0));
        lblDisks.setLabelFor(pnlDisksTable);
        lblDisks.setText(GuiResources.getGuiString("cell.node.disks"));

        lblRunningMode.setDisplayedMnemonic(GuiResources.getGuiString("cell.runningMode.mn").charAt(0));
        lblRunningMode.setLabelFor(cboRunningMode);
        lblRunningMode.setText(GuiResources.getGuiString("cell.node.changeRunningMode"));

        pnlDisksTable.setLayout(new java.awt.BorderLayout());

        pnlServices.setBorder(javax.swing.BorderFactory.createTitledBorder(GuiResources.getGuiString("cell.node.services")));
        tablServices.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        scrlServices.setViewportView(tablServices);

        org.jdesktop.layout.GroupLayout pnlServicesLayout = new org.jdesktop.layout.GroupLayout(pnlServices);
        pnlServices.setLayout(pnlServicesLayout);
        pnlServicesLayout.setHorizontalGroup(
            pnlServicesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlServicesLayout.createSequentialGroup()
                .addContainerGap()
                .add(scrlServices, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 489, Short.MAX_VALUE)
                .addContainerGap())
        );
        pnlServicesLayout.setVerticalGroup(
            pnlServicesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlServicesLayout.createSequentialGroup()
                .addContainerGap()
                .add(scrlServices, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 92, Short.MAX_VALUE)
                .addContainerGap())
        );

        btnShowHideAdv.setText("Show/Hide Details");
        btnShowHideAdv.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowHideAdvActionPerformed(evt);
            }
        });

        cboRunningMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboRunningModeActionPerformed(evt);
            }
        });

        lblId.setText(GuiResources.getGuiString("node.id"));

        lblIdValue.setText("jLabelData1");

        lblBios.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.node.biosVersion"));

        lblBiosVer.setText("Bios Version");

        lblSmdc.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.node.smdcVersion"));

        lblSmdcVer.setText("Smdc Version");

        lblFRU.setText(GuiResources.getGuiString("cell.node.fruId"));

        lblFRUValue.setText("fru identifier");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(pnlDisksTable, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 523, Short.MAX_VALUE)
                    .add(pnlServices, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(btnShowHideAdv)
                    .add(lblDisks)
                    .add(layout.createSequentialGroup()
                        .add(lblRunningMode)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(cboRunningMode, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblBios)
                            .add(lblSmdc))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblSmdcVer)
                            .add(lblBiosVer)))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblId)
                            .add(lblFRU))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblFRUValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(lblIdValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblId)
                    .add(lblIdValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblFRU)
                    .add(lblFRUValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblRunningMode)
                    .add(cboRunningMode, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(lblDisks)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlDisksTable, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 90, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(15, 15, 15)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblBios)
                    .add(lblBiosVer))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblSmdc)
                    .add(lblSmdcVer))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(pnlServices, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(btnShowHideAdv)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void cboRunningModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboRunningModeActionPerformed
       
         if(cboRunningMode.getSelectedIndex() == 0){
           explItem.setIsModified(false);
         }
         else if (explItem != null) {
            explItem.setIsModified(true);
        }
    }//GEN-LAST:event_cboRunningModeActionPerformed

    private void btnShowHideAdvActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowHideAdvActionPerformed
      //  handleBtnShowHideAdvActionPerformed(evt);
    }//GEN-LAST:event_btnShowHideAdvActionPerformed
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnShowHideAdv;
    private javax.swing.ButtonGroup buttonGroup1;
    private com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg cboRunningMode;
    private javax.swing.JLabel lblBios;
    private javax.swing.JLabel lblBiosVer;
    private javax.swing.JLabel lblDisks;
    private javax.swing.JLabel lblFRU;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblFRUValue;
    private javax.swing.JLabel lblId;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblIdValue;
    private javax.swing.JLabel lblRunningMode;
    private javax.swing.JLabel lblSmdc;
    private javax.swing.JLabel lblSmdcVer;
    private javax.swing.JPanel pnlDisksTable;
    private javax.swing.JPanel pnlServices;
    private javax.swing.JScrollPane scrlServices;
    private javax.swing.JTable tablServices;
    // End of variables declaration//GEN-END:variables
    
}
