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
 * PnlConfigDataTableTableModel.java
 *
 * Created on September 23, 2006, 6:05 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.honeycomb.admingui.present.panels;

//import com.sun.nws.mozart.ui.BaseSortedTableModel;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.utility.GuiResources;

/**
 *
 * @author jp203679
 */
public class PnlConfigDataTableTableModel extends BaseTableModel{
    
    /**
     * Table does not include an "Unset value" column
     */
   // public static final int TYPE_NO_UNSETVAL = 10;
    /**
     * Table includes an "Unset value" column and it is editable.
     */
   // public static final int TYPE_RW_UNSETVAL = 11;
    /**
     * Table includes an "Unset value" column and it is read-only.
     */
   // public static final int TYPE_RO_UNSETVAL = 12; 
    
    public static final int NAME = 0;
    public static final int DATA_TYPE = 1;   
    //  public static final int IS_QUERYABLE = 2;
    //    public static final int LENGTH = 3;

    /** Creates a new instance of ContentsTableModel */
   /* public PnlConfigDataTableTableModel(int type, boolean sorted) {
        super();
        TableColumn[] cols;
        if (type == TYPE_NO_UNSETVAL) {
            cols = new TableColumn[] { 
                new TableColumn(NAME, null, false, 
                     GuiResources.getGuiString(
                        "config.metadata.tables.fieldName")),
                new TableColumn(DATA_TYPE, new Integer(10), false, 
                     GuiResources.getGuiString(
                        "config.metadata.tables.dataType"))};
        } else {
            boolean isEditable = (type == TYPE_RW_UNSETVAL) ? true : false;
            cols = new TableColumn[] { 
                new TableColumn(NAME, null, false, 
                     GuiResources.getGuiString(
                        "config.metadata.tables.fieldName")),
                new TableColumn(DATA_TYPE, new Integer(8), false, 
                     GuiResources.getGuiString(
                        "config.metadata.tables.dataType")),
                new TableColumn(UNSET_VALUE, new Integer(10), isEditable, 
                     GuiResources.getGuiString(
                        "config.metadata.tables.unsetVal"))};                
        } 
        
        setColumns(cols);
        if (sorted) {
            setSortingStatus(NAME, BaseSortedTableModel.ASCENDING);
        } else {
            setSortingStatus(NAME, BaseSortedTableModel.NOT_SORTED);
        }
    } */
    /** Creates a new instance of ContentsTableModel */
    public PnlConfigDataTableTableModel(boolean editable) {
          super(new TableColumn[] {
                    new TableColumn(NAME, new Integer(120), editable,
                            GuiResources.getGuiString(
                            "config.metadata.namespace.name")),
                    new TableColumn(DATA_TYPE, new Integer(50), editable,
                            GuiResources.getGuiString(
                            "config.metadata.schema.dataType")),
//                    new TableColumn(IS_QUERYABLE, new Integer(40), editable,
//                            GuiResources.getGuiString(
//                            "config.metadata.schema.ns.queryable")),
        });                
    }
    
    public void populate(Object modelData) {
    
    }

    
}
