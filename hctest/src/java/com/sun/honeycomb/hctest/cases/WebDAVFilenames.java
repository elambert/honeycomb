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

import com.sun.honeycomb.hctest.util.WebDAVer;
import com.sun.honeycomb.hctest.util.DavTestSchema;

import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;

import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.XMLEncoder;
import com.sun.honeycomb.common.ByteArrays;

import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.NameValueObjectArchive;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpResponse;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.text.SimpleDateFormat;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;

import java.util.TimeZone;

import java.util.Random;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Test basic WebDAV and API inter-operation
 *
 * Store files from one side and retrieve from the other, and make
 * sure all the metdata values are present.
 *
 * The view used:
 * <PRE>
    &lt;fsView name="davtestFnames" namespace="davtest"
            filename="${string1}&amp;lt; ${string2} - ${string3}&amp;amp;${string4}.txt"
            collapsetrailingnulls="true">
      &lt;attribute name="long1" unset="unk" />
    &lt;/fsView>
   </PRE>
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class WebDAVFilenames extends HoneycombLocalSuite {

    private static Random random = new Random(System.currentTimeMillis());

    private NameValueObjectArchive api = null;
    private WebDAVer dav = null;

    private String vip = null;
    private int port = 0;

    // Size of files created for the tests
    private static final int FILESIZE = 100;

    private static SimpleDateFormat tsFormatter = null;

    public String help() {
        return("\tWebDAV Filename tests\n");
    }

    public void setUp() throws Throwable {
        Log.DEBUG("WebDAVFilenames.setUp() called");
        super.setUp();

        TestCase skip = createTestCase("WebDAVFilenames",
                                       "Test virtual view filenames.");
        skip.addTag(HoneycombTag.WEBDAV);
        skip.addTag(HoneycombTag.EMULATOR);
        skip.addTag(Tag.REGRESSION);
        skip.addTag(Tag.SMOKE);
        skip.addTag(Tag.UNIT);
        skip.addTag(Tag.QUICK);
        if (skip.excludeCase()) // should I run?
            return;

        vip = testBed.getDataVIP();
        port = testBed.getDataPort();

        try {
            api = new NameValueObjectArchive(vip, port);
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getAPIConnection");
            self.testFailed("Couldn't get API connection: " + e);
            return;
        }

        try {
            dav = new WebDAVer(vip + ":" + port);
            // Make sure the view exists
            if (!dav.exists("/webdav/davtestFnames/")) {
                Log.ERROR("No view \"davtestFnames\" -- check schema.");
                dav = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (dav == null) {
            TestCase self = createTestCase("WebDAV", "getDAVConnection");
            self.testFailed("Failed to get webdav connection");
        }
    }

    public void tearDown() throws Throwable {
        Log.DEBUG("WebDAVFilenames.tearDown() called");
        super.tearDown();
    }

    //////////////////////////////////////////////////////////////////////
    // Tests begin here

    public void testScanf() {
        // PUT a file into a view, then retrieve the file's metadata
        // with the API and check all string values. (It's like scanf
        // because the webdav server has to parse out four values from
        // the filename.)

        TestCase self = initTest("Scanf");
        if (self == null)
            return;

        try {
            Map file = webdavCreateFile();

            String oid = (String) file.get("system.object_id");
            String url = getURL(file);

            if (!dav.exists(url)) {
                self.testFailed("File can't be retrieved from stored URL \"" +
                                url + "\"");
                return;
            }

            Log.INFO("WebDAVFilenames: PUT \"" + url + "\" with WebDAV, OID " +
                     oid);

            Map retrieved = apiRetrieveMD(oid);

            DavTestSchema.compareMaps(file, retrieved);

            self.testPassed("OK");
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
    }

    public void testDirScanf() {

        TestCase self = initTest("DirScanf");
        if (self == null)
            return;

        try {
            Map file = webdavCreateFileWithSpecialDirs();

            String oid = (String) file.get("system.object_id");
            String url = getURL(file);

            if (!dav.exists(url)) {
                self.testFailed("File can't be retrieved from stored URL \"" +
                                url + "\"");
                return;
            }

            Log.INFO("WebDAVFilenames: PUT \"" + url + "\" with WebDAV, OID " +
                     oid);

            Map retrieved = apiRetrieveMD(oid);

            DavTestSchema.compareMaps(file, retrieved);

            self.testPassed("OK");
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
    }

    public void testPrintf() {
        // Store a file with the API, then GET via WebDAV. (It's like
        // printf because the webdav server is combining four values into
        // the filename.)
        TestCase self = initTest("Printf");
        if (self == null)
            return;

        try {
            Map file = apiCreateFile();

            String oid = (String) file.get("system.object_id");
            String url = getURL(file);

            Log.DEBUG("WebDAVFilenames: Stored " + oid +
                      " with API, looking for file \"" + url + "\"");

            if (dav.exists(url)) {
                self.testPassed("OK");
                return;
            }

            self.testFailed("File \"" + url + "\" not found");
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
    }

    public void testDirPrintf() {
        // Same as testPrintf except with directory names
        TestCase self = initTest("DirPrintf");
        if (self == null)
            return;

        try {
            Map file = apiCreateFileWithSpecialDirs();

            String oid = (String) file.get("system.object_id");
            String url = getURL(file);

            Log.DEBUG("WebDAVFilenames: Stored " + oid +
                      " with API, looking for file \"" + url + "\"");

            if (dav.exists(url)) {
                self.testPassed("OK");
                return;
            }

            self.testFailed("File \"" + url + "\" not found");
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
    }


    //////////////////////////////////////////////////////////////////////

    private TestCase initTest(String testName) {
        if (dav == null)
            // Skip the test
            return null;

        Log.INFO("++++++ WebDAVFilenames.test" + testName + " ++++++");

        return createTestCase("WebDAVFilenames", testName);
    }

    //////////////////////////////////////////////////////////////////////
    // Helpers

    private void addStrings(Map md, boolean useSpecials) {
        md.put("davtest.long1", new Long(System.currentTimeMillis()));
        for (int i = 1; i <= 4; i++)
            md.put("davtest.string" + i, DavTestSchema.newRandomString(10));

        if (useSpecials) {
            // Insert a few special chars into string4, which is the
            // second-level directory
            String value = (String) md.get("davtest.string4");
            value = "<&>" + value;
            md.put("davtest.string4", value);
        }
    }

    /**
     * Create a random new file using WebDAV and return its
     * metadata
     */
    private Map webdavCreateFile()
            throws HoneycombTestException, ArchiveException, IOException {
        return webdavCreateFile(false);
    }
    private Map webdavCreateFileWithSpecialDirs()
            throws HoneycombTestException, ArchiveException, IOException {
        return webdavCreateFile(true);
    }
    private Map webdavCreateFile(boolean useSpecials)
            throws HoneycombTestException, ArchiveException, IOException {
        CmdResult rc;
        HashMap md = newFactoryMD(false);
        addStrings(md, useSpecials);

        String url = getURL(md);

        rc = dav.putFile(url, FILESIZE, false);
        if (!rc.pass)
            return null;

        md.put("system.object_id", rc.mdoid);
        return md;
    }

    /**
     * Create a random new file using the Java API and return its
     * metadata
     */
    private Map apiCreateFile()
            throws HoneycombTestException, ArchiveException, IOException {
        return apiCreateFile(false);
    }

    private Map apiCreateFileWithSpecialDirs()
            throws HoneycombTestException, ArchiveException, IOException {
        return apiCreateFile(true);
    }
    private Map apiCreateFile(boolean useSpecials)
            throws HoneycombTestException, ArchiveException, IOException {
        CmdResult rc;
        HashMap md = newFactoryMD(false);
        addStrings(md, useSpecials);

        CmdResult storeResult = store(FILESIZE);
        if (!storeResult.pass)
            return null;

        CmdResult mdResult = addMetadata(storeResult.mdoid, md);
        if (!mdResult.pass)
            return null;

        md.put("system.object_id", mdResult.mdoid);
        return md;
    }

    /** Get the MD for the given OID using the API. */
    private Map apiRetrieveMD(String oid)
            throws HoneycombTestException, ArchiveException, IOException {
        Map md = new HashMap();

        NameValueRecord nv = api.retrieveMetadata(new ObjectIdentifier(oid));

        String[] keys = nv.getKeys();
        for (int i = 0; i < keys.length; i++)
            md.put(keys[i], nv.getObject(keys[i]));

        md.put("system.object_id", oid);
        return md;
    }

    private String getURL(Map md) {
        StringBuffer sb = new StringBuffer();

        String delims[] = { "< ", " - &", ".txt" };

        sb.append("/webdav/davtestFnames/");
        sb.append(md.get("davtest.long1").toString()).append('/');  
        sb.append(quote(md.get("davtest.string4").toString())).append('/');          

        for (int i = 1; i <= 3; i++) {
            sb.append(quote(md.get("davtest.string" + i).toString()));
            sb.append(quote(delims[i - 1]));
        }

        Log.DEBUG("++++ Returning \"" + sb + "\"");

        return sb.toString();
    }

    private String quote(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // Can never happen
            throw new RuntimeException(e);
        }
    }

}
