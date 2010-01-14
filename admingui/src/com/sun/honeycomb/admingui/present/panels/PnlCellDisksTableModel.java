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
public class PnlCellDisksTableModel extends BaseTableModel {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public static final int NODE_ID = 0;
    public static final int DISK_ID = 1;
    public static final int IS_ONLINE = 2;
    public static final int CAPACITY_USED = 3;
    public static final int RW_ACTIVITY = 4;

    /** Creates a new instance of ContentsTableModel */
    public PnlCellDisksTableModel() {
        super(new TableColumn[] { 
                new TableColumn(NODE_ID, new Integer(25), false, 
                     GuiResources.getGuiString("cell.disks.nodeIdCol")),
                new TableColumn(DISK_ID, new Integer(25), false, 
                     GuiResources.getGuiString("cell.disks.diskIdCol")),
                new TableColumn(IS_ONLINE, new Integer(10), true, 
                     GuiResources.getGuiString("cell.disks.isOnlineCol")),
                new TableColumn(CAPACITY_USED, new Integer(50), false, 
                     GuiResources.getGuiString("cell.disks.capacityCol")),
                new TableColumn(RW_ACTIVITY, null, false, 
                     GuiResources.getGuiString("cell.disks.rwActivityCol"))});
    }
    
    public void populate(Object modelData) {

    }
}
