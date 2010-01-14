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



package com.sun.honeycomb.lockmgrcli;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Random;

import com.sun.honeycomb.cm.lockmgr.LockMgr;
import com.sun.honeycomb.cm.lockmgr.LockMgrIntf;
import com.sun.honeycomb.cm.lockmgr.LockMgr.LockOwner;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.config.ClusterProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;


public class LockMgrCli implements LockMgrCliIntf, PropertyChangeListener
{
    //
    // Describes the test to be run-- uses config/update to specify it.
    //
    private static final String PROP_LOCK_MGR_CLI_TEST =
      "honeycomb.lockmgr.test";

    private static final int TEST_NONE = 0;
    private static final int TEST_LOCK_UNLOCK_SYNC = 1;
    private static final int TEST_LOCK_UNLOCK_ASYNC = 2;
    private static final int TEST_LOCK_UNLOCK_CONT = 4;
    private static final int TEST_LOCK_CLI_EXCEPT_WAIT = 8;
    private static final int TEST_LOCK_CLI_EXCEPT_OTHER = 16;
    private static final int TEST_LOCK_SVC_EXCEPT = 32;
    private static final int TEST_ALL = -1;

    //
    // Describes the action to the peer service
    //
    private static final int ACT_LOCK_UNLOCK_SYNC = 1;
    private static final int ACT_LOCK_UNLOCK_ASYNC = 2;
    private static final int ACT_LOCK_UNLOCK_CONTENTION = 3;
    private static final int ACT_LOCK_CLI_EXCEPT = 4;
    private static final int ACT_LOCK_SVC_EXCEPT = 5;

    private static final int DEFAULT_NB_LOCKS = 10;

    private static final String SVC_NAME = "LockMgrCli";

    private Thread           thr = null;
    private volatile boolean keepRunning = false;
    private int nodeId = -1;
    private int testType = 0;
    private LockOwner owner = null;

    private static final Logger logger = 
      Logger.getLogger(LockMgrCli.class.getName());

    private static final String LOCK_MGR_CLI_PREFIX = 
      "CLI_" + LockMgr.LOCK_MGR_PREFIX;

    public LockMgrCli() {
        thr = null;
        testType = 0;
        nodeId = getNodeMgrProxy().nodeId();
        ClusterProperties.getInstance().addPropertyListener(this);
        keepRunning = false;
    }

    public void shutdown() {

        keepRunning = false;
        if (thr != null) {
            thr.interrupt();
        }

        boolean stopped = false;
        while (!stopped) {
            try {
                if (thr != null) {
                    thr.join();
                }
                stopped = true;
            } catch (InterruptedException ignored) {
                
            }
        }
        logger.info(LOCK_MGR_CLI_PREFIX + "LockMgrCli now STOPPED");
    }

    public void syncRun() {
    }

    public void run() {

        int delay = 5000; // 5 sec
        keepRunning = true;
        int nbLocks = DEFAULT_NB_LOCKS;

        owner = new LockOwner();

        logger.info(LOCK_MGR_CLI_PREFIX + "LockMgrCli now STARTED, " +
            owner);

        thr = Thread.currentThread();
        while (keepRunning) {

            ServiceManager.publish(this);
            try {
                Thread.sleep(delay);

                if (isMasterNode()) {
                    if (testType == -1) {
                        runAllTests(nbLocks);
                        nbLocks *= 2;
                        if (nbLocks > 1000) {
                            nbLocks = DEFAULT_NB_LOCKS;
                        }
                    } else {
                        if (isTestSet(TEST_LOCK_UNLOCK_SYNC)) {
                            performLockUnlockToFixedTarget(DEFAULT_NB_LOCKS,
                              ACT_LOCK_UNLOCK_SYNC);
                        }
                        if (isTestSet(TEST_LOCK_UNLOCK_ASYNC)) {
                            performLockUnlockToFixedTarget(DEFAULT_NB_LOCKS,
                              ACT_LOCK_UNLOCK_ASYNC);
                        }
                        if (isTestSet(TEST_LOCK_UNLOCK_CONT)) {
                            performLockUnlockToFixedTarget(DEFAULT_NB_LOCKS,
                              ACT_LOCK_UNLOCK_CONTENTION);
                        }
                        if (isTestSet(TEST_LOCK_CLI_EXCEPT_WAIT)) {
                            performLockWithExceptionClient(DEFAULT_NB_LOCKS,
                              true);
                        }
                        if (isTestSet(TEST_LOCK_CLI_EXCEPT_OTHER)) {
                            performLockWithExceptionClient(DEFAULT_NB_LOCKS,
                              false);
                        }
                        if (isTestSet(TEST_LOCK_SVC_EXCEPT)) {
                        }
                        testType = TEST_NONE;
                    }
                }
            } catch (InterruptedException ignored) {
                if (!keepRunning) {
                    break;
                }
            }
        }
    }
    
