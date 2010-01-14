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



package com.sun.honeycomb.cm;

import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;

import java.util.logging.Logger;

public class CMMMain {

    private static final Logger logger = 
        Logger.getLogger(CMMMain.class.getName());

    private static String nodesConfig = 
        "101 10.123.45.101 true, 102 10.123.45.102 true, " +
        "103 10.123.45.103 true, 104 10.123.45.104 true, " +
        "105 10.123.45.105 true, 106 10.123.45.106 true, " +
        "107 10.123.45.107 true, 108 10.123.45.108 true, " +
        "109 10.123.45.109 true, 110 10.123.45.110 true, " +
        "111 10.123.45.111 true, 112 10.123.45.112 true, " +
        "113 10.123.45.113 true, 114 10.123.45.114 true, " +
        "115 10.123.45.115 true, 116 10.123.45.116 true";

    private static void usage () {
        System.out.println (
            "CMMMain NODEID [SINGLE_MODE] [ [QUORUM] [NUM_DISKS] ]");
    }

    public static void main (String[] args) {
        if (args == null || args.length == 0 
                || args.length > 4 || args.length == 3) {
            usage();
            System.exit (1);
        }

        int     localnode  = -1;
        boolean singleMode = false;
        boolean doQuorum   = false;
        int     numDisks   = 0;

        try {
            try {
                localnode = Integer.parseInt (args[0]);
            } catch (Exception e) {
                throw new ArgumentException ("nodeid", args[0], e);
            }

            if (args.length > 1) {
                try {
                    singleMode = Boolean.getBoolean (args[1]);
                } catch (Exception e) {
                    throw new ArgumentException ("singlemode", args[1], e);
                }
            }

            if (args.length > 2) {
                try {
                    doQuorum = Boolean.getBoolean (args[2]);
                } catch (Exception e) {
                    throw new ArgumentException ("quorum", args[2], e);
                }
                try {
                    numDisks = Integer.parseInt (args[3]);
                } catch (Exception e) {
                    throw new ArgumentException ("num_disks", args[3], e);
                }
            }
        } catch (ArgumentException e) {
           usage();
           System.out.println (e.getMessage());
           System.exit (2);
        }

        Runtime.getRuntime().addShutdownHook(new Goodbye());

        // By setting nul CMM is ready for Test Mode.
	//        CMM.start(localnode, null, singleMode, doQuorum, numDisks, "unit-test", 1);	
	// New CMM.start doesn't take in a minor version number
	// TODO: Fix this - "unit-test" may not be an acceptable version string
	CMM.start(localnode, null, singleMode, doQuorum, numDisks, "unit-test");
    }

    private static class Goodbye extends Thread {
        public void run() {
            String pid = System.getProperty("PID", "Unknown");
            logger.info("[PID " + pid + "] CMM JVM is exiting. Goodbye!");
        }
    }
}
