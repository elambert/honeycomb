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



/**
 * Parse node_config.xml into a list of Service objects.
 *
 * This code is mostly copy-and-paste of server/src/.../cm/NodeConfig.java
 * I could not use NodeConfig directly because in addition to parsing,
 * it creates ServiceMailbox'es and attaches to JVMs, which I don't need.
 * This class is just a straight-up parser.
 *
 */

package com.sun.honeycomb.cm.node_mgr;

import com.sun.honeycomb.cm.node_mgr.Service;
import com.sun.honeycomb.cm.node_mgr.JVMProcess;
import java.util.Vector;
import java.util.List;
import java.io.File;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.logging.Logger;

public class NodeConfigParser extends DefaultHandler 
{
    /** Default location of the input file.
     */
    public static final String NODE_CONFIG_XML = "node_config.xml";

    /** State enum: result of compare(). 
        Describes honeycomb run mode in terms of the set of running non-master services.
        The values do not match runlevels.
        The only legal modes are MAINTENANCE and DATA, others are error states.
    */
    public static final int SUBZERO_MODE = 0;
    public static final int MAINTENANCE_MODE = 1; // runlevel 2 and above are data
    public static final int HALFWAY_MODE = 5;
    public static final int DATA_MODE = 10;    // above highest valid runlevel
    public static final int OVERBOARD_MODE = 100;

    /** Master state enum: result of compareMaster().
     *  Describes mode in terms of the set of running master services.
     *  The only legal modes are MASTER and NON_MASTER, others are error states.
     */
    public static final int NON_MASTER = 1;
    public static final int MISSING_MASTER = 2;
    public static final int MASTER = 3;
    public static final int USURPER_MASTER = 4;
    public static final int OVERBOARD_MASTER = 5;

    /** All this is parsed out of node_config.xml
     */
    public String classpath = null;
    public String libdir = null;
    public String logconf = null;
    public String javabin = null;
    public String classprefix = null;
    public String jvmagent = null;
    public String nodeagent = null;
    public int    heartbeat = 0;

    private List jvmList = new Vector();
    private List serviceList = new Vector();

    private JVMProcess curJVM = null;
    private int curLevel = 0;
    private int curId = 0;
    private boolean curMasterOnly = false;
    private String mboxPath = null;
    private int nbServices = 0;
    private int nbMasterServices = 0;

    private static String config; // config file we parse
    private static Logger logger;

    /** Singleton access.
     *  Parsed node_config is used by NodeMgrVerifier on the client side,
     *  and all test services in Honeycomb on the cluster side.
     */
    private static NodeConfigParser nodeConfig;
    private static boolean gotInstance;

    public static NodeConfigParser getInstance() {
        if (!gotInstance) {
            try {
                nodeConfig = new NodeConfigParser();
            } catch (Exception e) {
                nodeConfig = null;
                logger.warning("Failed to parse node config: " + config);
            }
            gotInstance = true;
        }
        return nodeConfig;
    }

    /** Constructor parses given file of node_config.xml format.
     */
    private NodeConfigParser() throws Exception
    {
        logger = Logger.getLogger(NodeConfigParser.class.getName());
        String confdir = System.getProperty("honeycomb.config.dir", "/opt/honeycomb/share");
        String conffile = System.getProperty("honeycomb.config.node_config", NODE_CONFIG_XML);
        config = confdir + "/" + conffile;
        SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        parser.parse(new File(config), this);
    }

    /** Find a service by its tag, return null if no such service.
     */
    public Service findSrv(String tag) {
        for (int i = 0; i < serviceList.size(); i++) { // nominal set
            Service srv = (Service) serviceList.get(i);
            if (srv.getTag().equals(tag))
                return srv;
        }
        return null;
    }

    /** Find instance NUM of service CLASSNAME in the list, return service object.
     *  If NUM > count of CLASSNAME services in list, return the last match if any.
     *  This is a hack to recognize the same TestService restarted after quorum loss/regain.
     */
    public Service getService(String className, int num) {
        int count = 0;
        Service found = null;
        for (int i = 0; i < serviceList.size(); i++) { // nominal set
            Service srv = (Service) serviceList.get(i);
            if (srv.getClassName().equals(className)) { // classname match
                found = srv;
                if (count == num) // requested instance
                    break;
                count++;
            }
        }
        return found; // last match, or null if no match at all.
    }

    /** Find a JVM by its tag, return null if no such JVM.
     */
    public JVMProcess findJVM(String tag) {
        for (int i = 0; i < jvmList.size(); i++) { // nominal set
            JVMProcess jvm = (JVMProcess) jvmList.get(i);
            if (jvm.toString().equals(tag))
                return jvm;
        }
        return null;
    }

