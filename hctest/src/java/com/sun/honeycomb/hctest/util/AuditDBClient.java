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

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.common.ThreadPropertyContainer;

//import org.postgresql.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

//import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 ** Raw interface to the audit db. 
 **
 ** Postgres jdbc driver: http://jdbc.postgresql.org/
 **
 ** Each instance has its own Connection to the database.
 ** Post-instantiation, the connection is set with Auto Commit off and
 ** the transaction isolation level set to serializable.
 **
 ** This class is not thread-safe.
 **
 **/
public class AuditDBClient {

    ////////////////////////////////////////////////////////////////
    // db spec

    // op tables
    public static final String OP_TABLE = "op";
    public static final String OP_TYPE_TABLE = "op_type";
    public static final String OPID_SEQ = "op_id_seq";
    public static final String OP_MD_LONG_TABLE = "op_md_long";
    public static final String OP_MD_STRING_TABLE = "op_md_string";
    public static final String OP_MD_DOUBLE_TABLE = "op_md_double";
    public static final String OP_MD_BYTE_TABLE = "op_md_byte";

    // hcop.op_type values
    public static final int OP_STORE = 1;
    public static final int OP_LINK = 2;
    public static final int OP_RETRIEVE = 3;
    public static final int OP_RETRIEVE_RANGE = 4;
    public static final int OP_RETRIEVE_MD = 5;
    public static final int OP_DELETE = 6;
    public static final int OP_QUERY = 7;

    // obj tables - state of cluster
    public static final String OBJ_TABLE = "obj";
    public static final String MD_LONG_TABLE = "md_long";
    public static final String MD_STRING_TABLE = "md_string";
    public static final String MD_DOUBLE_TABLE = "md_double";
    public static final String MD_BYTE_TABLE = "md_byte";

    private String dbhost = null;
    private String cluster = null;
    
    public AuditDBClient(String dbhost, String cluster) 
        throws Throwable
    {
    	this.dbhost = dbhost;
    	this.cluster = cluster;
    	
    	// Now we have the pool setup for this dbhost/cluster :)
    	ConnectionPool.getInstance(this.dbhost, this.cluster);
    }
    
    //
    // <DB ACCESS>
    //

    /**
     ** @return the connection to the audit db.
     * @throws SQLException 
     * @throws HoneycombTestException 
     **/
    public ConnectionWrapper getConnection() throws SQLException, HoneycombTestException
    {
        return (ConnectionWrapper)ConnectionPool.getInstance(dbhost,cluster).getConnection();
    }
    
    public void returnConnection(ConnectionWrapper conn) throws HoneycombTestException{
    	ConnectionPool.getInstance(dbhost,cluster).returnConnection(conn);
    }

    //
    // </DB ACCESS>
    //

    //
    // <OP TABLE MANIPULATIONS>
    //

    /**
     ** @param op_type operation type; ie OP_STORE, OP_RETRIEVE, etc. (not null)
     ** @param status integer status code of operation (null ok)
     ** @param start_time start of op. (not null)
     ** @param end_time end of op. (null ok)
     ** @param run_id QB run id. (null ok)
     ** @param info additional text relevant to the op (null ok)
     ** @param oid the oid of the object as known by the test. (null ok)
     ** @param link_oid the linkOID of the object as known by the test. (null ok)
     ** @param sha1 sha1 digest of the file associated with this operation (null ok)
     ** @param has_metadata true if metadata is related to the operation. (not null)
     ** @param num_bytes num bytes associated with this operation. (null ok)
     ** @param offset num bytes offset for range retrieve (null ok)
     ** @param length num bytes to read for range retrieve (null ok)
     ** @param sr_oid the oid of the object as returned by a system record
     ** @param sr_link_oid the link oid of the object as returned by a system record
     ** @param sr_size size of file as reported by returned a system record (null ok)
     ** @param sr_ctime create time as reported by returned a system record (null ok)
     ** @param sr_dtime delete time as reported by returned a system record (null ok)
     ** @param sr_digest sha1 digest as reported by returned a system record (null ok)
     **
     **/
    

