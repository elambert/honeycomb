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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.NameValue;
import com.sun.honeycomb.test.TestRunner;

//import org.postgresql.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
//import java.sql.Statement;
//import java.sql.SQLException;
import java.sql.Timestamp;
//import java.sql.Types;

import java.util.LinkedList;

/**
 ** Raw interface to the 'streaming' metrics tables in the audit db. 
 **
 ** Postgres jdbc driver: http://jdbc.postgresql.org/
 **
 ** Each instance has its own Connection to the database.
 ** ?? Post-instantiation, the connection is set with Auto Commit off and
 ** ?? the transaction isolation level set to serializable.
 **
 ** This class is not thread-safe.
 **
 **/
public class AuditDBMetricsClient {

    ////////////////////////////////////////////////////////////////
    // perf table
    public static final String PERF_MONITOR_TABLE = "perf_monitor";
    public static final String ALERT_MONITOR_TABLE = "alert_monitor";

    // perf_monitor.type values
    public static final int STORE = 1;
    public static final int RETRIEVE = 2;
    public static final int INTERNAL_STORE = 3;
    public static final int INTERNAL_RETRIEVE = 4;
    public static final int INTERNAL_OPEN = 5;
    public static final int INTERNAL_CLOSE = 6;
    public static final int INTERNAL_RENAME = 7;

    private String cluster = TestRunner.getProperty(
                                       HoneycombTestConstants.PROPERTY_CLUSTER);
    private String url = null;

    private static final String types[] = {
        "<error>", 
        "store", "retrieve", "int_store", "int_retrieve",
        "open", "close", "rename" 
    };

    // db connection
    private static Connection conn = null;

    private boolean verbose = false;
    public void setVerbosity(boolean val) {
        verbose = val;
    }

