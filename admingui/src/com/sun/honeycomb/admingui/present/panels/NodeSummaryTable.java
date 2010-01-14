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
 * SummaryTable.java
 *
 * Created on March 29, 2006, 1:25 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Disk;
import com.sun.honeycomb.admingui.client.Fru;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.client.ServiceNode;
import com.sun.honeycomb.admingui.client.Switch;
import com.sun.honeycomb.common.CliConstants;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.MultiLineTableHeaderRenderer;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.utility.Picture;
import com.sun.nws.mozart.ui.utility.ToolBox;
import com.sun.nws.mozart.ui.swingextensions.IconTextTableCellRenderer;
import com.sun.nws.mozart.ui.swingextensions.MultiIconTableCellRenderer;
import com.sun.nws.mozart.ui.swingextensions.ProgressBarTableCellRenderer;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.UIManager;

import com.sun.honeycomb.admingui.client.Versions;

/**
 *
 * @author dp127224
 */
public class NodeSummaryTable extends JScrollPane {

    // Icons
    private static final String ONLINE_STATUS_IMG = "traffic_green.jpg";
    private static final String OFFLINE_STATUS_IMG = "traffic_red.jpg";
    
    // Different images are used for each disk because each disk will have
    // has a different tooltip
    private static Picture onlinePic = null;
    private static Picture offlinePic = null;

    // Tooltips won't vary from node to node, 
    // so we can use the same image objects
    private static ImageIcon nodeOnlineImg = null;
    private static ImageIcon nodeOfflineImg = null;
     
    private static int tallestCellHeight = 0;
    
    static {
        // Load the icons.  For checkmark and xmark, keep in Picture object
        // so we can easily copy ImageIcon objects.
        try {
            // Get online pics
            onlinePic = new Picture();
            onlinePic.load(ONLINE_STATUS_IMG, null);
            nodeOnlineImg = onlinePic.getImage();
            tallestCellHeight = nodeOnlineImg.getIconHeight();
            
            // Get offline pics
            offlinePic = new Picture();
            offlinePic.load(OFFLINE_STATUS_IMG, null);
            nodeOfflineImg = offlinePic.getImage();
            tallestCellHeight = 
                    Math.max(tallestCellHeight, nodeOfflineImg.getIconHeight());

            // Account for hight in MultiIcon table cell renderer
            tallestCellHeight = Math.max(tallestCellHeight, 
                                   MultiIconTableCellRenderer.calculateHeight(
                                        nodeOnlineImg.getIconHeight()));
            tallestCellHeight = Math.max(tallestCellHeight, 
                                   MultiIconTableCellRenderer.calculateHeight(
                                        nodeOfflineImg.getIconHeight()));
        } catch (UIException e) {
            Log.logAndDisplayException(e);
        }
    }
    
    private JTable theTable;
    private int numNodes = -1;
    private int cellId = -1;
    
    private float rollupCapacity = 0;
    private float rollupUsedCapacity = 0;
    
    private String[] biosVer = null;
    private String[] smdcVer = null;
    private Versions versions = null;
    
    
    /** 
     * Creates a new instance of SummaryTable
     *
     */
    public NodeSummaryTable() throws UIException {
        theTable = new JTable();
        
        setViewportView(theTable);
        Dimension dim = theTable.getPreferredScrollableViewportSize();
        dim.width = 350;
        NodeSummaryTableModel model = new NodeSummaryTableModel();
        ToolBox.setupJTable(theTable, this, model);

        // If number of rows in the scroll pane's table is small, the viewport
        // is visible underneath the last row.  Set the viewport to gray since
        // the above setupJTable call sets it to white (table background)
        getViewport().setBackground(
                    UIManager.getDefaults().getColor("Button.background"));

        TableColumnModel colModel = theTable.getColumnModel();
        
        // Use multi-line headers
        Enumeration cols = colModel.getColumns();
        while (cols.hasMoreElements()) {
            ((TableColumn)cols.nextElement())
                              .setHeaderRenderer(
                                        new MultiLineTableHeaderRenderer());
        }
        
        // Make capacity used column show progress bar
        colModel.getColumn(model.PERCENT_USED)
                .setCellRenderer(new ProgressBarTableCellRenderer());
     
        theTable.setRowHeight(tallestCellHeight);
        
        // Show an empty table initially
        int numRows = AdminApi.FULL_CELL_NODES;
        int padding = (tallestCellHeight - numRows) / numRows;
        int tableHeight = (tallestCellHeight + padding) * numRows;
        Dimension d = new Dimension(dim.width, tableHeight);
        theTable.setPreferredScrollableViewportSize(d);
    }

    /**
     * Returns the real table on the viewport view
     */
    public JTable getJTable() {
        return theTable;
    }
    
    /**
     * Returns the number of nodes in the cell
     */
    public int getNumNodes() {
        return numNodes;
    }
    
    public float getRollupCapacity() { return rollupCapacity; }
    public float getRollupUsedCapacity() { return rollupUsedCapacity; }
    