    public ManagedService.ProxyObject getProxy () {
        return new Proxy();
    }

    //
    // Listen to config/Update changes.
    //
    public void propertyChange(PropertyChangeEvent event) {

        String prop = event.getPropertyName();
        if (prop.equals(PROP_LOCK_MGR_CLI_TEST)) {
            testType = ClusterProperties.getInstance().getPropertyAsInt(prop,
              TEST_NONE);

            logger.info(LOCK_MGR_CLI_PREFIX +
                "Received notification to run tests " + testType);
        }
    }


    //
    // RMI CALLS
    //
    public boolean doAction(int action, int nodeTarget, String [] locks)
        throws ManagedServiceException {

        PerformActionAsync handler = null;

        switch (action) {
        case ACT_LOCK_UNLOCK_SYNC:
            return performLockUnlockSync(nodeTarget, locks);
        case ACT_LOCK_UNLOCK_ASYNC:
            handler = new PerformActionAsync(nodeTarget, locks, false);
            new Thread(handler).start();
            return true;
        case ACT_LOCK_UNLOCK_CONTENTION:
            handler = new PerformActionAsync(nodeTarget, locks, true);
            new Thread(handler).start();
            return true;
        case ACT_LOCK_CLI_EXCEPT:
            return performLockCliException(nodeTarget, locks);
        case ACT_LOCK_SVC_EXCEPT:
            return performLockSvcException(nodeTarget, locks);
        default:
            throw new RuntimeException("unknown action!!!");
        }
    }
    
    private boolean performLockUnlockSync(int target, String [] locks) 
        throws ManagedServiceException {
        return performLockUnlock("Sync", target, locks);
    }

    private void performLockUnlockAsync(int target, String [] locks) 
        throws ManagedServiceException {
        boolean res = performLockUnlock("Async", target, locks);
    }

    private boolean performLockUnlock(String tag, int target, String [] locks) 
        throws ManagedServiceException {
        
        logger.info(LOCK_MGR_CLI_PREFIX + "Start LockUnlock" + tag);

        LockMgrIntf api = LockMgrIntf.Proxy.getLockMgrAPI(target);
        if (api == null) {
            logger.severe(LOCK_MGR_CLI_PREFIX + 
              "Failed to retrieve LockMgr api");
            return false;
        }

        LockOwner RMIowner = new LockOwner();

        //
        // Lock them all and double check we can't lock twice
        //
        for (int i = 0; i < locks.length; i++) {
            String lock = locks[i];


            boolean res = api.tryLock(owner, lock);
            if (!res) {
                logger.severe(LOCK_MGR_CLI_PREFIX + "Failed to lock " + lock);
                return false;
            }

            res = api.tryLock(owner, lock);
            if (!res) {
                logger.severe(LOCK_MGR_CLI_PREFIX + "Failed to lock " + 
                  lock + " twice in a row for same owner");
                return false;
            }
        }

        logger.info(LOCK_MGR_CLI_PREFIX + "Completed LockUnlock" + 
          tag +" (1/2)");

        //
        // Unlock , lock , unlock 
        //
        for (int i = 0; i < locks.length; i++) {
            String lock = locks[i];

            api.unlock(owner, lock, false);

            boolean res = api.tryLock(owner, lock);
            if (!res) {
                logger.severe(LOCK_MGR_CLI_PREFIX + "Failed to lock " + lock);
                return false;
            }
            api.unlock(owner, lock, false);
        }
        logger.info(LOCK_MGR_CLI_PREFIX + "Completed LockUnlock" + 
          tag +" (2/2)");
        return true;
    }

