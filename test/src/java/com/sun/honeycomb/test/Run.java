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

import java.io.*;
import java.util.*;

/**
    Static class used by various classes in test run.
*/

public class Run {

    /* singleton is accessed via static method getInstance() 
     */
    private static Run r;
    synchronized public static Run getInstance() {
        if (r == null) {
            r = new Run();
        }
        return r;
    }
    private boolean checkDeps;
    private TestCount numTests; // Run-level counter of pass/fail
    private int runId = 0;
    private String cmdLine = null;
    private String testBedName = null;
    private String testerName = null;
    private String logsURL = null;
    private String logTag = null;
    private String buildVersion = null;
    private long startTime;
    private long endTime;
    private String startTimeQB = null;
    private String endTimeQB = null;
    private int exitCode = 0;
    private EnvContext env = null;
    private StringBuffer comments = null;

    private ArrayList classnames; // suite classes targeted to run
    private ArrayList includeCases;
    private ArrayList excludeCases;
    private ArrayList excludeForce;
    // this is a hash of Suite names -> Results because we don't want to
    // store potentially large Suite objects (tests extend Suite)
    private HashMap completedTests;
    private boolean includeAll = false; // shortcut
    private boolean doNotSkip = true; 
    private boolean exitOnFailure = false;
    
    private QB qb = null; // handle to QB repository
    private boolean noDB = false; // yes, post to QB DB
    private boolean helpMode = false;
    
    private LogArchive archiver; // access to singleton

    private Run() {
        
        numTests = new TestCount();
        startTime = System.currentTimeMillis();
        endTime = 0;

        String s;
        Log.DEBUG("Parsing test properties in Run()");

        includeCases = new ArrayList(); // populated from cmd-line args
        excludeForce = new ArrayList(); // populated from cmd-line args

        s = TestRunner.getProperty(TestConstants.PROPERTY_NOSKIP);
        if (s != null) {
            Log.DEBUG("Overriding default no-run tags");
            doNotSkip = true;
            excludeCases = new ArrayList();
        } else {
            excludeCases = new ArrayList(Arrays.asList(Tag.DEFAULT_EXCLUDE));
        }
        completedTests = new HashMap();
        
        s = TestRunner.getProperty(TestConstants.PROPERTY_EXCLUDE);
        if (s != null) {
            Log.DEBUG("Exclusion list: " + s);
            addExclude(s, excludeForce);
        }
        s = TestRunner.getProperty(TestConstants.PROPERTY_INCLUDE);
        if (s != null) {
            Log.DEBUG("Inclusion list: " + s);
            addInclude(s);
        }

        s = TestRunner.getProperty(TestConstants.PROPERTY_FAIL_EARLY);
        if (s != null) {
            exitOnFailure = true; // test will exit at first failure
        }
        
        testerName = TestRunner.getProperty(TestConstants.PROPERTY_OWNER);
        cmdLine = TestRunner.getProperty(TestConstants.PROPERTY_CMDLINE);
        
        s = TestRunner.getProperty(TestConstants.PROPERTY_BUILD);
        if (s != null) {
            buildVersion = s; // build version was set from cmd line
        }

        qb = QB.getInstance();

        s = TestRunner.getProperty(TestConstants.PROPERTY_NOQB);
        if ((s != null) && s.equals("no")) {
            qb.turnOff(); // will run without posting data to QB database
        }

        // enforce external dependencies? by default, do not enforce
        s = TestRunner.getProperty(TestConstants.PROPERTY_CHECKDEPS);
        if (s != null) checkDeps = true;
        
        env = new EnvContext();
        comments = new StringBuffer();

        archiver = LogArchive.getInstance();
    }
    
    public class EnvContext {
        String user;
        String host;
        String os;
        String javaVersion;
        String javaClassPath;

        public EnvContext() {
            user = "SYSTEM USER: " + System.getProperty("user.name");
            host = "HOST: " + Util.localHostName();
            os = "OS: " +
                System.getProperty("os.arch") + " " + 
                System.getProperty("os.name") + " " +
                System.getProperty("os.version");
            javaVersion = "JAVA VERSION: " + 
                System.getProperty("java.version");
            javaClassPath = "JAVA CLASS PATH: " +
                System.getProperty("java.class.path");
            
            // XXX: obtain testware version
        }

