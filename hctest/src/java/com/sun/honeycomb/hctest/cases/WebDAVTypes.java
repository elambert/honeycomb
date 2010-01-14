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
    <fsView name="qatypes" filename="${filename}">
      <attribute name="longsmall"/>           <!-- long -->
      <attribute name="dateearly"/>           <!-- date -->
      <attribute name="timeearly"/>           <!-- time -->
      <attribute name="timestampearly"/>      <!-- timestamp -->
      <attribute name="charnull"/>            <!-- char[32] -->
      <attribute name="stringnull"/>          <!-- string[32] -->
      <attribute name="binaryA"/>             <!-- byte[100] -->
      <attribute name="doublechunked"/>       <!-- double -->
    </fsView>
 * </PRE>
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class WebDAVTypes extends HoneycombLocalSuite {

    private static Random random = new Random(System.currentTimeMillis());

    private NameValueObjectArchive api = null;
    private WebDAVer dav = null;

    private String vip = null;
    private int port = 0;

    // Size of files created for the tests
    private static final int FILESIZE = 100;

    private static SimpleDateFormat tsFormatter = null;

    static {
        tsFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        tsFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public String help() {
        return("\tWebDAV Types tests\n");
    }

    public void setUp() throws Throwable {
        Log.DEBUG("WebDAVTypes.setUp() called");
        super.setUp();

        TestCase skip = createTestCase("WebDAVTypes","Test put/retrieve etc.");
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
        }
        catch (Exception e) {
            e.printStackTrace();
            TestCase self = createTestCase("WebDAV", "getDAVConnection");
            self.testFailed("Couldn't get webdav connection: " + e);
        }

        // Make sure the view exists
        if (!dav.exists("/webdav/qatypes/"))
            dav = null;
    }

    public void tearDown() throws Throwable {
        Log.DEBUG("WebDAVTypes.tearDown() called");
        super.tearDown();
    }

    //////////////////////////////////////////////////////////////////////
    // Tests begin here

    public void testPut_Retrieve() {
        // PUT a file into a view, then retrieve the file's metadata
        // and see how it looks. See if the file can be retrieved from
        // the URL it was stored to.

        TestCase self = initTest("Put_Retrieve");
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

            Log.INFO("WebDAVTypes: PUT \"" + url + "\" with WebDAV, OID " +
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

    public void testStore_Get() {
        // Store a file, then GET via WebDAV and check the values
        TestCase self = initTest("Store_Get");
        if (self == null)
            return;

        try {
            Map file = apiCreateFile();

            String oid = (String) file.get("system.object_id");
            String url = getURL(file);

            Log.INFO("WebDAVTypes: Stored " + oid +
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
        TestCase self = createTestCase("WebDAVTypes", testName);

        // Groups these tests are part of

        self.addTag(Tag.REGRESSION);
        self.addTag(Tag.SMOKE);

        self.addTag(Tag.UNIT);
        self.addTag(Tag.QUICK);

        self.addTag(HoneycombTag.WEBDAV);

        if (self.excludeCase())
            return null;

        if (dav == null) {
            self.testFailed("Failed to get webdav connection");
            return null;
        }

        Log.INFO("++++++ WebDAVTypes.test" + testName + " ++++++");

        return self;
    }

    //////////////////////////////////////////////////////////////////////
    // Helpers


    /**
     * Create a random new file using WebDAV and return its
     * metadata
     */
    private Map webdavCreateFile()
            throws HoneycombTestException, ArchiveException, IOException {
        CmdResult rc;
        HashMap md = newFactoryMD();
        addTypesMD(md);

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
        CmdResult rc;
        HashMap md = newFactoryMD();
        addTypesMD(md);

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

    //////////////////////////////////////////////////////////////////////
    // Machinery for creating metadata records

    /* View used:

    <fsView name="qatypes" filename="${filename}">
      <attribute name="longsmall"/>           <!-- long -->
      <attribute name="dateearly"/>           <!-- date -->
      <attribute name="timeearly"/>           <!-- time -->
      <attribute name="timestampearly"/>      <!-- timestamp -->
      <attribute name="charnull"/>            <!-- char[32] -->
      <attribute name="stringnull"/>          <!-- string[32] -->
      <attribute name="binaryA"/>             <!-- byte[100] -->
      <attribute name="doublechunked"/>       <!-- double -->
    </fsView>

    */
    private static final int T_Unknown = 0; // invalid value
    private static final int T_Long = 1;
    private static final int T_Date = 2;
    private static final int T_Time = 3;
    private static final int T_Timestamp = 4;
    private static final int T_Char = 5;
    private static final int T_String = 6;
    private static final int T_Binary = 7;
    private static final int T_Double = 8;

    private static class Attribute {
        int type;
        String name;
        int size;
        Attribute(int type, String name, int size) {
            this.type = type;
            this.name = name;
            this.size = size;
        }
        Attribute(int type, String name) {
            this(type, name, 0);
        }

        Object getRandomValue() {
            switch (type) {

            case T_Long:
                return new Long(random.nextLong());

            case T_Date:
                switch (random.nextInt(10)) {
                case 0: return Date.valueOf("1999-12-31");
                case 1: return Date.valueOf("2038-1-1");
                case 2: return Date.valueOf("1974-2-12");
                case 3: return Date.valueOf("2000-11-30");
                case 4: return Date.valueOf("2003-10-12");
                default:
                    // Approximately 400 years around the epoch
                    return new Date((long)(1.0e13*random.nextFloat()));
                }

            case T_Time:
                return new Time(random.nextInt(86400));

            case T_Timestamp:
                // Approximately 400 years around the epoch
                return new Timestamp((long)(1.0e13*random.nextFloat()));

            case T_Char:
                return DavTestSchema.newRandomFixedLengthString(size);

            case T_String:
                return DavTestSchema.newRandomString(size);

            case T_Binary:
                byte[] data = new byte[size];
                random.nextBytes(data);
                return data;

            case T_Double:
                return new Double(random.nextDouble());

            default:
                throw new RuntimeException("Unexpected value in case " + type);
            }
        }

        String format(Object value) {
            switch (type) {

            case T_Date:
            case T_Char:
            case T_Time:
            case T_String:
            case T_Long:
            case T_Double:
                return value.toString();

            case T_Timestamp:
                return tsFormatter.format((Timestamp)value);

            case T_Binary:
                return ByteArrays.toHexString((byte[])value);

            default:
                throw new RuntimeException("Unexpected value in case " + type);
            }
        }

    }

    private static Attribute[] attributes = {
        new Attribute(T_Unknown, ""),
        new Attribute(T_Long, "longsmall"),
        new Attribute(T_Date, "dateearly"),
        new Attribute(T_Time, "timeearly"),
        new Attribute(T_Timestamp, "timestampearly"),
        new Attribute(T_Char, "charnull", 32),
        new Attribute(T_String, "stringnull", 32),
        new Attribute(T_Binary, "binaryA", 100),
        new Attribute(T_Double, "doublechunked"),
        new Attribute(T_String, "filename", 64)
    };

    private void addTypesMD(Map md) {
        for (int i = 1; i < attributes.length; i++) {
            Attribute attr = attributes[i];
            md.put(attr.name, attr.getRandomValue());
        }
    }

    private String getURL(Map md) {
        StringBuffer sb = new StringBuffer();

        sb.append("/webdav/qatypes");

        for (int i = 1; i < attributes.length; i++) {
            Attribute attr = attributes[i];
            String fmt = attr.format(md.get(attr.name));
            sb.append('/').append(fmt);
        }

        Log.DEBUG("++++ Returning \"" + sb + "\"");

        return sb.toString();
    }

}
