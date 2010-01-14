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



package com.sun.honeycomb.hctest.rmi.clntsrv.srv;

import com.sun.honeycomb.hctest.rmi.clntsrv.common.*;
import com.sun.honeycomb.hctest.rmi.common.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;

import java.rmi.*;
import java.rmi.server.RMISocketFactory;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;

import java.util.logging.Logger;

public class ClntSrvServer {

    private static ClntSrvService svc = null;

    private static final Logger LOG =
        Logger.getLogger(ClntSrvServer.class.getName());

    public static void main(String args[]) {
        new ClntSrvServer();
    }

    public ClntSrvServer() {

        try {
            if (System.getSecurityManager() == null) {
                System.setSecurityManager( new RMISecurityManager() );
            }

            Registry registry = LocateRegistry.createRegistry(
                                        ClntSrvConstants.RMI_REGISTRY_PORT);
            RMISocketFactory.setSocketFactory(new 
                                FixedPortRMISocketFactory(
                                    ClntSrvConstants.RMI_SVC_PORT));
            svc = new ClntSrvService();
            registry.rebind(ClntSrvConstants.SERVICE, svc);
            LOG.info("started");
        } catch (Exception e) {
            LOG.severe("EXITING:" + e);
            System.exit(1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                                "Shutdown"));
        LOG.info("STARTED");

        while (true) {
            try {
                Thread.sleep(10000);
            } catch (Exception ignore) {}
            System.gc();
        }
    }
    private static class Shutdown implements Runnable {
        public void run() {
           svc.cleanup();
        }
    }
}
