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



package com.sun.honeycomb.stressconfig;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Random;
import java.util.HashMap;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.util.Map;

import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.config.ClusterProperties;



final public class ConfigStresser implements ConfigStresserIntf
{

    private final static int MAX_EXECUTORS = 5;
    private final static int NODE_BASE_ID = 100;

    private final static Logger logger = 
      Logger.getLogger(ConfigStresser.class.getName());

    private final static String CONFIG_FILENAME = 
        CMMApi.UPDATE_STRESS_TEST.name();
    private final static String PROP_CONFIG_ITERATION = 
      "honeycomb.stress.config.iteration";
    private final static String PROP_CONFIG_NODE_FAILURE = 
      "honeycomb.stress.config.failure.nodeid";
    private final static String PROP_CONFIG_TYPE_FAILURE = 
      "honeycomb.stress.config.failure.type";

    public final static int FAILURE_NONE = 0;
    public final static int FAILURE_DROP_MSG_UPDATE = 1;
    public final static int FAILURE_DROP_MSG_COMMIT = 2;
    public final static int FAILURE_SET_STATUS_UPDATE_FALSE = 3;
    public final static int FAILURE_SET_STATUS_COMMIT_FALSE = 4;


    private final static String PROP_NUM_NODES  = 
      "honeycomb.cell.num_nodes";


    private boolean keepRunning;
    private Thread  currentThread;
    private int numNodes;
    private int masterNode;

    private Thread [] thExecutors;
    private ConfigExecutor [] configExecutors;

    public ConfigStresser() {
        keepRunning = false;
        currentThread = null;
        thExecutors = new Thread [MAX_EXECUTORS];
        configExecutors = new ConfigExecutor [MAX_EXECUTORS];
        for (int i = 0; i < MAX_EXECUTORS; i++) {
            configExecutors[i] = null;
        }
        numNodes = 
          ClusterProperties.getInstance().getPropertyAsInt(PROP_NUM_NODES);
        masterNode =
          ServiceManager.proxyFor(ServiceManager.LOCAL_NODE).nodeId();
    }
    
    public ManagedService.ProxyObject getProxy () {
        return new Proxy();
    }

    public void shutdown() {
        keepRunning = false;
        
        currentThread.interrupt();
        boolean stopped = false;

        while (!stopped) {
            try {
                currentThread.join();
                stopped = true;
            } catch (InterruptedException ignored) {
                
            }
        }
        logger.info(CMMApi.LOG_PREFIX + " ConfigStresser now STOPPED");        
    }

    public void syncRun() {
        currentThread = Thread.currentThread();
    }

