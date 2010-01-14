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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.*;
import java.util.*;
import java.io.*;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.cm.jvm_agent.JVMService;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.admin.mgmt.Utils;
import com.sun.honeycomb.admin.mgmt.AdminException;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.util.sysdep.DiskOps;
import com.sun.honeycomb.admin.ProfilerIntf;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class HCProfilerAdapter 
    implements HCProfilerAdapterInterface {

    private Node []              nodes;
    /** Version file */
    private final static String HC_VERSION_FILE = "/opt/honeycomb/version";
    /** The property from which to read the cell name */
    private final static String PROP_CELL_NAME = "honeycomb.cell.name";
    private static transient final Logger logger = 
        Logger.getLogger(HCProfilerAdapter.class.getName());

    public void loadHCProfiler()
        throws InstantiationException {
        try {
            nodes = Utils.getNodes();
        } catch (AdminException ae) {
            throw new InstantiationException("Internal error : " +
                " failed to instanciate the Profile adapter");
        }
    }

    /*
    * This is the list of accessors to the object
    */
    public String getModules() throws MgmtException {
        String moduleslist = new String();
        File parsers = new File(ProfilerIntf.PROFILER_PARSER_PATH);
        String lfiles[] = parsers.list();
        for (int i = 0; i < lfiles.length; i++) {
            if (lfiles[i].startsWith(ProfilerIntf.PARSER_PREFIX)) {
                int bindex = ProfilerIntf.PARSER_PREFIX.length();
                int eindex = lfiles[i].lastIndexOf(".sh");
                if (eindex == -1) {
                    continue;
                }
                String module = lfiles[i].substring(bindex, eindex);                
                moduleslist = moduleslist.concat(module + " ");
            }
         }
        if (moduleslist.length() == 0) {
            moduleslist = "none";
        }
        return moduleslist;
    }
    public BigInteger getDummy() throws MgmtException {
        return BigInteger.valueOf(0);
    }

    /*
     * This is the list of custom actions
     */
    public BigInteger stop(BigInteger dummy) throws MgmtException {

        NodeMgrService.Proxy nodemgr;
        nodemgr = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        
        boolean error = false;
        StringBuffer errorMsg = new StringBuffer("operation failed on ");
        
        for (int i = 0; i < nodes.length; i++) {
            if (!nodes[i].isAlive()) {
                continue;
            }
            ManagedService.ProxyObject proxy;
            proxy = ServiceManager.proxyFor(nodes[i].nodeId(), "Profiler");
            if (!(proxy instanceof ProfilerIntf.Proxy)) {
                logger.warning("failed to get Profiler proxy for node " + 
                               nodes[i].nodeId()
                               );
                errorMsg.append(nodes[i].nodeId() + "(failed to get proxy) ");
                error = true;
                continue;
            }
            if (!(proxy.getAPI() instanceof ProfilerIntf)) {
                logger.warning("failed to get Profiler api for node " + 
                               nodes[i].nodeId()
                               );
                errorMsg.append(nodes[i].nodeId() + "(failed to get rmi api) ");
                error = true;
                continue;
            }
            ProfilerIntf api = (ProfilerIntf) proxy.getAPI();
            try {
                api.stopProfiling();
            } catch (ManagedServiceException cme) {
                logger.warning("failed to stop profiler on node " + 
                               nodes[i].nodeId() + " reason: " + cme.getMessage()
                               );
                error = true;
                errorMsg.append(nodes[i].nodeId() + "(" + cme.getMessage() + ") ");
            }
        }
        if (error) {
            throw new MgmtException(errorMsg.toString());
        }    

        return BigInteger.valueOf(0);
    }

    public String tarResult(BigInteger dummy) throws MgmtException {
        NodeMgrService.Proxy nodemgr;
        nodemgr = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        Disk [] nodeDisks = null;
        try {
            nodeDisks = Utils.getDisksOnNode(nodemgr.nodeId());
        } catch (AdminException ae) {
            throw new MgmtException("Failed to retrieve result");
        }

        String target = null;
        String diskPath = null;
        for (int d=0; d < nodeDisks.length; d++) {
            Disk disk = nodeDisks[d];
            if (null != disk && disk.isEnabled()) {
                diskPath = disk.getPath();
                target = diskPath + ProfilerIntf.PROFILE_RESULT_TARDIR;
                break;
            }
        }
        if (target == null) {
            throw new MgmtException("no disk available");
        }
        DiskOps.getDiskOps().rmdirP(target);

        for (int i = 0; i < nodes.length; i++) {
            File f = new File(target + "/" + nodes[i].nodeId());
            if (!f.exists()) {
                f.mkdirs(); 
            }

            if (!nodes[i].isAlive()) {
                continue;
            }
            ManagedService.ProxyObject obj;
            obj = ServiceManager.proxyFor(nodes[i].nodeId(), "Profiler");
            if (!(obj instanceof ProfilerIntf.Proxy)) {
                logger.warning("failed to get Profiler proxy for node " + 
                               nodes[i].nodeId()
                               );
                continue;
            }
            ProfilerIntf.Proxy proxy = (ProfilerIntf.Proxy) obj;
            
            String parser = ProfilerIntf.PROFILER_PARSER_PATH +
                ProfilerIntf.PARSER_PREFIX + proxy.getPrfModule() + ".sh";
            try {
                String cpCmd = "/usr/bin/cp " + parser + " " + target;
                Exec.exec (cpCmd, logger);
                
            } catch (IOException ioe) {
                logger.warning("Unable to copy parser file " +ioe.getMessage());
            }
                

            if (!(proxy.getAPI() instanceof ProfilerIntf)) {
                logger.warning("failed to get Profiler api for node " + 
                               nodes[i].nodeId()
                               );
                continue;
            }
            ProfilerIntf api = (ProfilerIntf) proxy.getAPI();
            try {
                String result = api.stopProfiling();
                String cpCmd = "/usr/bin/cp " + result + " " + f.getCanonicalPath();
                Exec.exec (cpCmd, logger);

            } catch (IOException ioe) {
                logger.warning("Unable to copy data files " +ioe.getMessage());

            } catch (ManagedServiceException cme) {
                logger.warning("failed to stop profiler on node " + 
                               nodes[i].nodeId() + " reason: " + cme.getMessage()
                               );
            }
        }

        String devname = ClusterProperties.getInstance().getProperty(PROP_CELL_NAME);
        if (devname == null) {
            devname = "unknown";
        }
        FileOutputStream versout = null;
        FileInputStream versin = null;
        try {
            versout = new FileOutputStream(target + ProfilerIntf.VERSION_FILE);
            versout.write(devname.getBytes());
            versout.write("\n".getBytes());
            versin = new FileInputStream(HC_VERSION_FILE);
            byte [] buf = new byte[2048];
            int res;
            while ((res = versin.read(buf, 0, buf.length)) > 0) {
                versout.write(buf, 0, res);
            }
        } catch (IOException ioe) {
            logger.warning("failed to create version file: " + ioe);

        } finally {
            if (versout != null) {
                try {
                    versout.close();
                } catch (IOException ignore) {}
            }
            if (versin != null) {
                try {
                    versin.close();
                } catch (IOException ignore) {}
            }
        }                

        try {
            String tarfile = null;
            if (target != null) {
                tarfile = target + ".tar";
                String tarCmd = "/usr/bin/tar -cf " + tarfile + 
                    " -C " + diskPath + ProfilerIntf.PROFILE_RESULT_DIR + 
                    " " + ProfilerIntf.RESULT_TARFILE;
                Exec.exec (tarCmd, logger);
            }
            return tarfile;
                
        } catch (IOException ioe) {
            String error = "Unable to create tar file " +ioe.getMessage();
            logger.warning(error);
            throw new MgmtException(error);
        }

    }
    public BigInteger start(String module,
                            BigInteger nodeId,
                            BigInteger howlongB) throws MgmtException {
        
        int nodeid = nodeId.intValue();
        int howlong = howlongB.intValue();

        NodeMgrService.Proxy nodemgr;
        nodemgr = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        boolean error = false;
        StringBuffer errorMsg = new StringBuffer("operation failed on ");
        
        for (int i = 0; i < nodes.length; i++) {
            if (nodeid > 0 && nodes[i].nodeId() != nodeid) {
                continue;
            }
            if (!nodes[i].isAlive()) {
                errorMsg.append(nodes[i].nodeId() + "(not alive) ");
                continue;
            }

            ManagedService.ProxyObject proxy;
            proxy = ServiceManager.proxyFor(nodes[i].nodeId(), "Profiler");
            if (!(proxy instanceof ProfilerIntf.Proxy)) {
                logger.warning("failed to get Profiler proxy for node " + 
                               nodes[i].nodeId()
                               );
                errorMsg.append(nodes[i].nodeId() + "(failed to get proxy) ");
                error = true;

                continue;
            }

            if (!(proxy.getAPI() instanceof ProfilerIntf)) {
                logger.warning("failed to get Profiler api for node " + 
                               nodes[i].nodeId()
                               );
                errorMsg.append(nodes[i].nodeId() + "(failed to get rmi api) ");
                error = true;

                continue;
            }
            ProfilerIntf api = (ProfilerIntf) proxy.getAPI();
            try {
                api.startProfiling(module, howlong);

            } catch (ManagedServiceException cme) {
                logger.warning("failed to start profiler on node " + 
                               nodes[i].nodeId() + " reason: " + cme.getMessage()
                               );
                error = true;
                errorMsg.append(nodes[i].nodeId() + "(" + cme.getMessage() + ") ");
            }
        }
        if (error) {
            throw new MgmtException(errorMsg.toString());
        }
        return BigInteger.valueOf(0);
    }
}
