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



package com.sun.honeycomb.hctest;

import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;

import java.io.File;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.ArrayList;

/**
 *  Helper functions for test cases that address 
 *  Honeycomb clusters directly from the test program (rather
 *  than via remote clients). Extends test.util.HoneycombAPISuite, 
 *  which is restricted to providing convenience calls to cluster 
 *  API/CLI and Service Processor RMI interfaces, as well as providing
 *  test.util.FileCache service. This class adds a tmpFiles ArrayList
 *  for further file-management flexibility. HoneycombAPISuite requires 
 *  that a dataVIP be provided, either in a property file or as a --ctx
 *  command-line argument (see TestRunner.java).
 *
 *  Methods useful for both local and remote access should be in
 *  HoneycombSuite. 
 *
 *  For all inheritors of this class, be sure to call super.setUp() 
 *  in your setUp() method and super.tearDown() likewise. 
 *
 *  Allowance is made in HoneycombAPISuite for addressing multiple 
 *  clusters, in that the primary routines require that the cluster 
 *  be specified. There is also a wrapper for each of these routines 
 *  that uses the default dataVIP variable, and these wrappers are
 *  what would be used for all single-cluster tests.
 */
public class HoneycombLocalSuite extends HoneycombAPISuite {

    // files added to tmpFiles are deleted by tearDown()
    public ArrayList tmpFiles = new ArrayList();

    public HoneycombLocalSuite() {
        super();
    }

