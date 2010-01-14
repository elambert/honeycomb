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

import com.sun.honeycomb.hctest.rmi.spsrv.clnt.SPSrvClnt;

import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.TestRequestParameters;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.Collection;
import java.util.Hashtable;
import java.util.HashMap;
import java.security.NoSuchAlgorithmException;

/**
 *  Helper functions for test cases that address 
 *  Honeycomb clusters directly from the test program (rather
 *  than via remote clients). Restricted to basic cluster API 
 *  and Service Processor RMI interface functionality, along with
 *  a FileCache for transparent and efficient file handling.
 * 
 *  Before modifying this class, please consider whether the extension
 *  class, test.HoneycombLocalSuite, would be more appropriate.
 * 
 *  Methods useful for both local and remote access should be in
 *  HoneycombSuite. 
 *
 *  For all inheritors of this class, be sure to call super.setUp() 
 *  in your setUp() method and super.tearDown() likewise. 
 *
 *  Allowance is made for addressing multiple clusters, in that the 
 *  primary routines require that the cluster be specified, but there 
 *  is a wrapper for each of these routines that uses the default 
 *  dataVIP variable (or dataIP's according to TestBed setting).
 * 
 *  Note: this class closely parallels rmi/clntsrv/srv/ClntSrvService.java
 */
public class HoneycombAPISuite extends HoneycombSuite {

    private Hashtable clusters = new Hashtable();

    public HoneycombAPISuite() {
        super();
    }

    public void setUp() throws Throwable {
        Log.DEBUG("com.sun.honeycomb.test.HoneycombAPISuite::setUp() called");
        //
        //  the basic requirement of a HoneycombAPISuite is that
        //      there is a cluster to address.
        //
        requiredProps.add(HoneycombTestConstants.PROPERTY_DATA_VIP);
        requiredProps.add(HoneycombTestConstants.PROPERTY_ADMIN_VIP);
        // XXX will be req'd when server is installed on sp's
        //requiredProps.add(HoneycombTestConstants.PROPERTY_SP_IP);
        super.setUp();

        FileUtil.tryLocalTempDir();
    }
    public void tearDown() throws Throwable {
        super.tearDown();
    }

    /**
     *  Determine whether exceptions will be thrown (wrapped in
     *  HoneycombTestException) or returned in CmdResult.
     */
    boolean throwAPI = true;
    public void setThrowAPIExceptions(boolean val) 
                                                throws HoneycombTestException {

        // existing cases 1st
        Collection values = clusters.values();
        Iterator it = values.iterator();
        while (it.hasNext()) {
            HoneycombTestClient htc = (HoneycombTestClient) it.next();
            htc.setThrowAPIExceptions(val);
        }
        // remember to set future cases too
        throwAPI = val;
    }

    ////////////////////////////////////////////////////////////////////
    //  basic Honeycomb API capabilities
    //
    /**
     *  Look up or create a client for the cluster.
     */
    public HoneycombTestClient getHCClient(String server)
                                                throws HoneycombTestException {
        return (getHCClient(server,
            HoneycombTestConstants.DEFAULT_DATA_PORT));
    }

    public HoneycombTestClient getHCClient(String server, int port) 
                                                throws HoneycombTestException {

        HoneycombTestClient htc = (HoneycombTestClient) clusters.get(server);
        if (htc == null) {
            htc = new HoneycombTestClient(server, port);
            htc.setThrowAPIExceptions(throwAPI);
            clusters.put(server, htc);
        }
        return htc;
    }


    /**
     * Disable Audit.
     */
    public void disableAudit() throws HoneycombTestException {
        // it's possible the test client hasn't been created yet, and if
        // that's the case, we create the default one now given the
        // default settings and disable audit on it.  The idea is the test
        // will use this testclient object later and audit will already
        // be disabled
        if (clusters.isEmpty()) {
            Log.INFO("Creating new test client (server " +
                testBed.getDataVIP() + ") and disabling audit");
            HoneycombTestClient htc = getHCClient(testBed.getDataVIP(),
                testBed.getDataPort());
            htc.disableAudit();
        } else {
            // loop through the clusters to disable audit for each of them
            Collection values = clusters.values();
            Iterator it = values.iterator();

            while (it.hasNext()) {
                HoneycombTestClient htc = (HoneycombTestClient) it.next();
                Log.INFO("disable Audit on test client " + htc);
                htc.disableAudit();
            }
        }
     }

