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
 * ContentsTableModel.java
 *
 * Created on December 22, 2005, 2:52 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Random;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
//import jj2000.j2k.util.MathUtil;

/**
 *
 * @author dp127224
 */
public class DiskSummaryTableModel extends BaseTableModel {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public final int NODE_ID;
    public final int DISK_ID;
    public final int ENABLED;
    public final int TOTAL_CAPACITY;
    public final int PERCENT_USED;
    public final int DISK_FRU_ID;
    

    
    /** 
     * @param tableType Use TYPE_CELL for a disk summary table for a cell.  
     * This will include a node column.  Use TYPE_NODE for a disk summary table
     * for a single node.  This will not  include a node column.
     */
    public DiskSummaryTableModel(int tableType) {
        super();
        
        switch (tableType) {
            case DiskSummaryTable.TYPE_NODE:
                NODE_ID = -1;
                DISK_ID = 0;                
                ENABLED = 1;
                TOTAL_CAPACITY = 2;
                PERCENT_USED = 3;   
                DISK_FRU_ID = 4;

                setColumns(new TableColumn[] {
                    new TableColumn(DISK_ID, new Integer(15), false,
                        GuiResources.getGuiString("summaryTable.disk.diskId")),
                    new TableColumn(ENABLED, new Integer(10), false,
                        GuiResources.getGuiString("summaryTable.disk.enabled")),
                    new TableColumn(TOTAL_CAPACITY, new Integer(20), false,
                        GuiResources.getGuiString(
                            "summaryTable.disk.totalCapacity")),
                    new TableColumn(PERCENT_USED, new Integer(20), false, 
                        GuiResources.getGuiString(
                            "summaryTable.disk.percentUsed")),
                    new TableColumn(DISK_FRU_ID, new Integer(320), false, 
                        GuiResources.getGuiString(
                            "summaryTable.disk.fruId"))
                });                
                break;

            case DiskSummaryTable.TYPE_CELL:
            default:
                NODE_ID = 0;
                DISK_ID = 1;
                ENABLED = 2;
                TOTAL_CAPACITY = 3;
                PERCENT_USED = 4; 
                DISK_FRU_ID = 5;
                
                setColumns(new TableColumn[] {
                    new TableColumn(NODE_ID, new Integer(10), false,
                        GuiResources.getGuiString("summaryTable.disk.nodeId")),
                    new TableColumn(DISK_ID, new Integer(30), false,
                        GuiResources.getGuiString("summaryTable.disk.diskId")),
                    new TableColumn(ENABLED, new Integer(10), false,
                        GuiResources.getGuiString("summaryTable.disk.enabled")),
// THIS ALLOWS THE DISKS TO BE DISABLED/ENABLED...once roles are defined in 1.2
//                    new TableColumn(ENABLED, new Integer(10), true,
//                        GuiResources.getGuiString("summaryTable.disk.enabled")),
                    new TableColumn(TOTAL_CAPACITY, new Integer(50), false,
                        GuiResources.getGuiString(
                            "summaryTable.disk.totalCapacity")),
                    new TableColumn(PERCENT_USED, new Integer(30), false, 
                        GuiResources.getGuiString(
                            "summaryTable.disk.percentUsed")),
                    new TableColumn(DISK_FRU_ID, new Integer(320), false, 
                        GuiResources.getGuiString(
                            "summaryTable.disk.fruId"))
                });                
                break;
        }
    }
    
    
    /**
     * Populates the table with the summary of entries contained in the
     * directory represented by the passed in node from the tree.  If the
     * node is a file, the table will be empty.
     */
    public void populate(Object modelData) {

    }
}
