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



package com.sun.honeycomb.admingui.server;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.security.KeyStore;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.mortbay.http.HttpServer;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.SunJsseListener;
import org.mortbay.util.InetAddrPort;
import org.mortbay.util.MultiException;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.multicell.lib.MultiCellLib;

public class AdminGUIMain
    implements AdminGUIService {

    private static final Logger LOG =
        Logger.getLogger(AdminGUIService.class.getName());

    private static final String ADMIN_VIP_PROPERTY =
                                    "honeycomb.cell.vip_internal_admin";
    private static final String DEFAULT_ADMIN_VIP = "localhost";
    private static final int DEFAULT_PORT = 8090;
    private static final String PROP_WEB_PATH = "admingui.web.path";
    private static final String PROP_WEB_PORT = "admingui.web.port";
    private static final String DEFAULT_RESOURCE_BASE = "/opt/honeycomb/web";

    private HttpServer server;
    private enum EmMode { NO_EM, GUI_EM, MGMT_EM };
    EmMode emulMode = EmMode.NO_EM;
    static final String MDCFG_FILE_EMULATOR = "/var/tmp/admingui.emulator.xml";

    private static String keystore = "/opt/honeycomb/etc/AdminGuiKeystore";

    void checkEmulMode() {
        File f = new File(MDCFG_FILE_EMULATOR);
        /*
         *  if the file doesn't exist we assume we're on a real cluster
         *  and we'll read the admin ip from ClusterProperties
         *
         *  otherwise we'll get the adm ip from cmd line and if
         *  - file is empty -> we use the new (adm/mgmt) API, which allows us
         *    to run against the new emulator
         *  - file is not empty -> we use the existing emulator and we read
         *    schema from this file
         */
        if (f.exists()) {
            if (f.length() == 0) {
                LOG.info("emulmode on, using MGMT emulator.");
                emulMode = EmMode.MGMT_EM;
            } else {
                LOG.info("emulmode on, using GUI emulator.");
                emulMode = EmMode.GUI_EM;
            }
        } else {
            LOG.info("not emulated.");
        }
    }
    public AdminGUIMain() {
        checkEmulMode();
        server = null;
    }

    /*
     * ManagedService methods
     */

    public void syncRun() {
        if (server == null) {
            server = new HttpServer();
        }
    }

    public void run() {
        LOG.info("Starting the Admin GUI HTTP server");
        try {
            server.setStatsOn(true);
            registerHandlers();

           String adminVIP = DEFAULT_ADMIN_VIP;
            // Get ip from command line, unless running in emulator mode
            if (emulMode.equals(EmMode.NO_EM)) {
                // get private VIP
                adminVIP = ClusterProperties.getInstance().
                             getProperty(ADMIN_VIP_PROPERTY, DEFAULT_ADMIN_VIP);

                LOG.info("current private adminVIP is " + adminVIP);
            } else {
                // don't need full path when running emulator
                keystore = keystore.substring(keystore.lastIndexOf("/") + 1);
                System.out.println("keystore location: " + keystore);
            }

            if (adminVIP.equals(DEFAULT_ADMIN_VIP)) {
                LOG.warning("Couldn't find the admin VIP. Using the default one"
                            + "[" + DEFAULT_ADMIN_VIP + "]");
            }
            int port = DEFAULT_PORT;
            String strPort = "";
            try {
                strPort = System.getProperty(PROP_WEB_PORT);
                if (strPort == null)
                    LOG.warning("No port specified for Admin GUI server. " +
                            "Will use default - " + port);
                else
                    port = Integer.parseInt(strPort);
            } catch (NumberFormatException e) {
                LOG.log(Level.SEVERE,
                        "Invalid port number specified on command line: " +
                        strPort, e);
            }
            LOG.info("gui server using private adminVIP " + adminVIP +
               ", port " + port);

            /*
            server.addListener(emulMode.equals(EmMode.NO_EM) ?
                new InetAddrPort(adminVIP, port) :
                // adminvip 'localhost' doesn't seem to work
                new InetAddrPort(port));
            */

            // add secure listener
            SunJsseListener listener = emulMode.equals(EmMode.NO_EM) ?
                new SunJsseListener(new InetAddrPort(adminVIP, port)) :
                new SunJsseListener(new InetAddrPort(port));
            String passwd = "changeit";
            try {
                File f = new File(keystore);
                if (f.exists()) {
                    System.out.print("deleting existing keystore (" + keystore
                            + ")...");
                    f.delete();
                    System.out.println("done");
                   
                }
                // generate the adminVIP to be included in certificate
                if (adminVIP.equals(DEFAULT_ADMIN_VIP)) { // emulator
                    adminVIP = Inet4Address.getLocalHost().getHostAddress();
                } else {
                    try {
                        adminVIP = MultiCellLib.getInstance().getAdminVIP();
                    } catch (Exception e) {
                        LOG.warning("Could not get public admin VIP " +
                            "(certificate will include private IP): " + e);
                    }
                }
                LOG.info("adminVIP for certificate is " + adminVIP +
                    ". Initializing keystore");
                sun.security.tools.KeyTool.main(
                    new String[] {"-genkey", "-alias", "STK5800Admin", 
                    "-dname","CN=" + adminVIP + ", O=Sun Microsystems, C=US",
                    "-keystore", keystore, "-storepass", passwd, "-keypass",
                    "changeit", "-keyalg","RSA","-validity","3650"});
            } catch (Exception e) {
                System.out.println("keystore error: " + e);
                LOG.severe("keystore error: " + e.getMessage());
            }
           
            // next section for debugging only
            try  {
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(new java.io.FileInputStream(keystore), passwd.toCharArray());
                // System.out.println(ks.getCertificate("STK5800Admin"));
            } catch (Exception e) {
                LOG.severe("keystore error: " + e.getMessage());
                System.out.println(e);
            }
           
            listener.setKeystore(keystore);
            listener.setPassword(passwd); // for keystore
            listener.setKeyPassword(passwd);
            server.addListener(listener);

            // Start the http server
            server.start();
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to start the Admin HTTP server ["+
                    e.getMessage()+"]",
                    e);
            throw new RuntimeException(e);
        } catch (MultiException e) {
            LOG.log(Level.SEVERE,
                    "Failed to start the Admin HTTP server ["+
                    e.getMessage()+"]",
                    e);
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        LOG.info("The Admin GUI server is shutting down");

        try {
            server.stop(true);
            server.join();
        } catch (InterruptedException e) {
            LOG.warning("Error while stopping the admin HTTP server ["+
                        e.getMessage()+"]");
        }
    }

    public ManagedService.ProxyObject getProxy() {
        return (new ManagedService.ProxyObject());
    }

    public static void main(String[] arg) {
        AdminGUIMain server = new AdminGUIMain();
        server.syncRun();
        server.run();
        LOG.info("The admin gui server is running");
    }

    /*
     * Private methods
     */

    private void registerHandlers() {
        HttpContext context = new HttpContext(server, "/RPC2");

        MainHandler mh = new MainHandler(emulMode.equals(EmMode.GUI_EM));

        context.addHandler(mh);
        server.addContext(context);
        context = new HttpContext(server, "/");
        String webPath = System.getProperty(PROP_WEB_PATH);
        if (webPath == null) {
            context.setResourceBase(DEFAULT_RESOURCE_BASE);
        } else {
            LOG.info("Not using the default static path [" +
                     webPath + "]");
            context.setResourceBase(webPath);
        }
        GUIResourceHandler resourceHandler = new GUIResourceHandler();
        resourceHandler.setDirAllowed(true);
        resourceHandler.setAcceptRanges(false);
        context.addHandler(resourceHandler);
        server.addContext(context);
    }

    static void logRequest(HttpRequest request) {
        int msglen_log_limit = 500;
        char[] buf = new char[msglen_log_limit];
        try {
            java.io.InputStream is = request.getInputStream();
            if (is.markSupported()) {
                is.mark(msglen_log_limit);
                java.io.InputStreamReader isr =
                    new java.io.InputStreamReader(is);
                int n = isr.read(buf, 0, msglen_log_limit - 1);
                if (n == -1)
                    n = msglen_log_limit - 1;
                String s = new String(buf, 0, n);
                s = s.replaceFirst("<methodCall>","gui=>").
                    replaceFirst("</methodName>","(").
                    replaceFirst("</methodCall>",")").
                    replaceAll("</param><param>",",").
                    replaceAll("<array>","[").replaceAll("</array>","]").
                    replaceAll("</value><value>",",").
                    replaceAll("<struct>","{").replaceAll("</struct>","}").
                    replaceAll("</member><member>",",").
                    replaceAll("<[.[^>]]*>", "");
                LOG.info(s);
                is.reset();
             } else
                LOG.warning("cannot log xmprpc call: mark not supported");
        } catch (Exception io) {
                LOG.warning("cannot log xmlrpc call: " + io);
        }
    }
}
