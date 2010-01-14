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

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.*;
import java.io.*;
import java.net.URL;
import java.net.JarURLConnection;
import java.util.jar.*;


/**
 *  TestRunner parses input and runs the specified tests;
 *  tests are specified by their full class name.
 *  Each class becomes a Suite, and its test methods get run.

 Runs test methods in special Test Objects.
 Test Objects are those which
   a) Have a zero-argument constructor, and
   b) Have 1 or more test methods
   There are two types of test methods: Simple and Generic
   Simple test methods conform to the following syntax.

      public boolean testXXX() [throws Throwable] {...}

      \"XXX\" can be any valid string and denotes the name
      of the test.  The return value singles whether the
      test passed (true) or failed (false).  An optional
      \"throws ...\" specification may be included.  Any
      exception thrown signifies that the test has failed.

      Generic tests methods are specified as follows.

      public void testXXX(Suite s) [throws Throwable] {...}

      In this case, the test method is free to add any
      number of results to the Suite within which it is
      running.

      Tests in a Test Object will run in alphabetical order
      regardless of being Simple or Generic methods.

 */

public class TestRunner {

    private int logLevel = Log.DEBUG_LEVEL;
    private ArrayList classnames = new ArrayList();
    private boolean help = false;

    /** Generic test properties 
     */

    // This is used to specify multiple properties after one ctx argument
    private static final String MULTIPLE_PROPERTY_DELIMITER = ":";

    // Shared properties, accessed by TestBed, Run, Suite
    private static Properties props;
    static {
        props = new Properties();
        setDefaultProperties();
    }

    public static String getProperty(String name) {
        return (String) props.getProperty(name);
    }

    public static void setProperty(String name, String value) {
        props.setProperty(name, value);
    }

    /* Default values of generic test properties
     * can be overwritten from command line args
     */
    private static void setDefaultProperties() {

	props.setProperty(TestConstants.PROPERTY_OWNER, System.getProperty("user.name"));

	// cmdline is what you'd run to reproduce this test run.
	// it's set for you in testrun script. if you don't use it,
	// you can supply cmd-line arg: -ctx cmdline=<cmd>
	String cmdLine = System.getProperty("cmdline");
	if (cmdLine == null) 
            cmdLine = "UNKNOWN";
	props.setProperty(TestConstants.PROPERTY_CMDLINE, cmdLine);

	// add more defaults here as needed
    }

    private void loadProperties(String file) {
      try {
        FileInputStream fis = new FileInputStream(file);
        props.load(fis);
      } catch (IOException e) {
        Log.ERROR("file read: " + file);
        System.exit(1);
      }
    }
    private void setProperties(String spec) {
      if (spec.indexOf(MULTIPLE_PROPERTY_DELIMITER) != -1) {
        String ss[] = spec.split(MULTIPLE_PROPERTY_DELIMITER);
        for (int i=0; i<ss.length; i++)
          setProperty(ss[i]);
      } else {
        setProperty(spec);
      }
    }
    private void setProperty(String spec) {
      String name = null;
      String value = "";
      int ei = spec.indexOf('=');
      if (ei == -1) {
        name = spec;
      } else {
        name = spec.substring(0, ei);
        if (ei < spec.length()-1)
          value = spec.substring(ei + 1);
      }
      props.setProperty(name, value);
    }

