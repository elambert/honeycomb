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

import com.sun.honeycomb.hctest.cases.interfaces.FileSizeCase;
import com.sun.honeycomb.hctest.cases.interfaces.FileSizeCases;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.cli.CLIUtil;
import com.sun.honeycomb.hctest.cli.HwStat;
import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.HoneycombSuite;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HCTestWritableByteChannel;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombFragment;

import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.HoneycombTestException;

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.TestNVOA;

import com.sun.honeycomb.common.NewObjectIdentifier;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutConfig;

import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;

public class RetrieveNodeFaults extends HoneycombSuite
{
    public RetrieveNodeFaults() {
        super();
    }

    public void setUp() throws Throwable 
    {
        super.setUp();
    }

    public String help() {
        return(
            "\tTry retrieves with nodes down\n" +
            "\tOptional -ctx args:\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_OFFLINE_NODES +
                "=n (n is the 3 digit nodeid. Can be comma separated list: 101,105,116)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_OFFLINE_DISKS +
                "=n (n is the node-disk. Can be comma separated list: 101" +
                HoneycombTestConstants.CLI_DISK_DELIMITER + "2,105" + 
                HoneycombTestConstants.CLI_DISK_DELIMITER + "0.)\n");
    }

    public void testRetrieveNodeFaults()
    {
        try {
            ArrayList tagSet = new ArrayList();
            tagSet.add("data-op");
            tagSet.add("retrieve");
            tagSet.add("node-fault");
            Run run = Run.getInstance();
            if (!run.isTagSetActive(tagSet)) {
                Log.INFO("skipping RetrieveNodeFaults");
                return;
            }

            String cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
            String dataVIP = cluster + "-data";
            String adminVIP = cluster + "-admin";
            HoneycombTestClient htc = new HoneycombTestClient(dataVIP);

            String offlineNodes = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_OFFLINE_NODES);
            String offlineDisks = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_OFFLINE_DISKS);
            long maxWaitTime = 5 * 60 * 1000; // wait between state transistions

            ArrayList fileSizes = new ArrayList();
            fileSizes.add(FileSizeCases.BOTROSARNOUD);
            fileSizes.add(FileSizeCases.SINGLECHUNK);
            fileSizes.add(FileSizeCases.MULTICHUNK);

            // For node tests, we don't do all combinations, just "interesting"
            // ones
            ArrayList whichFrags = new ArrayList();
            // first frag (special due to BotrosArnoud)
            whichFrags.add(new HoneycombFragment(0)); 
            // last data frag (indexed from 0)
            whichFrags.add(new HoneycombFragment(HoneycombTestConstants.OA_DATA_FRAGS - 1)); 
            // first parity frag (indexed from 0)
            whichFrags.add(new HoneycombFragment(HoneycombTestConstants.OA_DATA_FRAGS)); 

            // For retries to avoid IOExceptions
            boolean actionSucceeded = false;
            int numtries = 10;
            long retrySleep = 15 * 1000;

