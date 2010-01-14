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

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.HoneycombTag;

import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.DavTestSchema;
import com.sun.honeycomb.hctest.util.WebDAVer;
import com.sun.honeycomb.hctest.util.URLEncoder;

import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;

import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;

import com.sun.honeycomb.common.NewObjectIdentifier;

import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.XMLEncoder;

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.NameValueObjectArchive;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * Test WebDAV "GenFS"
 *
 * Store files using the API leaving some attributes unset; then see if the
 * files appear in the higher level directories.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class WebDAVGenfs extends HoneycombLocalSuite {

    // Size of files created for the tests
    private static final int FILESIZE = 100;

    private NameValueObjectArchive api = null;
    private DavTestSchema davtest = null;
    private WebDAVer dav = null;
    private String vip = null;
    private int port = 0;

    public String help() {
        return("\tWebDAV GenFS tests\n");
    }

    public void setUp() throws Throwable {
        debug("setUp() called");
        super.setUp();

        vip = testBed.getDataVIP();
        port = testBed.getDataPort();
        davtest = new DavTestSchema();

        try {
            api = new NameValueObjectArchive(vip, port);
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getAPIConnection");
            self.testFailed("Couldn't get API connection: " + e);
            return;
        }

        newConnection();

        if (!davtest.isSchemaLoaded(dav))
            // Going to skip all these tests
            dav = null;
    }

    private void newConnection() {
        try {
            dav = new WebDAVer(vip + ":" + port);
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getDAVConnection");
            self.testFailed("Couldn't get webdav connection: " + e);
        }
    }

    public void tearDown() throws Throwable {
        debug("tearDown() called");
        super.tearDown();
    }

    //////////////////////////////////////////////////////////////////////
    // Tests begin here

    /*

     These are the views used by this group of tests:
    
        <fsView name="davtest6" namespace="davtest"
                filename="${string1}_${string2}_${string3}_${string4}" 
                filesonlyatleaflevel="false">
          <attribute name="long1" unset="unk" />
          <attribute name="long2" unset="unk" />
          <attribute name="long3" unset="unk" />
          <attribute name="long4" unset="unk" />
        </fsView>

        <fsView name="davtest7" namespace="davtest"
                filename="${string1}_${string2}_${string3}_${string4}">
          <attribute name="long1" unset="unk" />
          <attribute name="long2" unset="unk" />
        </fsView>

        <fsView name="davtest9" namespace="davtest"
            filename="${long1}_${string1}" 
            filesonlyatleaflevel="false">
          <attribute name="string2" />
          <attribute name="string3" />
          <attribute name="string4" />
        </fsView>

     API-WebDAV tests: upload a file with the API, defining all
     metadata except long3 and long4. It should be visible in both
     views.

     WebDAV-API tests: upload files both to 6 (at intermediate level)
     and 7, then get the metadata using the API. There should be no
     long3 and long4 values.

     WebDAV only tests: create a file in 7; it should have the same
     path in 6. Create it in view 6 in the directory two levels
     above the lowest; it should appear in 7.

     Regression test for 6613735: save a file to
         /davtest9/abc/def/424242_AAAAAAAAAAAAAA
     and test for the existence of
         /davtest9/abc/424242_AAAAAAAAAAAAAA
     Now the genfs DB query generated is something like
         string2 == "abc" && string3 == "424242_AAAAAAAAAAAAAA"
     In that second term, sizeof(string3) < sizeof(value) which will
     case HADB to throw the "Data truncation" SQL exception. The test
     makes sure that a 404 is returned.
    */

    public void testWithStore() {
        // Store a file with API, get with webdav

        TestCase self = initTest("WithStore");
        if (self == null)
            return;

        try {
            Map file = apiCreateFile(self);
            if (file == null)
                return;

            String url1 = davtest.getDavTestName(file, 7);
            if (!dav.exists(url1)) {
                self.testFailed("File \"" + url1 + "\" not found");
                return;
            }

            String url2 = davtest.getDavTestShortName(file, 6);
            if (!dav.exists(url2)) {
                self.testFailed("File \"" + url2 + "\" not found");
                return;
            }

            self.testPassed("OK");
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
    }

    public void testWithPutA() {
        // Store to a short view, then retrieve from larger collapsing view

        TestCase self = initTest("WithPutA");
        if (self == null)
            return;
        // sel.addTag(Tag.NOAPI);

        try {
            Map file = webdavCreate7File(self);
            if (file == null)
                return;

            String url = davtest.getDavTestShortName(file, 6);
            if (dav.exists(url)) {
                self.testPassed("OK");
                return;
            }

            self.testFailed("File \"" + url + "\" not found");
        }
        catch (Exception e) {
            self.testFailed(e.getMessage());
        }
       
    }

    public void testWithPutB() {
        // Store to a larger collapsing view, then retrieve from short view

        TestCase self = initTest("WithPutB");
        if (self == null)
            return;
        // sel.addTag(Tag.NOAPI);

        try {
            Map file = webdavCreate6ShortFile(self);
            if (file == null)
                return;

            String url = davtest.getDavTestName(file, 7);
            if (dav.exists(url)) {
                self.testPassed("OK");
                return;
            }

            self.testFailed("File \"" + url + "\" not found");
        }
        catch (Exception e) {
            self.testFailed(e.getMessage());
            e.printStackTrace();
        }
       
    }

    public void testBug_6613735() {
        // Store "/davtest9/abc/def/424242_AAAAAAAAAAAAAA", verify
        // store OK, and test for
        // "/davtest9/abc/424242_AAAAAAAAAAAAAA". Repeat with the
        // converse: Store "/davtest9/abc/424242_AAAAAAAAAAAAAA",
        // verify, and test for
        // "/davtest9/abc/def/424242_AAAAAAAAAAAAAA".

        TestCase self = initTest("Bug_6613735");
        if (self == null)
            return;
        // self.addTag(Tag.NOAPI);

        try {
            HashMap md1 = getDavtest9MD();
            HashMap md2 = getDavtest9MD();
                
            String url1 = davtest.getDavTestName(md1, 9);
            String url1s = davtest.getDavTestShortName(md1, 9);

            String url2 = davtest.getDavTestName(md2, 9);
            String url2s = davtest.getDavTestShortName(md2, 9);

            Log.DEBUG("URLs: \"" + url1 + "\" \"" + url1s + "\"");
            Log.DEBUG("URLs: \"" + url2 + "\" \"" + url2s + "\"");

            dav.putFile(url1, FILESIZE, false);
            dav.putFile(url2s, FILESIZE, false);

            // Try to make sure we don't hit the same cluster node
            // (which would return results from its cache)
            newConnection();

            // Both these calls will trigger the "Data truncation"
            // message in the cluster log
            dav.getFile(url1, false);
            dav.getFile(url2s, false);

            // Sanity checks
            if (dav.exists(url1s)) {
                self.testFailed("File \"" + url1s + "\" exists!");
                return;
            }
            if (dav.exists(url2)) {
                self.testFailed("File \"" + url2 + "\" exists!");
                return;
            }

            self.testPassed("OK");
        }
        catch (Exception e) {
            self.testFailed(e.getMessage());
            e.printStackTrace();
        }
    }

    //////////////////////////////////////////////////////////////////////

    private TestCase initTest(String testName) {
        TestCase self = createTestCase("WebDAVGenfs", testName);

        if (dav == null) {
            self.testFailed("Failed to get webdav connection");
            return null;
        }

        // Groups these tests are part of
        self.addTag(Tag.REGRESSION);
        self.addTag(Tag.UNIT);
        self.addTag(Tag.QUICK);
        self.addTag(HoneycombTag.WEBDAV);
        self.addTag(HoneycombTag.EMULATOR);

        if (self.excludeCase())
            return null;

        Log.INFO("++++++ WebDAVGenfs.test" + testName + " ++++++");

        return self;
    }

    //////////////////////////////////////////////////////////////////////
    // Helpers

    private Map webdavCreate6ShortFile(TestCase self) {
        return webdavCreateFile(self, false);
    }

    private Map webdavCreate7File(TestCase self) {
        return webdavCreateFile(self, true);
    }

    private HashMap getDavtest9MD() throws HoneycombTestException {
        HashMap md = newFactoryMD();
        davtest.addDavMD(md);

        // Make sure that string4 is not set and string1 is set to a
        // long string
        md.remove("davtest.string4");
        md.put("davtest.string1", davtest.newRandomFixedLengthString(15));

        return md;
    }

    /** Create a random new file in the davtest1 view and return its URL */
    private Map webdavCreateFile(TestCase self, boolean which) {
        try {
            CmdResult rc;
            HashMap md = newFactoryMD();
            davtest.addDavMD(md);
            String url;

            if (which)
                url = davtest.getDavTestName(md, 7);
            else
                url = davtest.getDavTestShortName(md, 6);

            info("Creating file " + url);

            rc = dav.putFile(url, FILESIZE, false);
            if (!rc.pass)
                return null;

            md.put("system.object_id", rc.mdoid);
            return md;
        }
        catch (HoneycombTestException e) {
            if (e.exitStatus != null)
                self.testFailed("Unexpected! couldn't upload scratch file - " +
                                e.exitStatus.getReturnCode() + ":" + 
                                e.exitStatus.getOutputString(false));
            else
                throw new RuntimeException(e);
        }

        return null;
    }

    private Map apiCreateFile(TestCase self) {
        try {
            CmdResult rc;
            HashMap md = newFactoryMD();
            davtest.addDavMD(md);

            // Remove davtest.long4 and davtest.long3 from the map
            md.remove("davtest.long4");
            md.remove("davtest.long3");
            // Remove davtest.long4 and davtest.long3 from the map
            md.remove("davtest.long4");
            md.remove("davtest.long3");


            CmdResult storeResult = store(FILESIZE);
            if (!storeResult.pass)
                return null;

            md.put("system.object_id", storeResult.mdoid);
            CmdResult mdResult = addMetadata(storeResult.mdoid, md);
            if (!mdResult.pass)
                return null;

            // Do we need to do anything else?

            return md;
        }
        catch (HoneycombTestException e) {
            if (e.exitStatus != null)
                self.testFailed("Unexpected! couldn't upload scratch file - " +
                                e.exitStatus.getReturnCode() + ":" + 
                                e.exitStatus.getOutputString(false));
            else
                throw new RuntimeException(e);
        }

        return null;
    }

    /** Get the MD for the given OID using the API. */
    private Map apiRetrMD(TestCase self, String oid) {
        try {
            NameValueRecord nv = api.retrieveMetadata(new ObjectIdentifier(oid));
            String[] keys = nv.getKeys();

            Map md = new HashMap();

            for (int i = 0; i < keys.length; i++)
                md.put(keys[i], nv.getObject(keys[i]));

            return md;
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
        return null;
    }

    /** Compare a key value in two Maps */
    private boolean valuesEqual(String key, Map md1, Map md2) {
        Object v1 = md1.get(key);
        Object v2 = md2.get(key);

        if (v1 == null && v2 == null)
            return true;

        if (v1 != null && v2 != null)
            return v1.toString().equals(v2.toString());

        return false;
    }

    /** Format the values from two maps for the same key */
    private String fmtValues(String key, Map md1, Map md2) {
        return md1.get(key) + " =?= " + md2.get(key);
    }

    //////////////////////////////////////////////////////////////////////
    // logging and other shortcuts

    private static void info(String s) {
        Log.INFO("WebDAVGenfs::" + s);
    }

    private static void debug(String s) {
        Log.DEBUG("WebDAVGenfs::" + s);
    }

    private static void error(String s) {
        Log.ERROR("WebDAVGenfs::" + s);
    }

    private static void warn(String s) {
        Log.WARN("WebDAVGenfs::" + s);
    }

}
