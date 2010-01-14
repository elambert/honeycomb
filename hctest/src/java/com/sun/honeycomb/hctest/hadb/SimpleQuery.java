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


package com.sun.honeycomb.hctest.hadb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;

import com.sun.honeycomb.hadb.convert.QueryConvert;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.parsers.ParseException;

public class SimpleQuery {

    public static void main(String[] args) {
	new SimpleQuery(args);
    }

    Connection conn = null;
    long total_time = 0;
    int n_meas = 0;
    long total_time_u = 0;
    int n_meas_u = 0;

    SimpleQuery(String[] args) {
        connect(args[0]);

        ArrayList attrs = new ArrayList();
        //attrs.add("stringnull");
        attrs.add("ofoto.dir1");

        doQuery("ofoto.dir2='0'", attrs);
        doQuery("ofoto.dir1='0' and ofoto.dir2='0' and ofoto.dir3='0' " +
                " and ofoto.dir4='0' and ofoto.dir5='0' and ofoto.dir6='0' " +
                " and ofoto.fname='0'", attrs);

        doQueryUnique("ofoto.dir1", "ofoto.dir2='0'");
        doQueryUnique("ofoto.dir1", "ofoto.dir2='0' and ofoto.dir3='0'" +
                " and ofoto.dir4='0' and ofoto.dir5='0' and ofoto.dir6='0' " +
                " and ofoto.fname='0'");

        //doQuery("stringlarge='YES'", attrs);
        //doQuery("stringlarge='empty'", attrs);
        //doQuery("stringlarge='No'", attrs);
        //doQuery("stringlarge='MaybeSo'", attrs);
        //doQuery("stringlarge='AVALUE'", attrs);

        if (n_meas > 0)
            System.out.println("average: " + (total_time / n_meas));
        if (n_meas_u > 0)
            System.out.println("average_u: " + (total_time_u / n_meas_u));
    }

    private void doQuery(String query, ArrayList attrs) {

        try {
            QueryConvert qc = new QueryConvert(query, (NewObjectIdentifier)null, attrs);
            StringBuffer sb = new StringBuffer();
            qc.convert(sb);
            String cq = sb.toString();
            // take out 'order by' to see what happens [nothing]
            // cq = cq.substring(0, cq.indexOf("ORDER")-1);
            System.out.println("raw: [" + query + "]");
            System.out.println("cooked: [" + cq + "]");

            Statement st = conn.createStatement();
            // st.setMaxRows(10); - query hangs ??

            long t1 = System.currentTimeMillis();
            ResultSet res = st.executeQuery(cq);
            t1 = System.currentTimeMillis() - t1;
            total_time += t1;
            n_meas++;
            int ct = 0;
            while (res.next())
                ct++;
            System.out.println("time: " + t1 + "  found: " + ct);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void doQueryUnique(String unique, String query) {

        try {
            QueryConvert qc = new QueryConvert(unique, query, null);
            StringBuffer sb = new StringBuffer();
            qc.convert(sb);
            String cq = sb.toString();
            // take out 'order by' to see what happens [nothing]
            // cq = cq.substring(0, cq.indexOf("ORDER")-1);
            System.out.println("raw: [" + query + "]");
            System.out.println("cooked: [" + cq + "]");

            Statement st = conn.createStatement();
            // st.setMaxRows(10); - query hangs ??

            long t1 = System.currentTimeMillis();
            ResultSet res = st.executeQuery(cq);
            t1 = System.currentTimeMillis() - t1;
            total_time_u += t1;
            n_meas_u++;
            int ct = 0;
            while (res.next())
                ct++;
            System.out.println("utime: " + t1 + "  found: " + ct);

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
            conn.setAutoCommit(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        } catch (Exception e) {
            System.err.println("connect: " + e);
            System.exit(1);
        }
    }
}
