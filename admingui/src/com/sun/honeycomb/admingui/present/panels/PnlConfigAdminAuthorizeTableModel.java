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

import com.sun.honeycomb.adm.common.Validate;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.util.Vector;
import java.util.ArrayList;
import java.util.List;


/**
 *
 * @author dp1272245
 */
public class PnlConfigAdminAuthorizeTableModel extends BaseTableModel 
                                            implements RowValidatorHelper {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public static final int IP_ADDRESS = 0;
    public static final int SUBNET_MASK = 1;        
    private PnlConfigAdminAuthorize pnlAdminClients = null;
    private Vector dataTypeList = new Vector(); 
    private List invalidRows = new ArrayList();
    private int selectedRow = -1;
    private int newRow = -1;
    
    /** Creates a new instance of ContentsTableModel */
    public PnlConfigAdminAuthorizeTableModel() {
        super(new TableColumn[] { 
                new TableColumn(IP_ADDRESS, new Integer(50), true,
                        GuiResources.getGuiString(
                        "config.sysAccess.authorize.ip")),
                new TableColumn(SUBNET_MASK, new Integer(50), true, 
                        GuiResources.getGuiString(
                        "config.sysAccess.authorize.subnet"))
            });                
    }

    /**
     * Populates the table with the summary of entries contained in the
     * directory represented by the passed in node from the tree.  If the
     * node is a file, the table will be empty.
     */
    public void populate(Object modelData) {

    }  
      
    public void setPanel(PnlConfigAdminAuthorize adminPanel) {
        pnlAdminClients = adminPanel;        
    }
    
    public PnlConfigAdminAuthorize getPanel() {
        return pnlAdminClients;        
    }
        
    public boolean hasHostNameType() {            
        boolean hasHost = false;
         Vector data = getDataVector();       
        // iterate through each row in the data vector
        for (int idx = 0; idx < data.size(); idx++) {            
            Vector rowColValues = (Vector)data.get(idx);
            String name = (String)rowColValues.get(0);            
            Boolean isIPAddressType = new Boolean(false);           
            if (!Validate.isValidIpAddress(name) && 
                                            Validate.isValidHostname(name) ) {                
                 hasHost = true;
                 break;
            }                            
        } 
        return hasHost;
    }
    
    public boolean isCellEditable(int row, int col) { 
        newRow = row;
        String name = (String)this.getValueAt(row, this.IP_ADDRESS);
        if ((!Validate.isValidIpAddress(name) && col == this.SUBNET_MASK) ||
                (isInvalid(selectedRow).booleanValue() && row != selectedRow)) {
            return false;            
        } else {            
            return super.isCellEditable(row, col);        
        }   
    }
    
    
    // *********  RowValidatorHelper interface methods *****************
    /**
     * This method is responsible for keeping track of which row is currently
     * selected in the table using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The currently selected row within the table
     */
    public void setCurrentSelection(int row) {
        this.selectedRow = row;
    }
    /**
     * This method is responsible for keeping track of which row is newly
     * selected in the table using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The newly selected row within the table
     */
    public void setNewSelection(int row) {
        this.newRow = row;
    }
    
    /**
     * This method is responsible for adding an invalid row to the List of 
     * of invalid rows and using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The invalid row within the table
     */
    public void addInvalidRow(int row) {
        invalidRows.add(new Integer(row));
    }
    
    /**
     * This method is responsible for removing an invalid row from the List of 
     * of invalid rows and using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @param row The invalid row within the table
     */
    public void removeInvalidRow(int row) {
        invalidRows.remove(new Integer(row));
    }
    
    /**
     * This method is responsible for removing an invalid row from the List of 
     * of invalid rows and using it to set any member variables 
     * in the implementing class if need-be.  
     *
     * @return List of invalid rows within the table
     */
    public List getInvalidRows() {
        return invalidRows;
    }
    
    /**
     * This method is responsible for enabling the implementing class to 
     * return either true/false in a Boolean as to whether or not the given
     * row entry in the table is invalid.
     *
     * @param rowIndex A particular row in the table
     * @param columnIndex A particular column in the table
     *
     * @return Boolean object containing true if the table row is invalid,
     *                  otherwise, false.
     *
     */
    public Boolean isInvalid(int rowIndex) {
        if (invalidRows.contains(new Integer(rowIndex))) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;



    }
     
    // ********************************************************************
}
