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
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems.ExplItemConfigMetadataViewSetup;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.FsAttribute;
import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveList;
import com.sun.nws.mozart.ui.swingextensions.MultiLineTableHeaderRenderer;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author  jb127219
 */
public class PnlConfigDataViewViews extends javax.swing.JPanel
                            implements ContentPanel {
    
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private PnlConfigViewSummaryTableModel viewModel = null;
    private PnlConfigDataViewTableModel vFieldsModel = null;
    /**
     * Map of <view name, view fields> for all views
     * where the view name is a String representing the fully qualified
     * name and the view fields are an ArrayList of Field objects
     */
    private HashMap allViewFieldMap = new HashMap();

    
    // root namespace and its localized name
    private RootNamespace rootNS = null;
    
    // Vector of views
    private Vector vViews = new Vector();
       
    /**
     * Creates new form PnlConfigDataViewViews
     */
    public PnlConfigDataViewViews() throws HostException, UIException {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() throws HostException {
        setUpAddRemoveLists();
        setUpTableListeners();
    }
   
    private void setUpAddRemoveLists() {
        
        // View Summary Add Remove List
        viewModel = new PnlConfigViewSummaryTableModel();
        arlViewSummary.setTableModel(viewModel);
        arlViewSummary.setTableCellRenderer(
                   Object.class, new TooltipCellRenderer(
                      new Integer[]{new Integer(viewModel.NAME), 
                                    new Integer(viewModel.FILENAME_PATTERN)}));
//                                    new Integer(viewModel.ARCHIVES)}));
        arlViewSummary.setAddButtonTooltip(GuiResources.getGuiString(
                "config.metadata.namespace.addTooltip"));
        arlViewSummary.setRemoveButtonTooltip(GuiResources.getGuiString(
                "config.metadata.namespace.removeTooltip"));
        // Use multi-line headers
        TableColumnModel colModel = arlViewSummary.getTableColumnModel();
        Enumeration cols = colModel.getColumns();
        while (cols.hasMoreElements()) {
            ((TableColumn)cols.nextElement())
                              .setHeaderRenderer(
                                        new MultiLineTableHeaderRenderer());
        }
        
        // View Fields Add Remove List
        vFieldsModel = new PnlConfigDataViewTableModel(
                PnlConfigDataViewTableModel.TYPE_RO_UNSETVAL, false);
        arlViewDetails.setTableModel(vFieldsModel);
        arlViewDetails.setTableCellRenderer(Object.class, 
                                new TooltipCellRenderer(new Integer[]{
                                        new Integer(vFieldsModel.NAME)}));
        arlViewDetails.setAddButtonTooltip(GuiResources.getGuiString(
                "config.metadata.namespace.addTooltip"));
        arlViewDetails.setRemoveButtonTooltip(GuiResources.getGuiString(
                "config.metadata.namespace.removeTooltip"));
        // Use multi-line headers
        colModel = arlViewDetails.getTableColumnModel();
        cols = colModel.getColumns();
        while (cols.hasMoreElements()) {
            ((TableColumn)cols.nextElement())
                              .setHeaderRenderer(
                                        new MultiLineTableHeaderRenderer());
        }
    }
    
    private void setUpTableListeners() {
        // Ask to be notified of row selection changes in order to determine
        // which namespace has been selected and to show the corresponding
        // namespace fields
        // Add table model listeners
        viewModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                handleArlViewDetailsTableChanged(e);
            }
        });
        
        // Add table model listeners
        viewModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                handleArlViewSummaryTableChanged(e);
            }
        });
        
        
        ListSelectionModel rowModel = arlViewSummary.getTableSelectionModel();
        // TODO -- can change this to multi-select later once simple case works
        rowModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ArrayList fieldArray = null;
                boolean reservedNS = false;
                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int selectedIdx = lsm.getMinSelectionIndex();
                    FsView selectedView = (FsView)vViews.get(selectedIdx);
                    if (null != selectedView) {
                        loadViewFields(selectedView);  
                    }
                }  
            }
        });
    }
    
    private void loadViewData() throws HostException {
        // clear View Summary ARL
        viewModel.removeAll();
        
        // clear View Details ARL
        vFieldsModel.removeAll();

        // check if at least one field is available in Namespace
        ArrayList fields = new ArrayList();
        rootNS.getFields(fields, true);
        int numFields = fields.size();
        String msg = "";

        if (numFields == 0) {
            msg = GuiResources.getMsgString("info.noFields.noViews");
            JOptionPane.showMessageDialog(MainFrame.getMainFrame(),
                    msg,
                    GuiResources.getGuiString("app.name"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // check if at least one view exists
        FsView[] views = rootNS.getViews();
        if (views.length == 0 || views == null) {
            SwingUtilities.invokeLater(new Runnable(){
                    public void run() {
                        String question = 
                                    GuiResources.getMsgString("info.noViews");
                        String okText = GuiResources.getGuiString("button.ok");
                        String cancelText = 
                                    GuiResources.getGuiString("button.cancel");
                        int retVal = JOptionPane.showOptionDialog(null,
                                question,
                                GuiResources.getGuiString("app.name"),
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null, // Icon
                                new String[] { okText, cancelText },
                                cancelText);
                        if (retVal == JOptionPane.OK_OPTION) {
                            SwingUtilities.invokeLater(new Runnable(){
                                public void run() {
                                 ExplItemConfigMetadataViewSetup setupViewNode =
                                          new ExplItemConfigMetadataViewSetup();
                                 MainFrame mainFrame = MainFrame.getMainFrame();    
                                 mainFrame.selectExplorerItem(setupViewNode);
                                }
                            }); 
                        } else {
                            return;
                        }
                    }
                }); 
            
        }
        
        // build up views vector for easy manipulating
        for (int idx = 0; idx < views.length; idx++) {
            FsView view = (FsView)views[idx];
//            String[] archTypes = view.getArchiveTypes();
//            StringBuffer sb = new StringBuffer();
//            String archivalTypes = null;
//            if (archTypes != null) {
//                for (int i = 0; i < archTypes.length; i++) {
//                    sb.append("#").append(archTypes[i]).append("#");
//                    sb.append(" ");
//                }
//                archivalTypes = sb.toString().trim();
//                archivalTypes = archivalTypes.replaceAll("# #", ", ");
//                archivalTypes = archivalTypes.replaceAll("#", "");
//            }
            viewModel.addRow(new Object[] {
                view.getName(),
                new Boolean(view.isReadOnly()),
                view.getFilename().toString(),
                new Boolean(view.usesExtendedAttrs())
//                archivalTypes
            });
            vViews.add(idx, view);
        }
            
        // gather all of the view field information so the view 
        // fields can be shown when a view is selected
        collectViewFieldsInfo();
    }
    
    private void collectViewFieldsInfo() throws HostException {
        Iterator iter = vViews.iterator();
        while (iter.hasNext()) {
            FsView view = (FsView)iter.next();
            String vName = view.getName();
            // get the view's fields and add to <view, field> hashmap
            ArrayList fieldList = view.getAttributes();    
            allViewFieldMap.put(vName, fieldList);
        } // done iterating through vector of views
    }
    
    // This method is called from the list selection handler when a particular
    // view is selected.  
    private void loadViewFields(FsView selectedView) {
        // clear View Details (i.e. view fields) ARL      
        vFieldsModel.removeAll();
        
        ArrayList vFields = (ArrayList)allViewFieldMap
                                                .get(selectedView.getName());
        if (null != vFields && !vFields.isEmpty()) {
            int numAttributes = vFields.size();
            String cLength = null;
            for (int idx = 0; idx < numAttributes; idx++) {
                FsAttribute attr = (FsAttribute)vFields.get(idx);
                if (attr == null) {
                    continue;
                }
                Field field = attr.getField();
                if (field == null) {
                    continue;
                }
                
                if (field.getLength() <= 0) {
                    cLength = MetadataHelper.NOT_APPLICABLE;
                } else {
                    // length of the column
                    cLength = Integer.toString(field.getLength());
                }
                String strType = MetadataHelper.toTypeWithLength(
                                    new Integer(field.getType()), cLength);
                vFieldsModel.addRow(new Object[] {
                    field.getQualifiedName(),
                    strType,
                    attr.getUnsetString()     
                });
            } // field iteration
        }

             
    }

    // ********************************************
    // HANDLERS
    // ********************************************
    private void handleArlViewSummaryTableChanged(TableModelEvent e) {
        if (e.getType() == e.INSERT) {
            explItem.setIsModified(true);
        }
        int numRows = viewModel.getRowCount();
        if (e.getType() == e.DELETE) {
            if (numRows == 0) {
                arlViewDetails.setTableEmptyText(GuiResources.getGuiString(
                "config.metadata.schema.view.emptyTable"));
            }
        }
    }
    
    private void handleArlViewDetailsTableChanged(TableModelEvent e) {
        if (e.getType() == e.INSERT) {
            explItem.setIsModified(true);
        }
    }
    
    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        return GuiResources.getGuiString("config.metadata.view.title");
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_BOTH; }
    public ButtonPanel getButtonPanel() {
        return BtnPnlBlank.getButtonPanel();
    }
    
    public void loadValues() throws UIException, HostException {

        // Get root namespace
        try {
            rootNS = hostConn.getMetadataConfig();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        
        // populates ALL the view and view field data structures
        // and displays the views in the left-hand add remove
        // list component
        loadViewData();

        // simply need to view the data in this panel so disable all
        // add remove list components
        arlViewSummary.setButtonVisibility(false, 
                                    AddRemoveList.BOTH_BUTTONS);
        arlViewDetails.setButtonVisibility(false, 
                                    AddRemoveList.BOTH_BUTTONS);

        // disable all items if user is in read only 
        if (!ObjectFactory.isAdmin()) {
            Component [] allComponents = this.getComponents();
            for (int i = 0; i < allComponents.length; i++) {
                allComponents[i].setEnabled(false);
            }
        }
    }
    
    public void saveValues() throws UIException, HostException {
        
    }
    
    // *************************************************
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        pnlViews = new javax.swing.JPanel();
        lblNSSummary = new javax.swing.JLabel();
        arlViewDetails = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        arlViewSummary = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        lblNSDetails = new javax.swing.JLabel();

        lblNSSummary.setFont(new java.awt.Font("Dialog", 1, 14));
        lblNSSummary.setText(GuiResources.getGuiString("config.metadata.schema.view.summary"));

        lblNSDetails.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.schema.view.details"));

        org.jdesktop.layout.GroupLayout pnlViewsLayout = new org.jdesktop.layout.GroupLayout(pnlViews);
        pnlViews.setLayout(pnlViewsLayout);
        pnlViewsLayout.setHorizontalGroup(
            pnlViewsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlViewsLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlViewsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblNSSummary)
                    .add(arlViewSummary, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 451, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlViewsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(arlViewDetails, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                    .add(lblNSDetails))
                .add(16, 16, 16))
        );
        pnlViewsLayout.setVerticalGroup(
            pnlViewsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlViewsLayout.createSequentialGroup()
                .add(47, 47, 47)
                .add(pnlViewsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblNSSummary)
                    .add(lblNSDetails))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlViewsLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(arlViewSummary, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE)
                    .add(arlViewDetails, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE)))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlViews, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlViews, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlViewDetails;
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlViewSummary;
    private javax.swing.JLabel lblNSDetails;
    private javax.swing.JLabel lblNSSummary;
    private javax.swing.JPanel pnlViews;
    // End of variables declaration//GEN-END:variables
    
        

}
