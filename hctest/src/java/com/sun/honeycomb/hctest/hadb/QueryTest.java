/**
 * $Id: QueryTest.java 8076 2006-04-25 21:17:47Z wr152514 $
 * Copyright (c) 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package com.sun.honeycomb.hctest.hadb;
 
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;

public class QueryTest {

    Connection conn;

    QueryTest(String[] args) {
        
        connect(args[0]);
     
        String query1 = "select LOGsystem_object_id.objectid, LOGsystem_object_id.attrvalue, LOGsystem_object_ctime.attrvalue, LOGsystem_object_size.attrvalue from stringattribute as LOGofoto_dir2, longattribute as LOGsystem_object_ctime, stringattribute as LOGofoto_dir3, stringattribute as LOGofoto_dir1, stringattribute as LOGofoto_fname, longattribute as LOGsystem_object_size, stringattribute as LOGofoto_dir5, stringattribute as LOGofoto_dir6, stringattribute as LOGsystem_object_id, stringattribute as LOGofoto_dir4 where LOGofoto_dir2.objectid=LOGsystem_object_id.objectid " +
        " AND LOGsystem_object_ctime.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir3.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir1.objectid=LOGsystem_object_id.objectid" + 
        " AND LOGofoto_fname.objectid=LOGsystem_object_id.objectid" +
        " AND LOGsystem_object_size.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir5.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir6.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir4.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir2.attrname='ofoto.dir2'" +
        " AND LOGsystem_object_ctime.attrname='system.object_ctime'" +
        " AND LOGofoto_dir3.attrname='ofoto.dir3'" +
        " AND LOGofoto_dir1.attrname='ofoto.dir1'" +
        " AND LOGofoto_fname.attrname='ofoto.fname'" +
        " AND LOGsystem_object_size.attrname='system.object_size'" +
        " AND LOGofoto_dir5.attrname='ofoto.dir5'" +
        " AND LOGofoto_dir6.attrname='ofoto.dir6'" +
        " AND LOGsystem_object_id.attrname='system.object_id'" +
        " AND LOGofoto_dir4.attrname='ofoto.dir4'";
        String q2a =
        " AND ((((((( LOGofoto_dir1.attrvalue = '00'" +
        " AND LOGofoto_dir2.attrvalue = '00' )" +
        " AND LOGofoto_dir3.attrvalue = '00' )" +
        " AND LOGofoto_dir4.attrvalue = '00' )" +
        " AND LOGofoto_dir5.attrvalue = '00' )" +
        " AND LOGofoto_dir6.attrvalue = '00' )" +
        " AND LOGofoto_fname.attrvalue = 'xop_0_15k' ))" +
        " ORDER BY LOGsystem_object_id.objectid";
        String q2a2 =
        " AND LOGofoto_dir1.attrvalue = '00'" +
        " AND LOGofoto_dir2.attrvalue = '00' " +
        " AND LOGofoto_dir3.attrvalue = '00' " +
        " AND LOGofoto_dir4.attrvalue = '00' " +
        " AND LOGofoto_dir5.attrvalue = '00' " +
        " AND LOGofoto_dir6.attrvalue = '00' " +
        " AND LOGofoto_fname.attrvalue = 'xop_0_15k' " +
        " ORDER BY LOGsystem_object_id.objectid";
        String q2b =
        " AND ((((((( LOGofoto_dir1.attrvalue = ?" +
        " AND LOGofoto_dir2.attrvalue = ? )" +
        " AND LOGofoto_dir3.attrvalue = ? )" +
        " AND LOGofoto_dir4.attrvalue = ? )" +
        " AND LOGofoto_dir5.attrvalue = ? )" +
        " AND LOGofoto_dir6.attrvalue = ? )" +
        " AND LOGofoto_fname.attrvalue = ? ))" +
        " ORDER BY LOGsystem_object_id.objectid";

        String query2 = "select LOGsystem_object_id.objectid, LOGsystem_object_id.attrvalue, LOGsystem_object_ctime.attrvalue, LOGsystem_object_size.attrvalue from stringattribute as LOGofoto_dir2, longattribute as LOGsystem_object_ctime, stringattribute as LOGofoto_dir3, stringattribute as LOGofoto_dir1, stringattribute as LOGofoto_fname, longattribute as LOGsystem_object_size, stringattribute as LOGofoto_dir5, stringattribute as LOGofoto_dir6, stringattribute as LOGsystem_object_id, stringattribute as LOGofoto_dir4 where LOGofoto_dir2.objectid=LOGsystem_object_id.objectid " +
        " AND LOGsystem_object_ctime.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir3.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir1.objectid=LOGsystem_object_id.objectid" + 
        " AND LOGofoto_fname.objectid=LOGsystem_object_id.objectid" +
        " AND LOGsystem_object_size.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir5.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir6.objectid=LOGsystem_object_id.objectid" +
        " AND LOGofoto_dir4.objectid=LOGsystem_object_id.objectid" +
        " AND LOGsystem_object_ctime.attrname='system.object_ctime'" +
        " AND LOGsystem_object_size.attrname='system.object_size'" +
        " AND LOGsystem_object_id.attrname='system.object_id'" +
        " AND (LOGofoto_fname.attrname='ofoto.fname'" +
        " AND LOGofoto_fname.attrvalue = 'xop_0_15k') " +
        " AND (LOGofoto_dir3.attrname='ofoto.dir3'" +
        " AND LOGofoto_dir2.attrvalue = '00') " +
        " AND (LOGofoto_dir2.attrname='ofoto.dir2'" +
        " AND LOGofoto_dir3.attrvalue = '00') " +
        " AND (LOGofoto_dir1.attrname='ofoto.dir1'" +
        " AND LOGofoto_dir1.attrvalue = '00') " +
        " AND (LOGofoto_dir5.attrname='ofoto.dir5'" +
        " AND LOGofoto_dir5.attrvalue = '00') " +
        " AND (LOGofoto_dir6.attrname='ofoto.dir6'" +
        " AND LOGofoto_dir6.attrvalue = '00') " +
        " AND (LOGofoto_dir4.attrname='ofoto.dir4'" +
        " AND LOGofoto_dir4.attrvalue = '00') " +
        " ORDER BY LOGsystem_object_id.objectid";
        try {
            Statement st = conn.createStatement();
            long t1 = System.currentTimeMillis();
            ResultSet res = st.executeQuery(query1+q2a);
            t1 = System.currentTimeMillis() - t1;
            System.out.println("time: " + t1 + "  found: " + res.next());

            PreparedStatement ps = conn.prepareStatement(query1 + q2b);
            ps.setString(1, "00");
            ps.setString(2, "00");
            ps.setString(3, "00");
            ps.setString(4, "00");
            ps.setString(5, "00");
            ps.setString(6, "00");
            ps.setString(7, "xop_0_15k");
            t1 = System.currentTimeMillis();
            res = ps.executeQuery();
            t1 = System.currentTimeMillis() - t1;
            System.out.println("time1: " + t1);
            t1 = System.currentTimeMillis();
            res = ps.executeQuery();
            t1 = System.currentTimeMillis() - t1;
            System.out.println("time2: " + t1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void connect(String host) {

        String SqlUrl = "jdbc:sun:hadb:system+superduper@" + host + ":"
                                        + 15005;
        try {
            Class.forName("com.sun.hadb.jdbc.Driver");
            conn = DriverManager.getConnection(SqlUrl);
        } catch (Exception e) {
            System.err.println("connect: " + e);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        new QueryTest(args);
    }
}

