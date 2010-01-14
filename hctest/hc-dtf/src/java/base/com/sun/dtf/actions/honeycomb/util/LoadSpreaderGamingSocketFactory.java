package com.sun.dtf.actions.honeycomb.util;

import java.io.IOException; 
import java.net.InetAddress; 
import java.net.UnknownHostException; 
import java.net.Socket; 
 
import org.apache.commons.httpclient.protocol.Protocol; 
import org.apache.commons.httpclient.protocol.DefaultProtocolSocketFactory; 

/**
 * This class allows for testing code to modify the normal socket creation code
 * in the honeycomb client libraries to force the run of load against a 
 * specific node in the ring. This is very useful for targeted performance
 * testing like the smoke performance test.
 * 
 * NOTE: unfortunately right now there is no way to use this to point different
 *       operations and different nodes at the exact SAME TIME, it just wont
 *       work for the time being.
 *
 */
public class LoadSpreaderGamingSocketFactory extends DefaultProtocolSocketFactory{ 
 
    private static LoadSpreaderGamingSocketFactory _lsf = null;

    private int node; 
 
    private LoadSpreaderGamingSocketFactory(int node){ 
        this.node = node; 
    } 
    
    private int getNode() { 
        return node;
    }
    
    private void setNode(int node) { 
        this.node = node;
    }
     
    public synchronized static void registerSocketFactory(int node,
                                                          String host,
                                                          int port) { 
        if (_lsf == null) { 
            /*
             * if we're not gaming the load and have not ever setup the gaming
             * socket factory then return immediately so that we have no 
             * overhead on usual access.
             */
            if (node == -1)
                return;
            
            _lsf = new LoadSpreaderGamingSocketFactory(node); 
            Protocol protocol = new Protocol("http", _lsf, port); 
            Protocol.registerProtocol("http", protocol); 
        } 
        
        if (_lsf.getNode() != node) { 
            _lsf.setNode(node);
            NVOAPool.removeNVOA(host, port);
        }
    } 
   
    private static int maxTries = 10000; 
    public static ThreadLocal desiredNode = new ThreadLocal(); 
 
    public Socket createSocket(String host, int port) 
        throws IOException, UnknownHostException { 
        StringBuffer sb = null; 
        int target = node; 
        
        if (node == -1) {
            return super.createSocket(host, port);
        }
            
        if (desiredNode.get() != null) 
            target = ((Integer)desiredNode.get()).intValue(); 
        
        for (int i = 0; i < maxTries; i++){ 
            int localPort = ((int)(Math.random() * 1024 * 3)) * 16 + 2048 + target -1; 
            try{ 
                return new Socket(host, port, InetAddress.getLocalHost(), localPort); 
            } 
            catch (java.net.BindException e){ 
                if (sb == null) 
                    sb = new StringBuffer("Unable to bind to " + localPort); 
                else 
                    sb.append(", " + localPort); 
            } 
        } 
        throw new IOException(sb.toString()); 
    }; 
 
    // connection pooling fails without these methods 
    public boolean equals(Object obj) { 
        return obj != null && DefaultProtocolSocketFactory.class.isAssignableFrom(obj.getClass()); 
    } 
    
    public int hashCode() { 
        return DefaultProtocolSocketFactory.class.hashCode(); 
    } 
} 
 
