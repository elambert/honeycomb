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
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Disk;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.client.ServiceNode;
import com.sun.honeycomb.admingui.client.Switch;
import com.sun.honeycomb.admingui.client.Versions;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemSilo;
import com.sun.honeycomb.common.CliConstants;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.utility.AsyncProxy;
import com.sun.nws.mozart.ui.utility.CapacityValue;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.ToolBox;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.Iterator;
import java.util.Vector;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

/**
 *  This class is responsible for the panel shown on the home page.
 *
 */
public class PnlSilo extends JPanel implements ContentPanel {

    // Conversion from seconds to milliseconds
    private static final int SEC_TO_MILLISEC = 1000;

    private ExplorerItem explItem = null;
    private ServiceNode sp = null;
    private boolean initView = false;
    private long rollupSiloCapacity = 0;
    private long rollupSiloUsedCapacity = 0;
    private long rollupEstFreeSpace = 0;
    // number of cells in the silo
    private int numCells = 0;
    // online node and disk counts for silo
    private int nodeCount = 0;
    private int diskCount = 0;
    

    private static final Logger LOGGER = 
        Logger.getLogger(PnlSilo.class.getName());
    
    public PnlSilo() throws UIException, ClientException, ServerException {
        initComponents();
        initComponents2();
    }
     
    private void initComponents2()
                throws UIException, ClientException, ServerException {
        setBorder(BorderFactory.createLineBorder(new Color(153, 153, 153)));
    }
    
    // ******************************
    // ContentPanel Impl
    
