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

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.adm.common.Validate;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.InvalidDataException;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveList;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListDialog;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListResolver;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.IpSubnetMaskPair;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.UserErrorMessage;
import com.sun.nws.mozart.ui.utility.UserMessage;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 *
 * @author  dp127224
 */
public class PnlConfigAdminAuthorize extends JPanel 
        implements ContentPanel, AddRemoveListDialog, AddRemoveListResolver {
    
    //flag to indicate the type of server, host name or IP Address selected
    public static final int RADIO_IP_BTN_SELECT   = 0;
    public static final int RADIO_HOST_BTN_SELECT = 1;
    public static final int RADIO_NO_BTN_SELECT   = -1;
    
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;

    private BtnPnlApplyCancel bPanel = new BtnPnlApplyCancel();
    private final PnlConfigAdminAuthorizeTableModel model = 
                                new PnlConfigAdminAuthorizeTableModel();
    private JTable table = null;
    private StringBuffer validationError = new StringBuffer();
    private int maxNumClients = 0;
    
    private JTable tablMain = null;
    private boolean btnIPSelected = true;
    private int hostBtnSelection = RADIO_NO_BTN_SELECT;
    
    public PnlConfigAdminAuthorize() {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
        tablMain = new JTable() {
            // method is called every time the user attempts to select a new
            // cell.  When the row number differs, then validate the data -- 
            // otherwise, do nothing.
            public void changeSelection(int row, int column, 
                                            boolean toggle, boolean extend) {
                boolean valid = true;
                int sr = super.getSelectedRow();
                model.setCurrentSelection(sr);
                model.setNewSelection(row);
                if ((sr == row) || (sr == -1)) {
                    super.changeSelection(row, column, toggle, extend);
                    model.removeInvalidRow(sr);
                } else {
                    String ip = (String)super.getValueAt(sr, model.IP_ADDRESS);
                    String mask =
                            (String)super.getValueAt(sr, model.SUBNET_MASK);
                    valid = validateInput(ip, mask, true);
                    if (valid) {
                        ListSelectionModel lsm = super.getSelectionModel();
                        lsm.setSelectionInterval(row, row);
                    } else {
                        // if not already added to list, add the invalid row
                        if (!model.isInvalid(sr).booleanValue()) {
                            model.addInvalidRow(sr);
                        }
                    }
                }
            }
        };
        arlClients.setJTable(tablMain);
        arlClients.setTableModel(model);
        arlClients.setDialog(this);      
        model.setPanel(this);
        arlClients.setResolver(this);
        arlClients.setTableEmptyText(GuiResources.getGuiString(
                                    "config.sysAccess.authorize.emptyTable"));

        arlClients.setAddButtonTooltip(GuiResources.getGuiString(
                                    "config.sysAccess.authorize.addTooltip"));
        arlClients.setRemoveButtonTooltip(GuiResources.getGuiString(
                                "config.sysAccess.authorize.removeTooltip"));
        ListSelectionModel rowModel = arlClients.getTableSelectionModel();
        rowModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int currentRow = lsm.getMinSelectionIndex();
                    model.setCurrentSelection(currentRow);
                }
            }});

        // Column editors & renderers
        TableColumnModel colModel = arlClients.getTableColumnModel();
        TableColumn col = colModel.getColumn(
                            PnlConfigAdminAuthorizeTableModel.IP_ADDRESS);
          
        col.setCellEditor(new AuthClientsCellEditor(new IpAddressField(), false));
        col.setCellRenderer(new AuthClientsCellRenderer());
        
        AuthClientsCellEditor iace = (AuthClientsCellEditor)col.getCellEditor();
        iace.getField().addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    handleIPAddressPropertyChanged(evt);
                }
        }); 
        
        col = colModel.getColumn(PnlConfigAdminAuthorizeTableModel.SUBNET_MASK);
        col.setCellEditor(new AuthClientsCellEditor(new IpAddressField(), true));
        col.setCellRenderer(new AuthClientsCellRenderer());
        iace = (AuthClientsCellEditor)col.getCellEditor();
        iace.getField().addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    handleIPAddressPropertyChanged(evt);
                }
        }); 
        
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
                            "config.sysAccess.authorize.notSaved",
                            validationError.toString()));
                    throw new RuntimeException(
                            new ClientException("data.validation.error"));
                }
            }
        });
        
        // Model listener
        model.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                // Initialize values?
                PnlConfigAdminAuthorizeTableModel model = 
                        (PnlConfigAdminAuthorizeTableModel)e.getSource();
                explItem.setIsModified(true);
                bPanel.setShowConfirmDialog(true,
                        GuiResources.getGuiString(
                            "cell.operation.reboot.warning"),
                        GuiResources.getGuiString("app.name"));                
                
                
                if (e.getType() == TableModelEvent.INSERT) {
                    int row = e.getFirstRow();
                    if (model.getValueAt(row, model.IP_ADDRESS) == null) {
                        model.setValueAt("0.0.0.0", row, model.IP_ADDRESS);
                        if (model.getValueAt(row, model.SUBNET_MASK) == null) {
                           model.setValueAt("255.255.255.255", 
                                                row, model.SUBNET_MASK);
                        }
                    }
                   
                }
            }
        });
    }
        
    //////////////////////////////////////////
    // ContentPanel Impl
    
    public String getTitle() {
        return GuiResources.getGuiString("config.sysAccess.authorize.title");
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
        // Disable component if user has read-only mode
        arlClients.setEnabled(ObjectFactory.isAdmin());
        
        try {
            //DefaultTableModel dmodel = arlClients.getTableModel();
            arlClients.clearTable();

            String[] clients = hostConn.getClients();
            if (clients == null) {
                return;
            }
            maxNumClients = hostConn.getMaxNumAuthClients();
            for (int i = 0; i < clients.length; i++) {
                String client = clients[i];
                String hostId = client;
                boolean isValidIP = false;
                
                // first check to see if it is a valid IP Address                 
                // check the validity of clients 
                                    
                if (client.indexOf('/') != -1) {
                    // subnet mask is set
                    int vNetmask = Validate.validNetmask(client);
                    if (vNetmask == 0) {
                        isValidIP = true;
                    }
                    hostId = client.substring(0, client.indexOf('/'));
                } else if (Validate.isValidIpAddress(client) ) {   
                    isValidIP = true;
                }
                
                if (isValidIP) {
                    IpSubnetMaskPair pair;
                    try {
                        pair = new IpSubnetMaskPair(client);
                    } catch (InvalidDataException e) {
                        UserMessage msg = new UserErrorMessage(
                                GuiResources.getMsgString(
                                    "error.problem.cantLoad"),
                                GuiResources.getMsgString(
                                    "error.cause.badData", client),
                                GuiResources.getMsgString(
                                    "error.solution.contactAdmin"));
                        throw new UIException(msg);                
                    }
                    // if the ip/subnet mask pair only consists of an IP because
                    // a subnet mask is not set, then set the netmask to 32 or
                    // 255.255.255.255 (i.e. all on)
                    model.addRow(new Object[] { pair.getIpAddress(), 
                                  (pair.getSubnetMask()) == null 
                                    ? "255.255.255.255" 
                                    : pair.getSubnetMask()});
                }
                else {
                    if (client.length() != 0) {
                        model.addRow(new Object[] {hostId});
                    }
                }
            }
            //model.setDataType();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }            
    }
            
    public void saveValues() throws HostException {
        try {
            int errCnt = 0;
            StringBuffer localBuffer = new StringBuffer();
            DefaultTableModel model = arlClients.getTableModel();
            int cnt = model.getRowCount();
            //String[] clients = new String[cnt];
            ArrayList clients = new ArrayList();  
            for (int i = 0; i < cnt; i++) {
                String ipMask = null;
                String ip = (String) model.getValueAt(i, 
                        PnlConfigAdminAuthorizeTableModel.IP_ADDRESS);                
                String subnet = (String) model.getValueAt(i, 
                        PnlConfigAdminAuthorizeTableModel.SUBNET_MASK);
                IpSubnetMaskPair pair = 
                                    new IpSubnetMaskPair(ip, subnet);
                int maskNum = pair.getSubnetMaskNumber();
                // Remove netmask of 32 -- see CR6669531 -- problem occurs in
                // CLI too so fix really should be on server-side, but coding
                // the check for 32 as a work-around for 1.1.1
                if (maskNum == -1 || maskNum == 32) {
                    // no subnet mask set or invalid value
                    ipMask = ip;
                } else {
                    ipMask = pair.getSlashNotation();
                }
                if (validateInput(ip, subnet, false)) {
                    clients.add(ipMask);
                } else {
                    errCnt++;
                    if (localBuffer.length() > 0) {
                        localBuffer.append("\n");
                    }
                    localBuffer.append(validationError.toString());
                }                  
            } // end of for loop
            if (errCnt > 0) {
                // invalid data
                Log.logAndDisplay(Level.SEVERE, GuiResources.getGuiString(
                        "config.sysAccess.authorize.notSaved", 
                                localBuffer.toString()), null);
                explItem.setIsErrorOnPanel(true);
                throw new RuntimeException(
                        new ClientException("data.validation.error"));
            } 
            String strClients[] = new String[clients.size()]; 
            clients.toArray(strClients);
            
            hostConn.setClients(strClients);
            Log.logInfoMessage(GuiResources.getGuiString(
                                "config.sysAccess.authorize.saved"));

            // Reboot if necessary
            if (bPanel.getConfirmValue() == JOptionPane.YES_OPTION) {
                ObjectFactory.shutdownSystem(
                            null, true, true, true, this);
            }

            // Flush cache
            ObjectFactory.clearCache();            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
            
    }

    public String getPageKey() {
        return HelpFileMapping.AUTHORIZEDATACLIENTS;
    }
    
    //save
    //////////////////////////////////////////
    public boolean validateInput(String strHost, String subnet, boolean showMsg) {
        try {
            validationError = new StringBuffer();
            //check if it is a valid IP Address
            if (Validate.isValidIpAddress(strHost)) {                        
                IpSubnetMaskPair pair = new IpSubnetMaskPair(strHost, subnet);
                String ipMask = pair.getSlashNotation();

                // check the validity of clients
                if (strHost.equals("0.0.0.0")) {
                    // invalid -- show warning
                    if (validationError.length() > 0) {
                        validationError.append("\n");
                    }
                    validationError.append(GuiResources.getGuiString(
                            "config.ip.invalid", strHost));
                }
                if (ipMask.indexOf('/') != -1) {
                    // subnet mask is set
                    int vNetmask = Validate.validNetmask(ipMask);
                    if (vNetmask != 0) {
                        if (validationError.length() > 0) {
                            validationError.append("\n");
                        }
                        validationError.append(
                                outputNetmaskValidationError(vNetmask, ipMask));
                    }
                }                  
            } else {
               // else check if it a valid host name
              //String host = (String)model.getValueAt(selectedIdx, model.IP_ADDRESS);
              if (!Validate.isValidHostname(strHost)) {
                    if (validationError.length() > 0) {
                        validationError.append("\n");
                    }
                    validationError.append(GuiResources.getGuiString(
                           "config.host.invalid", strHost));
              }
            }
             //now  check to see if the DNS is enabled or else
            //do not allow servers with host names to be stored.
            if (model.hasHostNameType() && !Validate.isValidIpAddress(strHost)) {            
                if (!hostConn.isDnsEnabled() ) {
                //give warning to enable DNS before entering hostnames                 
                validationError.append(GuiResources.getGuiString(
                                "config.dnsDisabled"));                 
                }
            }
            // now check to see if somehow there might be duplicate entries
            // in the table - shouldn't pass duplicate authorized client
            // TODO
            if (validationError.length() > 0) {
                if (showMsg) {
                    JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            validationError.toString(),
                            GuiResources.getGuiString("app.name"),
                            JOptionPane.ERROR_MESSAGE);
                } 
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }  
    
    // Emulating the corresponding CLI method in 
    // com.sun.honeycomb.adm.cli.commands.ValueSetter -- GUI needs to have its
    // own method since Strings are localized.
    private String outputNetmaskValidationError(int errorCode, String netmask) {
        String err = null;
        switch (errorCode) {
            case 1:
                err = GuiResources.getGuiString(
                        "config.sysAccess.authorize.netmask.unspecified",
                        netmask);
                break;
            case 2:
                err = GuiResources.getGuiString(
                        "config.sysAccess.authorize.netmask.invalidIP",
                        netmask);
                break;
            case 3:
                err = GuiResources.getGuiString(
                        "config.sysAccess.authorize.netmask.maskBitNotNumber",
                        netmask);
                break;
            case 4:
                err = GuiResources.getGuiString(
                        "config.sysAccess.authorize.netmask.maskBitOutOfRange",
                        netmask);
                        break;
            case 5:
                err = GuiResources.getGuiString(
                        "config.sysAccess.authorize.netmask.invalidMask",
                        netmask);
                break;
            default:
                err = GuiResources.getGuiString(
                        "config.sysAccess.authorize.netmask.invalid",
                        netmask);    
                break;
        }
        return err;
    }
      
    private void handleIPAddressPropertyChanged(PropertyChangeEvent evt) {
   //     if (evt.getPropertyName().equals(IpAddressField.PROP_IP_ADDRESS)) {
            explItem.setIsModified(true);
     //   }
    }
    
    private boolean validationCheck() {
        // check to see if user has selected or modified an authorized client
        // entry in the table -- if so, check to see if whatever was last 
        // selected or modified is valid.
        boolean valid = true;
        ListSelectionModel lsm = arlClients.getTableSelectionModel();
        int selectedIdx = lsm.getMinSelectionIndex();
        if (selectedIdx != -1) {         
           String ip = (String)model.getValueAt(selectedIdx, model.IP_ADDRESS);
           String mask =
                 (String)model.getValueAt(selectedIdx, model.SUBNET_MASK);
           valid = validateInput(ip, mask, true);    
         }                         
        return valid;
    }

    public void preRowDelete(AddRemoveList arl) {
        // do nothing here....using this interface for popupModalDialog
    }

    public void popupModalDialog(AddRemoveList arl) {
        // check to see that the maximum number of clients condition has not
        // already been met
        if (arl.getTableModel().getRowCount() == maxNumClients) {
            JOptionPane.showMessageDialog(MainFrame.getMainFrame(),
                GuiResources.getGuiString(
                    "config.sysAccess.authorize.clientMaximum", 
                    String.valueOf(maxNumClients)),
                GuiResources.getGuiString("app.name"),
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        // if a row of data is selected, then we need to ensure that it is valid
        // before adding a new row
        if (validationCheck()) {
            // need to add a new row here  -- no modal dialog to do it
            arl.getTableModel().addRow(new Object[] {}); 
        }
    }

    public boolean isRowToBeKept() {
        return false;
    }

    public boolean isCanceled() {
        return false;
    }    
    
    public int getHostBtnSelection() {
        return hostBtnSelection;
    }
    public void setHostBtnSelection(int nHostSelected) {
        hostBtnSelection = nHostSelected;
    }
    
    private void handleHostNameIpAddress() {
        if (btnIPSelected) {
          hostBtnSelection = 0;
        } else {
          hostBtnSelection = 1;
        }
        int selectedRow = tablMain.getSelectedRow();
        if (!btnIPSelected) {
           model.setValueAt("", selectedRow, model.SUBNET_MASK);
        }
        else {
           model.setValueAt("255.255.255.255", selectedRow, model.SUBNET_MASK);
        }
        if (model.isCellEditable(selectedRow, model.IP_ADDRESS)) {
            tablMain.editCellAt(selectedRow, model.IP_ADDRESS);                
            Component editor = tablMain.getEditorComponent();
            if (editor instanceof JTextComponent) {
                ((JTextComponent) editor).setCaretPosition(0);
            }
            editor.validate();
            editor.repaint();
            editor.requestFocusInWindow();
        }              
    }
    
    public void setUseIPAddress(boolean useIP) {
        btnIPSelected = useIP;
        handleHostNameIpAddress();
    }
    
    public boolean useIPAddress() {
        return true;
    }
     
    public HashMap getColumnsToChange() {
        return null;
    }
    
    /** 
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        arlClients = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(arlClients, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 403, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(arlClients, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 207, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlClients;
    // End of variables declaration//GEN-END:variables
    
}
