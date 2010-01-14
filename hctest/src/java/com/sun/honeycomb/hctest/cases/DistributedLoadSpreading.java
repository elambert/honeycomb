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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.honeycomb.hctest.HoneycombRemoteSuite;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.util.*;


public class DistributedLoadSpreading extends HoneycombRemoteSuite {

    private double STORE_DURATION = 8; //hours
    private int STORES_PER_CLIENT = 100;
    private int SLEEP_WAKEUP_TIMEOUT = 60000; // milliseconds
    private int MAX_ONLINE_ITERATIONS = 45; // iterations
    private String LOG_LOCATION = "/var/adm/messages";
    private int NODE_NUM_OFFSET = 101;
    private int NODE_PORT_OFFSET = 2001;
    private double TOLERANCE = 0.2; // percentage
    private boolean STORE_ONLY = true;
    
    protected double storeDuration = STORE_DURATION;
    protected DistributedAlternatingStore das = null; 
    protected String cluster = null;
    protected CLI cli = null;
    protected int masterCell = 0;
    protected BufferedReader stdout = null;
    protected String adminVIP = null;
    protected String dataVIP = null;
    protected String spIP = null;
    protected int nodes = 0;
    protected int sentStores = 0;
    protected int numClients = 0;
    protected int[] storeCounts = null;
    protected String[] startDates = null;
    protected TestCase self;    // to file pass/fail test results
    
    
    public DistributedLoadSpreading() {
        super();
    }
   
