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


package com.sun.honeycomb.hctest.rmi.spsrv.srv;

import com.sun.honeycomb.hctest.rmi.spsrv.common.*;
import com.sun.honeycomb.hctest.rmi.common.*;

import java.rmi.*;
import java.rmi.server.RMISocketFactory;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;

import java.util.logging.Logger;

public class SPSrvServer {

    private final long NODE_SLEEP = 60000;

    private static final Logger LOG =
        Logger.getLogger(SPSrvServer.class.getName());

    public static void usage() {
        System.err.println("usage: .... [-n N]");
        System.err.println("\t[N = number of nodes, 2..16]");
        System.exit(1);
    }

    public static void main(String args[]) {
        int max_nodes = -1;

        if (args.length > 0) {
            if (args[0].equals("-n")) {
                if (args.length != 2)
                    usage();
                try {
                    max_nodes = Integer.parseInt(args[1]);
                } catch (Exception e) {
                    System.err.println(e.toString());
                    usage();
                }
                if (max_nodes < 2) {
                    System.err.println("nodes must be >= 2");
                    usage();
                }
            } else {
                usage();
            }
        }
        new SPSrvServer(max_nodes);
    }

    public SPSrvServer(int max_nodes) {

        SPSrvService svc = null;

        try {
            if (System.getSecurityManager() == null) {
                System.setSecurityManager( new RMISecurityManager() );
            }

            Registry registry = LocateRegistry.createRegistry(
                                        SPSrvConstants.RMI_REGISTRY_PORT);
            RMISocketFactory.setSocketFactory(new 
                                    FixedPortRMISocketFactory(
                                        SPSrvConstants.RMI_SVC_PORT));
            LOG.info("registry port: " + SPSrvConstants.RMI_REGISTRY_PORT + 
                                " svc port: " + SPSrvConstants.RMI_SVC_PORT +
                                " svc: " + SPSrvConstants.SERVICE);
            svc = new SPSrvService(max_nodes);
            registry.rebind(SPSrvConstants.SERVICE, svc);
        } catch (Exception e) {
            LOG.severe("EXITING:" + e);
            System.err.println("SPSrvServer exiting: " + e);
            System.exit(1);
        }
        LOG.info("STARTED");

        //
        //  keep tabs on node servers
        //
        while (true) {
            try {
                Thread.sleep(NODE_SLEEP);
            } catch (Exception e) {}
            svc.updateState();
        }
    }
}
