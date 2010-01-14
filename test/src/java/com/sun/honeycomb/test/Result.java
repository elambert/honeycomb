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

import com.sun.honeycomb.test.util.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * Used to record information about a test result.
 * Note: startTime is initialized by the constructor; remember to use 
 * the startClock() method if necessary to get startTime right.
 */
public class Result 
{
    public boolean pass;
    public boolean skipped;
    public long startTime = 0;
    public long endTime = 0;
    public String build;
    public String performer;
    public int procRetval;
    public String logsURL;
    public StringBuffer notes;
    public List bugs;
    public List metrics;
    public List tags;

    private int resultId = 0;
    private String scenario = null;
    private String params = null;
    private Run run = null; // access to singleton
    private int runId = 0;
    private String status = "unknown";
    private String startTimeQB = null;
    private String endTimeQB = null;
    private QB qb = null; // handle to QB repository
    private boolean started; // for sanity checking: start record in QB?
    private boolean posted; // more sanity checking: end record in QB?
    
    /**
     * Test Name is broken into two parts: common scenario + specific params
     * (analogous to a common script with specific command-line arg values)
     *
     * Protected means that it's got package wide access, but clients
     * should be calling postResult, not creating their own results.
     */
    protected Result(String scenario, String params)
    {
	this.scenario = scenario;
	this.params = params;

        started = posted = skipped = pass = false;
        startClock(); // because start() is not always called
        run = Run.getInstance();
        runId = run.getId();
        build = run.getBuildVersion();
        performer = TestRunner.getProperty(TestConstants.PROPERTY_OWNER);
        procRetval = 1;
        logsURL = run.getLogsURL();
        notes = new StringBuffer();
        bugs = new ArrayList();
        metrics = new ArrayList();
        tags = new ArrayList();
        qb = QB.getInstance();
    }

    /** post result-start record to QB database */
    public void start() throws Throwable {
        started = true;
        startClock(); // yes, restart the clock for more precision
        // eg: if the test had dependencies, their runtime shouldn't
        // be counted toward the duration of this test
        status = "running";
        // set startTimeQB if qb.isOff()
        qbPost();
    }

    /** post result-end record to QB database */
    public void post() throws Throwable {
        posted = true; 
        status = (pass ? "pass" : (skipped ? "skipped" : "fail"));
        stopClock();
        // set endTimeQB if qb.isOff()
        qbPost();
        // this Result has been posted to QB, no unfinished business here
    }

    /** set result status to skipped, later need to post() */
    public void skipped() {
        // set startTimeQB and endTimeQB
        skipped = true;
	status = "skipped";
    }

    /* this code was identical in start() and post() => deserved a method 
     * sends the entire Result data structure to QB database
     */
    private void qbPost() throws Throwable {
        if (qb.isOff()) {
            return;
        }
        File resultFile = qb.dataFile("result");
        writeRecord(new BufferedWriter(new FileWriter(resultFile)));
        resultId = qb.post("result", resultFile);
        postBugs();
        postTags();
    }

    // write test result data (eg: to a temp file)
    private void writeRecord(BufferedWriter out) throws Throwable {
        if (resultId != 0) out.write("QB.id: " + resultId + "\n"); // 0 is illegal
        // scenario and params must be set, otherwise post will fail
        if (scenario != null) out.write("QB.testproc: " + scenario + "\n");
        if (params != null) out.write("QB.parameters: " + params + "\n");
        if (runId != 0) out.write("QB.run: " + runId + "\n"); // run context is optional
        out.write("QB.status: " + status + "\n"); // status is always set
        if (startTimeQB != null) out.write("QB.start_time: " + startTimeQB + "\n");
        if (endTimeQB != null) out.write("QB.end_time: " + endTimeQB + "\n");
        if (build != null) out.write("QB.build: " + build + "\n");
        if (performer != null) out.write("QB.submitter: " + performer + "\n");
        if (logsURL != null) out.write("QB.logs_url: " + logsURL + "\n");
        out.write("QB.notes: " + new String(notes) + "\n");
        out.close();
    }

