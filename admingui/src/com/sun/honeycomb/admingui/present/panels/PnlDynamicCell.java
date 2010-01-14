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
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.CellProps;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Fru;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.Disk;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.client.ServiceNode;
import com.sun.honeycomb.admingui.client.Switch;
import com.sun.honeycomb.admingui.client.Versions;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 *
 * @author  jb127219
 */
public class PnlDynamicCell extends javax.swing.JPanel {
    
    private static final String CELL_PROPS_UNAVAILABLE = 
                        GuiResources.getGuiString("cellProps.unavailable");
    private Dimension diskSize = null;
    private Dimension spSize = null;
    private Dimension switchSize = null;
    
    private Cell theCell = null;
    private Node[] nodes = null;
    private Switch[] switches = null;
    private ServiceNode sp = null;
    private int numNodes = -1;
    private boolean staticImg = false;
    private boolean cellError = false;
    private String cellErrTooltip = "";
    private Versions version = null;
    private String switch1Tooltip = "";
    private String switch2Tooltip = "";
    private String spTooltip = "";
    
    // Outer most vertical box for cell image, which contains the images
    // for the service node, switches and all of the nodes/disks
    private Box vCellBox = null;
    
    // Panel containing the image for Switch 1...this is the first panel added
    // to the vCellBox because it is at the top of the cell image
    private PnlImageCellComponent siloSwitch1Panel = 
                                        new PnlImageCellComponent();
    
    // Panel containing the image for Switch 2...this is the second panel added
    // to the vCellBox
    private PnlImageCellComponent siloSwitch2Panel = 
                                        new PnlImageCellComponent();
    
    // Panel containing the image for the service node...this panel is added
    // to the vCellBox before all of the nodes/disks
    private PnlImageCellComponent siloSPPanel = new PnlImageCellComponent();
    
    // Panel containing ONLY the disks for the cell image...this panel is added
    // to the vCellBox AFTER the panels showing the service node and 
    // switches have been added
    private JPanel pnlAllCellDisks = null;
    // Panel containing the SP and switches for the cell image...this panel
    // is added to the vCellBox BEFORE the pnlAllCellDisks containing the nodes
    // and disks has been added
    private JPanel pnlSPSwitches = null;
     
    // Map in which <key, value> pairs consist of 
    // < nodeId, Vector<LblDiskImage> >
    private HashMap nodeDisks = null;
    
    // Stack to achieve LIFO behavior for ordering nodes within the cell image.
    // The lowest node Id will be at the bottom of this stack (e.g. 101...116)
    private Stack nodeIdStack = new Stack();
    
    // Stack to achieve LIFO behavior, which is opposite that of the node Id  
    // stack with respect to the node Id order.     
    // Thus, the indices of this node panel stack will match the indices of the 
    // Node Summary Table.  This stack is used to highlight the nodePanel  
    // corresponding to the row highlighted/clicked in the Node Summary Table.
    private Stack panelStack = new Stack();
    
    /**
     * Creates a panel which contains the dynamic cell image
     */
    public PnlDynamicCell(Cell cell, Node[] nodeArray, 
                                                Switch[] s, ServiceNode sn)
            throws UIException, ClientException, ServerException {
        theCell = cell;
        nodes = nodeArray;
        switches = s;
        sp = sn;
        numNodes = nodes.length;
        initComponents();
        initComponents2();
        doWork();
    }
    /**
     * Creates a panel which contains the static cell image
     */
    public PnlDynamicCell(boolean errOnCell, String ttt, int numOfNodes)
            throws UIException, ClientException, ServerException {
        numNodes = numOfNodes;
        staticImg = true;
        cellError = errOnCell;
        cellErrTooltip = ttt;
        initComponents();
        initComponents2();
        doWork();
    }
 
