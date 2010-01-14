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



package com.sun.honeycomb.hctest.cases;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.test.Tag;

import com.sun.honeycomb.client.*;

import java.util.ArrayList;

//
// The decision was made to disable the ObjectArchive tests.  There is
// no plan to give that interface to a customer.  For now, only the
// NameValueObjectArchive tests are enabled.  The ObjectArchive tests
// are disabled and some are known to fail.
//
// Note that you should be careful to verify against the emulator
// when changing this test.
//

/**
 * Test that the client library handles bad arguments.
 */
public class ProtocolBadArgs extends HoneycombLocalSuite {

    private ObjectArchive oa;
    private NameValueObjectArchive nvoa;

    private static final String HOSTNOTNULL = "host must not be null";
    private static final String NOACTIVEHOST = "no active host";
    private static final String IOEXCEPTION = "request failed with IOException";

    private void addTags() {
        addTag(Tag.REGRESSION);
        addTag(Tag.NEGATIVE);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
    }


    public ProtocolBadArgs() {
        super();
    }

    public String help() {
        return(
            "\tTest that the client library handles bad arguments.\n"
            );
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Test if a null server arg is handled.
     */
    public boolean testNullServer() {
        addTags();
        addTag(Tag.NORUN,"ObjectArchive isn't public interface -> not qual'ed");
        if (excludeCase()) return false;
        addBug("6303941",
            "ObjectArchive Constructor allows NULL host");
        addBug("6187443",
            "NPE if pass client library null for servers parameter");

        try {
            oa = new ObjectArchive(null);
        } catch (Throwable t) {
            if (Util.exceptionMatches(HOSTNOTNULL, t)) {
                Log.INFO("ObjectArchive(null) got expected exception: " + t);
                return (true);
            } else {
                Log.ERROR("ObjectArchive(null) got unexpected exception: " + t);
                return (false);
            }
        }

        Log.ERROR("ObjectArchive(null) should have failed but it succeeded");
        return (false);
    }

    /**
     * Test if a negative port arg is handled.
     */
    public boolean testNegativePort() {
        addTags();
        addTag(Tag.NORUN,"ObjectArchive isn't public interface -> not qual'ed");
        // addTag(Tag.NORUN, "Diff between emulator and cluster");
        if (excludeCase()) return false;
        try {
            oa = new ObjectArchive(testBed.dataVIP, -1);
            oa.storeObject(null);
        } catch (Throwable t) {
            // IOEXCEPTION is the exception on a cluster.
            // The emulator says:  Request failed on node  with status 501
            if (Util.exceptionMatches(IOEXCEPTION, t)) {
                Log.INFO("storeObject(null) using bad port got expected " +
                    "exception: " + t);
                return (true);
            } else {
                Log.ERROR("storeObject(null) using bad port got unexpected " +
                    "exception: " + t);
                return (false);
            }
        }

        Log.ERROR("ObjectArchive(server, -1) should have failed but it " +
            "succeeded, even when doing a store!");
        return (false);
    }

    /**
     * Test if a null server and valid port arg is handled.
     */
    public boolean testNullServerAndPort() {
        addBug("6303941",
            "ObjectArchive Constructor allows NULL host");
        addBug("6187443",
            "NPE if pass client library null for servers parameter");
        addTags();
        addTag(Tag.NORUN,"ObjectArchive isn't public interface -> not qual'ed");
        if (excludeCase()) return false;
        try {
            oa = new ObjectArchive(null, 8090);
        } catch (Throwable t) {
            if (Util.exceptionMatches(HOSTNOTNULL, t)) {
                Log.INFO("ObjectArchive(null, 8090) got expected " +
                    "exception: " + t);
                return (true);
            } else {
                Log.ERROR("ObjectArchive(null, 8090) got unexpected " +
                    "exception: " + t);
                return (false);
            }
        }

        Log.ERROR("ObjectArchive(null, 8090) should have failed but it " +
            "succeeded");
        return (false);
    }

    /**
     * Test if a null server arg is handled in the basic java API.
     */
    public boolean testNullServerNVOA() {
        addBug("6187443",
            "NPE if pass client library null for servers parameter");
        addTags();
        if (excludeCase()) return false;
        try {
            nvoa = new NameValueObjectArchive(null);
        } catch (Throwable t) {
            if (Util.exceptionMatches(HOSTNOTNULL, t)) {
                Log.INFO("NameValueObjectArchive(null) got expected " +
                    "exception: " + t);
                return (true);
            } else {
                Log.ERROR("NameValueObjectArchive(null) got unexpected " +
                    "exception: " + t);
                return (false);
            }
        }

        Log.ERROR("NameValueObjectArchive(null) should have failed but it " +
            "succeeded");
        return (false);
    }

    /**
     * Test if a negative port arg is handled in the basic java API.
     */
    public boolean testNegativePortNVOA() {
        addBug("6513820", "NameValueObjectArchive doesn't catch use of " +
            "negative port, should throw IllegalArgException");
        addTags();
        addTag(Tag.FAILING,"Bug 6513820");
        if (excludeCase()) return false;
        try {
            nvoa = new NameValueObjectArchive(testBed.dataVIP, -1);
            nvoa.storeObject(null);
        } catch (Throwable t) {
            if (Util.exceptionMatches(NOACTIVEHOST, t)) {
                Log.INFO("storeObject(null) using bad port got expected " +
                    "exception: " + t);
                return (true);
            } else {
                Log.ERROR("storeObject(null) using bad port got unexpected " +
                    "exception: " + t);
                return (false);
            }
        }

        Log.ERROR("NameValueObjectArchive(server, -1) should have failed " +
            "but it succeeded, even when doing a store!");
        return (false);
    }

    /**
     * Test if a null server and valid port arg is handled in the basic 
     * java API.
     */
    public boolean testNullServerAndPortNVOA() {
        addBug("6187443",
            "NPE if pass client library null for servers parameter");
        addTags();
        if (excludeCase()) return false;
        try {
            nvoa = new NameValueObjectArchive(null, 8090);
        } catch (Throwable t) {
            if (Util.exceptionMatches(HOSTNOTNULL, t)) {
                Log.INFO("NameValueObjectArchive(null, 8090) got expected " +
                    "exception: " + t);
                return (true);
            } else {
                Log.ERROR("NameValueObjectArchive(null, 8090) got unexpected " +
                    "exception: " + t);
                return (false);
            }
        }

        Log.ERROR("NameValueObjectArchive(null, 8090) should have failed " +
            " but it succeeded");
        return (false);
    }

    /**
     * Test if a null data stream is handled.
     * This should actually pass?
     */
    public boolean testStoreObjectNull() {
        addBug("6187947",
            "ObjectArchive.store(null) causes warnings in the log");
        addTag(Tag.NORUN,"ObjectArchive isn't public interface -> not qual'ed");
        addTags();
        if (excludeCase()) return false;
        SystemRecord sr;

        try {
            oa = new ObjectArchive(testBed.dataVIP);
            sr = oa.storeObject(null);
        } catch (Throwable t) {
            Log.ERROR("storeObject(null) got unexpected exception: " + t);
            return (false);
        }

        Log.INFO("storeObject(null) succeeded; but check for errors in log!");
        Log.INFO("It returned " + sr);
        Log.INFO("Manually marking this as failed since we can't analyze " +
            "the log yet.");
        return (false);
    }

    /**
     * Test if a null data stream is handled.
     * This should actually pass?
     */
    public boolean testStoreObjectNullNVOA() {
        addBug("6187947",
            "ObjectArchive.store(null) causes warnings in the log");
        addTags();
        if (excludeCase()) return false;
        SystemRecord sr;

        try {
            oa = new NameValueObjectArchive(testBed.dataVIP);
            sr = oa.storeObject(null);
        } catch (Throwable t) {
            Log.ERROR("storeObject(null) got unexpected exception: " + t);
            return (false);
        }

        Log.INFO("storeObject(null) succeeded; but check for errors in log!");
        Log.INFO("It returned " + sr);

        if (sr == null) {
            Log.ERROR("No excpeption thrown but null SystemRecord");
            return (false);
        }

        if (sr.getSize() != 0) {
            Log.ERROR("Expected 0 sized object for null store but got " +
                sr.getSize());
            return (false);
        }

        // manual verification that no scary errors are in the log can 
        // be done
        return (true);
    }
}


