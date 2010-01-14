package com.sun.dtf.comm;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.client.AsyncCallback;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcClientRequestImpl;

import com.sun.dtf.DTFProperties;
import com.sun.dtf.NodeInfo;
import com.sun.dtf.NodeState;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.component.Attrib;
import com.sun.dtf.actions.protocol.Connect;
import com.sun.dtf.actions.util.DTFActionCallback;
import com.sun.dtf.comm.rpc.ActionResult;
import com.sun.dtf.comm.rpc.DTFAsyncActionCallback;
import com.sun.dtf.comm.rpc.DTFTypeFactory;
import com.sun.dtf.comm.rpc.XmlRpcDtfHttpTransportFactory;
import com.sun.dtf.exception.CommException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.PropertyException;
import com.sun.dtf.logger.DTFLogger;
import com.sun.dtf.state.DTFState;

public class CommClient {
    
    private static DTFLogger _logger = DTFLogger.getLogger(CommClient.class);
   
    private XmlRpcClient _client = null;
    private URL _url = null;
    
    private static HashMap _attribs = new HashMap();
    
    public CommClient(String caddr, 
                      int cport) throws CommException {
        XmlRpcClientConfigImpl rpcConfig = new XmlRpcClientConfigImpl();
        try {
            _url = new URL("http://" + caddr + ":" + cport + "/xmlrpc");
            rpcConfig.setServerURL(_url);
        } catch (MalformedURLException e) {
            throw new CommException("Bad URL.",e);
        }
        
        _client = new XmlRpcClient();

        XmlRpcDtfHttpTransportFactory transport = 
                                    new XmlRpcDtfHttpTransportFactory(_client);
        
        _client.setTransportFactory(transport);
        _client.setTypeFactory(new DTFTypeFactory(_client));
        _client.setConfig(rpcConfig); 
    }
    
    public ActionResult sendAction(String id, Action action) throws CommException {
        try {
            XmlRpcRequest pRequest = 
                new XmlRpcClientRequestImpl(_client.getClientConfig(),
                                            "Node.execute",
                                            new Object[] {id, action});
                                        
            return (ActionResult) _client.getTransportFactory().
                                           getTransport().sendRequest(pRequest);
            
            //return (ActionResult)_client.execute("Node.execute", 
            //                                     new Object[] {id, action});
        } catch (XmlRpcException e) {
            throw new CommException("Error executing action.",e);
        }
    }
   
    public void sendAsyncAction(String id,
                                Action action,
                                DTFActionCallback callback) 
           throws CommException {
        try {
            DTFAsyncActionCallback asyncCallback = new 
                                           DTFAsyncActionCallback(callback);
            
            _client.executeAsync("Node.execute", 
                                 new Object[] {id, action},
                                 asyncCallback);
        } catch (XmlRpcException e) {
            throw new CommException("Error executing action.",e);
        }
    }
    
    public class Callback implements AsyncCallback {
       
        private long _start = 0;
        private NodeInfo _info = null;
        
        public Callback(NodeInfo info) { 
            _start = System.currentTimeMillis();
            _info = info;
        }

        public void handleError(XmlRpcRequest request, Throwable error) {
            _logger.info("Heartbeat missed for node: " + _info +  
                         " releasing locked components.");
            try {
                NodeState.getInstance().removeNode(_info);
            } catch (DTFException e) {
                _logger.error("Error unregistering node.",e);
            }
        }

        public void handleResult(XmlRpcRequest request, Object result) {
            if (_logger.isDebugEnabled())
                _logger.debug("Heartbeated in " + 
                              (System.currentTimeMillis() - _start) + "ms.");
        } 
    }

    /*
     * Async heartbeats for the moment till I finally get around to making jetty
     * the xmlrpc servlet server. At that point there shouldn't be such an 
     * issue as there currently is when under load.
     */
    public Boolean heartbeat(NodeInfo info) {
        Callback callback = new Callback(info);
        try {
            _client.executeAsync("Node.heartbeat", new Object[]{}, callback);
            return new Boolean(true);
        } catch (XmlRpcException e) {
            return new Boolean(false);
        }
    }

    /*
     * Synchrnous call still used at lock time.
     */
    public Boolean heartbeat() {
        try {
            _client.execute("Node.heartbeat", new Object[]{});
            return new Boolean(true);
        } catch (XmlRpcException e) {
            return new Boolean(false);
        }
    }
    
    public void shutdown() { }

    public void register() throws CommException {
        String id = Action.getConfig().getProperty(DTFProperties.DTF_NODE_NAME);
        
        int port;
        try {
            port = Action.getConfig().getPropertyAsInt(DTFProperties.DTF_LISTEN_PORT);
        } catch (PropertyException e) {
            throw new CommException("Unable to get listening port.",e);
        }
        
        Connect connect;
        try {
            connect = new Connect(id);
            
            /*
             * Put default Attribs into connect message.
             */
            Iterator objs = _attribs.values().iterator();
            while (objs.hasNext()) { 
                Attrib attrib = (Attrib) objs.next();
                connect.addAttrib(attrib);
            }
            
            String laddr = Action.getConfig().getProperty(DTFProperties.DTF_LISTEN_ADDR);
            connect.setAddress(laddr);
            connect.setPort(port); 
        } catch (DTFException e) {
            throw new CommException("Unable to register component.",e);
        } 
        
        try {
            ActionResult result = (ActionResult)
                                  _client.execute("Node.register",
                                                  new Object[] {connect});
            result.execute();
            // update timer to start of now
            Comm.heartbeat();
            _logger.info("Registering component at " + _url);
        } catch (XmlRpcException e) {
            throw new CommException("Error executing action.",e);
        } catch (DTFException e) {
            throw new CommException("Error executing action.",e);
        }
    }
    
    public void unregister(DTFState state) throws CommException {
        String id = state.getConfig().getProperty(DTFProperties.DTF_NODE_NAME);
        
        if (id == null)
            return;
        
        Connect connect;
        try {
            connect = new Connect(id);
        } catch (DTFException e) {
            throw new CommException("Unable to register component.",e);
        } 
        
        try {
            ActionResult result = (ActionResult)
                                  _client.execute("Node.unregister",
                                                  new Object[] {connect});
            result.execute();
            _logger.info("Unregistering component from " + _url);
        } catch (XmlRpcException e) {
            throw new CommException("Error executing action.",e);
        } catch (DTFException e) {
            throw new CommException("Error executing action.",e);
        }
    }
    
    public static void addAgentAttribute(String name, String value) { 
        addAgentAttribute(name, value, false);
    }
    
    /**
     * This method will add attributes that are later processed at lock time by
     * the DTFC in order to figure out if a component with the characteristics 
     * you mentioned is available.
     * 
     * @param name name of the attribute to register
     * @param value value of the attribute to register
     * @param isTestProperty this boolean identifies if the the attribute is to 
     *                       be recorded on the DTFX side as a test property. 
     *                       Making it available to all other subsequent tags in 
     *                       the test execution.
     */
    public static void addAgentAttribute(String name, 
                                         String value, 
                                         boolean isTestProperty) { 
        if (_attribs.containsKey(name)) 
            _logger.warn("Overwriting [" + name + "] client attribute.");
       
        _attribs.put(name, new Attrib(name, value, isTestProperty));
    }

    public void printStats() {
        if (_client != null) { 
            _logger.info("Number of requests: " + 
            _client.getWorkerFactory().getCurrentRequests());
        } 
    }
}