        // for database insertion as a single text blob
        public String toString() {
            return 
                user + "\n" + host + "\n" + os + "\n" +
                javaVersion + "\n" + javaClassPath + "\n";
        }
        
        // for logging as multiple lines
        public String[] toStrings() {
            return new String[] 
                {host + " " + user, os, javaVersion, javaClassPath};
        }
    }

    /**  Add an inclusion string for exclusion tests.
     *   Valid input: single string or comma-separated list
     */
    public void addInclude(String input) {
        StringTokenizer st = new StringTokenizer(input, ",");
        while (st.hasMoreTokens()) {
            String s = st.nextToken().trim();
            Log.DEBUG("Adding to inclusion list: " + s);
            includeCases.add(s);
            includeCases.add(s.toLowerCase());
            includeCases.add(s.toUpperCase());
            //
            // remove from exclude cases
            //
            int index;
            if ( (index=excludeCases.indexOf(s)) != -1) {
                excludeCases.remove(index);
            }
            if ( (index=excludeCases.indexOf(s.toLowerCase())) != -1) {
                excludeCases.remove(index);
            }
            if ( (index=excludeCases.indexOf(s.toUpperCase())) != -1) {
                excludeCases.remove(index);
            }
            if (s.equalsIgnoreCase("all")) {
                includeAll = true;
            }
        }
    }

    /**
     *  Register for all concerned that this is not a real test.
     */
    public void setHelpMode() {
        helpMode = true;
    }
    /**
     *  Tell if help mode has been registered.
     */
    public boolean isHelpMode() {
        return helpMode;
    }

    /**  Add an exclusion string for exclusion tests.
     *   Valid input: single string or comma-separated list
     */
    public void addExclude(String input) {
        // when called programmatically from testcase code
        addExclude(input, excludeCases); 
    }

    public void addExclude(String input, ArrayList exc) {
        StringTokenizer st = new StringTokenizer(input, ",");
        while (st.hasMoreTokens()) {
            String s = st.nextToken().trim();
            Log.DEBUG("Adding to exclusion list: " + s);
            exc.add(s);
            exc.add(s.toLowerCase());
            exc.add(s.toUpperCase());
        }
    }
    
    /** Should this testcase be skipped, based on its name and tags?
     */

    /** If this method returns true, do not post a skipped result
     */
    public boolean shouldSkipSilently(TestCase c) {
        boolean quiet = true;
        return shouldSkip(c, includeCases, excludeForce, quiet);
    }

    /** If this method returns true, DO post a skipped result
     */
    public boolean shouldSkipWithRecord(TestCase c) {
        boolean quiet = false;
        return shouldSkip(c, new ArrayList(0), excludeCases, quiet); 
    }

    private boolean shouldSkip(TestCase c, ArrayList inc, ArrayList exc, boolean quiet) {
        Log.DEBUG("Checking for exclusion - testcase: " + c +
                  (quiet ? " (quiet)" : " (with record)"));
        if (isNameNotIncluded(c.toString(), inc, quiet) &&
            areTagsNotIncluded(c, inc, quiet)) {

            Log.INFO("SKIPPING " + c + " - not in inclusion list");
            // inclusion list was given and contained neither name, nor tags
            return true; 
        }
        if (isNameExcluded(c.toString(), exc, quiet) ||
            areTagsExcluded(c, exc, quiet)) {
            // exclusion list was given and contained either tag, or name
            return true;
        }
        return false; // run this testcase
    }

    /** Should a test object of this name be skipped?
     *  Use this API when full testcase info is not available
     */
    public boolean shouldSkipAll(String c) {
        /** Since this is a shortcut, it will only check for exclusion.
         *  It is not possible to cover inclusion, because 
         *  maybe the testcase should be included based on its tags,
         *  but we do not know tags at this point.
         */
        Log.DEBUG("Checking for shortcut exclusion - testcase: " + c);

        // excluded from command-line args? quiet (print no warnings)
        boolean quiet = true;
        boolean exclude = isNameExcluded(c, excludeForce, quiet);
        if (exclude) return exclude;
        
        // excluded programmatically (by default)? not quiet (print warnings)
        return isNameExcluded(c, excludeCases, !quiet);
    }

