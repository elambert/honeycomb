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



package com.sun.honeycomb.cm.node_mgr;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.jvm_agent.JVMService;
import java.text.DecimalFormat;
import java.io.IOException;



public final class NodeMgrClient {


    /**
     * Print node manager proxy object.
     */
    public static void main(String args[]) {

        boolean doStart = false;
        boolean doStop = false;
        boolean doReboot = false;
        boolean doPowerOff = false;
        int nodeid = ServiceManager.LOCAL_NODE;

        String cmd = null;
        if (args.length > 0) {
            try {
                cmd = args[0];
                if ("start".equals(cmd)) {
                    doStart = true;
                } else if ("stop".equals(cmd)) {
                    doStop = true;
                } else if ("reboot".equals(cmd)) {
                    doReboot = true;
                } else if ("powerOff".equals(cmd)) {
                    doPowerOff = true;
                }
                if (args.length > 1) {
                    nodeid = Integer.parseInt(args[1]);
                } else {
                    nodeid = ServiceManager.LOCAL_NODE;
                }
            } catch (NumberFormatException e) {
                System.out.println(
                "usage: NodeMgrClient [start|stop|reboot|powerOff] <nodeid>"
                );
                System.exit(1);
            }
        }


        NodeMgrService.Proxy proxy = ServiceManager.proxyFor(nodeid);
        if (proxy != null) {
            NodeMgrService api = (NodeMgrService) proxy.getAPI();
            if (cmd != null) {
                System.out.println("command: " + cmd + " on node " + nodeid);
                try {
                    if (doStop) {
                        boolean res = api.stopAllServices();
                        if (res == true) {
                            System.exit(0);
                        } else {
                            System.exit(1);
                        }
                    } else if (doStart) {
                        api.start();
                    } else if (doReboot) {
                        api.reboot();
                    } else if (doPowerOff) {
                        api.powerOff(false);
                    }
                } catch (ManagedServiceException e) {
                    System.out.println("operation failed " + e);
                    e.printStackTrace();
                    System.exit(1);
                } catch (MgrException e) {
                    System.out.println("operation failed " + e);
                    e.printStackTrace();
                    System.exit(1);
                } 
                System.exit(0);
            }

            System.out.println("\n..... STATE .....\n");
            System.out.println("Maintenance [" + proxy.isMaintenance() + "]");
            System.out.println("Quorum [" + proxy.hasQuorum() + "]");

            Node[] nodes = proxy.getNodes();
            if (nodes != null) {
                System.out.println("\n..... NODES .....\n");
                for (int i = 0; i < nodes.length; i++) {
                    System.out.println(nodes[i]);
                }
            }
            Service[] services = proxy.getServices();
            if (services != null) {
                System.out.println("\n..... SERVICES ON THIS NODE .....\n");
                for (int i = 0; i < services.length; i++) {
                    System.out.println(services[i]);
                }
            }
            System.out.println(); // for readability
        } else {
            System.out.println("Cannot get NodeMgrService proxy " + proxy);
            System.exit(1);
        }
    }
}
