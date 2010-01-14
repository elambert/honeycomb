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



package com.sun.honeycomb.spreader;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

//
public class SwitchRules_test1 {

    private static final Logger logger =
        Logger.getLogger(SwitchRules_test1.class.getName());

    /*************************************************************
     **/
    public boolean test16NodesWithNode13Down() throws Exception {
        int masksize = 4; // 16 nodes

        int portMaskSize = masksize/2;
        int srcMaskSize = masksize - portMaskSize;
        int maxPVal = (1 << portMaskSize) - 1;
        int maxHVal = (1 << srcMaskSize) - 1;

        TestSwitchRules.imaskFile = "share/zimask.out";
        TestSwitchRules.iruleFile = "share/zirule.out";
        TestSwitchRules tsr = new TestSwitchRules(maxHVal, maxPVal);

        //tsr.dumpRules(Level.INFO);
        List rules = tsr.getRules();
        List expected = getRulesFromFile("share/expected.irules");
        if (!isEqual(expected, rules)) {
            return false;
        }
        logger.info("test passed");
        return true;
    }

    /*************************************************************/
    private List getRulesFromFile(String file) throws IOException {
        LinkedList rules = new LinkedList();
        BufferedReader br;
        br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            String[] fields = line.trim().split("[! \t]+");
            String vIP = fields[1]; // after -dest
            int switchPort;
            Rule rule;
            if (fields[2].matches("-t")) {
                if (fields[3].matches("arp_request")) {
                    switchPort = Integer.parseInt(fields[5]);
                    rule = new Rule(vIP, "arp_request", switchPort);
                } else if (fields[3].matches("icmp")) {
                    switchPort = Integer.parseInt(fields[5]);
                    rule = new Rule(vIP, "icmp", switchPort);
                } else {
                    throw new IOException("Unable to parse");
                }
            } else {
                int srcAddr = Integer.parseInt(fields[3]); // after -src
                int srcAddrMask = Integer.parseInt(fields[4]); // after !
                int ipPort = Integer.parseInt(fields[6]); // after srcport
                int portMask = Integer.parseInt(fields[7]); // after !
                int destPort = Integer.parseInt(fields[9]); // after -destport
                switchPort = Integer.parseInt(fields[11]); // after -s
                rule = new Rule(vIP, destPort, srcAddr, srcAddrMask, ipPort,
                                portMask, switchPort);
            }
            rules.add(rule);
        }
        return rules;
    }

    /*************************************************************/
    private boolean isEqual(List expected, List rules) {
        for (Iterator i = expected.iterator(); i.hasNext(); ) {
            Rule exp = (Rule) i.next();
            if (!rules.contains(exp)) {
                logger.severe("Rule not found: " + exp);
                for (Iterator it = rules.iterator(); it.hasNext(); ) {
                    Rule rule = (Rule) it.next();
                    logger.info(rule.toString());
                }
                return false;
            }
            rules.remove(exp);
        }
        if (!rules.isEmpty()) {
            for (Iterator i = rules.iterator(); i.hasNext(); ) {
                Rule rule = (Rule) i.next();
                logger.severe("Spurious rule: " + rule);
            }
            return false;
        }
        return true;
    }
}
