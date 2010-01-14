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



package com.sun.honeycomb.cm.jvm_agent;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.nio.channels.SocketChannel;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.Hashtable;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.net.SocketException;

/**
 * SAP - Service Access Point
 */
public class CMSAP implements java.io.Serializable {

    static final transient Logger logger = Logger.getLogger(CMSAP.class.getName());
    static private transient HashMap cache = new HashMap();

    final String host;
    final int port;
    final int expire;
    int juid;
    int suid;
    transient Sap sap;

    public CMSAP(String host, int port, int juid, int suid, int timeout) {
        this.host = host;
        this.port = port;
        this.suid = suid;
        this.juid = juid;
        this.expire = timeout;
        sap = null;
    }

    CMSAP(SocketChannel sock, int timeout) throws IOException {
        sap = new Sap(sock, timeout);
        port = sock.socket().getPort();
        host = sock.socket().getInetAddress().getHostAddress();
        expire = timeout;
    }

    CMSAP(SocketChannel sock) throws IOException {
        sap = new Sap(sock);
        port = sock.socket().getPort();
        host = sock.socket().getInetAddress().getHostAddress();
        expire = 0;
    }

    CMSAP duplicate() {
        return(new CMSAP(host, port, juid, suid, expire));
    }

    Object call(Method method, Object args[]) 
        throws IOException, ClassNotFoundException 
    {
        if (sap != null) {
            String error = "ClusterMgmt - already connected ";
            throw new RuntimeException(error + sap);
        }
        sap = getFromCache(host, juid);
        while (sap != null) {
            if (sap.port != port) {
                logger.fine("ClusterMgmt - purge cached connection host "
                            + host + " juid " + juid);
                close();
            } else {
                sap.arm(expire);
                try {
                    sap.reset();
                    sap.out.writeInt(juid);
                    sap.out.writeInt(suid);
                    sap.out.writeInt(method.hashCode());
                    sap.out.writeObject(args);
                    sap.out.flush();
                    return sap.in.readObject();
                
                } catch (Exception e) {
                    logger.fine("ClusterMgmt - cached connection expired" 
                                + " host " + host + " juid " + juid 
                                + " exception " + e);
                    close();
                }
            }
            sap = getFromCache(host, juid);
        }
        sap = new Sap(host, port, expire);
        sap.out.writeInt(juid);
        sap.out.writeInt(suid);
        sap.out.writeInt(method.hashCode());
        sap.out.writeObject(args);
        sap.out.flush();
        return sap.in.readObject();
    }

    void accept() throws IOException {
        sap.arm();
        sap.sc.configureBlocking(true);
        juid = sap.in.readInt();
        suid = sap.in.readInt();
    }

    boolean accept(Selector selector, long timeout) throws IOException {
        /* TODO - temporary select */
        return false;
    }
    
    void disconnect() throws IOException {
        if (sap != null) {
            sap.arm();
            putInCache();
            sap = null;
        }
    }

    void close() {
        if (sap != null) {
            try {
                sap.sc.socket().shutdownInput();
                sap.sc.socket().shutdownOutput();
            } catch (Exception e) { 
                // silently ignore 
            }
            try {
                sap.sc.close();
            } catch (Exception e) { 
                // silently ignore
            }
            sap = null;
        }
    }

    void flush() throws IOException {
        if (sap != null) {
            sap.out.flush();
        }
    }

    void nack(Throwable e) {
        try {
            sap.out.writeObject(e);
        } catch (Exception x) {
            logger.severe("ClusterMgmt - serialize " + e + " got " + x);
        }
    }

    SocketChannel channel() {
        if (sap != null) {
            return sap.sc;
        }
        return null;
    }

    ObjectOutputStream out() {
        if (sap != null) {
            return sap.out;
        }
        return null;
    }
    
    ObjectInputStream in() {
        if (sap != null) {
            return sap.in;
        } 
        return null;
    }

    boolean isConnected() {
        return sap != null;
    }

    boolean hasExpired() {
        if (sap != null && expire != 0) {
            return sap.hasExpired();
        }
        return false;
    }

    public String toString() {
        return host + ":" + port + "/jvm" + juid + ":" + suid + 
        ((sap == null)? 
             " !connected":" remaining: " + sap.remainingTime());
    }

    protected void finalize() {
        if (sap != null) {
            logger.warning("ClusterMgmt - SAP connected " + sap.sc);
        }
        close();
    }

    /**
     * transient and cacheable part of a Service Access Point
     */

    private Sap getFromCache(String host, int juid) {
        Object key = host + "/" + juid;
        Sap sap = null;
        synchronized (cache) {
            ArrayList list = (ArrayList)cache.get(key);
            if (list != null) {
                for (int i = 0, n = list.size(); i < n; i++) {
                    sap = (Sap) list.get(i);
                    if (sap != null) {
                        list.set(i, null);
                        break;
                    }
                }
            }
        }
        return sap;
    }
    
    private void putInCache() {
        Object key = host + "/" + juid;
        synchronized (cache) {
            ArrayList list = (ArrayList)cache.get(key);
            if (list == null) {
                list = new ArrayList();
                cache.put(key, list);
            }
            int idx = list.indexOf(null);
            if (idx != -1) {
                list.set(idx, sap);
            } else {
                list.add(sap);
            }
        }
        sap = null;
    }
    

    private static class Sap extends Timeout {

        private static final int REUSE_RATE = 10;
        private static final int CONNECT_TIMEOUT = 15000; // 15s

        SocketChannel sc;
        ObjectOutputStream out;
        ObjectInputStream in;
        int port;
        private int nbReused = 0;

        Sap(String host, int port, int timeout) throws IOException {
            super(timeout);
            InetSocketAddress addr = new InetSocketAddress(host, port);
            sc = SocketChannel.open();
            sc.socket().connect(addr, CONNECT_TIMEOUT);
            out = new ObjectOutputStream(sc.socket().getOutputStream());
            in  = new ObjectInputStream(sc.socket().getInputStream());
            this.port = port;
            setup();
        }

        Sap(SocketChannel sock, int timeout) throws IOException {
            super(timeout);
            sc = sock;
            out = new ObjectOutputStream(sc.socket().getOutputStream());
            in  = new ObjectInputStream(sc.socket().getInputStream());
            port = sc.socket().getPort();
            setup();
        }

        Sap(SocketChannel sock) {
            super(0);
            sc = sock;
            out = null;
            in = null;
            port = sc.socket().getPort();
            setup();
        }

        private void reset()
            throws IOException {
            nbReused++;
            if (nbReused >= REUSE_RATE) {
                out.reset();
                //in.reset();
                nbReused = 0;
            }
        }
                
        private void setup() {
            assert(sc != null);
            try {
                sc.socket().setTcpNoDelay(true);                
                sc.socket().setKeepAlive(true);
                
            } catch (SocketException se) {
                logger.warning("setup failed " + se + " for " + this);
            }
        }
    }
}