    protected void insertOp(int op_type, 
                            int status, 
                            long start_time, 
                            Long end_time,
                            long api_time,
                            Long run_id,
                            String info,
                            String oid,
                            String link_oid,
                            String sha1, 
                            boolean has_metadata, 
                            Long num_bytes, 
                            Long offset, 
                            Long length, 
                            String sr_oid,
                            String sr_link_oid,
                            Long sr_size,
                            Long sr_ctime,
                            Long sr_dtime,
                            String sr_digest,
                            String log_tag,
                            ConnectionWrapper conn)
        throws Throwable 
    {
    	validateConnection(conn);
    	
        PreparedStatement stmt = conn.getInOp();

        int col = 1;

        // op_type
        stmt.setInt(col++, op_type);

        // status
        stmt.setInt(col++, status);

        // start_time
        stmt.setTimestamp(col++, new Timestamp(start_time));

        // end_time
        if (end_time == null)
            stmt.setNull(col++, Types.TIMESTAMP);
        else
            stmt.setTimestamp(col++, new Timestamp(end_time.longValue()));

        // api time
        stmt.setLong(col++, api_time);

        // run_id
        if (run_id == null)
            stmt.setNull(col++, Types.BIGINT);
        else
            stmt.setLong(col++, run_id.longValue());

        // info
        if (info == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, info);

        // oid
        if (oid == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, oid);

        // link_oid
        if (link_oid == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, link_oid);

        // sha1
        if (sha1 == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, sha1);

        // has_metadata
        stmt.setBoolean(col++, has_metadata);

        // num_bytes
        if (num_bytes == null)
            stmt.setNull(col++, Types.BIGINT);
        else
            stmt.setLong(col++, num_bytes.longValue());

        // offset
        if (offset == null)
            stmt.setNull(col++, Types.BIGINT);
        else
            stmt.setLong(col++, offset.longValue());

        // length
        if (length == null)
            stmt.setNull(col++, Types.BIGINT);
        else
            stmt.setLong(col++, length.longValue());

        // sr_oid
        if (sr_oid == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, sr_oid);

        // sr_link_oid
        if (sr_link_oid == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, sr_link_oid);

        // sr_size
        if (sr_size == null)
            stmt.setNull(col++, Types.BIGINT);
        else
            stmt.setLong(col++, sr_size.longValue());

        // sr_ctime
        if (sr_ctime == null)
            stmt.setNull(col++, Types.BIGINT);
        else
            stmt.setLong(col++, sr_ctime.longValue());

        // sr_dtime
        if (sr_dtime == null)
            stmt.setNull(col++, Types.BIGINT);
        else
            stmt.setLong(col++, sr_dtime.longValue());

        // sr_digest
        if (sr_digest == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, sr_digest);
        
        // log_tag
        if (log_tag == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, log_tag);

        stmt.executeUpdate();
    }

    /**
     ** @return the id of the latest op inserted
     **/
    private long selectCurrOpId(ConnectionWrapper conn)
        throws Throwable
    {   	
        ResultSet rs = conn.getSelCurrOpId().executeQuery();
        if (!rs.next())
            throw new HoneycombTestException("unable to 'select currval(op_seq_id)'");
        
        long result = rs.getLong(1);
        
        return result;
    }

    /**
     ** @param status start of op, including retries. (not null)
     ** @param start_time start of op, including retries. (not null)
     ** @param end_time end of op, including retries. (null ok)
     ** @param run_id QB run id. (null ok)
     ** @param info unique string identifying the test run (null ok)
     ** @param sha1 sha1 of the object as calculated by the test (not null)
     ** @param metadata object metadata (null ok)
     ** @param num_bytes size of the object (null ok)
     ** @param sr_oid the oid of the object as returned by a system record
     ** @param sr_link_oid the link oid of the object as returned by a system record
     ** @param sr_size size of file as reported by returned a system record (null ok)
     ** @param sr_ctime create time as reported by returned a system record (null ok)
     ** @param sr_dtime delete time as reported by returned a system record (null ok)
     ** @param sr_digest sha1 digest as reported by returned a system record (null ok)
     **
     **/
    public void insertStoreOp(int status,
                              long start_time,
                              Long end_time,
                              long api_time,
                              Long run_id,
                              String info,
                              String sha1, 
                              NameValueRecord metadata,
                              Long num_bytes,
                              String sr_oid,
                              String sr_link_oid,
                              Long sr_size,
                              Long sr_ctime,
                              Long sr_dtime,
                              String sr_digest,
                              String log_tag,
                              ConnectionWrapper conn)
        throws Throwable 
    {
    	validateConnection(conn);
    	
        boolean hasMetadata = 
            (metadata != null && metadata.getKeys().length > 0);

        insertOp(OP_STORE, 
                 status, 
                 start_time,
                 end_time,
                 api_time,
                 run_id,
                 info,
                 null, // api_oid
                 null, // api_link_oid
                 sha1,
                 hasMetadata,
                 num_bytes,
                 null, // offset
                 null, // length
                 sr_oid,
                 sr_link_oid,
                 sr_size,
                 sr_ctime,
                 sr_dtime,
                 sr_digest,
                 log_tag,
                 conn);
        
        if (hasMetadata)
            insertOpMetadata(this.selectCurrOpId(conn), metadata, conn);
    }

