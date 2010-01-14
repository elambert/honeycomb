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

import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.layout.DiskMask;

/**
 * A class to interface with the CLI and provide utility
 * functions around these commands.
 */
public class CLIUtil
{
    private CLI cli;
    String adminVIP;
    private final int OFFLINE = 0;
    private final int ONLINE = 1;
    private final long sleeptime = 30 * 1000; // between interations

    public CLIUtil(String adminvip)
    {
        this.adminVIP = adminvip;
        this.cli = new CLI(adminvip);
    }

    public boolean waitForNodeOnline(int node, long timeout) 
        throws Throwable {
        return (waitForNodeState(node, ONLINE, timeout));
    }

    public boolean waitForNodeOffline(int node, long timeout) 
        throws Throwable {
        return (waitForNodeState(node, OFFLINE, timeout));
    }

    private boolean waitForNodeState(int node, int desiredState, long timeout) 
        throws Throwable {
        long timenow = System.currentTimeMillis();
        long timeend = timenow + timeout;

        try {
            while (timenow < timeend) {
                int disksCorrect = 0;
                HwStat hwstat = new HwStat(cli.runCommand("hwstat"));
                DiskMask diskMask = hwstat.getDiskMask();

                Log.DEBUG("diskMask is " + diskMask);

                // XXX - we should pay attention to the node state explicitly
                // and not infer that from the disk mask

                // Maybe have option to allow not all disks to be 
                // online in the the future?
                for (int i = 0; i < HoneycombTestConstants.DISKS_PER_NODE; i++) {
                    if (diskMask.isOnline(node, i)) {
                        if (desiredState == OFFLINE) {
                            Log.INFO("found online disk " + node + 
                                HoneycombTestConstants.LAYOUT_DISK_DELIMITER +
                                i + " but desire offline state");
                        } else {
                            disksCorrect++;
                        }
                    } else { // disk is offline
                        if (desiredState == ONLINE) {
                            Log.INFO("found offline disk " + node +
                                HoneycombTestConstants.LAYOUT_DISK_DELIMITER +
                                i + " but desire online state");
                        } else {
                            disksCorrect++;
                        }
                    }
                }

                if (disksCorrect == HoneycombTestConstants.DISKS_PER_NODE) {
                    Log.INFO("node " + node + " was in correct state");
                    // XXX is this needed?  should client lib handle this?
                    Log.INFO("XXX waiting a bit longer (" + sleeptime +
                        " msecs) to let switch updates, etc., occur");
                    Thread.sleep(sleeptime);
                    return (true);
                }

                Log.INFO(disksCorrect + " disks out of " +
                    HoneycombTestConstants.DISKS_PER_NODE +
                    " were in the correct state");

                long timeleft = timeend - timenow;
                if (timeleft < sleeptime) {
                    Log.INFO("not enough time left to try again...aborting");
                    break;
                }

                Log.INFO("Sleeping " + sleeptime + " waiting for state to change");
                Log.INFO("time before sleep is " + timenow + ", loop exit at " +
                    timeend + " (" + timeleft + " msecs from now)");
                Thread.sleep(sleeptime);
                timenow = System.currentTimeMillis();
            }
        } catch (Throwable t) {
            Log.ERROR("caught exception, assuming failure: " + t);
            Log.ERROR(Log.stackTrace(t));
            throw (t);
        }

        return (false);
    }

    /**
     * Get all nodes, disks that should be online, back online.
     * XXX should this take the offline lists and make them offline, or just
     * ignore the things in that list?
     */
    public boolean resetClusterState(String offlineNodes, String offlineDisks,
        long timeout) throws Throwable {
        long timenow = System.currentTimeMillis();
        long timeend = timenow + timeout;

        if (offlineNodes == null) {
            offlineNodes = "<none>";
        }

        if (offlineDisks == null) {
            offlineDisks = "<none>";
        } else {
            // hee hee, ':' is the property separator for the runtest command line
            // so we must pass the disks using 101-3 instead of 101:3
            if (offlineDisks.indexOf(HoneycombTestConstants.CLI_DISK_DELIMITER) == -1) {
                throw new RuntimeException("Couldn't find disk delimeter " +
                    HoneycombTestConstants.CLI_DISK_DELIMITER +
                    " in offline disk list: " + offlineDisks +
                    ".  Assuming syntax error");
            }

            offlineDisks = offlineDisks.replace(
                HoneycombTestConstants.CLI_DISK_DELIMITER,
                HoneycombTestConstants.LAYOUT_DISK_DELIMITER);
        }

        Log.INFO("Attempting to reset cluster to desired initial state.");
        Log.INFO("Nodes that are declared to be offline are " + offlineNodes);
        Log.INFO("Disks that are declared to be offline are " + offlineDisks);

        try {
            while (timenow < timeend) {
                HwStat hwstat = new HwStat(cli.runCommand("hwstat"));
                DiskMask diskMask = hwstat.getDiskMask();
                boolean offline = false;

                Log.DEBUG("diskMask is " + diskMask);

                for (int i = 1; i <= HoneycombTestConstants.MAX_CLUSTER; i++) {
                    // skip node in offline nodes list
                    if (offlineNodes.contains("" + (100+i))) {
                        Log.INFO("skipping node " + i + "; in offline node list");
                        continue;
                    }

                    for (int j = 0; j < HoneycombTestConstants.DISKS_PER_NODE; j++) {
                        String diskString = (100+i) +
                            HoneycombTestConstants.LAYOUT_DISK_DELIMITER + j;
                        if (offlineDisks.contains(diskString)) {
                            Log.INFO("skipping disk " + diskString +
                                "; in offline disk list");
                            continue;
                        }

                        // XXX - pay attention to node state explicitly
                        // and not infer that from the disk mask

                        if (diskMask.isOffline(100 + i, j)) {
                            // XXX make this logic smarter...to target just
                            // node or disk as needed.  Currently we blindly
                            // re-enable things, but this seems fine...
                            Log.INFO("found offline disk " + i + 
                                HoneycombTestConstants.LAYOUT_DISK_DELIMITER +
                                j + ", maybe node down.  Try enable");
                            ClusterNode hcNode = new ClusterNode(adminVIP, i);
                            hcNode.startHoneycomb();

                            Log.INFO("also try enabling that disk");
                            Log.INFO(HCUtil.readLines(cli.runCommand("hwcfg DISK-" +
                                diskString + " --enable")));

                            // note the fact that we need to check again next
                            // iteration
                            offline = true;
                        }
                    }
                }
 
                if (offline == false) {
                    Log.INFO("Cluster is as online as expected. " +
                        " Declared offline nodes are " + offlineNodes +
                        ".  Declared offline disks are " + offlineDisks + ".");
                    return (true);
                }

                long timeleft = timeend - timenow;
                if (timeleft < sleeptime) {
                    Log.INFO("not enough time left to try again...aborting");
                    break;
                }

                Log.INFO("Sleeping " + sleeptime + " waiting for state to change");
                Log.INFO("time before sleep is " + timenow + ", loop exit at " +
                    timeend + " (" + timeleft + " msecs from now)");
                Thread.sleep(sleeptime);
                timenow = System.currentTimeMillis();
            }
        } catch (Throwable t) {
            Log.ERROR("caught exception, assuming failure: " + t);
            Log.ERROR(Log.stackTrace(t));
            throw (t);
        }

        return (false);

    }
}
