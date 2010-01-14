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
 * PanelAdmin.java
 *
 * Created on February 9, 2006, 10:54 AM
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Disk;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.CacheOperations;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.honeycomb.admingui.present.panels.CenteredRenderer;
import java.awt.Component;
import javax.swing.JPanel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 *
 * @author  dp127224
 */
public class PnlCellDisks extends JPanel implements ContentPanel {

    private DiskSummaryTable tablSummary;
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    
    public PnlCellDisks() {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {
        tablSummary = new DiskSummaryTable(DiskSummaryTable.TYPE_CELL);
        pnlSummaryTable.add(tablSummary);
        
        DiskSummaryTableModel model = (DiskSummaryTableModel) 
                                        tablSummary.getTable().getModel();
        
        // Model listener
        model.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                explItem.setIsModified(true);
            }
        });
        
        // Centering the contents of Disk Summary Table
        tablSummary.getTable().getColumnModel().getColumn(model.NODE_ID)
                                    .setCellRenderer(new CenteredRenderer());
        
        tablSummary.getTable().getColumnModel().getColumn(model.DISK_ID)
                                    .setCellRenderer(new CenteredRenderer());
        
        tablSummary.getTable().getColumnModel().getColumn(model.TOTAL_CAPACITY)
                                    .setCellRenderer(new CenteredRenderer());  
        
    }
    
    // *****************************************
    // ContentPanel Impl
    
    public String getTitle() {
        Cell theCell = (Cell) explItem.getPanelPrimerData();
        ObjectFactory.setLastCellId(theCell.getID());
        String id = Integer.toString(theCell.getID());
        return GuiResources.getGuiString("cell.disks.title", id);
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_BOTH; }
    public ButtonPanel getButtonPanel() {
// THIS ALLOWS THE DISKS TO BE DISABLED/ENABLED...once roles are defined in 1.2  
//        ButtonPanel bPanel = null;
//        if (ObjectFactory.isAdmin()) {
//            bPanel = BtnPnlApplyCancel.getButtonPanel();
//        } else {
//            bPanel = BtnPnlBlank.getButtonPanel();
//        }
//        return bPanel;
        return BtnPnlBlank.getButtonPanel();
    }
    
    public void loadValues() throws UIException, HostException {
        try {
            Cell theCell = (Cell) explItem.getPanelPrimerData();
            tablSummary.populate(theCell);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        tablSummary.getTable().setEnabled(ObjectFactory.isAdmin());
    }
    
    public void saveValues() throws HostException {
        try {
            Cell theCell = (Cell) explItem.getPanelPrimerData();
            DiskSummaryTableModel model = (DiskSummaryTableModel) 
                                            tablSummary.getTable().getModel();
            int cnt = model.getRowCount();
            int curNodeId = -1;
            Node theNode = null;
            for (int i = 0; i < cnt; i++)
            {
                // Get the node
                String id = (String) model.getValueAt(i, model.NODE_ID);
                int nodeId = Integer.parseInt(id);
                if (nodeId != curNodeId) {
                    theNode = hostConn.getNode(theCell, nodeId);
                    curNodeId = nodeId;
                }
                Disk theDisk = theNode.getDisk(
                                (String) model.getValueAt(i, model.DISK_ID));
                
                if (model.getValueAt(i, model.ENABLED) != null){
                    if (((Boolean)model.getValueAt(
                                            i, model.ENABLED)).booleanValue()) {
                        // Should be enabled
                        if (theDisk.getStatus() != Disk.ENABLED) {
                            theDisk.enable();
                        }
                    } else {
                        // Should be disabled
                        if (theDisk.getStatus() != Disk.DISABLED) {
                            theDisk.disable();
                        }
                    }
                }
            }
            
            Log.logInfoMessage(GuiResources.getGuiString("cell.disks.saved"));
            
            // Flush cache
            ObjectFactory.clearCache();
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }   
    }

    public String getPageKey() {
        return HelpFileMapping.DISKSTATUS;
    }

    //
    // *****************************************

    /** 
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        pnlSummaryTable = new javax.swing.JPanel();

        pnlSummaryTable.setLayout(new java.awt.BorderLayout());

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(pnlSummaryTable, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 512, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(pnlSummaryTable, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 294, Short.MAX_VALUE)
                .addContainerGap())
        );
    }//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel pnlSummaryTable;
    // End of variables declaration//GEN-END:variables
    
}
