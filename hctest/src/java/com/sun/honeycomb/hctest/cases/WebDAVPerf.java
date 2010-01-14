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
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

import com.ice.tar.TarArchive;
import com.ice.tar.TarEntry;
import com.ice.tar.TarOutputStream;

import java.security.MessageDigest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;

/**
 *  Test webdav perf beyond what's in SimplePerf.
 */
public class WebDAVPerf extends HoneycombLocalSuite {

    private WebDAVer wd1 = null;
    private DavTestSchema davtest = null;
    CmdResult schemaResult = null;
    boolean bCalcHash = true;

    public WebDAVPerf() {
        super();
    }

    public String help() {
        return("\tWebDAV single-stream perf tests\n");
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();

        String vip = testBed.getDataVIP();
        try {
            wd1 = new WebDAVer(vip);
            schemaResult = getSchema();
        }
        catch (Exception e) {
            Log.ERROR("Getting webdav connection: " + e);
            wd1 = null;
            TestCase self = createTestCase("WebDAVPerf", "getDAVConn");
            self.testFailed("Get schema over webdav connection failed: " + e);
        }

        davtest = new DavTestSchema();

        // turn off hashing, since it interferes with timing
        bCalcHash = TestBed.doHash;
        TestBed.doHash = false;
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        TestBed.doHash = bCalcHash;
        super.tearDown();
    }

    private void check4DAVTestSchema(TestCase self) {
        if (schemaResult == null  ||  !schemaResult.pass) {
            self.addTag(Tag.MISSINGDEP, "failed to get schema");
            Log.INFO("failed to get schema");
            return;
        }

        if (!HCUtil.schemaHasAttribute(schemaResult.nvs, "davtest.string1")) {
            self.addTag(Tag.MISSINGDEP, "davtest schema not loaded");
            Log.INFO("davtest schema not loaded");
        }
    }

    public void testA_Perf1k() {

        final int NFILES = 1000;

        Log.INFO("%%%%%%%%% testA_Perf");
        TestCase self = createTestCase("WebDAVPerf", "Perf1k");
        self.addTag(HoneycombTag.WEBDAV);
        if (wd1 == null) {
            self.addTag(Tag.MISSINGDEP, "failed to get webdav conn");
            Log.INFO("failed to get webdav conn");
        }
        check4DAVTestSchema(self);

        if (self.excludeCase()) return;

        try {
            //CmdResult stores[] = new CmdResult[NFILES];
            CmdResult puts[] = new CmdResult[NFILES];
            CmdResult gets[] = new CmdResult[NFILES];

            //
            //  store some 1k files
            //
/*
            for (int i=0; i<NFILES; i++) {
                HashMap hm = newFactoryMD();
                davtest.addDavMD(hm, 8);
                stores[i] = store(1024, hm);
            }
*/
            long t0 = System.currentTimeMillis();
            for (int i=0; i<NFILES; i++) {
                HashMap hm = newFactoryMD();
                davtest.setRandomStringSize(8);
                davtest.addDavMD(hm);
                String url = davtest.getDavTestName(hm, 1);
                puts[i] = wd1.putFile(url, 1024, false); // calcHash=false
                if (!puts[i].pass) {
                    self.testFailed("PUT #" + (i+1) +
                                    " failed: " + puts[i].getExceptions());
                    return;
                }
            }
            t0 = System.currentTimeMillis() - t0;
            //
            //  get stats
            //
            Statistic st1 = new Statistic("1k PUT");
            for (int i=0; i<NFILES; i++)
                st1.addValue(puts[i].time);

            Log.INFO("1k PUT ops/sec: " + ((float)NFILES * 1000 / (float)t0));
            Log.INFO(st1.toString());

            //
            //  get the files
            //
            long t1 = System.currentTimeMillis();
            long t2 = t1;
            for (int i=0; i<NFILES; i++) {
                //String vname = davtest.getDavTestName(stores[i].mdMap, 1);
                gets[i] = wd1.getFile(puts[i].filename, false);
                if (!gets[i].pass) {
                    self.testFailed("GET #" + (i+1) + " failed: " +
                                    gets[i].getExceptions());
                    return;
                }
                if (i == 9)
                    t2 = System.currentTimeMillis();
            }
            long t3 = System.currentTimeMillis();
            t1 = t3 - t1;
            t2 = t3 - t2;

            //
            //  get stats
            //
            Statistic st2 = new Statistic("1k GET");
            for (int i=0; i<NFILES; i++)
                st2.addValue(gets[i].time);

            Log.INFO("1k PUT ops/sec: " + ((float)NFILES * 1000 / (float)t0));
            Log.INFO(st1.toString());

            Log.INFO("1k GET ops/sec: " + ((float)NFILES * 1000 / (float)t1));
            Log.INFO("1k GET ops/sec after 1st 10: " + 
                                     ((float)(NFILES-10) * 1000 / (float)t2));
            Log.INFO(st2.toString());

            self.testPassed("ok");
        } catch (Exception e) {
            self.testFailed("Unexpected exception: " + e);
        }
    }

    public void testB_Perf100m() {

        final int NFILES = 10;
        final int SIZE = 100 * 1024 * 1024;

        Log.INFO("%%%%%%%%% testB_Perf100m");
        TestCase self = createTestCase("WebDAVPerf", "Perf100m");
        self.addTag(HoneycombTag.WEBDAV);
        if (wd1 == null) {
            self.addTag(Tag.MISSINGDEP, "failed to get webdav conn");
            Log.INFO("failed to get webdav conn");
        }
        check4DAVTestSchema(self);

        if (self.excludeCase()) return;

        try {

            //CmdResult stores[] = new CmdResult[NFILES];
            CmdResult puts[] = new CmdResult[NFILES];
            CmdResult gets[] = new CmdResult[NFILES];

            //
            //  store some 100m files
            //
            for (int i=0; i<NFILES; i++) {
                HashMap hm = newFactoryMD();
                davtest.setRandomStringSize(8);
                davtest.addDavMD(hm);
                // stores[i] = store(SIZE, hm);
                String url = davtest.getDavTestName(hm, 1);
                puts[i] = wd1.putFile(url, SIZE, false); // calcHash=false
                if (!puts[i].pass) {
                    self.testFailed("PUT #" + (i+1) +
                                    " failed: " + puts[i].getExceptions());
                    return;
                }
            }
            //
            //  gather stats
            //
            BandwidthStatistic st1 = new BandwidthStatistic("100m PUT");
            for (int i=0; i<NFILES; i++)
                st1.add(puts[i].time, SIZE);

            //
            //  get the files
            //
            long t1 = System.currentTimeMillis();
            for (int i=0; i<NFILES; i++) {
                //String vname = davtest.getDavTestName(stores[i].mdMap, 1);
                gets[i] = wd1.getFile(puts[i].filename, false);
                if (!gets[i].pass) {
                    self.testFailed("GET #" + (i+1) + " failed");
                    return;
                }
            }
            t1 = System.currentTimeMillis() - t1;
            //
            //  check the results
            //
            BandwidthStatistic st2 = new BandwidthStatistic("100m GET");
            for (int i=0; i<NFILES; i++)
                st2.add(gets[i].time, SIZE);
            Log.INFO(st1.toString());
            Log.INFO(st2.toString());
            Log.INFO("100m GET ops/sec: " + ((float)NFILES * 1000 / (float)t1));

            self.testPassed("ok");
        } catch (Exception e) {
            self.testFailed("Unexpected exception: " + e);
        }
    }
}
