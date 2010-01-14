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
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.exploreritems
                                            .ExplItemConfigMetadataSchema;
import com.sun.honeycomb.emd.config.Column;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Table;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.ButtonPanel;
import com.sun.nws.mozart.ui.ContentPanel;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlApplyCancel;
import com.sun.nws.mozart.ui.buttonPanels.BtnPnlBlank;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveList;
import com.sun.nws.mozart.ui.swingextensions.AddRemoveListDialog;
import com.sun.nws.mozart.ui.swingextensions.SizeToFitLayout;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * This class is responsible for rendering the Monitoring --> View Schema and
 * Monitoring --> Set Up Schema panels.  The type of panel is determined by
 * the panel primer data set by the explorer item selected.  The View Schema
 * panel is read-only and displays committed metadata.  The Set Up Schema panel
 * is read/write and the metadata is not committed until the user selects the
 * APPLY button.  The following are some of the business rules for the 
 * Set Up Schema panel:
 *
 *  (1)  A namespace field can be changed from non-queryable to queryable only 
 *       for an extensible namespace.  A field can NOT be changed from queryable
 *       to non-queryable -- this is not supported.
 *  (2)  Only queryable fields are allowed in a table
 *  (3)  A queryable field can be in one table only
 *  (4)  Tables must be defined prior to the namespaces being committed since
 *       all queryable fields that are NOT in user defined tables will have a 
 *       default table created for that single field.
 *  (5)  A namespace may only have children if it is extensible.
 *  (6)  Fields may be added to already committed namespaces ONLY if the 
 *       namespaces are extensible.
 *  (7)  A namespace can be changed from extensible to non-extensible.  A
 *       namespace can NOT be changed from non-extensible to extensible - this 
 *       is not supported.
 *
 * NOTE:  When using the term "committed" in the code, it refers to those
 *        objects (i.e. Field/Namespace) that already exist as part of the
 *        metadata schema.  Objects that are referred to as "new" have yet to
 *        added to the schema on the device while those referred to as 
 *        "modified", already exist as well, but have some attribute that has
 *        potentially changed value.
 *
 *
 * @author  jb127219
 */
