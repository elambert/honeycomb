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

import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author ad210840
 */
public class NetworkNtpCellRenderer extends IpAddressField
        implements TableCellRenderer  {


    /**
     * Creates a new NetworkNtpCellRenderer.
     */
    public NetworkNtpCellRenderer() {
        super();
    }

    /** {@inheritDoc} */
    public Component getTableCellRendererComponent(final JTable table,
                                                   final Object value,
                                                   final boolean isSelected,
                                                   final boolean hasFocus,
                                                   final int row,
                                                   final int column) {
        // Background and foreground colors
        Color forgrdColor;
        Color bakgrdColor;
        
        if (isSelected) {
            forgrdColor = table.getSelectionForeground();
            bakgrdColor = table.getSelectionBackground();           
        } else {
            forgrdColor = table.getForeground();
            bakgrdColor = table.getBackground();                
        }

        // Border
        Border border = null;
        if (hasFocus) {                
            if (isSelected) {
                border = UIManager.getBorder(
                    "Table.focusSelectedCellHighlightBorder");
            }
            if (border == null) {
                border = UIManager.getBorder(
                    "Table.focusCellHighlightBorder");
            }          
        } 

        String input = (String) value;
        if (input != null && IpAddressField.IP_PATTERN.matcher(input)
                                                            .matches()) {
            // ip address            
            setForeground(forgrdColor);    
            setBackground(bakgrdColor);
            setBorder(border);
            setEditable(false);            
            setText(input);
            return this;
            
        } else {
            JTextField txtField = new JTextField();
            txtField.setForeground(forgrdColor);    
            txtField.setBackground(bakgrdColor);
            txtField.setBorder(border);            
            txtField.setText(input);
            return txtField;
        }        
    }
}
