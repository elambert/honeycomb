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



package com.sun.honeycomb.hctest.hadb;

import java.util.ArrayList;
import java.util.Iterator;


public class PerformanceStatistic {
	
	
	PerformanceStatistic (int optype, String desc, boolean trackHistory) {
		switch (optype) {
		case OP_TYPE_INSERT:
			m_opTypeDesc = INSERT_OP_TYPE_DESC;
			break;
		case OP_TYPE_QUERY:
			m_opTypeDesc = QUERY_OP_TYPE_DESC;
			break;
		case OP_TYPE_DELETE:
			m_opTypeDesc = DELETE_OP_TYPE_DESC;
			break;
		default:
			m_opTypeDesc = "UNKOWN";
		}
		this.m_desc = desc;
		this.m_trackHistory = trackHistory;
		this.m_opType = optype;
		
	}
	
	PerformanceStatistic (int opType) {
		new PerformanceStatistic(opType, "", false);
	}
	
	String getDesc () {
		return m_desc;
	}
	
	synchronized void addOperation(long time) {
		addOperation(time, null);
	}

	synchronized void addOperation(long time, String description) {
		m_numberOfOps++;
		m_totalTime += time;
		if (m_quickestOp == -1 || time < m_quickestOp) {
			m_quickestOp = time;
			if (description != null) {
				m_minOpDesc = description;
			}
		}
		if (time > m_longestOp) {
			m_longestOp = time;
			if (description != null) {
				m_maxOpDesc = description;
			}
		}
		
		if (m_trackHistory) {
			m_opHistory.add(new Long(time));
		}
	}
	
	long getMax() {
		return m_longestOp;
	}
	
	long getMin() {
		return m_quickestOp;
	}
	
	double getAverage () {
		if (m_numberOfOps >= 1) {
			return ((double)m_totalTime)/((double)m_numberOfOps);
		} else {
			return 0;
		}
	}
	
	synchronized void addOperation(long time, String description, long recs) {
		m_recordsRetrieved += recs;
		this.addOperation(time,description);
	}
	
	void add(PerformanceStatistic that) {
		this.m_numberOfOps += that.m_numberOfOps;
		this.m_totalTime += that.m_totalTime;
		if (that.m_longestOp > this.m_longestOp) {
			this.m_longestOp = that.m_longestOp;
			this.m_maxOpDesc =  that.m_maxOpDesc;
		}
		if (this.m_quickestOp == -1 && that.m_quickestOp != -1) {
			this.m_quickestOp = that.m_quickestOp;
		} else if (that.m_quickestOp != -1 && that.m_quickestOp < this.m_quickestOp) {
			this.m_quickestOp = that.m_quickestOp;
			this.m_minOpDesc = that.m_minOpDesc;
		}
		if (this.m_trackHistory) {
			this.m_opHistory.addAll(that.m_opHistory);
		}
		
		if (this.m_opType == OP_TYPE_QUERY) {
			this.m_recordsRetrieved += that.m_recordsRetrieved;
		}
		
	}
	
	void print () {
		System.out.println(" ===== Statistics for: " + this.getDesc() + " =====");
		System.out.println("Total " + m_opTypeDesc + ": " + m_numberOfOps);
		System.out.println("Total time: " + m_totalTime);
		System.out.println("Max Time: " + this.getMax());
		System.out.println("Min Time: " + this.getMin());
		System.out.println("Avg Time: " + this.getAverage());
		if (m_opType == OP_TYPE_QUERY) {
			System.out.println("Num Recs: " + this.m_recordsRetrieved);
		}
		System.out.println("");
	}
	
	void printDistribution () {
		double firstQuarter = 0;
		double secondQuarter = 0;
		double thirdQuarter = 0;
		long numFirstQuarter = 0;
		long numSecondQuarter = 0;
		long numThirdQuarter = 0;
		long numLastQuarter = 0;
		long diff = 0;
		if (!m_trackHistory) {
			System.out.println("Unable to generate distribution. History of operations were not tracked!");
			return;
		}
		diff = m_longestOp - m_quickestOp;
		firstQuarter = m_quickestOp + (diff * 0.25);
		secondQuarter = m_quickestOp + (diff * 0.50);
		thirdQuarter = m_quickestOp + (diff * 0.75);
		
		Iterator iter = m_opHistory.iterator();
		while (iter.hasNext()) {
			Long curItem = (Long)iter.next();
			long curItemL = curItem.longValue();
			if (curItemL <= firstQuarter) {
				numFirstQuarter++;
			} else if (curItemL <= secondQuarter) {
				numSecondQuarter++;
			} else if (curItemL <= thirdQuarter) {
				numThirdQuarter++;
			} else {
				numLastQuarter++;
			}
		}
		
		System.out.println("");
		System.out.println("===== " + m_opTypeDesc + " Distribution =====");
		System.out.println(m_quickestOp + " - " + firstQuarter + " : " + numFirstQuarter);
		System.out.println(firstQuarter + " - " + secondQuarter + " : " + numSecondQuarter);
		System.out.println(secondQuarter + " - " + thirdQuarter + " : " + numThirdQuarter);
		System.out.println(thirdQuarter + " - " + m_longestOp + " : " + numLastQuarter);
		System.out.println("");
	}
	
	
	
	public static final int OP_TYPE_INSERT = 0;
	public static final int OP_TYPE_QUERY = 1;
	public static final int OP_TYPE_DELETE = 2;
	private static final String INSERT_OP_TYPE_DESC = "inserts";
	private static final String QUERY_OP_TYPE_DESC = "queries";
	private static final String DELETE_OP_TYPE_DESC = "deletes";
	private boolean m_trackHistory = false;
	private ArrayList m_opHistory = new ArrayList();
	public String m_maxOpDesc = "";
	public String m_minOpDesc = "";
	long m_numberOfOps = 0;
	long m_quickestOp = -1;
	long m_longestOp = 0;
	long m_totalTime = 0;
	String m_desc = "";
	String m_opTypeDesc = "";
	long m_recordsRetrieved = 0;
	int m_opType = -1;
}
