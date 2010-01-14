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



package com.sun.honeycomb.hctest.cases;

import java.util.Random;

import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class SloshingWithFailures extends BasicSloshing {

    protected static String NODE_FAULT = "node";
    protected static String DISK_FAULT = "disk";
    
    protected static String MAX_FAULTS = "max";
    protected static String MIN_FAULTS = "min";
    
    protected static long FAULT_WAIT = 30000; // 30 seconds
    protected String fault_type = null;
    protected String fault_mode = null;
    
    protected SloshThread slosher = new SloshThread();
    protected FaultThread faulter = new FaultThread();
    
    public SloshingWithFailures() {
        super();
    }
    
    public void setUp() throws Throwable {
        super.setUp();
    
        self = createTestCase("SloshingWithFailures","sloshing,failures-during-sloshing");
        self.addTag("sloshing");
        self.addTag("faults");     
        
        if (self.excludeCase()) // should I run?
            return;

        fault_type = getProperty(HoneycombTestConstants.PROPERTY_FAULTTYPE);
        
        if (fault_type == null){
            Log.INFO("faulttype no specified, defaulting to node faults.");
            fault_type = "node";
        } else 
			fault_type.toLowerCase();
        
        if (!fault_type.equals(NODE_FAULT) && !fault_type.equals(DISK_FAULT)) {
            throw new HoneycombTestException("Only " + NODE_FAULT + " and " 
                                             + DISK_FAULT + 
                                             " are supported for property "+ 
                                             HoneycombTestConstants.PROPERTY_FAULTTYPE);
        }
        
        String s  = getProperty(HoneycombTestConstants.PROPERTY_FAULTMODE);
        
        if (s != null) {
			s.toLowerCase();
            if (!s.equals(MAX_FAULTS) && !s.equals(MIN_FAULTS)) {
                throw new HoneycombTestException("Only " + MAX_FAULTS + " and " 
                        + MIN_FAULTS + 
                        " are supported for property " + 
                        HoneycombTestConstants.PROPERTY_FAULTMODE);
            }
            fault_mode = s;
        } else { 
            // Deafult to minimum 
            fault_mode = MIN_FAULTS;
        }
    }
    
    public void tearDown() throws Throwable {
        super.tearDown();
        
        if (!finished) {
            faulter.undoFaults();
        }
    }
    
    public void testSloshing() throws HoneycombTestException {    
        if (self.excludeCase()) // should I run?
            return;
        
        cm.setQuorum(true);
        cm.initClusterState();
        
        verifyPreReqs();
        
        initCluster();   
        
        slosher.start();
        Log.INFO("Waiting for " + FAULT_WAIT/1000  +"s before starting faults.");
        pause(FAULT_WAIT);
        
        faulter.start();
        
        try {
            slosher.join();
        } catch (InterruptedException e) {
            throw new HoneycombTestException("Error waiting for SloshThread to finish.",e);
        } 
        
        try {
            faulter.join();
        } catch (InterruptedException e) {
            throw new HoneycombTestException("Error waiting for FaultThread to finish.",e);
        }
                
        verifyCluster();
        
        faulter.undoFaults();
                
        self.postResult(true, "Sloshing completed successfully.");
        finished = true;
    }
    
    class FaultThread extends Thread {
        private Random rand = null;
        private CLIState clistate = null;
        private int randNode = -1;
        
        private int randNodeID(){
			// TODO: make this function return the actual node 
            return rand.nextInt()%8 + 1;
        }
        
        private synchronized void initFaultThread() {
            // We must initialize the following only if the FaultThread
            // will really be used, since CLIstate talks to the live cluster.
            rand = new Random(System.currentTimeMillis());
            clistate = CLIState.getInstance();
        }

        public void run() {
            
            initFaultThread();

            randNode = randNodeID();
            
            try {                    
                if (fault_type.equals(DISK_FAULT)) {
                    // DO disk faults...
                    if (fault_mode.equals(MAX_FAULTS)) {
                        Log.INFO("Max disk faults; on nodes " + randNode + " and " + randNode+1);                        
                        clistate.disableDisk(randNode, 0);
                        clistate.disableDisk(randNode, 1);
                        clistate.disableDisk(randNode, 2);
                        clistate.disableDisk(randNode, 3);
                        randNode = randNode++%8;
                        clistate.disableDisk(randNode, 0);
                        clistate.disableDisk(randNode, 1);
                        clistate.disableDisk(randNode, 2);
                        clistate.disableDisk(randNode, 3);                        
                    } else {
                        clistate.disableDisk(randNode, 0);
                        randNode = randNode++%16;
                        clistate.disableDisk(randNode, 3);                        
                    }
                } else {
                    // Default to node faults...
                    if (fault_mode.equals(MAX_FAULTS)) {
                        Log.INFO("Max node faults on nodes " + randNode + " and " + randNode+1);
                        ClusterNode node = cm.getNode(randNode);
                        node.setupDevMode();
                        node.pkillJava();
                        
                        node = cm.getNode((randNode+1)%16);
                        node.setupDevMode();
                        node.pkillJava();
                    } else {
                        Log.INFO("Min node faults on node " + randNode);
                        ClusterNode node = cm.getNode(randNode);
                        node.setupDevMode();
                        node.pkillJava();
                    }             
                }
            } catch (HoneycombTestException e) {
                Log.ERROR("Error creating faults..." + Log.stackTrace(e));
            }
        }
        
        public void undoFaults() {
            try {
                if (randNode != -1) {
                    // Clean up disabled disks and nodes.
                    if (fault_type.equals(DISK_FAULT)) {
                        // DO disk faults...
                        if (fault_mode.equals(MAX_FAULTS)) {
                            Log.INFO("Bringing disk faults back online on nodes " + randNode + " and " + randNode+1);                        
                            clistate.enableDisk(randNode, 0);
                            clistate.enableDisk(randNode, 1);
                            clistate.enableDisk(randNode, 2);
                            clistate.enableDisk(randNode, 3);
                            int randNode2 = randNode++%8;
                            clistate.enableDisk(randNode2, 0);
                            clistate.enableDisk(randNode2, 1);
                            clistate.enableDisk(randNode2, 2);
                            clistate.enableDisk(randNode2, 3);                        
                        } else {
                            int randNode2 = randNode++%16;
                            clistate.enableDisk(randNode2, 0);
                            clistate.enableDisk(randNode2, 3);                        
                        }
                    } else {
                        // Default to node faults...
                        if (fault_mode.equals(MAX_FAULTS)) {
                            Log.INFO("Brining nodes " + randNode + " and " + (randNode+1) + " back online.");
                            ClusterNode node = cm.getNode(randNode);
                            node.removeDevMode();
                            node.runCmd("/opt/honeycomb/etc/init.d/honeycomb start");
                            
                            node = cm.getNode((randNode+1)%16);
                            node.removeDevMode();
                            node.runCmd("/opt/honeycomb/etc/init.d/honeycomb start");
                        } else {
                            Log.INFO("Brining node " + randNode + " back online.");
                  	        ClusterNode node = cm.getNode(randNode);
                            node.removeDevMode();
                            node.runCmd("/opt/honeycomb/etc/init.d/honeycomb start");
                        }             
                    }
                }
            } catch (HoneycombTestException e) {
                Log.ERROR("Error creating faults..." + Log.stackTrace(e));
            }
        }
    }
}
