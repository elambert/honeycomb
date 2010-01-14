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
import com.sun.honeycomb.admingui.present.panels.PnlGlobalCellOps;
import com.sun.honeycomb.admingui.present.panels.PnlSiloExpansion;
import com.sun.nws.mozart.ui.exceptions.HostException;
import java.util.logging.Level;

/**
 *
 * @author dp127224
 */
public class ExplItemCells extends ExplorerItem {

    public ExplItemCells() {
        super("silo.cells", 
                GuiResources.getGuiString("explorer.silo.cells"), 
                null,  
                true, 
                null);
        
    }

    public void populateChildren() throws UIException, HostException {
        try {
//            boolean multicell = false;
            ExplorerItem node = null;
            AdminApi hostConn = ObjectFactory.getHostConnection();
            
            if (ObjectFactory.isGetCellsError()) {
                Log.logToStatusAreaAndExternal(Level.SEVERE,
                        GuiResources.getGuiString("silo.cell.error"), null);
                return;
            }
            ObjectFactory.setRefreshExplItemCells(false);
            Cell[] cells = hostConn.getCells();    
            if (cells == null) {
                Log.logAndDisplayInfoMessage(
                        GuiResources.getMsgString("info.noCells"));
                return;
            }
            int numCells = cells.length;
            
            // TODO -- if the code below is uncommented, then need to rewrite
            // to take into account the scenario where one or more cells 
            // could be down (i.e. multicell setup) - do not want to make
            // remote calls on a down cell.
//            if (numCells > 1) {
//                multicell = true;
//            }
//
//            // need to check further to determine if single cell or
//            // multicell setup since there is only one cell in hive
//            if (!multicell) {
//                if (cells[0].getID() != 0) {
//                    Node[] nodes = 
//                           ObjectFactory.getHostConnection().getNodes(cells[0]);
//                    if (nodes.length == AdminApi.FULL_CELL_NODES) {
//                        multicell = true;
//                    }
//                }
//            }

//            // not allowed to add/remove cells if the cell in the hive has
//            // a half-cell (i.e. 8 nodes)
//            if (multicell) {
//                // add/remove cell node
//                node = new ExplorerItem("silo.cells.addremovecell", 
//                 GuiResources.getGuiString("explorer.silo.cells.addremovecell"),
//                 PnlSiloExpansion.class, 
//                 false, 
//                 null);
//                add(node);
//            }
            
// HIDE -- global operations panel for 1.1
//            if (multicell) {
//                // Global cell operations
//                node = new ExplorerItem("silo.cells.hive.ops", 
//                    GuiResources.getGuiString("explorer.silo.cells.hive.ops"),
//                    PnlGlobalCellOps.class, 
//                    false, 
//                    null);
//                add(node);
//            }

            for (int i = 0; i < numCells; i++) {
                node = new ExplItemCell(cells[i]);
                add(node);
            }
   
        } catch (Exception e) {
            throw new RuntimeException(e);
        }                      
    }
    
}
