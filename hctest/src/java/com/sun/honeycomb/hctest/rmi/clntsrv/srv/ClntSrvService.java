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


package com.sun.honeycomb.hctest.rmi.clntsrv.srv;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.logging.Logger;

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.rmi.clntsrv.common.ClntSrvIF;
import com.sun.honeycomb.hctest.rmi.spsrv.clnt.SPSrvClnt;
import com.sun.honeycomb.hctest.util.ClusterTestContext;
import com.sun.honeycomb.hctest.util.HCNonCyclicalReadableByteChannel;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.FileUtil;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;
import com.sun.honeycomb.test.util.RunCommand;

import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.common.TestRequestParameters;

public class ClntSrvService extends UnicastRemoteObject implements ClntSrvIF {

    private static final Logger LOG =
                            Logger.getLogger(ClntSrvService.class.getName());

    private static HCTestReadableByteChannel byteChannel = null;
    private RunCommand shell = new RunCommand();
    private Hashtable clusters = new Hashtable();
    private Hashtable spsrvs = new Hashtable();

    private CmdResult nullResult = new CmdResult();

    public ClntSrvService() throws RemoteException {

        super();
        LOG.info("ClntSrvService is initializing");
        FileUtil.tryLocalTempDir();
        LOG.info("ClntSrvService initialized");
    }

    public void cleanup() {
    	// nothing to cleanup now...
    }

    // 
    //  look up or create a client for the cluster
    //
    
    // Synchronized in order to avoid creating various HTC's becaues of multithreaded access.
    private synchronized HoneycombTestClient getHCClient(ClusterTestContext cluster) 
                                                        throws RemoteException {
        if (cluster.dataVIP == null) {
            throw new RemoteException("cluster.dataVIP is null");
        }
        //if (cluster.cluster == null) {
            //throw new RemoteException("cluster.cluster is null");
        //}

        String server = cluster.dataVIP + "/" + cluster.auditIP;

        HoneycombTestClient htc = (HoneycombTestClient) clusters.get(server);
        if (htc == null) {
            try {
                htc = new HoneycombTestClient(cluster.dataVIP, cluster.auditIP);
                htc.setThrowAPIExceptions(false);
            } catch (Exception e) {
                throw new RemoteException("creating client (" + server + ")",e);
            }
            clusters.put(server, htc);
        }

        // This setting of run ids has to be done ever single time we fetch the context
        // otherwise running multiple runs on a RMI setup will result in using old run ids
        
        // Set the clusterID and runID to coincide with what's going on in the master node.
        TestRunner.setProperty(HoneycombTestConstants.PROPERTY_CLUSTER, cluster.cluster);
        TestRunner.setProperty(HoneycombTestConstants.PROPERTY_RUN, new Integer(cluster.runID).toString());
        
        // Make this node have the same runID as the master node 
        Run.getInstance().setId(cluster.runID);
        
        // Re-Init the Audit system.
        htc.initTestNVOA();	
        
        return htc;
    }

    //
    //  look up or create an spsrv client
    //
    private SPSrvClnt getSPClient(ClusterTestContext cluster) 
                                                        throws RemoteException {
        String server = cluster.spIP;
        if (server == null) {
            throw new RemoteException("cluster.spIP is null");
        }
        SPSrvClnt spc = (SPSrvClnt) spsrvs.get(server);
        if (spc == null) {
            try {
                spc = new SPSrvClnt(server);
            } catch (Exception e) {
                throw new RemoteException("getting SP client", e);
            }
            spsrvs.put(server, spc);
        }
        return spc;
    }

    ////////////////////////////////////////////////////////////////////////
    //  infrastructure test ops
    //
    public CmdResult timeRMI(ClusterTestContext cluster, boolean trySP)
                                                        throws RemoteException {
        if (!trySP)
            return nullResult;
        if (cluster.spIP == null)
            throw new RemoteException("cluster.spIP is null");
        throw new RemoteException("spIP timeRMI not impl");
    }

