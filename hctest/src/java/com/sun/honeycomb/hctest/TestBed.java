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



package com.sun.honeycomb.hctest;

import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.hctest.suitcase.*;
import com.sun.honeycomb.hctest.rmi.clntsrv.clnt.ClntSrvClnt;
import com.sun.honeycomb.hctest.rmi.spsrv.clnt.SPSrvClnt;
//import com.sun.honeycomb.hctest.rmi.spsrv.common.SPSrvConstants;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.io.*;
import java.util.*;
import java.lang.Runtime;
import java.lang.reflect.Method;
import java.rmi.UnmarshalException;


/**
 *  TestBed manages Honeycomb-specific environment where the tests run.
 *  TestBed is a singleton, accessible by anyone who imports the package.
 *
 *  TestBed manages:
 *  
 *  <ul>
 *      <li> cluster/client properties
 *      <li> general remote facilities: 
 *       <ul>
 *          <li> access to cluster admin (cli) interface 
 *          <li> access to cluster service processor RMI server (for logging)
 *       </ul>
 *      <li> local facilities: 
 *       <ul>
 *          <li> file cache for reuse of files by multiple Suites
 *       </ul>
 *      <li> remote facilities:
 *       <ul>
 *          <li> RMI clients for any Client API hosts.
 *       </ul>
 *  </ul>
 */

public class TestBed implements TestHook {

    public static final int USE_VIP = 1;
    public static final int USE_ROUND_ROBIN_IP = 2;
    public static final int USE_RANDOM_IP = 3;



    public static boolean doHash = true;

    private int node_mode = USE_VIP;

    // test running mode: is the cluster up (default), or not?
    // if cluster startup is part of the test, set gotCluster = false.
    public boolean gotCluster; 

    /**
     *   The singleton is accessed via the static method getInstance() .
     */
    private static TestBed b;
    synchronized public static TestBed getInstance() {
        //
        //  TestBed not needed when instantiating Suites to
        //  get their help() methods.
        //
        if (Run.getInstance().isHelpMode())
            return null;
        if (b == null) {
            b = new TestBed();
        }
        return b;
    }
    
    /* sorry all these fields must remain public
     * because a lot of legacy code accesses them directly...
     */

    public String dataVIP = null;
    public String[] dataIPs = null;  // for non-VIP use, maybe multi-VIP..
    public int dataPort = HoneycombTestConstants.DEFAULT_DATA_PORT;
    public String adminVIP = null;
    public String spIP = null;
    public String auditIP = null;
    public String dataVIPaddr = null;
    // public String[] dataIPaddrs = null; // not supported yet
    public String adminVIPaddr = null;
    public String spIPaddr = null;
    public String auditIPaddr = null;
    public ClusterTestContext defaultCluster = null;  
    public SPSrvClnt sp = null;
    public String bedName = null;
    private String logTag = null;
    private String build = null; // cluster build version
    private boolean rmi = false; // are we running with RMI? not by default
    
    // should we collect cluster statistics, and how often
    private int statsLevel = 0; 

    public RunCommand shell = new RunCommand();
    public FileCache fc = new FileCache();  // for local file reuse

    /* internal bookkeeping
     */
    private String hostname = Util.localHostName();
    private ArrayList errors = new ArrayList();

    private Hashtable spsrvs = new Hashtable();
    private ArrayList clients = null;
    private HashMap clients_sync = new HashMap(); // for reconnect collisions

    private Run run = null; // access to singleton

    /**
     *  Private constructor, called once on first call to TestBed.get()
     *  Sets up the TestBed according to the properties
     */
    
   private TestBed() {
       init();
   }
   public void init() {
      
       run = Run.getInstance();
       
       LogArchive archiver = LogArchive.getInstance();
       /* This should be kept in sync with automount entry on QB web server
        * No other piece of code references this path
        */
       // XXX make log archiving optional
       String archivePath = System.getProperty(
	       			HCLocale.PROPERTY_LOG_ARCHIVE_PATH);
       String archiveUser = System.getProperty(
			        HCLocale.PROPERTY_LOG_ARCHIVE_USER);
       String archiveDest = System.getProperty(
		                HCLocale.PROPERTY_LOG_ARCHIVE_HOST);
       
       // Default Logger! 
       archiver.addLogDestination(new ArchiveLogDestination(archiveUser +"@" + 
	       archiveDest + ":" + archivePath));

       /* parse properties relevant to TestBed. Careful: order matters!
        */
        String s;
    
       s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_WDLOG);
       if (s != null) {
    	  Log.INFO("Using webdav log on: " + s);
          archiver.addLogDestination(new WebdavLogDestination("http://"+s,"/webdav/test_logs/" + run.getId()));
       }
       
