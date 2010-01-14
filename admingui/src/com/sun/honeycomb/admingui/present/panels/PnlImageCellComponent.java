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
 * This panel is the base class for all components that make up the cell image.
 * It MUST be added to a JButton in order to work properly.  The cell
 * components must be able to highlight themselves if the user clicks 
 *
 * @author jb127219
 */
public class PnlImageCellComponent extends JPanel {
    
    // Flag as to whether or not the item should be highlighted
    // within the dynamic cell image from mouse-over
    protected boolean border = false;
    
    // Flag as to whether or not the item should be highlighted
    // within the dynamic cell image when an item in the table is selected.
    protected boolean borderSelected = false;
    
    // Indicates whether or not the component is alive or dead
    private boolean alive = true;   
    
    // Indicates whether or not this item is part of a static or dynamic 
    // cell image
    protected boolean staticImg = false;
    
    /** 
     * Creates a new instance of PnlImageCellComponent in which:
     *          layout = Gridlayout
     *          # rows = 1
     *          # columns = 1
     *          component is part of a dynamic cell image
     */
    public PnlImageCellComponent() {
        this(1, 1, false);
    }

    /** 
     * Creates a new instance of PnlImageCellComponent specifying the number of
     * columns and whether or not this component is part of a static (true) or
     * dynamic (false) cell image.  This constructor uses the default layout, 
     * GridLayout, and assumes there is only one component per panel so the 
     * number of rows is 1.
     * 
     * @param c The number of columns
     * @param staticImage Flag indicating whether or not this image panel is
     *                    part of a static cell (true) or not (false)
     */
    public PnlImageCellComponent(int c, boolean staticImage) {
        // This is a panel representing a cell component.  Thus, there should 
        // only be one row with the column value representing those items to be
        // placed within the component.
        this(1, c, staticImage);
    }
    /** 
     * Creates a new instance of PnlImageCellComponent specifying the number
     * of rows and columns for the default layout, GridLayout.  Highlights the
     * component when it is moused-over only if it is no longer alive.
     * 
     * @param r The number of rows
     * @param c The number of columns
     * @param staticImage Flag indicating whether or not the cell image is
     *                    static or dynamic -- default is dynamic (e.g. false)
     */
    private PnlImageCellComponent(int r, int c, boolean staticImage) {
        this.setLayout(new GridLayout(r, c));
        this.staticImg = staticImage;
        setUpListeners();
    }
    protected void setUpListeners() {
        if (!staticImg) {
            this.addMouseListener(
                 new MouseAdapter() {
                    public void mouseEntered(MouseEvent mouseEnter) {
                        LblImageBase fruImg = (LblImageBase)getComponent(0);
                        if (fruImg != null && 
                               (fruImg.getFruStatusColor().equals(Color.RED))) {
                            border = true;
                            alive = false;
                            repaint();   
                        }
                    }
                    
                    public void mouseExited(MouseEvent mouseExit) {
                        border = false;
                        repaint();
                    }
                    
               });
        }
    }

    /**
     * Overrides JComponent's paint method.
     *
     * @param g  The <code>Graphics</code> context in which to paint 
     */
    public void paint(Graphics g) {
        
        super.paint(g);
        
        Dimension d = this.getSize();
        Graphics2D g2D = (Graphics2D)g;
        if (!alive && !border) {
            // component is "dead" and not mousing over the node -- 
            // just show on image
            g2D.setStroke(new BasicStroke(2.0f));
            g2D.setColor(Color.RED);
            g2D.drawRect(1, 1, d.width-2, d.height-2);
        } else if (border) {
            // component is dead and mousing over it
            g2D.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_BUTT,
                                            BasicStroke.JOIN_BEVEL, 0, 
                                            new float[] {2}, 0));
            g2D.setColor(Color.RED);
            g2D.drawRect(1, 1, d.width-3, d.height-3);
        }
        
        JButton button = (JButton)this.getParent();
        if (borderSelected) {
            g2D.setStroke(new BasicStroke(2.0f));
            g2D.setColor(Color.WHITE);
            g2D.drawRect(1, 1, d.width-2, d.height-2);           
        } else {
            button.setBorder(null);
        }
    }
    
    /**
     * Sets a flag indicating whether or not the component should highlight
     * itself.  As an example, the Cell Details page enables the user to select
     * an item in the Node Summary table and this method is called to highlight
     * corresponding component in the cell image.
     *
     * @param isBorder Flag which tells the cell component to paint a border
     *                 around itself if true; otherwise no border is painted. 
     */
    public void setSelectionBorder(boolean isBorder) {
        this.borderSelected = isBorder;
        repaint();
    }
    
}
