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
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveList;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListDialog;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListResolver;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.Log;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;

/**
 *
 * @author  dp127224
 */
public class PnlConfigNetworkNtp extends JPanel 
         implements ContentPanel, AddRemoveListDialog, AddRemoveListResolver {
                                            
     //flag to indicate the type of server, host name or IP Address selected
    public static final int RADIO_IP_BTN_SELECT   = 0;
    public static final int RADIO_HOST_BTN_SELECT = 1;
    public static final int RADIO_NO_BTN_SELECT   = -1;
    
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private PnlConfigNetworkNtpTableModel model = null;
    private boolean keepNtpServer = false;
    private StringBuffer validationError = new StringBuffer();
    private BtnPnlApplyCancel bPanel = new BtnPnlApplyCancel();
    private JTable tablMain = null;
    private boolean btnIPSelected = true;
    private int hostBtnSelection = RADIO_NO_BTN_SELECT;
    
    /** Creates new form PanelAdmin */
    public PnlConfigNetworkNtp() {
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
                    String Host = (String)super.getValueAt(sr, model.SERVER);                 
                    valid = validateInput(Host, true);
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
        arlNtpServers.setJTable(tablMain);
        model = new PnlConfigNetworkNtpTableModel();
        model.setPanel(this);
        arlNtpServers.setDialog(this);
        arlNtpServers.setResolver(this);
        arlNtpServers.setTableModel(model);
        arlNtpServers.setColumnHeaderVisible(false);       
        arlNtpServers.setAddButtonTooltip(GuiResources.getGuiString(
                                    "config.network.ntp.addTooltip"));
        arlNtpServers.setRemoveButtonTooltip(GuiResources.getGuiString(
                                    "config.network.ntp.removeTooltip"));                                       
        // Column editor
        TableColumnModel colModel = arlNtpServers.getTableColumnModel();
        TableColumn col = colModel.getColumn(
                                    PnlConfigNetworkNtpTableModel.SERVER);      
        col.setCellEditor(new NetworkNtpCellEditor(new IpAddressField(), false));        
        col.setCellRenderer(new NetworkNtpCellRenderer());
        NetworkNtpCellEditor iace = (NetworkNtpCellEditor)col.getCellEditor();
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
                    Log.logInfoMessage(
                        GuiResources.getGuiString("config.network.ntp.notsaved",
                                                validationError.toString()));                    
                    throw new RuntimeException(
                        new ClientException("data.validation.error"));
                }
            }
        });
        
        // Lifting data retention requirement from hivecfg command as of 5/3/07
//        // CR 6542587 - warn user if less than 3 servers configured
//        complianceLabel.setForeground(model.getRowCount() < 3 ? Color.RED :
//            UIManager.getColor("Panel.background"));
        
        // Model listener
        model.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                explItem.setIsModified(true);
                bPanel.setShowConfirmDialog(true,
                    GuiResources.getGuiString("cell.operation.reboot.warning"),
                    GuiResources.getGuiString("app.name"));
                
                if (e.getType() == TableModelEvent.INSERT) {
                    int row = e.getFirstRow();
                    if (model.getValueAt(row, model.SERVER) == null) {
                        model.setValueAt("0.0.0.0", row, model.SERVER);
                    }                    
                }                
