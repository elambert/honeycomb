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

import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.XMLEncoder;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpResponse;

import HTTPClient.NVPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * Test the filesystem cache
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class WebDAVCache extends HoneycombLocalSuite {

    // Size of each dir name
    private static final int DIRNAMESIZE = 8;

    // Size of files created for the tests
    private static final int FILESIZE = 100;

    private static final String PROP_MAX = "honeycomb.fscache.size.max";
    private static final String PROP_LOW = "honeycomb.fscache.size.lo";

    private static final int CACHE_MAX = 100;
    private static final int CACHE_LOW = 80;

    private WebDAVer dav = null;
    private DavTestSchema dts = null;

    private Set savedFiles = null; // So we can delete everything when done

    private int oldCacheMax;
    private int oldCacheLow;

    public String help() {
        return("\tWebDAV Cache tests\n");
    }

    public void setUp() throws Throwable {
        debug("setUp() called");
        super.setUp();

        String vip = testBed.getDataVIP();
        int port = testBed.getDataPort();

        dts = new DavTestSchema();

        try {
            dav = new WebDAVer(vip + ":" + port);
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getDAVConnection");
            self.testFailed("Couldn't get webdav connection: " + e);
        }

        if (!dts.isSchemaLoaded(dav))
            // Going to skip all these tests
            dav = null;
        else {
            getCacheSize();
            setCacheSize(CACHE_MAX, CACHE_LOW);
            savedFiles = new HashSet();
        }
    }

    public void tearDown() throws Throwable {
        debug("tearDown() called");
        if (dav != null) {
            setCacheSize(oldCacheMax, oldCacheLow);
            deleteAllFiles();
        }
        super.tearDown();
    }

    //////////////////////////////////////////////////////////////////////
    // Tests begin here

    public void testCache_Fill() {
        // Basic cache fill: create lots of random files, but with no
        // individual directory having too many values

        TestCase self = initTest("Cache_Fill");
        if (self == null)
            return;

        // There are 4 directory levels, so O(size^(1/4)) entries in
        // each directory
        runTest(self, CACHE_MAX,
                (int) (1.5 + Math.exp(0.25 * Math.log((double)CACHE_MAX))));
    }

    public void testCache_FillBigDir() {
        // Create lots of random files, but expect a directory to have
        // more entries than the size of the cache

        TestCase self = initTest("Cache_FillBigDir");
        if (self == null)
            return;

        runTest(self, CACHE_MAX, 0);
    }

    private void runTest(TestCase self, int nFiles, int entriesPerDir) {
        String msg = "Storing " + nFiles + " files";
        if (entriesPerDir > 0)
            msg += ", with <=" + entriesPerDir + " entries in each directory";
        info(msg);
       
        try {
            for (int i = 0; i < nFiles; i++)
                if (createFile(self, entriesPerDir) == null)
                    return;
            self.testPassed("OK");
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
    }

    //////////////////////////////////////////////////////////////////////

    private TestCase initTest(String testName) {
        TestCase self = createTestCase("WebDAVCache", testName);

        if (dav == null) {
            self.testFailed("Failed to get webdav connection");
            return null;
        }

        // After this passes, make it a regression test (search for "FIXME!!!")
        //self.addTag(Tag.REGRESSION);

        self.addTag(Tag.NOAPI); // doesn't use the API
        self.addTag(Tag.UNIT);
        self.addTag(Tag.QUICK);

        // Tests are not appropriate for the emulator
        self.addTag(Tag.NOEMULATOR);

        self.addTag(HoneycombTag.WEBDAV);

        if (self.excludeCase())
            return null;

        Log.INFO("++++++ WebDAVCache.test" + testName + " ++++++");

        return self;
    }

    //////////////////////////////////////////////////////////////////////
    // Helpers

    /** Create a random new file in the davtest1 view and return its URL */
    private Map createFile(TestCase self, int nValues) {
        dts.setPrngDomainSize(nValues);

        try {
            CmdResult rc;
            Map md = newFactoryMD();
            dts.addDavMD(md);

            String url = dts.getDavTestName(md, 1);

            NVPair[] headers = new NVPair[1];
            headers[0] = new NVPair("Content-type", "text/plain");

            rc = dav.putFile(url, FILESIZE, false, headers);
            if (!rc.pass)
                return null;

            savedFiles.add(url);

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

    private void deleteAllFiles() {
        int failedFiles = 0;

        info("Deleting " + savedFiles.size() + " files");

        String url;
        for (Iterator i = savedFiles.iterator(); i.hasNext(); )
            try {
                dav.deleteFile((String) i.next());
            }
            catch (HoneycombTestException e) {
                failedFiles++;
            }

        if (failedFiles > 0) {
            TestCase self = createTestCase("WebDAVCache", "deleteAllFiles");
            self.testFailed("Couldn't delete " + failedFiles + " files.");
        }
    }

    // FIXME!!! We need to get/set these values from/to the cluster.
    private void getCacheSize() {
        oldCacheMax = 10000; oldCacheLow = 8000;
        warn("GET max=" + oldCacheMax + ", low=" + oldCacheLow);
    }
    private void setCacheSize(int max, int low) {
        warn("SET max=" + max + ", low=" + low);
    }

    //////////////////////////////////////////////////////////////////////
    // logging and other shortcuts

    private static void info(String s) {
        Log.INFO("WebDAVCache::" + s);
    }

    private static void debug(String s) {
        Log.DEBUG("WebDAVCache::" + s);
    }

    private static void error(String s) {
        Log.ERROR("WebDAVCache::" + s);
    }

    private static void warn(String s) {
        Log.WARN("WebDAVCache::" + s);
    }

}
