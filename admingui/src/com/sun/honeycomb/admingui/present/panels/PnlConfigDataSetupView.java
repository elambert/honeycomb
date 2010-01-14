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

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemConfigMetadataView;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.Filename;
import com.sun.honeycomb.emd.config.FsAttribute;
import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.parsers.FilenameParser;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.SelectOrderedList;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.CacheOperations;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.UserErrorMessage;
import com.sun.nws.mozart.ui.utility.UserMessage;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

public class PnlConfigDataSetupView extends javax.swing.JPanel 
                                            implements ContentPanel {
    
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private RootNamespace root = null;
    private boolean readOnlyView = false;
    private boolean leafLevelOnlyFiles = true;
    private PnlConfigDataViewTableModel sourceModel = null;
    private PnlConfigDataViewTableModel targetModel = null;
    private StringBuffer validationError = new StringBuffer();
    
    /** This is the virtual filesystem/view created when user clicks APPLY */
    private FsView newView = null;  
    
    private TextInputVerifier verifier = new TextInputVerifier(
                                    TextInputVerifier.TBL_VIEW_FIELD_NAMES);
    
    /** Creates new form PnlConfigDataSetupView */
    public PnlConfigDataSetupView() throws HostException {
        initComponents();
        
        try {
            initComponents2();
        } catch (Exception ex) {
            // exception will get handled by the 
            // HoneycombCatchAllExceptionHandler
            throw new RuntimeException(ex);
        }
    }
        
    private void initComponents2() throws UIException {
        
        // Select ordered list
        solFields.setSourceLabel(GuiResources.getGuiString(
                        "config.metadata.views.dlg.availableFields"),
                         GuiResources.getGuiString(
                        "config.metadata.views.dlg.availableFields.mn"));
        solFields.setTargetLabel(GuiResources.getGuiString(
                        "config.metadata.views.dlg.selectedFields"),
                         GuiResources.getGuiString(
                        "config.metadata.views.dlg.selectedFieldsTable.mn"));
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
        
        txtViewName.setInputVerifier(verifier);
        
        sourceModel = new PnlConfigDataViewTableModel(
                    PnlConfigDataViewTableModel.TYPE_NO_UNSETVAL, true);
        targetModel = new PnlConfigDataViewTableModel(
                    PnlConfigDataViewTableModel.TYPE_RW_UNSETVAL, false);
        targetModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent evt) {
                handleTargetTableModelChanged(evt);
            }
        });
        // set tooltips for field names in the select ordered list since the
        // field names may be very long
        solFields.setTableCellRenderer(
                   Object.class, new TooltipCellRenderer(
                      new Integer[]{new Integer(sourceModel.NAME)}), 
                        SelectOrderedList.SOURCE);
        solFields.setTableCellRenderer(
                   Object.class, new TooltipCellRenderer(
                      new Integer[]{new Integer(targetModel.NAME)}), 
                        SelectOrderedList.TARGET);
        
        // Add a listener to activate APPLY and CANCEL buttons
        txtViewName.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent evt) {
                txtKeyTyped(evt);
            }
        });
        
        txtfPattern.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent evt) {
                txtKeyTyped(evt);
            }
        });
        
        // Add a listener to enable this view to be read-only or not
        cbReadOnly.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    readOnlyView = cbReadOnly.isSelected();
                    if (newView != null) {
                        newView.setReadOnly(readOnlyView);
                    }
                }
            }
        );
        
        // Add a listener to enable the files to be shown either at leaf level
        // or not (default is at leaf level)
        cbLeafLevelOnlyFiles.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    leafLevelOnlyFiles = cbLeafLevelOnlyFiles.isSelected();
                    if (newView != null) {
                        // Fix for CR6641952 -- collapsetrailingnulls and 
                        // filesonlyatleaflevel are opposites
                        newView.setCollapsingNulls(!leafLevelOnlyFiles);
                    }
                }
            }
        );    
        /*
         * deep archiving feature has been removed by backend in 1.1,
         * therefore we hide the gui JPanel counterpart
         */
        this.pnlArchiveBrowsing.setVisible(false);
    }
    
    // Enable the APPLY and CANCEL buttons
    private void txtKeyTyped(KeyEvent evt) {
        explItem.setIsModified(true);
    }
       
    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("config.metadata.setup.view.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_BOTH; }
    public ButtonPanel getButtonPanel() {
        ButtonPanel bp = null;
        if (ObjectFactory.isAdmin()) {
            bp = new BtnPnlApplyCancel();
            BtnPnlApplyCancel bac = (BtnPnlApplyCancel)bp;
            bac.setShowApplyButton(true);
            bac.setShowCancelButton(true);
//            bac.setRefreshPanelOnApply(false); // CR 6512443
            bac.setPanelValidator(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!validateInput()) {
                        // invalid data
                        explItem.setIsErrorOnPanel(true);
                        Log.logInfoMessage(GuiResources.getGuiString(
                                "notSaved",
                                validationError.toString()));
                        throw new RuntimeException(
                                new ClientException("data.validation.error"));
                    }
                }
            });
        } else {
            bp = BtnPnlBlank.getButtonPanel();
        }
        return bp;
    }
    
    public void loadValues() throws UIException, HostException {
        
        // initialize values
        sourceModel.removeAll();
        targetModel.removeAll();
        newView = null;
        readOnlyView = false;
        leafLevelOnlyFiles = true;
        txtViewName.setText("");
        cbReadOnly.setSelected(readOnlyView);
        cbLeafLevelOnlyFiles.setSelected(leafLevelOnlyFiles); //checkbox
        txtfPattern.setText("");

        chkArchiveTypeCpio.setSelected(false);
        chkArchiveTypeIso.setSelected(false);
        chkArchiveTypeTar.setSelected(false);
        chkArchiveTypeZip.setSelected(false);

        cboSelectedFields.removeAllItems();
        cboSelectedFields.setEnabled(false);
        btnAddToPattern.setEnabled(false);
        
        // Get root namespace
        try {
            root = hostConn.getMetadataConfig();
        } catch (Exception ex) {
            // exception will get handled by the 
            // HoneycombCatchAllExceptionHandler
            throw new RuntimeException(ex);
        }

        // Load fields
        ArrayList fields = new ArrayList();
        root.getFields(fields, true);
        int cnt = fields.size();
        String cLength = null;
        for (int i = 0; i < cnt; i++)
        {
            Field field = (Field) fields.get(i);
            if (field.isQueryable()) {
                if (field.getLength() <= 0) {
                    cLength = MetadataHelper.NOT_APPLICABLE;
                } else {
                    // length of the column
                    cLength = Integer.toString(field.getLength());
                }
                String strType = MetadataHelper.toTypeWithLength(
                                    new Integer(field.getType()), cLength);
                sourceModel.addRow(new Object[] {
                    field.getQualifiedName(), strType
                });
            }
        }
        // set the source table model AFTER the data has been populated to 
        // ensure the various selected ordered list buttons are
        // enabled/disabled accordingly
        solFields.setSourceTableModel(sourceModel);
        solFields.setTargetTableModel(targetModel);    

        // disable all items if user is in read only 
        if (!ObjectFactory.isAdmin()) {
            disableAllComponents(this.getComponents());
        }

    }
     
    public void saveValues() throws UIException, HostException { 
        StringBuffer message = new StringBuffer();
        PnlConfigDataViewTableModel attrsModel = (PnlConfigDataViewTableModel)
                                                solFields.getTargetTableModel();
        String viewName = txtViewName.getText().trim();
        String pattern = txtfPattern.getText().trim();
        FsView[] views = root.getViews();
  
        // So far so good.  Create new view
        newView = new FsView(viewName, null, false);    
        newView.setReadOnly(readOnlyView);
        // Fix for CR6641952 -- collapsetrailingnulls and filesonlyatleaflevel
        // are opposites
        newView.setCollapsingNulls(!leafLevelOnlyFiles);
        Filename filename = null;
        try{
            filename = FilenameParser.parse(pattern, newView, root);
        } catch (Throwable t) {
            // this shouldn't happen since the validation is performed
            // prior to the save in validateInpu
            validationError.append(GuiResources.getGuiString(
                                "config.metadata.views.dlg.save.prompt.badPattern",
                                t.getLocalizedMessage()));
        }
           
        // Archive types
        ArrayList archTypes = new ArrayList();
        if (chkArchiveTypeTar.isSelected()) {
            archTypes.add(MetadataHelper.ARCH_TYPE_TAR);
        }
        if (chkArchiveTypeCpio.isSelected()) {
            archTypes.add(MetadataHelper.ARCH_TYPE_CPIO);
        }
        if (chkArchiveTypeZip.isSelected()) {
            archTypes.add(MetadataHelper.ARCH_TYPE_ZIP);
        }
        if (chkArchiveTypeIso.isSelected()) {
            archTypes.add(MetadataHelper.ARCH_TYPE_ISO);
        }
        if (archTypes.size() > 0) {
            String[] attrTypesStr = (String[])
                            archTypes.toArray(new String[archTypes.size()]);
            newView.setArchiveTypes(attrTypesStr);
        }

        // Include FS attrs
        newView.setUsesExtendedAttrs(chkIncludeFSFields.isSelected());

        // Attributes
        int cnt = attrsModel.getRowCount();
        for (int i = 0; i < cnt; i++) {
            // Get namespace field
            String fieldName = (String) attrsModel.getValueAt(i, 
                                        PnlConfigDataViewTableModel.NAME);
            // do not remove - may be used in the future
//            String unsetValue = (String) attrsModel.getValueAt(i, 
//                                    PnlConfigDataViewTableModel.UNSET_VALUE);
            String unsetValue = "";
            Field field = root.resolveField(fieldName);
            FsAttribute attr = new FsAttribute(field, unsetValue, newView);
            newView.addAttribute(attr);
        }

        // Filename pattern
        newView.setFilename(filename);

        try {
            root.addFsView(newView);
        } catch (EMDConfigException e) {
            message.append(GuiResources.getMsgString(
                    "error.problem.creatingView")).append("\n").
                    append(e.getLocalizedMessage()).append("\n").
                    append(GuiResources.getMsgString(
                        "error.solution.tryAgainOrContactAdmin"));
            JOptionPane.showMessageDialog(
                        MainFrame.getMainFrame(),
                        message.toString(),
                        GuiResources.getGuiString("app.name"),
                        JOptionPane.ERROR_MESSAGE);
            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                    GuiResources.getGuiString(
                        "config.metadata.setup.view.notSaved", 
                                        e.getLocalizedMessage()), null); 
            return;
        }

        try {
            hostConn.setMetadataConfig(root);
        } catch (Exception ex) {
            // exception will get handled by the 
            // HoneycombCatchAllExceptionHandler
            throw new RuntimeException(ex);
        }

        // notify user that the view has been successfully created
        Log.logInfoMessage(GuiResources.getGuiString(
                                    "config.metadata.setup.view.created",
                                               new String[] {viewName}));

        // Flush cache
        ObjectFactory.clearCache();

        ExplItemConfigMetadataView view = new ExplItemConfigMetadataView();

        explItem.setIsModified(false);
        MainFrame mainFrame = MainFrame.getMainFrame();    
        mainFrame.selectExplorerItem(view);
    }
     
    // *************************************************
    public boolean validateInput() {
        validationError = new StringBuffer();
        try {
            // Check values
            PnlConfigDataViewTableModel attrsModel = (PnlConfigDataViewTableModel)
                                                    solFields.getTargetTableModel();
            int errCount = 0;
            String viewName = txtViewName.getText().trim();
            String pattern = txtfPattern.getText().trim();
            FsView[] views = root.getViews();

            if (viewName.length() == 0) {
                txtViewName.setText("");
                validationError.append(GuiResources.getGuiString(
                            "config.metadata.views.dlg.save.prompt.viewName"));
                errCount++;
            } 
            // verify input for namespace name
            boolean valid = verifier.verify(txtViewName);
            if (valid) {
                if (attrsModel.getRowCount() == 0) {
                    validationError.append(GuiResources.getGuiString(
                                "config.metadata.views.dlg.save.prompt.fields"));
                    errCount++;
                }
                if (pattern.length() == 0) {
                    txtfPattern.setText("");
                    validationError.append(GuiResources.getGuiString(
                                "config.metadata.views.dlg.save.prompt.pattern"));
                    errCount++;
                } 
                // check if this is a duplicate view -- 
                if (views.length != 0 || views != null) {
                    for (int idx = 0; idx < views.length; idx++) {
                        FsView view = (FsView)views[idx];
                        if (viewName.equals(view.getName())) {
                            validationError.append(GuiResources.getGuiString(
                        "config.metadata.views.dlg.save.prompt.duplicateViewName"));
                            errCount++;
                        }
                    }    
                } 

                // Validate pattern
                Filename filename = null;
                if (validationError.length() == 0) {            
                    // So far so good.  Create new view in order to validate pattern
                    newView = new FsView(viewName, null, false);    
                    try {
                        filename = FilenameParser.parse(pattern, newView, root);
                    } catch (Throwable t) {
                        validationError.append(GuiResources.getGuiString(
                                "config.metadata.views.dlg.save.prompt.badPattern",
                                t.getLocalizedMessage()));
                    }
                }

                if (validationError.length() > 0) {
                    if (errCount > 1) {
                        // there is more than one error message
                        validationError.insert(0, GuiResources.getGuiString(
                          "config.metadata.views.dlg.save.prompt.multipleErrors"));
                    }
                    JOptionPane.showMessageDialog(this, 
                            validationError.toString(),
                            getTitle(),
                            JOptionPane.INFORMATION_MESSAGE);
                    Log.logInfoMessage(GuiResources.getGuiString(
                                    "config.metadata.setup.view.notSaved", 
                                                        validationError.toString()));
                    return false;               
                } else {
                    return true;
                }
            } else {
                String info = verifier.getUserValidationMessage();
                validationError.append(info);
                JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            validationError.toString(),
                            GuiResources.getGuiString("app.name"),
                            JOptionPane.ERROR_MESSAGE);
                Log.logToStatusAreaAndExternal(Level.SEVERE, 
                            GuiResources.getGuiString(
                                "config.metadata.setup.view.notSaved", 
                                            validationError.toString()), null);  
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }  
    // ***************************
    // Handlers
    // ***************************
    
    private void handleTargetTableModelChanged(TableModelEvent evt) {
        // Update selected fields combo box.
        PnlConfigDataViewTableModel model = (PnlConfigDataViewTableModel)
                                                evt.getSource();            
        int type = evt.getType();
        if (type == TableModelEvent.INSERT ||
            type == TableModelEvent.DELETE) {
            // Reload fields in combo
            cboSelectedFields.removeAllItems();
            int cnt = model.getRowCount();
            if (cnt == 0) {
                cboSelectedFields.setEnabled(false);
                btnAddToPattern.setEnabled(false);
            } else {
                cboSelectedFields.setEnabled(true);
                btnAddToPattern.setEnabled(true);
                for (int i = 0; i < cnt; i++) {
                    String fieldName = (String) model.getValueAt(i, 
                                              PnlConfigDataViewTableModel.NAME);
                    cboSelectedFields.addItem(fieldName);
                }
            }
        }        
        
        explItem.setIsModified(true);
    }
    
    private void handleAddToPattern(ActionEvent evt) {
        // Take currently selected field and put token into file name 
        // pattern at caret position.
        // Tokens have the format ${fullyQualifiedFieldName}
        String fieldName = (String) cboSelectedFields.getSelectedItem();
        if (fieldName == null) {
            return;
        }
        String token = "${" + fieldName + "}";
        
        txtfPattern.replaceSelection(token);
        
        explItem.setIsModified(true);
    }

    private void disableAllComponents(Component [] allComponents) {
        for (int i = 0; i < allComponents.length; i++) {
            if (allComponents[i] instanceof JPanel) {
                disableAllComponents(
                    ((JPanel)allComponents[i]).getComponents());
            } else {
                allComponents[i].setEnabled(false);
            }
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
        txtViewName = new javax.swing.JTextField();
        pnlArchiveBrowsing = new javax.swing.JPanel();
        chkArchiveTypeTar = new javax.swing.JCheckBox();
        chkArchiveTypeCpio = new javax.swing.JCheckBox();
        chkArchiveTypeIso = new javax.swing.JCheckBox();
        chkArchiveTypeZip = new javax.swing.JCheckBox();
        pnlFields = new javax.swing.JPanel();
        solFields = new com.sun.nws.mozart.ui.swingextensions.SelectOrderedList();
        chkIncludeFSFields = new javax.swing.JCheckBox();
        pnlFileNamePattern = new javax.swing.JPanel();
        lblPattern = new javax.swing.JLabel();
        txtfPattern = new javax.swing.JTextField();
        lblSelectedFields = new javax.swing.JLabel();
        cboSelectedFields = new javax.swing.JComboBox();
        btnAddToPattern = new javax.swing.JButton();
        cbReadOnly = new javax.swing.JCheckBox();
        cbLeafLevelOnlyFiles = new javax.swing.JCheckBox();

        lblViewName.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.views.dlg.viewName.mn").charAt(0));
        lblViewName.setLabelFor(txtViewName);
        lblViewName.setText(GuiResources.getGuiString("config.metadata.views.dlg.viewName"));

        pnlArchiveBrowsing.setBorder(javax.swing.BorderFactory.createTitledBorder(GuiResources.getGuiString("config.metadata.views.dlg.lookInArchives")));
        chkArchiveTypeTar.setMnemonic(GuiResources.getGuiString("config.metadata.views.lookInArchives.tar.mn").charAt(0));
        chkArchiveTypeTar.setText(GuiResources.getGuiString("config.metadata.views.lookInArchives.tar"));
        chkArchiveTypeTar.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkArchiveTypeTar.setMargin(new java.awt.Insets(0, 0, 0, 0));

        chkArchiveTypeCpio.setMnemonic(GuiResources.getGuiString("config.metadata.views.lookInArchives.cpio.mn").charAt(0));
        chkArchiveTypeCpio.setText(GuiResources.getGuiString("config.metadata.views.lookInArchives.cpio"));
        chkArchiveTypeCpio.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkArchiveTypeCpio.setMargin(new java.awt.Insets(0, 0, 0, 0));

        chkArchiveTypeIso.setMnemonic(GuiResources.getGuiString("config.metadata.views.lookInArchives.iso.mn").charAt(0));
        chkArchiveTypeIso.setText(GuiResources.getGuiString("config.metadata.views.lookInArchives.iso"));
        chkArchiveTypeIso.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkArchiveTypeIso.setMargin(new java.awt.Insets(0, 0, 0, 0));

        chkArchiveTypeZip.setMnemonic(GuiResources.getGuiString("config.metadata.views.lookInArchives.zip.mn").charAt(0));
        chkArchiveTypeZip.setText(GuiResources.getGuiString("config.metadata.views.lookInArchives.zip"));
        chkArchiveTypeZip.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkArchiveTypeZip.setMargin(new java.awt.Insets(0, 0, 0, 0));

        org.jdesktop.layout.GroupLayout pnlArchiveBrowsingLayout = new org.jdesktop.layout.GroupLayout(pnlArchiveBrowsing);
        pnlArchiveBrowsing.setLayout(pnlArchiveBrowsingLayout);
        pnlArchiveBrowsingLayout.setHorizontalGroup(
            pnlArchiveBrowsingLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlArchiveBrowsingLayout.createSequentialGroup()
                .add(21, 21, 21)
                .add(chkArchiveTypeTar)
                .add(15, 15, 15)
                .add(chkArchiveTypeCpio)
                .add(19, 19, 19)
                .add(chkArchiveTypeZip)
                .add(23, 23, 23)
                .add(chkArchiveTypeIso)
                .addContainerGap(336, Short.MAX_VALUE))
        );
        pnlArchiveBrowsingLayout.setVerticalGroup(
            pnlArchiveBrowsingLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlArchiveBrowsingLayout.createSequentialGroup()
                .add(pnlArchiveBrowsingLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(chkArchiveTypeZip, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(chkArchiveTypeIso, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(chkArchiveTypeTar, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(chkArchiveTypeCpio, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        pnlFields.setBorder(javax.swing.BorderFactory.createTitledBorder(GuiResources.getGuiString("config.metadata.views.dlg.fields")));

        chkIncludeFSFields.setMnemonic(GuiResources.getGuiString("config.metadata.views.includeFSFields.mn").charAt(0));
        chkIncludeFSFields.setText(GuiResources.getGuiString("config.metadata.views.includeFSFields"));
        chkIncludeFSFields.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkIncludeFSFields.setMargin(new java.awt.Insets(0, 0, 0, 0));

        org.jdesktop.layout.GroupLayout pnlFieldsLayout = new org.jdesktop.layout.GroupLayout(pnlFields);
        pnlFields.setLayout(pnlFieldsLayout);
        pnlFieldsLayout.setHorizontalGroup(
            pnlFieldsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFieldsLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlFieldsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(chkIncludeFSFields)
                    .add(solFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 677, Short.MAX_VALUE))
                .addContainerGap())
        );
        pnlFieldsLayout.setVerticalGroup(
            pnlFieldsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFieldsLayout.createSequentialGroup()
                .add(chkIncludeFSFields)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(solFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 187, Short.MAX_VALUE)
                .addContainerGap())
        );

        pnlFileNamePattern.setBorder(javax.swing.BorderFactory.createTitledBorder(GuiResources.getGuiString("config.metadata.views.dlg.fileNamePattern")));
        lblPattern.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.views.dlg.pattern.mn").charAt(0));
        lblPattern.setLabelFor(txtfPattern);
        lblPattern.setText(GuiResources.getGuiString("config.metadata.views.dlg.pattern"));

        lblSelectedFields.setDisplayedMnemonic(GuiResources.getGuiString("config.metadata.views.dlg.selectedFields.mn").charAt(0));
        lblSelectedFields.setLabelFor(cboSelectedFields);
        lblSelectedFields.setText(GuiResources.getGuiString("config.metadata.views.dlg.selectedFields"));

        cboSelectedFields.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        btnAddToPattern.setMnemonic(GuiResources.getGuiString("config.metadata.views.dlg.addToPattern.mn").charAt(0));
        btnAddToPattern.setText(GuiResources.getGuiString("config.metadata.views.dlg.addToPattern"));
        btnAddToPattern.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddToPatternActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout pnlFileNamePatternLayout = new org.jdesktop.layout.GroupLayout(pnlFileNamePattern);
        pnlFileNamePattern.setLayout(pnlFileNamePatternLayout);
        pnlFileNamePatternLayout.setHorizontalGroup(
            pnlFileNamePatternLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFileNamePatternLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlFileNamePatternLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblSelectedFields)
                    .add(lblPattern))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlFileNamePatternLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(pnlFileNamePatternLayout.createSequentialGroup()
                        .add(cboSelectedFields, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(btnAddToPattern))
                    .add(txtfPattern, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 534, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(54, Short.MAX_VALUE))
        );
        pnlFileNamePatternLayout.setVerticalGroup(
            pnlFileNamePatternLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFileNamePatternLayout.createSequentialGroup()
                .add(pnlFileNamePatternLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblSelectedFields)
                    .add(cboSelectedFields, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(btnAddToPattern))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlFileNamePatternLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblPattern)
                    .add(txtfPattern, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(13, 13, 13))
        );

        cbReadOnly.setMnemonic(GuiResources.getGuiString("config.metadata.views.dlg.readOnly.mn").charAt(0));
        cbReadOnly.setText(GuiResources.getGuiString("config.metadata.views.dlg.readOnly"));
        cbReadOnly.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        cbReadOnly.setMargin(new java.awt.Insets(0, 0, 0, 0));

        cbLeafLevelOnlyFiles.setMnemonic(GuiResources.getGuiString("config.metadata.views.dlg.leafLevelFilesOnly.mn").charAt(0));
        cbLeafLevelOnlyFiles.setText(GuiResources.getGuiString("config.metadata.views.dlg.leafLevelFilesOnly"));
        cbLeafLevelOnlyFiles.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        cbLeafLevelOnlyFiles.setMargin(new java.awt.Insets(0, 0, 0, 0));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(cbReadOnly)
                        .add(32, 32, 32)
                        .add(cbLeafLevelOnlyFiles)
                        .addContainerGap())
                    .add(layout.createSequentialGroup()
                        .add(lblViewName)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(txtViewName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 261, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(391, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, pnlFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, pnlFileNamePattern, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, pnlArchiveBrowsing, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .add(20, 20, 20))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblViewName)
                    .add(txtViewName, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(cbReadOnly)
                    .add(cbLeafLevelOnlyFiles))
                .add(21, 21, 21)
                .add(pnlFields, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlFileNamePattern, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 93, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlArchiveBrowsing, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(22, 22, 22))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btnAddToPatternActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddToPatternActionPerformed
        handleAddToPattern(evt);
    }//GEN-LAST:event_btnAddToPatternActionPerformed
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddToPattern;
    private javax.swing.JCheckBox cbLeafLevelOnlyFiles;
    private javax.swing.JCheckBox cbReadOnly;
    private javax.swing.JComboBox cboSelectedFields;
    private javax.swing.JCheckBox chkArchiveTypeCpio;
    private javax.swing.JCheckBox chkArchiveTypeIso;
    private javax.swing.JCheckBox chkArchiveTypeTar;
    private javax.swing.JCheckBox chkArchiveTypeZip;
    private javax.swing.JCheckBox chkIncludeFSFields;
    private javax.swing.JLabel lblPattern;
    private javax.swing.JLabel lblSelectedFields;
    private javax.swing.JLabel lblViewName;
    private javax.swing.JPanel pnlArchiveBrowsing;
    private javax.swing.JPanel pnlFields;
    private javax.swing.JPanel pnlFileNamePattern;
    private com.sun.nws.mozart.ui.swingextensions.SelectOrderedList solFields;
    private javax.swing.JTextField txtViewName;
    private javax.swing.JTextField txtfPattern;
    // End of variables declaration//GEN-END:variables
    
}
