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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;

public class ConnectionWrapper implements Connection{

    private ConnectionPool pool;
    private Connection conn;
    private boolean inuse;
    private long timestamp;

    public ConnectionWrapper(Connection conn, ConnectionPool pool) {
        this.conn=conn;
        this.pool=pool;
        this.inuse=false;
        this.timestamp=0;
    }

    public synchronized boolean lease() {
       if(inuse)  {
           return false;
       } else {
          inuse=true;
          timestamp=System.currentTimeMillis();
          return true;
       }
    }
    public boolean validate() {
	try {
            conn.getMetaData();
        }catch (Exception e) {
	    return false;
	}
	return true;
    }

    public boolean inUse() {
        return inuse;
    }

    public long getLastUse() {
        return timestamp;
    }

    public void close() throws SQLException {
        pool.returnConnection(this);
    }

    protected void expireLease() {
        inuse=false;
    }

    protected Connection getConnection() {
        return conn;
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return conn.prepareCall(sql);
    }

    public Statement createStatement() throws SQLException {
        return conn.createStatement();
    }

    public String nativeSQL(String sql) throws SQLException {
        return conn.nativeSQL(sql);
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        conn.setAutoCommit(autoCommit);
    }

    public boolean getAutoCommit() throws SQLException {
        return conn.getAutoCommit();
    }

    public void commit() throws SQLException {
        conn.commit();
    }

    public void rollback() throws SQLException {
        conn.rollback();
    }

    public boolean isClosed() throws SQLException {
        return conn.isClosed();
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        return conn.getMetaData();
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
        conn.setReadOnly(readOnly);
    }
  
    public boolean isReadOnly() throws SQLException {
        return conn.isReadOnly();
    }

    public void setCatalog(String catalog) throws SQLException {
        conn.setCatalog(catalog);
    }

    public String getCatalog() throws SQLException {
        return conn.getCatalog();
    }

    public void setTransactionIsolation(int level) throws SQLException {
        conn.setTransactionIsolation(level);
    }

    public int getTransactionIsolation() throws SQLException {
        return conn.getTransactionIsolation();
    }