public class PnlConfigMetadataSchema extends javax.swing.JPanel
                            implements ContentPanel, AddRemoveListDialog {
    
    // Type of metadata schema panel
    public static final int TYPE_VIEW_PNL = 0;
    public static final int TYPE_SETUP_PNL = 1;
    
    // following members are used to keep track of what changes were committed
    // in the Set Up Schema panel in order to highlight/show the information
    // to the user on the View Schema panel.
    public static boolean showReserved = false;
    public static HashSet lastCommittedNamespaces = new HashSet();
    public static String lastNSSelectedAtCommit = "";
    public static boolean viewAfterSetupCommit = false;
   
    private AdminApi hostConn = ObjectFactory.getHostConnection();
    private ExplorerItem explItem = null;
    private boolean popupCanceled = false;
    private boolean keepRow = false;
    
    // Schema panel type (read = TYPE_VIEW_PNL or read/write = TYPE_SETUP_PNL)
    private int pnlType = -1;
    
    // root namespace and its localized name
    private RootNamespace rootNS = null;
    
    // AddRemoveList table model instances for this panel
    private PnlConfigNSSummaryTableModel nsSummaryModel = null;
    private NamespaceDetailsTableModel nsDetailsModel = null;
    private PnlConfigNameOnlyTableModel tblModel = null;
    private PnlConfigDataTableTableModel tblDetailModel = null;

    /**
     * Map of <namespace grouping, vector of Namespace objects> where the 
     * namespace grouping is an Integer of one of the following three
     * types defined in MetadataHelper:
     *    (1)  NONRESERVED_NS  (2)  RESERVED_NS  (3) ALL_NS 
     */
    private HashMap nsMap = new HashMap();
    /**
     * Map of <namespace name, namespace fields> for reserved namespaces where
     * the namespace name is a String representing the fully qualified name
     * and the namespace fields are an ArrayList of Field objects
     */
    private HashMap rsvdNSFieldMap = new HashMap();
    /**
     * Map of <namespace name, namespace fields> for nonreserved namespaces 
     * where the namespace name is a String representing the fully qualified 
     * name and the namespace fields are an ArrayList of Field objects
     */
    private HashMap nonrsvdNSFieldMap = new HashMap();
    
    // Vector of non-default factory namespace Table objects
    private Vector nsTables = new Vector();

    /**
     * Map of <qualified namespace name, Namespace object> for all NEW 
     * namespaces that have NOT been committed to the Honeycomb system.  
     * The namespace name is a String representing the fully qualified name 
     * and the value is its Namespace object
     */
    private HashMap newNSMap = new HashMap();
    /**
     * Map of <qualified namespace name, Namespace object> for all existing
     * namespaces that have been MODIFIED.  Committed namespaces whose
     * extensible attribute changed from extensible to non-extensible
     * or contains fields whose queryable attribute changed reside in this map.
     * The namespace name is a String representing the fully qualified name 
     * and the value is its Namespace object
     */
    private HashMap modifiedNSMap = new HashMap();
    /**
     * Map of <qualified namespace name, Namespace object> for all COMMITTED 
     * namespaces that HAVE been committed to the Honeycomb system.  
     * The namespace name is a String representing the fully qualified name 
     * and the value is its Namespace object
     */
    private HashMap committedNSMap = new HashMap();
    /**
     * Map of <qualified field name, Field object> for all NEW namespace fields 
     * that have NOT been committed to the Honeycomb system.  The field name
     * is a String representing the fully qualified name and the value is its
     * Field object
     */
    private HashMap newFieldMap = new HashMap();
    /**
     * Map of <field name, Field obj> for all existing fields that have
     * been MODIFIED.  Committed fields whose queryable attribute changed 
     * from non-queryable to queryable reside in this map.  The field name is a 
     * String representing the fully qualified name and the value is its 
     * Field object.
     */
    private HashMap modifiedFieldMap = new HashMap();
    /**
     * Map of <field name, Field obj> for all COMMITTED fields 
     * that are committed to the Honeycomb system.  The field name
     * is a String representing the fully qualified name and the value is its
     * Field object.
     */
    private HashMap committedFieldMap = new HashMap();
    /**
     * Map of <field name, Field obj> for those COMMITTED fields that are
     * non-queryable at the time the Set Up Schema panel is initialized -- this
     * list does not change while the Set Up Schema panel is being used.   
     */
    private HashMap initialNQFieldMap = new HashMap();
    /**
     * Map of <ns name, Namespace obj> for those COMMITTED namespaces that are
     * extensible at the time the Set Up Schema panel is initialized -- this
     * list does not change while the Set Up Schema panel is being used.   
     */
    private HashMap initExtNamespaceMap = new HashMap();
    /**
     * Map of <table name, Table obj> for all tables 
     * that have NOT been committed to the Honeycomb system.
     */
    private HashMap newTableMap = new HashMap();

    /**
     * Creates new form PnlConfigMetadataSchema
     */
    public PnlConfigMetadataSchema() throws HostException, UIException {
        initComponents();
        initComponents2();
    }
    
    private void initComponents2() throws HostException {
        chkBoxTables.setText(GuiResources
                 .getGuiString("config.metadata.schema.table.chkbox.include"));
        chkBoxTables.setMnemonic(GuiResources.getGuiString(
                  "config.metadata.schema.table.chkbox.include.mn").charAt(0));
        setUpAddRemoveLists();
        setUpTableListeners();
    }
    
    // ************************ AddRemoveListDialog Implementation ************
    // Indicates whether or not the popup dialog has been canceled
    public boolean isCanceled() {
        return popupCanceled;
    }
    
    public boolean isRowToBeKept() {
        return keepRow;
    }
    
    public void preRowDelete(AddRemoveList arl) {
        BaseTableModel btModel = arl.getTableModel();
        
        // reset flag
        keepRow = false;
        if (btModel instanceof NamespaceDetailsTableModel) {
            // Namespace fields
            int rowSelected = 
                   arlNSDetails.getTableSelectionModel().getMinSelectionIndex();
            
            if (rowSelected == -1) {
                // No row selected.
                return;
            }
            
            // get the namespace field to be deleted
            String fieldName = (String)nsDetailsModel.getValueAt(
                                            rowSelected, nsDetailsModel.NAME);
            
            if (!committedFieldMap.containsKey(fieldName)) {
                Field field = getNewModifiedField(fieldName);
                if (null != field) {
                    // found Field object in either new or modified field maps
                    if (field.getTableColumn() != null) {
                        // if the field is a member of a table, it can not be 
                        // deleted until the table has been removed
                        Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                                 "config.metadata.schema.ns.tableField.info"));
                        keepRow = true;
                        return;  
                    } else {
                        // field may be removed
                        Namespace ns = field.getNamespace();
                        String nsName = ns.getQualifiedName();
                        // remove the field from the namespace
                        ns.removeField(field.getName());

                        // remove the field from the field hashmap
                        String qualName = field.getQualifiedName();
                        if (newFieldMap.containsKey(qualName)) {
                            newFieldMap.remove(qualName);
                        }
                    }
                }
            } else {
                // field is already in a committed namespace field --
                Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                             "config.metadata.schema.ns.committedField.info"));
                keepRow = true;
                return;     
            }
        } else if (btModel instanceof PnlConfigNSSummaryTableModel) {
            // namespace summary
            int rowSelected = 
                   arlNSSummary.getTableSelectionModel().getMinSelectionIndex();
            if (rowSelected == -1) {
                return;
            }
            String nsName = (String)nsSummaryModel.getValueAt(
                                            rowSelected, nsSummaryModel.NAME);
            if (!committedNSMap.containsKey(nsName)) {
                Namespace n = findNSInMaps(nsName);
                ArrayList nsChildren = new ArrayList();
                if (n != null) {
                    // check to see if namespace has children before removing
                    // it....if it has children, notify user that it won't be 
                    // deleted                
                    n.getChildren(nsChildren, false);
                    if (nsChildren.size() > 0) {
                        // if this namespace has children, then it 
                        // cannot be deleted
                        Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                                 "config.metadata.schema.ns.hasChildren.info"));
                        keepRow = true;
                        return;               
                    }
                    // if the namespace doesn't contain fields that are part of 
                    // a table, then delete it by removing the fields from the 
                    // Namespace object and then make sure the data structures 
                    // no longer reference the namespace and its fields
                    Map fields = n.getFields();
                    List fieldList = new ArrayList(fields.values());
                    Iterator vIter = fieldList.iterator();
                    while (vIter.hasNext()) {
                        Field fVal = (Field)vIter.next();
                        // if this field belongs to a table, the namespace can't
                        // be deleted so notify the user
                        if (fVal.getTableColumn() != null) {
                            Log.logAndDisplayInfoMessage(GuiResources
                               .getGuiString(
                               "config.metadata.schema.ns.tableField.ns.info"));
                            keepRow = true;
                            return;   
                        }
                    }
                    // namespace didn't contain any fields in a table
                    vIter = fieldList.iterator();
                    while (vIter.hasNext()) {
                        Field fVal = (Field)vIter.next();
                        newFieldMap.remove(fVal.getQualifiedName());
                    }
                    String[] keys = new String[fields.size()];
                    fields.keySet().toArray(keys);
                    n.removeFields(keys);
                    n.getParent().unregisterChild(n.getName());
                    // can only delete NEW namespaces -
                    newNSMap.remove(nsName);
                    nsDetailsModel.removeAll();
                } 
            } else {
                // namespace is already committed --
                Log.logAndDisplayInfoMessage(GuiResources.getGuiString(
                             "config.metadata.schema.ns.committedNS.info"));
                keepRow = true;
                return;    
            }
        } else if (btModel instanceof PnlConfigNameOnlyTableModel) {
            // Table summary
            int rowSelected = 
                   arlTables.getTableSelectionModel().getMinSelectionIndex();
            if (rowSelected == -1) {
                return;
            }
            String tableName = (String)tblModel.getValueAt(
                                            rowSelected, tblModel.TBL_NAME);
            ArrayList columns = new ArrayList();
            Table table = (Table)newTableMap.get(tableName);
            if (table != null) {
                table.getColumns(columns);
            }
            if (columns.size() > 0) {
                // need to allow these fields/columns to be used in other 
                // tables now that this table is going to be deleted...set
                // the field's table column to null since this is what we
                // check to ensure the field doesn't already belong to a table
                Iterator cIter = columns.iterator();
                while (cIter.hasNext()) {
                    String fieldName = ((Column)cIter.next()).getFieldName();
                    Field f = getNewModifiedField(fieldName);
                    if (null != f) {
                        f.setTableColumn(null); 
                    }
                }
                           
            }
            // remove the table from the hashmap
            newTableMap.remove(tableName);
            // clear the column/table details table
            tblDetailModel.removeAll();
        }
    }
   
    public void popupModalDialog(AddRemoveList arl) {
        BaseTableModel btModel = arl.getTableModel();
        
        // reset the cancellation flag
        popupCanceled = false;
        if (btModel instanceof PnlConfigNSSummaryTableModel) {
            // Namespace summary
            launchNewNSDialog();
        } else if (btModel instanceof PnlConfigNameOnlyTableModel) {
            // Table summary
            launchNewTableDialog();
        } else if (btModel instanceof NamespaceDetailsTableModel) {
            // Namespace fields
            launchAddFieldsDialog();
        } else {
            // Table columns
            // No pop-up for PnlConfigDataTableTableModel since columns cannot
            // be added/removed from the table once one is instantiated due
            // to automatic indexing of the fields/columns
            return;
        }
    }
    // ************************ AddRemoveListDialog Implementation ************
    
    
    private void commitMetadata() throws HostException {
        lastCommittedNamespaces.clear();
        try {
            Iterator vIter = newNSMap.values().iterator();
            while (vIter.hasNext()) {
                // register the namespaces -- necessary since uncommitted 
                // ones are unregistered after creation 
                Namespace n = (Namespace)vIter.next();
                String pName = n.getParent().getQualifiedName();
                if (pName == null) {
                    // root namespace is the parent
                    rootNS.registerChild(n);
                } else {
                    Namespace parent = 
                              rootNS.resolveNamespace(pName);
                    if (parent != null) {
                        // found the ns
                        parent.registerChild(n);
                    }
                }
                lastCommittedNamespaces.add(n.getQualifiedName());
            }
            Iterator modIter = modifiedNSMap.values().iterator();
            while (modIter.hasNext()) {
                Namespace mns = (Namespace)modIter.next();
                String modNSName = mns.getQualifiedName();
                if (modNSName == null) {
                    modNSName = MetadataHelper.ROOT_NAME;
                }
                lastCommittedNamespaces.add(modNSName);
            }

            // add the new temporary tables to root
            Iterator tblIter = newTableMap.values().iterator();
            while (tblIter.hasNext()) {
                // use the temporary table to populate the information in 
                // the soon-to-be-committed table in root
                Table tmpTable = (Table)tblIter.next();
                
                // list of Columns
                ArrayList columns = new ArrayList();
                tmpTable.getColumns(columns);
                
                // create the table in the root namespace and populate it
                // with the columns from the temporary table
                Table table = rootNS.addTable(tmpTable.getName(), false);
                Iterator cIter = columns.iterator();
                while (cIter.hasNext()) {
                    Column tmpCol = (Column)cIter.next();
                    Field field = tmpCol.getField();
                    // remove any prior assoc. made when field was added to 
                    // temporary table
                    field.setTableColumn(null);
                    table.addColumn(field);
                }
            }
            if (modifiedNSMap.containsKey(MetadataHelper.ROOT_NAME)) {
                showReserved = true;
            } else {
                showReserved = false;
            }
            // saves namespaces and their fields
            hostConn.setMetadataConfig(rootNS);
            Log.logInfoMessage(GuiResources.getGuiString(
                                "config.metadata.schema.saved"));
        } catch (Exception e) {
            // goes to catch all exception handler
            throw new RuntimeException(e);
        }
    }
    
    private void launchNewNSDialog() {
        PnlConfigCreateNewDataNamespace pnlNewNS = null;
        try {           
            pnlNewNS = new PnlConfigCreateNewDataNamespace(
                                                MainFrame.getMainFrame(), 
                                                this, true);
        } catch (UIException ex) {
                Log.logAndDisplayInfoMessage(ex.getMessage());
                return;
        }        
        pnlNewNS.setTitle(GuiResources.getGuiString(
                                    "config.metadata.namespace.dlg.title"));
        pnlNewNS.setVisible(true);
        
        if (pnlNewNS.getUserCanceled()) {
            popupCanceled = true;
            // Need to return the mode back to normal
            return;
        }
       
        // Get newly created Namespace
        Namespace tmpNamespace = pnlNewNS.getNewNamespace();
        if (tmpNamespace == null) {
           return;
        }
        
        // need to unregister the newly created namespace from its parent
        // IFF the parent is an already committed namespace...if this is not
        // done, then the new namespace shows up as committed because its
        // parent is committed - note that the <root> namespace's qualified
        // name is null
        if (tmpNamespace.getParent().getQualifiedName() == null ||
                committedNSMap.containsKey(
                            tmpNamespace.getParent().getQualifiedName())) {
            // unregister it
            tmpNamespace.getParent().unregisterChild(tmpNamespace.getName());
        }
        
        // create a new hashmap containing the new uncommitted fields -- need
        // to iterate through the field map returned and create a new hashmap
        // since the Namespace object's field map keys are just the field names
        // and not the QUALIFIED field names
        Map tmpFields = tmpNamespace.getFields();
        Collection fields = tmpFields.values();
        Iterator tmpIter = fields.iterator();
        while (tmpIter.hasNext()) {
            Field f = (Field)tmpIter.next();
            newFieldMap.put(f.getQualifiedName(), f);
        }
        // Add the new Namespace to the new namespace map to keep track 
        // of all new namespaces created before commit
        String tmpNSName = tmpNamespace.getQualifiedName();
        newNSMap.put(tmpNSName, tmpNamespace);
        lastNSSelectedAtCommit = tmpNSName;
 
        // check to see if namespace is already in the model -- if so
        // then do not add it again
        String tmpParent = tmpNamespace.getParent().getName();
        if (!nsSummaryModel.getDataVector().contains(tmpNSName)) {
            nsSummaryModel.addRow(new Object[] {
                tmpNSName, (tmpParent == null) ?
                    MetadataHelper.ROOT_NAME : tmpParent,
                new Boolean(tmpNamespace.isWritable()),
                new Boolean(tmpNamespace.isExtensible())
            });
        }
        
        nsSummaryModel.setReserved((Vector)nsMap.get(
                            new Integer(MetadataHelper.RESERVED_NS)));

        pnlTables.setVisible(true); 
    }
    
    private void launchAddFieldsDialog() {
        PnlConfigDataNamespaceAddFieldsDlg pnlAddFields = 
                                    new PnlConfigDataNamespaceAddFieldsDlg(
                                                MainFrame.getMainFrame(), 
                                                this, true);
            
        pnlAddFields.setTitle(GuiResources.getGuiString(
                "config.metadata.namespace.addFieldsDlg.title"));
       
        AddRemoveList arlFields = pnlAddFields.getFieldsTableComponent();
        pnlAddFields.setVisible(true);
        
        if (pnlAddFields.getDidUserCancel()) {
            popupCanceled = true;
            // Need to return the mode back to normal
            return;
        }
        
        // Get currently selected namespace in the summary table
        int nsRowSelected =
                arlNSSummary.getTableSelectionModel().getMinSelectionIndex();
        if (nsRowSelected == -1) {
            // No row selected.
            popupCanceled = true;
            return;
        }
        String nsName = (String)nsSummaryModel.getValueAt(nsRowSelected,
                nsSummaryModel.NAME);
        if (nsName == null) {
            popupCanceled = true;
            return;
        }

        Namespace nsSelected = findNSInMaps(nsName);
        if (null != nsSelected) {
            // new fields that the user wants to add to existing namespace
            PnlConfigDataNamespaceTableModel fieldsModel =
                    (PnlConfigDataNamespaceTableModel)arlFields.getTableModel();
            Vector newFieldRows = new Vector();
            newFieldRows = (Vector)fieldsModel.getDataVector().clone();
            Vector newFieldNames = new Vector();
            for (int i = 0; i < newFieldRows.size(); i++) {
                newFieldNames.add(i, (String)((Vector)newFieldRows.elementAt(i))
                                                 .elementAt(fieldsModel.NAME));
            }
            
            // fields belonging to the existing namespace
            ArrayList nsFields = new ArrayList();
            nsSelected.getFields(nsFields, false);
            
            // use the fieldsModel, which contains the new fields the user 
            // wants to add, and add the existing namespace fields to it to 
            // create a list of unique fields -- save off the existing field
            // names into a List for comparison purposes
            List nsFieldNames = new ArrayList(nsFields.size());
            Iterator nsFieldIter = nsFields.iterator();
            while (nsFieldIter.hasNext()) {
                Field f = (Field)nsFieldIter.next();
                fieldsModel.addRow(new Object[] {
                        f.getName(),
                        MetadataHelper.dataTypeIntToString
                                        .get(new Integer(f.getType())),
                        Integer.valueOf(new Integer(f.getLength()).toString()),
                        Boolean.valueOf(f.isQueryable())
                    });
                nsFieldNames.add(f.getName());
            }

            // now pass all of the data (fields the user wants to add plus
            // the fields that already are part of the namespace) to get back
            // a mapping of the row# to the field name for unique fields
            // (i.e. no duplicates)
            Map uniqueFields = MetadataHelper
                        .getUniqueFields(fieldsModel, fieldsModel.NAME);
            String qualNSName = nsSelected.getQualifiedName();
            if (qualNSName == null) {
                qualNSName = MetadataHelper.ROOT_NAME;
            }
             
            // determine if whatever the iter points to is part of the
            // nsFields list....if it IS part of the list, then it 
            // doesn't need to be added...if it is NOT part of the list,
            // this it is new and should be added
            Iterator iter = uniqueFields.values().iterator();
            while (iter.hasNext()) {
                String fsName = (String)iter.next();
                
                // check to see if the field already exists in the namespace
                // and if it does NOT AND it matches the new fields entered
                // by the user, then add a new field to the namespace
                if (!nsFieldNames.contains(fsName)) {
                    // find the new field's index in the table model
                    int idx = newFieldNames.indexOf(fsName);
                    if (idx >= 0) {
                        String name = 
                                (String)((Vector)newFieldRows.elementAt(idx))
                                               .elementAt(fieldsModel.NAME);

                        // create a new Field object if it is NOT already
                        // included as a namespace field
                        Integer type = 
                                (Integer)MetadataHelper.dataTypeStringToInt
                                    .get((String)((Vector)newFieldRows
                                       .elementAt(idx))
                                         .elementAt(fieldsModel.DATA_TYPE));
                        Object lenObj = 
                                (Object)((Vector)newFieldRows.elementAt(idx))
                                             .elementAt(fieldsModel.LENGTH);
                        String strLength = 
                                        MetadataHelper.objToString(lenObj);
                        Integer len = null;
                        if ((strLength.trim()).equalsIgnoreCase(
                                          MetadataHelper.NOT_APPLICABLE)) {
                            len = Integer.valueOf("0");
                        } else {
                            len = Integer.valueOf(strLength);
                        }
                        String typeLength = MetadataHelper
                                         .toTypeWithLength(type, strLength);

                        Boolean isQueryable = (Boolean)fieldsModel
                                   .getValueAt(idx, fieldsModel.IS_QUERYABLE);

                        Field field = new Field(nsSelected,
                                                name,
                                                type.intValue(),
                                                len.intValue(),
                                                isQueryable.booleanValue(),
                                                false);

                        // add the field to the namespace
                        nsSelected.addField(field);

                        // add the field to the model
                        nsDetailsModel.addRow(new Object[] {
                            field.getQualifiedName(),
                            typeLength,
                            isQueryable
                        });

                        // need to add the field to the new field map
                        newFieldMap.put(field.getQualifiedName(), field);
                    }
                }
    
            } // end of iterating through all unique fields
            
            if (isReservedNS(nsName)) {
                // notify the table model of which namespace fields are 
                // reserved for rendering purposes 
                ArrayList allFields = new ArrayList();
                nsSelected.getFields(allFields, false);
                nsDetailsModel.setReserved(new Vector(allFields));
            }
            
            // potentially adding a field that may be queryable and the user can
            // create a new table using newly added queryable fields
            pnlTables.setVisible(true);
            
            // If the namespace has already been committed AND fields are 
            // added to it, it needs to be added to the modifiedNSMap
            addModifiedNS(nsSelected);
        } // check to see if selected namespace is null
    }
    
    private void launchNewTableDialog() {
        PnlConfigDataTableAddDlg pnlNewTable = null;
        try {
            pnlNewTable = new PnlConfigDataTableAddDlg(
                                   MainFrame.getMainFrame(), this, true);
        } catch (UIException ex) {
            Log.logAndDisplayInfoMessage(ex.getMessage());
            return;
        }
        pnlNewTable.setTitle(GuiResources.getGuiString(
                "config.metadata.tables.dlg.title"));
        pnlNewTable.setVisible(true);
        
        if (pnlNewTable.userCanceled()) {
            popupCanceled = true;
            return;
        }
        
        Table newTable = pnlNewTable.getNewTable();
        if (newTable == null) {
            popupCanceled = true;
            return;
        }
        
        String tableName = newTable.getName();
        if (tableName == null) {
            Log.trace(Level.SEVERE, "Table not found.");
        }
        
        // add the table to the model
        tblModel.addRow(new Object[] {newTable.getName()});
        newTableMap.put(newTable.getName(), newTable);
    }
    
    private void clearAllData() {
        
        explItem.setIsModified(false);
        
        // clear panel data and disable the apply and cancel buttons
        rootNS = null;
        
        // clear add remove list table models
        nsSummaryModel.removeAll();
        nsDetailsModel.removeAll();
        tblModel.removeAll();
        tblDetailModel.removeAll();

        // namespaces
        newNSMap.clear();
        modifiedNSMap.clear();
        committedNSMap.clear();
        // fields
        rsvdNSFieldMap.clear();
        nonrsvdNSFieldMap.clear();
        newFieldMap.clear();
        modifiedFieldMap.clear();
        committedFieldMap.clear();
        initialNQFieldMap.clear();
        initExtNamespaceMap.clear();
        // tables
        nsTables.clear();
        newTableMap.clear();
    }
    
    private void getRootNS() throws HostException {
        // Get root namespace
        try {
            rootNS = hostConn.getMetadataConfig();
        } catch (Exception e) {
            // goes to catch all exception handler
            throw new RuntimeException(e);
        } 
    }
    
    // code builds up lists of namespaces (reserved, nonreserved, all)
    public void loadNSDataMap() throws HostException {
        // Load child namespaces
        Vector allNS = new Vector();
        Vector reservedNS = new Vector();
        Vector nonreservedNS = new Vector();
        // list of hard-coded reserved namespaces -- until the API's are given
        // to the GUI to retrieve the user and factory schema separately, these
        // values will be hard-coded
        Set reservedNamespaces = hostConn.getReservedNamespaceNames();
        
        // get the root namespace's fields and its child namespaces
        ArrayList childList = new ArrayList(allNS);
        rootNS.getChildren(childList, true);
        allNS.add(rootNS);
        reservedNS.add(rootNS);

        // iterate through the root's child namespaces 
        Iterator iter = childList.iterator();
        while (iter.hasNext()) {
            Namespace namespace = (Namespace)iter.next();
            allNS.add(namespace);
            boolean isReservedNamespace = reservedNamespaces.contains(
                    namespace.getQualifiedName());
            if (isReservedNamespace) {
                reservedNS.add(namespace);
            } else {
                nonreservedNS.add(namespace);
            }
            if (namespace.isExtensible()) {
                // <root> namespace is excluded from the map
                initExtNamespaceMap
                            .put(namespace.getQualifiedName(), namespace);
            }
        }
        nsMap.put(new Integer(MetadataHelper.NONRESERVED_NS), nonreservedNS);
        nsMap.put(new Integer(MetadataHelper.RESERVED_NS), reservedNS);
        nsMap.put(new Integer(MetadataHelper.ALL_NS), allNS);
     
    }

    private void setUpAddRemoveLists() {
        
        // Namespace Summary Add Remove List
        nsSummaryModel = new PnlConfigNSSummaryTableModel(false);
        arlNSSummary.setTableModel(nsSummaryModel);
        arlNSSummary.setDialog(this);
        arlNSSummary.setNewRowsReadOnly(true);
        arlNSSummary.setTableCellRenderer(Object.class, 
                      new ReservedNamespaceCellRenderer(
                        new Integer[]{new Integer(nsSummaryModel.NAME), 
                                    new Integer(nsSummaryModel.PARENT)}));
        arlNSSummary.getTableColumnModel().
                    getColumn(PnlConfigNSSummaryTableModel.IS_EXTENSIBLE).
                        setCellRenderer(new ReadOnlyCheckboxRenderer());
        arlNSSummary.setAddButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.ns.addTooltip"));
        arlNSSummary.setRemoveButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.ns.removeTooltip"));
        
        // Namespace Fields Add Remove List
        nsDetailsModel = new NamespaceDetailsTableModel(false);
        arlNSDetails.setTableModel(nsDetailsModel);
        arlNSDetails.setDialog(this);
        arlNSDetails.setTableCellRenderer(Object.class, 
                       new ReservedNamespaceCellRenderer(
                            new Integer[]{new Integer(nsDetailsModel.NAME)}));
        arlNSDetails.getTableColumnModel().
                    getColumn(NamespaceDetailsTableModel.IS_QUERYABLE).
                        setCellRenderer(new ReadOnlyCheckboxRenderer());
        arlNSDetails.setAddButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.field.addTooltip"));
        arlNSDetails.setRemoveButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.field.removeTooltip"));
        
        // Table Add Remove List
        tblModel = new PnlConfigNameOnlyTableModel(false);
        arlTables.setTableModel(tblModel);
        arlTables.setDialog(this);
        arlTables.setTableCellRenderer(Object.class, 
                       new ReservedNamespaceCellRenderer(
                            new Integer[]{new Integer(tblModel.TBL_NAME)}, 
                                false));
        arlTables.setAddButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.table.addTooltip"));
        arlTables.setRemoveButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.table.removeTooltip"));
        
        // Table Details Add Remove List
        tblDetailModel = new PnlConfigDataTableTableModel(false);
        arlTableDetails.setTableModel(tblDetailModel);
        arlTableDetails.setDialog(this);
        arlTableDetails.setTableCellRenderer(Object.class, 
                       new ReservedNamespaceCellRenderer(
                            new Integer[]{new Integer(tblDetailModel.NAME)},
                                false));
        arlTableDetails.setAddButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.column.addTooltip"));
        arlTableDetails.setRemoveButtonTooltip(GuiResources.getGuiString(
                "config.metadata.schema.column.removeTooltip"));
   
    }
    
    private void disableChkboxesIfNotEditable(boolean readOnlyCheck) {
        ReadOnlyCheckboxRenderer rocr = 
               (ReadOnlyCheckboxRenderer)arlNSDetails.getTableColumnModel().
                    getColumn(NamespaceDetailsTableModel.IS_QUERYABLE).
                    getCellRenderer();
        rocr.setReadOnlyCheck(readOnlyCheck);
        rocr = (ReadOnlyCheckboxRenderer)arlNSSummary.getTableColumnModel().
                    getColumn(PnlConfigNSSummaryTableModel.IS_EXTENSIBLE).
                    getCellRenderer();
        rocr.setReadOnlyCheck(readOnlyCheck);
    }
    
    private void setUpTableListeners() {
    
        // Ask to be notified of row selection changes in order to determine
        // which namespace has been selected and to show the corresponding
        // namespace fields
        nsSummaryModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                handleArlNSSummaryTableChanged(e);
            }
        });
        
        
        // Add table model listeners
        nsDetailsModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                handleArlNSDetailsTableChanged(e);
            }
        });
        
        tblModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                handleArlTableSummaryTableChanged(e);
            }
        });
         
        ListSelectionModel rowModel = arlNSSummary.getTableSelectionModel();
        // TODO -- can change this to multi-select later once simple case works
        rowModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ArrayList fieldArray = new ArrayList();
                boolean reservedNS = false;
                nsDetailsModel.clearReservedFlags();
                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int currentRow = lsm.getMinSelectionIndex();
                    String nsName = (String)nsSummaryModel.getValueAt(
                                            currentRow, nsSummaryModel.NAME);
                    Namespace ns = null;
                    if (nsName.equals(MetadataHelper.ROOT_NAME)) {
                        reservedNS = true;
                        // able to add fields to the root namespace
                        arlNSDetails.setEnabled(true);
                        rootNS.getFields(fieldArray, false);
                    } else if (newNSMap.containsKey(nsName)) {
                        ns = (Namespace)newNSMap.get(nsName);
                        if (ns != null)
                            ns.getFields(fieldArray, false);
                    } else {
                        ns = (Namespace)committedNSMap.get(nsName);
                        if (ns != null) {
                            ns.getFields(fieldArray, false);
                            if (isReservedNS(nsName)) {
                                reservedNS = true;
                            }
                        }
                    }

                    // populate the data model with the field info    
                    if (null != fieldArray && !fieldArray.isEmpty()) {
                        loadNSFields(fieldArray, reservedNS);
                    } else {
                        // the namespace does not have any assoc. fields
                        nsDetailsModel.removeAll();
                        if (null != ns) {
                            // can't add fields if namespace is non-extensible
                            // and already exists as part of the device's schema
                            if (!ns.isExtensible() && 
                                    committedNSMap.containsKey(
                                            ns.getQualifiedName())) {
                                arlNSDetails.setEnabled(false);
                            } else {
                                // if the namespace is extensible 
                                // (committed/non-committed) OR non-extensible
                                // & NOT committed, then fields may be added
                                arlNSDetails.setEnabled(true);
                            }
                        }
                        arlNSDetails.setTableEmptyText(GuiResources
                           .getGuiString("config.metadata.schema.ns.noFields"));
                    }
                }  
            }
        });
       
        rowModel = arlTables.getTableSelectionModel();
        // TODO -- can change this to multi-select later once simple case works
        rowModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                tblDetailModel.removeAll();
                if (!lsm.isSelectionEmpty()) {
                    int selectedIdx = lsm.getMinSelectionIndex();
                    String tableName = (String)tblModel.getValueAt(
                                            selectedIdx, tblModel.TBL_NAME);
                    try {
                        loadTableFields(tableName);
                    } catch (HostException ex) {
                        Log.logAndDisplayInfoMessage(ex.getMessage());
                        return;
                    }
                }  
            }
        });
        
        
    }
    
    private void loadNSData(int nsType) throws HostException {
        // clear Namespace Summary ARL
        nsSummaryModel.removeAll();
        
        // clear Namespace Details ARL
        nsDetailsModel.removeAll();
         
        if (!nsMap.isEmpty()) {
            Vector vNS = (Vector)nsMap.get(new Integer(nsType));
            Iterator iter = vNS.iterator();
            while (iter.hasNext()) {
                Namespace ns = (Namespace)iter.next();
                String nsName = ns.getQualifiedName();
                Namespace parent = ns.getParent();
                String parentName = null;
                
                // Put in an empty string for the parent of root
                if ((parent == null) && (nsName == null)) {
                    parentName = "";
                    nsName = MetadataHelper.ROOT_NAME;
                } else {
                    parentName = parent.getName();
                }

                nsSummaryModel.addRow(new Object[] {
                    nsName, (parentName == null) ? 
                                MetadataHelper.ROOT_NAME : parentName,
                    new Boolean(ns.isWritable()),
                    new Boolean(ns.isExtensible())
                }); 
                committedNSMap.put(nsName, ns);
            }
            
            // gather all of the namespace field information so the namespace 
            // fields can be shown when a namespace is selected
            collectNSFieldsInfo(MetadataHelper.ALL_NS);

            nsSummaryModel.setReserved((Vector)nsMap.get(
                            new Integer(MetadataHelper.RESERVED_NS)));
        }
    }
    
    private void collectNSFieldsInfo(int nsType) throws HostException {
        if (!nsMap.isEmpty()) {
            if (nsType == MetadataHelper.ALL_NS) {
                collectNSFieldsInfo(MetadataHelper.NONRESERVED_NS);
                collectNSFieldsInfo(MetadataHelper.RESERVED_NS);
                // Populate the map of committed fields
                List fields = new ArrayList();
                fields.addAll(rsvdNSFieldMap.values());
                fields.addAll(nonrsvdNSFieldMap.values());
                Iterator fIter = fields.iterator();
                while (fIter.hasNext()) {
                    List subList = (ArrayList)fIter.next();
                    Iterator slIter = subList.iterator();
                    while (slIter.hasNext()) {
                        Field f = (Field)slIter.next();
                        if (!f.isQueryable()) {
                            initialNQFieldMap.put(f.getQualifiedName(), f);
                        }
                        committedFieldMap.put(f.getQualifiedName(), f);
                    }
                }
            } else {
                Vector vNS = (Vector)nsMap.get(new Integer(nsType));
                Iterator iter = vNS.iterator();
                while (iter.hasNext()) {
                    Namespace ns = (Namespace)iter.next();
                    String nsName = ns.getQualifiedName();
                    // get the namespace's fields and add to <ns, field> hashmap
                    ArrayList fieldList = new ArrayList();
                    ns.getFields(fieldList, false);
                    switch (nsType) {
                        case (MetadataHelper.RESERVED_NS):
                           if (nsName == null) {
                               nsName = MetadataHelper.ROOT_NAME;
                           }
                           rsvdNSFieldMap.put(nsName, fieldList); 
                           break;
                        case (MetadataHelper.NONRESERVED_NS):
                           nonrsvdNSFieldMap.put(nsName, fieldList); 
                           break;
                        default:
                           // log something here..should never reach this ??
                           break;
                    };
                } // done iterating through vector of namespaces
            }
        } // check if hashmap is empty
    }
    
    // This method is called from the list selection handler when a particular
    // namespace is selected.  The reserved flag is true if the namespace
    // selected is a system/reserved namespace and false if defined by the user.
    private void loadNSFields(ArrayList nsFields, boolean reserved) {
        // clear Namespace Details (i.e. namespace fields) ARL      
        nsDetailsModel.removeAll();
        
        if (null != nsFields && !nsFields.isEmpty()) {
            String length = null;
            Iterator fIter = nsFields.iterator();
            while (fIter.hasNext()) {
                Field field = (Field)fIter.next();
                if (field.getLength() <= 0) {
                    length = MetadataHelper.NOT_APPLICABLE;
                } else {
                    // length of the column
                    length = Integer.toString(field.getLength());
                }
                String typeLength = MetadataHelper.toTypeWithLength(
                                        new Integer(field.getType()), length); 
                
                nsDetailsModel.addRow(new Object[] {
                    field.getQualifiedName(),
                    typeLength,
                    new Boolean(field.isQueryable())
                });   
            }

            if (reserved) {
                // notify the table model of which namespace fields are 
                // reserved for rendering purposes 
                nsDetailsModel.setReserved(new Vector(nsFields));
            }
        }
    }
    
    private void populateSummaryModel(Collection namespaces) {
        Iterator iter = namespaces.iterator();
        while (iter.hasNext()) {
            Namespace ns = (Namespace)iter.next();
            String nsName = ns.getQualifiedName();
            Namespace parent = ns.getParent();
            String parentName = null;
            // Put in an empty string for the parent of root
            if ((parent == null) && (nsName == null)) {
                parentName = "";
                nsName = MetadataHelper.ROOT_NAME;
            } else {
                parentName = parent.getName();
            }

            nsSummaryModel.addRow(new Object[] {
                nsName, (parentName == null) ? 
                            MetadataHelper.ROOT_NAME : parentName,
                new Boolean(ns.isWritable()),
                new Boolean(ns.isExtensible())
            });  
        }
        nsSummaryModel.setReserved((Vector)nsMap.get(
                            new Integer(MetadataHelper.RESERVED_NS)));
    }
    
    // *************  Accessors for popup dialogs ***********************
    // this method is used when a committed field's queryable state is changed
    // and the original state of the queryable attribute was non-queryable
    public void addModifiedField(Field modField) {
        // potentially adding a field that may be queryable and the user can
        // create a new table using newly added queryable fields
        pnlTables.setVisible(true);
        
        if (committedFieldMap.containsKey(modField.getQualifiedName())) {
            modifiedFieldMap.put(modField.getQualifiedName(), modField);
       
            // need to add it's namespace to the modifiedNSMap 
            Namespace ns = modField.getNamespace();
            addModifiedNS(ns);
        }
    }
    
    // this method is used when a committed namespace changes (e.g. queryable
    // or extensible attributes change or fields are added to the namespace)
    public void addModifiedNS(Namespace modNS) {
        String nsName = null;
        if (modNS.getName() == null) {
            nsName = MetadataHelper.ROOT_NAME;
        } else {
            nsName = modNS.getQualifiedName();
        }
        // need to add namespace to the modifiedNSMap 
        if (committedNSMap.containsKey(nsName)) {
             modifiedNSMap.put(nsName, modNS);
             lastNSSelectedAtCommit = nsName;
        }
    }

    // returns all fields that could be included in a new table
    public List getFields() {
        ArrayList list = new ArrayList();
        list.addAll(newFieldMap.values());
        list.addAll(modifiedFieldMap.values());
        return list;
    }
    // returns the field object if the field was initially (i.e. before any 
    // modifications made in the Set Up Schema panel) non-queryable
    public Field getNQInitField(String name) {
        Field f = null;
        if (initialNQFieldMap.containsKey(name)) {
            f = (Field)initialNQFieldMap.get(name);
        } 
        return f;
    }
    // returns the namespace object if the namespace was initially (i.e. before
    // any modifications made in the Set Up Schema panel) extensible
    public Namespace getInitExtensibleNS(String name) {
        Namespace ns = null;
        if (initExtNamespaceMap.containsKey(name)) {
            ns = (Namespace)initExtNamespaceMap.get(name);
        } 
        return ns;
    }
   
    // returns all fields that could be included in a new table for the 
    // given namespace name
    public List getFieldsForNS(String namespaceName) {
        if ((null == namespaceName) || (namespaceName.length() == 0) ||
                !(newNSMap.containsKey(namespaceName) || 
                modifiedNSMap.containsKey(namespaceName))) {
            return null;
        }
        ArrayList list = new ArrayList();
        Namespace newNS = (Namespace)newNSMap.get(namespaceName);
        if (newNS != null) {
            list.addAll(newNS.getFields().values());
        }
        Namespace modNS = (Namespace)modifiedNSMap.get(namespaceName);
        if (modNS != null) {
            list.addAll(modNS.getFields().values());
        }
        return list;
    }
    

    public List getNewNS() {
        ArrayList list = new ArrayList();
        list.addAll(newNSMap.values());
        return list;
    }
    public List getModifiedNS() {
        ArrayList list = new ArrayList();
        list.addAll(modifiedNSMap.values());
        return list;
    }
    public List getCommittedNS() {
        ArrayList list = new ArrayList();
        list.addAll(committedNSMap.values());
        return list;
    }
    public Namespace getNewNS(String name) {
        Namespace ns = null;
        if (newNSMap.containsKey(name)) {
            ns = (Namespace)newNSMap.get(name);
        }
        return ns;
    }
    public Namespace getModifiedNS(String name) {
        Namespace ns = null;
        if (modifiedNSMap.containsKey(name)) {
            ns = (Namespace)modifiedNSMap.get(name);
        }
        return ns;
    }
    public Namespace getCommittedNS(String name) {
        Namespace ns = null;
        if (committedNSMap.containsKey(name)) {
            ns = (Namespace)committedNSMap.get(name);
        }
        return ns;
    }
    
    public Field getCommittedField(String name) {
        Field f = null;
        if (committedFieldMap.containsKey(name)) {
            f = (Field)committedFieldMap.get(name);
        } 
        return f;
    }
    
    public Field getModifiedField(String name) {
        Field f = null;
        if (modifiedFieldMap.containsKey(name)) {
            f = (Field)modifiedFieldMap.get(name);
        } 
        return f;
    }
    
    public Field getNewField(String name) {
        Field f = null;
        if (newFieldMap.containsKey(name)) {
            f = (Field)newFieldMap.get(name);
        } 
        return f;
    }
    
    public List getTables() {
        ArrayList list = new ArrayList();
        list.addAll(getExistingTables(true));
        list.addAll(newTableMap.values());
        return list;
    }
    
    public int getPanelType() {
        return pnlType;
    }
    
    public boolean isReservedNS(String qualNSName) {
        boolean reserved = false;
        if (rsvdNSFieldMap.containsKey(qualNSName) ||
                        (qualNSName.equals(MetadataHelper.ROOT_NAME))) {
            reserved = true;
        }
        return reserved;
    }
   
    // *************  Accessors for popup dialogs and table models *************
    // Searches both committed and uncommitted namespaces -- ends up searching
    // all types of namespaces (new, modified, committed) since modified 
    // namespaces are also included as part of the committed namespace map
    // Returns null if the namespace is not found.
    public Namespace findNSInMaps(String nsName) {
        Namespace ns = null;
        if (newNSMap.containsKey(nsName)) {
            ns = (Namespace)newNSMap.get(nsName);
        } else if (committedNSMap.containsKey(nsName)) {
            ns = (Namespace)committedNSMap.get(nsName);
        }
        return ns;
    }
    // Searches both committed and uncommitted fields -- ends up searching
    // all types of fields (new, modified, committed) since modified 
    // fields are also included as part of the committed field map
    // Returns null if the field is not found.
    public Field findFieldInMaps(String name) {
        Field f = null;
        if (newFieldMap.containsKey(name)) {
            f = (Field)newFieldMap.get(name);
        } else {
            f = (Field)committedFieldMap.get(name);
        }
        return f;
    }
    // only searches new and modified fields -- returns null if not found
    public Field getNewModifiedField(String fieldName) {
        Field f = null;
        if (modifiedFieldMap.containsKey(fieldName)) {
            f = (Field)modifiedFieldMap.get(fieldName);
        } else if (newFieldMap.containsKey(fieldName)) {
            f = (Field)newFieldMap.get(fieldName);
        } 
        return f;
    }

    public List getExistingTables(boolean includeAutogenerated) {
        nsTables.clear();
        Table[] tblArray = rootNS.getTables();
        
        int totalTables = tblArray.length;
        for (int idx = 0; idx < totalTables; idx++) {
            Table t = (Table)tblArray[idx];
            if (!includeAutogenerated && !t.isAutoGenerated()) {
                // only show user defined tables
                nsTables.add(t);
            } else if (includeAutogenerated) {
                nsTables.add(t); 
            }
        }
        return nsTables;
    }
    /**
     * This method is called to load the table names from root and populates
     * the lower-left-most add remove list component
     */
    private void loadTableData(boolean autogenerated) 
                                        throws UIException, HostException {
        // clear Table Summary ARL
        tblModel.removeAll();        
        // clear Table Column ARL
        tblDetailModel.removeAll();
       
        getExistingTables(autogenerated);
        for (int idx = 0; idx < nsTables.size(); idx++) {
            Table table = (Table)nsTables.get(idx);
            tblModel.addRow(new Object[] {table.getName()});   
        }
   
    }
 
    /**
     * This is called when user selects table name from the add remove list
     * component, which populates corresponding columns as fields in the
     * lower-most-right add remove list component
     */
    private void loadTableFields(String tableName) throws HostException  {   
        
        // get the Table instance
        Table tabl = null;
        if (pnlType == TYPE_SETUP_PNL) {
            Iterator tblIter = newTableMap.values().iterator();
            while (tblIter.hasNext()) {
                tabl = (Table)tblIter.next();
                if (tableName.equals(tabl.getName())) {
                    break;
                }
            }
        } else {
            tabl = rootNS.getTable(tableName);
        }
       
        if (tabl != null) {
            // get all of the columns for the given namespace table
            ArrayList columnsList = new ArrayList();
            tabl.getColumns(columnsList);
            
            if (columnsList != null) {
                tblDetailModel.removeAll();
                String length = null;
                for (int i = 0; i < columnsList.size(); i++) {
                    
                    Column col = (Column)columnsList.get(i);
                    if (col == null) {
                        Log.logInfoMessage("column NULL");
                        continue;
                    }
                    
                    Field field = col.getField();
                    if (field == null) {
                        Log.logInfoMessage("Field NULL");
                        continue;
                    }
                    if (field.getLength() <= 0) {
                        length = MetadataHelper.NOT_APPLICABLE;
                    } else {
                        // length of the column
                        length = Integer.toString(field.getLength());
                    }
                    String strType = MetadataHelper.toTypeWithLength(
                            new Integer(field.getType()), length);
                    tblDetailModel.addRow(new Object[] {
                        field.getQualifiedName(),
                        strType
                    });
                    
                }
            }
        }
        
    }
    
    private boolean areSchemaChangesPending() {
        return (!newNSMap.isEmpty() || !newFieldMap.isEmpty() ||
                !newTableMap.isEmpty() || !modifiedNSMap.isEmpty() ||
                                !modifiedFieldMap.isEmpty());
    }

    // ********************************************
    // Start of HANDLERS
    // ********************************************
    private void handleArlNSSummaryTableChanged(TableModelEvent e) {
        // number of rows in namespace summary table
        int numRows = nsSummaryModel.getRowCount();
        explItem.setIsModified(areSchemaChangesPending());
        if (e.getType() == e.INSERT) {
            if (numRows == 1) {
                // first namespace so enable the namespace fields table
                arlNSDetails.setTableEmptyText(GuiResources.getGuiString(
                    "config.metadata.schema.ns.emptyTable"));
            }
        }
        
        if (e.getType() == e.DELETE) {
            if (numRows == 0) {
                // deleting last namespace so disable the namespace fields table
                arlNSDetails.setEnabled(false);
                arlNSDetails.setTableEmptyText("");
            }
        }

    }
    
    private void handleArlNSDetailsTableChanged(TableModelEvent e) {
        explItem.setIsModified(areSchemaChangesPending());
        if (e.getType() == e.INSERT) {
  
            // Get currently selected namespace in the summary table 
            int nsRowSelected = arlNSSummary.getTableSelectionModel()
                                                .getMinSelectionIndex();
            String nsName = (String)nsSummaryModel
                                        .getValueAt(nsRowSelected, 
                                                nsSummaryModel.NAME);
            
            Namespace ns = findNSInMaps(nsName);
            if (null != ns) {
                // if the namespace is non-extensible AND already committed,
                // then the user cannot add fields to it but the user may
                // alter the Field's queryable attribute value
                if (!ns.isExtensible() && 
                        committedNSMap.containsKey(ns.getQualifiedName())) {
                    arlNSDetails.setEnabled(false);
                } else {
                    // if the namespace is extensible (committed/non-committed)
                    // OR non-extensible and NOT committed, then fields may
                    // be added to it
                    arlNSDetails.setEnabled(true);
                }
            }
        }
    }
    
    private void handleArlTableSummaryTableChanged(TableModelEvent e) {
        // number of rows in the summary of Tables
        int numRows = tblModel.getRowCount();
        explItem.setIsModified(areSchemaChangesPending());
        if (e.getType() == e.INSERT) {
            if (numRows == 1) {
                // first table so enable the table columns table
                // arlTableDetails.setEnabled(true);
                arlTableDetails.setTableEmptyText(GuiResources.getGuiString(
                                "config.metadata.schema.column.emptyTable"));
            }
        }
        
        if (e.getType() == e.DELETE) {
            if (numRows == 0) {
                // deleting last table so disable the table columns table
                arlTableDetails.setTableEmptyText("");
            }
        }
    }
    
    private void handleViewSelectNamespaceCheckBox(ActionEvent ae) {
        int typeOfNamespaces = -1;
        if (chkBoxNamespaces.isSelected()) {
            typeOfNamespaces = MetadataHelper.ALL_NS;
        } else {
            typeOfNamespaces = MetadataHelper.NONRESERVED_NS;
        }
        try {
            loadNSData(typeOfNamespaces);
            loadTableData(chkBoxTables.isSelected());
        } catch (HostException ex) {
            Log.logAndDisplayInfoMessage(ex.getMessage());
            return;
        } catch (UIException ue) {
            Log.logAndDisplayInfoMessage(ue.getMessage());
            return;
        }
    }
    
    private void handleSetupSelectNamespaceCheckBox(ActionEvent ae) {
        // clear Namespace Summary ARL
        nsSummaryModel.removeAll();
        
        // clear Namespace Details ARL
        nsDetailsModel.removeAll();
        if (chkBoxNamespaces.isSelected()) {
            // show only those namespaces that are new and/or modified 
            populateSummaryModel(newNSMap.values());
            populateSummaryModel(modifiedNSMap.values());
        } else {
            // show all committed namespaces PLUS new ones -- do not need
            // to include modified namespaces here since they ARE committed
            // namespaces and are referenced both in the committedNSMap as well
            // as the modifiedNSMap
            populateSummaryModel(committedNSMap.values());
            populateSummaryModel(newNSMap.values());
        }
    }
    
    private void handleSelectTableCheckBox(ActionEvent ae) {
        try {
            // if the checkbox is selected, then show the autogenerated tables
            loadTableData(chkBoxTables.isSelected());
        } catch (HostException ex) {
            Log.logAndDisplayInfoMessage(ex.getMessage());
            return;
        } catch (UIException ue) {
            Log.logAndDisplayInfoMessage(ue.getMessage());
            return;
        }
    }
    
    private void handleCancelActionPerformed(ActionEvent evt)
                                throws UIException, HostException {  
        List newNamespaces = new ArrayList(newNSMap.values());
        Iterator nIter = newNamespaces.iterator();
        while (nIter.hasNext()) {
            // UNREGISTER new namespaces -- they shouldn't be registered
            // anyway, but we do this to be safe
            Namespace n = (Namespace)nIter.next();
            Namespace parent = n.getParent();
            parent.unregisterChild(n.getName());
        }
        // ***************************************************
        // CLEAN UP for COMMITTED namespaces and fields
        // ***************************************************
        // modifiedFieldMap contains committed fields whose queryable attribute 
        // may have been changed from non-queryable to queryable - if user wants
        // to cancel, the queryable state must be returned to non-queryable
        // otherwise it will be committed as queryable.  
        List modifiedFields = new ArrayList(modifiedFieldMap.keySet());
        Iterator mfIter = modifiedFields.iterator();
        while (mfIter.hasNext()) {
            String modifiedFieldName = (String)mfIter.next();
            
            // initialNQField Map contains committed fields that were 
            // non-queryable before any modifications were performed in panel
            if (initialNQFieldMap.containsKey(modifiedFieldName)) {
                // return queryable attribute to original state
                Field f = (Field)initialNQFieldMap.get(modifiedFieldName);
                f.setQueryable(false);
            } 
        }
        List newFields = new ArrayList(newFieldMap.keySet());
        Iterator newIter = newFields.iterator();
        while (newIter.hasNext()) {
            String newFieldName = (String)newIter.next();
            
            // newFieldMap contains new fields that have been 
            // ADDED to an already committed namespace - if user wants to 
            // cancel, these newly added fields must be removed from the 
            // committed namespace or else they will appear as committed
            Field mField = (Field)newFieldMap.get(newFieldName);
            Namespace ns = mField.getNamespace();
            ns.removeField(mField.getName());
        }
        
        // modifiedNSMap contains committed namespaces whose extensible 
        // attribute may have been changed from extensible to non-extensible or
        // field(s) queryable attribute changed or new fields added.  Fields
        // are taken care of above, but if the user wants to cancel, the 
        // extensible state must be returned to extensible below otherwise it
        // will be committed as non-extensible.  
        List modifiedNS = new ArrayList(modifiedNSMap.keySet());
        Iterator mNSIter = modifiedNS.iterator();
        while (mNSIter.hasNext()) {
            String modifiedNSName = (String)mNSIter.next();
            
            // initExtNamespaceMap contains committed ns that were 
            // extensible before any modifications were performed in panel
            if (initExtNamespaceMap.containsKey(modifiedNSName)) {
                // need to create new namespace with the extensible attr set
                // to true once the ns is unregistered from its parent
                Namespace ns = (Namespace)initExtNamespaceMap
                                                        .get(modifiedNSName);
                
                // namespace may just have had field(s) added to it or field
                // attributes modified so must check extensible attribute
                if (!ns.isExtensible()) {
                    Namespace parent = ns.getParent();
                    parent.unregisterChild(ns.getName());

                    // upon creation newNS registers itself with its parent
                    Namespace newNS = new Namespace(parent,
                                                        modifiedNSName,
                                                        ns.isWritable(),
                                                        !ns.isExtensible(),
                                                        false);
                    // add fields from old namespace to new one
                    ArrayList nsFields = new ArrayList();
                    ns.getFields(nsFields, false);
                    Iterator nsFieldIter = nsFields.iterator();
                    while (nsFieldIter.hasNext()) {
                        Field f = (Field)nsFieldIter.next();
                        newNS.addField(f);
                    }

                    // need to unregister immediate children from the original
                    // namespace and reregister them with the new namespace
                    ArrayList kids = new ArrayList();
                    ns.getChildren(kids, false);
                    Iterator kIter = kids.iterator();
                    while (kIter.hasNext()) {
                        Namespace childNS = (Namespace)kIter.next();
                        ns.unregisterChild(childNS.getName());
                        newNS.registerChild(childNS);
                    }  
                }
            } 
        }
 
        clearAllData(); 
        
        ExplItemConfigMetadataSchema viewSchema = 
                                new ExplItemConfigMetadataSchema(
                                        PnlConfigMetadataSchema.TYPE_VIEW_PNL);
        explItem.setIsModified(false);
        MainFrame mainFrame = MainFrame.getMainFrame();    
        mainFrame.selectExplorerItem(viewSchema);
        dispatchEvent(evt);
    }

    // ********************************************
    // END of HANDLERS
    // ********************************************
    
    //  ***********************************
    // ContentPanel Impl
    // ************************************
    
    public String getTitle() {
        String title = null;
        if (pnlType == TYPE_SETUP_PNL) {
            title = GuiResources.getGuiString(
                                "config.metadata.setup.schema.title");
        } else {
            title = GuiResources.getGuiString(
                                "config.metadata.view.schema.title");
        }
        return title;
    }
    
    public void setExplorerItem(ExplorerItem item) { explItem = item; }
    public ExplorerItem getExplorerItem() { return explItem; }
    public JPanel getJPanel() { return this; }
    public int getAnchor() { return SizeToFitLayout.ANCHOR_CENTER; }
    public int getFillType() { return SizeToFitLayout.FILL_BOTH; }
    public ButtonPanel getButtonPanel() {
        pnlType = ((Integer)getExplorerItem().getPanelPrimerData()).intValue();
        // enables editing of queryable and extensible attributes in the 
        // ns details and ns summary tables, respectively(on Set Up screen only)
        nsDetailsModel.setPanel(this);
        nsSummaryModel.setPanel(this);
        ButtonPanel bp = new BtnPnlApplyCancel();
        if (pnlType == TYPE_SETUP_PNL && ObjectFactory.isAdmin()) {
            BtnPnlApplyCancel bac = (BtnPnlApplyCancel)bp;
            bac.setShowApplyButton(true);
            bac.setShowCancelButton(true);
            JButton cancel = 
                    bac.getCancelButton();
            cancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    try {
                        handleCancelActionPerformed(evt);
                    } catch (UIException ex) {
                        Log.logAndDisplayInfoMessage(ex.getMessage());
                        return;
                    } catch (HostException ex) {
                        Log.logAndDisplayInfoMessage(ex.getMessage());
                        return;
                    }
                }
            });
        } else {
            bp = BtnPnlBlank.getButtonPanel();
        }
        return bp;
    }
    
    public void loadValues() throws UIException, HostException {
        clearAllData();
        getRootNS();
        loadNSDataMap();
        if (pnlType == TYPE_VIEW_PNL) {
            // populates ALL the namespace and namespace field data structures
            // and displays the namespaces in the upper-left-most add remove
            // list component
            if (viewAfterSetupCommit) {
                // just modified an existing namespace or created a new
                // namespace in Setup Schema panel -- need to display the 
                // right data to the user on the View Schema panel
                if (showReserved) {
                    // reserved namespace modified (i.e. <root>)
                    loadNSData(MetadataHelper.ALL_NS);
                    chkBoxNamespaces.setSelected(true); 
                } else {
                    loadNSData(MetadataHelper.NONRESERVED_NS);
                    chkBoxNamespaces.setSelected(false); 
                }
                // Search for the namespace that was added/modified last in the
                // Setup Schema panel and set it as selected in the NS Summary
                // table in the View Schema panel.
                int si = findIndexInModel(lastNSSelectedAtCommit);
                if (si != -1) {
                    arlNSSummary.getTableSelectionModel()
                                                .setSelectionInterval(si, si);
                }
            } else {
                // simply viewing data
                loadNSData(MetadataHelper.NONRESERVED_NS);
                arlNSSummary.getTableSelectionModel().clearSelection();
                chkBoxNamespaces.setSelected(false);
            }
            viewAfterSetupCommit = false;
            // populates the table and column data structures and
            // displays the tables in the lower-left-most add remove list
            // component
            loadTableData(chkBoxTables.isSelected());
            
            // simply need to view the data in this panel
            arlNSSummary.setButtonVisibility(false, AddRemoveList.BOTH_BUTTONS);
            arlNSDetails.setButtonVisibility(false, 
                                                   AddRemoveList.BOTH_BUTTONS);
            arlTables.setButtonVisibility(false, AddRemoveList.BOTH_BUTTONS);
            arlTableDetails
                        .setButtonVisibility(false, AddRemoveList.BOTH_BUTTONS);
            disableChkboxesIfNotEditable(false);
            String chkBoxLabel = GuiResources
                  .getGuiString("config.metadata.schema.ns.chkbox.show");
            chkBoxNamespaces.setText(chkBoxLabel);
            chkBoxNamespaces.setMnemonic(chkBoxLabel.charAt(0));                                                     
        } else {
            // ------------------    SET UP PANEL    ---------------
            // populates ALL the committed namespace and namespace field 
            // data structures -- clear the summary model since it is not
            // displayed by default
            loadNSData(MetadataHelper.ALL_NS);
            nsSummaryModel.removeAll();
            lastNSSelectedAtCommit = "";
            
            // do not show the add remove list components for the metadata
            // tables until the user has created at least one namespace or
            // the user has added queryable fields to an already committed
            // namespace, then namespace tables can be constructed.
            pnlTables.setVisible(false);
            
            // show but disable the autogenerated table checkbox
            chkBoxTables.setEnabled(false);
            
            // set label text for namespace filter
            String chkBoxLabel = GuiResources.getGuiString(
                                "config.metadata.schema.ns.newModified");
            chkBoxNamespaces.setText(chkBoxLabel);
            chkBoxNamespaces.setMnemonic(chkBoxLabel.charAt(0));
            chkBoxNamespaces.setSelected(true);
            chkBoxNamespaces.setVisible(true);
            
            // do not show the add/remove buttons at all until we are allowed
            // to remove columns from tables
            arlTableDetails
                        .setButtonVisibility(false, AddRemoveList.BOTH_BUTTONS);
                        
            // disable the add/remove buttons for the namespace field table
            // until at least one namespace has been created
            arlNSDetails.setEnabled(false);
            disableChkboxesIfNotEditable(true);
            
            if (!ObjectFactory.isAdmin()) {
                arlNSSummary.setEnabled(false);
                arlNSDetails.setEnabled(false);
                chkBoxNamespaces.setEnabled(false);
            }
        }
    }
    
    public void saveValues() throws UIException, HostException {
        
        commitMetadata();
        clearAllData(); 
        ObjectFactory.clearCache(); // Flush cache
        
        ExplItemConfigMetadataSchema viewSchema = 
                                new ExplItemConfigMetadataSchema(
                                        PnlConfigMetadataSchema.TYPE_VIEW_PNL);
                                       
        explItem.setIsModified(false);
        MainFrame mainFrame = MainFrame.getMainFrame();    
        mainFrame.selectExplorerItem(viewSchema);
        viewAfterSetupCommit = true;
    }
    
    // *************************************************

    private int findIndexInModel(String nsName) {
        int numRows = nsSummaryModel.getRowCount();
        for (int idx = 0; idx < numRows; idx++) {
            String rowValue = 
                    (String)nsSummaryModel.getValueAt(idx, nsSummaryModel.NAME);
            if (rowValue.equals(nsName)) {
                return idx;
            }
        }
        return -1;
    }
    /** 
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        pnlNamespaces = new javax.swing.JPanel();
        lblNSSummary = new javax.swing.JLabel();
        arlNSDetails = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        arlNSSummary = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        chkBoxNamespaces = new javax.swing.JCheckBox();
        lblNSDetails = new javax.swing.JLabel();
        pnlTables = new javax.swing.JPanel();
        arlTables = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        arlTableDetails = new com.sun.nws.mozart.ui.swingextensions.AddRemoveList();
        lblTableSummary = new javax.swing.JLabel();
        lblTableDetails = new javax.swing.JLabel();
        chkBoxTables = new javax.swing.JCheckBox();

        lblNSSummary.setFont(new java.awt.Font("Dialog", 1, 14));
        lblNSSummary.setText(GuiResources.getGuiString("config.metadata.schema.ns.summary"));

        chkBoxNamespaces.setActionCommand("Show Namespaces");
        chkBoxNamespaces.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkBoxNamespaces.setMargin(new java.awt.Insets(0, 0, 0, 0));
        chkBoxNamespaces.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkBoxNSSelectionChanged(evt);
            }
        });

        chkBoxNamespaces.getAccessibleContext().setAccessibleName("Show Namespaces");

        lblNSDetails.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.schema.ns.details"));

        org.jdesktop.layout.GroupLayout pnlNamespacesLayout = new org.jdesktop.layout.GroupLayout(pnlNamespaces);
        pnlNamespaces.setLayout(pnlNamespacesLayout);
        pnlNamespacesLayout.setHorizontalGroup(
            pnlNamespacesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, pnlNamespacesLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlNamespacesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(chkBoxNamespaces)
                    .add(arlNSSummary, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 364, Short.MAX_VALUE)
                    .add(lblNSSummary, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 322, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlNamespacesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblNSDetails, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 278, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(arlNSDetails, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 313, Short.MAX_VALUE))
                .add(21, 21, 21))
        );
        pnlNamespacesLayout.setVerticalGroup(
            pnlNamespacesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlNamespacesLayout.createSequentialGroup()
                .add(13, 13, 13)
                .add(pnlNamespacesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(pnlNamespacesLayout.createSequentialGroup()
                        .add(lblNSSummary, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(chkBoxNamespaces))
                    .add(lblNSDetails, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlNamespacesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(arlNSDetails, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 187, Short.MAX_VALUE)
                    .add(arlNSSummary, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 187, Short.MAX_VALUE))
                .addContainerGap())
        );

        lblTableSummary.setFont(new java.awt.Font("Dialog", 1, 14));
        lblTableSummary.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.schema.table.summary"));

        lblTableDetails.setText(java.util.ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui").getString("config.metadata.schema.table.details"));

        chkBoxTables.setActionCommand("Show Namespaces");
        chkBoxTables.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        chkBoxTables.setMargin(new java.awt.Insets(0, 0, 0, 0));
        chkBoxTables.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkBoxTablesSelectionChanged(evt);
            }
        });

        org.jdesktop.layout.GroupLayout pnlTablesLayout = new org.jdesktop.layout.GroupLayout(pnlTables);
        pnlTables.setLayout(pnlTablesLayout);
        pnlTablesLayout.setHorizontalGroup(
            pnlTablesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlTablesLayout.createSequentialGroup()
                .addContainerGap()
                .add(pnlTablesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(arlTables, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 275, Short.MAX_VALUE)
                    .add(lblTableSummary, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 232, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(chkBoxTables))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlTablesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(lblTableDetails, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 357, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(arlTableDetails, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 411, Short.MAX_VALUE))
                .addContainerGap())
        );
        pnlTablesLayout.setVerticalGroup(
            pnlTablesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlTablesLayout.createSequentialGroup()
                .add(lblTableSummary)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlTablesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(lblTableDetails, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(chkBoxTables))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlTablesLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(arlTableDetails, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 216, Short.MAX_VALUE)
                    .add(arlTables, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 216, Short.MAX_VALUE)))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(pnlNamespaces, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(pnlTables, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(pnlNamespaces, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pnlTables, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void chkBoxTablesSelectionChanged(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkBoxTablesSelectionChanged
        handleSelectTableCheckBox(evt);
    }//GEN-LAST:event_chkBoxTablesSelectionChanged

    private void chkBoxNSSelectionChanged(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkBoxNSSelectionChanged
        if (pnlType == TYPE_VIEW_PNL) {
            handleViewSelectNamespaceCheckBox(evt);
        } else {
            handleSetupSelectNamespaceCheckBox(evt);
        }
    }//GEN-LAST:event_chkBoxNSSelectionChanged
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlNSDetails;
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlNSSummary;
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlTableDetails;
    private com.sun.nws.mozart.ui.swingextensions.AddRemoveList arlTables;
    private javax.swing.JCheckBox chkBoxNamespaces;
    private javax.swing.JCheckBox chkBoxTables;
    private javax.swing.JLabel lblNSDetails;
    private javax.swing.JLabel lblNSSummary;
    private javax.swing.JLabel lblTableDetails;
    private javax.swing.JLabel lblTableSummary;
    private javax.swing.JPanel pnlNamespaces;
    private javax.swing.JPanel pnlTables;
    // End of variables declaration//GEN-END:variables

}
