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
import com.sun.honeycomb.admingui.client.Fru;
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
 *  Sets the display attributes for the image components that make up
 *  the dynamic cell image in the GUI.
 */
public abstract class LblImageBase extends JLabel { 
 
    // The Fru object returned from the AdminAPI
    protected Fru fruObject = null; 
    // The ImageIcon to use for the Fru Object
    protected ImageIcon icon = null;
    // The status of the Fru object
    protected int status = -1;
    
    // Flag as to whether or not this image should be highlighted
    // within the dynamic cell image
    protected boolean border = false;
    // Color to highlight component
    protected Color borderColor = Color.GREEN;
    // Tooltip to show when user mouses over image
    protected String tooltip = "";
    // Flag as to whether or not a cell error occurred
    protected boolean error = false;

    /**
     * Creates a new instance of LblImageBase using one of the Fru objects
     * returned via the AdminApi methods
     */
    protected LblImageBase(Fru fruObj, ImageIcon imageIcon, String toolTipTxt)
        throws UIException {
        fruObject = fruObj;
        icon = imageIcon;
        tooltip = toolTipTxt;
        setText(null);       
        setDisplayAttr();
    }
    protected LblImageBase(boolean err, String tooltipErr, ImageIcon imageIcon) 
        throws UIException {
        // fruObject will be null
        error = err;
        tooltip = tooltipErr;
        icon = imageIcon;
        setText(null);        
        setDisplayAttr();
    }
    
    
    protected abstract Color getFruStatusColor();
    
    protected void setUpListeners() {
        if (fruObject != null) {
            this.addMouseListener(
                new MouseAdapter() {
                    public void mouseEntered(MouseEvent mouseEnter) {
                        border = true;
                        repaint();
                        // dispatch this event
                        JPanel panel = (JPanel)getParent();
                        panel.dispatchEvent(mouseEnter);
                    }

                    public void mouseExited(MouseEvent mouseExit) {
                        border = false;
                        repaint();
                        // dispatch this event
                        JPanel panel = (JPanel)getParent();
                        panel.dispatchEvent(mouseExit);
                    }
                    // dispatch a mouse click since nothing happens
                    public void mouseClicked(MouseEvent mouseClick) {
                        JPanel panel = (JPanel)getParent();
                        panel.dispatchEvent(mouseClick);
                    }
                });
        }
    }
    
    protected void setDisplayAttr() {
        if (error || fruObject == null) {
            borderColor = Color.RED;
        } else {
            borderColor = getFruStatusColor();
        }
        setUpListeners();
        // Set up tool tip preferences
        ToolTipManager ttMgr = ToolTipManager.sharedInstance();
        ttMgr.setInitialDelay(500);
        ttMgr.setReshowDelay(300);
        setToolTipText(tooltip);
        setIcon(icon);
    }
   /**
     * Overrides JComponent's paint method.
     *
     * @param g  The <code>Graphics</code> context in which to paint
     */
    public void paint(Graphics g) {
        
        super.paint(g);
        
        Dimension d = this.getSize();
        
        if (border) {
            g.setColor(borderColor);
            Graphics2D g2D = (Graphics2D)g;
            g2D.setStroke(new BasicStroke(2.0f));
            g2D.drawRect(1, 1, d.width-2, d.height-2);
        }
    }
    public Fru getObject() {
        return fruObject;
    }

}