       gotCluster = true; // set to false from test's constructor if needed


        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLIENTS);
        if (s != null) 
            parseClients(s);
    
        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
        if (s != null) { 
            // testbed name defaults to cluster name, VIPs are derived
            bedName = s;
            dataVIP = bedName + "-data";
            adminVIP = bedName + "-admin";
            spIP = bedName + "-cheat";
        }


        // setting VIPs and testbed on the command line overrides the defaults
        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_VIP);
        if (s != null) {
            String elems[] = s.split(",");
            // default
            dataVIP = elems[0].trim();
            dataIPs = new String[elems.length];
            for (int i=0; i<elems.length; i++)
                dataIPs[i] = elems[i].trim();
        } else if (dataVIP != null) {
            // so required properties check won't complain...
            TestRunner.setProperty(HoneycombTestConstants.PROPERTY_DATA_VIP, 
                                   dataVIP);
            // in case a multi-IP-aware test is run w/ single IP
            dataIPs = new String[1];
            dataIPs[0] = dataVIP;
        }

        // set the data port if it is specified
        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_PORT);
        if (s != null) {
            try {
                dataPort = Integer.parseInt(s);
                Log.INFO("Using specified port " + dataPort);
            } catch (Throwable t) {
                errors.add("Unexpected value for " +
                    HoneycombTestConstants.PROPERTY_DATA_PORT +
                    ": " + dataPort);
            }
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_ADMIN_VIP);
        if (s != null) 
            adminVIP = s;
        else if (adminVIP != null) 
            TestRunner.setProperty(HoneycombTestConstants.PROPERTY_ADMIN_VIP, 
                                   adminVIP);

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_SP_IP);
        if (s != null) 
            spIP = s;
        else if (spIP != null)
            TestRunner.setProperty(HoneycombTestConstants.PROPERTY_SP_IP, spIP);

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_AUDIT_IP);
        if (s != null)
            auditIP = s;
       
        // Set IP properties to avoid issues where we have flaky nameservers
        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_ADMIN_VIP_ADDR);
        if (s != null)
            adminVIPaddr = s;
        else if (adminVIP != null) {
            adminVIPaddr = HCUtil.getIPFromHostname(adminVIP);
            if (adminVIPaddr != null)
                TestRunner.setProperty(HoneycombTestConstants.PROPERTY_ADMIN_VIP_ADDR, adminVIPaddr);
        }

        // XXX did not convert array of data IPs to ipaddrs...
        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_VIP_ADDR);
        if (s != null)
            dataVIPaddr = s;
        else if (dataVIP != null) {
            dataVIPaddr = HCUtil.getIPFromHostname(dataVIP);
            if (dataVIPaddr != null)
                TestRunner.setProperty(HoneycombTestConstants.PROPERTY_DATA_VIP_ADDR, dataVIPaddr);
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_SP_IP_ADDR);
        if (s != null)
            spIPaddr = s;
        else if (spIP != null) {
            spIPaddr = HCUtil.getIPFromHostname(spIP);
            if (spIPaddr != null)
                TestRunner.setProperty(HoneycombTestConstants.PROPERTY_SP_IP_ADDR, spIPaddr);
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_AUDIT_IP_ADDR);
        if (s != null)
            auditIPaddr = s;
        else if (auditIP != null) {
            auditIPaddr = HCUtil.getIPFromHostname(auditIP);
            if (auditIPaddr != null)
                TestRunner.setProperty(HoneycombTestConstants.PROPERTY_AUDIT_IP_ADDR, auditIPaddr);
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_DATA_MODE);
        if (s != null) {
            if (s.equals(HoneycombTestConstants.PROPERTY_ROUND_ROBIN_DATA_MODE))
                setDataMode(USE_ROUND_ROBIN_IP);
            else if (s.equals(HoneycombTestConstants.PROPERTY_RANDOM_DATA_MODE))
                setDataMode(USE_RANDOM_IP);
            else
                errors.add("Unexpected value for " +
                           HoneycombTestConstants.PROPERTY_DATA_MODE + ": " +s);
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_CLUSTER);
        if (s != null) {
            gotCluster = false; // cluster isn't up when the test starts
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_TESTBED);
        if (s != null) 
            bedName = s;
        else if (bedName != null) 
            TestRunner.setProperty(HoneycombTestConstants.PROPERTY_TESTBED, 
                                   bedName);
        
        if ((bedName == null) && (dataVIP != null)) {
            try { // guess at the testbed name from its dataVIP
                bedName = dataVIP.substring(0, dataVIP.lastIndexOf("-"));
            } catch (Exception e) { ; } 
        }
        if (bedName != null) 
            run.setTestBed(bedName); // reference in QB DB
        else            
            bedName = "unknown";
        
        if (spIP != null) { 
            // see if we should log to / get logs from the service processor
            s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NOLOG);
            if (s == null) {
                // get only portion of syslog that pertains to this test run
                // archive under the name "cluster.log"
                archiver.addLogSource("root@"+spIP, 
                                      archiver.syslog("root@"+spIP), 
                                      "cluster.log", LogArchive.PARTIAL);
            }
            Log.addSyslogDest("root@" + spIP);
        }
        
        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_RMI);
        if ((s != null) && s.equals("yes")) {
            rmi = true; 

            //  connect to Service Processor rmi server  XXX audit server
            try {   
                sp = new SPSrvClnt(spIP);
            } catch (Throwable t) {
                errors.add("ERROR creating SP client: " + t);
            }

            /* make sure that clients can ping Service Processor
             *
            if (clients != null) {
                testClientAccessToCluster(spIP);
            }
            */
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_STATS);
        if (s != null) {
            if (s.equals("no")) statsLevel = 0;
            if (s.equals("run")) statsLevel = 1;
            if (s.equals("suite")) statsLevel = 2;
            if (s.equals("test")) statsLevel = 3;
        }

        s = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NOHASH);
        if (s != null)
            doHash = false;
        
        if (errors.size() > 0) {
            System.err.println("PROBLEMS WITH TestBed PROPERTIES:");
            for (int i=0; i<errors.size(); i++)
                System.err.println(errors.get(i).toString());
            System.exit(1);
        }

        // random string to tag our messages in server logs
        try { 
            logTag = RandomUtil.getRandomString(4);
        } catch (Exception e) {
            System.err.println("ERROR creating TestBed: " + e.toString());
            System.exit(1);
        }
        run.setLogTag(logTag); // record log tag with Run entry in QB DB

        defaultCluster = new ClusterTestContext(dataVIP, adminVIP, spIP, 
                                                auditIP, logTag, 
                                                Run.getInstance().getId(),
                                                TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER));

        // TestRunner will call tearDown() when tests are done or on exit
        TestRunner.registerTestHook(this);

        /* we used to determine build version here, and log state
         * but it's better to do so from testBed::logStart()
         * which is called from HoneycombSuite::setUp(),
         * by which time all properties are correctly set up.
         * No more ssh-ing to the cluster from constructor.
         */
    }


    /**
     *  Free any persistent resources that have been allocated.
     *
     *  Since TestBed is a singleton, it should clean up after itself
     *  But finalize() is not guaranteed to be called on JVM exit,
     *  and file cache will not be removed by the OS,
     *  so tearDown() must be called from a shutdown hook.
     */
    public void tearDown() {
        Log.DEBUG("TestBed tearDown(): cleaning up file cache");
        fc.clear();

        // end-of-run cluster statistics
        if (statsLevel > 0) 
            logAdminState();

        // stop channel monitor thread
        ChannelMonitor.done();
        
        // stop connection pool reaper thread
        ConnectionPool.stopReaperThread();


        SuitcaseLaunch launch = new SuitcaseLaunch();
    }

    public void startSP() throws HoneycombTestException {
        if (sp != null)
            return;
        sp = new SPSrvClnt(spIP);
    }

    /** For logging purposes: describe this testbed 
     */
    public String toString() {
        StringBuffer sb = new StringBuffer(1024);
        sb.append("TESTBED: " + bedName);
        if (dataVIP != null) sb.append(" dataVIP: " + dataVIP);
        if (adminVIP != null) sb.append(" adminVIP: " + adminVIP);
        if (spIP != null) sb.append(" spIP: " + spIP);
        if (build != null) sb.append(" build: [" + build + "]");
        if (logTag != null) sb.append(" logTag: " + logTag);
        // XXX: what else?
        return sb.toString();
    }

    ///////////////////////////////////////////////////////////////////////
    //  property-parsing
    //
    /**
     *  Turn a comma-delimited list of clients into a set
     *  of RMI client connections.
     */
    private void parseClients(String list) {

        clients = new ArrayList();
        StringTokenizer st = new StringTokenizer(list, ",");
        while (st.hasMoreTokens()) {
            String client = st.nextToken().trim();
            try {
                clients.add(new ClntSrvClnt(client));
                Object oo[] = new Object[2];
                oo[0] = new Boolean(false); // whether to wait
                oo[1] = new Object();       // what to wait on
                clients_sync.put(client, oo);
            } catch (Throwable t) {
                errors.add("Connecting to " + client + ": " + t);
            }
        }
    }

    /**
     *  Make sure that clients can access the cluster by
     *  having them ping the Service Processor.
     */
    private void testClientsAccessToCluster(String sp) {
        for (int i=0; i<clients.size(); i++) {
            ClntSrvClnt clnt = (ClntSrvClnt) clients.get(i);
            try {
                if (!clnt.ping(sp))
                    errors.add("client " + clnt.host + " cannot ping " + sp);
            } catch (Throwable t) {
                errors.add("client " + clnt.host + " ping " + sp + ": " + t);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////
    //  housekeeping
    //

    /**
     *  Log state returned by admin interface (CLI).
     */
    private void logAdminState() {

        if (adminVIP == null) return;

        try {

            Log.INFO("Getting cluster state information from " + adminVIP);
            HClusterState hc = getClusterState();

            // temporary solution to record this info in QB DB:
            // timestamped comments on the Run record
            // TODO: postState() to create state entry referencing Run
            run.addComment("\n" + toString() + " ADMIN STATE:\n" + 
                           "VERSION\n" + hc.version +
                           "SYSSTAT\n" + hc.sysstat +
                           "DF\n" + hc.df +
                           "HWSTAT\n" + hc.hwstat);
        } catch(Throwable t) {
            Log.ERROR("Failed to collect cluster stats" + t);
        }
    }

    /**
     *  log HC-specific environment, and pass log tag to cluster
     */
    public void logStart() throws Throwable {

        // is the build version already set (by the user)?
        build = run.getBuildVersion();
        if (build.equals("Unknown")) {
            if (gotCluster) {
                // see which build is on the cluster
                build = buildVersionTag();
                run.setBuildVersion(build);
            } // otherwise just leave it unknown
        }

        Log.SUM(toString());

        if (statsLevel > 1) logAdminState();
        if (sp != null) 
            logMsg("START RUN " + logTag); // to SP
    }

    /**
     *  log HC-specific environment, and pass log tag to cluster
     */
    public void logEnd() throws Throwable {
        if (statsLevel > 1) logAdminState();
        if (sp != null) 
            logMsg("END RUN " + logTag); // to SP
    }

    ///////////////////////////////////////////////////////////////////////
    //  direct access to service processor
    //
    /**
     *  Look up or create an spsrv client.
     */
    public SPSrvClnt getSPClient(ClusterTestContext cluster) 
                                                 throws HoneycombTestException {
        return getSPClient(cluster.spIP);
    }
    public SPSrvClnt getSPClient(String server) throws HoneycombTestException {
        if (server == null) {
            throw new HoneycombTestException("cluster.spIP is null");
        }
        try {
            SPSrvClnt spc = (SPSrvClnt) spsrvs.get(server);
            if (spc == null) {
                spc = new SPSrvClnt(server);
                spsrvs.put(server, spc);
            }
            return spc;
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }
    public boolean rmiEnabled() {
        return (rmi);
    }

    /**
     *  Base method to log a msg to an sp server, tags with logTag.
     *  XXX: should use audit server for this since it doesn't
     *  need the HC stack awareness of the sp server.
     */
    public void logMsg(ClusterTestContext cluster, String msg) 
                                                 throws HoneycombTestException {
        SPSrvClnt spc = getSPClient(cluster);
        //System.err.println("got server " + spsrv);
        spc.logMsg(logTag + " " + msg);
    }
    public void logMsg(String sp, String msg) throws HoneycombTestException {
        SPSrvClnt spc = getSPClient(sp);
        spc.logMsg(logTag + " " + msg);
    }

    /**
     *  Log a msg to the default sp server.
     */
    public void logMsg(String msg) throws HoneycombTestException {
        logMsg(defaultCluster, msg);
    }

    /**
     *  Get default cluster status.
     */
    public HClusterState getClusterState() throws HoneycombTestException {

        HClusterState hc = new HClusterState();

        hc.version = buildVersion();
        hc.df = df();
        hc.sysstat = sysstat();
        hc.hwstat = hwstat();

        return hc;
    }

    ///////////////////////////////////////////////////////////////////////
    //  direct access to admin interface 
    //  (best to have CLI ssh key installed locally)
    //
    /**
     *  ssh a command to admin@adminVIP
     */
    public String adminCmd(String cmd) throws HoneycombTestException {
        if (defaultCluster.adminVIP == null)
            throw new HoneycombTestException("adminVIP not defined");
        return shell.sshCmd("admin@" + defaultCluster.adminVIP, cmd);
    }

    /**
     *  Get hwstat of default cluster.
     */
    public String hwstat() throws HoneycombTestException {
        return adminCmd("hwstat");
    }

    /**
     *  Get sysstat of default cluster.
     */
    public String sysstat() throws HoneycombTestException {
        return adminCmd("sysstat");
    }

    /**
     *  Get disk usage of default cluster.
     */
    public String df() throws HoneycombTestException {
        return adminCmd("df");
    }

    /**
     *  Get build version of default cluster.
     */
    public String buildVersion() throws HoneycombTestException {
        return adminCmd("version");
    }

    /**
     *  Parse out build version tag (ie short identifying string)
     *  This parser matches honeycomb server build logic
     */
    public String buildVersionTag() {
        String bv; // build version: full, multi-line
        try {
            bv = buildVersion();
        } catch(HoneycombTestException e) {
            bv = "[Unknown]";
        }
        // expected format of fullVersion: sample output

        // ***** RELEASE [QA_drop0-3] *****
        // initrd.gz built Fri Aug 13 11:33:24 PDT 2004 on pratap
        // kernel version 2.6.5-charter #1 Fri Aug 13 09:41:50 PDT 2004
        
        String bt; // build tag: short, enclosed in [ ]
        try {
            bt = bv.substring(bv.indexOf('[')+1, bv.indexOf(']'));
        } catch (IndexOutOfBoundsException e) {
            Log.ERROR("Unexpected format of build version. " +
                      "Failed to parse: " + bv);
            bt = "Unknown"; 
        }
        return bt;
    }


    ///////////////////////////////////////////////////////////////////////
    //  choosing different data IPs/nodes
    //
    /**
     *  Report whether data IP's are configured for the test.
     */
    public boolean gotDataIPs() {
        if (dataIPs != null  &&  dataIPs.length > 1)
            return true;
        return false;
    }

    /**
     *  Get a random data node IP (devXXX-1, ...).
     */
    public String getRandomNodeIP() {
        if (dataIPs == null) {
            Log.ERROR("No dataIPs configured");
            return null;
        }
        try {
            return dataIPs[RandomUtil.randIndex(dataIPs.length)];
        } catch (Exception e) {
            Log.ERROR("Getting random index: " + e);
            return null;
        }
    }

    /**
     *  Get next data node IP (devXXX-1, ...).
     */
    private Object ipSync = new Object();
    private int nodeIndex = 0;
    public String getRoundRobinNodeIP() {
        if (dataIPs == null) {
            Log.WARN("No dataIPs configured");
            return null;
        }
        if (dataIPs.length == 1)
            return dataIPs[0];

        synchronized(ipSync) {
            String ret = dataIPs[nodeIndex];
            nodeIndex++;
            if (nodeIndex == dataIPs.length)
                nodeIndex = 0;
            return ret;
        }
    }

    /**
     *  Set multi-IP vs. VIP mode and return previous mode.
     */
    public int setDataMode(int node_mode) {
        int ret = this.node_mode;
        switch (node_mode) {
            case USE_VIP:
            case USE_ROUND_ROBIN_IP:
            case USE_RANDOM_IP:
                this.node_mode = node_mode;
                break;
            default:
                Log.ERROR("UNEXPECTED node_mode: " + node_mode + " NOT SET");
                break;
        }
        return ret;
    }

    public String getDataVIP() {
        switch (node_mode) {
            case USE_VIP:
                return dataVIP;
            case USE_ROUND_ROBIN_IP:
                return getRoundRobinNodeIP();
            case USE_RANDOM_IP:
                return getRandomNodeIP();
            default:
                return dataVIP;
        }
    }

    public int getDataPort() {
        return (dataPort);
    }

    private ClusterTestContext getContext(String vip) {
        if (vip == null) {
            if (node_mode == USE_VIP)
                return defaultCluster;
            ClusterTestContext localContext = 
                               new ClusterTestContext(defaultCluster);
            localContext.dataVIP = getDataVIP();
            return localContext;
        }
        ClusterTestContext localContext = 
                               new ClusterTestContext(defaultCluster);
        localContext.dataVIP = vip;
        return localContext;
    }

    ///////////////////////////////////////////////////////////////////////
    //  ops on remote client
    //
    //  remote client lookup interface
    //

    public int nextClient(int c) throws HoneycombTestException {
        synchronized(clients) {
            int nclients = clients.size();
            if (c < 0  ||  c >= nclients) {
                throw new HoneycombTestException("Client out of range: " + c +
                                            ", max = " + (nclients-1));
            }
            int next = c + 1;
            for (int i=0; i<nclients-1; i++) {
                // wrap if necessary
                if (next == nclients)
                    next = 0;

                if (clients.get(next) != null)
                    return next;
                next++;
            }
        }
        throw new HoneycombTestException("no nextClient");
    }

    /**
     *  Return count of remote clients.
     */
    public int clientCount() {
        if (clients == null)
            return 0;
        return clients.size();
    }

    /**
     *  Test if a client index (starting at 0) is valid).
     */
    public boolean validClientIndex(int c) {
        if (clients == null  ||  c < 0  ||  c > clients.size()-1)
            return false;
        return true;
    }

    /**
     *  Get a handle for an RMI client.
     */
    public ClntSrvClnt getClient(int c) throws HoneycombTestException {
        if (!validClientIndex(c)) {
            throw new HoneycombTestException("Client out of range: " + c);
        }
        synchronized(clients) {
            ClntSrvClnt clnt = (ClntSrvClnt) clients.get(c);
            if (clnt == null)
                throw new HoneycombTestException("Client is null: " + c);
            return clnt;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //  infrastructure test
    //
    /**
     *  Time the RMI overhead of a typical simple remote command on
     *  default cluster.
     */
    public CmdResult timeRMI(int client, boolean trySP)
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        long t1 = System.currentTimeMillis();
        CmdResult cr = clnt.timeRMI(defaultCluster, trySP);
        cr.time = System.currentTimeMillis() - t1;
        return cr;
    }

    /**
     *  Use default=0 client to time the RMI overhead of a typical 
     *  simple remote command.
     */
    public CmdResult timeRMI(boolean trySP) throws HoneycombTestException {
        return timeRMI(0, trySP);
    }

    ///////////////////////////////////////////////////////////////////////
    //  system ops on remote client
    //
    /**
     *  Test for file existence, retrying for given time.
     */
    public CmdResult doesFileExist(int client, String path, int waitSeconds)
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        return clnt.doesFileExist(path, waitSeconds);
    }

    /**
     *  Have the client ssh a command to a remote host (such as adminVIP).
     */
    public CmdResult sshCmd(int client, String login_host, String sshCmd)
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        return clnt.sshCmd(login_host, sshCmd);
    }

    ///////////////////////////////////////////////////////////////////////
    //  remote client ops using Honeycomb API and default cluster
    //

    /**
     *  Every now and then an RMI client may be restarted 
     *  in the middle of a long run, e.g. audit, so it can
     *  be useful to recreate it and retry the op (be sure
     *  to re-get the clnt 1st). 
     *  Return true if no retry warranted.
     */
    private boolean handleRMIReset(Exception e, int client, String host, 
                                                            String op) 
                                                 throws HoneycombTestException {
        String msg;
        if (e instanceof HoneycombTestException) {
            Throwable t = e.getCause();
            while (t instanceof HoneycombTestException)
                t = t.getCause();
            if (t == null)
                return false;
            msg = t.getMessage();
        } else {
            msg = e.getMessage();
        }
        if (msg == null) {
            return false;
        }
        boolean reset = false;
        if (msg.indexOf("no such object in table") != -1) {
            reset = true;
        } else if (msg.indexOf("Connection refused to host") != -1) {
            reset = true;
        } else if (msg.indexOf("Error unmarshaling return header") != -1) {
            Throwable t = e.getCause();
            if (t instanceof UnmarshalException)
                t = t.getCause();
            if (t instanceof EOFException)
                reset = true;
        }
        if (!reset) {
            return true;
        }

        //
        //  try to do the reset
        //
        Object[] host_sync = (Object[]) clients_sync.get(host);
        boolean wait = false;
        synchronized(host_sync) {
            Boolean b = (Boolean) host_sync[0];
            if (b.booleanValue() == true) {
                wait = true;
                Log.INFO(op + ": RMI client " + host + 
                              " gone - waiting for other thread to reset");
            } else
                host_sync[0] = new Boolean(true);
        }

        synchronized(host_sync[1]) {

            if (wait) {
                //
                //  there is a slight chance that this thread
                //  beat the lead thread to the host_sync[1] sync,
                //  so retest host_sync[0] to make sure that the
                //  lead is done, and wait if not. this should
                //  handle intermittent rmi resets, might
                //  have problems with continuous ones.
                //
                Boolean b = (Boolean) host_sync[0];
                if (b.booleanValue() == true) {
                    try {
                        host_sync[1].wait();
                    } catch (Exception e2) {
                        Log.ERROR(op + "RMI client " + host + " wait: " + e2);
                    }
                }
                Log.INFO(op + ": RMI client " + host + " other thread done");
                return false;
            }
            Log.INFO(op + ": RMI client " + host + 
                      " gone - sleeping/resetting/retrying");
            try { Thread.sleep(5000); } catch (Exception e2) {}
            ClntSrvClnt clnt = null;
            for (int i=0; i<5; i++) {
                try {
                    clnt = new ClntSrvClnt(host);
                    break;
                } catch (Exception e2) {
                    Log.INFO("reconnect to rmi clnt " + host + " failed: " +
                             e2.toString());
                    try { Thread.sleep(5000); } catch (Exception e3) {}
                }
            }
            if (clnt == null) {
                Log.ERROR("giving up on rmi clnt " + host);
                return true;
            }
            synchronized(clients) {
                clients.set(client, clnt);
            }
            Log.INFO("reconnected to rmi clnt " + host);
            host_sync[0] = new Boolean(false);
            try {
                host_sync[1].notifyAll();
            } catch (Exception e2) {
                Log.ERROR(op + "RMI client " + host + " notifyAll: " + e2);
            }
        }
        return false;
    }

    public CmdResult store(int client, long filesize, HashMap mdMap, 
                                                      String dataVIP) 
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);

        try {
            return clnt.store(getContext(dataVIP), filesize, false, mdMap, doHash);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "store"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.store(getContext(dataVIP), filesize, false, mdMap, doHash);
    }

    public CmdResult store(int client, long filesize, HashMap mdMap) 
                                                 throws HoneycombTestException {
        return store(client, filesize, mdMap, null);
    }


    public CmdResult store(int client, byte[] bytes, int repeats, 
                                                 HashMap mdMap, String dataVIP) 
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.store(getContext(dataVIP), bytes, repeats, false, 
                              mdMap, doHash);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "store"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.store(getContext(dataVIP), bytes, repeats, false, 
                          mdMap, doHash);
    }
    
	public CmdResult audit(int client, String oid, String dataVIP)
			throws HoneycombTestException {
		ClntSrvClnt clnt = getClient(client);
		try {
			return clnt.audit(getContext(dataVIP), oid);
		} catch (HoneycombTestException e) {
			//if (handleRMIReset(e, client, clnt.host, "audit"))
			throw e;
		}
	}

    public CmdResult store(int client, byte[] bytes, int repeats, HashMap mdMap)
                                                 throws HoneycombTestException {
        return store(client, bytes, repeats,mdMap, null);
    }

    public CmdResult addMetadata(int client, String oid, HashMap mdMap,
                                                         String dataVIP) 
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.addMetadata(getContext(dataVIP), oid, mdMap);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "addMetadata"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.addMetadata(getContext(dataVIP), oid, mdMap);
    }

    public CmdResult addMetadata(int client, String oid, HashMap mdMap) 
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.addMetadata(getContext(null), oid, mdMap);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "addMetadata"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.addMetadata(getContext(null), oid, mdMap);
    }
    public CmdResult retrieve(int client, String oid) 
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.retrieve(getContext(null), oid, doHash);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "retrieve"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.retrieve(getContext(null), oid, doHash);
    }

    public CmdResult retrieve(int client, String oid, String dataVIP) 
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.retrieve(getContext(dataVIP), oid, doHash);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "retrieve"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.retrieve(getContext(dataVIP), oid, doHash);
    }
    
    public CmdResult getMetadata(int client, String oid) 
    throws HoneycombTestException {
		ClntSrvClnt clnt = getClient(client);
		try {
		return clnt.getMetadata(getContext(null), oid );
		} catch (HoneycombTestException e) {
		if (handleRMIReset(e, client, clnt.host, "getmetadata"))
		throw e;
		}
		// retry
		clnt = getClient(client);
		return clnt.getMetadata(getContext(null), oid);
	}
    
    public CmdResult getMetadata(int client, String oid, String dataVIP) 
	    throws HoneycombTestException {
		ClntSrvClnt clnt = getClient(client);
		try {
		return clnt.getMetadata(getContext(dataVIP), oid);
		} catch (HoneycombTestException e) {
		if (handleRMIReset(e, client, clnt.host, "getmetadata"))
		throw e;
		}
		// retry
		clnt = getClient(client);
		return clnt.getMetadata(getContext(dataVIP), oid);
	}

    public CmdResult query(int client, String query)
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.query(getContext(null), query);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "query"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.query(getContext(null), query);
    }
    
    public CmdResult query_without_resulset(int client, String query)
			throws HoneycombTestException {
		ClntSrvClnt clnt = getClient(client);
		try {
			return clnt.query_without_resulset(getContext(null), query);
		} catch (HoneycombTestException e) {
			if (handleRMIReset(e, client, clnt.host, "query"))
				throw e;
		}
		// retry
		clnt = getClient(client);
		return clnt.query(getContext(null), query);
	}

    public CmdResult query(int client, String query, int maxResults)
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.query(getContext(null), query, maxResults);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "query"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.query(getContext(null), query, maxResults);
    }

    public CmdResult delete(int client, String oid)
                                                 throws HoneycombTestException {
        ClntSrvClnt clnt = getClient(client);
        try {
            return clnt.delete(getContext(null), oid);
        } catch (HoneycombTestException e) {
            if (handleRMIReset(e, client, clnt.host, "delete"))
                throw e;
        }
        // retry
        clnt = getClient(client);
        return clnt.delete(getContext(null), oid);
    }

    ///////////////////////////////////////////////////////////////////////
    //  remote SP ops
    //
    private void testSP() throws HoneycombTestException {
        if (sp == null) {
            throw new HoneycombTestException("no SP server");
        }
    }
    public CmdResult rebootNode(int node) throws HoneycombTestException {
        testSP();
        return sp.rebootNode(node, true);
    }
    /**
     * Gets the info for the oid.  If thisOnly is true, then we don't derefernce
     * MD oids to get their data oid.
     */
    public HOidInfo getOidInfo(String oid, boolean thisOnly)
                                                 throws HoneycombTestException {
        testSP();
        return sp.getOidInfo(oid, thisOnly);
    }
    public void deleteFragments(List l) throws HoneycombTestException {
        testSP();
        sp.deleteFragments(l);
    }
    public void restoreFragments(List l) throws HoneycombTestException {
        testSP();
        sp.restoreFragments(l);
    }

    /////////////////////////////////////////////////////////////////////
    //  Convenience functions that work with RMI data structures
    //
    /**
     * Check on the reference counts for a data object.
     */
    public boolean verifyRefCounts(HOidInfo oldinfo, HOidInfo newinfo,
        int delta) throws HoneycombTestException {
        // traverse through the oid structures and verify that refcount
        // has the expected delta.  This code is based on
        // DeleteSuite.onDiskFragsLookDeleted()
        // XXX assumes iterators for old and new will return same length lists
        // XXX handle null case -> verify all are 1
        // XXX handle missing frags, recovered frags, simultaneous tests
        ListIterator chunkIterOld = oldinfo.chunks.listIterator();
        ListIterator chunkIterNew = newinfo.chunks.listIterator();
        while (chunkIterOld.hasNext()) {
            HChunkInfo chunkOld = (HChunkInfo)chunkIterOld.next();
            HChunkInfo chunkNew = (HChunkInfo)chunkIterNew.next();
            ListIterator fragIterOld = chunkOld.getFragments().listIterator();
            ListIterator fragIterNew = chunkNew.getFragments().listIterator();
            while (fragIterOld.hasNext()) {
                HFragmentInfo fragOld = (HFragmentInfo)fragIterOld.next();
                HFragmentInfo fragNew = (HFragmentInfo)fragIterNew.next();
                ArrayList disksOld = fragOld.getDisks();
                ArrayList disksNew = fragNew.getDisks();
                if (disksOld == null || disksNew == null) {
                    Log.INFO("disks is null for frag " + fragOld.getFragNum() +
                        "; skipping");
                    continue;
                }
                ListIterator diskIterOld = fragOld.getDisks().listIterator();
                ListIterator diskIterNew = fragNew.getDisks().listIterator();
                while (diskIterOld.hasNext()) {
                    HFragmentInstance fragInstOld =
                        (HFragmentInstance) diskIterOld.next();
                    HFragmentInstance fragInstNew =
                        (HFragmentInstance) diskIterNew.next();
                    String sizePathOld = fragInstOld +
                        chunkOld.getHashPath() + fragOld.getFragNum();
                    String sizePathNew = fragInstNew +
                        chunkNew.getHashPath() + fragNew.getFragNum();

                    if (!compareRefCounts(sizePathNew, fragInstOld.refCounts,
                        fragInstNew.refCounts, delta)) {
                        return (false);
                    }
                }
            }
        }

        return (true);
    }

    public boolean compareRefCounts(String frg, String oldRefCount,
        String newRefCount, int delta) {
        RefCount oldRef = new RefCount(oldRefCount);
        RefCount newRef = new RefCount(newRefCount);
        if ((oldRef.cur + delta) != newRef.cur) {
            Log.ERROR("unexpected current refcnt for frg " + frg + ", delta " +
                delta + "; old refcnt " + oldRef + "; new refcnt " + newRef +
                "; expected cur refcnt of " + (oldRef.cur + delta));
            return (false);
        }

        if (delta > 0 && ((oldRef.max + delta) != newRef.max)) {
            Log.ERROR("unexpected max refcnt for frg " + frg + ", delta " +
                delta + "; old refcnt " + oldRef + "; new refcnt " + newRef +
                "; expected max refcnt of " + (oldRef.max + delta));
            return (false);
        }

        if (delta <= 0 && (oldRef.max != newRef.max)) {
            Log.ERROR("unexpected max refcnt for frg " + frg + ", delta " +
                delta + "; old refcnt " + oldRef + "; new refcnt " + newRef +
                "; expected refcnt of " + oldRef.max);
            return (false);
        }
        
        Log.DEBUG("refcounts as expected for frg " + frg + ", delta " +
            delta + "; old refcnt " + oldRef + "; new refcnt " + newRef);
        return (true);
    }

    public class RefCount {
        int cur = -2;
        int max = -2;
        RefCount(String refCount) {
            // string is like [3/2]
            if (refCount == null || refCount.length() < (1 + 3 + 1)) {
                return;
            }

            String r = refCount.substring(1, refCount.length() - 1);
            String[] refs = r.split("/");
            if (refs.length != 2) {
                Log.WARN("failed to parse ref count: " + r);
                return;
            }

            cur = Integer.parseInt(refs[0]);
            max = Integer.parseInt(refs[1]);
        }

        public String toString() {
            return ("cur=" + cur + "; max=" + max);
        }
    }
}