    ////////////////////////////////////////////////////////////////////////
    //  local shell ops
    //
    /**
     *  ping the remote host.
     */
    public boolean ping(String host) throws RemoteException {
        try {
            return shell.ping(host);
        } catch (Exception e) {
            throw new RemoteException("shell.ping", e);
        }
    }

    /**
     *  Check if file exists, retrying up to waitSeconds.
     */
    // XXX throw if parent dir doesn't exist? what about directories?
    public CmdResult doesFileExist(String path, int waitSeconds)
                                                        throws RemoteException {
        File f = new File(path);

        boolean exists = false;

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
        CmdResult cr = new CmdResult();
        cr.pass = exists;
        if (exists) {
            try {
                cr.datasha1 = shell.sha1sum(path);
            } catch (Exception e) {
                throw new RemoteException(path, e);
            }
        }
        return cr;
    }

    /**
     *  ssh a command to remote host.
     */
    public CmdResult sshCmd(String login_host, String cmd) 
                                                        throws RemoteException {
        try {
            CmdResult cr = new CmdResult();
            long t1 = System.currentTimeMillis();
            cr.string = shell.sshCmd(login_host, cmd);
            cr.time = System.currentTimeMillis() - t1;
            return cr;
        } catch (Exception e) {
            throw new RemoteException("shell.ssh " + cmd, e);
        }
    }

