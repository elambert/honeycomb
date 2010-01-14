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

import java.util.LinkedList;
import javax.sql.ConnectionEventListener;
import java.util.logging.Logger;
import java.sql.Connection;
import javax.sql.PooledConnection;
import javax.sql.ConnectionEvent;

import java.util.NoSuchElementException;
import java.sql.SQLException;
import java.util.logging.Level;

import com.sun.hadb.jdbc.pool.HadbConnectionPoolDataSource;
import com.sun.honeycomb.config.ClusterProperties;

public class ConnectionPool 
    implements ConnectionEventListener {

    private static final Logger LOG = Logger.getLogger(ConnectionPool.class.getName());

    // The Governor: limit is set in the cluster config
    private static final String GOVERNOR_PROP_NAME =
        "honeycomb.hadbhook.governor.max_concurrent";
    private static final int DEFAULT_MAX_CONCURRENT_RQSTS = 6;
    private static int maxConcurrentRqsts;
    
    private static ClusterProperties config = null;

    private LinkedList pool;
    private volatile int totalCreated;    
    private volatile int nbCheckedOut;
    private HadbConnectionPoolDataSource poolDataSource;
    private static ThreadLocal thrConnection;
    private boolean urlJustChanged = false;

    ConnectionPool(String serverList) {
        initConfig();           // initialize the config

        thrConnection = new ThreadLocal();
        pool = new LinkedList();
        poolDataSource = new HadbConnectionPoolDataSource();
        poolDataSource.setServerList(serverList);
        poolDataSource.setUser(HADBJdbc.DB_USER);
        poolDataSource.setPassword(HADBJdbc.DB_PWD);
        nbCheckedOut = 0;
        totalCreated = 0;
    }

    /**
     * Called from HADBJdbc.java when the jdbcurl changes
     * NOTE: This functions blocks for 60 seconds while outstanding
     * connections are tossed.  This is ok, because this function is ONLY
     * called from a sperate thread from HADBJdbc, who's only purpose is to
     * check for url changes from the MasterService
     */
    public void setServerList(String serverList) {
        synchronized (pool) {
            poolDataSource.setServerList(serverList);

            //toss all connections on the pool
            boolean done = false;
            synchronized(pool) {
                while(!done) {
                    try {
                        PooledConnection pCnx =
                            (PooledConnection)pool.removeFirst();
                        try {
                            pCnx.removeConnectionEventListener(this);
                            pCnx.close();
                        } catch (SQLException ignored) {}
                    } catch (NoSuchElementException e) {
                        done = true;
                    }
                }
                //set flag to toss outstanding connections
                urlJustChanged = true;
            }

            //set timeout on that flag.
            try {
                Thread.sleep(60000);
            } catch (InterruptedException ignored) {}

            //I guess we don't _really_ need to toggle this bool under
            //the lock, but it feels right to me [blau 11/30/06]
            synchronized(pool) {
                urlJustChanged = false;
            }
        }
    }
    
    public Connection getConnection() 
        throws SQLException, NoConnectionException {

        //
        // deadlock avoidance -
        // if this thread already has a db connection -
        // return its initial connection incrementing its ref count.
        //
        Object obj = thrConnection.get();
        if (obj != null) {
           LOG.warning("thread has already a db connection " + Thread.currentThread());
           Connection c;
           if (obj instanceof Connection) {
               c = (Connection) obj;
               thrConnection.set(new ThrDbConnection(c));
           } else {
               assert(obj instanceof ThrDbConnection);
               ThrDbConnection dbc = (ThrDbConnection) obj;
               dbc.refCount++;
               c = dbc.connection;
           }
           return c;
        }

        PooledConnection pCnx = null;
        synchronized (pool) {
           while (nbCheckedOut > maxConcurrentRqsts) {
               // limit the # of outstanding db operations
               try {
                   pool.wait();
               } catch (InterruptedException ie) {
                   LOG.warning("thread interrupted waiting for connection");
                   throw new NoConnectionException(ie.getMessage());
               }
            } 
            try {
                pCnx = (PooledConnection)pool.removeFirst();
            } catch (NoSuchElementException e) {
                pCnx = null;
            }
        }
        
        if (pCnx == null) {
            // Create a new PooledConnection
            pCnx = poolDataSource.getPooledConnection();
            pCnx.addConnectionEventListener(this);
            totalCreated++;
            LOG.info("Created a new pooled connection to the HADB database [total created so far "+
                     totalCreated+"]");

        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("A connection has been checked out [nb checked out "+nbCheckedOut+"]");
        }

        Connection c = pCnx.getConnection();
        if (c == null) {
            throw new NoConnectionException("Pooled Connection returned a " +
                       "null connection");
        }
        try {
            c.setAutoCommit(true);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            thrConnection.set(c);
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, 
                    "Trying to modify the checked out connection", sqe);
            try {
               c.close();
            } catch (SQLException e) {
                LOG.warning("Exception trying to close the connection: " + e);
            }
            throw sqe;
        }
         synchronized (pool) {
            nbCheckedOut++;
        }
        return c;
    }

    public void freeConnection(Connection connection) {

        Object obj = thrConnection.get();
        if (obj instanceof ThrDbConnection) {
            //
            // This thread opened the database multiple times -
            //
            ThrDbConnection dbc = (ThrDbConnection) obj;
            assert(dbc.refCount >= 0);
            dbc.refCount--;
            if (dbc.refCount > 0) {
                return;
            }
        }
        thrConnection.set(null);

        //
        // try to close the shadow connection 
        // The connection is discarded is this fails.
        //
        try {
             connection.close();
        } catch (SQLException sqle) {
             LOG.warning("failed to close shadow connection " + sqle);
             synchronized (pool) {
               nbCheckedOut--;
             }
        }
    }
 
    private void checkin(PooledConnection pCnx) {

        int curCheckedOut;
        synchronized (pool) {
            if(!urlJustChanged) {
                //Only check it into the pool if we're not discarding
                //connections right now.
                pool.add(pCnx);
            } else {
                try {
                    pCnx.getConnection().close();
                } catch (SQLException ignored) {}
            }
            nbCheckedOut--;
            curCheckedOut = nbCheckedOut;
            pool.notify();
        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("A connection has been checked in [nb checked out "+curCheckedOut+"]");
        }
    }
        
    public void connectionClosed(ConnectionEvent event) {
        PooledConnection pCnx = (PooledConnection)event.getSource();
        checkin(pCnx);
    }
    
    public void connectionErrorOccurred(ConnectionEvent event) {
        PooledConnection pCnx = (PooledConnection)event.getSource();
        checkin(pCnx);
    }

    /*
     * Inner class to keep track of number of db connections per thread.
     */
    private class ThrDbConnection {
        Connection connection;
        int refCount;

        ThrDbConnection(Connection c) {
            refCount = 2;
            connection = c;
        }
    }


    /** Initialize the cluster config */
    private static synchronized void initConfig() {
        if (config != null)
            return;

        config = ClusterProperties.getInstance();

        maxConcurrentRqsts = getIntProperty(GOVERNOR_PROP_NAME,
                                            DEFAULT_MAX_CONCURRENT_RQSTS);
        LOG.info("Governor: max. " + maxConcurrentRqsts +
                 " simultaneous requests");
    }

    // Cluster config helpers
    private static String getProperty(String pname) {
        return getProperty(pname, null);
    }
    private static int getIntProperty(String pname, int defaultVal) {
        String s = null;
        try {
            if ((s = getProperty(pname)) == null)
                return defaultVal;

            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            LOG.warning("Couldn't parse " + pname + " = \"" + s +
                        "\"; using default " + defaultVal);

        }

        return defaultVal;
    }
    private static String getProperty(String pname, String defaultVal) {
        try {
            String s = config.getProperty(pname);
            if (s != null) return s;
        }
        catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't get property \"" + pname + "\"");
        }

        return defaultVal;
    }

}