    public AuditDBMetricsClient() throws Throwable {
        if (cluster == null)
            throw new HoneycombTestException("property not defined: " +
                                       HoneycombTestConstants.PROPERTY_CLUSTER);

        String dbHost = System.getProperty(HCLocale.PROPERTY_DBHOST);
        if (dbHost == null) {
            throw new HoneycombTestException("Not found in System properties: "+
                                             HCLocale.PROPERTY_DBHOST);
        }
        String dbspec = dbHost + "/" + cluster;

        url = "jdbc:postgresql://" + dbspec;
                         // + "?prepareThreshold=1";
        //
        //  load driver - it registers w/ jdbc
        //
        Class.forName("org.postgresql.Driver");

        getConn();

        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));

        Log.INFO("audit db client connected: " + dbspec);
    }

    private void getConn() throws Throwable {

        if (conn != null)
            return;

        //
        //  get conn
        //
        //  set reuse threshold for faster PreparedStatement prep -
        //  assuming for now that all stmts will be reused a lot
        // http://jdbc.postgresql.org/documentation/head/server-prepare.html
        //
        conn = DriverManager.getConnection(url,
                                           cluster,   // =db user
                                           "");  // password
        conn.setAutoCommit(true);
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        //conn.setPrepareThreshold(1);
    }

    public static String typeName(int type) {
        return types[type];
    }

    private static class Shutdown implements Runnable {
        public void run() {
            try {
                conn.close();
            } catch (Throwable t) {
                Log.ERROR("closing conn: " + Log.stackTrace(t));
            }
        }
    }

    // perf table stmts
    private PreparedStatement inPerf = null;
    public void logPerf(int type, long time, int cases) throws Throwable {
        getConn();
        try {

            if (inPerf == null) {
                this.inPerf = conn.prepareStatement(
                                   "INSERT INTO " + PERF_MONITOR_TABLE + 
                                   " (type, time, cases) " +
                                   "VALUES" +
                                      " (?,?,?)");
            }
            inPerf.setInt(1, type);
            inPerf.setLong(2, time);
            inPerf.setInt(3, cases);
            inPerf.executeUpdate();
        } catch (Exception pe) {
            Log.ERROR("logPerf: " + pe);
            conn = null;
        }
    }

    private PreparedStatement inPerfBW = null;
    public void logPerfBW(int type, long time, long bytes) throws Throwable {
        if (inPerfBW == null) {
            inPerfBW = conn.prepareStatement(
                                      "INSERT INTO " + PERF_MONITOR_TABLE + 
                                      " (type, time, bytes) " +
                                      "VALUES" +
                                      " (?,?,?)");
            if (inPerfBW == null)
                throw new HoneycombTestException("null prepstmt");
        }
        inPerfBW.setInt(1, type);
        inPerfBW.setLong(2, time);
        inPerfBW.setLong(3, bytes);
        inPerfBW.executeUpdate();
    }

    private PreparedStatement getPerf = null;
    public LinkedList getPerfRecords(Timestamp start_time, Timestamp end_time) 
                                                           throws Throwable {
        if (getPerf == null) {
            getPerf = conn.prepareStatement(
                                   "SELECT * FROM " + PERF_MONITOR_TABLE +
                                   " WHERE meas_time >= ? AND meas_time <= ?" +
                                   " ORDER BY meas_time ASC");
            if (getPerf == null)
                throw new HoneycombTestException("null prepstmt");
        }
        getPerf.setTimestamp(1, start_time);
        getPerf.setTimestamp(2, end_time);
        if (verbose)
            System.out.println("query: " + getPerf);
        ResultSet rs = getPerf.executeQuery();
        if (rs == null)
            throw new HoneycombTestException("null rs");

        LinkedList ret = new LinkedList();
        while (rs.next())
            ret.add(new PerfRecord(rs));

if (ret.size() == 0) {
System.out.println("########### nothing.. adding an hour to end time");
return getPerfRecords(start_time, 
new Timestamp(end_time.getTime() + 60 * 60000));
}

        return ret;
    }

    public class PerfRecord {
        public long meas_time;
        public int type;
        public long time;
        public int cases;
        public long bytes;

        private PerfRecord(ResultSet rs) throws Throwable {
            Timestamp ts =  rs.getTimestamp("meas_time");
            if (verbose)
                System.out.println("rec " + rs.getTimestamp("meas_time"));
            meas_time = ts.getTime();
            type = rs.getInt("type");
            time = rs.getLong("time");
            cases = rs.getInt("cases");
            bytes = rs.getLong("bytes");
        }
    }

    // alerts table; inserts are codes in AlertLogger
    private PreparedStatement getAlerts = null;
    public LinkedList getAlertRecords(long start_time, long end_time) 
                                                           throws Throwable {
        if (getAlerts == null) {
            getAlerts = conn.prepareStatement(
                                     "SELECT * FROM " + ALERT_MONITOR_TABLE +
                                     "WHERE time >= ? AND time <= ?");
            if (getAlerts == null)
                throw new HoneycombTestException("null prepstmt");
        }
        getAlerts.setTimestamp(1, new Timestamp(start_time));
        getAlerts.setTimestamp(1, new Timestamp(end_time));

        ResultSet rs = getPerf.executeQuery();
        if (rs == null)
            throw new HoneycombTestException("null rs");

        LinkedList ret = new LinkedList();
        while (rs.next())
            ret.add(new AlertRecord(rs));
        return ret;
    }

    public class AlertRecord {
        public Timestamp time;
        public int node;
        public String name;
        public String value;

        private AlertRecord(ResultSet rs) throws Throwable {
            time = rs.getTimestamp("time");
            node = rs.getInt("node");
            name = rs.getString("name");
            value = rs.getString("value");
        }
    }

    private PreparedStatement getOps = null;
    public LinkedList getOpRecords(long run_id) throws Throwable {
        if (getOps == null) {
            getOps = conn.prepareStatement(
                                     "SELECT * FROM " + AuditDBClient.OP_TABLE +
                                     " WHERE run_id = ?");
            if (getOps == null)
                throw new HoneycombTestException("null prepstmt");
        }
        getOps.setLong(1, run_id);
        if (verbose)
            System.out.println("query: " + getOps);
        ResultSet rs = getOps.executeQuery();
        if (rs == null)
            throw new HoneycombTestException("null rs");

        LinkedList ret = new LinkedList();
        while (rs.next())
            ret.add(new OpRecord(rs));
        return ret;
    }

    public class OpRecord {
        int op_type;
        long duration;
        boolean has_metadata;
        long num_bytes;

        private OpRecord(ResultSet rs) throws Throwable {
            op_type = rs.getInt("op_type");
            duration = rs.getLong("duration");
            has_metadata = rs.getBoolean("has_metadata");
            num_bytes = rs.getLong("num_bytes");
        }
    }

    public Timestamp getRunStart(long run_id) throws Throwable {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
                       "SELECT MIN(start_time) FROM op WHERE run_id = " +
                        run_id);
        if (rs == null)
            throw new HoneycombTestException("null rs");
        Timestamp ts = null;
        while (rs.next()) {
            ts = rs.getTimestamp(1);
            if (verbose)
                System.out.println("start time: " + ts);
        }
        stmt.close();
        if (ts == null) {
            throw new HoneycombTestException("no start time found in op tbl");
        }
        return new Timestamp(ts.getTime() - 60000);
    }
    public Timestamp getRunEnd(long run_id) throws Throwable {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
                       "SELECT MAX(start_time) FROM op WHERE run_id = " +
                        run_id);
        if (rs == null)
            throw new HoneycombTestException("null rs");
        Timestamp ts = null;
        while (rs.next()) {
            ts = rs.getTimestamp(1);
            if (verbose)
                System.out.println("end time: " + ts);
        }
        stmt.close();
        if (ts == null)
            throw new HoneycombTestException("getRunEnd: nothing in op tbl");
        return new Timestamp(ts.getTime() + 60000);
    }
}
