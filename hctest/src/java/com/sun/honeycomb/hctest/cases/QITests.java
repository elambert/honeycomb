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



package com.sun.honeycomb.hctest.cases;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;

/**
 * This class is used to test the functionalities of Query Inegrity.
 * The purpose of this test is to verify the changes related to DataDcotor and HADB
 * will not affect the semantics of QueryIntegrity.
 *
 * The tests include:
 * - Verify the checkIndex method.
 * - QIT is reset to 0 after hadb is wiped.
 * - QIT is reset to 0 after cluster is rebooted.
 * - QI state changes when master failover happened.
 * - QI state changes when DataDoctor's populate_ext_cache_cycle changed.
 * - The least QIT is reported across query chunks.
 * - The least QIT is reported after hadb wiped during a query.
 *
 * Pre-Condition:
 *   1) The cluster to be tested has to be in HAFaultTorleeant state.
 *   2) The Query Integrity Time is advancing
 *
 * How to run this test:
 *    
 * CAUTION: THIS TEST WILL WIPE HADB.  IF YOUR CLUSTER IS FULL, IT MAY TAKE 
 *          LONG TIME.
 *
 * 1. load QA schema
 * 2. /opt/test/cur/bin/runtest --ctx include=queryIntegrity:explore:cluster=devxxx com.sun.honeycomb.hctest.cases.QITests
 * 
 * example: running test on cl601.east againt dev601.east
 *   /opt/test/cur/bin/runtest --ctx nolog=true:qb=no:include=queryIntegrity:explore:cluster=dev601:auditdisable=true com.sun.honeycomb.hctest.cases.QITests
 */
public class QITests extends HoneycombLocalSuite {

    protected static String CUSTOMER_WARNING_STRING = "***********";
    protected static String CYCLENAME = "populate_ext_cache_cycle"; 
    protected static String DATEFORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";
    protected static final int TOTAL_ADD_MD = 15;

    private CLI cli = null;
    private ClusterMembership cm = null;
    private int numOfNodes = 0;
    private int savedRepopExtCycle = 0;
    private int currentCellid = 0;

    public QITests() {
        super();
    }

    /**
     * SetUp
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();

        TestCase skip = createTestCase("queryIntegrity");
        // no tags so won't be run unless directly invoked
        if (skip.excludeCase()) // should I run?
            return;

        TestBed b = TestBed.getInstance();
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        cli = new CLI(b.adminVIP);
        currentCellid = getCellid();
        cm = new ClusterMembership(-1, b.adminVIP, 0, currentCellid, b.adminVIP, null);
        cm.setQuorum(true);
        cm.initClusterState();
        numOfNodes = cm.getNumNodes();
        savedRepopExtCycle = getPopulateExtCacheCycle();
    }

    public void tearDown() throws Throwable {
        super.tearDown();
        if (cli == null)
            return;

        changeDDExtCacheRepopCycle(savedRepopExtCycle);
    }

    public void testQIBasics() throws Throwable {
        if (cli == null) // excluded in setUp
            return;

        TestCase self = createTestCase("QIBasic");
        
        /* Verify the checkIndexed method */
        Log.INFO("=== test checkIndexed method ...");
        verifyCheckIndexed();
        Log.INFO("=== test checkIndexed method SUCCEEDED");

        /* redefine the populate_ext_cache_cycle */
        int interval = 600; // 10 minutes 

        try {

            /*
             * QueryIntegrity is up-to-date.  Changing the DD's
             * populate_ext_cache_cycle will shift QueryIntegrity from
             * up-to-date to static.
             */
            Log.INFO("=== test QIT change from up-to-date to static... ");
            verifyQITIsUpToDate();
            changeDDExtCacheRepopCycle(interval);
            verifyQITIsStatic();
            Log.INFO("=== test QIT change from up-to-date to static SUCCEEDED");

            /*
             * Assuming no Datadoctor error during the first cycle of repop, 
             * the QIT should be up-to-date again.
             */
            Log.INFO("=== test QIT change from static to up-to-date... ");
            sleep(interval * 1000);
            verifyQITIsUpToDate();
            Log.INFO("=== test QIT change from static to up-to-date SUCCEEDED");
        } catch (HoneycombTestException e) {
            Log.DEBUG(Log.stackTrace(e));
            self.testFailed(e.toString());
            throw e;
        }

