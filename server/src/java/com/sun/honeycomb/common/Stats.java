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


package com.sun.honeycomb.common;

import java.util.StringTokenizer;
import java.util.NoSuchElementException;

/**
 * General statics object used for holding # of operations performed
 * and elapsed time of those operations.  Consumers of
 * the counters used by this class can calculate
 * <P>
 * <UL>
 * <LI># Ops</LI>
 * <LI>Avg Ops/sec</LI>
 * <LI>Response Time</LI>
 * </UL>
 */
public class Stats {

    protected long totalExecTime = 0;
    
    /*
     * # of operations performed. 
     *
     * HCPerfStatsAdapter will use this value to determine if any
     * activity has occurred between samplings.
     */
    protected long totalOps = 0;
    
    
    public Stats() {};
    

    
    /**
     * Get total # of operations perform to date.  This value can
     * be used to calculate # ops for a given time period and
     * # of ops per second using this formula.
     * <P>
     * <UL>
     * <LI># of Operations (Ops) = totalOps(2) - totalOps(1)</LI>
     * <LI>ElapsedTime (ET) = totalExecTime(2) - totalExecTime(1)</LI>
     * <LI>Avg Ops/sec = (Ops)/(T2 - T1)</LI>
     * <LI>Response Time = ET / Ops
     * </UL>
     * <P>
     * T1 = Time 1<BR>
     * T2 = Time 2 which is lattern than T1<BR>
     * (1) represents sample taken at T1<BR>
     * (2) represents sample taken at T2<BR>
     */
    public long getTotalOps() {
	return totalOps;
    }
    
    /**
     * Get the total execution time
     * @return long the total execution time.
     */
    public long getTotalExecTime() {
	return totalExecTime;
    }

    /**
     * @return String contents of this object
     */
    public String toString() {
	StringBuffer buf = new StringBuffer();
	buf.append("Total Operations: ").append(getTotalOps()).append("\n");
	buf.append("Total Exec Time: ").append(getTotalExecTime()).append("\n");
	return buf.toString();
    }
}
