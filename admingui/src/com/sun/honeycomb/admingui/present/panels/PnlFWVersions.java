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
 * PnlFWVersions.java
 *
 * Created on November 20, 2006, 4:34 PM
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.Versions;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

//import java.awt.Component;
//import java.awt.Container;
/**
 * @author jp203679
 */

/**
 * Class to display Firmware versions of the Cell
 */
public class PnlFWVersions extends javax.swing.JPanel {
    
    public static String EMPTY_STR = "";
    private Versions version = new Versions(EMPTY_STR, EMPTY_STR, EMPTY_STR, 
                        EMPTY_STR, EMPTY_STR, new String[0], new String[0]);
    private Cell theCell = null;
    private HashMap nodeBiosVersion = new HashMap();
    private HashMap nodeSmdcVersion = new HashMap();
    private List nodeList = null;
    private AdminApi adminApi = ObjectFactory.getHostConnection();
    
    /**
     * Constructor which simply returns the framework panel for the 
     * firmware versions.
     */
    public PnlFWVersions() {
        initComponents(); 
        lblSPBiosVer.setText(EMPTY_STR);
        lblSPSmdcVer.setText(EMPTY_STR);
        lblSwitch1Ver.setText(EMPTY_STR);
        lblSwitch2Ver.setText(EMPTY_STR);
    }
    /**
     * Constructor which takes Cell as argument to get the Firmware version
     * of that current Cell
     */
    public PnlFWVersions(Cell theCell) {
        this.theCell = theCell;
        initComponents(); 
        populate();
        if (theCell.isAlive()) {
            makeNodeToVersionMaps();
        }
    }
    
    /**
     * Set Firmware Version labels to the Cell on the panel
     */
    private void populate() {
        try {
            if (theCell.isAlive()) {
                version = adminApi.getFwVersions(theCell);
                lblSPBiosVer.setText(version.getSPBios());
                lblSPSmdcVer.setText(version.getSPSmdc());
                lblSwitch1Ver.setText(version.getSwitch1Overlay());
                lblSwitch2Ver.setText(version.getSwitch2Overlay());
            } else {
                lblSPBiosVer.setText(
                            GuiResources.getGuiString("cellProps.unavailable"));
                lblSPSmdcVer.setText(
                            GuiResources.getGuiString("cellProps.unavailable"));
                lblSwitch1Ver.setText(
                            GuiResources.getGuiString("cellProps.unavailable"));
                lblSwitch2Ver.setText(
                            GuiResources.getGuiString("cellProps.unavailable"));
            }
        } catch (Exception e ) {
            throw new RuntimeException(e);
        }
    }
    
    public Dimension getFWPanelDimension() {
        int height = pnlFWVersions.getHeight();
        int width = pnlFWVersions.getWidth();
        return new Dimension(width, height);
    }
    
    /**
     * Returns current cell versions
     */
    public Versions getFWVersions() {
        return version;
    }
    
    // Cell version
    public String getCellVersion() {
        return version.getCellVer();
    }
    
    // Service Node Bios
    public String getSPBiosVersion() {
        return version.getSPBios();
    }
    
    // Service Node Smdc
    public String getSPSmdcVersion() {
        return version.getSPSmdc();
    }
    
    // Switch 1 Overlay
    public String getSwitch1OverlayVersion() {
        return version.getSwitch1Overlay();
    }
    
    // Switch 2 Overlay
    public String getSwitch2OverlayVersion() {
        return version.getSwitch2Overlay();
    }
    
    // Node Bios
    public String[] getBiosVersion() {
        return version.getBios();
    }
    
    // Node Smdc
    public String[] getSmdcVersion() {
        return version.getSmdc();
    }
    
    public String getNodeBios(Node n) {
        String bios = (String)nodeBiosVersion.get(new Integer(n.getNodeID()));
        if (bios == null) {
            bios = EMPTY_STR;
        }
        return bios;
    }
    
    public String getNodeSmdc(Node n) {
        String smdc = (String)nodeSmdcVersion.get(new Integer(n.getNodeID()));
        if (smdc == null) {
            smdc = EMPTY_STR;
        }
        return smdc;
    }
    
