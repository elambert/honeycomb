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

import java.io.File;
import java.util.ArrayList;

/**
 * Validate that the simplest ops (store and retrieve) have acceptable 
 * performance.
 */
public class SimplePerf extends HoneycombLocalSuite {

    private CmdResult storeResult = null;
    private CmdResult addMDresult = null;
    private CmdResult schemaResult = null;
    private WebDAVer wd = null;
    private boolean bCalcHash = true;

    // If the store fails, we will fail the other tests due to dependencies
    private boolean storedOK = false;
    private boolean addedMdOK = false;
    
    private int fudge = 1;

    public SimplePerf() {
        super();
    }

    public String help() {
        return("\tValidate that the simplest ops have acceptable performance\n");
    }

    /**
     * Store a file.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        bCalcHash = TestBed.doHash;
        TestBed.doHash = false;

        String emulatorStr =
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_CLUSTER);
        if (emulatorStr != null) {
            fudge = 10;
            Log.INFO("RUN AGAINST THE EMULATOR (nocluster defined)");
        }
        try {
            schemaResult = getSchema();
        } catch (Exception e) {
            TestCase self = createTestCase("SimplePerf", "getSchema");
            self.testFailed("Getting schema: " + e);
        }

    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        TestBed.doHash = bCalcHash;
        super.tearDown();
    }

    private void checkForFilesystemViews(TestCase self) {
        if (schemaResult == null  ||  !schemaResult.pass) {
            self.addTag(Tag.MISSINGDEP, "failed to get schema");
            Log.INFO("failed to get schema");
            return;
        }

        if (!HCUtil.schemaHasAttribute(schemaResult.nvs,
                                       "filesystemviewsarepresent")) {
            self.addTag(Tag.MISSINGDEP, "filesystem views are not present");
            Log.INFO("filesystem views are not loaded");
        }

    }

    public void testA_TinyFilePerformance() {
        doStore("StoreSingleByteFile", 1, 1500);
        doRetrieve("RetrieveSingleByteFile", 1, 150);
        doRetrieve("RetrieveSingleByteFile/cached", 1, 50);
    }
    public void testB_1kFilePerformance() {
        doStore("StoreOneKbyteFile", 1024, 2000);
        //doAddFactoryTestMD("AddFactoryTestMD", 800);
        doRetrieve("RetrieveOneKbyteFile", 1024, 200);
        doRetrieve("RetrieveOneKbyteFile/cached", 1024, 100);
    }
    public void testC_1mFilePerformance() {
        doStore("StoreOneMbyteFile", 1024*1024, 2500);
        //doAddFactoryTestMD("AddFactoryTestMD", 800);
        doRetrieve("RetrieveOneMbyteFile", 1024*1024, 500);
        doRetrieve("RetrieveOneMbyteFile/cached", 1024*1024, 200);
    }
    public void testD_100mFilePerformance() {
        doStore("Store100MbyteFile", 100*1024*1024, 60000);
        //doAddFactoryTestMD("AddFactoryTestMD", 800);
        doRetrieve("Retrieve100MbyteFile", 100*1024*1024, 10000);
        doRetrieve("Retrieve100MbyteFile/cached", 100*1024*1024, 10000);
    }
    public void testE_MDPerformance() {
        doAddFactoryTestMD("AddFactoryTestMD", 800);
        //doQueryMD("QueryFactoryMD", 100);
    }
    public void testF_WebDAVPerformance() {
        //
        //  make failed result if can't get conn
        //
        try {
            wd = new WebDAVer(testBed.getDataVIP() + ":" + testBed.getDataPort());
        } catch (Exception e) {
            Log.ERROR("Getting webdav connection: " + e);
            wd = null;
            TestCase self = createTestCase("SimplePerf", "getDAVConn");
            self.testFailed("Getting webdav connection: " + e);
            return;
        }

        doStore("StoreOneKbyteFile", 1024, 2000);
        doAddFactoryTestMD("AddFactoryTestMD", 800);

        // (depth == 0) => only the object itself
        doWebDAVList("RootWebDAVList", 0, 200);
        
        // The following test should FAIL until we add code to handle
        // external->internal OID translation in queries.
        doQuery("QueryGet1k",1500);

        // (depth == 1) => object and all its children
        doWebDAVList("TopLevelWebDAVList", 1, 200);
        
        // The following test should FAIL until we add code to handle
        // external->internal OID translation in queries.
        doQuery("QueryGet1k",1500);

        doWebDAVGet("WebDAVGet1k", 1500);
        doWebDAVGet("WebDAVGet1k/cached", 1000);

        doStore("Store1MFile", 1024*1024, 2500);
        doAddFactoryTestMD("AddFactoryTestMD", 800);
        doWebDAVGet("WebDAVGet1m", 1500);
        doWebDAVGet("WebDAVGet1m/cached", 1000);

        doStore("Store100MFile", 100*1024*1024, 60000);
        doAddFactoryTestMD("AddFactoryTestMD", 800);
        doWebDAVGet("WebDAVGet100m", 22000);
        doWebDAVGet("WebDAVGet100m/cached", 20000);

    }

    private void addTags(TestCase self) {
        self.addTag(Tag.SMOKE);
        //self.addTag(Tag.REGRESSION);
        self.addTag(Tag.POSITIVE);
        self.addTag(HoneycombTag.PERF_BASIC);
        self.addTag(HoneycombTag.EMULATOR);
    }

    private void doStore(String name, long size, long time) {

        storedOK = false;

        TestCase self = createTestCase("SimplePerf", name);
        addTags(self);
        
        if (self.excludeCase()) return;

        //Log.INFO("Starting store now");
        try {
            storeResult = store(size);
            //Log.INFO("Stored oid " + storeResult.mdoid);
        } catch (HoneycombTestException hte) {
            self.testFailed("Store failed: " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            self.testFailed(name + ": " + hte);
            return;
        }
        evalResult(self, name, storeResult, time);

        storedOK = true;
    }

    /**
     * Retrieve a file.
     */    
    private void doRetrieve(String name, long size, long time) {
        
        TestCase self = createTestCase("SimplePerf", name);
        addTags(self);
        
        if (!storedOK) 
            self.addTag(Tag.MISSINGDEP, "failed some dependency");

        if (self.excludeCase()) return;

        CmdResult cr;
        try {
            cr = retrieve(storeResult.mdoid);
            //cr = retrieve(addMDresult.mdoid);
            //Log.INFO("Retrieved oid " + storeResult.mdoid);
        } catch (HoneycombTestException hte) {
            Log.ERROR("Retrieve failed [oid " + storeResult.mdoid + "]: " +hte);
            Log.DEBUG(Log.stackTrace(hte));
            self.testFailed(name + ": " + hte);
            return;
        }
        evalResult(self, name, cr, time);
    }

