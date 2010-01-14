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



package com.sun.honeycomb.util;

import java.util.logging.*;
import java.io.*;

import com.sun.honeycomb.common.StringUtil;

import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;

/**
 * Static abstract class for access to non-cluster-nodes in a cluster:
 * the switch, the SP, etc.
 */
public abstract class AncillaryServer {

    private static final Logger logger =
        Logger.getLogger(AncillaryServer.class.getName());

    // The ssh command
    private static final String SSH_PREFIX =
        "/usr/bin/ssh -o StrictHostKeyChecking=no";

    // The ssh timeout so we don't block forever if a node is flaky
    private static final long SSH_TIMEOUT = 300000; // ms

    // The setuid wrapper
    private static final String SETUID_CMD = "/opt/honeycomb/sbin/setuid ";

    private static boolean fakeIt = false;

    // These methods describe how Honeycomb can talk to the
    // appropriate server.
    abstract String getAddress();
    abstract int getSshPort();
    abstract int getHttpPort();
    abstract String getSshUser();

    /////////////////////////////////////////////////////////////////
    // Ssh is used for commands that may modify the state of the
    // server. These are to run commands on the remote server; no
    // output is expected.

    protected boolean runSshCommand(String arg) {
        String cmd = getSshCmdline(arg);

        if (fakeIt) {
            System.out.println("Run command " + StringUtil.image(cmd));
            return true;
        }

        try {
            int rc;
            if ((rc = Exec.exec(cmd, SSH_TIMEOUT, logger)) == 0)
                return true;
            else
                logger.warning("Command \"" + cmd + "\" returned " + rc);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Command \"" + cmd + "\" failed", e);
        }
        return false;
    }

    protected boolean runSshCommandAsRoot(String arg) {
        return runSshCommand(SETUID_CMD + arg);
    }

    static void setFakeit(boolean v) {
        fakeIt = v;
    }

    /////////////////////////////////////////////////////////////////
    // The following return the string output from the remote command.

    protected String getSshFirstLine(String arg) {
        return getSshContent(arg, true);
    }

    protected String getSshContent(String arg) {
        return getSshContent(arg, false);
    }

    private String getSshContent(String arg, boolean firstLineOnly) {
        String cmd = getSshCmdline(arg);

        if (fakeIt) {
            System.out.println("Read " + StringUtil.image(cmd));
            return "Fake line.";
        }

        try {
            BufferedReader f = Exec.execRead(cmd, logger);
            return getContent(f, firstLineOnly);
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't read \"" + cmd + "\"", e);
        }
        return null;
    }

    private String getSshCmdline(String rcmd) {
        String cmd = SSH_PREFIX + " -p " + getSshPort();
        if (getSshUser() != null)
            cmd += " -l " + getSshUser();
        cmd += " " + getAddress() + " " + rcmd;
        return cmd;
    }

    /////////////////////////////////////////////////////////////////
    // HTTP

    protected String getHttpFirstLine(String uri) {
        return getHttpContent(uri, true);
    }

    protected String getHttpContent(String uri) {
        return getHttpContent(uri, false);
    }

    private String getHttpContent(String uri, boolean firstLineOnly) {
        String url = "http://" + getAddress() + uri;

        try {
            BufferedReader f = HttpClient.getHttp(url, logger);
            return getContent(f, firstLineOnly);
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't GET \"" + uri + "\"", e);
        }
        return null;
    }


    /////////////////////////////////////////////////////////////////
    // I/O utils

    /**
     * Read a BufferedReader and return the string read. The boolean
     * controls whether we should read till EOF or only the first line.
     */
    private String getContent(BufferedReader f, boolean firstLineOnly)
            throws IOException {
        if (f == null)
            return null;

        try {
            if (firstLineOnly)
                return f.readLine();

            StringBuffer retval = new StringBuffer();
            String line;
            while ((line = f.readLine()) != null)
                retval.append(line).append('\n');
            return retval.toString();
        }
        finally {
            f.close();
        }
    }

    /////////////////////////////////////////////////////////////////
    // Options for swadm/swcfg/spcfg
    //
    // N.B. All returned strings have a leading space.

    static String getAdminOptions(String adminVip) {
        return " -a " + adminVip;
    }

    static String getDataOptions(String dataVip) {
        return " -D " + dataVip;
    }

    static String getSpOptions(String spIp) {
        return " -S " + spIp;
    }

    static String getSmtpOptions(String smtp) {
        return " -s " + smtp;
    }

    static String getSmtpPortOptions(String smtpPort) {
        return " -T " + smtpPort;
    }

    static String getNtpOptions(String ntp) {
        return " -t " + ntp;
    }

    static String getGatewayOptions(String gateway) {
        return " -g " + gateway;
    }

    static String getSubnetOptions(String subnet) {
        return " -m " + subnet;
    }

    static String getLoggerOptions(String extlogger) {
        return " -l " + extlogger;
    }

    static String getRebootOptions() {
        return " -r -o";
    }

    static String getHostnameOptions(String hostname) {
        return " -h " + hostname;
    }

    // These options use a special value for nulls

    static String getDnsOptions(String dns) {
        return " -N " + dflValue(dns, "n");
    }

    static String getDomainOptions(String domain) {
        return " -Q " + dflValue(domain, "sun.com");
    }

    static String getDnsPrimaryServerOptions(String server) {
        return " -P " + dflValue(server, "0.0.0.0");
    }

    static String getDnsSecondaryServerOptions(String server) {
        return " -p " + dflValue(server, "0.0.0.0");
    }

    static String getAuthClientOptions(String clients) {
        return " -C " + dflValue(clients, "all");
    }

    static String getDnsSearchOptions(String dnss) {
        return " -j " + dflValue(dnss, "sun.com");
    }

    private static String dflValue(String value, String dfl) {
        if (value == null || value.equals(""))
            return dfl;
        return value;
    }

    // End swadm/swcfg options
    /////////////////////////////////////////////////////////////////

    static NodeMgrService.Proxy getProxy() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);

        if (!(obj instanceof NodeMgrService.Proxy)) {
            logger.severe("Can't get NodeMgr proxy!");
            return null;
        }

        return (NodeMgrService.Proxy) obj;
    }
}
