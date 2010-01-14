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
import com.sun.honeycomb.hctest.util.WebDAVer;
import com.sun.honeycomb.hctest.util.URLEncoder;

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
import java.io.UnsupportedEncodingException;

import java.text.SimpleDateFormat;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Iterator;

import java.util.TimeZone;

import java.util.Random;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Test basic WebDAV quoting of special chars
 *
 * Store files using the API, with metadata values that include
 * special characters (cf. RFC 1738)
 *
 * The view used:
 * <PRE>
    <fsView name="byUser" filename="${system.object_id}">
      <attribute name="user"/>
    </fsView>
 * </PRE>
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class WebDAVQuoting extends HoneycombLocalSuite {

    private static Random random = new Random(System.currentTimeMillis());

    private NameValueObjectArchive api = null;
    private WebDAVer dav = null;

    private String vip = null;
    private int port = 0;

    // Size of files created for the tests
    private static final int FILESIZE = 100;

    public String help() {
        return("\tWebDAV Types tests\n");
    }

    public void setUp() throws Throwable {
        Log.DEBUG("WebDAVQuoting.setUp() called");
        super.setUp();

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
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getDAVConnection");
            self.testFailed("Couldn't get webdav connection: " + e);
        }
        
        // Make sure the QA schema has been loaded
        if (!dav.exists("/webdav/byUser/"))
            dav = null;
    }

    public void tearDown() throws Throwable {
        Log.DEBUG("WebDAVQuoting.tearDown() called");
        super.tearDown();
    }

    //////////////////////////////////////////////////////////////////////
    // Tests begin here

    public void testDollar() {
        doit("Dollar", '$');
    }

    public void testSlash() {
        doit("Slash", '/');
    }

    public void testBackSlash() {
        doit("BackSlash", '\\');
    }

    public void testApostrophe() {
        doit("Apostrophe", '\'');
    }

    public void testQuote() {
        doit("Quote", '"');
    }

    public void testAmpersand() {
        doit("Ampersand", '&');
    }

    public void testQuestion() {
        doit("QuestionMark", '?');
    }

    public void testTilde() {
        doit("Tilde", '~');
    }

    public void testCaret() {
        doit("Equals", '=');
    }

    public void testCR() {
        doit("CR", '\r');
    }

    public void testLF() {
        doit("LF", '\n');
    }

    public void testColon() {
        doit("Colon", ':');
    }

    public void testSemiColon() {
        doit("SemiColon", ';');
    }

    public void testAtSign() {
        doit("AtSign", '@');
    }

    //////////////////////////////////////////////////////////////////////

    private void doit(String name, char special) {
        // Store a file, then GET via WebDAV and check the value

        TestCase self = initTest(name);
        if (self == null)
            return;

        try {
            Map file = apiCreateFile(special);

            String oid = (String) file.get("system.object_id");
            String url = getURL(file);

            Log.INFO("WebDAVQuoting: Stored " + oid +
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
        TestCase self = createTestCase("WebDAVQuoting", testName);

        if (dav == null) {
            self.testFailed("Failed to get webdav connection");
            return null;
        }

        // Groups these tests are part of

        self.addTag(Tag.REGRESSION);
        self.addTag(Tag.SMOKE);

        self.addTag(Tag.UNIT);
        self.addTag(Tag.QUICK);

        self.addTag(HoneycombTag.WEBDAV);
        self.addTag(HoneycombTag.EMULATOR);

        if (self.excludeCase())
            return null;

        Log.INFO("++++++ WebDAVQuoting.test" + testName + " ++++++");

        return self;
    }

    //////////////////////////////////////////////////////////////////////
    // Helpers

    /**
     * Create a random new file using the Java API, set a metadata
     * attribute to a value that includes that special character 'c',
     * and return its metadata
     */
    private Map apiCreateFile(char c)
            throws HoneycombTestException, ArchiveException, IOException {
        CmdResult rc;
        HashMap md = newFactoryMD();

        md.put("user", "User" + c);

        CmdResult storeResult = store(FILESIZE);
        if (!storeResult.pass)
            return null;

        CmdResult mdResult = addMetadata(storeResult.mdoid, md);
        if (!mdResult.pass)
            return null;

        md.put("system.object_id", mdResult.mdoid);
        return md;
    }

    private String getURL(Map md) throws HoneycombTestException {
        StringBuffer sb = new StringBuffer();

        sb.append("/webdav/byUser/");
        try {
            sb.append(urlQuote((String)md.get("user")));
        }
        catch (Exception e) {
            throw new HoneycombTestException(e);
        }
        sb.append('/').append((String)md.get("system.object_id"));

        return sb.toString();
    }

    /** URL-quote a string
     *
     * RFC 1738 says:
     *    "...Only alphanumerics [0-9a-zA-Z], the special characters
     *    $-_.+!*'(), and reserved characters used for their reserved
     *    purposes may be used unencoded within a URL."
     */
    private String urlQuote(String s) throws UnsupportedEncodingException {
        StringBuffer sb = new StringBuffer();
        String plain = "$-_.+!*'()";

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isLetterOrDigit(c) || plain.indexOf(c) >= 0)
                sb.append(c);
            else {
                sb.append('%');

                byte[] utf8 = s.substring(i, i+1).getBytes("UTF-8");
                for (int j = 0; j < utf8.length; j++) {
                    String b = Integer.toHexString(utf8[j] & 0x00ff);
                    if (b.length() < 2)
                        sb.append('0');
                    sb.append(b);
                }
            }
        }

        return sb.toString();
    }

}