    /**
     *  Store a file of a given size and return the filename and oid.
     */
    public CmdResult store(String server, int port, long size, boolean binary, 
                                                         HashMap mdMap)
                                                throws HoneycombTestException {
        FileHandle fh = testBed.fc.get(size, binary);
        String filename = fh.f.getPath();

        try {
            HoneycombTestClient htc = getHCClient(server, port);
            TestRequestParameters.setCalcHash(TestBed.doHash);
            CmdResult cr = htc.store(filename, mdMap);

            cr.filesize = fh.size;
            cr.datasha1 = fh.getSHA();
            cr.filename = filename;

            return cr;

        } finally {
            // return file to cache
            testBed.fc.add(fh);
        }
    }
    public CmdResult store(long size, boolean binary, HashMap mdMap)
                                                throws HoneycombTestException {
        return store(testBed.getDataVIP(), testBed.getDataPort(), size,
            binary, mdMap);
    }
    public CmdResult store(long size, HashMap mdMap)
                                                throws HoneycombTestException {
        return store(testBed.getDataVIP(), testBed.getDataPort(), size,
            false, mdMap);
    }
    public CmdResult store(long size) throws HoneycombTestException {
        return store(testBed.getDataVIP(), testBed.getDataPort(), size,
            false, null);
    }
    public CmdResult storeAsStream(long size, HashMap mdMap)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(testBed.getDataVIP(),
            testBed.getDataPort());
        HCTestReadableByteChannel hctrbc = null;
        try {
            hctrbc = new HCTestReadableByteChannel(size);
        } catch (NoSuchAlgorithmException e) {
            throw new HoneycombTestException(e);
        }
        TestRequestParameters.setCalcHash(TestBed.doHash);
        CmdResult cr = htc.storeObject(false, true, false, hctrbc, mdMap, null,
            null, size, null);
        return (cr);
    }
    public CmdResult store(String filename, HashMap md) 
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(testBed.getDataVIP(),
            testBed.getDataPort());
        TestRequestParameters.setCalcHash(TestBed.doHash);
        return htc.store(filename, md);
    }

    /**
     *  Add metadata to an obj and return the new metadata oid.
     */
    public CmdResult addMetadata(String server, int port, String oid,
        HashMap mdMap)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.addMetadata(oid, mdMap);
    }
    public CmdResult addMetadata(String oid, HashMap mdMap)
                                                throws HoneycombTestException {
        return addMetadata(testBed.getDataVIP(), testBed.getDataPort(),
            oid, mdMap);
    }

    /**
     *  Retrieve OID obj to new file, calc sha1 & delete file,
     *  return sha1 and time.
     */ 
    public CmdResult retrieve(String server, int port, String oid) 
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);

        TestRequestParameters.setCalcHash(TestBed.doHash);
        CmdResult cr = htc.retrieve(oid);

        return cr;
    }
    public CmdResult retrieve(String oid) throws HoneycombTestException {
        return retrieve(testBed.getDataVIP(), testBed.getDataPort(), oid);
    }
    public CmdResult retrieveAsStream(String oid, long size)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(testBed.getDataVIP(),
            testBed.getDataPort());
        HCTestWritableByteChannel hctwbc = null;
        Log.DEBUG("creating retrieve stream");
        try {
            hctwbc = new HCTestWritableByteChannel(size, null);
        } catch (NoSuchAlgorithmException e) {
            throw new HoneycombTestException(e);
        }
        Log.DEBUG("calling retrieve object with our stream");
        TestRequestParameters.setCalcHash(TestBed.doHash);
        CmdResult cr = htc.retrieveObject(oid, hctwbc);
        Log.DEBUG("retrieve object returned " + cr);
        return (cr);
    }


    /**
     *  Retrieve data OID obj using advanced API.
     */ 
    public CmdResult retrieveDataObject(String server, int port, String dataoid)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);

        CmdResult cr = null;
        try {
            HCTestWritableByteChannel hctwbc =
                new HCTestWritableByteChannel(getFilesize(), null);
            TestRequestParameters.setCalcHash(TestBed.doHash);
            cr = htc.retrieveObject(dataoid, hctwbc);
        } catch (NoSuchAlgorithmException nsae) {
            throw new HoneycombTestException(nsae);
        }
        return cr;
    }
    public CmdResult retrieveDataObject(String dataoid)
                                                throws HoneycombTestException {
        return retrieve(testBed.getDataVIP(), testBed.getDataPort(), dataoid);
    }

    /**
     *  Look up metadata for oid.
     */
    public CmdResult getMetadata(String server, int port, String oid)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.getMetadata(oid);
    }
    public CmdResult getMetadata(String oid) throws HoneycombTestException {
        return getMetadata(testBed.getDataVIP(), testBed.getDataPort(), oid);
    }

    /**
     *  Get oid's with metadata matching query.
     */
    public CmdResult query(String server, int port, String query)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, HoneycombTestConstants.USE_DEFAULT_MAX_RESULTS);
    }
    public CmdResult query(String server, int port, PreparedStatement query)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, HoneycombTestConstants.USE_DEFAULT_MAX_RESULTS);
    }
    public CmdResult query(String query) throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query);
    }
    public CmdResult query(PreparedStatement query) throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query);
    }

    public CmdResult query(String server, int port, String query,
        int maxResults)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, maxResults);
    }
    public CmdResult query(String server, int port, PreparedStatement query,
        int maxResults)
        throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, maxResults);
    }


    public CmdResult query(String query, int maxResults)
                                                throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query,
            maxResults);
    }
    public CmdResult query(PreparedStatement query, int maxResults)
                                                throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query,
            maxResults);
    }

    /**
     *  Get oid's with metadata matching queryPlus.
     */
    public CmdResult query(String server, int port,
                           String query, String[] selectKeys)
        throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, selectKeys,
                         HoneycombTestConstants.USE_DEFAULT_MAX_RESULTS);
    }
    public CmdResult query(String server, int port,
                           PreparedStatement query, String[] selectKeys)
        throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, selectKeys,
                         HoneycombTestConstants.USE_DEFAULT_MAX_RESULTS);
    }

    public CmdResult query(String query, String[] selectKeys)
        throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query,
                     selectKeys);
    }

    public CmdResult query(PreparedStatement query, String[] selectKeys)
        throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query,
                     selectKeys);
    }

    public CmdResult query(String server, int port, String query,
                           String[] selectKeys, int maxResults)
        throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, selectKeys, maxResults);
    }
    public CmdResult query(String server, int port, PreparedStatement query,
                           String[] selectKeys, int maxResults)
        throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.query(query, selectKeys, maxResults);
    }

    public CmdResult query(String query, String[] selectKeys, int maxResults)
        throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query,
                     selectKeys, maxResults);
    }
    public CmdResult query(PreparedStatement query, 
                           String[] selectKeys, int maxResults)
        throws HoneycombTestException {
        return query(testBed.getDataVIP(), testBed.getDataPort(), query,
                     selectKeys, maxResults);
    }

    public Date getDate() throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(testBed.getDataVIP(), testBed.getDataPort());
        return htc.getDate();
    }

    /**
     *  Delete an object using the Honeycomb API.
     */
    public CmdResult delete(String server, int port, String oid)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.delete(oid);
    }
    public CmdResult delete(String oid) throws HoneycombTestException {
        return delete(testBed.getDataVIP(), testBed.getDataPort(), oid);
    }

    /**
     *  Get metadata schema.
     */
    public CmdResult getSchema(String server, int port)
                                                throws HoneycombTestException {
        HoneycombTestClient htc = getHCClient(server, port);
        return htc.getSchema();
    }
    public CmdResult getSchema() throws HoneycombTestException {
        return getSchema(testBed.getDataVIP(), testBed.getDataPort());
    }

    ////////////////////////////////////////////////////////////////////////
    //  local host ops
    //
    /**
     *  Make name of nfs mountpoint for cluster (server).
     */
    public String nfsMountPoint(String server) {
        if (HoneycombTestConstants.NFS_BASE_MOUNTPOINT.endsWith(File.separator))
            return HoneycombTestConstants.NFS_BASE_MOUNTPOINT + server;
        else
            return HoneycombTestConstants.NFS_BASE_MOUNTPOINT + 
                                                    File.separator + server;
    }
    /**
     *  Make name of nfs mountpoint for default cluster.
     */
    public String nfsMountPoint() {
        return nfsMountPoint(testBed.dataVIP);
    }

    /**
     *  Nfs-mount cluster and check that view exists.
     */
    public void mountNFS(String server) throws HoneycombTestException {

        // mount
        String mountPoint = nfsMountPoint(server);
        try {
            File f = new File(mountPoint);
            if (!f.isDirectory()) {
                if (f.isFile()) {
                    throw new HoneycombTestException(
                                        "standard mount point is file: " +
                                        mountPoint);
                }
                if (!f.mkdir()) {
                    throw new HoneycombTestException("creating mount point " +
                                                        mountPoint);
                }
            }
        } catch (Exception e) {
            throw new HoneycombTestException("checking mount point " +
                                                        mountPoint, e);
        }
        testBed.shell.nfsMount(server + ":/", mountPoint);

        // verify a view we expect to be there is there
        Log.DEBUG("Verifying view " + 
                    HoneycombTestConstants.NFS_VIEWPATH_SIZEOID + " exists");
        File view = new File(mountPoint + File.separator + 
                    HoneycombTestConstants.NFS_VIEWPATH_SIZEOID);
        if (!view.isDirectory()) {
            throw new HoneycombTestException("View doesn't exist " + view);
        }
    }
    public void mountNFS() throws HoneycombTestException {
        mountNFS(testBed.dataVIP);
    }

    /**
     *  Unmount nfs for server.
     */
    public void umountNFS(String server) throws HoneycombTestException {

        String mountPoint = nfsMountPoint(server);
        testBed.shell.unmount(mountPoint);
    }
    /**
     *  Unmount nfs for default server.
     */
    public void umountNFS() throws HoneycombTestException {
        umountNFS(testBed.dataVIP);
    }

    //////////////////////////////////////////////////////////////////////
    //  admin interface ('CLI') routines
    //
    /**
     *  ssh a command to admin@host.
     */
    public String adminCmd(String server, String cmd) 
                                                throws HoneycombTestException {
        return testBed.shell.sshCmd("admin@" + server, cmd);
    }
    /**
     *  ssh a command to admin@adminVIP.
     */
    public String adminCmd(String cmd) throws HoneycombTestException {
        return testBed.adminCmd(cmd);
    }

    /**
     *  get hwstat of default cluster.
     */
    public String hwstat() throws HoneycombTestException {
        return testBed.adminCmd("hwstat");
    }
    /**
     *  Get sysstat of default cluster.
     */
    public String sysstat() throws HoneycombTestException {
        return testBed.adminCmd("sysstat");
    }
    /**
     *  Get disk usage of default cluster.
     */
    public String df() throws HoneycombTestException {
        return testBed.adminCmd("df");
    }

    //////////////////////////////////////////////////////////////////////
    //  SP server routines
    //
    /**
     *  Log a msg to an sp server.
     */
    public void logMsg(String spsrv, String msg) throws HoneycombTestException {
        testBed.logMsg(spsrv, msg);
    }
    /**
     *  Log a msg to the default sp server.
     */
    public void logMsg(String msg) throws HoneycombTestException {
        testBed.logMsg(msg);
    }
}
