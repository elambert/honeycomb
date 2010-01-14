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



package com.sun.honeycomb.hctest.rmi.spsrv.clnt;

import com.sun.honeycomb.hctest.rmi.spsrv.common.*;

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.DeleteSuite;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.test.util.*;

import java.rmi.*;
import java.util.*;
import java.io.*;

//import java.rmi.server.RMISocketFactory;
//import com.sun.honeycomb.harness.common.FixedPortRMISocketFactory;


public class SPSrvClnt {

    private String serviceName = null;
    private SPSrvIF svc = null;

    public SPSrvClnt(String server_ip) throws HoneycombTestException {

        if (System.getSecurityManager() == null) {
            System.setSecurityManager( new RMISecurityManager() ); 
        }
        serviceName = "rmi://" + server_ip + ":" + 
                                SPSrvConstants.RMI_REGISTRY_PORT + "/" +  
                                SPSrvConstants.SERVICE;
        getSvc();
    }

    private void getSvc() throws HoneycombTestException {
        try {
            svc = (SPSrvIF) Naming.lookup(serviceName);
        } catch (Exception e) {
            throw new HoneycombTestException (
                                "Naming.lookup(" + serviceName + "): " + e);
        }
    }

    ////////////////////////////////////////////////////////////////////
    //  admin for test system
    //

