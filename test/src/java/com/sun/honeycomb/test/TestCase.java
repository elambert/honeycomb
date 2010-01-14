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

import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.TestAlreadyRunException;

import java.util.ArrayList;
import java.util.Iterator;

/* A testcase is uniquely identified by procedure+parameters.
 *
 * A testcase can declare many tags:
 * "I am a regression, long-running, metadata king of test".
 *
 * A testcase can have many dependencies:
 * "I only run if Suites A and B passed, and my predecessor testC passed".
 * 
 * A testcase can generate many results during one run:
 * "I did this thing 10 times over, 6 of them passed and 4 failed".
 * 
 * In the context of each result, the test can record many bugs:
 * "This failure was due to bugs 123 and also 456".
 * 
 * In the context of each result, the test can record many metrics:
 * "I measured throughput = 5MB/s and response time = 3ms".
 *
 * All such multi-item data will be tracked in lists.
 */


public class TestCase
{
    private String procedure;  // base name of test scenario
    private String parameters; // incl. specific values
    private String name; // procedure::parameters

    private Run run; // access to singleton

    private boolean skipped; // was this test skipped? need for bookkeeping

    ArrayList tags;    // declared tags of this testcase

    
    Result currentRes; // result entry for currently running test
    ArrayList metrics; // for current result
    ArrayList bugs;    // for current result

    Suite suite;       // pointer to parent Suite

    public TestCase(Suite parent, String proc, String params) {
        // identify the testcase
        procedure = proc;
        if (params == null) {
            // rare! iff Generic TestCase throws exception
            // before even calling createResult()
            parameters = "unknown";
            name = proc;
        } else {
            parameters = params;
            name = proc + "::" + params;
        }

        suite = parent; // need for Suite-level bookkeeping
        suite.setTestCase(this); // support for Simple tests 

        run = Run.getInstance();

        tags = new ArrayList();
        metrics = new ArrayList();
        bugs = new ArrayList();

        currentRes = getResult();
        
        Log.STEP("TEST: " + name);
    }

    public String getProcedure() { return procedure; }

    public String getParameters() { return parameters; }

    public String toString() {
        return fullName(procedure, parameters);
    }

    public static String fullName(String procedure, String parameters) {
        return procedure + "::" + parameters;
    }

    // sanity check: since the testcase records "running" result in QB,
    // the test author MUST call postResult() at some point.
    // if they did not, we have an "orphaned" result that never got done.
    //
    public boolean isDone() { 
        if (currentRes != null) 
            return currentRes.isDone();
        else // if no outstanding result, we are implicitly done
            return true; 
    }
    
    public void addTag(String tag, String notes) {
        tags.add(new Tag(tag, notes));
    }

   
    public void addTag(String tag) {
        addTag(tag, null); // no notes
    }
    
    public void addTag(String [] tags) {
        for (int i = 0; i < tags.length; i++) {
            addTag(tags[i],null);
        }
    }

    public void addTags(ArrayList tags) {
        Iterator i = tags.iterator();
        while (i.hasNext()) {
            addTag(i.next().toString(), null);
        }
    }

    public boolean skipped() { return skipped; }

    /** Public API for a Generic test to manage its own Results.
     *
     *  Note: one Result object is implicitly created with the TestCase,
     *  and posted to QBDB with "running" state if excludeCase() => false
     *  i.e. if the test is supposed to start running.
     */
    public Result createResult() {
        // don't call getResult() here! the point is to force new object
        Result r = new Result(procedure, parameters);
        startResult(r);
        return r;
    }

    private void startResult(Result r) {
        try {
            r.start();
        } catch(Throwable t) {;}
        suite.addTotal();
    }

    /** Convenience wrappers to post current result with pass/fail and notes.
     */
    public void testPassed(String notes) {
        postResult(true, notes);
    }
    public void testPassed() {
        postResult(true);
    }

    public void testFailed(String notes) {
        postResult(false, notes);
    }
    public void testFailed() {
        postResult(false);
    }

    public void testSkipped(String notes) {
        postSkippedResult(getResult(), notes);
    }

    public void testSkipped() {
        postSkippedResult(getResult());
    }

    public void postResult() {
        postResult(getResult());
    }

    public void postResult(boolean pass) {
        postResult(getResult(), pass); // no notes
    }

    public void postResult(boolean pass, String notes) {
        postResult(getResult(), pass, notes);
    }