    private boolean isNameNotIncluded(String c, ArrayList inc, boolean quiet) {
        Log.DEBUG("Checking for name-based inclusion: " + c);
        // name-based, substring match
        if (inc.size() > 0  && !includeAll &&  
            !listElementContainsString(inc, c)) {
            return true;
        }
        return false; // name is included
    }

    private boolean isNameExcluded(String c, ArrayList exc, boolean quiet) {
        Log.DEBUG("Checking for name-based exclusion: " + c);
        // name-based, substring match
        if (exc.size() > 0  && 
            listElementContainsString(exc, c)) {
            if (!quiet) Log.WARN("SKIPPING " + c + " - name in exclusion list");
            return true;
        }
        return false; // name is not excluded
    }
    
    private boolean areTagsNotIncluded(TestCase c, ArrayList inc, boolean quiet) {
        Log.DEBUG("Checking for tag-based inclusion: " + c.tags);
        // tag-based, exact match
        if (inc.size() > 0  && !includeAll) {  
            String t = arrayElementMatches(inc, c.tags);
            if (t == null) {
                return true;
            }
        }
        return false; // tags are included
    }

    private boolean areTagsExcluded(TestCase c, ArrayList exc, boolean quiet) {
        Log.DEBUG("Checking for tag-based exclusion: " + c.tags);
        // tag-based, exact match
        if (exc.size() > 0) {
            String t = arrayElementMatches(exc, c.tags);
            if (t != null) {
                if (!quiet) Log.WARN("SKIPPING " + c + " - tag [" + t + "] in exclusion list");
                return true;
            }
        }
        return false; // tags are not excluded 
    }

    /**
     ** Determines whether the given tag set should be
     ** considered active.
     ** @param tagSet The tag set in question
     ** @return True if any tag in the given tag set is in the included tag
     **         set for this Run and there is not a tag in the given tag set
     **         which is in the excluded tag set for this Run.  
     **         False, otherwise.
     **/
    public boolean isTagSetActive(ArrayList tagSet) {
        return isTagSetIncluded(tagSet) && !isTagSetExcluded(tagSet);
    }

    /**
     * This method is to be used directly by testcases to see if
     * their setUp() and tearDown() methods should run.
     *
     * From setUp and tearDown code of the testcase, call 
     * isTagSetActive with a list of applicable tags,
     * and if it returns false, return without doing setup/teardown work.
     */
    public static boolean isTagSetActive(String[] tagSet) {
        ArrayList tags = new ArrayList();
        for (int i=0; i<tagSet.length; i++) {
            tags.add(tagSet[i]);
        }
        Run _run = Run.getInstance();
        return _run.isTagSetIncluded(tags) && !_run.isTagSetExcluded(tags);
    }

    public boolean isTagSetIncluded(ArrayList tagSet) {
        return
            includeAll || 
            includeCases.size() == 0 || 
            (arrayElementMatches(tagSet, includeCases) != null);
    }

    public boolean isTagSetExcluded(ArrayList tagSet) {
        return 
            arrayElementMatches(tagSet, excludeCases) != null ||
            arrayElementMatches(tagSet, excludeForce) != null;
    }

    // do the 2 arrays have a common element? assumes string conversion
    private String arrayElementMatches(ArrayList a, ArrayList b) {
        Log.DEBUG("--> arrayElementMatches()");
        for (int i = 0; i < b.size(); i++) {
            String s = b.get(i).toString();
            Log.DEBUG("looking for '" + s + "'");
            if (a.contains(s)) {
                Log.DEBUG("Found match: " + s);
                return s;
            }
        }
        Log.DEBUG("No match");
        return null; 
    }

    private boolean listElementContainsString(List l, String fullTestCase) {
        // Log.DEBUG("Match string: " + fullTestCase + " against list: " + l); 
        Iterator i = l.iterator();
        while (i.hasNext()) {
            String exclusionSubstring = (String) i.next();
            if (fullTestCase.indexOf(exclusionSubstring) != -1) {
                return (true);
            }
        }
        return (false);
    }

    public int getId() { return runId; } // 0 if unknown
    
    // Used by ClntSrvService inorder to sync RMI Servers with the Master RMI Server Node
    public void setId(int id) { runId = id; }
    
    public void setExitCode(int val) { exitCode = val; }

