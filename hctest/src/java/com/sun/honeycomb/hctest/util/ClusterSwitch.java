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

import com.sun.honeycomb.test.SolarisNode;
import com.sun.honeycomb.test.util.RunCommand;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.hctest.TestBed;

// It's a bit of a misnomer that switch class extends SolarisNode,
// since switch OS isn't even Solaris... but all we need is convenient
// ssh-based runCmd methods, so let's keep it this way.

// TODO: When switch tests are automated, extend this class into
// PrimarySwitch and SecondarySwitch.

public class ClusterSwitch extends SolarisNode {

    private String adminVIP; // for ssh to switch

    private ClusterSwitch() {
        this(getAdminVIP(), SSH_ARGS + "-p 2222 root@" + getAdminVIP());
        adminVIP = getAdminVIP();
    }

    private ClusterSwitch(String hostname, String ssh) {
        super(hostname, ssh);
    }

    private static String getAdminVIP() {
        TestBed t = TestBed.getInstance();
        if (t != null) { // testbed is null in help mode
            return t.adminVIP;
        } else {
            return "unknown";
        }
    }

    /** Singleton access method (XXX any point in making it public?)
     */
    private static ClusterSwitch hcswitch;
    synchronized public static ClusterSwitch getInstance() {
        if (hcswitch == null) {
            hcswitch = new ClusterSwitch();
        }
        return hcswitch;
    }

    public String toString() {
        return "Switch:" + adminVIP;
    }
    
    /** Static access method to run given command on the cheat
     */
    public static String runCmdOnSwitch(String cmd) throws HoneycombTestException {
        return getInstance().runCmd(cmd);
    }

}

