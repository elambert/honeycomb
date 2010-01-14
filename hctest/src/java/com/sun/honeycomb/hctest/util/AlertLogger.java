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

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.alert.AlertApi;
import com.sun.honeycomb.alert.AlertApiImpl;
import com.sun.honeycomb.alert.AlertException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;

/**
    AlertLogger runs on the master node, fetching alerts repeatedly.
    When 'interesting' activity appears, the current alert status is
    logged to an external database and all alerts that change are
    also logged on the interval until a given interval after interesting
    activity ceases.
*/

public class AlertLogger implements ManagedService {
    
    private final static String ALERTS_SMTP_EMAIL = "honeycomb.alerts.smtp.to";
    private final static String HCTEST_ALERT_DB_PROXY = "10.123.45.1";

    private static final Logger LOG = Logger.getLogger(AlertLogger.class.getName());

    private HashMap hm = new HashMap();

    private static final String ALERT_MONITOR_TABLE = "alert_monitor";
    private static Connection conn = null;
    private PreparedStatement psInAlert = null;
    private PreparedStatement psPoll = null;

    private final int POLL_INTERVAL = 5000;
    private final int IDLE_INTERVAL = 5 * 60 * 1000;

    private boolean running = false;
    private Thread serverThread;

    private String dbHost = null;
    private String cluster = null;
    private String dbspec, url = null;


    public AlertLogger() {

        LOG.info("creating.. ");

        initDB();

        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownThread(),
                                                        "Shutdown"));
        serverThread = Thread.currentThread();
    }

    public void run() {

        try {
            if (url == null) {
                LOG.severe("missing db spec, not running");
                return;
            }
    
            LOG.info("starting.. " + Thread.currentThread().getName());
            running = true;
    
            // XXX HACK SpreaderService doesn't bring up vip when it should,
            // so sleep a bit
            LOG.info("hack sleep 3 min");
            sleep(3*60000);

            connectDB();

            //LOG.info("audit db client connected: " + dbspec);
    
            boolean logging = false;
            long lastTrigger = System.currentTimeMillis();
    
            while (running) {
    
                AlertApi.AlertObject property = null;
                AlertApi.AlertViewProperty properties = null;

                try {
                    AlertApi alertApi = AlertApiImpl.getAlertApi();
                    properties = alertApi.getViewProperty();
                    property = properties.getFirstAlertProperty();
                } catch (AlertException ae) {
                    LOG.severe("AlertLogger exists..." + ae);
                    running = false;
                    break;
                }
                boolean gotActivity = false;
    
                while (property != null) {
                    try {
                        String name = property.getPropertyName();
                        String value = property.getPropertyValue().toString();
                        String prev = (String) hm.get(name);
                        if (prev == null  ||  !prev.equals(value)) {
                            hm.put(name, value);
                            if (!gotActivity  &&  trigger(name))
                                gotActivity = true;
                            if (logging) {
                                logAlert(name, value);
                            }
                        }
    
                        property = properties.getNextAlertProperty();
    
                    } catch (AlertException e) {
                        property = null;
                    }
                }
                if (gotActivity) {
                    lastTrigger = System.currentTimeMillis();
                    if (!logging) {
                        LOG.info("got activity, starting db log..");
                        logging = true;
                        Set set = hm.entrySet();
                        Iterator it = set.iterator();
                        while (it.hasNext()) {
                            Map.Entry me = (Map.Entry) it.next();
                            String name = (String) me.getKey();
                            String value = (String) me.getValue();
                            logAlert(name, value);
                        }
                        LOG.info("logged base state");
                    }
                } else if (logging  &&  
                         System.currentTimeMillis() - lastTrigger > IDLE_INTERVAL) {
                    logging = false;
                    LOG.info("idled off, ending db log");
                }
                sleep(POLL_INTERVAL);
            }
            LOG.info("done");
        }
        catch (Throwable t) {
            LOG.log(Level.SEVERE, "unexcpected exception", t);
        }
    }

    /**
     *  ManagedService interface
     */
    public void shutdown() {
        LOG.info("got shutdown");
        running = false;
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    /**
     *  ManagedService interface
     */
    public ManagedService.ProxyObject getProxy() {
        return(new ManagedService.ProxyObject());
    }

    /**
     *  ManagedService interface
     */
    public void syncRun() {
    }

    private boolean trigger(String s) {
        if (s.indexOf("OAStat") != -1)
            return true;
        return false;
    }

    private void initDB() {
        // get cluster name from alert email addr
        ClusterProperties config = ClusterProperties.getInstance();
        String s = config.getProperty(ALERTS_SMTP_EMAIL);
        if (s == null) {
            LOG.severe("can't find property " + ALERTS_SMTP_EMAIL + 
                       " to determine db user");
            return;
        }
        int i = s.indexOf("@");
        if (i == -1  ||  i == 0) {
            LOG.severe("can't parse '<cluster>@' from " + ALERTS_SMTP_EMAIL + 
                       "[" + s + "] to determine db user");
            return;
        }
        cluster = s.substring(0, i);

        dbHost = HCTEST_ALERT_DB_PROXY;
        dbspec = dbHost + "/" + cluster;
        url = "jdbc:postgresql://" + dbspec;
                         // + "?prepareThreshold=1";

        //
        //  load driver - it registers w/ jdbc
        //
        try {
            Class.forName("org.postgresql.Driver");
            LOG.info("Class.forName ok");
        } catch (Throwable t) {
            LOG.severe("loading jdbc driver: " + t);
            //System.exit(1); be nice to HC..
        }

        LOG.info("db: " + url);
    }

    private void connectDB() {
        if (url == null) {
            LOG.severe("missing db config/setup");
            return;
        }
        LOG.info("connectDB running=" + running);
        while (running) {
            try {

                //
                //  get conn
                //
                //  set reuse threshold for faster PreparedStatement prep -
                //  assuming for now that all stmts will be reused a lot
                // http://jdbc.postgresql.org/documentation/head/server-prepare.html
                //
                LOG.info("connecting to: " + url);
                conn = DriverManager.getConnection(url,
                                                   cluster,   // =db user
                                                   "");  // password
                LOG.info("got conn");
                conn.setAutoCommit(true);
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                //conn.setPrepareThreshold(1);
                psInAlert = conn.prepareStatement(
                                      "INSERT INTO " + ALERT_MONITOR_TABLE +
                                      " (node, name, value) " +
                                      "VALUES" +
                                      " (?,?,?)");
                //if (psInAlert == null)
                    //throw new HoneycombTestException("null prepstmt");
                LOG.info("connected: " + url);
                break;
            } catch (Throwable t) {
                LOG.severe("[" + url + "]: " + t);
                sleep(POLL_INTERVAL);
            }
        }
    }

    public void logAlert(String name, String value) {
        try {

            int node = Integer.parseInt(name.substring(5, 8));
            name = name.substring(9);

            psInAlert.setInt(1, node);
            psInAlert.setString(2, name);
            psInAlert.setString(3, value);
            psInAlert.executeUpdate();
        } catch (Exception e) {
            LOG.severe("logAlert: " + e);
            // XXX add connectDB() when appropriate
            try {
                conn.close();
            } catch (Exception ee) {
                LOG.info("closing: " + ee);
            }
            connectDB();
        }
    }

    private void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch (Exception e) {}
    }

    private static class ShutdownThread implements Runnable {
        public void run() {
            if (conn == null)
                return;
            try {
                conn.close();
            } catch (Throwable t) {
                LOG.severe("closing conn: " + t);
            }
        }
    }
}
