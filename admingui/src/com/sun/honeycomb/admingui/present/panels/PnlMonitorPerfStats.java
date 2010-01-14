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
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.OpnStats;
import com.sun.honeycomb.admingui.client.PerfStats;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemMonitorPerfStats;
import com.sun.nws.mozart.ui.BaseActionCollection;
import com.sun.nws.mozart.ui.BaseObjectFactory;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.ComboEntry;
import com.sun.nws.mozart.ui.swingextensions.EntryDisabledComboBox;
import com.sun.nws.mozart.ui.swingextensions.MultiLineTableHeaderRenderer;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.AsyncProxy;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.utility.ToolBox;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;
import java.util.logging.Level;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.table.TableColumn;

/**
 *
 * @author  ronaldso
 */
public class PnlMonitorPerfStats extends JPanel implements ContentPanel {

    private ExplorerItem explItem = null;
    private AdminApi hostConn = ObjectFactory.getHostConnection();

    // Vector contains strings where a single cellIdName is a concatenation 
    // of the string, "Cell" with the cellId number (e.g. Cell 1)
    private Vector cellIdNames = null;

    // Vector contains Integers where a single value contains the cellid integer
    private Vector cellIds = null;

    // Data structure to keep track of the last known OpnStats.  Use this
    // as a base to calculate "Change" value in JTable
    private static OpnStats [] previousCellStats = null;
    private static OpnStats [] previousNodeStats = null;
    private static double [] previousCPUCellStats = null;
    private static double [] previousCPUNodeStats = null;
    
    private JEditorPane emptyNodeTable = new JEditorPane();
    private JEditorPane emptyCellTable = new JEditorPane();
    
    
    private Node selectedNode;
    private Cell [] cells = null;
    private Node [] nodes = null;
    private int selectedInterval = 0;
    private int selectedNodeIndex = 0;
    private PerfStats perfStats = null;
    
    // initial view of this GUI panel
    private boolean initView = false;

    public static final int TABLE_TYPE_MAIN = 0;
    public static final int TABLE_TYPE_CPU  = 1;

    private static final String PLUS_SIGN = "+";
    private static final String EMPTY_STR = "";
    
    private static final int NODE_TABLE = 0;
    private static final int CELL_TABLE = 1;
    private static final int BOTH_TABLES = 2;

    // String array contains refresh time intervals
    private String [] timeIntervals =
        new String [] {
            "monitor.perfstat.timeinterval.off",
            "monitor.perfstat.timeinterval.15secs",
            "monitor.perfstat.timeinterval.30secs",
            "monitor.perfstat.timeinterval.1min",
            "monitor.perfstat.timeinterval.5min"
    };
    private int intervalSecs[] = { 0, 15, 30, 60, 300 },
            defaultInterval = 15;

    // private MonitorPerfStatsTable nodeTable;

    /** Creates new instance of PnlMonitorPerfStats */
    public PnlMonitorPerfStats() throws HostException {
        initComponents();
        initComponents2();
    }

