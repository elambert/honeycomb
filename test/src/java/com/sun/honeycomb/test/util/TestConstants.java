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



package com.sun.honeycomb.test.util;

import java.util.Properties;

/**
 *  Common test properties
 */

public class TestConstants 
{
    public NameValue[] getPropUsage() { return propertyUsage; }

    protected NameValue[] propertyUsage;

    public final static String PROPERTY_EXCLUDE =  "exclude";
    public final static String PROPERTY_INCLUDE =  "include";
    public final static String PROPERTY_MOAT = "explore";
    public final static String PROPERTY_NOSKIP = "noskip"; 
    public final static String PROPERTY_CHECKDEPS = "checkdeps";
    public final static String PROPERTY_OWNER = "owner";
    public final static String PROPERTY_NOQB = "qb";
    public final static String PROPERTY_BUILD = "build"; 
    public final static String PROPERTY_CMDLINE = "cmdline";
    public final static String PROPERTY_SEED = "rseed";
    public static final String PROPERTY_FAIL_EARLY = "failearly";

    public TestConstants() {
        
        NameValue[] propHelp = {
            new NameValue(PROPERTY_EXCLUDE, "Exclude specific testcases from the base set, "+
                          "ie run all tests except for these. The tests can be specified by "+
                          "testcase class name (substring matching) and/or by tag, as a "+
                          "comma-separated list (eg: " + PROPERTY_EXCLUDE + "=NEGATIVE,MyBadTest,"+
                          "UnfinishedTest)."),

            new NameValue(PROPERTY_INCLUDE, "Include only these testcases in the base set, "+
                          "ie run only these tests. Syntax just like above (eg: " 
                          + PROPERTY_EXCLUDE + "=POSITIVE,MyGoodTest). The exclude rule can "+
                          "be applied on top of include rule, if desired."),

            new NameValue(PROPERTY_MOAT, "Automatically discover the base set of testcases. "+
                          "Turned off by default, so a mistake in the command line will not "+
                          "result in wasted test runs. Exclude and include rules can be applied"+
                          " to the auto-discovered base set of testcases. (" + PROPERTY_MOAT +
                          "=yes). The alternative is to specify testcase classnames explicitly "+
                          "on the command line."),

            new NameValue(PROPERTY_NOSKIP, "Ignore default exclusion rules. Tests with certain "+
                          "tags are never run by default, unless you set this option (" +
                          PROPERTY_NOSKIP + "=yes)."),

            new NameValue(PROPERTY_CHECKDEPS, "Check external dependencies of testcases which "+
                          "declare them. "),

            new NameValue(PROPERTY_OWNER, "Who is executing this test (you!), by default is set"+
                          " to the system username. The name is recorded in the test results "+
                          "database for ease of querying. If the test is executed by root or "+
                          "another system account, you'll want to use this option (eg: " + 
                          PROPERTY_OWNER + "=JohnDoe)."),

            new NameValue(PROPERTY_SEED, "Set random seed to reproduce a prior test run. " +
                          "Use the seed value logged in the previous test run, it has a special format."),

            new NameValue(PROPERTY_NOQB, "Turn off database interaction. By default, test results "+
                          "are recorded in QB database. This may be undesirable in experimental test "+
                          "runs, or if the database is down. (" + PROPERTY_NOQB + "=no)."),

            new NameValue(PROPERTY_BUILD, "Build version of the software being tested. By default, "+
                          "obtained from the hive. (eg: " + PROPERTY_BUILD + "=dev_withMyGreatFix)."),

            new NameValue(PROPERTY_FAIL_EARLY, "Toggle the exit-on-failure mode. By default, " +
                          "the test decides when to exit. It may report multiple failures without exiting. " +
                          "If you want the test to exit on first failure, set this property."),

            new NameValue(PROPERTY_CMDLINE, "Command line to reproduce this test run. By default, it "+
                          "is set by the runtest script. If you are not using the runtest script, you "+
                          "can set the property explicitly (eg: " + PROPERTY_CMDLINE + 
                          "=my_special_wrapper_script -n 1).")
        };

        propertyUsage = propHelp;
    }
    
}
