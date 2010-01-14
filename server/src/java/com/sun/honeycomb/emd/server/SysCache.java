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



package com.sun.honeycomb.emd.server;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.admin.mgmt.server.MgmtServerIntf;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.MDManagedService;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.SysCacheException;
import com.sun.honeycomb.layout.LayoutConfig;
import com.sun.honeycomb.oa.bulk.RestoreSession;

public class SysCache implements Runnable,PropertyChangeListener {

    private static Logger LOG = Logger.getLogger(SysCache.class.getSimpleName());
    private static String SYSCACHE_PREFIX = "SysCache: ";
    
    private static String NUM_NODES = "honeycomb.cell.num_nodes";
    
    // main loop timeout
    private static final long LOOP_TIMEOUT = 10000;    
    // wait 5m for all caches to be up and complete
    private static final long START_TIMEOUT = 300000;  
    // time to check up on restore status
    private static final long RESTORE_TIMEOUT = 300000; 
    // spam the logs with the state every minute
    private static final long MSG_INTERVAL = 60000; 
   
    // NodeMgrProxy timeouts
    private static final long TIMEOUT = 30000;
    private static final long PROXY_SLEEP = 1000;
    
    public static final String SYSTEM_CACHE_STATE = "honeycomb.cell.syscache.state";
    public static final String SYSTEM_CACHE_INIT = "honeycomb.cell.syscache.ininit";
    public static final String RESTOR_FT_DATE    = "honeycomb.cell.syscache.restoring.todate";
   
    // STARTING state is never set in the config it's only reported
    // if you use the getState method
    public static final String STARTING          = "starting";
    public static final String RUNNING           = "running";
    public static final String WAITFORREPOP      = "waitfor.repop";
    public static final String STOPPED           = "stopped";
    public static final String CORRUPTED         = "corrupted";
    public static final String RESTORING         = "restoring";
    public static final String RESTOR_FT         = "restoring.firsttape";
	
    public static final String FIRST_ERROR_TIME  = "honeycomb.cell.syscache.first_error";
    public static final String LAST_ERROR_TIME   = "honeycomb.cell.syscache.last_error";
    
    private static SysCache _instance = null;
    
	private Thread _thread = null;
	private ClusterProperties _properties = null;
    private MetadataClient _mdClient = null;
    
    private boolean _running = true;
    private boolean _inInit = true;
    
    private boolean _newError = false;
    
    private Level _repeatedMsglevel = Level.INFO;
    
	private SysCache() {
		_properties = ClusterProperties.getInstance();
        _properties.addPropertyListener(this);
        
        _mdClient = MetadataClient.getInstance();
        
        setInit(true);
	}

    public synchronized static SysCache getInstance() { 
        if (_instance == null)  {
            _instance = new SysCache();
        }
   
        return _instance;
    }
    
	public void start() {
		if (_thread == null) {
			_thread = new Thread(this);
			_thread.start();
		}
	}
	
	public void stop() {
		if (_thread != null) {
			// Master thread is last one to go down... so we verify
			// that all other caches have been shutdown before stopping.
            _running = false;
            try {
                _thread.join();
            } catch (InterruptedException e) {
                LOG.log(Level.WARNING,
                  SYSCACHE_PREFIX + "thread interrupted.",e);
            }
            setInit(true);
            _thread = null;
		}
	}

    /**
     * Calculates based on availability of the caches if enough caches are up 
     * and ready to be used by the system. 
     * 
     * @return
     */
    private boolean enoughCachesUp() {
        NodeMgrService.Proxy proxy = getNodeMgrProxy(); 
        Node[] nodes = proxy.getNodes();
        int num_nodes = ClusterProperties.getInstance().getPropertyAsInt(NUM_NODES);
        int uniqFailures = 0;
       
        for (int n = 0; n < num_nodes; n++) {
            Disk[] disks = DiskProxy.getDisks(nodes[n].nodeId);

            if (disks == null) {
                uniqFailures++;
                continue;
            }

            boolean failedDisk = false;
            for (int i = 0; i < disks.length; i++) {
                if (disks[i] != null && disks[i].isEnabled()) {
                    MDManagedService.Proxy remote = MDManagedService.Proxy
                            .getProxy(disks[i].nodeId());
                    if (remote != null) {
                        /*
                         * Any cache that is not running or is corrupted is to 
                         * be considered as a bad cache.
                         */
                        if (!remote.isCacheRunning(disks[i])  ||
                            remote.isCacheCorrupted(disks[i]) ||
                            !remote.isCacheComplete(disks[i]))
                            failedDisk = true;
                       
                        if (!remote.isCacheComplete(disks[i]))
                            if (LOG.isLoggable(Level.FINE))
                                LOG.fine("Saw incomplete cache on disk: " + 
                                        disks[i].getId().toStringShort());
                        
                    } else
                        failedDisk = true;
                } else
                    failedDisk = true;
            }
            if (failedDisk) uniqFailures++;
        }
      
        if (uniqFailures != 0)
            LOG.info(SYSCACHE_PREFIX + "Currently " + uniqFailures + 
                     " unique failures in system caches.");
     
        return (uniqFailures <= calcMaxUniqFailures());
    }
    
