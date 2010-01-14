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

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 *    Suite is the base class for all GENERIC tests
 *    (they extend Suite or one of its subclasses).
 *    SIMPLE tests get wrapped into a Suite object by the framework.
 *
 *    Subclasses (in hctest module):
 *    HoneycombSuite adds general Honeycomb-specific methods.
 *    HoneycombLocalSuite adds general methods for local-execution
 *    tests to the Suite extension test.util.HoneycombAPISuite.
 *    HoneycombRemoteSuite adds general methods for remote-execution
 *    tests to the Suite extension test.util.HoneycombRMISuite.
 */

public class Suite implements Runnable {

    private Object testObject;
    private List children; // lower level Suites within ours
    private TestCount count; // runtime counter of pass/fail
    private TestCase currentTest; // bookkeeping
    private String suiteName;

    protected Suite parent; // back pointer to parent Suite
    protected Run run; // access to singleton for Suite and its subclasses
    
    /** Add required properties to this list before calling super.setUp() */
    public ArrayList requiredProps = new ArrayList();

    /**
     *   Create a Suite using any class, wrapping a non-Suite class
     *   in a Suite. The testObject variable in the Suite points to 
     *   the class, either the Suite itself or the non-Suite.
     */
    public static Suite newInstance(String classname) 
        throws TestAlreadyRunException,Throwable {
        String shortName = getShortName(classname);

        if(Run.getInstance().checkDeps()) {
            if(Run.getInstance().isCompleted(shortName))
                throw new TestAlreadyRunException("already run");
        }
        Suite s = null;

        Class c = Class.forName(classname);

        if (!( (c.getModifiers() & Modifier.ABSTRACT)>0) ) {
            if (Suite.class.isAssignableFrom(c)) {
                
                // excellent, a superclass! use the super's constructor
                try {
                    Constructor co = 
                        c.getDeclaredConstructor( new Class[] { } );
                    
                    s = (Suite) co.newInstance( new Object[] { } );
                } catch (Exception e) {
                    throw new HoneycombTestException(e);
                }
            } else {
                //  ick - stuff whatever it is into a generic Suite
                s = new Suite(c.newInstance());
            }
            s.suiteName = classname;
            Log.DEBUG("Suite.java: newInstance succeeded.");
            return s;
        }
        return null;


    }

    /**
     *  Create a Suite.
     */
    public Suite() {
        children = new ArrayList();
        parent = null; // i am topmost suite, unless someone calls addParent
        count = new TestCount();
        testObject = this;
        run = Run.getInstance();
    }

    /**
     *  Create a Suite around a non-Suite Object.
     */
    public Suite(Object testObject) {
        this();
        this.testObject = testObject;
    } 

    public String toString() {
        String s = "name: " + getFullClassName() + "\n";
        s += "children: " + children + "\n";
        return s;
    }

    /**
     *  Get a property (convenience call to TestRunner.getProperty()).
     */
    public String getProperty(String p) throws HoneycombTestException {
        return TestRunner.getProperty(p);
    }

    public void setTestCase(TestCase t) {
        currentTest = t;
    }

    /* Naming of testcase/testsuite entities:
     *
     * Suite: TestSuiteA
     * Class: com.sun.honeycomb.test.TestSuiteA
     * getFullClassName("TestSuiteA") => com.sun.honeycomb.test.TestSuiteA
     * getShortName("com.sun.honeycomb.test.TestSuiteA") => TestSuiteA
     *
     * Method: TestMethodOne
     * procedureName("TestMethodOne") => TestSuiteA::TestMethodOne
     * getMethodName("TestMethodOne") => TestMethodOne
     * 
     */

    /**
     *  Given test method name as input, eg TestMethodOne
     *  return SuiteName::MethodName, eg TestSuiteA::TestMethodOne 
     */
    private String procedureName(String testName) {
        return getShortName(suiteName) + "::" + testName;
    }

    /**
     *  Get name of class of this Suite or wrapped test Object.
     */
    public String getFullClassName() { 
        return testObject.getClass().getName(); 
    }

    /**
     *  strip off com.sun.honeycomb.etc.etc., return class name
     */
    public static String getShortName(String longName) {
        return longName.substring(longName.lastIndexOf(".")+1);
    }

