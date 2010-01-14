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

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.DecimalFormat;

public class FactoryClientTest {

    private static final long[] sizes = {
        // 1k    10m
          1024L, 1024L*1024L*10L
    };
    private static byte[] bytes = new byte[1024*1024];

    private final String TYPE_LONG = "system.test.type_long";
    private final String TYPE_DOUBLE = "system.test.type_double";
    private final String TYPE_STRING = "system.test.type_string";

    private NameValueObjectArchive archive;
    private ClientThread[] threads;
    private String timeStr;
    private BandwidthStatistic storeStats = new BandwidthStatistic("store");
    private BandwidthStatistic retrvStats = new BandwidthStatistic("retrv");

    private static void usage(int exit) {
        System.out.println("NAME");
        System.out.println("       FactoryClientTest - client simulation");
        System.out.println();
        System.out.println("SYNOPSIS");
        System.out.println("       java FactoryClientTest [OPTIONS] <IP | HOST>");
        System.err.println();
        System.err.println("       CLASSPATH must include honeycomb-client.jar");
        System.out.println();
        System.out.println("OPTIONS");
        System.out.println("       -h, --help");
        System.out.println("              print this message");
        System.out.println();
        System.out.println("EXAMPLES");
        System.out.println("       java FactoryClientTest hc_host-data");
        System.exit(exit);
    }

    private static String address = null;
    private static boolean help = false;
    private static int exitcode = 0;

    public static void main(String [] argv) {

        //
        //  handle args
        //
        getopt(argv);
        if (help)
            usage(0);
        if (exitcode != 0)
            System.exit(exitcode);

        //
        //  do it in an object 
        //  (solves a compile problem with populating a static array of threads)
        //
        new FactoryClientTest();
    }