    // these strings are set only when we run with a TestBed
    // ie when HoneycombSuite-based tests run, but not for unit tests
    public void setTestBed(String name) { testBedName = name; }
    public void setLogTag(String tag) { logTag = tag; }
    public String getLogTag() { return this.logTag; }
    public void setBuildVersion(String ver) { 
        if (buildVersion == null) {
            buildVersion = ver; 
        } // don't override if already set from command line
    }
    public String getBuildVersion() { 
        return (buildVersion == null) ? "Unknown" : buildVersion; 
    }
    public String getLogsURL() { return logsURL; }

    public void upNumTests(TestCount c) { numTests.add(c); }

    public int gotFailure() { return numTests.gotFailure(); }

    public void addComment(String c) {
        comments.append(Util.currentTimeStamp() + c); 
    }

    public boolean shouldExitOnFailure() { return exitOnFailure; }

    /** Seed the random number generator in the local JVM
     */
    private void setRandomSeed() {
        String seed = TestRunner.getProperty(TestConstants.PROPERTY_SEED);
        try {
            if (seed != null) 
                RandomUtil.initRandom(seed);
            else 
                RandomUtil.initRandom();
            Log.INFO("RANDOM SEED IN LOCAL JVM: " + RandomUtil.getSeed());
        } catch (HoneycombTestException e) {
            Log.WARN("Failed to set random seed to [" + seed + "]: " + e);
        }
    }

    /**
     *  Log start of run wherever applicable.
     */
    public void logStart() throws Throwable {
        
        start(); // QB run-start entry

        // set up archiving of test logs into a dir named after runID
        if (runId != 0) {
            String logDir = Integer.toString(runId);
            archiver.setLogDir(logDir);
            logsURL = logDir; // web server prepends this URL with host:/path
        }

        ArrayList lines = new ArrayList();
        lines.add("START RUN_ID=" + runId);
        lines.add("COMMAND LINE: " + cmdLine);
        lines.addAll(Arrays.asList(env.toStrings())); // log run env
        Log.SUM((String[]) lines.toArray(new String[0]));

        setRandomSeed();
    }

    /**
     *  Log end of run wherever applicable.
     */
    public void logEnd() throws Throwable {

        endTime = System.currentTimeMillis();

        Log.SPACE();
        Log.SUM("END RUN_ID=" + runId 
                + " RUNTIME=" + Util.formatTime(endTime - startTime)
                + " " + numTests.toString());
        
        if (runId != 0) {
            Log.INFO("Online results at " + qb.getRunResultsURL() + runId);
        } else {
            Log.INFO("No online results or archived logs (quiet run)");
        }
        
        // the above should be the last log message,
        // since we are archiving logs now!!!
        archiver.archiveLogs();

        post(); // QB run-end entry
    }



    /** post run-start record to QB database */
    public void start() throws Throwable {
        if (qb.isOff()) {
            return;
        }
        File runStart = qb.dataFile("run");
        writeRecord(new BufferedWriter(new FileWriter(runStart)));
        runId = qb.post("run", runStart);
    }
    
    /** post run-end record to QB database */
    public void post() throws Throwable {
        if (qb.isOff()) {
            return;
        }
        File runEnd = qb.dataFile("run");
        writeRecord(new BufferedWriter(new FileWriter(runEnd)));
        runId = qb.post("run", runEnd);
    }
    
    /** write test run data (eg: to a temp file) */
    private void writeRecord(BufferedWriter out) throws Throwable {
        if (runId != 0) out.write("QB.id: " + runId + "\n"); // 0 is illegal
        if (!cmdLine.equals("")) out.write("QB.command: " + cmdLine + "\n");
        if (testBedName != null) out.write("QB.bed: " + testBedName + "\n");
        if (testerName != null) out.write("QB.tester: " + testerName + "\n");
        if (logsURL != null) out.write("QB.logs_url: " + logsURL + "\n");
        if (logTag != null) out.write("QB.logtag: " + logTag + "\n");
        if (startTimeQB != null) out.write("QB.start_time: " + startTimeQB + "\n");
        if (endTimeQB != null) out.write("QB.end_time: " + endTimeQB + "\n");
        out.write("QB.exitcode: " + exitCode + "\n"); // 0 is legal
        if (env.toString().length() != 0) out.write("QB.env: " + env.toString() + "\n");
        if (comments != null) out.write("QB.comments: " + comments + "\n");
        out.close();
    }
    
