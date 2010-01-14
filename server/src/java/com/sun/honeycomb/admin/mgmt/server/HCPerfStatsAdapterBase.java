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

import java.util.logging.Logger;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.honeycomb.common.AlertConstants;
import com.sun.honeycomb.common.PerfStats;
import java.math.BigInteger;
import com.sun.honeycomb.protocol.server.ProtocolProxy;
import com.sun.honeycomb.common.BandwidthStatsAccumulator;
import com.sun.honeycomb.common.StatsAccumulator;

public abstract class HCPerfStatsAdapterBase implements HCPerfStatsAdapterInterface {
    private static transient final Logger logger = 
         Logger.getLogger(HCPerfStatsAdapterBase.class.getName());
    
    // Used to hold last performance statics acquired from the
    // various nodes.  We have to make these persistent 
    // since the HCPerfStatsAdapter class seems to keep getting
    // recreated every 4-5 minutes.  If we don't do this
    // we'll get hickups on the remote side where we won't have
    // any stats for a single poll cycle.  
    protected static BandwidthStatsAccumulator lastStoreStats[];
    protected static BandwidthStatsAccumulator lastStoreMDStats[];
    protected static BandwidthStatsAccumulator lastStoreBothStats[];
    protected static StatsAccumulator lastStoreMDSideStats[];

    protected static BandwidthStatsAccumulator lastRetrieveStats[];
    protected static BandwidthStatsAccumulator lastRetrieveMDStats[];

    protected static StatsAccumulator lastQueryStats[];
    protected static StatsAccumulator lastDeleteStats[];
    protected static StatsAccumulator lastSchemaStats[];

    protected static BandwidthStatsAccumulator lastPutWebDAVStats[];
    protected static BandwidthStatsAccumulator lastGetWebDAVStats[];

    protected static final String alertPerfStatProps[] = { 
	AlertConstants.PERF_STORE_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_STORE_MD_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_STORE_BOTH_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_STORE_MD_SIDE_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_RETRIEVE_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_RETRIEVE_MD_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_QUERY_TIME_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_DELETE_TIME_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_GET_SCHEMA_TIME_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_WEBDAV_PUT_BRANCH_LOOKUP_KEY,
	AlertConstants.PERF_WEBDAV_GET_BRANCH_LOOKUP_KEY,
	AlertConstants.LOAD_STATS_BRANCH_LOOKUP_KEY,
        AlertConstants.MEMORY_STATS_BRANCH_LOOKUP_KEY,
    };


    static int _interval=-1;
    static int _nodeId=-1;
    static HCPerfElement _storeOnly=new HCPerfElement();
    static HCPerfElement _storeMd=new HCPerfElement();
    static HCPerfElement _storeBoth = new HCPerfElement();
    static HCPerfElement _storeMdSide = new HCPerfElement();
    static HCPerfElement _retrieveOnly = new HCPerfElement();
    static HCPerfElement _retrieveMd = new HCPerfElement();
    static HCPerfElement _query = new HCPerfElement();
    static HCPerfElement _delete = new HCPerfElement();
    static HCPerfElement _schema = new HCPerfElement();
    static HCPerfElement _webdavPut = new HCPerfElement();
    static HCPerfElement _webdavGet = new HCPerfElement();
    static String _loadOneMinute = null;
    static String _loadFiveMinute = null;
    static String _loadFifteenMinute = null;
    static BigInteger  _usedMb = null;
    static BigInteger  _totalMb = null;
    static String  _usePercent = null;

    //
    // Hit on each call
    //
    public void loadHCPerfStats()
        throws InstantiationException {
    }

    public BigInteger getNewInterval() throws MgmtException {
        return BigInteger.valueOf(_interval);
    }
    public void setNewInterval(BigInteger value) throws MgmtException {
        _interval = value.intValue();
    }
    public BigInteger getNodeId() throws MgmtException {
        return BigInteger.valueOf(_nodeId);
    }
    public void setNodeId(BigInteger value) throws MgmtException {
        _nodeId = value.intValue();
    }
    public HCPerfElement getStoreOnly() throws MgmtException { 

        return _storeOnly;
    }
    public HCPerfElement getStoreBoth() throws MgmtException { return _storeBoth;}
    public HCPerfElement getStoreMd() throws MgmtException { return _storeMd;}
    public HCPerfElement getStoreMdSide() throws MgmtException { return _storeMdSide;}
    public HCPerfElement getRetrieveOnly() throws MgmtException { return _retrieveOnly;}
    public HCPerfElement getRetrieveMd() throws MgmtException { return _retrieveMd; }
    public HCPerfElement getQuery() throws MgmtException { return _query; }
    public HCPerfElement getDelete() throws MgmtException { return _delete;}
    public HCPerfElement getSchema() throws MgmtException { return _schema;}
    public HCPerfElement getWebdavPut() throws MgmtException { return _webdavPut;}
    public HCPerfElement getWebdavGet() throws MgmtException { return _webdavGet;}
    public String getLoadOneMinute() throws MgmtException { return _loadOneMinute; }
    public String getLoadFiveMinute() throws MgmtException { return _loadFiveMinute; }
    public String getLoadFifteenMinute() throws MgmtException { return _loadFifteenMinute; }
    public BigInteger getDiskUsedMb() throws MgmtException { return _usedMb; }
    public BigInteger getDiskTotalMb() throws MgmtException { return _totalMb; }
    public String getUsePercent() throws MgmtException { return _usePercent; }

    /*
     * This is the list of custom actions
     */
    public BigInteger cellCollect(BigInteger dummy) throws MgmtException {
        generateCellPerfStats();
        return BigInteger.valueOf(0);

    }
    public BigInteger nodeCollect(BigInteger dummy) throws MgmtException {

        if(_nodeId==-1) {
            //
            // Internationalize here
            //
            throw new MgmtException("Internal error, invalid node id specified.");
        }
        generateNodePerfStats();
        return BigInteger.valueOf(0);
    }

    protected abstract void generateNodePerfStats() throws MgmtException;
    protected abstract void generateCellPerfStats() throws MgmtException;
}
