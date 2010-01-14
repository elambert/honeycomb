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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class SnapshotTool {    
    public static String DO_COPY = "copy";
    public static String DO_MOVE = "move";
    public static String DO_LIVE = "live";
    public static String DO_NO_DB = "nodb";

    private static SnapshotTool _instance = null;
    private static String cluster = null;
    private static CheatNode cheat = null;

    private boolean quiet = true; // to log less command output
     
    private SnapshotTool() throws HoneycombTestException {
        Log.INFO("Checking cheat for SUNWhcwbsp package");
        // Verify SUNWhcwbsp package is installed
        cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        cheat = CheatNode.getDefaultCheat();
        runOnCheat("pkginfo SUNWhcwbsp","Install the SUNWhcwbsp on cheat.", quiet);
    }
    
    public static SnapshotTool getInstance() throws HoneycombTestException {
        if (_instance != null)
            return _instance;
        else
            return (_instance = new SnapshotTool());
    }
    
    /** Restore partial cluster snapshot on specific node and/or disk and/or layout map,
     *  with optional restore of audit DB
     */
    public void restoreSnapshot(String name, String type, String nodeId, 
                                String diskId, String mapId, boolean auditdb) 
        throws HoneycombTestException {
        String node = (nodeId == null) ? "" : nodeId;
        String disk = (diskId == null) ? "" : diskId;
        String map = (mapId == null) ? "" : mapId;
        String dbname = auditdb ? cluster : DO_NO_DB;
        runOnCheat("/bin/yes | /opt/test/bin/snapshot.sh restore " + dbname + 
                   " " + name + " " + type + " " + node + " " + disk + " " + map, 
                   "Restore of snapshot " + name + " failed.");
    }

    /** Restore full snapshot of cluster data and audit DB
     */
    public void restoreSnapshot(String name, String type) throws HoneycombTestException {
        restoreSnapshot(name, type, null, null, null, true);
    }

    /** Take partial cluster snapshot of specific node and/or disk and/or layout map,
     *  with optional snapshot of audit DB
     */
    public void saveSnapshot(String name, String type, String nodeId, 
                             String diskId, String mapId, boolean auditdb) 
        throws HoneycombTestException {
        String node = (nodeId == null) ? "" : nodeId;
        String disk = (diskId == null) ? "" : diskId;
        String map = (mapId == null) ? "" : mapId;
        String dbname = auditdb ? cluster : DO_NO_DB;
        runOnCheat("/bin/yes | /opt/test/bin/snapshot.sh save " + dbname + 
                   " " + name + " " + type + " " + node + " " + disk + " " + map, 
                   "Saving of snapshot " + name + " failed.");
    }

    /** Take full snapshot of cluster data and audit DB
     */
    public void saveSnapshot(String name, String type) throws HoneycombTestException {
        saveSnapshot(name, type, null, null, null, true);
    }
    
    /** Delete snapshot with given name from the cluster, and matching snapshot of audit DB.
     * 
     *  This method has no "partial" version: 
     *  you delete the snapshot from all places where it exists, incl. audit DB.
     */
    public void deleteSnapshot(String name) throws HoneycombTestException {
        runOnCheat("/bin/yes | /opt/test/bin/snapshot.sh delete " + cluster + 
                   " " + name, "Deleting of snapshot " + name + " failed.");
    }

    /** Delete data on the cluster on specific node and/or disk and/or layout map,
     *  optionally drop and recreate cluster's audit DB. 
     *     
     *  Note: you most likely do NOT want to blow away audit DB unless 
     *  you're deleting all cluster data, so call this method with auditdb=false.
     */
    public void deletedata(String nodeId, String diskId, String mapId, boolean auditdb) 
        throws HoneycombTestException {
        String node = (nodeId == null) ? "" : nodeId;
        String disk = (diskId == null) ? "" : diskId;
        String map = (mapId == null) ? "" : mapId;
        String dbname = auditdb ? cluster : DO_NO_DB;
        runOnCheat("/bin/yes | /opt/test/bin/snapshot.sh deletedata " 
                   + dbname + " " + node + " " + disk + " " + map, 
                   "Data clean up failed.");
    }

    /** Delete data on the whole cluster, drop and recreate audit DB.
     *  Delete != wipe, it's much faster because HC doesn't need to mkfs afterwards.
     */
    public void deletedata() throws HoneycombTestException {
        deletedata(null, null, null, true);
    }
    
    /** Partially verify cluster data against snapshot on given node and/or disk and/or layout map
     */
    public void verifyDataAgainstSnapshot(String name, String nodeId, String diskId, String mapId) 
        throws HoneycombTestException {
        String node = (nodeId == null) ? "" : nodeId;
        String disk = (diskId == null) ? "" : diskId;
        String map = (mapId == null) ? "" : mapId;
        runOnCheat("/bin/yes | /opt/test/bin/verify_snapshot.sh " + name + 
                   " " + node + " " + disk + " " + map, 
                   "Data verification against snapshot " + name + " failed.");
    }

    /** Verify cluster data against snapshot (check that cluster data contains ALL snapshot data)
     */
    public void verifyDataAgainstSnapshot(String name) throws HoneycombTestException {
        verifyDataAgainstSnapshot(name, null, null, null);
    }

    /** Run given command on the cheat node, fail with given message on error
     */
    public int runOnCheat(String command, String onFailureMsg, boolean quiet)
        throws HoneycombTestException {
        int result = cheat.runCmdAndLog(command, quiet);
        if (result != 0) throw new HoneycombTestException(onFailureMsg);
        return result;
    }
    
    public int runOnCheat(String command, String onFailureMsg) throws HoneycombTestException {
        return runOnCheat(command, onFailureMsg, false); // not quiet, verbose
    } 
}
