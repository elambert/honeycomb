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
 * PnlConfigDataTableAddDlg.java
 *
 * Created on September 23, 2006, 8:05 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */


package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Table;
import com.sun.nws.mozart.ui.BaseDialog;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg;
import com.sun.nws.mozart.ui.swingextensions.SelectOrderedList;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.UserErrorMessage;
import com.sun.nws.mozart.ui.utility.UserMessage;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author  jp203679
 */

public class PnlConfigDataTableAddDlg extends BaseDialog {
   
    private static String OPT_ALL = GuiResources.getGuiString(
                                "config.metadata.tables.dlg.showAllFields");
    private static String SELECT_NS = GuiResources.getGuiString(
                                "config.metadata.tables.dlg.selectNamespace");
    private boolean userCanceled = false;
    private RootNamespace root = null;  
    /** The table created when user clicks ok. */
    private Table newTable = null; 
    private PnlConfigDataTableTableModel sourceModel = null;
    private PnlConfigDataTableTableModel targetModel = null;
    private PnlConfigMetadataSchema pnlSchema = null;
    
    // each map entry "maps" the namespace name to a list 
    // of possible selectable fields
    private HashMap nsSelectableFieldsMap = new HashMap();
    
    /*
     * Initialize the panel by which user can create new table
     */
    public PnlConfigDataTableAddDlg(java.awt.Frame parent, 
                                    PnlConfigMetadataSchema schema, 
                                    boolean modal) throws UIException {
        super(parent, modal);
        pnlSchema = schema;
        initComponents();
        initDialog(); 
        getSelectableFields();
    }
    
    private void initDialog() throws UIException {
        super.initDialog(btnOk);
                
        // Select ordered list
        solFields.setSourceLabel(GuiResources.getGuiString(
                        "config.metadata.tables.dlg.availableFields"),
                         GuiResources.getGuiString(
                        "config.metadata.tables.dlg.availableFields.mn"));
        solFields.setTargetLabel(GuiResources.getGuiString(
                        "config.metadata.tables.dlg.selectedFields"),
                         GuiResources.getGuiString(
                        "config.metadata.tables.dlg.selectedFieldsTable.mn"));
               
        solFields.setButtonTooltip(SelectOrderedList.ADD_BUTTON, 
                        GuiResources.getGuiString(
                            "config.metadata.schema.sol.addTooltip"));
        solFields.setButtonTooltip(SelectOrderedList.ADD_ALL_BUTTON, 
                        GuiResources.getGuiString(
                            "config.metadata.schema.sol.addAllTooltip"));
        solFields.setButtonTooltip(SelectOrderedList.REMOVE_BUTTON, 
                        GuiResources.getGuiString(
                            "config.metadata.schema.sol.removeTooltip"));
        solFields.setButtonTooltip(SelectOrderedList.REMOVE_ALL_BUTTON, 
                        GuiResources.getGuiString(
                            "config.metadata.schema.sol.removeAllTooltip"));
        solFields.setButtonTooltip(SelectOrderedList.UP_BUTTON, 
                        GuiResources.getGuiString(
                            "config.metadata.schema.sol.moveUpTooltip"));
        solFields.setButtonTooltip(SelectOrderedList.DOWN_BUTTON, 
                        GuiResources.getGuiString(
                            "config.metadata.schema.sol.moveDownTooltip"));
        
        sourceModel = new PnlConfigDataTableTableModel(false);
        targetModel = new PnlConfigDataTableTableModel(false);
        // associate the select ordered list with model that will populate it
        solFields.setSourceTableModel(sourceModel);
        solFields.setTargetTableModel(targetModel);

        // Get root namespace
        try {
            root = ObjectFactory.getHostConnection().getMetadataConfig();   
        } catch (ClientException e) {
            UserMessage msg = new UserErrorMessage(
                    GuiResources.getMsgString("error.problem.loadNamespaces"),
                    GuiResources.getMsgString("error.cause.hostCommError", 
                                                e.getLocalizedMessage()),
                    GuiResources.getMsgString("error.solution.contactAdmin"));
            throw new UIException(msg);
        } catch (ServerException e) {
            UserMessage msg = new UserErrorMessage(
                    GuiResources.getMsgString("error.problem.loadNamespaces"),
                    GuiResources.getMsgString("error.cause.hostCommError", 
                                                e.getLocalizedMessage()),
                    GuiResources.getMsgString("error.solution.contactAdmin"));
            throw new UIException(msg);
        }
                
        // Combo
        cboParentNamespaces.removeAllItems();
        cboParentNamespaces.setMessage(GuiResources.getGuiString(
                        "config.metadata.namespace.selectParent"));
        cboParentNamespaces.setWidthBy(JComboBoxWithDefaultMsg.WIDTH_BY_BOTH);
        txtTableName.setRequestFocusEnabled(true);
    }
    