    private void doAddFactoryTestMD(String name, long time) {

        TestCase self = createTestCase("SimplePerf", name);
        addTags(self);
        
        if (!storedOK) 
            self.addTag(Tag.MISSINGDEP, "failed some dependency");

        if (self.excludeCase()) return;

        try {
            addMDresult = addFactoryMD(storeResult.mdoid);
        } catch (HoneycombTestException hte) {
            Log.ERROR("addFactoryMD failed [oid " + storeResult.mdoid + "]: " +hte);
            Log.DEBUG(Log.stackTrace(hte));
            self.testFailed(name + ": " + hte);
            return;
        }
        if (addMDresult.pass) {
            addedMdOK = true;
        } else {
            Log.ERROR("addFactoryMD failed [oid " + storeResult.mdoid + "]: " +
                                            addMDresult);
            self.testFailed(name + ": " + addMDresult);
            return;
        }
        evalResult(self, name, addMDresult, time);
    }


    private void doQuery(String name, long time) {

        TestCase self = createTestCase("SimplePerf", name);
        addTags(self);
        
        if (!storedOK || !addedMdOK) 
            self.addTag(Tag.MISSINGDEP, "failed some dependency - no store md");

        if (self.excludeCase()) return;

        CmdResult cr = null;
        try {
            //
            //  construct path via the oidByGidUid view
            //

            Object o = addMDresult.mdMap.get("filesystem.gid");
            if (o == null)
                throw new HoneycombTestException(name + ": no filesystem.gid in md");
            String gid = o.toString();

            o = addMDresult.mdMap.get("filesystem.uid");
            if (o == null)
                throw new HoneycombTestException(name + ": no filesystem.uid in md");
            String uid = o.toString();

            String query = "filesystem.uid="+uid+" and filesystem.gid="+gid+
                "and system.object_id={objectid '"+addMDresult.mdoid+"'}";
            cr = query(query);
            QueryResultSet qrs = (QueryResultSet) cr.rs;

            boolean found = false;
            while (qrs.next()) {
                ObjectIdentifier oid = qrs.getObjectIdentifier();
                if ((addMDresult.mdoid).equals(oid.toString())) {
                    self.testPassed("Found oid " + oid +
                                    " in query results");
                    found = true;
                    break;
                }
            }

            if (!found) {
                self.testFailed("We didn't find our expected oid " +
                                addMDresult.mdoid + " for query " 
                                + query);
            }

        } catch (Throwable e) {
            Log.ERROR(name + ": " + e);
            self.testFailed(name + ": " + e);
        }

        evalResult(self, name, cr, time);

    }