    public static int calcMaxUniqFailures() { 
        /*
         * How many uniq failures can we have in terms of caches and still 
         * have a valid view of the data on the cluster ? 
         * 
         *  - 2 uniq failures on a 16 node cluster 
         *  - 1 uniq failure on an 8 node cluster 
         */
        int maxUniqFailures = 0;
        ClusterProperties props = ClusterProperties.getInstance();
        int num_nodes = props.getPropertyAsInt(NUM_NODES);
        if (num_nodes == 8) {
            maxUniqFailures = 1;
        } else if (num_nodes == 16) {
            maxUniqFailures = 2;
        }
        return maxUniqFailures; 
    }
   
    /**
     * Wait for caches to be up and running before proceeding otherwise 
     * mark the state as waiting for repopulation.
     *  
     */
    private void waitForCaches() throws SysCacheException {
        // wait for all caches to report they are up and running
        int num_nodes = ClusterProperties.getInstance().getPropertyAsInt(NUM_NODES);
        long start = System.currentTimeMillis();
       
        LOG.info(SYSCACHE_PREFIX + "Nodes to check: " + num_nodes);
        while (!enoughCachesUp()) {
            LOG.info(SYSCACHE_PREFIX + "Waiting for enough disks to be up");
            pause(LOOP_TIMEOUT);
            
            if (System.currentTimeMillis() - start > START_TIMEOUT) {
                break;
            }
        }
       
        if (enoughCachesUp())  {
            updateStateAndInit(RUNNING,false);
            LOG.info(SYSCACHE_PREFIX + "System Cache is up.");
        } else 
            updateStateAndInit(WAITFORREPOP,false);
    }
  
    /** 
     * Retrieves the last repop cycle completion witout any errors for the 
     * populate system cache task from the Mgmt layer.
     * 
     * @return
     * @throws ManagedServiceException 
     */
    private long lastRepopCycleStart() throws ManagedServiceException { 
        MgmtServerIntf api = MgmtServerIntf.Proxy.getMgmtServerAPI();
        if (api == null) {
            LOG.severe(SYSCACHE_PREFIX + 
              "waitForRepop failed: can't retrieve MgmtServer api");
            return 0;
        }
        
        int taskId = api.getTaskId("PopulateSysCache");
        long cycleStartTime = api.getTaskSuperCycleStart(taskId);
        long cycleRunTime   = api.getTaskSuperRunStart(taskId);

        // If both are > 0 and if superCycleStart > superRunStart, then 
        // superCycleStart is the start time of the most recent error-free 
        // cycle, and now is the end of it.
        if (cycleStartTime > 0 
                && cycleRunTime > 0 && cycleStartTime > cycleRunTime)
            return cycleStartTime;
            
        return 0;
    }
    