    private class PerformActionAsync implements Runnable {
        
        private int target;
        private String [] locks;
        private boolean contention;

        private PerformActionAsync(int target,
          String [] locks, boolean contention) {
            this.target = target;
            this.locks = locks;
            this.contention = contention;
        }
        
        public void run() {
            try {
                if (contention) {
                    performLockUnlockContention(target, locks);
                } else {
                    performLockUnlockAsync(target, locks);
                }
            } catch (ManagedServiceException mse) {
                logger.log(Level.SEVERE, LOCK_MGR_CLI_PREFIX +
                  "Failed to run LockUnlockASync/Contention", mse);
            }
        }
    }

    private void performLockUnlockContention(int target, String [] locks) 
        throws ManagedServiceException {

        logger.info(LOCK_MGR_CLI_PREFIX + "Start LockUnlockContention");

        LockMgrIntf api = LockMgrIntf.Proxy.getLockMgrAPI(target);
        if (api == null) {
            logger.severe(LOCK_MGR_CLI_PREFIX + "Failed to retrieve LockMgr api");
            return;
        }
        
        LockOwner RMIowner = new LockOwner();

        //
        // Lock/unlock them 
        // If the lock is taken, wait and retry.
        // At some point we should succeed to have locked them all
        //
        for (int i = 0; i < locks.length; i++) {

            String lock = locks[i];
            boolean res = false;
            int retry = 0;

           do {
                res = api.tryLock(owner, lock);
                if (res) {
                    break;
                }
                retry++;
                try {
                    Thread.sleep(27);
                } catch (InterruptedException ie) {
                }
            } while (!res);

            if (retry > 0) {
                logger.info(LOCK_MGR_CLI_PREFIX + "got lock " + lock +
                    " after " + retry + " retries");
            }

            try {
                Thread.sleep(213);
            } catch (InterruptedException ignored) {
            }

            api.unlock(owner, lock, false);
        }

        logger.info(LOCK_MGR_CLI_PREFIX + "Completed LockUnlockContention ");
    }

    private boolean performLockCliException(int target, String [] locks) 
        throws ManagedServiceException {

        logger.info(LOCK_MGR_CLI_PREFIX + "Start LockCliException");

        LockMgrIntf api = LockMgrIntf.Proxy.getLockMgrAPI(target);
        if (api == null) {
            logger.severe(LOCK_MGR_CLI_PREFIX + "Failed retrieve LockMgr api");
            return false;
        }

        LockOwner RMIowner = new LockOwner();
            
        //
        // Lock/unlock them 
        // If the lock is taken, wait and retry.
        // At some point we should succeed to have locked them all
        //
        for (int i = 0; i < locks.length; i++) {

            String lock = locks[i];
            boolean res = false;
            int retry = 0;
 
            res = api.tryLock(owner, lock);
            if (!res) {
                logger.severe(LOCK_MGR_CLI_PREFIX + "Failed to lock " + lock);
                return false;
            }

            if (i == (locks.length -1 )) {
                logger.info(LOCK_MGR_CLI_PREFIX + 
                  "Completed LockCliException by exiting with lock " +
                    lock + " held");
                System.exit(1);
            }

            api.unlock(owner, lock, false);
        }
        // To lure the compiler...
        return true;
    }

    private boolean performLockSvcException(int target, String [] locks) {
        return true;
    }

