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

import java.util.ArrayList;

//
// Fixme: Only used by Statistics, which is only used in the doomed AdminServer.
// remove this when adminServer goes away (we switch to adm tree)
//

/**
 * Generates and tracks statistics for a given set of numbers.
 */
public class Statistics {
    private ArrayList _values = new ArrayList();

    private double _total; // total
    private double _min;   // floor
    private double _max;   // ceiling
    private double _avg;   // mean
    private double _sdSum; // running total of the sum of the mean diffs
    private double _var;   // variance
    private double _sd;    // standard deviation

    public Statistics () {
        super();

        _total = 0;
        _min   = Double.NaN;
        _max   = Double.NaN;
        _avg   = Double.NaN;
        _sdSum = 0;
        _var   = 0;
        _sd    = Double.NaN;
    }

    public long count() {
        return _values.size();
    }

    public double total() {
        return _total;
    }

    public double mean() {
        return _avg;
    }

    public long variance () {
        return Math.round (_var);
    }

    public double std_dev() {
        return _sd;
    }

    public double min() {
        return _min;
    }

    public double max() {
        return _max;
    }

    public double median() {
        assert false : "function not implemented";
        return Double.NaN;
    }

    public double mode() {
        assert false : "function not implemented";
        return Double.NaN;
    }

    public void add (double num) {
        _values.add (new Double (num));
        _total = _total + num;

        if (_values.size() == 1) {
            _min = num;
            _max = num;
            _avg = num;
            return;
        }

        // If we have more than 1 num, we can get other statistics
        _min = Math.min (_min, num);
        _max = Math.max (_max, num);

        double newavg =  _avg + (num - _avg) / _values.size();
        _sdSum = _sdSum + (num - _avg) * (num - newavg);
        _var = _sdSum / (_values.size() - 1);
        _sd = Math.sqrt (_var);
        _avg = newavg;
    }
}
