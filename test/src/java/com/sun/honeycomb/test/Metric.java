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



package com.sun.honeycomb.test;

import com.sun.honeycomb.test.util.QB;
import com.sun.honeycomb.test.util.Log;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.sql.Time;

/** 
 * Tests can collect metrics (key-value, point-in-time measurements).
 * Metrics are recorded in QB database with a reference to test result.
 * It is also possible to group metrics together.
 */

public class Metric
{
    private String name; // meaningful name of what we measure: "uptime"
    private String units; // units of measurement: "DD:HH:MM"
    private String atTime; // when measurement took place: YYYY-MM-DD HH:MM:SS

    private int metricId; // set when metric is posted to QB, used for groups
    private int result; // Result ID to reference in QB database
    private int group; // Metric group ID, if posting a set of metrics together

    private int intValue;
    private double doubleValue;
    private String stringValue;
    private Time timeValue; // java.sql.Time (duration, not date/time)
    private String printValue; // any type -> string for display purposes

    private String datatype; // see choices below

    // Metrics of each datatype are recorded in a separate QB DB table,
    // to make use of DB ability to compute average/max/min etc.

    public static final String TYPE_INT = "int"; // eg: 12345
    public static final String TYPE_DOUBLE = "double"; // eg: 0.12345
    public static final String TYPE_STRING = "string"; // eg. foo
    public static final String TYPE_TIME = "time"; // eg: 12:34:56

    private QB qb = null; // handle to QB repository
    

    /** Any public constructor must call this private ctor.
     *  Hack: the boolean arg is only used to distinguish signature.
     */
    private Metric(String mName, String mUnits, String mVal, boolean basic) {
        name = mName;
        units = mUnits;
        printValue = mVal; // for display purposes
        atTime = QB.sqlDateTime(System.currentTimeMillis()); // now
        metricId = result = group = 0;
        qb = QB.getInstance();
    }

    public Metric(String name, String units, int val) {
        this(name, units, Integer.toString(val), true);
        datatype = TYPE_INT;
        intValue = val;
    } 

    public Metric(String name, String units, double val) {
        this(name, units, Double.toString(val), true);
        datatype = TYPE_DOUBLE;
        doubleValue = val;
    }

    // We store longs as doubles in the DB, but print them as longs for
    // readability
    public Metric(String name, String units, long val) {
        this(name, units, Long.toString(val), true);
        datatype = TYPE_DOUBLE;
        doubleValue = (new Long(val)).doubleValue();
    }

    public Metric(String name, String units, String val) {
        this(name, units, val, true);
        datatype = TYPE_STRING;
        stringValue = val;
    }

    public Metric(String name, String units, Time val) {
        this(name, units, val.toString(), true);
        datatype = TYPE_TIME;
        timeValue = val;
    }

    public int getId() { return metricId; }

    // Not public API. The test authors will call TestCase::postMetric()
    // or Result::postMetric() which will correlate Metric with Result.
    //
    protected void setResult(int resultId) {
        result = resultId;
    }

    /** Metrics can be posted together as a group.
     *  A metric can belong to at most 1 group (for simplicity).
     *
     *  In this case, we will use first metric's ID (returned from post())
     *  as the groupId for all metrics in the group. 
     *  Yes, this means that first metric must be posted and then updated.
     *  Not public API, called from postMetricGroup().
     */
    protected void setGroup (int groupId) {
        group = groupId;
    }

    protected static void postGroup(Metric[] mm) throws Throwable {
        Metric m = mm[0]; // first metric in the group will reference itself
        m.post(); // will get metric ID back from QB database
        int groupId = m.getId(); // use first metric's ID as group ID
        
        for (int i = 0; i < mm.length; i++) {
            mm[i].setGroup(groupId);
            mm[i].post();
        } 
        // now all metrics in the group are posted with the same group ID
    }
    
    public String toString() {
        return 
            name + " = " + printValue + " " 
            + units + " (" + datatype + ") "
            + ((group == 0) ? "" : "group = " + group);
        // no need for result ID - metrics are printed in the context of Result
    }

    public void post() throws Throwable {
        if (qb.isOff()) return;
        File postMetric = qb.dataFile("metric");
        writeRecord(new BufferedWriter(new FileWriter(postMetric)));
        metricId = qb.post("metric", postMetric);
        // The ID comes from result_metric table
    }

    private void writeRecord(BufferedWriter out) throws Throwable {
        // on update, metricId will be known (used for metric groups)
        if (metricId != 0) out.write("QB.id: " + metricId + "\n"); 
        out.write("QB.name: " + name + "\n");
        out.write("QB.units: " + units + "\n");
        out.write("QB.datatype: " + datatype + "\n");
        out.write("QB.value: " + printValue + "\n");
        out.write("QB.at_time: " + atTime + "\n");
        out.write("QB.result: " + result + "\n");
        if (group != 0) out.write("QB.mgroup: " + group + "\n");
        out.close();
    }
}
