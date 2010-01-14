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

/**
 * Used to hold delta's of performance numbers retrieved from a node
 * or the cluster.  These #'s are then used to calculate various
 * performance statistics.
 */
public class PerfStats {
        private long storeBytesProcessed = 0;
        private long storeExecTime = 0;
        private long storeOps = 0;
	
        private long storeMDBytesProcessed = 0;
        private long storeMDExecTime = 0;
        private long storeMDOps = 0;
	
        private long storeMDSideExecTime = 0;
        private long storeMDSideOps = 0;
	
	private long storeBothBytesProcessed = 0;
        private long storeBothExecTime = 0;
        private long storeBothOps = 0; 

        private long retrieveBytesProcessed = 0;
        private long retrieveExecTime = 0;
        private long retrieveOps = 0;
	
        private long retrieveMDBytesProcessed = 0; 
        private long retrieveMDExecTime = 0; 
        private long retrieveMDOps = 0;
	
	private long queryExecTime = 0;
        private long queryOps = 0;
	
        private long deleteExecTime = 0;
        private long deleteOps = 0;
	
	private long schemaExecTime = 0;
        private long schemaOps = 0;
	
        private long putWebDAVBytesProcessed = 0;
        private long putWebDAVExecTime = 0;
        private long putWebDAVOps = 0;
	
        private long getWebDAVBytesProcessed = 0;
        private long getWebDAVExecTime = 0;
        private long getWebDAVOps = 0;

        float systemLoad = 0;
        float load1M = 0;
        float load5M = 0;
        float load15M = 0;
        float memUsagePercent = 0;
        float freeMem = 0;
        float totalMem = 0;
        long diskUsed = 0; 
        long diskSize = 0;
  
        public PerfStats() {}
	
	public void addStoreBytesProcessed(long deltaBytesProcessed) {
            this.storeBytesProcessed += deltaBytesProcessed;
        }
 
        public void addStoreBothBytesProcessed(long deltaBytesProcessed) {
            this.storeBothBytesProcessed += deltaBytesProcessed;
        }
	
        public void addStoreMDBytesProcessed(long deltaBytesProcessed) {
            this.storeMDBytesProcessed += deltaBytesProcessed;
        }
	
	public void addStoreExecTime(long deltaExecTime) {
            this.storeExecTime += deltaExecTime;
        }
	
        public void addStoreBothExecTime(long deltaExecTime) {
            this.storeBothExecTime += deltaExecTime;
        }
 
        public void addStoreMDExecTime(long deltaExecTime) {
            this.storeMDExecTime += deltaExecTime;
        }
	
	public void addStoreMDSideExecTime(long deltaExecTime) {
            this.storeMDSideExecTime += deltaExecTime;
        }
 
        public void addStoreOps(long deltaOps) {
            this.storeOps += deltaOps;
        }
 
        public void addStoreMDOps(long deltaOps) {
            this.storeMDOps += deltaOps;
        }
	
        public void addStoreMDSideOps(long deltaOps) {
            this.storeMDSideOps += deltaOps;
        }

        public void addStoreBothOps(long deltaOps) {
            this.storeBothOps += deltaOps;
        }
	
        public void addRetrieveBytesProcessed(long deltaBytesProcessed) {
            this.retrieveBytesProcessed += deltaBytesProcessed;
        }
 
        public void addRetrieveMDBytesProcessed(long deltaBytesProcessed) {
            this.retrieveMDBytesProcessed += deltaBytesProcessed;
        }
	
	public void addRetrieveExecTime(long deltaExecTime) {
            this.retrieveExecTime += deltaExecTime;
        }
 
        public void addRetrieveMDExecTime(long deltaExecTime) {
            this.retrieveMDExecTime += deltaExecTime;
        }
        
        public void addRetrieveOps(long deltaOps) {
            this.retrieveOps += deltaOps;
        }
 
        public void addRetrieveMDOps(long deltaOps) {
            this.retrieveMDOps += deltaOps;
        }
  
	public void addQueryExecTime(long deltaExecTime) {
            this.queryExecTime += deltaExecTime;
        }
	
        public void addQueryOps(long deltaOps) {
            this.queryOps += deltaOps;
        }
	
	public void addSchemaExecTime(long deltaExecTime) {
            this.schemaExecTime += deltaExecTime;
        }
	
