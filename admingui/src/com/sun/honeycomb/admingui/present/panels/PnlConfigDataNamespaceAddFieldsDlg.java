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
 * AboutDialog.java
 *
 * Created on February 17, 2006, 1:16 PM
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.nws.mozart.ui.BaseDialog;
//import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveList;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListValidator;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.awt.event.ActionEvent;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author  dp127224
 */
public class PnlConfigDataNamespaceAddFieldsDlg extends BaseDialog 
                                            implements AddRemoveListValidator {

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
 
    private boolean didUserCancel = false;
    
    // Class for editing cell as JFormattedTextField for FieldLength column
    public class IntegerEditor extends DefaultCellEditor {
        public IntegerEditor(JFormattedTextField jft) {
            super(new JFormattedTextField());
            JTextField jf = (JTextField) getComponent();
            jft = (JFormattedTextField) jf;
            jft.setBorder(null);
        }
    }  
           
    /** Creates new form AboutDialog */
    public PnlConfigDataNamespaceAddFieldsDlg(java.awt.Frame parent, 
                                                PnlConfigMetadataSchema schema,
                                                boolean modal) {
        super(parent, modal);
        pnlSchema = schema;
        initComponents();
        initDialog();        
    }

    // Add remove list initialized by caller.
    protected void initDialog() {
        super.initDialog(btnOk);        
        
        setupAddRemoveFieldsList();
    }
    
    protected void onDialogCancel() {
        setVisible(false);
    }
    
    public boolean getDidUserCancel() { return didUserCancel; }

    public AddRemoveList getFieldsTableComponent() { return arlFields; }
    
    /**
     * Called when user clicks "OK" button the new namespace panel, which 
     * validates the data and adds newly created fields to existing Namespace
     */
    private void handleOK(ActionEvent evt) { 
        
        // if pressing OK, then the user should have added new fields to ns.
        if (model.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, 
                GuiResources.getGuiString(
                    "config.metadata.schema.ns.field.add.info"),
                getTitle(),
                JOptionPane.INFORMATION_MESSAGE);
            return;     
        } 
        boolean emptyRows = validateAllRows();
        boolean validInteger = false;
        
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
        
        setVisible(false);
        didUserCancel = false;          
    }
    
    /**
     * Close the new namespace dialog on clicking "Cancel" button without
     * creating new namespace
     */
    private void handleCancel(ActionEvent evt) {       
        setVisible(false);
        didUserCancel = true;      
    }         
    
    public void setupAddRemoveFieldsList()  {               
        arlFields.setValidator(this);        
        setValidationErrMsg("");
       
        // Clearing all previous rows from AddRemoveList buffer if any
        if (mdlListener != null) {
            mdlListener.removeAll();
        }

        model = new PnlConfigDataNamespaceTableModel(true, true,
                            PnlConfigDataNamespaceTableModel.TYPE_FIELDS);
        arlFields.setTableModel(model);
        arlFields.setAddButtonTooltip(GuiResources.getGuiString(
                                    "config.metadata.namespace.addTooltip"));
        arlFields.setRemoveButtonTooltip(GuiResources.getGuiString(
                                    "config.metadata.namespace.removeTooltip"));
   
        // Table cell editors
        colModel = arlFields.getTableColumnModel();

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
         ListSelectionModel selModel = arlFields.getTableSelectionModel();
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
                             (PnlConfigDataNamespaceTableModel)e.getSource();
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
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        btnOk = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();
        arlFields = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(GuiResources.getGuiString("config.metadata.namespace.addFieldsDlg.title"));
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

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .add(arlFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE))
                    .add(layout.createSequentialGroup()
                        .addContainerGap(295, Short.MAX_VALUE)
                        .add(btnOk)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(btnCancel)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(arlFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
                .add(27, 27, 27)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(btnCancel)
                    .add(btnOk))
                .addContainerGap())
        );
        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOkActionPerformed
        handleOK(evt);
    }//GEN-LAST:event_btnOkActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        handleCancel(evt);
    }//GEN-LAST:event_btnCancelActionPerformed
   
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlFields;
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnOk;
    // End of variables declaration//GEN-END:variables
    
}
