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
 *  Sets the display attributes for the disk images that make up
 *  the dynamic cell image in the GUI.
 */
public class LblDiskImage extends LblImageBase { 
    
    public static final ImageIcon DISK_IMG =
          new ImageIcon(LblDiskImage.class.getResource(
          "/com/sun/honeycomb/admingui/present/images/single_disk.jpg"));
    public static final ImageIcon DISK_GREEN_IMG =
          new ImageIcon(LblDiskImage.class.getResource(
          "/com/sun/honeycomb/admingui/present/images/single_disk_enable.jpg"));        
    public static final ImageIcon DISK_RED_IMG =
         new ImageIcon(LblDiskImage.class.getResource(
         "/com/sun/honeycomb/admingui/present/images/single_disk_disable.jpg"));

    /**
     * Creates a new instance of LblDiskImage using the Disk object
     * returned via the AdminApi methods
     */
    public LblDiskImage(Disk apiDisk) throws UIException {
        super(apiDisk, DISK_IMG, "");
    } 
    public LblDiskImage(boolean error, String tooltipErr) throws UIException {
        // disk will be null
        super(error, tooltipErr, error ? DISK_RED_IMG : DISK_IMG);
    }
    public LblDiskImage() throws UIException {
        super(false, "", DISK_IMG); 
    }  
    protected void setUpListeners() {
        if (fruObject != null) {
            final Disk disk = (Disk)fruObject;
            this.addMouseListener(
                    new MouseAdapter() {
                public void mouseEntered(MouseEvent mouseEnter) {
                    if (disk.getNode().isAlive()) {
                        border = true;
                        repaint();
                    } else {
                        // Need to highlight the node so dispatch this event
                        JPanel panel = (JPanel)getParent();
                        panel.dispatchEvent(mouseEnter);
                    }
                }

                public void mouseExited(MouseEvent mouseExit) {
                    if (disk.getNode().isAlive()) {
                        border = false;
                        repaint();
                    } else {
                        // Need to highlight the node so dispatch this event
                        JPanel panel = (JPanel)getParent();
                        panel.dispatchEvent(mouseExit);
                    }
                }
                // Always dispath a mouse click since nothing happens for
                // a disk; only a node.
                public void mouseClicked(MouseEvent mouseClick) {
                    JPanel panel = (JPanel)getParent();
                    panel.dispatchEvent(mouseClick);
                }
            });
        }
    }
    protected Color getFruStatusColor() {
        final Disk disk = (Disk)fruObject;
        if (disk != null && disk.getNode().isAlive()) {
            status = disk.getStatus();
        } else {
            status = Disk.OFFLINE;
        }
        switch (status) {
            case Disk.AVAILABLE:
            case Disk.ENABLED:
                icon = DISK_GREEN_IMG;
                borderColor = Color.GREEN;
                if (disk != null) {
                    int percentUsed = ToolBox.calcPcntUsedCapacity(
                        disk.getCapUsed(), disk.getCapTotal());
                    tooltip = GuiResources.getGuiString("cell.disk.enabledToolTip",
                        new String[] {disk.getDiskId(),
                        Integer.toString(percentUsed)});
                }
                break;
            case Disk.DISABLED:
                icon = DISK_RED_IMG;
                borderColor = Color.RED;
                if (disk != null) {
                    tooltip = GuiResources.getGuiString(
                            "cell.disk.disabledToolTip",
                            new String[] {disk.getDiskId(), disk.getFRU()});
                }
                break;
            case Disk.MISPLACED:
                icon = DISK_RED_IMG;
                borderColor = Color.RED;
                if (disk != null) {
                    tooltip = GuiResources.getGuiString(
                                    "cell.disk.misplacedToolTip", disk.getID());
                }
                break;
            case Disk.ABSENT:
                icon = DISK_RED_IMG;
                borderColor = Color.RED;
                if (disk != null) {
                    tooltip = GuiResources.getGuiString(
                                    "cell.disk.absentToolTip", disk.getID());
                }
                break;                
            case Disk.OFFLINE:
                icon = DISK_RED_IMG;
                borderColor = Color.RED;
                if (disk != null) {
                    if (!disk.getNode().isAlive()) {
                        // disks are offline due to the node being dead
                        tooltip = GuiResources.getGuiString(
                                    "cell.node.offlineToolTip",
                                        disk.getNode().getID());
                    } else {
                        tooltip = GuiResources.getGuiString(
                                    "cell.disk.offlineToolTip", disk.getID());
                    }
                }
                break;
            default:
                icon = DISK_IMG;
                if (disk != null) {
                    borderColor = Color.GREEN;
                    tooltip = GuiResources.getGuiString(
                                    "cell.disk.offlineToolTip", disk.getID());
                }
        }       
        return borderColor;
    }
 
}
