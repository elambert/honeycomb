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

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;


/**
 * Validate that a gratuitous arp is being sent for the data VIP
 */
public class SendArp extends HoneycombLocalSuite {
    protected ClusterMembership cm = null;

    /*
     * Capture 60 arp packets from the external network adapter. Note
     * that this means we need to collect at least one gratuitous arp
     * reply for the data vip within 60 packets. If the lab network
     * gets really busy then we need to up this number. The ideal way
     * to do this would be to run snoop for longer than the sendarp
     * interval to make sure we get one gratuitous arp.
     */
    private static final int WAIT_TIME_FOR_ARP = 2; // in mins 
    private static final String SNOOP_FILE = "/tmp/snoop.out";

    private static String snoopArgs;
    private String[] tags = {Tag.REGRESSION,HoneycombTag.SWITCH};

    public SendArp() {
        super();
    }

    public String help() {
        return("\tValidate that  a gratuitous arp is being sent for the data VIP\n");
    }

    public void setUp() throws Throwable {
        if (!Run.isTagSetActive(tags)) {
       	    Log.WARN("Tag set is not active skipping set up.");
            return;
	}
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();        
        cm = new ClusterMembership(-1, testBed.adminVIP);
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    public void testSendArp() throws HoneycombTestException {
        String snoop = null;
        int error = 0;
        TestCase self = createTestCase("SendArp", "data VIP gratuitous arp");
        
        self.addTag(tags);

        if (self.excludeCase()) return;

        String dataVIP = testBed.dataVIPaddr;
        Log.INFO("Data VIP: " + dataVIP);

        // Create the snoop output string to match
        String sendArp = "ARP R " + dataVIP; 
        ClusterNode clusterNode = cm.getNode(1);
 
        // get Active Switch ID
        int switchID = 0;
        try {
            switchID = clusterNode.getActiveSwitchID();
        } catch(HoneycombTestException e) {
            throw new HoneycombTestException("Unable to get Active Switch id"); 
        }
        
        if(switchID == 1) {
            snoopArgs = "-r -d bge1000 -o " + SNOOP_FILE + " arp";
        } else if(switchID == 2) {
            snoopArgs = "-r -d nge1000 -o " + SNOOP_FILE + " arp";
        } else {
            throw new HoneycombTestException("Unable to get Active Switch id");     
        } 
        Log.INFO("Running: snoop " + snoopArgs);

        try {
            // start snooping for arp packets 
            Thread snoopThr = new Thread(new Snoop(clusterNode));
            snoopThr.start();
 
            // Assuming that 2 mins is sufficient and that spreader 
            // service will send a gratituous arp request by then 
            Log.INFO("wait for  " + WAIT_TIME_FOR_ARP + " mins for spreader "+ 
                     "service to send an arp request"); 
            Thread.sleep(1000 * 60 * WAIT_TIME_FOR_ARP);

            if (snoopThr.isAlive()) {
                snoopThr.interrupt();
            }

            Log.INFO("Parsing snoop output file");
            snoop = clusterNode.getSnoopOutput(SNOOP_FILE);

            clusterNode.pkillSnoop(snoopArgs);
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("Failed to execute: snoop " +
                                             snoopArgs + "\n" + e);
        } catch (InterruptedException e) { }

        Log.INFO("snoop: " + snoop);

        if (!snoop.contains(sendArp)) {
            self.testFailed("Failed to snoop a gratuitous arp for data VIP " +
                            dataVIP + ". Make sure you have version 1.1-8567 " +
                            "or later installed.");
        } else {
            self.testPassed("Found a gratuitous arp for data VIP " + dataVIP);
        }
    }

    private static class Snoop implements Runnable {
        ClusterNode clusterNode;
 
        public Snoop(ClusterNode clusterNode) {
            this.clusterNode = clusterNode;
        }

        public synchronized void run() {
            try {
                clusterNode.snoop(snoopArgs);
            } catch (HoneycombTestException e) {
            }   
        }  
    }  
}