    /**
     ** Insert record of retrieve - for metrics and knowing when
     ** any missing oid was last seen.
     **
     ** @param status integer status code of operation (null ok)
     ** @param start_time start of op, including retries. (not null)
     ** @param end_time end of op, including retries. (null ok)
     ** @param run_id QB run id (null ok)
     ** @param info additional text relevant to the op (null ok)
     ** @param oid the oid of the object as known by the test. (not null)
     ** @param sha1 sha1 digest of the file associated with this operation (null ok)
     ** @param num_bytes num bytes associated with this operation. (null ok)
     ** @param offset num bytes offset for range retrieve (null ok)
     ** @param length num bytes to read for range retrieve (null ok)
     **
     **/
    public void insertRetrieveOp(int status,
                                 long start_time, 
                                 Long end_time,
                                 long api_time,
                                 Long run_id,
                                 String info,
                                 String oid,
                                 String sha1, 
                                 Long num_bytes, 
                                 Long offset, 
                                 Long length,
                                 String log_tag,
                                 ConnectionWrapper conn)
        throws Throwable
    {
    	validateConnection(conn);
    	
        if (oid == null)
            throw new NullPointerException("non-null oid required for retrieve op insertion");

        insertOp((offset == null && length == null) ? 
                 OP_RETRIEVE : OP_RETRIEVE_RANGE, 
                 status, 
                 start_time,
                 end_time,
                 api_time,
                 run_id,
                 info,
                 oid,
                 null, // link_oid
                 sha1,
                 false, // has_metadata
                 num_bytes,
                 offset,
                 length,
                 null, // sr_oid
                 null, // sr_link_oid
                 null, // sr_size
                 null, // sr_ctime
                 null, // sr_dtime
                 null,
                 log_tag,
                 conn); // sr_digest
    }

    
    /**
     ** Insert record of metadata retrieve - for metrics and knowing when
     ** any missing oid was last seen.
     **
     ** @param status integer status code of operation (null ok)
     ** @param start_time start of op, including retries. (not null)
     ** @param end_time end of op, including retries. (null ok)
     ** @param run_id QB run id (null ok)
     ** @param info additional text relevant to the op (null ok)
     ** @param oid the oid of the object as known by the test. (not null)
     ** @param num_bytes num bytes associated with this operation. (null ok)
     ** @param offset num bytes offset for range retrieve (null ok)
     ** @param length num bytes to read for range retrieve (null ok)
     **
     **/
    public void insertRetrieveMDOp(int status,
                                 long start_time, 
                                 Long end_time,
                                 long api_time,
                                 Long run_id,
                                 String info,
                                 String oid,
                                 Long num_bytes, 
                                 Long offset, 
                                 Long length,
                                 String log_tag,
                                 ConnectionWrapper conn)
        throws Throwable
    {
    	validateConnection(conn);
    	
        if (oid == null)
            throw new NullPointerException("non-null oid required for retrieve op insertion");

        insertOp(OP_RETRIEVE_MD, 
                 status, 
                 start_time,
                 end_time,
                 api_time,
                 run_id,
                 info,
                 oid,
                 null, // link_oid
                 null, // sha1
                 false, // has_metadata
                 num_bytes,
                 offset,
                 length,
                 null, // sr_oid
                 null, // sr_link_oid
                 null, // sr_size
                 null, // sr_ctime
                 null, // sr_dtime
                 null,
                 log_tag,
                 conn); // sr_digest
    }
    
    public void insertQueryOp(int status,
            long start_time, 
            Long end_time,
            long api_time,
            Long run_id,
            String info,
            String log_tag,
            ConnectionWrapper conn)
	throws Throwable
	{
		validateConnection(conn);
				
		insertOp(OP_QUERY, 
			status, 
			start_time,
			end_time,
			api_time,
			run_id,
			info,
			null,
			null, // link_oid
			null, // sha1
			false, // has_metadata
			null,
			null,
			null,
			null, // sr_oid
			null, // sr_link_oid
			null, // sr_size
			null, // sr_ctime
			null, // sr_dtime
			null,
			log_tag,
			conn); // sr_digest
	}
    