    /**
     * Populates the table for a given cell.
     */
    public void populate(Cell theCell) 
    throws ClientException, ServerException, UIException {
                
        AdminApi adminApi = ObjectFactory.getHostConnection();
        
        NodeSummaryTableModel model = 
                (NodeSummaryTableModel) theTable.getModel();
        model.removeAll();
        theTable.setOpaque(true);

        if (theCell == null) {
            Log.logAndDisplayInfoMessage(
                    GuiResources.getMsgString("info.noCells"));
            return;
        }
        
        if (!theCell.isAlive()) {
            // show empty table if cell is not alive
            theTable.setOpaque(false);
            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                    GuiResources.getGuiString("cell.operation.down.error", 
                    String.valueOf(theCell.getID())), null);
            return;
        }
        ArrayList fruObjs = new ArrayList();
        ArrayList biosSmdcInfo = new ArrayList();
        ArrayList percentUsedInfo = new ArrayList();
        rollupCapacity = 0;
        rollupUsedCapacity = 0;
        String cellId = String.valueOf(theCell.getID());

        // Get nodes from cell object
        Node[] nodes = adminApi.getNodes(theCell);
        
        // Set the number of nodes value for this table 
        numNodes = nodes.length;
        int padding = (tallestCellHeight - numNodes) / numNodes;
        theTable.setRowHeight(tallestCellHeight + padding);
        if (nodes == null) {
            Log.logAndDisplayInfoMessage(
                    GuiResources.getMsgString(
                        "info.noNodesOnCell", cellId));
            return;
        }
            
        // Firmware Versions info
        PnlFWVersions vHelper = new PnlFWVersions(theCell);
        String biosAndSmdc = "";
        
        for (int j = 0; j < numNodes; j++) {
            Node theNode = nodes[j];    
            fruObjs.add(theNode);
            
            Integer pcntUsedCapacity = null;
            ImageIcon[] diskIcons = null;
            biosAndSmdc = vHelper.getNodeBios(theNode)+"/"+
                                            vHelper.getNodeSmdc(theNode);
            
            // If the node is not alive, then skip disk stuff
            if (theNode.isAlive()) {
                // Calculate capacity percent used rollup for node disks
                Disk[] disks = theNode.getDisks();
                if (disks == null) {
                    Log.logAndDisplayInfoMessage(
                            GuiResources.getMsgString("info.noDisksOnNode", 
                            theNode.getID()));
                    continue;
                }
                float totalCapacity = 0;
                float usedCapacity = 0;
                // Disk status column takes an array of ImageIcon objects,
                // one for each disk in the node.
                diskIcons = new ImageIcon[disks.length];
                for (int k = 0; k < disks.length; k++) {
                    Disk theDisk = disks[k];
                    totalCapacity += theDisk.getCapTotal();
                    usedCapacity += theDisk.getCapUsed();
                    String tooltip;
                    ImageIcon img;
                    if (theDisk.getStatus() == Disk.ENABLED) {
                        tooltip = GuiResources.getGuiString(
                                "summaryTable.diskStatus.enabledTooltip", 
                                theDisk.getDiskId());
                        img = onlinePic.getImageCopy();
                    } else {
                        tooltip = GuiResources.getGuiString(
                                "summaryTable.diskStatus.disabledTooltip", 
                                theDisk.getDiskId());
                        img = offlinePic.getImageCopy();
                    }
                    img.setDescription(tooltip);
                    diskIcons[k] = img;
                } // end of disk iteration
                rollupCapacity += totalCapacity;
                rollupUsedCapacity += usedCapacity;
                pcntUsedCapacity = new Integer(ToolBox.calcPcntUsedCapacity(
                                                            usedCapacity, 
                                                            totalCapacity));
                biosSmdcInfo.add(biosAndSmdc);
                percentUsedInfo.add(pcntUsedCapacity);
            } else {
                biosSmdcInfo.add("");
                percentUsedInfo.add(new Integer(0));
            }
            
        } // end iterate through nodes

        // Get switches and SP from the cell object
        ServiceNode sn = adminApi.getSp(theCell);
        if (sn == null) {
            sn = new ServiceNode(adminApi, theCell, 
                    GuiResources.getGuiString("service.node.id"), false, 
                        Fru.STATUS_UNAVAILABLE, "");
        }
        fruObjs.add(sn);
        Switch[] switches = adminApi.getSwitches(theCell);
        for (int idx = 0; idx < switches.length; idx++) {
            fruObjs.add(switches[idx]);
        }
        
        Iterator iter = fruObjs.iterator();
        Object[] data;
        while (iter.hasNext()) {
            Fru f = (Fru)iter.next();
            String version = "N/A";
            String fruID = f.getID();
            int idx = -1;
            if (f.getType() == Fru.TYPE_NODE) {
                idx = ((Node)f).getNodeID() - 101;
                version = (String)biosSmdcInfo.get(idx);
                fruID = "NODE-" + fruID;
            } else if (f.getType() == Fru.TYPE_SWITCH) {
                version = ((Switch)f).getVersion();
            } else if (f.getType() == Fru.TYPE_SP) {
                PnlFWVersions pnlVersions = new PnlFWVersions(theCell);
                version = pnlVersions.getSPBiosVersion() + "/" + 
                                pnlVersions.getSPSmdcVersion();
            }
            data = new Object[] {
                fruID,
                Fru.getStatusAsString(f.getType(), f.getStatus()),
                f.getType() == Fru.TYPE_NODE ?
                    percentUsedInfo.get(idx) :
                    null,
                version,
                f.getFRU()
            };
            model.addRow(data);

        }

        // Set viewport view so that it just fits around
        ToolBox.shrinkWrapTableViewport(theTable);       
    }
    
}
