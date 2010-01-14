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

import com.sun.honeycomb.admingui.present.panels.PnlConfigMetadataSchema;
import com.sun.nws.mozart.ui.exceptions.HostException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.utility.GuiResources;

/**
 *
 * @author jb127219
 */
public class ExplItemConfigMetadataSchema extends ExplorerItem {

    private int type = PnlConfigMetadataSchema.TYPE_SETUP_PNL;
     
    public ExplItemConfigMetadataSchema(int type) {
        super();
        this.type = type;
        
        if (type == PnlConfigMetadataSchema.TYPE_SETUP_PNL) {
            setName("silo.config.metadata.setup.schema");
            setLabel(GuiResources.getGuiString(
                    "explorer.silo.config.metadata.setup.schema"));
        } else {
            setName("silo.config.metadata.view.schema");
            setLabel(GuiResources.getGuiString(
                    "explorer.silo.config.metadata.view.schema"));
        }
        setPanelClass(PnlConfigMetadataSchema.class);
        setAllowsChildren(false);
        setIcon(null);
        
        // Need to set what this panel will be doing....setting up the schema?
        // or....viewing the schema?
        setPanelPrimerData(new Integer(type));
    }

    public void populateChildren() throws UIException, HostException {

    }
    
    /**
     * Override the getIsModified method in order to always return false
     * if its the "View Schema" panel
     */
     public boolean getIsModified() { 
         boolean modified = false;
         if (type == PnlConfigMetadataSchema.TYPE_SETUP_PNL) {
             modified = super.getIsModified(); 
         }
         // if its the "View Schema" panel, then always return FALSE
         return modified;
     }
    
}