    private void initComponents2() {
        JButton btnAddToPanel = null;
        diskSize = getImageSize(Fru.TYPE_DISK);
        spSize =  getImageSize(Fru.TYPE_SP);
        switchSize =  getImageSize(Fru.TYPE_SWITCH);
        
        // This panel contains all of the panels, which represent nodes, that
        // contain labels, which represent disks.
        pnlAllCellDisks = new JPanel(new GridLayout(numNodes, 1));
        pnlAllCellDisks.setPreferredSize(diskSize);
        pnlAllCellDisks.setMaximumSize(diskSize);
        pnlAllCellDisks.setMinimumSize(diskSize);
        
        // This panel contains the SP and Switch components
        Dimension spSwitchesPnlSize = new Dimension(
                AdminApi.DISKS_PER_NODE * LblDiskImage.DISK_IMG.getIconWidth(), 
                LblServiceNodeImage.SP_IMG.getIconHeight() + 
                        (2*LblSwitchImage.SWITCH_IMG.getIconHeight()));
        pnlSPSwitches = new JPanel(new GridLayout(3, 1));
        pnlSPSwitches.setPreferredSize(spSwitchesPnlSize);
        pnlSPSwitches.setMaximumSize(spSwitchesPnlSize);
        pnlSPSwitches.setMinimumSize(spSwitchesPnlSize);
        
        // Creates the outer most vertical box for the image, which contains
        // all of the subsequent panels (i.e. switch2, switch1, sp and nodes) --
        // Do NOT change the order that these panels are added to vCellBox 
        // since they match the way the cell looks physically.
        vCellBox = Box.createVerticalBox();
        
        // Each switch and SP image is on a JLabel, which is added to a 
        // PnlImageCellComponent (JPanel).  It's added to an invisible button,
        // btnAddToOuterPanel, to handle the case where user clicks on the 
        // image.  The button is then added to another JPanel, pnlSPSwitches,
        // to ensure proper layout.  The pnlSPSwitches is added to the vCellBox,
        // which represents the entire cell image.

        // Switch 2 image
        btnAddToPanel = makeComponentPnlOnButton(siloSwitch2Panel, switchSize);
        pnlSPSwitches.add(btnAddToPanel);
        // Switch 1 image
        btnAddToPanel = makeComponentPnlOnButton(siloSwitch1Panel, switchSize);
        pnlSPSwitches.add(btnAddToPanel);
        // ServiceNode image
        btnAddToPanel = makeComponentPnlOnButton(siloSPPanel, spSize);
        pnlSPSwitches.add(btnAddToPanel);
        
        // add the panel containing the SP and switches to the vertical box
        vCellBox.add(pnlSPSwitches);
        
        // add the panel containing all nodes/disks to the vertical box
        vCellBox.add(pnlAllCellDisks);

        // Add the cell panel to the overall panel
        pnlCellImage.add(vCellBox);
        
//// ATTEMPT to outline the cell disks in red to denote a cell error --
//// this doesn't work....TODO -- figure out why
////        pnlAllCellDisks.setLayout(
////                    new BoxLayout(pnlAllCellDisks, BoxLayout.PAGE_AXIS));
////        if (cellError) {
////            pnlAllCellDisks.setBorder(
////                            BorderFactory.createLineBorder(Color.RED, 2));
////            pnlAllCellDisks.setToolTipText(GuiResources
////                            .getGuiString("cell.error.toolTip"));
////        }
    }
    /**
     * Accessor method to retrieve all of the panels.
     *
     * @return A stack containing the panels created that are part of
     *         the cell image.
     */
    public Vector getCellPanels() {
        return panelStack;
    } 
    