    public int getId() { return resultId; }
    public boolean isInit() { return started; }
    public boolean isDone() { return (isInit() ? posted : true); }

    // record a bug with this test result, and some notes
    public void addBug(String id, String notes) {
        bugs.add(new Bug(id, notes));
    }

    public void addBug(Bug bug) {
        bugs.add(bug);
    }

    private void postBugs() {
        if (bugs != null) {
            Iterator i = bugs.iterator();
            while (i.hasNext()) {
                Bug b = (Bug)i.next();
                b.setResult(resultId);
                try { 
                    b.post();
                } catch(Throwable t) { ; }
            }
        }
    }

    public void postMetric(Metric m) {
        m.setResult(resultId);
        try {
            m.post(); // to see real-time metrics in QB, post immediately
        } catch(Throwable t) {;}
        Log.INFO("METRIC: " + m);
        metrics.add(m); // XXX: do we need to track metrics here at all?
    }

    public void postMetricGroup(Metric[] mm) {
        for (int i = 0; i < mm.length; i++) {
            mm[i].setResult(resultId);
        }
        try {
            Metric.postGroup(mm);
        } catch(Throwable t) {;}

        for (int i = 0; i < mm.length; i++) {
            Log.INFO("METRIC: " + mm[i]);
            metrics.add(mm[i]); // XXX: need to track?
        }
    }

    private void postTags() {
        for (int i = 0; i < tags.size(); i++) {
            Tag t = (Tag)tags.get(i);
            t.setResult(resultId);
            try {
                t.post();
            } catch(Throwable e) { ; }
        }
    }


    /**
     * Sets startTime = System.currentTime()
     */
    public void startClock()
    {
        startTime = System.currentTimeMillis();
        startTimeQB = QB.sqlDateTime(startTime);
        // Log.DEBUG("startTime: " + startTimeQB);
    }

    /**
     * Sets endTime = System.currentTime()
     */
    public void stopClock()
    {
        if (endTime == 0) {
            endTime = System.currentTimeMillis();
            endTimeQB = QB.sqlDateTime(endTime);
            // Log.DEBUG("endTime: " + endTimeQB);
        }
    }

    /**
     * Appends a stack trace to the notes.
     */
    public static String logTrace(Throwable t)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        sw.write("<stacktrace>\n");
        t.printStackTrace(pw);
        sw.write("</stacktrace>\n");
        return sw.toString();
    }

    /* For logging purposes: short representation of Result
     */
    public String toString() {
        StringBuffer sb = new StringBuffer(1024);
        sb.append(status.toUpperCase() + " RESULT_ID=" + resultId +
                  " RUNTIME=" + Util.formatTime(endTime - startTime));
        if (!bugs.isEmpty()) { // add bug list
            sb.append(" BUGS=");
            Iterator i = bugs.iterator();
            while (i.hasNext()) {
                sb.append(i.next() + ",");
            } // chop extra comma
            sb.delete(sb.length()-1, sb.length()); 
        }
        sb.append(" " + getFullTestName());
        return sb.toString();
    }

    /* If the test failed, print all declared bugs and their reasons as "Possible causes"
     * If the test passed, print all declared bugs and their reasons as "Possibly fixed"
     * Each bug/reason message will be on a separate line (hence the string array)
     */
    public String[] bugDetails() {
        if (bugs.isEmpty()) return null;
        ArrayList lines = new ArrayList();
        String prefix = (pass ? "Possibly fixed " : "Possible causes ");
        int total = bugs.size();
        for (int i = 0, num = 1; i < bugs.size(); i++, num++) {
            Bug bug = (Bug)bugs.get(i);
            String bugMsg = prefix + "(" + num + " of " + total + "): " + bug.details();
            lines.add(bugMsg);
        }
        return (String[])lines.toArray(new String[0]);
    }

    public String getFullTestName() { return scenario + "::" + params; }
    public String getMethodName() { return scenario; }
    public String getProcedure() { return scenario; }
    public String getParameters() { return params; }
}
