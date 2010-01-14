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
 * Test WebDAV "compliance" functionality: expiration dates and legal
 * holds. Since authentication is required to set/modify legal holds,
 * it also tests auth.
 *
 * It uses WebDAVer.doMethodWithXML(String method, String path,
 * String request, Map extraHeaders) to carry out WebDAV operations,
 * since HTTPClient doesn't know WebDAV methods.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class WebDAVCompliance extends HoneycombLocalSuite {
    private static final String PROP_EXPIRATION = "expiration";
    private static final String PROP_LEGALHOLDS = "legalholds";

    private static final String HC_XMLNS = "http://www.sun.com/honeycomb/";

    private static final SimpleDateFormat utcFormat;

    static {
        utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // Size of files created for the tests
    private static final int FILESIZE = 100;

    private WebDAVer dav = null;
    private DavTestSchema dts = null;

    private String fiveMinutesAhead = null;

    private static final String[] holdsA = {
        "Brown v. Topeka Board of Education",
        "Griswold v. Connecticut",
        "Santa Clara County v. Southern Pacific Railroad"
    };

    private static final String[] holdsB = {
        "Bowers v. Hardwick",
        "Dred Scott v. Sandford"
    };

    private static final String[] holdsC = {
        "Plessey v. Ferguson",
    };

    private static final String[] holdsD = {
    };


    public String help() {
        return("\tWebDAV Compliance tests\n");
    }

    public void setUp() throws Throwable {
        debug("setUp() called");
        super.setUp();

        String vip = testBed.getDataVIP();
        int port = testBed.getDataPort();

        dts = new DavTestSchema();

        try {
            dav = new WebDAVer(vip + ":" + port);
            fiveMinutesAhead = toUtc(new Date(dav.getServerTime() + 300000));
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getDAVConnection");
            self.testFailed("Couldn't get webdav connection: " + e);
        }

        if (!dts.isSchemaLoaded(dav))
            // Going to skip all these tests
            dav = null;
    }

    public void tearDown() throws Throwable {
        debug("tearDown() called");
        super.tearDown();
    }

    //////////////////////////////////////////////////////////////////////
    // Tests begin here

    // Utils available:
    //     void setProperty(String url, String name, String value)
    //     String getProperty(String url, String name)
    //     void delete(String url)

    public void testAuth_Basic() {
        // 1. Set expiration date: should be error
        // 2. Authenticate
        // 3. Set expiration date: should succeed
        // 4. Get expiration date and compare
        // (This has to be the first test, since we start unauthenticated.)

        TestCase self = initTest("Auth_Basic");
        if (self == null)
            return;

        String file = createFile(self);
        if (file == null)
            return;

        self.addTag(Tag.SMOKE);

        try {
            setProperty(file, PROP_EXPIRATION, fiveMinutesAhead);
            self.testFailed("Unauthenticated setExpiration request " +
                            "succeeded for \"" + file + "\"");
            return;
        }
        catch (HttpException e) {
            // Expect "401 Unauthorized"
            if (e.getCode() != HttpResponse.__401_Unauthorized) {
                self.testFailed("Unauthenticated setExpiration req for \"" +
                                file + "\" returned " + e.getCode());
                return;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed("Unexpected exception: " + e);
            return;
        }

        Log.INFO("setExpiration required authentication -- good!");

        try {
            file = createFile(self);
            if (file == null)
                return;

            dav.setCredentials("HC WebDAV", "root", "honeycomb");

            setProperty(file, PROP_EXPIRATION, fiveMinutesAhead);
            String exp = getProperty(file, PROP_EXPIRATION);

            if (exp.equals(fiveMinutesAhead))
                self.testPassed("OK basic");
            else
                self.testFailed("Expiration: set \"" + fiveMinutesAhead +
                                "\" but got \"" + exp + "\"");
        }
        catch (HttpException e) {
            warn(URLEncoder.decode(e.getMessage()));
            self.testFailed("Request failed: " +
                            URLEncoder.decode(e.getMessage()));
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed("Unexpected exception: " + e);
        }

    }

    public void testExpiration_Unspec() {
        // 1. Set expiration date to "unknown"
        // 2. Delete

        TestCase self = initTest("Expiration_Unspec");
        if (self == null)
            return;

        expiration_test(self, "unknown");
    }

    public void testExpiration_Specific() {
        // 1. Set expiration date to specific value
        // 2. Delete

        TestCase self = initTest("Expiration_Specific");
        if (self == null)
            return;

        expiration_test(self, fiveMinutesAhead);
    }

    public void testExpiration_Past() {
        // 1. Set expiration date to a date in the past

        TestCase self = initTest("Expiration_Past");
        if (self == null)
            return;

        String file = createFile(self);
        if (file == null)
            return;

        try {
            setProperty(file, PROP_EXPIRATION, "2001-01-01T00:00:00Z");
            self.testFailed("Expiration set to a time in the past");
        }
        catch (HttpException e) {
            // Expect "409 Conflict"
            if (e.getCode() != HttpResponse.__409_Conflict) {
                e.printStackTrace();
                self.testFailed("Delete req. for file with expiration \"" +
                                file + "\" returned " + e.getCode() + ":" +
                                URLEncoder.decode(e.getMessage()));
                return;
            }
            self.testPassed("OK");
        }
    }

    public void testExpiration_Expiry() {
        // 1. Set expiration date
        // 2. Wait for expiration date
        // 3. Delete

        TestCase self = initTest("Expiration_Expiry");
        if (self == null)
            return;

        self.addTag(Tag.SMOKE);

        String file = createFile(self);
        if (file == null)
            return;

        try {
            // Set the expiration 10s in the future (from server's time)
            String expiration = toUtc(new Date(dav.getServerTime() + 10000));
            Log.INFO("Expiration set to " + expiration);
            setProperty(file, PROP_EXPIRATION, expiration);
        }
        catch (HoneycombTestException e) {
            warn(e.getMessage());
            self.testFailed("Unexpected error: couldn't get server time");
            return;
        }
        catch (HttpException e) {
            warn(URLEncoder.decode(e.getMessage()));
            self.testFailed("Unexpected error: couldn't set expiration: " +
                            URLEncoder.decode(e.getMessage()));
            return;       
        }

        try {
            webdavDelete(file);
            self.testFailed("File with an expiration date was deleted");
            return;
        }
        catch (HttpException e) {
            // Expect "403 Forbidden"
            if (e.getCode() != HttpResponse.__403_Forbidden) {
                e.printStackTrace();
                self.testFailed("Delete req. for file with expiration \"" +
                                file + "\" returned " + e.getCode() + ":" +
                                URLEncoder.decode(e.getMessage()));
                return;
            }
        }

        Log.INFO("delete was forbidden -- good!");

        try {
            // Wait for the file to expire
            warn("Now waiting for 15s for expiration time....");
            Thread.sleep(15000);
            webdavDelete(file);
            self.testPassed("OK");
            return;
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed("Unexpected exception: " + e);
        }
    }

    public void testExpiration_OverwriteUnspec() {
        // 1. Set expiration to "unknown"
        // 2. Overwrite with specific value
        // 3. Get expiration date

        TestCase self = initTest("Expiration_OverwriteUnspec");
        if (self == null)
             return;

        expirationOverwrite(self, "unknown", fiveMinutesAhead, true);
    }

    public void testExpiration_OverwriteSpecific() {
        // 1. Set expiration to specific value
        // 2. Overwrite with "unknown"
        // 3. Get expiration date

        TestCase self = initTest("Expiration_OverwriteSpecific");
        if (self == null)
            return;

        expirationOverwrite(self, fiveMinutesAhead, "unknown", false);
    }

    public void testExpiration_Reduce() {
        // 1. Set expiration to specific value
        // 2. Overwrite with earlier value
        // 3. Get expiration date

        TestCase self = initTest("Expiration_Reduce");
        if (self == null)
            return;

        try {
            String minuteAhead = toUtc(new Date(dav.getServerTime() + 60000));
            expirationOverwrite(self, fiveMinutesAhead, minuteAhead, false);
        }
        catch (HoneycombTestException e) {
            self.testFailed(e.getMessage());
        }
    }

    public void testExpiration_Extend() {
        // 1. Set expiration to specific value
        // 2. Overwrite with later value
        // 3. Get expiration date

        TestCase self = initTest("Expiration_Extend");
        if (self == null)
            return;

        try {
            String minuteAhead = toUtc(new Date(dav.getServerTime() + 60000));
            expirationOverwrite(self, minuteAhead, fiveMinutesAhead, true);
        }
        catch (HoneycombTestException e) {
            self.testFailed(e.getMessage());
        }
    }

    public void testHolds_Basic() {
        // 1. Set legal holds
        // 2. Get legal holds
        // 3. Compare
        // 4. Delete

        TestCase self = initTest("Holds_Basic");
        if (self == null)
            return;

        self.addTag(Tag.SMOKE);

        String file = createFile(self);
        if (file == null)
            return;

        if (!setHolds(self, file, holdsA))
            return;

        String[] returned = getHolds(self, file);
        if (returned == null)
            return;

        if (!holdsEqual(holdsA, returned)) {
            self.testFailed("Returned holds different from stored holds");
            return;
        }

        try {
            webdavDelete(file);
            self.testFailed("Delete succeeded in spite of legal holds");
            return;
        }
        catch (HttpException e) {
            // Expect "403 Forbidden"
            if (e.getCode() != HttpResponse.__403_Forbidden) {
                e.printStackTrace();
                self.testFailed("Delete req. for file with legal holds \"" +
                                file + "\" returned " + e.getCode() + ":" +
                                URLEncoder.decode(e.getMessage()));
                return;
            }
        }

        // Release the file so it doesn't become undeletable
        setHolds(self, file, null);

        self.testPassed("OK");
    }

    public void testHolds_Overwrite() {
        // 1. Set legal holds
        // 2. Overwrite legal holds
        // 3. Get legal holds

        TestCase self = initTest("Holds_Overwrite");
        if (self == null)
            return;

        String file = createFile(self);
        if (file == null)
            return;

        if (!setHolds(self, file, holdsA))
            return;

        if (!setHolds(self, file, holdsB))
            return;

        String[] returned = getHolds(self, file);
        if (returned == null)
            return;

        if (!holdsEqual(holdsB, returned)) {
            self.testFailed("Overwriting hold set failed");
            return;
        }

        // Release the file so it doesn't become undeletable
        setHolds(self, file, null);

        self.testPassed("OK");
    }

    public void testHolds_Delete() {
        // 1. Set legal holds
        // 2. Overwrite legal hold to empty set
        // 3. Get legal holds
        // 4. Delete

        TestCase self = initTest("Holds_Delete");
        if (self == null)
            return;

        String file = createFile(self);
        if (file == null)
            return;

        if (!setHolds(self, file, holdsA))
            return;

        String[] returned = getHolds(self, file);
        if (returned == null)
            return;

        if (!holdsEqual(holdsA, returned)) {
            self.testFailed("Couldn't set holds");
            return;
        }

        if (!setHolds(self, file, null))
            return;

        try {
            webdavDelete(file);
            self.testPassed("OK");
        }
        catch (HttpException e) {
            String msg = URLEncoder.decode(e.getMessage());
            warn(msg);
            self.testFailed(msg);
        }
    }


    //////////////////////////////////////////////////////////////////////

    private TestCase initTest(String testName) {
        TestCase self = createTestCase("WebDAVComplicance", testName);

        if (dav == null) {
            self.testFailed("Failed to get webdav connection");
            return null;
        }

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
        // Groups these tests are part of
        //self.addTag(Tag.REGRESSION);
        //self.addTag(Tag.UNIT);
        //self.addTag(Tag.QUICK);
        //self.addTag(Tag.NOAPI); // doesn't use the API
        //self.addTag(HoneycombTag.EMULATOR);

        self.addTag(HoneycombTag.WEBDAV);

	self.addTag(Tag.NORUN, "Compliance feature has been disabled for 1.1, tests do not apply"); 

        if (self.excludeCase())
            return null;

        Log.INFO("++++++ WebDAVCompliance.test" + testName + " ++++++");

        return self;
    }

    //////////////////////////////////////////////////////////////////////
    // Helpers

    private void expirationOverwrite(TestCase self,
                                     String value1, String value2,
                                     boolean expectSuccess) {
        String file = createFile(self);
        if (file == null)
            return;

        // Set value1
        try {
            setProperty(file, PROP_EXPIRATION, value1);
        }
        catch (Exception e) {
            self.testFailed("Unexpected error: couldn't set expiration");
            return;
        }

        // Set value2
        try {
            setProperty(file, PROP_EXPIRATION, value2);
            if (!expectSuccess) {
                self.testFailed("Overwrite \"" + value1 + "\" with \"" +
                                value2 + "\" succeeded");
                return;
            }
        }
        catch (Exception e) {
            if (expectSuccess) {
                self.testFailed("Overwrite \"" + value1 + "\" with \"" +
                                value2 + "\" failed");
                return;
            }
        }

        // See if value is correct
        String exp;
        try {
            exp = getProperty(file, PROP_EXPIRATION);
        }
        catch (Exception e) {
            self.testFailed("Unexpected error: couldn't get expiration");
            return;
        }

        boolean ok = (expectSuccess && exp.equals(value2)) ||
            (!expectSuccess && exp.equals(value1));

        if (ok)
            self.testPassed("OK");
        else
            self.testFailed("Result of overwrite is bogus");
    }

    private void expiration_test(TestCase self, String expiration) {
        String file = createFile(self);
        if (file == null)
            return;

        try {
            setProperty(file, PROP_EXPIRATION, expiration);
            String exp = getProperty(file, PROP_EXPIRATION);
            if (!exp.equals(expiration)) {
                self.testFailed("got back \"" + exp + "\" instead of \"" +
                                expiration + "\"");
                return;
            }

            webdavDelete(file);
            self.testFailed("File with an expiration date was deleted");
        }
        catch (HttpException e) {
            // Expect "403 Forbidden"
            if (e.getCode() != HttpResponse.__403_Forbidden) {
                e.printStackTrace();
                self.testFailed("Delete req. for file with expiration \"" +
                                file + "\" returned " + e.getCode() + ":" +
                                URLEncoder.decode(e.getMessage()));
                return;
            }
            self.testPassed("OK");
        }
        catch (Exception e) {
            e.printStackTrace();
            self.testFailed("Unexpected exception: " + e);
        }
    }

    private String createFile(TestCase self) {
        try {
            return dav.createFile(1, dts);
        }
        catch (HoneycombTestException e) {
            self.testFailed(e.toString());
            warn(e.toString());
        }
        return null;
    }

    private boolean setHolds(TestCase self, String file, String[] holds) {
        try {
            setProperty(file, PROP_LEGALHOLDS,
                        encodeHolds(holds, "HCFS:case"));
        }
        catch (HttpException e) {
            e.printStackTrace();
            self.testFailed("Couldn't set legal holds: " +
                            URLEncoder.decode(e.getMessage()));
            return false;
        }

        return true;
    }

    private String[] getHolds(TestCase self, String file) {
        try {
            return decodeHolds(getProperty(file, PROP_LEGALHOLDS), 
                               "HCFS:case");
        }
        catch (HttpException e) {
            e.printStackTrace();
            self.testFailed("Couldn't get legal holds: " +
                            URLEncoder.decode(e.getMessage()));
            return null;
        }
        catch (HoneycombTestException e) {
            self.testFailed("Couldn't get holds: " + e.getMessage());
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////
    // Utils

    /** Delete file */
    private void webdavDelete(String url) throws HttpException {
        try {
            dav.deleteFile(url);
        }
        catch (HoneycombTestException e) {
            if (e.exitStatus != null)
                throw new HttpException(e.exitStatus.getReturnCode(),
                                        e.exitStatus.getOutputString(false));
            else
                throw new RuntimeException(e);
        }
    }

    /** Set property. The value is already appropriately encoded. */
    private void setProperty(String url, String name, String value)
            throws HttpException {
        CmdResult rc;

        String request = req_setProperty(name, value);
        try {
            rc = dav.doMethodWithXML("PROPPATCH", url, request, null);
        }
        catch (HoneycombTestException e) {
            if (e.exitStatus != null)
                throw new HttpException(e.exitStatus.getReturnCode(),
                                        e.exitStatus.getOutputString(false));
            else
                throw new RuntimeException(e);
        }

        if (!rc.pass)
            throw new HttpException(HttpResponse.__500_Internal_Server_Error,
                                    rc.getExceptions());
    }

    /** Get property. The value is returned as-is, not decoded. */
    private String getProperty(String url, String name)
            throws HttpException {
        try {
            String request = req_getProperty(name);
            CmdResult rc =
                dav.doMethodWithXML("PROPFIND", url, request, null);
            if (!rc.pass)
                throw new HttpException(HttpResponse.__500_Internal_Server_Error,
                                        rc.getExceptions());

            String xmlns = getXMLns(rc.string, HC_XMLNS) + ":";
            if (xmlns.length() == 1) // HC is the default namespace
                xmlns = "";

            return parse_getValue(rc.string, xmlns + name);
        }
        catch (HoneycombTestException e) {
            if (e.exitStatus != null)
                throw new HttpException(e.exitStatus.getReturnCode(),
                                        e.exitStatus.getOutputString(false));
            else
                throw new RuntimeException(e);
        }
    }

    //////////////////////////////////////////////////////////////////////
    // Holds

    private static boolean holdsEqual(String[] holdsA, String[] holdsB) {
        // An array of holds is always sorted, so equality test is easy

        if (holdsA.length != holdsB.length) {
            warn("Holds of different sizes: " + toString(holdsA) + " != " +
                 toString(holdsB));
            return false;
        }

        for (int i = 0; i < holdsA.length; i++)
            if (!holdsA[i].equals(holdsB[i])) {
                warn("Holds different at [" + i + "]: " + toString(holdsA) +
                     " != " + toString(holdsB));
                return false;
            }

        return true;
    }

    private static String toString(String[] holds) {
        StringBuffer buf = new StringBuffer();
        String delim = " ";
        buf.append("{");
        for (int i = 0; i < holds.length; i++) {
            buf.append(delim).append("<<").append(holds[i]).append(">>");
            delim = ", ";
        }
        buf.append(" }");

        return buf.toString();
    }

    private static String encodeHolds(String[] holds, String tag) {
        if (holds == null)
            return "";
        return encodeHolds(holds, tag, holds.length);
    }

    private static String encodeHolds(String[] holds, String tag, int n) {
        if (holds == null)
            return "";

        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < n; i++) {
            buf.append("<").append(tag).append('>');
            buf.append(XMLEncoder.encode(holds[i]));
            buf.append("</").append(tag).append('>');
        }

        return buf.toString();
    }

    private static String[] decodeHolds(String xml, String tag)
            throws HoneycombTestException {
        // XML string looks like:
        //     <tag>value1</tag> <tag>value2</tag> <tag>value3</tag> ...

        String a = xml.replaceAll("<" + tag + ">", "<<");
        String munged = a.replaceAll("</" + tag + ">", ">>");

        // Now it looks like
        //     <<value1>> <<value2>> <<value3>> ...

        List values = new ArrayList();

        int pos = 0;
        while ((pos = munged.indexOf("<<", pos)) >= 0) {
            pos += 2;

            int endPos = munged.indexOf(">>", pos);
            if (endPos < 0)
                throw new HoneycombTestException("Holds not well-formed: \"" +
                                                 xml + "\"");

            // A little sanity check
            int nPos = munged.indexOf("<<", pos);
            if (nPos >= 0 && nPos < endPos)
                throw new HoneycombTestException("Holds can't be nested: \"" +
                                                 xml + "\"");

            try {
                values.add(XMLEncoder.decode(munged.substring(pos, endPos)));
            }
            catch (XMLException e) {
                Log.WARN(e.toString());
                e.printStackTrace();
            }
            pos = endPos + 2;
        }

        String[] holds = new String[values.size()];
        for (int i = 0; i < holds.length; i++)
            holds[i] = (String) values.get(i);

        Arrays.sort(holds);

        return holds;
    }

    //////////////////////////////////////////////////////////////////////
    // logging and other shortcuts

    private static void info(String s) {
        Log.INFO("WebDAVCompliance::" + s);
    }

    private static void debug(String s) {
        Log.DEBUG("WebDAVCompliance::" + s);
    }

    private static void error(String s) {
        Log.ERROR("WebDAVCompliance::" + s);
    }

    private static void warn(String s) {
        Log.WARN("WebDAVCompliance::" + s);
    }

    private static synchronized String toUtc(Date when) {
        // SimpleDateFormat is not thread-safe
        return utcFormat.format(when);
    }

    //////////////////////////////////////////////////////////////////////
    // XML generating and parsing helpers

    private static final String prefixGet =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind " +
        "xmlns=\"DAV:\" xmlns:HCFS=\"" + HC_XMLNS + "\">" +
        "<prop><";
    private static final String suffixGet = "/></prop></propfind>";

    private static String req_getProperty(String propname) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(prefixGet).append(propname).append(suffixGet);
        return buffer.toString();
    }

    private static final String prefixSet =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><propertyupdate " +
        "xmlns=\"DAV:\" xmlns:HCFS=\"" + HC_XMLNS + "\">" +
        "<set><prop><";
    private static final String suffixSet = "></prop></set></propertyupdate>";

    private static String req_setProperty(String propname, String value) {
        StringBuffer buffer = new StringBuffer(prefixSet);
        buffer.append(propname).append(">").append(value);
        buffer.append("</").append(propname).append(suffixSet);

        return buffer.toString();
    }

    private static String parse_getValue(String xml, String propname)
            throws HoneycombTestException {
        // The XML needs to be of the form
        // ...
        // <propstat>
        // <prop>
        // <HCFS:expiration>...value...</HCFS:expiration>
        // </prop>
        // ...
        // </propstat>

        // We should really parse the XML here. For now: just look for
        // the opening and closing tags and extract everything between
        // them.

        String emptyTag = "<" +  propname + "/>";
        if (xml.indexOf(emptyTag) >= 0)
            return "";

        String openTag = "<" + propname + ">";
        String closeTag = "</" + propname + ">";

        int oPos = xml.indexOf(openTag);
        int cPos = xml.indexOf(closeTag);

        if (cPos < 0 || oPos < 0 || oPos > cPos)
            throw new HoneycombTestException("XML not well-formed");

        return xml.substring(oPos + openTag.length(), cPos);
    }

    private static String getXMLns(String xml, String url)
            throws HoneycombTestException {
        String tag = "=\"" + url;
        int pos = xml.indexOf(tag);
        if (pos < 0) {
            String msg = "Couldn't parse reply: no Honeycomb xmlns found " +
                "in \"" + xml + "\" -- looking for <" + tag + ">";
            throw new HoneycombTestException(msg);
        }

        String prefix = xml.substring(0, pos);
        int nsPos = prefix.lastIndexOf("xmlns");
        if (nsPos < 0) {
            String msg = "Couldn't parse reply: bad Honeycomb xmlns found";
            throw new HoneycombTestException(msg);
        }
        String ns = prefix.substring(nsPos);

        // ns is either "xmlns" or "xmlns:HCFS"
        int colon = ns.indexOf(':');
        if (colon < 0)
            return "";
        return ns.substring(colon + 1);
    }

    //////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    // Manual tests for returned XML

    private static final String xmlExp1 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><href>/webav/View1/Users/Miles/So%20What</href><propstat xmlns=\"DAV:\" xmlns:HCFS=\"http://www.sun.com/honeycomb/\"><prop><HCFS:expiration>2010-12-31T00:00:00Z</HCFS:expiration></prop></propstat>";
    private static final String xmlExp2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><DAV:propstat xmlns=\"http://www.sun.com/honeycomb/\"><DAV:prop><expiration>2010-12-31T00:00:00Z</expiration></DAV:prop></DAV:propstat>";
    private static final String xmlExp3 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><DAV:propstat xmlns=\"http://www.sun.com/honeycomb/\"><DAV:prop><expiration/></DAV:prop></DAV:propstat>";

    private static final String xmlLegal1 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><href>/webav/View1/Users/Miles/So%20What</href><propstat xmlns=\"DAV:\" xmlns:HCFS=\"http://www.sun.com/honeycomb/\"><prop><HCFS:legalholds><HCFS:case>A</HCFS:case><HCFS:case>B</HCFS:case></HCFS:legalholds></prop></propstat>";
    private static final String xmlLegal2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><DAV:propstat xmlns=\"http://www.sun.com/honeycomb/\"><DAV:prop><legalholds><case>A</case><case>Sun v. &lt;Microsoft&gt;</case></legalholds></DAV:prop></DAV:propstat>";
    private static final String xmlLegal3 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><DAV:propstat xmlns=\"http://www.sun.com/honeycomb/\"><DAV:prop><legalholds/></DAV:prop></DAV:propstat>";

    private static void doLine(String line, String tag)
            throws HoneycombTestException {
        String xmlns = getXMLns(line, HC_XMLNS) + ":";
        if (xmlns.length() == 1) // HC is the default namespace
            xmlns = "";

        if (tag.equals("legalholds")) {

            String holdXML = parse_getValue(line, xmlns + "legalholds");
            String[] holds = decodeHolds(holdXML, xmlns + "case");

            System.out.print("legalholds = { ");
            for (int i = 0; i < holds.length; i++)
                System.out.print("\"" + holds[i] + "\" ");
            System.out.println("}");
        }
        else
            System.out.println(tag + " = \"" +
                               parse_getValue(line, xmlns + tag) + "\"");
    }

    public static void main(String[] args) {
        BufferedReader console =
            new BufferedReader(new InputStreamReader(System.in));
        String line;
        String[] cases;

        // Test both when HCFS is and isn't the default namespace

        try {
            doLine(xmlExp1, "expiration");
            doLine(xmlExp2, "expiration");
            doLine(xmlExp3, "expiration");
            doLine(xmlLegal1, "legalholds");
            doLine(xmlLegal2, "legalholds");
            doLine(xmlLegal3, "legalholds");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