    /* 
     * Tests must be run on a cluster that is already online.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        
        self = createTestCase("LoadSpreading",
                "Simple distributed load spreading test with load.",
                false);
        self.addTag("loadSpreading");
        
        if (self.excludeCase()) // should I run?
            return;
        
        dataVIP = testBed.dataVIPaddr;
        adminVIP = testBed.adminVIPaddr;
        spIP = testBed.spIPaddr;
        numClients = testBed.clientCount();
        cluster = 
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        String time = TestRunner.getProperty("time");
        if (time != null) {
            storeDuration = Double.parseDouble(time);
        }
        initAll();
        nodes = cli.hwstat(masterCell).getNodes().size();
        
        das = new DistributedAlternatingStore(STORES_PER_CLIENT, STORE_ONLY);
        das.setUp();
        
        rollLogs();
        storeStartTimes();
        super.setUp(); 
    }
    
    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        if (excludeCase()) return;
        das.tearDown();
        super.tearDown();
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    //////////////////////////////////  TESTS /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public boolean test_LoadSpreading() throws Throwable {
        if (excludeCase()) return false;
        waitForClusterToStart();
        Log.INFO("Running load for " + storeDuration + " hours...");
        double endTime =
            System.currentTimeMillis() + storeDuration * 60 * 60 * 1000;
 
        while (System.currentTimeMillis() < endTime) {
            boolean ok = das.testStressMultipleStoreRetrieve();
            sentStores += (STORES_PER_CLIENT * numClients);
            if (!ok) {
                Log.WARN("Unsuccessful store detected");
            }
        }
        countResults();
        boolean pass = analyzeResults();
        return pass;
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////////////  HELPERS ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    
    
    protected void countResults() throws Throwable {
        Log.INFO("Grep'ing logs and tallying stores per node...");
        storeCounts = new int[nodes];
        
        String pat = "\\s+(\\d+)";
        Pattern pattern = Pattern.compile(pat, Pattern.MULTILINE);
        
        // For each node
        for (int i = 0; i < nodes; i++) {
            int nodeNum = i + NODE_NUM_OFFSET;
            int port = i + NODE_PORT_OFFSET;
            Log.INFO("NODE " + nodeNum);
            
            runSystemCommand(
                "ssh -p " + port + " -o StrictHostKeyChecking=no root@"+
                adminVIP + " \"grep 'MEAS store' " + LOG_LOCATION + 
                " | wc -l\"");
            String wc = stdout.readLine();
            Matcher matcher = pattern.matcher(wc);
            // get the amount of stores in the current log
            if (matcher.find()) {
                int matches = Integer.parseInt(matcher.group(1));
                storeCounts[i] += matches;
                Log.INFO(" - " + LOG_LOCATION + ": " + matches + " stores");
            }
            
            // get the stores in the rolled logs
            int n = 0;
            boolean rezip = false;
            runSystemCommand(
                    "ssh -p " + port + " -o StrictHostKeyChecking=no root@"+
                    adminVIP + " \"head -n 1 " + LOG_LOCATION + "." + n +
                    " | cut -d \' \' -f 1-3\"");
            String line1 = stdout.readLine();
            while (line1.compareTo(startDates[i]) > 0) { //(log > start
                // get the amount of stores in the current log
                runSystemCommand(
                        "ssh -p "+port+" -o StrictHostKeyChecking=no root@"+
                        adminVIP+" \"grep 'MEAS store' "+LOG_LOCATION+"."+n+
                        " | wc -l\"");
                wc = stdout.readLine();
                matcher = pattern.matcher(wc);
                if (matcher.find()) {
                    int matches = Integer.parseInt(matcher.group(1));
                    storeCounts[i] += matches;
                    Log.INFO(" - " + LOG_LOCATION + "." + n + ": " + matches +
                            " stores");
                }
                
                // rezip if this was zipped before us
                if (rezip) {
                    runSystemCommand(
                        "ssh -p " + port+" -o StrictHostKeyChecking=no root@"+
                        adminVIP + " \"gzip -f " + LOG_LOCATION + "." +n+"\"");
           
                }
                n++;
                
                // if the next file is gzipped, unzip it
                boolean ok = runSystemCommand(
                        "ssh -p " + port+" -o StrictHostKeyChecking=no root@"+
                        adminVIP + " \"ls " + LOG_LOCATION + "." + n+"\""); 
                if (!ok) {
                    rezip = true;
                    runSystemCommand(
                        "ssh -p " + port+" -o StrictHostKeyChecking=no root@"+
                        adminVIP + " \"gzip -d "+LOG_LOCATION+"."+n+".gz\"");
                }
                runSystemCommand(
                        "ssh -p " + port+" -o StrictHostKeyChecking=no root@"+
                        adminVIP + " \"head -n 1 " + LOG_LOCATION + "." + n +
                        " | cut -d \' \' -f 1-3" + "\"");
                line1 = stdout.readLine();
            }
            
            Log.INFO(" - hcb" + nodeNum + " stores: " + storeCounts[i]);
            
            // rezip if the last file was zipped
            if (rezip) {
                runSystemCommand(
                        "ssh -p " + port+" -o StrictHostKeyChecking=no root@"+
                        adminVIP + " \"gzip " + LOG_LOCATION + "." + n + "\"");
            }
        }   
    }
    
    protected boolean analyzeResults() {
        boolean pass = true;
        double totalStores = 0;
        for (int i = 0; i < nodes; i++) {
            totalStores += storeCounts[i];
        }
        Log.INFO("Expected Total Stores: " + (sentStores*2)); //why?
        Log.INFO("Received Total Stores: " + (int) totalStores);
        if ((sentStores*2) != totalStores) {
            Log.WARN("Lost stores detected");
        }
        
        DecimalFormat df = new DecimalFormat( "##.##" );
        double expected = (1 / (double) nodes) * 100;
        Log.INFO("Expected load level: " + df.format(expected) + "%");
        double lowBound = expected - (expected * TOLERANCE);
        double highBound = expected + (expected * TOLERANCE);
        Log.INFO("Tolerable load range: " + df.format(lowBound) + "-" + 
                df.format(highBound) + "%");
        for (int i = 0; i < nodes; i++) {
            int node = i + NODE_NUM_OFFSET;
            if (nodes == 0) {
                return false;
            }
            double p = ((long) storeCounts[i]) / totalStores * 100;
            Log.INFO("NODE HCB" + node + ": " + df.format(p) + "%");
            if (p < lowBound) {
                pass = false;
                Log.ERROR(" - Load percentage is too low");
            }
            if (p > highBound) {
                pass = false;
                Log.ERROR(" - Load percentage is too high");
            }
        }
        return pass;
    }
    // Get the log line so we know how far back to go in the logs
    protected void rollLogs() throws Throwable {
        for (int i = 0; i < nodes; i++) {
            int node = i + NODE_PORT_OFFSET;
            runSystemCommand(
                "ssh -p " + node + " -o StrictHostKeyChecking=no root@"+
                adminVIP + " logadm " + LOG_LOCATION + " -s 10b");
        }
    }
    
    protected boolean runSystemCommand(String cmd) throws Throwable {
        Log.INFO("Running: " + cmd);
        String s = null;
        String[] command = {"sh", "-c", cmd};
        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
        BufferedReader stdError = new BufferedReader(new 
                InputStreamReader(p.getErrorStream()));
        while ((s = stdError.readLine()) != null)
            Log.ERROR(s);
        stdout =  new BufferedReader(new
                InputStreamReader(p.getInputStream()));
        if (p.exitValue() != 0) {
            Log.ERROR("Command exited with value: " + p.exitValue());
            return false;
        }
        return true;
    }
    
    // Store each nodes date (in case ntp is not working correctly)
    protected void storeStartTimes() throws Throwable {
        startDates = new String[nodes];
        for (int i = 0; i < nodes; i++) {
            int node = i + NODE_PORT_OFFSET;
            runSystemCommand(
                "ssh -p " + node + " -o StrictHostKeyChecking=no root@"+
                adminVIP + " date \\'+%b %d %H:%M:%S\\'");
            startDates[i] = stdout.readLine();

        }
    }
    
    
    protected void initAll() throws Throwable {
        Log.INFO("initAll() called");
        initAdmincli();
        getMasterCell();        
    }
    
    protected void initAdmincli() {
        cli = new CLI(cluster + "-admin");
    }
    
    protected void getMasterCell() throws Throwable {
        stdout = cli.runCommand("hiveadm");
        String regex = "Cell (\\d+): adminVIP = " + adminVIP;
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        String line;
        while ((line = stdout.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                masterCell = new Integer(matcher.group(1)).intValue();
                Log.INFO("Master Cell: " + masterCell);
                return;
            }
        } 
    }
    
    protected void waitForClusterToStart() throws HoneycombTestException {
        // Wait for cli to be accessible and sysstat returns Online

        Log.INFO("Waiting for cluster to come online...");
        boolean ready = false;
        int i = MAX_ONLINE_ITERATIONS;
        while (i > 0 && !ready) {
            try {
                i--;
                ArrayList lines = readSysstat();
                if (lines.toString().contains("Data services Online"))
                    ready = true;
                if (!ready)
                    pause(SLEEP_WAKEUP_TIMEOUT);
            } catch (Throwable e) {
                pause(SLEEP_WAKEUP_TIMEOUT);
            }
        }
        if (i == 0) {
            Log.WARN("Cluster is not Online and (HA)FaultTolerant");
        }
    }
    
    protected ArrayList readSysstat() throws Throwable {
        return readSysstat (false);
    }
    
    
    protected ArrayList readSysstat(boolean extended) throws Throwable {
        ArrayList result = new ArrayList();
        String line = null;
        String cmd = null;
        if (extended)
            cmd = "sysstat -r";
        else 
            cmd = "sysstat";
        stdout = cli.runCommand(cmd + " -c " + masterCell);
        while ((line = stdout.readLine()) != null)
            result.add(line);
        return result;
    }
    
    
    protected void pause(long milliseconds) {
        long time = milliseconds / 1000;
        Log.INFO("Sleeping for " + time + " seconds...");
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) { /* Ignore */ }
    }
    
}