    private void initComponents2() throws HostException{
        populateRefreshIntervals();
        initializeTables();
        setupListeners();

        // initialize node panel to invisible
        handleToggleButtonClicked();

        // Disable manual refresh button in toolbar
        try {
            BaseActionCollection col = BaseObjectFactory.getActionsCollection();
            // col.setEnableAction(RefreshAction.class, false);
        } catch (UIException uie) {
            Log.logAndDisplayException(uie);
        }
        
        // explicitly add text to labels due to mnemonics
        labelPerfStats.setText(GuiResources.getGuiString(
                "monitor.perfstat.label.performancestatistics"));
        labelRefresh.setText(GuiResources.getGuiString(
                "monitor.perfstat.label.refresh"));
        labelFor.setText(GuiResources
                .getGuiString("monitor.perfstat.label.for"));
        // add mnemonics to labels
        labelPerfStats.setDisplayedMnemonic(GuiResources.getGuiString(
                "monitor.perfstat.label.performancestatistics.mn").charAt(0));
        labelRefresh.setDisplayedMnemonic(GuiResources.getGuiString(
                "monitor.perfstat.label.refresh.mn").charAt(0));
        labelFor.setDisplayedMnemonic(GuiResources
                .getGuiString("monitor.perfstat.label.for.mn").charAt(0));
        
        emptyNodeTable.setEditable(false);
        emptyNodeTable.setContentType("text/html");
        emptyCellTable.setEditable(false);
        emptyCellTable.setContentType("text/html");
    }
    /**
     * Initialize the tables
     */
    private void initializeTables() {
        // Remove default grey border
        setBorder(null);

        ToolBox.setupJTable(cellTable, cellScrollPane,
                new MonitorPerfStatsTableModel(TABLE_TYPE_MAIN));
        ToolBox.setupJTable(cellCPUTable, cellCPUScrollPane, 
                new MonitorPerfStatsTableModel(TABLE_TYPE_CPU));
        ToolBox.setupJTable(nodeTable, nodeScrollPane, 
                new MonitorPerfStatsTableModel(TABLE_TYPE_MAIN));
        ToolBox.setupJTable(nodeCPUTable, nodeCPUScrollPane, 
                new MonitorPerfStatsTableModel(TABLE_TYPE_CPU));

        MultiLineTableHeaderRenderer renderer =
            new MultiLineTableHeaderRenderer();
        Enumeration e = cellTable.getColumnModel().getColumns();
        while (e.hasMoreElements()) {
            ((TableColumn) e.nextElement()).setHeaderRenderer(renderer);
        }
        e = cellCPUTable.getColumnModel().getColumns();
        while (e.hasMoreElements()) {
            ((TableColumn) e.nextElement()).setHeaderRenderer(renderer);
        }
        e = nodeTable.getColumnModel().getColumns();
        while (e.hasMoreElements()) {
            ((TableColumn) e.nextElement()).setHeaderRenderer(renderer);
        }
        e = nodeCPUTable.getColumnModel().getColumns();
        while (e.hasMoreElements()) {
            ((TableColumn) e.nextElement()).setHeaderRenderer(renderer);
        }

        cellTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        nodeTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        cellCPUTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        nodeCPUTable.getColumnModel().getColumn(0).setPreferredWidth(170);

        for (int i = 1; i < 7; i++) {
            cellTable.getColumnModel().getColumn(i).setCellRenderer(
                new MonitorPerfStatsColorRenderer());
            nodeTable.getColumnModel().getColumn(i).setCellRenderer(
                new MonitorPerfStatsColorRenderer());
            cellCPUTable.getColumnModel().getColumn(i).setCellRenderer(
                new MonitorPerfStatsColorRenderer());
            nodeCPUTable.getColumnModel().getColumn(i).setCellRenderer(
                new MonitorPerfStatsColorRenderer());

            cellTable.getColumnModel().getColumn(i).setPreferredWidth(100);
            nodeTable.getColumnModel().getColumn(i).setPreferredWidth(100);
            cellCPUTable.getColumnModel().getColumn(i).setPreferredWidth(100);
            nodeCPUTable.getColumnModel().getColumn(i).setPreferredWidth(100);
        }
    }

