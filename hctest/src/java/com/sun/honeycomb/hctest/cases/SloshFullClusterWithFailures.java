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
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.emiLoad;
import java.io.BufferedReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SloshFullClusterWithFailures extends SloshingWithFailures {

    private int numClients = 0;
    private String[] clients = null;
    private String dataVIP;
    private emiLoad emi;
    private double fullness = SloshFullCluster.DEFAULT_FULLNESS;

    public SloshFullClusterWithFailures() {
        super();
    }
    
    public void setUp() throws Throwable {
        super.setUp();
    
        self = createTestCase("SloshFullClusterWithFailures","full-sloshing,failures-during-full-sloshing");
        self.addTag("full-sloshing");
        self.addTag("faults");     

        if (self.excludeCase()) // should I run?
            return;

        if (TestRunner.getProperty("clients") == null) {
            String hostname = com.sun.honeycomb.test.util.Util.localHostName();
            TestRunner.setProperty("clients",hostname);
        }
       
        String fullnessParam = TestRunner.getProperty("fullness");
        if (fullnessParam != null) {
            fullness = Double.parseDouble (fullnessParam);
        }

        numClients = testBed.clientCount(); 
        dataVIP = testBed.dataVIP;
	    String c =
	        TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLIENTS);
	    clients = c.split(",");

        // initialize emi stress test
        emi = new emiLoad(cluster, dataVIP, clients, emiLoad.OP_STORE);
        emi.setUp();
        if (self.excludeCase()) // should I run?
            return;
    }
    
    public void tearDown() throws Throwable {
        super.tearDown();

        if (self.excludeCase()) // should I run?
            return;

        emi.tearDown(); 
        if (!finished) {
            faulter.undoFaults();
        }
    }

    public void doLoad() throws HoneycombTestException {
        emi.startLoad();

        // loop here until the cluster is full
        while (clusterNotFull()) {
            sleep (1000*60*15); // 15m
        }

        emi.stopLoad();
        emi.analyzeLoad();
    }

    protected double getClusterFullness() {
        double usage = 0.0;

        try {   
            String line;
            BufferedReader br = cli.runCommand("df");
            line = (String)br.readLine();
            line = (String)br.readLine();
            Log.INFO ("df: " + line);
            Pattern p = Pattern.compile ("\\d+\\.\\d+%$");
            Matcher m = p.matcher (line);
            if (m.find()) {
                Log.INFO ("Cluster fullness: " + m.group());
                String d = m.group().substring (0, m.group().length()-1);
                usage = Double.parseDouble(d);
            }
        } catch (Throwable t) {
            Log.INFO ("Throwable caught reading cluster usage " + t.getMessage()
                + "\n" + Log.stackTrace (t));
        }

        return usage;
    }

    protected boolean clusterNotFull() {
        if (getClusterFullness() <= fullness)
            return true;
        return false;
    }
}
