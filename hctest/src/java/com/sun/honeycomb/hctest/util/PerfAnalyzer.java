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

import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.test.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.io.*;
import java.util.regex.*;
import java.sql.Timestamp;

public class PerfAnalyzer {

    public static final String DATE_FORMAT = "yyyy MMM dd hh:mm:ss";
    public static final String INPUT_DATE_FORMAT = "MMM dd hh:mm:ss";

    private final static int MILLISECONDS = 0;
    private final static int SECONDS = 1;
    private final static int MINUTES = 2;
    private final static int HOURS = 3;

    private static int format = MILLISECONDS;

    AuditDBMetricsClient db = null;
    private long run_id = -1;
    private static boolean per_size = false;
    private static boolean verbose = false;

    //long slot_width = 1 * 60 * 1000; // 1 min
    long slot_width = 10 * 1000; // 10 sec

    Timestamp start, end;

    Hashtable diskStart = new Hashtable();
    HashMap diskEnd = new HashMap();
    Hashtable loadStart = new Hashtable();
    HashMap loadEnd = new HashMap();

    ObjectCounter all_hosts = new ObjectCounter();
    ObjectCounter store_hosts = new ObjectCounter();
    ObjectCounter retrieve_hosts = new ObjectCounter();

    Calendar cal = new GregorianCalendar(); 

    /**
     *  User-supplied start/end.
     */
    public PerfAnalyzer(Timestamp start, Timestamp end) throws Throwable {

        db = new AuditDBMetricsClient();
        db.setVerbosity(verbose);
        this.start = start;
        this.end = end;
    }

    /**
     *  Start/end derived from user-supplied run id.
     */
    public PerfAnalyzer(long run_id) throws Throwable {
        db = new AuditDBMetricsClient();
        db.setVerbosity(verbose);
        this.run_id = run_id;
        this.start = db.getRunStart(run_id);
        this.end = db.getRunEnd(run_id);
    }


    private static void usage() {
        usage(null);
    }
    private static void usage(String s) {
        if (s != null)
            System.err.println(s);
        System.err.println("Usage: java PerfAnalyzer -c cluster [-i interval]" +
                                " [-ops] [-clnt] [-clu] [-all] [-m|-h] [-S] [-v]");
        System.err.println("-i: interval in seconds for -clnt (default 5)");
        System.err.println("-m: x axis in decimal minutes (default milliseconds)");
        System.err.println("-h: x axis in decimal hours");
        System.err.println("-S: report per-size ops performance");
        System.err.println("-ops: aggregate per store, retrieve etc. operation");
        System.err.println("-clnt: aggregate clnt bandwidth");
        System.err.println("-clu: cluster internal measurements");
        System.err.println("-all: ops, clnt, clu");
        System.err.println("       AND:");
        System.err.println("              -r run_id");
        System.err.println("       OR:");
        System.err.println("              -s start_time -e end_time");
        System.err.println(" *_time like this: " + INPUT_DATE_FORMAT);
        System.exit(1);
    }

    /** main is for test and ?? */
    public static void main(String args[]) {

        /////////////////////////////////////////////////
        //  args

        if (args.length == 0  ||  args.length > 18)
            usage();

        //
        //  options
        //
        long run_id = -1;
        String start_time = null;
        String end_time = null;
        String cluster = null;

        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);

        long interval = 5;
        boolean ops = false;
        boolean clnt = false;
        boolean clu = false;

