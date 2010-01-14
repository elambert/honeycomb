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
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemSilo;
import com.sun.nws.mozart.ui.utility.CapacityValue;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.utility.ToolBox;
import com.sun.nws.mozart.ui.MainFrame;
import java.util.logging.Level;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 *
 * @author dp127224
 */
public class DiskSummaryTable extends JScrollPane {
    
    public static final int TYPE_CELL = 0;
    public static final int TYPE_NODE = 1;

    private JTable theTable;
    private int tableType;
    
    private float rollupCapacity = 0;
    private float rollupUsedCapacity = 0;
    
        
    /** 
     * @param tableType Use TYPE_CELL for a disk summary table for a cell.  
     * This will include a node column.  Use TYPE_NODE for a disk summary table
     * for a single node.  This will not  include a node column.
     *
     */
    public DiskSummaryTable(int tableType) {
        this.tableType = tableType;
        
        theTable = new JTable();
        setViewportView(theTable);
        DiskSummaryTableModel model = new DiskSummaryTableModel(tableType) {
            
            public boolean isCellEditable(int row, int column) {                
                Object obj = this.getValueAt(row, column);
                if (obj == null)
                    return false;
                else 
                    return super.isCellEditable(row, column);                
            }                
        };

        ToolBox.setupJTable(theTable, this, model);

        // If number of rows in the scroll pane's table is small, the viewport
        // is visible underneath the last row.  Set the viewport to gray since
        // the above setupJTable call sets it to white (table background)
        getViewport().setBackground(
                    UIManager.getDefaults().getColor("Button.background"));
        
        // Make capacity used column show progress bar
        theTable.getColumnModel()
                .getColumn(model.PERCENT_USED)
                .setCellRenderer(new CustomProgressBarTableCellRenderer(
                GuiResources.getMsgString("info.offlineNode")));
        
        // Make "enabled" column to show checkbox if node is alive, otherwise 
        // make it as label to display info on that cell if required
        theTable.getColumnModel()
            .getColumn(model.ENABLED)
            .setCellRenderer(new CheckBoxTableCellRenderer());  
        
    }
    
    public JTable getTable() { return theTable; }
    public float getRollupCapacity() { return rollupCapacity; }
    public float getRollupUsedCapacity() { return rollupUsedCapacity; }

    /**
     * Populates the table.
     *
     */
    public void populate(Cell theCell) throws ClientException, ServerException {
        populate(theCell, null);
    }
    
    public void populate(Node theNode) throws ClientException, ServerException {
        populate(null, theNode);
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
    private void populate(Cell theCell, Node theNode) 
    throws ClientException, ServerException {
        if (ObjectFactory.isGetCellsError()) {
            showHomePage();
            return;
        }
        Cell c = null;
        Cell[] cells = null;
        AdminApi hostConn = ObjectFactory.getHostConnection();
        DiskSummaryTableModel model = 
                (DiskSummaryTableModel) theTable.getModel();
        model.removeAll();
        theTable.setOpaque(true);
        try {
            cells = hostConn.getCells();
        } catch (Exception e) {
            showHomePage();
            return;
        }
        if (theCell != null) {
            // Cell object from panel primer data is not updated -- thus, need
            // to use recent cell data to determine logic
            c = this.findCell(cells, theCell.getID());
        } else {
            c = this.findCell(cells, theNode.getCell().getID());
        }
        if (c != null && !c.isAlive()) {
            theTable.setOpaque(false);
            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                GuiResources.getGuiString("cell.operation.down.error", 
                String.valueOf(c.getID())), null);
            return;
        }
        
        // TODO Use the cell object to get the nodes
        Node[] nodes;
        if (tableType == TYPE_CELL) {
            nodes = hostConn.getNodes(c);
        } else {
            nodes = new Node[] { theNode };
        }
        if (nodes == null) {
            Log.logAndDisplayInfoMessage(
                    GuiResources.getMsgString("info.noNodes"));
            return;
        }
        
        
        rollupCapacity = 0;
        rollupUsedCapacity = 0;       
        
        // Firmware Versions info        
        if(c == null && theNode != null){
            c = theNode.getCell();            
        }
        
        CapacityValue capVal = new CapacityValue();
        for (int i = 0; i < nodes.length; i++) {
            Node aNode = nodes[i];                        
            String strNodeId = aNode.getID();  
            if (!aNode.isAlive()) {
                Object[] offlineNode;
                offlineNode = new Object[] {
                    strNodeId,
                    null,null,null,null                    
                };
                model.addRow(offlineNode);
                continue;
            }

            // Calculate capacity percent used rollup for node disks
            Disk[] disks = aNode.getDisks();
            if (disks == null) {
                Log.logAndDisplayInfoMessage(
                        GuiResources.getMsgString("info.noDisksOnNode", 
                        aNode.getID()));
                continue;
            }            
            
           // String strNodeId = aNode.getID();
            for (int j = 0; j < disks.length; j++) {
                Disk theDisk = disks[j];
                float totalCapacity = theDisk.getCapTotal();
                float usedCapacity = theDisk.getCapUsed();
                rollupCapacity += totalCapacity;
                rollupUsedCapacity += usedCapacity;
                capVal.setValue(totalCapacity, ObjectFactory.CAPACITY_UNIT);  
            
                Object[] data;
                if (tableType == TYPE_CELL) {
                    data = new Object[] {
                        strNodeId,
                        theDisk.getDiskId(),
                        (theDisk.getStatus() == Disk.ENABLED)
                            ? new Boolean(true)
                            : new Boolean(false),
                        (theDisk.getStatus() == Disk.ENABLED)
                            ? capVal.getDisplayBestUnitsDecimal(2)
                            : null,
                        (theDisk.getStatus() == Disk.ENABLED)
                            ? (Object)new Integer(
                                ToolBox.calcPcntUsedCapacity(
                                                    usedCapacity, 
                                                    totalCapacity))
                            : (Object)CustomProgressBarTableCellRenderer
                                                                    .EMPTY_STR,
                        theDisk.getFRU()
                    };
                } else {
                    data = new Object[] {
                        theDisk.getDiskId(),                                    
                        (theDisk.getStatus() == Disk.ENABLED)
                            ? new Boolean(true)
                            : new Boolean(false),
                        (theDisk.getStatus() == Disk.ENABLED)
                            ? capVal.getDisplayBestUnitsDecimal(2)
                            : null,
                        (theDisk.getStatus() == Disk.ENABLED)
                            ? (Object)new Integer(
                                ToolBox.calcPcntUsedCapacity(
                                                    usedCapacity, 
                                                    totalCapacity))
                            : (Object)CustomProgressBarTableCellRenderer
                                                        .EMPTY_STR,
                        theDisk.getFRU()
                    };
                }
                model.addRow(data);
            }
        }        
        // Set viewport view so that it just fits around
        ToolBox.shrinkWrapTableViewport(theTable);        
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

}
