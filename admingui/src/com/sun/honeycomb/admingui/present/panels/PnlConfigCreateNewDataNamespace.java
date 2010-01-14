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



/**
 * PnlConfigCreateNewDataNamespace.java
 *
 * Created on November 3, 2006, 03:40 PM
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.nws.mozart.ui.BaseDialog;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.ToolBox;
import com.sun.nws.mozart.ui.utility.UserErrorMessage;
import com.sun.nws.mozart.ui.utility.UserInfoMessage;
import com.sun.nws.mozart.ui.utility.UserMessage;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveList;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListValidator;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author  jp203679
 */

public class PnlConfigCreateNewDataNamespace extends BaseDialog
                                            implements AddRemoveListValidator {
    
    
    
    private AdminApi hostConn = ObjectFactory.getHostConnection();   
    private AddRemoveList arlFields = new AddRemoveList(); 
    private Namespace newNamespace = null;
    private RootNamespace root = null; 
    private PnlConfigMetadataSchema pnlSchema = null;
    private PnlConfigDataNamespaceTableModel model = null;
    private PnlConfigDataNamespaceTableModel mdlListener = null;
    private TableColumnModel colModel = null;
    private JFormattedTextField jftLengthField = new JFormattedTextField();
    
    // drop down component used for the DATA_TYPE column in the table
    private JComboBox cboDataTypes = null;
   
    // handling validation errors
    private String errorMessageAllRows = "";
    private String validationErrMsg = "";
    private String integerErrMsg = "";
    private int integerErrMsgRow = 0;
    private int errorMessageRow = 0;
    private int selectedRow = 0;
    private boolean invalidateRowCount = false;
    private boolean userCanceled = false;  
    private TextInputVerifier verifier = new TextInputVerifier(
                                    TextInputVerifier.NAMESPACE_NAME);
    
    // Class for editing cell as JFormattedTextField for FieldLength column
    public class IntegerEditor extends DefaultCellEditor {
        public IntegerEditor(JFormattedTextField jft) {
            super(new JFormattedTextField());
            JTextField jf = (JTextField) getComponent();
            jft = (JFormattedTextField) jf;
            jft.setBorder(null);
        }
    }  
    
    /** Creates new form PanelAdmin */
    public PnlConfigCreateNewDataNamespace(java.awt.Frame frame, 
                                            PnlConfigMetadataSchema schema,
                                            boolean modal) 
                                                throws UIException {
        super(frame, modal);
        pnlSchema = schema;
        initComponents();
        initComponents2();        
    }     
    
    /**
     * This method is called to initialize the new namespace dialog with 
     * namespaces combo and AddRemoveList for creating new namespace
     */
    private void initComponents2() throws UIException {                
        super.initDialog(btnOk);   
        
        // initialize the namespace name's text field
        txtfName.setText("");       
        txtfName.setVisible(true);
        txtfName.requestFocusInWindow();
        txtfName.setInputVerifier(verifier);
        
        // initialize the values for the namespace and the namespace parent
        lblNameValue.setText("");
        lblNameValue.setVisible(false);
        lblParentNameValue.setText(""); 
        lblParentNameValue.setVisible(false);
        
        // initialize the "Is Writable" and "Is Extensible" check boxes
        chkIsWritable.setEnabled(true);
        chkIsWritable.setSelected(true);
        chkIsExtensible.setEnabled(true);
        chkIsExtensible.setSelected(true);
        
        // initialize the panel, scroll pane and add remove list components
        // for namespace fields
        arlFields.setVisible(true);
        pnlFields.add(arlFields);
        setupAddRemoveFieldsList(arlFields);
        scrlFields.setViewportView(arlFields);
        scrlFields.setVisible(true);
        ToolBox.setupJTable(tablFields, scrlFields, model); 
        
        // initialize the drop down containing the potential parent namespaces
        cboParentNamespaces.setVisible(true);
        cboParentNamespaces.setMessage(GuiResources.getGuiString(
                        "config.metadata.namespace.selectParent"));
        cboParentNamespaces.setWidthBy(JComboBoxWithDefaultMsg.WIDTH_BY_BOTH);
 
        // Get root namespace
        try {
            root = hostConn.getMetadataConfig();
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
        
        populateNamespaces(root, cboParentNamespaces);
    }
    
    /**
     * This method is used to provide Namespace by taking "root" and 
     * string "name" as arguments.
     */
    public Namespace getNamespace(RootNamespace root, 
                                         String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        // Check for root parent namespace
        if (qualifiedName.equals(MetadataHelper.ROOT_NAME)) {
            return root;
        } else {
            // Not root namespace. Get ns.
            return pnlSchema.findNSInMaps(qualifiedName);
        }
    }
    
    /**
     * Populates parent namespaces combo box
     */
    public void populateNamespaces(RootNamespace root, JComboBox cbo) {
        cbo.removeAllItems();
        
        // Add root
        cbo.addItem(MetadataHelper.ROOT_NAME);
        
        // Add all namespaces that are extensible to the dropdown
        List list = pnlSchema.getNewNS();
        list.addAll(pnlSchema.getCommittedNS());
        
        Iterator iter = list.iterator();
        while (iter.hasNext()) {
            Namespace ns = (Namespace) iter.next();
            // only add the namespace ONLY if it is extensible
            if (ns.isExtensible() && 
                        ns.getQualifiedName() != null) {
                cbo.addItem(ns.getQualifiedName());
            }
        }
    }
    
    
    public void setupAddRemoveFieldsList(AddRemoveList arl)  {               
        arl.setValidator(this);        
        setValidationErrMsg("");
       
        // Clearing all previous rows from AddRemoveList buffer if any
        if (mdlListener != null) {
            mdlListener.removeAll();
        }

        model = new PnlConfigDataNamespaceTableModel(true, true,
                            PnlConfigDataNamespaceTableModel.TYPE_FIELDS);
        arl.setTableModel(model);
        arl.setAddButtonTooltip(GuiResources.getGuiString(
                                    "config.metadata.namespace.addTooltip"));
        arl.setRemoveButtonTooltip(GuiResources.getGuiString(
                                    "config.metadata.namespace.removeTooltip"));
   
        // Table cell editors
        colModel = arl.getTableColumnModel();

        cboDataTypes = new JComboBox();
        cboDataTypes.addItem(MetadataHelper.LONG);
        cboDataTypes.addItem(MetadataHelper.DOUBLE);            
        cboDataTypes.addItem(MetadataHelper.STRING_N);
        cboDataTypes.addItem(MetadataHelper.CHAR_N);
        cboDataTypes.addItem(MetadataHelper.BINARY_N);
        cboDataTypes.addItem(MetadataHelper.DATE);
        cboDataTypes.addItem(MetadataHelper.TIME);
        cboDataTypes.addItem(MetadataHelper.TIMESTAMP);

        // setup the data type drop down as an editor for the data type column
        DefaultCellEditor cellEditor = new DefaultCellEditor(cboDataTypes);
        colModel.getColumn(PnlConfigDataNamespaceTableModel.DATA_TYPE)
                                                .setCellEditor(cellEditor);
        
        // use the IntegerEditor as the length column's editor
        colModel.getColumn(PnlConfigDataNamespaceTableModel.LENGTH) 
                            .setCellEditor(new IntegerEditor(jftLengthField));
         
         // This listener is used to retain the enable/disable functionality
         // of FieldLength cell depending on the datatype
         ListSelectionModel selModel = arl.getTableSelectionModel();
         selModel.addListSelectionListener(new ListSelectionListener() {
             public void valueChanged(ListSelectionEvent listSelEvt) {
                 ListSelectionModel mdl = 
                                (ListSelectionModel) listSelEvt.getSource();
                 setSelectedRow(mdl.getMaxSelectionIndex());
             }
         });
 
      
         // Model listener
         model.addTableModelListener(new TableModelListener() {
             public void tableChanged(TableModelEvent e) {
                 if (e.getType() == TableModelEvent.INSERT) {
                     // validates input row-by-row after the "add" button 
                     // is clicked
                     if (e.getFirstRow() > 0) {
                        boolean res = isRowDataValid();
                     } 

                     // populates new row with default values
                     mdlListener =
                             (PnlConfigDataNamespaceTableModel) e.getSource();
                     if (mdlListener.getValueAt(e.getFirstRow(),
                                            mdlListener.DATA_TYPE) == null) {
                         mdlListener.setValueAt(MetadataHelper.LONG,
                                                    e.getFirstRow(), 
                                                    mdlListener.DATA_TYPE);
                     }
                     if (mdlListener.getValueAt(e.getFirstRow(),
                                                mdlListener.LENGTH) == null) {
                         mdlListener.setValueAt(MetadataHelper.NOT_APPLICABLE,
                                                    e.getFirstRow(), 
                                                    mdlListener.LENGTH);
                         jftLengthField.setEnabled(false);
                     }
                     if (mdlListener.getValueAt(e.getFirstRow(),
                                            mdlListener.IS_QUERYABLE) == null) {
                         mdlListener.setValueAt(new Boolean(true),
                                                    e.getFirstRow(), 
                                                    mdlListener.IS_QUERYABLE);
                     }
                     jftLengthField.setEnabled(false);
                 }
                
                 // FieldLength value gets changed as per Combo List selection
                 if (e.getType() == TableModelEvent.UPDATE) {
                     int row = e.getFirstRow();
                     int column = e.getColumn();
                     
                     if (column == PnlConfigDataNamespaceTableModel.DATA_TYPE &&
                             row >= 0) {
                         String dataType = (String)mdlListener.getValueAt(row,
                                 mdlListener.DATA_TYPE);
                         Object lenObj = (Object)mdlListener.getValueAt(row,
                                 mdlListener.LENGTH);
                         String strLen = MetadataHelper.objToString(lenObj);
                         strLen = strLen.trim();
                         if (dataType.equalsIgnoreCase(
                                 MetadataHelper.STRING_N)) {
                            if (strLen.equalsIgnoreCase(
                                     MetadataHelper.NOT_APPLICABLE) ||
                                     !MetadataHelper.isValidInteger(
                                                        lenObj, dataType)) {
                                jftLengthField.setEnabled(true);
                                mdlListener.setValueAt(new Integer(
                                            MetadataHelper.DEFAULT_STRING_SIZE),
                                            row, mdlListener.LENGTH);
                                            
                             }
                         } else if (dataType.equalsIgnoreCase(
                                                    MetadataHelper.CHAR_N)) {
                             if (strLen.equalsIgnoreCase(
                                    MetadataHelper.NOT_APPLICABLE) ||
                                        !MetadataHelper.isValidInteger(
                                                        lenObj, dataType)) {
                                 jftLengthField.setEnabled(true);
                                 mdlListener.setValueAt(new Integer(
                                         MetadataHelper.DEFAULT_CHAR_SIZE),
                                         row, mdlListener.LENGTH);
                             }
                         } else if (dataType.equalsIgnoreCase(
                                                    MetadataHelper.BINARY_N)) {
                             if (strLen.equalsIgnoreCase(
                                MetadataHelper.NOT_APPLICABLE) ||
                                     !MetadataHelper.isValidInteger(
                                                            lenObj, dataType)) {
                                jftLengthField.setEnabled(true);
                                mdlListener.setValueAt(new Integer(
                                        MetadataHelper.DEFAULT_BINARY_SIZE), 
                                        row, mdlListener.LENGTH);
                             }
                         } else {
                             mdlListener.setValueAt(
                                        MetadataHelper.NOT_APPLICABLE,
                                                    row, mdlListener.LENGTH);
                             jftLengthField.setEnabled(false);
                         }
                         
                     }
                 } // end UPDATE
                 
             }
         }); // end model
         
         // To retain FieldLength cell editable depending on DataType
         jftLengthField.addFocusListener(new FocusAdapter() {
             public void focusGained(FocusEvent focusEvent) {
                 if (getSelectedRow() >= 0) {
                     String dataTypeFld = (String)mdlListener
                        .getValueAt(getSelectedRow(), mdlListener.DATA_TYPE);
                     if (dataTypeFld.equalsIgnoreCase(MetadataHelper.STRING_N) 
                        || dataTypeFld.equalsIgnoreCase(MetadataHelper.CHAR_N) 
                            || dataTypeFld.equalsIgnoreCase(
                                                    MetadataHelper.BINARY_N)) {
                         jftLengthField.setEnabled(true);
                     } else {
                         jftLengthField.setEnabled(false);
                     }
                     
                 }
             }
         });
    }
    
    public boolean getUserCanceled() { return userCanceled; }
    // ************************** AddRemoveListValidator Impl **************
    
    public boolean isRowDataValid() {
        return validateRow();
    }
    
    public String getValidationErrMsg() {
        return validationErrMsg();
    }
    // *********************************************************************
    public void setValidationErrMsg(String msg) {
        this.validationErrMsg = msg;
    }
    public String getValErrMsg() {
        return validationErrMsg;
    }
    
    private  int getSelectedRow() {
        return selectedRow;
    }
    private void setSelectedRow(int row) {
        selectedRow = row;
    }
    public void setErrorMessage(String msg) {
        this.errorMessageAllRows = msg;
    }
    public String getErrorMessage() {
        return errorMessageAllRows;
    }
    public void setErrorMessageRow(int row) {
        this.errorMessageRow = row;
    }
    public int getErrorMessageRow() {
        return errorMessageRow;
    }
    public void setIntegerErrMsg(String msg) {
        this.integerErrMsg = msg;
    }
    public String getIntegerErrMsg() {
        return integerErrMsg;
    }
    public void setIntegerErrMsgRow(int row) {
        this.integerErrMsgRow = row;
    }
    public int getIntegerErrMsgRow() {
        return integerErrMsgRow;
    }
    
    /**
     * This is used for AddRemoveListValidator
     * interface's method which is called from getValidationErrMsg()
     */   
    public String validationErrMsg() {
        String msg = "";
        msg = getValErrMsg();                
        return msg;
    }
    
    /** 
     * validate row by row if name field or length field is empty - called
     * when clicking "add" icon in AddRemoveList panel
     */
    public  boolean validateRow() {
        boolean result = true;
        boolean fieldNames = true;
        boolean fieldLengths = true;
        String msg = "";
        String row = "";
        int totalRowCount = 0;
        Integer in = null;
        if (mdlListener != null) {
            totalRowCount = mdlListener.getRowCount();
            if (totalRowCount > 0) {
                for (int i = 0; i < totalRowCount; i++) {
                    String name = (String)mdlListener
                                            .getValueAt(i, mdlListener.NAME);
                    Object lenObj = (Object)mdlListener
                            .getValueAt(i, mdlListener.LENGTH);
                    String length = MetadataHelper.objToString(lenObj);
                    
                    if (name == null || name.trim().length() == 0) {
                        fieldNames = false;
                        result = false;
                        msg = GuiResources.getGuiString(
                                "config.metadata.namespace.field.noName");
                        row = String.valueOf(i + 1);
                        setValidationErrMsg(msg + row);
                        break;
                    } else if (name.length() > 
                                        MetadataHelper.MAX_FIELD_NAME_LENGTH) {
                        String maxNameLen = String.valueOf(
                                        MetadataHelper.MAX_FIELD_NAME_LENGTH);
                        fieldNames = false;
                        result = false;
                        msg = GuiResources.getGuiString(
                                "config.metadata.namespace.field.maxNameSize",
                                maxNameLen);
                        row = String.valueOf(i + 1);
                        setValidationErrMsg(msg + row);
                        break;
                    } else if (length == null || length.trim().length() == 0) {
                        fieldLengths = false;
                        result = false;
                        msg = GuiResources.getGuiString(
                                "config.metadata.namespace.field.noLengthSize");
                        row = String.valueOf(i + 1);
                        setValidationErrMsg(msg + row);
                        break;
                    } else {
                        boolean res = validateIsInteger(i);
                        if (res == false) {
                            result = false;
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * validate row by row if name field or length field is empty - called
     * when clicking "OK"  button in AddRemoveList / new namespace panels
     */    
    public  boolean validateAllRows() {
        boolean result = true;
        boolean fieldNames = true;
        boolean fieldLengths = true;
        String msg = "";
        String rowNum = "";
        int totalRowCount = 0;
        if (mdlListener != null) {
            totalRowCount = mdlListener.getRowCount();
            
            for (int i = 0; i < totalRowCount; i++) {
                String name = (String)mdlListener
                                    .getValueAt(i, mdlListener.NAME);
                Object lenObj = (Object)mdlListener
                                            .getValueAt(i, mdlListener.LENGTH);
                String length = MetadataHelper.objToString(lenObj);
                if (name == null || name.trim().length() == 0) {
                    fieldNames = false;
                    result = false;
                    msg = GuiResources.getGuiString(
                                    "config.metadata.namespace.field.noName");
                    rowNum = String.valueOf(i + 1);
                    setErrorMessage(msg + rowNum);
                    break;
                } else if (name.length() > 
                                        MetadataHelper.MAX_FIELD_NAME_LENGTH) {
                    String maxNameLen = String.valueOf(
                                        MetadataHelper.MAX_FIELD_NAME_LENGTH);
                    fieldNames = false;
                    result = false;
                    msg = GuiResources.getGuiString(
                            "config.metadata.namespace.field.maxNameSize",
                            maxNameLen);
                    rowNum = String.valueOf(i + 1);
                    setErrorMessage(msg + rowNum);
                    break;
                } else if (length == null || length.trim().length() == 0) {
                    fieldLengths = false;
                    result = false;
                    msg = GuiResources.getGuiString(
                            "config.metadata.namespace.field.noLengthSize");
                    rowNum = String.valueOf(i + 1);
                    setErrorMessage(msg + rowNum);
                    break;
                }
            }
        }
        return result;
    }
     
    /** 
     * validate if Field Length is in the valid range or not - called
     * after clicking "add" icon in AddRemoveList panel
     */
    public boolean validateIsInteger(int row) {
        boolean result = true;
        int value = 0;
        String msg = "";
        String rowNum = "";
        if (mdlListener != null) {
            Object lenObj = (Object) mdlListener
                                        .getValueAt(row, mdlListener.LENGTH);
            String length = MetadataHelper.objToString(lenObj);
            String dataType = (String)mdlListener
                                        .getValueAt(row, mdlListener.DATA_TYPE);
            length = length.trim();
            if (dataType.equalsIgnoreCase(MetadataHelper.STRING_N) ||
                    dataType.equalsIgnoreCase(MetadataHelper.CHAR_N) ||
                        dataType.equalsIgnoreCase(MetadataHelper.BINARY_N)) {
                try {
                    value = Integer.parseInt(length);
                    if (value < MetadataHelper.MIN_FIELD_LENGTH ||
                            value > MetadataHelper.MAX_FIELD_LENGTH) {
                        String maxLen = String.valueOf(
                                            MetadataHelper.MAX_FIELD_LENGTH);
                        String minLen = String.valueOf(
                                            MetadataHelper.MIN_FIELD_LENGTH);
                        String str[] = new String[2];
                        str[0] = minLen;
                        str[1] = maxLen;
                        msg = GuiResources.getGuiString(
                             "config.metadata.namespace.field.fieldLengthRange",
                                                                        str);
                        rowNum = String.valueOf(row + 1);
                        setValidationErrMsg(msg + rowNum);
                        result = false;
                    }
                } catch (NumberFormatException ex) {
                    msg = GuiResources.getGuiString(
                          "config.metadata.namespace.field.fieldInvalidLength");
                    rowNum = String.valueOf(row + 1);
                    setValidationErrMsg(msg + rowNum);
                    result = false;
                }
            }
        }
        return result;
    }
     
    /** 
     * validate if Field Length is in the valid range or not - called after
     * clicking "OK" button in AddRemoveList / New Namespace panels
     */
    public boolean validateIsIntegerAllRows() {
        boolean result = true;
        int totalRows = 0;
        int value = 0;
        String msg = "";
        String rowNum = "";
        if (mdlListener != null) {
            totalRows = mdlListener.getRowCount();
            for (int i = 0; i < totalRows; i++) {
                Object lenObj = (Object)mdlListener
                                            .getValueAt(i, mdlListener.LENGTH);
                String length = MetadataHelper.objToString(lenObj);
                String dataType = (String)mdlListener
                                        .getValueAt(i, mdlListener.DATA_TYPE);
                length = length.trim();
                if (dataType.equalsIgnoreCase(MetadataHelper.STRING_N) ||
                    dataType.equalsIgnoreCase(MetadataHelper.CHAR_N) ||
                        dataType.equalsIgnoreCase(MetadataHelper.BINARY_N)) {
                    try {
                        value = Integer.parseInt(length);
                        if (value < MetadataHelper.MIN_FIELD_LENGTH || 
                                    value > MetadataHelper.MAX_FIELD_LENGTH) {
                            String maxLen = String.valueOf(
                                            MetadataHelper.MAX_FIELD_LENGTH);
                            String minLen = String.valueOf(
                                            MetadataHelper.MIN_FIELD_LENGTH);
                            String str[] = new String[2];
                            str[0] = minLen;
                            str[1] = maxLen;
                            msg = GuiResources.getGuiString(
                             "config.metadata.namespace.field.fieldLengthRange",
                                                                        str);
                            rowNum = String.valueOf(i + 1);
                            setIntegerErrMsg(msg + rowNum);
                            result = false;
                            break;
                        }
                    } catch (NumberFormatException ex) {
                        msg = GuiResources.getGuiString(
                          "config.metadata.namespace.field.fieldInvalidLength");
                        rowNum = String.valueOf(i + 1);
                        setIntegerErrMsg(msg + rowNum);
                        result = false;
                    }
                }
            }
        }
        return result;
    }
    /**
     * This method is used to set the check boxes Is-writable and Is-extensible
     * to false if parent values are false
     */
    private void handleSelectParentNamespace(ActionEvent evt) {
        // Is-writable and Is-extensible must be false if parent values
        // are false.
        JComboBox cbo = (JComboBox) evt.getSource();
        Namespace parent = getNamespace(root, (String)cbo.getSelectedItem());
        if (parent == null) {
            return;
        }
        if (parent.isWritable()) {
            chkIsWritable.setEnabled(true);
            chkIsWritable.setSelected(true);
        } else {
            // Writable must be false
            chkIsWritable.setEnabled(false);
            chkIsWritable.setSelected(false);
        }
        
        if (parent.isExtensible()) {
            chkIsExtensible.setEnabled(true);
            chkIsExtensible.setSelected(true);
        } else {
            // Extensible must be false
            chkIsExtensible.setEnabled(false);
            chkIsExtensible.setSelected(false);
        }
    }
    
    /**
     * Cancels the new namespace dialog on clicking "cancel" button, which just
     * quits without creating new namespace
     */
    protected void onDialogCancel() {
        setVisible(false);
        userCanceled = true;
    }
    
    /**
     * Call this method after the dialog closes to get 
     * the newly created Namespace
     */
    public Namespace getNewNamespace() { return newNamespace; }
    
    /**
     * This method will create a new namespace within this panel
     * with newly added Fields to the RootNamespace
     */ 
    private void handleBtnOkActionPerformed(ActionEvent evt) 
                                throws UIException, HostException {
        // Validate that all information entered into each table cell is
        // valid before creating the new namespace
        boolean validInteger = false;
        boolean emptyRows = validateAllRows();
        if (!emptyRows) {
            Log.logAndDisplayInfoMessage(getErrorMessage());
            return;
        } else {
            validInteger = validateIsIntegerAllRows();
            if (!validInteger) {
                Log.logAndDisplayInfoMessage(getIntegerErrMsg());
                return;
            }
        } 
        
        // Persist the new namespace entered by the user locally
        String namespaceName = txtfName.getText();
        if (namespaceName != null) {
            namespaceName = namespaceName.trim();
        }
        if (namespaceName == null || namespaceName.length() == 0) {
            txtfName.setText("");
            UserInfoMessage msg = new UserInfoMessage(
                    GuiResources.getGuiString(
                    "config.metadata.namespace.save.prompt.name"));
            throw new UIException(msg);
        }
        
        // Retrieve the parent namespace the user selected for the new ns
        String parentName = (String) cboParentNamespaces.getSelectedItem();
        
        // Check for duplicate Namespace
        String fullNamespacename = "";
        Namespace parent = getNamespace(root, parentName);
        if (parentName.equals(MetadataHelper.ROOT_NAME)) {
            // <root> namespace does not have a parent namespace
            fullNamespacename = namespaceName;
        } else {
            fullNamespacename = parentName + "." + namespaceName;
        }
        fullNamespacename = fullNamespacename.trim();

        if (parent != null) {
            // check to see if namespace user entered is already committed
            Namespace eNS = pnlSchema.getCommittedNS(fullNamespacename);
            if (eNS != null) {
                UserInfoMessage msg = new UserInfoMessage(
                     GuiResources.getGuiString(
                     "config.metadata.namespace.save.prompt.name.duplicate",
                     fullNamespacename));
                throw new UIException(msg);
            }

            // if it passes the above check for already existing namespaces,
            // then the uncommitted namespaces must be checked in
            // case the user created a namespace with the same name and  
            // did not commit it yet.
            Namespace newNS = pnlSchema.getNewNS(fullNamespacename);
            if (newNS != null) {
                UserInfoMessage msg = new UserInfoMessage(
                     GuiResources.getGuiString(
                     "config.metadata.namespace.save.prompt.name.duplicate",
                     fullNamespacename));
                throw new UIException(msg);
            }
        } else {
            UserInfoMessage msg = new UserInfoMessage(
                         GuiResources.getGuiString(
                         "config.metadata.namespace.newNamespace.error"));
                    throw new UIException(msg);     
        }
        
 
        // verify input for namespace name
        boolean valid = verifier.verify(txtfName);
        if (valid) {
            if (namespaceName.length() > 
                        MetadataHelper.MAX_NAMESPACE_NAME_LENGTH) {
                UserInfoMessage msg = new UserInfoMessage(
                        GuiResources.getGuiString(
                        "config.metadata.namespace.maxNameSize"));
                throw new UIException(msg);
            }
            if (namespaceName.indexOf(".") != -1) {
                UserInfoMessage msg = new UserInfoMessage(
                     GuiResources.getGuiString("config.metadata.namespace.dot",
                     namespaceName));
                throw new UIException(msg);
            }

            boolean isWritable = chkIsWritable.isSelected();
            boolean isExtensible = chkIsExtensible.isSelected();
            newNamespace = new Namespace(parent,
                                        namespaceName,
                                        isWritable,
                                        isExtensible,
                                        false);

            // Add fields 
            model = (PnlConfigDataNamespaceTableModel)arlFields.getTableModel();
            Map validRows = MetadataHelper.getUniqueFields(model, model.NAME);
            Iterator iter = validRows.keySet().iterator();
            while (iter.hasNext()) {
                int col = ((Integer)iter.next()).intValue();
                Integer type = (Integer)MetadataHelper.dataTypeStringToInt.get(
                        (String)model.getValueAt(col, model.DATA_TYPE));     
                Integer len = null;
                Object lenObj = (Object)model.getValueAt(col, model.LENGTH);   

                String str = MetadataHelper.objToString(lenObj);
                if ((str.trim()).equalsIgnoreCase(
                        MetadataHelper.NOT_APPLICABLE) ||
                                        str.trim().length() == 0) {
                    len = Integer.valueOf("0");
                } else {
                    len = Integer.valueOf(str);
                }
                Boolean isQueryable =
                        (Boolean) model.getValueAt(col, model.IS_QUERYABLE);
                String fieldName = (String)validRows.get(new Integer(col));
                Field field = new Field(newNamespace,
                                        fieldName,
                                        type.intValue(),
                                        len.intValue(),
                                        isQueryable.booleanValue(),
                                        false);

                newNamespace.addField(field);
            }
            setVisible(false);
            userCanceled = false;
        } else {
            setVisible(true);
            String info = verifier.getUserValidationMessage();
            UserInfoMessage msg = new UserInfoMessage(info);
            throw new UIException(msg);
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
        lblName = new javax.swing.JLabel();
        lblNameValue = new javax.swing.JLabel();
        txtfName = new javax.swing.JTextField();
        lblParentName = new javax.swing.JLabel();
        lblParentNameValue = new javax.swing.JLabel();
        cboParentNamespaces = new com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg();
        lblIsWritable = new javax.swing.JLabel();
        lblIsExtensible = new javax.swing.JLabel();
        chkIsWritable = new javax.swing.JCheckBox();
        chkIsExtensible = new javax.swing.JCheckBox();
        pnlFields = new javax.swing.JPanel();
        btnOk = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();
        scrlFields = new javax.swing.JScrollPane();
        tablFields = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        lblName.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.namespace.namespaceName.mn").charAt(0));
        lblName.setLabelFor(txtfName);
        lblName.setText(GuiResources.getGuiString("config.metadata.namespace.namespaceName"));

        lblNameValue.setText("jLabel2");

        txtfName.setColumns(15);
        txtfName.setText("jTextField1");

        lblParentName.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.namespace.parentName.mn").charAt(0));
        lblParentName.setLabelFor(cboParentNamespaces);
        lblParentName.setText(GuiResources.getGuiString("config.metadata.namespace.parentName"));

        lblParentNameValue.setText("jLabel4");

        cboParentNamespaces.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cboParentNamespaces.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboParentNamespacesActionPerformed(evt);
            }
        });

        lblIsWritable.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.namespace.isWritable.mn").charAt(0));
        lblIsWritable.setLabelFor(chkIsWritable);
        lblIsWritable.setText(GuiResources.getGuiString("config.metadata.namespace.isWritable"));

        lblIsExtensible.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.namespace.isExtensible.mn").charAt(0));
        lblIsExtensible.setLabelFor(chkIsExtensible);
        lblIsExtensible.setText(GuiResources.getGuiString("config.metadata.namespace.isExtensible"));

        chkIsWritable.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkIsWritable.setMargin(new java.awt.Insets(0, 0, 0, 0));

        chkIsExtensible.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkIsExtensible.setMargin(new java.awt.Insets(0, 0, 0, 0));

        org.jdesktop.layout.GroupLayout pnlFieldsLayout = new org.jdesktop.layout.GroupLayout(pnlFields);
        pnlFields.setLayout(pnlFieldsLayout);
        pnlFieldsLayout.setHorizontalGroup(
            pnlFieldsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 0, Short.MAX_VALUE)
        );
        pnlFieldsLayout.setVerticalGroup(
            pnlFieldsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 247, Short.MAX_VALUE)
        );

        btnOk.setMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.namespace.newNamespace.ok.mn").charAt(0));
        btnOk.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.namespace.newNamespace.ok"));
        btnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOkActionPerformed(evt);
            }
        });

        btnCancel.setMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.namespace.newNamespace.cancel.mn").charAt(0));
        btnCancel.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.namespace.newNamespace.cancel"));
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        scrlFields.setBorder(javax.swing.BorderFactory.createTitledBorder(GuiResources.getGuiString("config.metadata.namespace.fields")));
        scrlFields.setViewportView(tablFields);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(scrlFields, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 495, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(pnlFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(btnOk)
                        .add(14, 14, 14)
                        .add(btnCancel)
                        .addContainerGap())
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                                .add(lblIsExtensible)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 88, Short.MAX_VALUE)
                                .add(chkIsExtensible))
                            .add(layout.createSequentialGroup()
                                .add(lblIsWritable)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 100, Short.MAX_VALUE)
                                .add(chkIsWritable))
                            .add(org.jdesktop.layout.GroupLayout.LEADING, lblName)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, lblParentName))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(lblNameValue)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(txtfName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(layout.createSequentialGroup()
                                .add(lblParentNameValue)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(cboParentNamespaces, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .add(129, 129, 129))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblName)
                    .add(lblNameValue)
                    .add(txtfName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblParentName)
                    .add(lblParentNameValue)
                    .add(cboParentNamespaces, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblIsExtensible)
                    .add(chkIsExtensible))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(chkIsWritable)
                    .add(lblIsWritable))
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(30, 30, 30)
                        .add(pnlFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(layout.createSequentialGroup()
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(scrlFields, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 247, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(btnCancel)
                    .add(btnOk))
                .addContainerGap())
        );
        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        onDialogCancel();
    }//GEN-LAST:event_btnCancelActionPerformed

    private void btnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOkActionPerformed
        try {
            handleBtnOkActionPerformed(evt);
        } catch (UIException ex) {
            Log.logAndDisplayInfoMessage(ex.getMessage());
        } catch (HostException ex) {
            Log.logAndDisplayInfoMessage(ex.getMessage());
        }
    }//GEN-LAST:event_btnOkActionPerformed

    private void cboParentNamespacesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboParentNamespacesActionPerformed
        handleSelectParentNamespace(evt);
    }//GEN-LAST:event_cboParentNamespacesActionPerformed
   
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnOk;
    private com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg cboParentNamespaces;
    private javax.swing.JCheckBox chkIsExtensible;
    private javax.swing.JCheckBox chkIsWritable;
    private javax.swing.JLabel lblIsExtensible;
    private javax.swing.JLabel lblIsWritable;
    private javax.swing.JLabel lblName;
    private javax.swing.JLabel lblNameValue;
    private javax.swing.JLabel lblParentName;
    private javax.swing.JLabel lblParentNameValue;
    private javax.swing.JPanel pnlFields;
    private javax.swing.JScrollPane scrlFields;
    private javax.swing.JTable tablFields;
    private javax.swing.JTextField txtfName;
    // End of variables declaration//GEN-END:variables
    
}
