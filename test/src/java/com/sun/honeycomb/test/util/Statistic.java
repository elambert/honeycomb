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

import java.text.DecimalFormat;

/**
 *  Calc and hold a statistic.
 */

public class Statistic {

    private static DecimalFormat dFormat = new DecimalFormat("#######0.0##");

    public String name;
    protected int N = 0;
    protected long total, min, max;
    protected double rms_acc = 0.0;

    public Statistic(String name) {
        this.name = name;
    }

    private void add(long val) {

        if (N == 0) {
            total = min = max = val;
        } else {
            total += val;
            if (val < min)
                min = val;
            else if (val > max)
                max = val;
        }
        N++;
    }

    public void addValue(long val) {
        add(val);
        rms_acc += (double) val * (double) val;
    }

    /**
     *  Merge numbers from another Statistic.
     */
    public void addStatistic(Statistic stat) {

        rms_acc += stat.rms_acc;
        total += stat.total;

        if (N == 0) {
            min = stat.min;
            max = stat.max;
        } else {
            if (stat.min < min)
                min = stat.min;
            if (stat.max > max)
                max = stat.max;
        }
        N += stat.N;
    }

    public long average() {
        if (N == 0)
            return -999999;
        return total / N;
    }
    public double rms() {
        if (N == 0)
            return -999999;
        return Math.sqrt(rms_acc / N);
    }

    /**
     *      sqrt( avg_of_squares - avg_squared )
     *      = sqrt( sum(val^2)/N - (sum(val)/N)^2 )
     */
    public double std_dev() {
        if (N < 2)
            return 0.0;
        double tmp = (double)average();
        tmp *= tmp;   // avg^2
        tmp = rms_acc/N - tmp;
        return Math.sqrt(tmp);
    }

    public int getN() { return N; }
    public long min() { return min; }
    public long max() { return max; }

    public String toString() {
        if (N == 0)
            return name + ": no cases";

        return name + ": avg " + average() + " dev " + dFormat.format(std_dev()) + 
                      "  min " + min + "  max " + max +
                      "  N " + N;
    }
}
