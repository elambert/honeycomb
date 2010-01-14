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


package com.sun.honeycomb.adm.cli.commands;

import java.math.BigInteger;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.adm.cli.ExitCodes;
import com.sun.honeycomb.adm.cli.ShellCommand;
import com.sun.honeycomb.adm.cli.parser.Option;
import com.sun.honeycomb.admin.mgmt.client.HCPerfStats;
import com.sun.honeycomb.admin.mgmt.client.HCPerfElement;
import com.sun.honeycomb.adm.cli.ConnectException; 
import com.sun.honeycomb.adm.cli.PrecisionFormatter;
import com.sun.honeycomb.adm.cli.PrintfFormat;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.client.ClientUtils;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class CommandPerfStats extends ShellCommand 
implements ExitCodes {

    public static final String STORE_ONLY_LABEL = "Store Only";
    public static final String STORE_MD_LABEL = "Add MD";
    
    /**
     * This represents the MD portion of the Store Both operation
     */
    public static final String STORE_MD_SIDE_LABEL = "Store Both (MD portion)";
    public static final String STORE_BOTH_LABEL = "Store"; // MD and Data
    public static final String RETRIEVE_ONLY_LABEL = "Retrieve";
    public static final String RETRIEVE_MD_LABEL = "Retrieve MD";
    public static final String QUERY_LABEL = "Query";
    public static final String DELETE_LABEL = "Delete";
    public static final String SCHEMA_LABEL = "Schema Get";
    public static final String WEBDAV_PUT_LABEL = "WebDAV Put";
    public static final String WEBDAV_GET_LABEL = "WebDAV Get";
    private final Option _optAll;
    private final Option _optHowLong;
    private final Option _optInterval;
    
    /**
     * Holds the summation of all the delta's for # ops and exec time
     * From this we can compute avg ops/sec and avg response time
     *
     * Avg Ops/sec = sum_delta_#_ops / sum_dela_elapseTime
     * Avg Response Time = delta_elapseTime / delta_#_ops
     */
    private AvgMetric avgStoreBoth;
    private AvgMetric avgStoreMd;
    private AvgMetric avgStoreMdSide;
    private AvgMetric avgStoreOnly;
    private AvgMetric avgRetrieveOnly;
    private AvgMetric avgRetrieveMd;
    private AvgMetric avgQuery;
    private AvgMetric avgDelete;
    private AvgMetric avgSchema;
    private AvgMetric avgWebdavGet;
    private AvgMetric avgWebdavPut;
    
    private static final int MAX_INTERVAL = 60 * 15;   // 15 minutes
    private static final int KILOBYTES_PER_MB = 1024;
    
    private PrintfFormat allStatsFormatter 
            = new PrintfFormat("  %-15s %8s %10s %10s %8s %10s %10s ");
    private PrintfFormat statsFormatter 
            = new PrintfFormat("  %-15s  %8s %10s  %12s");
    
    private int pollingInterval = 0;
    
    public CommandPerfStats (String name, String[] aliases, Boolean isHidden) {
        super (name, aliases, isHidden);
	_optAll = addOption(OPTION_BOOLEAN, 'a', "all");
        _optHowLong = addOption(OPTION_INTEGER, 't', "howlong");
        _optInterval = addOption(OPTION_INTEGER, 'i', "interval");
	addCellIdOption(true);
	addNodeOption(false);
	addForceOption();
    }
    

    public int main (Properties env, String[] argv) throws MgmtException, PermissionException, ConnectException {
        int    howlong = -1;
	boolean printAll;
	int retCode = handleStandardOptions(argv, true);
	if (retCode != EX_CONTINUE) {
	    return retCode;
	}
	
	String[] unparsedArgs = getRemainingArgs();
	if (unparsedArgs.length > 0) {
	    System.out.println("Unknown argument: " + unparsedArgs[0]);
	    usage ();
	    return EX_USAGE;
	}

	howlong = getOptionValueInteger (_optHowLong);
	pollingInterval = getOptionValueInteger (_optInterval);
	
	printAll = getOptionValueBoolean(_optAll);
	if (printAll) {
	    if (isHiddenOptionProceed() == false) {
		return EX_USERABORT;
	    }
	}
	
	// handleStandardOptions validated the node information so no
	// additional validation of node parameter is needed.
            
        /** Assign default values */
        if(pollingInterval < 0) {
            System.out.println(
		"Invalid interval of '" + pollingInterval 
		+ "' specified. Value must be positive.");
            return EX_USAGE;
        }else if (pollingInterval > MAX_INTERVAL) {
	    System.out.println("Specified polling interval is too large.  Maximum allowed\n"
		+ "polling interval is " + MAX_INTERVAL + " seconds.");
	    return EX_USAGE;
	}
	if(howlong < 0) {
            System.out.println("Negative how long " +howlong+ " is invalid.");
            return EX_USAGE;
        }
        if (pollingInterval == 0) {
            pollingInterval = 15;
        }
        if (howlong == 0) {
            howlong = -1;
        }

        
	
        /*
         * if time period specified expires, break from infinite loop
         * otherwise, display stats every 5 secs (default time interval) 
         */
        long startTimeMins = System.currentTimeMillis()/CliConstants.ONE_MINUTE;
        long curTimeMins = System.currentTimeMillis()/CliConstants.ONE_MINUTE;
        int intervalMillis = pollingInterval * CliConstants.ONE_SECOND;

	int nodeid = getNodeId();
	HCPerfStats perfStats;
	
	resetAvgStats();
	while (true) {

	    if ((howlong > 0) && ((curTimeMins - startTimeMins) > howlong)) {
		break;
	    }  

	    try {
		if (nodeid != -1) {
		    // display node perf stats
		    perfStats = getApi().getNodePerfStats(nodeid, pollingInterval, cellId);  
		} else {
		    // cluster stats
		    perfStats = getApi().getClusterPerfStats(pollingInterval, cellId);
		}
		if (printAll)
		    addToAvg(perfStats);
		if (perfStats != null) {
		    if(nodeid!=-1) 
			printNodePerfStats(perfStats, nodeid, printAll);
		    else 
			printCellPerfStats(perfStats, printAll);
		    Thread.currentThread().sleep(intervalMillis);
		} 
		if (printAll == false) {
		    // Pad with 2 lines.  This gives us a perfect 80x25 refresh
		    System.out.println("\n");
		}

	    } catch (InterruptedException ie) {
		System.out.println("Interrupted during sleep of " +pollingInterval + ".");
		return EX_SOFTWARE;
	    } 
	    curTimeMins = System.currentTimeMillis()/(60 * 1000); 
        } 
        return EX_OK; 
    }
   
    /**
     * Add the newly collected stats to the average objects so the average
     * over the run can be calculated.
     * @param stats the stats to add to the average object
     */
    private void addToAvg(HCPerfStats stats) {
	addToAvg(STORE_BOTH_LABEL, avgStoreBoth, stats.getStoreBoth());
	addToAvg(STORE_MD_LABEL, avgStoreMd, stats.getStoreMd());
	addToAvg(STORE_MD_SIDE_LABEL, avgStoreMdSide, stats.getStoreMdSide());
	addToAvg(STORE_ONLY_LABEL, avgStoreOnly, stats.getStoreOnly());
	addToAvg(RETRIEVE_ONLY_LABEL, avgRetrieveOnly, stats.getRetrieveOnly());
	addToAvg(RETRIEVE_MD_LABEL, avgRetrieveMd, stats.getRetrieveMd());
	addToAvg(QUERY_LABEL, avgQuery, stats.getQuery());
	addToAvg(DELETE_LABEL, avgDelete, stats.getDelete());
	addToAvg(SCHEMA_LABEL, avgSchema, stats.getSchema());
	addToAvg(WEBDAV_GET_LABEL, avgWebdavGet, stats.getWebdavGet());
	addToAvg(WEBDAV_PUT_LABEL, avgWebdavPut, stats.getWebdavPut());
    }
   
    /**
     * Add <code>valueToAdd</code> metrics to the <code>avg</code> metrics object
     * for the metric <code>metric</code>
     *
     * @param metric the metric type being added
     * @param avg the average metric object that is being added to
     * @param valueToAdd the values being added to <code>avg</code> object
     */
    private void addToAvg(String metric, AvgMetric avg, HCPerfElement valueToAdd) {
	if (valueToAdd == null)
	    return;
	
	BigInteger execTime = valueToAdd.getExecTime();
	BigInteger ops = valueToAdd.getOps();
	String opsPerSec = valueToAdd.getOpSec();
	if (execTime == null || ops == null || opsPerSec == null) {
	    // These should never be null
	    Logger.getLogger(CommandPerfStats.class.getName()).log(
		Level.WARNING, 
		"CLI Internal Error: "
		+ metric + " got unexpected null value, execTime="
		+ execTime + " ops=" + ops);
	    return;
	}
	
	// The underlying code on the server always does it calculations
	// using the time interval we specified.  We need the actual time
	// interval to get really accurate calculations here.  However,
	// since the design of performance needs to change (in where
	// the client should do the calculations) for now keep it simple and 
	// just use the time interval we set since we know this is what
	// the master is doing for us.  This won't be accurate if 2+ users
	// are doing performance monitoring.
	String kbPerSec = valueToAdd.getKbSec();
	if (kbPerSec == null)
	    avg.add(ops.longValue(), pollingInterval, execTime.longValue());
	else
	    avg.add(ops.longValue(), pollingInterval, execTime.longValue(), Double.valueOf(kbPerSec));
	
    }
    
    void printNodePerfStats(HCPerfStats stats, int nodeId, boolean printAll) {
        System.out.println("\n" + getNodeName(nodeId) 
	    + " Performance Statistics:\n");
        printDatabaseStats(stats, printAll);  
        printSysPerf(stats);
	System.out.println();
    }

    void printCellPerfStats(HCPerfStats stats, boolean printAll) {
        System.out.println("\nCell Performance Statistics:\n");
        printDatabaseStats(stats, printAll);
        printSysPerf(stats);
    }
   
    private void printDatabaseStats(HCPerfStats stats, boolean printAll) {
	if (printAll) {
	    System.out.println(
		"                     Avg.      Avg        Avg.   Run Avg.  Run Avg.   Run Avg.");
	    System.out.println(
		"                    Op/sec  Resp Time     KB/sec  Op/sec   Resp Time   KB/sec");
	    System.out.println(allStatsFormatter.sprintf(
		new String[] { 
		    "",
		    "--------", 
		    "----------",
		    "----------",
		    "--------",
		    "----------",
		    "----------",
		}));  
	} else {
	    System.out.println(
		"                               Avg           Avg");
	    System.out.println(
		"                     # Ops    Op/sec       KB/sec");
	    System.out.println(statsFormatter.sprintf(
		new String[] { 
		    "",
		    "--------", 
		    "----------",
		    "------------",
		}));  
	}
	if (printAll) {
	    printDatabasePerfElement(STORE_ONLY_LABEL, 
	        stats.getStoreOnly(), avgStoreOnly, printAll);
	}
        printDatabasePerfElement(STORE_MD_LABEL, 
	    stats.getStoreMd(), avgStoreMd, printAll);
	
	// Don't output this stat for now.  It's different than
	// all the other stats since it's the MD portion of
	// Store Both.
	/*
	if (printAll) {
	    printDatabasePerfElement(STORE_MD_SIDE_LABEL, 
		stats.getStoreMdSide(), avgStoreMdSide, printAll);
	}
	 */
        printDatabasePerfElement(STORE_BOTH_LABEL, 
	    stats.getStoreBoth(), avgStoreBoth, printAll);
        printDatabasePerfElement(RETRIEVE_ONLY_LABEL, 
	    stats.getRetrieveOnly(), avgRetrieveOnly, printAll);
        printDatabasePerfElement(RETRIEVE_MD_LABEL, 
	    stats.getRetrieveMd(), avgRetrieveMd, printAll);
	printDatabasePerfElement(DELETE_LABEL, 
	    stats.getDelete(), avgDelete, printAll);
	if (printAll) {
	    printDatabasePerfElement(SCHEMA_LABEL, 
		stats.getSchema(), avgSchema, printAll);
	}
        printDatabasePerfElement(QUERY_LABEL, 
	    stats.getQuery(), avgQuery, printAll);
        printDatabasePerfElement(WEBDAV_PUT_LABEL, 
	    stats.getWebdavPut(), avgWebdavPut, printAll);
        printDatabasePerfElement(WEBDAV_GET_LABEL, 
	    stats.getWebdavGet(), avgWebdavGet, printAll);
    }

    private void printSysPerf(HCPerfStats stats) {
	
        System.out.println("\nHive Performance Statistics:\n");
        System.out.println("  Load 1m: "+
                           getLoadAvgValue(stats.getLoadOneMinute())
                           +" Load 5m: "+
                           getLoadAvgValue(stats.getLoadFiveMinute())
                           +" Load 15m: "+
                           getLoadAvgValue(stats.getLoadFifteenMinute())
                           );
        System.out.println("  Disk Used: "+
                           getDiskSize(stats.getDiskUsedMb())
                           +"  Disk Total: "+
                           getDiskSize(stats.getDiskTotalMb())
                           +"  Usage: "+
                           getPercentage(stats.getUsePercent()));
	System.out.println();

    }

    private void printDatabasePerfElement(
	String elementName, 
	HCPerfElement element,
	AvgMetric avg,
	boolean printAll) {
	if (printAll) {
	    String kbSec = element.getKbSec();
	    String runKBSec = null;
	    if (kbSec != null)
		runKBSec = Double.toString(avg.getRunAvgKBTransferred());
	    System.out.println(allStatsFormatter.sprintf(
		new String[] { 
		    new StringBuffer(elementName).append(":").toString(),
		    getDoubleValue(element.getOpSec()),
		    PrecisionFormatter.formatValue(avg.getAvgResponseTime(), 2),
		    getDoubleValue(kbSec),
		    PrecisionFormatter.formatValue(avg.getRunAvgOpPerSec(), 2),
		    PrecisionFormatter.formatValue(avg.getRunAvgResponseTime(), 2),
		    getDoubleValue(runKBSec)
		}));    
	} else {
	    System.out.println(statsFormatter.sprintf(
		new String[] { 
		    new StringBuffer(elementName).append(":").toString(),
		    getLongValue(element.getOps()), 
		    getDoubleValue(element.getOpSec()),
		    getDoubleValue(element.getKbSec()),
		}));     
	}
    }
    
    private String getLongValue(BigInteger value) {
	if (value == null)
	    return "-";
	return PrecisionFormatter.formatValue(value.longValue());
    }

    private String getDoubleValue(String value) {
	if (value == null || value.length() == 0)
	    return "-";
	try {
	    double dValue = Double.parseDouble(value);
	    if (dValue < 0) {     // We use -1 to represent metric isn't avail
		return "-";
	    }
	    return PrecisionFormatter.formatValue(dValue, 2);
	}
	catch (NumberFormatException nfe) {
	    return value;
	}
    }
    
    private String getLoadAvgValue(String value) {
	if (value == null || value.length() == 0)
	    return "-";
	try {
	    double fValue = Float.parseFloat(value);
	    return PrecisionFormatter.formatValue(fValue, 2);
	}
	catch (NumberFormatException nfe) {
	    return value;
	}
    }
    
    private String getPercentage(String value) {
	if (value == null)
	    return "-";
	try {
	    double dValue = Double.parseDouble(value);
	    return ClientUtils.getPercentage(dValue);
	}
	catch (NumberFormatException nfe) {
	    return new StringBuffer().append(value).append("%").toString();
	}
    }
    
    public String getDiskSize(BigInteger value) {
	if (value == null)
	    return "-";
	return ClientUtils.reduceMBStorageUnit(value.longValue());
    }
    
    /**
     * @param value the value to divide
     * @param divisor the # to divide value by
     * @return String null if value or divisor is null other
     * the resulting double value as a string
     */
    private String divide(BigInteger value, BigInteger divisor) {
	if (divisor == null || value == null)
	    return null;
	if (divisor.longValue() == 0)
	    return new String("0");
	return Double.toString(value.doubleValue()/divisor.longValue());
    }
    
    private void resetAvgStats() {
	avgStoreBoth = new AvgMetric();
	avgStoreMd = new AvgMetric();
	avgStoreMdSide = new AvgMetric();
	avgStoreOnly  = new AvgMetric();
	avgRetrieveOnly = new AvgMetric();
	avgRetrieveMd = new AvgMetric();
	avgSchema = new AvgMetric();
	avgQuery = new AvgMetric();
	avgDelete = new AvgMetric();
	avgWebdavGet = new AvgMetric();
	avgWebdavPut = new AvgMetric();
    }
    
    /**
     * Class used for calculating averages for a given metric over the
     * course of a performance run.  The same add() method must be used
     * for all metrics added.  Don't mix the two.
     */
    public class AvgMetric {
	
	private long total_ops = 0;
        private long delta_num_ops = 0;
	private long delta_time = 0;    // in seconds
	private long total_time = 0;    // in seconds
	private long delta_execTime = 0;  // in miliseconds
	private long total_execTime = 0;  // in miliseconds
	private double total_kbytes_xfer = -1;
	
	public AvgMetric() {}
    
	/**
	 * @param ops the # of operations performed between T1 and T2 as reported
	 * @param timeInterval the time interval (T2 - T1)
	 * @param execTime the executine time between T1 and T2 as reported
	 */
        public void add(long ops, long interval, long execTime) {
	    delta_num_ops = ops;
	    total_ops += ops;
	    delta_time = interval;
	    total_time += interval;
	    delta_execTime = execTime;
	    total_execTime += execTime;
	}
	
	/**
	 * @param ops the # of operations performed between T1 and T2
	 * @param timeInterval the time interval (T2 - T1)
	 * @param execTime the executine time between T1 and T2
	 * @param kbytes_xfer the kbytes transferred between T1 and T2
	 */
	public void add(long ops, long interval, long execTime, double kbytes_xfer) {
	    add(ops, interval, execTime);
	    if (total_kbytes_xfer == -1)
		total_kbytes_xfer = kbytes_xfer;
	    else
		total_kbytes_xfer += kbytes_xfer;
	}
	
	/**
	 * @return double average response time over the course of
	 * the entire performance run. 
	 */
	public double getRunAvgOpPerSec() {
            if (total_time == 0)
                return 0;
            return total_ops/(double)total_time;
        }
	
	/**
	 * @return double average response time between T2 - T1.
	 */
	public double getAvgResponseTime() {
	    if (delta_num_ops == 0)
		return 0;
	    return (double)delta_execTime/delta_num_ops;
	}
	
	/**
	 * @return double average response time over the course of
	 * the entire performance run. 
	 */
        public double getRunAvgResponseTime() {
            if (total_ops == 0)
                return 0;
            return total_execTime/(double)total_ops;
        }
	
	/**
	 * @return double average KB transferred over the course of
	 * the entire performance run.  -1 if statistic isn't available.
	 */
        public double getRunAvgKBTransferred() {
	    if (total_kbytes_xfer == -1)
		return -1;
            if (total_time == 0)
                return 0;
            return total_kbytes_xfer/(double)total_time;
        }
	
    }
}