    //
    // MASTER NODE DRIVES THE TESTS
    //
    private void runAllTests(int nbLocks) throws InterruptedException {
        performLockUnlockToFixedTarget(nbLocks, ACT_LOCK_UNLOCK_SYNC);
        Thread.sleep(5000);
        performLockUnlockToFixedTarget(nbLocks, ACT_LOCK_UNLOCK_ASYNC);
        Thread.sleep(5000);
        performLockUnlockToFixedTarget(nbLocks, ACT_LOCK_UNLOCK_CONTENTION);
        Thread.sleep(5000);
        performLockWithExceptionClient(nbLocks, true);
        Thread.sleep(5000);
        performLockWithExceptionClient(nbLocks, false);
    }

    //
    // Pick each cluster node (alive and no master) as the target on which
    // other nodes will trigger lock/unlock operations.
    //
    private void performLockUnlockToFixedTarget(int numbers, int action) {

        logger.info(LOCK_MGR_CLI_PREFIX +
          "=> Start LockUnlock test, action = " + getActionName(action));

        Node [] allNodes = getNodeMgrProxy().getNodes();
        Node target = null;

        for (int j = 0; j < allNodes.length; j++) {
            if (allNodes[j] != null &&
              allNodes[j].isAlive() && 
              !allNodes[j].isMaster()) {
                target = allNodes[j];
            }
            if (target == null) {
                continue;
            }

            for (int i = 0; i < allNodes.length; i++) {
                if (allNodes[i] != null &&
                    allNodes[i].isAlive() && 
                  !allNodes[i].isMaster() &&
                  (target.nodeId() != allNodes[i].nodeId())) {

                    Node curNode = allNodes[i];
                    int faults = 0;
                    int lockNodeId = -1;

                    switch (action) {
                    case ACT_LOCK_UNLOCK_ASYNC:
                        lockNodeId = curNode.nodeId();
                        break;
                    default:
                        /* ACT_LOCK_UNLOCK_CONTENTION */
                        /* ACT_LOCK_UNLOCK_SYNC */
                        lockNodeId = nodeId;
                        break;
                    }
                    String [] locks = createLockNames(getActionName(action),
                      lockNodeId, numbers);

                    logger.info(LOCK_MGR_CLI_PREFIX +
                      "Request lock/unlock on node " + curNode.nodeId() +
                      ", target = node " + target.nodeId());

                    LockMgrCliIntf api = 
                      LockMgrCliIntf.Proxy.getLockMgrCliAPI(curNode.nodeId());
                    if (api == null) {
                        throw new RuntimeException("can't get LockMgrCli API " +
                          " for node " + curNode.nodeId());
                    }
                    try {
                        boolean res  = api.doAction(action, 
                          target.nodeId(), locks);
                        if (!res) {
                            faults++;
                            logger.severe(LOCK_MGR_CLI_PREFIX +
                              "Failed to run  " + getActionName(action )+ 
                              " on node" + curNode.nodeId() +
                              " for target node " + target.nodeId());
                        }
                    } catch (ManagedServiceException mse) {
                        faults++;
                        logger.log(Level.SEVERE,
                          LOCK_MGR_CLI_PREFIX +
                          "Failed operation "  + getActionName(action ),
                          mse);
                    }                    
                    logger.info(LOCK_MGR_CLI_PREFIX +
                      "Completed lock/unlock on node " +curNode.nodeId() +
                      ", target = node " + target.nodeId() + 
                      " errors = " + faults);
                }
            }
        }

        logger.info(LOCK_MGR_CLI_PREFIX +
          "=> Completed LockUnlock test, action = " + getActionName(action));
    }

