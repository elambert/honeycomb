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
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 *
 * @author  dp127224
 */
public class PnlConfigNotifLogHost extends JPanel implements ContentPanel {
                                            
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private StringBuffer validationError = new StringBuffer(); 
    private BtnPnlApplyCancel bPanel = new BtnPnlApplyCancel();   
    
    /** Creates new form PanelAdmin */
    public PnlConfigNotifLogHost() {
        
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {        
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
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
    }
    
    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("config.notif.logHost.title");
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
        try {
            ipaLogHost.setText("0.0.0.0");
            txtLogHost.setText("");
            final String addr = hostConn.getAddress(AdminApi.ADDRT_LOG_IP);
            if (isValidIpAddress(addr)) {
               ipaLogHost.setText(addr);
               btnLogIPAddress.setSelected(true);
            }
            else {
               txtLogHost.setText(addr);
               btnLogHostName.setSelected(true);
            }            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }             
    }
 
    public void saveValues() throws UIException, HostException {
        try {
            String hostIp = null;
            if (btnLogIPAddress.isSelected()) {
              hostIp = ipaLogHost.getText();
            }
            else {
              hostIp = txtLogHost.getText();
            } 
            if (!validateInput(hostIp)) {                   
              throw new RuntimeException(
                        new ClientException("data.validation.error"));             
            }
            hostConn.setAddress(AdminApi.ADDRT_LOG_IP, hostIp);
            Log.logInfoMessage(GuiResources.getGuiString(
                    "config.notif.logHost.saved", hostIp));
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
        return HelpFileMapping.SETLOGGINGHOST;
    }   
    // ************************************
    // Display warning & confirmation message if user edits 
    // the log host IP and presses Apply button
    private void handleIpAddressPropertyChanged(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(IpAddressField.PROP_IP_ADDRESS)) {
            bPanel.setShowConfirmDialog(true,
                    GuiResources.getGuiString("cell.operation.reboot.warning"),
                    GuiResources.getGuiString("app.name"));
            explItem.setIsModified(true);
        }    
    }
    
    private void handleHostNamePropertyChanged(PropertyChangeEvent evt) {       
            bPanel.setShowConfirmDialog(true,
                    GuiResources.getGuiString("cell.operation.reboot.warning"),
                    GuiResources.getGuiString("app.name"));
            explItem.setIsModified(true);        
    }
    
    private void handleHostNameIpAddress(boolean bIsHostSelected) {   
       if (bIsHostSelected) {
          txtLogHost.setVisible(true);
          txtLogHost.setBounds(ipaLogHost.getBounds());
          ipaLogHost.setVisible(false);
          txtLogHost.requestFocusInWindow();
          explItem.setIsModified(true);
       }
       else {
          ipaLogHost.setVisible(true);
          txtLogHost.setVisible(false); 
          explItem.setIsModified(true);
       }
       validate();              
       repaint();
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
    
    private boolean isValidIpAddress(String txtHost) {
      if (txtHost != null && IpAddressField.IP_PATTERN.matcher(txtHost).matches()) {
        return true; 
      }
      return false;
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
        String hostIp = null;
        if (btnLogIPAddress.isSelected()) {
            hostIp = ipaLogHost.getText();
        } else {
            hostIp = txtLogHost.getText();
        }
        valid = validateInput(hostIp);        
        return valid;
    }
     
    public boolean validateInput(String host) {
        try {
            validationError = new StringBuffer();
            // check the validity of log host
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
            if (!hostConn.isDnsEnabled() && !btnLogIPAddress.isSelected()) {
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
                    getGuiString("config.notif.logHost.notsaved.forhost", 
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
        jPanel1 = new javax.swing.JPanel();
        ipaLogHost = new com.sun.nws.mozart.ui.swingextensions.IpAddressField();
        txtLogHost = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        btnLogIPAddress = new javax.swing.JRadioButton();
        btnLogHostName = new javax.swing.JRadioButton();
        lblHost = new javax.swing.JLabel();

        ipaLogHost.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                ipaLogHostPropertyChange(evt);
            }
        });

        txtLogHost.setText("012345678901234567890123456789");
        txtLogHost.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                txtLogHostPropertyChange(evt);
            }
        });
        txtLogHost.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                txtLogHostKeyTyped(evt);
            }
        });

        buttonGroup1.add(btnLogIPAddress);
        btnLogIPAddress.setMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.ipaddress.mn").charAt(0));
        btnLogIPAddress.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.ipaddress"));
        btnLogIPAddress.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        btnLogIPAddress.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btnLogIPAddress.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                btnLogIPAddressItemStateChanged(evt);
            }
        });

        buttonGroup1.add(btnLogHostName);
        btnLogHostName.setMnemonic(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.hostname.mn").charAt(0));
        btnLogHostName.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.hostname"));
        btnLogHostName.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        btnLogHostName.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btnLogHostName.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                btnLogHostNameItemStateChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(btnLogIPAddress)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 18, Short.MAX_VALUE)
                .add(btnLogHostName)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(0, 0, Short.MAX_VALUE)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(btnLogIPAddress)
                    .add(btnLogHostName))
                .addContainerGap())
        );

        lblHost.setDisplayedMnemonic(GuiResources.getGuiString("config.notif.logHost.host.mn").charAt(0));
        lblHost.setLabelFor(ipaLogHost);
        lblHost.setText(GuiResources.getGuiString("config.notif.logHost.host"));

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(lblHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 135, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(24, 24, 24)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(ipaLogHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 205, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(txtLogHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 205, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {ipaLogHost, txtLogHost}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(txtLogHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(ipaLogHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(lblHost))
                .add(17, 17, 17)
                .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(61, 61, 61))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {ipaLogHost, txtLogHost}, org.jdesktop.layout.GroupLayout.VERTICAL);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 370, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(27, 27, 27)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 88, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(27, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void txtLogHostKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtLogHostKeyTyped
        handleTextChanged(evt);
    }//GEN-LAST:event_txtLogHostKeyTyped

    private void txtLogHostPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_txtLogHostPropertyChange
        handleHostNamePropertyChanged(evt);
    }//GEN-LAST:event_txtLogHostPropertyChange

    private void btnLogHostNameItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_btnLogHostNameItemStateChanged
        handleHostNameRadioBtn(evt);
    }//GEN-LAST:event_btnLogHostNameItemStateChanged

    private void btnLogIPAddressItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_btnLogIPAddressItemStateChanged
        handleIpAddressRadioBtn(evt);
    }//GEN-LAST:event_btnLogIPAddressItemStateChanged

    private void ipaLogHostPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_ipaLogHostPropertyChange
        handleIpAddressPropertyChanged(evt);
    }//GEN-LAST:event_ipaLogHostPropertyChange
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton btnLogHostName;
    private javax.swing.JRadioButton btnLogIPAddress;
    private javax.swing.ButtonGroup buttonGroup1;
    private com.sun.nws.mozart.ui.swingextensions.IpAddressField ipaLogHost;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JLabel lblHost;
    private javax.swing.JTextField txtLogHost;
    // End of variables declaration//GEN-END:variables
    
}
