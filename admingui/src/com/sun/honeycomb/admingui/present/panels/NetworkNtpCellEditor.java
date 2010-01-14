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

import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.swingextensions.IpAddressCellEditor;
import com.sun.nws.mozart.ui.swingextensions.IpAddressField;
import com.sun.nws.mozart.ui.utility.Log;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * This class allows you to switch between a regular text field and one 
 * especially formatted to handle IP addresses.
 *
 * @author ad210840
 */
public class NetworkNtpCellEditor extends IpAddressCellEditor {
    private static final String DEFAULT_HOST_TEXT = "";
    private static final int NUM_CLICKS_TO_EDIT = 1;
    private JTextField hostField = new JTextField();
    private IpAddressField ipaField;    
    private boolean isIP = true;    
    private PnlConfigNetworkNtp ntpPanel = null;
    private PnlConfigNetworkNtpTableModel model = null;
    
    /**
     * Verifies that the entered text is a valid ip address.
     *
     * @param field The cell editor component.
     * @param isSubnetMask If true, the ip address must follow special rules
     * particular to subnet masks.
     */
    public NetworkNtpCellEditor(final IpAddressField field,
        final boolean isSubnetMask) {        
        super(field, isSubnetMask);        
        ipaField = field;
        hostField.setText(DEFAULT_HOST_TEXT);        
    }

    /** {@inheritDoc} */
    public Component getTableCellEditorComponent(final JTable table,
                                                 final Object value,
                                                 final boolean isSelected,
                                                 final int row,
                                                 final int column) {        
        String input = (String) value;
        model = (PnlConfigNetworkNtpTableModel)table.getModel();
        ntpPanel = model.getPanel();
        
        int nSelection = ntpPanel.getHostBtnSelection();  
        if (nSelection == 1) {
            input = "";
            isIP = false;
            ntpPanel.setHostBtnSelection(-1);
        }
        else if (nSelection == 0)
        {
            input = "0.0.0.0";
            isIP = true;
            ntpPanel.setHostBtnSelection(-1);
        }
        else if (isValidIpAddress(input)) {
            isIP = true;
           
        } else {                       
            isIP = false;            
        }
        
        if (isIP) {
            ipaField.setText(input);
            return ipaField;
        }
        else {
            hostField.setText(input);
            return hostField;
        }
    }

    // Called when editing is stopping.  Return value is put into the model.
    public Object getCellEditorValue() {
        if (isIP) {
            return ipaField.getText();
        } else {
            return hostField.getText();
        }
    }
    
   private boolean isValidIpAddress(String txtHost) {
      if (txtHost != null && IpAddressField.IP_PATTERN.matcher(txtHost).matches()) {
        return true; 
      }
      return false;
    }
}