    public String getTitle() {
        return GuiResources.getGuiString("silo.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public ButtonPanel getButtonPanel() {
        return BtnPnlBlank.getButtonPanel();
    }
    
    public int getAnchor() { return SizeToFitLayout.ANCHOR_NORTHWEST; }
    
    public int getFillType() { return SizeToFitLayout.FILL_BOTH; }
    
    AdminApi adminApi = ObjectFactory.getHostConnection();
    
    // Task which primes the cache by calling all necessary AdminApi methods.
    public class DataLoader implements Runnable {
        boolean err = false;
        public void run() {
            Cell[] cells = null;
            try {
                cells = adminApi.getCells();
             } catch (Exception e) {
                err = true;
                Log.logToStatusAreaAndExternal(Level.SEVERE,
                        GuiResources.getGuiString("silo.cell.error"), e);
            }
            if (!err) {
                for (int i = 0; i < cells.length; i++) {
                    Cell c = cells[i];
                    if (c.isAlive()) {
                        try {
                            // we try to retrieve the SP info since if the SP
                            // is down then this will take awhile -- store
                            // the ServiceNode object in the class to avoid
                            // retrieving it 2x
                            long sTime = System.currentTimeMillis();
                            sp = adminApi.getSp(c);
                            long fTime = System.currentTimeMillis();
                            if ((fTime - sTime) > CliConstants.ONE_MINUTE) {
                                Log.logToStatusAreaAndExternal(Level.WARNING, 
                                    GuiResources.getGuiString(
                                            "sp.refresh.warning", 
                                    Integer.toString(c.getID())), null); 
                            }
                        } catch (Exception e) {
                            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                    GuiResources.getGuiString(
                                            "sp.refresh.error", 
                                    Integer.toString(c.getID())), null);                        
                        }
                        try {
                            adminApi.getNodes(c);
                        } catch (Exception e) {
                            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                    GuiResources.getGuiString(
                                            "nodes.refresh.error", 
                                    Integer.toString(c.getID())), null);
                        }
                        try {
                            adminApi.getFwVersions(c);
                        } catch (Exception e) {
                             Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                    GuiResources.getGuiString(
                                            "version.refresh.error", 
                                    Integer.toString(c.getID())), null);                       
                        }
                        try {
                            adminApi.getCellProps(c.getID());
                        } catch (Exception e) {
                            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                    GuiResources.getGuiString(
                                            "cellProps.refresh.error", 
                                    Integer.toString(c.getID())), null);                        
                        }
                        try {
                            adminApi.getHADBStatus(c);
                        } catch (Exception e) {
                            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                    GuiResources.getGuiString(
                                            "hadbStatus.refresh.error", 
                                    Integer.toString(c.getID())), null);                        
                        }
                        try {
                            adminApi.getSwitches(c);
                        } catch (Exception e) {
                            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                    GuiResources.getGuiString(
                                            "switches.refresh.error", 
                                    Integer.toString(c.getID())), null);                        
                        }
                        // Do not remove -- may add unhealed failures/recovery in
                        // post 1.1 releases
    //                    adminApi.getNumOfUnhealedFailures(c.getID());
    //                    if (adminApi.hasQuorum(c.getID()))
    //                        adminApi.getRecoveryCompletionPercent(c);

                    } 
                } // end of for loop
            }
           
        }
        public void runReturn() {
            if (!ObjectFactory.isGetCellsError()) {
                // turn auto-refresh back on since cell information is cached
                // and if time-outs occur retrieving cell info then GUI 
                // won't be affected
                ((ExplItemSilo)explItem).
                                setTimerInterval(MainFrame.getRefreshRate());
                // data has been gathered and now it is time to update the 
                // initial cell image(s) with the information to render a
                // dynamic cell image
                initView = false;
            }
            buildGui();
        }
    }
    
    public void loadValues() throws UIException, HostException { 
        // initial view of home panel before data has been collected -- set
        // initView to true to indicate the image data is static
        initView = true;
 
        // shows progress bar so user knows data is being collected
        showRetrievalProgress();
        
        // builds the cell image
        buildGui();
        Log.logInfoMessage(GuiResources.getGuiString("silo.refresh.message"));
        // set auto-refresh off until getCells call returns 
        ((ExplItemSilo)explItem).setTimerInterval(0);
        
        if (ObjectFactory.isGetCellsError()) {
            // return if there was an error getting cell information -- do not
            // initiate remote call on the asynchronous thread in DataLoader
            return;
        }
        AsyncProxy.run(new DataLoader());
    }
    
    /**
     * Method which populates a vector with each cell's attributes which are
     * displayed above the cell image.  If the image is static and data has
     * not yet been collected, then the values are empty.
     * @param c The cell for which the attributes are shown
     * @param collectInfo Flag indicating whether or not the cell image is
     *                    static (false) or dynamic (true).
     * @return Vector containing the cell attributes for display
     * @throws UIException, ClientException, ServerException
     *
     */
    private Vector getCellAttributes(Cell c, boolean collectInfo) 
            throws UIException, ClientException, ServerException {
        // add labels (2 per row) above cell image
        // Create a vector of label strings in sequential order
        Vector attributes = new Vector();
        if (!collectInfo) {
            // initializing cell info
            attributes.add(GuiResources.getGuiString("cell.version"));
            attributes.add("");
            attributes.add(GuiResources.getGuiString("silo.adminIp"));
            attributes.add("");
            attributes.add(GuiResources.getGuiString("silo.dataIp"));
            attributes.add("");
            attributes.add(GuiResources
                                .getGuiString("silo.queryStatus"));
            attributes.add("");
        } else {
            // try and retrieve cell info
            String cellVersion = getCellVersion(c);
            CellProps cellProps = getCellProperties(c);
            String hadbStatus = getCellHADBStatus(c);
            
            // populate cell info
            attributes.add(GuiResources.getGuiString("cell.version"));
            attributes.add(cellVersion);
            attributes.add(GuiResources.getGuiString("silo.adminIp"));
            attributes.add(cellProps.adminIP);
            attributes.add(GuiResources.getGuiString("silo.dataIp"));
            attributes.add(cellProps.dataIP);
            attributes.add(GuiResources
                                .getGuiString("silo.queryStatus"));
            attributes.add(hadbStatus);
        }
        return attributes;
    }
    
    /**
     * Method which builds up the cell image.  The image can be static or
     * dynamic depending on what flags are passed in.  If the image is dynamic,
     * then isImageDynamic is true, otherwise false.  The isDataCollected flag
     * is needed because although an image being built might be dynamic, the
     * state of the cell may be such that the data is not available (i.e. node
     * and/or cell is down).  Thus, if isDataCollected is true, then data should
     * be available for the cell.  If isDataCollected is false, then there is 
     * a problem collecting the cell data.
     * @param cell The cell whose image is being built
     * @param isImageDynamic Flag indicating whether or not the cell image is
     *                       static (false) or dynamic (true).
     * @param isDataCollected Flag indicating whether or not data has been
     *                        collected for the cell.
     * @return Box containing all of the components that provide information
     *         about the cell (i.e. cell image, attributes, id...)
     * @throws UIException, ClientException, ServerException
     *
     */
    private Box buildCell(Cell cell, boolean isImageDynamic, boolean isDataCollected) 
                throws UIException, ClientException, ServerException {
        
        PnlDynamicCell pnlCellImage = null;
        boolean isAlive = true;
        String cellId = "";
        
        if (isDataCollected) {
            isAlive = cell.isAlive();
            cellId = Integer.toString(cell.getID());
        } 
        
        // This box contains the labels and the cell image
        Box cBox = Box.createVerticalBox();

        // Preferred dimensions so that only two images show up
        // side by side -- no need to scroll vertically or
        // horizontally to view data.
        cBox.setPreferredSize(new Dimension(275, 440));

        // Border around the cellBox
        // cell border without version
        cBox.setBorder(BorderFactory.createTitledBorder(
                        GuiResources.getGuiString("silo.cellSummary", cellId)));

        // Add a strut/fixed space above the labels (aesthetics)
        cBox.add(Box.createVerticalStrut(2));
        
        // check to see that image is dynamic and cell is alive -- 
        if (isImageDynamic && isAlive) {
            Node[] nodes = null;
            Switch[] switches = null;
            try {
                nodes = adminApi.getNodes(cell);
                switches = adminApi.getSwitches(cell);
            } catch (Exception e) {
                // problem while trying to get information about the cell 
                // (usually node error) -- set the isDataCollected value to
                // true since the cell is alive and some data is there but need
                // to set isImageDynamic to false because all of the data can
                // not shown in the dynamic image
                return buildCell(cell, false, true);
            }
            loadCellAttributes(cell, cBox, 
                                getCellAttributes(cell, isDataCollected), 2);
            pnlCellImage = new PnlDynamicCell(cell, nodes, switches, sp);
            
            // Adds the total capacity and used capacity values for the
            // cell to the overall rollupSiloCapacity and
            // rollupSiloUsedCapacity values.  This method also keeps
            // track of the online nodes and disks for the silo.
            calcCellCapacity(cell, nodes);
            
            // keep track of the estimated free space
            rollupEstFreeSpace += cell.getEstFreeSpace();
        } else {
            loadCellAttributes(null, cBox, 
                                getCellAttributes(cell, isDataCollected), 2);
            /**
             * End up here if:
             *     a) an error occurred while trying to build a dynamic
             *        cell image and still want to show the cell attribute data 
             *     b) if the cell is dead
             *     c) cell image is purely static and cell attribute information
             *        should be shown as empty
             */
            String ttTxt = "";
            int numOfNodes = AdminApi.FULL_CELL_NODES;
            
            // node error if static image but some data has been collected
            boolean nodeError = !isImageDynamic && isDataCollected;
            if (nodeError) {
                ttTxt = GuiResources.getGuiString("node.error.toolTip");
            } else if (!isAlive) {
                // cell is dead
                ttTxt = GuiResources.getGuiString("cell.error.toolTip");
                numOfNodes = AdminApi.FULL_CELL_NODES;
            } else if (cell != null) {
                numOfNodes = cell.getNumNodes();
            }
            pnlCellImage = 
                new PnlDynamicCell(nodeError || !isAlive, ttTxt, numOfNodes);
        }        
        // Add a strut/fixed space below the labels (aesthetics)
        cBox.add(Box.createVerticalStrut(5));
        cBox.add(pnlCellImage);
        cBox.add(Box.createVerticalGlue());
        return cBox;
    }
   /**
     * Method showing the cell information could not be retrieved.
     */    
    private void showGetCellsError() {
        JLabel lblError = new JLabel();
        pnlStatus.removeAll();
        pnlStatus.setLayout(new GridLayout(1, 1));

        lblError.setText(GuiResources.getGuiString(
                                        "silo.cell.error"));
        lblError.setFont(new java.awt.Font("Dialog", 1, 12));
        lblError.setForeground(Color.RED);
        JPanel pnlError = new JPanel();
        pnlError.setLayout(
                new BoxLayout(pnlError, BoxLayout.PAGE_AXIS));
        pnlError.add(Box.createVerticalStrut(2));
        pnlError.add(lblError);
        pnlError.add(Box.createVerticalStrut(2));

        pnlStatus.add(pnlError);
        this.pnlStatus.validate();
        this.pnlStatus.repaint();
    }
    /**
     * Method to show the progress bar as data is being collected.
     */    
    private void showRetrievalProgress() {
        JLabel lblRetrieving = new JLabel();
        JProgressBar prgBarDataRetrieval = new JProgressBar();
        pnlStatus.removeAll();
        pnlStatus.setLayout(new GridLayout(1, 2));

        // make the data retrieval progress bar look like the 
        // percent capacity used progress bar
        Dimension dBar = new Dimension((int)(prgPercentUsed.getWidth()), 
                                    (int)(prgPercentUsed.getHeight()));
        prgBarDataRetrieval.setPreferredSize(dBar);
        prgBarDataRetrieval.setMaximumSize(dBar);
        prgBarDataRetrieval.setMinimumSize(dBar);
        prgBarDataRetrieval.setIndeterminate(true);
        prgBarDataRetrieval.setAlignmentX(SwingConstants.RIGHT);

        lblRetrieving.setText(GuiResources.getGuiString(
                                        "silo.refresh.panel.message"));
        lblRetrieving.setFont(new java.awt.Font("Dialog", 1, 12));

        JPanel pnlProgressBar = new JPanel();
        pnlProgressBar.setLayout(
                new BoxLayout(pnlProgressBar, BoxLayout.PAGE_AXIS));
        pnlProgressBar.add(Box.createVerticalStrut(2));
        pnlProgressBar.add(prgBarDataRetrieval);
        pnlProgressBar.add(Box.createVerticalStrut(2));

        pnlStatus.add(lblRetrieving);
        pnlStatus.add(pnlProgressBar);
        this.pnlStatus.validate();
        this.pnlStatus.repaint();
    }
    /**
     * Method which builds up the entire GUI page.  This includes one or more
     * cells, cell attributes and hive attributes.
     *
     */    
    private void buildGui() {
        try {
            // reset values for page
            rollupSiloCapacity = 0;
            rollupSiloUsedCapacity = 0;
            rollupEstFreeSpace = 0;
            numCells = 0;
            nodeCount = 0;
            diskCount = 0;
            lblTotalCapacityValue.setText("");
            prgPercentUsed.setValue(0);
            lblEstFreeSpaceValue.setText("");
            Cell[] cells = null;
            
            
            if (ObjectFactory.isGetCellsError()) {
                showGetCellsError();
                numCells = adminApi.getNumOfCells();
            } else if (initView) {
                showRetrievalProgress();
                numCells = adminApi.getNumOfCells();
            } else {
                cells = adminApi.getCells();
                if (cells == null) {
                    Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                        "info.noCells"));
                    return;
                }
                // Number of cells in the silo
                numCells = cells.length;
            }           

            // Clear panels containing the cell images and their attributes
            pnlMiddle.removeAll();
            
            // Overall vertical box which resides in the
            // JScrollPane, scrlPaneTables
            Box verticalBox = Box.createVerticalBox();
                        
            // Add the large vertical box to the scroll pane so it aligns the
            // rows of 2 inner boxes.  Each inner box contains the cell image
            // and attribute info on a per cell basis.
            scrlPaneCells.setViewportView(verticalBox);
            
            // Add the scroll pane to the panel
            pnlMiddle.add(scrlPaneCells);
            
            // This is the outer panel whose layout enables the inner row
            // panel (pnlRow) to be centered.
            JPanel pnlRowBorder = new JPanel(new BorderLayout(5, 5));
            
            // Create the first row panel which will contain two cells
            JPanel pnlRow = new JPanel(new GridLayout(1, 2, 10, 0));

            for (int i = 0; i < numCells; i++) {
                Box cellBox = null;
                Cell cell = cells != null ? cells[i] : null;
                if (initView) {
                    cellBox = buildCell(cell, false, false);
                } else {
                    cellBox = buildCell(cell, true, true);
                }
                pnlRow.add(cellBox);

                // If there is an even number of cells, then add the two
                // cells in the row to the panel and add the panel to the
                // large vertical box residing inside of the scroll pane
                if (0 == (i + 1) % 2) {
                    verticalBox.add(pnlRowBorder);
                    pnlRowBorder.add(Box.createHorizontalBox(),
                                            BorderLayout.PAGE_START);
                    pnlRowBorder.add(Box.createVerticalBox(), 
                                            BorderLayout.LINE_START);
                    pnlRowBorder.add(pnlRow, BorderLayout.CENTER);
                    pnlRowBorder.add(Box.createVerticalBox(), 
                                            BorderLayout.LINE_END);
                    pnlRowBorder = new JPanel(new BorderLayout(5, 5));
                    pnlRow =
                            new JPanel(new GridLayout(1, 2, 10, 0));
                }

                // If there is an odd number of cells or just ONE cell,
                // then the cell should appear centered in the row
                if (1 == numCells ||
                        (i == numCells - 1 && numCells % 2 != 0)) {
                    // Use a horizontal box and add struts to it with each
                    // strut a quarter of the box's length
                    Box horizontalBox = Box.createHorizontalBox();
                    int prefWidth = (int)cellBox.getPreferredSize().
                            getWidth();

                    // Add horizontal strut (0.25 of scroll pane width)
                    horizontalBox.
                            add(Box.createHorizontalStrut(prefWidth/4));
                    // Add cellBox (0.50 of scroll pane width)
                    horizontalBox.add(cellBox);
                    // Add horizontal strut (0.25 of scroll pane width)
                    horizontalBox.
                            add(Box.createHorizontalStrut(prefWidth/4));
                    pnlRow.add(horizontalBox);
                    verticalBox.add(pnlRowBorder);

                    pnlRowBorder.add(Box.createHorizontalBox(),
                                            BorderLayout.PAGE_START);
                    pnlRowBorder.add(Box.createHorizontalStrut(prefWidth/2),
                                            BorderLayout.LINE_START);
                    pnlRowBorder.add(pnlRow, BorderLayout.CENTER);
                    pnlRowBorder.add(Box.createHorizontalStrut(prefWidth/2),
                                            BorderLayout.LINE_END);

                }

            } // For each cell

            if (!initView) {
                // Set silo capacity numbers
                CapacityValue capVal = new CapacityValue(
                        rollupSiloCapacity,
                        ObjectFactory.CAPACITY_UNIT);
                lblTotalCapacityValue.setText(
                        capVal.getDisplayBestUnitsDecimal(2));
                prgPercentUsed.setValue(ToolBox.calcPcntUsedCapacity(
                        rollupSiloUsedCapacity,
                        rollupSiloCapacity));

                // Show the number of online nodes and disks for the silo
                lblSystemStats.setText(GuiResources.getGuiString(
                                            "silo.stats", new String[] {
                                                String.valueOf(numCells), 
                                                String.valueOf(nodeCount), 
                                                String.valueOf(diskCount)}));
                lblSystemStats.setFont(new java.awt.Font("Dialog", 1, 12));
                pnlStatus.removeAll();
                pnlStatus.setLayout(new GridLayout(1, 1));
                pnlStatus.add(lblSystemStats);
                this.pnlStatus.validate();
                this.pnlStatus.repaint();
                
                // set silo estimated free space
                CapacityValue free = new CapacityValue(rollupEstFreeSpace,
                                                ObjectFactory.CAPACITY_UNIT);
                lblEstFreeSpaceValue.setText(
                                            free.getDisplayBestUnitsDecimal(2));
            }

            this.scrlPaneCells.validate();
            
        } catch (Exception e) {
            AsyncProxy.handleException(e);
        }
    }
    
    private String getCellVersion(Cell c) {
        String cellVersion = GuiResources.getGuiString("cellProps.unavailable");
        if (!c.isAlive()) {
            return cellVersion;
        }
        try {
            Versions vObj = adminApi.getFwVersions(c);
            cellVersion = vObj.getCellVer();
            int vStart = cellVersion.indexOf('[');
            int vEnd = cellVersion.indexOf(']');
            // syntax may change so check to see if brackets were found
            if (vStart != -1 && vEnd != -1) {
                cellVersion = cellVersion.substring(++vStart, vEnd);
            }
        } catch (Exception e) {
            // do nothing
        }
        return cellVersion;
    }
    
    private CellProps getCellProperties(Cell c) {
        CellProps cp = new CellProps();
        cp.adminIP = GuiResources.getGuiString("cellProps.unavailable");
        cp.dataIP = GuiResources.getGuiString("cellProps.unavailable");
        cp.spIP = GuiResources.getGuiString("cellProps.unavailable");
        if (!c.isAlive()) {
            return cp;
        }
        
        try {
            cp = adminApi.getCellProps(c.getID());
        } catch (Exception ex) {
            // do nothing
        }
        return cp;
    }
    
    private String getCellHADBStatus(Cell c) {
        String status = GuiResources.getGuiString("cellProps.unavailable");
        if (!c.isAlive()) {
            return status;
        }
        try {
            status = adminApi.getHADBStatus(c);
        } catch (Exception exc) {
            // do nothing
        }
        return status;
    }   
    
    public void saveValues() {
        
    }

    public String getPageKey() {
        return HelpFileMapping.SYSTEMSTATUS;
    }
    
    
    //
    // *************************************************

    /**
     * Method computes the total capacity and used capacity for
     * the cell specified and adds these values to the rollupSiloCapacity 
     * and rollupSiloUsedCapacity values.  
     *
     * @param cell The cell that the capacity is calculated from 
     * @param nodes The nodes that make up the cell
     */
    private void calcCellCapacity(Cell cell, Node[] nodes) {

        // Set the number of nodes for this cell
        int numNodes = nodes.length;
        
        if (nodes == null) {
            // if there are no nodes, then cell can't have quorum...irrelevant
            Log.logAndDisplayInfoMessage(
                    GuiResources.getMsgString(
                        "info.noNodesOnCell", Integer.toString(cell.getID())));
            return;
        }
            
        for (int j = 0; j < numNodes; j++) {
            Node theNode = nodes[j];

            Integer pcntUsedCapacity = null;

            // If the node is not alive, then skip disk stuff
            if (theNode.isAlive()) {
                this.nodeCount++;
                // Calculate capacity percent used rollup for node disks
                Disk[] disks = theNode.getDisks();
                if (disks == null) {
                    Log.logAndDisplayInfoMessage(
                            GuiResources.getMsgString("info.noDisksOnNode", 
                            theNode.getID()));
                    continue;
                }
                float totalCapacity = 0;
                float usedCapacity = 0;
                for (int k = 0; k < disks.length; k++) {
                    Disk theDisk = disks[k];
                    totalCapacity += theDisk.getCapTotal();
                    usedCapacity += theDisk.getCapUsed();
                    if (theDisk.getStatus() != Disk.OFFLINE &&
                            theDisk.getStatus() != Disk.DISABLED) {
                        this.diskCount++;
                    } 
                }
                rollupSiloCapacity += totalCapacity;
                rollupSiloUsedCapacity += usedCapacity;
            }
        } // done iterating through nodes

    }
    /**
     * Method which takes the cell attribute information and populates it
     * within the box containing all of the cell data. 
     * @param cell The cell whose image is being built
     * @param box Container for the cell data
     * @param attributes Cell attributes
     * @param labelsPerRow Number of attributes shown in each row within the box
     * @throws ClientException, ServerException
     *
     */
    private void loadCellAttributes(Cell cell, Box box,
                                        Vector attributes, int labelsPerRow) 
                                   throws ServerException, ClientException {
        // Set width of panel based on the width of the box 
        // its being added to
        int labelBoxWidth = (int)box.getPreferredSize().getWidth();
        
        // Iterate through and add the labels to the panel and then add 
        // each panel to the box containing all of the cell information
        for (Iterator iter = attributes.iterator(); iter.hasNext(); ) {
            JPanel pnlOuter =
                new JPanel(new GridLayout(1, labelsPerRow, 1, 0));
            pnlOuter.setPreferredSize(new Dimension(2*labelBoxWidth, 20));
            pnlOuter.setMaximumSize(new Dimension(2*labelBoxWidth, 20));
            
            // Left-hand label
            JPanel pnlLabels = new JPanel();
            pnlLabels.setLayout(new BoxLayout(pnlLabels, BoxLayout.LINE_AXIS));
            pnlLabels.setMaximumSize(new Dimension(labelBoxWidth/4, 20));
            JLabel leftLabel = new JLabel((String)iter.next(), JLabel.LEFT);
            
            // Add spacers to the left-side's labels in order to center 
            // both the left and right labels over the cell image
            pnlLabels.add(Box.createHorizontalStrut(
                                (int)pnlLabels.getMaximumSize().getWidth()/4));
            pnlLabels.add(leftLabel);
            pnlLabels.add(Box.createHorizontalGlue());
            pnlOuter.add(pnlLabels);
           
            // Right-hand label
            pnlLabels = new JPanel();
            pnlLabels.setLayout(new BoxLayout(pnlLabels, BoxLayout.LINE_AXIS));
            pnlLabels.setMaximumSize(new Dimension(labelBoxWidth/4, 20));
            JLabel rightLabel = new JLabel((String)iter.next(), JLabel.LEFT);
            
            // Add spacer before the right-side's labels in order to center 
            // both the left and right labels over the cell image
            pnlLabels.add(Box.createHorizontalStrut(
                                (int)pnlLabels.getMaximumSize().getWidth()/4));
            pnlLabels.add(rightLabel);
           
            // Do not remove -- may add unhealed failures/recovery in
            // post 1.1 releases
//            if (cell != null && cell.isAlive()) {
//              // If the number of unhealed failures is greater than zero, 
//              // then a progress bar with the percentage of healing recovery
//              // should be shown UNLESS the cell does not meet quorum criteria
//              // (less than 75% + 1 disks online).  If no quorum, then the 
//              // progress bar is NOT shown and "No Quorum" text takes its place.
//              if (leftLabel.getText().equals(GuiResources.
//                            getGuiString("silo.unhealedFailures")) &&
//                        Integer.parseInt(rightLabel.getText()) > 0) {
//                  if (adminApi.hasQuorum(cell.getID())) {
//                      JProgressBar healing = new JProgressBar();
//                      healing.setPreferredSize(
//                                new Dimension((labelBoxWidth/3), 18));
//                      int percentHealed = 
//                        adminApi.getRecoveryCompletionPercent(cell);
//                      healing.setStringPainted(true);
//                      healing.setString(GuiResources.
//                                            getGuiString("silo.percent.healed", 
//                                            Integer.toString(percentHealed)));
//                      healing.setValue(percentHealed);
//                      // Put spacing between # of unhealed failures & progress bar
//                      pnlLabels.add(Box.createHorizontalStrut(10));
//                      pnlLabels.add(healing);
//                  } else {
//                      JLabel lblQuorum = new JLabel(GuiResources.
//                            getGuiString("silo.noQuorum"), JLabel.LEFT); 
//                      // Put spacing between # of unhealed failures & text
//                      pnlLabels.add(Box.createHorizontalStrut(15));
//                      pnlLabels.add(lblQuorum);
//                  }
//              }
//          }
            
            pnlLabels.add(Box.createHorizontalGlue());
            pnlOuter.add(pnlLabels);
            
            // Add the row of labels to the cell box
            box.add(pnlOuter);

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
        pnlProps = new javax.swing.JPanel();
        pnlPropsCapacity = new javax.swing.JPanel();
        lblTotalCapacity = new javax.swing.JLabel();
        lblTotalCapacityValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        lblPercentUsed = new javax.swing.JLabel();
        prgPercentUsed = new javax.swing.JProgressBar();
        pnlMiddle = new javax.swing.JPanel();
        scrlPaneCells = new javax.swing.JScrollPane();
        pnlStatsAndFreeSpace = new javax.swing.JPanel();
        pnlStatus = new javax.swing.JPanel();
        lblSystemStats = new javax.swing.JLabel();
        lblEstFreeSpaceValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        lblEstFreeSpace = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));
        setAlignmentX(0.25F);
        setAlignmentY(0.25F);
        pnlProps.setLayout(new java.awt.BorderLayout(2, 5));

        pnlPropsCapacity.setMinimumSize(new java.awt.Dimension(324, 37));
        pnlPropsCapacity.setPreferredSize(new java.awt.Dimension(324, 50));
        lblTotalCapacity.setText(GuiResources.getGuiString("silo.totalCapacity"));

        lblTotalCapacityValue.setMaximumSize(new java.awt.Dimension(67, 14));

        lblPercentUsed.setText(GuiResources.getGuiString("silo.percentUsed"));

        prgPercentUsed.setMaximumSize(new java.awt.Dimension(25, 18));
        prgPercentUsed.setMinimumSize(new java.awt.Dimension(25, 18));
        prgPercentUsed.setPreferredSize(new java.awt.Dimension(80, 18));
        prgPercentUsed.setStringPainted(true);

        org.jdesktop.layout.GroupLayout pnlPropsCapacityLayout = new org.jdesktop.layout.GroupLayout(pnlPropsCapacity);
        pnlPropsCapacity.setLayout(pnlPropsCapacityLayout);
        pnlPropsCapacityLayout.setHorizontalGroup(
            pnlPropsCapacityLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlPropsCapacityLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlPropsCapacityLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(pnlPropsCapacityLayout.createSequentialGroup()
                        .add(lblTotalCapacity)
                        .add(28, 28, 28)
                        .add(lblTotalCapacityValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 142, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(pnlPropsCapacityLayout.createSequentialGroup()
                        .add(lblPercentUsed)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(prgPercentUsed, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 100, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(78, Short.MAX_VALUE))
        );
        pnlPropsCapacityLayout.setVerticalGroup(
            pnlPropsCapacityLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlPropsCapacityLayout.createSequentialGroup()
                .add(pnlPropsCapacityLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblTotalCapacity, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(lblTotalCapacityValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlPropsCapacityLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(prgPercentUsed, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(lblPercentUsed))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlMiddle.setLayout(new java.awt.GridLayout(1, 0));

        pnlMiddle.setAlignmentX(0.25F);
        pnlMiddle.setAlignmentY(0.25F);
        pnlMiddle.setPreferredSize(new java.awt.Dimension(650, 400));
        scrlPaneCells.setBorder(null);
        scrlPaneCells.setAlignmentX(0.25F);
        scrlPaneCells.setAlignmentY(0.25F);
        pnlMiddle.add(scrlPaneCells);

        pnlMiddle.getAccessibleContext().setAccessibleParent(pnlMiddle);

        pnlStatus.setLayout(new java.awt.GridLayout(1, 0));

        lblSystemStats.setText(GuiResources.getGuiString("silo.refresh.panel.message"));
        lblSystemStats.setMaximumSize(new java.awt.Dimension(60, 10));
        lblSystemStats.setMinimumSize(new java.awt.Dimension(60, 10));
        lblSystemStats.setPreferredSize(new java.awt.Dimension(60, 10));
        pnlStatus.add(lblSystemStats);

        lblEstFreeSpaceValue.setMaximumSize(new java.awt.Dimension(67, 14));

        lblEstFreeSpace.setText(GuiResources.getGuiString("silo.estFree"));

        org.jdesktop.layout.GroupLayout pnlStatsAndFreeSpaceLayout = new org.jdesktop.layout.GroupLayout(pnlStatsAndFreeSpace);
        pnlStatsAndFreeSpace.setLayout(pnlStatsAndFreeSpaceLayout);
        pnlStatsAndFreeSpaceLayout.setHorizontalGroup(
            pnlStatsAndFreeSpaceLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlStatsAndFreeSpaceLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlStatsAndFreeSpaceLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, pnlStatus, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 333, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, pnlStatsAndFreeSpaceLayout.createSequentialGroup()
                        .add(lblEstFreeSpace)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(lblEstFreeSpaceValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 172, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        pnlStatsAndFreeSpaceLayout.setVerticalGroup(
            pnlStatsAndFreeSpaceLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlStatsAndFreeSpaceLayout.createSequentialGroup()
                .add(pnlStatus, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlStatsAndFreeSpaceLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblEstFreeSpace)
                    .add(lblEstFreeSpaceValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(741, 741, 741)
                .add(pnlProps, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(layout.createSequentialGroup()
                .add(pnlStatsAndFreeSpace, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 352, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlPropsCapacity, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 371, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(pnlMiddle, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 721, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(pnlPropsCapacity, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 49, Short.MAX_VALUE)
                    .add(pnlProps, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(pnlStatsAndFreeSpace, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 49, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlMiddle, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 471, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lblEstFreeSpace;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblEstFreeSpaceValue;
    private javax.swing.JLabel lblPercentUsed;
    private javax.swing.JLabel lblSystemStats;
    private javax.swing.JLabel lblTotalCapacity;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblTotalCapacityValue;
    private javax.swing.JPanel pnlMiddle;
    private javax.swing.JPanel pnlProps;
    private javax.swing.JPanel pnlPropsCapacity;
    private javax.swing.JPanel pnlStatsAndFreeSpace;
    private javax.swing.JPanel pnlStatus;
    private javax.swing.JProgressBar prgPercentUsed;
    private javax.swing.JScrollPane scrlPaneCells;
    // End of variables declaration//GEN-END:variables
    
}