    private void parseArgs(String[] argv) {

        ArrayList errors = new ArrayList();

        LongOpt [] longOpts = new LongOpt[] {
          new LongOpt("log-level", LongOpt.REQUIRED_ARGUMENT, null, 'L'),
          new LongOpt("ctx", LongOpt.REQUIRED_ARGUMENT, null, 'x'),
          new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h')
        };

        Getopt g = new Getopt("TestRunner", argv, "x:L:h", longOpts, true);
        int c;
        while ((c = g.getopt()) != -1) {
            switch (c) {
                case 'x':
                    String ctx = g.getOptarg();
                    if (ctx.startsWith("@")) {
                        loadProperties(ctx.substring(1));
                    } else {
                        setProperties(ctx);
                    }
                    break;

                case 'L':
                    String log_level = g.getOptarg();
                    int level = Log.parseLevel(log_level);
                    if (level == -1) {
                        errors.add("invalid log level: " + log_level);
                    } else {
                        logLevel = level;
                        Log.global.level = Log.DEFAULT_LEVEL = level;
                    }
                    break;

                case 'h':
                    help = true;
                    break;

                case 1: // A non-option argument was passed early.
                    classnames.add(g.getOptarg());
                    break;

                default: // It's a bug if we get here.
                    assert false;
            }
        }
        for (int i = g.getOptind(); i < argv.length; i++) {
            classnames.add(argv[i]);
        }

        if (help) {
            Run.getInstance().setHelpMode();
            System.out.println(getUsage());
            System.exit(0);
        }

        if (errors.size() > 0) {
            System.err.println(getUsage());
            for (int i=0; i<errors.size(); i++)
                System.err.println(errors.get(i).toString());
            System.exit(1);
        }

        if (classnames.size() == 0 && (getProperty(TestConstants.PROPERTY_MOAT) != null )) {
            classnames = motherOfAllTests();
            if (Run.getInstance().checkDeps()) {
                Run.getInstance().addClassNames(classnames);
            }
        } else if (Run.getInstance().checkDeps()) {
            //
            // don't wanna run the moat more than necessary.
            //
            Run.getInstance().addClassNames(motherOfAllTests());
        }

    }
    
    
    /** MOAT: Mother Of All Tests
     *  Find all testcase classes present in the loaded jar file.
     */
    private ArrayList motherOfAllTests() {

        Log.INFO("Auto-discovering testcases...");

        ArrayList moat = new ArrayList();

        //
        // If we are dealing with hctest module (common case),
        // use the hard-coded names for jarfile and testdir.
        // In other cases (eg: running examples from test module),
        // set env variable EXTRA_JVM_ARGS, which is passed by runtest.
        //
        // SET EXTRA_JVM_ARGS="-Dtestjar=honeycomb-test.jar -Dtestdir=examples"
        //
        String jarName = System.getProperty("testjar");
        if (jarName == null) 
            jarName = "honeycomb-hctest.jar";
        String testDir = System.getProperty("testdir");
        if (testDir == null) 
            testDir = "cases";
        // all QA tests extend Suite class => OK to hard-code
        String suiteClassPath = "com.sun.honeycomb.test.Suite";

        Class suiteClass = null;
        try {
            suiteClass = Class.forName(suiteClassPath);
            Log.DEBUG("Suite class: " + suiteClass.getName());
        } catch (Exception e) {
            System.err.println("Failed to locate Suite class: " + e);
            System.exit(1); // fatal because can't locate Suite subclasses
        }

        String jarPath = null; // need absolute path to jarName
        String pkgs = System.getProperty("java.class.path");
        StringTokenizer st = new StringTokenizer(pkgs, ":");
        while (st.hasMoreTokens()) {
            String sub = st.nextToken();
            if (sub.endsWith(jarName)) {
                jarPath = sub;
                break;
            }
        }
        if (jarPath == null) {
            System.err.println("Failed to locate " + jarName);
            System.exit(1);
        }
        File jarFile = new File(jarPath);
        jarPath = jarFile.getAbsolutePath(); 
        
        // connect to the jar file
        JarFile jar = null;
        try {
            URL url = new URL("jar:file:" + jarPath + "!/");
            JarURLConnection conn = (JarURLConnection)url.openConnection();
            jar = conn.getJarFile();
        } catch (Exception ex) {
            System.err.println("FATAL: Could not connect to jar file " + jarPath);
        }

        // read entries from the jar file
        Enumeration e = jar.entries();
        while (e.hasMoreElements()) { // what's in the jar file
            JarEntry entry = (JarEntry)e.nextElement();
            String entryName = entry.getName();
            if (!entryName.endsWith(".class")) 
                continue; // not a class
            if (entryName.indexOf(testDir) == -1) 
                continue; // not a testcase 
            String className = entryName.substring(0, entryName.length()-6);
            if (className.startsWith("/"))
                className = className.substring(1);
            className = className.replace('/', '.');
            Log.DEBUG("Class name: " + className);
            try { // check that the class is loaded, and extends Suite
                Class c = Class.forName(className);
                if (!suiteClass.isAssignableFrom(c)) 
                    continue; // not a suite
                moat.add(className);
                Log.DEBUG("Added to MOAT: " + className);
            } catch (ClassNotFoundException ex) {
                System.err.println("Could not locate class: " + className);
            } catch (NoClassDefFoundError ex) {
                System.err.println("Could not locate class def: " + className);
            } 
        }
        
        return moat;
    }