    public SQLWarning getWarnings() throws SQLException {
        return conn.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        conn.clearWarnings();
    }

	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		return conn.createStatement(resultSetType,resultSetConcurrency);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		return conn.prepareStatement(sql,resultSetType, resultSetConcurrency);
	}

	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		return conn.prepareCall(sql,resultSetType,resultSetConcurrency);
	}

	public Map getTypeMap() throws SQLException {
		return conn.getTypeMap();
	}

	public void setTypeMap(Map arg0) throws SQLException {
		conn.setTypeMap(arg0);
	}

	public void setHoldability(int holdability) throws SQLException {
		conn.setHoldability(holdability);
	}

	public int getHoldability() throws SQLException {
		return conn.getHoldability();
	}

	public Savepoint setSavepoint() throws SQLException {
		return conn.setSavepoint();
	}

	public Savepoint setSavepoint(String name) throws SQLException {
		return conn.setSavepoint(name);
	}

	public void rollback(Savepoint savepoint) throws SQLException {
		conn.rollback(savepoint);
	}

	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		conn.releaseSavepoint(savepoint);
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return conn.createStatement(resultSetType,resultSetConcurrency,resultSetHoldability);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return conn.prepareStatement(sql,resultSetType,resultSetConcurrency,resultSetHoldability);
	}

	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return conn.prepareCall(sql,resultSetType,resultSetConcurrency,resultSetHoldability);
	}

	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		return conn.prepareStatement(sql,autoGeneratedKeys);
	}

	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		return conn.prepareStatement(sql,columnIndexes);
	}

	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		return conn.prepareStatement(sql, columnNames);
	}
	
	/**
	 * Prepared statement caching methods.
	 */
	private PreparedStatement inOp = null;
	public PreparedStatement getInOp()
        throws Throwable
    {
        if (inOp == null) {
            inOp = 
                conn.prepareStatement(
                                      "INSERT INTO " + AuditDBClient.OP_TABLE + 
                                      " (" +
                                      " id," +
                                      " op_type," +
                                      " status," +
                                      " start_time," +
                                      " end_time," +
                                      " duration, " +
                                      " run_id," +
                                      " info," +
                                      " api_oid," +
                                      " api_link_oid," +
                                      " sha1," +
                                      " has_metadata," +
                                      " num_bytes," +
                                      " rr_offset," +
                                      " rr_length," +
                                      " sr_oid," +
                                      " sr_link_oid," +
                                      " sr_size," +
                                      " sr_ctime," +
                                      " sr_dtime," +
                                      " sr_digest," +
                                      " log_tag" +
                                      " ) " +
                                      "VALUES" +
                                      " (" +
                                      " NEXTVAL('" + AuditDBClient.OPID_SEQ + "')," +
                                      " ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?" +
                                      " )"
                                      );
        }
        return inOp;
    }

    private PreparedStatement selCurrOpId = null;
    public PreparedStatement getSelCurrOpId()
        throws Throwable
    {	
        if (selCurrOpId == null) {
            selCurrOpId = 
                conn.prepareStatement("SELECT CURRVAL('" + AuditDBClient.OPID_SEQ + "')");
        }
        return selCurrOpId;
    }

    private PreparedStatement inOpMdString = null;
    public PreparedStatement getInOpMdString()
        throws Throwable
    {
        if (inOpMdString == null) {
            inOpMdString = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.OP_MD_STRING_TABLE +
                                      " (op, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return inOpMdString;
    }

    private PreparedStatement inOpMdLong = null;
    public PreparedStatement getInOpMdLong()
        throws Throwable
    {
        if (inOpMdLong == null) {
            inOpMdLong = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.OP_MD_LONG_TABLE +
                                      " (op, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return inOpMdLong;
    }

    private PreparedStatement inOpMdDouble = null;
    public PreparedStatement getInOpMdDouble()
        throws Throwable
    {
        if (inOpMdDouble == null) {
            inOpMdDouble = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.OP_MD_DOUBLE_TABLE +
                                      " (op, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return inOpMdDouble;
    }

    private PreparedStatement inOpMdByte = null;
    public PreparedStatement getInOpMdByte()
        throws Throwable
    {
        if (inOpMdByte == null) {
            inOpMdByte = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.OP_MD_BYTE_TABLE +
                                      " (op, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return inOpMdByte;
    }

    // obj table stmts
    private PreparedStatement inObj = null;
    public PreparedStatement getInObj()
        throws Throwable
    {
        if (this.inObj == null) {
            this.inObj = 
                conn.prepareStatement(
                                      "INSERT INTO " + AuditDBClient.OBJ_TABLE + 
                                      " (" +
                                      " api_oid," +
                                      " sys_oid," +
                                      " sha1," +
                                      " has_metadata," +
                                      " size," +
                                      " api_link_oid," +
                                      " ctime," +
                                      " dtime," +
                                      " deleted" +
                                      " ) " +
                                      "VALUES" +
                                      " (" +
                                      " ?,?,?,?,?,?,?,?,?" +
                                      " )"
                                      );
        }
        return this.inObj;
    }

    private PreparedStatement upObjDeleted = null;
    public PreparedStatement getUpObjDeleted()
        throws Throwable
    {
        if (this.upObjDeleted == null) {
            this.upObjDeleted =
                conn.prepareStatement(
                                      "UPDATE " + AuditDBClient.OBJ_TABLE + 
                                      " SET deleted=? WHERE" +
                                      " api_oid=?"
                                      );
        }
        return this.upObjDeleted;
    }
    
    private PreparedStatement upObjUnknownDeleted = null;
    public PreparedStatement getUpObjUnknownDeleted()
        throws Throwable
    {
        if (this.upObjUnknownDeleted == null) {
            this.upObjUnknownDeleted =
                conn.prepareStatement(
                                      "UPDATE " + AuditDBClient.OBJ_TABLE + 
                                      " SET deleted=null WHERE" +
                                      " api_oid=?"
                                      );
        }
        return this.upObjUnknownDeleted;
    }

    private PreparedStatement selObjSha = null;
    public PreparedStatement getSelObjSha()
        throws Throwable
    {
        if (this.selObjSha == null) {
            this.selObjSha = 
                conn.prepareStatement(
                                      "SELECT SHA1 FROM " + AuditDBClient.OBJ_TABLE + 
                                      " WHERE api_oid=?"
                                      );
        }
        return this.selObjSha;
    }

    private PreparedStatement inMdString = null;
    public PreparedStatement getInMdString()
        throws Throwable
    {
        if (this.inMdString == null) {
            this.inMdString = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.MD_STRING_TABLE +
                                      " (api_oid, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return this.inMdString;
    }

    private PreparedStatement inMdLong = null;
    public PreparedStatement getInMdLong()
        throws Throwable
    {
        if (this.inMdLong == null) {
            this.inMdLong = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.MD_LONG_TABLE +
                                      " (api_oid, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return this.inMdLong;
    }

    private PreparedStatement inMdDouble = null;
    public PreparedStatement getInMdDouble()
        throws Throwable
    {
        if (this.inMdDouble == null) {
            this.inMdDouble = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.MD_DOUBLE_TABLE +
                                      " (api_oid, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return this.inMdDouble;
    }

    private PreparedStatement inMdByte = null;
    public PreparedStatement getInMdByte()
        throws Throwable
    {
        if (this.inMdByte == null) {
            this.inMdByte = 
                conn.prepareStatement(
                                      "INSERT INTO " + 
                                      AuditDBClient.MD_BYTE_TABLE +
                                      " (api_oid, name, value)" +
                                      " VALUES (?,?,?)"
                                      );
        }
        return this.inMdByte;
    }
    
    private PreparedStatement upObjLocked = null;
    public PreparedStatement getUpObjLocked()
        throws SQLException
    {
        if (this.upObjLocked == null) {
            this.upObjLocked =
                conn.prepareStatement(
                                      "UPDATE " + AuditDBClient.OBJ_TABLE + 
                                      " SET lock=? WHERE" +
                                      " api_oid=?"
                                      );
        }
        return this.upObjLocked;
    }
    
    private PreparedStatement upObjUnlock = null;
    public PreparedStatement getUpObjUnLocked() throws SQLException
    {
        if (this.upObjUnlock == null) {
            this.upObjUnlock =
                conn.prepareStatement(
                                      "UPDATE " + AuditDBClient.OBJ_TABLE + 
                                      " SET lock=null WHERE" +
                                      " api_oid=?"
                                      );
        }
        return this.upObjUnlock;
    }
}
