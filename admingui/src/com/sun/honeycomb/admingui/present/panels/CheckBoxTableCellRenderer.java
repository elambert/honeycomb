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

import java.awt.Component;
import java.awt.Font;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author jp203679
 */

// Customised class for rendering Label instead
// of Checkbox in table column
public class CheckBoxTableCellRenderer extends JCheckBox 
                                            implements TableCellRenderer {
    
    // Label to display on the desired cell
    private String cellLabel = "";
    
    /** Creates a new instance of CheckBoxTableCellRenderer */ 
     public CheckBoxTableCellRenderer() {
            super();
            setHorizontalAlignment(SwingConstants.CENTER);
     }
     public CheckBoxTableCellRenderer(String label) {
            super();
            this.cellLabel = label;
     }
           

     public Component getTableCellRendererComponent(JTable table, 
                                        Object value,
                                        boolean isSelected,
                                        boolean hasFocus,
                                        int row, int column) {

        Component comp = (JCheckBox) this;

        if (value == null) {              
            comp = new JLabel();
            JLabel label = (JLabel)comp;                              
            label.setOpaque(true);  
            if (cellLabel.trim().length() != 0) {
               label.setText(cellLabel) ;
               label.setFont(new Font("Dialog",Font.PLAIN,12));                
               label.setHorizontalAlignment(JLabel.CENTER);
            }

            if (isSelected) {
                label.setForeground(table.getSelectionForeground());
                label.setBackground(table.getSelectionBackground());
            }
            else {
                label.setForeground(table.getForeground());
                label.setBackground(table.getBackground());
            }


       } else {
            setSelected(((Boolean)value).booleanValue());
        }

        if (isSelected) {
            setForeground(table.getSelectionForeground());
            super.setBackground(table.getSelectionBackground());
            }
        else {
            setForeground(table.getForeground());
            setBackground(table.getBackground());
        }

        return comp;
    }
    
}
