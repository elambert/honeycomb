
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
 * Test basic WebDAV and API inter-operation
 *
 * Store files from one side and retrieve from the other, and make
 * sure all the metdata values are present.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class WebDAVapi extends HoneycombLocalSuite {

    // Size of files created for the tests
    private static final int FILESIZE = 100;

    private NameValueObjectArchive api = null;
    private WebDAVer dav = null;
    private DavTestSchema davtest = null;
    private String vip = null;
    private int port = 0;

    public String help() {
        return("\tWebDAV API tests\n");
    }

    public void setUp() throws Throwable {
        debug("setUp() called");
        super.setUp();

        TestCase skip = createTestCase("WebDAVapi",
                                       "Test WebDAV/API interop.");

        // Groups these tests are part of
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

        try {
            dav = new WebDAVer(vip + ":" + port);
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getDAVConnection");
            self.testFailed("Couldn't get webdav connection: " + e);
        }

        if (!davtest.isSchemaLoaded(dav))
            // Going to skip all these tests
            dav = null;
    }

    public void tearDown() throws Throwable {
        debug("tearDown() called");
        super.tearDown();
    }

    //////////////////////////////////////////////////////////////////////
    // Tests begin here

    public void testStoreGet() {
        // Store a file with API, get with webdav
        TestCase self = initTest("Store_Get");
        if (self == null)
            return;

        try {
            Map file = apiCreateFile(self);
            if (file == null)
                return;

            String url = davtest.getDavTestName(file, 1);
            if (dav.exists(url)) {
                self.testPassed("OK");
                return;
            }

            self.testFailed("File \"" + url + "\" not found");
        }
        catch (HoneycombTestException e) {
            self.testFailed(e.getMessage());
        }
    }

    public void testPutRetrieve() {
        // Put a file with webdav, retrieve with API

        TestCase self = initTest("Put_Retrieve");
        if (self == null)
            return;

        try {
            Map file = webdavCreateFile(self);
            if (file == null)
                return;

            String url = davtest.getDavTestName(file, 1);
            String oid = (String) file.get("system.object_id");

            info("Wrote file <" + oid + "> \"" + url + "\"");

            Map retrieved = apiRetrMD(self, oid);
            if (retrieved == null)
                return;

            // We don't use davtest.double1 in view1 so don't test it
            file.remove("davtest.double1");

            DavTestSchema.compareMaps(file, retrieved);
            self.testPassed("OK");
        }
        catch (HoneycombTestException e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
    }
    public void testBug_6666568_FsAttrs() {
        // 1. Store one file with filesystem.* attributes and one
        //    without (using the API).
        // 2. See if the files are accessible through both views (5 and 5a)
        // 3. In view 5a, make sure MIME type is returned in Content-type

        TestCase self = initTest("Bug_6666568_FsAttrs");
        if (self == null)
            return;

        try {
            Map headers = null;

            Map file1 = apiCreateFile(self, false);
            Map file2 = apiCreateFile(self, true);

            String url1 = davtest.getDavTestName(file1, 5);
            String url1a = url1.replaceFirst("/davtest5/", "/davtest5a/");

            String url2 = davtest.getDavTestName(file2, 5);
            String url2a = url2.replaceFirst("/davtest5/", "/davtest5a/");

            // See if both files exist in the non-fsattr view
            dav.getFile(url1, false);
            dav.getFile(url2, false);

            // url1a should have Content-type: application/octet-stream
            // (the default)

            headers = new HashMap();
            dav.getFileStream(url1a, headers);

            String contentType = (String) headers.get("content-type");
            if (!"application/octet-stream".equals(contentType)) {
                self.testFailed("Expected default content-type for \"" +
                                url2a + "\"; got " +
                                DavTestSchema.image(contentType));
                return;
            }

            // url2a should have the right Content-type

            headers = new HashMap();
            dav.getFileStream(url2a, headers);

            contentType = (String) headers.get("content-type");
            String mimeType = (String) file2.get("filesystem.mimetype");
            if (!mimeType.equals(contentType)) {
                self.testFailed("Content-type of \"" + url2a +
                                "\": expected " + mimeType + " but got " +
                                DavTestSchema.image(contentType));
                return;
            }
        }
        catch (Exception e) {
            self.testFailed("Exception: " + e);
            e.printStackTrace();
            return;
            
        }

        self.testPassed("OK");
    }

    public void testBug_6510317() {
        // Store a file with API, get with webdav
        TestCase self = initTest("Bug_6510317");
        if (self == null)
            return;

        try {
            // Create a file with metadata that will make it show up
            // in the view "oidByGidUidMimetype"
            Map file = apiCreateFile(self, /* addFsAttrs */ true);
            if (file == null) {
                self.testFailed("Couldn't create file using the API");
                return;
            }

            String url = getDirURL_oidByGidUidMimetype(file);

            // Now read the HTML returned and make sure that the href
            // values are formatted correctly, with the expected MIME
            // type, and that the link itself is valid. (This should
            // be a full parse of the returned HTML, but instead we
            // know that the listing is returned one element per line
            // so we look based on that.)
            BufferedReader inp =
                new BufferedReader(new InputStreamReader(dav.getFileStream(url)));

            // This is the child URL, what you get if you click on the
            // link to get to the file we just uploaded
            String mimeType = (String) file.get("filesystem.mimetype");
            String nextURL = url + "/" + URLEncoder.encode(mimeType);

            String marker = "href=\"" + nextURL + "\"";
            String mimeText = ">" + mimeType + "</a>";
            boolean markerFound = false;
            String line;
            while ((line = inp.readLine()) != null) {
                if (line.indexOf(marker) >= 0) {
                    if (line.indexOf(mimeText) < 0) {
                        self.testFailed("Unquoted MIMEtype text " + 
                                        DavTestSchema.image(mimeText) +
                                        " not found in listing");
                        return;
                    }
                    markerFound = true;
                }
            }
            if (!markerFound) {
                self.testFailed("Marker " + DavTestSchema.image(marker) +
                                " not found.");
                return;
            }

            // And make sure that the child is also a valid URL
            if (dav.exists(nextURL)) {
                self.testPassed("OK");
                return;
            }

            self.testFailed("File " + DavTestSchema.image(nextURL) +
                            " not found");
        }
        catch (Exception e) {
            self.testFailed(e.getMessage());
            e.printStackTrace();
        }
    }


    //////////////////////////////////////////////////////////////////////

    private TestCase initTest(String testName) {
        if (dav == null)
            // Skip the test
            return null;

        Log.INFO("++++++ WebDAVapi.test" + testName + " ++++++");

        return createTestCase("WebDAVapi", testName);
    }

    //////////////////////////////////////////////////////////////////////
    // Helpers

    /** Create a random new file in the davtest1 view and return its URL */
    private Map webdavCreateFile(TestCase self) {
        return webdavCreateFile(self, 1);
    }

    /** Create a random new file in a davtest view and return its URL */
    private Map webdavCreateFile(TestCase self, int viewIndex) {
        try {
            CmdResult rc;
            HashMap md = newFactoryMD();
            davtest.addDavMD(md);
            String url = davtest.getDavTestName(md, viewIndex);

            rc = dav.putFile(url, FILESIZE, false);
            if (!rc.pass)
                return null;

            md.put("system.object_id", rc.mdoid);
            return md;
        }
        catch (HoneycombTestException e) {
            if (e.exitStatus != null)
                self.testFailed("Unexpected! couldn't upload scratch file, " +
                                e.exitStatus.getReturnCode() + ":" + 
                                e.exitStatus.getOutputString(false));
            else
                throw new RuntimeException(e);
        }

        return null;
    }

    private Map apiCreateFile(TestCase self) {
        return apiCreateFile(self, false);
    }

    private Map apiCreateFile(TestCase self, boolean addFsAttrs) {
        try {
            CmdResult rc;
            HashMap md = newFactoryMD(addFsAttrs);
            davtest.addDavMD(md);

            CmdResult storeResult = store(FILESIZE);
            if (!storeResult.pass)
                return null;

            CmdResult mdResult = addMetadata(storeResult.mdoid, md);
            if (!mdResult.pass)
                return null;

            md.put("system.object_id", mdResult.mdoid);

            // Do we need to do anything else?

            return md;
        }
        catch (HoneycombTestException e) {
            if (e.exitStatus != null)
                self.testFailed("Unexpected! couldn't upload scratch file, " +
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
            NameValueRecord nv =
                api.retrieveMetadata(new ObjectIdentifier(oid));
            String[] keys = nv.getKeys();

            Map md = new HashMap();

            for (int i = 0; i < keys.length; i++)
                md.put(keys[i], nv.getObject(keys[i]));
            md.put("system.object_id", oid);
            return md;
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed(e.getMessage());
        }
        return null;
    }

    /**
     * Return the oidByGidUidMimetype URL for two directories down so
     * that the return value is a directory listing containing all the
     * filesystem.mimetype values
     */
    private String getDirURL_oidByGidUidMimetype(Map md) {
        return "/webdav/oidByGidUidMimetype/" +
            md.get("filesystem.gid") + "/" +
            md.get("filesystem.uid");
    }

    //////////////////////////////////////////////////////////////////////
    // logging and other shortcuts

    private static void info(String s) {
        Log.INFO("WebDAVapi::" + s);
    }

    private static void debug(String s) {
        Log.DEBUG("WebDAVapi::" + s);
    }

    private static void error(String s) {
        Log.ERROR("WebDAVapi::" + s);
    }

    private static void warn(String s) {
        Log.WARN("WebDAVapi::" + s);
    }

}