    private Result getResult() {
        if ((currentRes == null) || currentRes.isDone()) {
            Log.DEBUG("Creating new Result object!");
            currentRes = new Result(procedure, parameters);
        } 
        Log.DEBUG("Using Result object: " + currentRes);
        return currentRes;
    }

    /** Public API for Generic tests to post Results with status and notes.
     *
     *  Pass/fail status and notes parameters are optional,
     *  the test can also manipulate them directly on the Result object
     *  and then call postResult(Result r);
     */
    public void postResult(Result r, boolean pass, String notes) {
        if (notes != null) r.notes.append(notes);
        postResult(r, pass);
    }
    
    public void postResult(Result r, boolean pass) {
        r.pass = pass;
        postResult(r);
    }

    public void postSkippedResult(Result r, String notes) {
        if (notes != null) r.notes.append(notes);
        postSkippedResult(r);
    }

    public void postSkippedResult(Result r) {
        r.skipped();
        skipped = true; // for bookkeeping
        postResult(r);
    }

    public void postResult(Result r) {
        r.bugs = bugs; // inherit common bugs of TestCase
        r.tags = tags; // inherit tags

        try {
            r.post();
        } catch(Throwable t) {;}

        if (r.notes.length() > 0) {
            if (r.pass || r.skipped) 
                Log.INFO(r.notes.toString());
            else 
                Log.ERROR("FAIL: " + r.notes.toString());
        }
        if (r.bugs.size() > 0) {
            Log.INFO(r.bugDetails());
        }
        Log.STEP(r.toString());
        Log.SPACE(); // to distinguish test logs visually
        
        if (r.pass) {
            suite.addPass();
        } else if (r.skipped) {
            suite.addSkipped();
            skipped = true;
        } else {
            suite.addFail(r);
        }
        if (!r.isInit()) // didn't have a start record
            suite.addTotal(); 

        run.addResult(suite, r);

        if (!r.pass && !r.skipped && run.shouldExitOnFailure()) {
            Log.WARN("*** EARLY EXIT ON FAILURE ***");
            try {
                run.logEnd(); // statistics will be off
            } catch (Throwable t) { ; }
            System.exit(1);
        }
    }

    public void addBug(Bug bug) {
        bugs.add(bug);
    }

    public void addBug(String bug, String notes) {
        bugs.add(new Bug(bug, notes));
    }

    // add an array of Bug objects
    public void addBugs(Bug[] bugsToAdd) {
        for (int i = 0; i < bugsToAdd.length; i++) {
            bugs.add(bugsToAdd[i]);
        }
    }

    // add an array of bug IDs
    public void addBugs(String[] bugsToAdd) {
        for (int i = 0; i < bugsToAdd.length; i++) {
            bugs.add(new Bug(bugsToAdd[i]));
        }
    }

    /** Public API to post metrics with the current test result.
     *
     *  Note: metrics will be posted to QB database immediately,
     *  and not at the very end when the result itself is posted.
     */
    public void postMetric(Metric m) {
        currentRes = getResult(); // should be in DB before we post metric
        if (!currentRes.isInit()) startResult(currentRes);
        currentRes.postMetric(m);
    }

    public void postMetricGroup(Metric[] mm) {
        currentRes = getResult(); // should be in DB before we post metric
        if (!currentRes.isInit()) startResult(currentRes);
        currentRes.postMetricGroup(mm);
    }
    
    /**
     *  Test if a given class or method should be excluded,
     *  based on testcase name matching the exclusion list (substring match)
     *  or testcase's tags matching the exclusion list (exact match).
     *
     *  Creates the initial "running" result record if the test should run,
     *  or a "skipped" result record if the test shouldn't run,
     *  or no record whatsoever if the test should be silently ignored.
     */
    public boolean excludeCase() {
        boolean excluded = run.shouldSkipSilently(this);
        if (excluded) { // do not record "skipped" in QB, be silent
            skipped = true; // for bookkeeping
            return excluded;
        }

        excluded = run.shouldSkipWithRecord(this);
        if (excluded) { // record "skipped" in QB
            postSkippedResult();
        } else { // record "running" in QB
            currentRes = createResult();
        }
        return excluded;
    }

    private void postSkippedResult() {
        if (currentRes == null) 
            currentRes = getResult();
        currentRes.skipped();
        skipped = true;
        postResult(currentRes);
    }
}

/* TODO:
 * 
 * move Bug class here, private
 * change Suite::addBug to find testcase by name and call its addBug
 *
 */
