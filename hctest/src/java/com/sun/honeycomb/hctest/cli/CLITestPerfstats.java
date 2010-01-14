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



package com.sun.honeycomb.hctest.cli;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author jk142663
 */
public class CLITestPerfstats extends HoneycombCLISuite {
    
    private static final String PERFSTATS_HOWLONG_CHAR = "t";
    private static final String PERFSTATS_INTERVAL_CHAR = "i";
    private static final String PERFSTATS_NODE_CHAR = "n";
    
    private static final String METRIC_VALUE = "(([^\\s]+)|(-))";
    private static final String SPACE_SEPERATOR = "\\s*";
    private static final String PERFSTATS_PATTERN = ":" + SPACE_SEPERATOR + METRIC_VALUE 
	+ SPACE_SEPERATOR + METRIC_VALUE
	+ SPACE_SEPERATOR + METRIC_VALUE;
  
    private static final String PERFSTATS_STORE = 
            "Add MD" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_STORE_BOTH = 
            "Store" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_RETRIEVE_ONLY = 
            "Retrieve" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_RETRIEVE_MD = 
            "Retrieve MD" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_DELETE =  
            "Delete" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_QUERY =  
            "Query" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_WEBDAV_PUT = 
            "WebDAV Put" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_WEBDAV_GET = 
            "WebDAV Get" + PERFSTATS_PATTERN;
    private static final String PERFSTATS_LOAD = 
            "Load 1m: [^\\s]+ Load 5m: [^\\s]+ Load 15m: [^\\s]+";
    private static final String PERFSTATS_DISK = 
            "Disk Used: [^\\s]+ (MB|GB|TB) \\s*Disk Total: [^\\s]+ (MB|GB|TB) \\s*Usage: [^\\s]+%";
    
    private static String PERFSTATS_HOWLONG_PARAM = null; // 1 minutes
    private static String PERFSTATS_INTERVAL_PARAM = null;
    private static String PERFSTATS_NODE_PARAM = null;
    
    private static String PERFSTATS_HOWLONG = "1"; // 1 minutes
    private static String PERFSTATS_INTERVAL = "10";
    private static String PERFSTATS_NODE = "NODE-101";
    
    private static final String PERFSTATS_NODE_HEADER = 
            PERFSTATS_NODE + " Performance Statistics:";
    private static final String PERFSTATS_CLUSTER_HEADER = 
            "Cell Performance Statistics:";
    private static final String PERFSTATS_SYS_HEADER = 
            "Hive Performance Statistics:";
    
    /** Creates a new instance of CLITestPerfstats */
    public CLITestPerfstats() {
        super();
    }
    
    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tCommand: perfstats\n");
        sb.append("\t o positive test - output syntax\n");
        sb.append("\t o negative test - invalid option/interval/node\n");
        
        sb.append("\nnodes is a required argument for single " +
                "cell setup (-ctx nodes=<node_num>), default to 8\n");
        sb.append("\nUser can provide how long and interval value as follows: ");
        sb.append("(-ctx howlong=<howlong_in_minutes>), default to 1\n");
        sb.append("(-ctx interval=<interval_in_secs>), default to 10\n");
        
