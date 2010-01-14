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



package com.sun.honeycomb.admin.mgmt.server;

import org.w3c.dom.Document;
import java.math.BigInteger;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.cm.ServiceManager;
import java.util.logging.*;
import java.io.*;
import java.util.*;
import com.sun.honeycomb.cm.jvm_agent.JVMService;

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.admin.mgmt.AdminException;
import com.sun.honeycomb.mgmt.common.MgmtException;

public  class HCLoglevelsAdapter
    implements HCLoglevelsAdapterInterface {
    
    private static transient final Logger logger = 
        Logger.getLogger(HCLoglevelsAdapter.class.getName());

    public static final String[] LOG_LEVELS = {
        "OFF", "FINEST", "FINE", "INFO", "WARNING", "SEVERE", "FATAL" };
    //    public static final String[] LOG_LEVELS = AdminInterface.LOG_LEVELS;
    public void loadHCLoglevels()
        throws InstantiationException {

    }

    /*
    * This is the list of accessors to the object
    */
    public void populateLogLevels(List<String> array) throws MgmtException {
        Node [] nodes = null;
        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to retrieve log level");
        }
        for (int i = 0; i < nodes.length; i++) {
            if (!nodes[i].isAlive()) {
                continue;
            }

            NodeMgrService.Proxy nodeProxy = nodes[i].getProxy();
            if (nodeProxy == null) {
                logger.severe (
                    "cannot get node manager proxy for "
                        + nodes[i].nodeId());
                continue;
            }
            Service[] services = nodeProxy.getServices();

            if (services == null) {
                logger.info("no services available");
            } else {            
                String servicesCsv=null;
                for (int j = 0; j < services.length; j++) {
                    if (!services[i].isManaged()) {
                        continue;
                    }

                    // this assumes symmetric architecture and running
                    // on master.
                    if (services[j].isJVM()) {
                        if(null==servicesCsv) {
                            servicesCsv=services[j].getName();
                        } else {
                            servicesCsv=servicesCsv+","+services[j].getName();
                        }

                    }
                }

                array.add(servicesCsv);
            }
        }
    }
    public BigInteger getDummy() throws MgmtException {
        return BigInteger.valueOf(0);
    }

    /*
     * This is the list of custom actions
     */

    /**
     * The "object" can be the name of a JVM, or null (which implies
     * all JVMs). Optionally the JVM name may be followed by a colon
     * and the name of a class hierarchy level; all classes below that
     * will be affected.
     */
    public BigInteger setLevel(BigInteger nodeId,
                               BigInteger levelB,
                               String jvmName) throws MgmtException {
       
        String object=jvmName;
        int nodeid = nodeId.intValue();
        int level = levelB.intValue();

        assert (level >= 0);
        assert (level < LOG_LEVELS.length);

        boolean allNodes = false;
        boolean allJVMs  = (object == null);

        if (nodeid <= 0) {
            allNodes = true;
        }

        // JVM name followed by a class name?
        String jvm = "";
        String className = "";
        if (object != null) {
            jvm = object;

            int pos = object.indexOf(':');
            if (pos >= 0) {
                jvm = object.substring(0, pos);
                className = object.substring(pos + 1);
            }
        }

        if (jvm.equals("") || jvm.equalsIgnoreCase ("all")) {
            allJVMs = true;
        }

        Node [] nodes = null;
        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            //
            // Internationalize here
            //
            throw new MgmtException("Failed to retrieve log level");
        }
        for (int i = 0; i < nodes.length; i++) {

            if (!allNodes && nodes[i].nodeId() != nodeid) {
                continue;
            }

            if (!nodes[i].isAlive()) {
                continue;
            }

            NodeMgrService.Proxy nodeProxy = nodes[i].getProxy();
            if (nodeProxy == null) {
                logger.severe (
                    "cannot get node manager proxy for "
                        + nodes[i].nodeId());
                continue;
            }

            boolean foundJVM = allJVMs; // to determine if the JVM is found

            Service[] services = nodeProxy.getServices();
            for (int j = 0; j < services.length; j++) {
                if (!allJVMs && jvm.compareTo (services[j].getName()) != 0) {
                    continue;
                }

                if (!services[j].isJVM()) {
                    continue;
                }

                if (!services[j].isManaged()) {
                    continue;
                }

                ManagedService.ProxyObject obj;
                obj = services[j].getProxy(nodes[i].nodeId());
                if (obj == null) {
                    logger.severe (
                        "cannot get proxy for " + services[j] + " on node "
                        + nodes[i].nodeId());
                    continue;
                }

                if (! (obj.getAPI() instanceof JVMService)) {
                    logger.severe (
                        "cannot get API for " + services[j] + " on node "
                        + nodes[i].nodeId());
                    continue;
                }

                foundJVM = true;
                JVMService api = (JVMService) obj.getAPI();
                try {

                    logger.info ("setting log level to " +
                        LOG_LEVELS[level] + " for " + services[j].getName()
                        + " on node " + nodes[i].nodeId());

                    api.setLogLevel (LOG_LEVELS[level], className);

                }
                catch (Exception e) {
                    logger.severe (
                        "unable to set log level (rmi error) for "
                        + services[j] + " " + e);
                }
            }

            if (!foundJVM) {
                logger.info ("unable to set log level for jvm '" + jvm
                    + "': no such jvm");
            }
        }
        return BigInteger.valueOf(0);
    }


}