    private void performLockWithExceptionClient(int nbLockOk, 
      boolean waitForPeer) {

        logger.info(LOCK_MGR_CLI_PREFIX +
          "=> Start LockWithExceptionClient test");

        int action = ACT_LOCK_CLI_EXCEPT;

        Node [] allNodes = getNodeMgrProxy().getNodes();
        Node target = null;
        for (int i = 0; i < allNodes.length; i++) {
            if (allNodes[i] != null &&
                allNodes[i].isAlive() && 
              !allNodes[i].isMaster()) {
                target = allNodes[i];
            }
            if (target == null) {
                continue;
            }

            Node curNode = getRandomCandidate(allNodes, target);

            String [] locks = createLockNames(getActionName(action),
              nodeId, nbLockOk);

            LockMgrCliIntf api = 
              LockMgrCliIntf.Proxy.getLockMgrCliAPI(curNode.nodeId());
            if (api == null) {
                throw new RuntimeException("can't get LockMgrCli API " +
                  " for node " + curNode.nodeId());
            }
            try {
                api.doAction(action, target.nodeId(), locks);
            } catch (Throwable thw /* ServiceManagedException, 
                                      RuntimeException   */) {
                //
                // We expect to hit an exception because the 
                // service on the peernode (curNode) will kill 
                // itself before returning from RMI call
                //
                logger.info(LOCK_MGR_CLI_PREFIX +
                  "service on peer node " + curNode.nodeId() + 
                    "retruns with an exception as excepted");
            }
            if (!waitForPeer) {
                curNode = getNextRandomCandidate(allNodes, target, curNode);
                api = LockMgrCliIntf.Proxy.getLockMgrCliAPI(curNode.nodeId());
                if (api == null) {
                    throw new RuntimeException("Failed to get ockMgrCli API " +
                      " for node " + curNode.nodeId());
                }
            } else {
                long curIncarnation = getIncarnationNumber(curNode.nodeId());
                if (!waitForPeer(curNode.nodeId(), curIncarnation)) {
                    curNode = null;
                } else {
                }
            }

            //
            // Give some time for mailbox to get propagated
            // so we can hit the case we are the service has
            // restarted and the existing lock is invalid.
            //
            try {
                Thread.sleep(12000);
            } catch (InterruptedException ignore) {
            }
            
            if (curNode != null) {
                //
                // Retry same sequence but without failure either
                // - on the peer that came back
                // - on a different node 
                //
                api = LockMgrCliIntf.Proxy.getLockMgrCliAPI(curNode.nodeId());
                if (api == null) {
                    throw new RuntimeException("can't get LockMgrCli API " +
                      " for node " + curNode.nodeId());
                }
                try {
                    boolean res  = api.doAction(ACT_LOCK_UNLOCK_SYNC,
                      target.nodeId(), locks);
                    if (!res) {
                        logger.severe(LOCK_MGR_CLI_PREFIX +
                          "Failed to run operation on node " + 
                          curNode.nodeId() +
                          " for target node " + target.nodeId());
                    }
                } catch (ManagedServiceException mse) {
                    logger.log(Level.SEVERE,
                      LOCK_MGR_CLI_PREFIX +
                      "Failed operation ", mse);
                }                    
            }
        }
        logger.info(LOCK_MGR_CLI_PREFIX +
          "=> Completed LockWithExceptionClient test");
    }
    private void performLockWithExceptionServer() {
    }

    private String [] createLockNames(String prefix, int nodeid, int numbers) {

        String [] res = new String[numbers];

        for (int i = 0; i < numbers; i++) {
            StringBuffer buf = new StringBuffer();
            buf.append(prefix);
            buf.append("_");
            buf.append(nodeid);
            buf.append("_");
            buf.append(i);
            res[i] = buf.toString();
        }
        return res;
    }

    private String getActionName(int actionCode) {
        switch (actionCode) {
        case ACT_LOCK_UNLOCK_SYNC:
            return "ACT_LOCK_UNLOCK_SYNC";
        case ACT_LOCK_UNLOCK_ASYNC:
            return "ACT_LOCK_UNLOCK_ASYNC";
        case ACT_LOCK_UNLOCK_CONTENTION:
            return "ACT_LOCK_UNLOCK_CONTENTION";
        case ACT_LOCK_CLI_EXCEPT:
            return "ACT_LOCK_CLI_EXCEPT";
        case ACT_LOCK_SVC_EXCEPT:
            return "ACT_LOCK_SVC_EXCEPT";
        default:
            return "unknown";
        }
    }

