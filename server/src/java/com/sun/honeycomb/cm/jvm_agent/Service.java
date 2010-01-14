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

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ipc.Mailbox;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.UndeclaredThrowableException;
import java.lang.reflect.InvocationTargetException;
import java.io.ObjectStreamClass;
import java.util.Hashtable;
import java.lang.reflect.Field;


public final class Service extends ThreadGroup implements Mailbox.Listener {

    public static final Logger logger =
        Logger.getLogger(Service.class.getName());
    
    private static final int ESCALATION_TIMEOUT = 10000; // 10s
    private static final int POLL_DELAY = 500; // 500ms
    // how many times do we loop for threads to die during service shutdown
    private static final int SHUTDOWN_MAX_LOOP = 3; 

    private final Class cls;
    private final String tag;
    private final Mailbox mailbox;
    private final ProxyHeader hdr;
    private final Hashtable api;
    private final int shutdownTimeoutValue;
    
    private Boolean lock = Boolean.FALSE;
    private CtrlThread ctrlThread = null;
    private boolean doStart = false;
    private ShutdownThread shutdownThread = null;
    private volatile ManagedService service = null;
    private volatile int publishEvents = 0;
    private volatile Timeout monitorTimeout = null;
    

    Service(Class cls, String tag, CMSAP sap,
            int _shutdownTimeoutValue) throws CMAException {
        super(tag);
        this.cls = cls;
        this.tag = tag;

        if (_shutdownTimeoutValue > 0) {
            shutdownTimeoutValue = _shutdownTimeoutValue;
        } else {
            shutdownTimeoutValue = ESCALATION_TIMEOUT;
        }


        hdr = new ProxyHeader(cls, sap);
        try {
            mailbox = new Mailbox(tag, false);
        } catch (IOException ioe) {
            throw new CMAException(ioe);
        }
        api = new Hashtable();
        for (int i = 0; i < hdr.rmi.length; i++) {
            Method[] methods = hdr.rmi[i].forClass().getDeclaredMethods();
            for (int j = 0; j < methods.length; j++) {
                /*
                 * Check that the RMI call is valid and add it
                 * to the service remote api.
                 */
                Class[] ex = methods[j].getExceptionTypes();
                boolean found = false;
                for (int k = 0; k < ex.length; k++) {
                    if (ManagedServiceException.class.isAssignableFrom(ex[k])) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    logger.warning("ClusterMgmt - method " + methods[j] 
                                   + " in service " + svcName() + 
                                   " does not declare throwing " + 
                                   ManagedServiceException.class.getName()
                                   );
                }    
                Object key = new Integer(methods[j].hashCode());
                api.put(key, methods[j]);
            }
        }
        publish();
    }
    
    /**
     * Proxy header in the mailbox
     * This object is wrote only once and defines the accessible API
     * and service access point of this managed service.
     * It exports how to join this service and which APIs are available.
     * This provides the underlying mechanism for the RMI framework,
     */
    public static class ProxyHeader implements java.io.Serializable {
        
        CMSAP sap;
        ObjectStreamClass[] rmi;
        
        public ProxyHeader(Class cls, CMSAP sap) throws CMAException {
            this.sap = sap;
            Class all[] = cls.getInterfaces();
            Class pub[] = new Class[all.length];
            
            int j = 0;
            for (int i = 0; i < all.length; i++) {
                if (ManagedService.RemoteInvocation.
                    class.isAssignableFrom(all[i])) {
                    pub[j++] = all[i];
                }
            }
            rmi = new ObjectStreamClass[j];
            for (int i = 0; i < rmi.length; i++) {
                rmi[i] = ObjectStreamClass.lookup(pub[i]);
                if (rmi[i] == null) {
                    throw new CMAException("Invalid exported API " + pub[i]);
                }
            }
        }
    }
    
    /**
     * Return the unique identifier for this service in this jvm.
     */

    int getSid() {
        return hdr.sap.suid;
    }


    /**
     * Return the java class for this service.
     */
    public Class getServiceClass() {
        return cls;
    }
    
    public String toString() {
        return cls.getName();
    }
    
    /**************************************
     * Honeycomb service management API
     **************************************/

