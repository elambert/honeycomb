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

import com.sun.honeycomb.emd.config.Namespace;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;
import javax.swing.JOptionPane;


/**
 *
 * @author jb127219
 */
public class PnlConfigNSSummaryTableModel extends BaseTableModel
                                  implements ReservedNamespaceTableHelper {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public static final int NAME = 0;
    public static final int PARENT = 1;
    public static final int IS_WRITABLE = 2;
    public static final int IS_EXTENSIBLE = 3;
    public static final String NA = "N/A";
    
    private Vector reservedNSFlags = new Vector();
    private PnlConfigMetadataSchema pnlSchema = null;
    private int panelType = -1;
    

    /** Creates a new instance  */
    public PnlConfigNSSummaryTableModel(boolean editable) {
        super(new TableColumn[] { 
            new TableColumn(NAME, new Integer(90), editable, 
                 GuiResources.getGuiString("config.metadata.namespace.name")),
            new TableColumn(PARENT, new Integer(60), editable, 
                 GuiResources.getGuiString(
                    "config.metadata.schema.ns.parentName")),
            new TableColumn(IS_WRITABLE, new Integer(50), editable,
                  GuiResources.getGuiString(
                    "config.metadata.schema.ns.writable")), 
            new TableColumn(IS_EXTENSIBLE, new Integer(60), editable,
                 GuiResources.getGuiString(
                    "config.metadata.schema.ns.extensible"))
        });                
    }
    
    public void setPanel(PnlConfigMetadataSchema schema) {
        pnlSchema = schema;
        panelType = schema.getPanelType();
    }

    public void setValueAt(Object aValue, 
                            int rowIndex,
                            int columnIndex) {
        // if code reaches here, then value MAY be able to be modified
        if (columnIndex == IS_EXTENSIBLE) {
            String nsName = (String)getValueAt(rowIndex, this.NAME);
                Namespace ns = pnlSchema.findNSInMaps(nsName);
                if (ns != null) {
                    if (isOKConfirmed()) {
                        ns.setExtensible(((Boolean)aValue).booleanValue());
                        // need to add namespace to the modified namespace map
                        if (pnlSchema.getCommittedNS(nsName) != null &&
                                pnlSchema.getInitExtensibleNS(nsName) != null) {
                            pnlSchema.addModifiedNS(ns);
                        }
                    } else {
                        aValue = new Boolean(!((Boolean)aValue).booleanValue());
                    }
                }
        } 
        super.setValueAt(aValue, rowIndex, columnIndex);
    }
    
    private boolean isOKConfirmed() {
        Object[] options = {GuiResources.getGuiString("button.ok"),
                                GuiResources.getGuiString("button.cancel")};
        int confirmVal = JOptionPane.showOptionDialog(
                                MainFrame.getMainFrame(),
                                GuiResources.getGuiString(
                                  "config.metadata.schema.ns.extensible.check"),
                                GuiResources.getGuiString("app.name"),
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.WARNING_MESSAGE,
                                null, 
                                options, 
                                options[1]);
        return confirmVal == JOptionPane.OK_OPTION;
    }
    
    public boolean isCellEditable(int row, int column) {
        boolean editable = false;
        if (panelType == PnlConfigMetadataSchema.TYPE_SETUP_PNL) {
            if (column == IS_EXTENSIBLE) {
                // Logic for allowing the user to edit the extensible (E) 
                // attribute -- User can change an E ns to NE but 
                // CAN NOT change a NE ns to E.  
                String nsName = (String)getValueAt(row, this.NAME);
                Namespace ns = pnlSchema.findNSInMaps(nsName);
                if (ns != null && ns.isExtensible() && 
                                            !pnlSchema.isReservedNS(nsName)) {
                    // need to determine if the ns being changed from E to NE
                    // has children that are NEW namespaces -- if the ns has
                    // children that are already committed, then they are
                    // grandfathered in -- ns cannot be changed from E to NE if
                    // it has new children. Children are not automatically 
                    // registered with the parent until they are committed - 
                    // thus, need to iterate through all new namespaces to
                    // determine if the ns is their parent.
                    boolean hasNewKid = false;
                    ArrayList newNSList = (ArrayList)pnlSchema.getNewNS();
                    Iterator nsIter = newNSList.iterator();
                    while (nsIter.hasNext()) {
                        Namespace newNS = (Namespace)nsIter.next();
                        Namespace parent = newNS.getParent();
                        String pName = parent.getQualifiedName();
                        if (pName == null) {
                            // root namespace is the parent
                            pName = MetadataHelper.ROOT_NAME;
                        } 
                        if (pName.equals(nsName)) {
                            hasNewKid = true;
                            break;
                        }
                    }
                    if (!hasNewKid) {
                        editable = true;
                    } else {
                        editable = super.isCellEditable(row, column);
                    }
                }
            }
        } 
        return editable;
    }
    
    public void populate(Object modelData) {

    }
    
    public void clearReservedFlags() {
        reservedNSFlags.clear();
    }
    
    public void setReserved(Vector reservedNS) {
        
        Vector data = getDataVector();  
        clearReservedFlags();
        // iterate through each row in the data vector
        for (int idx = 0; idx < data.size(); idx++) {
            Iterator iter = reservedNS.iterator();
            Vector rowColValues = (Vector)data.get(idx);
            String name = (String)rowColValues.get(0);
            // iterate through the reserved namespace names
            Boolean isReserved = new Boolean(false);
            while (iter.hasNext()) {
                Namespace ns = (Namespace)iter.next();
                String qualName = ns.getQualifiedName();
                // check to see if the qualified name matches any of the 
                // reserved namespaces -- 
                if (qualName == null) { 
                    if (name == MetadataHelper.ROOT_NAME) {
                        isReserved = new Boolean(true);
                    }
                } else if (name.compareTo(qualName) == 0) {
                    isReserved = new Boolean(true);
                    break;
                }
            } // done iterating through all reserved namespace names
            reservedNSFlags.add(idx, isReserved);
        } // done iterating through all of the rows of data   
        
    }
    
    public Vector getReserved() {
        return reservedNSFlags;
    }
    
    public Boolean isReserved(int rowIndex, int columnIndex) {
        Boolean isNSReservedValue = new Boolean(false);
        int numRows = reservedNSFlags.size();
        if ((numRows > 0) && (numRows > rowIndex)) {
            isNSReservedValue = (Boolean)reservedNSFlags.get(rowIndex);
        } 
        return isNSReservedValue;          
    }
}
