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



package com.sun.honeycomb.factory;

import java.util.BitSet;
import java.util.Random;
import java.util.logging.Logger;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.net.InetAddress;
    

/**
*/

public class NFSLoad {

    final private static int OPEN_CUTOFF = 150; //ms

    final private static int SLICE_SIZE = 64 * 1024;
    final private static int WRITE_BUFS = 512;
    final private static int DEFAULT_ITERATIONS = 10;
    final private static int DEFAULT_NB_DISKS = 4;

    private boolean verbose = false;
    private boolean verbose2 = false;

    ///////////////////////////////////////////////////////////////////

    private int nb_iterations = DEFAULT_ITERATIONS;
    private int nb_nodes = -1;
    private int nb_disks = DEFAULT_NB_DISKS;
    private int long_opens = 0;
    private int this_node;
    private int nfiles;

    private void usage(String msg) {
        if (msg != null)
            System.err.println(msg);
        System.err.println("usage: ... [-i nb_iterations] [-n nb_nodes] [-d nb_disks per node] [-lvrN]");
        System.err.println("\t-i\tnumber of iteration (default " + 
                           DEFAULT_ITERATIONS + ") 0=forever");
        System.err.println("\t-p\tparallel slice writes\n");
        System.err.println("\t-P\tparallel slice writes w/ no sync\n");
        System.err.println("\t-v\tverbose (prints total host/disk usage)\n");
        System.exit(1);
    }

    private void fatal(String err, Throwable t) {
        t.printStackTrace();
        String s = t.toString();
        if (t.getCause() != null)
            s += " cause " + t.getCause();
        fatal(err + ": " + s);
    }

    private void fatal(String err) {
        err = "fatal on node " + this_node + ": " + err;
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
        new NFSLoad(args);
    }

    Statistic open_stat = new Statistic("open");
    BandwidthStatistic write_stat = new BandwidthStatistic("write");
    Statistic close_stat = new Statistic("close");
    Statistic rename_stat = new Statistic("rename");

    NFSLoad(String[] args) {
 
        try {
            // get local node number (101..116)
            InetAddress localMachine = InetAddress.getLocalHost();
            String s = localMachine.getHostAddress();
            s = s.substring(s.lastIndexOf('.') + 1);
            this_node = Integer.parseInt(s);
            Getopt opts = new Getopt(args, "i:d:n:");
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

                    case 'v':
                        verbose = true;
                        break;

                    case 'V':
                        verbose2 = true;
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

        if (nb_nodes == -1)
            usage("-n is required");

        //try { Thread.sleep(3000); } catch (Exception ignore) {}

        nfiles = (nb_nodes - 1) * nb_disks;

        fragSync = new Syncer(nfiles);
        initBuf();
        initFiles();

        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));
        /**
         *  each iteration simulates storing a file in
         *  nfiles pieces
         */
        long t0 = System.currentTimeMillis();
        for (long i=0; ; i++) {
            if (nb_iterations != 0  &&  i >= nb_iterations) 
                break;
            chooseDrives();
            startThreads();
            joinThreads();
            getStats();
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
        long total_bytes = 1L * nb_iterations * nfiles * 
                           WRITE_BUFS * SLICE_SIZE;
        System.out.println("agg_bw_mb_s " +
                        BandwidthStatistic.toMBPerSec(total_bytes, total_time));
    }

    private byte[] data_buf = null;
    private void initBuf() {
        data_buf = new byte[SLICE_SIZE];
        for (int i=0; i<data_buf.length; i++)
            data_buf[i] = (byte)i;
    }

