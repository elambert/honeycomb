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
 * PanelSilo.java
 *
 * Created on February 8, 2006, 10:54 AM
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.CellProps;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServiceNode;
import com.sun.honeycomb.admingui.client.Switch;
import com.sun.honeycomb.admingui.present.HelpFileMapping;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemCell;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemCells;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemSilo;
import com.sun.honeycomb.common.CliConstants;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.utility.AsyncProxy;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author  dp127224
 */
public class PnlCell extends JPanel implements ContentPanel {

    private NodeSummaryTable tablSummary;
    private PnlDynamicCell imagePanel;
    private Vector imgPanels = null;
    private AdminApi adminApi = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private ServiceNode sp = null;
    private ImageIcon imgCellTop = null;
    private ImageIcon imgNode = null;
    private boolean multicell = false;
    
    private PnlFWVersions pnlVersion = null;
    private boolean initView = false;
    
    // These keep track of the current and previous row numbers selected
    private int currentRow = -1;
    private int previousRow = -1;
    
    private boolean chkboxSel = false;
    
    class NotifyPanel extends JPanel {
      public NotifyPanel(String s1, String s2) {
         chkboxSel = false;
         final JCheckBox options = new JCheckBox(s1, false);
          options.addActionListener(new ActionListener() {
              public void actionPerformed(ActionEvent e) {
                chkboxSel = options.isSelected();
              }
          });
          setLayout(new BorderLayout());
          add(new JLabel(s2), BorderLayout.CENTER);
          add(options, BorderLayout.SOUTH);
       }
    }
    
    public PnlCell() throws UIException {
        initComponents();
        initComponents2();
    }
 
    private void initComponents2() throws UIException {
        // Remove default grey border
        setBorder(null);
        
        // Operations combo box
        cboRunningMode.setMessage(
                GuiResources.getGuiString("cell.operation.unselected"));
        // WIDTH_BY_BOTH is more expensive, but the number of cbo items is small
        // so its ok.
        cboRunningMode.setWidthBy(JComboBoxWithDefaultMsg.WIDTH_BY_BOTH);
        
        tablSummary = new NodeSummaryTable(); 
        tablSummary.getJTable().getColumnModel().
                getColumn(NodeSummaryTableModel.STATUS)
                                    .setCellRenderer(new CenteredRenderer());
        tablSummary.getJTable().setDefaultRenderer(Object.class, 
                new TooltipCellRenderer(new Integer[]{
                        new Integer(NodeSummaryTableModel.FRU_ID)}));
        pnlSummaryTable.add(tablSummary);
        
    }
    