        return sb.toString();
    }
    
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
        
        PERFSTATS_HOWLONG_PARAM = " -" + PERFSTATS_HOWLONG_CHAR + 
                " " + PERFSTATS_HOWLONG;
        PERFSTATS_INTERVAL_PARAM = " -" + PERFSTATS_INTERVAL_CHAR +
                " " + PERFSTATS_INTERVAL;
        PERFSTATS_NODE_PARAM = " -" + PERFSTATS_NODE_CHAR + " " + 
                PERFSTATS_NODE;       
    }
    
    protected boolean isInErrorExclusionList(String errorString) {
	if (errorString == null)
	    return false;
	errorString = errorString.toLowerCase();
	if ((errorString.indexOf(INVALID_CELLID_MESSAGE) != -1) ||
                (errorString.indexOf(INVALID_NODE_MESSAGE) != -1) ||
		(errorString.startsWith("invalid interval") ||
		(errorString.indexOf("interval is too large") != -1))) 
	    return true;
	
	return super.isInErrorExclusionList(errorString);
    }
    
    public void testPerfstatsSyntax() {
        
        String [] lsTestCase = {
            "CLI_perfstats_howlong", 
            "CLI_perfstats_howlong_interval", 
            "CLI_perfstats_howlong_node",
            "CLI_perfstats_howlong_interval_node"
        };
        
        String [] lsCommand = getAllTestCommand();        
        String [] lsArgs = getAllTestArg();
	
	assert(lsTestCase.length == lsCommand.length);
	assert(lsTestCase.length == lsArgs.length);
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i], lsArgs[i]);
        
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!

            if (self.excludeCase()) return;

            boolean isTestPass = false;
            
            try {
		if (isMultiCell()) {     
		    isTestPass = verifyCommandStdout(false, 
                        lsCommand[i],
                        HoneycombCLISuite.MULTICELL_USAGE_MESSAGE);                    
		}
		else {
		    BufferedReader output = runCommandWithoutCellid(
			lsCommand[i]);

                    String stdout = HCUtil.readLines(output);
                    output.close();
		    isTestPass = verifyPerfstats(lsCommand[i], stdout); 
		}
            } catch (Throwable t) {
                isTestPass = false;
		Log.ERROR("testPerfstatsSyntax Exception: " 
		    + Log.stackTrace(t));
            }
                                          
            if (isTestPass)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testPerfstatsSyntaxWithCellid() {
        
        String [] lsTestCase = {
            "CLI_perfstats_howlong_with_cellid", 
            "CLI_perfstats_howlong_interval_with_cellid", 
            "CLI_perfstats_howlong_node_with_cellid",
            "CLI_perfstats_howlong_interval_node_with_cellid"
        };
        
        String [] lsCommand = getAllTestCommand();        
        String [] lsArgs = getAllTestArg();        
        
	
	assert(lsTestCase.length == lsCommand.length);
	assert(lsTestCase.length == lsArgs.length);
	
        ArrayList allCellid = getAllCellid();
        
        for (int i=0; i<lsTestCase.length; i++) {
            TestCase self = createTestCase(lsTestCase[i], lsArgs[i]);
        
            self.addTag(HoneycombTag.CLI);
            // self.addTag(Tag.REGRESSION); // add me!

            if (self.excludeCase()) return;

            boolean isTestPass = false;
            int noOfFailure = 0;
            
            for (int cellCount = 0; cellCount<allCellid.size(); cellCount++) {
                
                int cellid = ((Integer) allCellid.get(cellCount)).intValue();
                setCellid(cellid);
                
                try {
                    BufferedReader output = runCommandWithCellid(lsCommand[i], cellid);
                    String lines = HCUtil.readLines(output);
                    output.close();
                    
                    isTestPass = verifyPerfstats(lsCommand[i], lines);            
                } catch (Throwable t) {
                    Log.ERROR("Error while executing command perfstats: " + t.toString());
                }
                              
                if (!isTestPass)
                    noOfFailure++;
            }
            
            if (noOfFailure == 0)
                self.testPassed();
            else
                self.testFailed();
        }
    }
    
    public void testPerfstatsNegativeTest() {
        
        TestCase self = createTestCase("CLI_perfstats_Negative_Test");

        self.addTag(Tag.NEGATIVE); 
        self.addTag(HoneycombTag.CLI);
        // self.addTag(Tag.REGRESSION); // add me!

        if (self.excludeCase()) return;

        // invalid interval
        String [] invalidInterval = getAllInvalidInteger();
        
        // will test each command with invalid time 
        String [] lsParams = {
            HoneycombCLISuite.PERFSTATS_COMMAND + " -" + PERFSTATS_HOWLONG_CHAR,
            HoneycombCLISuite.PERFSTATS_COMMAND + " -" + PERFSTATS_INTERVAL_CHAR            
        };
            
        // invalid option and invalid node id
        String [] lsInvalidArgs = {
            HoneycombCLISuite.PERFSTATS_COMMAND + " -T " + PERFSTATS_HOWLONG,
            HoneycombCLISuite.PERFSTATS_COMMAND + " -I " + PERFSTATS_INTERVAL,
            HoneycombCLISuite.PERFSTATS_COMMAND + " -N " + PERFSTATS_NODE,
            HoneycombCLISuite.PERFSTATS_COMMAND + " -" + PERFSTATS_NODE_CHAR + " NODE-117"
        };
        
        // will test with invalid cellid 
        String [] lsInvalidMulticellArgs = {
            HoneycombCLISuite.PERFSTATS_COMMAND,
            HoneycombCLISuite.PERFSTATS_COMMAND + PERFSTATS_HOWLONG_PARAM,
            HoneycombCLISuite.PERFSTATS_COMMAND + PERFSTATS_INTERVAL_PARAM,
            HoneycombCLISuite.PERFSTATS_COMMAND + PERFSTATS_NODE_PARAM     
        };
        
        boolean isTestPass = false;
        int noOfFailure = 0;
        
        // test with inavlid interval
        for (int i=0; i<lsParams.length; i++) {
              
            for (int j=0; j<invalidInterval.length; j++) {
            
                String invalidArg = lsParams[i] + " " + invalidInterval[j];
           
                // execute command with invalid interval (without cellid)
                isTestPass = verifyCommandStdout(false, invalidArg, 
                        null);
            
                if (!isTestPass)
                    noOfFailure++;
            
                // execute command with invalid interval (with cellid)
                setCellid();
                isTestPass = verifyCommandStdout(true, invalidArg, 
                        null);
                
                if (!isTestPass)
                    noOfFailure++;
            }
        }
        
        // test with invalid args
        for (int i=0; i<lsInvalidArgs.length; i++) {
            
            if (i == lsInvalidArgs.length-1)
                isTestPass = verifyCommandStdout(false, lsInvalidArgs[i], null);
            else                
                isTestPass = verifyCommandStdout(false, lsInvalidArgs[i],
                    HoneycombCLISuite.PERFSTATS_HELP);
            
            if (!isTestPass)
                noOfFailure++;
            
            // execute command with invalid interval (with cellid)
            setCellid();
            if (i == lsInvalidArgs.length-1)
                isTestPass = verifyCommandStdout(false, lsInvalidArgs[i], null);
            else                
                isTestPass = verifyCommandStdout(true, lsInvalidArgs[i], 
                    HoneycombCLISuite.PERFSTATS_HELP);
                
            if (!isTestPass)
                noOfFailure++;
        }
        
        // execute command with invalid cellid
        int invalidCellId = getInvalidCellid();
    
        for (int i=0; i<lsInvalidMulticellArgs.length; i++) {
            String formattedCommand = formatCommandWithCellid(
                    lsInvalidMulticellArgs[i], invalidCellId);
        
            isTestPass = verifyCommandStdout(false, formattedCommand, null);
        
            if (!isTestPass)
                noOfFailure++;       
        }
        
        if (noOfFailure == 0)
            self.testPassed();
        else
            self.testFailed();
    }
    
    private boolean verifyPerfstats(String command, String lines) {
        int noOfFailure = 0;
        
	String line = null;
        try {
            String honeycombHeader = PERFSTATS_CLUSTER_HEADER;
            if (command.contains("-n"))
                honeycombHeader = PERFSTATS_NODE_HEADER;
            
	    if (lines.indexOf("Performance Statistics for NODE") != -1) {
		// The peformance tests can't run for the node if it's down
		// or still comming up
		Log.WARN(lines);
		return true;
	    }
            String [] lsLine = tokenizeIt(lines, "\n");
              
            Pattern regexpDelete = Pattern.compile(PERFSTATS_DELETE);
            Pattern regexpStore = Pattern.compile(PERFSTATS_STORE);
            Pattern regexpStoreBoth = Pattern.compile(PERFSTATS_STORE_BOTH);
            Pattern regexpRetrieveOnly = Pattern.compile(PERFSTATS_RETRIEVE_ONLY);
            Pattern regexpRetrieveMD = Pattern.compile(PERFSTATS_RETRIEVE_MD);
            Pattern regexpQuery = Pattern.compile(PERFSTATS_QUERY);
            Pattern regexpWebdavPut = Pattern.compile(PERFSTATS_WEBDAV_PUT);
            Pattern regexpWebdavGet = Pattern.compile(PERFSTATS_WEBDAV_GET);
            Pattern regexpLoad = Pattern.compile(PERFSTATS_LOAD);
            Pattern regexpDisk = Pattern.compile(PERFSTATS_DISK);
            
            for (int i=0; i<lsLine.length; i++) {
                
                // Honeycomb cluster/node performance
		while (isEmptyLine(lsLine[i]))
		    i++;         
                if(!isHeaderOk(honeycombHeader, lsLine[i])) 
                    noOfFailure++;
                
		while (isEmptyLine(lsLine[i]))
		    i++;         
		
		// Skip 3 lines for column headers
		i +=3;
                // store, retrieve, query, webdav
                if (!isPatternMatch(regexpStore, lsLine[++i]))
                    noOfFailure++;
                if (!isPatternMatch(regexpStoreBoth, lsLine[++i]))
                    noOfFailure++;
                if (!isPatternMatch(regexpRetrieveOnly, lsLine[++i]))
                    noOfFailure++;  
                if (!isPatternMatch(regexpRetrieveMD, lsLine[++i]))
                    noOfFailure++;  
                if (!isPatternMatch(regexpDelete, lsLine[++i]))
                    noOfFailure++;
                if (!isPatternMatch(regexpQuery, lsLine[++i]))
                    noOfFailure++;  
                if (!isPatternMatch(regexpWebdavPut, lsLine[++i]))
                    noOfFailure++;  
                if (!isPatternMatch(regexpWebdavGet, lsLine[++i]))
                    noOfFailure++;  
                
                // System Performance
		while (isEmptyLine(lsLine[i]))
		    i++;         
                if(!isHeaderOk(PERFSTATS_SYS_HEADER, lsLine[++i]))
                    noOfFailure++;
		while (isEmptyLine(lsLine[i]))
		    i++;         
                
                // load, disk
                if (!isPatternMatch(regexpLoad, lsLine[++i]))
                    noOfFailure++;
                if (!isPatternMatch(regexpDisk, lsLine[++i]))
                    noOfFailure++;                  
            }             
            
        } catch (Throwable t) {
            noOfFailure++;  
            Log.ERROR("Error while parsing perfstats:\n" + line
		+ "\nException: " 
		+ Log.stackTrace(t));
        } 
        
        if (noOfFailure == 0)
            return true;
        else
            return false;
    }
    
    private boolean isPatternMatch(Pattern regexp, String line) {
	if (line == null)
	    return false;
        line = line.trim();
        Matcher matcher = regexp.matcher(line);
                    
        // verify the output pattern
        if (!matcher.matches()) {
            Log.ERROR("Actual: " + line + 
                    ", Expected: " + matcher.toString());
            return false;
        } 
        else {
            Log.INFO(line);
            return true;
        }
    }
    
    private boolean isHeaderOk(String header, String line) {
	if (line == null)
	    return false;
        line = line.trim();
        if (!line.equals(header)) {
            Log.WARN("Actual Header: " + line + "\nExpected Header: " + header);
            return false;
        }
        else {
            Log.INFO(line);             
            return true;    
        }
    }
    
    private boolean isEmptyLine(String line) {
	if (line == null)
	    return false;
        if (line.trim().equals("")) {
            return true;
        }
        else 
            return false;                
    }
    
    private void setHowlongInterval() throws Throwable{
        String tmp = getProperty("howlong");   
        int tmpVal = -1;
        if (null != tmp) {
            try {
                tmpVal = Integer.parseInt(tmp);
                if (tmpVal <= 0) 
                    Log.WARN("invalid how long: " + tmpVal + 
                            " will run test using default (1 minute)");
                else
                    PERFSTATS_HOWLONG = tmp;
            } catch (NumberFormatException nfe) {
                Log.WARN("Invalid how long: " + tmpVal + " (should be a number), "
                        + "will run test using default (1 minute)");
            }
        }
        
        tmp = getProperty("interval");   
        if (null != tmp) {
            try {
                tmpVal = Integer.parseInt(tmp);
                if (tmpVal <= 0) 
                    Log.WARN("invalid interval: " + tmpVal + 
                            " will run test using default (10 secs)");
                else
                    PERFSTATS_INTERVAL = tmp;
            } catch (NumberFormatException nfe) {
                Log.WARN("Invalid interval: " + tmpVal + " (should be a number), "
                        + "will run test using default (10 secs)");
            }
        }
    }
    
    private String [] getAllTestCommand() {
        try {
            setHowlongInterval();
        } catch (Throwable t) {}
        
        String [] lsCommand = {
            HoneycombCLISuite.PERFSTATS_COMMAND + PERFSTATS_HOWLONG_PARAM,
            HoneycombCLISuite.PERFSTATS_COMMAND + PERFSTATS_HOWLONG_PARAM + 
                    PERFSTATS_INTERVAL_PARAM,
            HoneycombCLISuite.PERFSTATS_COMMAND + PERFSTATS_HOWLONG_PARAM + 
                    PERFSTATS_NODE_PARAM, 
            HoneycombCLISuite.PERFSTATS_COMMAND + PERFSTATS_HOWLONG_PARAM + 
                    PERFSTATS_INTERVAL_PARAM + PERFSTATS_NODE_PARAM
        };
        
        return lsCommand;
    }
    
    private String [] getAllTestArg() {
        String [] lsArgs = {
            "how long=" + PERFSTATS_HOWLONG,
            "how long=" + PERFSTATS_HOWLONG + "; interval=" + PERFSTATS_INTERVAL,
            "how long=" + PERFSTATS_HOWLONG + "; node=" + PERFSTATS_NODE,
            "how long=" + PERFSTATS_HOWLONG + "; interval=" + PERFSTATS_INTERVAL +
                    "; node=" + PERFSTATS_NODE
        }; 
        
        return lsArgs;
    }
    
}
