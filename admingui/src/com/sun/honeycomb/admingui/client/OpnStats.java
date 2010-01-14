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



package com.sun.honeycomb.admingui.client;

/**
 * encapsulates performance statistics for a particular operation type
 */
public class OpnStats {

    public static final int OP_STORE_DATA    = 0;
    public static final int OP_STORE_MD      = 1;
    public static final int OP_STORE_BOTH    = 2;
    public static final int OP_RETRIEVE_DATA = 3;
    public static final int OP_RETRIEVE_MD   = 4;
    public static final int OP_QUERY         = 5;
    public static final int OP_DELETE        = 6;
    public static final int OP_WEBDAV_PUT    = 7;
    public static final int OP_WEBDAV_GET    = 8;

    protected int opnType;
    protected double kbytesPerSec = 0, opsPerSec = 0;
    protected int ops = 0;
    

    public OpnStats(int opnType,
        double kbytesPerSec, int ops, double opsPerSec) {

        this.opnType = opnType;
        this.kbytesPerSec = kbytesPerSec;
        this.ops = ops;
        this.opsPerSec = opsPerSec;
    }

    public OpnStats(int opnType, String statsStr) {
        
        this.opnType = opnType;
        String[] stats = statsStr.split(",");
        if (stats == null || stats.length < 3)
            return;
        try {
            if (opnType != OP_QUERY && opnType != OP_DELETE)
                this.kbytesPerSec = Double.parseDouble(stats[0]);
            this.ops = Integer.parseInt(stats[1]);
            this.opsPerSec = Double.parseDouble(stats[2]);
        } catch (Exception e) {
            System.out.println("Bad format for OpnStats type " + opnType + ":"
                + e);
        }
    }

    public int getOpnType() { return opnType; }
    public double getKBPerSec() { return kbytesPerSec; }
    public int getOps() { return ops; }
    public double getOpsPerSec() { return opsPerSec; }
    
    public String toString() {
        return "OpnStat[" + opnType + "," + kbytesPerSec
                + "," + ops + "," + opsPerSec + "]";
    }
}