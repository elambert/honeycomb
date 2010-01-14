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

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.util.Exec;

import java.io.IOException;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 * @author Shamim Mohamed
 * @version $Id: Rule.java 5292 2005-07-29 00:11:43Z sm152240 $
 */
class RuleSet {

    private static final String PROP_SWITCH_TIMEOUT =
        "honeycomb.switch_mgr.zrule.timeout";
    private static final String PROP_SWITCH_RETRIES =
        "honeycomb.switch_mgr.zrule.retries";
    private static final String PROP_SWITCH_ALGORITHM =
        "honeycomb.switch_mgr.xfer.algorithm";

    public static final int DEFAULT_TIMEOUT = 7000;
    public static final int DEFAULT_RETRIES = 4;

    private List rules;
    private boolean sorted;
    private int maxHVal, maxPVal;

    private static final int ALGO_MERGE = 0;
    private static final int ALGO_SET = 1;
    private static int updateAlgo = ALGO_SET;

    private static int timeout = DEFAULT_TIMEOUT;
    private static int retries = DEFAULT_RETRIES;

    private static boolean initialized = false;

    private static final Logger logger =
        Logger.getLogger(RuleSet.class.getName());

    RuleSet(ClusterProperties config) {
        this(config, 0, 0);
    }
    RuleSet(ClusterProperties config, int maxHVal, int maxPVal) {
        if (!initialized)
            readConf(config);

        rules = new LinkedList();
        this.maxHVal = maxHVal;
        this.maxPVal = maxPVal;
        this.sorted = true;

        if (logger.isLoggable(Level.INFO))
            logger.info("Switch programming timeout = " + timeout + "ms, " +
                        "with " + retries + " retries.");
    }

    /**
     * Add a rule to the list
     *
     * @return success value
     */
    public boolean add(Rule r) {
        if (r == null)
            return false;

        sorted = false;
        return rules.add(r);
    }

    public Iterator iterator() {
        if (!sorted) {
            sorted = true;
            Collections.sort(rules);
        }
        return rules.iterator();
    }

    public static void reset(String address) {
        execSwitchCmd(address, "reset", "");   // clear all rules
    }

    public boolean send(String address) {
        return send(address, true);
    }

    public boolean send(String address, boolean merge) {
        boolean ok = false;

        if (!sorted) {
            sorted = true;
            Collections.sort(rules);
        }

        if (!merge) {
            return syncSet(address);
        }

        switch (updateAlgo) {
        case ALGO_SET:
            return syncSet(address);

        case ALGO_MERGE:
            return syncMerge(address);

        default: throw new RuntimeException("Bad algorithm " + updateAlgo);
        }
    }

    /**
     * Compare the rules on the switch with this list (wrapper)
     *
     * @return success value
     */
    public boolean verify(String address) {
        String curRules = null;
        try {
            SwitchRules s = new SwitchRules(maxHVal, maxPVal);
            List currentRules = s.getRules();
            curRules = prRules("Current switch rules:", currentRules);

            if (equals(currentRules)) {
                logger.info("Switch rules are OK.");
                return true;
            }
            s.dumpOutput(Level.INFO);
        }
        catch (MalformedOutputException e) {
            logger.log(Level.SEVERE, "Couldn't talk to switch", e);
        }

        if (curRules != null)
            logger.info(curRules);
        logger.info(prRules("Required rules:", rules));
        return false;
    }

    /**
     * Compare the rules with this list
     *
     * @param currentRules list of rules to compare with
     * @return success value
     */
    private boolean equals(List currentRules) {
        boolean ok = true;
        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();
            if (!remove(currentRules, r)) {
                logger.warning("Rule \"" + r + "\" didn't get added");
                ok = false;
            }
        }

        for (Iterator i = currentRules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();
            logger.warning("Rule \"" + r + "\" on switch is spurious");
            ok = false;
        }