    private void setupListeners() throws HostException {
        // Add listener to handle the event when user chooses a different cell
        cellSelectionDropDown.addItemListener(
            new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        // Log.logInfoMessage("Cell Item Changed!");
                        try {
                            handleCellSelection();
                        } catch (HostException ex) {
                            Log.logAndDisplay(
                                Level.SEVERE, ex.getMessage(), null);
                        }
                    }
                }
            });

        nodeSelectionDropDown.addItemListener(
            new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        // Log.logInfoMessage("Node Item Changed!");
                        try {
                            handleNodeSelection();
                        } catch (HostException ex) {
                            Log.logAndDisplay(
                                Level.SEVERE, ex.getMessage(), null);
                        }
                    }
                }
            });

        // Add listener to handle the event when toggle button is clicked
        toggleButton.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        handleToggleButtonClicked();
                    } catch (HostException ex) {
                        Log.logAndDisplay(
                            Level.SEVERE, ex.getMessage(), null);
                    }
                }
            }
        );

        refreshIntervalDropDown.addItemListener(
            new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        // Log.logInfoMessage("Refresh Interval Changed!");
                        try {
                            handleIntervalSelection();
                        } catch (HostException ex) {
                            Log.logAndDisplay(
                                Level.SEVERE, ex.getMessage(), null);
                        }
                    }
                }
            });
    }
    
        // Task which primes the cache by calling all necessary AdminApi methods.
    public class DataLoader implements Runnable {
        boolean err = false;
        public void run() {
            try {
                cells = hostConn.getCells();
            } catch (Exception e) {
                err = true;
                Log.logToStatusAreaAndExternal(Level.SEVERE,
                        GuiResources.getGuiString("silo.cell.error"), e);
            }
        }
        public void runReturn() {
            if (err) {
                setTableText(GuiResources.getGuiString("silo.cell.error"), BOTH_TABLES);
            } else {
                initView = false;
                // turn auto-refresh back on since cell information is cached
                // and if time-outs occur retrieving cell info then GUI 
                // won't be affected
                ((ExplItemMonitorPerfStats)explItem).setTimerInterval(
                            getRefreshTimeInterval(refreshIntervalDropDown));
                buildGui();
            }
        }
    }
    
    private void buildGui() {
        try {
            if (initView || ObjectFactory.isGetCellsError()) {
                enableComponents(false);
            } else {
                enableComponents(true);
                
                populateCellDropDown();
                Cell selectedCell = getSelectedCell();

                if (!populateData(selectedCell, null)) {
                    return;
                }
                if (!isToggleButtonLabelShow()) {
                    nodes = hostConn.getNodes(selectedCell);
                    populateNodeDropDown(nodes);

                    setNodeSelection();
                    if (!populateData(null, selectedNode)) {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            AsyncProxy.handleException(e);
            return;
        }
    }
    private void enableComponents(boolean enable) {
        cellSelectionDropDown.setEnabled(enable);
        nodeSelectionDropDown.setEnabled(enable);
        refreshIntervalDropDown.setEnabled(enable);
        toggleButton.setEnabled(enable);
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        labelPerfStats = new javax.swing.JLabel();
        labelRefresh = new javax.swing.JLabel();
        cellSelectionDropDown = new EntryDisabledComboBox(new ComboEntry(GuiResources.getGuiString("cell.multicell.unselected")));
        refreshIntervalDropDown = new javax.swing.JComboBox();
        cellScrollPane = new javax.swing.JScrollPane();
        cellTable = new javax.swing.JTable();
        NodePanel = new javax.swing.JPanel();
        nodeScrollPane = new javax.swing.JScrollPane();
        nodeTable = new javax.swing.JTable();
        nodeCPUScrollPane = new javax.swing.JScrollPane();
        nodeCPUTable = new javax.swing.JTable();
        cellCPUScrollPane = new javax.swing.JScrollPane();
        cellCPUTable = new javax.swing.JTable();
        toggleButton = new javax.swing.JButton();
        labelFor = new javax.swing.JLabel();
        nodeSelectionDropDown = new javax.swing.JComboBox();

        labelPerfStats.setLabelFor(cellSelectionDropDown);

        labelRefresh.setLabelFor(refreshIntervalDropDown);

        cellScrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        cellScrollPane.setPreferredSize(new java.awt.Dimension(453, 400));
        cellTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        cellScrollPane.setViewportView(cellTable);

        nodeScrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        nodeTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        nodeScrollPane.setViewportView(nodeTable);

        nodeCPUScrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        nodeCPUTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        nodeCPUTable.setPreferredSize(new java.awt.Dimension(300, 68));
        nodeCPUScrollPane.setViewportView(nodeCPUTable);

        org.jdesktop.layout.GroupLayout NodePanelLayout = new org.jdesktop.layout.GroupLayout(NodePanel);
        NodePanel.setLayout(NodePanelLayout);
        NodePanelLayout.setHorizontalGroup(
            NodePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(nodeScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 632, Short.MAX_VALUE)
            .add(nodeCPUScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 632, Short.MAX_VALUE)
        );
        NodePanelLayout.setVerticalGroup(
            NodePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(NodePanelLayout.createSequentialGroup()
                .add(nodeScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 168, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nodeCPUScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 59, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(14, Short.MAX_VALUE))
        );

        cellCPUScrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        cellCPUTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        cellCPUTable.setAutoscrolls(false);
        cellCPUTable.setPreferredSize(new java.awt.Dimension(300, 68));
        cellCPUScrollPane.setViewportView(cellCPUTable);

        labelFor.setLabelFor(nodeSelectionDropDown);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(cellScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 632, Short.MAX_VALUE)
                    .add(NodePanel, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(labelPerfStats)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(cellSelectionDropDown, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 78, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(63, 63, 63)
                        .add(labelRefresh)
                        .add(1, 1, 1)
                        .add(refreshIntervalDropDown, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .add(toggleButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(labelFor)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(nodeSelectionDropDown, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, cellCPUScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 632, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(labelPerfStats)
                    .add(refreshIntervalDropDown, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(cellSelectionDropDown, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(labelRefresh))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(cellScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 167, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(cellCPUScrollPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 59, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(8, 8, 8)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(labelFor)
                    .add(nodeSelectionDropDown, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(toggleButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(NodePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    protected javax.swing.JPanel NodePanel;
    protected javax.swing.JScrollPane cellCPUScrollPane;
    protected javax.swing.JTable cellCPUTable;
    protected javax.swing.JScrollPane cellScrollPane;
    protected javax.swing.JComboBox cellSelectionDropDown;
    protected javax.swing.JTable cellTable;
    protected javax.swing.JLabel labelFor;
    private javax.swing.JLabel labelPerfStats;
    private javax.swing.JLabel labelRefresh;
    protected javax.swing.JScrollPane nodeCPUScrollPane;
    protected javax.swing.JTable nodeCPUTable;
    protected javax.swing.JScrollPane nodeScrollPane;
    protected javax.swing.JComboBox nodeSelectionDropDown;
    protected javax.swing.JTable nodeTable;
    protected javax.swing.JComboBox refreshIntervalDropDown;
    protected javax.swing.JButton toggleButton;
    // End of variables declaration//GEN-END:variables

    // ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("monitor.perfstats.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_NORTH; }
    public int getFillType() { return SizeToFitLayout.FILL_HORZ; }
    public ButtonPanel getButtonPanel() {
        return BtnPnlBlank.getButtonPanel();
    }
    public void loadValues() throws UIException, HostException {
        initView = true;
        buildGui();
        // set auto-refresh off until getCells call succeeds for this page
        ((ExplItemMonitorPerfStats)explItem).setTimerInterval(0);
        if (ObjectFactory.isGetCellsError()) {
            setTableText(GuiResources.getGuiString("silo.cell.error"), 
                                                                BOTH_TABLES);
            Log.logToStatusAreaAndExternal(Level.SEVERE,
                    GuiResources.getGuiString("silo.cell.error"), null);
            return;
        }
        AsyncProxy.run(new DataLoader());
    }
    
    // No apply button, this page is a read only page
    public void saveValues() throws UIException, HostException { }
    
    // ************************************ 
    private void setNodeSelection() {
        if (selectedNode == null) {
            nodeSelectionDropDown.setSelectedIndex(0);
            selectedNode = nodes[0];
        } else {
            selectedNodeIndex = nodeSelectionDropDown.getSelectedIndex();
            selectedNode = nodes[selectedNodeIndex];
        }
    }
    private void populateCellDropDown()
        throws ClientException, ServerException {
        int numCells = -1;

        // set up the drop down
        cellSelectionDropDown.removeAllItems();
        String cellIdName = null;

        // populate the drop down values
        numCells = cells.length;
        if (numCells > 0) {
            cellIds = new Vector(numCells);
            cellIdNames = new Vector(numCells);
        }
        Vector entries = new Vector(numCells);
        for (int i = 0; i < numCells; i++) {
            int id = cells[i].getID();
            cellIds.add(new Integer(id));
            if (cells[i].isAlive()) {
                cellIdName = GuiResources.getGuiString(
                        "explorer.silo.cells.cell", Integer.toString(id));
            } else {
                cellIdName = GuiResources.getGuiString(
                           "cell.down.error", Integer.toString(id));
            }
            cellIdNames.add(cellIdName);
            ComboEntry entry = new ComboEntry(cellIdName);
            if (entry.getText().indexOf(
                    GuiResources.getGuiString("cell.down")) > -1) {
                // disable the combobox entry
                entry.setEnabled(false);
            }
            entries.add(entry);
        }

        cellSelectionDropDown.setModel(new DefaultComboBoxModel(entries));
        int lastCellId = ObjectFactory.getLastCellId();
        if (lastCellId < 0) {
            // If not set, the last visited cell defaults to the first cell
            lastCellId = ((Integer)cellIds.get(0)).intValue();
            ObjectFactory.setLastCellId(lastCellId);
        }

        int lastCellIndex =
            cellIds.indexOf(new Integer(lastCellId));
        cellSelectionDropDown.setSelectedIndex(lastCellIndex);
    }

    private void populateNodeDropDown(Node [] nodes) {

        Vector nodeIdNames = new Vector(nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            String nodeIdName =
                GuiResources.getGuiString(
                    "explorer.silo.cells.cell.nodes.node",
                    nodes[i].getID());
            nodeIdNames.add(nodeIdName);
        }
        nodeSelectionDropDown.removeAllItems();
        nodeSelectionDropDown.setModel(new DefaultComboBoxModel(nodeIdNames));
        nodeSelectionDropDown.setSelectedIndex(selectedNodeIndex);
    }

    private void populateRefreshIntervals() {
        Vector refreshIntervals = new Vector(timeIntervals.length);
        for (int i = 0; i < timeIntervals.length; i++) {
            refreshIntervals.add(GuiResources.getGuiString(timeIntervals[i]));
        }

        // Remove all items in the drop down
        refreshIntervalDropDown.removeAllItems();

        // populate
        refreshIntervalDropDown.setModel(
                                new DefaultComboBoxModel(refreshIntervals));
        refreshIntervalDropDown.setSelectedIndex(1);
    }
    
    protected void handleIntervalSelection() throws HostException {
        int oldSamplingInterval = explItem.getTimerInterval();
        selectedInterval = getRefreshTimeInterval(refreshIntervalDropDown);  
        ((ExplItemMonitorPerfStats)explItem)
                                    .setTimerInterval(selectedInterval);
        if (explItem.getTimerInterval() == 0) {
            // auto-refresh is turned off
            setTableText(GuiResources
                 .getGuiString("monitor.perfstat.noRefreshTable.message"), 
                        BOTH_TABLES);
        } else {
            Log.logInfoMessage(GuiResources
                             .getGuiString("monitor.perfstat.refresh.message", 
                                String.valueOf(explItem.getTimerInterval())));
            if (oldSamplingInterval == 0) {
                // previous value was 0....need to inform user that perf stats
                // are now going to be retrieved
                setTableText(GuiResources.getGuiString(
                        "monitor.perfstat.refreshingTable.message"), 
                        BOTH_TABLES);
            }
        }
        
        
    }

    protected void handleCellSelection() throws HostException {
        // reset previous node since we are changing node
        previousCellStats = null;

        int idx = cellSelectionDropDown.getSelectedIndex();
        int lastCellId = cells[idx].getID();
        ObjectFactory.setLastCellId(lastCellId);

        Cell selectedCell = getSelectedCell();
        if (!populateData(selectedCell, null)) {
            return;
        }
        
        if (!isToggleButtonLabelShow()) {
            try {
                nodes = hostConn.getNodes(selectedCell);
                populateNodeDropDown(nodes);
            } catch (Exception e) {
                AsyncProxy.handleException(e);
                return;
            }
            
            setNodeSelection();
            if (!populateData(null, selectedNode)) {
                return;
            }
        }
    }

    protected void handleNodeSelection() throws HostException {
        // reset previous node since we are changing node
        previousNodeStats = null;

        selectedNodeIndex = nodeSelectionDropDown.getSelectedIndex();
        selectedNode = nodes[selectedNodeIndex];
        if (!populateData(null, selectedNode)) {
            return;
        }
    }

    private boolean isToggleButtonLabelShow() {
        String text = toggleButton.getText();
        return
            GuiResources.getGuiString(
                "monitor.perfstat.button.toggle.show").equals(text);
    }

    protected void handleToggleButtonClicked() throws HostException {
        boolean showEnabled = isToggleButtonLabelShow();
        toggleButton.setText(
            showEnabled ?
                GuiResources.getGuiString(
                    "monitor.perfstat.button.toggle.hide") :
                GuiResources.getGuiString(
                    "monitor.perfstat.button.toggle.show"));
        toggleButton.setMnemonic(
            showEnabled ?
                GuiResources.getGuiString(
                    "monitor.perfstat.button.toggle.hide.mn").charAt(0) :
                GuiResources.getGuiString(
                    "monitor.perfstat.button.toggle.show.mn").charAt(0));

        // Show or hide associated content
        labelFor.setVisible(showEnabled);
        nodeSelectionDropDown.setVisible(showEnabled);
        NodePanel.setVisible(showEnabled);

        if (showEnabled) {
            try {
                nodes = hostConn.getNodes(getSelectedCell());
                populateNodeDropDown(nodes);
            } catch (Exception e) {
                AsyncProxy.handleException(e);
                return;
            }

            setNodeSelection();
            if (!populateData(null, selectedNode)) {
                return;
            }
        } 
    }
    
    private PerfStats getPerfStats(Cell myCell, Node myNode) 
                                                throws HostException {
        int refreshInterval = explItem.getTimerInterval();
        PerfStats pStats = null;
        boolean cellStats = false;
        if (myCell != null) {
            cellStats = true;
        }
        
        try {
            if (refreshInterval > 0) {
                if (cellStats) {
                    if (myCell.isAlive()) {
                        pStats = hostConn.getPerfStats(refreshInterval, myCell);
                    }
                    cellScrollPane.setViewportView(cellTable);
                    cellCPUScrollPane.setViewportView(cellCPUTable);
                } else {
                    pStats = hostConn.getPerfStats(refreshInterval, myNode);
                    nodeScrollPane.setViewportView(nodeTable);
                    nodeCPUScrollPane.setViewportView(nodeCPUTable);
                }                  
            } else {
                // auto-refresh is turned off
                setTableText(GuiResources
                     .getGuiString("monitor.perfstat.noRefreshTable.message"), 
                            BOTH_TABLES);
            }            
        } catch (Exception e) {
            if (cellStats) {
                setTableText(GuiResources
                            .getGuiString("monitor.autoRefresh.cell.error",
                                String.valueOf(myCell.getID())), CELL_TABLE);
                Log.logAndDisplay(Level.SEVERE, GuiResources
                                .getGuiString("monitor.autoRefresh.cell.error",
                                    String.valueOf(myCell.getID())), null);
            } else {
                setTableText(GuiResources
                                .getGuiString("monitor.autoRefresh.node.error",
                                    myNode.getID()), NODE_TABLE);
                Log.logAndDisplay(Level.SEVERE, GuiResources
                                .getGuiString("monitor.autoRefresh.node.error",
                                    myNode.getID()), null);
            }
            ((ExplItemMonitorPerfStats)explItem).setTimerInterval(0);
            refreshIntervalDropDown.setSelectedIndex(0);
        }
        
        return pStats;
    }

    private Cell getSelectedCell() {
        int lastCellId = ObjectFactory.getLastCellId();
        if (lastCellId == -1 && cells != null && cells.length > 1) {
            // get the first cell in the list
            lastCellId = ((Integer)cellIds.get(0)).intValue();
            ObjectFactory.setLastCellId(lastCellId);
        }
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].getID() == ObjectFactory.getLastCellId()) {
                if (cells[i].isAlive()) {
                    return cells[i];
                } else {
                    // master cell is never dead so return it
                    cellSelectionDropDown.setSelectedIndex(0);
                    return cells[0];
                }   
            }
        }
        return null;
    }

    private int getRefreshTimeInterval(JComboBox dropDown) {
        int selectedIndex = dropDown.getSelectedIndex();
        if (selectedIndex >= 0 || selectedIndex < intervalSecs.length)
            return intervalSecs[selectedIndex];
        else
            return defaultInterval;
    }
    
    private void populateMainTable(Cell myCell, Node myNode)
        throws HostException {

        MonitorPerfStatsTableModel model = null;
        OpnStats [] stats = null;
        boolean onCellTable = true;
        
        if (myCell != null) {
            model = (MonitorPerfStatsTableModel) cellTable.getModel();
        } else {
            onCellTable = false;
            model = (MonitorPerfStatsTableModel) nodeTable.getModel();
        }
        model.removeAll();
        
        PerfStats ps = getPerfStats(myCell, myNode);
        if (ps == null) {
            return;
        }
        stats = ps.getOpnStats();
        

        double kbsLast = 0, opssLast = 0;
        double kbsChange = 0, opssChange = 0;
        int opsLast = 0, opsChange = 0;
        String operation = EMPTY_STR;

        for (int i = 0; i < stats.length; i++) {
            int type = stats[i].getOpnType();
            OpnStats previousStat =
                onCellTable ?
                    previousCellStats == null ?
                        null : getPreviousStat(previousCellStats, type) :
                    previousNodeStats == null ?
                        null : getPreviousStat(previousNodeStats, type);
            kbsLast = stats[i].getKBPerSec();
            opssLast = stats[i].getOpsPerSec();
            opsLast  = stats[i].getOps();

            if (onCellTable) {
                // Cell
                if (previousCellStats != null) {
                    kbsChange = kbsLast - previousStat.getKBPerSec();
                    opsChange  = opsLast - previousStat.getOps();
                    opssChange = opssLast = previousStat.getOpsPerSec();
                }
            } else {
                // Node
                if (previousNodeStats != null) {
                    kbsChange = kbsLast - previousStat.getKBPerSec();
                    opsChange  = opsLast - previousStat.getOps();
                    opssChange = opssLast = previousStat.getOpsPerSec();
                }
            }

            switch (type) {
                /* Stored Data Only. removed from perfstats CLI
                case OpnStats.OP_STORE_DATA:
                    operation = "monitor.perfstat.table.row.store.data";
                    break;
                */
                // Stored Metadata Only
                case OpnStats.OP_STORE_MD:
                    operation = "monitor.perfstat.table.row.store.metadata";
                    break;

                // Stored Both
                case OpnStats.OP_STORE_BOTH:
                    operation = "monitor.perfstat.table.row.store.both";
                    break;

                // Retrieve Data Only
                case OpnStats.OP_RETRIEVE_DATA:
                    operation = "monitor.perfstat.table.row.retrieve.data";
                    break;

                // Retrieve Metadata Only
                case OpnStats.OP_RETRIEVE_MD:
                    operation = "monitor.perfstat.table.row.retrieve.metadata";
                    break;

                // Query
                case OpnStats.OP_QUERY:
                    operation = "monitor.perfstat.table.row.query";
                    break;

                // Delete
                case OpnStats.OP_DELETE:
                    operation = "monitor.perfstat.table.row.delete";
                    break;

                // Web Dev Put
                case OpnStats.OP_WEBDAV_PUT:
                    operation = "monitor.perfstat.table.row.webdev.put";
                    break;

                // Web Dev Get
                case OpnStats.OP_WEBDAV_GET:
                    operation = "monitor.perfstat.table.row.webdev.get";
                    break;
                default:
                    continue;
            }

            if ((onCellTable && previousCellStats != null) ||
                (!onCellTable && previousNodeStats != null)) {
                model.addRow(
                    new Object [] {
                        GuiResources.getGuiString(operation),
                        (type == OpnStats.OP_DELETE || type ==
                         OpnStats.OP_QUERY) ? " " : formatStat(kbsLast),
                        (type == OpnStats.OP_DELETE || type ==
                         OpnStats.OP_QUERY) ? " " : appendPlus(kbsChange),
                        Integer.toString(opsLast),
                        appendPlus(opsChange),
                        formatStat(opssLast),
                        appendPlus(opssChange)});
            } else {
                model.addRow(
                    new Object [] {
                        GuiResources.getGuiString(operation),
                        (type == OpnStats.OP_DELETE || type ==
                         OpnStats.OP_QUERY) ? " " : formatStat(kbsLast),
                        EMPTY_STR,
                        Integer.toString(opsLast),
                        EMPTY_STR,
                        formatStat(opssLast),
                        EMPTY_STR});
            }

            // reset change variables for next comparison
            kbsChange = 0;
            opsChange  = 0;
            opssChange = 0;
        }

        // Update previous OpnStats to the current one, then shrink the table
        // to eliminate excessive white spaces at the bottom of the table
        if (onCellTable) {
            previousCellStats = stats;
            ToolBox.shrinkWrapTableViewport(cellTable);
        } else {
            previousNodeStats = stats;
            ToolBox.shrinkWrapTableViewport(nodeTable);
        }
    }
    private boolean populateData(Cell myCell, Node myNode) 
                                                throws HostException {
        boolean pulse = true;
        if (myCell != null) {
            pulse = myCell.isAlive();
        }
        populateMainTable(myCell, myNode);
        if (getRefreshTimeInterval(refreshIntervalDropDown) == 0 ||
                !pulse) {
            return false;
        }
        populateCPUTable(myCell, myNode);
        if (getRefreshTimeInterval(refreshIntervalDropDown) == 0 ||
                !pulse)
            return false;
        else
            return true;
    }
    private void populateCPUTable(Cell myCell, Node myNode)
        throws HostException {
        MonitorPerfStatsTableModel model = null;
        double [] cpuLoads = null;
        boolean onCellTable = true;

        if (myCell != null) {
            model = (MonitorPerfStatsTableModel) cellCPUTable.getModel();
        } else {
            onCellTable = false;
            model = (MonitorPerfStatsTableModel) nodeCPUTable.getModel();
        }
        model.removeAll();
        
        PerfStats ps = getPerfStats(myCell, myNode);
        if (ps == null) {
            return;
        }
        cpuLoads = ps.getCpuLoad();

        double load1min = 0, load5min = 0, load15min = 0;

        for (int i = 0; i < cpuLoads.length; i++) {
            switch (i) {
                case 0:
                    load1min = cpuLoads[i];
                    break;
                case 1:
                    load5min = cpuLoads[i];
                    break;
                case 2:
                    load15min = cpuLoads[i];
                    break;
            }
        }
        Object [] data = null;

        if (onCellTable && previousCPUCellStats != null ||
            !onCellTable && previousCPUNodeStats != null) {
            double load1minChange =
                onCellTable ?
                    load1min - previousCPUCellStats[0] :
                    load1min - previousCPUNodeStats[0];
            double load5minChange =
                onCellTable ?
                    load5min - previousCPUCellStats[1] :
                    load5min - previousCPUNodeStats[1];
            double load15minChange =
                onCellTable ?
                    load15min - previousCPUCellStats[2] :
                    load15min - previousCPUNodeStats[2];
            data = new Object [] {
                GuiResources.getGuiString(
                    "monitor.perfstat.table.row.cpu"),
                formatStat(load1min),
                appendPlus(load1minChange),
                formatStat(load5min),
                appendPlus(load5minChange),
                formatStat(load15min),
                appendPlus(load15minChange)
            };
        } else {
            data = new Object [] {
                GuiResources.getGuiString(
                    "monitor.perfstat.table.row.cpu"),
                formatStat(load1min),
                EMPTY_STR,
                formatStat(load5min),
                EMPTY_STR,
                formatStat(load15min),
                EMPTY_STR
            };
        }
        model.addRow(data);

        // Update previous OpnStats to the current one, then shrink the table
        // to eliminate excessive white spaces at the bottom of the table
        if (onCellTable) {
            previousCPUCellStats = cpuLoads;
            ToolBox.shrinkWrapTableViewport(cellCPUTable);
        } else {
            previousCPUNodeStats = cpuLoads;
            ToolBox.shrinkWrapTableViewport(nodeCPUTable);
        }
    }
   
    private void setTableText(String text, int location) {
        StringBuffer html = null;
        JEditorPane jep = null;
        
        if (text != null) {
            if (text.length() > 0) {
                html = new StringBuffer();
                html.append("<html><body><center>");
                html.append("<b><font face='arial' font-size='10pt'>");
                html.append(text);
                html.append("</font></b></center></body></html>");

                jep = new JEditorPane();
                jep.setEditable(false);
                switch (location) {
                    case (CELL_TABLE):
                        emptyCellTable.setText(html.toString());
                        cellScrollPane.setViewportView(emptyCellTable);
                        cellCPUScrollPane.setViewportView(jep);
                        break;
                    case (NODE_TABLE):
                        emptyNodeTable.setText(html.toString());
                        nodeScrollPane.setViewportView(emptyNodeTable);
                        nodeCPUScrollPane.setViewportView(jep);
                        break;
                    case (BOTH_TABLES):
                    default:
                        setTableText(text, CELL_TABLE);
                        setTableText(text, NODE_TABLE);
                        break;
                }
            } else {
                jep = new JEditorPane();
                emptyCellTable.setText(EMPTY_STR);
                cellScrollPane.setViewportView(emptyCellTable);
                cellCPUScrollPane.setViewportView(jep);
                emptyNodeTable.setText(EMPTY_STR);
                nodeScrollPane.setViewportView(emptyNodeTable);
                nodeCPUScrollPane.setViewportView(jep);
            }
        } 
    }

    /**
     * Return previous OpnStats object by providing the operation type.
     * Should pass in either previousCellStats or previousNodeStats.
     */
    private OpnStats getPreviousStat(OpnStats [] previousStats, int type) {
        for (int i = 0; i < previousStats.length; i++) {
            if (previousStats[i].getOpnType() == type) {
                return previousStats[i];
            }
        }
        return null;
    }
    
    /**
     * Formats a double number to have precision of one decimal place
     */
    private String formatStat(double value) {
        NumberFormat numFormatter = 
                    NumberFormat.getNumberInstance(Locale.getDefault());
        DecimalFormat df = (DecimalFormat)numFormatter;
        df.setMinimumFractionDigits(1);
        df.setMaximumFractionDigits(1);
        return df.format(value);   
    }


    /**
     * Append a plus sign if the value is a positive double/int
     */
    private String appendPlus(double dValue) {
        return
                dValue > 0 ?
                    PLUS_SIGN.concat(formatStat(dValue)) :
                    formatStat(dValue);
    }

    private String appendPlus(int iValue) {
        return
            iValue > 0 ?
                PLUS_SIGN.concat(Integer.toString(iValue)) :
                Integer.toString(iValue);
    }

    public String getPageKey() {
        return HelpFileMapping.PERFORMANCESTATISTICS;
    }
}
