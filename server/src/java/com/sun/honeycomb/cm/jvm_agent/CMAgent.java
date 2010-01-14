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



/*
 * Cluster Management
 * This class implements the main routine in the JVMs managed by the
 * cluster management
 *
 */

package com.sun.honeycomb.cm.jvm_agent;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.NodeMgr;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;
import com.sun.honeycomb.cm.cluster_membership.messages.api.ConfigChangeNotif;

import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.channels.ServerSocketChannel;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.ClosedChannelException;
import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.nio.channels.SelectableChannel;
import java.util.logging.LogManager;
import java.util.logging.Level;
import java.util.Enumeration;
import java.net.InetAddress;
import java.util.Observer;
import java.util.Observable;
import java.util.Hashtable;
import java.nio.ByteBuffer;
import java.io.EOFException;


public class CMAgent implements JVMService {
    
    private static final Logger logger = 
        Logger.getLogger(CMAgent.class.getName());
    
    private static final int HEARTBEAT_INTERVAL = 2000;   // 2s
    private static final int PUBLISH_INTERVAL = 10000;    // 10s
    private static final int POLL_DELAY = 500;            // 500ms
    private static final int IDLE_SAP_EXPIRATION = 60000; // 1mn
    private static final int IDLE_THREAD_EXPIRATION = (60 * 60000); // 1h
    private static final int MONITOR_GRACE_PERIOD = 5;   // 5*500ms
    private static final int LOG_INTERVAL = 300000;      // 5min

    private static final int START_PORT = 2035;
    private static final int END_PORT = 2135;
    // CMM is the only module exporting events right now.
    private static final int CLUSTER_MEMBERSHIP_EVENT = 1;
    private static final Timeout timeout = new Timeout(PUBLISH_INTERVAL);

    // For dispatch threads
    private static final int DEFAULT_DISPATCH_THREADS = 2;    // 1 + #CPUs
    private static final int DEFAULT_MAX_DISPATCH_THREADS = 32;

    private static int localNodeId;
    private static List services;
    private static HashSet saps;
    private static Hashtable notifications = new Hashtable();
    private static String agentName;
    private static Thread agentThr;
    private static ServerSocketChannel listener;
    private static Selector dispatch;
    private static int sapUID;
    private static int jvmUID;
    private static String hostName = null;
    private static int portAgent;
    private static volatile int activeCount;
    private static volatile Thread activeThr;
    private static volatile boolean serviceIsRunning;
    private static volatile long currentTimeSecs;

    private static int numDispatchThreads = DEFAULT_DISPATCH_THREADS;
    private static int maxDispatchThreads = DEFAULT_MAX_DISPATCH_THREADS;

    private static boolean initialized = false;
    private static synchronized void initProperties() {
        if (initialized)
            return;

        ClusterProperties props = ClusterProperties.getInstance();
        numDispatchThreads = 
            props.getPropertyAsInt(ConfigPropertyNames.PROP_CM_NUMTHREADS,
                                   DEFAULT_DISPATCH_THREADS); 
        maxDispatchThreads = 
            props.getPropertyAsInt(ConfigPropertyNames.PROP_CM_MAXTHREADS,
                                   DEFAULT_MAX_DISPATCH_THREADS);

        logger.info("Dispatch threads: " +
                    numDispatchThreads + "," + maxDispatchThreads);
        initialized = true;
    }

    /*
     * the JVM proxy propagated in the cluster.
     */
    private JVMService.Proxy proxy;

    /*
     * notification event keys
     */
    private static SelectionKey cmmKey = null;

    /**
     * default constructor called by cluster management
     */
    public CMAgent() {
        logger.info("ClusterMgmt - JVM " + agentName + " created");
        proxy = new JVMService.Proxy();
    }
                
    public void syncRun() {
        currentTimeSecs = System.currentTimeMillis() / 1000;
    }

