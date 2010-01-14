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



package com.sun.honeycomb.cm.err_inj;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.config.ClusterProperties;

import java.util.*;
import java.io.*;
import java.lang.NullPointerException;
import java.util.logging.Logger;


/**
 * This is the implementation of the managed service example.
 * It publishes periodically its proxy object in the cell.
 */
public class ErrInj implements ErrInjManagedService, Observer {

    private static final int POLL_INTERVAL = 5000; // 5 sec

    private static final int TIMEOUT_5mn = (5 * 60000); // 5mn
    private static final int TIMEOUT_1mn = 60000; // 1mn

    private final static int NO_CMD = -1;
    private final static int EXCEPTION = 1;
    private final static int KILL = 2;
    private final static int EAT_MEM = 3;
    private final static int EAT_FDS = 4;
    private final static int EAT_THREADS = 5;

    private static int cmd = NO_CMD;
    private static String msg = null;

    private static boolean autoMode = false;

    private String component = null;

    private static final Logger logger = 
        Logger.getLogger(ErrInj.class.getName());


    Random generator;
    volatile boolean keeprunning;

    // default constructor called by CM
    public ErrInj() {
        Thread t = Thread.currentThread();
        String tname = Thread.currentThread().getName();
        String s[] = tname.split(" ");
        if (s.length != 2) {
            logger.severe("ERRINJ: thread name needs 2 components [" +
					tname + "]");
        } else {
            component = s[1];
            if (component.indexOf("_auto_") != -1) {
                logger.warning("ERRINJ: in auto mode (" + component + ")");
                autoMode = true;
            }
        }
        logger.info("ERRINJ init [" + tname + "]");
        generator = new Random();
        keeprunning = true;
    }

    public void shutdown() {
        keeprunning = false;
        logger.info("ERRINJ shutdown");
    }

    // return the current proxy for this service
    public ProxyObject getProxy() {
        return new ErrInjManagedService.Proxy(generator.nextInt());
    }

    public void syncRun() {
    }