        public void addSchemaOps(long deltaOps) {
            this.schemaOps += deltaOps;
        }
	
	public void addDeleteExecTime(long deltaExecTime) {
            this.deleteExecTime += deltaExecTime;
        }

        public void addDeleteOps(long deltaOps) {
            this.deleteOps += deltaOps;
        }
 
        public void addWebDAVPutBytesProcessed(double deltaBytesProcessed) {
            this.putWebDAVBytesProcessed += deltaBytesProcessed;
        }

	public void addWebDAVPutExecTime(double deltaExecTime) {
            this.putWebDAVExecTime += deltaExecTime;
        }
	
        public void addWebDAVPutOps(long deltaOps) {
            this.putWebDAVOps += deltaOps;
        }

        public void addWebDAVGetBytesProcessed(double deltaBytesProcessed) {
            this.getWebDAVBytesProcessed += deltaBytesProcessed;
        }
	
	public void addWebDAVGetExecTime(double deltaExecTime) {
            this.getWebDAVExecTime += deltaExecTime;
        }

        public void addWebDAVGetOps(long deltaOps) {
            this.getWebDAVOps += deltaOps;
        }

        public void addSystemLoad(float systemLoad) {
            this.systemLoad += systemLoad;
        }

        public void addLoad1M(float load1M) {
            this.load1M += load1M;
        }

        public void addLoad5M(float load5M) {
            this.load5M += load5M;
        }

        public void addLoad15M(float load15M) {
            this.load15M += load15M;
        }
 
        public void addMemUsagePercent(float memUsagePercent) {
            this.memUsagePercent += memUsagePercent;
        }

        public void addFreeMem(float freeMem) {
            this.freeMem += freeMem;
        }

        public void addTotalMem(float totalMem) {
            this.totalMem += totalMem;
        }  

        public void addDiskUsed(long diskUsed) {
            this.diskUsed += diskUsed;
        }

        public void addDiskSize(long diskSize) {
            this.diskSize += diskSize;
        }

	public long getStoreBytesProcessed() {
            return storeBytesProcessed;
        }
 
        public long getStoreBothBytesProcessed() {
            return storeBothBytesProcessed;
        }
	
        public long getStoreMDBytesProcessed() {
            return storeMDBytesProcessed;
        }
	
	public long getStoreExecTime() {
            return storeExecTime;
        }
	
        public long getStoreBothExecTime() {
            return storeBothExecTime;
        }
 
        public long getStoreMDExecTime() {
            return storeMDExecTime;
        }

        public long getStoreMDSideExecTime() {
            return storeMDSideExecTime;
        }
 
        public long getStoreOps() {
            return storeOps;
        }
 
        public long getStoreMDOps() {
            return storeMDOps;
        }

        public long getStoreMDSideOps() {
            return storeMDSideOps;
        }

        public long getStoreBothOps() {
            return storeBothOps;
        }
	
        public long getRetrieveBytesProcessed() {
            return retrieveBytesProcessed;
        }
 
        public long getRetrieveMDBytesProcessed() {
            return retrieveMDBytesProcessed;
        }
	
	public long getRetrieveExecTime() {
            return retrieveExecTime;
        }
 
        public long getRetrieveMDExecTime() {
            return retrieveMDExecTime;
        }
        
        public long getRetrieveOps() {
            return retrieveOps;
        }
 
        public long getRetrieveMDOps() {
            return retrieveMDOps;
        }
  
	public long getQueryExecTime() {
            return queryExecTime;
        }
	
        public long getQueryOps() {
            return queryOps;
        }
	
	public long getSchemaExecTime() {
            return schemaExecTime;
        }
	
        public long getSchemaOps() {
            return schemaOps;
        }
	
	public long getDeleteExecTime() {
            return deleteExecTime;
        }

        public long getDeleteOps() {
            return deleteOps;
        }
 
        public long getWebDAVPutBytesProcessed() {
            return putWebDAVBytesProcessed;
        }

	public long getWebDAVPutExecTime() {
            return putWebDAVExecTime;
        }
	
        public long getWebDAVPutOps() {
            return putWebDAVOps;
        }

        public long getWebDAVGetBytesProcessed() {
            return getWebDAVBytesProcessed;
        }
	
