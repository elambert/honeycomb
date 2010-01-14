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



/**
 *  This class accumulates bandwidth stats for a given number of measurements.
 *  XXX consider generalizing StatsAccumulator to handle N-dimensional 
 *  measurements so it could be used as a basis for this class.
 */

package com.sun.honeycomb.common;

import java.util.StringTokenizer;
import java.util.NoSuchElementException;

/**
 * General statics object used for capturing # of operations performed and 
 * throughput. Consumers of the counters used by this class can calculate
 * <P>
 * <UL>
 * <LI># Ops</LI>
 * <LI>Avg Ops/sec</LI>
 * <LI>bytes/sec transferrred</LI>
 * <LI>Response Time</LI>
 * </UL>
 */
public class BandwidthStatsAccumulator extends Stats {

    private long bytesProcessed = 0;
    
    public BandwidthStatsAccumulator() {
        super();
    }
    
    /**
     * Reconsistue the bandwidth stats from the stats
     * string retrieved from the alert tree
     * @param statsStr the stats string retrieved from the alert tree
     */
    public BandwidthStatsAccumulator(String statsStr) 
    throws NoSuchElementException, NumberFormatException {
	
	String[] st = statsStr.split(",");
	int count = st.length;
	if (count == 0) {
	    throw new NoSuchElementException("No performance data passed.");
	}
	if (count == 3) {
	    totalOps = Long.parseLong(st[0]);
	    totalExecTime = Long.parseLong(st[1]);
	    bytesProcessed = Long.parseLong(st[2]);
	    
	    // Check and handle overflow when reconsistuting the object
	    if (totalOps < 0)
		totalOps = (totalOps << 1 >> 1) + 1;
	    if (totalExecTime < 0)
		totalExecTime = (totalExecTime << 1 >> 1) + 1;
	    if (bytesProcessed < 0)
		bytesProcessed = (bytesProcessed << 1 >> 1) + 1;
	    return;
	}
        throw new NoSuchElementException("Unexpected performance data format:"
	    + statsStr);

	
    }

    /**
     * Add a performance measurement
     * @param sizeInBytes the # of bytes acted upon
     * @param execTime the amount of time in milliseconds it
     *    took to execute the perf action
     */
    synchronized public void add(long sizeInBytes, long execTime) {
	totalExecTime += execTime;
        totalOps++;
	bytesProcessed += sizeInBytes;
    }

    /**
     * Total # of bytes processed.  This value can
     * be used to calculate total throughput per second for a
     * given time period using this formula.
     * <P>
     * <UL>
     * <LI>throughput/sec = total(2) - totalN(1)/ (T2 - T1)</LI>
     * </UL>
     * <P>
     * T1 = Time 1<BR>
     * T2 = Time 2 which is lattern than T1<BR>
     * (1) represents sample taken at T1<BR>
     * (2) represents sample taken at T2<BR>
     * @return long total number of bytes processed to date  
     */
    public long getTotalBytesProcessed() {
        return bytesProcessed; 
    }
    
    /**
     * @return String the statistic string that is used to store the stats
     * in the alert tree.  This string can then be reconsisted via the
     * the constructor for this object
     */
    synchronized public String getStatsStr() {
	// NOTE: Any changes to this string will require changes to 
	// constructor that reconsistutes this object from the string
	// Values should be comma seperated.
	return new StringBuffer().append(totalOps)
	    .append(",").append(getTotalExecTime())
	    .append(",").append(getTotalBytesProcessed())
	    .toString();
    }

    /**
     * @return String contents of this object
     */
    public String toString() {
	StringBuffer buf = new StringBuffer(super.toString());
	buf.append("Total Bytes Processed:").append(getTotalBytesProcessed()).append("\n");
	return buf.toString();
    }
}
