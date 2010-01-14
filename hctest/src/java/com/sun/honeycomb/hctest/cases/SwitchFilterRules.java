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

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validate that external traffic drop and accept rules for the data
 * VIP, admin VIP, and service processor IP are present on the switch.
 */
public class SwitchFilterRules extends HoneycombLocalSuite {
    protected ClusterMembership cm = null;

    // Entire drop rule
    private String dropRule =
        "drop | ip                                       0";

    // Base accept rule
    private String acceptRule =
        "accpt | ip                       0  ";

    // The patterns below are hardcoded for port 8080/8079. This is to ensure 
    // we have rules only for those. If the ports changes, this test has to
    // modified.

    // Forward Rules for tcp port 8079
//    private String forwdRule8079 =
//        ".*forwd.*tcp.*8079.*(\\d)$";

    // Forward Rules for tcp port 8080
    private String forwdRule8080 =
        ".*forwd.*tcp.*8080.*(\\d)$";

    private int numForwdRule8080=0;

//    private int numForwdRule8079=0;

    private boolean foundRulesForDeadNode = false;


    /*
     * Sometimes there is an extra space or two depending on ip
     * length. We should do a pattern match here with the base accept
     * rule plus one or more spaces, then the ip.
     */
    private String acceptRule2 = acceptRule + " ";
    private String acceptRule3 = acceptRule2 + " ";
    private String acceptRule4 = acceptRule3 + " ";

    public SwitchFilterRules() {
        super();
    }