    private void startupSystemCache() throws SysCacheException {
        updateInitState(true); 
        String current = _properties.getProperty(SYSTEM_CACHE_STATE);
        long firstError = _properties.getPropertyAsLong(FIRST_ERROR_TIME);
        long lastError = _properties.getPropertyAsLong(LAST_ERROR_TIME);
        
        if (LOG.isLoggable(Level.INFO))
        LOG.info(SYSCACHE_PREFIX + "Startup system cache, current state = " +
                 ((current == null) ? "null" : current) 
                 + " first fault at " 
                 + ((firstError == Long.MIN_VALUE) 
                         ? "NEVER" : new Date (firstError))
                 + " last fault at "
                 + ((lastError == Long.MIN_VALUE)
                         ? "NEVER" : new Date (lastError)));

        // If we had errors, we want to set our state to wait for repop.
        if (firstError>0) current = WAITFORREPOP;
        
        if (current == null || current.equals(STOPPED)) {
            waitForCaches();
            checkRestored();
        } else if (current.equals(RUNNING)) {
            waitForCaches();
            checkRestored();
        } else if (current.equals(RESTORING) || current.equals(RESTOR_FT)) {
            // Wipe must be done in order to get out of here.
            updateStateAndInit(CORRUPTED,false);
        } else if (current.equals(CORRUPTED)) {
            // nothing to do stay in this state.
            updateStateAndInit(CORRUPTED,false);
        } else if (current.equals(WAITFORREPOP)) { 
            // stay in waiting for repop until one cycle has completed. 
            updateStateAndInit(WAITFORREPOP,false);
        } else {
            // This shouldn't happen.. throw runtimexception
            throw new RuntimeException("Bad config property: " + 
                                       SYSTEM_CACHE_STATE + "=" + 
                                       current);
        }
    }
   
    public boolean isInInit() { 
        return _inInit;
    }
   
    private NodeMgrService.Proxy getNodeMgrProxy(){ 
        NodeMgrService.Proxy proxy = null;
        long start = System.currentTimeMillis();
        do {
            proxy = ServiceManager.proxyFor (ServiceManager.LOCAL_NODE);
            try { 
                Thread.sleep(PROXY_SLEEP);
            } catch (InterruptedException ignore) { } 
        } while (proxy == null && (System.currentTimeMillis() - start < TIMEOUT));
        return proxy;
    }
    
    private boolean iAmMaster() { 
        NodeMgrService.Proxy proxy = getNodeMgrProxy(); 
        Node me = proxy.getNode();
        Node master = proxy.getMasterNode();
        return (master != null  && me.nodeId == master.nodeId);
    }
    
    private void setInitBasedOnMaster() throws ManagedServiceException { 
        setInit(getInitFromMaster());
    }

    private boolean getInitFromMaster() { 
        NodeMgrService.Proxy proxy = getNodeMgrProxy(); 
        Node master = proxy.getMasterNode();
        MDManagedService remote =  null;
        ManagedServiceException exception = null;
        
        long start = System.currentTimeMillis();
        do {
            remote = MDManagedService.Proxy.getServiceAPI(master.nodeId);
           
            if (remote != null) { 
                try {
                    return remote.isSystemCacheInInit();
                } catch (ManagedServiceException e) { 
                   // crap lets try again 
                   remote = null;
                   exception = e;
                }
            }
            
            try { 
                Thread.sleep(PROXY_SLEEP);
            } catch (InterruptedException ignore) { } 
        } while (remote == null && (System.currentTimeMillis()-start < TIMEOUT));
    
        if (exception == null) 
            throw new RuntimeException("Unable to get the proxy to the master node :(");
        else 
            throw new RuntimeException("Unable to make call to the master.",exception);
    }
    
    public String getStateFromMaster() throws SysCacheException { 
        if (getInitFromMaster()) 
            return STARTING;
        return (String) _properties.getProperty(SYSTEM_CACHE_STATE);
    }
    
    private synchronized void setInit(boolean inInit) { 
        _inInit = inInit;
    }
  