    private void doWebDAVList(String name, int depth, long time) {

        TestCase self = createTestCase("SimplePerf", name);
        addTags(self);
        
        if (!storedOK || !addedMdOK) 
            self.addTag(Tag.MISSINGDEP, "failed dependency - no store md");
        if (wd == null)
            self.addTag(Tag.MISSINGDEP, "failed dependency - no webdav");
        checkForFilesystemViews(self);

        if (self.excludeCase()) return;

        CmdResult cr = null;
        try {
            cr = wd.list("/webdav/", depth);
            Log.INFO("==== list:\n" + cr.string);
        } catch (Throwable e) {
            Log.ERROR(name + ": " + e);
            self.testFailed(name + ": " + e);
        }
        evalResult(self, name, cr, time);
    }

    private void doWebDAVGet(String name, long time) {

        TestCase self = createTestCase("SimplePerf", name);
        addTags(self);
        
        if (!storedOK || !addedMdOK) 
            self.addTag(Tag.MISSINGDEP, "failed some dependency - no store md");
        if (wd == null)
            self.addTag(Tag.MISSINGDEP, "failed to get webdav conn");

        checkForFilesystemViews(self);

        if (self.excludeCase()) return;

        CmdResult cr = null;
        try {
            //
            //  construct path via the oidByGidUid view
            //
            Object o = addMDresult.mdMap.get("filesystem.gid");
            if (o == null)
                throw new HoneycombTestException(name + ": no filesystem.gid in md");
            String gid = o.toString();
            o = addMDresult.mdMap.get("filesystem.uid");
            if (o == null)
                throw new HoneycombTestException(name + ": no filesystem.uid in md");
            String uid = o.toString();
            String path = "/webdav/oidByGidUid/" + gid + "/" + uid + "/" +
                          addMDresult.mdoid;
            cr = wd.getFile(path, false);
//%%
        } catch (Throwable e) {
            Log.ERROR(name + ": " + e);
            self.testFailed(name + ": " + e);
        }
        if (cr != null) {
            try {
                File f = new File(cr.filename);
                f.delete();
            } catch (Exception e) {
                Log.ERROR("deleting file: " + e);
            }
        }
        evalResult(self, name, cr, time);
    }

    private void evalResult(TestCase self, String name, CmdResult cr, long time) {
        if (cr == null)
            self.testFailed(name + ": result is null (?)");
        if (!cr.pass) {
            self.testFailed(name + ": " + cr.getExceptions());
            return;
        }
        self.postMetric(new Metric(name, "msec", cr.time));

        time *= fudge;

        if (cr.time > time) {
            self.testFailed(name + " took too long: " + cr.time + 
                            " ms (expected < " + time + ")");
        } else {
            self.testPassed(name + " took " + cr.time + " ms");
        }        
    }
}