    //
    //
    // Dependency checking universe starts here
    //
    //
    public boolean checkDeps() {
        return checkDeps;
    }

    public void addClassNames(ArrayList newNames) {
        classnames = new ArrayList((ArrayList)newNames.clone());

    }

    /** validateDep() is meant to be called only from tests' setUp() methods
     */
    public boolean validateDep(String predecessor) 
        throws Throwable,HoneycombTestException {

        try {  // enforce call from setUp() only
            throw new Throwable("inspect me");
        }
        catch (Throwable t) {
            StackTraceElement [] trace = t.getStackTrace();
            String testMethod = trace[2].getMethodName(); // trace[1] is validateDep, trace[2] is the caller
            if (!testMethod.equals("setUp")) {
                throw new HoneycombTestException("Illegal call to validateDep from a non-setUp() method: " + testMethod);
            }
        } 
        
        if(!checkDeps) {
            Log.INFO("Dependencies aren't enforced");
            return true;
        }

        if(!isCompleted(predecessor)) {
            //            run suite named prececessor; (or throw on no such suite);
            Iterator i = classnames.iterator();
            String realClassName = null;
            while (i.hasNext()) {
                String classname = i.next().toString();
                Log.DEBUG("Comparing classnames: " + classname + " <-> " + predecessor);
                if(Suite.getShortName(classname).equals(predecessor)){
                    realClassName=classname;
                }
            }
            if(null==realClassName) {
                Log.ERROR("Can't validate dependency on predecessor, it doesn't exist:" + predecessor);
                return false;
            }

            Log.INFO("Dependency requires that we run suite: " + predecessor);
            try {
                Suite s = Suite.newInstance(realClassName);
                s.run();
            } catch (TestAlreadyRunException e) {
                Log.INFO("Suite "+realClassName+" was already run because of another dependency, skipping.");
            }

            Log.DEBUG("Finished suite triggered by dependency: " + predecessor);
        }
        return(didPass(predecessor));

    }

    /* Keep track of completed (ie already run) suites
     * Used for dependencies (did my predecessor run?)
     */
    public ArrayList addCompleted(Suite s){
        String suiteName = Suite.getShortName(s.getClass().getName());
        ArrayList suiteResults=(ArrayList)completedTests.get(suiteName);
        if(null==suiteResults) {
            suiteResults = new ArrayList();
            completedTests.put(suiteName,suiteResults);            
        }
        return suiteResults;
    }

    /* Keep track of all results generated when suites ran
     * Used for dependencies (did my predecessor pass?)
     */
    public void addResult(Suite s, Result r) {
        if(!checkDeps) 
            return; // nothing to do, skip the recursion

        ArrayList suiteResults = addCompleted(s);
        suiteResults.add(r);
        
        if (s.parent == null) return; // base case
        
        // Recursive because suites can contain other suites hierarchically.
        // Only the lowest-level testcases generate results,
        // but the dependency can be declared on a higher-level suite,
        // so we must associate each result with all suites up above it.
        //
        addResult(s.parent, r); 
    }
    

    public boolean isCompleted(String suiteName) {
        if(!checkDeps)
            return true;
        
        if (getResults(suiteName) == null) {
            Log.DEBUG("Suite has not been run: " + suiteName);
            return false;
        } else {
            return true;
        }
    }

    public ArrayList getResults(String suiteName) {
        ArrayList suiteResults=(ArrayList)completedTests.get(suiteName);
        return suiteResults;
    }
   
    //
    // Note that we also have a binary "was it run"
    // for each suite object. So some of these 
    // lists we're checking will be empty.
    //
    public boolean didPass(String suiteName) {
        if(!checkDeps)
            return true;

        ArrayList suiteResults = getResults(suiteName);
        if(null==suiteResults) {
            Log.DEBUG("Suite has no results: " + suiteName);
            return false;
        } else {
            Iterator i = suiteResults.iterator();
            while (i.hasNext()) {
                Result res = (Result)i.next();
                // check pass and skipped for each result
                // skipped is not a failure
                if(!(res.pass || res.skipped)) {
                    Log.DEBUG("Suite has a failed result: " + suiteName + " -> " + res);
                    return false;
                }
            }
            return true;
        }
    }
   
}


