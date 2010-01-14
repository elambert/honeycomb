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
 * PanelAdmin.java
 *
 * Created on February 9, 2006, 10:54 AM
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author  dp127224
 */
public class PnlConfigNotifEmail extends JPanel implements ContentPanel {
            
    public static final String TYPE_TO = GuiResources.getGuiString(
            "config.notif.email.type.to");
    public static final String TYPE_CC = GuiResources.getGuiString(
            "config.notif.email.type.cc");

    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private StringBuffer validationError = new StringBuffer(); 
    private BtnPnlApplyCancel bPanel = new BtnPnlApplyCancel();
    
    /**
     * Creates new form PanelAdmin
     */
    public PnlConfigNotifEmail() {
        
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
        
        // Table model
        PnlConfigNotifEmailTableModel model = 
                new PnlConfigNotifEmailTableModel();
        arlEmailAddresses.setTableModel(model);
        arlEmailAddresses.setAddButtonTooltip(GuiResources.getGuiString("" +
                "config.notif.email.addTooltip"));
        arlEmailAddresses.setRemoveButtonTooltip(GuiResources.getGuiString("" +
                "config.notif.email.removeTooltip"));

        // Setup combobox editor in Type column
        
        // Populate combobox
        JComboBox cbo = new JComboBox();
        cbo.addItem(GuiResources.getGuiString(
                "config.notif.email.type.default"));
        cbo.addItem(TYPE_TO);
        cbo.addItem(TYPE_CC);
        
        // Add table editor
        TableColumnModel colModel = arlEmailAddresses.getTableColumnModel();
        TableColumn typeCol = colModel.getColumn(
                PnlConfigNotifEmailTableModel.TYPE);
        typeCol.setCellEditor(new DefaultCellEditor(cbo));
        
        bPanel.setShowApplyButton(true);
        bPanel.setShowCancelButton(true);
        // validates panel input at the time the Apply button is selected
        bPanel.setPanelValidator(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!ObjectFactory.switchesOK()) {
                    bPanel.setRefreshOnValidationFailure(true);
                    throw new RuntimeException(
                            new ClientException("data.validation.error"));
                }
                if (!validationCheck()) {
                    // invalid data
                    Log.logInfoMessage(GuiResources.getGuiString(
                        "config.notif.email.notSaved", 
                        validationError.toString()));
                    throw new RuntimeException(
                        new ClientException("data.validation.error"));
                }
            }
        });
        
        // Add table model listeners
        model.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                handleArlEmailAdressesTableChanged(e);
            }
        });
    }
    
    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("config.notif.email.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_NONE; }
    public ButtonPanel getButtonPanel() {
         ButtonPanel bp = null;
         if (ObjectFactory.isAdmin()) {
             bp = bPanel;
         } else {
             bp = BtnPnlBlank.getButtonPanel();
         }
         return bp;
     }
    
    public void loadValues() throws UIException, HostException {
        // disable items if user is in read only mode
        if (!ObjectFactory.isAdmin()) {
            Component [] allComponents = this.getComponents();
            for (int i = 0; i < allComponents.length; i++) {
                allComponents[i].setEnabled(false);
            }
        }

        String addr = "";
        ipaSmtpServer.setText("0.0.0.0");
        txtSmtpHost.setText("");
        try {
            addr = hostConn.getAddress(AdminApi.ADDRT_SMTP_IP);
             if (isValidIpAddress(addr)) {
                ipaSmtpServer.setText(addr);
                btnSmtpIPAddress.setSelected(true);                
             }
             else {
                txtSmtpHost.setText(addr);
                btnSmtpHostName.setSelected(true);
             }
            

            // JFormattedTextField editor puts in a Long, se we need to 
            // use a long too
            txtSmtpPort.setValue(new Long(hostConn.getPort(AdminApi.PORTT_SMTP)));

            PnlConfigNotifEmailTableModel model = 
                    (PnlConfigNotifEmailTableModel)
                        arlEmailAddresses.getTableModel();
            model.removeAll();

            String[] emailsTo = hostConn.getEmailsTo();
            if (emailsTo != null) {
                for (int i = 0; i < emailsTo.length; i++) {
                    String email = emailsTo[i];
                    if (email != null && email.length() > 0) {
                        model.addRow(new Object[] {
                                        TYPE_TO,
                                        email
                        });
                    }
                }
            }
            String[] emailsCc = hostConn.getEmailsCc();
            if (emailsCc != null) {
                for (int i = 0; i < emailsCc.length; i++) {
                    String email = emailsCc[i];
                    if (email != null && email.length() > 0) {
                        model.addRow(new Object[] {
                                        TYPE_CC,
                                        email
                        });
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }    
    }
    
    public void saveValues() throws HostException, UIException {
        try {
            String smtpServer = null;
            if (btnSmtpIPAddress.isSelected()) {
              smtpServer = ipaSmtpServer.getText();
            }
            else {
              smtpServer = txtSmtpHost.getText();
            }  
            if (!validateInput(smtpServer)) {
                throw new RuntimeException(
                        new ClientException("data.validation.error"));
            }
                        
            int smtpPort = ((Long) txtSmtpPort.getValue()).intValue();
            
            // See CR 6526685 - only call write for values that have changed.
            if (!smtpServer.equals(hostConn.getAddress(AdminApi.ADDRT_SMTP_IP)))
                hostConn.setAddress(AdminApi.ADDRT_SMTP_IP, smtpServer);
            if (smtpPort != hostConn.getPort(AdminApi.PORTT_SMTP))
                hostConn.setPort(AdminApi.PORTT_SMTP, smtpPort);

            // E-mail list
            ArrayList emailsTo = new ArrayList();
            ArrayList emailsCc = new ArrayList();
            PnlConfigNotifEmailTableModel model = 
                    (PnlConfigNotifEmailTableModel)
                        arlEmailAddresses.getTableModel();
            int cnt = model.getRowCount();
            for (int i = 0; i < cnt; i ++) {
                String type = (String) model.getValueAt(i, 
                        PnlConfigNotifEmailTableModel.TYPE);
                String email = (String) model.getValueAt(i, 
                        PnlConfigNotifEmailTableModel.ADDRESS);
                if (type.equals(TYPE_TO)) {
                    emailsTo.add(email);
                } else if (type.equals(TYPE_CC)) {
                    emailsCc.add(email);
                } else {
                    // TODO trace error
                }
            }
            String[] emailsToArray = (String[]) emailsTo.toArray(
                    new String[emailsTo.size()]);
            String[] emailsCcArray = (String[]) emailsCc.toArray(
                    new String[emailsCc.size()]);
            
            if (!Arrays.equals(emailsToArray, hostConn.getEmailsTo()))
                hostConn.setEmailsTo(emailsToArray);
            if (!Arrays.equals(emailsCcArray, hostConn.getEmailsCc()))
                hostConn.setEmailsCc(emailsCcArray);
            Log.logInfoMessage(
                    GuiResources.getGuiString("config.notif.email.saved"));
            
            // Reboot if necessary
            if (bPanel.getConfirmValue() == JOptionPane.YES_OPTION) {
                ObjectFactory
                        .shutdownSystem(null, true, true, true, this);
            }
            
            ObjectFactory.clearCache(); // Flush cache
        } catch (Exception e) {
            throw new RuntimeException(e);
        }    
    }

    public String getPageKey() {
        return HelpFileMapping.CONFIGUREEMAILALERTS;
    }
    
    // **************************************
    // Handlers
    // ***************************************

    private void handleIpAddressPropertyChanged(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(IpAddressField.PROP_IP_ADDRESS)) {
            explItem.setIsModified(true);
            bPanel.setShowConfirmDialog(true,
                    GuiResources.getGuiString("cell.operation.reboot.warning"),
                    GuiResources.getGuiString("app.name"));
        }    
    }

    private void handleArlEmailAdressesTableChanged(TableModelEvent e) {
        explItem.setIsModified(true);
        // If row has been added, provide defaults
        if (e.getType() == TableModelEvent.INSERT) {
            int rowNum = e.getFirstRow();
            // Initialize values
            PnlConfigNotifEmailTableModel model = 
                    (PnlConfigNotifEmailTableModel) 
                        arlEmailAddresses.getTableModel();
            boolean selectNewRow = false;
            if (model.getValueAt(rowNum, PnlConfigNotifEmailTableModel.TYPE) 
                                                                    == null) {
                model.setValueAt(GuiResources.getGuiString(
                                    "config.notif.email.type.default"),
                                 rowNum, PnlConfigNotifEmailTableModel.TYPE);
                selectNewRow = true;
            }
            if (model.getValueAt(rowNum, PnlConfigNotifEmailTableModel.ADDRESS) 
                                                                    == null) {
                model.setValueAt(GuiResources.getGuiString(
                                    "config.notif.email.address.default"),
                                 rowNum, PnlConfigNotifEmailTableModel.ADDRESS);
                selectNewRow = true;
            }
            if (selectNewRow) {
                // TODO get newly created row to show up
                
            }
        }
    }
    
     private void handleHostNameIpAddress(boolean bIsHostSelected) {
       explItem.setIsModified(true);
       if (bIsHostSelected) { 
          txtSmtpHost.setVisible(true);
          txtSmtpHost.setBounds(ipaSmtpServer.getBounds());
          txtSmtpHost.requestFocusInWindow();
          ipaSmtpServer.setVisible(false);          
       }
       else {
          ipaSmtpServer.setVisible(true);
          txtSmtpHost.setVisible(false); 
       }
       validate(); 
       repaint();
    }
     
    private boolean isValidIpAddress(String txtHost) {
      if (txtHost != null && IpAddressField.IP_PATTERN.matcher(txtHost).matches()) {
        return true; 
      }
      return false;
    }
    
    private void handleHostNameRadioBtn (java.awt.event.ItemEvent e) {    
       if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {          
         handleHostNameIpAddress(true);       
       }
    }

    private void handleIpAddressRadioBtn (java.awt.event.ItemEvent e) {
       if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {         
         handleHostNameIpAddress(false);
       }
    }
    
    private void handleTextChanged(java.awt.event.KeyEvent evt) {
        explItem.setIsModified(true);
        bPanel.setShowConfirmDialog(true,
                GuiResources.getGuiString("cell.operation.reboot.warning"),
                GuiResources.getGuiString("app.name"));   
    }
    
    private boolean validationCheck() {
        // check to see if user has selected or modified an authorized client
        // entry in the table -- if so, check to see if whatever was last 
        // selected or modified is valid.
        boolean valid = true;
        String  smtpServer = null;
        if (btnSmtpIPAddress.isSelected()) {
          smtpServer = ipaSmtpServer.getText();
        }
        else {
          smtpServer = txtSmtpHost.getText();
        }              
        valid = validateInput(smtpServer);        
        return valid;
    }
     
    public boolean validateInput(String host) {
        try {
            validationError = new StringBuffer();
            // check the validity of smtp server
            if (host.equals("0.0.0.0")) {
                // invalid -- show warning
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                validationError.append(GuiResources.getGuiString(
                               "config.ip.invalid", host));
            } else if (!Validate.isValidHostname(host)) {
                // invalid -- show warning
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                validationError.append(GuiResources.getGuiString(
                                "config.host.invalid", host));
            } 
            //now  check to see if Host name selection is made,
            // then make sure DNS is also enabled 
            if (!hostConn.isDnsEnabled() && !btnSmtpIPAddress.isSelected()) {
                //give warning to enable DNS before entering hostnames and exit                
                validationError.append(GuiResources.getGuiString(
                                "config.dnsDisabled"));                 
            }
            if (validationError.length() > 0) {
                JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            validationError.toString(),
                            GuiResources.getGuiString("app.name"),
                            JOptionPane.ERROR_MESSAGE);
                Log.logInfoMessage(GuiResources.
                    getGuiString("config.notif.email.notsaved.forhost", 
                        new String[] {host, validationError.toString()}));
                explItem.setIsErrorOnPanel(true);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        buttonGroup1 = new javax.swing.ButtonGroup();
        lblEmailAddresses = new javax.swing.JLabel();
        arlEmailAddresses = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        jPanel2 = new javax.swing.JPanel();
        lblServer = new javax.swing.JLabel();
        ipaSmtpServer = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        txtSmtpHost = new javax.swing.JTextField();
        lblPort = new javax.swing.JLabel();
        txtSmtpPort = new com.sun.nws.mozart.ui.swingextensions.JFormattedPortField();
        jPanel1 = new javax.swing.JPanel();
        btnSmtpIPAddress = new javax.swing.JRadioButton();
        btnSmtpHostName = new javax.swing.JRadioButton();

        lblEmailAddresses.setDisplayedMnemonic(GuiResources.getGuiString("config.notif.email.addresses.mn").charAt(0));
        lblEmailAddresses.setLabelFor(arlEmailAddresses);
        lblEmailAddresses.setText(GuiResources.getGuiString("config.notif.email.addresses"));

        lblServer.setDisplayedMnemonic(GuiResources.getGuiString("config.notif.email.server.mn").charAt(0));
        lblServer.setLabelFor(ipaSmtpServer);
        lblServer.setText(GuiResources.getGuiString("config.notif.email.server"));

        ipaSmtpServer.setPreferredSize(new java.awt.Dimension(205, 20));
        ipaSmtpServer.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaSmtpServerPropertyChange(evt);
            }
        });

        txtSmtpHost.setPreferredSize(new java.awt.Dimension(205, 20));
        txtSmtpHost.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                txtSmtpHostKeyTyped(evt);
            }
        });

        lblPort.setDisplayedMnemonic(GuiResources.getGuiString("config.notif.email.port.mn").charAt(0));
        lblPort.setLabelFor(txtSmtpPort);
        lblPort.setText(GuiResources.getGuiString("config.notif.email.port"));

        txtSmtpPort.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                txtSmtpPortKeyTyped(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(111, 111, 111)
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(txtSmtpPort, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 47, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(txtSmtpHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 205, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(lblPort)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(111, 111, 111)
                        .add(ipaSmtpServer, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 205, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(lblServer)))
        );

        jPanel2Layout.linkSize(new java.awt.Component[] {ipaSmtpServer, txtSmtpHost}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblServer)
                    .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(ipaSmtpServer, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(txtSmtpHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 22, Short.MAX_VALUE)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(lblPort)
                    .add(txtSmtpPort, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
        );

        jPanel2Layout.linkSize(new java.awt.Component[] {ipaSmtpServer, txtSmtpHost}, org.jdesktop.layout.GroupLayout.VERTICAL);

        buttonGroup1.add(btnSmtpIPAddress);
        btnSmtpIPAddress.setMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.ipaddress.mn").charAt(0));
        btnSmtpIPAddress.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.ipaddress"));
        btnSmtpIPAddress.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        btnSmtpIPAddress.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btnSmtpIPAddress.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                btnSmtpIPAddressItemStateChanged(evt);
            }
        });

        buttonGroup1.add(btnSmtpHostName);
        btnSmtpHostName.setMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.hostname.mn").charAt(0));
        btnSmtpHostName.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.hostname"));
        btnSmtpHostName.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        btnSmtpHostName.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btnSmtpHostName.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                btnSmtpHostNameItemStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(btnSmtpIPAddress)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 18, Short.MAX_VALUE)
                .add(btnSmtpHostName))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(btnSmtpIPAddress)
                .add(btnSmtpHostName))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, lblEmailAddresses)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                                .add(111, 111, 111)
                                .add(arlEmailAddresses, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 477, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                    .add(layout.createSequentialGroup()
                        .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(17, 17, 17)
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(93, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 29, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblEmailAddresses)
                    .add(arlEmailAddresses, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 144, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(21, 21, 21))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void txtSmtpHostKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtSmtpHostKeyTyped
        handleTextChanged(evt);
    }//GEN-LAST:event_txtSmtpHostKeyTyped

    private void btnSmtpHostNameItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_btnSmtpHostNameItemStateChanged
        handleHostNameRadioBtn(evt);
    }//GEN-LAST:event_btnSmtpHostNameItemStateChanged

    private void btnSmtpIPAddressItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_btnSmtpIPAddressItemStateChanged
        handleIpAddressRadioBtn(evt);
    }//GEN-LAST:event_btnSmtpIPAddressItemStateChanged

    private void ipaSmtpServerPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_ipaSmtpServerPropertyChange
        handleIpAddressPropertyChanged(evt);
    }//GEN-LAST:event_ipaSmtpServerPropertyChange

    private void txtSmtpPortKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtSmtpPortKeyTyped
        handleTextChanged(evt);   
    }//GEN-LAST:event_txtSmtpPortKeyTyped
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlEmailAddresses;
    private javax.swing.JRadioButton btnSmtpHostName;
    private javax.swing.JRadioButton btnSmtpIPAddress;
    private javax.swing.ButtonGroup buttonGroup1;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField ipaSmtpServer;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JLabel lblEmailAddresses;
    private javax.swing.JLabel lblPort;
    private javax.swing.JLabel lblServer;
    private javax.swing.JTextField txtSmtpHost;
    private com.sun.nws.mozart.ui.swingextensions.JFormattedPortField txtSmtpPort;
    // End of variables declaration//GEN-END:variables
    
}
