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



package com.sun.honeycomb.util;

import com.sun.honeycomb.common.Getopt;

import java.util.logging.Logger;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.net.InetAddress;

/**
    Java implementation of unit_tests/src/c/hcload.c; use 
    hcload.c for more fine-grained analysis.

    Simulator of honeycomb load, primarily for testing write
    performance with different nfs options. Uses multithreaded
    (parallel) writes. NOTE: could add metadata to this.

    unit_tests/.../cm/DiskfulCMMMain can be used to set up
    the crossmounts.

    See options and first set of 'final's for parameters.

    The -l option allows testing on local mounts. The default
    is to not use local mounts.

    From HoneycombTestConstants.java:

    // === nomenclature ===
    //
    // 'fragment size' of 64k is actually the size of each data write to
    // the frag file. Max frag file size is apparently 3200x64k + any
    // parity and header/footer data. 'block size' thus refers to one
    // stripe of 64k across the fragment files. 'chunks' are also referred
    // to as 'extents'.
    //
*/

public class HCLoad {

    private static final Logger logger =
                                Logger.getLogger(HCLoad.class.getName());

    final private static int OPEN_CUTOFF = 150; //ms

    final private static int DEFAULT_SLICE_SIZE = 64 * 1024;
    final private static int MAX_WRITE_BUFS = 3200;
    final private static int DEFAULT_ITERATIONS = 10;
    final private static int DEFAULT_WRITE_BUFS = 1000;
    final private static int DEFAULT_FRAGS_PER_SFILE = 7;
    final private static int DEFAULT_NB_DISKS = 4;
    final private static int DEFAULT_NB_NODES = 16;
    final private static int DEFAULT_NB_ITERATIONS = 10;

    private boolean verbose = false;
    private boolean verbose2 = false;

    ///////////////////////////////////////////////////////////////////

    private int nb_iterations = DEFAULT_ITERATIONS;
    private int nb_nodes = DEFAULT_NB_NODES;
    private int frags_per_sfile = DEFAULT_FRAGS_PER_SFILE;
    private int nb_disks = DEFAULT_NB_DISKS;
    private int write_bufs = DEFAULT_WRITE_BUFS;
    private int slice_size = DEFAULT_SLICE_SIZE;
    private boolean local_disks = false;
    private boolean parallel_writes = false;
    private boolean slice_sync = false;
    private int selected_host = -1;
    private int selected_disk = -1;
    private int long_opens = 0;
    private int node;

    private void usage(String msg) {
        if (msg != null)
            System.err.println(msg);
        System.err.println("usage: ... [-i nb_iterations] [-w nb_write_bufs] [-n nb_nodes] [-d nb_disks per node] [-m max] [-o host] [-s disk] [-f fragments] [-S slice_size] [-lvrN]");
        System.err.println("\t-i\tnumber of iteration (default " + 
                           DEFAULT_ITERATIONS + ") 0=forever");
        System.err.println("\t-w\tnumber of 64k bufs (must be <= " + 
                           MAX_WRITE_BUFS + "; default " + 
                           DEFAULT_WRITE_BUFS + ")");
        System.err.println("\t-f\tfragments (" + DEFAULT_FRAGS_PER_SFILE + ")");
        System.err.println("\t-l\tlocal disks only (fragments=nb_disks/node)");
        System.err.println("\t-o <host>\tspecific host (101, 102, .. 116)");
        System.err.println("\t-s <disk>\tspecific disk (0...max)");
        System.err.println("\t-S <slice_sz>\tin kbytes (default 64k)");
        System.err.println("\t-p\tparallel slice writes\n");
        System.err.println("\t-P\tparallel slice writes w/ no sync\n");
        System.err.println("\t-v\tverbose (prints total host/disk usage)\n");
        System.exit(1);
    }

    private void fatal(String err, Throwable t) {
        String s = t.toString();
        if (t.getCause() != null)
            s += " cause " + t.getCause();
        fatal(err + ": " + s);
    }

    private void fatal(String err) {
        err = "fatal: " + err;
        logger.severe(err);
        System.err.println(err);
        System.exit(1);
    }

