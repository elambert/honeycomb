package com.sun.dtf.comm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.sun.dtf.DTFNode;
import com.sun.dtf.DTFProperties;
import com.sun.dtf.NodeInfo;
import com.sun.dtf.NodeState;
import com.sun.dtf.actions.Action;
import com.sun.dtf.comm.rpc.ControllerNode;
import com.sun.dtf.comm.rpc.Node;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.CommException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.PropertyException;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.util.HostUtils;
import com.sun.dtf.util.ThreadUtil;

public class Comm extends Thread {
   
    private static DTFLogger _logger = DTFLogger.getLogger(Comm.class);
  
    /*
     * 5s  heart beat all registered components every 5s, now if a heart beat
     * takes 50ms then in 5s we are able to heart beat at least 1000 active 
     * components and for now that "limitation" is acceptable.
     */
    private final long HEARTBEAT_INTERVAL       = 5000; 
    
    /*
     * Maximum amount of time that we'll tolerate without heart beating a 
     * component is 20s
     */
    private final long HEARTBEAT_TIMEOUT        = 20000; 
    
    private CommServer _server = null;

    private boolean _running = true;
    
    public Comm(Config config) throws CommException { 
        CommClient.addAgentAttribute("type", DTFNode.getType());
        CommClient.addAgentAttribute("os", System.getProperty(DTFProperties.DTF_NODE_OS));
        CommClient.addAgentAttribute("arch", System.getProperty(DTFProperties.DTF_NODE_OS_ARCH));
        CommClient.addAgentAttribute("version", System.getProperty(DTFProperties.DTF_NODE_OS_VER));
        
        String laddr = config.getProperty(DTFProperties.DTF_LISTEN_ADDR);
        
        int lport = -1;
        try {
            lport = config.getPropertyAsInt(DTFProperties.DTF_LISTEN_PORT,-1);
        } catch (PropertyException e) {
            throw new CommException("Port number bad format.",e);
        }
        
        if (laddr == null) { 
            laddr = HostUtils.getHostname();
            config.setProperty(DTFProperties.DTF_LISTEN_ADDR, laddr);
        }
        _logger.info("Host address [" + laddr + "]");
        
        String type = config.getProperty(DTFProperties.DTF_NODE_TYPE);
        
        if (type == null) 
            throw new CommException(DTFProperties.DTF_NODE_NAME + 
                                    " can not be null.");
       
        _server = new CommServer(laddr,lport);
        
        try {
            _server.start();
        } catch (CommException e) {
            throw new CommException("Unable to start CommServer.", e);
        }
        
        start();
        
        // Because the port can be selected by the RPCServer when the chosen 
        // one is not available
        config.setProperty(DTFProperties.DTF_LISTEN_PORT,""+_server.getPort());
        
        if (type.equalsIgnoreCase("dtfc")) {

            // DTFC node
            _logger.info("DTFC Setup."); 
            // Controller node with Controller handler available
            _server.addHandler("Node", ControllerNode.class); 
            
            Action.getState().disableReplace();
        } else if (type.equalsIgnoreCase("dtfa")) { 
            // DTFA node
            _logger.info("DTFA Setup."); 
            // heart beat handler for DTFC to be able to check up on each agent
            _server.addHandler("Node", Node.class); 
        } else { 
            // DTFX node
            _logger.info("DTFX Setup."); 
            // Any other DTF node has the basic heart beat handler up 
            _server.addHandler("Node", Node.class); 
        }
        
        if (!type.equalsIgnoreCase("dtfc")) {
            String caddr = config.getProperty(DTFProperties.DTF_CONNECT_ADDR,
                                              DTFProperties.DTF_CONNECT_ADDR_DEFAULT);
            int cport = -1;
            try {
                cport = config.getPropertyAsInt(DTFProperties.DTF_CONNECT_PORT,
                                                DTFProperties.DTF_CONNECT_PORT_DEFAULT);
            } catch (PropertyException e) {
                throw new CommException("Port number bad format.",e);
            }
      
            CommClient client  = new CommClient(caddr, cport);

            /*
             * only DTFA's will register automatically to the DTFC 
             */
            if (type.equalsIgnoreCase("dtfa")) { 
                client.register();
            }
            
            _clients.put("dtfc", client);
        }
    }
  
