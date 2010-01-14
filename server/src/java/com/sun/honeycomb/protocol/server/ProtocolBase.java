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




package com.sun.honeycomb.protocol.server;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.config.ClusterProperties;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpListener;
import org.mortbay.http.HttpServer;
import org.mortbay.http.SocketListener;
import org.mortbay.util.InetAddrPort;

import org.mortbay.util.Log;
import org.mortbay.util.LogSink;
import org.mortbay.util.NullLogSink;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.Comparator;
import java.lang.ClassCastException;


public abstract class ProtocolBase implements ServiceRegistration {

    static boolean reportStackTrace = false;

    static protected final String PROP_CELLID = "honeycomb.silo.cellid";

    private static int defaultPort;
    protected MultiPortSocketListener httpListener;
    protected HttpServer server;
    protected Logger logger;
    protected boolean started = false;
    private List contexts;
    private Set defaultListenerPorts;

    protected ProtocolProxy proxy = null;
    protected ClusterProperties config = null;

    // Used by NewObjectIdentifier
    public static byte CELL_ID = 0;

    public ProtocolBase() throws IOException {
        logger = Logger.getLogger(getClass().getName());

        config = ClusterProperties.getInstance();        
        defaultPort = 
            config.getPropertyAsInt(ProtocolConstants.API_SERVER_PORT_PROPERTY,
                                    ProtocolConstants.DEFAULT_PORT);
        try {
            CELL_ID = Byte.parseByte(config.getProperty(PROP_CELLID));
        } catch (Exception ignore) {
            // If the property is not defined this is defaulted to CELL_ID = 0
            CELL_ID = 0;
        }

        contexts = new ArrayList();
        defaultListenerPorts = new TreeSet(new TreeSetComparator());
        defaultListenerPorts.add(new Integer(defaultPort));
    }

    static public int getDefaultPort() {
        return defaultPort;
    }

    private void addContext(ContextRegistration ctx) throws IOException{
        int port = (ctx.getPort() ==  ContextRegistration.DEFAULT_PORT) ?
            defaultPort : ctx.getPort();

        logger.log(Level.INFO, "add context for " + ctx.getNamespace() +
                      ", new listener = " + ctx.isNewListener() +
                      ", port = " + port);
        if (ctx.isNewListener()) {
            SocketListener newListener = (SocketListener)
                server.addListener(new InetAddrPort(port));
            newListener.setMaxIdleTimeMs(10000);
        } else {
            defaultListenerPorts.add(new Integer(port));
        }
        server.addContext(ctx.getContext());
    }


    public void init(String dataVIP) throws IOException {
	ClusterProperties properties = ClusterProperties.getInstance();
	int MAX_IDLE_TIME = 1000 *
            properties.getPropertyAsInt(ConfigPropertyNames.PROP_PROTOCOL_MAXIDLETIME,
                                        30);
	int MIN_THREADS =
            properties.getPropertyAsInt(ConfigPropertyNames.PROP_PROTOCOL_MINTHREADS,
                                        16);
	int MAX_THREADS =
            properties.getPropertyAsInt(ConfigPropertyNames.PROP_PROTOCOL_MAXTHREADS,
                                        32);
	reportStackTrace  = 
            properties.getPropertyAsBoolean(
                                     ProtocolConstants.SENDSTACKTRACE_PROPERTY,
                                     false);


        if (logger.isLoggable(Level.INFO)) {
            String msg = "Protocol server at ";
            if (dataVIP != null)
                msg += dataVIP;
            msg += ":" + defaultPort;

            msg += " maxIdleTime: " + MAX_IDLE_TIME;

            msg += "ms; numThreads: (" + MIN_THREADS + "," + MAX_THREADS + ")";

            logger.info(msg);
        }

	try {
	    LogSink sink;
	    if (properties.getPropertyAsBoolean(
                                       ProtocolConstants.JETTYLOG_PROPERTY)) {
		sink = new LogSink(){
			public void log(String formattedLog){
			    logger.log(Level.INFO, "Jetty log: " +
                                       formattedLog);
			}
			public void log(String tag, java.lang.Object msg,
                                        org.mortbay.util.Frame frame,
                                        long time){
			    logger.log(Level.INFO, "Jetty log: " + tag +
                                       " " + msg + " " + frame + " " + time);
			}
			public String getOptions(){return null;}
			public void setOptions(String s){};
			public void start() throws java.lang.Exception {};
			public void stop()
                            throws java.lang.InterruptedException{};
			public boolean isStarted(){return true;};
		    };
	    } else{
		sink = new NullLogSink();
	    }
	    sink.start();
	    Log.instance().add(sink);
	} catch (Exception e){
	    logger.log(Level.WARNING,
		       "Failed to start the jetty logging ["+
		       e.getMessage()+"]",
		       e);
	}

        // Create HttpServer
        server = new HttpServer();
        server.setStatsOn(true);
        server.setRequestsPerGC(16);

        // Add the default contexts/handlers
        createDefaultContexts();

        // Creates default MultiPortListener
        httpListener = new MultiPortSocketListener(dataVIP);
        httpListener.setMaxIdleTimeMs(MAX_IDLE_TIME);
        httpListener.setMinThreads(MIN_THREADS);
        httpListener.setMaxThreads(MAX_THREADS);
        httpListener.setPoolName("ApiThread");

        synchronized (contexts){
            Iterator it = contexts.iterator();
            while (it.hasNext()) {
                ContextRegistration ctx = (ContextRegistration) it.next();
                addContext(ctx);
            }
        }
        
        httpListener.initialize(defaultListenerPorts);
        server.addListener(httpListener);
        started = true;
    }