    // service entry point
    public void run() {
        logger.info("ERRINJ runs");
        try {
            ServiceManager.register(ServiceManager.CMM_EVENT, this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ServiceManager.publish(this);
        // loop getting client exception injects
        long timeout1mn = System.currentTimeMillis() + TIMEOUT_1mn;
        long timeout5mn = System.currentTimeMillis() + TIMEOUT_5mn;
        while (keeprunning) {
            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException e) {
                logger.info("Interrupted: " + e);
            }
            switch (cmd) {
                case NO_CMD:
                    break;
                case EXCEPTION:
                    throw new NullPointerException("Injected: " + msg);
                case KILL:
                    kill(msg);
                case EAT_MEM:
                    eatmemory(msg);
                    break;
                case EAT_FDS:
                    try {
                        eatfds(msg);
                    } catch (Exception e) {}
                    break;
                case EAT_THREADS:
                    eatthreads(msg);
                    break;
                default:
                    logger.severe("ERRINJ: UNEXPECTED OPTION " + cmd);
                    break;
            }
            cmd = NO_CMD;
            if (autoMode) {
                if (timeout5mn - System.currentTimeMillis() < 0) {
                    triggerFault_1(true);
                    timeout5mn = System.currentTimeMillis() + TIMEOUT_5mn;
                } else if (timeout1mn - System.currentTimeMillis() < 0) {
                    triggerFault_1(false);
                    timeout1mn = System.currentTimeMillis() + TIMEOUT_1mn;
                }
            }
        }
    }

    /*
     * remote API exported by the managed service
     */

    public void update(Observable obj, Object arg) {
                                                                                
        NodeChange nodechange = (NodeChange) arg;
        int cause = nodechange.getCause();
        int node = nodechange.nodeId();
        logger.info("LUDO - CMM callback triggered " + nodechange);
    }

    public void voidCall() throws ManagedServiceException {
        // change the log level of the service for example
        logger.info("Server - remote method invocation");
    }

    // the server needs to throw the exception, so it polls
    // to see that this clnt method has set it
    public void throwException(String msg) throws ManagedServiceException {
        logger.info("ERRINJ: throwException: " + msg);
        cmd = EXCEPTION;
        this.msg = msg;
    }

    public void killJVM(boolean server, String msg) 
					throws ManagedServiceException {
        logger.info("ERRINJ: killJVM: " + msg + (server ? " (server)" : ""));
        if (server) {
            cmd = KILL;
            this.msg = msg;
        } else
            kill(msg);
    }
    private void kill(String msg) {
        logger.severe("ERRINJ: KILL from [" + msg + "]");
        System.exit(2);
    }

    public void eatMemory(boolean server, String msg) 
					throws ManagedServiceException {
        logger.info("ERRINJ: eatMemory: " + msg + (server ? " (server)" : ""));
        if (server) {
            cmd = EAT_MEM;
            this.msg = msg;
        } else
            eatmemory(msg);
    }
    private void eatmemory(String msg) {
        logger.severe("ERRINJ: EATING MEMORY [" + msg + "]");
        LinkedList l = new LinkedList();
        for (int i=1; ; i++) {
            l.add(new Vector(2048));
            if (i % 1000 == 0)
                logger.info("ERRINJ: eatMemory (" + msg + "): added Vector(2048) " + i);
        }
    }

    public void eatFDs(boolean server, String msg) 
					throws ManagedServiceException {
        logger.info("ERRINJ: eatFds: " + msg + (server ? " (server)" : ""));
        if (server) {
            cmd = EAT_FDS;
            this.msg = msg;
        } else
            eatfds(msg);
    }
    private void eatfds(String msg) throws ManagedServiceException {
        logger.severe("ERRINJ: EATING FDs [" + msg + "]");
        LinkedList l = new LinkedList();
        try {
            for (int i=1; ; i++) {
                l.add(new FileOutputStream("/dev/null"));
                if (i % 1000 == 0)
                    logger.info("ERRINJ: eatFDs(" + msg + "): opened " + i);
            }
        } catch (Exception e) {
            logger.severe("ERRINJ eatfds [" + msg + "]: " + e);
            throw new ManagedServiceException(e);
        }
    }

    public void eatThreads(boolean server, String msg) 
					throws ManagedServiceException {
        logger.info("ERRINJ: eatThreads: " + msg + (server ? " (server)" : ""));
        if (server) {
            cmd = EAT_THREADS;
            this.msg = msg;
        } else
            eatthreads(msg);
    }
    private void eatthreads(String msg) {
        logger.severe("ERRINJ: EATING THREADS [" + msg + "]");
        for (int i=1; ; i++) {
            new EatThread(i).start();
            if (i % 100 == 0)
                logger.info("ERRINJ: eatThreads(" + msg + "): " + i);

            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }

    class EatThread extends Thread {
        int id;
        EatThread(int id) {
            this.id = id;
        }
        public void run() {
            double acc = 111000222111333111.123;
            for (int i=1; ; i++) {
                double d = (double) i;
                for (int j=0; j<1000; j++)
                    acc = (acc + d) / d;
                for (int j=0; j<1000; j++) {
                    acc = Math.sqrt(acc);
                    acc *= acc;
                }
                if (acc < 111000222111333111.123)
                    acc += 111000222111333111.123;
                if (i % 5000000 == 0)
                    logger.info("ERRINJ: Thread " + id + ": " + i + ": " + acc);
            }
        }
    }

    // this method forces the caller to pass the
    // proxy object used to make the remote call
    public boolean checkedCall(ErrInjManagedService.Proxy proxy) 
        throws NullPointerException, ManagedServiceException {
        if (proxy == null) {
            // trows back exception to the client
            throw new NullPointerException("proxy is null");
        }
        logger.info("Server - remote method invocation with " + proxy);
        return true;
    }

    /*
     * internal fault injector
     */

    /**
     * generate max supported failures in the cluster
     * TODO - this code assume m+n=5+3 (which can be read from the
     * config) and a 8 nodes cluster (which can be figure out from
     * the node manager proxy).
     * trigger M=parity software failures of the given
     * component and force an node failure escalation.
     */
    private void triggerFault_1(boolean escalation) {

        NodeMgrService.Proxy proxy;
        proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (!proxy.getNode().isMaster()) {
            return;
        }

        // make sure all nodes are up
        // XXX use power-on status rather than config when available/accurate
        ClusterProperties config = ClusterProperties.getInstance();
        String s = config.getProperty("honeycomb.layout.numnodes");
        if (s == null) {
            logger.severe("ERRINJ: can't get numnodes from config");
            return;
        }
        int config_nodes = Integer.parseInt(s);
        if (proxy.nodesAliveCount() < config_nodes)
            return;

        // inject errors..

        // get M to determine how many failures needed
        // for now get from config file
        int errors = 3;  // default
        s = config.getProperty("honeycomb.layout.parityfrags");
        if (s != null)
            errors = (new Integer(s)).intValue();
        // what about 3-node cluster?
        if (errors > config_nodes - 1) {
            logger.warning("ERRINJ: afflicting " + (config_nodes - 1) + 
			" nodes instead of parity (" + errors + 
			") due to low node count");
            errors = config_nodes - 1;
        }

        logger.info("ERRINJ: fault injection start mode: " + 
                    ((escalation)? "NODE FAILURE":"SOFTWARE FAILURE"));
        Node[] nodes = proxy.getNodes();
        int nodeid = proxy.nodeId();
        int targets = 0;
        do {
            for (int i = 0; targets < errors && i < nodes.length; i++) {
                int target_node = nodes[i].nodeId();

                // skip master
                if (target_node == nodeid)
                    continue;
                // flip coin
                if (generator.nextBoolean())
                    continue;

                if (escalation) {
                    if (injectFailure(target_node)) {
                        targets++;
                    }
                } else if (injectException(target_node)) {
                    targets++;
                }
            }
        } while (targets < errors);
    }

    private boolean injectException(int target_node) {
        ManagedService.ProxyObject obj;
        obj = ServiceManager.proxyFor(target_node, component);
        if (!(obj instanceof ErrInjManagedService.Proxy)) {
            return false;
        }
        if (!(obj.getAPI() instanceof ErrInjManagedService)) {
            logger.warning("ERRINJ: unexpected API " + obj.getAPI());
            return false;
        }
        ErrInjManagedService api;
        api = (ErrInjManagedService) obj.getAPI();
        try {
            String msg = "FAULT INJECTION FOR " + target_node;
            logger.info("ERRINJ - " + msg); 
            api.throwException(msg);
        } catch (ManagedServiceException mse) {
            logger.warning("ERRINJ: got exception " + mse);
            return false;
        }
        return true;
    }

    private boolean injectFailure(int target_node) {
        long timeout = System.currentTimeMillis() + TIMEOUT_1mn;
        while (true) {
            NodeMgrService.Proxy proxy;
            proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            Node[] nodes = proxy.getNodes();
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].nodeId() == target_node) {
                    if (!nodes[i].isAlive()) {
                        logger.info("ERRINJ - node " + target_node + 
							" rebooted");
                        return true;
                    }
                }
            }
            injectException(target_node);
            try {
                Thread.currentThread().sleep(POLL_INTERVAL);
            } catch (InterruptedException ie) {
                break;
            }
            if (timeout - System.currentTimeMillis() < 0) {
                logger.info("ERRINJ: Node manager still running - giving up");
                return false;
            }
        }
        // for javac - actual returns are in while loop
        logger.info("ERRINJ - node " + target_node + " rebooted");
        return true;
    }
}


