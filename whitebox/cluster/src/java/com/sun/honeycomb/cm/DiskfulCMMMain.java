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
 *  This class starts a cmm+disk version of HC which mounts disks
 *  locally before starting cmm, then, after cmm start, registers
 *  via the cmm api to get notification of nodes entering or leaving
 *  the cluster; when notification is received, it nfs mounts/umounts
 *  the disks on the other node. No provision is made for problems 
 *  with individual disks - it's all-or-nothing. The purpose is to 
 *  enable nfs option testing via non-HC load emulation (nfsopen2.c 
 *  or its progeny).
 */

package com.sun.honeycomb.cm;

// unit_test
import com.sun.honeycomb.disks.DiskInitialize2;

// real HC
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.cm.cluster_membership.CMM;
import com.sun.honeycomb.cm.cluster_membership.CMMException;
import com.sun.honeycomb.cm.cluster_membership.CMMApi;
import com.sun.honeycomb.cm.cluster_membership.messages.Message;
import com.sun.honeycomb.cm.cluster_membership.messages.api.NodeChange;

import com.sun.honeycomb.util.sysdep.DiskOps;

import java.util.*;
import java.util.logging.*;
import java.nio.channels.*;
import java.io.*;

public class DiskfulCMMMain {

    private static final Logger logger = 
        Logger.getLogger(DiskfulCMMMain.class.getName());

    private final String BASE_IP = "10.123.45.";

    private static String nodesConfig = 
        "101 10.123.45.101 true, 102 10.123.45.102 true, " +
        "103 10.123.45.103 true, 104 10.123.45.104 true, " +
        "105 10.123.45.105 true, 106 10.123.45.106 true, " +
        "107 10.123.45.107 true, 108 10.123.45.108 true, " +
        "109 10.123.45.109 true, 110 10.123.45.110 true, " +
        "111 10.123.45.111 true, 112 10.123.45.112 true, " +
        "113 10.123.45.113 true, 114 10.123.45.114 true, " +
        "115 10.123.45.115 true, 116 10.123.45.116 true";

    // from DiskMonitor
    private static final String PNAME_NEWFS_OPTIONS =
        "honeycomb.disks.newfs.options";
    private static final String PNAME_NEWFS_TIMEOUT =
        "honeycomb.disks.newfs.timeout";
    private static final String PNAME_HWPROFILE =
        "honeycomb.hardware.profile";

    private static int localnode = -1;
    private static ClusterProperties config = ClusterProperties.getInstance();

    // from Platform
    private static final String PNAME_NFS_OPTIONS ="honeycomb.cell.nfs.options";
    String nfsOptions = config.getProperty(PNAME_NFS_OPTIONS);

    // from NfsManager
    private static DiskOps diskOps = DiskOps.getDiskOps();

    private static final String PNAME_DISKSPERNODE = 
                                                "honeycomb.layout.diskspernode";
    private int diskspernode;

    private static final String PNAME_NUM_NODES = "honeycomb.cell.num_nodes";
    private int num_nodes;

    private static void usage(String msg) {
        if (msg != null)
            System.err.println(msg);
        System.err.println("usage: DiskfulCMMMain <hostnum>");
        System.exit(1);
    }

    public static void main (String[] args) {
        new DiskfulCMMMain(args);
    }

    DiskfulCMMMain(String[] args) {

        if (args != null  &&  args.length != 1)
            usage(null);

        String hostnum = args[0];

        try {
            localnode = Integer.parseInt(hostnum);
        } catch (Exception e) {
           usage(hostnum + ": " + e.toString());
        }
        try {
            String s = config.getProperty(PNAME_DISKSPERNODE);
            diskspernode = Integer.parseInt(s);
            s = config.getProperty(PNAME_NUM_NODES);
            num_nodes = Integer.parseInt(s);
        } catch (Exception e) {
           usage("getting disks per node: " + e.toString());
        }


        Runtime.getRuntime().addShutdownHook(new Goodbye());

        //
        //  mount local disks
        //
        setupMounts();

        //
        //  start CMM
        //
        boolean singleMode = false;
        boolean doQuorum   = false;
        int     numDisks   = 0;

	//        CMM.start(localnode, nodesConfig, singleMode, doQuorum, numDisks, 
	//                             "unit-test", 1);
	// New CMM.start doesn't take in a minor version number
	// TODO: Fix this -"unit-test" may not be an acceptable version string
        CMM.start(localnode, nodesConfig, singleMode, doQuorum, numDisks, 
                             "unit-test");

        //
        //  handle CMM notifications of nodes 
        //  joining/leaving cluster
        //
        notificationLoop();
    }

    private void fatal(String msg) {
        logger.log(Level.SEVERE, msg);
        System.exit(1);
    }

    private long getLongProperty(String pname, long defaultVal) {
        String s = null;
        try {
            if ((s = config.getProperty(pname)) == null)
                return defaultVal;

            return Long.parseLong(s);

        } catch (NumberFormatException e) {
            logger.warning("Couldn't parse " + pname + " = \"" + s +
                           "\"; using default " + defaultVal);
        }

        return defaultVal;
    }

