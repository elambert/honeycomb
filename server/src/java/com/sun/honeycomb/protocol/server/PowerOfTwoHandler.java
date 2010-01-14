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




package com.sun.honeycomb.protocol.server;

import java.io.FileWriter;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.io.DataOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpInputStream;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpMessage;
import org.mortbay.http.HttpResponse;

import com.sun.honeycomb.multicell.CellInfo;
import com.sun.honeycomb.multicell.MultiCellIntf;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;


public class PowerOfTwoHandler extends ProtocolHandler {

    private static transient final Logger logger = 
        Logger.getLogger(PowerOfTwoHandler.class.getName());

    public PowerOfTwoHandler(final ProtocolBase newService) {
        super(newService);
    }


    private MultiCellIntf getMultiCellAPI() {

        MultiCellIntf res =null;

        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            throw new InternalException("cannot fetch NodeManager Proxy" +
                                        " for local node");
        }
        NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy) obj;

        Node master = nodeMgr.getMasterNode();
        if (master == null) {
            throw new InternalException("cannot retrieve master node");
        }

        ManagedService.ProxyObject proxy =
            ServiceManager.proxyFor(master.nodeId(), "MultiCell");
        if (! (proxy instanceof MultiCellIntf.Proxy)) {
            logger.severe("cannot retrieve proxy for MultiCell service");
            return null;
        }

        Object objApi = proxy.getAPI();
        if (! (objApi instanceof MultiCellIntf)) {
            logger.severe("cannot retrieve API for MultiCell service");
            return null;
        }
        res = (MultiCellIntf) objApi;
        return res;
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {


        // Hardcode fake info for cell 1. this handler will disappear anyway...
         HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
         DataOutputStream dOut = new DataOutputStream(out);
         // 1 cell
         dOut.writeInt(1);

         dOut.writeInt(1);
         dOut.writeLong(10000);
         dOut.writeLong(390);
         dOut.flush();
         

        
//         MultiCellIntf api = getMultiCellAPI();
//         if (api == null) {
//             logger.severe("failed to get MultiCell API");            
//             return;
//         }
        
//         List cells = api.getAvailableCells();

//         HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
//         DataOutputStream dOut = new DataOutputStream(out);


//         dOut.writeInt(cells.size());
//         for (int i = 0; i < cells.size(); i++) {
//             CellInfo cell = (CellInfo) cells.get(i);
//             //
//             // Write the cellId as an 32 bit value to ease the decoding
//             // form the C API library.
//             //
//             dOut.writeInt(cell.getId());
//             dOut.writeLong(cell.getTotalCapacity());
//             dOut.writeLong(cell.getUsedCapacity());
//         }
//         dOut.flush();

    }
}