    /*
     * On selecting <All> from JComboList, call this method to populate
     * all fields from all Namespaces
     */
    private void populateAllNSFields() throws HostException {
        sourceModel.removeAll();
        
        int numOfNamespaces = nsSelectableFieldsMap.size();
        String[] keys = new String[numOfNamespaces];
        nsSelectableFieldsMap.keySet().toArray(keys);
        for (int idx = 0; idx < numOfNamespaces; idx++) {
            populateSourceModel(keys[idx], sourceModel);  
        }
    }
    
    /*
     * This method is called from handleCboParentNamespacesActionPerformed()
     * method to populate Fields in source model table of selected
     * Namespace.
     */    
    public void populateNSFields(String nameSpaceName)
                                    throws HostException, UIException {
        if (nameSpaceName == null) {
            return;
        }  
       
        // if <Select Namespace> selected, show nothing in the select ordered
        // list source table
        if (nameSpaceName.equalsIgnoreCase(GuiResources.getGuiString(
                        "config.metadata.tables.dlg.selectNamespace"))) {  
            sourceModel.removeAll();
            return;
        }  

        // clear map
        nsSelectableFieldsMap.clear();
        
        // populate map by determining which fields have potential for being 
        // added to the new table
        getSelectableFields();   
        
       // if <All> selected
        if (nameSpaceName.equalsIgnoreCase(GuiResources.getGuiString(
                        "config.metadata.tables.dlg.showAllFields"))) {
            populateAllNSFields();
            return;
        }
 
        if (sourceModel == null) {
            // Log.logInfoMessage("source model NULL");
            return;
        }
        
        // clear any data remaining
        if (sourceModel != null) {
            sourceModel.removeAll();
        } 
        populateSourceModel(nameSpaceName, sourceModel);  
        
    }
    /*
     *  This method handles determining which fields meet the criteria for
     *  selection/inclusion in a new filesystem table.  
     *
     */
    private void getSelectableFields() {
        // Add <Select Namespace>
        if (!isInComboBox(SELECT_NS)) {
            cboParentNamespaces.addItem(SELECT_NS);
        }
        ArrayList fields = new ArrayList(pnlSchema.getFields());
        Iterator fIter = fields.iterator();
        while (fIter.hasNext()) {
            Field field = (Field)fIter.next();
            String qualNSName = field.getNamespace().getQualifiedName();
            if (qualNSName == null) {
                // root namespace
                qualNSName = MetadataHelper.ROOT_NAME;
            }
            if (!nsSelectableFieldsMap.containsKey(qualNSName)) {
                nsSelectableFieldsMap.put(qualNSName, new ArrayList());
            }
            // Only add field to the model if the field hasn't been included
            // in a table already AND the field is queryable since ONLY
            // queryable fields are allowed in tables.  To determine if a field
            // has been added to a table, check whether or not its table column
            // is set -- not part of a table if table column = null.
            
            // An additional check is needed to avoid putting duplicate fields
            // in the target model...if the field has already been added to
            // the target model, it will not show up in the source model.
            if (field.getTableColumn() == null && field.isQueryable()) {
                if (!isInTarget(field.getQualifiedName())) {
                    List fieldsList = 
                            (ArrayList)nsSelectableFieldsMap.get(qualNSName);
                    fieldsList.add(field);
                }
                if (!isInComboBox(qualNSName)) {
                    cboParentNamespaces.addItem(qualNSName);
                }
                
            } // check if Field is already used
        } // field iteration
        if (cboParentNamespaces.getItemCount() > 2 && !isInComboBox(OPT_ALL)) {
            cboParentNamespaces.addItem(OPT_ALL);
        }
    }
    
    private void populateSourceModel(String selectedNSName, 
                                                    BaseTableModel model)
    {
        if (nsSelectableFieldsMap.containsKey(selectedNSName)) {
            String cLength = null;
            // get the possible fields to select for the given namespace
            ArrayList fList = 
                        (ArrayList)nsSelectableFieldsMap.get(selectedNSName);
            Iterator fIter = fList.iterator();
            while (fIter.hasNext()) {
                Field field = (Field)fIter.next();
                if (field.getLength() <= 0) {
                    cLength = MetadataHelper.NOT_APPLICABLE;
                } else {
                    // length of the column
                    cLength = Integer.toString(field.getLength());
                }

                int dataType = field.getType();
                String strType = MetadataHelper
                            .toTypeWithLength(new Integer(dataType), cLength);
                model.addRow(new Object[] {
                            field.getQualifiedName(),
                            strType,
                            new Boolean(field.isQueryable()),
                            new Boolean(field.isIndexed())
                });
            }
        }
        // reassociate the table model with the select ordered list since 
        // the table may have data in it and the behavior of the add/remove
        // buttons is dependent upon knowing what data is in the source table
        solFields.setSourceTableModel(model);
    }
    
