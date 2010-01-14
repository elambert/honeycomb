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

import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.emiLoad;

public class SloshingWithLoad extends BasicSloshing {

    private int numClients = 0;
    protected int STORES_PER_CLIENT = 50;
    private String[] clients;
    private String dataVIP;
    private emiLoad emi;
 
    public SloshingWithLoad() {
        super();
    }
    
    public void setUp() throws Throwable {
        super.setUp();
        self = createTestCase("SloshingWithLoad","sloshing,load-during-sloshing");
        self.addTag("sloshing");
        self.addTag("load");
        if (self.excludeCase()) // should I run?
            return;

        if (TestRunner.getProperty("clients") == null) {
            String hostname = com.sun.honeycomb.test.util.Util.localHostName();
            TestRunner.setProperty("clients",hostname);
        }
        numClients = testBed.clientCount(); 
        dataVIP = testBed.dataVIP;
	String c =
	    TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLIENTS);
	clients = c.split(",");

        // initialize emi stress test
        emi = new emiLoad(cluster, dataVIP, clients);
        emi.setUp();
    }

    public void tearDown() throws Throwable {
        if (self.excludeCase()) // should I run?
            return;
        emi.tearDown();
    }   
 
    private boolean sloshingDone = false;
    public void testSloshing() throws HoneycombTestException {   
        if (self.excludeCase()) // should I run?
            return;
        cm.setQuorum(true);
        cm.initClusterState();
        
        verifyPreReqs();
        
        initCluster();   
      
        // start load against the cluster
        emi.startLoad();

        SloshThread slosher = new SloshThread();
        slosher.start();
        
        try {
            slosher.join();
        } catch (InterruptedException e) {
            throw new HoneycombTestException("Error waiting for SloshThread to finish.",e);
        } finally {
            sloshingDone = true;
        }
       
        emi.stopLoad();
        emi.analyzeLoad();

        verifyCluster();
                
        self.postResult(true, "Sloshing completed successfully.");
        finished = true;
    }
}
