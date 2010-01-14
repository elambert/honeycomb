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




import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.ParameterMetaData;

import java.util.ArrayList;
import java.util.List;

import java.util.logging.Logger;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import com.sun.honeycomb.common.ByteArrays;

public class RetryableCode {

    private static final Logger LOG = Logger.getLogger(RetryableCode.class.getName());


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

    private static boolean isRetryable(SQLException e) {
        return false;
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

    /**
     * No actual retrying for the emulator, but having this class
     * and this method makes it easier to transfer code.
     */
    public static Object retryCall(RetryableCall call)
        throws SQLException {

        return call.call();
    }

    /**********************************************************************
     *
     * Some RetryableCall implementations
     *
     **********************************************************************/

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
            for (int i = 0; i < literals.size(); i++) {
                Object obj = literals.get(i);
                if (obj instanceof String) {
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
                } else if (obj instanceof byte[]) {
                    statement.setBytes(i+1,(byte [])obj);
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
                } else if (obj == null) {
                    //Must look up param's SQL type to call setNull
                    ParameterMetaData params =
                        statement.getParameterMetaData();
                    statement.setNull(i+1,params.getParameterType(i+1));
                } else {
                    throw new InternalException("object of unhandled type: "+
                                 obj.getClass().getCanonicalName());
                }
            }
            
            if (isQuery) {
                result = statement.executeQuery();
            } else {
                statement.execute();
            }
            return(result);
        } // call
    } // RetryablePreparedQuery

}
