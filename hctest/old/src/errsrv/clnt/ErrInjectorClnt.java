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



package com.sun.honeycomb.errsrv.clnt;

import com.sun.honeycomb.errsrv.common.*;

import java.rmi.*;
import java.util.*;
import java.io.*;

import java.rmi.server.RMISocketFactory;
//import com.sun.honeycomb.errsrv.srv.FixedPortRMISocketFactory;


public class ErrInjectorClnt {

    private String serviceName = null;
    private ErrInjectorIF eif = null;

    public ErrInjectorClnt(String server_ip) throws ErrInjectorException {

        if (System.getSecurityManager() == null) {
            System.setSecurityManager( new RMISecurityManager() ); 
        }
        serviceName = "rmi://" + server_ip + ":" + 
					EIConstants.RMI_REGISTRY_PORT +
					"/" +  EIConstants.SERVICE;
        try {
            eif = (ErrInjectorIF) Naming.lookup(serviceName);
        } catch (Exception e) {
            throw new ErrInjectorException(
				"Naming.lookup(" + serviceName + "): " + e);
        }
    }

    public String[] getNodes() throws ErrInjectorException {
        try {
            return eif.getNodes();
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public String[] getActiveNodes() throws ErrInjectorException {
        try {
            return eif.getActiveNodes();
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public ErrInjectorIF.EClusterConfig getConfig() 
					throws ErrInjectorException {
        try {
            return eif.getConfig();
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public List getFragmentPaths(String oid, boolean allChunks) 
						throws ErrInjectorException {
        try {
            return eif.getFragmentPaths(oid, allChunks);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public String getDataOID(String mdoid) throws ErrInjectorException {
        try {
            return eif.getDataOID(mdoid);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public void deleteFragments(List fragments, int numToDelete)
						throws ErrInjectorException {
        try {
            eif.deleteFragments(fragments, numToDelete);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public void restoreFragments(List fragments, int numToRestore)
						throws ErrInjectorException {
        try {
            eif.restoreFragments(fragments, numToRestore);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public void deleteFragments(List fragments, int[] list)
						throws ErrInjectorException {
        try {
            eif.deleteFragments(fragments, list);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public void restoreFragments(List fragments, int[] list)
						throws ErrInjectorException {
        try {
            eif.restoreFragments(fragments, list);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public void deleteOneFragment(List fragments, int fragToDelete)
						throws ErrInjectorException {
        try {
            eif.deleteOneFragment(fragments, fragToDelete);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public void corruptFragments(List fragments, int[] list, int nbytes,
                                                                byte mask)
						throws ErrInjectorException {
        try {
            eif.corruptFragments(fragments, list, nbytes, mask);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public void injectServiceProblem(int nodeid, String svc_name, String action)
						throws ErrInjectorException {
        try {
            eif.injectServiceProblem(nodeid, svc_name, action);
        } catch (Exception e) {
            throw new ErrInjectorException(e);
        }
    }

    public String toString() {
        return "ErrInjectorClnt(SVC=" + serviceName + ")";
    }

    // main is for test
    public static void main(String args[]) {
        if (args.length != 2) {
            System.err.println("Usage: ErrInjectorClnt <rmi_host_ip> <oid|-e>");
            System.err.println("    oid:   get config & info on oid");
            System.err.println("     -e:   inject NullPointerException into API JVM");
            System.exit(1);
        }
        try {
            //RMISocketFactory.setSocketFactory(new
                                //FixedPortRMISocketFactory(3333));
            ErrInjectorClnt clnt = new ErrInjectorClnt(args[0]);
            if (args[1].equals("-e")) {
                clnt.injectServiceProblem(-1, "API", "excep");
                System.out.println("exception injected");
                System.exit(0);
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
            ErrInjectorIF.EClusterConfig econfig = clnt.getConfig();
            System.out.println("disksPerNode=" + econfig.disksPerNode +
                " nDataFrags=" + econfig.nDataFrags +
                " nParityFrags=" + econfig.nParityFrags);
            System.out.println("ACTIVE NODES:");
            if (econfig.activeNodeIPs == null) {
                System.err.println("  list is null");
                System.exit(1);
            }
            for (int i=0; i<econfig.activeNodeIPs.length; i++)
                System.out.println("    " + econfig.activeNodeIPs[i]);

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

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
