package com.sun.dtf.comm.rpc;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcStreamServer;
import org.apache.xmlrpc.webserver.WebServer;

import com.sun.dtf.logger.DTFLogger;



public class RPCServer extends WebServer {
    
    private static DTFLogger _logger = DTFLogger.getLogger(RPCServer.class);

    private final int LISTEN_STARTING_PORT = 20000;
    private int _port = -1;
    
    private boolean _stopping = false;
   
    public RPCServer(int port) {
        super(port);
        _port = port;
        setParanoid(false);
    }

    protected XmlRpcStreamServer newXmlRpcStreamServer() {
        XmlRpcStreamServer xmlRpcServer = super.newXmlRpcStreamServer();

        xmlRpcServer.setTypeFactory(new DTFTypeFactory(xmlRpcServer));
        xmlRpcServer.setTypeConverterFactory(new DTFConverterFactory());
        
        return xmlRpcServer;
    }
    
    public int getPort() { 
        return _port;
    }
    
    public synchronized void shutdown() {
        _stopping = true;
        super.shutdown();
    }
   
    /*
     * Override the existing logging methods so XMLRpcServer will log using the
     * DTF logger.
     */
    public synchronized void log(String message) {
        _logger.info(message);
    }

    /*
     * Special situation: do not log errors on shutdown because they're known
     * to be InterruptedExceptions that are of no importance. Keep it as a debug
     * log so we can easily track things when debugging problems.
     */
    public void log(Throwable error) {
        if (!_stopping) 
            _logger.error("Error.",error);
        else
            _logger.debug("Error during shutdown.",error);
    }

    /*
     * Overriding so we can check for the next available port in the case of 
     * some specific DTF components.
     */
    protected ServerSocket createServerSocket(int port, 
                                              int backlog, 
                                              InetAddress addr) 
                           throws IOException {
        if (port == -1) {
            port = LISTEN_STARTING_PORT;
            
            ServerSocketChannel ssc = ServerSocketChannel.open();
            InetSocketAddress isa = null;
            while (true) { 
                try {
                    isa = new InetSocketAddress(port);
                    ssc.socket().bind(isa, backlog);
                    _port = port;
                    _logger.info("Listening at " + isa);
                    return ssc.socket();
                } catch (BindException e) { 
                    if (_logger.isDebugEnabled())
                        _logger.debug("Trying other port: " + port);
                    
                    // retrying on next available port
                    port++;
                }
            }
        } else {
            try { 
                _port = port;
                return super.createServerSocket(port, backlog, addr);
            } finally { 
                _logger.info("Listening at " + port);
            }
        }
    }
    
    public void addHandler(String name, Class handler) throws XmlRpcException { 
        PropertyHandlerMapping phm = new PropertyHandlerMapping();
        phm.setTypeConverterFactory(getXmlRpcServer().getTypeConverterFactory());
        phm.addHandler(name, handler);
        getXmlRpcServer().setHandlerMapping(phm);
    }
}
