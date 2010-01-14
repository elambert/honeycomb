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



package com.sun.honeycomb.hctest.cases.interfaces;

import java.util.Random;
import com.sun.honeycomb.test.util.Log;

/**
 * This is a helper class that is used for specifying a range of filesizes
 * to use for a case and picking a random one (based on a seed) within
 * that size range.
 */
public class FileSizeCase {
    public static long seed = System.currentTimeMillis();
    public static Random r = null;
    public String name;
    public long min;
    public long max;

    FileSizeCase(long n) {
        name = FileSizeCases.lookupCaseName(n);
        min = n;
        max = n;
    }

    FileSizeCase(String n, long minimum, long maximum) {
        name = n;
        min = minimum;
        max = maximum;
    }

    /**
     * Return a size within the min and max range.
     * XXX The implementation isn't quite right...but it will do for now
     */
    public long getSize() {
        if (r == null) {
            Log.INFO("Using random seed " + seed);
            r = new Random(seed);
        }

        return ((long)((max - min) * r.nextDouble()) + min);
    }

    /**
     * Pick a seed to use so that we can repeat the randomness.
     */
    public static void setSeed(long newSeed) {
        if (r == null) {
            r = new Random();
        }

        seed = newSeed;
        Log.INFO("Setting seed to " +  seed);
        r.setSeed(seed);
    }
}
