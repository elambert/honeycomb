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
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 *
 * @author  jb127219
 */
public class PnlGlobalCellOps extends JPanel implements ContentPanel {

    private ExplorerItem explItem = null;
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ArrayList cellIds = null;
    
    private boolean chkboxSel = false;
    
    class NotifyPanel extends JPanel {
      public NotifyPanel(String s1, String s2) {
         chkboxSel = false;
         final JCheckBox options = new JCheckBox( s1, false);
          options.addActionListener(new ActionListener() {
              public void actionPerformed(ActionEvent e) {
                chkboxSel = options.isSelected();
              }
          });
          setLayout(new BorderLayout());
          add(new JLabel( s2 ), BorderLayout.CENTER);
          add(options, BorderLayout.SOUTH);
       }
    }
    
    /** Creates new form PnlGlobalCellOps */
    public PnlGlobalCellOps() {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {    
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
        
        btnGroup.add(rbtnWipe);
        btnGroup.add(rbtnReboot);
        btnGroup.add(rbtnShutdown);
        /**
         * the "wipe" radio button is selected
         */
        rbtnWipe.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    explItem.setIsModified(false);
                }
            });
        /**
         * the "reboot" radio button is selected
         */
        rbtnReboot.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    explItem.setIsModified(false);
                }
            });
            
        /**
         * the "shutdown" radio button is selected
         */
        rbtnShutdown.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    explItem.setIsModified(false);
                }
            });
    }
    
    // ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("cell.operation.global.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_NONE; }
    public ButtonPanel getButtonPanel() {
        ButtonPanel bPanel = null;
        if (ObjectFactory.isAdmin()) {
            bPanel = BtnPnlApplyCancel.getButtonPanel();
        } else {
            bPanel = BtnPnlBlank.getButtonPanel();
        }
        return bPanel;
    }
    
    public void loadValues() throws UIException, HostException {
        if (!ObjectFactory.isAdmin()) {
            disableAllItems();
        }    
        
        rbtnWipe.setSelected(false);
        rbtnReboot.setSelected(false);
        rbtnShutdown.setSelected(false);
    }
    
    public void saveValues() throws UIException, HostException {
        try {
            String confirm = null;
            String opName = null;
            String submitted = null;
            String chkboxMsg = null;
            Object chkboxParam =  null;                  
            
            if (rbtnWipe.isSelected()) {
                opName = GuiResources.getGuiString("cell.operation.wipeAll");
                confirm = GuiResources.getGuiString(
                        "cell.operation.wipeAll.confirm");
                submitted = GuiResources.getGuiString(
                            "cell.operation.wipeAll.submitted");
                chkboxParam = confirm;     
            } else if (rbtnReboot.isSelected()) {
                opName = GuiResources.getGuiString("cell.operation.rebootAll");
                confirm = GuiResources.getGuiString(
                        "cell.operation.rebootAll.confirm");
                submitted = GuiResources.getGuiString(
                            "cell.operation.rebootAll.submitted");
                chkboxMsg = GuiResources.getGuiString
                        ("cell.operation.reboot.options");
                chkboxParam = new NotifyPanel(chkboxMsg, confirm); 
            } else {
                opName = GuiResources.getGuiString("cell.operation.shutdownAll");
                confirm = GuiResources.getGuiString(
                        "cell.operation.shutdownAll.confirm");
                submitted = GuiResources.getGuiString(
                            "cell.operation.shutdownAll.submitted");
                chkboxMsg = GuiResources.getGuiString
                        ("cell.operation.shutdown.options");
                chkboxParam = new NotifyPanel(chkboxMsg, confirm); 
            }
            int retVal = JOptionPane.showConfirmDialog(MainFrame.getMainFrame(),
                                        chkboxParam,
                                        GuiResources.getGuiString("app.name"),
                                        JOptionPane.YES_NO_OPTION);
            if (retVal == JOptionPane.YES_OPTION) {
                if (opName.equals(GuiResources.getGuiString(
                                            "cell.operation.wipeAll"))) {
                    hostConn.wipeDisks();  
                } else if (opName.equals(GuiResources.getGuiString(
                                            "cell.operation.rebootAll"))) {
                    hostConn.reboot(null, chkboxSel, chkboxSel);    
                } else {
                    hostConn.powerOff(null, chkboxSel, chkboxSel);
                }
                Log.logInfoMessage(submitted);
                ObjectFactory.clearCache(); // Flush cache
            }
        } catch (ClientException e) {
            Log.logInfoMessage(
                "Client Exception caught! Reason: " + e.getMessage());
            throw new HostException(e);
        } catch (ServerException e) {
            Log.logInfoMessage(
                "Server Exception caught! Reason: " + e.getMessage());
            throw new HostException(e);
        }
    }

    public String getPageKey() {
        return "n/a"; // HelpFileMapping.GLOBALCELLOPS;
    }
    
    // ************************************ 
    
    /**
     * Disable all items 
     */
    private void disableAllItems() {
        Component [] allComponents = this.getComponents();
        for (int i = 0; i < allComponents.length; i++) {
              allComponents[i].setEnabled(false);
        }
    }


    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        btnGroup = new javax.swing.ButtonGroup();
        rbtnWipe = new javax.swing.JRadioButton();
        rbtnReboot = new javax.swing.JRadioButton();
        rbtnShutdown = new javax.swing.JRadioButton();

        rbtnWipe.setMnemonic(GuiResources.getGuiString("cell.operation.wipeAll.mn").charAt(0));
        rbtnWipe.setText(GuiResources.getGuiString("cell.operation.wipeAll"));
        rbtnWipe.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        rbtnWipe.setMargin(new java.awt.Insets(0, 0, 0, 0));

        rbtnReboot.setMnemonic(GuiResources.getGuiString("cell.operation.rebootAll.mn").charAt(0));
        rbtnReboot.setText(GuiResources.getGuiString("cell.operation.rebootAll"));
        rbtnReboot.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        rbtnReboot.setMargin(new java.awt.Insets(0, 0, 0, 0));

        rbtnShutdown.setMnemonic(GuiResources.getGuiString("cell.operation.shutdownAll.mn").charAt(0));
        rbtnShutdown.setText(GuiResources.getGuiString("cell.operation.shutdownAll"));
        rbtnShutdown.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        rbtnShutdown.setMargin(new java.awt.Insets(0, 0, 0, 0));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(33, 33, 33)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(rbtnReboot)
                    .add(rbtnWipe)
                    .add(rbtnShutdown))
                .addContainerGap(36, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(26, 26, 26)
                .add(rbtnWipe)
                .add(27, 27, 27)
                .add(rbtnReboot)
                .add(27, 27, 27)
                .add(rbtnShutdown)
                .addContainerGap(35, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup btnGroup;
    private javax.swing.JRadioButton rbtnReboot;
    private javax.swing.JRadioButton rbtnShutdown;
    private javax.swing.JRadioButton rbtnWipe;
    // End of variables declaration//GEN-END:variables
    
}
