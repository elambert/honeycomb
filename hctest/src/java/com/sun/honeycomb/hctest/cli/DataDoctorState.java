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


import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;

import com.sun.honeycomb.hctest.suitcase.OutputReader;
import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.cases.interfaces.HCFileSizeCases;
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.hctest.cli.HwStat;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.CheatNode;

import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.HoneycombTestException;

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.TestNVOA;

import com.sun.honeycomb.common.OIDFormat;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.protocol.server.ProtocolService;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;

import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.oa.FragmentFooter;

import java.lang.Runtime;
import java.nio.channels.ReadableByteChannel;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
/*
  There may b many tests that want to alter the state of the datadoctor.
  When we get around to running in parallel (Trash fragments + load, or 
  possibly trash fragments + performance) we want to ensure that we don't
  have several tests trying to tell the data doctor conflicting things.
  This will be the single point of access for altering the datadoctor state.
*/

// Singleton me
// enum for state names
// "all" accessor. 
// Somehow grab/reinstate run times when we re-enable.






public class DataDoctorState {
    private static DataDoctorState instance=null;


    //
    //
    // FIXME:
    // This is somewhat lame
    /*  
        I've added a new hidden "convenience" command for setting the
        DataDoctor cycle targets, to use it then add this to your config,
        (or get the cluster_config.properties from the trunk)
        
        honeycomb.hcsh.cmd.name.CommandDataDocConfig = ddcfg
        honeycomb.hcsh.cmd.name.CommandDataDocConfig.isHidden = true
        
        here is the usage:
        ddcfg < off | fullspeed | default >
        
*/


    private CLIState cli;

    // XXX Add checksum block section
    // XXX Add data + checksum secction
    public static final int FIRST_BYTE=0;
    public static final int CORRUPT_FIRST_FRAGMENT=1;
    public static final int CORRUPT_LAST_FRAGMENT=2;
    public static final int CORRUPT_MID_FRAGMENT=3;
    public static final int CORRUPT_BOUNDARY=4;
    public static final int TRUNCATE_TWO=5;
    public static final int LAST_BYTE=6;
    public static final int CORRUPT_FOOTER=7;
    public static final int MAX_CORRUPT=5;

    public static final String TESTDIR = "HCTEST";
    private static final int SSHRETRIES=20;
    private static int sshRetryCount=SSHRETRIES;

    private static String cheatIP;