    private Node getNextRandomCandidate(Node [] allNodes,
      Node target, Node curNode) {
        Node res = null;
        do {
            res = getRandomCandidate(allNodes, target);
            if (res.nodeId() != curNode.nodeId()) {
                return res;
            }
        } while (res.nodeId() == curNode.nodeId());
        return null;
    }

    private Node getRandomCandidate(Node [] allNodes, Node target) {

        Random genCandidate = new Random(System.currentTimeMillis());

        boolean [] candidates = new boolean[allNodes.length];
        int nbCandidates = 0;

        for (int i = 0; i < allNodes.length; i++) {
            if (allNodes[i] != null &&
                allNodes[i].isAlive() && 
              !allNodes[i].isMaster() &&
              (target.nodeId() != allNodes[i].nodeId())) {
                nbCandidates++;
                candidates[i] = true;
            } else {
                candidates[i] = false;
            }
        }
        if (nbCandidates > 0) {
            boolean found = false;
            do {
                int res = genCandidate.nextInt(nbCandidates + 1);
                if (candidates[res]) {
                    logger.info(LOCK_MGR_CLI_PREFIX +
                      "found candidate node = " + allNodes[res].nodeId());
                    return allNodes[res];
                }
            } while (!found);
        }
        throw new RuntimeException("Can't find a candidate node!!!");
    }


    private long getIncarnationNumber(int peer) {
        NodeMgrService.Proxy proxy = getNodeMgrProxy(peer);
        Service [] svc = proxy.getServices();
        Service peerSvc = null;
        for (int i = 0; i < svc.length; i++) {
            if (svc[i].getName().endsWith(SVC_NAME)) {
                peerSvc = svc[i];
                break;
            }
        }
        if (peerSvc == null) {
            throw new RuntimeException("Can't find peer svc"); 
        }
        return peerSvc.getIncarnationNumber();
    }

    private boolean waitForPeer(int peer, long incarnation) {

        int nbTry = 0;
        final int nbTryMax = 90; // 3 min (2 * 90 = 180 sec)
        do {
            int delay = 2000; // 2 sec
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
            }
            nbTry++;
            logger.info(LOCK_MGR_CLI_PREFIX + "wait for peer " +
                SVC_NAME + " one node " + peer + "...");
                
            NodeMgrService.Proxy proxy = getNodeMgrProxy(peer);
            Service [] svc = proxy.getServices();
            Service peerSvc = null;
            for (int i = 0; i < svc.length; i++) {
                if (svc[i].getName().endsWith(SVC_NAME)) {
                    peerSvc = svc[i];
                    break;
                }
            }
            if (peerSvc != null && peerSvc.isRunning()) {

                logger.info(LOCK_MGR_CLI_PREFIX + 
                  "found peer svc (name = " + peerSvc.getName() +
                  ", tag = " + peerSvc.getTag() + ")" +
                  " on node " + peer + 
                  ", prev incarnation = " + incarnation +
                  ", curIncarnation = " + peerSvc.getIncarnationNumber());

                if (incarnation < peerSvc.getIncarnationNumber()) {
                    return true;
                }
            }
        } while (nbTry < nbTryMax);
        logger.severe(LOCK_MGR_CLI_PREFIX + "Failed to waitForPeer on node " + 
          peer + ", time expired...");
        return false;
    }

    private boolean isTestSet(int thisTest) {
        if ((testType & thisTest) == thisTest) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isMasterNode() {
        int masterNodeId = getNodeMgrProxy().getMasterNode().nodeId();
        if (masterNodeId == nodeId) {
            return true;
        } else {
            return false;
        }
    }

    private NodeMgrService.Proxy getNodeMgrProxy() {
        return getNodeMgrProxy(ServiceManager.LOCAL_NODE);
    }

    private NodeMgrService.Proxy getNodeMgrProxy(int nodeid) {
        Object obj = ServiceManager.proxyFor(nodeid);
        if (! (obj instanceof NodeMgrService.Proxy)) {
            throw new RuntimeException("Can't retrieve NodeMgr Proxy");
        }
        return  (NodeMgrService.Proxy) obj;
    }
}