        return ok;
    }

    private static synchronized void readConf(ClusterProperties config) {
        String prop;
        if ((prop = config.getProperty(PROP_SWITCH_TIMEOUT)) != null)
            try {
                timeout = Integer.parseInt(prop);
            } catch (NumberFormatException e) {
                logger.warning("Value \"" + prop + "\" for property " +
                               PROP_SWITCH_TIMEOUT + " non-numeric; using " +
                               timeout + "ms");
            }

        if ((prop = config.getProperty(PROP_SWITCH_RETRIES)) != null)
            try {
                retries = Integer.parseInt(prop);
            } catch (NumberFormatException e) {
                logger.warning("Value \"" + prop + "\" for property " +
                               PROP_SWITCH_RETRIES + " non-numeric; using " +
                               retries);
            }

        if ((prop = config.getProperty(PROP_SWITCH_ALGORITHM)) != null) {
            prop = prop.trim();
            if (prop.equalsIgnoreCase("set"))
                updateAlgo = ALGO_SET;
            else if (prop.equalsIgnoreCase("merge"))
                updateAlgo = ALGO_MERGE;
            else
                logger.warning("Value \"" + prop + "\" for property " +
                               PROP_SWITCH_RETRIES + " invalid.");
        }

        initialized = true;
    }

    /**
     * Send the rules to a specified switch by first deleting all rules
     * and then adding the desired ones
     *
     * @param address the address to bind to
     * @return success value
     */
    private synchronized boolean syncSet(String address) {
        boolean ok = true;

        reset(address);         // Erase all rules

        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();
            if (!execSwitchCmd(address, "add", r.toString()))
                ok = false;
        }

        return ok;
    }

    /**
     * Given a list of desired rules, "make it so" on the switch with
     * the appropriate sequence of adds and deletes
     *
     * @return success value
     */
    private synchronized boolean syncMerge(String address) {
        boolean ok = true;

        List currentRules = null;
        try {
            SwitchRules s = new SwitchRules(maxHVal, maxPVal);
            currentRules = s.getRules();
        }
        catch (MalformedOutputException e) {
            logger.log(Level.SEVERE, "Couldn't talk to switch", e);
            return false;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine(prRules("Current rules:", currentRules));
            logger.fine(prRules("Desired rules:", rules));
        }

        // For every rule in currentRules, delete everything we don't
        // need; then for every rule in the desired rules, add
        // everything not already present

        for (Iterator i = currentRules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();

            // if r is in the desired set of rules, ignore it
            if (member(rules, r))
                continue;

            if (!execSwitchCmd(address, "delete", r.toString()))
                ok = false;
            i.remove();
        }

        // The rules in currentRules are already on the switch

        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();
            
            // if r is already in currentRules, ignore it
            if (member(currentRules, r))
                continue;

            if (!execSwitchCmd(address, "add", r.toString()))
                ok = false;
        }

        return ok;
    }

    /**
     * Adds or deletes a rule on a switch by calling an external
     * program <tt>zrule</tt>
     *
     * @param action whether to add or delete the rule
     * @param rule the rule expressed in a form suitable for zrule
     * @param addr the source address for the request
     * @return success of the endeavour
     */
    private static boolean execSwitchCmd(String addr,
                                         String action, String rule) {

        String cmdname = "/opt/honeycomb/bin/zrule2";

        String options = "-b " + addr +
            " -timeout " + timeout + " -retries " + retries;

        // Verbose output?
        if (logger.isLoggable(Level.INFO))
            options += " -v";
        if (logger.isLoggable(Level.FINE))
            options += " -v";

        String cmd = cmdname + " " + action + " " + options + " " + rule;
        return execCmd(cmd);
    }

    /**
     * Code which actually forks and exec the command the be sent to the switch
     * (Silo switch, or Cell switch)
     *
     * @param cmd command to be sent
     */
    private static boolean execCmd(String cmd) {
        int ec = -1;
        try {
            ec = Exec.exec(cmd, logger);
        }
        catch (IOException e) {
            logger.severe("Couldn't exec " + cmd);
            return false;
        }

        if (ec != 0) {
            logger.severe("Exit code " + ec);
            return false;
        }
        return true;
    }


    /**
     * Remove a rule from the list of rules. List.remove() doesn't do
     * what we want because it doesn't use the objects' equals() op
     *
     * @param rules list of rules
     * @param r the rule to remove
     * @return whether we were successful in removing it
     */
    private boolean remove(List rules, Rule r) {
	if (r == null) {
	    logger.finer("Null rule");
	    return true;
	}
        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule ri = (Rule) i.next();
	    if (ri == null) {
		logger.warning("Null rule in rules");
		continue;
	    }
            if (r.equals(ri)) {
                logger.finer("Rule \"" + r + "\" on switch is required");
                i.remove();
                return true;
            }
        }
        logger.finer("Rule \"" + r + "\" undesired; to be removed");
        return false;
    }

    /**
     * Look for a rule in the list of rules. List.member() doesn't do
     * what we want because it doesn't use the objects' equals() op
     *
     * @param rules list of rules
     * @param r the rule to find
     * @return whether the rule exists in the list
     */
    private boolean member(List rules, Rule r) {
	if (r == null)
	    return false;

        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule ri = (Rule) i.next();
	    if (ri == null)
		continue;

            if (r.equals(ri))
                return true;
        }
        return false;
    }

    private static String prRules(String label, List rules) {
        String s = "";

        if (label != null)
            s = label + "\n";

        for (Iterator i = rules.iterator(); i.hasNext(); ) {
            Rule r = (Rule) i.next();
            s += "    " + r + "\n";
        }
        return s;
    }
}
