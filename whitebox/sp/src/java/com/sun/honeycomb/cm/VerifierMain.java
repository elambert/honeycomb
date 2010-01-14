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

import com.sun.honeycomb.cm.CMMVerifier;
import com.sun.honeycomb.cm.NodeMgrVerifier;
import java.lang.reflect.Constructor;
import java.util.Map;

public class VerifierMain {

    public static void main (String[] args) {
        
        int clusterSize = 0;
        int maxRetries = 0;
        int verifyInterval = 1; // default: 1 second
        boolean noMailbox = false;
        boolean runForever = false;
        boolean quorum = false;
        boolean factory = false;

        final int NODEMGR_RETRIES = 3; // fixed
        
        CMMVerifier cmm = null;
        NodeMgrVerifier nmgr = null;

        if (args.length > 0 && args[0].startsWith("-h")) {
            usage();
        }

        clusterSize = Integer.getInteger("nodes", 8).intValue();
        maxRetries = Integer.getInteger("tries", 1).intValue();
        if (maxRetries == 0)
            runForever = true;
        verifyInterval = Integer.getInteger("interval", 1).intValue();
        noMailbox = Boolean.getBoolean("cmm-only");
        quorum = Boolean.getBoolean("quorum");
        factory = Boolean.getBoolean("factory");
        String verifierClassName = System.getProperty("cmm.test.verifier");

        System.out.println("Running verifier (version=" +
                           (verifierClassName == null ? "cluster" : "unit-test") +
                           ") with options: nodes=" + clusterSize +
                           " tries=" + maxRetries + " (runForever=" + runForever + 
                           ") interval=" + verifyInterval + " cmm-only=" + noMailbox +
                           " quorum=" + quorum + " factory=" + factory);

        try {
            /* Use unit-test version of CMM Verifier if specified, cluster version by defaults.
             */
            if (verifierClassName == null) {
                cmm = new CMMVerifier(clusterSize, maxRetries);
            } else {
                Class verifierClass = Class.forName(verifierClassName);
                Class[] constructorTypes = { int.class, int.class };
                Object[] constructorParams = { new Integer(clusterSize), new Integer(maxRetries) };
                Constructor constructor = verifierClass.getConstructor(constructorTypes);
                cmm = (CMMVerifier)constructor.newInstance(constructorParams);
            }
        } catch (Exception e) {
            System.out.println("Fatal error: " + e.getMessage());
            System.exit (100);
        }
        
        /* In normal case this loop will keep calling verify() every VERIFY_INTERVAL
         * until the view is consistent, or we reach maxRetries, whichever comes earlier.
         *
         * If runForever flag is set, will keep calling verify() every VERIFY_INTERVAL forever.
         */

        if (factory)
            System.out.println("CHECKING CMM STATE");
        else
            System.out.println("\n*** CHECKING CMM STATE ***\n");

        int errors = -1;
        boolean consistencyErr = false;
        boolean stateErr = false;
        boolean mailboxErr = false;

        while (runForever || ((errors != 0) && (cmm.retries < maxRetries))) {
            cmm.refresh();
            errors = cmm.verify();
            if (stateChanged(cmm.retries, clusterSize, cmm.clusterView, 
                             cmm.lastView)) {
                if (factory) {
                    if (errors == 0) {
                        System.out.println("CMM PASSED FACTORY CHECK");
                        break;
                    }
                } else {
                    if (errors != 0)
                        System.out.println("\n*** CLUSTER STATE - DIFFERENT: " + errors + 
                                       " ERRORS AFTER " + cmm.retries + " RETRIES ***");
                    else
                        System.out.println("\n*** CLUSTER STATE - DIFFERENT: OK AFTER " + 
                                       cmm.retries + " RETRIES ***");
                    cmm.report(); // print state when it changes, incl. initial state
               }
            } else {
                if (errors != 0)
                    System.out.println("\n*** CLUSTER STATE - SAME: " + errors + 
                                       " ERRORS AFTER " + cmm.retries + " RETRIES ***");
                else if (runForever)
                    System.out.println("\n*** CLUSTER STATE - SAME: OK AFTER " + 
                                       cmm.retries + " RETRIES ***");
            } // don't print state if it's the same as before
            doSleep(verifyInterval);
        }
        consistencyErr = (errors > 0 ? true : false);
        stateErr = (errors < 0 ? true : false);

        errors = -1; // restart counter
        if (!noMailbox && !consistencyErr) {
            try {
                nmgr = new NodeMgrVerifier(clusterSize, cmm.baseView, 
                                           NODEMGR_RETRIES, quorum, factory);
            } catch (Exception e) {
                System.out.println("Fatal error: " + e.getMessage());
                System.exit (100);
            } 
            if (factory)
                System.out.println("CHECKING INTERNAL SERVICES");
            else
                System.out.println("\n*** CHECKING NODE_MGR MAILBOXES ***\n");

            while ((errors != 0) && (nmgr.retries < NODEMGR_RETRIES)) {
                nmgr.refresh();
                errors = nmgr.verify();
                // mailbox errors are already printed from verify()
            }
            mailboxErr = (errors == 0 ? false : true);
        }
        
        // error / state reporting on exit
        if (consistencyErr) {
            if (factory) {
                System.out.println("FACTORY TEST FAILED: INCONSISTENT STATE AFTER " + cmm.retries + " RETRIES");
                System.exit(1);
            } else
                System.out.println("\n*** FATAL: INCONSISTENT STATE AFTER " + cmm.retries + " RETRIES");
        } else {
            if (factory) {
                if (stateErr && mailboxErr) {
                    System.out.println("FACTORY TEST FAILED: MASTERSHIP AND SERVICE MAILBOX(ES) WRONG");
                } else if (stateErr  ||  mailboxErr) {
                    System.out.println("FACTORY TEST FAILED: SERVICE MAILBOX(ES) WRONG");
                } else {
                    System.out.println("FACTORY TEST PASSED");
                    System.exit(0);
                }
                System.exit(1);
            } else {
                String msg = "\n*** CLUSTER STATE: CONSISTENT AFTER " + cmm.retries + " RETRIES";
                if (stateErr && mailboxErr) {
                    System.out.println(msg + " BUT MASTERSHIP AND MAILBOX WRONG");
                } else if (stateErr) {
                    System.out.println(msg + " BUT MAILBOX WRONG");
                } else if (mailboxErr) {
                    System.out.println(msg + " BUT MAILBOX WRONG");
                } else {
                    System.out.println(msg);
                }
                if (noMailbox)
                    cmm.report();  // just CMM state
                else if (nmgr != null)
                    nmgr.report(); // CMM + mailbox (services) state
            }
        }
    }
    
