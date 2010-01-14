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
 *  Data structure for the query performance statistics
 */
public class AdvQueryPerformanceStatistics
        extends AdvQueryPerformanceStatisticsBase {
    
    public long totalfirsts = 0;
    public long totalfirsttime = 0;
    public long totalfirstsquares = 0;
    
    public long totalfetches = 0;
    public long minfetch = Long.MAX_VALUE;
    public long maxfetch = Long.MIN_VALUE;
    public long minfirst = Long.MAX_VALUE;
    public long maxfirst = Long.MIN_VALUE;
    public long totalfetchtime = 0;
    public long totalfetchsquares = 0;
    
    public long batchops = 0;
    
    public long totalops = 0;
    public long elapsed = 0;
    public double aveops = 0.0; //= totalops/(elapsed/1000.0);
    public double avefirsts = 0.0;
    public double stddevfirsts = 0.0;
    public double avefetches = 0.0;
    public double stddevfetches = 0.0;
    
    public long threadID; // = Thread.currentThread().getId();
    
    
    /** Creates a new instance of PerformanceStatistics */
    public AdvQueryPerformanceStatistics() {
    }
    
    public Object clone() {
        return super.clone();
    }
    
    public AdvQueryPerformanceStatistics(
            AdvQueryPerformanceStatistics stats) {
        //super(stats.itemCount, stats.errorCount);
        
        this.setStatistics(stats);
        
    }
    
    public void setStatistics(
            AdvQueryPerformanceStatistics stats) {
        super.setStatistics(stats.itemCount, stats.errorCount);
        
        totalfirsts = stats.totalfirsts;
        totalfirsttime = stats.totalfirsttime;
        totalfirstsquares = stats.totalfirstsquares;       
        minfirst = stats.minfirst;
        maxfirst = stats.maxfirst;
        
        totalfetches = stats.totalfetches;
        totalfetchtime = stats.totalfetchtime;
        totalfetchsquares = stats.totalfetchsquares;
        minfetch = stats.minfetch;
        maxfetch = stats.maxfetch;
        
        batchops = stats.batchops;
        
        totalops = stats.totalops;
        elapsed = stats.elapsed;
        aveops = stats.aveops;
        avefirsts = stats.avefirsts;
        stddevfirsts = stats.stddevfirsts;
        avefetches = stats.avefetches;
        stddevfetches = stats.stddevfetches;
        threadID = stats.threadID;
        
        
    }
}