    private void makeNodeToVersionMaps() {
        try {
            Node aNode = null;
            String[] biosVer = version.getBios();
            String[] smdcVer = version.getSmdc();
            nodeList = Arrays.asList(adminApi.getNodes(theCell));

            // create two hashmaps -- <nodeId, biosVersion> 
            // and <nodeId, smdcVersion>
            for (int i = 0; i < biosVer.length; i++) {
                aNode = (Node)nodeList.get(i);
                nodeBiosVersion.put(new Integer(aNode.getNodeID()), biosVer[i]);
            }

            for (int idx = 0; idx < smdcVer.length; idx++) {
                aNode = (Node)nodeList.get(idx);
                nodeSmdcVersion.put(new Integer(aNode.getNodeID()), smdcVer[idx]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the FormEditor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        pnlFWVersions = new javax.swing.JPanel();
        lblSPBios = new javax.swing.JLabel();
        lblSPSmdc = new javax.swing.JLabel();
        lblSwitch1 = new javax.swing.JLabel();
        lblSPBiosVer = new javax.swing.JLabel();
        lblSPSmdcVer = new javax.swing.JLabel();
        lblSwitch1Ver = new javax.swing.JLabel();
        lblSwitch2 = new javax.swing.JLabel();
        lblSwitch2Ver = new javax.swing.JLabel();

        pnlFWVersions.setBorder(javax.swing.BorderFactory.createTitledBorder(null, java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.firmware.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Microsoft Sans Serif", 1, 12)));
        pnlFWVersions.setAlignmentY(0.0F);
        pnlFWVersions.setMaximumSize(new java.awt.Dimension(1000, 1000));
        pnlFWVersions.setMinimumSize(new java.awt.Dimension(0, 0));
        pnlFWVersions.setPreferredSize(new java.awt.Dimension(0, 0));
        lblSPBios.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.sp.bios.version"));

        lblSPSmdc.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.sp.smdc.version"));

        lblSwitch1.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.switch.one.version"));

        lblSPBiosVer.setFont(new java.awt.Font("Dialog", 0, 12));

        lblSPSmdcVer.setFont(new java.awt.Font("Dialog", 0, 12));

        lblSwitch1Ver.setFont(new java.awt.Font("Dialog", 0, 12));

        lblSwitch2.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("cell.switch.two.version"));

        lblSwitch2Ver.setFont(new java.awt.Font("Dialog", 0, 12));

        org.jdesktop.layout.GroupLayout pnlFWVersionsLayout = new org.jdesktop.layout.GroupLayout(pnlFWVersions);
        pnlFWVersions.setLayout(pnlFWVersionsLayout);
        pnlFWVersionsLayout.setHorizontalGroup(
            pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFWVersionsLayout.createSequentialGroup()
                .add(12, 12, 12)
                .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(pnlFWVersionsLayout.createSequentialGroup()
                        .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblSwitch1)
                            .add(pnlFWVersionsLayout.createSequentialGroup()
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(lblSwitch2)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblSwitch1Ver, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)
                            .add(lblSwitch2Ver, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)))
                    .add(pnlFWVersionsLayout.createSequentialGroup()
                        .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblSPBios)
                            .add(lblSPSmdc))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblSPBiosVer)
                            .add(lblSPSmdcVer))))
                .add(40, 40, 40))
        );
        pnlFWVersionsLayout.setVerticalGroup(
            pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFWVersionsLayout.createSequentialGroup()
                .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblSPBios)
                    .add(lblSPBiosVer))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblSPSmdc)
                    .add(lblSPSmdcVer))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblSwitch1)
                    .add(lblSwitch1Ver))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlFWVersionsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblSwitch2Ver)
                    .add(lblSwitch2))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlFWVersionsLayout.linkSize(new java.awt.Component[] {lblSwitch1Ver, lblSwitch2Ver}, org.jdesktop.layout.GroupLayout.VERTICAL);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFWVersions, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 186, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlFWVersions, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 115, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lblSPBios;
    private javax.swing.JLabel lblSPBiosVer;
    private javax.swing.JLabel lblSPSmdc;
    private javax.swing.JLabel lblSPSmdcVer;
    private javax.swing.JLabel lblSwitch1;
    private javax.swing.JLabel lblSwitch1Ver;
    private javax.swing.JLabel lblSwitch2;
    private javax.swing.JLabel lblSwitch2Ver;
    private javax.swing.JPanel pnlFWVersions;
    // End of variables declaration//GEN-END:variables
    
}