    FactoryClientTest() {

        SimpleDateFormat df = new SimpleDateFormat("MM/dd HH:mm:ss.S");
        timeStr = df.format(new Date());
        log(TYPE_STRING + " metadata for all stores is [" + timeStr + "]");

        //
        //  init bytes to store to be a rolling count
        //
        byte b = 0;
        for (int i=0; i<bytes.length; i++)
            bytes[i] = b++;

        StringBuffer sb = new StringBuffer("file sizes:");
        for (int i=0; i<sizes.length; i++)
            sb.append(" ").append(sizes[i]);
        log(sb.toString());

        try {
            //
            //  get API and schema
            //
            log("connecting to honeycomb");
            archive = new NameValueObjectArchive(address);

            //
            //  get schema and index it
            //
            log("getting/checking schema");
            NameValueSchema nvs = archive.getSchema();
            NameValueSchema.Attribute[] attributes = nvs.getAttributes();
            HashMap hm = new HashMap(); // duplicate Attributes 'impossible'
            for (int i=0; i<attributes.length; i++) {
                // Attribute.getType() gives NameValueSchema.ValueType
                hm.put(attributes[i].getName(), attributes[i].getType());
            }
            //
            //  check schema using index
            //
            sb = new StringBuffer();
            schemaCheck(sb, hm, TYPE_DOUBLE, Double.class);
            schemaCheck(sb, hm, TYPE_LONG, Long.class);
            schemaCheck(sb, hm, TYPE_STRING, String.class);
            if (sb.length() > 0)
                throw new Exception("schema:\n" + sb);

            //
            //  get/check initial query values
            //
            log("getting/checking initial query values");
            int sizeCounts[] = new int[sizes.length];
            for (int i=0; i<sizeCounts.length; i++)
                sizeCounts[i] = countOIDs(TYPE_LONG, Long.toString(sizes[i]),
                                          -1);
            int doubleCounts[] = new int[sizes.length];
            for (int i=0; i<doubleCounts.length; i++)
                doubleCounts[i] = countOIDs(TYPE_DOUBLE, 
                                         Double.toString(Math.sqrt(sizes[i])),
                                         -1);
            int timeCount = countOIDs(TYPE_STRING, timeStr, -1);
            if (timeCount != 0)
                throw new Exception("already entries under this date: " +
                                    timeCount + " [" + timeStr + "]");

            //
            //  store/retrieve/delete a small file 1st for
            //  a lightweight sanity check
            //
            log("store/retrieve/delete a 1-byte file for quick sanity check");
            ClientThread ct = new ClientThread(1);
            ct.start();
            ct.join();
            if (ct.hasError())
                throw new Exception("1-byte store/retrieve failed");
            archive.delete(ct.oid);

            //
            //  launch per-filesize threads
            //
            log("launching parallel per-size store/retrieve threads");
            threads = new ClientThread[sizes.length];
            for (int i=0; i<sizes.length; i++) {
                threads[i] = new ClientThread(sizes[i]);
                threads[i].start();
            }

            //
            //  wait for threads to complete
            //
            for (int i=0; i<threads.length; i++) {
                try {
                    threads[i].join();
                    if (threads[i].hasError()) {
                        exitcode = 1;
                        //break;
                    }
                } catch (Exception e) {
                    System.err.println("waiting for task " + i + ": " + e);
                    System.exit(1);
                    //break;
                }
            }
            log("store/retrieve threads completed " + 
                (exitcode == 0 ? " successfully" : " with error"));

            //
            //  check queries
            //
            log("getting/checking post-store query values");
            for (int i=0; i<sizes.length; i++) {
                ct = threads[i];

                if (ct.oid == null)
                    continue;

                int cnt = countOIDs(TYPE_LONG, Long.toString(sizes[i]), i);
                if (cnt != sizeCounts[i] + 1)
                    throw new Exception("bad " + TYPE_LONG + " for size " +
                                        sizes[i] + ": " + cnt +
                                        " expected " + (sizeCounts[i]+1));
                cnt = countOIDs(TYPE_DOUBLE, 
                                         Double.toString(Math.sqrt(sizes[i])),
                                         i);
                if (cnt != doubleCounts[i] + 1)
                    throw new Exception("bad " + TYPE_DOUBLE + " for size " +
                                        sizes[i] + ": " + cnt +
                                        " expected " + (doubleCounts[i]+1));
            }
            timeCount = countOIDs(TYPE_STRING, timeStr, -2);
            if (timeCount != sizes.length)
                throw new Exception("bad " + TYPE_STRING + ": should be " + 
                                    sizes.length + " under this date: " +
                                    timeCount + " [" + timeStr + "]");

            //
            //  delete/check
            //
            log("delete/retrieve check");
            for (int i=0; i<threads.length; i++) {
                ct = threads[i];

                if (ct.oid != null) {
                    archive.delete(ct.oid);
                    boolean error = false;
                    try {
                        NullWriteChannel wch = new NullWriteChannel();
                        archive.retrieveObject(ct.oid, wch);
                        // TBD: could check data of wrongly-retrieved file
                    } catch (NoSuchObjectException expected) {
                        error = true;
                    } catch (Exception e) {
                        throw new Exception(
                               "wrong err on retrieve deleted file, size=" + 
                               ct.size + " oid=" + ct.oid + " exception=" + e);
                    }
                    if (!error)
                        throw new Exception("retrieved deleted file, size=" + 
                                            ct.size + " oid=" + ct.oid);
                    log("deletd " + ct.size + " as " + ct.oid);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            exitcode = 1;
        }
        log(storeStats.toString());
        log(retrvStats.toString());
        log("exit status: " + exitcode + (exitcode == 0 ? " OK" : " FAILED"));
        System.exit(exitcode);
    }

    private static void getopt(String[] argv) {
        if (argv.length != 1)
            usage(1);
        if (argv[0].equals("-h")  ||  argv[0].equals("--help"))
            usage(0);
        
        address = argv[0];
    }

    SimpleDateFormat df = new SimpleDateFormat("MM/dd HH:mm:ss");
    private void log(String msg) {
        System.out.println(df.format(new Date()) + ": " + msg);
    }

    private void schemaCheck(StringBuffer sb, HashMap hm, String typeName, 
                             Class cl) {

        NameValueSchema.ValueType vt = (NameValueSchema.ValueType)
                                            hm.get(typeName);
        if (vt == null) {
            sb.append("\tmissing: ").append(typeName).append("\n");
        } else if (vt.javaType != cl) {
            sb.append("\twrong type: ").append(typeName);
            sb.append(" got " + vt.javaType);
            sb.append(" expected " + cl + "\n");
        }
    }

    private int countOIDs(String type, String value, int verify) 
                                                            throws Exception {
        boolean checkoff[] = null;
        if (verify == -2) {
            checkoff = new boolean[sizes.length];
            for (int i=0; i<checkoff.length; i++)
                checkoff[i] = false;
        }
        int count = 0;
        String query;
        if (type.equals(TYPE_STRING))
            query = type + " = '" + value + "'";
        else
            query = type + " = " + value;
        QueryResultSet qrs = archive.query(query, 50);
        while (qrs.next()) {
            count++;
            if (verify == -1)
                continue;
            ObjectIdentifier oid = qrs.getObjectIdentifier();
            if (verify == -2) {
                for (int i=0; i<checkoff.length; i++) {
                    if (threads[i].oid.equals(oid)) {
                        checkoff[i] = true;
                        break;
                    }
                }
                continue;
            }
            if (oid.equals(threads[verify].oid))
                verify = -1;
        }
        if (verify == -2) {
            for (int i=0; i<checkoff.length; i++) {
                 if (!checkoff[i])
                    throw new Exception("oid not in query results [" + query + 
                                        "]: size=" + threads[i].size +
                                        " oid=" + threads[i].oid);
            }
        }
        if (verify > -1)
            throw new Exception("query [" + query + 
                                "] not found: size=" + threads[verify].size +
                                " oid=" + threads[verify].oid);
        return count;
    }

    class ClientThread extends Thread {

        long size;
        private boolean error = false;
        ObjectIdentifier oid = null;

        ClientThread(long size) {
            this.size = size;
        }

        boolean hasError() {
            return error;
        }

        public void run() {
            log("size " + size + " starting");
            try {
                //
                //  store
                //
                NullReadChannel rch = new NullReadChannel(size);
                NameValueRecord nvr = archive.createRecord();
                nvr.put(TYPE_LONG, size);
                nvr.put(TYPE_DOUBLE, Math.sqrt(size));
                nvr.put(TYPE_STRING, timeStr);
                //nvr.put("system.test.type_date", new java.sql.Date());
                //nvr.put("system.test.type_time", run_time);

                long t1 = System.currentTimeMillis();
                SystemRecord sr = archive.storeObject(rch, nvr);
                storeStats.add(System.currentTimeMillis()-t1, size);

                if (sr == null)
                    throw new Exception("store returned null record, size=" +
                                        size);
                oid = sr.getObjectIdentifier();
                if (oid == null)
                    throw new Exception("store record returned null oid, size="+
                                        size);
                String out = oid.toString();
                log("stored " + size + " as " + out);

                //
                //  retrieve/check
                //
                NullWriteChannel wch = new NullWriteChannel();

                t1 = System.currentTimeMillis();
                archive.retrieveObject(oid, wch);
                retrvStats.add(System.currentTimeMillis()-t1, size);

                if (wch.getError())
                    throw new Exception("retrieved bytes don't match, size=" + 
                                       size + " oid=" + out);
                if (wch.getSize() != size)
                    throw new Exception("retrieved size doesn't match, size=" + 
                                       size + " rtrvd=" + wch.getSize() +
                                       " oid=" + out);
                log("rtrved " + size + " as " + out);

            } catch (Exception e) {
                //e.printStackTrace();
                error = true;
                log("size " + size + " got " + e);
            } finally {
                log("size " + size + " done");
            }
        }
    }

    /**
     *  A lightweight source for bytes to store; depends
     *  on bytes[] array being initted by main().
     */
    class NullReadChannel implements ReadableByteChannel {
        private boolean closed = false;
        long size;
        byte last = 0;

        NullReadChannel(long size) {
            this.size = size;
        }
        public void close() throws IOException {
            if (closed)
                throw new IOException("attempted close on closed channel");
            closed = true;
        }
        public boolean isOpen() {
            return !closed;
        }
        public int read(ByteBuffer dst) throws IOException {
            if (size < 1)
                return -1;

            //
            //  pick max size
            //
            long copy = size;
            if (dst.capacity() < copy)
                copy = dst.capacity();
            if (bytes.length < copy)
                copy = bytes.length;
            //
            //  round down to mod 256
            //  to keep sequence for check
            //  in NullWriteChannel
            //
            if (copy != size) {
                long div = copy / 256;
                if (div == 0) {
                    System.err.println("UNEXPECTED div==0");
                    System.exit(1);
                }
                copy = div * 256;
            }

            dst.put(bytes, 0, (int)copy);
            size -= copy;
            return (int) copy;
        }
    }

    class NullWriteChannel implements WritableByteChannel {
        private boolean closed = false;
        private long written = 0;
        private boolean error = false;
        private byte last = -1;
        private byte buf[] = new byte[1024];
        NullWriteChannel() {
        }
        public void close() throws IOException {
            if (closed)
                throw new IOException("attempted close on closed channel");
            closed = true;
        }
        public boolean isOpen() {
            return !closed;
        }
        public int write(ByteBuffer src) throws IOException {
            if (closed)
                throw new IOException("attempted write on closed channel");
            int count = 0;
            while (src.hasRemaining()) {
                //
                //  get bytes
                //
                int copy = src.remaining();
                if (copy > buf.length)
                    copy = buf.length;
                src.get(buf, 0, copy);

                //
                //  check bytes
                //
                for (int i=0; i<copy; i++) {
                    last++;
                    if (buf[i] != last)
                        error = true;
                }
                count += copy;
            }
            written += count;
            return count;
        }
        public boolean getError() {
            return error;
        }
        public long getSize() {
            return written;
        }
    }


    /**
     *  Accumulate bandwidth statistics in units of bytes/sec; optionally
     *  accumulate BandwidthStatistic on each size for the times; plus 
     *  methods for formatting bandwidth.
     */
    class BandwidthStatistic {
    
        ////////////////////////////////////////////////////////////////////////
        //  static utility methods
        //
        private DecimalFormat dFormat = new DecimalFormat("#######0.0##");
    
        /**
         *   Return a string that represents megs per second.
         *   'Q' means 'quiet' - return 0 if time is 0.
         */
        public String toMBPerSecQ(long bytes, long msecs) {
            if (msecs == 0)
                return "0";
            // multiply in constants while converting to double
            // (multiply preferred to divide in speed & also precision
            // since the divide will involve bigger numbers.. hand wave..)
            double time = (double)msecs * 1024.0 * 1024.0;
            double size = (double)bytes * 1000.0;
    
            return dFormat.format(size / time);
        }
    
        public String toMBPerSec(long bytes, long msecs) {
            if (msecs == 0)
                return "undefined";
    
            // multiply in constants while converting to double
            // (multiply preferred to divide in speed & also precision
            // since the divide will involve bigger numbers.. hand wave..)
            double time = (double)msecs * 1024.0 * 1024.0;
            double size = (double)bytes * 1000.0;
    
            return dFormat.format(size / time) + " MB/sec";
        }
    
        /**
         *   Return a string that represents gigs per day.
         */
        public String toGBPerDay(long bytes, long msecs) {
            if (msecs == 0)
                return "undefined";
            // multiply in constants while converting to double
            // (multiply preferred to divide in speed & also precision
            // since the divide will involve bigger numbers.. hand wave..)
            double time = (double)msecs * 1024.0 * 1024.0 * 1024.0;
            double size = (double)bytes * 1000.0 * 60 * 60 * 24;
    
            return dFormat.format(size / time) + " GB/day";
        }
    
        ///////////////////////////////////////////////////////////////////////
        //  (non-static) measurement accumulator
        //
    
        public String name;
        private int N = 0;
        private long total_bytes = 0;
        private long total_time_ms = 0;
        private long max_sz, min_sz;
        private long min_bw, max_bw;
        private Hashtable per_size = null;
        private LinkedList per_size_stats = null;
        private int rejected = 0;
        private long rejected_size = 0;
        private double rms_acc = 0.0;
    
        public BandwidthStatistic(String name) {
            this.name = name;
        }
    
        /**
         *  Option to specify per-size stats.
         */
        public BandwidthStatistic(String name, boolean per_size) {
            this.name = name;
            if (per_size) {
                this.per_size = new Hashtable();
                this.per_size_stats = new LinkedList();
            }
        }
    
        private BandwidthStatistic getPerSize(long bytes) {
            return getPerSize(new Long(bytes));
        }
        private BandwidthStatistic getPerSize(Long key) {
            BandwidthStatistic bst = (BandwidthStatistic) per_size.get(key);
            if (bst == null) {
                bst = new BandwidthStatistic(key.toString());
                per_size.put(key, bst);
                per_size_stats.add(bst);
            }
            return bst;
        }
    
        /**
         *  Accumulate a measurement.
         */
        public void add(long time_ms, long bytes) {
    
            if (per_size != null) {
                BandwidthStatistic bst = getPerSize(bytes);
                bst.add(time_ms, bytes);
            }
    
            if (time_ms == 0) {
                rejected++;
                rejected_size += bytes;
                return;
            }
    
            long rate = (1000 * bytes) / time_ms;
            double rate_sq = rate * rate;
            rms_acc += rate_sq;
    
            this.total_time_ms += time_ms;
            this.total_bytes += bytes;
            N++;
    
            if (N == 1) {
                min_bw = max_bw = rate;
                min_sz = max_sz = bytes;
            } else {
    
                if (rate < min_bw)
                    min_bw = rate;
                else if (rate > max_bw)
                    max_bw = rate;
    
                if (bytes < min_sz)
                    min_sz = bytes;
                else if (bytes > max_sz)
                    max_sz = bytes;
            }
        }
    
        /**
         *  Add data from another BandwidthStatistic - wipe out per-size if
         *  this has it and the added one BandwidthStatistic's lacks it.
         */
        public void add(BandwidthStatistic bs) {
            if (bs.getN() == 0)
                return;
            rejected += bs.getRejected();
            rejected_size += bs.getRejectedSize();
            total_time_ms += bs.total_time_ms();
            total_bytes += bs.total_bytes();
            rms_acc += bs.rms_acc;
            N += bs.getN();
    
            if (N == 0) {
                min_bw = bs.min_bw();
                max_bw = bs.max_bw();
                min_sz = bs.min_sz();
                max_sz = bs.max_sz();
            } else {
                long t = bs.min_bw();
                if (t < min_bw)
                    min_bw = t;
                t = bs.max_bw();
                if (t > max_bw)
                    max_bw = t;
                t = bs.min_sz();
                if (t < min_sz)
                    min_sz = t;
                t = bs.max_sz();
                if (t > max_sz)
                    max_sz = t;
            }
    
            if (per_size != null) {
                Iterator it = bs.getPerSizeStats();
                if (it == null) {
                    //
                    //  wipe out per_size because it will be incomplete
                    //
                    per_size = null;
                    per_size_stats = null;
                } else {
                    //
                    //  add per-size stats to this
                    //
                    while (it.hasNext()) {
                        BandwidthStatistic bst = (BandwidthStatistic) it.next();
                        Long key = Long.decode(bst.name);
                        BandwidthStatistic bst2 = getPerSize(key);
    
                        bst2.N += bst.N;
                        bst2.rejected += bst.rejected;
                        bst2.rejected_size += bst.rejected_size;
                        bst2.total_bytes += bst.total_bytes;
                        bst2.total_time_ms += bst.total_time_ms;
                        if (bst.min_bw < bst2.min_bw)
                            bst2.min_bw = bst.min_bw;
                        if (bst.max_bw > bst2.max_bw)
                            bst2.max_bw = bst.max_bw;
                        bst2.rms_acc += bst.rms_acc;
                    }
                }
            }
        }
    
        /**
         *  Return bytes/sec.
         */
        public long average_bw() {
            if (total_time_ms == 0)
                return -1;
            return (1000 * total_bytes) / total_time_ms;
        }
    
        /**
         *  Return MB/sec as String.
         */
        public String averageBW_MB() {
            return toMBPerSec(total_bytes, total_time_ms);
        }
    
        /**
         *  Return root-mean-square in bytes/sec.
         */
        public double rms_bw() {
            return Math.sqrt(rms_acc) / (double)N;
        }
        /**
         *  Return root-mean-square in MB/sec.
         */
        public double rmsBW_MB() {
            return rms_bw() / ((double)1024 * 1024);
        }
    
        /**
         *  Return standard deviation in bytes/sec.
         *      sqrt( avg_of_squares - avg_squared )
         *      = sqrt( sum(val^2)/N - (sum(val)/N)^2 )
         */
        public double std_dev_bw() {
            if (N == 0)
                return 0.0;
            double tmp = average_bw();
            tmp *= tmp;    //avg^2
            tmp = rms_acc/N - tmp;
//System.out.println("std_dev_bw sqrt(" + tmp + ")");
            return Math.sqrt(tmp);
        }
    
        /**
         *  Return standard deviation in MB/sec.
         */
        public double std_dev_BW_MB() {
            return std_dev_bw() / ((double)1024 * 1024);
        }
    
        /**
         *  Return average size.
         */
        public long average_sz() {
            return total_bytes / N;
        }
        public int getN() { return N; }
        public int getRejected() { return rejected; }
        public long getRejectedSize() { return rejected_size; }
        public int getTotalCases() { return N + rejected; }
        public long total_time_ms() { return total_time_ms; }
        public long total_bytes() { return total_bytes; }
        public long min_bw() { return min_bw; }
        public long max_bw() { return max_bw; }
        public long min_sz() { return min_sz; }
        public long max_sz() { return max_sz; }
    
        /**
         *  Get Iterator over per-size BandwidthStatistic's.
         */
        public Iterator getPerSizeStats() {
            if (per_size == null)
                return null;
            return per_size_stats.iterator();
        }
    
        /**
         *  Print name and stats (using MB/sec).
         */
        public String toString() {
            if (N == 0)
                return name + ": no cases";
    
            StringBuffer sb = new StringBuffer();
            sb.append(name).append(": avg_bw ");
            sb.append(toMBPerSec(total_bytes, total_time_ms));
            sb.append(" dev ").append(dFormat.format(std_dev_BW_MB()));
            sb.append("  N ").append(N);
            if (rejected > 0) {
                sb.append(" rejected (time==0) ").append(rejected);
                sb.append(" total size ").append(rejected_size);
            }
            if (per_size != null) {
                Iterator it = getPerSizeStats();
                while (it.hasNext()) {
    
                    BandwidthStatistic bst = (BandwidthStatistic) it.next();
    
                    sb.append("\n\tsize ").append(bst.name);
                    sb.append("  avg_bw ").append(bst.average_bw());
                    sb.append(" dev ").append(dFormat.format(bst.std_dev_bw()));
                    sb.append(" (").append(bst.averageBW_MB());
                    sb.append(" dev ").append(dFormat.format(bst.std_dev_BW_MB()));
                    sb.append(")  N ").append(bst.getN());
                }
            }
    
            return sb.toString();
        }
    }
}