    private boolean isInTarget(String qFieldName) {
        boolean used = false;
        if (targetModel != null) {
            int usedFieldCount = targetModel.getRowCount();
            for (int idx = 0; idx < usedFieldCount; idx++) {
                String name = 
                        (String)targetModel.getValueAt(idx, targetModel.NAME);
                if (name.compareTo(qFieldName) == 0) {
                    used = true;
                    break;
                }
            }
        }
        return used;
    }
    
    private boolean isInSource(String qFieldName) {
        boolean used = false;
        if (sourceModel != null) {
            for (int idx = 0; idx < sourceModel.getRowCount(); idx++) {
                String qfName = (String)sourceModel
                                    .getValueAt(idx, sourceModel.NAME);
                if (qfName.equals(qFieldName)) {
                    used = true;
                    break;
                }
            }
        }
        return used;
    }
    
    private boolean isTableDuplicate(ArrayList tables, String name) {
        boolean duplicate = false;
        Iterator tblIter = tables.iterator();
        while (tblIter.hasNext()) {
            Table table = (Table)tblIter.next();
            if (table.getName().compareTo(name) == 0) {
                duplicate = true;
            }
        }
        return duplicate;
    }
    
    private boolean isInComboBox(String nsName) {
        boolean isThere = false;
        for (int idx=0; idx < cboParentNamespaces.getItemCount(); idx++) {
            String currentItem = (String)cboParentNamespaces.getItemAt(idx);
            if(currentItem.equals(nsName)) {
                isThere = true;
            }  
        }
        return isThere;
    }
 
    /*
     * This is called when user clicks on "Cancel" button on Add Tables Dialog
     */
    public boolean userCanceled() { return userCanceled; }
    
    /**
     * Call this method after the dialog closes to get the newly created table
     */        
    public Table getNewTable() { return newTable; }
    
    
    
