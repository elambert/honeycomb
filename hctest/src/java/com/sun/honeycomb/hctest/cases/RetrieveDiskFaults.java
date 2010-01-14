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

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.cases.interfaces.HCFileSizeCases;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.cli.HwStat;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;

import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.Log;

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.client.QAClient;

import com.sun.honeycomb.common.NewObjectIdentifier;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutConfig;

import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;

public class RetrieveDiskFaults extends Suite
{
    public RetrieveDiskFaults() {}

    public void setUp()
    {
    }

    public void runTests()
    {
        try {
            ArrayList reconTagSet = new ArrayList();
            reconTagSet.add("data-op");
            reconTagSet.add("retrieve");
            reconTagSet.add("disk-disable");
            reconTagSet.add("reed-solomon");
            Run run = Run.getInstance();
            if (!run.isTagSetActive(reconTagSet)) {
                Log.INFO("skipping RetrieveDiskFaults");
                return;
            }

            String cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
            NameValueObjectArchive archive = new TestNVOA(cluster + "-data");
            Audit audit = Audit.getInstance(cluster);
            
            ArrayList fileSizes = HCFileSizeCases.getRetrieveReconSizes();
            for (int i = 0; i < fileSizes.size(); i++) {
                long fileSize = ((Long) fileSizes.get(i)).longValue();
                ReadableByteChannel storeChannel = new HCTestReadableByteChannel(fileSize);
                SystemRecord systemRecord = archive.storeObject(storeChannel);
                ObjectIdentifier linkOid = QAClient.getLinkIdentifier(systemRecord);
                ObjectIdentifier oid = systemRecord.getObjectIdentifier();
                
                NewObjectIdentifier newOid = NewObjectIdentifier.fromHexString(linkOid.toString());
                int layoutMapId = newOid.getLayoutMapId();
                LayoutClient layoutClient = LayoutClient.getInstance();
                
                CLI cli = new CLI(cluster + "-admin");
                HwStat hwstat = new HwStat(cli.runCommand("hwstat"));
                // XXX: Per Amber's code review, 'hwstat' really isn't the right way to determine the
                // XXX: disk mask.  Suggestion is to add a cli (hidden) command that prints what
                // XXX: LayoutClient actually believes is the disk mask.
                
                for (int frag = 0; frag < HoneycombTestConstants.OA_TOTAL_FRAGS; frag++) {
                    int num_chunks = (int) (fileSize / HoneycombTestConstants.OA_MAX_CHUNK_SIZE); 
                    int chunk = num_chunks;
                    if ((fileSize != 0) && 
                        (fileSize % HoneycombTestConstants.OA_MAX_CHUNK_SIZE) == 0) {
                        chunk--;
                    }
                    while (chunk >= 0) {
                        TestCase testCase = new TestCase(this, 
                                                         "retrieve-disk-disabled",
                                                         "size(" + Long.toString(fileSize) + "),chunk_a(" + Integer.toString(chunk) + "),frag_a(" + Integer.toString(frag) + ")");
                        testCase.addTags(reconTagSet);
                        if (!testCase.excludeCase()) {
                            try {
                                int mapId = layoutMapId + chunk;
                                DiskMask diskMask = hwstat.getDiskMask();
                                Layout layout = layoutClient.utGetLayout(mapId, diskMask);
                                DiskId disk = (DiskId) layout.get(frag);
                                int nodeId = disk.nodeId();
                                int diskIndex = disk.diskIndex();
                                // 
                                // XXX: using the hwstat cli command to determine the disk mask is not the best approach.  it works for now, but we'd like to swap
                                // in a better solution.  A better solution would be to add a cli command which displays the actual disk mask as told by a LayoutClient.
                                Log.INFO(HCUtil.readLines(cli.runCommand("hwcfg -F DISK-" + Integer.toString(nodeId) + ":" + Integer.toString(diskIndex) + " --disable")));
                                Thread.sleep(1000*30);
                                Log.INFO(HCUtil.readLines(cli.runCommand("hwstat")));
                                
                                testCase.postResult(audit.auditObject(archive, oid.toString()));
                                
                                Log.INFO(HCUtil.readLines(cli.runCommand("hwcfg -F DISK-" + Integer.toString(nodeId) + ":" + Integer.toString(diskIndex) + " --enable")));
                                Thread.sleep(1000*30);
                                
                                Log.INFO(HCUtil.readLines(cli.runCommand("hwstat")));
                            }
                            catch (Throwable t) {
                                testCase.postResult(false, Log.stackTrace(t));
                            }
                        }
                        chunk--;
                    }
                }
            }
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
