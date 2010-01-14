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



package com.sun.honeycomb.hctest.rmi.nodesrv.clnt;

import com.sun.honeycomb.hctest.rmi.nodesrv.common.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;

import java.rmi.*;
import java.util.*;
import java.io.*;

//import java.rmi.server.RMISocketFactory;
//import com.sun.honeycomb.harness.common.FixedPortRMISocketFactory;

import java.util.logging.Logger;

public class NodeSrvClnt {

    private static final Logger LOG =
        Logger.getLogger(NodeSrvClnt.class.getName());

    private String serviceName = null;
    private NodeSrvIF nif = null;

    public int cmds = 0;
    public long start_time = 0;

    public NodeSrvClnt(String server_ip) throws RemoteException {

        if (System.getSecurityManager() == null) {
            System.setSecurityManager( new RMISecurityManager() ); 
        }
        serviceName = "rmi://" + server_ip + ":" + 
                                    NSConstants.RMI_REGISTRY_PORT + "/" +  
                                    NSConstants.SERVICE;
        try {
            nif = (NodeSrvIF) Naming.lookup(serviceName);
        } catch (Exception e) {
            throw new RemoteException(
                            "Naming.lookup(" + serviceName + "): " + e);
        }
        start_time = System.currentTimeMillis();
    }

    public String uptime() throws RemoteException {
        return nif.uptime();
    }

    public void shutdown() throws RemoteException {
        nif.shutdown();
        nif = null;
        cmds = 0;
    }

    public void logMsg(String msg) throws RemoteException {
        nif.logMsg(msg);
        cmds++;
    }
 
    public String[] getNodes() throws RemoteException {
        cmds++;
        return nif.getNodes();
    }

    public String[] getActiveNodes() throws RemoteException {
        cmds++;
        return nif.getActiveNodes();
    }

    public HClusterConfig getConfig() throws RemoteException {
        cmds++;
        return nif.getConfig();
    }

    public HOidInfo getOidInfo(String oid, boolean thisOnly)
                                                        throws RemoteException {
        cmds++;
        return nif.getOidInfo(oid, thisOnly);
    }
/*
    public String getDataOID(String mdoid) throws RemoteException {
        cmds++;
        try {
            return nif.getDataOID(mdoid);
        } catch (Exception e) {
            throw new RemoteException(e);
        }
    }
*/
    public void deleteFragments(List fragments) throws RemoteException {
        cmds++;
        nif.deleteFragments(fragments);
    }

    public void restoreFragments(List fragments) throws RemoteException {
        cmds++;
        nif.restoreFragments(fragments);
    }

    public void waitForFragments(List fragments, boolean shouldExist)
                                                       throws RemoteException  {
        cmds++;
        nif.waitForFragments(fragments, shouldExist);
    }

/*
    public void corruptFragments(List fragments, int[] list, int nbytes,
                                                                byte mask)
                                                    throws RemoteException {
        cmds++;
        nif.corruptFragments(fragments, list, nbytes, mask);
    }
*/
    public void injectServiceProblem(String svc_name, String action)
                                                    throws RemoteException {
        cmds++;
        nif.injectServiceProblem(svc_name, action);
    }
    public void reboot(boolean fast) throws RemoteException {
        cmds++;
        nif.reboot(fast);
    }

    public String toString() {
        return "NodeSrvClnt(SVC=" + serviceName + ")";
    }


    // main is for test
    public static void usage() {
            System.err.println("Usage: NodeSrvClnt <rmi_host_ip> <oid|-e|-r|-c>");
            System.err.println("               -c:   get cluster config info");
            System.err.println(" -o oid [noderef]:   get config & info on oid; noderef means not to get the data oid");
            System.err.println("               -e:   inject NullPointerException into API JVM");
            System.err.println("               -u:   get system uptime");
            System.err.println("               -r:   reboot node");
            System.exit(1);
    }

    public static void main(String args[]) {
        try {
            //RMISocketFactory.setSocketFactory(new
                                //FixedPortRMISocketFactory(3333));
            if (args.length == 0)
                usage();
            System.out.println("connecting..");
            NodeSrvClnt clnt = new NodeSrvClnt(args[0]);
            System.out.println("connected");
            if (args[1].equals("-c")) {
                HClusterConfig c = clnt.getConfig();
                System.out.println("data         " + c.nDataFrags);
                System.out.println("parity       " + c.nParityFrags);
                System.out.println("disks/node   " + c.disksPerNode);
                System.out.println("active nodes " + c.activeNodeIPs.length);
                System.exit(0);
            } else if (args[1].equals("-e")) {
                clnt.injectServiceProblem("API", "excep");
                System.out.println("exception injected");
                System.exit(0);
            } else if (args[1].equals("-u")) {
                System.out.println(clnt.uptime());
            } else if (args[1].equals("-r")) {
                clnt.reboot(false);
                System.out.println("node should be rebooting");
            } else if (args[1].equals("-o")) {
                boolean thisOnly = false;
                if (args.length == 4) {
                    if (args[3].equals("noderef")) {
                        thisOnly = true;
                    } else {
                        System.err.println("invalid option: " + args[3]);
                        usage();
                    }
                }

                HOidInfo inf = clnt.getOidInfo(args[2], thisOnly);
                System.out.print(inf.toString());
            } else if (args[1].equals("-help")) {
                usage();
            } else {
                System.err.println("unexpected option: " + args[1]);
                usage();
            }
/*
            System.out.println("NODES:");
            String[] nodes = clnt.getNodes();
            if (nodes == null) {
                System.err.println("  list is null");
                System.exit(1);
            }
            for (int i=0; i<nodes.length; i++)
                System.out.println("    " + nodes[i]);
            nodes = clnt.getActiveNodes();
*/
/*
            HClusterConfig hconfig = clnt.getConfig();
            System.out.println("disksPerNode=" + hconfig.disksPerNode +
                " nDataFrags=" + hconfig.nDataFrags +
                " nParityFrags=" + hconfig.nParityFrags);
            System.out.println("ACTIVE NODES:");
            if (hconfig.activeNodeIPs == null) {
                System.err.println("  list is null");
                System.exit(1);
            }
            for (int i=0; i<hconfig.activeNodeIPs.length; i++)
                System.out.println("    " + hconfig.activeNodeIPs[i]);

            List l = clnt.getFragmentPaths(args[1], false);
            System.out.println("FRAGMENT LIST:");
            for (int i=0; i<l.size(); i++)
                System.out.println("    " + l.get(i));
            String dataOid = clnt.getDataOID(args[1]);
            System.out.println("Data oid=" + dataOid);
            l = clnt.getFragmentPaths(dataOid, true);
            System.out.println("FRAGMENT LIST:");
            for (int i=0; i<l.size(); i++) {
                System.out.println("Chunk " + i);
                List ll = (List) l.get(i);
                for (int j=0; j<ll.size(); j++) 
                    System.out.println("    " + ll.get(j));
            }
*/

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
