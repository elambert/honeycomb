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


package com.sun.honeycomb.hctest.rmi.auditsrv.srv;

import com.sun.honeycomb.hctest.rmi.auditsrv.common.*;
import com.sun.honeycomb.hctest.rmi.common.*;

import java.rmi.*;
import java.rmi.server.RMISocketFactory;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;

import java.util.logging.Logger;

public class AuditSrvServer {

    private static final Logger LOG =
        Logger.getLogger(AuditSrvServer.class.getName());

    public static void main(String args[]) {

        new AuditSrvServer();
    }

    public AuditSrvServer() {

        AuditSrvService svc = null;

        try {
            if (System.getSecurityManager() == null) {
                System.setSecurityManager( new RMISecurityManager() );
            }

            Registry registry = LocateRegistry.createRegistry(
                                        AuditSrvConstants.RMI_REGISTRY_PORT);
            RMISocketFactory.setSocketFactory(new 
                                    FixedPortRMISocketFactory(
                                        AuditSrvConstants.RMI_SVC_PORT));
            LOG.info("registry port: " + AuditSrvConstants.RMI_REGISTRY_PORT + 
                            " svc port: " + AuditSrvConstants.RMI_SVC_PORT +
                            " svc: " + AuditSrvConstants.SERVICE);
            svc = new AuditSrvService();
            registry.rebind(AuditSrvConstants.SERVICE, svc);
        } catch (Exception e) {
            LOG.severe("EXITING:" + e);
            System.err.println("AuditSrvServer exiting: " + e);
            System.exit(1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));
        LOG.info("STARTED");
    }


    private static class Shutdown implements Runnable {
        public void run() {
            LOG.info("exiting");
        }
    }
}
