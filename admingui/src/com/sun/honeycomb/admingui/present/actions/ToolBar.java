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
 * ToolBar.java
 *
 * Created on April 7, 2006, 1:43 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.honeycomb.admingui.present.actions;

import com.sun.honeycomb.admingui.present.HoneycombProgressIndicator;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.BaseActionCollection;
import com.sun.nws.mozart.ui.BaseToolBar;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.nws.mozart.ui.swingextensions.ToolbarButton;
import com.sun.nws.mozart.ui.actions.GoHomeAction;
import com.sun.nws.mozart.ui.actions.LogoutAction;
import com.sun.nws.mozart.ui.actions.OnlineHelpAction;
import com.sun.nws.mozart.ui.actions.RefreshAction;
import com.sun.nws.mozart.ui.actions.ShowWebsiteAction;

/**
 *
 * @author dp127224
 */
public class ToolBar extends BaseToolBar {
    
    /** Creates a new instance of ToolBar */
    public ToolBar() throws UIException {
        BaseActionCollection col = ObjectFactory.getActionsCollection();
        
        ToolbarButton homeButton = new ToolbarButton();
        addButton(homeButton, col.getAction(GoHomeAction.class));

        ToolbarButton logoutButton = new ToolbarButton();
        addButton(logoutButton, col.getAction(LogoutAction.class));
        
        ToolbarButton refreshButton = new ToolbarButton();
        addButton(refreshButton, col.getAction(RefreshCacheAction.class));

        ToolbarButton launchOnlineHelpButton = new ToolbarButton();
        addButton(launchOnlineHelpButton,
                  col.getAction(OnlineHelpAction.class));
        
        addFiller();
        
//        ToolbarButton showWebsiteButton = new ToolbarButton();
//        addButton(showWebsiteButton, col.getAction(ShowWebsiteAction.class));
        add(new HoneycombProgressIndicator());
    }
    
}