    /** For each service, provide output: name + runlevel + master-only
     *  MASTER_SERVICE[5M] JUST_A_SERVICE[3]
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < serviceList.size(); i++) {
            Service srv = (Service) serviceList.get(i);
            sb.append(srv.getTag());
            sb.append("[" + srv.getRunLevel());
            if (srv.isMasterOnly())
                sb.append("M] ");
            else
                sb.append("] ");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Compare method takes a list of services with their runtime state,
     *  and expected runlevel (maintenance / data).
     *  Compares against the nominal set of services and their runlevels.
     *  Returns one of four states as defined by enum.
     *  Ignores master-only services (call compareMaster() for those).
     */
    public int compareNormal(Vector runServices, int runlevel)
    {
        boolean extra_srv = false;
        boolean missing_srv = false;

        for (int i = 0; i < serviceList.size(); i++) { // nominal set
            Service base = (Service) serviceList.get(i);
            if (base.isMasterOnly())
                continue; // skip master-only services
            boolean found = false;

            for (int j = 0; j < runServices.size(); j++) { // running set
                Service srv = (Service) runServices.get(j);

                if (srv.getTag().equals(base.getTag())) {
                    found = true;
                    if (runlevel >= base.getRunLevel()) { // service should run
                        if (!srv.isRunning())
                            missing_srv = true;
                    } else { // service should not run
                        if (srv.isRunning())
                            extra_srv = true;
                    }
                }
            }
            if (!found) { // service from nominal set is not present in running set
                if (runlevel >= base.getRunLevel()) { // but should be running!
                    missing_srv = true;
                }
            }
        }
        if (runlevel == MAINTENANCE_MODE) {
            if (missing_srv)
                return SUBZERO_MODE;
            else if (extra_srv)
                return HALFWAY_MODE;
            else 
                return MAINTENANCE_MODE;
        } else if (runlevel == DATA_MODE) {
            if (missing_srv)
                return HALFWAY_MODE;
            else if (extra_srv)
                return OVERBOARD_MODE;
            else
                return DATA_MODE;
        }
        else
            return runlevel; // bad mode?
    }

    /** Takes a list of services with their runtime state,
     *  expected runlevel (maintenance / data), and isMaster flag.
     *  Compares master-only services against nominal set.
     *  Returns one of four states as defined by enum.
     */
    public int compareMaster(Vector runServices, int runlevel, boolean isMaster)
    {
        boolean extra_srv = false;
        boolean missing_srv = false;

        for (int i = 0; i < serviceList.size(); i++) { // nominal set
            Service base = (Service) serviceList.get(i);
            if (!base.isMasterOnly())
                continue; // skip non-master services
            boolean found = false;

            for (int j = 0; j < runServices.size(); j++) { // running set
                Service srv = (Service) runServices.get(j);

                if (srv.getTag().equals(base.getTag())) {
                    found = true;
                    if (runlevel >= base.getRunLevel()) { // service should run on master node 
                        if (isMaster && !srv.isRunning())
                            missing_srv = true;
                        if (!isMaster && srv.isRunning())
                            extra_srv = true;
                    } 
                }
            }
            if (!found) { // service from nominal set is not present in running set
                if (runlevel >= base.getRunLevel()) { // but should be running!
                    if (isMaster)
                        missing_srv = true;
                }
            }
        }
        if (isMaster) {
            if (missing_srv)
                return MISSING_MASTER;
            else if (extra_srv)
                return OVERBOARD_MASTER;
            else 
                return MASTER;
        } else {
            if (extra_srv) 
                return USURPER_MASTER;
            else
                return NON_MASTER;
        }
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
            // new jvm baseline - ignore, we are interested only in services
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
                curJVM = new JVMProcess(curId, NodeMgrService.mboxTag, nodeagent, size);
            } else {
                curJVM = new JVMProcess(curId, name, javabin, params,
                                        jvmagent, libdir, logconf,
                                        localClasspath, envp, size);
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
            String cls = classprefix + "." + cl;
            
            Service svc = new Service (name, cls, curJVM.getId(), curLevel, curMasterOnly, -1, false);
            serviceList.add(svc);
        }
    }

    /** Translates state enum into human-readable string.
     */
    public static String printSrvState(int state) {
        switch (state) {
        case SUBZERO_MODE:
            return "SUBZERO";
        case MAINTENANCE_MODE:
            return "MAINTENANCE";
        case HALFWAY_MODE:
            return "HALFWAY";
        case DATA_MODE:
            return "DATA";
        case OVERBOARD_MODE:
            return "OVERBOARD";
        default:
            return "UNKNOWN [" + state + "]";
        }
    }

    /** Translates state enum into human-readable string.
     */
    public static String printMasterState(int state) {
        switch (state) {
        case NON_MASTER:
            return "NON_MASTER";
        case MISSING_MASTER:
            return "MISSING_MASTER_SERVICES";
        case MASTER:
            return "MASTER";
        case USURPER_MASTER:
            return "USURPER_MASTER";
        case OVERBOARD_MASTER:
            return "EXTRA_MASTER_SERVICES";
        default:
            return "UNKNOWN [" + state + "]";
        }
    }
}