    /**
     * Disable this service.
     * This method is ASYNC safe.
     */
    void disable() {
        ManagedService svc;
        synchronized (lock) {
            svc = service;
            if (svc == null) {
                return;
            }
            service = null;
        }
        Timeout timeout = new Timeout(ESCALATION_TIMEOUT);
        shutdown(svc, timeout);
        mailbox.setDisabled();
        logger.info("ClusterMgmt - " + svcName() + " is now DISABLED");
    }

    /**
     * publish the current proxy object of this service in the cell.
     * This method is ASYNC safe.
     */
    void publish() {
        // we don't want to block and we don't want to loose the last
        // publish event either.
        synchronized (lock) {
            if (publishEvents != 0) {
                publishEvents++;
                Thread.currentThread().yield();
                return;
            }
            publishEvents = 1;
        }
        boolean again;
        do {
            ManagedService.ProxyObject proxy = getProxy();

            try {
                ObjectOutputStream out = new ObjectOutputStream(mailbox);
                out.writeObject(hdr);
                out.writeObject(proxy);
                out.flush();
                mailbox.sync();
                logger.fine("ClusterMgmt - serialized proxy "  + 
                            tag + " size " + mailbox.size() + " bytes");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "ClusterMgmt - " + svcName() + 
                           " I/O serialization error " + proxy, e);
                disable();
            }
            synchronized (lock) {
                again = (publishEvents > 1);
                publishEvents = 0;
            }
        } while (again);
    }

    /**
     * remote invocation trampoline -
     * invoke the method in the managed service.
     */
    boolean invoke(CMSAP sap) {

        try {
            Method method;
            Object args[];

            try {

                Object key = new Integer(sap.in().readInt());
                args = (Object[]) sap.in().readObject();
                method = (Method) api.get(key);
                if (method == null) {
                    String msg = "remote method " + key.toString() + 
                                 " does not exists on " + sap;
                    throw new NoSuchMethodException(msg);
                }
            } catch (Exception e) {
                // if the above failed - this is an internal error
                // and we want to wrap the exception.
                throw new CMAException(e);
            }
            if (service != null) {
                Object ret = method.invoke(service, args);
                sap.out().writeObject(ret);
                sap.flush();
                sap.out().reset();
                if (ret instanceof ManagedService.RemoteChannel) {
                    ManagedService.RemoteChannel sc;
                    sc = (ManagedService.RemoteChannel) ret;
                    sc.setChannel(new ServiceChannelImpl(sap, true));
                    return false;
                }
            } else {
                // service is currently disabled - internal error
                throw new CMAException("Service disabled " + svcName());
            }
        } catch (Exception e) {
            // 
            // stream back the exception to the caller
            //
            if (e instanceof InvocationTargetException) {
                logger.log(Level.INFO,"ClusterMgmt - invoke failed ",e);
                sap.nack(e.getCause());
            } else {
                logger.log(Level.WARNING,"ClusterMgmt - invoke failed ",e);
                sap.nack(e);
            }
        }
        return true;
    }

    /**
     * Check the health of the service. This function triggers
     * runtime state changes in the managed service
     * (INIT READY, RUNNING, DISABLED).
     * This method is ASYNC-SAFE
     * @return true if the service needs to be monitored.
     */
    boolean monitor() {
        synchronized (lock) {
            if (lock.booleanValue()) {
                return true;
            }
            lock = Boolean.TRUE;
        }
        boolean monitor = false;
        if (monitorTimeout == null) {
            // can trigger listener callbacks.
            monitor = mailbox.stateCheck(this);
        } else if (monitorTimeout.hasExpired()) {
            // escalation
            logger.severe("ClusterMgmt - " + svcName() +
                          " operation failed - escalation -");
            System.exit(1);
        }
        lock = Boolean.FALSE;
        return monitor;
    }
    
    
    /****************************************
     * ThreadGroup management and extention
     ****************************************/
    
    /**
     * Catch all uncaught exceptions from the service threads.
     */
    public void uncaughtException(Thread t, Throwable e) {

        // log the reason of failure
        // TODO - keep historic in JVM proxy object
        Throwable culprit = e;
        if (e instanceof UndeclaredThrowableException) {
            culprit = ((UndeclaredThrowableException) e).getUndeclaredThrowable();
        }
        logger.severe("ClusterMgmt - " + t + " got exception " + culprit);
        StackTraceElement[] stack = e.getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            logger.severe("ClusterMgmt - " + stack[i].toString());
        }
        if (e.getCause() != null) {
            stack = e.getCause().getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                logger.severe("ClusterMgmt - " + stack[i].toString());
            }
        }
        // unrecoverable
        if (e instanceof Error) {
            logger.severe("ClusterMgmt - EXITING " + svcName() + " got " + e);
            System.exit(1);
        }
        // disable the service
        logger.severe("ClusterMgmt - " + svcName() + " is going DISABLED");
        disable();
    }

    /**
     * Return true if the given thread belongs to the service group.
     * false otherwise
     */
    boolean includeThread(Thread thr) {
        ThreadGroup thrGroup = thr.getThreadGroup();
        ThreadGroup svcGroup = (ThreadGroup) this;

        if (thrGroup.getName().equals(svcGroup.getName())) {
            return true;
        } else {
            return false;
        }
    }
    
    /******************************
     * Mailbox listener interface
     ******************************/

    /**
     * Initialize the service
     */
    public void doInit() {
        // the boostrap thread is accounted in the service
        // threadGroup.
        synchronized (mailbox) {
            if (ctrlThread != null) {
                return;
            }
            ctrlThread = new CtrlThread();
        }
        new Thread(this, ctrlThread, cls.getName() + " " + svcName()).start();
    }

    /**
     * Start the service
     */
    public void doStart() {
        synchronized(mailbox) {
            doStart = true;
            mailbox.notify();
        }
    }

    /**
     * Destroy the service
     * This operation is time bounded
     */
    public void doDestroy() {
        try {
            if (service != null) {
                logger.info("ClusterMgmt - " + svcName() + " going DISABLE");
            } else {
                String error = svcName() + " already destroyed";
                logger.severe("ClusterMgmt - "  + error);
                System.exit(1);
            }
            disable();
        } catch (Exception e) {
            String error = svcName() + " got " + e;
            logger.severe("ClusterMgmt - ESCALATION " + error);
            System.exit(1);
        }
    }

    /**
     * Stop gracefully the service
     */
    public void doStop() {
        synchronized(mailbox) {
            doStart = false;
        }
        shutdownThread = new ShutdownThread(this);
        shutdownThread.start();
    }

    /**
     * Heartbeat stop progress
     */
    public void doCheckShutdownTimeout() {
        if (shutdownThread != null && shutdownThread.hasExpired()) {
            // escalation
            logger.severe("ClusterMgmt - " + svcName() +
                          " shutdown timed-out - escalation -");
            System.exit(1);
        }
        return;
    }


    /*****************
     * Internal
     *****************/
    
    /**
     * Shutdown thread - 
     * triggered by a change in the mailbox state (NodeMgr request).
     * shutdown gracefully the service.
     * escalate if the service is not in the expected state.
     */
    private class ShutdownThread extends Thread {

        private final Timeout shutdownTimeout;
        
        ShutdownThread(ThreadGroup grp) {
            super(grp, svcName());
            shutdownTimeout = new Timeout(shutdownTimeoutValue);
        }
        
        boolean hasExpired() {
            return shutdownTimeout.hasExpired();
        }

        public void run() {
            shutdown(service, shutdownTimeout);
            shutdownThread = null;
            doInit();
        }
    }


    /**
     * Controller thread -
     * Initialize and start the service according to the mailbox state.
     * This thread eventually executes the run() method of the service.
     */
    private class CtrlThread implements Runnable {
        
        public void run() {
            try {

                // TODO - security check on service class
                // use our own class loader to force reinitialization of
                // static variables.

                if (service == null) {
                    // Instantiate service -
                    // Done only once in the lifetime of this JVM.
                    // If the service class has a constructor with String 
                    // parameter, use that constructor to create a service 
                    // instance with given tag.
                    // This is a later addition, used by test services.
                    // Original services only have a default no-arg constructor.
                    try {
                        Class[] signature = { String.class };
                        Constructor namedCls = cls.getConstructor(signature);
                        Object[] params = { tag };
                        service = (ManagedService) namedCls.newInstance(params);
                    } catch (Exception e) {
                        // default ctor
                        service = (ManagedService) cls.newInstance(); 
                    }
                }
                synchronized(mailbox) {
                    mailbox.setReady();
                }
                logger.info("ClusterMgmt - " + svcName() + " going READY");
            } catch (Exception e) {
                logger.severe("ClusterMgmt - " + svcName() + " got " + e);
                ctrlThread = null;
                mailbox.setDisabled();
                RuntimeException newe = new RuntimeException();
                newe.initCause(e);
                throw newe;
            }
            publish();
            synchronized (mailbox) {
                while (!doStart) {
                    try {
                        mailbox.wait();
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }
                }
            }
            service.syncRun();
            synchronized (mailbox) {
                mailbox.setRunning();
            }
            logger.info("ClusterMgmt - " + svcName() + " going RUNNING");
            service.run();
        }
    }

    /**
     * gracefully shutdown the service, escalate if that fails.
     * This method must be called ASYNC-SAFE
     */
    private void shutdown(ManagedService svc, Timeout timeout) {
        // shutdown the service. This operation is time bounded. 
        assert (svc != null && monitorTimeout == null && timeout != null);
        monitorTimeout = timeout;
        svc.shutdown();
        Thread.currentThread().interrupted();
        
        // loop until all threads die.
        // Note the service shutdown() should take care of destroying
        // all threads running in the service. This is not always the
        // case (hadb) and we don't want to escalate if some threads
        // are still lingering around.
        for (int loop = 0; 
             (activeCount() > 1) && loop < SHUTDOWN_MAX_LOOP;
             loop++)
        {
            Thread[] thr = new Thread[activeCount()];
            int count = enumerate(thr);
            if (count == 1 && thr[0] == Thread.currentThread()) {
                // we are done - the last thread is us
                break;
            }
            // interrupt all threads still in this service
            for (int i = 0; i < count; i++) {
                if (thr[i] != Thread.currentThread()) {
                    thr[i].interrupt();
                }
            }
            // yield the CPU
            try {
                Thread.currentThread().sleep(POLL_DELAY);
            } catch (InterruptedException ignore) {}

            // check if some threads are still running
            for (int i = 0; i < count; i++) {
                if (thr[i] == Thread.currentThread()) {
                    continue;
                }
                logger.warning("Waiting for thread " + thr[i] + " to die");
                try {
                    thr[i].join(POLL_DELAY);
                } catch (InterruptedException ie) {
                    // The service interrupted current thread (the mgmt
                    // control thread) or cluster mgmt is asking us to
                    // give up the operation. We should not escalate to give 
                    // a chance to all services runninng in this JVM 
                    // to shutdown gracefully.
                    logger.severe("ClusterMgmt - WARNING giving up stopping " +
                                  svcName() + " service - thread interrupted");
                    break;
                }
            }
        }
        
        if (activeCount() > 1) {
            logger.severe("ClusterMgmt - WARNING " + activeCount() +
                          " threads still running in service " + svcName()
                          );
        }
        
        ctrlThread = null;
        monitorTimeout = null;
    }
    
    
    /**
     * Get the current network proxy object for this service.
     */
    private ManagedService.ProxyObject getProxy() {
        ManagedService.ProxyObject proxy = null;
        ManagedService svc = service;
        if (svc != null) {
            try {
                proxy = svc.getProxy();
                // TODO - check proxy validity
            } catch (Exception e) {
                logger.severe("ClusterMgmt - " + svcName() + " got " + e);
                StackTraceElement[] stack = e.getStackTrace();
                for (int i = 0; i < stack.length; i++) {
                    logger.severe("ClusterMgmt - " + stack[i].toString());
                }
                disable();
            }
        }
        if (proxy == null) {
            proxy = new ManagedService.ProxyObject();
        }
        // TODO - permission access
        proxy.api   = null;
        proxy.load = 0; // TODO
        proxy.resource = 0; // TODO
        proxy.thrCount = activeCount();
        if (mailbox.isRunning()) {
            proxy.state = ManagedService.ProxyObject.RUNNING;
        } else if (mailbox.isReady()) {
            proxy.state = ManagedService.ProxyObject.READY;
        } else if (mailbox.isDisabled()) {
            proxy.state = ManagedService.ProxyObject.DISABLED;
        } else if (mailbox.isInit()) {
            proxy.state = ManagedService.ProxyObject.INIT;
        } else {
            proxy.state = -1;
        }
        return proxy;
    }
    
    /**
     * return the name of this service
     */
    private String svcName() {
        String[] parts = tag.split("/");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        } 
        return tag;
    }
}
