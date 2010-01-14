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
 *  Test webdav functionality beyond what's in SimplePerf.
 */
public class WebDAV extends HoneycombLocalSuite {

    private WebDAVer wd1 = null;
    private WebDAVer wd2 = null;
    private DavTestSchema davtest = null;
    private LinkedList expected_files = new LinkedList();
    private LinkedList list_files = new LinkedList();
    CmdResult schemaResult = null;
    CmdResult tarContentFile = new CmdResult();

    public WebDAV() {
        super();
        davtest = new DavTestSchema();
    }

    public String help() {
        return("\tWebDAV tests\n");
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();

        String vip = testBed.getDataVIP();
        String vip2 = testBed.getRoundRobinNodeIP();
        if (vip2.equals(vip)) {
            vip2 = testBed.getRoundRobinNodeIP();
            if (vip2.equals(vip))
                vip2 = null;
        }
        try {
            wd1 = new WebDAVer(vip);
            schemaResult = getSchema();
            if (vip2 != null)
                wd2 = new WebDAVer(vip2);
        } catch (Exception e) {
            Log.ERROR("Getting webdav connection: " + e);
            wd1 = null;
            wd2 = null;
            TestCase self = createTestCase("WebDAV", "getDAVConn");
            self.testFailed("Get schema over webdav connection failed: " + e);
        }
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
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
/*
    public void test0_PropFindInfinite() {
        Log.INFO("%%%%%%%%% testA_PropFindInfinite");
        TestCase self = createTestCase("WebDAV", "PropFindInfinite");
        self.addTag(HoneycombTag.WEBDAV);
        if (wd1 == null) {
            self.addTag(Tag.MISSINGDEP, "failed to get webdav conn");
            Log.INFO("failed to get webdav conn");
        }
        if (self.excludeCase()) return;

        try {
            CmdResult cr = wd1.list("/webdav/", WebDAVer.ALL);
            if (cr.pass) {
                self.testPassed("ok");
            } else {
                self.testFailed("failed: " + cr.getExceptions());
            }
        } catch (Exception e) {
            self.testFailed("failed: " + e);
            return;
        }
    }
*/

    public void testA_FsViews() {
        Log.INFO("%%%%%%%%% testA_FsViews");
        TestCase self = createTestCase("WebDAV", "FsViews");
        self.addTag(HoneycombTag.WEBDAV);
        self.addTag(HoneycombTag.EMULATOR);
        if (wd1 == null) {
            self.addTag(Tag.MISSINGDEP, "failed to get webdav conn");
            Log.INFO("failed to get webdav conn");
        }
        check4DAVTestSchema(self);

        if (self.excludeCase()) return;

        try {

            //
            //  store something w/ unique 'davtest' schema
            //
            HashMap hm = newFactoryMD();
            davtest.setRandomStringSize(12);
            davtest.addDavMD(hm);
            CmdResult storeResult = store(1024, hm);

            //
            //  get it w/ the davtest views
            //
            String vname = davtest.getDavTestName(hm, 1);
            if (get(wd1, vname, storeResult, "GetDavTest1", true)) {
                self.testFailed("GET #1 failed");
                return;
            }
            vname = davtest.getDavTestName(hm, 2);
            if (get(wd1, vname, storeResult, "GetDavTest2", true)) {
                self.testFailed("GET #2 failed");
                return;
            }
            vname = davtest.getDavTestName(hm, 3);
            if (get(wd1, vname, storeResult, "GetDavTest3", true)) {
                self.testFailed("GET #3 failed");
                return;
            }
            vname = davtest.getDavTestName(hm, 4);
            if (get(wd1, vname, storeResult, "GetDavTest4", true)) {
                self.testFailed("GET #4 failed");
                return;
            }
            // this works even tho double precision might
            // be expected to be a problem
            vname = davtest.getDavTestName(hm, 5);
            if (get(wd1, vname, storeResult, "GetDavTest5", true)) {
                self.testFailed("GET #5 failed");
                return;
            }
            self.testPassed("ok");
        } catch (Exception e) {
            self.testFailed("Unexpected exception: " + e);
        }
    }

    private String chop(String path) throws IndexOutOfBoundsException {
        int i = path.lastIndexOf('/');
        i = path.lastIndexOf('/', i-1);
        return path.substring(0, i+1);
    }

    public void testB_CacheDelete() {

        if (wd2 != null)
            Log.INFO("%%%%%%%%% testB_CacheDelete w/ multi-node");
        else
            Log.INFO("%%%%%%%%% testB_CacheDelete");

        TestCase self = createTestCase("WebDAV", "CacheDelete");
        self.addTag(HoneycombTag.WEBDAV);
        if (wd1 == null)
            self.addTag(Tag.MISSINGDEP, "failed to get webdav conn");
        check4DAVTestSchema(self);
        if (self.excludeCase()) return;

        try {
            //
            //  store something with unique md
            //
            HashMap hm = newFactoryMD();
            davtest.setRandomStringSize(20);
            davtest.addDavMD(hm);
            CmdResult storeResult = store(1024, hm);
            if (!storeResult.pass) {
                self.testFailed("STORE subtest failed");
                return;
            }

            //
            //  get it to put it in cache
            //
            String vname = davtest.getDavTestName(hm, 1);
            if (get(wd1, vname, storeResult, "GetDavTest1ForDelete", true)) {
                self.testFailed("GET subtest failed");
                return;
            }

            //
            //  if 2nd vip specd (switch=other) get it from that node too
            //
            if (wd2 != null) {
                if (get(wd2, vname, storeResult, "GetDavTest1ForDeleteVip2", true)) {
                    self.testFailed("GET subtest 2 failed");
                    return;
                }
            }

            //
            //  delete it
            //
            CmdResult cr = delete(storeResult.mdoid);
            if (!cr.pass) {
                self.testFailed("Delete subtest failed");
                return;
            }

            //
            //  try to look it up
            //
            cr = wd1.list(vname, 0);
            if (cr.pass) {
                self.testFailed("List of delete object succeeded");
                return;
            }
            Log.INFO("list failed = pass");

            //
            //  try to get it
            //
            if (get(wd1, vname, storeResult, "GetDavTest1Name.deleted", false)) {
                self.testFailed("Get of delete object succeeded");
                return;
            }
/*
            SKIPPED - dirs aren't 'real' anyway
            //
            //  make sure dirs are gone too - depends on the
            //  random dir values.
            //
            String vname2 = chop(vname);
            while (!vname2.equals("/webdav/davtest1/")) {
                Log.INFO("checking path " + vname2);
                cr = wd1.list(vname2, 0);
                if (cr.pass) {
                    self.testFailed("List of delete dir succeeded");
                    return;
                }
                vname2 = chop(vname2);
            }
*/
            //
            //  try lookup/get via different node if avail
            //
            if (wd2 != null) {
                Log.INFO("%%%%%%%%% trying list/get via different node..");
                cr = wd2.list(vname, 0);
                if (cr.pass) {
                    self.testFailed("List 2 of delete object succeeded");
                    return;
                }
                Log.INFO("list failed = pass");
                if (get(wd2, vname, storeResult, "GetDavTest1Name.deleted2", false)) {
                    self.testFailed("Get 2 of delete object succeeded");
                    return;
                }
            }

        } catch (Exception e) {
            self.testFailed("Unexpected exception: " + e);
            return;
        }
        self.testPassed("ok");
    }

    /**
     *  Store a tar file and browse its contents via an fsView
     *  with zopen="tar,cpio"
     */
    public void testC_Tar() {
//if(true)
//return;
        Log.INFO("%%%%%%%%% testC_Tar");

        TestCase self = createTestCase("WebDAV", "TarTest");
        self.addTag(HoneycombTag.WEBDAV);
        if (wd1 == null)
            self.addTag(Tag.MISSINGDEP, "failed to get webdav conn");
        if (self.excludeCase()) return;

        //
        //  make/store tar file
        //
        CmdResult storeTar = mkTar("testTar");
        if (!storeTar.pass) {
            self.testFailed("making/storing tarfile: " + 
                                           storeTar.getExceptions());
            return;
        }

        //
        //  use /webdav/oidByGidUid/ view to get file
        //
        Long uidL = (Long) storeTar.mdMap.get("filesystem.uid");
        Long gidL = (Long) storeTar.mdMap.get("filesystem.gid");
        String getTar = "/webdav/oidByGidUid/" + gidL + "/" + uidL + "/" +
                                                            storeTar.mdoid;
        //
        //  webdav-retrieve tar file and check hash
        //
        if (get(wd1, getTar, storeTar, "WebDAVGet", true)) {
            self.testFailed("GET subtest failed");
            return;
        }

        //
        //  webdav-list contents of tar and check using 'zopen' view
        //  (%2F = escaped '/')
        //
        String viewTar = "/webdav/oidByGidUidWithTar/" +    
                                  gidL + "/" + uidL + "/" + 
                                  storeTar.mdoid;
        if (listTar(viewTar)) {
            self.testFailed("ListTar subtest failed");
            return;
        }

        //
        //  retrieve the files in the tar
        //
        Iterator it = list_files.iterator();
        while (it.hasNext()) {
            WebDAVer.ListResponse lr = (WebDAVer.ListResponse) it.next();
            String wholePath = viewTar + "/" + lr.path;
            if (get(wd1, wholePath, tarContentFile, "WebDAVGetTarContents", true)) {
                self.testFailed("Tar.GET subtest failed");
                return;
            }
        }
        Log.INFO("retrieved/checked files in tar");
        self.testPassed("ok");
    }

    /**
     *  List contents of tar, saving files into a list
     *  for retrieve testing, and validate.
     */
    private boolean listTar(String viewTar) {
        TestCase self = createTestCase("WebDAV", "ListTar");
        CmdResult cr;
        try {
            //cr = wd.list("/webdav", WebDAVer.DEPTH_INFINITE);
            cr = wd1.list(viewTar, WebDAVer.DEPTH_INFINITE);
            if (!cr.pass) {
                self.testFailed("list: " + cr.getExceptions());
                return true;
            }
System.out.println("XXXXXXXXXXXXXXXXXX: " + cr.string);
//System.exit(0);
            // put all paths in HashMap for checking
            HashMap hm = new HashMap();
            Iterator it = cr.list.iterator();
            while (it.hasNext()) {
                WebDAVer.ListResponse lr = (WebDAVer.ListResponse)it.next();
                hm.put(lr.path, lr);
                Log.INFO(lr.toString());
                if (lr.isCollection  &&  lr.contents != null) {
                    Iterator it2 = lr.contents.iterator();
                    while (it2.hasNext()) {
                        WebDAVer.ListResponse lr2 = (WebDAVer.ListResponse)
                                                    it2.next();
                        hm.put(lr2.path, lr2);
                        // add to list to retrieve
                        list_files.add(lr2);
                    }
                }
            }

            //
            //  see if expected files are there
            //
            it = expected_files.iterator();
            while (it.hasNext()) {
                WebDAVer.ListResponse lr = (WebDAVer.ListResponse) it.next();
                String wholePath = viewTar + "/" + lr.path;
                WebDAVer.ListResponse lr2 = (WebDAVer.ListResponse) 
                                                  hm.get(wholePath);
                if (lr2 == null) 
                    throw new HoneycombTestException("missing from list: " +
                                                     wholePath);
                if (lr2.uid != lr.uid  ||  lr2.gid != lr.gid  ||
                    lr2.length != lr.length ||
                    !lr2.displayname.equals(lr.displayname))
                    throw new HoneycombTestException("mismatched attributes: " +
                                                     lr.path);
            }
            self.testPassed("checked " + expected_files.size() + " files");

        } catch (Exception e) {
            self.testFailed("listing tarfile: " + e);
            return true;
        }

        return false;
    }

    //  mimetypes:
    // application/x-tar
    // application/x-cpio
    private CmdResult mkTar(String toppath) {
        if (!toppath.endsWith("/"))
            toppath += "/";
        File basefile = null;
        File tarfile = null;
        try {
            //
            //  copy a well-known file to store, getting hash
            //
            MessageDigest md = MessageDigest.getInstance(
                                   HoneycombTestConstants.CURRENT_HASH_ALG);
            basefile = FileUtil.createTempFile();
            FileInputStream in = new FileInputStream("/etc/passwd");
            FileOutputStream out = new FileOutputStream(basefile);
            byte[] buf = new byte[1024];
            int len;
            while ((len=in.read(buf)) > 0) {
                md.update(buf, 0, len);
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            //
            //  remember characteristics of the 'base' file for
            //  comparison w/ retrieved versions
            //
            tarContentFile.datasha1 = HCUtil.convertHashBytesToString(md.digest());
            tarContentFile.filesize = basefile.length();

            //
            //  make list of files (copies of 'base') to add
            //
            // level 1 content file
            expected_files.add(new WebDAVer.ListResponse(
                     toppath + "content.f", false, null, 
                     "content.f",
                     null, null, basefile.length(), null, null, 101, 11));

            // level 2 content files in same dir
            expected_files.add(new WebDAVer.ListResponse(
                     toppath + "dir1/content1.f", false, null, 
                     "content1.f",
                     null, null, basefile.length(), null, null, 102, 12));
            expected_files.add(new WebDAVer.ListResponse(
                     toppath + "dir1/content2.f", false, null, 
                     "content2.f",
                     null, null, basefile.length(), null, null, 103, 13));

            // level 3 content file on different branch
            expected_files.add(new WebDAVer.ListResponse(
                     toppath + "dir2/dir2/content.f", false, null, 
                     "content.f",
                     null, null, basefile.length(), null, null, 104, 14));

            // level 4 content file on different branch
            expected_files.add(new WebDAVer.ListResponse(
                     toppath + "dir3/dir3/dir3/content.f", false, null, 
                     "content.f",
                     null, null, basefile.length(), null, null, 105, 15));

            //
            //  build tarfile
            //
            tarfile = FileUtil.createTempFile();
            FileOutputStream os = new FileOutputStream(tarfile);
            TarArchive tar = new TarArchive(os);

            // top dir - needed?
            TarEntry te;
            te = new TarEntry(toppath);
            te.setModTime(System.currentTimeMillis());
            te.setIds(105, 5);
            te.setNames("uidoneohfive", "gidfive");
            tar.writeEntry(te, false);

            // files
            Iterator it = expected_files.iterator();
            while (it.hasNext()) {
                WebDAVer.ListResponse lr = (WebDAVer.ListResponse) it.next();
                te = new TarEntry(basefile);
                te.setName(lr.path);
                te.setIds(lr.uid, lr.gid);
                te.setNames("uid"+lr.uid, "gid"+lr.gid);
                tar.writeEntry(te, false);
            }

            tar.closeArchive();

            //
            //  store tarfile w/ factory metadata mimetype=tar
            //
            HashMap hm = newFactoryMD();
            hm.put("filesystem.mimetype", "application/x-tar");
            CmdResult cr = store(tarfile.getAbsolutePath(), hm);
            return cr;
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            try {
                if (basefile != null)
                    basefile.delete();
                if (tarfile != null)
                    tarfile.delete();
            } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
            }
        }
        return null;
    }

    //
    //  return true if failure - note that expectPass conditions
    //  whether {failure to get} == pass
    //
    private boolean get(WebDAVer wd, String path, CmdResult store, String name, 
                                                      boolean expectPass) {

        TestCase self = createTestCase("WebDAV", name);
        if (expectPass)
            self.addTag(Tag.POSITIVE);
        else
            self.addTag(Tag.NEGATIVE);

        try {
            CmdResult cr = wd.getFile(path, TestBed.doHash);
            if (cr.pass) {
                if (!expectPass) {
                    self.testFailed("get succeeded = failure");
                    return true;
                }
                if (cr.filesize != store.filesize) {
                    self.testFailed("filesizes (store/retrieve): " + 
                                     store.filesize + "/" + cr.filesize);
                    return true;
                }
                if (TestBed.doHash) {
                    if (!cr.datasha1.equals(store.datasha1)) {
                        self.testFailed("sha (store/retrieve): " + 
                                     store.datasha1 + "/" + cr.datasha1);
                        return true;
                    }
                }
                self.testPassed("get: " + 
                           BandwidthStatistic.toMBPerSec(cr.filesize, cr.time));
                return false;
            } else {
                if (!expectPass) {
                    self.testPassed("get failed = pass");
                    return false;
                }
                self.testFailed("get: " + cr.getExceptions());
                return true;
            }
        } catch (Exception e) {
            self.testFailed("get: " + e);
            e.printStackTrace();
            return true;
        }
    }
}