    /**
     ** @param status integer status code of operation (null ok)
     ** @param start_time start of op, including retries. (not null)
     ** @param end_time end of op, including retries. (null ok)
     ** @param run_id QB run id (null ok)
     ** @param info additional text relevant to the op (null ok)
     ** @param oid the oid of the object as known by the test. (not null)
     **
     **/
    public void insertDeleteOp(int status,
                               long start_time, 
                               Long end_time,
                               long api_time,
                               Long run_id,
                               String info,
                               String oid,
                               String log_tag,
                               ConnectionWrapper conn)
        throws Throwable
    {
    	validateConnection(conn);
    	
        if (oid == null)
            throw new NullPointerException("non-null oid required for retrieve op insertion");

        this.insertOp(OP_DELETE, 
                      status, 
                      start_time,
                      end_time,
                      api_time,
                      run_id,
                      info,
                      oid,
                      null, // link_oid
                      null, // sha1
                      false, // has_metadata
                      null, // num_bytes
                      null, // offset
                      null, // length
                      null, // sr_oid
                      null, // sr_link_oid
                      null, // sr_size
                      null, // sr_ctime
                      null, // sr_dtime
                      null,
                      log_tag,
                      conn); // sr_digest
    }

    /**
     ** @param status integer status code of operation (null ok)
     ** @param start_time start of op, including retries. (not null)
     ** @param end_time end of op, including retries. (null ok)
     ** @param run_id QB run id (null ok)
     ** @param info additional text relevant to the op (null ok)
     ** @param link_oid the linkOID of the object as known by the test. (not null)
     ** @param metadata the metadata associated with the link. (null ok)
     ** @param sr_oid the oid of the object as returned by a system record
     ** @param sr_link_oid the link oid of the object as returned by a system record
     ** @param sr_size size of file as reported by returned a system record (null ok)
     ** @param sr_ctime create time as reported by returned a system record (null ok)
     ** @param sr_dtime delete time as reported by returned a system record (null ok)
     ** @param sr_digest sha1 digest as reported by returned a system record (null ok)
     **
     **/
    public void insertLinkOp(int status,
                             long start_time, 
                             Long end_time,
                             long api_time,
                             Long run_id,
                             String info,
                             String link_oid,
                             NameValueRecord metadata, 
                             String sr_oid,
                             String sr_link_oid,
                             Long sr_size,
                             Long sr_ctime,
                             Long sr_dtime,
                             String sr_digest,
                             String log_tag,
                             ConnectionWrapper conn)
        throws Throwable 
    {
    	validateConnection(conn);
    	
        if (link_oid == null)
            throw new NullPointerException("non-null link_oid required for retrieve op insertion");

        boolean hasMetadata = 
            (metadata != null && metadata.getKeys().length > 0);

        this.insertOp(OP_LINK, 
                      status, 
                      start_time,
                      end_time,
                      api_time,
                      run_id,
                      info,
                      null, // oid
                      link_oid,
                      null, // sha1
                      hasMetadata,
                      null, // num_bytes
                      null, // offset
                      null, // length
                      sr_oid,
                      sr_link_oid,
                      sr_size,
                      sr_ctime,
                      sr_dtime,
                      sr_digest,
                      log_tag,
                      conn);
        
        if (hasMetadata)
            insertOpMetadata(this.selectCurrOpId(conn), metadata, conn);
    }