	public long getWebDAVGetExecTime() {
            return getWebDAVExecTime;
        }

        public long getWebDAVGetOps() {
            return getWebDAVOps;
        }

        public float getSystemLoad() {
            return systemLoad;
        }

        public float getLoad1M() {
            return load1M;
        }

        public float getLoad5M() {
            return load5M;
        }

        public float getLoad15M() {
            return load15M;
        }
 
        public float getMemUsagePercent() {
            return memUsagePercent;
        }

        public float getFreeMem() {
            return freeMem;
        }

        public float getTotalMem() {
            return totalMem;
        }  

        public long getDiskUsed() {
            return diskUsed;
        }

        public long getDiskSize() {
            return diskSize;
        }

        public void setStoreBytesProcessed(long deltaBytesProcessed) {
            this.storeBytesProcessed = deltaBytesProcessed;
        }
 
        public void setStoreBothBytesProcessed(long deltaBytesProcessed) {
            this.storeBothBytesProcessed = deltaBytesProcessed;
        }
	
        public void setStoreMDBytesProcessed(long deltaBytesProcessed) {
            this.storeMDBytesProcessed = deltaBytesProcessed;
        }
	
	public void setStoreExecTime(long deltaExecTime) {
            this.storeExecTime = deltaExecTime;
        }
	
        public void setStoreBothExecTime(long deltaExecTime) {
            this.storeBothExecTime = deltaExecTime;
        }
 
        public void setStoreMDExecTime(long deltaExecTime) {
            this.storeMDExecTime = deltaExecTime;
        }
	
        public void setStoreMDSideExecTime(long deltaExecTime) {
            this.storeMDSideExecTime = deltaExecTime;
        }

        public void setStoreOps(long deltaOps) {
            this.storeOps = deltaOps;
        }
 
        public void setStoreMDOps(long deltaOps) {
            this.storeMDOps = deltaOps;
        }
	
	
        public void setStoreMDSideOps(long deltaOps) {
            this.storeMDSideOps = deltaOps;
        }

        public void setStoreBothOps(long deltaOps) {
            this.storeBothOps = deltaOps;
        }
	
        public void setRetrieveBytesProcessed(long deltaBytesProcessed) {
            this.retrieveBytesProcessed = deltaBytesProcessed;
        }
 
        public void setRetrieveMDBytesProcessed(long deltaBytesProcessed) {
            this.retrieveMDBytesProcessed = deltaBytesProcessed;
        }
	
	public void setRetrieveExecTime(long deltaExecTime) {
            this.retrieveExecTime = deltaExecTime;
        }
 
        public void setRetrieveMDExecTime(long deltaExecTime) {
            this.retrieveMDExecTime = deltaExecTime;
        }
        
        public void setRetrieveOps(long deltaOps) {
            this.retrieveOps = deltaOps;
        }
 
        public void setRetrieveMDOps(long deltaOps) {
            this.retrieveMDOps = deltaOps;
        }
	
	public void setQueryExecTime(long deltaExecTime) {
            this.queryExecTime = deltaExecTime;
        }
	
        public void setQueryOps(long deltaOps) {
            this.queryOps = deltaOps;
        }
	
	public void setSchemaExecTime(long deltaExecTime) {
            this.schemaExecTime = deltaExecTime;
        }
	
        public void setSchemaOps(long deltaOps) {
            this.schemaOps = deltaOps;
        }
	
	public void setDeleteExecTime(long deltaExecTime) {
            this.deleteExecTime = deltaExecTime;
        }

        public void setDeleteOps(long deltaOps) {
            this.deleteOps = deltaOps;
        }
 
        public void setWebDAVPutBytesProcessed(long deltaBytesProcessed) {
            this.putWebDAVBytesProcessed = deltaBytesProcessed;
        }

	public void setWebDAVPutExecTime(long deltaExecTime) {
            this.putWebDAVExecTime = deltaExecTime;
        }
	
        public void setWebDAVPutOps(long deltaOps) {
            this.putWebDAVOps = deltaOps;
        }

        public void setWebDAVGetBytesProcessed(long deltaBytesProcessed) {
            this.getWebDAVBytesProcessed = deltaBytesProcessed;
        }
	
