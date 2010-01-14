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



package com.sun.honeycomb.webdav;

import com.sun.honeycomb.common.Getopt;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.HttpPassword;

import com.sun.honeycomb.fscache.HCFile;

import com.sun.honeycomb.connectors.HCGlue;
import com.sun.honeycomb.connectors.HCInterface;
import com.sun.honeycomb.connectors.Simulator;
import com.sun.honeycomb.connectors.DerbySimulator;
import com.sun.honeycomb.connectors.DeleteListener;
import com.sun.honeycomb.common.HttpPassword;
import com.sun.honeycomb.auth.HCAuthenticator;
import com.sun.honeycomb.auth.HCUserRealm;


import java.io.File;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Map;
import java.util.Iterator;
import java.util.Properties;
import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.BasicAuthenticator;
import org.mortbay.http.DigestAuthenticator;
import org.mortbay.http.HashUserRealm;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.SocketListener;
import org.mortbay.http.handler.SecurityHandler;

import org.mortbay.util.InetAddrPort;
import org.mortbay.util.Log;
import org.mortbay.util.MultiException;

/**
 * Class that implements the WebDAV filesystem. This class should not
 * require that it be running on a cluster; that sort of code should
 * go in {@link HCDAV}.
 */
public class DAVServer implements DeleteListener {

    /*
     * Static methods
     */

    private static final Logger logger =
        Logger.getLogger(DAVServer.class.getName());
    
    private static String PROPNAME_PREFIX = "honeycomb.webdav.auth";
    private static String PROPNAME_AUTH = "authenticate";
    private static String PROPNAME_REALM = "realm";

    // These are for testing only
    private static String PROPNAME_USER = "user";

    private static boolean DEFAULT_AUTH = true; // Authenticate by default

    private static boolean initialized = false;

    protected static Properties config = null;
    protected static HCInterface hc = null;

    /*
     * Class implementation
     */

    private HttpContext context = null;

    private static synchronized void init(HCInterface conn) {
        hc = conn;
    }

    private static synchronized void initCache() {
        try {
            HCFile.init(config, hc);
            logger.info("FS cache initialized.");
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Cannot initialize FS cache", e);
        }
    }

    private static synchronized void resetCache() {
        try {
            HCFile.reset();
            logger.info("FS cache destroyed.");
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Cannot reset FS cache", e);
        }
    }

    public static synchronized void saveProperty(String name, String value) {
        hc.saveClusterProperty(name, value);
    }

    /** Used by the emulator */
    public DAVServer() {
        this(null);
    }

    /**
     * The constructor is provided the HCInterface to use. This means
     * it behaves identically on a cluster and on the various emulators.
     */
    public DAVServer(HCInterface hc) {
        init(hc);

        if (hc == null)
            // It's the emulator
            return;

        context = new HttpContext();
        context.setContextPath(ProtocolConstants.WEBDAV_PATH);

        // Authentication is always required (but tested for PROPPATCH only)

        SecurityConstraint sc = new SecurityConstraint();
        sc.setAuthenticate(true);
        sc.addRole(SecurityConstraint.ANY_ROLE);
        context.addSecurityConstraint("/*", sc);

        // Construct realm and add to context
        HCUserRealm.init(config);
        HCUserRealm realm = HCUserRealm.getInstance();

        // The realm needs to register for property change events to
        // get the new password when changed by the user
        hc.addPropertyChangeListener(realm);

        context.setRealm(realm);

        context.setAuthenticator(new HCAuthenticator());
        context.addHandler(new SecurityHandler());

        // SecurityHandler (if any) *must* be added before MainHandler
        context.addHandler(new MainHandler());

        // Register for delete notifications from HC
        hc.register(this, context);

        logger.info("The webdav server will be started.");
    }

    public HttpContext getContext() {
        return context;
    }

    public void syncRun() {
        initCache();
    }

    public void run() {
        logger.info("run() called.");
    }

    public void shutdown() {
        // Re-initialize in case the server is re-started
        resetCache();
    }

    public void deleted(NewObjectIdentifier oid) {
        if (oid == null)
            return;

        if (logger.isLoggable(Level.INFO))
            logger.info("Handling deletion of " + oid.toExternalHexString());

        if (HCFile.fileCache == null) {
            logger.warning("FS cache uninitialized; ignoring delete " + oid);
            return;
        }

        try {
            HCFile.fileCache.remove(null, oid.getDataBytes());
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't delete " + oid, e);
        }
    }

    ////////////////////////////////////////////////////////////////////////