    protected void createDefaultContexts() {

	HttpContext context = null;
        TreeSet evts = null;
        context = new HttpContext(server, ProtocolConstants.STORE_PATH);
        context.addHandler(new StoreHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.STORE_PATH,
                                                context, false));
        
        context = new HttpContext(server,
                                  ProtocolConstants.STORE_METADATA_PATH);
        context.addHandler(new StoreMetadataHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                         ProtocolConstants.STORE_METADATA_PATH,
                                         context, false));

        context = new HttpContext(server, ProtocolConstants.STORE_BOTH_PATH);
        context.addHandler(new StoreBothHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                             ProtocolConstants.STORE_BOTH_PATH,
                                             context, false));

        context = new HttpContext(server, ProtocolConstants.RETRIEVE_PATH);
        context.addHandler(new RetrieveHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                                ProtocolConstants.RETRIEVE_PATH,
                                                context, false));

        context = new HttpContext(server,
                                  ProtocolConstants.RETRIEVE_METADATA_PATH);
        context.addHandler(new RetrieveMetadataHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                      ProtocolConstants.RETRIEVE_METADATA_PATH,
                                      context, false));

        context = new HttpContext(server, ProtocolConstants.DELETE_PATH);
        context.addHandler(new DeleteHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        evts = new TreeSet();
        evts.add(new  Integer(EventRegistrant.API_DELETE));
        registerContext(new ContextRegistration(ProtocolConstants.DELETE_PATH,
                                                Coordinator.getInstance(),
                                                evts,
                                                context, false));

        context = new HttpContext(server, ProtocolConstants.QUERY_PATH);
        context.addHandler(new QueryHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.QUERY_PATH,
                                                context, false));

        context = new HttpContext(server, ProtocolConstants.QUERY_PLUS_PATH);
        context.addHandler(new QueryPlusHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                            ProtocolConstants.QUERY_PLUS_PATH,
                                            context, false));

        context = new HttpContext(server, ProtocolConstants.GET_VALUES_PATH);
        context.addHandler(new GetValuesHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                            ProtocolConstants.GET_VALUES_PATH,
                                            context, false));

        context = new HttpContext(server,
                                  ProtocolConstants.GET_CACHE_CONFIG_PATH);
        context.addHandler(new GetConfigurationHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                        ProtocolConstants.GET_CACHE_CONFIG_PATH,
                                        context, false));

        context = new HttpContext(server,
                                  ProtocolConstants.CHECK_INDEXED_PATH);
        context.addHandler(new CheckIndexedHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(
                                        ProtocolConstants.CHECK_INDEXED_PATH,
                                        context, false));

	if (ClusterProperties.getInstance().getPropertyAsBoolean(
                                             ProtocolConstants.EVAL_PROPERTY,
                                             false)) {
	    context = new HttpContext(server, ProtocolConstants.EVAL_PATH);
	    context.addHandler(new EvalHandler(this));
	    context.setRedirectNullPath(false);
	    context.setStatsOn(true);
	    registerContext(new ContextRegistration(ProtocolConstants.EVAL_PATH,
                                                    context, false));
	}

	 /********************************************************
	  *
	  * Bug 6554027 - hide retention features
	  *
	  *******************************************************/
	 /*
         // Compliance set retention time path
        context = new HttpContext(server,
                                  ProtocolConstants.SET_RETENTION_PATH);
        context.addHandler(new ComplianceHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.
                                                SET_RETENTION_PATH,
                                                context, false));

         // Compliance set relative retention time path
        context = new HttpContext(server,
                                  ProtocolConstants.
                                  SET_RETENTION_RELATIVE_PATH);
        context.addHandler(new ComplianceHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.
                                                SET_RETENTION_RELATIVE_PATH,
                                                context, false));

        // Compliance get retention time path
        context = new HttpContext(server,
                                  ProtocolConstants.GET_RETENTION_PATH);
        context.addHandler(new ComplianceHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.
                                                GET_RETENTION_PATH,
                                                context, false));

         // Compliance add legal hold tag path
        context = new HttpContext(server, ProtocolConstants.ADD_HOLD_PATH);
        context.addHandler(new ComplianceHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.
                                                ADD_HOLD_PATH,
                                                context, false));

        // Compliance remove legal hold tag path
        context = new HttpContext(server, ProtocolConstants.REMOVE_HOLD_PATH);
        context.addHandler(new ComplianceHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.
                                                REMOVE_HOLD_PATH,
                                                context, false));
	 */

        // Get cell time for compliance use
        context = new HttpContext(server, ProtocolConstants.GET_DATE_PATH);
        context.addHandler(new DateHandler(this));
        context.setRedirectNullPath(false);
        context.setStatsOn(true);
        registerContext(new ContextRegistration(ProtocolConstants.
                                                GET_DATE_PATH,
                                                context, false));
    }

    
    public class TreeSetComparator implements Comparator {

        public TreeSetComparator() {
        }

        public int compare(Object obj1, Object obj2)
        throws ClassCastException {
            if (!(obj1 instanceof Integer) ||
                !(obj2 instanceof Integer)) {
                throw new ClassCastException("Wrong argument");
            }
            Integer i1 = (Integer) obj1;
            Integer i2 = (Integer) obj2;

            return i1.compareTo(i2);
        }
    }

    /**
     *
     * Service Registration
     *
     */
    public void registerContext(ContextRegistration context) {
        // If Jetty has already started, dynamically register the context.
        // Otherwise, just add to the list for later registration
        logger.log(Level.INFO, "Registering " + context.getNamespace());

        synchronized (contexts){
            contexts.add(context);
            if (started){
                try{
                    addContext(context);
                    if (server.isStarted())
                        context.getContext().start();
                }
                catch(Exception e){
                    throw new InternalException("Failed to register Context " + context.getNamespace(), e);
                }
            }
        }
    }


    public HttpContext getContext(String ctxName) {
        Iterator it = contexts.iterator();
        while (it.hasNext()) {
            ContextRegistration ctx = (ContextRegistration) it.next();
            if (ctx.getNamespace().equals(ctxName)) {
                return (ctx.getContext());
            }
        }
        return (null);
    }


    /*
     * Remote invocation from ProtocolProxy
     *
     */
    public boolean apiCallback(int event, NewObjectIdentifier oid, 
                               NewObjectIdentifier dataOid) {
	boolean success = true;
        Integer EVT = new Integer(event);

        Iterator it = contexts.iterator();
        while (it.hasNext()) {
            ContextRegistration ctx = (ContextRegistration) it.next();        
            EventRegistrant reg = ctx.getRegistrant();
            if (reg != null) {
                if (ctx.getEvents().contains(EVT)) {
                    boolean res = reg.apiCallback(event, oid, dataOid);
                    if (res == false) {
                        success = res;
                    }
                }
            }
        }
        return success;
    }
}
