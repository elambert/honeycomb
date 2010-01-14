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



package com.sun.honeycomb.hadb;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;

import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.StringUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.ConnectionEvent;
import javax.sql.PooledConnection;

import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HADBJdbc extends Thread {
    private static final Logger LOG =
        Logger.getLogger(HADBJdbc.class.getName());

    private static HADBJdbc instance = null;

    public static final String MD_HADB_JDBC_URL_PROPERTY = "md.hadb.jdbc.url";

    public static final String DB_USER = "system";
    public static final String DB_PWD = "superduper";
    private static final String JDBC_URL_PREFIX = "jdbc:sun:hadb:";
    
    private static final long LONG_SLEEP_TIME = 30*60*1000; // 30 minutes
    private static final long SHORT_SLEEP_TIME = 60*1000; // 1 minute

    /**
     * The normal factory method. Using this will result in an
     * instance that gets the JDBC URL from the MasterService proxy,
     * and spawns a thread that periodically gets the URL and sees if
     * it changed. */
    public static synchronized HADBJdbc getInstance() {
        if (instance == null) {
            instance = new HADBJdbc();
        }
        return (instance);
    }

    /**
     * This "create instance" sets up a special "this JVM only"
     * version where the URL is not published generally. If
     * instantiated here, the poll thread is not started. */
    public static synchronized HADBJdbc getInstanceWithUrl(String url) {
        if (instance != null) {
            if (instance.jdbcURL == null) {
                LOG.warning("Overwriting instance with null URL");
            }
            else if (instance.jdbcURL.equals(url)) {
                LOG.warning("Asked to overwrite with same URL: no-op");
                return instance;
            }
            else {
                LOG.warning("Overwriting old instance with URL " +
                            instance.jdbcURL);
            }

            instance.reset();
        }

        LOG.info("Creating instance with URL " + url);
        instance = new HADBJdbc(url);
        return instance;
    }

    /** Clear and reset any pre-existing instance */
    static synchronized void resetInstance() {
        if (instance == null)
            return;

        // XXX Do we need to close the connections in the pool?

        // Make sure that if someone still has a reference to the
        // instance they won't be able to use it.
        instance.reset();

        instance = null;
    }

    /**********************************************************************
     *
     * Class implementation
     *
     **********************************************************************/

    private String jdbcURL;
    private ConnectionPool connectionPool;
    private boolean initialized;
    private boolean keepRunning;
    
    private HADBJdbc() {
        reset();
    }

    private HADBJdbc(String url) {
        reset();
        checkURL(url);
        jdbcURL = addAuthInfo(url);
        initialized = true;     // This implies the thread won't be started
    }

    /** Kill the thread if it's running, and null out all parameters */
    private synchronized void reset() {
        if (isAlive())
            shutdown();

        jdbcURL = null;
        connectionPool = null;
        initialized = false;
    }

    /** Tell the URL checker thread to stop, and wait for it */
    private synchronized void shutdown() {
        keepRunning = false;
        interrupt();
        try {
            join();
        } catch (InterruptedException ignored) {}
    }

    /** Basic sanity check for a JDBC URL */
    private void checkURL(String url) {
        if (url != null && !url.startsWith(JDBC_URL_PREFIX))
            throw new InternalException("Bad JDBC URL \"" + url + "\"!");
    }

    /** Add authentication information to URL and return it. */
    private String addAuthInfo(String url) {
        if (url == null) {
            return null;
        }
        
        StringBuffer sb = new StringBuffer(JDBC_URL_PREFIX);
        sb.append(DB_USER);
        sb.append("+");
        sb.append(DB_PWD);
        sb.append("@");
        sb.append(url.substring(JDBC_URL_PREFIX.length()));

        return sb.toString();
    }


    /** Initialize, and if there's a valid URL, start the thread. */
    private synchronized void init() throws NoConnectionException {
        if (initialized) {
            return;
        }

        // The old URL is invalid, even if we fail to set a good URL here.
        jdbcURL = null;

        // For testing, the JDBC URL is explicitly set. See main() in
        // AttributeTable and HADBHook.
        String staticURL = System.getProperty(MD_HADB_JDBC_URL_PROPERTY);
        if (staticURL != null) {
            StringBuffer sb = new StringBuffer();
            sb.append("Overriding JDBC URL: ");
            sb.append(MD_HADB_JDBC_URL_PROPERTY);
            sb.append("=\"").append(staticURL).append("\"");
            LOG.warning(sb.toString());

            startUsingURL(staticURL);
            initialized = true;
            return;
        }

        // Normal path. Get the URL from the MasterService proxy
        // and start using it if it passes basic sanity checks.

        HADBMasterInterface.Proxy proxy = MasterService.getMasterProxy();
        if (proxy == null)
            throw new NoConnectionException("Couldn't get MasterService proxy!");

        startUsingURL(proxy.getJdbcURL());

        LOG.info("Starting URL watcher thread; JDBC URL is " + jdbcURL);

        setDaemon(true);
        keepRunning = true;
        initialized = true;

        start();
    }

    /**
     * Perform sanity checks on the URL and start using it. Also
     * creates a connection pool to use with the URL.
     */
    private void startUsingURL(String newJdbcURL)
            throws NoConnectionException {
        if (newJdbcURL == null)
            throw new NoConnectionException("The JDBC URL is not ready yet.");

        checkURL(newJdbcURL);
        jdbcURL = newJdbcURL;

        if (jdbcURL != null) {
            String servers = jdbcURL.substring(JDBC_URL_PREFIX.length());
            connectionPool = new ConnectionPool(servers);
        }

    }


    /**********************************************************************
     *
     * Public methods
     *
     **********************************************************************/

    public void run() {
        // General strategy: if we get errors, just loop again, and we
        // hope they'll be fixed in a few minutes.

        // We should already have a good URL when the thread is
        // started; so start off with the happy case.
        try {
            Thread.sleep(LONG_SLEEP_TIME);
        } catch (InterruptedException ignored) {}

        while (keepRunning) {
            
            HADBMasterInterface.Proxy proxy = MasterService.getMasterProxy();
            if (proxy == null) {
                // Wait a short while and try again
                try {
                    Thread.sleep(SHORT_SLEEP_TIME);
                } catch (InterruptedException e) {}
                continue;
            }

            String newUrl = proxy.getJdbcURL();
            if (newUrl != null && !newUrl.equals(jdbcURL)) {
                jdbcURL = newUrl;

                String servers = jdbcURL.substring(JDBC_URL_PREFIX.length());
                connectionPool.setServerList(servers);

                LOG.info("Found a new JDBC URL: \"" + jdbcURL + "\"");
            }

            // Things look normal; sleep for a while.
            try {
                Thread.sleep(LONG_SLEEP_TIME);
            } catch (InterruptedException e) {}
        } // while

        LOG.info("URL watcher thread is exiting.");
    }

    public String getJdbcURL() {
        if (!initialized) {
            try {
                init();
            } catch (NoConnectionException e) {
                LOG.log(Level.WARNING, "Failed to get the JDBC URL", e);
            }
        }
        return (jdbcURL);
    }
    
    public Connection getConnection()
        throws SQLException, NoConnectionException {
        
        if (!initialized) {
            init();
        }

        RetryableCode.RetryableCall call =
            new RetryableCode.RetryableGetConnection(connectionPool, jdbcURL);

        return ((Connection)RetryableCode.retryCall(call));
    }

    public void freeConnection(Connection connection) {
   
        if (connectionPool != null) {
            connectionPool.freeConnection(connection);
        } else {
            try {
                connection.close();
            } catch (SQLException sqle) {
                LOG.warning("failed to close connection " + sqle);
            }
        }
    }

    // Check to see if the hadb state machine is in the "running" state
    public boolean isRunning() {
        HADBMasterInterface.Proxy proxy = MasterService.getMasterProxy();

        if (proxy == null)
            return false;

        return proxy.isRunning();
    }

}