    private void setUpListeners() {

        final JTable nodeSummaryTable = tablSummary.getJTable();
        // Ask to be notified of row selection changes in order to highlight
        // the corresponding component in the cell image.pnl
        nodeSummaryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListSelectionModel rowModel = nodeSummaryTable.getSelectionModel();
        rowModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel lsm =
                    (ListSelectionModel)e.getSource();
                PnlImageCellComponent pCurrentItem = null;
                PnlImageCellComponent pPreviousItem = null;
                if (!lsm.isSelectionEmpty()) {
                    currentRow = lsm.getMinSelectionIndex();
                    if (previousRow >= 0) {
                        pPreviousItem = 
                            (PnlImageCellComponent)imgPanels.get(previousRow);
                        pPreviousItem.setSelectionBorder(false);         
                    }
                    previousRow = currentRow;
                    pCurrentItem = 
                            (PnlImageCellComponent)imgPanels.get(currentRow);
                    pCurrentItem.setSelectionBorder(true);
                }
                
            }
        });

        
        // Add a mouse listener to each item
        Iterator iter = imgPanels.iterator();
        while (iter.hasNext()) {
            final PnlImageCellComponent item = 
                                    (PnlImageCellComponent)iter.next();
       
            item.addMouseListener(
                new MouseAdapter() {
                    public void mouseClicked(MouseEvent mouseClick) {
                        ListSelectionModel lsm = 
                                    nodeSummaryTable.getSelectionModel();
                        int lastRowSelected = lsm.getMaxSelectionIndex();
                        PnlImageCellComponent pItem = null;
                        if (lastRowSelected >= 0) {
                            // Clear the previous selected item if one was
                            // selected already
                            pItem = (PnlImageCellComponent)imgPanels.
                                                        get(lastRowSelected);
                            pItem.setSelectionBorder(false);
                        }
                        LblImageBase item = 
                                (LblImageBase)mouseClick.getSource();
                        PnlImageCellComponent component =
                                (PnlImageCellComponent)item.getParent();
                        int rowToSelect = imgPanels.indexOf(component);
                        // Selecting the row in the table will fire 
                        // an event to the list selection listener and
                        // thereby highlighting the clicked on node
                        lsm.setLeadSelectionIndex(rowToSelect);
                        lsm.setSelectionInterval(rowToSelect, rowToSelect);
                        component.setSelectionBorder(true);
                    }
            
            });
        }
    }
    
    // ***************************************************
    // ContentPanel Impl
    
    public String getTitle() {
        Cell theCell = (Cell) explItem.getPanelPrimerData();
        ObjectFactory.setLastCellId(theCell.getID());
        String id = Integer.toString(theCell.getID());
        return GuiResources.getGuiString("cell.title", id);
    }
    
    public int getAnchor() { return SizeToFitLayout.ANCHOR_NORTHWEST; }
    public int getFillType  () { return SizeToFitLayout.FILL_BOTH; }
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public ButtonPanel getButtonPanel() {
        ButtonPanel bPanel = null;
        if (ObjectFactory.isAdmin()) {
            bPanel = BtnPnlApplyCancel.getButtonPanel();
        } else {
            bPanel = BtnPnlBlank.getButtonPanel();
        }
        return bPanel;
    }
    
    public class DataLoader implements Runnable {
        boolean err = false;
        public void run() {
            Cell[] cells = null;
            try {
                cells = adminApi.getCells();
             } catch (Exception e) {
                err = true;
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
                            adminApi.getCellProps(c.getID());
                        } catch (Exception e) {
                            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                    GuiResources.getGuiString(
                                            "cellProps.refresh.error", 
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
//                    adminApi.getNumOfUnhealedFailures(cells[i].getID());
//                    if (adminApi.hasQuorum(cells[i].getID()))
//                        adminApi.getRecoveryCompletionPercent(cells[i]);
                    }
                } // end of for loop
            }  
        }
        public void runReturn() {
            if (err) {
                showHomePage();
            } else {
                // turn auto-refresh back on since cell information is cached
                // and if time-outs occur retrieving cell info then GUI 
                // won't be affected
                ((ExplItemCell)explItem).
                                setTimerInterval(MainFrame.getRefreshRate());
                // data has been gathered and now it is time to update the 
                // initial cell image(s) with the information to render a
                // dynamic cell image
                initView = false;
                buildGui();
            }
        }
    }
    
    // TODO: Need to display cell online / offline status.
    public void loadValues() {
        // initial view before data has been collected
        initView = true;
        buildGui();
        Log.logInfoMessage(GuiResources.getGuiString("cell.refresh.message"));
        // set auto-refresh off until getCells call returns 
        ((ExplItemCell)explItem).setTimerInterval(0);
        
        if (ObjectFactory.isGetCellsError()) {
            showHomePage();
            // return if there was an error getting cell information -- do not
            // initiate remote call on the asynchronous thread in DataLoader
            return;
        }
        AsyncProxy.run(new DataLoader());
    }
    private void showHomePage() {
        SwingUtilities.invokeLater(new Runnable(){
            public void run() {
                try{
                    ExplItemSilo homePage = 
                                new ExplItemSilo(ObjectFactory.isAdmin());
                    MainFrame.getMainFrame().selectExplorerItem(homePage);
                    Log.logToStatusAreaAndExternal(Level.SEVERE,
                        GuiResources.getGuiString("silo.cell.error"), null);
                } catch (Exception e) {}
            }
        });
    }
    private void buildGui() {
        try {
            Cell panelCell = (Cell) explItem.getPanelPrimerData();
            int cellID = panelCell.getID();
            lblIdValue.setText(Integer.toString(cellID));
            Cell[] cells = null;
            Cell theCell = null;

            if (ObjectFactory.isGetCellsError()) {
                showHomePage();
            } else if (initView) {
                imagePanel = new PnlDynamicCell(false, "", 
                                                panelCell.getNumNodes());
                pnlVersion = new PnlFWVersions();
                cboRunningMode.setEnabled(false);
                DefaultTableModel dtm = (DefaultTableModel) tablSummary.
                                                        getJTable().getModel();
                dtm.setRowCount(0);
            } else {
                // Cell object from panel primer data is not updated -- thus,
                // need to use recent cell data to determine logic
                cells = adminApi.getCells();
                theCell = this.findCell(cells, cellID);            
                // only refresh the tree node if the states differ (i.e.stagnant
                // data is preserved in the panelCell while recent data has been
                // collected and stored in theCell)
                if (theCell.isAlive() != panelCell.isAlive()) {
                    try {
                        MainFrame.getMainFrame().
                                    refreshExplorerItemData(ExplItemCells.class);
                    } catch (Exception e) {
                        // do nothing...
                    }
                }
                Node[] nodes = null;
                Switch[] switches = null;

                // Number of cells in the silo   
                if (cells.length == 1) {
                    multicell = false;
                } else {
                    multicell = true;
                }
                
                tablSummary.populate(theCell);
                
                // Firmware version information, pass the Cell to constructor
                pnlVersion = new PnlFWVersions(theCell);             
                String time = GuiResources.getGuiString("cellProps.unavailable");
                String ver = GuiResources.getGuiString("cellProps.unavailable");
                String aIP = GuiResources.getGuiString("cellProps.unavailable");
                String dIP = GuiResources.getGuiString("cellProps.unavailable");
                if (!theCell.isAlive()) {
                    // build static error cell
                    imagePanel = new PnlDynamicCell(true, 
                                GuiResources.getGuiString("cell.error.toolTip"),
                                AdminApi.FULL_CELL_NODES);
                } else {
                    // build the dynamic cell image
                    // get cell properties
                    CellProps cellProps = null;
                    try {
                        cellProps = adminApi.getCellProps(theCell.getID());
                        aIP = cellProps.adminIP;
                        dIP = cellProps.dataIP;
                    } catch (Exception exc) {
                        // if connection is refused due to cell being down
                        // then we don't want to bomb out -- notify user
                        Log.logToStatusAreaAndExternal(Level.SEVERE, 
                                GuiResources.getGuiString(
                                        "cellProps.refresh.error", 
                                Integer.toString(theCell.getID())), null); 
                    }
                    // Retrieve the system time for the cell
                    time = adminApi.getDate(cellID);
                    nodes = adminApi.getNodes(theCell);
                    switches = adminApi.getSwitches(theCell);
                    imagePanel = new PnlDynamicCell(
                                            theCell, nodes, switches, sp); 
                    imgPanels = imagePanel.getCellPanels();

                    // Parse out only version number from the string if possible
                    String cellVersion = pnlVersion.getCellVersion();
                    int vStart = cellVersion.indexOf('[');
                    int vEnd = cellVersion.indexOf(']');
                    // syntax may change so check to see if brackets were found
                    if (vStart != -1 && vEnd != -1) {
                        ver = cellVersion.substring(++vStart, vEnd);
                    }

                    setUpListeners();

                    // Running mode combo
                    cboRunningMode.removeAllItems();

                    String runningMode = (String) cboRunningMode.getSelectedItem();
                    if ((theCell != null) && (cellID >= 0)) {
                        cboRunningMode.addItem(GuiResources.getGuiString(
                                "cell.operation.unselected"));
                        cboRunningMode.addItem(GuiResources.getGuiString(
                                "cell.operation.reboot"));
                        cboRunningMode.addItem(GuiResources.getGuiString(
                                "cell.operation.shutdown"));
                        if (multicell) {
                            cboRunningMode.addItem(GuiResources.getGuiString(
                                "cell.operation.wipeAll"));
                        } else {
                            cboRunningMode.addItem(GuiResources.getGuiString(
                                "cell.operation.wipe"));
                        }
                    }

                    // mode combo
                    // disable running mode drop down if user is in read only 
                    cboRunningMode.setEnabled(ObjectFactory.isAdmin() && 
                                                            theCell.isAlive());
                    cboRunningMode.setSelectedIndex(0);
                    lblUnhealedFailures.setVisible(false);
                    // Do not remove - unhealed failures and recovery progress
                    // may be used in post 1.1 code
        //            int failures = adminApi.
        //                               getNumOfUnhealedFailures(theCell.getID());
        //            lblUnhealedFailuresValue.setText(Integer.toString(failures));
        //            if (failures > 0) {
        //                boolean hasQuorum = true;
        //                try {
        //                    hasQuorum = adminApi.hasQuorum(theCell.getID());
        //                } catch (NullPointerException npe) {
        //                    // there is a bug on the server side code -- leave
        //                    // until it is fixed....TODO
        //                }
        //                if (hasQuorum) {
        //                    JProgressBar prgHealing = new JProgressBar();
        //                    prgHealing.setPreferredSize(new Dimension(
        //                               lblUnhealedFailures.getSize().width, 12));
        //                    int percentHealed = adminApi
        //                               .getRecoveryCompletionPercent(theCell);
        //                    prgHealing.setStringPainted(true);
        //                    prgHealing.setString(GuiResources.
        //                                     getGuiString("silo.percent.healed", 
        //                                     Integer.toString(percentHealed)));
        //                    prgHealing.setValue(percentHealed);
        //                    pnlDiskStatus.add(prgHealing);
        //                } else {
        //                    JLabel lblQuorum = new JLabel(GuiResources.
        //                            getGuiString("silo.noQuorum"), JLabel.LEFT); 
        //                    pnlDiskStatus.add(lblQuorum);
        //                }
        //            }
                }// cell alive check
                lblTimeValue.setText(time);
                lblVersionValue.setText(ver);
                lblAdminIPValue.setText(aIP);
                lblDataIPValue.setText(dIP);
            }      
            pnlCellImage.removeAll();
            pnlCellImage.add(imagePanel);
            pnlCellImage.setAlignmentX(Component.LEFT_ALIGNMENT);
            this.validate();
            this.repaint(); 
        } catch (Exception e) {
            throw new RuntimeException(e);
        } 
    }
    
    public void saveValues() throws HostException, UIException {
        try {
            String confirm = null;
            String chkboxMsg = null;
            Object chkboxParam =  null;
            String submitted = null;
            String runningMode = null;
            
            Cell theCell = (Cell) explItem.getPanelPrimerData();
            // Reboot or shutdown cell
            runningMode = (String) cboRunningMode.getSelectedItem();
            if (runningMode.equals(
                    GuiResources.getGuiString("cell.operation.unselected"))) {
                return;
            }
            if (runningMode.equals(
                    GuiResources.getGuiString("cell.operation.shutdown"))) {
                confirm = GuiResources.getGuiString(
                        "cell.operation.shutdown.confirm",
                        String.valueOf(theCell.getID()));
                chkboxMsg = GuiResources.getGuiString
                        ("cell.operation.shutdown.options");
                chkboxParam = new NotifyPanel(chkboxMsg, confirm);     
            } else if (runningMode.equals(
                    GuiResources.getGuiString("cell.operation.reboot"))) {
                confirm = GuiResources.getGuiString(
                        "cell.operation.reboot.confirm",
                        String.valueOf(theCell.getID()));
                chkboxMsg = GuiResources.getGuiString
                        ("cell.operation.reboot.options");
                chkboxParam = new NotifyPanel(chkboxMsg, confirm);    
            } else {
                if (multicell) {
                    confirm = GuiResources.getGuiString(
                        "cell.operation.wipeAll.confirm");
                } else {
                    confirm = GuiResources.getGuiString(
                        "cell.operation.wipe.confirm");
                }                
                chkboxParam = confirm;     
             } 
                   
            int retVal = JOptionPane.showConfirmDialog(MainFrame.getMainFrame(),
                    chkboxParam,
                    GuiResources.getGuiString("app.name"),
                    JOptionPane.YES_NO_OPTION);
            if (retVal == JOptionPane.YES_OPTION) {                
                if (runningMode.equals(
                        GuiResources.getGuiString(
                            "cell.operation.shutdown"))) {
                    submitted = GuiResources.getGuiString(
                            "cell.operation.shutdown.submitted",
                            String.valueOf(theCell.getID()));
                    if (!adminApi.areSwitchesOk(theCell.getID()) 
                                                    && chkboxSel) {
                        submitted = GuiResources.getGuiString(
                                            "switches.cell.shutdown.error");
                        JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            submitted,
                            GuiResources.getGuiString("app.name"),
                            JOptionPane.ERROR_MESSAGE);
                    } else {
                        if (!theCell.isMaster()) {
                            // shutdown synchronously
                            adminApi.powerOff(theCell, chkboxSel, chkboxSel);
                        } else {
                            // shutdown asynchronously AND close GUI
                            ObjectFactory.shutdownSystem(theCell, chkboxSel,
                                                        chkboxSel, false, this);
                        }
                    }
                } else if (runningMode.equals(
                        GuiResources.getGuiString("cell.operation.reboot"))) {
                    submitted = GuiResources.getGuiString(
                                            "cell.operation.reboot.submitted",
                                            String.valueOf(theCell.getID()));
                    if (!adminApi.areSwitchesOk(theCell.getID()) 
                                                    && chkboxSel) {
                        submitted = GuiResources.getGuiString(
                                            "switches.cell.reboot.error");
                        JOptionPane.showMessageDialog(
                            MainFrame.getMainFrame(),
                            submitted,
                            GuiResources.getGuiString("app.name"),
                            JOptionPane.ERROR_MESSAGE);
                    } else {
                        if (!theCell.isMaster()) {
                            // reboot synchronously
                            adminApi.reboot(theCell, chkboxSel, chkboxSel);
                        } else {
                            // reboot asynchronously AND close GUI
                            ObjectFactory.shutdownSystem(theCell, chkboxSel,
                                                         chkboxSel, true, this);
                        }
                    }
                } else if (runningMode.equals(
                        GuiResources.getGuiString("cell.operation.wipe")) ||
                            runningMode.equals(GuiResources.getGuiString(
                                                "cell.operation.wipeAll"))) {
                    adminApi.wipeDisks();
                    if (multicell) {
                        submitted = GuiResources.getGuiString(
                                        "cell.operation.wipeAll.submitted");
                    } else {
                        submitted = GuiResources.getGuiString(
                                        "cell.operation.wipe.submitted");
                    }
                } else {
                    submitted = GuiResources.getGuiString(
                                                "cell.operation.unknown");
                }
                Log.logInfoMessage(submitted);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getPageKey() {
        return HelpFileMapping.CELLSTATUS;
    }
    
   
    // ********************************************************
    private Cell findCell(Cell[] cArray, int id) {
        Cell c = null;
        for (int idx = 0; idx < cArray.length; idx++) {
            c = cArray[idx];
            if (c.getID() == id) {
                break;
            }
        }
        return c;
    }
    private void loadVersionInfo() {
        
    }
    
    
    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        lblId = new javax.swing.JLabel();
        lblIdValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        lblRunningMode = new javax.swing.JLabel();
        pnlSummaryTable = new javax.swing.JPanel();
        cboRunningMode = new com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg();
        pnlCellImage = new javax.swing.JPanel();
        lblAdminIP = new javax.swing.JLabel();
        lblDataIP = new javax.swing.JLabel();
        lblDataIPValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        lblUnhealedFailuresValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        lblAdminIPValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        lblVersion = new javax.swing.JLabel();
        lblVersionValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();
        pnlDiskStatus = new javax.swing.JPanel();
        lblUnhealedFailures = new javax.swing.JLabel();
        lblSystemTime = new javax.swing.JLabel();
        lblTimeValue = new com.sun.nws.mozart.ui.swingextensions.JLabelData();

        lblId.setText(GuiResources.getGuiString("cell.id"));
        lblId.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        lblIdValue.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        lblRunningMode.setDisplayedMnemonic(GuiResources.getGuiString("cell.operation.mn").charAt(0));
        lblRunningMode.setLabelFor(cboRunningMode);
        lblRunningMode.setText(GuiResources.getGuiString("cell.operation"));

        pnlSummaryTable.setLayout(new java.awt.BorderLayout());

        cboRunningMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cboRunningModeActionPerformed(evt);
            }
        });

        pnlCellImage.setLayout(new java.awt.GridLayout(1, 0));

        pnlCellImage.setMaximumSize(new java.awt.Dimension(4000, 4000));

        lblAdminIP.setText(GuiResources.getGuiString("silo.adminIp"));
        lblAdminIP.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        lblDataIP.setText(GuiResources.getGuiString("silo.dataIp"));

        lblAdminIPValue.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        lblVersion.setText(GuiResources.getGuiString("cell.version"));
        lblVersion.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        lblVersionValue.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        pnlDiskStatus.setLayout(new javax.swing.BoxLayout(pnlDiskStatus, javax.swing.BoxLayout.Y_AXIS));

        lblSystemTime.setText(GuiResources.getGuiString("config.network.ntp.systemTime"));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, pnlCellImage, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 131, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(lblDataIP)
                            .add(lblAdminIP))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(lblDataIPValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(lblAdminIPValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 30, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(pnlSummaryTable, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)
                        .addContainerGap())
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(lblId, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 39, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(lblIdValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(lblSystemTime, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 82, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(lblRunningMode))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(cboRunningMode, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 200, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(lblTimeValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(layout.createSequentialGroup()
                                .add(lblVersion, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 81, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(lblVersionValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                        .add(134, 134, 134))
                    .add(layout.createSequentialGroup()
                        .add(42, 42, 42)
                        .add(lblUnhealedFailures, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(lblUnhealedFailuresValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 114, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(pnlDiskStatus, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 126, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(112, Short.MAX_VALUE))))
        );

        layout.linkSize(new java.awt.Component[] {lblAdminIPValue, lblDataIPValue}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(26, 26, 26)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(lblDataIP)
                            .add(lblDataIPValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(lblSystemTime)
                            .add(lblTimeValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(lblAdminIPValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(lblAdminIP)
                        .add(lblId)
                        .add(lblIdValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(lblVersion)
                        .add(lblVersionValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .add(8, 8, 8)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(layout.createSequentialGroup()
                        .add(pnlSummaryTable, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(lblRunningMode, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 21, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(cboRunningMode, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(32, 32, 32)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(lblUnhealedFailuresValue, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(lblUnhealedFailures, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 16, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(pnlDiskStatus, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(36, 36, 36))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(pnlCellImage, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(122, 122, 122)))
                .add(13, 13, 13))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    private void cboRunningModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cboRunningModeActionPerformed
        
        if (cboRunningMode.getSelectedIndex() == 0) {
            explItem.setIsModified(false);
        } else if (explItem != null) {
            explItem.setIsModified(true);
        }
    }//GEN-LAST:event_cboRunningModeActionPerformed
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.JComboBoxWithDefaultMsg cboRunningMode;
    private javax.swing.JLabel lblAdminIP;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblAdminIPValue;
    private javax.swing.JLabel lblDataIP;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblDataIPValue;
    private javax.swing.JLabel lblId;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblIdValue;
    private javax.swing.JLabel lblRunningMode;
    private javax.swing.JLabel lblSystemTime;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblTimeValue;
    private javax.swing.JLabel lblUnhealedFailures;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblUnhealedFailuresValue;
    private javax.swing.JLabel lblVersion;
    private com.sun.nws.mozart.ui.swingextensions.JLabelData lblVersionValue;
    private javax.swing.JPanel pnlCellImage;
    private javax.swing.JPanel pnlDiskStatus;
    private javax.swing.JPanel pnlSummaryTable;
    // End of variables declaration//GEN-END:variables
    
    
}
