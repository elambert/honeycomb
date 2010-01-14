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



package com.sun.honeycomb.hctest.rmi.clntsrv.clnt;

import com.sun.honeycomb.hctest.rmi.clntsrv.common.*;
import com.sun.honeycomb.hctest.rmi.common.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.util.*;

import java.rmi.*;
import java.util.*;
import java.io.*;

import java.rmi.server.RMISocketFactory;
//import com.sun.honeycomb.harness.common.FixedPortRMISocketFactory;


public class ClntSrvClnt {

    public String host;

    private String serviceName = null;
    private ClntSrvIF svc = null;

    public ClntSrvClnt(String server_ip) throws HoneycombTestException {

        host = server_ip;

        if (System.getSecurityManager() == null) {
            System.setSecurityManager( new RMISecurityManager() ); 
        }
        serviceName = "rmi://" + server_ip + ":" + 
                                    ClntSrvConstants.RMI_REGISTRY_PORT +
                                    "/" +  ClntSrvConstants.SERVICE;
        try {
            svc = (ClntSrvIF) Naming.lookup(serviceName);
        } catch (Exception e) {
            throw new HoneycombTestException (
                                    "Naming.lookup(" + serviceName + "): " + e);
        }
    }

    public CmdResult timeRMI(ClusterTestContext cluster, boolean trySP)
                                                throws HoneycombTestException {
        try {
            return svc.timeRMI(cluster, trySP);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public void logMsg(ClusterTestContext cluster, String msg) 
                                                throws HoneycombTestException {
        try {
            svc.logMsg(cluster, msg);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public boolean ping(String host) throws HoneycombTestException {
        try {
            return svc.ping(host);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult doesFileExist(String path, int waitSeconds)
                                                throws HoneycombTestException {
        try {
            return svc.doesFileExist(path, waitSeconds);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult sshCmd(String login_host, String cmd) 
                                                throws HoneycombTestException {
        try {
            return svc.sshCmd(login_host, cmd);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult ls(String path) throws HoneycombTestException {
        try {
            return svc.ls(path);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult store(ClusterTestContext cluster, 
                           long size, 
                           boolean binary, 
                           HashMap mdMap)
                                                throws HoneycombTestException {
        return store(cluster, size, binary, mdMap, true);
    }

    public CmdResult store(ClusterTestContext cluster,
                           long size,
                           boolean binary,
                           HashMap mdMap,
                           boolean calcHash)
                                                throws HoneycombTestException {
        if (svc == null) {
            throw new HoneycombTestException("no service");
        }
        try {
            return svc.store(cluster, size, binary, mdMap, calcHash);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }


    public CmdResult store(ClusterTestContext cluster, 
                           byte[] bytes, 
                           int repeats,
                           boolean binary, 
                           HashMap mdMap)
                                                throws HoneycombTestException {
        return store(cluster, bytes, repeats, binary, mdMap, true);
    }

    public CmdResult store(ClusterTestContext cluster, 
                           byte[] bytes, 
                           int repeats,
                           boolean binary, 
                           HashMap mdMap,
                           boolean calcHash)
                                                throws HoneycombTestException {
        if (svc == null) {
            throw new HoneycombTestException("no service");
        }
        try {
            return svc.store(cluster, bytes, repeats, binary, mdMap, calcHash);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult addMetadata(ClusterTestContext cluster, String oid,
                                                        HashMap mdMap)
                                                throws HoneycombTestException {
        try {
            return svc.addMetadata(cluster, oid, mdMap);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
    
    public CmdResult audit(ClusterTestContext cluster, String oid)
			throws HoneycombTestException {
		try {
			return svc.audit(cluster, oid);
		} catch (Exception e) {
			throw new HoneycombTestException(e);
		}
	}

    public CmdResult delete(ClusterTestContext cluster, String oid)
                                                throws HoneycombTestException {
        try {
            return svc.delete(cluster, oid);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult retrieve(ClusterTestContext cluster, String oid)
                                                throws HoneycombTestException {
        return retrieve(cluster, oid, true);
    }

    public CmdResult retrieve(ClusterTestContext cluster, String oid, 
                              boolean calcHash)
                                                throws HoneycombTestException {
        try {
            return svc.retrieve(cluster, oid, calcHash);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult getMetadata(ClusterTestContext cluster, String oid)
                                                throws HoneycombTestException {
        try {
            return svc.getMetadata(cluster, oid);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult query(ClusterTestContext cluster, String query)
                                                throws HoneycombTestException {
        try {
            return svc.query(cluster, query);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
    
    public CmdResult query_without_resulset(ClusterTestContext cluster, String query)
			throws HoneycombTestException {
		try {
			return svc.query_without_resultset(cluster, query);
		} catch (Exception e) {
			throw new HoneycombTestException(e);
		}
	}

    public CmdResult query(ClusterTestContext cluster, String query, 
                                                                int maxResults)
                                                throws HoneycombTestException {
        try {
            return svc.query(cluster, query, maxResults);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public void shutdown() throws HoneycombTestException {
        try {
            svc.shutdown();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public String toString() {
        return "ClntSrvClnt(SVC=" + serviceName + ")";
    }

    // main is for test
    public static void main(String args[]) {
        String usage = "Usage: ClntSrvClnt <rmi_host_ip> <ip> <-s|-i<oid>|-r<oid>|-l<msg>|-S>";
        if (args.length < 3) {
            System.err.println(usage);
            System.err.println("     -l:   log msg to sp log (use sp ip)");
            System.err.println("     <ip> is cluster for the following:");
            System.err.println("     -s:   store 1K file, return oid & sha1");
            System.err.println("     -i:   get info on oid");
            System.err.println("     -r:   retrieve oid and return sha1");
            System.err.println("     -S:   shutdown server");
            System.exit(1);
        }

        try {
            //RMISocketFactory.setSocketFactory(new
                                //FixedPortRMISocketFactory(3333));
            String runTag = RandomUtil.getRandomString(4);
            ClntSrvClnt clnt = new ClntSrvClnt(args[0]);
            ClusterTestContext cluster = 
                      new ClusterTestContext(args[1], null, null, null, runTag, 
                                             Run.getInstance().getId(), null);
            if (args[2].equals("-l")) {
                cluster.spIP = args[1]; // hack
                clnt.logMsg(cluster, args[3]);
                System.out.println("msg logged");
            } else if (args[2].equals("-s")) {
                CmdResult hcr = clnt.store(cluster, 1024, false, null);
                System.out.println("OID: " + hcr.mdoid);
                System.out.println("SHA: " + hcr.datasha1);
            } else if (args[2].equals("-i")) {
                CmdResult hcr = clnt.getMetadata(cluster, args[3]);
                System.out.println("MD: " + hcr.mdMap);
            } else if (args[2].equals("-r")) {
                CmdResult hcr = clnt.retrieve(cluster, args[3]);
                System.out.println("SHA: " + hcr.datasha1);
            } else if (args[2].equals("-S")) {
                try {
                    clnt.shutdown();
                } catch (HoneycombTestException e) {
                    Throwable t = e.getCause();
                    if (t instanceof java.rmi.UnmarshalException)
                        t = t.getCause();
                    if (t instanceof EOFException) {
                        System.out.println("shutdown ok");
                    } else {
                        System.err.println("unexpected: " + t);
                    }
                }
            } else {
                System.err.println(usage);
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