	public void setWebDAVGetExecTime(long deltaExecTime) {
            this.getWebDAVExecTime = deltaExecTime;
        }

        public void setWebDAVGetOps(long deltaOps) {
            this.getWebDAVOps = deltaOps;
        }

   
        public void setSystemLoad(float systemLoad) {
            this.systemLoad = systemLoad;
        }
        
        public void setLoad1M(float load1M) {
            this.load1M = load1M;
        }
        
        public void setLoad5M(float load5M) {
            this.load5M = load5M;
        }
        
        public void setLoad15M(float load15M) {
            this.load15M = load15M;
        }
     
        public void setMemUsagePercent(float memUsagePercent) {
            this.memUsagePercent = memUsagePercent;
        }
        public void setFreeMem(float freeMem) {
            this.freeMem = freeMem;
        }
        public void setTotalMem(float totalMem) {
            this.totalMem = totalMem;
        }
       
        public void setDiskUsed(long diskUsed) {
            this.diskUsed = diskUsed;
        }
      
        public void setDiskSize(long diskSize) {
            this.diskSize = diskSize;
        }
	
	public String toString() {
	    StringBuffer buf = new StringBuffer();
	    buf.append("storeBytesProcessed=").append(storeBytesProcessed).append(", ");
	    buf.append("storeExecTime=").append(storeExecTime).append(", ");
	    buf.append("storeOps=").append(storeOps).append(", ");
	    buf.append("storeMDBytesProcessed=").append(storeMDBytesProcessed).append(", ");
	    buf.append("storeMDExecTime=").append(storeMDExecTime).append(", ");
	    buf.append("storeMDOps=").append(storeMDOps).append(", ");
	    buf.append("storeMDSideExecTime=").append(storeMDSideExecTime).append(", ");
	    buf.append("storeMDSideOps=").append(storeMDSideOps).append(", ");
	    buf.append("storeBothBytesProcessed=").append(storeBothBytesProcessed).append(", ");
	    buf.append("storeBothExecTime=").append(storeBothExecTime).append(", ");
	    buf.append("storeBothOps=").append(storeBothOps).append(", ");
	    buf.append("retrieveBytesProcessed=").append(retrieveBytesProcessed).append(", ");
	    buf.append("retrieveExecTime=").append(retrieveExecTime).append(", ");
	    buf.append("retrieveOps=").append(retrieveOps).append(", ");
	    buf.append("retrieveMDBytesProcessed=").append(retrieveMDBytesProcessed).append(", ");
	    buf.append("retrieveMDExecTime=").append(retrieveMDExecTime).append(", ");
	    buf.append("retrieveMDOps=").append(retrieveMDOps).append(", ");
	    buf.append("queryExecTime=").append(queryExecTime).append(", ");
	    buf.append("queryOps=").append(queryOps).append(", ");
	    buf.append("deleteExecTime=").append(deleteExecTime).append(", ");
	    buf.append("deleteOps=").append(deleteOps).append(", ");
	    buf.append("schemaExecTime=").append(schemaExecTime).append(", ");
	    buf.append("schemaOps=").append(schemaOps).append(", ");
	    buf.append("putWebDAVBytesProcessed=").append(putWebDAVBytesProcessed).append(", ");
	    buf.append("putWebDAVBytesProcessed=").append(putWebDAVBytesProcessed).append(", ");
	    buf.append("putWebDAVOps=").append(putWebDAVOps).append(", ");
	    buf.append("getWebDAVBytesProcessed=").append(getWebDAVBytesProcessed).append(", ");
	    buf.append("getWebDAVExecTime=").append(getWebDAVExecTime).append(", ");
	    buf.append("getWebDAVOps=").append(getWebDAVOps).append(", ");
            buf.append("systemLoad=").append(systemLoad).append(", ");
            buf.append("load1M=").append(load1M).append(", ");
            buf.append("load5M=").append(load5M).append(", ");
            buf.append("load15M=").append(load15M).append(", ");
            buf.append("memUsagePercent=").append(memUsagePercent).append(", ");
            buf.append("freeMem=").append(freeMem).append(", ");
            buf.append("totalMem=").append(totalMem).append(", ");
            buf.append("diskUsed=").append(diskUsed).append(", ");
            buf.append("diskSize=").append(diskSize);
	    return buf.toString();
	}
}
