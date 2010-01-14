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



package com.sun.honeycomb.hctest.rmi.nodesrv.srv;

import com.sun.honeycomb.hctest.rmi.nodesrv.common.*;
import com.sun.honeycomb.hctest.rmi.common.*;

import java.rmi.*;
import java.rmi.server.RMISocketFactory;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;

import java.util.logging.Logger;

public class NodeSrvServer {

    NodeSrvService svc = null;

    private static final Logger LOG =
        Logger.getLogger(NodeSrvServer.class.getName());

    private static Registry registry = null;

    public static void main(String args[]) {
        new NodeSrvServer();
    }

    public NodeSrvServer() {


        try {
            LOG.info("starting..");
            if (System.getSecurityManager() == null) {
                //System.out.println("setting security mgr");
                System.setSecurityManager( new RMISecurityManager() );
            }

            LOG.info("creating registry on port " +
                                            NSConstants.RMI_REGISTRY_PORT);
            registry = LocateRegistry.createRegistry(
                                            NSConstants.RMI_REGISTRY_PORT);
            LOG.info("setting new socket factory on port " +
                                            NSConstants.RMI_SVC_PORT);
            RMISocketFactory.setSocketFactory(new 
                                    FixedPortRMISocketFactory(
                                            NSConstants.RMI_SVC_PORT));
            LOG.info("creating service");
            // use launch script to wait until hc started
            svc = new NodeSrvService();
            // createService();
            LOG.info("binding service");
            registry.rebind(NSConstants.SERVICE, svc);
            LOG.info("add shutdown hook");
            Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));
            LOG.info("started");
        } catch (Exception e) {
            LOG.severe("EXITING:" + e);
            System.exit(1);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {}
    }

    private void createService() {
        int tries = 0;
        // while (true) {
            try {
                tries++;
                CreateService cs = new CreateService();
                cs.start();
                sleep(5000);
                if (!cs.done) {
LOG.warning("exiting for new jvm to try");
System.exit(1);
                    //break;
}
            } catch (Exception e) {
                LOG.info("creating service (loop): " + e);
            }
            sleep(5000);
        //}
        LOG.info("loop done, tries=" + tries);
    }

    private class CreateService extends Thread {
        boolean done = false;
        public void run() {
            try {
                LOG.info("trying to create service..");
                svc = new NodeSrvService();
                LOG.info("created service..");
                done = true;
            } catch (Exception e) {
                LOG.info("creating service (thread): " + e);
            }
        }
    }

    private static class Shutdown implements Runnable {
        public void run() {
            LOG.info("shutting down");
            try {
                registry.unbind(NSConstants.SERVICE);
            } catch (Exception e) {
                LOG.warning("unbinding registry: " + e);
            }
            LOG.info("goodbye");
        }
    }
}
