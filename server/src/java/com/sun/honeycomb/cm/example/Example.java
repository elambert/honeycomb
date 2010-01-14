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



package com.sun.honeycomb.cm.example;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import java.util.Random;
import java.lang.NullPointerException;
import java.util.logging.Logger;


/**
 * This is the implementation of the managed service example.
 * It publishes periodically its proxy object in the cell.
 */
public class Example implements ExampleManagedService {

    private static final Logger logger = 
        Logger.getLogger(Example.class.getName());

    private static final int DELAY = 10000; // 10s
    private static final int TIMEOUT = (5 * 60000); // 5mn
    private boolean triggerFault;

    Random generator;
    volatile boolean keeprunning;

    // default constructor called by CM
    public Example() {
        logger.info("Example init");
        generator = new Random();
        keeprunning = true;
        triggerFault = false;
    }

    public void shutdown() {
        keeprunning = false;
        logger.info("Example shutdowns");
    }

    // return the current proxy for this service
    public ManagedService.ProxyObject getProxy() {
        return new ExampleManagedService.Proxy(generator.nextInt());
    }

    public void syncRun() {
    }

    // service entry point
    public void run() {
        logger.info("Example runs");
        long timeout = System.currentTimeMillis() + TIMEOUT;
        while (keeprunning) {
            if (triggerFault) {
                logger.info("*** EXAMPLE - throw nullPointer ***");
                throw new NullPointerException("FAULT INJECTION TEST");
            }
            ServiceManager.publish(this);
            try {
                Thread.sleep(DELAY);
            } catch (InterruptedException e) {
                // end of this service.
                logger.info("example ends");
                break;
            }
            if (timeout - System.currentTimeMillis() < 0) {
                // triggerFault_1();
                triggerFault_0();
                timeout = System.currentTimeMillis() + TIMEOUT;
            }
        }
    }

    /*
     * remote API exported by the managed service
     */

    public void voidCall() throws ManagedServiceException {
        // change the log level of the service for example
        logger.info("Server - remote method invocation");
    }

    // this method forces the caller to pass the
    // proxy object used to make the remote call
    public boolean checkedCall(ExampleManagedService.Proxy proxy) 
        throws NullPointerException, ManagedServiceException {
        if (proxy == null) {
            // trows back exception to the client
            throw new NullPointerException("proxy is null");
        }
        logger.info("Server - remote method invocation with " + proxy);
        return true;
    }

    // this method will force the service to throw an exception
    public void faultInject() throws ManagedServiceException {
        logger.info("**** FAULT INJECTION ****");
        triggerFault = true;
    }

    /*
     * internal
     */

    /* force a master fail-over */
    private void triggerFault_0() {
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (!proxy.getNode().isMaster()) {
            return;
        }
        triggerFault = true;
    }

    /* assume m+n=5+3, force 3 nodes failure */
    private void triggerFault_1() {
        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        int alives = proxy.nodesAliveCount();
        if (alives < 8) {
            return;
        }
        if (!proxy.getNode().isMaster()) {
            return;
        }
        boolean doMyself = false;
        Node[] nodes = proxy.getNodes();
        int targets = 0;
        do {
            for (int i = 0; targets < 3 && i < nodes.length; i++) {
                if (generator.nextBoolean() == false) {
                    continue;
                }
                int target = nodes[i].nodeId();
                if (target == proxy.nodeId()) {
                    doMyself = true;
                    targets++;
                    continue;
                }
                ManagedService.ProxyObject obj;
                obj = ServiceManager.proxyFor(target, Example.class);
                if (!(obj instanceof ExampleManagedService.Proxy)) {
                    continue;
                }
                if (!(obj.getAPI() instanceof ExampleManagedService)) {
                    continue;
                }
                ExampleManagedService api;
                api = (ExampleManagedService) obj.getAPI();
                try {
                    logger.info("*** FAULT INJECTION FOR " + 
                                nodes[i].nodeId() + " ***");
                    api.faultInject();
                    targets++;
                } catch (ManagedServiceException mse) {
                    throw new RuntimeException(mse);
                }
            }
        } while (targets < 3);
        if (doMyself) {
            logger.info("*** FAULT INJECTION FOR MASTER ***");
            triggerFault = true;
        }
    }
}


/**
 * This is the code one can run on the client side 
 * to access the Example service remotely
 */
class ExampleClient {

    public static void Example(int nodeid) {

        ManagedService.ProxyObject obj;
        obj = ServiceManager.proxyFor(nodeid, Example.class);

        if (obj instanceof ExampleManagedService.Proxy) {
            ExampleManagedService.Proxy proxy;
            proxy = (ExampleManagedService.Proxy) obj;

            // access embedded information.
            int value = proxy.getValue();
            System.out.println("Client - got proxy " + proxy);

            if (proxy.getAPI() instanceof ExampleManagedService) {
                ExampleManagedService api;
                api = (ExampleManagedService) obj.getAPI();
                try {
                    // simple rpc call.
                    api.voidCall();
                    // rpc passing the source proxy as parameter.
                    boolean ret = api.checkedCall(proxy);
                    System.out.println("Client - checkCall returned: " + ret);
                    // generate null pointer exception
                    try {
                        api.checkedCall(null);
                    } catch (NullPointerException e) {
                        System.out.println("Client - expected : " + e);
                    }
                } catch (ManagedServiceException e) {
                    System.out.println("Remote Invocation got: " + e);
                }
            }
        }
    }
            
    public static void main(String args[]) {

        int nodeid;
        if (args.length > 0) {
            nodeid = Integer.parseInt(args[0]);
        } else {
            nodeid = ServiceManager.LOCAL_NODE;
        }
        int loop = 0;
        while (true) {
            loop++;
            System.out.println("\n\n*** EXAMPLE ITERATION " + loop + " ***");
            Example(nodeid);
            try {
                int delay;
                if ((loop % 20) == 0) {
                    delay = 60000;
                } else {
                    delay = 100;
                }
                System.out.println("*** waiting for " + delay + "ms ***");
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