    private static void usage () {
        StringBuffer msg = new StringBuffer();
        msg.append("VerifierMain ");
        msg.append("[-Dnodes=<clusterSize:8>] ");    // how many nodes in cluster (default 8)
        msg.append("[-Dcmm-only] ");                 // verify only cmm? (default no, verify nodemgr_mailbox too)
        msg.append("[-Dtries=<count:1>] ");          // how many verification attempts? (default 1)
        msg.append("[-Dinterval=<seconds:1>] ");     // how long between retries? (default 1 second)
        msg.append("[-Dquorum] ");                   // expect quorum (data mode)? (default no, maintenance mode)
        msg.append("\n");
        msg.append("Use tries=0 to run verifier in a loop forever. More help in source file.\n");
        System.out.println(msg.toString());
        System.exit(1);
    }

    private static void doSleep (int seconds) {
        try {
            Thread.sleep(seconds * 1000); 
        } catch (InterruptedException ie) {
            System.out.println("Sleep interrupted");
        }
    }

    /* Compares current cluster state to last (ie before last refresh), return true if differs.
     */
    private static boolean stateChanged(int retries, int numNodes, Map currView,
                                         Map lastView) {
        if (retries == 0 || retries == 1) {
            // first known state is treated as a change 
            return true; 
        }
        for (int i = 1; i <= numNodes; i++) {
            NodeView current = (NodeView) currView.get(node(i));
            NodeView last = (NodeView) lastView.get(node(i));
            if ((current == null || last == null) ||
                (current.id != last.id)) { // something's bogus
                return true;
            }
            if (!current.cmp(last)) { // state has changed
                return true;
            }
        }
        return false; // same state as before
    }

    private static String node(int num) {
        int nodeNum = 100 + num;
        return "hcb" + nodeNum;
    }

}
