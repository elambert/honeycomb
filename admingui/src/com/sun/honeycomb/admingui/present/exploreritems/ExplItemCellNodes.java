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
 * NodesExplorerItem.java
 *
 * Created on March 9, 2006, 3:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.honeycomb.admingui.present.exploreritems;

import com.sun.honeycomb.admingui.client.AdminApi;
import com.sun.honeycomb.admingui.client.Cell;
import com.sun.honeycomb.admingui.client.ClientException;
import com.sun.honeycomb.admingui.client.Node;
import com.sun.honeycomb.admingui.client.ServerException;
import com.sun.nws.mozart.ui.exceptions.UIException;
import com.sun.nws.mozart.ui.ExplorerItem;
import com.sun.nws.mozart.ui.utility.GuiResources;
import com.sun.nws.mozart.ui.utility.Log;
import com.sun.honeycomb.admingui.present.ObjectFactory;
import com.sun.honeycomb.admingui.present.panels.PnlCellNodesNode;
import com.sun.nws.mozart.ui.exceptions.HostException;
import java.util.logging.Level;

/**
 *
 * @author dp127224
 */
public class ExplItemCellNodes extends ExplorerItem {
        
    /** Creates a new instance of NodesExplorerItem */
    public ExplItemCellNodes(Cell theCell) {
        super("silo.cells.cell.nodes", 
                GuiResources.getGuiString("explorer.silo.cells.cell.nodes"), 
                null,  
                true, 
                null);
        setPanelPrimerData(theCell);
    }


    public void populateChildren() throws UIException, HostException {
        AdminApi hostConn = ObjectFactory.getHostConnection();
        Cell theCell = (Cell)getPanelPrimerData();
        
        ObjectFactory.setLastCellId(theCell.getID());
        if (!theCell.isAlive()) {
            Log.logToStatusAreaAndExternal(Level.SEVERE, 
                    GuiResources.getGuiString("cell.operation.down.error", 
                    String.valueOf(theCell.getID())), null);
            return;
        }

        Node[] nodes = null;
        try {
            nodes = hostConn.getNodes(theCell);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }    
        
        if (nodes == null || nodes.length == 0) {
            Log.logAndDisplayInfoMessage(
                    GuiResources.getMsgString("info.noNodes"));
            return;
        }

        ExplorerItem item;
        for (int i = 0; i < nodes.length; i++) {
            Node theNode = nodes[i];
            item = new ExplorerItem(
                "silo.cells.cell.nodes.node" + theNode.getID(), 
                GuiResources.getGuiString("explorer.silo.cells.cell.nodes.node",
                                      theNode.getID()), 
                PnlCellNodesNode.class,  
                false, 
                null);
                    
            item.setPanelPrimerData(theNode);
            add(item);
        }
    }
    
}