    private static long lastHeartbeat = System.currentTimeMillis();
    public static void heartbeat() { 
        lastHeartbeat = System.currentTimeMillis();
    }
   
    private boolean keepAlive() {
        long heartbeat = (System.currentTimeMillis() - lastHeartbeat);
        if (heartbeat > HEARTBEAT_TIMEOUT) {
            return false;
        }
        return true;
    }
    
    public void run() {
        
        long lastUpdate = System.currentTimeMillis();
        
        while (_running) { 

            if (_logger.isDebugEnabled()) { 
                if (System.currentTimeMillis() - lastUpdate > 10000) { 
                    lastUpdate = System.currentTimeMillis();
                    Runtime rt = Runtime.getRuntime();
                    int MB = 1048567;
                    _logger.debug("JVM MEMORY MAX(MB): " + (rt.maxMemory()/MB) +
                                  " FREE(MB): " + (rt.freeMemory()/MB) +
                                  " TOTAL(MB): " + (rt.totalMemory()/MB));
                }
            }
            
            if (DTFNode.getType().equals("dtfc")) { 
                /*
                 * Heart-beating all registered components.
                 */
                NodeState ns = NodeState.getInstance();
                ArrayList nodes = ns.getRegisteredNodes();
               
                for (int i = 0; i < nodes.size(); i++) { 
                    NodeInfo node = (NodeInfo)nodes.get(i);
                    
                    Boolean result = node.getClient().heartbeat(node);
                    if (!result.booleanValue()) {
                        /*
                         * heart beat missed the component must have died lets 
                         * remove the node from the registeredNodes, which in turn 
                         * will unlock any components locked by this agent.
                         */
                        try {
                            // make sure that the component didn't just go away
                            if (ns.getNodeInfo(node.getId()) != null) { 
                                _logger.info("Heartbeat missed for node: " + 
                                             node + ", releasing locked components.");
                                    ns.removeNode(node);
                            } else { 
                                _logger.info("Just avoided confusion.");
                            }
                        } catch (DTFException e) {
                            _logger.error("Unable to unregister component " + 
                                          node,e);
                        }
                    }
                }
            } else { 
                /*
                 * Keep Alive check for all nodes except the DTFC, this is used by
                 * DTF nodes to know when the DTFC has gone away. The way it works 
                 * is that the DTFC will heart beat each node every 5s but if he 
                 * fails to heart beat 2 in a row then the component will just 
                 * shutdown.
                 */
                if (DTFNode.getType().equals("dtfx") && 
                    !Action.getComponents().hasComponents()) {
                    // if you're a dtfx and you don't have registered components
                    // then you have no reason to care about heartbeats
                } else { 
                    if (!keepAlive()) {
                        _logger.info("DTFC failed to heartbeat for " +
                                     HEARTBEAT_TIMEOUT/1000 + 
                                     "s, shutting down node.");
                        _running = false; 
                        return;
                    }
                }
            }
            ThreadUtil.pause(HEARTBEAT_INTERVAL);
        }
    }

    public boolean isUp() {
        return _running;
    }

    public void shutdown() {
        if (_server != null)
            _server.shutdown();
        
        Iterator iterator = _clients.keySet().iterator();
        
        while (iterator.hasNext()) { 
            String id = (String)iterator.next();
            CommClient c = (CommClient)_clients.get(id);
            c.shutdown();
        }
       
        _running = false;
    }

    private static HashMap _clients = new HashMap();
    public static void addClient(String id, CommClient client) { 
        _clients.put(id, client);
    }

    public Action sendAction(String id, Action action) throws DTFException {
        CommClient client = getCommClient(id);
        
        if (client == null)
            client = getCommClient("dtfc");
       
        return  client.sendAction(id,action);
    }
    
    public CommClient getCommClient(String id) { 
        return (CommClient)_clients.get(id); 
    } 
}
