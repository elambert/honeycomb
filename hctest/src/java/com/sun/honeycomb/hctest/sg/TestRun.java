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



package com.sun.honeycomb.hctest.sg;

import java.io.*;
import java.lang.*;
import java.util.*;
import java.net.InetAddress;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

/**
 * This a class to describe information about a test run.
 * This is only used by the older tests.
 */
public class TestRun {
    // General Information
    public static String testRunName = "";
    public static String testRunClient = "";

    // Test runtime stats
    public static long testRunStartTime = 0;
    public static boolean testRunError = false;
    public static String testRunResultsSummary = "";

    // For logging verbosity
    public static boolean testRunLogInfoMsgs = true;
    public static boolean testRunLogDebugMsgs = false;
    public static boolean useLogClass = false;
    
    // Exit behavior
    public static boolean throwInsteadOfExit = false;
    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    public TestRun() {
    }

    public TestRun(String name) {
        testRunName = name;
    }

    public static String getString() {
        return ("TestRun=" + testRunName + "; TestRunClient=" + testRunClient +
            "; TestRunStartTime=" + testRunStartTime);
    }

    public static void testRunInit() {
        testRunClient = testRunClientIP();
        testRunStartTime = TestLibrary.msecsNow();
        printDate("\n---> Starting test " + testRunName + " at ");
    }

    public static void testRunFini() throws HoneycombTestException {
        testRunFini(!testRunError);
    }

    public static void testRunFini(boolean success)
        throws HoneycombTestException {
        String status = (success == true ? "SUCCESS" : "ERROR");
        int rc = (success == true ? 0 : 1);

        printInfo("\n" + testRunName + " Results Summary:\n\n" +
            testRunResultsSummary + "\n");

        printDate("<--- Exiting test " + testRunName + " with " +
            status + " (total execution time was " +
            (TestLibrary.msecsNow() - testRunStartTime) + " milliseconds) at ");
        if (throwInsteadOfExit) {
            throw new HoneycombTestException((success == true ? PASS : FAIL));
        }
        System.exit(rc);
    }

    public static String testRunClientIP() {
        String host = null;
        try {
            InetAddress ia = InetAddress.getLocalHost();
            host = ia.getHostAddress();
        } catch (Throwable e) {
            printError("ERROR: Couldn't get client name: " +
                e.getMessage());
        }

        if (host == null) {
            host = "localhost";
        }

        return (host);
    }

    public static void printError(String s) {
        if (useLogClass) {
            Log.ERROR(s);
        } else {
            System.out.println(s);
        }
    }
    
    public static void printInfo(String s) {
        if (useLogClass) {
            Log.INFO(s);
        } else {
            if (testRunLogInfoMsgs) {
                System.out.println(s);
            }
        }
    }
    
    public static void printDebug(String s) {
        if (useLogClass) {
            Log.DEBUG(s);
        } else {
            if (testRunLogDebugMsgs) {
                System.out.println(s);
            }
        }
    }

    public static void printDate(String s) {
        Date d = new Date();
        printInfo(s + d.toString());
    }
    
    public static void sleep(int sleepTime) {
        try {
            if (sleepTime > 0) {
                printDebug("Sleeping for " + sleepTime + " msecs");
            }
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            printDebug("Sleep Interrupted: " + e.getMessage());
        }
    }
    
    public static void done() throws HoneycombTestException {
        testRunFini();
    }

    public static void die() throws HoneycombTestException {
        testRunFini(false);
    }
}