        try {

            /*
             * Verify the least QIT is reported across query chunks. 
             */
            Log.INFO("=== test QIT across query chunks ...");
            verifyQITAcrossQueryChunks();
            Log.INFO("=== test QIT across query chunks SUCCEEDED");

            /*
             * Verify the least QIT is reported after wipe during a query.
             */
            Log.INFO("=== test QIT with wipe during a query ...");
            verifyQITWithWipeDuringQuery();
            Log.INFO("=== test QIT with wipe during a query SUCCEEDED");

            /* 
             * Verify the QIT is static within the DD's first repop cycle
             * after master failover.
             */
            Log.INFO("=== test query integrity with master failover ...");
            verifyQITWithMasterFailover();
            Log.INFO("=== test query integrity with master failover SUCCEEDED");

            /*
             * Verify the QIT is reset after wipe.
             */
            Log.INFO("=== test query integrity with hadb wipe ...");
            wipeHadb();
            verifyQITChangeToZero();
            Log.INFO("=== test query integrity with hadb wipe SUCCEEDED");

            /*
             * Verify the QIT is reset after rebooting the cluster.
             */
            Log.INFO("=== test query integrity with reboot ...");
            rebootCluster();
            verifyQITChangeToZero();
            Log.INFO("=== test query integrity with reboot SUCCEEDED");

        } catch (Throwable t) {
            self.testFailed(t.toString());
            throw t;
        }

