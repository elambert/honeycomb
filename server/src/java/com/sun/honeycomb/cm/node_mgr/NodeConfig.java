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



package com.sun.honeycomb.cm.node_mgr;

import com.sun.honeycomb.cm.ServiceManager;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.logging.Logger;


final public class NodeConfig extends DefaultHandler {

    public String classpath = null;
    public String libdir = null;
    public String logconf = null;
    public String javabin = null;
    public String classprefix = null;
    public String jvmagent = null;
    public String nodeagent = null;
    public int    heartbeat = 0;

    private List jvmList = new Vector();
    private ServiceMailbox[] services = null;
    private JVMProcess curJVM = null;
    private int curLevel = 0;
    private int curId = 0;
    private boolean curMasterOnly = false;
    private String mboxPath = null;
    private int nbServices = 0;
    private int nbMasterServices = 0;

    private static NodeConfig instance = null;

    private static final Logger logger  = Logger.getLogger(NodeConfig.class.getName());

    static NodeConfig getInstance() throws MgrException {
        synchronized (NodeConfig.class) {
            if (instance == null) {
                instance = new NodeConfig(NodeMgr.HC_NODE_CONFIG);
            }
        }
        return instance;
    }
    
    private NodeConfig(String config) throws MgrException {
        mboxPath = "/" + NodeMgr.nodeId() + "/";
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            parser.parse(new File(config), this);
            TreeSet set = new TreeSet();
            for (int i = 0; i < jvmList.size(); i++) {
                JVMProcess jvm = (JVMProcess) jvmList.get(i);
                jvm.init();
                List svc = jvm.getServices();
                for (int j = 0; j < svc.size(); j++) {
                    if (((ServiceMailbox)svc.get(j)).masterOnly()) {
                        nbMasterServices++;
                    }
                    set.add(svc.get(j));
                    nbServices++;
                }
            }
            services = new ServiceMailbox[set.size()];
            services = (ServiceMailbox[]) set.toArray(services);
        } catch (Exception e) {
            throw new MgrException(e);
        }
    }

    /**
     * return the list of services sorted by runlevel
     */
    ServiceMailbox[] getServices() {
        return services;
    }

    /**
     * return the corresponding JVM
     */
    JVMProcess getJVM(ServiceMailbox svc) {
        return (JVMProcess) jvmList.get(svc.getId());
    }

    /**
     * return the node manager service
     */
    ServiceMailbox getService(Class cls) {
        for (int i = 0; i < services.length; i++) {
            if (services[i].getClassName().equals(cls.getName())) {
                return services[i];
            }
        }
        return null;
    }

    /**
     * return the list of jvms
     */
    List getJVMs() {
        return jvmList;
    }

    /**
     * return the number of services
     */
    int nbServices() {
        return nbServices;
    }

    /**
     * return the number of master services
     */
    int nbMasterServices() {
        return nbMasterServices;
    }

    /**
     * parse the node config xml file.
     */
    public void startElement(String uri,
                             String localName,
                             String qName,
                             Attributes attributes) throws SAXException {
        if (qName.equals("node")) {
            //
            // config baseline
            //
            javabin = attributes.getValue("javabin");
            classprefix = attributes.getValue("package");
            libdir = attributes.getValue("libdir");
            logconf = attributes.getValue("logconf");
            heartbeat = Integer.parseInt(attributes.getValue("heartbeat"));
            classpath = attributes.getValue("classpath");
            String agent = attributes.getValue("jvmagent");
            jvmagent = classprefix + "." + agent;
            String nodemgr = attributes.getValue("nodemgr");
            nodeagent = classprefix + "." + nodemgr;
        }
        if (qName.equals("jvm")) {
            //
            // new jvm baseline
            //
            String name = attributes.getValue("name");
            String params = attributes.getValue("params");
            String rss = attributes.getValue("rss");
            String localClasspath = attributes.getValue("classpath");
            String[] envp = null;

            if (localClasspath == null) {
                localClasspath = classpath;
            }

            String envpString = attributes.getValue("env");
            if (envpString != null) {
                envp = envpString.split(":");
            }

            int size = 0;
            if (rss != null) {
                if (rss.endsWith("MB")) {
                    size = Integer.parseInt(
                                   rss.substring(0, rss.length() - 2));
                }
            }
            if (params.matches("node\\s++manager\\s++jvm")) {
                String tag = mboxPath + NodeMgrService.mboxTag;
                curJVM = new JVMProcess(curId, tag, nodeagent, size);
            } else {
                String tag = mboxPath + name;
                curJVM = new JVMProcess(curId,
                                        tag,
                                        javabin,
                                        params,
                                        jvmagent,
                                        libdir,
                                        logconf,
                                        localClasspath,
                                        envp,
                                        size);
            }
            curId++;
            jvmList.add(curJVM);
        }
        if (qName.equals("group")) {
            //
            // new group baseline
            //
            String runlevel = attributes.getValue("runlevel");
            String location = attributes.getValue("location");

            curLevel = Integer.parseInt(runlevel);
            curMasterOnly = false;
            if (location != null) {
                if (location.equals("master-node")) {
                    curMasterOnly = true;
                }
            }
        }
        if (qName.equals("service")) {
            //
            // new service baseline
            //
            String name = attributes.getValue("name");
            String cl   = attributes.getValue("class");
            String shutdownTimeout = attributes.getValue("shutdownTimeout");
            String opts = attributes.getValue("options");

            String cls = classprefix + "." + cl;
            String tag = mboxPath + name; 

            ServiceMailbox svc;
            try {
                int shutdownTimeoutValue = -1;
                if (shutdownTimeout != null) {
                    try {
                        shutdownTimeoutValue = Integer.parseInt(shutdownTimeout);
                    } catch (NumberFormatException e) {
                        logger.warning("The shutdownTimeout attribute value for ["+
                                       name+"] is incorrect ("+
                                       shutdownTimeout+") - "+
                                       e.getMessage());
                    }
                }

                svc = new ServiceMailbox(curJVM.getId(), 
                                         cls,
                                         tag, 
                                         curLevel, 
                                         curMasterOnly,
                                         shutdownTimeoutValue,
                                         opts,
                                         false);

            } catch (IOException ioe) {
                throw new SAXException(ioe);
            }
            curJVM.attach(svc);
        }
    }
}