            for (int i = 0; i < fileSizes.size(); i++) {
                FileSizeCase fsc = (FileSizeCase) fileSizes.get(i);
                long fileSize = fsc.getSize();

                TestCase setupCase = new TestCase(this, 
                    "retrieve_node_down",
                    "setup_" + fsc.name);
                setupCase.addTag(new String[] {"data-op", "retrieve", "node-fault"});
                LayoutClient layoutClient = LayoutClient.getInstance();

                CLI cli = new CLI(adminVIP);
                CLIUtil cliUtil = new CLIUtil(adminVIP);
                if (!cliUtil.resetClusterState(offlineNodes, offlineDisks,
                    maxWaitTime)) {
                    setupCase.testSkipped(
                        "cluster didn't reach desired initial state");
                    return;
                }

                // Sometimes we hit IOException....retry in that case
                CmdResult cr = null; 
                for (int l = 1; l <= numtries; l++) {
                    cr = null;
                    try {
                        Log.INFO("Calling store with size " + fileSize);
                        HCTestReadableByteChannel storeChannel =
                            new HCTestReadableByteChannel(fileSize);
                        cr = htc.store(storeChannel, (HashMap)null);
                        Log.INFO("Finished store with size " + fileSize);
                        break;
                    } catch (HoneycombTestException e) {
                        Log.ERROR("Caught Exception: " + e);
                        Log.ERROR(Log.stackTrace(e));

                        // avoid the extra wait if we are going to
                        // exit the loop nexttime
                        if ((l + 1) > numtries) {
                            cr = null;
                            break;
                        }
                        Log.WARN("sleeping " + retrySleep +
                            " and will retry");
                        Thread.sleep(retrySleep);
                        Log.WARN("XXX");
                        Log.WARN("XXX recreate the archive object");
                        Log.WARN("XXX");
                        Log.WARN("Try " + l +
                            " failed, try again with new archive");
                        try {
                            htc = new HoneycombTestClient(dataVIP);
                        } catch (Throwable t) {
                            Log.ERROR("creating new archive failed: " + t);
                            Log.WARN("retry with old archive and retry " +
                                "recreate next iteration");
                        }
                    }

                }
 
                if (cr == null) {
                    setupCase.testSkipped("failed to store due to exceptions" +
                        ", even after " + numtries + " tries");
                    continue;
                }

                String dataoid = cr.dataoid;
                String mdoid = cr.mdoid;
                String dataHash = cr.datasha1;
                Log.INFO("Stored object of size " + fileSize +
                    " as dataoid " + dataoid + ", mdoid " + mdoid);
                
                NewObjectIdentifier newOid = 
                    NewObjectIdentifier.fromHexString(dataoid);
                int layoutMapId = newOid.getLayoutMapId();
                 
                HwStat hwstat = new HwStat(cli.runCommand("hwstat"));
                Log.DEBUG("hwstat parsed:\n" + hwstat);

                // XXX: Per Amber's code review, 'hwstat' really isn't the right way to determine the
                // XXX: disk mask.  Suggestion is to add a cli (hidden) command that prints what
                // XXX: LayoutClient actually believes is the disk mask.
                DiskMask diskMask = hwstat.getDiskMask();
                Layout layout = layoutClient.utGetLayout(layoutMapId, diskMask);

                // Print full object disk mask for reference
                String layoutString = "";
                for (int k = 0; k < HoneycombTestConstants.OA_TOTAL_FRAGS; k++) {
                    DiskId d = (DiskId) layout.get(k);
                    layoutString +=
                        "[" + k + "]=" + d.nodeId() + ":" + d.diskIndex() + " ";
                }
                Log.INFO("oid " + dataoid + " has layout " + layoutString);

                // XXX caching!
                for (int j = 0; j < whichFrags.size(); j++) {
                    HoneycombFragment hcFrag =
                        (HoneycombFragment) whichFrags.get(j);
                    String symbolicFrag = hcFrag.fragname;
                    int frag = hcFrag.fragid;

                    TestCase testCase = new TestCase(this, 
                                                     "retrieve_node_down",
                                                     "fault_type(svcadm),size(" + fsc.name + "),moment(before),chunk_a(1),frag_a(" + symbolicFrag + "),chunk_b(none),frag_b(none)");
                    testCase.addTag(new String[] {"data-op", "retrieve", "node-fault"});
                    if (!testCase.excludeCase()) {
                        try {
                            // Make sure cluster is online fully before starting
                            if (!cliUtil.resetClusterState(offlineNodes,
                                offlineDisks, maxWaitTime)) {
                                testCase.testSkipped("cluster didn't reach " +
                                    "desired initial state");
                                break;
                            }

                            DiskId disk = (DiskId) layout.get(frag);
                            int nodeId = disk.nodeId();
                            int twoDigitNodeId = nodeId - 100;
                            ClusterNode hcNode = new ClusterNode(adminVIP, 
                                twoDigitNodeId);
                            int diskIndex = disk.diskIndex();
                            // 
                            // XXX: using the hwstat cli command to determine the disk mask is not the best approach.  it works for now, but we'd like to swap
                            // in a better solution.  A better solution would be to add a cli command which displays the actual disk mask as told by a LayoutClient.
                            Log.INFO("frag[" + frag + "] is on node " + nodeId +
                                " (cm node " + hcNode + ") disk " + disk);
                            hcNode.stopHoneycomb();

                            // XXX how to get cluster back for next test case?
                            // XXX abort all test cases?
                            if (!cliUtil.waitForNodeOffline(nodeId, maxWaitTime)) {
                                testCase.testSkipped(
                                    "cluster didn't reach desired state");
                                break;
                            }
                            Log.INFO(HCUtil.readLines(cli.runCommand("hwstat")));

                            // XXX does switch reprogramming affect ability to
                            // retrieve...we seem to get a lot of IOExceptions.
                            // Try re-creating the archive....
                            // XXX but shouldn't the client lib handle this?
                            for (int l = 1; l <= numtries; l++) {
                                try {
                                    Log.INFO("Calling retrieve with size " +
                                        fileSize + ", oid " + mdoid);
                                    actionSucceeded = htc.retrieveObject(
                                        mdoid.toString(),
                                        new HCTestWritableByteChannel(
                                        fileSize, testCase),
                                        dataHash);
                                    Log.INFO("Finished retrieve with size " +
                                        fileSize);
                                    break;
                                } catch (HoneycombTestException e) {
                                    Log.ERROR("Caught Exception: " + e);
                                    Log.ERROR(Log.stackTrace(e));

                                    // avoid the extra wait if we are going to
                                    // exit the loop nexttime
                                    if ((l + 1) > numtries) {
                                        Log.ERROR("failed to retrieve due to " +
                                            "exceptions, even after " +
                                            numtries + " tries");
                                        actionSucceeded = false;
                                        break;
                                    }

                                    // check if this is an IOException
                                    Log.WARN("sleeping " + retrySleep +
                                        " and will retry");
                                    Thread.sleep(retrySleep);
                                    Log.WARN("XXX");
                                    Log.WARN("XXX recreate the archive object");
                                    Log.WARN("XXX");
                                    Log.WARN("Try " + l +
                                        " failed, try again with new archive");
                                    try {
                                        htc = new HoneycombTestClient(dataVIP);
                                    } catch (Throwable t) {
                                        Log.WARN("creating new archive failed: "
                                            + t);
                                        Log.WARN("retry with old archive and " +
                                            "retry recreate next iteration");
                                    }
                                }
                            }
                            testCase.postResult(actionSucceeded);

                            hcNode.startHoneycomb();
                            if (!cliUtil.waitForNodeOnline(nodeId, maxWaitTime)) {
                                // XXX how to handle this case?
                                //testCase.postResult(false,
                                //    "cluster didn't reach desired state");
                                //break;
                                Log.ERROR("Ack, cluster didn't get into desired state");
                            }
                            Log.INFO(HCUtil.readLines(cli.runCommand("hwstat")));
                        }
                        catch (Throwable t) {
                            testCase.postResult(false, Log.stackTrace(t));
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
