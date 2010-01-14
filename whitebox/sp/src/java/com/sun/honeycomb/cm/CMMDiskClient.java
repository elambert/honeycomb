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



/**
 *  Set disks online/offline via CMM API. No actual disks are involved.
 *  This can be used to toggle cluster quorum (quorum = 75% disks online).
 */

package com.sun.honeycomb.cm;

import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.CMMException;


public class CMMDiskClient {

    private CMMApi api;
    private int nodeId;
    private int diskCount;

    public CMMDiskClient(int _nodeId) {
        nodeId = _nodeId; // 101,..
        diskCount = -1; // unknown
    }

    /** Sets active disk count on NODE to NUMDISKS.
     *  Returns total active disk count in the cluster.
     */
    public int setOnlineDisks(int numDisks) {
        boolean ok = _getAPI();
        if (ok)
            ok = _setOnline(numDisks);

        int activeDisks = getOnlineDisks(); 
        return activeDisks;
    }

    /** Returns total active disk count in the cluster.
     */
    public int getOnlineDisks() {
        boolean ok = _getAPI();
        if (ok)
            ok = _getOnline();
        return (ok ? diskCount : -1);
    }

    private boolean _getAPI() {
        try {
            api = CMM.getAPI("hcb" + nodeId);
        } catch (Error err) {
            reportErr("Error connecting to CMM API: " + err);
            api = null;
        }
        return (api != null);
    }

    private boolean _setOnline(int numDisks) {
        if (api == null)
            return false;
        try {
            api.setActiveDiskCount(numDisks);
        } catch (CMMException e) {
            reportErr("Error enabling disks: " + e);
            return false;
        }
        return true;
    }

    private boolean _getOnline() {
        if (api == null)
            return false;
        try {
            diskCount = api.getActiveDiskCount();
        } catch (CMMException e) {
            reportErr("Error getting disk count: " + e);
            diskCount = -1;
            return false;
        }
        return true;
    }

    private void reportErr(String errMsg) {
        System.err.println("CMMDiskEnable ERROR on node " + nodeId + ": " + errMsg);
    }

    private static void usage() {
        usage(null);
    }

    private static void usage(String errMsg) {
        if (errMsg != null)
            System.out.println("ERROR: " + errMsg);
        System.out.println("This tool enables specified number of disks on given node via CMM API.");
        System.out.println("If number of disks is not given, the tool prints enabled disk count for the node.");
        System.out.println("CMMDiskEnable -Dnode=<id> [-Ddisks=<num>]");
        System.exit(1);
    }

    public static void main(String[] args) {

        int nodeId = -1;
        int setDisks = -1;
        int gotDisks = -1;
        boolean on;
        
        if (args.length > 0 && args[0].startsWith("-h")) {
            usage();
        }

        try {
            nodeId = Integer.getInteger("node").intValue();
            setDisks = Integer.getInteger("disks", -1).intValue();
        } catch (Exception e) {
            usage("Invalid arg: " + e);
        }

        CMMDiskClient cmm = new CMMDiskClient(nodeId);
        if (setDisks > -1) {
            gotDisks = cmm.setOnlineDisks(setDisks);
            if (gotDisks > -1)
                System.out.println("Disks:" + gotDisks);
            else
                System.exit(1);
        } else {
            gotDisks = cmm.getOnlineDisks();
            if (gotDisks > -1)
                System.out.println("Disks:" + gotDisks);
            else
                System.exit(1);
        }
    }
}
