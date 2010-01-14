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



package com.sun.honeycomb.admingui.present;

import com.sun.nws.mozart.ui.utility.AsyncProxy;
import com.sun.nws.mozart.ui.utility.RemoteCallListener;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ResourceBundle;
import javax.swing.JPanel;

/**
 *
 * @author Kevin A. Roll T201692
 */
public class HoneycombProgressIndicator extends JPanel 
    implements RemoteCallListener {
    
    private static final Font FONT = new Font( "Arial", 
        Font.BOLD | Font.TRUETYPE_FONT, 18 );
    
    private static final ResourceBundle RB = 
        ResourceBundle.getBundle("com/sun/honeycomb/admingui/present/resources/l10n_gui");
    
    private int busyCount;
    private double rotation;
    
    /** Creates a new instance of HoneycombProgressIndicator. */
    public HoneycombProgressIndicator() {
        setPreferredSize(new Dimension(24, 24));
        
        setToolTipText(RB.getString("async.tooltip"));
        
        AsyncProxy.addRemoteCallListener(this);
        
        new Thread() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep( 250 );
                    } catch ( Exception ex ) {
                    }
                    increment();
                }
            }
        }.start();
        
    }

    public synchronized void begin() {
        ++busyCount;
        repaint();
    }
    
    public synchronized void end() {
        --busyCount;
        if (busyCount == 0)
            rotation = 0.0;
        repaint();
    }

    private void increment() {
        if (busyCount > 0) {
            rotation += Math.PI / 12.0;
            repaint();
        }
    }
    protected synchronized void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Dimension d = getSize();
        
        // Clear background
        g.setColor(getBackground());
        g.fillRect(0, 0, d.width, d.height);
        // Enable antialiasing for shapes
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                             RenderingHints.VALUE_ANTIALIAS_ON);
        // Enable antialiasing for text
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
            // Draw numerical indicator of operations in progress
        g.setFont(FONT);
        FontMetrics fm = g.getFontMetrics(FONT);
        String text = String.valueOf(busyCount);
        Rectangle2D r = fm.getStringBounds(text, g);
        int x = (int) (d.width/2 - r.getWidth()/2);
        int y = (int) (d.height/2 + fm.getAscent()/2 - 1);
        g.setColor(Color.BLACK);
        g.drawString( text, x, y );
        
        // Draw hexagon
        g.setColor(busyCount > 0 ? Color.YELLOW.darker() : Color.GRAY);
        g2d.setStroke(new BasicStroke(2.0f, 
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double angle = rotation;
        double radius = d.width/2-1;
        GeneralPath path = new GeneralPath();
        for (int i = 0; i < 6; ++i) {
            Line2D.Double line = new Line2D.Double(
                d.width/2.0 + radius * Math.cos(angle),
                d.height/2.0 + radius * Math.sin(angle),
                d.width/2.0 + radius * Math.cos(angle + Math.PI / 3.0),
                d.height/2.0 + radius * Math.sin(angle + Math.PI / 3.0));
            path.append(line, true);
            angle += Math.PI / 3.0;
        }        
        g2d.draw(path);  
    }    

    public void remoteCallBegin() {
        begin();
    }

    public void remoteCallEnd() {
        end();
    }
}
