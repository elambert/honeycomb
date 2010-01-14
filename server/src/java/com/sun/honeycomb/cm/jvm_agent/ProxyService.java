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

import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.io.ObjectStreamClass;
import java.lang.reflect.UndeclaredThrowableException;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ipc.MailboxReader;


final class ProxyService {

    public static final Logger logger =
        Logger.getLogger(ProxyService.class.getName());
    
    /* how often we refresh the proxy in sec */
    private static final int REFRESH_INTERVAL = 1; // 1s
    
    private final String tag;
    private final int node;
    private MailboxReader mailbox;
    private Class[] api;
    private Service.ProxyHeader hdr;
    private ManagedService.ProxyObject proxy;
    private long lastUpdateTime;

    /**
     * Create a new proxy service.
     */
    ProxyService(int nodeid, String tag) throws CMAException {
        mailbox = null;
        try {
            this.tag = tag;
            node = nodeid;
            lastUpdateTime = 0;
            mailbox = new MailboxReader(tag);
            setupProxy();
        } catch (Exception e) {
            destroy();
            throw new CMAException(tag, e);
        }
    }

    /**
     * Destroy this ProxyService
     */
    void destroy() {
        if (mailbox != null) {
            mailbox.close();
            mailbox = null;
        }
    }

    /**
     * Return an appropriate unique local identifier for
     * a ProxyService object.
     */
    static Object getKey(int nodeid, Class cls) {
        return nodeid + "/" + cls;
    }

    /**
     * This class creates the object API exported by the remote service.
     * It builds a proxy object that wraps exported interface methods
     * and implements the trampoline code.
     */
    static class ProxyAPI implements InvocationHandler {

        Service.ProxyHeader hdr;
        
        ProxyAPI(Service.ProxyHeader hdr) {
            this.hdr = hdr;
        }

        /*
         * trampoline code in the proxy API -
         * dispatch the method call to the correct managed service.
         */
        public Object invoke(Object caller, Method method, Object[] args) 
            throws Throwable 
        {
            ManagedService.ProxyObject proxy;
            Object ret;
            CMSAP sap = null;
            try {
                /*
                 * do the RMI call.
                 */
                sap = hdr.sap.duplicate();
                ret = sap.call(method, args);
                
                if (ret instanceof ManagedService.RemoteChannel) {
                    /*
                     * the return object is a stream channel shared
                     * between the 2 services. Don't release the socket
                     */
                    ManagedService.RemoteChannel sc;
                    sc = (ManagedService.RemoteChannel)ret;
                    sc.setChannel(new ServiceChannelImpl(sap, false));
                } else {
                    /*
                     * release and cache the underlying socket.
                     */
                    sap.disconnect();
                }
            } catch (IOException ioe) {
                /*
                 * I/O error -
                 * Close the underlying socket and throw 
                 * a ManagedServiceException. Note that if the RMI method
                 * did not declare throwing this type of exception, the
                 * exception is transformed into a UndeclaredThrowableException
                 */ 
                if (sap != null) {
                    sap.close();
                }
                logger.warning("ClusterMgmt - remote call on " + sap + 
                               " failed " + ioe);
                StackTraceElement[] stack = ioe.getStackTrace();
                for (int i = 0; i < stack.length; i++) {
                    logger.warning("ClusterMgmt - " + stack[i].toString());
                }
                throw new ManagedServiceException(ioe);
            } 

            if (ret instanceof Throwable) {
                /*
                 * The RMI call failed - propagate the exception.
                 */
                if (ret instanceof CMAException) {
                    /*
                     * This is an internal error -
                     * Log a warning, this should happen only
                     * during failure or when the system is malfunctioning.
                     */
                    CMAException cme = ((CMAException) ret);
                    String msg;
                    if (cme.getCause() != null) {
                        msg = cme.getCause().getMessage();
                    } else {
                        msg = cme.getMessage();
                    }
                    logger.warning("ClusterMgmt - rmi call " + method.getName()
                                   + "[" + method.hashCode() + "]" +
                                   " failed: " + msg
                                   );
                }
                throw (Throwable) ret;
            }
            return ret;
        }
    }

    /**
     * Get the current network proxy object for this service.
     */
    synchronized ManagedService.ProxyObject getProxy() throws CMAException {
        try {
            long elapsed = CMAgent.currentTimeSecs() - lastUpdateTime;
            if (elapsed > REFRESH_INTERVAL) {
                if (!mailbox.isUpToDate()) {
                    mailbox.update();
                    setupProxy();
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("ClusterMgmt - proxy " + node 
                                    + "/" + getName() + " updated"
                                    );
                    }
                }
                lastUpdateTime = CMAgent.currentTimeSecs();
            }
            return proxy;
        } catch (Exception e) {
            throw new CMAException(getName(), e);
        }
    }

    /**
     * Setup the proxy object
     * Read the header and proxy object from the mailbox
     * and reconstruct the RMI api.
     */
    private void setupProxy() throws CMAException {
        try {
            ObjectInputStream in = new ObjectInputStream(mailbox);
            hdr = (Service.ProxyHeader) in.readObject();
            proxy = (ManagedService.ProxyObject) in.readObject();
            
            api = new Class[hdr.rmi.length];
            for (int i = 0; i < api.length; i++) {
                api[i] = hdr.rmi[i].forClass();
            }
            
            if (api.length > 0) {
                proxy.api = (ManagedService.RemoteInvocation) 
                    Proxy.newProxyInstance(
                            proxy.getClass().getClassLoader(),
                            api,
                            new ProxyAPI(hdr));
            } else {
                proxy.api = null;
            }
        } catch (Exception e) {
            throw new CMAException(e);
        }
    }

    /**
     * return the name of this service
     */
    private String getName() {
        String[] parts = tag.split("/");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        } 
        return tag;
    }
}