        self.testPassed("OK");
    }

    private void verifyCheckIndexed() throws Throwable {

        CmdResult storeResult = null;
        CmdResult addMDResult = null;
        HashMap hm = new HashMap();

        try {
            verifyHadb();
            storeResult = store(getFilesize());
            hm.put(HoneycombTestConstants.MD_VALID_FIELD, storeResult.mdoid);
            addMDResult = addMetadata(storeResult.mdoid, hm);
            String query = HoneycombTestConstants.MD_VALID_FIELD + 
                "='" + storeResult.mdoid + "'";
            long qit = checkIndexed(query, addMDResult);
            Log.INFO("Object is successfully indexed");
            if (qit == 0) {
                Log.INFO("Query is incomplete");
            } else {
                Log.INFO("Query is complete");
                Log.INFO("Query Integrity Time (from API) is " + formatQIT(qit));
            }
        } catch (HoneycombTestException e) {
            Log.DEBUG(Log.stackTrace(e));
            throw e;            
        }
    }

    /* 
     * Verify the  QIT will reset to 0 after rebooting the cluster.
     */
    private void verifyQITChangeToZero() throws Throwable {

        long qit = 0L;
        long qit2 = 0L;
        try {
            qit = getQueryIntegrityTimeFromCLI();
            if (0 != qit) {
                throw new HoneycombTestException(
                    "QueryIntegrityTime is not reset to 0 " +
                    "after rebooting the cluster");
            }
            waitForHadb(60 * 60 ); // 1 hour
       //     sleep(60 * 1000);
            qit2 = getQueryIntegrityTimeFromCLI();
            if (0 != qit2) {
        Log.INFO("Query Integrity Time is " + formatQIT(qit2));
                throw new HoneycombTestException(
                    "QueryIntegrityTime is not reset to 0 " +
                    "after rebooting the cluster and HADB is running");
            }
        } catch (HoneycombTestException e) {
            Log.DEBUG(Log.stackTrace(e));
            throw e;
        }

        if (0 != qit2) {
            throw new HoneycombTestException(
                "QueryIntegrityTime is not reset to 0 " +
                "after rebooting the cluster");
        }
    }

    /* Verify the QIT is reset to 0 after wipe */
    /*
    private void verifyQITChangeToZeroAfterWipe() throws Throwable {

        long qit = 0L;
        try {
            wipeHadb();
            qit = getQueryIntegrityTimeFromCLI();
        } catch (HoneycombTestException e) {
            Log.DEBUG(Log.stackTrace(e));
            throw new HoneycombTestException(e.getMessage());
        }

        if ( 0 != qit) {
            throw new HoneycombTestException( 
                "QueryIntegrityTime is not reset to 0 after wiping HADB"); 
        }
    }
    */
    
    /* Verify the QIT is static within the DD's fisrt repop cycle
     * after master failover */
    private void verifyQITWithMasterFailover() throws Throwable {

        String nodeName = cm.getMaster().getName();
        String masterNode = "NODE-" + nodeName.substring(3);
        Log.INFO("master node: " + masterNode);
        disableNode(masterNode);
        waitForHadb(60 * 60);
        verifyQITIsStatic();
        enableNode(masterNode);
    }


    /* Verify the QIT is 0 if hadb is wiped during a query. */
    private void verifyQITWithWipeDuringQuery() throws Throwable {
        QITWithCookies(true);
    }

    /* Verify the least QIT is reported across query chunks. */
    private void verifyQITAcrossQueryChunks() throws Throwable {
        QITWithCookies(false);
    }

    private void QITWithCookies(boolean needReboot) 
        throws HoneycombTestException{

        CmdResult storeResult = null;
        HashSet oids = null;

        try {
            storeResult = store(getFilesize());
    
            Log.INFO("Stored file of size " + getFilesize() +
                " as oid " + storeResult.mdoid);
    
            // Now, we add MD using the oid above as a unique value
            HashMap hm = new HashMap();
            hm.put(HoneycombTestConstants.MD_VALID_FIELD, storeResult.mdoid);
    
            oids = new HashSet();
            for (int i = 0; i < TOTAL_ADD_MD; i++) {
                CmdResult addMetadataResult = addMetadata(storeResult.mdoid, hm);
                Log.INFO("add metadata returned " + addMetadataResult.mdoid);
                oids.add(addMetadataResult.mdoid);
            }

            String query = HoneycombTestConstants.MD_VALID_FIELD + "='" + 
                storeResult.mdoid + "'";
            Log.INFO("Running query " + query);

            int numPerFetch=1; 
            int numTotalResults = 0;
            long savedQIT = 0L;
            boolean firstTime = true;
            boolean rebooted = false;

            Log.INFO("resultsPerFetch = " + numPerFetch);
            CmdResult cr = query(query, numPerFetch);
            QueryResultSet qrs = (QueryResultSet) cr.rs;
            while (qrs.next()) {
                ObjectIdentifier result = qrs.getObjectIdentifier();
                long qit = qrs.getQueryIntegrityTime();
                if (firstTime) {
                    savedQIT = qit;
                }
                Log.INFO("QueryIntegrityTime is " + formatQIT(qit));
                if (qit > savedQIT) {
                     throw new HoneycombTestException(
                             "Query Integrity Time should not " +
                             "advance across query chunks");
                }
                Log.INFO("Found result " + result);
                if (!oids.contains(result.toString())) {
                     throw new HoneycombTestException(
                              "oid " + result.toString() + 
                              ", should not be " +
                              "returned by this query");
                }
                numTotalResults++;
                if (numTotalResults > TOTAL_ADD_MD) {
                    throw new HoneycombTestException(
                              "Too many results returned");
                }
                if (needReboot && !rebooted) {
                    wipeHadb();
                    waitForHadb(60 * 60);
                    rebooted = true;
                    // should wait long enough 
                    // to have some records repopulated in hadb
                    sleep(20 * 60 * 1000);
                }
                
                // sleep 5 seconds to let QIT advance
                sleep(5 * 1000);
            }
        } catch (HoneycombTestException e) {
            throw e;
        } catch (Throwable e) {
            throw new HoneycombTestException(e.getMessage()); 
        }
    }

    /* Get the QIT via CLI sysstat command. */
    private long getQueryIntegrityTimeFromCLI()
        throws HoneycombTestException {
        String QINOTESTABILISHED = "Query Integrity not established";
        String QIESTABILISHED = "Query Integrity established as of ";
        boolean found = false;
        long qit = 0L;
        String [] output = runCommand("sysstat -c " + currentCellid);
        for (int i = 0, n = output.length; i < n; i++) {
            String line = output[i].trim();
            if (line.startsWith("Query Integrity")) {
                Log.INFO(line);
                if (line.equals(QINOTESTABILISHED)) {
                    found = true;
                }
                if (line.startsWith(QIESTABILISHED)) {
                    found = true;
                    java.util.Date jd = CanonicalEncoding.decodeJavaDate(
                            line.substring(QIESTABILISHED.length()),
                            DATEFORMAT,
                            "UTC");
                    qit = jd.getTime();
                }
            }
        }
        if (!found) {
            throw new HoneycombTestException(
                "No output from 'sysstat' command start with 'Query Integrity'");
        }
        return qit;
    }

    /* Get the QIT via client API */
    private long getQueryIntegrityTimeFromAPI()
        throws HoneycombTestException {
        HashMap hm = new HashMap();
        CmdResult storeResult = store(getFilesize());
        hm.put(HoneycombTestConstants.MD_VALID_FIELD, storeResult.mdoid);
        CmdResult addMDResult = addMetadata(storeResult.mdoid, hm);
        String query = HoneycombTestConstants.MD_VALID_FIELD + 
                "='" + storeResult.mdoid + "'";
        return checkIndexed(query, addMDResult);
    }

    /* Verify the QIT is up-to-date, i.e. QIT is advancing */
    private void verifyQITIsUpToDate()
        throws HoneycombTestException {

        long qit = getQueryIntegrityTimeFromCLI();
        Log.INFO("Query Integrity Time is " + formatQIT(qit));
        sleep(60 * 1000);
        long qit2 = getQueryIntegrityTimeFromCLI();
        Log.INFO("Query Integrity Time is " + formatQIT(qit2));

        if (qit == 0 || qit2 == 0) {
            throw new HoneycombTestException(
                "verifyQITIsUpToDate: QueryIntegrityTime is not established");
        }

        if (qit2 <= qit) {
            throw new HoneycombTestException(
                  "QueryIntegrityTime is not up-to-date");
        }

    }

    /* Verify the QIT is static, i.e. QIT is not advancing. */
    private void verifyQITIsStatic()
        throws HoneycombTestException {

        long qit = getQueryIntegrityTimeFromCLI();
        Log.INFO("Query Integrity Time is " + formatQIT(qit));
        sleep(60 * 1000);

        long qit2 = getQueryIntegrityTimeFromCLI();
        Log.INFO("Query Integrity Time is " + formatQIT(qit2));

        if (qit == 0 || qit2 == 0) {
            throw new HoneycombTestException(
                "verifyQITIsUpToDate: QueryIntegrityTime is not established");
        }

        if (qit != qit2) {
            throw new HoneycombTestException(
                "verifyQITIsSTatic: QueryIntegrityTime is not static");
        }
    }

    private long checkIndexed(String query, CmdResult cr) 
        throws HoneycombTestException {
        if (cr == null) {
            throw new HoneycombTestException(
                "checkIndexed failed: result is null)");
        }

        if (!cr.pass) {
            throw new HoneycombTestException(
                "checkIndexed failed: " + cr.getExceptions());
        }
        if (cr.sr == null) {
            throw new HoneycombTestException(
                "checkIndexed failed: SystemRecord is null");
        }

        if (cr.sr.isIndexed() == false) {
            Log.INFO("Object is stored OK, but not yet indexed.");
            try {
                NameValueObjectArchive oa = new NameValueObjectArchive(
                    TestBed.getInstance().getDataVIP());

                while (oa.checkIndexed(cr.sr.getObjectIdentifier()) == 0) {
                    Log.INFO("Object is still not indexed...");
                    Log.INFO("Query Integrity Time (from CLI) is " + 
                            formatQIT(getQueryIntegrityTimeFromCLI()));

                    if (isQueryable(query, cr)) {
                        throw new HoneycombTestException(
                            "Object is not indexed, but it is queryable");
                    }

                    Thread.sleep(10000);  // wait 10 seconds
                }
            } catch (HoneycombTestException e) {
                throw e;
            } catch (Throwable e) {
                throw new HoneycombTestException(
                    "checkIndexed failed: " + e.getMessage());
            }
        }
        return getQueryIntegrityTime(query, cr);
    }

    private boolean isQueryable(String str, CmdResult result) {
        try {
            CmdResult cr = query(str);
            QueryResultSet qrs = (QueryResultSet)cr.rs;

            while (qrs.next()) {
                ObjectIdentifier oid = qrs.getObjectIdentifier();
                if ((result.mdoid).equals(oid.toString())) {
                    return true;
                }
            }
        } catch (Throwable e) {
            Log.ERROR("isQueryable failed: " + Log.stackTrace(e));
        }

        return false;
    }

    /* Get the QIT from the query results. */
    private long getQueryIntegrityTime(String str, CmdResult result) 
        throws HoneycombTestException {
        long qt = 0L;
        boolean found = false;
        try {
            CmdResult cr = query(str);
            QueryResultSet qrs = (QueryResultSet)cr.rs;

            while (qrs.next()) {
                ObjectIdentifier oid = qrs.getObjectIdentifier();
                if ((result.mdoid).equals(oid.toString())) {
                    found = true;
                    qt = qrs.getQueryIntegrityTime();
                }
            }
        } catch (Throwable e) {
            throw new HoneycombTestException(
                    "getQueryIntegrityTime failed: " + e);
        }

        if (!found) {
            throw new HoneycombTestException("Object is not queryable");
        }
        return qt;
    }

    private String formatQIT(long qt) {
        SimpleDateFormat formatter = 
            new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return qt == 0 ? "0" : formatter.format(new Timestamp(qt));
    }

    private boolean isQueryComplete(String str, CmdResult result) {
        try {
            CmdResult cr = query(str);
            QueryResultSet qrs = (QueryResultSet)cr.rs;

            while (qrs.next()) {
                ObjectIdentifier oid = qrs.getObjectIdentifier();
                if ((result.mdoid).equals(oid.toString())) {
                    return qrs.isQueryComplete();
                }
            }
        } catch (Throwable e) {
            Log.ERROR("isQueryable failed: " + Log.stackTrace(e));
        }
        return false;
    }

    /* Disable a node. */
    private void disableNode(String nodeName)
        throws HoneycombTestException {
        try {
            cli.runCommand("hwcfg -F -c " + currentCellid + " -D " + nodeName);
            Log.INFO("Node (" + nodeName +") is disabled.");
            sleep(5 * 60 * 1000);
        } catch (Throwable e) {
            throw new HoneycombTestException(
                    "disableNode: " + e.getMessage());
        }
    }

    /* Enable a node. */
    private void enableNode(String nodeName)
        throws HoneycombTestException {
        try {
            cli.runCommand("hwcfg -F -c " + currentCellid + " -E " + nodeName);
            Log.INFO("Node (" + nodeName +") is enabled.");
            sleep(30 * 60 * 1000); // waitn for 30 min
        } catch (Throwable e) {
            throw new HoneycombTestException(
                    "enableNode: " + e.getMessage());
        }
    }

    /* Modified the DD's populate_ext_cache_cycle. */
    private void changeDDExtCacheRepopCycle(int interval)
        throws HoneycombTestException {
        try {
            cli.runCommand("ddcfg -F -c " + currentCellid + " " + CYCLENAME + " " + interval);
            sleep(60 * 1000);
            verifyCommandOutput("ddcfg -F -c " + currentCellid  + " " + CYCLENAME, 
                   CYCLENAME + " = " + interval);
        } catch (Throwable e) {
            throw new HoneycombTestException(
                    "changeDDExtCacheRepopCycle: " + e.getMessage());
        }
    }

    /* Reboot the cluster */
    private void rebootCluster() 
        throws HoneycombTestException {
        try {
            verifyCommandOutput("reboot -F -c " + currentCellid,
                    "Exiting; cell [" + currentCellid + "] is rebooting.");
            sleep(10 * 60 * 1000); // 10 min
        } catch (Throwable e) {
            throw new HoneycombTestException("rebootCluster: " + e.getMessage());
        }
    }

    /* wipe HADB */
    private void wipeHadb() 
        throws HoneycombTestException {
        try {
            cli.runCommand("hadb -F -c " + currentCellid + " clear");
            do {
                sleep(60 * 1000);
            } while (isHadbOperational()); 
        } catch (Throwable e) {
            throw new HoneycombTestException("wipeHadb: " + e.getMessage());
        }
    }

    /* Waiting for HADB to operational. */
    private void waitForHadb(int timeout) 
        throws HoneycombTestException {
        boolean verified = false;
        long endTime =System.currentTimeMillis() + timeout * 1000;
        Log.INFO("Waiting for HADB to be operational ...");
        do {
            verified = isHadbOperational();
            if (!verified) {
                sleep(10 * 1000);
            }
        } while (!verified && System.currentTimeMillis() < endTime);
        
        if (!verified) {
            throw new HoneycombTestException(
                "HADB is not operational after " + timeout + " seconds.");
        }
        
    }

    /* Verify HADB is operational */
    private void verifyHadb() throws HoneycombTestException {
        if (!isHadbOperational()) {
            throw new HoneycombTestException ("HADB is not operational");
        }
    }

    /* Check to see if HADB is operational. */
    private boolean isHadbOperational() throws HoneycombTestException {
        boolean verified = false;
        String [] output = runCommand("hadb -F -c " + currentCellid + " status");
        String line = null;
        for (int i = 0, n = output.length; i < n && !verified; i++) {
            line = output[i].trim();
            if (line.indexOf("FaultTolerant") != -1 ||
                line.equals("Operational")) {
                Log.INFO("Query Engine Status: " + line);
                verified = true;
            }
        }
        return verified;
    }
   
    /* Retrieve the DD's populate_ext_cache_cycle. */
    private int getPopulateExtCacheCycle() 
        throws HoneycombTestException {
        String str = "populate_ext_cache_cycle = ";
        int interval = 0; 
        String [] output = runCommand("ddcfg -F -c " + currentCellid);
        for (int i = 0, n = output.length; i < n; i++) {
            String line = output[i].trim();
            if (line.indexOf(str) != -1) {
                interval = Integer.parseInt(line.substring(str.length()));
                break;
            }
        }
        return interval;
    }
    
    /* Verify the CLI optput. */
    private void verifyCommandOutput(String cmd, String expected) 
        throws HoneycombTestException {
        boolean verified = false;
        String [] output = runCommand(cmd);
        String line = null;
        for (int i = 0, n = output.length; i < n && !verified; i++) {
            line = output[i].trim();
            if (line.equals(expected)) {
                Log.INFO(line);
                verified = true;                
            }
        }
        
        if (!verified) {
            throw new HoneycombTestException (
                    "Command (" + cmd + ") output does not contain " +
                    "the expected line: " + expected);
        }
    }
   
    /* Execute the spcified CLI command.  The customer warning banner 
     * is stripped out from the output.
     */
    private String[] runCommand(String cmd) 
        throws HoneycombTestException {
        boolean isWarning = false;
        ArrayList out = new ArrayList();
        try {
            BufferedReader br = cli.runCommand(cmd);
            String lines = HCUtil.readLines(br);
            String [] output = tokenizeIt(lines, "\n");   
            for (int i = 0, n = output.length; i < n; i++) {
                String line = output[i].trim();
                if (line.startsWith(CUSTOMER_WARNING_STRING)) {
                    if (isWarning) {
                        isWarning = false;
                        continue;
                    } else {
                        isWarning = true;
                    }
                }

                // Skip customer warnings
                if (isWarning || line.equals("")) 
                    continue;
                    
                out.add(line);
            }
        } catch (Throwable e) {
            throw new HoneycombTestException ("runCommand: " + e.getMessage());
        }
        String[] result = new String[out.size()];
        for (int i = 0, n = out.size(); i < n; i++) {
            result[i] = (String)out.get(i);
        }
        return result;
    }

    // Pass the delimiter as parameter
    private String [] tokenizeIt(String string, String delimiter) 
        throws Exception {
        StringTokenizer st = new StringTokenizer(string, delimiter);
        String [] tokenized;
        tokenized = new String[ st.countTokens()];
        int mctr = 0;
    
        while (st.hasMoreTokens()) {
            tokenized[mctr] = st.nextToken().trim();
            mctr++;
        }
         
        return tokenized ;
    } 

    // Find a valid cell ID for this test.
    private int getCellid()  
        throws HoneycombTestException {
        int cellid = -1;
        int cellCount = 0;

        String[] result = runCommand("hiveadm -s");
        // Here is the output looks like:
        // There is/are 1 cell(s) in the hive:
        // - Cell 0: adminVIP = 10.8.119.5, dataVIP = 10.8.119.6

        try {
            
            String[] line = tokenizeIt(result[0], " ");
            cellCount = Integer.parseInt(line[2]);
            
            for (int i = 1;  i < result.length; i++) {
                line = tokenizeIt(result[i], " ");
                if (line[2].indexOf(":") != -1){
                    String str = line[2].substring(0, line[2].length()-1);  
                    try {
                        cellid = Integer.parseInt(str);
                    } catch (NumberFormatException e) {
                        // try next cell
                        continue;
                    }
                    break;
                }
            }
        } catch (Exception e){        
            throw new HoneycombTestException(e.getMessage());
        }

        if (cellid == -1) {
            throw new HoneycombTestException(
                "Can't find a valid cell id in this hive");
        }

        Log.INFO("Total number of cells: " + cellCount);
        Log.INFO("Query Integrity test is running on the cell whose id is " +
                cellid);
        return cellid;
    }
}