    /**
     *  Add another Suite as a child of this one.
     */
    public void addChild(Suite child) {
        this.children.add(child);
        child.addParent(this); // back pointer
    }
    
    /** Mirror logic od addChild; if I'm your child, you are my parent
     */
    public void addParent(Suite parent) {
        this.parent = parent;
    }

    /**
     *  API for Simple tests to register their bugs, tags, metrics
     */
    public void addBug(String bug, String note) {
        currentTest.addBug(bug, note);
    }
    public void addTag(String tag, String note) {
        currentTest.addTag(tag, note);
    }
    public void addTag(String tag) {
        currentTest.addTag(tag, null);
    }
    public void postMetric(Metric m) {
        currentTest.postMetric(m);
    }
    public void postMetricGroup(Metric[] mm) {
        currentTest.postMetricGroup(mm);
    }

    /** API for Simple tests to check whether they should run or not
     */
    public boolean excludeCase() {
        return currentTest.excludeCase();
    }
    
    public void addTotal() { count.numTotal++; }

    public void addPass() { count.numPass++; }

    public void addSkipped() { count.numSkipped++; }

    public void addFail(Result r) { count.addFail(r); }

    /** API for simple tests to enforce dependencies
     *  Do NOT add code here, real work is done in Run::validateDep
     */
    public boolean validateDep(String predecessor) throws Throwable{
        // slighly redundant, since Run is a singleton, but we follow 
        // the convention of exporting methods via Suite for simple tests.
        return  run.validateDep(predecessor);
    }

    
    /**
     *  Class for summarizing all results in a Suite.
     */
    public class Summary {

	private TestCount numTests;
        private Run run; // access to singleton

        // numTests is not truly private because Summary is inner class
        public TestCount getTestCount() {
            return numTests;
        }
        public Summary() {
	    numTests = new TestCount();
            run = Run.getInstance();
        }

        public String toString() {
            return numTests.toString();
        }
    }

    /**
     *  Collect results of this Suite and all children
     */
    public void summarize(Summary s) {
        TestCount testCount = countTests();

        s.numTests.add(testCount);
        Iterator i = children.iterator();
        while (i.hasNext()) {
            Suite child  = (Suite) i.next();
            child.summarize(s);
        }
        // update run record in QB with run's exit code
        run.setExitCode(s.numTests.numFail);
        try {
            run.post();
        } catch(Throwable t) {;}
    }

    /**
     *  Return count of tests which passed, failed,
     *  or were skipped within this Suite
     */
    public TestCount countTests() {
        return countTests(false);
    }

    /**
     *  Return count of tests which passed, failed,
     *  or were skipped within this Suite and
     *  optionally all of its children (if tree==true)
     */
    public TestCount countTests(boolean tree) {
        TestCount total = count;
        if (tree) {
            Iterator j = children.iterator();
            while (j.hasNext()) {
                Suite s = (Suite) j.next();
                total.add(s.countTests(true));
            }
        }
        return total;
    }

    /**
     *  Initialize the Suite
     *  Inheritors should call super.tearDown() if they override it.
     */
    public void setUp() throws Throwable {
        //
        //  If a property is required (ie, is in requiredProps), 
        //      throw if it doesn't have a value set
        //
        if (requiredProps.size() > 0) {
            StringBuffer sb = new StringBuffer();
            int count = 0;
            Iterator li = requiredProps.listIterator();
            while (li.hasNext()) {
                String p = (String)li.next();
                String val = getProperty(p);
                if (val == null) {
                    sb.append(p).append(" ");
                    count++;
                }
                Log.DEBUG("required property " + p + " has value " + val);
            }
            if (count > 0) {
                throw new Throwable("These properties [" + sb +
                                    "] must be set for this test case");
            }
        }

        // Invoke the setUp method of the non-Suite test object, if it exists
        if (testObject != this) {
            try {
                Method m = testObject.getClass().getMethod("setUp", null);
                m.invoke(testObject, null);
            } catch (NoSuchMethodException ignored) {
                Log.INFO("No setUp method has been found");
            }
        }
    }
    
    /**
     *  Tear down anything set up by the Suite: does nothing.
     *  Inheritors should call super.tearDown() if they override it
     */
    public void tearDown() throws Throwable {
        // Invoke the setUp method of the non-Suite test object, if it exists
        if (testObject != this) {
            try {
                Method m = testObject.getClass().getMethod("tearDown", null);
                m.invoke(testObject, null);
            } catch (NoSuchMethodException ignored) {
                Log.INFO("No tearDown method has been found");
            }
        }
    }