    int mounted = 0;
    public synchronized void addDisk() {
        mounted++;
        if (mounted == diskspernode) {
            this.notifyAll();
        }
    }
    private synchronized void waitForDisks() {
        logger.info("waiting for local disk mounts");
        try {
            this.wait();
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("local disk mounts done");
    }

    private void setupMounts() {

        //
        //  unmount any left-over nfs mounts 1st
        //
        for (int i=0; i<num_nodes; i++) {

            int node = 101 + i;
            if (node == localnode)
                continue;

            String mount_base = "/netdisks/" + BASE_IP + node + "/data/";
            try {
                for (int j=0; j<diskspernode; j++) {
                    String mountPoint = mount_base + j;
                    File f = new File(mountPoint);
                    String ff[] = f.list();
                    if (ff == null)
                        fatal("NFS MNT POINT NOT A DIR: " + mountPoint);
                    if (ff.length > 0) {
                        logger.info("pre-umounting nfs: " + mountPoint);
                        // from NfsManager
                        diskOps.umount(mountPoint, DiskOps.FS_NFS, true);
                    }
                }
            } catch (Throwable t) {
                fatal("STARTUP NFSUMOUNT EXITING ON " + t.toString());
            }
        }
        //logger.info("Unmounted disks for node " + node);


        //
        //  from DiskMonitor constructor
        //
        String profileName = config.getProperty(PNAME_HWPROFILE);
        if (profileName == null)
            fatal("Can't get " + PNAME_HWPROFILE);
        HardwareProfile profile = HardwareProfile.getProfile(profileName);
        if (profile == null)       
            fatal("Unknown profile: " + profileName);
        String newfsOptions = config.getProperty(PNAME_NEWFS_OPTIONS, "");
        long newfsTimeout = 1000 * getLongProperty(PNAME_NEWFS_TIMEOUT,
                                         DiskInitialize2.DEFAULT_NEWFS_TIMEOUT);

        //
        //  on to code from DiskMonitor.prepareDisks
        //
        int cellId = 0;  // always standalone cluster
        short siloId = 0; // always standalone cluster
        try {
            DiskInitialize2.init(profile, this, cellId, siloId,
                                 (short)localnode);
        } catch (Throwable t) {
            fatal("DiskInitialize2.init: " + t);
        }
        logger.info("DiskInitialize2.init ok");

        //
        //  wait for the disk init threads to complete
        //  (any w/ problems will System.exit())
        //
        waitForDisks();

        //
        //  check that everything is mounted just
        //  in case
        //
        try {
            for (int i=0; i<diskspernode; i++) {
                File f = new File("/data/" + i);
                String ff[] = f.list();
                if (ff == null  ||  ff.length == 0) {
                    fatal("CHECK MOUNTS EXITING ON NO MOUNT: " + f);
                }
            }
        } catch (Throwable t) {
            fatal("CHECK MOUNTS EXITING ON " + t.toString());
        }

        // export nfs
        try {
            for (int i=0; i<diskspernode; i++)
                diskOps.export("/data/" + i);
        } catch (Throwable t) {
            fatal("EXPORT NFS EXITING ON " + t.toString());
        }
        logger.info("nfs export ok - disk setup done");
    }

    private void notificationLoop() {

        CMMApi api = null;

        // CMM takes a little while to start
        String ip = BASE_IP + localnode;
        for (int i=0; i<5; i++) {
            try {
                Thread.sleep(1000);
                logger.info("get api.. " + ip);
                api = CMM.getAPI(ip);
                break;
            } catch (Throwable t) {
                logger.info("getting api: " + t.getMessage());
                api = null;
            }
        }
        if (api == null)
            fatal("can't get api");

        logger.info("got api");

        try {
            // Register for notifications
            SocketChannel sc = api.register();

            // Wait for notifs
            Selector select = Selector.open();
            sc.configureBlocking(false);
            sc.register(select, SelectionKey.OP_READ);

            while (true) {
                select.select();
                Iterator keys = select.selectedKeys().iterator();
                SelectionKey key;
                while (keys.hasNext()) {
                    key = (SelectionKey) keys.next();
                    if (key.isReadable()) {
                        StringBuffer sb = new StringBuffer();
                        Message msg =
                            api.getNotification((SocketChannel)key.channel());
                        NodeChange change = null;
                        if (msg instanceof NodeChange) {
                            change = (NodeChange) msg; 
                            //change.exportString(sb);
                            int node = change.nodeId();
                            if (node == localnode  ||  node < 0)
                                continue;
                            switch (change.getCause()) {
                            case NodeChange.MEMBER_JOINED:
                                nfsMount(node);
                                break;
                            case NodeChange.MEMBER_LEFT:
                                nfsUMount(node);
                                break;
                            }
                        }
                    }
                }
                select.selectedKeys().clear();
            }
        } catch (Throwable t) {
            fatal("NOTIFY EXITING ON " + t.toString());
        }
    }

    private void nfsMount(int node) {
        logger.info("Node " + node + " joined the cluster, nfs mounting disks");
        String host_base = BASE_IP + node + ":/data/";
        String mount_base = "/netdisks/" + BASE_IP + node + "/data/";
        try {
            for (int i=0; i<diskspernode; i++) {
                String path = host_base + i;
                String mountPoint = mount_base + i;
                // from NfsManager
                diskOps.mount(path, DiskOps.FS_NFS, mountPoint, nfsOptions);
            }
        } catch (Throwable t) {
            fatal("NFSMOUNT EXITING ON " + t.toString());
        }
        logger.info("Mounted disks for node " + node);
    }
    private void nfsUMount(int node) {
        logger.info("Node " + node + " left the cluster, umounting nfs disks");
        String mount_base = "/netdisks/" + BASE_IP + node + "/data/";
        try {
            for (int i=0; i<diskspernode; i++) {
                String mountPoint = mount_base + i;
                // from NfsManager
                diskOps.umount(mountPoint, DiskOps.FS_NFS, true);
            }
        } catch (Throwable t) {
            fatal("NFSUMOUNT EXITING ON " + t.toString());
        }
        logger.info("Unmounted disks for node " + node);
    }

    private static class Goodbye extends Thread {
        public void run() {
            String pid = System.getProperty("PID", "Unknown");
            logger.info("[PID " + pid + 
                        "] DiskfulCMMMain JVM is exiting. Goodbye!");
        }
    }
}
