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


package com.sun.honeycomb.alert;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.ManagedServiceException;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Hashtable;
import java.io.IOException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;



/** *  Alert Server 
 */
public class AlerterServer implements AlerterServerIntf, PropertyChangeListener {

    private static transient final Logger logger = 
        Logger.getLogger(AlerterServer.class.getName());

    private final static String PROPERTY_CELL_NUM_NODES = "honeycomb.cell.num_nodes";
    private final static int delay = 1000;

    static private AlertCorrelationEngine engine = null;

    private volatile boolean              keepRunning;
    private Thread                        thr;
    // Config
    private ClusterProperties             config;
    private volatile int                  numNodes;

    static public AlertCorrelationEngine getEngine() {
        return engine;
    }


    public AlerterServer() {

        keepRunning = true;
        thr = Thread.currentThread();

        config = ClusterProperties.getInstance();
        config.addPropertyListener(this);

        readNumNodes();
    }

    public ManagedService.ProxyObject getProxy () {
        return new Proxy();
    }


    /**
     * Called by CM (Service) when we transition back from RUNNING to READY
     * - Perform uninitialization done in syncRun(),
     * - Shutdown the service-- in our case we only need to return from run()
     */
    public void shutdown () {
        keepRunning = false;
        thr.interrupt();
        boolean stopped = false;
        while (!stopped) {
            try {
                thr.join();
                stopped = true;
            } catch (InterruptedException ignored) {
                
            }
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.info("AlerterServer now STOPPED");
        }        
        engine = null;
    }

    /**
     * Execute initialization that needs to be done before we reach 
     * the RUNNING state, so that our dependents are satisfied.
     */
    public void syncRun() {
        engine = new AlertCorrelationEngine();
    }

    /**
     * RMI call -
     * Notify the clients who subscribed for this property
     *
     */
    public void notifyClients(String prop, String msg) 
        throws AlertException, ManagedServiceException {
        if (engine == null) {
            throw new AlertException("engine not set");
        }
        engine.notifyClients(prop, msg);
    }
    
    /**
     * RMI call -
     * Return the alert properties.
     */
    public AlertApi.AlertViewProperty getViewProperty() 
        throws ManagedServiceException {
        if (engine == null) {
            return(null);
        }
        AlertApi.AlertViewProperty view;
        view = new AlertApiImpl.AlertViewPropertyImpl(engine);
        return view;
    }
    
    /**
     * @see AlerterServerIntf#getNodeAlertProperties(int, String[])
     */
    public Hashtable getNodeAlertProperties(int nodeId, String[] props) 
        throws AlertException, ManagedServiceException {

        Hashtable alertObjMap = new Hashtable();

        if (engine == null) {
            return(null);
        }

        /*
         * Traverse the list of alert properties we are
         * interested in and push them into a hashtable
         */
	
	AlertApiImpl.AlertViewPropertyImpl alertView = 
	    new AlertApiImpl.AlertViewPropertyImpl(engine);
        for (int j=0; j<props.length; j++) {
            String prop = "";
            AlertApi.AlertObject ob;
            try {
                prop = "root." +nodeId +"." +props[j];
		if (alertView.isAlertPropertyPresent(prop) == false)
		    continue;
                ob = alertView.getAlertProperty(prop);
                alertObjMap.put(prop, ob); 
            } catch (AlertException ae) {
                    logger.log(Level.WARNING, 
			"Failure retrieving AlertObject for property '" + prop + "'", ae);
		    throw ae;
            }
        }
        return alertObjMap;
    }

    
    /**
     * @see AlerterServerIntf#getClusterAlertProperties(Node[], String[])
     */
    public Hashtable[] getClusterAlertProperties(Node[] nodes, String[] props) 
        throws AlertException, ManagedServiceException {
        if (engine == null) {
            return(null);
        }

        Hashtable[] alertObjMap = new Hashtable[nodes.length];
        int nodeId;
	
	AlertApiImpl.AlertViewPropertyImpl alertView = 
	    new AlertApiImpl.AlertViewPropertyImpl(engine);
        for (int i=0 ; i<nodes.length; i++) {
            if (!nodes[i].isAlive()) {
                 continue;
            }
	    
            nodeId = nodes[i].nodeId();
	    
            /*
             * Traverse the list of alert properties we are
             * interested in and push them into a hashtable
             */
            alertObjMap[i] = new Hashtable();
            for (int j=0; j<props.length; j++) {
                String prop = "";
                AlertApi.AlertObject ob;
                try {
                    prop = "root." +nodeId +"." +props[j];
		    if (alertView.isAlertPropertyPresent(prop) == false)
			continue;
                    ob = alertView.getAlertProperty(prop);
                    alertObjMap[i].put(prop, ob);
                } catch (AlertException ae) {
                    logger.log(Level.WARNING, 
			"Failure retrieving AlertObject for property '" + prop + "'", ae);
		    throw ae;
                }
            } 
        }
        return alertObjMap;
    }

    /**
     * RMI call -
     * Return the alert object.
     */
    public AlertApi.AlertObject getProperty(String property) 
        throws AlertException, ManagedServiceException {
        if (engine == null) {
            return(null);
        }

        AlertApi.AlertObject ob;
        ob = new AlertApiImpl.AlertViewPropertyImpl(engine).getAlertProperty(property); 
        if (ob == null) {
            logger.warning("RMI call getAlertProperty returned a null Alert object");
            throw new AlertException("RMI call getAlertProperty returned a null Alert Object");
        } 
        return ob;
    }

    //
    // Entry point:
    // -----------
    //
    // - Update Tree of properties
    // - Publish Mailbox.
    //
    public void run() {

        int nodeIndex = -1;

        while (keepRunning) {

            synchronized (AlerterServer.class) {
                nodeIndex = (nodeIndex == (numNodes - 1)) ? 0 : ++nodeIndex;
            }

            try {
                engine.updateTree(false, nodeIndex);
            } catch(AlertException ae) {
                logger.log(Level.SEVERE,
                           "cannot update tree of properties",
                           ae);
            }

            try {
                Thread.sleep (delay);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    public void propertyChange(PropertyChangeEvent event) {
        String prop = event.getPropertyName();
        if (prop.equals(PROPERTY_CELL_NUM_NODES)) {
            readNumNodes();
        }
    }

    private void readNumNodes() {
        synchronized (AlerterServer.class) {
            String sNumNodes = config.getProperty(PROPERTY_CELL_NUM_NODES);
            if (sNumNodes == null) {
                logger.severe("property " + PROPERTY_CELL_NUM_NODES + 
                              " does not exist, default to 16");
                numNodes = 16;
                return;
            }
            try {
                numNodes = Integer.parseInt(sNumNodes);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("AlerterServer configured with " +
                                numNodes + " nodes");
                }
            } catch (NumberFormatException  nfe) {
                logger.severe("property " + PROPERTY_CELL_NUM_NODES + 
                              "is invalid, default to 16");
                numNodes = 16;
            }
        }
    }
}