    /** Public API: wrapper around TestCase constructor.
     *
     *  Supplies back-pointer to Suite, converts method name -> testcase name.
     *  Keeps track of the "current" testcase object.
     *  Implication: Yes, only one testcase can be active at any given time 
     *  while a test method is running! However, calling postResult()
     *  puts the testcase into "done" state, then a new one can be created.
     */
    public TestCase createTestCase(String testProc, String params, boolean addSuite) {
        if ((currentTest != null) && !currentTest.isDone() &&
            currentTest.toString().equals(TestCase.fullName(testProc,params))) {
            Log.DEBUG("Testcase exists, will reuse: " + currentTest);
        } else { // replace the old currentTest object with new one
            if (addSuite) { 
                // prepend suite name to testcase name
                currentTest = new TestCase(this, procedureName(testProc), params);
            } else {
                // use given testProc as testcase name, without suite
                currentTest = new TestCase(this, testProc, params);
            }
        }
        return currentTest;
    }

    /* Default public API: create testcase object with name that includes suite name
     * Use 3-arg version with addSuite=false if you don't want suite name.
     */
    public TestCase createTestCase(String testProc, String params) {
        return createTestCase(testProc, params, true);
    }

    /* Wrapper around TestCase constructor for Simple tests without params
     */
    public TestCase createTestCase(String testProc) {
        return createTestCase(testProc, "no-params");
    }

    /* Helper function to create and post Result upon test failure
     * used only in Suite bookkeeping, not to be called from testcases
     */
    private void postFailure(String testProc, Throwable t) {
        currentTest = createTestCase(testProc, "unknown");
        // will get existing object if currentTest is around
        currentTest.postResult(false, Result.logTrace(t)); // failure -> QB
    }
    /* Helper function to create and post skipped result,
     * used when the test failed dependencies in setUp().
     */
    private void postSkipped(String testProc, String note) {
        currentTest = createTestCase(testProc, "unknown");
        Result res = currentTest.createResult();
        res.notes.append(note);
        res.skipped();
        currentTest.postResult(res);
    }


    /** 
     *  Run all the testXXX() methods in the Suite.
     */
    public void runTests() {
        // Invoke the runTests method of the non-Suite test object, if it exists.
        // This is the best way for a class to run and record its own test cases.
        boolean autoRun = true;
        if (testObject != this) {
            try {
                Method m = testObject.getClass().getMethod("runTests", null);
                m.invoke(testObject, null);
		autoRun = false;
            } catch (NoSuchMethodException ignored) {
                Log.INFO("No tearDown method has been found");
                autoRun = true;
            } catch (Throwable t) {
                /* only happens due to programmer error in testcase code
                 * normally the test author will catch exceptions
                 */
                Throwable t2 = t.getCause();
                // the stack just goes up to the invoke, so get the cause
                if (t2 != null)
                    t = t2;
                Log.ERROR("ERROR: Test threw an exception, assuming failure");
                postFailure(getFullClassName() + "::runTests", t);
            }
        }
        if (autoRun) {
            Method [] testMethods = getTestMethods();
            for (int i = 0; i < testMethods.length; i++) {
                
                Method m = testMethods[i];
                
                // shortcut exclude, based on method name substring match
                if (run.shouldSkipAll(m.getName())) {            
                    currentTest = createTestCase(m.getName());
                    currentTest.excludeCase();
                    continue; // skipped result is posted to QB
                }
                
                if (isSimpleTest(m))
                    runSimpleTest(m);
                else 
                    runGenericTest(m);
                
                if ((currentTest != null) && !currentTest.isDone()) {
                    currentTest.postResult(false, "PROGRAMMER ERROR: "
                                           + "postResult() was never called");
                } // currentTest is null if running a collection suite 
            }
        }
    }

