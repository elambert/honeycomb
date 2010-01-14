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





package com.sun.honeycomb.admingui.present.exploreritems;

import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.honeycomb.admingui.present.panels.PnlConfigNetworkNtp;
import com.sun.honeycomb.admingui.present.panels.PnlConfigDNS;
import javax.swing.Icon;

/**
 *
 * @author dp127224
 */
public class ExplItemConfigNetwork extends ExplorerItem {

    public ExplItemConfigNetwork() {
        super("silo.config.network", 
                GuiResources.getGuiString("explorer.silo.config.network"), 
                null, 
                true, 
                null);
    }

    public void populateChildren() throws UIException {
        // Silo Configuration Network node        
        ExplorerItem node = new ExplorerItem("silo.config.network.ntp", 
                GuiResources.getGuiString("explorer.silo.config.network.ntp"), 
                PnlConfigNetworkNtp.class, 
                false, 
                null);
        add(node);
        
        node = new ExplorerItem("silo.config.network.dns",
                GuiResources.getGuiString("explorer.silo.config.network.dns"),
                PnlConfigDNS.class,
                false,
                null);
        
        add(node);
        
    }
    
}