    /**
     *  Have sp server get status from node server.
     */
    public String nodeStatus(int node) throws HoneycombTestException {
        try {
            return svc.nodeStatus(node);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    /**
     *  Get status from SP server.
     */
    public String spStatus() throws HoneycombTestException {
        try {
            return svc.spStatus();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    /**
     *  Tell node servers to end (since we can't pkill java on nodes
     *  w/out getting HC).
     */
    public void shutdownNodeServers() throws HoneycombTestException {
        try {
            svc.shutdownNodeServers();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    ////////////////////////////////////////////////////////////////////
    //  test client functions
    //
    public void logMsg(String msg) throws HoneycombTestException {
        try {
            svc.logMsg(msg);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
    public void logMsg(String msg, int node) throws HoneycombTestException {
        try {
            svc.logMsg(msg, node);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult checkNodes(boolean comprehensive) 
                                                throws HoneycombTestException {
        try {
            return svc.checkNodes(comprehensive);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public HClusterState getClusterState(ClusterTestContext c)
                                                 throws HoneycombTestException {
        try {
            return svc.getClusterState(c);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

/*
    public String[] getNodes() throws HoneycombTestException {
        try {
        return svc.getNodes();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
    public String[] getActiveNodes() throws HoneycombTestException {
        try {
        return svc.getActiveNodes();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
*/

    public HClusterConfig getConfig() throws HoneycombTestException {
        try {
            return svc.getConfig();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public HOidInfo getOidInfo(String oid, boolean thisOnly)
                                                throws HoneycombTestException {
        try {
            return svc.getOidInfo(oid, thisOnly);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HoneycombTestException(e);
        }
    }
/*
    public String getDataOID(String mdoid) throws HoneycombTestException {
        try {
        return svc.getDataOID(mdoid);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
*/
    public void deleteFragments(List fragments) throws HoneycombTestException {
        try {
            svc.deleteFragments(fragments);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public void restoreFragments(List fragments) throws HoneycombTestException {
        try {
            svc.restoreFragments(fragments);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

/*
    public void corruptFragments(List fragments, int[] list, int nbytes,
                                                                byte mask)
                                                throws HoneycombTestException {
        try {
        svc.corruptFragments(fragments, list, nbytes, mask);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
*/
    public void injectServiceProblem(int nodeid, String svc_name, String action)
                                                throws HoneycombTestException {
        try {
            svc.injectServiceProblem(nodeid, svc_name, action);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public CmdResult rebootNode(int nodeid, boolean fast) 
                                                throws HoneycombTestException {
        try {
            return svc.rebootNode(nodeid, fast);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public String toString() {
        return "SPSrvClnt(SVC=" + serviceName + ")";
    }

    //
    //  main is for unit test of the sp server
    //
    public static void usage() {
        System.err.println("Usage: SPSrvClnt <rmi_host_ip> <args>");
        System.err.println("               -s:   get uptime status on all nodes");
        System.err.println("               -c:   get config/status");
        System.err.println(" -o oid [noderef]:   get info on oid; noderef for MD objs means don't find data oid");
        System.err.println(" -D oid numfrags random|fixed|0,4,6:   for the given oid, delete numfrags frags in random or fixed ways or explicitly a list of frags like 0,4,6");
        System.err.println(" -R oid fragpath,[fragpath,...]:   for the given oid, restore the fragment at the named path.  Multiple paths are comma separated");
        System.err.println("               -e:   inject NullPointerException into API JVM on random node");
        System.err.println("           -r [n]:   reboot node n in 1.. (random node if no n)");
        System.err.println("               -l:   log msg to sp log");
        System.err.println("              -sn:   shutdown node servers");
        System.exit(1);
    }

    public static void main(String args[]) {
        String progname = "TestSP";

        if (args.length < 2)
            usage();

        try {
            //RMISocketFactory.setSocketFactory(new
                                //FixedPortRMISocketFactory(3333));
            SPSrvClnt clnt = new SPSrvClnt(args[0]);
            if (args[1].equals("-s")) {
                if (args.length == 2) {
                    System.out.println(clnt.spStatus());
                } else {
                    try {
                        int node = Integer.parseInt(args[2]);
                        node--;
                        if (node < 0) {
                            System.err.println("node must be >=1");
                            System.exit(1);
                        }
                        System.out.println(clnt.nodeStatus(node));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (args[1].equals("-c")) {
                CmdResult cr = clnt.checkNodes(false);
                System.out.println("connected nodes: " + cr.count);
            } else if (args[1].equals("-l")) {
                clnt.logMsg(args[2]);
                System.out.println("msg logged [" + args[2] + "]");
            } else if (args[1].equals("-o")) {
                boolean thisOnly = false;
                if (args.length == 4) {
                    if (args[3].equals("noderef")) {
                        thisOnly = true;
                    } else {
                        System.err.println("invalid option " + args[3]);
                        usage();
                    }
                }
                HOidInfo info = clnt.getOidInfo(args[2], thisOnly);
                if (info == null) {
                    System.out.println("null returned..");
                    System.exit(1);
                }
                System.out.print(info.toString());
             } else if (args[1].equals("-D")) {
                 if (args.length < 5) {
                     System.err.println("too few args");
                     usage();
                 }

                 String restoreFrags = "";
                 boolean thisOnly = true;
                 String oid = args[2];
                 int howManyFrags = Integer.parseInt(args[3]);
                 boolean random = false;
                 HashSet frags = null;
                 if (args[4].equals("random")) {
                    random = true;
                 } else if (args[4].equals("fixed")) { 
                     random = false;
                 } else {
                     // try to parse something that looks like this: 0,4,6
                     // into a HashSet
                     String[] fragIds = args[4].split(",");

                     frags = new HashSet();
                     for (int i = 0; i < fragIds.length; i++) {
                         int id = Integer.parseInt(fragIds[i]);
                         if (id >= HoneycombTestConstants.OA_TOTAL_FRAGS) {
                             System.out.println(
                                 "invalid frag id " + id +
                                 "; must be less than " +
                                 HoneycombTestConstants.OA_TOTAL_FRAGS);
                             System.exit(1);
                         }
                         frags.add(new Integer(id));
                     }
 
                     if (frags.size() != howManyFrags) {
                         System.out.println("specified " + howManyFrags +
                             " frags but only provided " + frags);
                         System.exit(1);
                     }
                 } 

                 if (howManyFrags <= 0) {
                     System.out.println("No frags to act upon, exiting");
                     return;
                 }

                 List fragsToDelete = DeleteSuite.pickFragsToDelete(
                     howManyFrags, clnt.getOidInfo(oid, thisOnly), null,
                     random, frags);

                 System.out.println("calling delete on frags:");
                 for (int i = 0; i < fragsToDelete.size(); i++) {
                     System.out.println("   " + fragsToDelete.get(i));
                     restoreFrags += fragsToDelete.get(i) + ",";
                 }
                 restoreFrags = restoreFrags.substring(0,
                     restoreFrags.length() - 1);

                 System.out.println("this can take awhile to complete");
                 clnt.deleteFragments(fragsToDelete);
                 System.out.println("Frags look like this now: " +
                     clnt.getOidInfo(oid, thisOnly));
                 System.out.println("Restore command: " + progname + " " +
                     args[0] + " -R " + oid + " " + restoreFrags);
             } else if (args[1].equals("-R")) {
                 if (args.length < 3) {
                     System.err.println("too few args");
                     usage();
                 }

                 boolean thisOnly = true;
                 String oid = args[2];

                 // eventually we will search for frags instead of specifying
                 // them.  maybe...
                 String[] fragPaths = args[3].split(",");

                 ArrayList fragsToRestore = new ArrayList();
                 System.out.println("calling restore on frags: ");
                 for (int i = 0; i < fragPaths.length; i++) {
                     fragsToRestore.add(fragPaths[i]);
                     System.out.println("   " + fragPaths[i]);
                 }
                 System.out.println("this can take awhile to complete");
                 clnt.restoreFragments(fragsToRestore);
                 System.out.println("Frags look like this now: " +
                     clnt.getOidInfo(oid, thisOnly));
             } else if (args[1].equals("-r")) {
                 int node = SPSrvConstants.RANDOM_NODE;
                 if (args.length == 3) {
                     try {
                         node = Integer.parseInt(args[2]);
                         node--;
                         if (node < 0) {
                             System.err.println("node must be >=1");
                             System.exit(1);
                         }
                     } catch (Exception e) {
                         e.printStackTrace();
                    }
                }
                clnt.rebootNode(node, true);
                System.out.println("node rebooted");
            } else if (args[1].equals("-sn")) {
                clnt.shutdownNodeServers();
                System.out.println("shutdown ok");
            } else if (args[1].equals("-e")) {
                clnt.injectServiceProblem(SPSrvConstants.RANDOM_NODE, 
                                                    "API", "excep");
                System.out.println("exception injected");
            } else {
                usage();
            }
            System.exit(0);
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
/*
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