    /* SIMPLE TEST: 
     *
     * all bookkeeping happens here in Suite
     * the test may call addBug(), addTag(), addDep()
     * but does not manage its own results!
     */
    private void runSimpleTest(Method m) {
        currentTest = createTestCase(m.getName());
        boolean retval = false;
        String notes = null;
        
        try {
            Object o = m.invoke(testObject, null);
            retval = ((Boolean) o).booleanValue();
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                //  "InvocationTargetException is a checked exception 
                //  that wraps an exception thrown by an invoked method 
                //  or constructor."
                t = t.getCause();
                }
            Log.ERROR("ERROR: Test threw an exception, assuming failure");
            notes = Result.logTrace(t);
        }
        if (!currentTest.skipped()) // if skipped, ignore retval
            currentTest.postResult(retval, notes); // post pass/fail result to QB
    }
     
    /* GENERIC TEST: 
     *
     * manages its own results and other test runtime objects
     * almost no bookkeeping happens here in Suite
     * exception: uncaught exceptions propagate up to Suite level
     * and get logged as a generic test failure
     */
    private void runGenericTest(Method m) {
        try {
            assert isGenericTest(m);
            m.invoke(testObject, null);
        } catch (Throwable t) {
            /* only happens due to programmer error in testcase code
             * normally the test author will catch exceptions
             */
            Throwable t2 = t.getCause();
            // the stack just goes up to the invoke, so get the cause
            if (t2 != null)
                t = t2;
            Log.ERROR("ERROR: Test threw an exception, assuming failure");
            postFailure(m.getName(), t);
        } 
    }

    /**
     *  Perform setUp for the Suite; run all tests; then tearDown.
     */
    public final void run() {
        String name = getShortName(getFullClassName());

        // shortcut exclusion of an entire Suite by name
        if (run.shouldSkipAll(name)) {
            createTestCase(name).excludeCase();
            return;
        }
        Log.SUM("START SUITE: " + name);
                
        boolean ok = true;

        try {
            setUp();
        } catch(DependencyFailedException e) {
            Log.ERROR("SETUP failed, skipping tests: " + e);
            postSkipped("setUp", e.getMessage());
            ok = false;
        } catch (Throwable t) { // any other problem
	    Log.ERROR("SETUP failed, skipping tests: " + t);
            postFailure("setUp", t);
	    ok = false;
	}
	
        if (ok) runTests();
        // runs each test method, catches exceptions, posts results
       	
        try {
	    tearDown();
	} catch (Throwable t) {
	    Log.ERROR("TEARDOWN failed: " + t);
            postFailure("tearDown", t);
	}
    
	Summary s = new Summary();
        summarize(s);

        run.addCompleted(this);
        run.upNumTests(countTests()); // non-recursive call
                                                      
        Log.SUM("END SUITE: " + name + ": " + s.toString());
    }

    private Method [] getTestMethods() {
        TreeSet tests = 
            new TreeSet(new Comparator() {
                             public int compare(Object o1, Object o2) {
                                 Method m1 = (Method) o1;
                                 Method m2 = (Method) o2;
                                 return m1.getName().compareTo(m2.getName());
                             }
                             public boolean equals(Object o) {
                                 return o == this;
                             }
                         }
                     );
	
        Method [] methods = testObject.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (isSimpleTest(m)) {
                tests.add(m);
            }
            else if (isGenericTest(m)) {
                tests.add(m);
            }
            else if (mightBeTestMethod(m)) {
                // log a warning if a user just has a wrong signature
                Log.WARN("SKIPPING method " + m + ".  Maybe check the signature.");
            }
            // else ignore it
        }

        return (Method []) tests.toArray(new Method[] {});
    }

    private boolean isSimpleTest(Method m) {
        return m.getName().startsWith("test") &&
            m.getReturnType().getName().equals("boolean") &&
            m.getParameterTypes().length == 0 &&
            canInvoke(m);
    }

    private boolean isGenericTest(Method m) {
        Class [] params = m.getParameterTypes();
        return m.getName().startsWith("test") &&
            m.getReturnType().getName().equals("void") &&
            params.length == 0 &&
            canInvoke(m);
    }

    private boolean mightBeTestMethod(Method m) {
        Class [] params = m.getParameterTypes();
        return m.getName().startsWith("test") &&
            canInvoke(m);
    }

    private boolean canInvoke(Method m) {
        int mods = m.getModifiers();
        return Modifier.isPublic(mods) &&
            !Modifier.isAbstract(mods) &&
            !Modifier.isStatic(mods);
    }
}
