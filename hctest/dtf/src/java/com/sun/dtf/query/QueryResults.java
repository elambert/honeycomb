package com.sun.dtf.query;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;

import com.sun.dtf.exception.ParseException;
import com.sun.dtf.recorder.Event;


public class QueryResults implements ResultSet {

    private static int MAX_ROWS = 500;
    
    private ArrayList _metadata = null;
   
    private int _index = -1;
    private String _query = null;
    private Connection _connection = null;
    private ResultSetMetaData _md = null;
    
    private int _id = 0;

    public QueryResults(ResultSet set, ArrayList fields) throws SQLException,
            ParseException {
        init(set);
    }
    
    public void init(ResultSet set) throws SQLException,
                ParseException {
        _metadata = new ArrayList();
        _index = -1;
        
        _md = set.getMetaData();
        while (set.next()) {
            Event event = new Event();
            for (int i = 1; i <= _md.getColumnCount(); i++) {
                String name = _md.getColumnName(i);
                String value = set.getString(name);
                event.addAttribute(name, value, false);
            }

            _metadata.add(event);
            _id = set.getInt("ID");
        }
        
        set.close();
    }

    public QueryResults(String query, Connection connection)
           throws SQLException, ParseException {
        init(query,connection);
    }
    
    public void init(String query, Connection connection)
           throws SQLException, ParseException {
        _query = query.toLowerCase();
        synchronized(connection) { 
            _connection = connection;
            
            if (query.indexOf("where") != -1)
                query = query + " AND id > " + _id;
            else
                query = query + " where id > " + _id;
            
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setMaxRows(MAX_ROWS);
            stmt.execute();
            init(stmt.getResultSet());
        }
    }

    public boolean next() throws SQLException {
        _index++;
        if (_index >= _metadata.size()) { 
            try {
                init(_query,_connection);
            } catch (ParseException e) {
                //TODO: better exception details throwing...
                throw new SQLException("Error parsing event.",e.getMessage());
            }
            _index++;
        }
        
        return (_index < _metadata.size());
    }
    
    public Object getObject(String columnLabel) throws SQLException {
        Event event = (Event)_metadata.get(_index);
        return event.retAttribute(columnLabel);
    }
    
    public String getString(String columnLabel) throws SQLException {
        Event event = (Event)_metadata.get(_index);
        try {
            return event.retAttribute(columnLabel).getValue();
        } catch (ParseException e) {
            //TODO: better exception details throwing...
            throw new SQLException("Error processing attribute.",e.getMessage());
        }
    }
    
    public void close() throws SQLException { }

    public ResultSetMetaData getMetaData() throws SQLException {
        return _md;
    }

    // Nothing below this line is implemented...
    public boolean absolute(int row) throws SQLException {
        throw new RuntimeException("Method not implemented.");
    }

    public void afterLast() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void beforeFirst() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void cancelRowUpdates() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void clearWarnings() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void deleteRow() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int findColumn(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean first() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Array getArray(int i) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Array getArray(String colName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public InputStream getAsciiStream(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public BigDecimal getBigDecimal(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public BigDecimal getBigDecimal(int columnIndex, int scale)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public BigDecimal getBigDecimal(String columnName, int scale)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public InputStream getBinaryStream(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Blob getBlob(int i) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Blob getBlob(String colName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean getBoolean(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean getBoolean(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public byte getByte(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public byte getByte(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public byte[] getBytes(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public byte[] getBytes(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Reader getCharacterStream(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Reader getCharacterStream(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Clob getClob(int i) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Clob getClob(String colName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int getConcurrency() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public String getCursorName() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Date getDate(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Date getDate(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Date getDate(String columnName, Calendar cal) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public double getDouble(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public double getDouble(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int getFetchDirection() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int getFetchSize() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public float getFloat(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public float getFloat(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int getInt(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int getInt(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public long getLong(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public long getLong(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Object getObject(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Object getObject(int arg0, Map arg1) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Object getObject(String arg0, Map arg1) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Ref getRef(int i) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Ref getRef(String colName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int getRow() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public short getShort(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public short getShort(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Statement getStatement() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public String getString(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Time getTime(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Time getTime(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Time getTime(String columnName, Calendar cal) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Timestamp getTimestamp(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Timestamp getTimestamp(int columnIndex, Calendar cal)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public Timestamp getTimestamp(String columnName, Calendar cal)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public int getType() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public URL getURL(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public URL getURL(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public InputStream getUnicodeStream(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public SQLWarning getWarnings() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void insertRow() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean isAfterLast() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean isBeforeFirst() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean isFirst() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean isLast() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean last() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void moveToCurrentRow() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void moveToInsertRow() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean previous() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void refreshRow() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean relative(int rows) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean rowDeleted() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean rowInserted() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean rowUpdated() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void setFetchDirection(int direction) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void setFetchSize(int rows) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateArray(int columnIndex, Array x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateArray(String columnName, Array x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateAsciiStream(int columnIndex, InputStream x, int length)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateAsciiStream(String columnName, InputStream x, int length)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBigDecimal(int columnIndex, BigDecimal x)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBigDecimal(String columnName, BigDecimal x)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBinaryStream(int columnIndex, InputStream x, int length)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBinaryStream(String columnName, InputStream x, int length)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBlob(String columnName, Blob x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBoolean(String columnName, boolean x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateByte(int columnIndex, byte x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateByte(String columnName, byte x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateBytes(String columnName, byte[] x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateCharacterStream(int columnIndex, Reader x, int length)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateCharacterStream(String columnName, Reader reader,
            int length) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateClob(int columnIndex, Clob x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateClob(String columnName, Clob x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateDate(int columnIndex, Date x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateDate(String columnName, Date x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateDouble(int columnIndex, double x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateDouble(String columnName, double x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateFloat(int columnIndex, float x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateFloat(String columnName, float x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateInt(int columnIndex, int x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateInt(String columnName, int x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateLong(int columnIndex, long x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateLong(String columnName, long x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateNull(int columnIndex) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateNull(String columnName) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateObject(int columnIndex, Object x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateObject(String columnName, Object x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateObject(int columnIndex, Object x, int scale)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateObject(String columnName, Object x, int scale)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateRef(int columnIndex, Ref x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateRef(String columnName, Ref x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateRow() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateShort(int columnIndex, short x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateShort(String columnName, short x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateString(int columnIndex, String x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateString(String columnName, String x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateTime(int columnIndex, Time x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateTime(String columnName, Time x) throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateTimestamp(int columnIndex, Timestamp x)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public void updateTimestamp(String columnName, Timestamp x)
            throws SQLException {
        throw new RuntimeException("Method not supported.");
    }

    public boolean wasNull() throws SQLException {
        throw new RuntimeException("Method not supported.");
    }
}