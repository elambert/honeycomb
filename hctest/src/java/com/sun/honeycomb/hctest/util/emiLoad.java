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



package com.sun.honeycomb.hctest.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

public class emiLoad extends HoneycombRemoteSuite {

    public static int OP_STORE = 1;
    public static int OP_RETRIEVE = 2;
    public static int OP_QUERY = 4;
    public static int OP_DELETE = 8;

    protected String START_LOAD = "start-master-stress.sh";
    protected String STOP_LOAD = "kill-stress-test.sh";
    protected String ANALYZE = "analyze-perf.sh";
    protected TestCase self;
    private String ENV_LOC = "/opt/test/bin/load/emi-load/";
    private String STRESS_LOG_LOCATION = "/mnt/test/emi-stresslogs/";
    private String ANALYZE_PERF_OK = " (\\d+) OK";
    private String ANALYZE_PERF_ERR = " (\\d+) ERR";
    private String cluster = null;
    private String[] clients = null;
    private String dataVIP = null;
    private int ops = OP_STORE|OP_RETRIEVE|OP_QUERY;

    public emiLoad(String cluster, String dataVIP, String[] clients) {
        super();
        this.cluster = cluster; 
        this.dataVIP = dataVIP;
        this.clients = clients;
        Log.INFO("cluster = " + cluster);
        Log.INFO("dataVIP = " + dataVIP);
        Log.INFO("clients = " + clients[0]);
    }
   
    public emiLoad (String cluster, String dataVIP, String[] clients, int ops) {
        this(cluster, dataVIP, clients);
        ops = ops;
    }

    public void setUp() throws Throwable {
        super.setUp();
        self = createTestCase("emiLoad","emi stress test", false);
        self.addTag("load");      
        if (self.excludeCase()) // should I run?
            return;
    }

    public void tearDown() throws Throwable {
        if (self.excludeCase()) // should I run?
            return;
    }
     
    public void startLoad() {
		//Edit ENV File
        StringBuffer cmd = new StringBuffer ("sed s/dev[0-9X][0-9X][0-9X]/")
            .append(cluster).append("/g ").append(ENV_LOC).append("ENV")
            .append("|sed s/STARTSTORES=[0-1]/STARTSTORES=");

        if ((ops & OP_STORE) == 1)
            cmd.append ("1");
        else
            cmd.append ("0");

        cmd.append ("/g | sed s/STARTRETRIEVES=[0-1]/STARTRETRIEVES=");

        if ((ops & OP_RETRIEVE) == 1) 
            cmd.append ("1");
        else
            cmd.append ("0");

        cmd.append ("/g | sed s/STARTQUERIES=[0-1]/STARTQUERIES=");

        if ((ops & OP_QUERY) == 1)
            cmd.append ("1");
        else
            cmd.append ("0");

        cmd.append ("/g | sed s/STARTDELETES=[0-1]/STARTDELETES=");

        if ((ops & OP_DELETE) == 1)
            cmd.append ("1");
        else
            cmd.append ("0");

        cmd.append("/g > /tmp/ENV.").append(cluster);
		try {
			runSystemCommand(cmd.toString());
		} catch (Throwable e) {
			Log.stackTrace(e);
			Log.ERROR("Could not setup ENV file");
			return;
		}
		
		
		//SCP into each client
		for (int i = 0; i < clients.length; i++) {
			try {
				// Delete the old stress logs
				runSystemCommand("ssh -o StrictHostKeyChecking=no root@" +
						clients[i] + " rm -f " + STRESS_LOG_LOCATION + "*");
			} catch (Throwable e1) {
				Log.WARN("Could not erase old logs on " + clients[i]);
				Log.WARN("May produce incorrect analysis at the end of the test");
			}
			try {
				runSystemCommand("scp -o StrictHostKeyChecking=no /tmp/ENV." +
						cluster + " root@" + clients[i] + ":" + ENV_LOC + 
						"ENV" + " 2>&1 >> /dev/null");
				try {
					//Start master stress on each
					runSystemCommand("ssh -o StrictHostKeyChecking=no root@" +
							clients[i] + " " + ENV_LOC + START_LOAD +
							" 2>&1 >> /dev/null");
					Log.INFO("Load started on " + clients[i]);
				} catch (Throwable e) {
					Log.stackTrace(e);
					Log.WARN(
						"Could not start-master-stress on "+clients[i]);
					Log.WARN("Not running load from " + clients[i]);
				}
			} catch (Throwable e) {
				Log.stackTrace(e);
				Log.WARN("Could not setup ENV file on " + clients[i]);
				Log.WARN("Not running load from " + clients[i]);
			}		
		}
    }
    
