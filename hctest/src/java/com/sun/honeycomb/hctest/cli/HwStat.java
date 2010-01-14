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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.testcmd.common.HoneycombTestException;






/*
05/15/06 18:39:53 INF: TestNVOA::storeObject() sha1(8ace90d11b62d53c28cdd0e1735ab91ae2cd3179) numBytes(32768) tag [cl8:main:1147743590224] oid 010001d9a0c333e47c11daae1a00e0815970e4000016620200000000
05/15/06 18:39:53 INF: TestNVOA::storedObject() [cl8:main:1147743590224] request succeeded.
05/15/06 18:39:54 INF: Stored: 010001d96d7e32e47c11daae1a00e0815970e4000008b30100000000 (size=32768; mdoid 010001d9a0c333e47c11daae1a00e0815970e4000016620200000000)
05/15/06 18:39:54 INF: Deleting and healing filesize 32768 (dataoid 010001d96d7e32e47c11daae1a00e0815970e4000008b30100000000) with 1 chunks
 
05/15/06 18:39:54 INF: -------------> Delete 2 fragments: 1 and 0 in chunk: 0 of oid: 010001d96d7e32e47c11daae1a00e0815970e4000008b30100000000 of size: 32768
May 15, 2006 6:39:54 PM com.sun.honeycomb.common.UID <clinit>
WARNING: Loading UID libraries from [/opt/test/lib]
05/15/06 18:39:54 INF: CLI::runCommand(hwstat -v)
05/15/06 18:39:55 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:39:55 INF: CLI::runCommand(hwstat -v)
05/15/06 18:39:57 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:39:57 INF: CLI::runCommand(hwstat -v)
05/15/06 18:39:58 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:39:58 INF: CLI::runCommand(hwstat -v)
05/15/06 18:40:00 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:40:00 INF: CLI::runCommand(hwstat -v)
05/15/06 18:40:02 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:40:02 INF: CLI::runCommand(hwstat -v)
05/15/06 18:40:03 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:40:03 INF: CLI::runCommand(hwstat -v)
05/15/06 18:40:05 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:40:05 INF: CLI::runCommand(hwstat -v)
05/15/06 18:40:06 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:40:06 INF: CLI::runCommand(hwstat -v)
05/15/06 18:40:08 ERR: Cannot get disk mask from CLI; retrying: java.lang.RuntimeException: Failed to properly parse hwstat output: found only 7 nodes, expected 16
05/15/06 18:40:08 INF: CLI::runCommand(hwstat -v)

 */
public class HwStat
{
    private ArrayList nodes;
    
    public HwStat(BufferedReader output)
        throws IOException, HoneycombTestException
    {
        this.nodes = new ArrayList();

        String line = null;
        int node_i = -1;
        int disk_id = 0; 

        NodeStat nodeStat = null;
        while ((line = output.readLine()) != null) {
	    try {
		Log.DEBUG(line);
                // The fru name always starts with the fru name so use it to
                // match our line
		if (line.trim().length() == 0) {
		    // Blank line in output - ignore	
		} else if (line.startsWith(FruInfo.FRU_TYPE_NODE)) {
		    node_i++;
		    nodeStat = new NodeStat(line);
		    nodes.add(nodeStat);
		    disk_id = 0;
		} else if (line.startsWith(FruInfo.FRU_TYPE_DISK)) {
		    if (nodeStat == null)
			throw new HoneycombTestException("DISK data came before NODE");
		    nodeStat.addDisk(disk_id++, new DiskStat(line));
                } else if (line.startsWith(FruInfo.FRU_TYPE_SWITCH)) {
                    // no-op
                } else if (line.startsWith(FruInfo.FRU_TYPE_SP)) {
                    // no-op
		} else if(line.startsWith("Component") || line.startsWith("---------")) {
		    // no-op
		} else {
		    Log.WARN("Unexpected output from hwstat: " + line);
		}
	    }
	    catch (HoneycombTestException hte) {
		throw hte;
	    }
	    catch (Exception e) {
		throw new HoneycombTestException("Unexpected error HwStat<constructor> ", e);
	    }
        }
        output.close();
    }

    public DiskMask getDiskMask()
    {
        int disksPerNode = HoneycombTestConstants.DISKS_PER_NODE;
        DiskMask diskMask = new DiskMask();
        for (int i = 0; i < nodes.size(); i++) {
            NodeStat node = (NodeStat)nodes.get(i);
            if (node.isInCluster) {
                for (int j = 0; j < node.disks.length; j++) {
                    DiskStat disk = node.disks[j];
                    if (disk.isEnabled && disk.diskId !=
                        DiskStat.INVALID_DISK) {
                        diskMask.setOnline(node.nodeId, disk.diskId);
                    }
                    else if (!disk.isEnabled && disk.diskId !=
                        DiskStat.INVALID_DISK) {
                        diskMask.setOffline(node.nodeId, disk.diskId);
                    }
                }
            }
        }
        return diskMask;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < this.nodes.size(); i++) {
            NodeStat node = (NodeStat)this.nodes.get(i);
            sb.append(node.toString());
            sb.append("\n");

            for (int j = 0; j < node.disks.length; j++) {
                DiskStat disk = node.disks[j];
                if (disk != null) {
                    sb.append(disk.toString());
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    public static void main(String [] args) 
        throws Throwable
    {
        String adminVIP = args[0];
        CLI cli = new CLI(adminVIP);
        HwStat hwstat = new HwStat(cli.runCommand("hwstat"));
        System.out.println(hwstat.toString());
    }

    public ArrayList getNodes() {
        return nodes;
    }

}