    private int runTests() throws Throwable {
        //
        // XXX we could set up a way to check parameter req'ts
        //      of all suites before starting the run
        //

        if (classnames.size() == 0) {
            System.err.println("\n--NO TEST CLASSES DEFINED\n");
            System.err.println(getUsage());
            System.exit(1);
        }

        Run run = Run.getInstance(); // access to singleton

        run.logStart(); // record generic run environment
        
        //
        //  run the Suite(s)
        //
        Iterator it = classnames.iterator();
        while (it.hasNext()) {
            String classname = it.next().toString();
            Suite s = Suite.newInstance(classname);
            //
            // if this is instantiatable - some of these
            // coudl be abstract classes
            //
            if (s != null) {
                Log.DEBUG("running suite: " + classname);
                s.run();
            }
        }
        
        run.logEnd(); // record this run's statistics

        tearDown();

        return run.gotFailure();
    }

    public TestRunner(String[] argv) {
        try {
            parseArgs(argv);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static ArrayList testHooks = new ArrayList();

    public static void registerTestHook(TestHook th) {
        testHooks.add(th);
    }
    private void tearDown() {
        // call any hooks that other classes have registered
        for (int i=0; i<testHooks.size(); i++) {
            TestHook th = (TestHook) testHooks.get(i);
            th.tearDown();
        }
    }

    /* Java magic to guarantee cleanup when JVM exits
     */
    private void setupShutdownHook() {
        try {
            Method hookMethod = 
                Runtime.class.getMethod("addShutdownHook",
                                        new Class[]{Thread.class});
            hookMethod.invoke(Runtime.getRuntime(),
                              new Object[]{createShutdownHook()});
            Log.DEBUG("Added shutdown hook to call TestRunner.tearDown() on exit");
        } catch (Exception e) {
            Log.WARN("Failed to add shutdown hook for TestRunner. " +
                     "Will not call TestRunner.tearDown() on exit");
        }
    }

    private Runnable createShutdownHook() {
        return new Thread() {
            public void run() { 
                tearDown(); 
            }
        };
    }

    public static void main(String[] argv) 
        throws Throwable
    {
        TestRunner runner = new TestRunner(argv);
        int ret = runner.runTests();

        System.exit(ret);
    }



    private String getUsage() {

      StringBuffer sb = new StringBuffer();
      sb.append("NAME\n");
      sb.append("       TestRunner - Run Tests\n");
      sb.append("\n");
      sb.append("SYNOPSIS\n");
      sb.append("       java com.sun.honeycomb.test.TestRunner [OPTIONS] <classname>\n");
      sb.append("\n");
      sb.append("DESCRIPTION\n");
      sb.append("\n");
      sb.append("\n");
      sb.append("OPTIONS\n");
      sb.append("\n");
      sb.append("       -x, --ctx <name>[=<value>][" + 
                                    MULTIPLE_PROPERTY_DELIMITER + 
                                    "<name>[=<value>]]\n");
      sb.append("       -x, --ctx @<file.properties>\n");
      sb.append("              Add a property (or comma-separated list of\n");
      sb.append("              properties) to the test run context.\n");
      sb.append("              If a @<file>.properties spec is provided\n");
      sb.append("              then the file contents will be interpreted\n");
      sb.append("              as a standard Java properties file.\n");
      sb.append("\n");
      sb.append("              Property names are defined in\n");
      sb.append("                  test/util/HoneycombTestConstants.java\n");
      sb.append("              Commonly-used/required properties for tests\n");
      sb.append("              involving clusters:\n");
      sb.append("                dataVIP adminVIP spIP\n");
      sb.append("              ('cluster' will give -data and -admin)\n");
      sb.append("\n");
      sb.append("       -L, --log-level <debug|info|warn|error|quiet>\n");
      sb.append("              Set the output log level.  Default 'debug'.\n");
      sb.append("\n");
      sb.append("       -h, --help\n");
      sb.append("              Print this message and messages from any help()\n");
      sb.append("              methods in specified Suites.\n");

      sb.append("\n\nExample command line for running regression tests:\n");
      sb.append("  ./bin/runtest -ctx include=regression:dataVIP=dev314-data.sfbay.sun.com:"+
                "adminVIP=dev314-admin:spIP=dev314-cheat:explore\n");
      sb.append("  Load tests:\n");
      sb.append("  ./bin/runtest com.sun.honeycomb.hctest.cases.storepatterns.ContinuousStore "+
                "-ctx processes=3:minsize=1M:maxsize=100M:dataVIP=dev314-data:adminVIP=dev314-admin"+
                "clients=cl41,cl42,cl43,cl44,cl45,cl46,cl47:time=-1:"+
                "include=distributed:spIP:dev314-cheat\n");
      

      // get help on test and hctest properties, if loaded
      sb.append("\n          Test Properties: \n\n");
      String[] propClasses = { 
          "com.sun.honeycomb.test.util.TestConstants",
          "com.sun.honeycomb.test.Tag",
          "com.sun.honeycomb.hctest.HoneycombTag",
          "com.sun.honeycomb.hctest.util.HoneycombTestConstants"
          };
      for (int j = 0; j < propClasses.length; j++) {
          try {
              Class propClass = Class.forName(propClasses[j]);
              TestConstants prps = (TestConstants) propClass.newInstance();
              NameValue[] propHelp = prps.getPropUsage();
              sb.append("\n");
              for (int i = 0; i < propHelp.length; i++) {
                  sb.append("          " + propHelp[i].name + "\n");
                  sb.append("            " + propHelp[i].value + "\n");
              }
          } catch (Exception e) {
              Log.DEBUG("Class " + propClasses[j] + " not loaded? " + e);
          }
      }
      

      //
      //  see if per-class help is desired/possible
      //
      if (!help  ||  classnames.size() == 0)
          return sb.toString();

      sb.append("\n==== Test classes help:\n\n");

      Iterator it = classnames.iterator();

      while (it.hasNext()) {

          String classname = it.next().toString();

          //
          //  instantiate the class if it's a Suite
          //
          Suite s = null;
          try {
              s = Suite.newInstance(classname);
          } catch (java.lang.ClassNotFoundException e) {
              sb.append(classname).append(": class not found\n");
              continue;
          } catch (java.lang.InstantiationException e) {
              sb.append(classname).append(": can't instantiate class\n");
              continue;
          } catch (Throwable t) {
              sb.append(classname).append(": unexpected exception: ");
              sb.append(t.toString()).append("\n");
              continue;
          }

          //
          //  if this is instantiatable - some of these
          //  could be abstract classes
          //
          if (s == null) {
              sb.append(classname).append(" - not a Suite, skipping\n");
              continue;
          }

          //
          //  find the help() method if it exists
          //
          Method[] methods = s.getClass().getMethods();
          boolean foundHelp = false;
          for (int i=0; i<methods.length; i++) {

              Method m = methods[i];
              if (!m.getName().equals("help"))
                  continue;
              Class[] params = m.getParameterTypes();
              int mods = m.getModifiers();
              if (m.getReturnType().getName().equals("java.lang.String")  &&
                    params.length == 0  &&
                    Modifier.isPublic(mods)  &&
                    !Modifier.isAbstract(mods)  &&
                    !Modifier.isStatic(mods)) {
                  foundHelp = true;
                  try {
                      Object o = m.invoke(s, null);
                      sb.append(classname).append(":\n").append(o.toString());
                      if (sb.charAt(sb.length()-1) != '\n')
                          sb.append("\n");
                  } catch (Exception e) {
                      sb.append(classname).append(" - getting help: ");
                      sb.append(e.getMessage()).append("\n");
                  }
                  break;
              }
          }
          if (!foundHelp)
              sb.append(classname).append(" - no help() method\n");
      }
      return sb.toString();
    }
}
