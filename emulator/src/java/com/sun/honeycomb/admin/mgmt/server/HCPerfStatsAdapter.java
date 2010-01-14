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



package com.sun.honeycomb.admin.mgmt.server;

import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertException;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.common.BandwidthStatsAccumulator;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.common.PerfStats;
import com.sun.honeycomb.common.StatsAccumulator;
import com.sun.honeycomb.mgmt.common.MgmtException;
		
import java.math.BigInteger;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
		

public class HCPerfStatsAdapter 
    extends HCPerfStatsAdapterBase {

    private static transient final Logger logger = 
         Logger.getLogger(HCPerfStatsAdapter.class.getName());
    
    private static final long ONE_SECOND = 1000;   // in milleseconds
    private static final long KILOBYTE = 1024;	  
    private static int perfNodeStatsMaxIndex = 0;
    
    public HCPerfStatsAdapter() {
        initCounters();
    }


    public synchronized void initCounters() {
	
        int size = 16;
        
	perfNodeStatsMaxIndex = size;
	lastStoreStats = new BandwidthStatsAccumulator[size];
	lastStoreMDStats = new BandwidthStatsAccumulator[size];
	lastStoreBothStats = new BandwidthStatsAccumulator[size];
	lastStoreMDSideStats = new StatsAccumulator[size];

	lastRetrieveStats = new BandwidthStatsAccumulator[size];
	lastRetrieveMDStats = new BandwidthStatsAccumulator[size];

	lastQueryStats = new StatsAccumulator[size];
	lastDeleteStats = new StatsAccumulator[size];
	
	lastSchemaStats = new StatsAccumulator[size];

	lastPutWebDAVStats = new BandwidthStatsAccumulator[size];
	lastGetWebDAVStats = new BandwidthStatsAccumulator[size];
        
        try {
            fetchCellPerfStats();
        }
        catch (Exception ignore) {
            ignore.printStackTrace();
        };
        
        
        
    }
    
    public BigInteger reset(BigInteger dummy) throws MgmtException {
        initCounters();
        return BigInteger.valueOf(0);
    }


    public Node[] getNodes() throws RuntimeException {
        return null;
    }

    /*
     * Get Perf Stats for a Given Node
     * Returns an Array of Strings with formatted output
     */ 
    protected synchronized void generateNodePerfStats( )
        throws MgmtException {
	PerfStats nodePerfStats = fetchNodePerfStats();
	publishStatistics(nodePerfStats);
    }
    
    protected PerfStats fetchNodePerfStats()
        throws MgmtException {
	
        try {
            return getNodePerfStats(_nodeId);
	} catch (MgmtException me) {
	    throw me;
	} catch (Exception e) {
	    //
	    // Internationalize here
	    //
	    logger.log(Level.SEVERE, "Internal error generating performance numbers.", e);
	    throw new MgmtException(
		"Internal error, failed to fetch performance Statistics for NODE-"
		+ _nodeId + ".");
	}
    }
    
    protected synchronized void generateCellPerfStats() 
        throws MgmtException {
	PerfStats cellPerfStats = fetchCellPerfStats();
	publishStatistics(cellPerfStats);
    }
    
    /*
     * Get Perf Stats for a Given Node
     */ 
    private PerfStats fetchCellPerfStats() 
        throws MgmtException {
	
	/*
	 * Aggregate Perf Stats for all the nodes in the cluster
	 */    
	PerfStats cellPerfStats = new PerfStats();
	
        try {
	    int nodeCount = 8;
	    for (int i=0 ; i< nodeCount; i++) {


		int nodeId = 101 + i;
		PerfStats perfStats = getNodePerfStats(nodeId);
            
                cellPerfStats.addStoreBytesProcessed(perfStats.getStoreBytesProcessed());
		cellPerfStats.addStoreExecTime(perfStats.getStoreExecTime());
		cellPerfStats.addStoreOps(perfStats.getStoreOps());
		
		cellPerfStats.addStoreMDBytesProcessed(perfStats.getStoreMDBytesProcessed());
		cellPerfStats.addStoreMDExecTime(perfStats.getStoreMDExecTime());
		cellPerfStats.addStoreMDOps(perfStats.getStoreMDOps());
		
		cellPerfStats.addStoreMDSideExecTime(perfStats.getStoreMDSideExecTime());
		cellPerfStats.addStoreMDSideOps(perfStats.getStoreMDSideOps());
		
		cellPerfStats.addStoreBothBytesProcessed(perfStats.getStoreBothBytesProcessed());
		cellPerfStats.addStoreBothExecTime(perfStats.getStoreBothExecTime());
		cellPerfStats.addStoreBothOps(perfStats.getStoreBothOps());
		
		cellPerfStats.addRetrieveBytesProcessed(perfStats.getRetrieveBytesProcessed());
		cellPerfStats.addRetrieveExecTime(perfStats.getRetrieveExecTime());
		cellPerfStats.addRetrieveOps(perfStats.getRetrieveOps());
		
		cellPerfStats.addRetrieveMDBytesProcessed(perfStats.getRetrieveMDBytesProcessed());
		cellPerfStats.addRetrieveMDExecTime(perfStats.getRetrieveMDExecTime());
		cellPerfStats.addRetrieveMDOps(perfStats.getRetrieveMDOps());
		
		cellPerfStats.addQueryExecTime(perfStats.getQueryExecTime());
		cellPerfStats.addQueryOps(perfStats.getQueryOps());
		
		cellPerfStats.addDeleteExecTime(perfStats.getDeleteExecTime());
		cellPerfStats.addDeleteOps(perfStats.getDeleteOps());
		
		cellPerfStats.addSchemaExecTime(perfStats.getSchemaExecTime());
		cellPerfStats.addSchemaOps(perfStats.getSchemaOps());

		cellPerfStats.addWebDAVGetBytesProcessed(perfStats.getWebDAVGetBytesProcessed());
		cellPerfStats.addWebDAVGetExecTime(perfStats.getWebDAVGetExecTime());
		cellPerfStats.addWebDAVGetOps(perfStats.getWebDAVGetOps());
		
		cellPerfStats.addWebDAVPutBytesProcessed(perfStats.getWebDAVPutBytesProcessed());
		cellPerfStats.addWebDAVPutExecTime(perfStats.getWebDAVPutExecTime());
		cellPerfStats.addWebDAVPutOps(perfStats.getWebDAVPutOps());

                cellPerfStats.addSystemLoad(perfStats.getSystemLoad());
                cellPerfStats.addLoad1M(perfStats.getLoad1M());
                cellPerfStats.addLoad5M(perfStats.getLoad5M());
                cellPerfStats.addLoad15M(perfStats.getLoad15M());
                cellPerfStats.addMemUsagePercent(perfStats.getMemUsagePercent());
                cellPerfStats.addFreeMem(perfStats.getFreeMem());
                cellPerfStats.addTotalMem(perfStats.getTotalMem());
                cellPerfStats.addDiskUsed(perfStats.getDiskUsed());
                cellPerfStats.addDiskSize(perfStats.getDiskSize());
	    }
	} catch(MgmtException me) {
            logger.log(Level.SEVERE, "Internal error", me);
	    throw me;
	} catch (Exception e) {
	    //
	    // Internationalize here
	    //
            e.printStackTrace();
	    logger.log(Level.SEVERE, "Internal error generating performance numbers.", e);
	    throw new MgmtException("Unable to fetch performance numbers for the specified cell.", e);
	}
	return cellPerfStats;
    }
    
    /**
     * @param obMap the alert tree object map
     * @param nodes
     * @param nodeId the node to retrieve stats for
     */
    private PerfStats getNodePerfStats(int nodeId)
        throws AlertException, MgmtException {
        int mapIdx = nodeId - 101;

        float systemLoad = 0;
        float load1M = 0;
        float load5M = 0;
        float load15M = 0;
        float freeMem = 0;
        float totalMem = 0;
        long diskUsed = 0;
        long diskSize = 0;
      
       

        PerfStats perfStats = new PerfStats(); 
        AlertApi.AlertObject alertObj; 

	// We retrieve the stored performance values for each performance property
	// using the origional class that the stats were written out to
	// the alert tree to reconsistitute it.  Calculate the delta between
	// the current and last value and store them in perfStats
	
	EmulatedBSA bStats = null;
	EmulatedSA stats = null;
	
	
	
        if (lastStoreStats[mapIdx] == null)
            bStats = new EmulatedBSA();
        else {
            bStats = new EmulatedBSA(lastStoreStats[mapIdx]);
            bStats.increment();
            perfStats.setStoreBytesProcessed(
                calculateDelta(bStats.getTotalBytesProcessed(), 
                    lastStoreStats[mapIdx].getTotalBytesProcessed()));
            perfStats.setStoreExecTime(
                calculateDelta(bStats.getTotalExecTime(), 
                    lastStoreStats[mapIdx].getTotalExecTime()));
            perfStats.setStoreOps(
                calculateDelta(bStats.getTotalOps(), 
                    lastStoreStats[mapIdx].getTotalOps()));
        }
        lastStoreStats[mapIdx] = bStats;
	
        if (lastStoreMDStats[mapIdx] == null) 
            bStats = new EmulatedBSA();
        else {
            bStats = new EmulatedBSA(lastStoreMDStats[mapIdx]);
            bStats.increment(50, 0, DEFAULT_MAX_TIME);
            perfStats.setStoreMDBytesProcessed(
                calculateDelta(bStats.getTotalBytesProcessed(), 
                    lastStoreMDStats[mapIdx].getTotalBytesProcessed()));
            perfStats.setStoreMDExecTime(
                calculateDelta(bStats.getTotalExecTime(), 
                    lastStoreMDStats[mapIdx].getTotalExecTime()));
            perfStats.setStoreMDOps(
                calculateDelta(bStats.getTotalOps(), 
                    lastStoreMDStats[mapIdx].getTotalOps()));
        }
        lastStoreMDStats[mapIdx] = bStats;
	
        if (lastStoreMDSideStats[mapIdx] == null)
            stats = new EmulatedSA();
        else {
            stats = new EmulatedSA(lastStoreMDSideStats[mapIdx]);
            stats.increment();
            perfStats.setStoreMDSideExecTime(
                calculateDelta(stats.getTotalExecTime(), 
                    lastStoreMDSideStats[mapIdx].getTotalExecTime()));
            perfStats.setStoreMDSideOps(
                calculateDelta(stats.getTotalOps(), 
                    lastStoreMDSideStats[mapIdx].getTotalOps()));
        }
        lastStoreMDSideStats[mapIdx] = stats;
	
        if (lastStoreBothStats[mapIdx] == null)
            bStats = new EmulatedBSA();
        else {
            bStats = new EmulatedBSA(lastStoreBothStats[mapIdx]);
            bStats.increment();
            perfStats.setStoreBothBytesProcessed(
                calculateDelta(bStats.getTotalBytesProcessed(), lastStoreBothStats[mapIdx].getTotalBytesProcessed()));
            perfStats.setStoreBothExecTime(
                calculateDelta(bStats.getTotalExecTime(), lastStoreBothStats[mapIdx].getTotalExecTime()));
            perfStats.setStoreBothOps(
                calculateDelta(bStats.getTotalOps(), lastStoreBothStats[mapIdx].getTotalOps()));
        }
        lastStoreBothStats[mapIdx] = bStats;
	
        /* Retrieve Stats */ 
        if (lastRetrieveStats[mapIdx] == null)
            bStats = new EmulatedBSA();
        else {
            bStats = new EmulatedBSA(lastRetrieveStats[mapIdx]);
            bStats.increment();
            perfStats.setRetrieveBytesProcessed(
                calculateDelta(bStats.getTotalBytesProcessed(), 
                    lastRetrieveStats[mapIdx].getTotalBytesProcessed()));
            perfStats.setRetrieveExecTime(
                calculateDelta(bStats.getTotalExecTime(), 
                    lastRetrieveStats[mapIdx].getTotalExecTime()));
            perfStats.setRetrieveOps(
                calculateDelta(bStats.getTotalOps(), 
                    lastRetrieveStats[mapIdx].getTotalOps()));
        }
        lastRetrieveStats[mapIdx] = bStats;
	
        if (lastRetrieveMDStats[mapIdx] == null) 
            bStats = new EmulatedBSA();
        else {
            bStats = new EmulatedBSA(lastRetrieveMDStats[mapIdx]);
            bStats.increment(50, 0, DEFAULT_MAX_TIME);
            perfStats.setRetrieveMDBytesProcessed(
                calculateDelta(bStats.getTotalBytesProcessed(), 
                    lastRetrieveMDStats[mapIdx].getTotalBytesProcessed()));
            if (bStats.getTotalExecTime() != 0)
            perfStats.setRetrieveMDExecTime(
                calculateDelta(bStats.getTotalExecTime(), 
                    lastRetrieveMDStats[mapIdx].getTotalExecTime()));
            perfStats.setRetrieveMDOps(
                calculateDelta(bStats.getTotalOps(), 
                    lastRetrieveMDStats[mapIdx].getTotalOps()));
        }
        lastRetrieveMDStats[mapIdx] = bStats;
		 
	/* Query and Delete Stats */	
        if (lastQueryStats[mapIdx] == null) 
            stats = new EmulatedSA();
        else {
            stats = new EmulatedSA(lastQueryStats[mapIdx]);
            stats.increment();
            perfStats.setQueryExecTime(
                calculateDelta(stats.getTotalExecTime(), lastQueryStats[mapIdx].getTotalExecTime()));
            perfStats.setQueryOps(
                calculateDelta(stats.getTotalOps(), lastQueryStats[mapIdx].getTotalOps()));
        }
        lastQueryStats[mapIdx] = stats;
	
        if (lastDeleteStats[mapIdx] == null)
            stats = new EmulatedSA();
        else {
            stats = new EmulatedSA(lastDeleteStats[mapIdx]);
            stats.increment(50, DEFAULT_MAX_TIME);
            perfStats.setDeleteExecTime(
                calculateDelta(stats.getTotalExecTime(), lastDeleteStats[mapIdx].getTotalExecTime()));
            perfStats.setDeleteOps(
                calculateDelta(stats.getTotalOps(), lastDeleteStats[mapIdx].getTotalOps()));
        }
        lastDeleteStats[mapIdx] = stats;
	
	/* Schema Stats */	
        if (lastSchemaStats[mapIdx] == null) {
            
        } else {
            perfStats.setSchemaExecTime(
                calculateDelta(stats.getTotalExecTime(), lastSchemaStats[mapIdx].getTotalExecTime()));
            perfStats.setSchemaOps(
                calculateDelta(stats.getTotalOps(), lastSchemaStats[mapIdx].getTotalOps()));
        }
        lastSchemaStats[mapIdx] = stats;

        /* webdav put and get Stats */
        if (lastPutWebDAVStats[mapIdx] == null)
            bStats = new EmulatedBSA();
        else {
            bStats = new EmulatedBSA(lastPutWebDAVStats[mapIdx]);
            bStats.increment(75, 250, DEFAULT_MAX_TIME);
            perfStats.setWebDAVPutBytesProcessed(
                calculateDelta(bStats.getTotalBytesProcessed(), 
                               lastPutWebDAVStats[mapIdx].getTotalBytesProcessed()));
            perfStats.setWebDAVPutExecTime(
                calculateDelta(bStats.getTotalExecTime(), 
                               lastPutWebDAVStats[mapIdx].getTotalExecTime()));
            perfStats.setWebDAVPutOps(
                calculateDelta(bStats.getTotalOps(), lastPutWebDAVStats[mapIdx].getTotalOps()));
        }
        lastPutWebDAVStats[mapIdx] = bStats;
	
        if (lastGetWebDAVStats[mapIdx] == null) {
            bStats = new EmulatedBSA();
        } else {
            bStats = new EmulatedBSA(lastGetWebDAVStats[mapIdx]);
            bStats.increment(75, 250, DEFAULT_MAX_TIME);
            perfStats.setWebDAVGetBytesProcessed(
                calculateDelta(bStats.getTotalBytesProcessed(), 
                               lastGetWebDAVStats[mapIdx].getTotalBytesProcessed()));
            perfStats.setWebDAVGetExecTime(
                calculateDelta(bStats.getTotalExecTime(), 
                               lastGetWebDAVStats[mapIdx].getTotalExecTime()));
            perfStats.setWebDAVGetOps(
                calculateDelta(bStats.getTotalOps(), lastGetWebDAVStats[mapIdx].getTotalOps()));
        }
        lastGetWebDAVStats[mapIdx] = bStats;

        systemLoad = 0.3F;
        load1M = systemLoad;
        load5M = systemLoad;
        load15M = systemLoad;
        
        freeMem = 0; 
        totalMem = 0; 

        List<HCDisk> disks =ValuesRepository.getInstance().getDisks().getDisksList();
        Iterator iter=disks.iterator();
        while(iter.hasNext()){
            HCDisk curDisk=(HCDisk)iter.next();
            if (curDisk.getNodeId().intValue() == nodeId) {
                diskSize += curDisk.getTotalCapacity();
                diskUsed += curDisk.getUsedCapacity();
            }
        }
        
        
        /*
         * System Metrics - Format String is as follows: 
         * Memory Stats "0, freeMemory, TotalMemory, 0, time"
         * System Load Stats "currentSystemLoad, 1MinSample, 15MinSample, 0, time"
         */ 
	if (freeMem > 0 && totalMem > 0) {
	    //perfStats.setMemUsagePercent(100-((100 * freeMem)/totalMem));  
	    perfStats.setFreeMem(freeMem);  
	    perfStats.setTotalMem(totalMem);  
	} 
	if (systemLoad > 0) {
	    perfStats.setSystemLoad(systemLoad);  
	}
        if (load1M > 0) {
            perfStats.setLoad1M(load1M);
        } 
        if (load5M > 0) {
            perfStats.setLoad5M(load5M);
        } 
        if (load15M > 0) {
            perfStats.setLoad15M(load15M);
        } 
        if (diskUsed > 0) {
            perfStats.setDiskUsed(diskUsed);
        } 
        if (diskSize > 0) {
            perfStats.setDiskSize(diskSize);
        } 
        return perfStats;
    }
    
    
    
    /**
     * Take the passed in statistics and move them to there global
     * variables so that they may be transferred to the client.
     */
    private synchronized void publishStatistics(PerfStats perfStats) 
    {
	// Strings and BigInteger are the only 2 data types currently available 
	// for sending data to the UI layer. We therefore convert doubles to 
	// Strings
	//
	// Pass the execute time to allow clients to compute average 
	// response time.  
	//
	// Interval here is the polling interval.  Which is technically wrong
	// as it should represent the actual time period between two polling
	// intervals.  
	
	// Design Issue: 
	//
	// If 2 users are monitoring performance we have problems.
	// Since delta are computed based on cli polls the delta's are 
	// going to be wrong for both users.  User 1 specified time period
	// of 5 sec.  User 2 starts poll process right after User 1 and
	// indicates a polling time of 10 secs.  Now all calculations are
	// going to be with a interval value of 10.  Furthermore since we
	// have 2 users polling now the delta they get are for the values
	// between the sample of the last poll.  Not for the time interval
	// they specified.  Thus the performance numbers are going to be
	// almost useless if 2+ users do performance monitoring at the same 
	// time.
	//
	// The solution to this is to push all calculations to the clients.
	// This is where it belongs. (Post 1.1)
	//
	// 
	try {
            //
            // store only
            //  
	    _storeOnly.setOps(BigInteger.valueOf(perfStats.getStoreOps())); 
            _storeOnly.setOpSec(Double.toString(
		divide(perfStats.getStoreOps(), _interval))); 
            _storeOnly.setKbSec(Double.toString(
		divide(divide(perfStats.getStoreBytesProcessed(), KILOBYTE), (long)_interval)));
	    _storeOnly.setExecTime(BigInteger.valueOf(perfStats.getStoreExecTime()));
	    
	    //
	    // store MD
	    //
	    _storeMd.setOps(BigInteger.valueOf(perfStats.getStoreMDOps())); 
            _storeMd.setOpSec(Double.toString(
		divide(perfStats.getStoreMDOps(), _interval))); 
            _storeMd.setKbSec(Double.toString(
		divide(divide(perfStats.getStoreMDBytesProcessed(), KILOBYTE), (long)_interval)));
            _storeMd.setExecTime(BigInteger.valueOf(perfStats.getStoreMDExecTime()));
	    
	    //
	    // store MD
	    //
	    _storeMdSide.setOps(BigInteger.valueOf(perfStats.getStoreMDSideOps())); 
            _storeMdSide.setOpSec(Double.toString(
		divide(perfStats.getStoreMDSideOps(), _interval))); 
            _storeMdSide.setKbSec(null);
            _storeMdSide.setExecTime(BigInteger.valueOf(perfStats.getStoreMDSideExecTime()));
	    
	    //
	    // store both
	    //
	    _storeBoth.setOps(BigInteger.valueOf(perfStats.getStoreBothOps())); 
            _storeBoth.setOpSec(Double.toString(
		divide(perfStats.getStoreBothOps(), _interval))); 
            _storeBoth.setKbSec(Double.toString(
		divide(divide(perfStats.getStoreBothBytesProcessed(), KILOBYTE), (long)_interval)));
            _storeBoth.setExecTime(BigInteger.valueOf(perfStats.getStoreBothExecTime()));
	    
	    //
	    // retrieve
	    //
	    _retrieveOnly.setOps(BigInteger.valueOf(perfStats.getRetrieveOps())); 
            _retrieveOnly.setOpSec(Double.toString(
		divide(perfStats.getRetrieveOps(), _interval)));
            _retrieveOnly.setKbSec(Double.toString(
		divide(divide(perfStats.getRetrieveBytesProcessed(), KILOBYTE), (long)_interval)));
            _retrieveOnly.setExecTime(BigInteger.valueOf(perfStats.getRetrieveExecTime()));
	    
	    
	    //
	    // retrieve MD
	    //
	    _retrieveMd.setOps(BigInteger.valueOf(perfStats.getRetrieveMDOps())); 
            _retrieveMd.setOpSec(Double.toString(
		divide(perfStats.getRetrieveMDOps(), _interval))); 
            _retrieveMd.setKbSec(Double.toString(
		divide(divide(perfStats.getRetrieveMDBytesProcessed(), KILOBYTE), (long)_interval)));
            _retrieveMd.setExecTime(BigInteger.valueOf(perfStats.getRetrieveMDExecTime()));
	    
	    //
	    // query
	    //
	    _query.setOps(BigInteger.valueOf(perfStats.getQueryOps())); 
            _query.setOpSec(Double.toString(
		divide(perfStats.getQueryOps(), _interval))); 
            _query.setKbSec(null);     // N/A
            _query.setExecTime(BigInteger.valueOf(perfStats.getQueryExecTime()));
	    
	    //
	    // delete
	    //
	    _delete.setOps(BigInteger.valueOf(perfStats.getDeleteOps())); 
            _delete.setOpSec(Double.toString(
		divide(perfStats.getDeleteOps(), _interval))); 
            _delete.setKbSec(null);    // N/A
	    _delete.setExecTime(BigInteger.valueOf(perfStats.getDeleteExecTime()));
	    
	    //
	    // schema
	    //
	    _schema.setOps(BigInteger.valueOf(perfStats.getSchemaOps())); 
            _schema.setOpSec(Double.toString(
		divide(perfStats.getSchemaOps(), _interval))); 
            _schema.setKbSec(null);     // N/A
            _schema.setExecTime(BigInteger.valueOf(perfStats.getSchemaExecTime()));

            //
            // webdav put
            //
	    _webdavPut.setOps(BigInteger.valueOf(perfStats.getWebDAVPutOps())); 
            _webdavPut.setOpSec(Double.toString(
		divide(perfStats.getWebDAVPutOps(), _interval))); 
            _webdavPut.setKbSec(Double.toString(
		divide(divide(perfStats.getWebDAVPutBytesProcessed(), KILOBYTE), (long)_interval)));
            _webdavPut.setExecTime(BigInteger.valueOf(perfStats.getWebDAVPutExecTime()));
	    
            //
            // webdav get
            //
	    _webdavGet.setOps(BigInteger.valueOf(perfStats.getWebDAVGetOps())); 
            _webdavGet.setOpSec(Double.toString(
		divide(perfStats.getWebDAVGetOps(), _interval))); 
            _webdavGet.setKbSec(Double.toString(
		divide(divide(perfStats.getWebDAVGetBytesProcessed(), KILOBYTE), (long)_interval)));
            _webdavGet.setExecTime(BigInteger.valueOf(perfStats.getWebDAVGetExecTime()));
	    
            //
            // load
            //
            _loadOneMinute= Float.toString(perfStats.getLoad1M());
            _loadFiveMinute= Float.toString(perfStats.getLoad5M());
            _loadFifteenMinute= Float.toString(perfStats.getLoad15M()); 
	    
            // 
            // memory 
            //
            /*
              memUsagePercent = perfStats.getMemUsagePercent();
              freeMem = perfStats.getFreeMem();
              totalMem = perfStats.getTotalMem();
              Float.toString(freeMem);
              Float.toString(totalMem);
              Float.toString(((totalMem-freeMem)*100)/totalMem); 
            */
            // 
            // Disk Usage is in MBytes
	    long diskUsed = perfStats.getDiskUsed();
            _usedMb= BigInteger.valueOf(diskUsed);

	    // Disk Size is in MBytes
	    long diskSize = perfStats.getDiskSize();
            _totalMb=BigInteger.valueOf(diskSize);
	    _usePercent = Double.toString(divide(diskUsed, diskSize) * 100);

        }  catch (NumberFormatException nfe) {
	    logger.log(Level.SEVERE, "Parsing exception: ", nfe); 
        } 
    }
    
    

    /**
     * Calculate the delta of 2 performance samples.
     */
    protected long calculateDelta(long newValue, long oldValue) {
	
	if (newValue == oldValue)
	    return 0;
	
	if (newValue > oldValue)
	    return newValue - oldValue;
	
	// Counter has wrapped.
	return Long.MAX_VALUE - oldValue + newValue;
    }
    
    protected double divide(long value, long divisor) {
	if (divisor == 0)
	    return 0;
	return (double)value/divisor;
    }
    
    protected double divide(long value, double divisor) {
	if (divisor == 0)
	    return 0;
	return value/divisor;
    }
    
    protected double divide(double value, double divisor) {
	if (divisor == 0)
	    return 0;
	return value/divisor;
    }
    
    private Random rand = new Random(new Date().getTime());
    
    public int getRandom(int max) {
        if (max <= 0)
            return 0;
        int value = rand.nextInt(max);
        if (value < 0)
            value = (value << 1 >> 1) + 1;
        return value;
    }
    
    public int getRandom(int min, int max) {
        int value = max - min;
        if (value < 0)
            return 0;
        return rand.nextInt(value) + min;
    }
    
    private int DEFAULT_MAX_TIME = CliConstants.ONE_SECOND;
    
    public class EmulatedBSA extends BandwidthStatsAccumulator {
        
        
        public EmulatedBSA() {
            super();
        }
        
        public EmulatedBSA(BandwidthStatsAccumulator old) {
            super(old.getStatsStr());
        }
        
        /**
         * For emulation mode we auto increment the stats via some random
         * value.
         */
        public void increment(int maxOps, int maxValue, int maxTime) {
            int ops = getRandom(0, maxOps);
            int minTime = 100;
            while (ops > 0 && maxTime > minTime && maxTime > 0) {
                long sizeInBytes = getRandom(maxValue);
                int execTime = getRandom(minTime, maxTime);
                add(sizeInBytes, execTime);
                maxTime -= execTime;
                ops--;
            }
        }
        
        public void increment() {
            increment(300, 1024 * 5, DEFAULT_MAX_TIME);
        }
        
    }
    
    public class EmulatedSA extends StatsAccumulator {
        
        public EmulatedSA() {
            super();
        }
        
        public EmulatedSA(StatsAccumulator old) {
            super(old.getStatsStr());
        }
        
        public void increment(int maxOps, int maxTime) {
            int minTime = 100;
            while (maxOps-- > 0 && maxTime > minTime && maxTime > 0) {

                long execTime = getRandom(minTime, maxTime);
                add(execTime);
                maxTime -= execTime;
            }
        }
        
        /**
         * For emulation mode we auto increment the stats via some random
         * value.
         */
        public void increment() {
            increment(getRandom(100), DEFAULT_MAX_TIME);
            
        }
        
    }
    
    
}