	public void run() {
        String current = STARTING;
       
        try {
            if (iAmMaster()) {
	            // First boot I'm the master so init is true and everyone 
                // should set it to that
	            updateInitState(true);
	        } else 
                setInitBasedOnMaster();
	       
            LOG.info(SYSCACHE_PREFIX + "System cache state machine in '" + 
                     getState() + "' state.");
            
            long lastReport = 0;
            // Non Masters just go and sit in the loop watching the state
            while (_running) {
                current = getState();
               
                if (iAmMaster()) {
                    if (isInInit()) {
                        LOG.info(SYSCACHE_PREFIX + "Master doing init.");
                        startupSystemCache();
                    }
                    
                    current = _properties.getProperty(SYSTEM_CACHE_STATE); 
                    // spam only every minute... 
                    if ((System.currentTimeMillis() - lastReport) > MSG_INTERVAL)  {
                        lastReport = System.currentTimeMillis();
	                    if (current != null && current.equals(CORRUPTED))
	                        LOG.info(SYSCACHE_PREFIX +
                                     "System cache state: '" + current + 
                                     "', needs intervention.");
	                    else {
                            boolean oaRestoreInCourse = 
                                _properties.getPropertyAsBoolean(RestoreSession.PROP_RESTORE_SESSION_IN_PROGRESS);
                            if (oaRestoreInCourse){
                                LOG.info(SYSCACHE_PREFIX +
                                         "System cache state: '" + current + ", reading tape'");
                            }
                            else {
                                LOG.info(SYSCACHE_PREFIX +
                                         "System cache state: '" + current + "'");
                            }
                        }
                    }
                   
                    /*
                     * We have a new error, so set the state to WAITFORREPOP
                     */
                    if(_newError) {
                        updateState(WAITFORREPOP);
                        _newError = false;
                    }
                    
                    /*
                     * This only happens when the old master died through 
                     * escalation, he goes down and sets the state to STOPPED.
                     */
                    if (current.equals(STOPPED)) {
                        LOG.info(SYSCACHE_PREFIX + 
                                 "Master failover happened, and the state was" + 
                                 " set to stopped, new master bringing cache up" +
                                 " again.");
                        startupSystemCache();
                    }
                   
                    /*
                     * XXX: 
                     * At runtime how do we detect that we dont' have enough 
                     * valid caches ? like if for example we lose two disks in a
                     * very small period of time ? This is runtime during restore
                     * as well...
                     */
                    
                    if (current.equals(RESTORING) || current.equals(RESTOR_FT) ) {
                        // check sys cache for completion of restoring work
                        checkRestored();
                        pause(RESTORE_TIMEOUT);
                    }
                    
                    /*
                     * If waiting for repop then check if a cycle has completed 
                     * since we last checked.
                     * 
                     */
                    if (current.equals(WAITFORREPOP)) { 
                        long startOfCurrentCycle = lastRepopCycleStart();
                        long firstError 
                            = _properties.getPropertyAsLong(FIRST_ERROR_TIME, 
                                                            Long.MIN_VALUE);
                        long lastError 
                            = _properties.getPropertyAsLong(LAST_ERROR_TIME, 
                                                        Long.MIN_VALUE);
                        if (startOfCurrentCycle > 0) {
                            if (startOfCurrentCycle > lastError) {
                                firstError = Long.MIN_VALUE;
                                lastError = Long.MIN_VALUE;
                                
                                if(LOG.isLoggable(Level.INFO))
                                    LOG.info(SYSCACHE_PREFIX + 
                                        "PopulateSysCache completed full cycle," 
                                        + " we may now transit into running "
                                        + " again.");
                                updateState(RUNNING);
                                _repeatedMsglevel = Level.INFO;
                            } 
                            else if (firstError < startOfCurrentCycle
                                        && startOfCurrentCycle < lastError) {
                                firstError = startOfCurrentCycle;
                                if(LOG.isLoggable(_repeatedMsglevel))
                                    LOG.log(_repeatedMsglevel, SYSCACHE_PREFIX + 
                                        "PopulateSysCache completed full cycle," 
                                        + " but insert errors exist outside its"
                                        + " reliability window: first now " 
                                        + new Date (firstError) 
                                        + ", last now " 
                                        + new Date (lastError));
                                _repeatedMsglevel = Level.FINE;
                            }
                            
                            HashMap newValues = new HashMap();
                            newValues.put(SysCache.FIRST_ERROR_TIME, 
                                          Long.toString(firstError));
                            newValues.put(SysCache.LAST_ERROR_TIME, 
                                          Long.toString(lastError));
                            
                            try {
                                ClusterProperties.getInstance().putAll(newValues);
                            } catch (ServerConfigException sce) {
                                throw new ManagedServiceException (
                                    "Unable to update config with syscache"
                                        + " errors time");
                            }
                        } else
                            LOG.info(SYSCACHE_PREFIX + 
                                     "Waiting for PopulateSysCache to complete "
                                    + " a full cycle.");
                    }
                }
                
                /*
                 *  Check state of caches on this node. All nodes monitor there
                 *  own caches every 10s and therefore if there is corruption 
                 *  at runtime we will be able to detect it, offline the cache
                 *  and wipe and recreate, also making sure to flag the cache
                 *  as being incomplete so that system cache state machine doesn't
                 *  count it as a       valid cache for backup.
                 */
                int corrupt_count = 0;
                Disk[] disks = DiskProxy.getDisks(ServiceManager.LOCAL_NODE);
                if (disks != null)
	                for (int d = 0; d < LayoutConfig.DISKS_PER_NODE; d++) {
	                    MDManagedService.Proxy proxy = 
	                        MDManagedService.Proxy.getProxy(ServiceManager.LOCAL_NODE);
	                    MDManagedService remote = 
	                        MDManagedService.Proxy.getServiceAPI(ServiceManager.LOCAL_NODE);
	   
	                    if (disks[d] != null &&  
	                        proxy != null &&
	                        disks[d].isEnabled() && 
	                        proxy.isCacheCorrupted(disks[d])) {
	                        /*
	                         * Found a corrupted cache lets clean it
	                         */
	                        try {
	                            LOG.info(SYSCACHE_PREFIX + 
	                                     "Detected corrupted cache on " + 
	                                     disks[d].diskIndex() + 
	                                     " proceeding to wipe.");
	                            remote.wipeCache(CacheClientInterface.SYSTEM_CACHE, disks[d]);
	                            corrupt_count++;
	                            
	                            // RMI call to MgmtServer to tell it to reset
	                            // the state of syscache completion status.
	                            MgmtServerIntf api
	                                = MgmtServerIntf.Proxy.getMgmtServerAPI();
	                            if (api != null) {
	                                api.resetSysCache();
	                                LOG.info(SYSCACHE_PREFIX
	                                         + "Resetting syscache completion "
	                                         + "time clusterwide due to corrupted "
	                                         + "syscache on " 
	                                         + disks[d].diskIndex());       
	                            } else {
	                                LOG.warning(
                                        "Unable to reset syscache completion "
                                        + "time, MgmtServer api is null");
	                            }

	                        } catch (EMDException e) {
	                            LOG.log(Level.SEVERE, SYSCACHE_PREFIX + 
	                                    "Unable to wipe system cache: " + 
	                                    disks[d].diskIndex(), e);
	                        } catch (ManagedServiceException e) {
	                            LOG.log(Level.SEVERE,SYSCACHE_PREFIX + 
	                                    "Unable to wipe system cache: " + 
	                                    disks[d].diskIndex(), e);
	                        }
	                    }
	                }
	                    
                if (corrupt_count > 0)
                    LOG.info(SYSCACHE_PREFIX + "Found and fixed " + 
                             corrupt_count + " corrupted cache(s).");
         
                pause(LOOP_TIMEOUT);
            }
          
            /*
             * Shutodwn sequence for the system cache state machine.
             */
            if (iAmMaster()) {
                LOG.info(SYSCACHE_PREFIX + 
                  "Master shutting down state machine.");
                // If we are running or restoring we can shut down nicely and 
                // make sure that all available system caches do so as well.
                if (current.equals(RUNNING) || current.equals(RESTORING)) {
                    LOG.info(SYSCACHE_PREFIX + "Shutting down from : " + current);
                    // update the config to the STOPPED state
                    updateState(STOPPED);
                    LOG.info(SYSCACHE_PREFIX + "State update in config.");
                } else if (current.equals(WAITFORREPOP)) { 
                    LOG.info(SYSCACHE_PREFIX + 
                             "Going down in waiting for repopulation and maintaining that state.");
                } else { 
                    LOG.info(SYSCACHE_PREFIX +
                      "Going down in a bad state: " + current + " :(");
                }
            }
            
        } catch (SysCacheException e) {
            // if we had an exception then we should make the service
            // restart in order for the issue to be 
            LOG.log(Level.SEVERE,
              SYSCACHE_PREFIX + "Failure in system cache state machine.",e);
            throw new RuntimeException("Failure in system cache state machine.", e);
        } catch (ManagedServiceException e) { 
            LOG.log(Level.SEVERE,
              SYSCACHE_PREFIX + "Failure in system cache state machine.",e);
            throw new RuntimeException("Failure in system cache state machine.", e);
        }
	 }
    
