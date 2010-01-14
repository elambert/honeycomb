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
 * CustomProgressBarTableCellRenderer.java
 *
 * Created on January 17, 2007, 7:27 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.honeycomb.admingui.present.panels;

import com.sun.nws.mozart.ui.swingextensions.ProgressBarTableCellRenderer;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTable;

/**
 *
 * @author jp203679
 */

// Customised class for rendering Label  instead of ProgressBar
// in table column 
public class CustomProgressBarTableCellRenderer extends 
                                            ProgressBarTableCellRenderer {
    public static String EMPTY_STR = "";
    // Label to display on the desired cell 
    private String cellLabel = EMPTY_STR;
    
    /** Creates a new instance of CustomProgressBarTableCellRenderer */ 
       public CustomProgressBarTableCellRenderer() {
            super();
        }
       public CustomProgressBarTableCellRenderer(String label) {           
           super();
           this.cellLabel = label;
       }
        
        public Component getTableCellRendererComponent(JTable table, 
                                                    Object value, 
                                                    boolean isSelected, 
                                                    boolean hasFocus, 
                                                    int row, 
                                                    int column) {
            // if a null value is passed in, then the cell's label has text
            // of whatever was set during instantiation of this renderer...if 
            // an empty string is passed in, then the cell's label is blank.
            if (value == null || value == EMPTY_STR) { 
                JLabel label = null;
                if (value == null) {
                    label = makeCellLabel(cellLabel);
                } else if (value == EMPTY_STR) {
                    label = makeCellLabel(EMPTY_STR);
                }
                
                if (isSelected) {
                    label.setForeground(table.getSelectionForeground());
                    label.setBackground(table.getSelectionBackground());
                }
                else {
                    label.setForeground(table.getForeground());
                    label.setBackground(table.getBackground());
                } 
                return label;
            } else {
                return super.getTableCellRendererComponent(table, 
                                                    value, 
                                                    isSelected, 
                                                    hasFocus, 
                                                    row, 
                                                    column);
            }
        }

        
        private JLabel makeCellLabel(String labelText) {
            JLabel lblCell = new JLabel();                  
            lblCell.setOpaque(true); 
            if (labelText.trim().length() != 0) {
                lblCell.setText(labelText) ;
                lblCell.setFont(new Font("Dialog",Font.PLAIN,12));                
                lblCell.setHorizontalAlignment(JLabel.CENTER);
            }
            return lblCell;
        }
}