        int type = 0;
        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-c")) {
                if (cluster != null)
                    usage();
                if (args.length < i+2)
                    usage();
                cluster = args[i+1];
                i++;
            } else if (args[i].equals("-m")) {
                format = MINUTES;
            } else if (args[i].equals("-h")) {
                format = HOURS;
            } else if (args[i].equals("-ops")) {
                ops = true;
            } else if (args[i].equals("-clnt")) {
                clnt = true;
            } else if (args[i].equals("-clu")) {
                clu = true;
            } else if (args[i].equals("-all")) {
                ops = true;
                clnt = true;
                clu = true;
            } else if (args[i].equals("-i")) {
                interval = Long.parseLong(args[i+1]);
                i++;
                if (interval < 1)
                    usage();
            } else if (args[i].equals("-S")) {
                per_size = true;
            } else if (args[i].equals("-v")) {
                verbose = true;
            } else if (args[i].equals("-s")) {
                if (type == 2)
                    usage();
                type = 1;
                if (start_time != null)
                    usage();
                if (args.length < i+4)
                    usage();
                start_time = year + " " +
                             args[i+1] + " " + args[i+2] + " " + args[i+3];
                i += 3;
            } else if (args[i].equals("-e")) {
                if (type == 2)
                    usage();
                type = 1;
                if (end_time != null)
                    usage();
                if (args.length < i+4)
                    usage();
                end_time = year + " " +
                           args[i+1] + " " + args[i+2] + " " + args[i+3];
                i += 3;
            } else if (args[i].equals("-r")) {
                if (type == 1)
                    usage();
                type = 2;
                run_id = Long.parseLong(args[i+1]);
                i++;
            } else {
                usage("unexpected: " + args[i]);
            }
        }
        if (cluster == null)
            usage();
        if (type == 1  && (start_time == null  ||  end_time == null))
            usage();
        if (!ops  &&  !clnt  && !clu)
            usage();

        TestRunner.setProperty(HoneycombTestConstants.PROPERTY_CLUSTER,
                               cluster);

        try {
            PerfAnalyzer pa = null;
            if (type == 1) {
                // format: May 24 19:09:33 (current year assumed)
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
                Date d = sdf.parse(start_time);
                Timestamp start = new Timestamp(d.getTime());
                d = sdf.parse(end_time);
                Timestamp end = new Timestamp(d.getTime());

                pa = new PerfAnalyzer(start, end);
            } else {
                pa = new PerfAnalyzer(run_id);
            }
            StringBuffer sb = new StringBuffer();
            if (clnt)
                pa.clientPerf(sb, interval * 1000);
            if (ops)
                pa.opPerf(sb);
            if (clu)
                sb.append("cluster internal: not implemented\n");
            System.out.println(sb.toString());
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public void clientPerf(StringBuffer sb, long interval) {

        this.slot_width = interval;

        long slot_start = 0; 
        long slot_end = 0;
        long slot_time = 0;
        long write_bytes = 0;
        long read_bytes = 0;
        long last_time = 0;

        LinkedList ll;
        try {
            ll = db.getPerfRecords(start, end);
        } catch (Throwable t) {
            sb.append(t.toString());
            return;
        }
        sb.append("\n#time___\twrite\tread\n");
        Iterator it = ll.iterator();
        int count = 0;
        while (it.hasNext()) {
            count++;
            AuditDBMetricsClient.PerfRecord pr = 
                                    (AuditDBMetricsClient.PerfRecord) it.next();
            if (slot_start == 0) {
                slot_start = pr.meas_time;
                slot_end = pr.meas_time + slot_width;
                slot_time = (slot_end + slot_start) / 2;
            } else if (pr.meas_time > slot_end) {
                finishSlot(sb, 
                           pr.meas_time - slot_end > slot_width, 
                           pr.meas_time,
                           slot_time, 
                           write_bytes, read_bytes, 
                           last_time - slot_start);

                // XXX possibly add any 0-data slots between
                // last & current

                slot_start = pr.meas_time;
                slot_end = pr.meas_time + slot_width;
                slot_time = (slot_end + slot_start) / 2;
                write_bytes = 0;
                read_bytes = 0;
            }
            last_time = pr.meas_time;
            switch (pr.type) {
                case AuditDBMetricsClient.STORE:
                    write_bytes += pr.bytes;
                    break;
                case AuditDBMetricsClient.RETRIEVE:
                    read_bytes += pr.bytes;
                    break;
            }
        }
        if (write_bytes + read_bytes > 0)
            finishSlot(sb, false, 0, slot_time, write_bytes, read_bytes, 
                       last_time - slot_start);
        sb.append("\n");
    }

    private long base = 0;
    private long ptime(long time) {
        if (base == 0)
            base = time;
        time -= base;
        return time;
    }

    private void finishSlot(StringBuffer sb, boolean add_zero, long zero_time,
                            long time, 
                            long write_bytes, long read_bytes, long interval) {

        long pt = ptime(time);
        append(sb, pt, write_bytes, read_bytes, interval);
        if (add_zero) {
            if (write_bytes + read_bytes > 0)
                append(sb, pt+1, 0, 0, 0);
            append(sb, ptime(zero_time)-1, 0, 0, 0);
        }
    }
    private void append(StringBuffer sb, long time, 
                        long write_bytes, long read_bytes, long interval) {

        // time version.. Wed Oct 26 14:46:17 PDT 2005
        //cal.setTime(new Date(time));
        //sb.append(twoDigit(cal.get(Calendar.HOUR_OF_DAY))).append(":");
        //sb.append(twoDigit(cal.get(Calendar.MINUTE))).append(":");
        //sb.append(twoDigit(cal.get(Calendar.SECOND)));

        if (format == MINUTES)
            sb.append((double)time/60000.0);
        else if (format == HOURS)
            sb.append((double)time/3600000.0);
        else
            sb.append(time);
        sb.append("    ").append(
                      BandwidthStatistic.toMBPerSecQ(write_bytes, interval));
        sb.append("    ").append(
                      BandwidthStatistic.toMBPerSecQ(read_bytes, interval));
        sb.append("\n");
    }

    private String twoDigit(int num) {
        String s = Integer.toString(num);
        if (num > 9)
            return s;
        return "0" + s;
    }

    private void opPerf(StringBuffer sb) throws Throwable {
        if (run_id == -1) {
            sb.append("opPerf(): need run_id\n");
            return;
        }

        //
        //  gather data
        //
        BandwidthStatistic meas_store =
                            new BandwidthStatistic("store", per_size);
        BandwidthStatistic meas_store_md =
                            new BandwidthStatistic("store_md", per_size);
        BandwidthStatistic meas_retrieve =
                            new BandwidthStatistic("retrieve", per_size);
        BandwidthStatistic meas_retrieve_range =
                            new BandwidthStatistic("retrieve_range", per_size);
        Statistic meas_retrieve_md_only = new Statistic("retrieve_md_only");
        Statistic meas_link = new Statistic("link");
        Statistic meas_delete = new Statistic("delete");

        int count = 0;
        int unexpected = 0;

        LinkedList ll = db.getOpRecords(run_id);
        Iterator it = ll.iterator();

        while (it.hasNext()) {
            count++;
            AuditDBMetricsClient.OpRecord or = 
                                    (AuditDBMetricsClient.OpRecord) it.next();
            switch (or.op_type) {
                case AuditDBClient.OP_STORE:
                    if (or.has_metadata)
                        meas_store_md.add(or.duration, or.num_bytes);
                    else
                        meas_store.add(or.duration, or.num_bytes);
                    break;
                case AuditDBClient.OP_RETRIEVE:
                    meas_retrieve.add(or.duration, or.num_bytes);
                    break;
                case AuditDBClient.OP_RETRIEVE_RANGE:
                    meas_retrieve_range.add(or.duration, or.num_bytes);
                    break;
                case AuditDBClient.OP_RETRIEVE_MD:
                    meas_retrieve_md_only.addValue(or.duration);
                    break;
                case AuditDBClient.OP_LINK:
                    meas_link.addValue(or.duration);
                    break;
                case AuditDBClient.OP_DELETE:
                    meas_delete.addValue(or.duration);
                    break;
                default:
                    unexpected++;
            }
        }

        //
        //  convert data to string
        //
        if (meas_store.getN() > 0)
            sb.append(meas_store.toString()).append("\n");
        if (meas_store_md.getN() > 0)
            sb.append(meas_store_md.toString()).append("\n");
        if (meas_retrieve.getN() > 0)
            sb.append(meas_retrieve.toString()).append("\n");
        if (meas_retrieve_range.getN() > 0)
            sb.append(meas_retrieve_range.toString()).append("\n");
        if (meas_retrieve_md_only.getN() > 0)
            sb.append(meas_retrieve_md_only.toString()).append("\n");
        if (meas_link.getN() > 0)
            sb.append(meas_link.toString()).append("\n");
        if (meas_delete.getN() > 0)
            sb.append(meas_delete.toString()).append("\n");
        sb.append("\n");
    }
}
