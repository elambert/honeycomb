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

import com.sun.honeycomb.admingui.client.Disk;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServiceNode;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.ToolBox;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
/**
 *  Sets the display attributes for the service node image that is part of the
 *  dynamic cell image in the GUI.
 */
public class LblServiceNodeImage extends LblImageBase { 
    
    public static final ImageIcon SP_IMG = 
            new ImageIcon(LblDiskImage.class.getResource( 
            "/com/sun/honeycomb/admingui/present/images/newsp.jpg"));    

    /**
     * Creates a new instance of LblServiceNodeImage using the ServiceNode 
     * object returned via the AdminApi methods
     */
    public LblServiceNodeImage(ServiceNode apiSP, String ttText) 
        throws UIException {
        super(apiSP, SP_IMG, ttText);
    } 
    public LblServiceNodeImage(boolean error, String tooltipErr) 
        throws UIException {
        // service node will be null
        super(error, tooltipErr, SP_IMG);
        border = error;
    }
    public LblServiceNodeImage() throws UIException {
        super(false, "", SP_IMG);
    }
    protected void setUpListeners() {
        if (fruObject != null) {
            this.addMouseListener(
                new MouseAdapter() {
                    public void mouseEntered(MouseEvent mouseEnter) {
                        border = true;
                        repaint();
                    }

                    public void mouseExited(MouseEvent mouseExit) {
                        // only remove the border if the border color is NOT red
                        // -- if the SP isn't in a good state, then it is
                        // outlined in red at all times, not just when moused
                        // over.  Setting the border to false will remove the
                        // red border, which we want to remain at all times.
                        if (!borderColor.equals(Color.RED)) {
                            border = false;
                            repaint();
                        }
                    }
                    // dispatch a mouse click since nothing happens
                    public void mouseClicked(MouseEvent mouseClick) {
                        JPanel panel = (JPanel)getParent();
                        panel.dispatchEvent(mouseClick);
                    }
                });
        }
    }
    protected Color getFruStatusColor() {
        final ServiceNode sp = (ServiceNode)fruObject;
        if (sp != null) {
            status = sp.getStatus();
        } else if (error) {
            return Color.RED;
        }
        switch (status) {
            case Node.PWR_DOWN:
            case Node.OFFLINE:
                border = true;
                borderColor = Color.RED;
                tooltip = GuiResources.getGuiString("cell.sp.offlineToolTip");
                break;
            case Node.ONLINE:
                border = false;
                borderColor = Color.GREEN;
                break;
            default:
                borderColor = Color.GREEN;
                break;
        }       
        return borderColor;
    }
    
}