    /**
     *  ls.
     */
    public CmdResult ls(String fname) throws RemoteException {
        try {
            CmdResult cr = new CmdResult();
            long t1 = System.currentTimeMillis();
            cr.list = shell.ls(fname);
            cr.time = System.currentTimeMillis() - t1;
            return cr;
        } catch (Exception e) {
            throw new RemoteException("shell.ls " + fname, e);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //  SP rmi server
    // 
    /**
     *  Log message to SP log (shared with cluster).
     */
    public void logMsg(ClusterTestContext cluster, String msg) 
                                                        throws RemoteException {

        SPSrvClnt spc = getSPClient(cluster);

        try {
            if (cluster.runTag == null)
                spc.logMsg(msg);
            else
                spc.logMsg(cluster.runTag + " " + msg);
        } catch (Exception e) {
            throw new RemoteException("sp client exception", e);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //  cluster API
    // 
    /**
     *  Store a file of a given size and return the filename and oid.
     */
    public CmdResult store(ClusterTestContext cluster, 
                           long size, 
                           boolean binary, 
                           HashMap mdMap,
                           boolean calcHash)
                                                        throws RemoteException {
        HoneycombTestClient htc = getHCClient(cluster);

        CmdResult cr = null;

        try {
        	HCNonCyclicalReadableByteChannel byteChannel = new HCNonCyclicalReadableByteChannel(size, 12345, null,false);
            TestRequestParameters.setCalcHash(calcHash);
        	Log.INFO("Storing HCTestReadableByteChannel: " + byteChannel);
            cr = htc.store(byteChannel, mdMap);
            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("storing [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        return cr;
    }

    /**
       byte array based store
    */

    public CmdResult store(ClusterTestContext cluster, 
                           byte[] bytes, 
                           int repeats,
                           boolean binary, 
                           HashMap mdMap,
                           boolean calcHash)
                                                        throws RemoteException {
        HoneycombTestClient htc = getHCClient(cluster);

        
        CmdResult cr = null;
        String cmdTag = null;

        try {
            cmdTag = RandomUtil.getRandomString(4);

            String bytePatternString= new String();
            for(int i=0;
                i < (bytes.length < 6 ? bytes.length : 6 );
                i++) {                
                bytePatternString +=  ( Integer.toHexString(bytes[i]).substring(6) );
                
            }

            TestRequestParameters.setCalcHash(calcHash);
            cr = htc.store(bytes, repeats, mdMap);
            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("storing [" + TestRequestParameters.getLastLogTag() + "]", e);
        }

        return cr;
    }

    public CmdResult addMetadata(ClusterTestContext cluster, String oid, 
                                                            HashMap mdMap)
                                                        throws RemoteException {
        HoneycombTestClient htc = getHCClient(cluster);
        CmdResult cr = null;
        try {

            cr = htc.addMetadata(oid, mdMap);
            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("adding metadata [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        
        return cr;
    }

    // 
    // retrieve OID obj to new file, calc sha1 & delete file,
    //  return sha1 and time
    // 
    public CmdResult retrieve(ClusterTestContext cluster, String oid,
                              boolean calcHash) 
                                                        throws RemoteException {
        HoneycombTestClient htc = getHCClient(cluster);
        CmdResult cr; 

        try {  
            TestRequestParameters.setCalcHash(calcHash);
            cr = htc.retrieve(oid);
            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("retieve [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        
        return cr;
    }

    public CmdResult getMetadata(ClusterTestContext cluster, String oid)
                                                        throws RemoteException {

        HoneycombTestClient htc = getHCClient(cluster);
        CmdResult cr = null;
        
        try {

            cr = htc.getMetadata(oid);

            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("get metadata [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        return cr;
    }

    public CmdResult query(ClusterTestContext cluster, String query) 
                                                        throws RemoteException {

        HoneycombTestClient htc = getHCClient(cluster);
        CmdResult cr = null;
        
        try {

            cr =  htc.query(query, 
                                HoneycombTestConstants.USE_DEFAULT_MAX_RESULTS);
            cr.query = null; // not serializable
            cr.rs = null; // not serializable


            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("query [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        
        return cr;
    }
    
    /* This method is used by load tests (BlockStore tool) for query.
       The batch size is limited to a reasonable default (2000 results in a set),
       and we keep calling next until we get all results from the cluster,
       thereby exercising cookies. We throw away results and pass the count to the caller.
     */
    public CmdResult query_without_resultset(ClusterTestContext cluster, String query)
			throws RemoteException {

		HoneycombTestClient htc = getHCClient(cluster);
		CmdResult cr = null;
		
		try {

                        cr = htc.query(query, 
                                       HoneycombTestConstants.DEFAULT_MAX_RESULTS_CONSERVATIVE);
			cr.logTag = TestRequestParameters.getLastLogTag();
			
			QueryResultSet qrs = (QueryResultSet)cr.rs;
			
			
			long count = 0;
			if (qrs != null)
				while (qrs.next())
					count++;
			
			cr.query_result_count = count;
			
			// we can't seralize the QueryResultSet... 
			cr.rs = null;
                        cr.query = null; // not serializable
            
        } catch (Exception e) {
            throw new RemoteException("query [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        
        return cr;
	}

    public CmdResult query(ClusterTestContext cluster, String query, 
                                                                int maxResults)
                                                        throws RemoteException {

        HoneycombTestClient htc = getHCClient(cluster);
        CmdResult cr = null;
        
        try {

            cr = htc.query(query, maxResults);
            cr.query = null; // not serializable
            cr.rs = null; // not serializable
            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("query [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        
        return cr;
    }

    //
    //  delete an object using the Honeycomb API
    //
    public CmdResult delete(ClusterTestContext cluster, String oid) 
                                                        throws RemoteException {

        if (oid == null)
            throw new RemoteException("delete: OID IS NULL!!");

        HoneycombTestClient htc = getHCClient(cluster);
        CmdResult cr = null;

        try {

            cr = htc.delete(oid);
            cr.logTag = TestRequestParameters.getLastLogTag();
        } catch (Exception e) {
            throw new RemoteException("delete [" + TestRequestParameters.getLastLogTag() + "]", e);
        }
        
        return cr;
    }

    public void shutdown() throws RemoteException {
        LOG.info("shutdown received");
        System.exit(0);
    }

	public CmdResult audit(ClusterTestContext cluster, String oid) throws RemoteException {
		CmdResult cr = new CmdResult();
		
		try {
			HoneycombTestClient htc = getHCClient(cluster);
			cr = htc.audit(cluster.cluster,oid);
			cr.logTag = TestRequestParameters.getLastLogTag();
		} catch (Throwable t) {
			cr.pass = false;
			cr.addException(t);
		}
		
		return cr;
	}
}