    class Syncer {
        int n;
        int waiting;
        Syncer(int n) {
            this.n = n;
        }
        synchronized void sync() {
            waiting++;
            try {
                if (waiting == n) {
                    waiting = 0;
                    notifyAll();
                } else {
                    wait();
                }
            } catch (Throwable t) {
                fatal("syncing", t);
            }
        }
    }

    Syncer fragSync;

    public static void main(String[] args) {
        new HCLoad(args);
    }

    Statistic open_stat = new Statistic("open");
    BandwidthStatistic write_stat = new BandwidthStatistic("write");
    Statistic close_stat = new Statistic("close");
    Statistic rename_stat = new Statistic("rename");

    HCLoad(String[] args) {
 
        try {
            // get local node number (101..116)
            InetAddress localMachine = InetAddress.getLocalHost();
            String s = localMachine.getHostAddress();
            s = s.substring(s.lastIndexOf('.') + 1);
            node = Integer.parseInt(s);
            Getopt opts = new Getopt(args, "i:d:n:m:o:s:f:w:S:rqvhlpPV");
            int opt_ct = 0;
            while (opts.hasMore()) {
                opt_ct++;
                Getopt.Option option = opts.next();
                switch (option.name()) {

                    case 'i':
                        nb_iterations = Integer.parseInt(option.value());
                        if (nb_iterations < 0)
                            usage("nb_iterations (1.. or 0=forever)");
                        break;

                    case 'n':
                        nb_nodes = Integer.parseInt(option.value());
                        if (nb_nodes < 1  ||  nb_nodes > 16)
                            usage("nb_nodes (1..16)");
                        break;

                    case 'd':
                        nb_disks = Integer.parseInt(option.value());
                        if (nb_disks < 1)
                            usage("nb_disks (1..)");
                        break;

                    case 'o': // 'only' given node
                        selected_host = Integer.parseInt(option.value());
                        if (selected_host < 101  ||  selected_host > 116)
                            usage("selected_host (101..116)");
                        break;

                    case 's': // 'selected' disk
                        selected_disk = Integer.parseInt(option.value());
                        if (selected_disk < 0  ||  selected_disk > 7)
                            usage("selected_disk (0..7)");
                        break;

                    case 'l':  // overrides -s etc.
                        local_disks = true;
                        break;

                    case 'f':
                        frags_per_sfile = Integer.parseInt(option.value());
                        if (frags_per_sfile < 1)
                            usage("frags_per_sfile (1..)");
                        break;

                    case 'v':
                        verbose = true;
                        break;

                    case 'V':
                        verbose2 = true;
                        break;

                    case 'w':
                        write_bufs = Integer.parseInt(option.value());
                        if (write_bufs < 0  ||  write_bufs > MAX_WRITE_BUFS)
                            usage("write_bufs (1.." + MAX_WRITE_BUFS + ")");
                        break;

                    case 'S':
                        slice_size = Integer.parseInt(option.value());
                        if (slice_size < 1)
                            usage("slice_size (1..)");
                        slice_size *= 1024;
                        break;

                    case 'p':
                        parallel_writes = true;
                        break;

                    case 'P':
                        parallel_writes = true;
                        slice_sync = true;
                        break;

                    case 'h':
                    default:
                        usage(null);
                }
            }
            if (args.length > 0  &&  opt_ct == 0)
                usage("illegal 1st opt");

        } catch (Throwable t) {
            t.printStackTrace();
            usage(t.getMessage());
        }

        if (selected_host != -1  &&  local_disks)
            usage("-o and -l are incompatible");

        if (local_disks) {
            if (selected_disk != -1) {
                System.out.println("* 1 frag to local disk " + selected_disk + 
                                   " only");
                frags_per_sfile = 1;
            } else {
                System.out.println("* local disks only");
                if (frags_per_sfile != nb_disks) {
                    System.out.println("local host only: " +
                                   "adjusting frags-per-file to #/disks=" + 
                                   nb_disks);
                    frags_per_sfile = nb_disks;
                }
            }
        } else {
            if (selected_host != -1  &&  selected_disk != -1) {
                System.out.println("* single fragment only to host " +
                                   selected_host + " disk " + 
                                   selected_disk);
                frags_per_sfile = 1;
            } else if (selected_host != -1) {
                frags_per_sfile = nb_disks;
                System.out.println("* " + nb_disks + " frags to host " + 
                                   selected_host + " only");
            } else if (selected_disk != -1) {
                System.out.println("* disk " + selected_disk + " only");
            }
        }

        if ( !local_disks  &&  selected_host == -1  &&
             nb_nodes-1 < frags_per_sfile)
            System.out.println("* WARNING: >1 frag to >0 node (using " +
                               (nb_nodes-1) + " nodes for nfs-only)");

        fragSync = new Syncer(frags_per_sfile);
        initBuf();
        initFiles();

        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));
        /**
         *  each iteration simulates storing a file in
         *  frags_per_sfile pieces
         */
        long t0 = System.currentTimeMillis();
        for (long i=0; ; i++) {
            chooseDrives();
            logger.info("starting file " + i);
            startThreads();
            joinThreads();
            getStats();

            if (nb_iterations != 0  &&  i >= nb_iterations) 
                break;
        }
        long total_time = System.currentTimeMillis() - t0;
        System.out.println(open_stat.toString());
        System.out.println("slow_open (opens_>_" + OPEN_CUTOFF + "_ms) " + 
                          long_opens +
                           " " + (100.0 * (float)long_opens / 
                                  (float)open_stat.getN()) + " %");
        System.out.println(write_stat.toString());
        System.out.println(close_stat.toString());
        System.out.println(rename_stat.toString());
        long total_bytes = nb_iterations * frags_per_sfile * 
                           write_bufs * slice_size;
        System.out.println("agg_bw_mb_s " +
                        BandwidthStatistic.toMBPerSec(total_bytes, total_time));

        if (verbose) {
            StringBuffer sb = new StringBuffer();
            sb.append("Host/Disk usage:");
            int n = 0;
            for (int i=0; i<cum_disk_counts.length; i++) {
                if (i % nb_disks == 0) {
                    sb.append("\n\t").append(n).append(":  (");
                    sb.append(cum_node_counts[n]).append(")  ");
                    if (cum_node_counts[n] < 10)
                        sb.append(' ');
                    n++;
                }
                sb.append(cum_disk_counts[i]).append(' ');
            }
            System.out.println(sb.toString());
        }
    }

    private byte[] data_buf = null;
    private void initBuf() {
        data_buf = new byte[slice_size];
        for (int i=0; i<data_buf.length; i++)
            data_buf[i] = (byte)i;
    }

    private static File[] files = null;
    private void initFiles() {
        files = new File[frags_per_sfile];
    }

    static private void cleanup() {
        for (int i=0; i<files.length; i++) {
            if (files[i] != null) {
                try {
                    files[i].delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private int randIndex(int size) {
        int ret = -99;
        try {
            ret = RandomUtil.randIndex(size);
        } catch (Throwable t) {
            fatal("randIndex", t);
        }
        return ret;
    }

    private int onetoone(int i) {
        int n = i + 101;
        // skip this node
        if (n < node)
            return n;
        return n + 1;
    }

    int[] node_counts = null;
    int[] disk_counts = null;
    int[] cum_node_counts = null;
    int[] cum_disk_counts = null;

    StringBuffer sb = new StringBuffer();

    private void chooseDrives() {

        sb.setLength(0);

        if (node_counts == null) {
            node_counts = new int[nb_nodes];
            disk_counts = new int[nb_nodes * nb_disks];
            cum_node_counts = new int[nb_nodes];
            for (int i=0; i<cum_node_counts.length; i++)
                cum_node_counts[i] = 0;
            cum_disk_counts = new int[nb_nodes * nb_disks];
            for (int i=0; i<cum_disk_counts.length; i++)
                cum_disk_counts[i] = 0;
        }

        // track frags per node
        for (int i=0; i<node_counts.length; i++)
            node_counts[i] = 0;

        // track frags per disk
        for (int i=0; i<disk_counts.length; i++)
            disk_counts[i] = 0;

        for (int i=0; i<frags_per_sfile; i++) {
            //
            //  choose node - avoid this one if possible
            //  since we want to test nfs
            //
            int nnode;
            if (selected_host != -1) {
                //
                //  might be this node - obviously a power user
                //
                nnode = selected_host;
            } else if (local_disks) {
                //
                //  must use this node - user is testing local fs
                //
                nnode = node;
            } else {
                //
                //  avoid this node to go the extra mile
                //
                if (nb_nodes-1 == frags_per_sfile) {
                    //
                    //  perfect frags/nodes match
                    //
                    nnode = onetoone(i);
                } else if (nb_nodes-1 < frags_per_sfile) {
                    //
                    //  must have >1 frag on >0 nodes
                    //  (user warned at arg parse post-check)
                    //
                    if (i < nb_nodes-1) {
                        // first 'layer'
                        nnode = onetoone(i);
                    } else {
                        //
                        //  pull out the random thingamabob
                        //
                        // heuristic - this could be made perfect
                        // try for <= 2 frags/node
                        //
                        nnode = -1;
                        // try harder the further along
                        for (int k=0; k<i; k++) { 
                            int ii = randIndex(nb_nodes);
                            if (node_counts[ii] == 1) {
                                nnode = ii + 101;
                                break;
                            }
                        }
                        if (nnode == -1) {
                            // in this case we could check and
                            // see if 'we' randomly missed a case
                            // that would give perfect tho overloaded
                            // packing, which would be done in 'real' code
                            while (true) {
                                nnode = randIndex(nb_nodes) + 101;
                                if (nnode != node)
                                    break;
                            }
                        }
                    } 
                } else {

                    //
                    //  more than enough nodes - 
                    //  place randomly w/out collision
                    //
                    while (true) {

                        int ii = randIndex(nb_nodes);
                        if (node_counts[ii] > 0)
                            continue;

                        nnode = ii + 101;
                        if (nnode != node)
                            break;
                    }
                }
                node_counts[nnode-101]++;
            }
            cum_node_counts[nnode-101]++;

            //
            //  choose drive
            //
            int disk;
            int hindex = (nnode - 101) * nb_disks;
            if (selected_disk != -1) {
                // power user or just crazy
                disk = selected_disk;
            } else if (local_disks  &&  frags_per_sfile == nb_disks) {
                // using all local disks
                disk = i;
            } else if (node_counts[nnode - 101] == 1) {
                // choose random drive
                disk = randIndex(nb_disks);
            } else {
                // hack - try to avoid drive collisions on this host
                for (int j=0; ; j++) {
                    disk = randIndex(nb_disks);
                    int count = disk_counts[hindex + disk];
                    if (count == 0)
                        break;
                    if (count == 1  &&  j > 16) // 16 == guess
                        break;
                    if (count > 1  &&  j > 32) // 32 == another guess
                        break;
                }
            }
            disk_counts[hindex + disk]++;
            cum_disk_counts[hindex + disk]++;

            //
            //  make file
            //
            String hashPath;
            int ii = randIndex(100);
            if (ii < 10)
                hashPath = "/0" + ii;
            else
                hashPath = "/" + ii;
            ii = randIndex(100);
            if (ii < 10)
                hashPath += "/0" + ii;
            else
                hashPath += "/" + ii;

            int i2 = randIndex(100);
            if (local_disks) {
                files[i] = new File("/data/" + disk + hashPath + 
                                    "/TESTFILE." + node + "." + i);
            } else {
                files[i] = new File("/netdisks/10.123.45." + nnode +
                                    "/data/" + disk + hashPath +
                                    "/TESTFILE." + node + "." + i);
            }

            if (verbose)
                sb.append(nnode).append(":").append(disk).append(" ");
        }
        if (verbose)
            System.out.println("disks: " + sb.toString());
    }

    private FragThread[] fragThreads = null;

    private void startThreads() {

        fragThreads = new FragThread[frags_per_sfile];
        for (int i=0; i<frags_per_sfile; i++)
            fragThreads[i] = new FragThread(i);

        for (int i=0; i<frags_per_sfile; i++)
            fragThreads[i].start();
    }

    private void joinThreads() {
        try {
            for (int i=0; i<frags_per_sfile; i++)
                fragThreads[i].join();
        } catch (Throwable t) {
            fatal("join", t);
        }
    }

    private void getStats() {
        for (int i=0; i<frags_per_sfile; i++) {
            FragThread ft = fragThreads[i];
            if (ft.open_time > OPEN_CUTOFF)
                long_opens++;
            open_stat.addValue(ft.open_time);
            close_stat.addValue(ft.close_time);
            rename_stat.addValue(ft.rename_time);
        }
        write_stat.add(fragThreads[0].write_time, 
                       frags_per_sfile * write_bufs * slice_size);
    }

    class FragThread extends Thread {

        int id;
        public long open_time, write_time;
        public long close_time, rename_time;
        private ByteBuffer bbuf = ByteBuffer.allocateDirect(slice_size);

        FileChannel channel = null;

        FragThread(int id) {
            this.id = id;
            bbuf.put(data_buf);
            bbuf.flip(); // prepare for read
        }

        public void run() {
            //
            //  RandomAccessFile/FileChannel per FragmentFile.java
            //
            RandomAccessFile raf = null;

            //
            //  open
            //
            long t0 = System.currentTimeMillis();
            long t1 = t0;
            try {
                raf = new RandomAccessFile(files[id], "rw");
            } catch (Throwable t) {
                fatal("opening " + files[id], t);
            }
            open_time = System.currentTimeMillis() - t1;
            channel = raf.getChannel();
            fragSync.sync();

            //
            //  write
            //
            t1 = System.currentTimeMillis();
            if (parallel_writes) {
                for (int i=0; i<write_bufs; i++) {
                    bbuf.rewind();
                    try {
                        while (bbuf.hasRemaining()) {
                            if (channel.write(bbuf) < 1) {
                                fatal("wrote < 1");
                            }
                        }
                    } catch (Throwable t) {
                        fatal("writing " + files[id], t);
                    }

                    if (slice_sync) {
                        fragSync.sync();
                    }
                    if (verbose2  &&  id == 0)
                        System.out.print(".");
                }
                if (verbose2  &&  id == 0)
                    System.out.println();

            } else if (id == 0) {
                // serial writes, like HC as of Sept 05
                for (int i=0; i<write_bufs; i++) {
                    // stripe across frags
                    for (int j=0; j<frags_per_sfile; j++) {
                        FragThread ft = fragThreads[j];
                        bbuf.rewind();
                        try {
                            while (bbuf.hasRemaining()) {
                                if (ft.channel.write(bbuf) < 1) {
                                    fatal("wrote < 1");
                                }
                            }
                        } catch (Throwable t) {
                            fatal("writing " + files[j], t);
                        }
                        if (verbose2)
                            System.out.print(".");
                    }
                    if (verbose2)
                        System.out.println();
                }
            }
            fragSync.sync();
            write_time = System.currentTimeMillis() - t1;

            //
            //  close
            //
            t1 = System.currentTimeMillis();
            try {
                raf.close();
            } catch (Throwable t) {
                fatal("closing " + files[id], t);
            }
            close_time = System.currentTimeMillis() - t1;
            try {
                channel.close();
            } catch (Throwable t) {
                fatal("closing channel " + files[id], t);
            }
            fragSync.sync();

            //
            //  rename
            //
            File newname = new File(files[id].toString() + "_mvd");
            t1 = System.currentTimeMillis();
            try {
                files[id].renameTo(newname);
            } catch (Throwable t) {
                fatal("renaming " + files[id], t);
            }
            rename_time = System.currentTimeMillis() - t1;
            files[id] = newname;
            fragSync.sync();

            //
            //  delete and forget
            //
            files[id].delete();
            files[id] = null;
        }
    }

    private static class Shutdown implements Runnable {
        public void run() {
            cleanup();
        }
    }
}