    /** Used to initialise the real connection to HC */
    protected static synchronized HCInterface initHC() {

        logger.info("Initializing the connection to HC...");

        // Initialize the connection to HC
        hc = HCGlue.getInstance();
        try {
            hc.init();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Get the cluster configuration
        config = hc.getClusterProperties();

        return hc;
    }

    /**
     * Initialize a connection to the Derby implementation of the
     * Honeycomb API (Coordinator etc.)
     */
    protected static synchronized HCInterface initSim(File dir)
            throws IOException {
        // Simulator: initialize hc to the framework and seed config with
        // test values

        String simUser = "root";
        String simPasswd = "honeycomb";
        String simRealm = "HC WebDAV";

        config = new Properties();

        // The cache implementation to test
        config.setProperty("com.sun.honeycomb.fscache", "LinkedHashMapCache");

        config.setProperty("honeycomb.fscache.coherency.time", "300");
        config.setProperty("honeycomb.fscache.refresh_on_failure", "true");

        config.setProperty("honeycomb.fscache.size.max", "10000");
        config.setProperty("honeycomb.fscache.size.lo", "8000");

        config.setProperty("honeycomb.webdav.confdir", ".");

        // Set the password into the props
        byte[] hash = HttpPassword.makeHash(simRealm, simUser, simPasswd);
        config.setProperty("honeycomb.webdav.auth.hash",
                           simRealm + ":" + simUser + ":" +
                           ByteArrays.toHexString(hash));

        config.setProperty("honeycomb.config.dir", dir.getAbsolutePath());

        hc = new DerbySimulator(dir);
        hc.init();

        return hc;
    }

    ////////////////////////////////////////////////////////////////////////

    /** Setup and run the standalone WebDAV server with the Derby backend */
    public static void main(String[] args) {
        int port = 8088;
        String dirName = null;
        Level logLevel = Level.FINE;

        Getopt opts = new Getopt(args, "p:l:");
        while (opts.hasMore()) {
	    Getopt.Option option = opts.next();
            if (option.noSwitch()) { // what a stupid name
                if (dirName != null) {
                    System.err.println("Only one argument, please.");
                    System.exit(1);
                }
                dirName = option.value();
            }
            else if (option.name() == 'l')
                try {
                    logLevel = Level.parse(option.value().toUpperCase());
                }
                catch (IllegalArgumentException e) {
                    System.err.println("Unknown level: " + option.value());
                    System.exit(1);
                }
            else if (option.name() == 'p')
                try {
                    port = Integer.parseInt(option.value());
                }
                catch (NumberFormatException e) {
                    System.err.println("Not a number: " + option.value());
                    System.exit(1);
                }
        }

        if (dirName == null) {
            System.err.println("Usage: <java ...> [-p port] [-l logLevel] <database-dir>");
            System.exit(1);
        }

        Simulator.setupLogging(logLevel);

        File dir = new File(dirName);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("No dir \"" + dir.getAbsolutePath() + "\"");
            System.exit(2);
        }

        System.setProperty("emulator.root", dir.getAbsolutePath());
        System.setProperty("uid.lib.path", "emulator");
        System.setProperty("honeycomb.config.dir",
                           dir.getAbsolutePath() + "/config");

        try {
            HttpServer server = new HttpServer();
            SocketListener listener = new SocketListener();
            listener.setPort(port);
            server.addListener(listener);

            DAVServer davServer = new DAVServer(initSim(dir));

            server.addContext(davServer.getContext());
            server.start();

            BufferedReader console =
                new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = console.readLine()) != null)
                if (line.equals("quit"))
                    break;
            server.stop();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (hc != null)
                    ((Simulator)hc).close();
                System.exit(0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Config property helpers

    public static String getStringProperty(String pname, String defaultVal) {
        try {
            String s = config.getProperty(PROPNAME_PREFIX + pname);
            if (s != null) return s;
        }
        catch (Exception e) {}

        return defaultVal;
    }

    public static int getIntProperty(String pname, int defaultVal) {
        String s = null;
        try {
            if ((s = config.getProperty(PROPNAME_PREFIX + pname)) == null)
                return defaultVal;

            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            logger.warning("Couldn't parse " + pname + " = \"" + s +
                           "\"; using default " + defaultVal);

        }

        return defaultVal;
    }

    public static boolean getBooleanProperty(String pname, boolean defVal) {
        String s = null;

        if ((s = config.getProperty(PROPNAME_PREFIX + pname)) == null)
            return defVal;

        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes"))
            return true;
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no"))
            return false;

        return defVal;

    }

}
