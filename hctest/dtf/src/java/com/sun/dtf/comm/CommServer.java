package com.sun.dtf.comm;

import java.io.IOException;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;

import com.sun.dtf.comm.rpc.RPCServer;
import com.sun.dtf.exception.CommException;
import com.sun.dtf.logger.DTFLogger;


public class CommServer {
    
    private static DTFLogger _logger = DTFLogger.getLogger(CommServer.class);
    private RPCServer _server = null;
   
    public CommServer(String addr, int port) { 
        _server = new RPCServer(port);
        
        XmlRpcServer xmlRpcServer = _server.getXmlRpcServer();

        XmlRpcServerConfigImpl serverConfig = 
                              (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();

        serverConfig.setKeepAliveEnabled(true);
        serverConfig.setEnabledForExtensions(true);
        serverConfig.setEnabledForExceptions(true);
    }
    
    public int getPort() { 
        return _server.getPort();
    }
    
    public void addHandler(String name, Class handler) throws CommException { 
        try {
            _server.addHandler(name, handler);
        } catch (XmlRpcException e) {
            throw new CommException("Unable to add handler [" + name + "]",e);
        }
    }
    
    public void start() throws CommException { 
        try {
            _server.start();
        } catch (IOException e) {
            throw new CommException("Unable to start RPCServer.",e);
        }
    }
    
    public void shutdown() {
        try { 
            _server.shutdown();
        } catch (Throwable t) { 
            
        }
    }

    public void printStats() {
        if (_server != null) { 
            _logger.info("Number of requests: " + 
             _server.getXmlRpcServer().getWorkerFactory().getCurrentRequests());
        }
    }
}
