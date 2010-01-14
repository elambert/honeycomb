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

import com.sun.honeycomb.emd.config.Field;
import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.utility.GuiResources;
import java.util.Iterator;
import java.util.Vector;


/**
 *
 * @author dp127224
 */
public class PnlConfigDataNamespaceTableModel extends BaseTableModel {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public static int NAME;
    public static int DATA_TYPE;
    public static int LENGTH;
    public static int IS_QUERYABLE;
    
    public static final String NA = "N/A";
    public static final int TYPE_DETAILS = 0;
    public static final int TYPE_FIELDS = 1;
    
    private Vector reservedFlags = new Vector();

    /** Creates a new instance of PnlConfigDataNamespaceTableModel */
    public PnlConfigDataNamespaceTableModel(boolean editable, boolean sorted,
                                                        int tblType) {
        super();

        switch (tblType) {
            case TYPE_DETAILS:
                NAME = 0;
                DATA_TYPE = 1;
                IS_QUERYABLE = 2;
                LENGTH = 3;
                setColumns(new TableColumn[] {
                    new TableColumn(NAME, new Integer(120), editable,
                            GuiResources.getGuiString(
                            "config.metadata.namespace.name")),
                    new TableColumn(DATA_TYPE, new Integer(50), editable,
                            GuiResources.getGuiString(
                            "config.metadata.schema.dataType")),
                    new TableColumn(IS_QUERYABLE, new Integer(40), editable,
                            GuiResources.getGuiString(
                            "config.metadata.schema.ns.queryable"))
                });
                break;
                
            case TYPE_FIELDS:
            default:
                NAME = 0;
                DATA_TYPE = 1;
                LENGTH = 2;
                IS_QUERYABLE = 3;
                setColumns(new TableColumn[] {
                    new TableColumn(NAME, null, editable,
                            GuiResources.getGuiString(
                            "config.metadata.namespace.name")),
                    new TableColumn(DATA_TYPE, null, editable,
                            GuiResources.getGuiString(
                            "config.metadata.namespace.dataType")),
                    new TableColumn(LENGTH, null, editable,
                            GuiResources.getGuiString(
                            "config.metadata.namespace.length")),
                    new TableColumn(IS_QUERYABLE, null, editable,
                            GuiResources.getGuiString(
                            "config.metadata.namespace.isQueryable")),
                }); 
                break;
        }

    }
    
    public void populate(Object modelData) {

    }
}
