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

import com.sun.honeycomb.emd.config.Field;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import javax.swing.JOptionPane;


/**
 *
 * @author jb127219
 */
public class NamespaceDetailsTableModel extends BaseTableModel 
                                    implements ReservedNamespaceTableHelper {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public static int NAME = 0;
    public static int DATA_TYPE = 1;
    public static int IS_QUERYABLE = 2;
    
    public static final String NA = "N/A";
    private Vector reservedFlags = new Vector();
    private PnlConfigMetadataSchema pnlSchema = null;
    private int panelType = -1;

    /** Creates a new instance of ContentsTableModel */
    public NamespaceDetailsTableModel(boolean editable) {
        super();
        setColumns(new TableColumn[] {
            new TableColumn(NAME, new Integer(120), editable,
                    GuiResources.getGuiString(
                    "config.metadata.namespace.name")),
            new TableColumn(DATA_TYPE, new Integer(50), editable,
                    GuiResources.getGuiString(
                    "config.metadata.schema.dataType")),
            new TableColumn(IS_QUERYABLE, new Integer(40), editable,
                    GuiResources.getGuiString(
                    "config.metadata.schema.ns.queryable"))
        });

    }
    
    public void setPanel(PnlConfigMetadataSchema schema) {
        pnlSchema = schema;
        panelType = schema.getPanelType();
    }
    
    public void setValueAt(Object aValue, 
                            int rowIndex,
                            int columnIndex) {
        // if code reaches here, then value can be modified
        Field field = null;
        String fieldName = null;
        if (columnIndex == IS_QUERYABLE) {
            fieldName = (String)getValueAt(rowIndex, this.NAME);
            field = pnlSchema.findFieldInMaps(fieldName);
            if (field != null) {
                field.setQueryable(((Boolean)aValue).booleanValue());

                // need to add field to the modified fields map to keep track of
                // it when figuring out which fields can be added to new tables
                if (pnlSchema.getCommittedField(fieldName) != null &&
                        pnlSchema.getNQInitField(fieldName) != null) {
                    pnlSchema.addModifiedField(field);
                }
            }
        } 
        super.setValueAt(aValue, rowIndex, columnIndex);
    }
    
    private boolean isFieldNotInTable(Field f) {
        boolean change = false;
        if (f.getTableColumn() == null) {
            // field is not part of a table and therefore the 
            // attribute can be modified
            change = true;
        }
        return change;
    }
    
    private boolean canAttributeChange(int row, int column) {
        boolean change = false;
        // Logic for allowing the user to edit the queryable attribute
        // -- User can change a NQ committed field to Q but CAN NOT
        // change a Q committed field to NQ.  While on the Set Up 
        // Schema panel, the user should be able to change the 
        // queryable attribute for new fields as long as they are NOT
        // part of a table.
        String fieldName = (String)getValueAt(row, this.NAME);
        Field field = pnlSchema.getNewField(fieldName);
        if (field != null) { 
            // new field so can change from Q to NQ and back to Q provided
            // the field is not in a table
            change = isFieldNotInTable(field);
        } else {
            field = pnlSchema.getCommittedField(fieldName);
            if (field != null) {
                // committed field so determine if original state 
                // was NQ or Q....if NQ, then its state can be changed
                // from NQ to Q and back to NQ provided it is NOT part
                // of a table and the changes haven't been committed
                Field initField = pnlSchema.getNQInitField(fieldName);
                if (initField != null) {
                    // The original state of the committed field is NQ
                    change = isFieldNotInTable(field);
                } 
            } else {
                field = pnlSchema.getModifiedField(fieldName);
                if (field != null) {
                    change = isFieldNotInTable(field);
                }
            }
        } // end of else
        
        return change;
    }
    
    public boolean isCellEditable(int row, int column) {
        boolean editable = false;
        if (panelType == PnlConfigMetadataSchema.TYPE_SETUP_PNL) {
            if (column == IS_QUERYABLE) {
                editable = canAttributeChange(row, column);
            } else {
                editable = super.isCellEditable(row, column);
            }
        } 
        return editable;
    }
    
    public void populate(Object modelData) {
        // do nothing here
    }
    public void clearReservedFlags() {
        reservedFlags.clear();
    }
        
    public void setReserved(Vector reserved) {
        
        Vector data = getDataVector();
        clearReservedFlags();
        // iterate through each row in the data vector
        for (int idx = 0; idx < data.size(); idx++) {
            Iterator iter = reserved.iterator();
            // iterate through the reserved fields, which are part of
            // a reserved namespace
            Boolean isReserved = new Boolean(false);
            while (iter.hasNext()) {
                Field f = (Field)iter.next();
                Vector rowColValues = (Vector)data.get(idx);
                String name = (String)rowColValues.get(0);
                String qualName = f.getQualifiedName();
                if (name.compareTo(qualName) == 0) {
                    isReserved = new Boolean(true);
                    break;
                }
            } // done iterating through all reserved field names
            reservedFlags.add(idx, isReserved);
        } // done iterating through all of the rows of data    
    }

    public Vector getReserved() {
        return reservedFlags;
    }
    
    public Boolean isReserved(int rowIndex, int columnIndex) {
        Boolean isReservedValue = new Boolean(false);
        int numRows = reservedFlags.size();
        if ((numRows > 0) && (numRows + 1 >= rowIndex)) {
            isReservedValue = (Boolean)reservedFlags.get(rowIndex);
        } 
        return isReservedValue;          
    }
}
