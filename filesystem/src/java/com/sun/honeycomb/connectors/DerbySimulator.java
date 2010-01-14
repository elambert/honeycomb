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



package com.sun.honeycomb.connectors;

import com.sun.honeycomb.common.NewObjectIdentifier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DerbySimulator extends Simulator {

    private static final Logger logger =
        Logger.getLogger(DerbySimulator.class.getName());

    private static final String dbName = "ObjectDB";
    private static final String OBJ_DIR_NAME = "FileContents";
    private static final int OBJ_DIR_LEVELS = 5;

    private String[] schema = null;
    private Connection conn = null;

    public DerbySimulator(File directory) {
        super(directory);
    }

    public void init() throws IOException {
        if ((conn = getConnection(directory.getAbsolutePath())) == null)
            throw new RuntimeException("Couldn't connect to Derby");

        int i;
        String[] names = HCInterface.getNames();
        schema = new String[names.length + 2];
        for (i = 0; i < names.length; i++)
            schema[i] = names[i];
        schema[i++] = FIELD_EXPIRATION;
        schema[i++] = FIELD_LEGALHOLDS;

        createDB();
        new SaverThread(this).start();
    }

    protected void finalize() {
        if (conn != null) {
            try {
                write();
                conn.close();
            }
            catch (Exception e) {
                System.out.println("Couldn't commit+close!!");
                e.printStackTrace();
            }
        }

        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
            System.out.println("Database shut down normally.");
        }
        catch (SQLException e) {
            System.out.println("Database did not shut down normally!");
        }

        super.finalize();
    }

    //////////////////////////////////////////////////////////////////////

    protected File makeFile(File dir, NewObjectIdentifier oid) {
        String idStr = oid.toExternalHexString();

        int pos = 0;
        StringBuffer sb = new StringBuffer(dir.getAbsolutePath());
        sb.append('/').append(OBJ_DIR_NAME);
        for (int i = 0; i < OBJ_DIR_LEVELS; i++)
            sb.append('/').append(idStr.charAt(i));

        File f = new File(sb.toString());
        if (f.exists() && !f.isDirectory())
            throw new RuntimeException("Unexpected file " + f.getAbsolutePath());

        if (!f.exists())
            f.mkdirs();

        return new File(f, idStr.substring(pos));
    }

    //////////////////////////////////////////////////////////////////////

    // Create or verify DB
    private void read() throws IOException {
        // XXX
    }

    // Save the database to disk
    protected void write() throws IOException {
        logger.info("Writing out changes ...");
        try {
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.toString());
        }
        logger.info("... done.");
    }

    // add an object (synchronization needed for Simulator.setMD())
    protected void store(NewObjectIdentifier oid, Map metadata) {
        synchronized (table) {
            String delim = "";
            String query = "insert into HCSim values(";
            for (int i = 0; i < schema.length; i++) {
                String value = (String) metadata.get(schema[i]);
                if (value != null)
                    query += delim + "'" + HCInterface.quoteEMD(value) + "'";
                else
                    query += delim + "''";
                delim = ",";
            }
            query += ")";

            try {
                execQuery(query);
            }
            catch (SQLException e) {
                System.err.println("Error for query \"" + query + "\"");
                e.printStackTrace();
            }
        }
    }

    /** Get all metadata for object, synchronization for Simulator.setMD() */
    protected Map retrieve(NewObjectIdentifier oid) {
        synchronized (table) {
            Map retval = new HashMap();

            String query = "select * from HCSim where " + quoteSQL(FIELD_OID) +
                "='" + HCInterface.quoteEMD(oid.toHexString()) + "'";

            try {
                ResultSet rs = execQuery(query);

                if (rs.next()) {
                    // There should be exactly one RS

                    for (int i = 0; i < schema.length; i++) {
                        String name = schema[i];
                        String value = rs.getString(i+1);
                        retval.put(name, value);
                    }
                }
            }
            catch (Exception e) {
                System.err.println("Error for query \"" + query + "\"");
                e.printStackTrace();
            }

            return retval;
        }
    }

    /** Add a name-value pair to object's metadata */
    protected void setMD(NewObjectIdentifier oid, String name, String value) {
        synchronized(table) {
            StringBuffer query = new StringBuffer();

            query.append("update HCSim set ");
            query.append(quoteSQL(name)).append("='");
            query.append(HCInterface.quoteEMD(value));
            query.append("' where ");
            query.append(quoteSQL(FIELD_OID)).append("='");
            query.append(HCInterface.quoteEMD(oid.toHexString()));
            query.append("'");

            try {
                execQuery(query.toString());
            }
            catch (Exception e) {
                System.err.println("Error for query \"" + query + "\"");
                e.printStackTrace();
            }
        }
    }

    // query: find next value
    protected int findValues(String[] names, String[] values,
                             MDListener listener) {
        if (names.length <= values.length)
            throw new RuntimeException("names <= values");

        String query = "select distinct " + quoteSQL(names[values.length]) + " from HCSim";

        if (values.length > 0) {
            query += " where ";
            String delim = "";
            for (int i = 0; i < values.length; i++) {
                query += delim;
                query += quoteSQL(names[i]) +
                    "='" + HCInterface.quoteEMD(values[i]) + "'";
                delim = " and ";
            }
        }

        int l = 0;

        try {
            ResultSet rs = execQuery(query);

            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null && v.length() > 0) {
                    l++;
                    listener.nextValue(v);
                }
            }
        }
        catch (SQLException e) {
            System.err.println("Error for query \"" + query + "\"");
            e.printStackTrace();
        }

        return l;
    }

    // query: find object
    protected int findObjects(String[] names, String[] values,
                              ArrayList desiredAttrs,
                              MDListener listener) {
        boolean oidDesired = false;

        String query = "select ";

        String delim = "";
        for (int i = 0; i < desiredAttrs.size(); i++) {
            if (FIELD_OID.equals((String)desiredAttrs.get(i)))
                oidDesired = true;
            query += delim + quoteSQL((String) desiredAttrs.get(i));
            delim = ", ";
        }
        if (!oidDesired)
            // Even if the caller doesn't need it, we do
            query += delim + quoteSQL(FIELD_OID);

        query += " from HCSim where ";

        delim = "";
        for (int i = 0; i < names.length; i++) {
            query += delim;
            query += quoteSQL(names[i]) + "='" + HCInterface.quoteEMD(values[i]) + "'";
            delim = " and ";
        }

        int l = 0;

        try {
            ResultSet rs = execQuery(query);

            while (rs.next()) {
                Map metadata = new HashMap();

                int i;
                //FIXME:   Should use getObject and then use CanonicalString 
                //  encoding.
                for (i = 0; i < desiredAttrs.size(); i++)
                    metadata.put(desiredAttrs.get(i), rs.getString(i + 1));
                if (!oidDesired)
                    metadata.put(FIELD_OID, rs.getString(i + 1));

                String id = (String) metadata.get(HCInterface.FIELD_OID);
                NewObjectIdentifier oid = NewObjectIdentifier.fromHexString(id);
                l++;
                listener.nextObject(oid, metadata);
            }
        }
        catch (SQLException e) {
            System.err.println("Error for query \"" + query + "\": " + e);
            e.printStackTrace();
        }
        return l;
    }

    protected String[] getAttributes(NewObjectIdentifier oid,
                                     ArrayList desiredAttrs) {

        String delim = "";
        String query = "select ";
        for (int i = 0; i < desiredAttrs.size(); i++) {
            query += delim + quoteSQL((String)desiredAttrs.get(i));
            delim = ", ";
        }
        query += " from HCSim where " + quoteSQL(FIELD_OID) +
            "=\'" + oid.toExternalHexString() + "\'";

        String[] results = new String[desiredAttrs.size()];
        try {
            ResultSet rs = execQuery(query);
            rs.next();
            for (int j = 0; j < results.length; j++)
                results[j] = rs.getString(j + 1);
        }
        catch (SQLException e) {
            System.err.println("Error for query \"" + query + "\": " + e);
            e.printStackTrace();
        }
        return results;
    }

    protected HCObject getMatch(String[] names, String[] values) {
        // Need to query for FIELD_OID, FIELD_CTIME, FIELD_SIZE
        String query = "select " + quoteSQL(FIELD_OID) + ", " +
            quoteSQL(FIELD_CTIME) + ", " + quoteSQL(FIELD_SIZE) +
            " from HCSim where ";

        String delim = "";
        for (int i = 0; i < values.length; i++) {
            query += delim;
            query += quoteSQL(names[i]) + "='" + HCInterface.quoteEMD(values[i]) + "'";
            delim = " and ";
        }

        logger.info("Searching with query \"" + query + "\"");

        try {
            ResultSet rs = execQuery(query);
            if (rs.next()) {
                NewObjectIdentifier oid =
                    NewObjectIdentifier.fromHexString(rs.getString(1));
                long ctime = Long.parseLong(rs.getString(2));
                long size = Long.parseLong(rs.getString(3));
                logger.info("Returning " + oid + 
                            ": ctime=" + ctime + ", size=" + size);
                return new HCObject(oid, ctime, size);
            }
            else
                logger.info("No match.");
        }
        catch (SQLException e) {
            System.err.println("Error for query \"" + query + "\": " + e);
            e.printStackTrace();
        }
        return null;
    }

    private void createDB() {
        String query = "create table HCSim(";
        String delim = "";
        for (int i = 0; i < schema.length; i++) {
            query += delim + quoteSQL(schema[i]) + " varchar(200)";
            delim = ", ";
        }
        query += ")";

        try {
            execQuery(query);
        }
        catch (SQLException e) {
            logger.info("DB create: " + e);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "DB create: " + e);
        }
    }

    private ResultSet execQuery(String query) throws SQLException {
        logger.info("sqlQuery \"" + query + "\"");
        ResultSet rs = null;

        if (query.startsWith("select "))
            rs = conn.createStatement().executeQuery(query);
        else
            conn.createStatement().execute(query);

        return rs;
    }

    private static String quoteSQL(String s) {
        return s.replace('.', '_');
    }

    private static Connection getConnection(String dirName) {
        Connection conn = null;
        System.setProperty("derby.system.home", dirName);

        Properties props = new Properties();
        props.put("user", "user1");
        props.put("password", "user1");

        try {
            String driver = "org.apache.derby.jdbc.EmbeddedDriver";
            String url = "jdbc:derby:" + dbName + ";create=true";

            Class.forName(driver).newInstance();
            conn = DriverManager.getConnection(url, props);
            conn.setAutoCommit(false);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return conn;
    }

}

