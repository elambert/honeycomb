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



package com.sun.honeycomb.spreader;

import java.util.logging.Logger;
import java.io.IOException;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.util.PowerController;
import com.sun.honeycomb.util.PowerControllerFactory;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.util.Exec;


/**
 * This class manages the node power service
 *
 * @version $Id: NodeMonitor.java 10855 2007-05-19 02:54:08Z bberndt $
 */
public class NodeMonitor {

    private static final Logger logger =
        Logger.getLogger(NodeMonitor.class.getName());
    
    /*
     * Tuneable to enable power cycle timeout.
     * 0 to disable node monitoring, value in sec as timeout.
     * If this property does not exist, node monitoring is disabled.
     */
    private static final String HC_POWERCYCLE_TIMEOUT = 
        "honeycomb.cell.node_monitor.powercycle_timeout";
    
    /*
     * Property to give the number of expected nodes in the cell.
     */
    private static final String HC_NUM_NODES = "honeycomb.cell.num_nodes";
    
    /*
     * Minimum possible value for powercycle timeout.
     * Needs to take boot time into account.
     * in sec.
     */
    private static final long MIN_POWERCYCLE_TIMEOUT = (5 * 60);
    
    /*
     * how often do we poll for the nodes status.
     * in msec.
     */
    private static final int POLL_INTERVAL = (60 * 1000);
    
    /*
     * If a node is still dead after this timeout, we power cycle it
     * in ms.
     */
    private long pwrTimeout;
    
    /*
     * handle to power controller.
     */
    private PowerController pwrCtrl;
    
    /*
     * background task to monitor and power cycle nodes.
     */
    private PowerCycleTask pwrTask;
    
    /*
     * Record starting time of nodes detected dead in the cluster.
     */
    private long deadNodes[];
    
    /*
     * handle to the cell config
     */
    private ClusterProperties cellConfig;

    /*
     * Only once instance of node monitor must be running
     */
    private static NodeMonitor instance = null;
    
    public static synchronized NodeMonitor getInstance() {
        if (instance == null) {
            instance = new NodeMonitor();
        }
        return(instance);
    }
    
    private NodeMonitor() {
        
        cellConfig = ClusterProperties.getInstance();
        deadNodes = new long[CMM.MAX_NODES];
        for (int i = 0; i < deadNodes.length; i++) {
            deadNodes[i] = 0;
        }
        
        pwrCtrl = PowerControllerFactory.getPowerController();
        if (pwrCtrl == null) {
            logger.severe("cannot find power ctrl - node monitor disabled");
            return;
        }
        
        pwrTimeout = 0;
        try {
            String foo = cellConfig.getProperty(HC_POWERCYCLE_TIMEOUT);
            if (foo != null) {
                pwrTimeout = Integer.parseInt(foo);
                if (pwrTimeout > 0 && pwrTimeout < MIN_POWERCYCLE_TIMEOUT) {
                    logger.warning("Power cycle timeout out of range: " + 
                                   pwrTimeout + " secs - resetting to " +
                                   MIN_POWERCYCLE_TIMEOUT + " secs"
                                   );
                    pwrTimeout = MIN_POWERCYCLE_TIMEOUT;
                }
            }
        } catch (NumberFormatException e) {
            logger.warning("failed to parse " + HC_POWERCYCLE_TIMEOUT + ": " + e);
        }
         
        if (pwrTimeout == 0) {
            logger.info("Node monitor power control is disabled.");
            pwrTask = null;
        } else {
            logger.info("Node monitor: power cycle dead nodes after " +
                        pwrTimeout + " seconds"
                        );
            pwrCtrl.start();
            pwrTimeout *= 1000; // in msec
            pwrTask = new PowerCycleTask();
        }
    }
    
    /*
     * Background thread to monitor the state of all nodes in the cluster
     * and power cycle the ones that are not online for more than the 
     * power cycle timeout.
     */
    private class PowerCycleTask implements Runnable {
        
        boolean isRunning;
        
        PowerCycleTask() {
            isRunning = true;
            Thread thr = new Thread(this, "NodeMonitor thread");
            thr.setDaemon(true);
            thr.start();
        }
        
        public void run() {            
            assert (pwrTimeout != 0);
            try {
                while (isRunning) {
                    try {
                        Thread.currentThread().sleep(POLL_INTERVAL);
                        work();
                    } catch (InterruptedException ie) {
                        logger.warning("node monitor thread interrupted");
                        break;
                    }
                }
            } finally {
                logger.severe("node monitor thread exiting");
            }
        }
        
        void shutdown() {
            isRunning = false;
        }
        
        private void work() {
            /*
             * Figure out how many nodes are expected to be online.
             */
            int nodesCount = CMM.MAX_NODES;
            try {
                String foo = cellConfig.getProperty(HC_NUM_NODES);
                if (foo != null) {
                    nodesCount = Integer.parseInt(foo);
                }
            } catch (NumberFormatException e) {
                logger.warning("failed to parse " + HC_NUM_NODES + 
                               " using value " + nodesCount + " : " + e
                               );
            }
            
            NodeMgrService.Proxy proxy;
            proxy = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            
            long now = System.currentTimeMillis();
            Node[] nodes = proxy.getNodes();
            assert(nodesCount <= nodes.length);
            
            /*
             * For each node,
             * if it has been detected dead for more than timeout and
             * cannot be reached through rsh, then powercyle the node.
             */
            for (int i = 0; i < nodes.length; i++) {
                
                if (i >= nodesCount || nodes[i].isAlive()) {
                    deadNodes[i] = 0;
                    
                } else if (deadNodes[i] == 0) {
                    deadNodes[i] = now;
                    
                } else if ((now - deadNodes[i]) > pwrTimeout) {
                    
                    if (!isAccessible(nodes[i].getAddress())) {
                        
                        logger.warning("Power cycling node " + nodes[i]);
                        pwrCtrl.powerCycle(nodes[i].nodeId());
                        deadNodes[i] = 0;

                    } else {
                        logger.warning("Node " + nodes[i] + 
                                       " is up but not running software"
                                       );
                    }
                }
            }
        }
        
        private boolean isAccessible(String hostname) {
            String[] cmd = new String[] {
                "/usr/bin/rsh",
                "-n",
                hostname,
                "/usr/bin/uptime"
            };
            try {
                if (Exec.exec(cmd, null, logger) == 0) {
                    return true;
                }
            } catch (IOException ie) {
                logger.warning("failed to rsh to " + hostname);
            }
            return false;
        }            
    }    
}
