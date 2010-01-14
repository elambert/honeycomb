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
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.hctest.TestBed;

public class CheatNode extends SolarisNode {

    private String hostname;

    public CheatNode(String hostname) {
        super(hostname, SSH_ARGS + "root@" + hostname);
        this.hostname = hostname;
    }
    
    private static String getDefaultCheatIP() {
        TestBed t = TestBed.getInstance();
        if (t != null) { // testbed is null in help mode
            return t.spIP;
        } else {
            return "unknown";
        }
    }

    public String toString() {
        return "Cheat: " + hostname;
    }

    private static CheatNode defaultCheat = null;
    public static CheatNode getDefaultCheat() {
        if (defaultCheat == null)
            defaultCheat = new CheatNode(getDefaultCheatIP());
        return defaultCheat;
    }

    /** Static access method to run given command on the cheat
     */
    public static String runCmdOnDefaultCheat(String cmd) 
        throws HoneycombTestException {

        Log.INFO(
              "NOT MULTICELL-COMPATIBLE: using cheat node of default cell");
        return getDefaultCheat().runCmd(cmd);
    }
}