    private static File[] files = null;
    private void initFiles() {
        files = new File[nfiles];
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

    private Random random = new Random();
    private int randIndex(int size) {
        int ret = -99;
        try {
            ret = random.nextInt(size);
        } catch (Throwable t) {
            fatal("randIndex", t);
        }
        return ret;
    }

    private void chooseDrives() {

        int file = 0;
        for (int i=0; i<nb_nodes; i++) {
            int node = 101 + i;
            if (node == this_node)
                continue;

            for (int j=0; j<nb_disks; j++) {

                //
                //  make file name
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
                files[file] = new File("/netdisks/10.123.45." + node +
                                    "/data/" + j + hashPath +
                                    "/TESTFILE." + node + "." + file);
                file++;
            }
        }
    }

    private FragThread[] fragThreads = null;

    private void startThreads() {

        fragThreads = new FragThread[nfiles];
        for (int i=0; i<nfiles; i++)
            fragThreads[i] = new FragThread(i);

        for (int i=0; i<nfiles; i++)
            fragThreads[i].start();
    }

    private void joinThreads() {
        try {
            for (int i=0; i<nfiles; i++)
                fragThreads[i].join();
        } catch (Throwable t) {
            fatal("join", t);
        }
    }

    private void getStats() {
        for (int i=0; i<nfiles; i++) {
            FragThread ft = fragThreads[i];
            if (ft.open_time > OPEN_CUTOFF)
                long_opens++;
            open_stat.addValue(ft.open_time);
            close_stat.addValue(ft.close_time);
            rename_stat.addValue(ft.rename_time);
        }
        write_stat.add(fragThreads[0].write_time, 
                       nfiles * WRITE_BUFS * SLICE_SIZE);
    }

    class FragThread extends Thread {

        int id;
        public long open_time, write_time;
        public long close_time, rename_time;
        private ByteBuffer bbuf = ByteBuffer.allocateDirect(SLICE_SIZE);

        FileChannel channel = null;

        FragThread(int id) {
            this.id = id;
            if (id == 0) {
                bbuf.put(data_buf);
                bbuf.flip(); // prepare for read
            }
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
            if (id == 0) {
                t1 = System.currentTimeMillis();
                for (int i=0; i<WRITE_BUFS; i++) {
                    for (int j=0; j<nfiles; j++) {
                        FragThread ft = fragThreads[j];
                        bbuf.rewind();
                        try {
                            while (bbuf.hasRemaining()) {
                                if (ft.channel.write(bbuf) < 1) {
                                    fatal("wrote < 1");
                                }
                            }
                        } catch (Throwable t) {
                            fatal("writing slice " + i + " to " + files[j], t);
                        }
                        if (verbose2)
                            System.out.print(".");
                    }
                    if (verbose2)
                        System.out.println();
                }
                write_time = System.currentTimeMillis() - t1;
            }
            fragSync.sync();

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
    
    public class Getopt {
        private BitSet optchars;
        private BitSet optsWithArg;
    
        private String optstring;
        private String[] argv;
        private int optind;
    
        public class Option {
            private char optname;
            private String optarg;
            private int optind;
            private boolean hasMore;
    
            public int index() { return optind; }
            public char name() { return optname; }
            public String value() { return optarg; }
    
            Option(int index, char name, String optarg) {
                this.optind = index;
                this.optname = name;
                this.optarg = optarg;
            }
        }
    
        public Getopt(String[] args, String optstr) {
            this.argv = args;
            this.optstring = optstr;
    
            setup();
        }
    
        public boolean hasMore() {
            return optind < argv.length && isOption(argv[optind]);
        }
    
        public String[] remaining() {
            if (optind >= argv.length)
                return null;
    
            String[] retval = new String[argv.length - optind];
            for (int i = 0; i < retval.length; i++)
                retval[i] = argv[optind + i];
            return retval;
        }
    
        /** The meat */
        public Option next() {
            // If an option doesn't take an argument, it may be combined
            // with other non-argument-taking options. We handle this by
            // removing the handled option from the string i.e. "-xzf",
            // after we handle the "x" option, becomes "-zf"
    
            for (;;) {
                if (!hasMore())
                    return null;
    
                // Examine argv[optind]. We already know it begins with a
                // '-'. Is it in optchars? If not, it's bogus.
                String arg = argv[optind];
                char o = arg.charAt(1);
    
                if (!optchars.get(o)) {
                    System.err.println("Option \"-" + o + "\" unknown.");
                    if (arg.length() > 2)
                        argv[optind] = "-" + arg.substring(2);
                    else
                        optind++;
                    continue;
                }
    
                // Is it in optsWithArg? If so, find an argument for it:
                // the rest of the string if it's of size > 2, the next
                // argument if it's not
    
                String optarg = null;
                int index = optind;
    
                if (optsWithArg.get(o)) {
                    optind++;
                    if (arg.length() > 2)
                        optarg = arg.substring(2);
                    else {
                        if (optind >= argv.length)
                            optarg = "";
                        else
                            optarg = argv[optind++];
                    }
                }
                else {
                    // Set up for option after this one
                    if (arg.length() > 2)
                        argv[optind] = "-" + arg.substring(2);
                    else
                        optind++;
                }
                return new Option(index, o, optarg);
            }
        }
    
        private void setup() {
            optind = 0;
            optchars = new BitSet(256);
            optsWithArg = new BitSet(256);
    
            char prev = 0;
            for (int i = 0; i < optstring.length(); i++) {
                char c = optstring.charAt(i);
                if (c == '-' || c == ' ' || c == '?')
                    throw new RuntimeException("Option string \"" + optstring +
                                               "\" malformed");
                if (c == ':') {
                    if (prev > 0)
                        optsWithArg.set(prev);
                    prev = 0;
                }
                else {
                    optchars.set(c);
                    prev = c;
                }
            }
        }
    
        private boolean isOption(String s) {
            return s.length() > 1 && s.startsWith("-") &&
                optchars.get(s.charAt(1));
        }
    }
}