    private void insertOpMetadata(long opId,
                                  NameValueRecord metadata, 
                                  ConnectionWrapper conn)
        throws Throwable
    {
    	validateConnection(conn);
    
        String [] keys = metadata.getKeys();
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            NameValueSchema.ValueType type = metadata.getAttributeType(key);
            
            if (type == NameValueSchema.STRING_TYPE) {
                PreparedStatement stmt = conn.getInOpMdString();
                int col = 1;
                stmt.setLong(col++, opId);
                stmt.setString(col++, key);
                stmt.setString(col++, metadata.getString(key));
                
                stmt.executeUpdate();
                
            } else if (type == NameValueSchema.LONG_TYPE) {
                PreparedStatement stmt = conn.getInOpMdLong();
                int col = 1;
                stmt.setLong(col++, opId);
                stmt.setString(col++, key);
                stmt.setLong(col++, metadata.getLong(key));

                stmt.executeUpdate();

            } else if (type == NameValueSchema.DOUBLE_TYPE) {
                try {
                    PreparedStatement stmt = conn.getInOpMdDouble();
                    int col = 1;
                    stmt.setLong(col++, opId);
                    stmt.setString(col++, key);
                    stmt.setDouble(col++, metadata.getDouble(key));
                    
                    stmt.executeUpdate();
                }
                catch (SQLException e) {
                    Log.ERROR("Error during insertion of double metadata into audit db");
                    Log.ERROR("This is a known problem.  All other object metadata will be inserted");
                    Log.ERROR(Log.stackTrace(e));
                }

            } else if (type == NameValueSchema.BINARY_TYPE ||
                       type == NameValueSchema.CHAR_TYPE ||
                       type == NameValueSchema.DATE_TYPE ||
                       type == NameValueSchema.TIME_TYPE ||
                       type == NameValueSchema.TIMESTAMP_TYPE ||
                       type == NameValueSchema.OBJECTID_TYPE) {
                //Log.DEBUG("AuditDBClient ignoring metadata of type "+type);
            } else {
                throw new HoneycombTestException("unsupported md type: " +type);
            }
        }
    }

    //
    // </OP TABLE MANIPULATIONS>
    //

    //
    // <OP QUERY FUNCTIONS>
    //

    /**
     ** Perform query over op table.
     ** @param select SQL attributes clause
     ** @param where SQL conditions clause
     ** @param orderby SQL attributes and sort order clause
     ** @return a ResultSet.  Caller should close the ResultSet and Statement.
     **/
    public ResultSet queryOp(String select,
                             String where,
                             String orderby)
        throws Throwable
    {
    	
    	// Get Connection from pool.
    	ConnectionWrapper conn = getConnection();
    	
        Statement stmt = conn.getConnection().createStatement();
        StringBuffer sb = new StringBuffer();
        sb.append("select ");
        sb.append(select);
        if (where != null) {
            sb.append(" where ");
            sb.append(where);
        }
        if (orderby != null) {
            sb.append(" order by ");
            sb.append(orderby);
        }
        
        ResultSet result = stmt.executeQuery(sb.toString());
        
        // Release Connection to Pool.
        returnConnection(conn);
        
        return result;
    }

    //
    // </OP QUERY FUNCTIONS>
    //


    // 
    // <OBJ TABLE MANIPULATIONS>
    //

    /**
     ** @param api_oid external object ID (not null)
     ** @param sys_oid internal object ID (null ok)
     ** @param sha1 sha of the file (not null)
     ** @param metadata object metadata (null ok)
     ** @param size num bytes of the file (null ok.  could be metadata obj)
     ** @param api_link_oid oid of underlying data obj (null ok.  could be a data obj)
     ** @param ctime create time as reported by returned a system record (null ok)
     ** @param dtime delete time as reported by returned a system record (null ok)
     ** @param deleted true if the file has been deleted (not null)
     * @param conn 
     **
     **/
     public void insertObj(String api_oid,
                           String sys_oid,
                           String sha1,
                           NameValueRecord metadata,
                           long size,
                           String api_link_oid,
                           long ctime,
                           long dtime,
                           boolean deleted, 
                           ConnectionWrapper conn)
         throws Throwable
    {
     	validateConnection(conn);
     	
        if (api_oid == null)
            throw new NullPointerException("non-null api_oid required for objection insertion");
        if (sha1 == null)
            throw new NullPointerException("non-null sha1 required for objection insertion");

        boolean hasMetadata =
            (metadata != null && metadata.getKeys().length > 0);

        PreparedStatement stmt = conn.getInObj();
        int col = 1;

        stmt.setString(col++, api_oid);

        if (sys_oid == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, sys_oid);

        stmt.setString(col++, sha1);

        stmt.setBoolean(col++, hasMetadata);

        stmt.setLong(col++, size);

        if (api_link_oid == null)
            stmt.setNull(col++, Types.VARCHAR);
        else
            stmt.setString(col++, api_link_oid);

        stmt.setLong(col++, ctime);

        stmt.setLong(col++, dtime);

        stmt.setBoolean(col++, deleted);
        
        stmt.executeUpdate();

        if (hasMetadata)
            insertMetadata(api_oid, metadata, conn);
    }

    /*
    public String selectObjSha(String oidExt)
        throws Throwable
    {
        PreparedStatement stmt = this.getSelObjSha();
        int col = 1;
        stmt.setString(col++, oidExt);
    }
    */

    public void updateObjDeleted(String oid, ConnectionWrapper conn)
        throws Throwable
    {
        PreparedStatement stmt = conn.getUpObjDeleted();
        stmt.setBoolean(1, true);
        stmt.setString(2, oid);
        stmt.executeUpdate();
    }
    
    public void updateObjDeletedUnkown(String oid, ConnectionWrapper conn)
    throws Throwable
	{
	    PreparedStatement stmt = conn.getUpObjUnknownDeleted();
	    stmt.setNull(1, Types.BOOLEAN);
	    stmt.setString(2, oid);
	    stmt.executeUpdate();
	}

    /**
     ** Insert metadata
     ** @param api_oid external oid
     ** @param metadata the metadata
     **/
    private void insertMetadata(String api_oid,
                                NameValueRecord metadata,
                                ConnectionWrapper conn)
        throws Throwable
    {
    	validateConnection(conn);
    	
        String [] keys = metadata.getKeys();
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            NameValueSchema.ValueType type = metadata.getAttributeType(key);
            
            if (type == NameValueSchema.STRING_TYPE) {
                PreparedStatement stmt = conn.getInMdString();
                int col = 1;
                stmt.setString(col++, api_oid);
                stmt.setString(col++, key);
                stmt.setString(col++, metadata.getString(key));
                
                stmt.executeUpdate();
                
            } else if (type == NameValueSchema.LONG_TYPE) {
                PreparedStatement stmt = conn.getInMdLong();
                int col = 1;
                stmt.setString(col++, api_oid);
                stmt.setString(col++, key);
                stmt.setLong(col++, metadata.getLong(key));

                stmt.executeUpdate();

            } else if (type == NameValueSchema.DOUBLE_TYPE) {
                try {
                    PreparedStatement stmt = conn.getInMdDouble();
                    int col = 1;
                    stmt.setString(col++, api_oid);
                    stmt.setString(col++, key);
                    stmt.setDouble(col++, metadata.getDouble(key));
                    
                    stmt.executeUpdate();
                }
                catch (SQLException e) {
                    Log.ERROR("Error during insertion of double metadata into audit db");
                    Log.ERROR("This is a known problem.  All other object metadata will be inserted");
                    Log.ERROR(Log.stackTrace(e));
                }
            } else if (type == NameValueSchema.BINARY_TYPE ||
                       type == NameValueSchema.CHAR_TYPE ||
                       type == NameValueSchema.DATE_TYPE ||
                       type == NameValueSchema.TIME_TYPE ||
                       type == NameValueSchema.TIMESTAMP_TYPE ||
                       type == NameValueSchema.OBJECTID_TYPE) {
                Log.DEBUG("AuditDBClient.insertMetadata ignoring metadata of type "+type);
            } else {
            	conn.expireLease();
                throw new HoneycombTestException("unsupported md type: " + type);
            }
        }
    }

    public void resetOpsHook(int which) throws HoneycombTestException {
    	ConnectionWrapper conn = null;

        try {
        	// Get Connection from pool.
         	conn = getConnection();
         	
            Statement stmt = conn.createStatement();
            if (which == 1)
                stmt.execute("UPDATE " + OP_TABLE + " SET hook1 = 0");
            else if (which == 2)
                stmt.execute("UPDATE " + OP_TABLE + " SET hook2 = 0");
            else
                throw new HoneycombTestException("resetOpsHook: must be 1 or 2");
            stmt.close();
        } catch (Exception e) {
            throw new HoneycombTestException("resetOpsHook", e);
        } finally {
        	if (conn != null)
        		returnConnection(conn);
        }
    }

    public void resetObjsHook(int which) throws HoneycombTestException {
    	ConnectionWrapper conn = null;

        try {
        	// Get Connection from pool.
         	conn = getConnection();
        
            Statement stmt = conn.createStatement();
            if (which == 1)
                stmt.execute("UPDATE " + OBJ_TABLE + " SET hook1 = 0");
            else if (which == 2)
                stmt.execute("UPDATE " + OBJ_TABLE + " SET hook2 = 0");
            else
                throw new HoneycombTestException(
                                              "resetObjsHook: must be 1 or 2");
            stmt.close();
        } catch (Exception e) {
            throw new HoneycombTestException("resetObjsHook", e);
        } finally {
        	if (conn != null)
        		returnConnection(conn);
        }
    }

    
    /**
     * TODO: CLean up these methods and port them else where incase we want to do threaded access.
     */
    /**
     *  Initialize internal ResultSet for getting obj records via
     *  getNextObjRecord() - one thread should call this
     *  method first, then multiple threads can get records
     *  via getNextObjRecord which is synchronized.
     */
    private ResultSet objResult = null;
    private PreparedStatement fetchObjs = null;
    private PreparedStatement fetchUnmarkedObjs = null;
    private PreparedStatement fetchAllObjs = null;
    
    public void queryObjects(boolean unmarked) throws HoneycombTestException {
        if (objResult != null)
            throw new HoneycombTestException("previous fetchObjs not closed");
        ConnectionWrapper conn = null;
		try {
			conn = getConnection();
		} catch (Throwable e1) {
			throw new HoneycombTestException(e1);
		}
        try {
            if (unmarked) {
                if (fetchUnmarkedObjs == null) {
                    fetchUnmarkedObjs = conn.prepareStatement(
                                          "SELECT * FROM " + OBJ_TABLE +
                                          " WHERE hook1 = 0");
                }
                fetchObjs = fetchUnmarkedObjs;
            } else {
                if (fetchAllObjs == null) {
                    fetchAllObjs = conn.prepareStatement(
                                          "SELECT * FROM " + OBJ_TABLE);
                }
                fetchObjs = fetchAllObjs;
            }
            objResult = fetchObjs.executeQuery();
        } catch (Exception e) {
            throw new HoneycombTestException("queryObjects("+unmarked+")", e);
        } finally {
        	returnConnection(conn);
        }
    }

    synchronized public void resetObjectQuery() throws HoneycombTestException {
        if (objResult == null)
            return;
        try {
            objResult.close();
            fetchObjs.close();
            objResult = null;
        } catch (Exception e) {
            throw new HoneycombTestException("resetObjQuery", e);
        }
    }

    synchronized public HashMap getNextObjectRecord() 
                                                 throws HoneycombTestException {
        if (objResult == null)
            throw new HoneycombTestException(
                                "getNextObjRecord: call queryObjects first");
        try {
            if (!objResult.next()) {
                resetObjectQuery();
                return null;
            }
            HashMap ret = new HashMap();

            ret.put("oid", objResult.getObject("oid"));
            ret.put("size", objResult.getObject("size"));
            ret.put("sha1", objResult.getObject("sha1"));
            ret.put("has_md", objResult.getObject("has_md"));
            ret.put("deleted", objResult.getObject("deleted"));

            return ret;

        } catch (HoneycombTestException e) {
            throw e;
        } catch (Exception e) {
            throw new HoneycombTestException("getNextObjectRecord", e);
        }
    }

    /** main is for cmd line testing */
    public static  void main(String args[]) 
        throws Throwable {

        if (args.length < 2) {
            System.err.println("usage: xxx host cluster");
            System.exit(1);
        }

        String dbhost = args[0];
        String cluster = args[1];

        AuditDBClient db = new AuditDBClient(dbhost, cluster);
        
        ConnectionWrapper conn = db.getConnection();
        
        NameValueObjectArchive archive = 
            new NameValueObjectArchive(cluster + "-data");
        NameValueRecord metadata = archive.createRecord();
        metadata.put("stringlarge", "word1");
        metadata.put("longsmall", (long) 1);
        
        ThreadPropertyContainer.setLogTag("Main Test in AuditDBClient");
        
        db.insertStoreOp(0,
                         System.currentTimeMillis(),
                         new Long(System.currentTimeMillis()),
                         0, // api_time
                         new Long(0), // run id
                         "info",
                         "sha1",
                         metadata,
                         new Long(0), // num_bytes
                         "sroid",
                         "srlinkoid",
                         new Long(0), // sr_size
                         new Long(System.currentTimeMillis()), // sr_ctime
                         new Long(-1), // sr_dtime
                         "sr_digest",
                         ThreadPropertyContainer.getLogTag(),
                         conn);
        conn.commit();

        db.insertStoreOp(-1,
                         System.currentTimeMillis(),
                         null, // end_time
                         0, // api_time
                         new Long(0), // run id
                         null, // info
                         null, // sha1
                         null, // metadata
                         null, // num_bytes
                         null, // sr_oid
                         null, // sr_link_oid
                         null, // sr_size
                         null, // sr_ctime
                         null, // sr_dtime
                         null,
                         ThreadPropertyContainer.getLogTag(),
                         conn); // sr_digest
        conn.commit();

        db.insertRetrieveOp(0,
                            System.currentTimeMillis(),
                            new Long(System.currentTimeMillis()), // end_time
                            0, // api_time
                            new Long(0), // run id
                            "info", // info
                            "oid", // oid
                            "sha1", // sha1
                            new Long(0), // num_bytes
                            new Long(0), // offset
                            new Long(0),
                            ThreadPropertyContainer.getLogTag(),
                            conn); // length
        conn.commit();

        db.insertRetrieveOp(-1,
                            System.currentTimeMillis(),
                            null, // end_time
                            0, // api_time
                            new Long(0), // run id
                            null, // info
                            "oid", // oid
                            null, // sha1
                            null, // num_bytes
                            null, // offset
                            null,
                            ThreadPropertyContainer.getLogTag(),
                            conn); // length
        conn.commit();

        db.insertDeleteOp(0,
                          System.currentTimeMillis(),
                          new Long(System.currentTimeMillis()), // end_time
                          0, // api_time
                          new Long(0), // run id
                          "info", // info
                          "oid",
                          ThreadPropertyContainer.getLogTag(),
                          conn); // oid
        conn.commit();

        db.insertDeleteOp(-1,
                          System.currentTimeMillis(),
                          null, // end_time
                          0, // api_time
                          new Long(0), // run id
                          null, // info
                          "oid",
                          ThreadPropertyContainer.getLogTag(),
                          conn); // oid
        conn.commit();

        db.insertLinkOp(0,
                        System.currentTimeMillis(),
                        new Long(System.currentTimeMillis()),
                        0, // api_time
                        new Long(0), // run id
                        "info",
                        "link_oid",
                        metadata,
                        "sroid",
                        "srlinkoid",
                        new Long(0), // sr_size
                        new Long(System.currentTimeMillis()), // sr_ctime
                        new Long(-1), // sr_dtime
                        "sr_digest",
                        ThreadPropertyContainer.getLogTag(),
                        conn);
        conn.commit();

        db.insertLinkOp(-1,
                        System.currentTimeMillis(),
                        null, // end_time
                        0, // api_time
                        new Long(0), // run id
                        null, // info
                        "link_oid", // link_oid
                        null, // metadata
                        null, // sr_oid
                        null, // sr_link_oid
                        null, // sr_size
                        null, // sr_ctime
                        null, // sr_dtime
                        null,
                        ThreadPropertyContainer.getLogTag(),
                        conn); // sr_digest
        conn.commit();

        long oid_num = System.currentTimeMillis();
        db.insertObj("api_oid" + Long.toString(oid_num),
                     "sys_oid" + Long.toString(oid_num),
                     "sha1",
                     metadata,
                     0, // size
                     "api_link_oid",
                     System.currentTimeMillis(), // ctime
                     0, // dtime
                     false,
                     conn); // deleted
        conn.commit();

        oid_num = System.currentTimeMillis();
        db.insertObj("api_oid" + Long.toString(oid_num),
                     null, // sys_oid
                     "sha1",
                     null,
                     0, // size
                     null,
                     System.currentTimeMillis(), // ctime
                     0, // dtime
                     true,
                     conn); // deleted
        conn.commit();
                     

        /*
        db.insertRetrieveOp(System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            "oid",
                            "sha1",
                            1022222222,
                            "optag",
                            "runtag");
        db.insertDeleteOp(System.currentTimeMillis(),
                          System.currentTimeMillis(),
                          "oid",
                          "optag",
                          "runtag");
        ArrayList recs = db.getOidOps("oid");
        System.out.println("OPTAG\tRUNTAG\tSTART\tEND\tEXEC\tSIZE");
        for (int i=0; i<recs.size(); i++) {
            HashMap hm = (HashMap) recs.get(i);
            String optag = (String) hm.get("op_tag");
            String runtag = (String) hm.get("run_tag");
            Timestamp start = (Timestamp) hm.get("start_time");
            Timestamp end = (Timestamp) hm.get("end_time");
            Long time = (Long) hm.get("duration");
            Long size = (Long) hm.get("size");
            System.out.println(optag + "\t" + runtag + "\t" + start + "\t" +
                               end + "\t" + time + "\t" + size);
        }
        */
    }
    
    public void validateConnection(ConnectionWrapper conn) throws HoneycombTestException{
    	if (conn == null){
       		throw new HoneycombTestException("Connection is null.");
    	}
    }
}