/**
 * This is the code one can run on the client side 
 * to access the ErrInj service remotely
 */
class ErrInjClient {

    private static String getErrInjServices(int nodeid) {
        Object obj = ServiceManager.proxyFor(nodeid);
        if (!(obj instanceof NodeMgrService.Proxy)) {
            return "getting services: cannot retrieve Node manager proxy";
        }
        NodeMgrService.Proxy proxy = (NodeMgrService.Proxy) obj;
        Service[] svcs = proxy.getServices();
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<svcs.length; i++) {
            if (svcs[i].isJVM())
                continue;
            if (!svcs[i].isManaged())
                continue;
            String s = svcs[i].getName();
            if (!s.startsWith("ErrInj_"))
                continue;
            sb.append(s.substring(7)).append(" ");
        }
        if (sb.length() == 0)
            return "[no ErrInj services defined]";
        return sb.toString();
    }

    private static String getNodeInfo() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (!(obj instanceof NodeMgrService.Proxy)) {
            return "getting nodes: cannot retrieve Node manager proxy";
        }
        NodeMgrService.Proxy proxy = (NodeMgrService.Proxy) obj;
        Node[] nodes = proxy.getNodes();
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<nodes.length; i++)
            sb.append(nodes[i].toStatus()).append("\n");
        return sb.toString();
    }
    public static void errInjClientExample(int nodeid, String svc_name,
							boolean server,
							String action) {

        ManagedService.ProxyObject obj;
        obj = ServiceManager.proxyFor(nodeid, svc_name);

        if (obj == null) {
            String node = null;
            if (nodeid == 0)
                node = "this";
            else
                node = "" + nodeid;
            if (svc_name.startsWith("ErrInj_"))
                svc_name = svc_name.substring(7);
            System.err.println("Service not found: " + svc_name + 
					" (node=" + node + ")");
            System.err.println("Services: " + getErrInjServices(nodeid));
            System.exit(1);
        }

        if (obj instanceof ErrInjManagedService.Proxy) {
            ErrInjManagedService.Proxy proxy = 
				(ErrInjManagedService.Proxy) obj;

            // access embedded information.
            int value = proxy.getValue();
            System.out.println("Client - got proxy " + proxy);

            if (proxy.getAPI() instanceof ErrInjManagedService) {
                ErrInjManagedService api;
                api = (ErrInjManagedService) obj.getAPI();
                if (action == null)
                    action = "excep";
                try {
                    if (action.equals("excep")) {
                        api.throwException("demo app");
                    } else if (action.equals("kill")) {
                        api.killJVM(server, "demo app");
                    } else if (action.equals("fd")) {
                        api.eatFDs(server, "demo app");
                    } else if (action.equals("mem")) {
                        api.eatMemory(server, "demo app");
                    } else if (action.equals("proc")) {
                        api.eatThreads(server, "demo app");
                    } else {
                        System.err.println("Unknown cmd: " + action);
                        System.exit(1);
                    }
                    System.out.println("Remote Invocation done");
                } catch (ManagedServiceException e) {
                    System.err.println("Remote Invocation got: " + e);
                    System.exit(1);
                }
            } else {
                System.err.println("false: proxy.getAPI() got " +
				obj.getClass().getName() +
				" instead of ErrInjManagedService");
                System.exit(1);
            }
        }
    }

    private static void usage(int nodeid) {
        String usage = "Usage: <cmd> [-node n] <svc-name> [-s] " +
			"[kill | fd | mem | proc]\n" +
			"    svc-name:  " + getErrInjServices(nodeid) + "\n" +
			"          -s:  action performed by server thread\n" +
			"       excep:  server throws NullPointerException\n" +
			"        kill:  System.exit(2)\n" +
			"          fd:  open fd's forever\n" +
			"         mem:  allocate memory forever\n" +
			"        proc:  spawn busy threads every 1 sec forever\n" +
			"     default:  server throws exception";
        System.err.println(usage);
        System.exit(1);
    }
    public static void main(String args[]) {

        int nodeid = ServiceManager.LOCAL_NODE;

        if (args.length == 0)
            usage(nodeid);

        String svc_name = null;
        String action = null;
        boolean server = false;
        if (args.length == 1) {
            svc_name = args[0];
        } else {
            // get node svc [-s] [action]
            // or  svc [-s] [action]
            int i = 0;
            if (args[0].equals("-node")) {
                if (args.length < 3)
                    usage(nodeid);
                nodeid = Integer.parseInt(args[1]);
                i = 2;
            }
            if (i >= args.length)
                usage(nodeid);
            svc_name = args[i];
            i++;
            if (i < args.length) {
                if (args[i].equals("-s")) {
                    server = true;
                    i++;
                }
                if (i < args.length)
                    action = args[i];
            }
        }
        svc_name = "ErrInj_" + svc_name;
        errInjClientExample(nodeid, svc_name, server, action);
    }
}
