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
 * ContentsTableModel.java
 *
 * Created on December 22, 2005, 2:52 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.nws.mozart.ui.BaseSortedTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.utility.GuiResources;


/**
 *
 * @author dp127224
 */
public class PnlConfigDataViewTableModel extends BaseSortedTableModel {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public static final int NAME = 0;
    public static final int DATA_TYPE = 1;
    
    // do not remove - may be used in future
//    public static final int UNSET_VALUE = 2;
    
    /**
     * Table does not include an "Unset value" column
     */
    public static final int TYPE_NO_UNSETVAL = 10;
    /**
     * Table includes an "Unset value" column and it is editable.
     */
    public static final int TYPE_RW_UNSETVAL = 11;
    /**
     * Table includes an "Unset value" column and it is read-only.
     */
    public static final int TYPE_RO_UNSETVAL = 12;

    /** Creates a new instance of ContentsTableModel */
    public PnlConfigDataViewTableModel(int type, boolean sorted) {
        super();
        TableColumn[] cols;
        if (type == TYPE_NO_UNSETVAL) {
            cols = new TableColumn[] { 
                new TableColumn(NAME, null, false, 
                     GuiResources.getGuiString(
                        "config.metadata.views.fieldName")),
                new TableColumn(DATA_TYPE, new Integer(10), false, 
                     GuiResources.getGuiString(
                        "config.metadata.schema.dataType"))};
        } else {
            boolean isEditable = (type == TYPE_RW_UNSETVAL) ? true : false;
            cols = new TableColumn[] { 
                new TableColumn(NAME, new Integer(60), false,
                     GuiResources.getGuiString(
                        "config.metadata.views.fieldName")),
                new TableColumn(DATA_TYPE, new Integer(10), false, 
                     GuiResources.getGuiString(
                        "config.metadata.schema.dataType")),
//                new TableColumn(UNSET_VALUE, new Integer(10), isEditable, 
//                     GuiResources.getGuiString(
//                        "config.metadata.views.unsetVal"))
            };                
        }
        setColumns(cols);
        if (sorted) {
            setSortingStatus(NAME, BaseSortedTableModel.ASCENDING);
        } else {
            setSortingStatus(NAME, BaseSortedTableModel.NOT_SORTED);
        }
    }
    
    public void populate(Object modelData) {

    }
}