    private void checkRestored() throws SysCacheException {
        // check if all objects in the system cache are reported
        // as restored and if so then change the current state to
        // running.
        String current = _properties.getProperty(SYSTEM_CACHE_STATE); 
        boolean oaRestoreInCourse = 
                          _properties.getPropertyAsBoolean(
                               RestoreSession.PROP_RESTORE_SESSION_IN_PROGRESS);
        
        if (current != null && !current.equals(CORRUPTED) && !oaRestoreInCourse) 
	        try {
	            QueryResult result = _mdClient.query(CacheClientInterface.SYSTEM_CACHE, 
	                                                 SystemCacheConstants.SYSTEM_QUERY_ISNOTRESTORED,
	                                                 null,
	                                                 1);
	            if (result.results.size() == 0) {
	                // We're good we've completed restore
	                // update state to RUNNING
                    if (current.equals(RESTORING) || current.equals(RESTOR_FT)) {
                        LOG.info(SYSCACHE_PREFIX +
                          "Restore finished, changing state to running");
                        updateState(RUNNING);
                    }
	            } else if (!current.equals(RESTORING) && !current.equals(RESTOR_FT)) {
	                updateState(RESTORING);
                }
	            
	        } catch (ArchiveException e) {
	            LOG.log(Level.SEVERE,
                  SYSCACHE_PREFIX + "Unable to query system cache.", e);
	        } 
    }
 
