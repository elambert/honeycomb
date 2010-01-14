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



package com.sun.honeycomb.platform;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;

/**
 * syslog-ng log router.
 */
final class LogRouter {
    private static final Logger logger = 
        Logger.getLogger(LogRouter.class.getName());
    private static final long MASTER_UPDATE_TIME = 5 * 1000; 
    private static final String FIFO_FILE_PREFIX = "/tmp/node";

    public static void main(String args[]) {
        BufferedReader input = 
            new BufferedReader(new InputStreamReader(System.in));
        PrintWriter output = null;
        Node currentMaster = null;
        int currentMasterNodeId = -1;
        long lastChecked = 0;

        String line = null;
        // listen to stdin
            while(true) {
                try {
                    line = input.readLine();
                    if(line == null) {
                        break;
                    }
                    if((currentMaster == null) || 
                       ((System.currentTimeMillis() - lastChecked) 
                        >= MASTER_UPDATE_TIME)) {
                        currentMaster = getMasterNode();
                        if(currentMaster == null) {
                            continue; //no master exists. We can not forward
                                      //anything yet
                        }
                        if(currentMaster.nodeId() != currentMasterNodeId) {
                            currentMasterNodeId = currentMaster.nodeId();
                            output = 
                                new PrintWriter(new FileOutputStream
                                          (FIFO_FILE_PREFIX + 
                                           currentMasterNodeId));
                            //todo: send the last few secs worth of data to 
                            // the new master
                        }
                        lastChecked = System.currentTimeMillis();
                    }
                    if(output != null) {
                        output.println(line);
                        output.flush();
                    }
                } catch(IOException e) {
                    logger.severe("IO Error " + e);
                }
            }
    }
    private static Node getMasterNode() {
        ManagedService.ProxyObject obj;
        obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        Node masterNode = null;
        if (obj instanceof NodeMgrService.Proxy) {
            NodeMgrService.Proxy proxy = (NodeMgrService.Proxy) obj;
            Node[] nodes = proxy.getNodes();
            if (nodes != null) {
                for (int i = 0; i < nodes.length; i++) {
                    if(nodes[i].isMaster()) {
                        masterNode = nodes[i];
                        break;
                    }
                }
            }
        }
        return masterNode;
    }
}