    private void doWork() throws UIException {
        setUpNodeData();
        setUpSPSwitchData();
        if (!this.staticImg) {
            // dynamic
            buildDynamicCell();
        } else {
            // static
            buildStaticCell();
        }
        panelStack.add(siloSPPanel);
        panelStack.add(siloSwitch1Panel);
        panelStack.add(siloSwitch2Panel);
    }
    /**
     * Method gets the node and disk data needed to build the dynamic cell image
     */
    private void setUpNodeData() throws UIException {
        Disk[] disks = null;
        Vector diskImages = null;
 
        // Iterate through nodes and create disk images for each node.
        // Store vector of disk images on a per node basis in a hashmap
        // where the key is the node Id.
        nodeDisks = new HashMap(numNodes);
        for (int nIdx = 0; nIdx < numNodes; nIdx++) {
            int numDisks = 0;
            if (!staticImg) {
                // Get all of the disks per node
                disks = nodes[nIdx].getDisks();
                numDisks = disks.length;
            } else {
                numDisks = AdminApi.DISKS_PER_NODE;
            }

            
            // Iterate through disks and create an image for each disk
            // belonging to the node.  Store images in the vector.
            diskImages = new Vector(numDisks);
            for (int dIdx = 0; dIdx < numDisks; dIdx++) {
                LblDiskImage diskImage = null;
                if (!staticImg) {
                    diskImage = new LblDiskImage(disks[dIdx]);
                } else {
                    diskImage = 
                        cellError ? new LblDiskImage(true, cellErrTooltip) : 
                                                            new LblDiskImage();
                }
                diskImages.add(diskImage);
            }
            
            Integer nodeId = null;
            if (!staticImg) {
                nodeId = new Integer(nodes[nIdx].getNodeID());
            } else {
                nodeId = new Integer(nIdx);
            }
            // Construct a HashMap consisting of a <NodeId, LblDiskImage[]>
            nodeDisks.put(nodeId, diskImages);
            // Add nodeId to stack to preserve node order when 
            // building cell image
            nodeIdStack.push(nodeId);
        }
    }
    /**
     * Method gets the SP and Switch data needed to build the dynamic cell image
     */
    private void setUpSPSwitchData() throws UIException {
        try {
            CellProps cellProps = new CellProps();
            String switch1ver, switch2ver, spBiosVer, spSmdcVer  = "";
            
            // initial states are for static cell -- if dynamic cell then 
            // LblImageBase objects (sp, switches) will be created with data
            LblImageBase spImage = cellError ? 
                            new LblServiceNodeImage(true, cellErrTooltip) :
                                                    new LblServiceNodeImage();
            LblImageBase switch1Image = cellError ? 
                            new LblSwitchImage(true, cellErrTooltip) :
                                                    new LblSwitchImage();
            LblImageBase switch2Image = cellError ? 
                            new LblSwitchImage(true, cellErrTooltip) :
                                                    new LblSwitchImage();
            if (!staticImg) {
                AdminApi adminApi = ObjectFactory.getHostConnection();
                try {
                    version = adminApi.getFwVersions(theCell);
                    // 2 switches showing firmware version
                    // TODO -- need API to grab switch firmware
                    switch1ver = version.getSwitch1Overlay();
                    switch2ver = version.getSwitch2Overlay();
                
                    // 1 service node showing firmware version
                    spBiosVer = version.getSPBios();
                    spSmdcVer = version.getSPSmdc();
                } catch (Exception e) {
                    switch1ver = CELL_PROPS_UNAVAILABLE;
                    switch2ver = CELL_PROPS_UNAVAILABLE;       
                    spBiosVer = CELL_PROPS_UNAVAILABLE;        
                    spSmdcVer = CELL_PROPS_UNAVAILABLE; 
                }      
                switch1Image = new LblSwitchImage(switches[0], 
                        GuiResources.getGuiString(
                                "cell.silo.switch1.toolTip", new String[] {
                                    Fru.getStatusAsString(
                                        Fru.TYPE_SWITCH, 
                                        switches[0].getStatus()), switch1ver}));
                switch2Image = new LblSwitchImage(switches[1], 
                        GuiResources.getGuiString(
                                "cell.silo.switch2.toolTip", new String[] {
                                    Fru.getStatusAsString(
                                        Fru.TYPE_SWITCH, 
                                        switches[1].getStatus()), switch2ver}));             
                try {
                    cellProps = adminApi.getCellProps(theCell.getID());
                } catch (Exception e) {
                    cellProps.spIP = CELL_PROPS_UNAVAILABLE;
                }
                if (sp != null) {
                    spImage = new LblServiceNodeImage(this.sp, 
                        GuiResources.getGuiString(
                            "cell.silo.sp.toolTip", new String[] {
                                Fru.getStatusAsString(
                                    Fru.TYPE_SP, sp.getStatus()), 
                                        cellProps.spIP, spBiosVer, spSmdcVer}));
                } else {
                    spImage = new LblServiceNodeImage(true, 
                        GuiResources.getGuiString("cell.silo.sp.err.toolTip"));
                }
            }     
            siloSwitch1Panel.add(switch1Image);
            siloSwitch2Panel.add(switch2Image);
            siloSPPanel.add(spImage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
 
    /**
     * Method populates the components used to construct the dynamic image
     */
    private void buildDynamicCell() {
        try {
            // Since the nodePanels are instantiated as the node Id's are popped
            // off of the node id stack AND the lowest node id is at the bottom
            // of the stack, the highest node id will correspond to the first
            // index of the node panel stack (e.g. [0] = 116...[15] = 101). The 
            // table lists the nodes in order of lowest to highest so the first
            // index needs to be lowest node id (e.g. [0] = 101...[15] = 116).
            // The tmpStack is used to reverse the order of newly created node
            // panels so the panels in the stack are highest at the bottom and
            // lowest at the top (e.g. [0] = 116...[15] = 101) so when the node
            // panels are popped off the stack, the first value is the lowest
            // like the table's.
            Stack tmpNodePnlStack = new Stack();


            // Pop off the nodeId values from the stack since this determines
            // the order of the nodes in the cell image -- use the value from
            // the stack as the key to get the correct vector of disk images
            // in the hashmap
            for (int mapIdx = 0; mapIdx < nodeDisks.size(); mapIdx++) {

                // The button needs to be a parent of this node panel since the
                // button can listen for actions and perform the operation for
                // the correct node.  The button is invisible though because its
                // main function is to handle action events.
                final JButton btnInvisible = new JButton();
                btnInvisible.setMargin(new Insets(0, 0, 0, 0));
                btnInvisible.setBorder(null);

                // Panel containing all of the disks for a single node
                final PnlNode nodePanel = new PnlNode();

                LblDiskImage d = null;
                Integer id =  (Integer)nodeIdStack.pop();
                Vector disks = (Vector)nodeDisks.get(id);
                if (disks != null && disks.size() > 0) {
                    Iterator iter = disks.iterator();
                    while (iter.hasNext()) {
                        d = (LblDiskImage)iter.next();
                        nodePanel.add(d);
                    } // done with disks

                    // just need one disk to get node and determine if its alive
                    if (!((Disk)d.getObject()).getNode().isAlive()) {
                        nodePanel.setIsNodeAlive(false);
                    }
                } // done with node
                tmpNodePnlStack.add(nodePanel);
                btnInvisible.add(nodePanel);
                pnlAllCellDisks.add(btnInvisible);

            } // done with all nodes in cell

            // Fix the order of the panels such that when a row is 
            // selected in the Node Summary table, the correct node is selected
            // in the cell image.
            int length = tmpNodePnlStack.size();
            for (int idx = 0; idx < length; idx++) {
                panelStack.add(idx, tmpNodePnlStack.pop());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    // builds up a static cell image
    private void buildStaticCell() {
        try {
            for (int mapIdx = 0; mapIdx < nodeDisks.size(); mapIdx++) {
                // The button must be a parent of this node panel because the
                // button can listen for actions and perform the operation for
                // the correct node.  The button is invisible though since its
                // main function is to handle action events.
                final JButton btnInvisible = new JButton();
                btnInvisible.setMargin(new Insets(0, 0, 0, 0));
                btnInvisible.setBorder(null);
                
                // Panel containing all of the disks for a single node
                PnlNode nodePanel = new PnlNode(true);
                
                LblDiskImage d = null;
                Integer id =  (Integer)nodeIdStack.pop();
                Vector disks = (Vector)nodeDisks.get(id);
                if (disks != null && disks.size() > 0) {
                    Iterator iter = disks.iterator();
                    while (iter.hasNext()) {
                        d = (LblDiskImage)iter.next();
                        nodePanel.add(d);
                    } // done with disks
                } // done with node
                btnInvisible.add(nodePanel);
                pnlAllCellDisks.add(btnInvisible);
                panelStack.add(mapIdx, nodePanel);
            } // done with all nodes in cell
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Method sizes the JPanel according to the specified dimensions, adds it
     * to an invisible JButton.  The panel is added to the button so the button
     * can handle mouse click events on the panel.
     *
     * @return JButton object containing the specified panel sized according
     *         to the specified dimensions.
     */
    private JButton makeComponentPnlOnButton(JPanel panel, Dimension size) {
        panel.setPreferredSize(size);
        panel.setMaximumSize(size);
        panel.setMinimumSize(size);
        // The button needs to be a parent of each panel added to the 
        // vCellBox since the button listens for actions and performs the
        // operation for the correct component.  The button is invisible 
        // though because its main function is to handle mouse click events.
        final JButton btnInvisible = new JButton();
        btnInvisible.setMargin(new Insets(0, 0, 0, 0));
        btnInvisible.setBorder(null);
        btnInvisible.add(panel);
        return btnInvisible;
    }
    /**
     * Method calculates the size of the constructed cell image based on
     * the height and width of each icon, dependent upon type.
     *
     * @return A Dimension object which holds the height and width of the
     *         specified image.
     */
    private Dimension getImageSize(int fruType) {
        int height = 0;
        switch(fruType) {
            case Fru.TYPE_DISK:
                height = numNodes * LblDiskImage.DISK_IMG.getIconHeight();
                break;
            case Fru.TYPE_SWITCH:
                height = LblSwitchImage.SWITCH_IMG.getIconHeight();
                break;
            case Fru.TYPE_SP:
                height = LblServiceNodeImage.SP_IMG.getIconHeight();
                break;
            default:
                height = LblServiceNodeImage.SP_IMG.getIconHeight();
        }
        int width = 
                AdminApi.DISKS_PER_NODE * LblDiskImage.DISK_IMG.getIconWidth();
        return new Dimension(width, height);
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        pnlCellImage = new javax.swing.JPanel();

        pnlCellImage.setLayout(new java.awt.GridLayout(1, 0));

        pnlCellImage.setDoubleBuffered(false);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(pnlCellImage, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 218, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlCellImage, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 121, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

   
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel pnlCellImage;
    // End of variables declaration//GEN-END:variables
    
}
