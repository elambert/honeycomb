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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 *
 */

public class ReservedNamespaceCellRenderer extends DefaultTableCellRenderer {

    private ArrayList toolTipColumns = new ArrayList();
    private boolean columnsInHelperTable = true;
    
    public ReservedNamespaceCellRenderer(Integer[] columns) {
        this(columns, true);
    }
    
    public ReservedNamespaceCellRenderer(Integer[] columns, 
                                                boolean tableHelper) {
        columnsInHelperTable = tableHelper;
        if (columns != null) {
            for (int idx = 0; idx < columns.length; idx++) {
                Integer col = (Integer)columns[idx];
                toolTipColumns.add(col);
            }
        }
    }
    public Component getTableCellRendererComponent(JTable table,
                                                    Object value,
                                                    boolean isSelected,
                                                    boolean hasFocus,
                                                    int row, int column) {

        super.getTableCellRendererComponent(table, value, isSelected,
            hasFocus, row, column);

        if (columnsInHelperTable) {
            ReservedNamespaceTableHelper helper = 
                            (ReservedNamespaceTableHelper)table.getModel();
            Boolean reserved = helper.isReserved(row, column);
            if (reserved.booleanValue()){
                setFont(new Font("Arial", Font.BOLD|Font.ITALIC, 12)); 
            } else {
                setFont(new Font("Arial", Font.PLAIN, 12));
            }
        }
        
        if (value == null) {
            setToolTipText(null);
        } else {
            // try setting the tooltip
            if (toolTipColumns != null && 
                        toolTipColumns.contains(new Integer(column)) &&
                                                (value instanceof String)) { 
                setToolTipText((String)value); 
            } else {
                setToolTipText(null);
            }
        }
        return this;
    }
}