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



package com.sun.honeycomb.test.util;

import java.util.*;
import java.text.DecimalFormat;

/**
 *  Accumulate bandwidth statistics in units of bytes/sec; optionally
 *  accumulate Statistic on each size for the times; plus static methods
 *  for formatting bandwidth.
 */

public class BandwidthStatistic {

    ////////////////////////////////////////////////////////////////////////
    //  static utility methods
    //
    private static DecimalFormat dFormat = new DecimalFormat("#######0.0##");

    /**
     *   Return a string that represents megs per second.
     *   'Q' means 'quiet' - return 0 if time is 0.
     */
    public static String toMBPerSecQ(long bytes, long msecs) {
        if (msecs == 0)
            return "0";
        // multiply in constants while converting to double
        // (multiply preferred to divide in speed & also precision
        // since the divide will involve bigger numbers.. hand wave..)
        double time = (double)msecs * 1024.0 * 1024.0;
        double size = (double)bytes * 1000.0;

        return dFormat.format(size / time);
    }

    public static String toMBPerSec(long bytes, long msecs) {
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
    public static String toGBPerDay(long bytes, long msecs) {
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
        sb.append(name).append(": avg_bw ").append(average_bw());
        sb.append(" dev ").append(dFormat.format(std_dev_bw()));
        sb.append(" (").append(toMBPerSec(total_bytes, total_time_ms));
        sb.append(" dev ").append(dFormat.format(std_dev_BW_MB())).append(")");
        sb.append("  min_bw ").append(min_bw);
        sb.append("  max_bw ").append(max_bw);
        sb.append("  avg_sz ").append(average_sz());
        sb.append("  min_sz ").append(min_sz);
        sb.append("  max_sz ").append(max_sz);
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
