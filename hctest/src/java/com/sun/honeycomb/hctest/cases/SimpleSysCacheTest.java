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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.HoneycombCMSuite;
import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.TestBed;
import com.sun.honeycomb.hctest.util.ClusterMembership;
import com.sun.honeycomb.hctest.util.ClusterNode;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.hctest.HoneycombTag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class SimpleSysCacheTest extends HoneycombLocalSuite {

    protected ClusterMembership cm = null;
    private String clusterIP = null;
    protected int numNodes = 0;
    private TestCase self = null;
    
    // TODO: make this a property on the command line for flamebox regression 
    //       testing.
    long[] sizes = {0,1024,
                    HoneycombTestConstants.ONE_MEGABYTE,
                    10*HoneycombTestConstants.ONE_MEGABYTE};   
    
    public String help() {
        return("\tPopulateSysCache task testing\n");
    }

    public void setUp() throws Throwable {
        super.setUp();
        
        Log.WARN("*** DO NOT RUN THIS TEST WHILE LOAD IS GOING ON FROM OTHER ACTIVITIES ***");
        Log.WARN("*** IT WILL DESTROY YOUR SYSTEM CACHES ***");
        self = createTestCase("SimpleSysCacheTest");
      
        self.addTag(Tag.NOEMULATOR);
        self.addTag(HoneycombTag.DATA_DOCTOR);
        self.addTag(HoneycombTag.SYS_CACHE);
        
        if (self.excludeCase()) 
            return;
   
        init();
    }
    
    protected void init() throws HoneycombTestException {
        TestBed b = TestBed.getInstance();
        
        // TestBed assumes that the cluster is already online
        // In our case, it's not, it will be started by the test.
        if (b != null) {
            clusterIP = b.adminVIP;
        } else {
            throw new HoneycombTestException("Unable to get AdminVIP.");
        }
        
        cm = new ClusterMembership(-1, clusterIP, HoneycombCMSuite.FULL_HC);
        numNodes = cm.getNumNodes();
        
        checkForWBClusterPkg();
        cm.setQuorum(true);
        cm.initClusterState();
    }
    
    public void checkForWBClusterPkg() throws HoneycombTestException{
        for (int node = 1; node <= numNodes; node++) {
            ClusterNode clusterNode = cm.getNode(node);
            if (!clusterNode.packageInstalled("SUNWhcwbcluster"))
                throw new HoneycombTestException("SUNWhcwbcluster package must be installed on all nodes.");
        }
    }

    public void tearDown() throws Throwable {
        super.tearDown();
    }
    
    public void testSysCache() throws HoneycombTestException {
        
        if (self.excludeCase()) 
            return;
        
        Vector results = new Vector();
        
        // Store known data
        results = storeData();

        // Check state in syscache.
        Log.INFO("Storig some known data.");
        checkSysCacheState(results);

        // Delete the data I just stored.
        Log.INFO("Deletes the previously stored data and verifying the system cache.");
        deleteData(results);
        
        // Check state in syscache.
        checkSysCacheState(results);
        
        self.postResult(true);
    }
    
    public void checkSysCacheState(Vector results) throws HoneycombTestException {
        Enumeration elems = results.elements();
        CmdResult res;

        Log.INFO("Checking system cache status.");
        int counter = 0;
        while (elems.hasMoreElements()) {
            Log.INFO("Verifying object " + (counter++ + 1) + " of " + results.size());
            res = (CmdResult)elems.nextElement();
            verifySysCache(res,res.deleted);          
        }
    }
    
    public void printSysCacheState(Vector results) throws HoneycombTestException {
        Enumeration elems = results.elements();
        CmdResult res;

        while (elems.hasMoreElements()) {
            res = (CmdResult)elems.nextElement();
            SysCache syscache = retrieveSysCache(res.mdoid);
            Log.INFO("Syscache view for metadata object: " + res.mdoid);
            Log.INFO(""+syscache);
            syscache = retrieveSysCache(res.dataoid);
            Log.INFO("Syscache view for data object: " + res.dataoid);
            Log.INFO(""+syscache);
        }
    }
    
    public void deleteData(Vector results) throws HoneycombTestException {
        CmdResult res;
        Enumeration elems = results.elements();

        int counter = 0;
        Log.INFO("Deleting some data.");
        while (elems.hasMoreElements()) {
            Log.INFO("Deleting object " + (counter++ + 1) + " of " + results.size());
            res = (CmdResult) elems.nextElement();
            try {
                delete(res.mdoid);
                res.deleted = true;
                Log.INFO("Deleted object: " + res.mdoid);
            } catch (HoneycombTestException e) {
                throw new HoneycombTestException("Failed to delete object on cluster "
                        + Log.stackTrace(e));
            }
        }
    }
    
    public Vector storeData() throws HoneycombTestException {
        Vector results = new Vector();
        CmdResult res;
        
        Log.INFO("Storing some data.");    
        for (int i  = 0; i < sizes.length; i++) {
            Log.INFO("Storing object " + (i+1) + " of " + sizes.length);
            Log.INFO("Filesize: " + sizes[i] + " bytes.");
            
            try {
                res = store(sizes[i]);
            } catch (HoneycombTestException e) {
                throw new HoneycombTestException("Failed to store object on cluster " + Log.stackTrace(e));
            }
            results.add(res);
        }
        return results;
    }
    
    public SysCache retrieveSysCache(String oid) {
        SysCache result = new SysCache();
        
        for (int node = 1; node <= numNodes; node++) {
            ClusterNode clusterNode = cm.getNode(node);
            
            try {
                Iterator list = clusterNode.checkSysCache(oid).iterator();
                NodeSysCache nodeCache = new NodeSysCache();
                DiskSysCache diskCache = null;
                Long diskID = null;
                Properties metadata = null;
                boolean nodeCacheUpdate = false;
                
                while (list.hasNext()) {                 
                    String line = (String)list.next();
                    
                    if (line.startsWith("Disk")) {
                        // if no MD in that disk then skip it
                        if (diskCache != null && metadata.size() != 0) {
                            diskCache.addMD(oid,metadata);
                            nodeCache.addDiskSysCache(diskID,diskCache);
                            nodeCacheUpdate = true;
                        }
                        
                        diskCache = new DiskSysCache();
                        diskID = new Long(line.substring(5,6));
                        metadata = new Properties();           
                    } else if (line.indexOf("=") != -1) {
                        // Metadata
                        String[] parts = line.split("=");
                        metadata.put(parts[0].trim(), parts[1].trim());
                    } // else ignore it.. it's garbage!
                }
                
                if (metadata.size() != 0) {
                    diskCache.addMD(oid,metadata);
                    nodeCache.addDiskSysCache(diskID,diskCache);
                    nodeCacheUpdate = true;
                }
                
                if (nodeCacheUpdate)
                    result.addNodeSysCache(new Long(node),nodeCache);
                
            } catch (HoneycombTestException e) {
                Log.ERROR("Failed to check sys cache status " + Log.stackTrace(e));
            } 
        }
        
        return result;
    }
    
    void verifySysCache(CmdResult cr, boolean deleted) throws HoneycombTestException {
        // verify data
        verifySysCache(cr, deleted, true);
        // verify metadata
        verifySysCache(cr, deleted, false);
    }
    
    void verifySysCache(CmdResult cr, boolean deleted, boolean data)
            throws HoneycombTestException {

        String oid = (data ? cr.dataoid : cr.mdoid);
        String oidName = (data ? "data" : "metadata");
        SysCache sysCache = retrieveSysCache(oid);
        Iterator nodes = sysCache.nodes.keySet().iterator();

        String diskIDBefore = null;
        String nodeIDBefore = null;
        Properties before = null;
        int found = 0;

        Log.INFO("Verifying the system cache contains " + oidName + 
                 " object: " + oid);

        while (nodes.hasNext()) {
            Object nodeID = nodes.next();
            NodeSysCache nodeCache = (NodeSysCache) sysCache.nodes.get(nodeID);
            Iterator disks = nodeCache.disks.keySet().iterator();

            while (disks.hasNext()) {
                Object diskID = disks.next();
                DiskSysCache diskCache = (DiskSysCache) nodeCache.disks
                        .get(diskID);

                Properties properties = (Properties) diskCache.metadata.get(oid);

                if (properties != null) {
                    found++;
                    if (before == null) {
                        before = properties;
                        nodeIDBefore = nodeID.toString();
                        diskIDBefore = diskID.toString();
                    } else {
                        // compare with last one found
                        if (nodeID == nodeIDBefore) {
                            throw new HoneycombTestException(
                                    "Object: " + oid
                                            + " found twice on the same node: "
                                            + nodeID);
                        }

                        Enumeration keys = properties.keys();
                        while (keys.hasMoreElements()) {
                            String key = keys.nextElement().toString();
                            if (!before.get(key).equals(properties.get(key))) {
                                Log
                                        .ERROR(oidName + " system metadata mismatch between nodes: "
                                                + nodeID
                                                + " disk: "
                                                + diskID
                                                + " and node: "
                                                + nodeIDBefore
                                                + " disk: " + diskIDBefore);
                            } else {
                                Log
                                        .DEBUG(oidName + " system metadata match between nodes: "
                                                + nodeID
                                                + " disk: "
                                                + diskID
                                                + " and node: "
                                                + nodeIDBefore
                                                + " disk: " + diskIDBefore);
                            }
                        }
                    }

                    String dtime = (String)properties.get("system.object_dtime");                
                    if (dtime == null){
                        throw new HoneycombTestException("system.object_dtime is null on on node: "
                                + nodeID + "disk: " + diskID + " equals -1");
                    }
                    
                    if (deleted && dtime.equals("-1")) {
                        throw new HoneycombTestException(oidName + " system metadata: "
                                + oid
                                + " should be deleted but dtime on node: "
                                + nodeID + "disk: " + diskID + " equals -1");
                    } else if (!deleted && !dtime.equals("-1")) {
                        throw new HoneycombTestException(oidName + " system metadata: "
                                + oid
                                + " should not be deleted but dtime on node: "
                                + nodeID + "disk: " + diskID + " equals "
                                + dtime);
                    }

                    Log.INFO(oidName + " system metadata is correctly marked as"
                                    + (deleted ? " " : " not ")
                                    + "deleted on node: "
                                    + nodeID
                                    + " disk: "
                                    + diskID);

                    String size = (String)properties.get("system.object_size");             
                    if (size == null){
                        throw new HoneycombTestException("system.object_size is null on on node: "
                                + nodeID + "disk: " + diskID + " equals -1");
                    }
                    if (data) {       
                        if (new Long(size).longValue() != -2) {    
                            if (cr.filesize != new Long(size).longValue()) {
                                throw new HoneycombTestException(
                                        oidName + " system metadata on node: " 
                                                + nodeID + " disk: " + diskID 
                                                + " does not have the correct filesize.");
                            }
        
                            Log.INFO(oidName + " system metadata has correct file size on node: "
                                            + nodeID + " disk: " + diskID);
                        } else {
                            Log.WARN("Multi chunk object found ignoring filesize until bug #6439984  is fixed.");
                        }
                    }
                }
            }
        }

        if (found == 3) {
            Log.INFO(oidName + " system metadata: " + oid
                    + " correctly found in 3 different system caches.");
        } else {
            throw new HoneycombTestException(oidName + " system metadata: "
                        + oid + " not found in 3 different system caches.");
        }

        Log.INFO("Verification of data in system cache passed.");
    }

    class DiskSysCache {
        HashMap metadata = new HashMap();
        
        public void addMD(String oid, Properties md){
            metadata.put(oid,md);
        }
        
        public String toString(){
            String result = "";
            
            Iterator keys = metadata.keySet().iterator();

            while (keys.hasNext()) {
                String key = (String) keys.next();
                Properties properties = (Properties)metadata.get(key);

                Enumeration en = properties.keys();
               
                result = result + "\t\t OID: " + key + "\n";  
                while (en.hasMoreElements()) {
                    String prop = (String)en.nextElement();
                    result = result + "\t\t " + prop + "=" + properties.getProperty(prop) + "\n";
                }
            }
            
            return result;
        }
    }
    
    class NodeSysCache {
        HashMap disks = new HashMap();
        
        public void addDiskSysCache(Long disk, DiskSysCache diskSysCache){
            disks.put(disk,diskSysCache);
        }
        
        public String toString() {
            String result = "";
            
            Iterator keys = disks.keySet().iterator();
            
            while (keys.hasNext()){
                Long key = (Long) keys.next();
                result = result + "\t DISK: " + key + "\n" + disks.get(key);
            }
            
            return result;
        }
    }
    
    class SysCache {
        HashMap nodes = new HashMap();
        
        public void addNodeSysCache(Long nodeID, NodeSysCache nodeSysCache){
            nodes.put(nodeID,nodeSysCache);
        }
        
        public String toString() {
            String result = "";

            Iterator keys = nodes.keySet().iterator();
            
            while (keys.hasNext()){
                Long key = (Long) keys.next();
                result = result + "NODE: " + key + "\n" + nodes.get(key);
            }            

            return result;
        }
    }
}