    public void run() {

        logger.info(CMMApi.LOG_PREFIX + 
          "ConfigStresser now RUNNING");
        
        int delay = 5000;
        while (keepRunning) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
            }
            ServiceManager.publish(this);
        }
        shutdownExecutors();
    }

    public static String getConfigFileName() {
        return CONFIG_FILENAME;
    }

    private void shutdownExecutors() {
        for (int i = 0; i < MAX_EXECUTORS; i++) {
            stopExecutor(i, true);
        }        
    }

    //
    // RMI calls
    //
    public int startConfigUpdate(long waitInBetweenConfig, 
      boolean storeConfig, boolean nodeFailure, 
      int rateFailure) throws IOException {
        return createExecutor(waitInBetweenConfig,
          storeConfig, nodeFailure, rateFailure);
    }

    public String stopConfigUpdate(int executorId) throws IOException {
        String status = null;
        ConfigExecutor executor = stopExecutor(executorId, false);
        if (executor == null) {
            status = "Failed to stop executor";
        } else {
            status = executor.getFormattedStatus();
        }
        return status;
    } 


    public void resetFailure(long version) {

        logger.info(CMMApi.LOG_PREFIX + 
          "Reset failure for file, version = " + version);

        HashMap map = parseFile(version);
        if (map != null) {
            String valueTypeFailure = 
              (String) map.remove(PROP_CONFIG_TYPE_FAILURE);
            map.put(PROP_CONFIG_TYPE_FAILURE, Integer.toString(FAILURE_NONE));
            try {
                createFile(map, version);
            } catch (IOException ioe) {
                logger.log(Level.SEVERE,
                  CMMApi.LOG_PREFIX +
                  "failed to reset failure for file version " +
                  version, ioe);
            }
        }
    }

    // Used by CMM 
    public static boolean isDropFlagForUpdate(HashMap map,
      int nodeid, long version) {
        return isFailureSetForNode(map,
          nodeid, FAILURE_DROP_MSG_UPDATE, version);
    }

    public static boolean isDropFlagForCommit(HashMap map,
      int nodeid, long version) {
        return isFailureSetForNode(map,
          nodeid, FAILURE_DROP_MSG_COMMIT, version);
    }

    public static boolean isSetStatusFalseForUpdate(HashMap map,
      int nodeid, long version) {
        return isFailureSetForNode(map,
            nodeid, FAILURE_SET_STATUS_UPDATE_FALSE, version);
    }

    public static boolean isSetStatusFalseForCommit(HashMap map,
      int nodeid, long version) {
        return isFailureSetForNode(map,
            nodeid, FAILURE_SET_STATUS_COMMIT_FALSE, version);
    }

    private static boolean isFailureSetForNode(HashMap map,
      int nodeid, int value, long version) {

        String valueNode =  (String) map.get(PROP_CONFIG_NODE_FAILURE);
        if (valueNode == null) {
            logger.severe(CMMApi.LOG_PREFIX + 
                "Missing property " + PROP_CONFIG_NODE_FAILURE);
            return false;
        }
        if (Integer.parseInt(valueNode) != nodeid) {
            return false;
        }

        String valueFailure = (String) map.get(PROP_CONFIG_TYPE_FAILURE);
        if (valueFailure == null) {
            logger.severe(CMMApi.LOG_PREFIX + 
              "Missing property " + PROP_CONFIG_TYPE_FAILURE);
            return false;
        }
        if (Integer.parseInt(valueFailure) == value) {
            return true;
        } else {
            return false;
        }
    }

    public static HashMap parseFile(long version) {
        
        HashMap map = new HashMap();
        String filename = "unknown";
        try {
            filename = CONFIG_FILENAME + "." + version;
            BufferedReader reader = 
              new BufferedReader(new FileReader(filename));

            String line = reader.readLine();
            do {
                String [] lineElements = line.split("=");
                String name = lineElements[0].trim();
                String value =  lineElements[1].trim();
                map.put(name, value);
                line = reader.readLine();
            } while (line != null);

        } catch (IOException ioe) {
            logger.log(Level.SEVERE,
              CMMApi.LOG_PREFIX + "failed to parse file " +
              filename, ioe);
            return null;
        } catch (NumberFormatException nfe) {
            logger.log(Level.SEVERE,
              CMMApi.LOG_PREFIX + "failed to parse file, invalid format " +
              filename, nfe);
            return null;
        }
        return map;
    }


    public static void createFile(Map map, long version) throws IOException {

        String valueIteration = (String) map.get(PROP_CONFIG_ITERATION);
        String valueNodeFailure = (String) map.get(PROP_CONFIG_NODE_FAILURE);
        String valueTypeFailure = (String) map.get(PROP_CONFIG_TYPE_FAILURE);

        String filename = CONFIG_FILENAME + "." + version;

        File f = new File(filename);
        if (f.exists()) {
            logger.info(CMMApi.LOG_PREFIX +
              " File " + filename + " already exists, delete it");
            f.delete();
        }
        FileWriter fw = new FileWriter(f, false);
        BufferedWriter bw = new BufferedWriter(fw);

        String propIteration = PROP_CONFIG_ITERATION + " = " + 
          valueIteration;
        bw.write(propIteration, 0, propIteration.length());
        bw.newLine();

        String propNodeFailure =  PROP_CONFIG_NODE_FAILURE + " = " + 
          valueNodeFailure;
        bw.write(propNodeFailure, 0, propNodeFailure.length());
        bw.newLine();

        String propTypeFailure = PROP_CONFIG_TYPE_FAILURE + " = " + 
          valueTypeFailure;
        bw.write(propTypeFailure, 0, propTypeFailure.length());
        bw.newLine();
        bw.flush();
        bw.close();
    }

    private synchronized int createExecutor(long waitInBetweenConfig, 
      boolean storeConfig,
      boolean nodeFailure, int rateFailure) throws IOException {

        for (int i = 0; i < MAX_EXECUTORS; i++) {
            if (configExecutors[i] == null) {
                configExecutors[i] = new ConfigExecutor(waitInBetweenConfig,
                  storeConfig, nodeFailure, rateFailure, i);
                thExecutors[i] = new Thread(configExecutors[i]);
                thExecutors[i].start();
                return i + 1;
            }
        }
        return -1;
    }

    private synchronized ConfigExecutor stopExecutor(int executorId,
      boolean hard) {

        ConfigExecutor executor = getExecutor(executorId);
        if (executor == null) {
            return null;
        }
        Thread th = thExecutors[executorId -1];

        executor.isWorking = false;
        if (hard) {
            th.interrupt();
            boolean stopped = false;
            while (!stopped) {
                try {
                    th.join();
                    stopped = true;
                } catch (InterruptedException ignored) {
                }
            }
        }
        configExecutors[executorId - 1] = null;
        thExecutors[executorId - 1] = null;
        return executor;
    }

    private synchronized ConfigExecutor getExecutor(int executorId) {
        int index = executorId - 1;
        if (index < 0 || index >= MAX_EXECUTORS) {
            return null;
        }
        return configExecutors[index];
    }

    private String getFailureType(int type) {

        String res;
        switch (type) {
        case FAILURE_NONE:
            res = "none";
            break;
        case FAILURE_DROP_MSG_UPDATE:
            res = "drop_msg_update";
            break;
        case FAILURE_DROP_MSG_COMMIT:
            res = "drop_msg_commit";
            break;
        case FAILURE_SET_STATUS_UPDATE_FALSE:
            res = "set_status_update_false";
            break;
        case FAILURE_SET_STATUS_COMMIT_FALSE:
            res = "set_status_commit_false";
            break;
        default:
            res = "unknown";
            break;
        }
        return res;        
    }

    private class ConfigExecutor implements Runnable {

        private boolean nodeFailure;
        private int lastFailure;
        private long waitInBetweenConfig;
        private Boolean [] rateFailure;
        private int index;
        private boolean isWorking;
        private boolean storeConfig;
        private Random genFailNode;
        private long min;
        private long cumul;
        private long max;
        private int iteration;

        ConfigExecutor(long waitInBetweenConfig, 
          boolean storeConfig, boolean nodeFailure, int rate, int index) {
            this.nodeFailure = nodeFailure;
            this.lastFailure = FAILURE_SET_STATUS_COMMIT_FALSE;
            this.waitInBetweenConfig = waitInBetweenConfig;
            this.storeConfig = storeConfig;
            this.index = index;
            this.genFailNode = new Random(System.currentTimeMillis());
            this.min = 0;
            this.max = 0;
            this.cumul = 0;
            this.iteration = 1;
            rateFailure = new Boolean[100];
            initRateFailure(rate);
        }


        public void run() {

            logger.info(CMMApi.LOG_PREFIX + 
              " Starting Executor " + (index + 1));

            isWorking = true;
            while (isWorking) {
                long startTime = System.currentTimeMillis();
                boolean createFailure = isFailure();
                boolean res = generateConfigUpdate(createFailure);
                long endTime = System.currentTimeMillis();
                recordStats(endTime - startTime);
                if (!res) {
                    logger.info(CMMApi.LOG_PREFIX +
                      " Stoping Executor " + (index + 1) + 
                        " because of failure, iteration = " + iteration);
                    isWorking = false;
                    break;
                }
                if (waitInBetweenConfig > 0) {
                    try {
                        Thread.sleep(waitInBetweenConfig);
                    } catch (InterruptedException ignored) {
                    }
                }
                iteration++;
            }
            logger.info(CMMApi.LOG_PREFIX + 
              " Stoping Executor " + (index + 1));
        }

        private void initRateFailure(int rate) {

            int distance = 0;

            if (rate <= 0) {
                distance = 100;
            } else if (rate >= 100) { 
                distance = 0;
            } else {
                distance = 100 / rate;
            }

            for (int i = 0; i < 100; i++) {
                switch (distance) {
                case 100:
                    rateFailure[i] = Boolean.FALSE;
                    break;
                case 0:
                    rateFailure[i] = Boolean.TRUE;
                    break;
                default:
                    if ((i % distance) == 0) {
                        rateFailure[i] = Boolean.TRUE;
                    } else {
                        rateFailure[i] = Boolean.FALSE;
                    }
                    break;
                }
            }
        }

        private boolean isFailure() {
            return (rateFailure[iteration % 100]).booleanValue();
        }

        private void recordStats(long time) {
            if (time < min) {
                min = time;
            }
            if (time > max) {
                max = time;
            }
            cumul = cumul + time;
        }

        public String getFormattedStatus() {
            StringBuffer buf = new StringBuffer();
            buf.append("Executor " + (index + 1) + ":");
            buf.append(" min = " + min);
            buf.append(", max = " + max);
            buf.append(", average = " + 
              (double) ((double) cumul / (double) iteration));
            return buf.toString();
        }

        private int getRandomNode() {
            int res;
            do {
                res = NODE_BASE_ID + genFailNode.nextInt(numNodes) + 1;
            } while (res == masterNode);
            return res;
        }
        
        private Map createMap(boolean createFailure) {
            
            int valueNodeFailure = 0;
            int valueTypeFailure = FAILURE_NONE;
            if (createFailure) {
                valueNodeFailure = getRandomNode();
                
                valueTypeFailure = 
                  (lastFailure  % FAILURE_SET_STATUS_COMMIT_FALSE) + 1;
                lastFailure = valueTypeFailure;
            }
            
            Map map = new HashMap();
            map.put(PROP_CONFIG_ITERATION,
              Integer.toString(iteration));
            map.put(PROP_CONFIG_NODE_FAILURE,
              Integer.toString(valueNodeFailure));
            map.put(PROP_CONFIG_TYPE_FAILURE,
              Integer.toString(valueTypeFailure));
            return map;
        }

        private boolean generateConfigUpdate(boolean createFailure) {
            
            Map map = createMap(createFailure);
            long version = -1;
            if (storeConfig) {
                version = index + iteration * MAX_EXECUTORS;
                try {
                    createFile(map, version);
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE,
                      CMMApi.LOG_PREFIX + " Failed to create config/update file ["+
                      ioe.getMessage()+"]", ioe);
                    return false;
                }
            }

            try {

                logger.info(CMMApi.LOG_PREFIX + 
                  "!!!!!!!!!!  Start Config/update version = " + 
                  ((version == -1) ? "unknown (updateFile)" : version) +
                  "  !!!!!!!!!!");

                if (storeConfig) {
                    CMM.getAPI().storeConfig(CMMApi.UPDATE_STRESS_TEST, 
                      version, "0000000000000000");
                } else {
                    CMM.getAPI().updateConfig(CMMApi.UPDATE_STRESS_TEST, map);
                }
            } catch (CMMException cmme) {
                logger.log(Level.SEVERE,
                  CMMApi.LOG_PREFIX + 
                  "!!!!!!!!!!   Failed to generate config update ["+
                  cmme.getMessage()+"]   !!!!!!!!!!", cmme);
                return false;
            } catch (ServerConfigException sce) {
                logger.log(Level.SEVERE,
                  CMMApi.LOG_PREFIX + 
                  "!!!!!!!!!!   Failed to generate config update ["+
                  sce.getMessage()+"]   !!!!!!!!!", sce);
                return false; 
            } catch (Throwable thw) {
                logger.log(Level.SEVERE,
                  CMMApi.LOG_PREFIX + 
                  "!!!!!!!!!!   Failed to generate config update    !!!!!!!!!" +
                  thw);
                return false; 
                
            }
            logger.info(CMMApi.LOG_PREFIX + 
                "!!!!!!!!!!  Config/update version = " + 
              version + " successful   !!!!!!!!!!");
            return true;
        }
    }
}