    public String help() {
        return("\tValidate that filtering rules are set on the switch\n");
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();        
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    public synchronized void init () throws HoneycombTestException {
        // Do the following after we know there is a real cluster.
        if (cm == null) 
            cm = new ClusterMembership(-1, testBed.adminVIP);
    }

    public void testFilterRules() throws HoneycombTestException {
        String irules;
        String nodeMgrMailBox;
//        ArrayList port8079_rules_list = new ArrayList();
        ArrayList port8080_rules_list = new ArrayList();
        ArrayList deadnode_port_list = new ArrayList();
        int error = 0;
        TestCase self = createTestCase("SwitchFilterRules", "switch filter rules");
        
        self.addTag(Tag.REGRESSION);
        self.addTag(Tag.NOEMULATOR);
        self.addTag(HoneycombTag.SWITCH);

        if (self.excludeCase()) return;

        init();

        String dataVIP = testBed.dataVIPaddr;
        String adminVIP = testBed.adminVIPaddr;
        String spIP = testBed.spIPaddr;

        Log.INFO("Data VIP: " + dataVIP);
        Log.INFO("Admin VIP: " + adminVIP);
        Log.INFO("Service Processor IP: " + spIP);


        ClusterNode clusterNode = cm.getNode(1);

        try {
            irules = clusterNode.irules();
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("Failed to retrieve switch rules "
                                             + Log.stackTrace(e));
        }
        if (irules == null ||
            irules.equals("")) {
            throw new HoneycombTestException("Failed to retrieve switch rules ");
        }
        Log.INFO("irules: " + irules);

        // Get the nodemgr mailbox.
        try {
            nodeMgrMailBox = clusterNode.readNodeMgrMailbox();
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("Failed to retrieve node manager mailbox "
                                             + Log.stackTrace(e));
        }
        if (nodeMgrMailBox == null ||
            nodeMgrMailBox.equals("")) {
            throw new HoneycombTestException("Failed to retrieve mailbox ");
        }

        Log.INFO("nodeMgrMailBox: " + nodeMgrMailBox);

        // Compile the pattern for DEAD nodes.
        String patternDead = "^(\\d+).*(DEAD)";
        Pattern pattern = Pattern.compile(patternDead, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(nodeMgrMailBox);
        // Read the lines
        while (matcher.find()) {
           int tmpport = Integer.parseInt(matcher.group(1)) - 100;
           Integer tmpIport = new Integer(tmpport);
           deadnode_port_list.add(tmpIport.toString());
        }

//         pattern = Pattern.compile(forwdRule8079, Pattern.MULTILINE);
//         matcher = pattern.matcher(irules);
//         while (matcher.find()) {
//            numForwdRule8079++;
//            port8079_rules_list.add(matcher.group(1));
//         }

        pattern = Pattern.compile(forwdRule8080, Pattern.MULTILINE);
        matcher = pattern.matcher(irules);
        while (matcher.find()) {
           numForwdRule8080++;
           port8080_rules_list.add(matcher.group(1));
        }

        // Check if there are same number of rules as the number of nodes.
        if (cm.getNumNodes() !=0 &&
	  numForwdRule8080 == cm.getNumNodes()) { 

//            &&
//            numForwdRule8079 == cm.getNumNodes()) {

//            Log.INFO("Found the forwd rules. Expected: " + cm.getNumNodes() + " rules for each port. Found port 8080: " + numForwdRule8080 + " port 8079: " + numForwdRule8079);

            Log.INFO("Found the forwd rules. Expected: " + cm.getNumNodes() + " rules for each port. Found port 8080: " + numForwdRule8080);

        } else {
//            Log.ERROR("Failed to match forwd rules. Expected: " + cm.getNumNodes() + " rules for each port. Found  port 8080: " + numForwdRule8080 + " port 8079: " + numForwdRule8079);

            Log.ERROR("Failed to match forwd rules. Expected: " + cm.getNumNodes() + " rules for each port. Found  port 8080: " + numForwdRule8080);

            error = 1;
        }
        
        // Check if there are rules for port 8080/8079 in the dead nodes list
        for (Iterator iter=deadnode_port_list.iterator(); iter.hasNext();) {
           if (port8080_rules_list.contains(iter.next())) {
               Log.ERROR("Failed: Found rules for dead nodes for port 8080");
               foundRulesForDeadNode = true;
               error = 1;
           }
        }

//         for (Iterator iter=deadnode_port_list.iterator(); iter.hasNext();) {
//            if (port8079_rules_list.contains(iter.next())) {
//                Log.ERROR("Found rules for dead nodes for port 8079");
//                foundRulesForDeadNode = true;
//                error = 1;
//            }
//         }
       
        if (foundRulesForDeadNode == false) {
            Log.INFO("Rules for dead nodes were not found");
        }

        if (!irules.contains(dropRule)) {
            Log.ERROR("Failed to find the drop rule for all external traffic");
            error = 1;
        } else {
            Log.INFO("Found the drop rule for all external traffic");
        }

        if (!irules.contains(acceptRule + adminVIP) &&
            !irules.contains(acceptRule2 + adminVIP) &&
            !irules.contains(acceptRule3 + adminVIP) &&
            !irules.contains(acceptRule4 + adminVIP)) {
            Log.ERROR("Failed to find the accept admin VIP rule for " + adminVIP);
            error = 1;
        } else {
            Log.INFO("Found the accept admin VIP rule for " + adminVIP);
        }

        if (!irules.contains(acceptRule + dataVIP) &&
            !irules.contains(acceptRule2 + dataVIP) &&
            !irules.contains(acceptRule3 + dataVIP) &&
            !irules.contains(acceptRule4 + dataVIP)) {
            Log.ERROR("Failed to find the accept data VIP rule for " + dataVIP);
            error = 1;
        } else {
            Log.INFO("Found the accept data VIP rule for " + dataVIP);
        }

        if (!irules.contains(acceptRule + spIP) &&
            !irules.contains(acceptRule2 + spIP) &&
            !irules.contains(acceptRule3 + spIP) &&
            !irules.contains(acceptRule4 + spIP)) {
            Log.ERROR("Failed to find the accept sp IP rule for " + spIP);
            error = 1;
        } else {
            Log.INFO("Found the accept sp IP rule for " + spIP);
        }

        if (error != 0) {
            self.testFailed("Failing test because not all drop/accept/forwd " +
                            "rules are present on the switch. Make sure " +
                            "you have version 1.1-8567 or later installed.");
        } else {
            self.testPassed("Found all drop/accept/forwd rules on the switch");
        }
    }
}
