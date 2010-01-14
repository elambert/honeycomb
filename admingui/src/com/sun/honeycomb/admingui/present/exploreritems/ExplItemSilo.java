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
 * CellExplorerItem.java
 *
 * Created on March 9, 2006, 3:40 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.honeycomb.admingui.present.exploreritems;

import com.sun.nws.mozart.ui.BaseObjectFactory;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.honeycomb.admingui.present.panels.PnlSilo;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.MainFrame;
import com.sun.nws.mozart.ui.utility.GuiResources;

/**
 *
 * @author dp127224
 */
public class ExplItemSilo extends ExplorerItem {
    
    private int interval = MainFrame.getRefreshRate();

    public ExplItemSilo(boolean fullAccess) throws UIException {
        super("silo", 
                ObjectFactory.getGUIServerIP() == null ?
                    fullAccess ?
                        BaseObjectFactory.getHostId() :
                        "<html>".concat(BaseObjectFactory.getHostId())
                        .concat("&nbsp;<b>").concat(GuiResources
                        .getGuiString("explorer.silo.readonly"))
                        .concat("</b></html>") :
                    fullAccess ?
                        ObjectFactory.getGUIServerIP() :
                        "<html>".concat(ObjectFactory.getGUIServerIP())
                        .concat("&nbsp;<b>").concat(GuiResources
                        .getGuiString("explorer.silo.readonly"))
                        .concat("</b></html>"),
                PnlSilo.class, 
                true, 
                null);
    }

    public void populateChildren() throws UIException {
        // Silo Configuration
        ExplorerItem node = new ExplItemConfig();
        add(node);
        
        // Silo Monitoring
        node = new ExplItemMonitor();
        add(node);
        
        // Silo cells
        node = new ExplItemCells();
        add(node);
        
    }
    /**
     * Override --
     * This method returns a timer interval, in seconds, at which
     * this panel should be refreshed.
     */
    public int getTimerInterval() {
        return interval;
    }
    
    
    public void setTimerInterval(int refreshInterval) {
        interval = refreshInterval; 
    }
    
}
