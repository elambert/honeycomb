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

import com.sun.honeycomb.adm.common.Validate;
import com.sun.honeycomb.admingui.XmlRpcParamDefs;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.contentpanels.PnlDNS;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListValidator;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.AsyncProxy;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;


/**
 *
 * @author jb127219
 */
public class PnlConfigDNS extends PnlDNS 
                        implements ContentPanel, AddRemoveListValidator {
    
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    
    private BtnPnlApplyCancel bPanel = new BtnPnlApplyCancel();
    
    // keeps track of validation errors
    private StringBuffer validationError = new StringBuffer();
    
    private DisableDNSHelper dnsHelper = null;
    private String[] authClients = null;
    private String[] ntpServers = null;
    private String[] logHostIP = null;
    private String[] smtpServerIP = null;
    
    // flag which indicates whether or not the values should be saved
    private boolean applyCheckOK = true;
    
    /** Creates a new instance of PnlDNS */
    public PnlConfigDNS() {
        super();
                               
        // no support for dynamic DNS
        this.dynDnsPanel.setVisible(false);
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));

        
        PnlConfigDNSTableModel model = new PnlConfigDNSTableModel();
        this.dnsSuffixList.setTableModel(model);
        this.dnsSuffixList.setColumnHeaderVisible(false);
        this.dnsSuffixList.setValidator(this);

        this.dnsSuffixList.setAddButtonTooltip(GuiResources.getGuiString(
                                        "config.network.suffix.addTooltip"));
        this.dnsSuffixList.setRemoveButtonTooltip(GuiResources.getGuiString(
                                    "config.network.suffix.removeTooltip"));
        // Column editor
        TableColumnModel colModel = dnsSuffixList.getTableColumnModel();
        TableColumn col = colModel.getColumn(
                                    PnlConfigDNSTableModel.SEARCH_STRING);

        /** Listeners */
        // Detect whether or not the user starts typing in a domain name
        // in order to activate APPLY and CANCEL buttons
        this.domainName.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent evt) {
                txtDomainKeyTyped(evt);
            }
        });
        
        this.primaryServer.addPropertyChangeListener(
                                                new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().
                                    equals(IpAddressField.PROP_IP_ADDRESS)) {
                    if (explItem != null) {
                        explItem.setIsModified(true);
                    }
                    bPanel.setShowConfirmDialog(true,
                     GuiResources.getGuiString("cell.operation.reboot.warning"),
                        GuiResources.getGuiString("app.name"));
                }                
                
            }
        });
        
        this.secondaryServer.addPropertyChangeListener(
                                                new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().
                                    equals(IpAddressField.PROP_IP_ADDRESS)) {
                    if (explItem != null) {
                        explItem.setIsModified(true);
                    }
                     bPanel.setShowConfirmDialog(true,       
                     GuiResources.getGuiString("cell.operation.reboot.warning"),
                        GuiResources.getGuiString("app.name"));
                }                
                
            }
        });
        
        // Model listener
        model.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                explItem.setIsModified(true);
                bPanel.setShowConfirmDialog(true,
                    GuiResources.getGuiString("cell.operation.reboot.warning"),
                    GuiResources.getGuiString("app.name"));
            }
        });
        
        // Checkbox listener (CR 6521437)
        dnsEnabled.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                explItem.setIsModified(true);
                bPanel.setShowConfirmDialog(true,
                    GuiResources.getGuiString("cell.operation.reboot.warning"),
                    GuiResources.getGuiString("app.name"));
            }
        });
        
        bPanel.setShowApplyButton(true);
        bPanel.setShowCancelButton(true);
        bPanel.getApplyButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                try {
                    handleApplyActionPerformed(evt);
                } catch (Exception ex) {
                    Log.logInfoMessage(GuiResources.getGuiString(
                            "config.network.dns.notSaved.noargs",
                            validationError.toString()));
                    AsyncProxy.handleException(ex);
                }
            }
        });
        // Panel validator (CR 6542611)
        bPanel.setPanelValidator(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!ObjectFactory.switchesOK()) {
                    bPanel.setRefreshOnValidationFailure(true);
                    throw new RuntimeException(
                            new ClientException("data.validation.error"));
                }
                if (!validateInput(true)) {
                    // invalid data
                    Log.logInfoMessage(GuiResources.getGuiString(
                            "config.network.dns.notSaved",
                            validationError.toString()));
                    throw new RuntimeException(
                            new ClientException("data.validation.error"));
                }
            }
        });
        
        dnsHelper = new DisableDNSHelper(this);
    }
    public void resolveHostIds() {
        authClients = null;
        ntpServers = null;
        logHostIP = null;
        smtpServerIP = null;
        configSrvicesBuff = new StringBuffer();
        try {
            // Authorized Data Clients
            String[] clientIds = hostConn.getClients();
            if (clientIds == null || clientIds.length == 0) {
                Log.logInfoMessage(GuiResources.getGuiString(
                            "config.sysAccess.authorize.allAuthorized"));
            } else {
                for (int idx = 0; idx < clientIds.length; idx++) {
                    String clientId = clientIds[idx];
                    if (clientId.indexOf('/') != -1) {
                        // subnet mask is set
                        int vNetmask = Validate.validNetmask(clientId);
                        if (vNetmask != 0) {
                            // invalid netmask, which means that the clientId
                            // is a host name
                            clientId = 
                                    clientId.substring(0,clientId.indexOf('/'));
                            clientIds[idx] = clientId;
                        }
                    }
                }
                authClients = dnsHelper.getHostIPs(clientIds);
                if (dnsHelper.getNumHostnames() > 0) {
                    configSrvicesBuff.append(GuiResources.getGuiString(
                        "config.network.dns.hostName.configured.authClients")).
                            append("\n");
                } else {
                    authClients = null;
                }
            }

            // NTP Servers
            String[] ntpIPs = hostConn.getNTPAddrs();
            if (ntpIPs != null) {
                ntpServers = dnsHelper.getHostIPs(ntpIPs);
                if (dnsHelper.getNumHostnames() > 0) {
                    configSrvicesBuff.append(GuiResources.getGuiString(
                        "config.network.dns.hostName.configured.ntp")).
                            append("\n");
                } else {
                    ntpServers = null;
                }
            }

            // Log Host
            String logHost = hostConn.getAddress(AdminApi.ADDRT_LOG_IP);
            // an array of 1
            String[] logIP = new String[] {logHost};
            logHostIP = dnsHelper.getHostIPs(logIP);
            if (dnsHelper.getNumHostnames() > 0) {
                configSrvicesBuff.append(GuiResources.getGuiString(
                        "config.network.dns.hostName.configured.logHost")).
                            append("\n");
            } else {
                logHostIP = null;
            }

            // SMTP Server
            String server = hostConn.getAddress(AdminApi.ADDRT_SMTP_IP);
            // an array of 1
            String[] smtpIP = new String[] {server};
            smtpServerIP = dnsHelper.getHostIPs(smtpIP);
            if (dnsHelper.getNumHostnames() > 0) {
                configSrvicesBuff.append(GuiResources.getGuiString(
                        "config.network.dns.hostName.configured.smtp")).
                            append("\n");
            } else {
                smtpServerIP = null;
            }
        } catch (Exception ex) {
            AsyncProxy.handleException(ex);
        }
    }
    public void saveResolvedHostIds() {
        try { 
            if (dnsHelper.atLeastOneResolved()) {
                if (authClients != null) {
                    hostConn.setClients(authClients);
                }
                if (ntpServers != null) {
                    hostConn.setNTPAddrs(ntpServers);
                }
                if (logHostIP != null) {
                    hostConn.setAddress(AdminApi.ADDRT_LOG_IP, logHostIP[0]);
                }
                if (smtpServerIP != null) {
                    hostConn.setAddress(AdminApi.ADDRT_SMTP_IP, smtpServerIP[0]);
                }
                ObjectFactory.clearCache();
            }
        } catch (Exception e) {
            AsyncProxy.handleException(e);
        }
    }
    
    private void handleApplyActionPerformed(ActionEvent evt) {  
        boolean enabled = dnsEnabled.isSelected();
        dnsHelper.disableDNSCheck();
        if (dnsHelper.okToDisableDNS()) {
            if (dnsHelper.atLeastOneResolved()) {
                bPanel.setShowConfirmDialog(true,
                            GuiResources.getGuiString(
                                "config.network.dns.hostName.resolution.saved",
                                    dnsHelper.getResolvedList()),
                            GuiResources.getGuiString("app.name"));
            } else {
                bPanel.setShowConfirmDialog(true,
                    GuiResources.getGuiString(
                "config.network.dns.hostName.resolution.noneConverted.saved"),
                                        GuiResources.getGuiString("app.name"));
            }
        }
        applyCheckOK = (dnsHelper.okToDisableDNS() && !enabled) || enabled;
        if (!applyCheckOK) {
            bPanel.setShowConfirmDialog(false,
                            GuiResources.getGuiString(
                                "cell.operation.reboot.warning"),
                            GuiResources.getGuiString("app.name"));
        }
    }

    public boolean isApplyCheckOK() {
        return applyCheckOK;
    }
    
    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("config.network.dns.title");
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
        loadDNSInfo();
        loadSuffixList();

        // disable all items if user is in read only 
        if (!ObjectFactory.isAdmin()) {
            disableAllComponents(this.getComponents());
        }
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
  
    public void saveValues() throws UIException, HostException { 
        if (applyCheckOK) {
            if (!validateInput(false)) {
                Log.logAndDisplay(Level.SEVERE, GuiResources.getGuiString(
                        "config.network.dns.notSaved", 
                        validationError.toString()), null);
                throw new RuntimeException(
                        new ClientException("data.validation.error"));
            }
            saveDNSInfo();
            saveSuffixList();

            // Reboot if necessary
            if (bPanel.getConfirmValue() == JOptionPane.YES_OPTION) {
                try {
                    ObjectFactory
                            .shutdownSystem(null, true, true, true, this);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
            // Flush cache
            ObjectFactory.clearCache(); 
        }
    }
     
    // *************************************************

    public String getPageKey() {
        return HelpFileMapping.SETUPDNS;
    }
    
    // placeholder for validating suffixes -- CLI doesn't do any validation
    // for 1.1, but this may change in subsequent releases
    public boolean isRowDataValid() {
        return isSuffixValid();
    }
    public String getValidationErrMsg() {
        return "need to define string here";
    } 
    private boolean isSuffixValid() {
        boolean valid = true;
        return valid;
    }

    // Enable the APPLY and CANCEL buttons
    private void txtDomainKeyTyped(KeyEvent evt) {                                         
        explItem.setIsModified(true);
    }  
    
    private void loadDNSInfo() throws HostException, UIException {
        try {
            this.domainName.setText(hostConn.getDomainName());
            this.primaryServer.setText(hostConn                    
                    .getAddress(AdminApi.ADDRT_PRIDNS_IP));           
            
            this.secondaryServer.setText(hostConn
                    .getAddress(AdminApi.ADDRT_SECDNS_IP));

            // if enabled on the box, then enable it in the GUI.  Otherwise,
            // it should be disabled.
            ObjectFactory.clearCacheMethod(XmlRpcParamDefs.ISDNSON);
            if (hostConn.isDnsEnabled()) {
                this.enableDNS(true); 
                this.enableSuffixSearch(true);
                this.dnsEnabled.setSelected(true);
            } else {
                this.enableDNS(false);
                this.enableSuffixSearch(false);
                this.dnsEnabled.setSelected(false);
            }

        } catch (Exception e) {
            AsyncProxy.handleException(e);
        }

    }
    
   
    private void loadSuffixList() throws HostException, UIException {
        BaseTableModel model = this.dnsSuffixList.getTableModel();
        model.removeAll();
        try {
            // retrieve the suffixes used to search for DNS
            String [] suffixes = hostConn.getDnsSearch();
            if (suffixes == null) {
                return;
            }
            for (int idx = 0; idx < suffixes.length; idx++) {
                String suffix = suffixes[idx];
                if (suffix.length() != 0) {
                    model.addRow(new Object[]{suffix});
                }
            }
        } catch (Exception e) {
            AsyncProxy.handleException(e);
        }
    }

    private boolean validateInput(boolean showMsg) {
        validationError = new StringBuffer();
        // CR 6542611 - Ensure that all DNS parameters are valid
        if (dnsEnabled.isSelected()) {
            String dName = this.domainName.getText();
            String pServer = this.primaryServer.getText();            
            String sServer = this.secondaryServer.getText();
            BaseTableModel model = this.dnsSuffixList.getTableModel();
            int cnt = model.getRowCount();

            if (!Validate.isValidDomainName(dName)) {
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                if (dName == null) {
                    dName = "null";
                } 
                validationError.append(GuiResources.getGuiString(
                            "config.network.dns.domainName.error", dName));
            }
            if (pServer.equals("0.0.0.0") ||
                                        !Validate.isValidIpAddress(pServer)) {
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                validationError.append(GuiResources.getGuiString(
                            "config.ip.invalid", pServer));
            }
            if (sServer.equals("0.0.0.0") ||
                                        !Validate.isValidIpAddress(sServer)) {
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                validationError.append(GuiResources.getGuiString(
                            "config.ip.invalid", sServer));
            }
            if (cnt == 0) {
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                validationError.append(GuiResources.getGuiString(
                                            "config.network.dns.suffix.error"));
            }
        }
        if (validationError.length() > 0) {
            if (showMsg) {
                JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            validationError.toString(),
                            GuiResources.getGuiString("app.name"),
                            JOptionPane.ERROR_MESSAGE);
            }
            explItem.setIsErrorOnPanel(true);
            return false;
        } else {
            return true;
        }
    }
      
    private void saveDNSInfo() throws HostException, UIException {       
        try {
            boolean de = dnsEnabled.isSelected();
            String dn = this.domainName.getText();
            String ps = this.primaryServer.getText();            
            String ss = this.secondaryServer.getText();
            
            // See CR 6526685 - only call write for values 
            // that have changed.
            ObjectFactory.clearCacheMethod(XmlRpcParamDefs.ISDNSON);
            if (de != hostConn.isDnsEnabled()) {
                if (de)
                    hostConn.enableDns();
                else
                    if (dnsHelper.okToDisableDNS()) {
                        saveResolvedHostIds();
                        hostConn.disableDns();
                    }
            }
            if (!dn.equals(hostConn.getDomainName()))
                hostConn.setDomainName(dn);
            if (!ps.equals(hostConn.getAddress(AdminApi.ADDRT_PRIDNS_IP)))
                hostConn.setAddress(AdminApi.ADDRT_PRIDNS_IP, ps);
            if (!ss.equals(hostConn.getAddress(AdminApi.ADDRT_SECDNS_IP)))
                hostConn.setAddress(AdminApi.ADDRT_SECDNS_IP, ss);
            Log.logInfoMessage(GuiResources
                                  .getGuiString("config.network.dns.saved",
                                               new String[] {dn, ps, ss}));
        } catch (Exception e) {
            AsyncProxy.handleException(e);
        }
    }
    
    private void saveSuffixList() throws HostException, UIException {
        try {
            BaseTableModel model = this.dnsSuffixList.getTableModel();
            int cnt = model.getRowCount();
            String[] suffixes = new String[cnt];
            for (int i = 0; i < cnt; i++) {
                suffixes[i] = (String) model.getValueAt(i, 
                                    PnlConfigDNSTableModel.SEARCH_STRING);
            }
            if (!Arrays.equals(suffixes, hostConn.getDnsSearch()))
                hostConn.setDnsSearch(suffixes);
            Log.logInfoMessage(
                    GuiResources.getGuiString("config.network.dns.saved.list"));
        } catch (Exception e) {
            AsyncProxy.handleException(e);
        }       
    }
   
}
