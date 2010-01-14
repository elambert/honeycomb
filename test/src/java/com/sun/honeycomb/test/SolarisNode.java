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



package com.sun.honeycomb.test;

import com.sun.honeycomb.test.util.*;

import java.util.Iterator;
import java.util.List;

public class SolarisNode {

    protected String name;  // hostname (hcb10X or cheat)

    protected boolean isDown; // host down? based on ssh/ping    
    protected boolean beDown; // expected up/down state
    
    // constants
    protected static final String PKILL_JAVA ="\"if [ \\`ps -ef | grep java | grep -v grep | wc -l\\` != 0 ]; then pkill java; else /bin/true; fi\"";
    protected static final String PKG_INFO = "pkginfo ";
    protected static final String SNOOP = "/usr/sbin/snoop ";
    protected static final String SSH_ARGS = "-o StrictHostKeyChecking=no -o BatchMode=yes ";

    // support to run commands remotely over ssh
    protected RunCommand shell;
    protected String sshArgs; // for direct ssh access to this node

    /** Constructor takes hostname and command to ssh to this machine
     */
    public SolarisNode(String hostname, String ssh) {
        name = hostname;
        sshArgs = ssh;
        shell = new RunCommand();

        // Ping node and if it's down then set initial state to down
        // ## do this after initialization so that ClusterNode constructor 
        // can set cheat node 1st
        //if (!ping()){
        //    isDown = true;
        //    beDown = true;
        //}
    }

    public void initStatus() {
        // Ping node and if it's down then set initial state to down
        if (!ping()){
            isDown = true;
            beDown = true;
        }
    }
    
    public String toString() {
        return name;
    }

    /** Run given command on this machine over ssh, return output
     */
    public String runCmd(String cmd) throws HoneycombTestException {
        return shell.sshCmd(sshArgs, cmd);
    }

    /** Run given command on this machine over ssh, return info structure
     */
    public ExitStatus runCmdPlus(String cmd) throws HoneycombTestException {
        return shell.sshCmdStructuredOutput(sshArgs, cmd);
    }

    /** Run given command on this machine over ssh, verify its status,
     *  throw exception if command failed, return info structure
     */
    public ExitStatus runCmdVerify(String cmd) throws HoneycombTestException {
        ExitStatus status = runCmdPlus(cmd);
        verifyStatus(status);
        return status; // give back to caller so they can see output
    }
    
    protected void Log(List elements, int loglevel) {
        if (elements == null) 
            return;
        
        Iterator iter = elements.iterator();
        while (iter.hasNext()){
            Log.LOG(loglevel, (String)iter.next());
        }
    }
    
    public int runCmdAndLog(String command, boolean quiet) throws HoneycombTestException {
        String divider = "-------------------------------------------------------";
        if (!quiet) {
            // Output command line, stderr, stdout, in a visually distinct way
            Log.INFO(divider); // visual indentation
            Log.INFO("Executing command: '" + command + "' on " + name);    
        }

        ExitStatus status = runCmdPlus("\"" + command + "\"");
        
        if (!quiet) {
            List err = status.getErrStrings();
            if (err != null && !err.isEmpty()) {
                Log.ERROR(divider);
                Log(err, Log.ERROR_LEVEL);
                Log.ERROR(divider);
            }
            List out = status.getOutStrings();
            if (out != null && !out.isEmpty()) {
                Log.INFO(divider);
                Log(out, Log.INFO_LEVEL);
                Log.INFO(divider); 
            }
            Log.INFO(""); // visual indentation
        }
        
        return status.getReturnCode();
    }
    
    /** Verify status of command we ran, throw exception if it failed
     */
    public void verifyStatus(ExitStatus status) throws HoneycombTestException {
        int ret = status.getReturnCode();
        if (ret != 0 ){
            List l = status.getErrStrings();
            String errMsg = "Failed to run [" + status.getCmdExecuted() +"]: " +
                "return code: " + ret + " errors: " + l;
            throw new HoneycombTestException(errMsg);
        }
    }

    /** Check whether a given Solaris package is installed on this machine.
     */
    public boolean packageInstalled(String packageName) throws HoneycombTestException {
        if (isDown) return false; // ClusterNode has different logic
        
        Log.INFO("Checking for installed package: " + packageName );
        ExitStatus status = runCmdPlus(PKG_INFO + packageName);
        
        if (status.getReturnCode() != 0 )
            return false;
        else
            return true;
    }

    /** Ping this machine, return true if it's alive
     */
    public boolean ping() {
        boolean alive = false;
        try {
            alive = shell.ping(name);
        } catch (HoneycombTestException e) {
            Log.INFO("Node " + name + " is not pingable: " + e.getMessage());
        }
        if (!alive) {
            Log.INFO("Node " + name + " is not pingable");
        }
        return alive;
    }

    /** Write given data to new file on this machine
     */
    public void createFile(String filename, String contents) throws HoneycombTestException {
        if (isDown) return;
        Log.INFO("Creating file: " + filename);
        runCmdVerify("\"echo \'" + contents  + "\' > " + filename + "\"");
    }
	
    /** Delete given file on this machine
     */
    public void removeFile(String filename) throws HoneycombTestException {
        if (isDown) return;
        Log.INFO("Removing file: " + filename + " on node: " + name);
        runCmdVerify("\"rm -f " + filename +"\"");
    }
	
    /** Reboots this machine.
     */
    public void reboot() throws HoneycombTestException {
        Log.INFO("Rebooting node " + name);
        runCmd("reboot");
        beDown = true;
    }
    
    public void rebootQuick() throws HoneycombTestException {
	Log.INFO("Quick Reboot of node " + name);
	runCmd("reboot -q");
	beDown = true;
    }

    /** Run after a reasonable time period after reboot to set node-up expectations.
     */
    public void rebootDone() {
        Log.INFO("Node " + name + " should have rebooted by now");
        beDown = false;
    }
    
    public void setBeDown(boolean b) {
	beDown=b;
    }

    public boolean getBeDown() {
	return beDown;
    }
    
    public boolean getIsDown () {
	return isDown;
    }



    

}