    private void updateInitState(boolean inInit)
        throws SysCacheException {

        LOG.info(SYSCACHE_PREFIX + "Set inInit to " + inInit);

        setInit(inInit);
        try {
            _properties.put(SYSTEM_CACHE_INIT, Boolean.toString(inInit)); 
        } catch (ServerConfigException e) {
            throw new SysCacheException("Unable to update '" + 
              SYSTEM_CACHE_INIT + 
              "' to " + inInit +  " in config.", e);
        }
    }
    
    private void updateStateAndInit(String state, boolean inInit)
        throws SysCacheException {

        LOG.info(SYSCACHE_PREFIX + "Update state to " + state +
          " and set inInit to " + inInit);

        setInit(inInit);
        HashMap map = new HashMap();
        map.put(SYSTEM_CACHE_INIT, Boolean.toString(inInit));
        map.put(SYSTEM_CACHE_STATE, state);
        try {
            _properties.putAll(map);
        } catch (ServerConfigException e) {
            throw new SysCacheException("Unable to update '" + SYSTEM_CACHE_STATE + 
              "' to " + state + " in config.", e);
        }
    }
    
    private void updateState(String state) throws SysCacheException {
        LOG.info(SYSCACHE_PREFIX + "Update state to " + state);

        try {
            _properties.put(SYSTEM_CACHE_STATE, state);
        } catch (ServerConfigException e) {
            throw new SysCacheException("Unable to update '" + SYSTEM_CACHE_STATE + 
                                        "' to " + state + " in config.", e);
        }
    }

    private String getState() throws SysCacheException {
        if (_inInit) 
            return STARTING;
        return (String) _properties.getProperty(SYSTEM_CACHE_STATE);
    }
    
    public static void changeState(String state) throws SysCacheException {
        try {
            ClusterProperties.getInstance().put(SYSTEM_CACHE_STATE, state);
        } catch (ServerConfigException e) {
            throw new SysCacheException("Unable to update system cache state.",e);
        }
    }
    
    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {/* ignore */}
    }

    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        // If init property changes to false then we've finished init.
        if (prop.equals(SYSTEM_CACHE_INIT)) {
              
            if (!Boolean.parseBoolean((String) evt.getNewValue())) {
                setInit(false);
            } else {
                setInit(true);
            }
            
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest(SYSCACHE_PREFIX +
                           "Notification for property _inInit. Set to " + 
                           _inInit);
        }
        if (prop.equals(SYSTEM_CACHE_STATE)) {
            if (LOG.isLoggable(Level.FINEST))
                LOG.finest(SYSCACHE_PREFIX + "" +
                           "Notification for property syscache state. No action");
        }
        if (prop.equals(LAST_ERROR_TIME)) {
            _newError = true;
        }
    }
    
    public long getFirstErrorTime() {
        return _properties.getPropertyAsLong(FIRST_ERROR_TIME, Long.MIN_VALUE);
    }
    
    public void setInsertFailureTime (long timestamp) 
            throws ServerConfigException {
        HashMap newValues = new HashMap();
        long firstError = _properties.getPropertyAsLong(FIRST_ERROR_TIME, 
                                                        Long.MIN_VALUE);
        if (firstError<0) {
            newValues.put(FIRST_ERROR_TIME, Long.toString(timestamp));
        }
        newValues.put(LAST_ERROR_TIME, Long.toString(timestamp));
        
        if (LOG.isLoggable(Level.INFO)) {
            LOG.info(SYSCACHE_PREFIX 
                + " Performing config update to set SysCache insert failure"
                + " times. Timestamp=" + timestamp + " (" + new Date(timestamp)
                + ")" + newValues.toString());
        }
        
        synchronized (_properties) {
            _properties.putAll(newValues);
        }
    }
}