    /**
     * entry point of the jvm management agent.
     */
    public void run() {
        logger.info("ClusterMgmt - JVM " + agentName + " running ");
        ServiceManager.publish(this);
        
        //
        // Monitor the CMAgent service -
        // At any point of time, this service uses at least 2 threads.
        // One in the scheduler and this one.
        // 
        agentThr = Thread.currentThread();
        while (serviceIsRunning) {
            
            if (Thread.currentThread().activeCount() < 2) {
                logger.severe("ClusterMgmt - ERROR in scheduler, thread count "
                              + "incorrect: current number of threads is " +
                              Thread.currentThread().activeCount()  +
                              " active scheduler count is " + activeCount
                              );
                System.exit(2);
            }
            try {                
                Thread.currentThread().sleep(HEARTBEAT_INTERVAL);
            } catch (InterruptedException ignore) {}
        }
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * shutdown of the management agent.
     */
    public void shutdown() {
        logger.info("ClusterMgmt - JVM " + agentName + " shutdowns");
        if (serviceIsRunning) {
            serviceIsRunning = false;
            dispatch.wakeup();
            synchronized (this) {
                if (agentThr != null) {
                    while (agentThr.isAlive()) {
                        try {
                            agentThr.interrupt();
                            wait(POLL_DELAY);
                        } catch (InterruptedException ignore) {}
                    }
                }
            }
        }
        for (int i = 0; i < services.size(); i++) {
            Service svc = (Service) services.get(i);
            svc.disable();
        }
    }

    /**
     * Return the proxy object for the management agent.
     */
    public ManagedService.ProxyObject getProxy() {
        ThreadGroup rootGrp = null;
        ThreadGroup grp = Thread.currentThread().getThreadGroup();
        while (grp != null) {
            rootGrp = grp;
            grp = grp.getParent();
        }
        if (rootGrp == null) {
            throw new RuntimeException("ClusterMgmt - cannot get thread group");
        }
        Runtime runtime = Runtime.getRuntime();
        proxy.threadCount = rootGrp.activeCount();
        proxy.freeMemory = runtime.freeMemory();
        proxy.maxMemory = runtime.maxMemory();
        proxy.totalMemory = runtime.totalMemory();
        return proxy;
    }

    /**
     * publish in the cell a new proxy object for the given service.
     */
    public static void publish(ManagedService service) {
        Class cls = service.getClass();
        for (int i = 0; i < services.size(); i++) {
            Service svc = (Service) services.get(i);
            if (cls == svc.getServiceClass()) {
                svc.publish();
                return;
            }
        }
        logger.warning("ClusterMgmt - service not found " + cls);
    }
    
    /**
     * return the ManagedService class for the current thread.
     * or null if the service cannot be found.
     */
    public static Class currentManagedService() {
        Thread thr = Thread.currentThread();

        if (services == null) {
            logger.warning("No services? If this isn't the regression test, " +
                           "something is gang agley in thread " + thr.getName());
            return null;
        }

        for (int i = 0; i < services.size(); i++) {
            Service svc = (Service) services.get(i);
            if (svc.includeThread(thr)) {
                return svc.getServiceClass();
            }
        }
        logger.warning("ClusterMgmt - service not found for " + thr.getName());
        return null;
    }
    
    /**
     * return the proxy object for the given service.
     */
    public static ManagedService.ProxyObject proxyFor(int node, String tag) {
        StringBuffer mboxTag = new StringBuffer();
        mboxTag.append("/");
        mboxTag.append(node);
        mboxTag.append("/");
        mboxTag.append(tag);
        return ProxyFactory.proxyFor(node, mboxTag.toString());
    }

    /**
     * return the proxy object for the given service class.
     * this assume one instance of the service per node.
     */
    public static ManagedService.ProxyObject proxyFor(int node, Class cls) {
        NodeMgrService.Proxy prnode = proxyFor(node);
        if (prnode == null) {
            // Changed this log from WARNING to FINE since someone keeps
            // calling this with nodeId = 101..116 and fills up the logs
            if (logger.isLoggable (Level.FINE))
                logger.fine("ClusterMgmt - node proxy " + node +
                            " does not exist");
            return null;
        }
        String tag = prnode.getTagByClass(cls);
        if (tag == null) {
            logger.warning("ClusterMgmt - service " + cls.getName() 
                           + " does not exist on " + node);
            return null;
        }
        return ProxyFactory.proxyFor(node, tag);
    }

    /**
     * return the node manager proxy object for the given node.
     */
    public static NodeMgrService.Proxy proxyFor(int node) {
        String tag = node + "/" + NodeMgrService.mboxTag;
        Object obj = ProxyFactory.proxyFor(node, tag);
        if (obj instanceof NodeMgrService.Proxy) {
            return (NodeMgrService.Proxy) obj;
        }
        /*
         * if node is local - escalation
         */
        if (node == ServiceManager.LOCAL_NODE || node == localNodeId) {
            logger.severe(
                  "ClusterMgmt - ERROR - cannot access local node proxy");
            System.exit(1);
        }
        return null;
    }

    /**
     * register the observer for event notification
     */
    synchronized public static void register(int event, Observer observer) 
        throws CMAException {
        Integer evtType = null;
        EventNotification notif = null;
        switch (event) {
        case ServiceManager.CMM_EVENT:
        case ServiceManager.CONFIG_EVENT:
            evtType = new Integer(CLUSTER_MEMBERSHIP_EVENT);
            break;
        default:
            throw new CMAException("event type not supported");
        }
        notif = (EventNotification) notifications.get(evtType);
        if (notif == null) {
            try {
                SocketChannel sc = CMM.getAPI(hostName).register();
                notif = new EventNotification(sc);
            } catch (Exception e) {
                throw new CMAException(e);
            }
            notifications.put(evtType, notif);
        }
        notif.add(observer, event);
    }

    /**
     * Create a JVM agent
     */
    public static void createAgent(String localHost, Class cls, String tag) 
        throws CMAException {
        initProperties();
        sapUID = 0;
        services = new Vector();
        saps = new HashSet();
        notifications = new Hashtable();
        hostName = localHost;
        String[] parts = tag.split("/");
        if (parts.length > 1) {
            agentName = parts[parts.length - 1];
        } else {
            agentName = tag;
        }
        try {
            logger.info("ClusterMgmt - JVM " + agentName + 
                        " bootstrapped on " + hostName);
            /*
             * Setup the listener socket for the jvm agent
             */
            listener = ServerSocketChannel.open();
            listener.configureBlocking(false);
            ServerSocket sock = listener.socket();
            sock.setReuseAddress(true);
            InetSocketAddress addr = null;
            for (portAgent = START_PORT; portAgent < END_PORT; portAgent++) {
                try {
                    addr = new InetSocketAddress(hostName, portAgent);
                    sock.bind(addr);
                    break;
                } catch (Exception e) {
                    logger.finest(
                        "ClusterMgmt - skipping port " + portAgent + " " + e);
                }
            }
            if (portAgent == END_PORT) {
                logger.severe("ClusterMgmt - ERROR cannot bind to any port");
                System.exit(1);
            }
            dispatch = Selector.open();
            listener.register(dispatch, SelectionKey.OP_ACCEPT, "listener");
            logger.info("ClusterMgmt - JVM " + agentName + 
                        " connected to " + addr.toString());
        } catch (Exception e) {
            throw new CMAException(e);
        }

        addService(cls, tag);
        Service svc = (Service) services.get(0);
        serviceIsRunning = true;
        activeCount = 0;
        activeThr = null;
        for (int i = 0; i < numDispatchThreads; i++) {
            try {
                new Dispatcher(svc);
            } catch (IOException ioe) {
                throw new CMAException(ioe);
            }
        }
    }

    /**
     * Add the given service to the control agent list.
     */
    public static void addService(Class cls, String tag) 
        throws CMAException {
        addService(cls, tag, -1);
    }

    public static void addService(Class cls, String tag,
                                  int shutdownTimeout) 
        throws CMAException {
        int sid;
        synchronized (CMAgent.class) {
            sid = sapUID++;
        }
        CMSAP sap = new CMSAP(hostName, portAgent, jvmUID, sid, IDLE_SAP_EXPIRATION);
        Service svc = new Service(cls, tag, sap, shutdownTimeout);
        services.add(svc);
    }

    /**
     * Returns the current time in seconds.
     * Use a cache version of the time refreshed ~1s
     */
    public static long currentTimeSecs() {
        return currentTimeSecs;
    }
    
    
    /*
     * Remote API
     */

    /**
     * Set the log level for class "name" (and subclasses) in this
     * JVM. If "name" is empty, apply to all classes.
     */
    public void setLogLevel(String levelp, String name) {
        LogManager logMgr = LogManager.getLogManager();
        Level level = Level.parse(levelp);
        boolean ok = true;

        if (name == null || name.equals("")) {
            Enumeration names = logMgr.getLoggerNames();
            while (names.hasMoreElements())
                if (!setLevel(logMgr, levelp, (String) names.nextElement()))
                    ok = false;
            name = "*";
        }
        else
            ok = setLevel(null, levelp, name);

        if (ok)
            logger.info("ClusterMgmt - log level for " +
                        agentName + ":" + name + " set to " + level);
    }

    /**
     * Set the log level for some classes
     *
     * @param logMgr the log manager to use
     * @param levelp the new log level
     * @param name the class name to change; all subclasses also affected
     */
    private boolean setLevel(LogManager logMgr, String levelp, String name) {
        try {
            Level level = Level.parse(levelp);
            Logger log = null;
            if (logMgr != null)
                // Do not create any loggers that don't already exist
                log = logMgr.getLogger(name);
            else
                log = Logger.getLogger(name);

            if (log != null) {
                log.setLevel(level);
                return true;
            }
        }
        catch (Exception e) {}

        logger.log(Level.WARNING, 
                   "Couldn't set log level for \"" + name + "\"");
        return false;
    }


    /*
     * internal
     */

    static void addToDispatcher(CMSAP sap) {
        synchronized (saps) {
            saps.add(sap);
            dispatch.wakeup();
        }
    }
        
    /**
     * JVM agent threads pool - scheduler
     * Dispatcher threads pool to monitor and control services.
     * It also provides thread contexts to handle RMI calls.
     * 
     */
    static private class Dispatcher implements Runnable {
        
        private static int threadIncarnation = 0;
        private boolean expired = false;
        
        private Dispatcher() throws IOException {
            synchronized (Dispatcher.class) {
                activeCount++;
            }
            logger.info("ClusterMgmt - new dispatcher thread [" + this + "]. "+
                        threadIncarnation+
                        "th dispatcher."
                        );
            new Thread(this, "CMAgent-dispatcher-"+(threadIncarnation++)).start();
            if (threadIncarnation == Integer.MAX_VALUE-1) {
                threadIncarnation = 0;
            }
        }

        private Dispatcher(ThreadGroup group) throws IOException {
            synchronized (Dispatcher.class) {
                activeCount++;
            }
            new Thread(group, this, "CMAgent-dispatcher").start();
        }

        private void scheduler(long timeout) throws IOException {

            synchronized (saps) {
                Timeout idleTimeout = new Timeout(IDLE_THREAD_EXPIRATION);
                while (activeThr != null) {
                    try {
                        if (idleTimeout.hasExpired()) {
                            expired = true;
                            return;
                        }                        
                        saps.wait(IDLE_THREAD_EXPIRATION);
                        
                    } catch (InterruptedException ie) {
                        logger.warning("ClusterMgmt - thread interrupted");
                    }
                }
                activeThr = Thread.currentThread();
                expired = false;
            }
            
            CMSAP sap = null;
            try {                
                //
                // purge the cache if some connections
                // are idle for too long.
                //
                Iterator it = dispatch.keys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();
                    if (key.isValid() && !key.isReadable()) {
                        Object obj = key.attachment();
                        if (!(obj instanceof CMSAP)) {
                            continue;
                        }
                        sap = (CMSAP) obj;
                        if (sap.hasExpired()) {
                            key.cancel();
                            sap.close();
                            logger.finest("ClusterMgmt - expired " + sap);
                        }
                    }
                }
                // 
                // putback all service access points for every ended
                // RMI calls back to the main dispatch selector.
                //
                synchronized (saps) {
                    it = saps.iterator();
                    while (it.hasNext()) {
                        sap = (CMSAP) it.next();
                        SelectableChannel ch = sap.channel();
                        assert (ch != null);
                        ch.configureBlocking(false);
                        ch.register(dispatch, SelectionKey.OP_READ, sap);
                    }
                    saps.clear();
                }
                //
                // check if there is any RMI call/monitor action or
                // notification pending, waiting for the duration of 
                // the timeout.
                //
                sap = null;
                if (dispatch.select(timeout) > 0) {
                    it = dispatch.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey key = (SelectionKey) it.next();
                        if (key.isValid()) {
                            //
                            // RMI call or notification pending -
                            //
                            if (key.isAcceptable()) {
                                SocketChannel s = listener.accept();
                                if (s != null) {
                                    s.socket().setReuseAddress(true);
                                    sap = new CMSAP(s, IDLE_SAP_EXPIRATION);
                                }
                                break;
                            } else if (key.isReadable()) {
                                key.cancel();
                                Object obj = key.attachment();
                                dispatch.selectNow();
                                if (obj instanceof CMSAP) {
                                    sap = (CMSAP) obj;
                                    saps.remove(sap);
                                    break;
                                }
                            }
                        }
                    }
                    dispatch.selectedKeys().clear();
                }
            } finally {
                synchronized (saps) {
                    activeThr = null;
                    saps.notify();
                }
            }                

            // 
            // Perform the actual operation -
            //
            if (sap != null) {
                try {
                    boolean reuseSAP;
                    if (sap instanceof EventNotification) {
                        reuseSAP = notify((EventNotification)sap);
                    } else {
                        reuseSAP = invoke(sap);
                    }
                    if (reuseSAP) {
                        addToDispatcher(sap);
                        sap = null;
                    }
                } catch (EOFException ignored) {
                    if (sap != null) {
                        sap.close();
                    }
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, "ClusterMgmt - invoke error ", ioe);
                    if (sap != null) {
                        sap.close();
                    }
                }
            }
        }

        private boolean invoke(CMSAP sap) throws IOException {
            synchronized (Dispatcher.class) {
                activeCount--;
            }
            boolean reuseSAP = true;
            try {
                if (activeCount == 0) {
                    int count = Thread.currentThread().activeCount();
                    if (count < maxDispatchThreads) {
                        new Dispatcher();
                    }
                }
                sap.accept();
                Service target = null;
                for (int i = 0; i < services.size(); i++) {
                    Service svc = (Service) services.get(i);
                    if (svc.getSid() == sap.suid) {
                        target = svc;
                        break;
                    } 
                }
                if (target != null) {
                    reuseSAP = target.invoke(sap);
                } else {
                    Exception ex;
                    ex = new ManagedServiceException("NoSuchService " + sap.suid);
                    sap.nack(ex);
                }
            } finally {
                synchronized (Dispatcher.class) {
                    activeCount++;
                }
            }
            return reuseSAP;
        }

        private boolean monitor() throws IOException {
            boolean attention = false;
            for (int i = 0; i < services.size(); i++) {
                Service svc = (Service) services.get(i);
                if (svc.monitor()) {
                    attention = true;
                }
            }
            return attention;
        }

        private boolean notify(EventNotification notif) throws IOException {
            synchronized (Dispatcher.class) {
                activeCount--;
            }
            try {
                if (activeCount == 0) {
                    int count = Thread.currentThread().activeCount();
                    if (count < maxDispatchThreads) {
                        new Dispatcher();
                    }
                }
                notif.callback();
            } finally {
                synchronized (Dispatcher.class) {
                    activeCount++;
                }
            }
            return true;
        }

        private void publish() {
            for (int i = 0; i < services.size(); i++) {
                Service svc = (Service) services.get(i);
                svc.publish();
            }
            logger.finest("ClusterMgmt - JVM " + this);
        }

        public void run() {
            try {
                int monitorLoops = 0;
                int loggerLoops = 0;
                Runtime rt = Runtime.getRuntime(); // for memory stats

                while (serviceIsRunning) {
                    try {
                        int delay;
                        if (monitor()) {
                            delay = POLL_DELAY;
                        } else if (monitorLoops > MONITOR_GRACE_PERIOD) {
                            monitorLoops = 0;
                            delay = HEARTBEAT_INTERVAL;
                        } else {
                            monitorLoops++;
                            delay = POLL_DELAY;
                        }                        
                        scheduler(delay);
                        
                    } catch (IOException ioe) {
                        logger.warning("ClusterMgmt - IO error " + ioe);
                    }
                    
                    // publish all service proxies periodically and keep
                    // track of the current time.
                    boolean publishNow = false;
                    synchronized (timeout) {
                        currentTimeSecs = System.currentTimeMillis() / 1000;
                        if (timeout.hasExpired()) {
                            publishNow = true;
                            loggerLoops++;
                            timeout.arm();
                        }
                    }
                    if (publishNow) {
                        publish();
                    }
                    
                    // Log JVM memory usage every 5 minutes
                    if (loggerLoops * PUBLISH_INTERVAL > LOG_INTERVAL) {
                        logger.info("ClusterMgmt - Memory Usage " + agentName + 
                                    ": Max=" + rt.maxMemory() +
                                    " Total=" + rt.totalMemory() + 
                                    " Free=" + rt.freeMemory()
                                    );
                        loggerLoops = 0;
                    }

                    synchronized (Dispatcher.class) {
                        if (expired && activeCount > numDispatchThreads) {
                            break;
                        }
                    }
                }
            } finally {
                synchronized (Dispatcher.class) {
                    activeCount--;
                    if (Thread.currentThread().activeCount() == 1) {
                        try {
                            dispatch.close();
                        } catch (IOException ioe) {
                            logger.severe("ClusterMgmt - I/O error " + ioe);
                        }
                    } else if (!serviceIsRunning) {
                        dispatch.wakeup();
                    }
                }
                logger.info("ClusterMgmt - idle dispatcher thread exits " + this);
            }
        }

        public String toString() {
            Thread cur = Thread.currentThread();
            return agentName + 
                " count [" + activeCount + "/" + cur.activeCount() + "]";
        }
    }

    /**
     * Event notification record the observer interested in
     * a particular channel (only CMM for now).
     */
    static private class EventNotification extends CMSAP {

        Hashtable clients;

        class Callback extends Observable {
            void invoke(Object obj) {
                setChanged();
                notifyObservers(obj);
            }
        }

        EventNotification(SocketChannel sock) throws IOException {
            super(sock);
            clients = new Hashtable();
            synchronized (saps) {
                saps.add(this);
                dispatch.wakeup();
            }
        }

        void callback() throws IOException {
            try {
                CMMApi api = CMM.getAPI(hostName);
                Message msg = api.getNotification(channel());
                Callback ctrl = null;
                if (msg instanceof NodeChange) {
                    ctrl = (Callback)
                        clients.get(new Integer(ServiceManager.CMM_EVENT));
                } else if (msg instanceof ConfigChangeNotif) {
                    ctrl = (Callback)
                        clients.get(new Integer(ServiceManager.CONFIG_EVENT));
                } else {
                    logger.severe("ClusterMgmt - ERROR - invalid " +
                                  "notification message");
                }
                if (ctrl != null) {
                    ctrl.invoke(msg);                    
                }
            } catch (Exception e) {
                throw new IOException(e.toString());
            }
        }

        void add(Observer o, int event) {
            Callback ctrl = (Callback) clients.get(new Integer(event));
            if (ctrl == null) {
                ctrl = new Callback();
                clients.put(new Integer(event), ctrl);
            }
            ctrl.addObserver(o);
        }
    }

    /**
     * Main routine.
     *
     * This is the main entry point of CMAgent.
     */
    public static void main(String[] arg) {
        try {
            /*
             * Create and fire up the jvm agent.
             */
            String tag = System.getProperty("hc.mailbox");

            NodeMgrService.Proxy nodemgr;
            nodemgr = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
            localNodeId = nodemgr.nodeId();
            jvmUID = nodemgr.getService(tag).getId();
            createAgent(nodemgr.getHostname(), CMAgent.class, tag);
            
            /*
             * Set the resources limit for this JVM.
             */
            if (ServiceManager.setResourcesLimit()) {
                logger.info("ClusterMgmt - process resources limit set");
            } else {
                logger.warning("ClusterMgmt - failed to set resources limit");
            }
            
            /*
             * Add all the service running in this JVM under the
             * agent control.
             */
            com.sun.honeycomb.cm.node_mgr.Service[] svc;
            svc = nodemgr.getServices();
            for (int i = 0; i < svc.length; i++) {
                if (svc[i].getId() == jvmUID && !svc[i].isJVM()) {
                    Class cls = Class.forName(svc[i].getClassName());
                    addService(cls, 
                               svc[i].getTag(), 
                               svc[i].getShutdownTimeout()
                               );
                }
            }
        } catch (Throwable e) {
            logger.log (Level.SEVERE,
                  "ClusterMgmt - EXITING: main thread failed", e);
            System.exit(1);
        }
    }
}
