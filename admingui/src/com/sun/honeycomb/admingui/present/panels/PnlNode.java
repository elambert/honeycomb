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

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Disk;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.Border;

/**
 *
 * @author jb127219
 */
public class PnlNode extends PnlImageCellComponent {
    
    // Indicates whether or not the node is alive or dead
    private boolean alive = true;   
    
    /** 
     * Creates a new instance of PnlNode in which:
     *          layout = Gridlayout
     *          # rows = 1
     *          # columns = AdminApi.DISKS_PER_NODE
     */
    public PnlNode() {
        super(AdminApi.DISKS_PER_NODE, false);
    }
    
    /** 
     * Creates a new instance of PnlNode in which:
     *          layout = Gridlayout
     *          # rows = 1
     *          # columns = AdminApi.DISKS_PER_NODE
     *          part of a static image so no listeners are added
     */
    public PnlNode(boolean staticImage) {
        super(AdminApi.DISKS_PER_NODE, staticImage);        
    }
    
    /** 
     * Creates a new instance of PnlNode specifying the number of columns.
     * The number of columns represents the number of disks per node.  However, 
     * the value will most likely never change from 4 disks/node.  This 
     * constructor uses the default layout, GridLayout, and assumes
     * there is only one node per panel so the number of rows is 1.
     * 
     * @param c The number of columns
     */
    public PnlNode(int c) {
        // This is a panel representing a node.  Thus, there should only be 
        // one row with the column representing the number of disks there
        // are per node.
        super(c, false);
    }
    // overriding behavior of base class
    protected void setUpListeners() {
        if (!staticImg) {
            // Only highlight the node if it is NOT alive
            this.addMouseListener(
                 new MouseAdapter() {
                    public void mouseEntered(MouseEvent mouseEnter) {
                        // Only need to get one of the panel's components, which
                        // is a LblDiskImage, in order to determine if this  
                        // node is alive or not --
                        LblDiskImage disk = 
                                (LblDiskImage)getComponent(0);
                        if (disk != null) {
                            if (!((Disk)disk.getObject()).getNode().isAlive()) {
                                alive = false;
                                border = true;
                                repaint();
                            } 
                        } 
                    }
                    
                    public void mouseExited(MouseEvent mouseExit) {
                        border = false;
                        repaint();
                    }
                    
               });
        }
    }
    public boolean isNodeAlive() {
        return alive;
    }    
    public void setIsNodeAlive(boolean isAlive) {
        alive = isAlive;
    }    
}
