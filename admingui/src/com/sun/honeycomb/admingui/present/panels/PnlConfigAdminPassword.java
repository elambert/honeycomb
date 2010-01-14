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
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.UserErrorMessage;
import com.sun.nws.mozart.ui.utility.UserMessage;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 *
 * @author  dp127224
 */
public class PnlConfigAdminPassword extends JPanel implements ContentPanel {
                                            
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;

    /** Creates new form PanelAdmin */
    public PnlConfigAdminPassword() {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() {
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
    }
    
    //////////////////////////////////////////
    // ContentPanel Impl
    
    public String getTitle() {
        return GuiResources.getGuiString("config.sysAccess.password.title");
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
    
    public void loadValues() throws UIException {
        txtpCurrentPassword.setText("");
        txtpNewPassword.setText("");
        txtpReenterPassword.setText("");    
    }
    
    public void saveValues() throws UIException, HostException {
        try {
            // Verify correct password entered
            String currentPwd = new String(txtpCurrentPassword.getPassword());
            if (!hostConn.verifyPasswd(currentPwd)) {
                UserMessage msg = new UserErrorMessage(
                        GuiResources.getMsgString(
                            "error.problem.cantSave"),
                        GuiResources.getMsgString("error.cause.badPassword"),
                        GuiResources.getMsgString(
                            "error.solution.enterCorrectPassword"));
                throw new UIException(msg);
            }

            // Verify new and reenter the same
            String newPwd = new String(txtpNewPassword.getPassword());
            String reenterPwd = new String(txtpReenterPassword.getPassword());
            if (!newPwd.equals(reenterPwd)) {
                UserMessage msg = new UserErrorMessage(
                    GuiResources.getMsgString("error.problem.cantSave"),
                    GuiResources.getMsgString("error.cause.passwordMismatch"),
                    GuiResources.getMsgString("error.solution.reenter"));
                throw new UIException(msg);
            }
            hostConn.setPasswd(newPwd);
//            Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
            Log.logInfoMessage(GuiResources.getGuiString(
                    "config.sysAccess.password.saved"));
            
            // Flush cache
            ObjectFactory.clearCache();
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        } 
    }

    public String getPageKey() {
        return HelpFileMapping.CHANGEADMINISTRATIONPASSWORD;
    }

    //
    //////////////////////////////////////////

    /** 
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        lblCurrentPassword = new javax.swing.JLabel();
        lblNewPassword = new javax.swing.JLabel();
        lblReenterPassword = new javax.swing.JLabel();
        txtpCurrentPassword = new javax.swing.JPasswordField();
        txtpNewPassword = new javax.swing.JPasswordField();
        txtpReenterPassword = new javax.swing.JPasswordField();

        lblCurrentPassword.setDisplayedMnemonic(GuiResources.getGuiString("config.sysAccess.password.current.mn").charAt(0));
        lblCurrentPassword.setLabelFor(txtpCurrentPassword);
        lblCurrentPassword.setText(GuiResources.getGuiString("config.sysAccess.password.current"));

        lblNewPassword.setDisplayedMnemonic(GuiResources.getGuiString("config.sysAccess.password.new.mn").charAt(0));
        lblNewPassword.setLabelFor(txtpNewPassword);
        lblNewPassword.setText(GuiResources.getGuiString("config.sysAccess.password.new"));

        lblReenterPassword.setDisplayedMnemonic(GuiResources.getGuiString("config.sysAccess.password.reenter.mn").charAt(0));
        lblReenterPassword.setLabelFor(txtpReenterPassword);
        lblReenterPassword.setText(GuiResources.getGuiString("config.sysAccess.password.reenter"));

        txtpCurrentPassword.setMinimumSize(new java.awt.Dimension(20, 20));

        txtpNewPassword.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                txtpNewPasswordKeyTyped(evt);
            }
        });

        txtpReenterPassword.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                txtpReenterPasswordKeyTyped(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblCurrentPassword, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 109, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(lblNewPassword, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 109, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(lblReenterPassword))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(txtpCurrentPassword, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 173, Short.MAX_VALUE)
                    .add(txtpNewPassword, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 173, Short.MAX_VALUE)
                    .add(txtpReenterPassword, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 173, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {txtpCurrentPassword, txtpNewPassword, txtpReenterPassword}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblCurrentPassword)
                    .add(txtpCurrentPassword, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblNewPassword)
                    .add(txtpNewPassword, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblReenterPassword)
                    .add(txtpReenterPassword, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(14, Short.MAX_VALUE))
        );

        layout.linkSize(new java.awt.Component[] {txtpCurrentPassword, txtpNewPassword, txtpReenterPassword}, org.jdesktop.layout.GroupLayout.VERTICAL);

    }// </editor-fold>//GEN-END:initComponents

    private void txtpReenterPasswordKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtpReenterPasswordKeyTyped
        explItem.setIsModified(true);
    }//GEN-LAST:event_txtpReenterPasswordKeyTyped

    private void txtpNewPasswordKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtpNewPasswordKeyTyped
        explItem.setIsModified(true);
    }//GEN-LAST:event_txtpNewPasswordKeyTyped
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lblCurrentPassword;
    private javax.swing.JLabel lblNewPassword;
    private javax.swing.JLabel lblReenterPassword;
    private javax.swing.JPasswordField txtpCurrentPassword;
    private javax.swing.JPasswordField txtpNewPassword;
    private javax.swing.JPasswordField txtpReenterPassword;
    // End of variables declaration//GEN-END:variables
    
}