//                complianceLabel.setForeground(model.getRowCount() < 3 ? 
//                    Color.RED : UIManager.getColor("Panel.background"));
            }
        });
        ListSelectionModel rowModel = arlNtpServers.getTableSelectionModel();
        rowModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int currentRow = lsm.getMinSelectionIndex();
                    model.setCurrentSelection(currentRow);
                }
            }});
    }
    
    //////////////////////////////////////////
    // ContentPanel Impl
    
    public String getTitle() {
        return GuiResources.getGuiString("config.network.ntp.title");
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
        // Disable add/remove list if user has read only permission
        arlNtpServers.setEnabled(ObjectFactory.isAdmin());      
        try {
            BaseTableModel model = arlNtpServers.getTableModel();
            arlNtpServers.clearTable();

            String [] servers = hostConn.getNTPAddrs();
            if (servers == null) {
                return;
            }
            for (int i = 0; i < servers.length; i++) {
                String server = servers[i];  
                if (server.length() != 0) {
                    model.addRow(new Object[] { server });
                }
            }     

        } catch (Exception e) {
            throw new RuntimeException(e);
        }            
    }
    
    public void saveValues() throws UIException, HostException {
        try {
            int errCnt = 0;
            StringBuffer localBuffer = new StringBuffer();
            BaseTableModel model = arlNtpServers.getTableModel();
            int cnt = model.getRowCount();
            ArrayList servers = new ArrayList();            
            String rowValue = null;            
            for (int i = 0; i < cnt; i++) {
                //get the servers in the table and verify if each entry
                //is valid (this is needed as the user might exit the screen
                //entering invalid entry and the regular validate check is not 
                //performed when user clicks  on a different operation.
                rowValue = (String) model.getValueAt(i, 
                                    PnlConfigNetworkNtpTableModel.SERVER); 
                if (validateInput(rowValue, false)) {
                    servers.add(rowValue);                 
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
                        "config.network.ntp.notsaved", 
                                localBuffer.toString()), null);
                explItem.setIsErrorOnPanel(true);
                throw new RuntimeException(
                        new ClientException("data.validation.error"));
            } 
            String strServers  [  ]  = new String  [ servers.size  (  )  ] ; 
            servers.toArray  ( strServers ) ;
            hostConn.setNTPAddrs(strServers);
            Log.logInfoMessage(
                    GuiResources.getGuiString("config.network.ntp.saved"));
            
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
        return HelpFileMapping.SETNTPSERVERS;
    }

    public void preRowDelete(AddRemoveList arl) {
        keepNtpServer = false;
        BaseTableModel data = arl.getTableModel();
        // do not allow delete if trying to remove the last row in the table
        if (data.getRowCount() == 1) {
            keepNtpServer = true;
            String msg = 
                    GuiResources.getGuiString("config.network.ntp.noDeleteMsg");
            JOptionPane.showMessageDialog(MainFrame.getMainFrame(), msg);
        }
    }

    public void popupModalDialog(AddRemoveList arl) {
        // if a row of data is selected, then we need to ensure that it is valid
        // before adding a new row
        if (validationCheck()) {
            //need to add a new row here since there is no modal dialog to do it
            arl.getTableModel().addRow(new Object[] {}); 
        }
    }

    public boolean isRowToBeKept() {
        return keepNtpServer;
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
        BaseTableModel model = (BaseTableModel) tablMain.getModel();
        int selectedRow = tablMain.getSelectedRow();
        int colCnt = model.getColumnCount();
        for (int i = 0; i < colCnt; i++) {
            if (model.isCellEditable(selectedRow, i)) {
                tablMain.editCellAt(selectedRow, i);                
                Component editor = tablMain.getEditorComponent();
                if (editor instanceof JTextComponent) {
                    ((JTextComponent) editor).setCaretPosition(0);
                }
                editor.validate();
                editor.repaint();
                editor.requestFocusInWindow();
                //break;
            }
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
    
    
    private boolean validationCheck() {
        // check to see if user has selected or modified a NTP server
        // entry in the table -- if so, check to see if whatever was last 
        // selected or modified is valid.
        boolean valid = true;
        ListSelectionModel lsm = arlNtpServers.getTableSelectionModel();
        int selectedIdx = lsm.getMinSelectionIndex();
        if (selectedIdx != -1) {
            String Host = (String)model.getValueAt(selectedIdx, model.SERVER);
            valid = validateInput(Host, true);
        }
        return valid;
    }
     
    public boolean validateInput(String Host, boolean showMsg) {
        try {
            validationError = new StringBuffer();
            // check the validity of ntp servers
            if (Host.equals("0.0.0.0")) {
                // invalid -- show warning
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                validationError.append(GuiResources.getGuiString(
                               "config.ip.invalid", Host));
            } else if (!Validate.isValidHostname(Host)) {
                // invalid -- show warning
                if (validationError.length() > 0) {
                    validationError.append("\n");
                }
                validationError.append(GuiResources.getGuiString(
                                "config.host.invalid", Host));
            }

            //now  check to see if the DNS is enabled or else
            //do not allow servers with host names to be stored.
            if (model.hasHostNameType() && !Validate.isValidIpAddress(Host)) {
                if (!hostConn.isDnsEnabled() ) {
                    //give warning to enable DNS before entering hostnames and exit
                    validationError.append(GuiResources.getGuiString(
                            "config.dnsDisabled"));
                }
            }
            // now check to see if somehow there might be duplicate entries
            // in the table - shouldn't pass duplicate ntp servers
            // TODO
            if (validationError.length() > 0 ) {
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
    
    
    //
    //////////////////////////////////////////

    private void handleIPAddressPropertyChanged(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(IpAddressField.PROP_IP_ADDRESS)) {
            explItem.setIsModified(true);
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
        arlNtpServers = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        complianceLabel = new javax.swing.JLabel();

        arlNtpServers.setTableEmptyText("");

        complianceLabel.setForeground(java.awt.Color.red);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(arlNtpServers, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 261, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(complianceLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 249, Short.MAX_VALUE)
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(arlNtpServers, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 191, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(complianceLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlNtpServers;
    private javax.swing.JLabel complianceLabel;
    // End of variables declaration//GEN-END:variables
    
}
