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

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.ParameterMetaData;

import java.util.ArrayList;
import java.util.List;

import java.util.logging.Logger;

import com.sun.hadb.jdbc.DbException;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExternalObjectIdentifier;

public class RetryableCode {

    private static final int MAX_RETRIES = 5;
    private static final int RETRY_SLEEP = 1000; // in ms.

    private static final Logger LOG = Logger.getLogger(RetryableCode.class.getName());

    /**********************************************************************
     *
     * Retryable exceptions
     *
     **********************************************************************/

    private static final int[] retryableList = {
        // Removed 20005 - "type mismatch"
        // need to review the rest of these error numbers.
        208, 216, 224, 1552, 2080, 2097, 2168, 2320, 3104, 3504, 4192, 4576, 4624,
        25018, 25017, 25012, 25005, 2304, 20001, 20004, 1680, 2078, 1986
    };

    /**********************************************************************
     *
     * RetryableCall interface
     *
     **********************************************************************/

    public static interface RetryableCall {
        Object call() throws SQLException;
    }

    /**********************************************************************
     *
     * Code helpers
     *
     **********************************************************************/

    static boolean isRetryable(SQLException e) {
        int errorCode = e.getErrorCode();
        boolean result = false;
        for (int i=0; (i<retryableList.length) && (!result); i++) {
            if (retryableList[i] == errorCode) {
                result = true;
            }
        }
        return(result);
    }

    public static ResultSet retryExecuteQuery(Statement statement,
                                              String query)
        throws SQLException {
        RetryableQuery call = new RetryableQuery(statement, query, true);
        return( (ResultSet)retryCall(call) );
    }

    public static void retryExecute(Statement statement,
                                    String query)
        throws SQLException {
        RetryableQuery call = new RetryableQuery(statement, query, false);
        retryCall(call);
    }

    public static ResultSet retryExecutePreparedQuery(
                                                      PreparedStatement statement,
                                                      List literals)
        throws SQLException {
        RetryablePreparedQuery call = new RetryablePreparedQuery(statement, 
                                                                 literals, 
                                                                 true);
        return( (ResultSet)retryCall(call) );
    }

    public static void retryExecutePrepared(PreparedStatement statement,
                                            List literals)
        throws SQLException {
        RetryablePreparedQuery call = new RetryablePreparedQuery(statement, 
                                                                 literals, 
                                                                 false);
        retryCall(call);
    }

    public static Object retryCall(RetryableCall call)
        throws SQLException {
        Object result = null;
        int nbRetriesLeft = MAX_RETRIES;
        boolean done = false;
        long startTime = 0;

        while (!done) {
            --nbRetriesLeft;
            try {
                startTime = System.currentTimeMillis();
                result = call.call();
                done = true;
            } catch (SQLException e) {
                if ( (nbRetriesLeft > 0) && (isRetryable(e)) ) {
                    LOG.warning("Got a retryable exception. Message is ["+
                                e.getMessage()+"(error_code="+e.getErrorCode()+
                                ")]. Retrying ... ("+nbRetriesLeft+" left)");
                    
                    long stopTime = System.currentTimeMillis();
                    long timeToWait = (stopTime-startTime < RETRY_SLEEP)
                        ? RETRY_SLEEP - (stopTime-startTime)
                        : 0;
                    
                    try {
                        Thread.currentThread().sleep(timeToWait);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    throw e;
                }
            }
        }
        
        return(result);
    }

    /**********************************************************************
     *
     * Some RetryableCall implementations
     *
     **********************************************************************/

    private static class RetryableQuery 
        implements RetryableCall {

        private Statement statement;
        private String sql;
        private boolean isQuery;

        private RetryableQuery(Statement nStatement,
                               String nSql,
                               boolean nIsQuery) {
            statement = nStatement;
            sql = nSql;
            isQuery = nIsQuery;
        }

        public Object call() 
            throws SQLException {
            Object result = null;
            if (isQuery) {
                result = statement.executeQuery(sql);
            } else {
                statement.execute(sql);
            }
            return(result);
        }
    }

    private static class RetryablePreparedQuery 
        implements RetryableCall {
        
        private PreparedStatement statement;
        private List literals;
        private boolean isQuery;

        private RetryablePreparedQuery(PreparedStatement nStatement,
                                       List nLiterals,
                                       boolean nIsQuery) {
            statement = nStatement;
            literals = nLiterals;
            isQuery = nIsQuery;
        } // RetryableQuery

        public Object call() 
            throws SQLException {
            Object result = null;
            
            statement.clearParameters();
            if (literals != null) {
                for (int i = 0; i < literals.size(); i++) {
                    Object obj = literals.get(i);
                    if (obj == null) {
                        statement.setObject(i+1,null);
                    } else if (obj instanceof byte[]) {
                        statement.setBytes(i+1,(byte [])obj);
                    } else if (obj instanceof String) {
                        statement.setString(i+1,(String)obj);
                    } else if (obj instanceof Long) {
                        statement.setLong(i+1,((Long)obj).longValue());
                    } else if (obj instanceof Double) {
                        statement.setDouble(i+1,((Double)obj).doubleValue());
                    } else if (obj instanceof Date) {
                        statement.setDate(i+1,(Date)obj);
                    } else if (obj instanceof Time) {
                        statement.setTime(i+1,(Time)obj);
                    } else if (obj instanceof Timestamp) {
                        statement.setTimestamp(i+1,(Timestamp)obj);
                    } else if (obj instanceof ExternalObjectIdentifier) {
                        /* Convert objectid fields to internal form */
                        ExternalObjectIdentifier eoid = 
                            (ExternalObjectIdentifier) obj;
                        byte[] bytes;
                        if (eoid.toByteArray().length == 0) {
                            bytes = new byte[0];
                        } else {
                            NewObjectIdentifier oid =
                                NewObjectIdentifier.fromExternalObjectID(eoid);
                            bytes = oid.getDataBytes();
                        }
                        statement.setBytes(i+1,bytes);
                    } else {
                        throw new InternalException("object of unhandled type: "+
                                                    obj.getClass().getCanonicalName());
                    } // if / elseif / else
                } // for
            } // if (literals != null)
            
            if (isQuery) {
                result = statement.executeQuery();
            } else {
                statement.execute();
            }

            return(result);
        } // call
    } // RetryablePreparedQuery

    public static class RetryableGetConnection
        implements RetryableCall {

        ConnectionPool connectionPool;
        String jdbcURL;

        public RetryableGetConnection(ConnectionPool nConnectionPool,
                                       String nJdbcURL) {
            connectionPool = nConnectionPool;
            jdbcURL = nJdbcURL;
        }
        
        public Object call() 
            throws SQLException {

            Object result = null;
 
           if (connectionPool == null) {
                LOG.info("Creating a connection with url ["+jdbcURL+"]. No pool used");
                try {
                    Class.forName("com.sun.hadb.jdbc.Driver");
                } catch (ClassNotFoundException e) {
                    SQLException newe = new SQLException("Failed to load the hadb JDBC driver ["+
                                                         e.getMessage());
                    newe.initCause(e);
                    throw newe;
                }
                result = DriverManager.getConnection(jdbcURL);
            } else {
                try {
                    result = connectionPool.getConnection();
                } catch (NoConnectionException e) {
                    SQLException newe = new SQLException(e.getMessage());
                    newe.initCause(e);
                    throw newe;
                }
            }

           return(result);
        }
    }
}