    public void setUp() throws Throwable {
        Log.DEBUG("com.sun.honeycomb.test.HoneycombLocalSuite::setUp() called");
        super.setUp();
    }
    public void tearDown() throws Throwable {
        super.tearDown();
        for (int i=0; i<tmpFiles.size(); i++) {
            try {
                (new File((String)tmpFiles.get(i))).delete();
            } catch (Exception e) {
                Log.ERROR("cleaning up tmpFiles: " + e);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //  non-API capabilities
    //

    /**
    ***  Local host checking of file data match by sha1sum. 
    ***  See HoneycombSuite for a CmdResult-based version.
    ***  XXX It could be faster to use cmp. However this method
    ***  leaves one with sha1's which might be useful later.
    **/
    public void verifyFilesMatch(String f1, String f2)
                                                throws HoneycombTestException {
        String f1sha = testBed.shell.sha1sum(f1);
        String f2sha = testBed.shell.sha1sum(f2);
        if (!f1sha.equals(f2sha)) {
            throw new HoneycombTestException(f1 + " has sha1sum " + f1sha +
                "; " + f2 + " has sha1sum " + f2sha);
        }
    }

    /**
    ***  Check if file exists, waiting/retrying as requested. 
    ***  There is a remote version in rmi.HoneycombRMISuite.
    ***  XXX throw if parent dir doesn't exist? what about directories?
    **/
    public boolean doesFileExist(String path, int waitSeconds)
                                                throws HoneycombTestException {
        boolean exists = false;

        File f = new File(path);
        if (waitSeconds == 0) {
            Log.DEBUG("Checking if " + f + " exists");
            exists = f.isFile();
        } else {

            while (waitSeconds-- > 0) {
                Log.DEBUG("Checking if " + f + " exists");
                exists = f.isFile();
                if (exists) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {}
            }
        }
        Log.DEBUG("File " + f + " exists: " + exists);
        return exists;
    }

    /**
    ***  Simple 'ls' interface.
    ***  There is a remote version in rmi.HoneycombRMISuite.
    **/
    public CmdResult ls(String fname) throws HoneycombTestException {
        CmdResult cr = new CmdResult();
        long t1 = System.currentTimeMillis();
        cr.list = testBed.shell.ls(fname);
        cr.time = System.currentTimeMillis() - t1;
        return cr;
    }

    /**
    ***  Simple 'ping' interface.
    ***  There is a remote version in rmi.HoneycombRMISuite,
    ***  for testing remote client-to-cluster access.
    **/
    public boolean ping(String host) throws HoneycombTestException {
        return testBed.shell.ping(host);
    }

    ///////////////////////////////////////////////////////////////////
    //
    // API-related
    //
    ////////////////////////// factory namespaces //////////////////
    ////// from server/src/config/metadata_config_factory.xml //////
    //
    //  <schema>
    //    <namespace name="system" writable="false" extensible="false">
    //      <field name="object_id" type="string" indexable="true"/>
    //      <field name="object_ctime" type="long" indexable="true"/>
    //      <field name="object_layoutMapId" type="long" indexable="true"/>
    //      <field name="object_size" type="long" indexable="true"/>
    //      <field name="object_hash" type="string" indexable="true"/>
    //      <field name="object_hash_alg" type="string" indexable="true"/>
    //      <namespace name="test">
    //        <field name="type_long" type="long" indexable="true" />
    //        <field name="type_double" type="double" indexable="true" />
    //        <field name="type_string" type="string" indexable="true" />
    //      </namespace>
    //    </namespace>
    //    <namespace name="filesystem" writable="true" extensible="false">
    //      <field name="uid" type="long" indexable="true"/>
    //      <field name="gid" type="long" indexable="true"/>
    //      <field name="mode" type="long" indexable="true"/>
    //      <field name="mimetype" type="string" indexable="true"/>
    //      <namespace name="ro" writable="false" extensible="false">
    //      </namespace>
    //    </namespace>
    //  <schema>
    //
    /**
    ***  Add basic factory schema md to this oid.
    **/
    private String mimes[] = {
        "application/pdf", "application/postscript", "application/x-gzip",
        "audio/mpeg",      "chemical/x-pdb",         "image/jpeg"
    };

    private static Long runId = null;
    private void setRunId() {
        if (runId == null) {
            String run = TestRunner.getProperty(
                                          HoneycombTestConstants.PROPERTY_RUN);
            if (run == null) {
                try {
                    runId = new Long(RandomUtil.getLong());
                } catch (Exception e) {
                    runId = new Long(1122334455);
                }
            } else
                runId = Long.decode(run);
        }
    }
    public long getRunId() {
        setRunId();
        return runId.longValue();
    }

    public HashMap newFactoryMD() {
        return newFactoryMD(true);
    }
    public HashMap newFactoryMD(boolean addFSattrs) {
        HashMap hm = new HashMap();

        if (addFSattrs) {
            long uid, gid;
            int mode;
            String mimetype;
            try {
                uid = 12345 + RandomUtil.randIndex(5);
                gid = 10 + RandomUtil.randIndex(5);
                mimetype = mimes[RandomUtil.randIndex(mimes.length)];
                mode = 0755;
            } catch (Exception e) {
                uid = 1; gid = 2; mimetype = mimes[0]; mode = 0777;
            }
            hm.put("filesystem.uid", new Long(uid));
            hm.put("filesystem.gid", new Long(gid));
            hm.put("filesystem.mode", new Long(mode)); 
            hm.put("filesystem.mimetype", mimetype);
        }

        setRunId();
        hm.put("system.test.type_long", runId);

        double d;
        try {
            d = RandomUtil.getDouble();
        } catch (Exception e) {
            d = 123456789.12345;
        }
        hm.put("system.test.type_double", new Double(d));

        return hm;
    }
    public CmdResult addFactoryMD(String oid) throws HoneycombTestException {
        HashMap hm = newFactoryMD();
        // use the oid as a unique value
        hm.put("system.test.type_string", oid);
        Log.DEBUG("Attemping to add Metadata " + hm);
        return addMetadata(oid, hm);
    }


    /**
    ***  Test that addFactoryMD() was called for this oid.
    **/
    public CmdResult queryFactoryMDOid(String oid) 
                                               throws HoneycombTestException {
        String q = "\"system.test.type_string\"='" + oid + "'";
        return query(q);
    }
/*
////// from server/src/config/metadata_config_factory.xml //////
  <fsViews>
    <fsView name="uidByGid" filename="${filesystem.uid}">
      <attribute name="filesystem.gid" unset="?" />
    </fsView>
    <fsView name="uidGidByMimetype" filename="${filesystem.uid}.${filesystem.gid
}">
      <attribute name="filesystem.mimetype" unset="unknown" />
    </fsView>
  </fsViews>
*/
    /**
    ***  Make query and check if given oid is included in result.
    ***  Return true if oid is in results of query q, else return false.
    **/
    public boolean queryAndCheckOid(String server, int port, String oid,
        String q)
                                                throws HoneycombTestException {

        CmdResult cr = query(server, port, q);
        return oidInQueryResults(oid, cr.rs);
    }
    public boolean queryAndCheckOid(String oid, String q)
                                                throws HoneycombTestException {
        return queryAndCheckOid(testBed.getDataVIP(), testBed.getDataPort(),
            oid, q);
    }
}