    public void stopLoad() {
    	//Edit the stop script
		try {
			runSystemCommand("sed s/java/\"-f " + dataVIP + "\"/ " + 
					ENV_LOC + STOP_LOAD + " > /tmp/" + STOP_LOAD);
		} catch (Throwable e) {
			Log.stackTrace(e);
			Log.ERROR("Could not edit kill-stress-test file...");
			Log.ERROR("  File will run as is, killing all java,");
			Log.ERROR("  including the rmi server. Future tests will fail");
		}
		
		//SCP into each client
		for (int i = 0; i < clients.length; i++) {
			try {
				runSystemCommand(
					"scp -o StrictHostKeyChecking=no /tmp/" + STOP_LOAD +
					" root@" + clients[i] + ":" + ENV_LOC + STOP_LOAD +
					" 2>&1 >> /dev/null");
			} catch (Throwable e) {
				Log.stackTrace(e);
				Log.WARN("Could not send " + STOP_LOAD + 
						" file on " + clients[i]);
				Log.ERROR("Killing all java on " + clients[i]);
			}
			
			try {
				//Stop master stress on each
				runSystemCommand("ssh -o StrictHostKeyChecking=no root@"
						+ clients[i] + " " + ENV_LOC + STOP_LOAD +
						" 2>&1 >> /dev/null");
				Log.INFO("Load stopped on " + clients[i]);
			} catch (Throwable e) {
				Log.stackTrace(e);
				Log.ERROR(
					"Could not kill-stress-test on "+clients[i]);
				Log.ERROR("Still running load from " + clients[i]);
			}
		}
    }
    
    public void analyzeLoad() {
    	String cls = join(clients, " ");
    	try {
    	    runSystemCommand("cp -f /tmp/ENV." + cluster + " " + 
    			ENV_LOC + "ENV" + " 2>&1 >> /dev/null");
      	    runSystemCommand(
			ENV_LOC + ANALYZE + " -m "+cls+" > /tmp/"+ANALYZE+".out");
       	    FileReader input = new FileReader("/tmp/"+ANALYZE+".out");
      	    BufferedReader bufRead = new BufferedReader(input);
            Matcher matcher = null;
	    long okCount = 0;
	    long errCount = 0;
    	    Pattern okPattern =
    			Pattern.compile(ANALYZE_PERF_OK, Pattern.MULTILINE);
    	    Pattern errPattern =
    	    Pattern.compile(ANALYZE_PERF_ERR, Pattern.MULTILINE);
            String line = null;
            while ((line = bufRead.readLine()) != null) {
 	        Log.INFO("---> " + line);
	    	matcher = okPattern.matcher(line);
	        if (matcher.find()) {
	       	    okCount += Integer.parseInt(matcher.group(1));
	        }
	        matcher = errPattern.matcher(line);
	        if (matcher.find()) {
	       	        errCount += Integer.parseInt(matcher.group(1));
	        }
   	    }
			
	    long total = okCount + errCount;
	    Log.INFO("Total ops: " + total);
	    Log.INFO(" - OK: " + okCount);
	    Log.INFO(" - ERR: " + errCount);
	} catch (Throwable e) {
    	    Log.stackTrace(e);
	    Log.ERROR("Unable to analyze peformance data");
            Log.ERROR("message -> " + e.getMessage());
	}
    }

    private boolean runSystemCommand(String cmd) throws Throwable {
        Log.INFO("Running: " + cmd);
        String s = null;
        String[] command = {"sh", "-c", cmd};
        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(p.getErrorStream()));
        while ((s = stdError.readLine()) != null)
            Log.ERROR(s);
        BufferedReader stdout =  new BufferedReader(new
                InputStreamReader(p.getInputStream()));
        if (p.exitValue() != 0) {
                Log.ERROR("Command exited with value: " + p.exitValue());
                return false;
        }
        return true;
    }
    private String join(String[] who, String delimiter) {
    	String joined = "";
    	for (int i = 0; i < (who.length - 1); i++) {
    		joined += who[i];
    		joined += delimiter;
    	}
    	// Last one
    	joined += who[who.length - 1];
    	return joined;
    }
}
