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

import com.sun.nws.mozart.ui.BaseTableModel;
import com.sun.nws.mozart.ui.BaseTableModel.TableColumn;
import com.sun.nws.mozart.ui.utility.GuiResources;


/**
 *
 * @author jb127219
 */
public class PnlConfigViewSummaryTableModel extends BaseTableModel {

    // Would like to use enum here, but can't because you can't have "base" enum
    // and refer in the base class to enums in the derived class.
    public static final int NAME = 0;
    public static final int READ_ONLY = 1;
    public static final int FILENAME_PATTERN = 2;
    public static final int EXTENDED_FIELDS_INCLUDED = 3;
//    public static final int ARCHIVES = 4;



    /** Creates a new instance of ContentsTableModel */
    public PnlConfigViewSummaryTableModel() {
        super(new TableColumn[] { 
            new TableColumn(NAME, new Integer(70), false, 
                 GuiResources.getGuiString(
                    "config.metadata.views.fieldName")),
            new TableColumn(READ_ONLY, new Integer(5), false, 
                 GuiResources.getGuiString(
                    "config.metadata.schema.view.readOnly")),
            new TableColumn(FILENAME_PATTERN, new Integer(90), false, 
                 GuiResources.getGuiString(
                    "config.metadata.schema.view.pattern")), 
            new TableColumn(EXTENDED_FIELDS_INCLUDED, new Integer(20), false, 
                 GuiResources.getGuiString(
                        "config.metadata.schema.view.extendedfields"))});
//            new TableColumn(ARCHIVES, new Integer(20), false, 
//                 GuiResources.getGuiString(
//                    "config.metadata.schema.view.archives"))});            

    }
    
    public void populate(Object modelData) {

    }
}
