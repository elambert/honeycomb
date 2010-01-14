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



package com.sun.honeycomb.multicell.lib;

import com.sun.honeycomb.common.UID;

import java.util.Random;

public class Rule implements java.io.Serializable {

    static private final short INTERVAL_START_DEFAULT = 0;
    static private final short INTERVAL_STOP_DEFAULT = 32767;
    static private final long  INTERVAL_INITIAL_CAPACITY_DEFAULT = 0;
    static private final byte  RULE_NB_DEFAULT = 1;


    static private Random generator = null;

    private byte originCellid;
    private byte ruleNumber;
    private Interval interval;

    // Default rule
    public Rule(byte originCellid) {
        Interval defaultInterval = new Interval(INTERVAL_START_DEFAULT,
                                                INTERVAL_STOP_DEFAULT,
                                                INTERVAL_INITIAL_CAPACITY_DEFAULT);
        this.originCellid = originCellid;
        this.ruleNumber = ruleNumber;
        this.interval = defaultInterval;
    }

    public Rule(byte originCellid, byte ruleNumber, Interval interval) {
        this.originCellid = originCellid;
        this.ruleNumber = ruleNumber;
        this.interval =  interval;
    }

    public Rule(Rule copy) {
        this.originCellid = copy.getOriginCellid();
        this.ruleNumber = copy.getRuleNumber();
        this.interval =  copy.getInterval();
    }

    public void resetOriginCellid(byte newCellid) {
        this.originCellid = newCellid;        
    }

    public byte getOriginCellid() {
        return originCellid;
    }

    public byte getRuleNumber() {
        return ruleNumber;
    }

    public Interval getInterval() {
        return interval;
    }

    public boolean equals(Rule rule) {
        if (originCellid != rule.getOriginCellid()) {
            return false;
        }
        if (ruleNumber != rule.getRuleNumber()) {
            return false;
        }
        return interval.equals(rule.getInterval());
    }

    static public class Interval implements java.io.Serializable {

        //
        // We split the interval into NB_PARTS for the
        // allocation of the 'silolocation'
        //
        static public final short NB_PARTS = 100;


        private short  start;
        private short  end;
        private short  distance;
        private long   initialCapacity;


        public Interval(short start, short end, long capacity) {
            this.start = start;
            this.end = end;
            this.initialCapacity = capacity;

            distance = (short) ((end - start) / NB_PARTS);
            if (distance == 0) {
                throw new MultiCellLibError("cannot discretize the interval [" +
                                            start + "..." + end + "]");
            }
        }

        public short getStart() {
            return start;
        }

        public short getEnd() {
            return end;
        }

        public long getInitialCapacity() {
            return initialCapacity;
        }

        public short getDistance() {
            return distance;
        }

        public boolean equals(Interval interval) {
            if (start != interval.getStart()) {
                return false;
            }
            if (end != interval.getEnd()) {
                return false;
            }
            if (initialCapacity != interval.getInitialCapacity()) {
                return false;
            }
            return true;
        }

        /**
         * Hash Algorithm as found in Brian Kernighan and Dennis
         * Ritchie's book "The C Programming Language".
         */
        protected long computeHash(String str) {
            long seed = 131; // 31 131 1313 13131 131313 etc..
            long hash = 0;
            int length = str.length();
            for(int i = 0; i < length; i++) {
                hash = (hash * seed) + str.charAt(i);
            }
            return (hash & 0x7FFFFFFF);
        }

        /**
         * XXX (TODO): Since silolocation is computed based on UID,
         * there is no need to store it in NewObjectIdentifier and
         * fragment file names. Compute silolocation from UID as
         * needed.
         */
        public synchronized short getNextSiloLocation(UID uid) {
            short hashValue = (short) (computeHash(uid.toString()) % NB_PARTS);
            return (short) (end - (short) (hashValue * distance));
        }
    }
}