    // ************************
    // Handlers
    // ************************
    /*
     * This method is called when user clicks on "OK" button after inputs
     * to create new Table, and stores the root value in a Namespace object
     */
    private void handleBtnOkActionPerformed(ActionEvent evt) {
        // Check values in the list's target, which will be the new table's
        // columns
        PnlConfigDataTableTableModel tblColumnsModel = 
                (PnlConfigDataTableTableModel)solFields.getTargetTableModel();
        
        String message = "";
        String tableName = txtTableName.getText().trim();
        
        if (tableName.length() == 0) {
            txtTableName.setText("");
            message = GuiResources.getGuiString(
                        "config.metadata.tables.dlg.save.prompt.tableName");
                         
        } else if (tblColumnsModel.getRowCount() == 0) {
            message = GuiResources.getGuiString(
                        "config.metadata.tables.dlg.save.prompt.fields");
        }
        
        // check to see if a table already exists by the new table's name - 
        ArrayList tempTables = new ArrayList(pnlSchema.getTables());
        if (isTableDuplicate(tempTables, tableName)) {
            message = GuiResources.getGuiString(
                    "config.metadata.tables.dlg.save.prompt.duplicateTable");   
        }         
        
        if (message.length() > 0) {
            JOptionPane.showMessageDialog(this, 
                    message,
                    getTitle(),
                    JOptionPane.INFORMATION_MESSAGE);
            return;               
        } 
  
        // create a non-factory default table
        newTable = new Table(tableName, false, false);
        
        // iterate through the model containing the table's columns
        int cnt = tblColumnsModel.getRowCount();
        for (int i = 0; i < cnt; i++) {
            // Get the actual Field object
            String fieldName = (String)tblColumnsModel.getValueAt(i, 
                                            PnlConfigDataTableTableModel.NAME);
            ArrayList fields = new ArrayList(pnlSchema.getFields());
            Field f = null;
            Iterator fIter = fields.iterator();
            while (fIter.hasNext()) {
                // find the fields being added to the table from the list of
                // modified or new fields from the uncommitted schema maps
                f = (Field)fIter.next();
                if (f.getQualifiedName().compareTo(fieldName) == 0) {
                    // found Field object matching the column name
                    break;
                }
            }
            newTable.addColumn(f);           
        }
        setVisible(false);
        userCanceled = false; 
    }
    /*
     * This is called when user clicks on "Cancel" button on "New Table" panel,
     * which brings back to previous state without any changes.
     */
    protected void onDialogCancel() {
        setVisible(false);
        userCanceled = true;
    }
    
 
    /**
     * This method is called when user selects an item in JComboList and
     * the selected item is passed as an argument to 
     * populateSelectedNamespaceFields() method which populates the 
     * corresponding Fields of the Namespace in SourceTable
     */
    private void handleCboParentNamespacesActionPerformed(ActionEvent evt) {
        int selectedIndex = 0;
        JComboBoxWithDefaultMsg cbo = 
                        (JComboBoxWithDefaultMsg)evt.getSource();
        String nsName = (String)cbo.getSelectedItem();
        selectedIndex = cbo.getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }
        try {
            populateNSFields(nsName.trim());
        } catch (UIException ex) {
            // TODO -- log something here
        } catch (HostException ex) {
            // TODO -- log something here
        }
    }
    
    /** 
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        lblViewName = new javax.swing.JLabel();
        txtTableName = new javax.swing.JTextField();
        btnOk = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();
        pnlFields = new javax.swing.JPanel();
        solFields = new com.sun.nws.mozart.ui.swingextensions.SelectOrderedList();
        lblNamespace = new javax.swing.JLabel();
        cboParentNamespaces = new com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        lblViewName.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.tables.dlg.tableName.mn").charAt(0));
        lblViewName.setLabelFor(txtTableName);
        lblViewName.setText(GuiResources.getGuiString("config.metadata.tables.dlg.tableName"));

        btnOk.setMnemonic(GuiResources.getGuiString("button.ok.mn").charAt(0));
        btnOk.setText(GuiResources.getGuiString("button.ok"));
        btnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOkActionPerformed(evt);
            }
        });

        btnCancel.setMnemonic(GuiResources.getGuiString("button.cancel.mn").charAt(0));
        btnCancel.setText(GuiResources.getGuiString("button.cancel"));
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        pnlFields.setBorder(javax.swing.BorderFactory.createTitledBorder(GuiResources.getGuiString("config.metadata.tables.dlg.fields")));

        org.jdesktop.layout.GroupLayout pnlFieldsLayout = new org.jdesktop.layout.GroupLayout(pnlFields);
        pnlFields.setLayout(pnlFieldsLayout);
        pnlFieldsLayout.setHorizontalGroup(
            pnlFieldsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFieldsLayout.createSequentialGroup()
                .addContainerGap()
                .add(solFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 661, Short.MAX_VALUE)
                .addContainerGap())
        );
        pnlFieldsLayout.setVerticalGroup(
            pnlFieldsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFieldsLayout.createSequentialGroup()
                .add(22, 22, 22)
                .add(solFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 196, Short.MAX_VALUE)
                .addContainerGap())
        );

        lblNamespace.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.tables.dlg.nameSpace.mn").charAt(0));
        lblNamespace.setLabelFor(cboParentNamespaces);
        lblNamespace.setText(GuiResources.getGuiString("config.metadata.tables.dlg.nameSpace"));

        cboParentNamespaces.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cboParentNamespaces.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboParentNamespacesActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(290, 290, 290)
                        .add(btnOk)
                        .add(19, 19, 19)
                        .add(btnCancel))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(pnlFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblNamespace)
                            .add(lblViewName))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(cboParentNamespaces, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(txtTableName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 172, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(26, 26, 26)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblViewName)
                    .add(txtTableName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(21, 21, 21)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblNamespace)
                    .add(cboParentNamespaces, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(46, 46, 46)
                .add(pnlFields, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 89, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(btnOk)
                    .add(btnCancel))
                .addContainerGap())
        );
        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cboParentNamespacesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboParentNamespacesActionPerformed
        handleCboParentNamespacesActionPerformed(evt);
    }//GEN-LAST:event_cboParentNamespacesActionPerformed

    private void btnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOkActionPerformed
        handleBtnOkActionPerformed(evt);
    }//GEN-LAST:event_btnOkActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        onDialogCancel();
    }//GEN-LAST:event_btnCancelActionPerformed
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnOk;
    private com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg cboParentNamespaces;
    private javax.swing.JLabel lblNamespace;
    private javax.swing.JLabel lblViewName;
    private javax.swing.JPanel pnlFields;
    private com.sun.nws.mozart.ui.swingextensions.SelectOrderedList solFields;
    private javax.swing.JTextField txtTableName;
    // End of variables declaration//GEN-END:variables
    
}