    private DataDoctorState() {
        //
        // Nasty little hack supports the NewObjectIdentifier interface.
        //
        ProtocolService.CELL_ID=1;        
        cli = CLIState.getInstance();
        cheatIP = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_SP_IP_ADDR);
    }

    class FragLocation {

        String oid;
        int fragId;
        int chunkId;
        int nodeId;
        int layoutId;
        Layout layout;
        String path;
        DiskId disk;
  
        public FragLocation(String oid, int fragId, int chunkId) 
                                 throws HoneycombTestException {
            this.oid = oid;
            this.fragId = fragId;
            this.chunkId = chunkId;

            path = getPath(oid, fragId, chunkId);

            OIDFormat newOid = new OIDFormat(oid, true);
            newOid = newOid.getOidForChunk(chunkId);
            layoutId = newOid.getLayoutMapId();       
            layout = getLayout(layoutId);        
            disk = (DiskId) layout.get(fragId);
            nodeId = disk.nodeId();
        }

        public String toString() {
            return 
                "frag=" + fragId + " of chunk=" + chunkId + " of oid=" + oid +
                " stored on layout=" + layoutId + " [" + layout + "]" + 
                " on " + nodeId + ":" + path; 
        }

        public ClusterNode getNode() {
            CheatNode cheat = new CheatNode(cheatIP);
            return new ClusterNode(cli.getAdminVIP(), cheat, (nodeId-100));
        }

        /** Where should this fragment be located, if current disk fails?
         */
        public DiskId getBackupLocation() {

            // let's pretend the disk where our frag resides failed
            DiskMask newMask = (DiskMask) _diskMask.clone();
            newMask.setOffline(disk.nodeId(), disk.diskIndex());
            
            // which disk serves as backup for our "failed" disk?
            Layout newLayout = LayoutClient.getInstance().utGetLayout(layoutId, newMask);
            DiskId newDisk = (DiskId) newLayout.get(fragId);
            
            return newDisk;
        }
    }

    public void startCycle(CLIState state) throws HoneycombTestException {
        cli.startDDCycle(state);
    }
    public void stopCycle(CLIState state) throws HoneycombTestException {
        cli.stopDDCycle(state);
    }

    
    public void setValue(CLIState state,int value) {
        cli.setIntValue(state, value);
    }

    //
    // Note - this does NOT interrogate the cluster; it's
    // tracked internally. That's da point - if two close to
    // simultaneous processes wanna do the same thing, we only
    // take action if the requested state differs from what we
    // believe the state to be.
    //
    public int getValue(CLIState state) throws HoneycombTestException {
        return cli.getIntValue(state);
    }
    

    //
    // Testing
    //
    public void dumpState() {
        cli.dumpState();
    }
    public void syncState() throws HoneycombTestException {
        cli.syncState();
    }

    public void disable() {
        cli.disableDD();
    }

    public void fastest() {
        cli.fastestDD();
    }
  
    public void enable() throws HoneycombTestException {
        cli.enableDD();
    }
    private String nodeNumber(int node) {
        String nodeNumber=""+node;
        return nodeNumber;
        /*
        String nodeNumber;
        if(node<=9) {
            nodeNumber="10"+node;
        } else {
            nodeNumber="1"+node;
        }
        return nodeNumber;
        */
    }
    public String runCommand(int node, String runCommand) {
        return runCommand(node,runCommand, false);
    }
    public String runCommand(int node, String runCommand,boolean displayOutput) {
        // ssh to the cheat to run the command on the node using IPs
        // ssh 10.7.224.140 -o StrictHostKeyChecking=no -l root "ssh 10.123.45.102 -o  StrictHostKeyChecking=no uname -a"
        String lastLine=null;
        String lastErrLine=null;
        String nodeName=HoneycombTestConstants.BASE_IP + nodeNumber(node);
        String command="ssh "+nodeName+" -o  StrictHostKeyChecking=no " + runCommand;
        String [] args = new String [] {
            "/usr/bin/ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "-l",
            "root",
            cheatIP,
            command
        };
        Log.INFO("DataDoctorState::runCommand(" + command + ")");            
        //
        // TODO: make this retry
        //

        try {

            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(args);
            try {
                BufferedReader results,error;
                
                // true indicates to use the log instead of stdout
                    OutputReader errReader= new OutputReader( process.getErrorStream(),!displayOutput, true);
                    OutputReader stdReader = new OutputReader( process.getInputStream(),!displayOutput, true);
                    process.waitFor();
                    lastLine=stdReader.getLastLine();
                    lastErrLine=errReader.getLastLine();
            } catch (InterruptedException e) {
                Log.ERROR("error in ssh command " + command + ": " + e.toString());
                lastLine=e.toString();
            }
            
            if (process.exitValue() != 0) {
                // Grr...sometimes you just see ssh errors...                
                throw new RuntimeException("Non-zero exit status of "
                          + process.exitValue() + " for command: " + command +
                          "; Last line of stdout " + lastLine +
                          "; Last line of stderr " + lastErrLine);
                                
            } 
        } catch (IOException e) {
            Log.ERROR("IO error attempting command:"+ command);
            Log.ERROR("error reported: " + e.toString());
            lastLine=e.toString();
        }
        return lastLine;
    }

    //
    // Uses current disk mask - ensure it's current!
    //
    DiskMask _diskMask = null;
    public Layout getLayout (int layoutNumber) throws HoneycombTestException {
        if(null==_diskMask) {
            boolean failed=true;
            int retries;
            for (retries=10;retries > 0 && true == failed; retries--) {
                try {
                    _diskMask = cli.getDiskMask();
                    failed=false;
                } catch (Throwable e) {
                    Log.ERROR("Cannot get disk mask from CLI; retrying: " + 
                            Log.stackTrace(e));
                }
            }
            if(0 == retries && true == failed) {
                throw new HoneycombTestException(
                        "Cannot get disk mask from CLI; fatal(1.1), exiting.");
            }
                        
        }

        return(LayoutClient.getInstance().utGetLayout(layoutNumber, _diskMask));
    }

    //
    // Uses current disk mask if we habe one; gets one if not.
    // if the disk mask has changed, you're SOL.
    //
    public Layout getLayout(String oid) throws HoneycombTestException {
        OIDFormat newOid = new OIDFormat(oid, true);
        int layoutMapId = newOid.getLayoutMapId();

        LayoutClient layoutClient = LayoutClient.getInstance();
                
        //
        // Don't want to be doing this all the time - it requires talking
        // to the CLI.
        // 
        if(null==_diskMask) {
            // XXX: Per Amber's code review, 'hwstat' really isn't the right way to determine the
            // XXX: disk mask.  Suggestion is to add a cli (hidden) command that prints what
            // XXX: LayoutClient actually believes is the disk mask.
            boolean failed=true;
            int retries;
            for (retries=10;retries > 0 && true == failed; retries--) {
                try {
                    _diskMask = cli.getDiskMask();
                    failed=false;
                } catch (Throwable e) {
                    Log.ERROR ("Cannot get disk mask from CLI; retrying: " + 
                            Log.stackTrace(e));
                }
            }
            if(0 == retries && true == failed) {
                throw new HoneycombTestException(
                        "Cannot get disk mask from CLI; fatal,(1.0) exiting.");
            }
                        

        }
        
        return layoutClient.utGetLayout(layoutMapId, _diskMask);
    }

    private String getRemotePath(int node, String path) {
        return "hcb" + node + ":" + path;
    }

    /** Convert oid/frag/chunk into file path on disk where it's stored
     */
    public String getPath(String dataOid, int fragment, int chunk) 
                                   throws HoneycombTestException {

        // get disk ID where this fragment is stored
        
        OIDFormat newOid = new OIDFormat(dataOid, true);
        newOid = newOid.getOidForChunk(chunk);
        int layoutNumber=newOid.getLayoutMapId();
        Layout layout = getLayout(layoutNumber);        
        String layoutString=(new Integer(layoutNumber)).toString();

        DiskId disk = (DiskId) layout.get(fragment);
        int node = disk.nodeId();
        int diskNumber = disk.diskIndex();

        return getPath(dataOid, fragment, chunk, diskNumber);
    }

    /** Convert oid/frag/chunk into file path on given disk
     */
    public String getPath(String dataOid, int fragment, 
                          int chunk, int diskNumber) 
                          throws HoneycombTestException {

        String path="/data/"+diskNumber+"/";

        // get layout directory names XX/YY

        OIDFormat newOid = new OIDFormat(dataOid, true);
        newOid = newOid.getOidForChunk(chunk);
        int layoutNumber=newOid.getLayoutMapId();
        String internalOid=newOid.toString();

        Layout layout = getLayout(layoutNumber);        
        String layoutString=(new Integer(layoutNumber)).toString();
        int zeroCount=4-layoutString.length();
        int zeroRemainingCount=zeroCount;

        for (int charCount=0;charCount < 4;charCount++){
            if(zeroRemainingCount > 0) {
                path += "0";
                zeroRemainingCount--;                           
            } else {
                path += layoutString.substring(charCount-zeroCount,(charCount-zeroCount)+1);                
            }
            if(charCount==1 || charCount == 3) {
                path += "/";
            }
        }
        
        path=path+internalOid+"_"+fragment;
        return path;
    }


    //
    // The next handful of routines are for copying a fragment with the
    // goal of verifying its contents against the fragment that is 
    // recovered by data doctor/OA, since we've seen places where
    // this level of validation is required (since a retrieve might
    // succeed even if the headers are a bit off).
    //

    // Make a backup of the frag.  Sometimes, we really want to remove the frag
    // for the test which means we can use mv to both remove and make a backup.
    // This is simply an optimization.
    public void makeFragBackup(int node, String path, boolean useMove) {
        Log.INFO("Making a backup of " + node + ":" + path);
        String cmd;

        if (useMove) {
            cmd = "mv ";
        } else {
            cmd = "cp -p ";
        }

        // We might not have created the testdir for backup frags,
        // so catch any exception, try to make the directory,
        // and then retry the command.
        try {
            runCommand(node, cmd + path + " " + getBackupPath(path));
            return; // command succeeded, we are done
        } catch (Throwable t) {
            Log.INFO(cmd + " failed.  Trying mkdir on testdir (if this works, no action required)...");
        }

        makeTestBackupDir(node, path);

        // if we fail this time, we can't do anything
        runCommand(node, cmd + path + " " + getBackupPath(path));
    }

    public void removeFragBackup(int node, String path) {
        Log.INFO("Removing backup of " + node + ":" + path);
        runCommand(node, "rm " + getBackupPath(path));
    }

    // After recovery, make sure the orig frag matches the generated frag
    public void compareFragBackup(int node, String path) {
        Log.INFO("Comparing backup of " + node + ":" + path);
        runCommand(node, "diff " + path + " " + getBackupPath(path));
    }

    // Where to copy or move the backup file to.
    // Note that data doctor gets sad if it discovers files it doesn't
    // expect while crawling around (see bug 6379946)
    // So, we put this in /data/<n>/HCTEST/<fragname>
    public String getBackupPath(String path) {
        // we are given something that looks like this:
        //  /data/3/03/45/7523df77-8fa2-11da-83a6-00e081598478.1.1.1.0.345_0
        // we change this to something that looks like this:
        //  /data/3/HCTEST/7523df77-8fa2-11da-83a6-00e081598478.1.1.1.0.345_0

        return (getTestBackupDir(path) + path.substring(13));
    }

    public void makeTestBackupDir(int node, String path) {
        runCommand(node, "mkdir -p " + getTestBackupDir(path));
    }

    public String getTestBackupDir(String path) {
        return (path.substring(0, 8) + TESTDIR);
    }

    public static String getCorruptionTypeString(int corruptType) {
        switch (corruptType) {
          case FIRST_BYTE:
            return ("FIRST_BYTE");
          case CORRUPT_FOOTER:
            return ("CORRUPT_FOOTER");
          case CORRUPT_FIRST_FRAGMENT:
            return ("CORRUPT_FIRST_FRAGMENT");
          case CORRUPT_LAST_FRAGMENT:
            return ("CORRUPT_LAST_FRAGMENT");
          case CORRUPT_MID_FRAGMENT:
            return ("CORRUPT_MID_FRAGMENT");
          case CORRUPT_BOUNDARY:
            return ("CORRUPT_BOUNDARY");
          case LAST_BYTE:
            return ("LAST_BYTE");
          case TRUNCATE_TWO:
            return ("TRUNCATE_TWO");

          default:
            throw new RuntimeException("unknown corrupt type " + corruptType);
        }
    }

    public boolean corrupt(String dataOid, int fragment, int chunk, int section) 
                        throws HoneycombTestException {
        //
        // Note - FragmentScanner.java will ultimatley treat "fragmentFooter"
        //        differently from the rest of the fragment. 
        //        However, that's currently commented out - see
        //        "scanFragment" in FragmentScanner.java.
        //        if repairFragmentFooter gets uncommented, we're ready to go.
        //
        stopIt("scan_frags_cycle",CLIState.SCAN_FRAGS);

        String path=getPath(dataOid,fragment,chunk);

        OIDFormat newOid = new OIDFormat(dataOid, true);
        newOid = newOid.getOidForChunk(chunk);
        int layoutNumber=newOid.getLayoutMapId();       
        Layout layout = getLayout(layoutNumber);        
        DiskId disk = (DiskId) layout.get(fragment);
        int node = disk.nodeId();

        // First copy the frag so we can validate it in target()
        // (false means use cp and not mv)
        makeFragBackup(node, path, false);

        // 
        // Get filesize
        //
        String size = runCommand(node,"ls -l " + path + " | awk '{print $5}'");

        if (size == null || size.equals("")) {
            Log.ERROR("couldn't get size for " + path + "; got " + size);
            return (false);
        }

        // int is okay because fragsize is < 1G
        int fragSize = -1;
        try {
            fragSize = fragSize=Integer.parseInt(size);
        } catch (NumberFormatException e) {
            Log.ERROR("couldn't translate size " + size + " for " + path);
            return (false);
        }

        // XXX how to avoid corrupting the pad???
        long seekTo = 0;
        long corruptSize = 10;  // XXX vary this
        if (section == CORRUPT_FOOTER) {
            seekTo = fragSize - FragmentFooter.SIZE + 1;
        } else if (section == CORRUPT_FIRST_FRAGMENT) {
            seekTo = 100;
            // Try to do something for small files
            if ((seekTo + corruptSize) > fragSize) {
                seekTo = 0;
                corruptSize = fragSize - FragmentFooter.SIZE;
            }
        } else if (section == CORRUPT_LAST_FRAGMENT) {
            seekTo = fragSize - (FragmentFooter.SIZE + 100);
            // this won't work for 0 byte file, but we'll catch it later
            // but otherwise try to get the last byte of the file
            if (seekTo < 0) {
                seekTo = fragSize - (FragmentFooter.SIZE + 1);
                corruptSize = 1;
            }
        } else if (section == CORRUPT_MID_FRAGMENT) {
            //
            // This is cheezy.
            //
            seekTo = fragSize / 2;
            corruptSize = 100;
            if(seekTo <= FragmentFooter.SIZE)
                seekTo = -1;
        } else if (section == CORRUPT_BOUNDARY) { 
            seekTo = fragSize - FragmentFooter.SIZE - 1;
            corruptSize = 3;
        } else if (section == LAST_BYTE) {
            seekTo = fragSize - 1;
            corruptSize = 1;
        } else if (section == FIRST_BYTE) {
            seekTo = 0;
            corruptSize = 1;
        } else if (section == TRUNCATE_TWO) {
            Log.INFO("Truncating to a filesize of 2 bytes.");
            runCommand(node,"echo -n kilroy was here \\| dd of=corruptme  seek=1 bs=1 count=1");
            
        } else {
            throw new HoneycombTestException("Invalid corruption type passed.");
        }
        //
        // Corrupt correct "section" of fragment
        //
        // From the test plan:
        // (header, footer, checksum boundaries, checksums themselves, etc)

        // 132 (bloom) + 4 (ftr. checksum) + 236 (all else)
        //         OAClient.OA_FRAGMENT_SIZE;

        if (seekTo < 0 || (seekTo + corruptSize) > fragSize) {
            Log.INFO ("Fragment of size " + fragSize +
                      " too short to perform that kind of corruption.  seek=" +
                      seekTo + "; corruptSize=" + corruptSize);
            return false;
        }

        Log.INFO("Corrupting " + getCorruptionTypeString(section) + " of " +
                 node + ":" + path + " at byte " + seekTo +
                 " for " + corruptSize + " bytes of frag of size " + size +
                 " (frag " + fragment + " of chunk " + chunk + " of layout " +
                 layoutNumber + " " + layout + ")");

        runCommand(node,"echo -n kilroy was here \\| dd of=" + 
                   path +
                   " conv=notrunc seek="+
                   seekTo+
                   " bs=1 count="+
                   corruptSize);
        return true;
    }

    private int getDDValue(CLIState state) throws HoneycombTestException {
        return cli.getDDValue(state);
    }

    public void stopIt(String propName, CLIState state) 
                        throws HoneycombTestException {
        cli.stopItDD(propName, state);
    }


    /** Delete (hide) given fragment
     */
    public void delete(String dataOid, int fragment, int chunk) 
                                throws HoneycombTestException {

        //
        // Put some locking here so we don't hit race conditions.
        //

        stopIt("recover_lost_frags_cycle",CLIState.LOST_FRAGS);

        FragLocation frag = new FragLocation(dataOid, fragment, chunk);

        // hide the frag so we can validate it in target()
        boolean moveFrag = true;
        makeFragBackup(frag.nodeId, frag.path, moveFrag);
        Log.INFO("Deleted (via mv) " + frag);
    }

    /** Copy given fragment to all remote disks; used in healback tests
     */
    public void makeRemoteFragBackups(String dataOid, int fragment, int chunk) 
                                               throws HoneycombTestException { 

        FragLocation frag = new FragLocation(dataOid, fragment, chunk);

        Log.INFO("Making remote copies of " + frag);

        // Copy fragment to all remote nodes/disks
        int nodes = 100 + cli.getNumNodes();
        for (int n=101; n <= nodes; n++) {
            // we never place fragment onto the same node in recovery,
            // so no need to copy it to other local disks
            if (n == frag.nodeId)
                continue; 

            // Copy to all disks on remote node, in the right layout dir
            // XXX: this is slow. TODO: parallelize.
            for (int d=0; d <= 3; d++) {
                String remotePath = getRemotePath(n, getPath(dataOid, fragment, chunk, d));
                copyFragment(frag.getNode(), frag.path, remotePath);
            }
        }
    }

    /** Copy given fragment to its backup disk; used in healback tests
     */
    public void makeOneRemoteFragBackup(String dataOid, int fragment, int chunk)
        throws HoneycombTestException {
        
        FragLocation frag = new FragLocation(dataOid, fragment, chunk);
        DiskId newDisk = frag.getBackupLocation();
        
        // destination path
        int node = newDisk.nodeId();
        int disk = newDisk.diskIndex();
        String remotePath = getRemotePath(node, getPath(dataOid, fragment, chunk, disk));
        
        Log.INFO("Making remote copy of " + frag + " to " + remotePath);

        copyFragment(frag.getNode(), frag.path, remotePath);
    }

    /** Delete given fragment from its backup disk; used in healback tests
     */
    public void deleteOneRemoteFragBackup(String dataOid, int fragment, int chunk)
        throws HoneycombTestException {

        FragLocation frag = new FragLocation(dataOid, fragment, chunk);
        DiskId newDisk = frag.getBackupLocation();
        
        // destination path
        int node = newDisk.nodeId();
        int disk = newDisk.diskIndex();
        String remotePath = getPath(dataOid, fragment, chunk, disk);
        
        Log.INFO("Deleting remote copy of " + frag + " from node " + node + " path " + remotePath);
        
        deleteFragment(node, remotePath);
    }

    /** Helper method to copy fragment from this to remote location
     */
    private void copyFragment(ClusterNode node, String path, String remotePath) {
        int rc = 0;
        try {
            boolean quiet = true; // set to false for extra logging
            rc = node.runCmdAndLog("scp -o StrictHostKeyChecking=no " 
                                   + path + " " + remotePath, quiet);
        } catch (HoneycombTestException e) {
            Log.ERROR("RunCmd failed: " + e.toString());
            rc = -1;
        }
        if (rc != 0) {
            String errmsg = "Failed to copy frag to " + remotePath;
            Log.ERROR(errmsg);
            throw new RuntimeException(errmsg);
        }
    }

    /** Helper method to delete fragment from remote location
     */
    private void deleteFragment(int node, String remotePath) {
        ClusterNode remoteNode = new ClusterNode(cli.getAdminVIP(), (node-100));
        int rc = 0;
        try {
            boolean quiet = true; // set to false for extra logging
            rc = remoteNode.runCmdAndLog("rm -f " + remotePath, quiet);
        } catch (HoneycombTestException e) {
            Log.WARN("Failed to delete remote frag backup on node=" + node +
                     " path " + remotePath + " -- " + e.toString());
        }
        if (rc != 0) {
            Log.WARN("Failed to delete remote frag backup on node=" + node + " path " + remotePath);
        }        
    }

    /** Undo the makeRemoteFragBackups operation (used in healback tests for cleanup)
     */
    public void deleteRemoteFragBackups(String dataOid, int fragment, int chunk) 
                                                 throws HoneycombTestException { 

        FragLocation frag = new FragLocation(dataOid, fragment, chunk);

        Log.INFO("Removing remote copies of " + frag);

        // Delete fragment from all remote nodes/disks where it had been copied to
        int nodes = 100 + cli.getNumNodes();
        for (int n=101; n <= nodes; n++) {

            if (n == frag.nodeId)
                continue; // don't delete local frag

            // Delete from all disks on remote node, log errors but do not fail
            for (int d=0; d <= 3; d++) {
                deleteFragment(n, getPath(dataOid, fragment, chunk, d));
            }
        }
    }

    public boolean verify(String dataOid,int fragment, int chunk) 
                                  throws HoneycombTestException {
        OIDFormat newOid = new OIDFormat(dataOid, true);
        newOid = newOid.getOidForChunk(chunk);
        int layoutNumber=newOid.getLayoutMapId();       
        Layout layout = getLayout(layoutNumber);        
        DiskId disk = (DiskId) layout.get(fragment);
        int node = disk.nodeId();
        String path=getPath(dataOid,fragment,chunk);
        return (verify(node, path));
    }



    public boolean verify(int node, String path) {
        Log.INFO("verifying....");
        try {
            compareFragBackup(node, path);
        } catch (Throwable e) {
            Log.ERROR("Verify failed: " + e.toString());
            return false;
        }
        removeFragBackup(node, path);
        Log.INFO("verify complete....");
        return true;
    }

    // Find all fragments of given oid/chunk on disk
    //
    public FragLocation[] locateFragments(String oid, int chunk)
        throws HoneycombTestException {
        ArrayList frags = new ArrayList(7);
        // XXX not implemented!
        return (FragLocation[])frags.toArray(new FragLocation[7]);
    }

    // Find temp fragments of given oid/chunk on disk
    //
    public FragLocation[] locateTempFragments(String oid, int chunk)
        throws HoneycombTestException {
        ArrayList frags = new ArrayList(7);
        // XXX not implemented!
        return (FragLocation[])frags.toArray(new FragLocation[7]);
    }

    // Check whether all 7 fragments of given oid/chunk are present on disk
    // in the expected location for their layout.
    // A false result doesn't necessarily mean that oid has less than 7 frags;
    // they may be in some unexpected disk - use locateFragments().
    //
    public boolean checkPresentAllFragments(String oid, int chunk)
        throws HoneycombTestException {
        boolean allPresent = true;
        for (int i = 0; i < 7; i++) {
            allPresent &= checkPresent(oid, i, chunk);
        }
        return allPresent;
    }

    public boolean checkPresent(String dataOid,int fragment, int chunk) 
                                        throws HoneycombTestException {
        OIDFormat newOid = new OIDFormat(dataOid, true);
        newOid = newOid.getOidForChunk(chunk);
        int layoutNumber=newOid.getLayoutMapId();       
        Layout layout = getLayout(layoutNumber);        
        DiskId disk = (DiskId) layout.get(fragment);
        int node = disk.nodeId();
        String path=getPath(dataOid,fragment,chunk);
        return (checkPresent(node, path));
    }

    public boolean checkPresent(int node, String path) {
        try {
            //if [ -e ./myfilee ]; then exit 100 boo; else exit 0; fi;
            runCommand(node, "\"if [ -e " + path + " ]; then exit 0; else exit 1; fi;\"");
        } catch (Throwable e) {
            Log.INFO("File not found: " + path);
            return false;
        }
        Log.INFO("File found:"+path);
        return true;
    }
    
    public boolean dataDoctorFullHeal(String dataOid, 
                                      int fragment,
                                      int chunk,
                                      long fileSize,
                                      boolean verbose) 
            throws HoneycombTestException {
        /*
          Log.INFO("Triggering full heal");
          Log.INFO("Joe:Exiting now; check to see if we have all our frags.");
          System.exit(1);
        */
        //
        // "fullWait" means wait for the presence of the fragment in
        // question
        // Otherwise, check every few seconds. when it shows up, proceed.
        //
        startCycle(CLIState.LOST_FRAGS);

        try {
            long increments =  120;


            Log.INFO("checking every 30 seconds for...  "+
                     increments/2+
                     " minutes.");

            boolean keepChecking = true;
            for(int i=0;(i<increments) && keepChecking;i++) {

                if(i==60) {
                    //
                    // Nuttin's happened for a half hour. Re-issue the dd cycle command.
                    //
                    stopCycle(CLIState.LOST_FRAGS);
                    Log.WARN("Re-issuing cycle command. This bad - could be due to dd cycle respond bug");
                    Thread.sleep(30000);
                    startCycle(CLIState.LOST_FRAGS);                       
                }

                Log.INFO("sleeping 30 secs");
                Thread.sleep(30000);
                Log.INFO("  Checking...");                
                keepChecking = !checkPresent(dataOid,fragment,chunk);
            }
            if(keepChecking) {
                Log.ERROR("Never found the healed back fragment, verify should fail.");
            }
        } catch (InterruptedException ie) {
            Log.ERROR("Interrupted exception: "+ie.toString());
        }
        Log.INFO("Stopping full heal cycle.");
        stopCycle(CLIState.LOST_FRAGS);

        return verify(dataOid,fragment,chunk);                    

    }

    /** Runs healing on the layout map where requested fragment lives, with mandatory verification
     */
    public boolean dataDoctorTargetedHeal(String dataOid, 
                                          int fragment,
                                          int chunk,
                                          CLIState state, 
                                          boolean verbose) 
            throws HoneycombTestException {
        return dataDoctorTargetedHeal(dataOid, fragment, chunk, state, verbose, true);
    }

    /** Runs healing on the layout map where requested fragment lives, with optional verification
     */
    public boolean dataDoctorTargetedHeal(String dataOid, 
                                          int fragment,
                                          int chunk,
                                          CLIState state, 
                                          boolean verbose,
                                          boolean verify) 
              throws HoneycombTestException {
        
        OIDFormat newOid = new OIDFormat(dataOid, true);
        newOid = newOid.getOidForChunk(chunk);
        int layoutNumber=newOid.getLayoutMapId();       
        Layout layout = getLayout(layoutNumber);        
        DiskId disk = (DiskId) layout.get(fragment);
        int node = disk.nodeId();
        int diskNumber = disk.diskIndex();

        Log.INFO("Targeting " + node + ":" + diskNumber + " (frag " + fragment +
            " of chunk " + chunk + " of layout " + layoutNumber + " " + layout +
            ")");
                
        dataDoctorTargetedHeal(node,diskNumber,layoutNumber,state,verbose);

        if (!verify)
            return true; // done

        // do verification
        String path=getPath(dataOid,fragment,chunk);

        // There are cases in OA where we do not (on purpose) do inline
        // corruption repair (ie, when the footer is corrupted).  We
        // expect recovery to take care of regenerating the fragment
        // for us.  So, in the scan case, we manually invoke a recovery
        // before doing the validation if the frag file is missing.  
        //
        // Should OA do inline recovery? Not clear.
        // See bug 6187542 Fragment scanner should not delete
        // fragments with corrupted footers without recovering them,
        // though now that DD runs recovery constantly, this is less of
        // an issue...
        if (state == CLIState.SCAN_FRAGS) {
            File f = new File(path);
            if (!f.exists()) {
                Log.WARN("Frag " + f + " doesn't exist after scan...try recovery");
                Log.INFO("-------------> Do Manual Recovery After Scan");
                dataDoctorTargetedHeal(node,diskNumber,layoutNumber,CLIState.LOST_FRAGS,verbose);
            }
        }
        return (verify(node,path));

    }

    //
    // "nodes" start with 1. (ie: hcb101)
    // Enforces no-activity on data doctor rule.
    //
    private void dataDoctorTargetedHeal(int node, 
                        int disk, 
                        int layout, 
                        CLIState state, 
                        boolean verbose) 
                            throws HoneycombTestException {
        String javaCommand="java -cp "+
            "/opt/honeycomb/lib/honeycomb-server.jar:"+
            "/opt/honeycomb/lib/honeycomb-common.jar:"+
            "/opt/honeycomb/lib//honeycomb-hadb.jar:"+
            "/opt/honeycomb/lib/jetty-4.2.20.jar:"+
            "/opt/honeycomb/lib/jug.jar:"+
            "/usr/local/st5800/lib/st5800-suitcase.jar:"+
            "/opt/honeycomb/share"+
            " -Djava.library.path=/opt/honeycomb/lib";

        String nodeNumber=nodeNumber(node);            
        if(state==CLIState.LOST_FRAGS) {
            stopIt("recover_lost_frags_cycle", CLIState.LOST_FRAGS);
            
            javaCommand += " com.sun.honeycomb.datadoctor.DirectoryCheck frag "+nodeNumber+":"+disk+" "+layout;            
            Log.INFO("Recovery line: " + javaCommand);


        } else if(state==CLIState.SCAN_FRAGS) {
            stopIt("scan_frags_cycle",CLIState.SCAN_FRAGS);
            
            javaCommand += " com.sun.honeycomb.datadoctor.DirectoryCheck scan "+nodeNumber+":"+disk+" "+layout;            
            Log.INFO("Scan line: " + javaCommand);

        } else {
            throw new HoneycombTestException(
                    "Only possible to target using LOST_FRAGS at present.");
        }

        // Make this easier to see if we are being chatty
        if (verbose) {
            Log.INFO("------------------ BEGIN DataDoctor ---------------------");
        }

        runCommand(node,javaCommand,verbose);

        // Make this easier to see if we are being chatty
        if (verbose) {
            Log.INFO("------------------ END DataDoctor -----------------------");
        }
    }


    public void setDefault(CLIState state) throws HoneycombTestException {
        cli.setDefaultDD(state);
    }
    
    public static DataDoctorState getInstance() {
        if(null==instance) {
            instance = new DataDoctorState();
        }
        return instance;

    }

}

