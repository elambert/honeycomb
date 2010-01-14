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

import com.sun.honeycomb.connectors.HCInterface;
import com.sun.honeycomb.connectors.MDHandler;

import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.fscache.FSCache;
import com.sun.honeycomb.fscache.CacheLoader;
import com.sun.honeycomb.fscache.FSCacheObject;
import com.sun.honeycomb.fscache.FSCacheException;

import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.common.ProtocolConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.Socket;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.DigestAuthenticator;
import org.mortbay.http.HashUserRealm;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpHandler;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.handler.AbstractHttpHandler;
import org.mortbay.util.UrlEncoded;

public class MainHandler
    extends AbstractHttpHandler {

    private static final Logger logger =
        Logger.getLogger(MainHandler.class.getName());

    private HashMap methods;

    public static final int M_NULL       = 0;
    public static final int M_OPTIONS    = 1;
    public static final int M_PROPFIND   = 2;
    public static final int M_GET        = 3;
    public static final int M_HEAD       = 4;
    public static final int M_MKCOL      = 5;
    public static final int M_PUT        = 6;
    public static final int M_MOVE       = 7;
    public static final int M_DELETE     = 8;
    public static final int M_PROPPATCH  = 9;

    public static final int MAX_REQ_TIME = 60000; // ms

    // (Debug) When logging a request, print at most this many characters
    private static final int REQUEST_DUMP_MAX = 100;

    private SpecificHandler[] handlers = {
        null,
        new OptionHandler(),
        new PropfindHandler(),
        new GetHandler(),
        new HeadHandler(),
        new MkcolHandler(),
        new PutHandler(),
        new MoveHandler(),
        new DeleteHandler(), 

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
        // new ProppatchHandler()  
    };
	    
    public void initialize(HttpContext nContext) {
	super.initialize(nContext);
	Constants.init();

        methods.put("OPTIONS",   new Integer(M_OPTIONS));
        methods.put("PROPFIND",  new Integer(M_PROPFIND));
        methods.put("GET",       new Integer(M_GET));
        methods.put("HEAD",      new Integer(M_HEAD));
        methods.put("MKCOL",     new Integer(M_MKCOL));
        methods.put("PUT",       new Integer(M_PUT));
        methods.put("MOVE",      new Integer(M_MOVE));
        methods.put("DELETE",    new Integer(M_DELETE));

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
        // methods.put("PROPPATCH", new Integer(M_PROPPATCH));
    }

    public MainHandler() {
	super();
        methods = new HashMap();
    }

    public String getName() {
	return("HCDAV");
    }

    public void setName(String s) {
    }

    private boolean pathSanityCheck(String path) {
        return path != null &&
            !path.toUpperCase().startsWith("/WEB-INF") &&
            !path.toUpperCase().startsWith("/META-INF") &&
            !path.endsWith(".DS_Store") &&
            !path.endsWith(".hidden");
    }

    public void handle(String requestPath,
		       String pathParams,
		       HttpRequest request,
		       HttpResponse response)
	    throws IOException {
        String threadID = Thread.currentThread().toString();

        HCFile file = null;
        long startTime = System.currentTimeMillis();

        if (logger.isLoggable(Level.INFO)) {
            String sAddr = request.getHttpConnection().getRemoteHost();
            int sPort = getClientPort(request);
            logger.info("StartRequest " + sAddr + ":" + sPort + " " +
                        toString(request) + " " + threadID);
        }

        String sMethod = null;
        int method = 0;
        try {
            sMethod = request.getMethod();
            Integer m = (Integer) methods.get(sMethod);
            method = m.intValue();
        }
        catch (Exception e) {
            logger.info("Bad method \"" + sMethod + "\": 501 Not implemented");
	    response.sendError(HttpResponse.__501_Not_Implemented, sMethod);
            request.setHandled(true);
            return;
	}

        if (!pathSanityCheck(requestPath)) {
            response.sendError(HttpResponse.__404_Not_Found,
                               requestPath + ":  no such file");
            request.setHandled(true);
            return;
        }

        // Remove the root path from the front of the path leaving a
        // relative path

        HCFile root = HCFile.getRoot();
        String rootPath = root.fileName();
        if (rootPath.equals("/"))
            rootPath = "";
        if (!requestPath.startsWith(rootPath + "/")) {
            response.sendError(HttpResponse.__404_Not_Found,
                               "Unknown prefix in \"" + requestPath + "\"");
            request.setHandled(true);
            return;
        }

        String encodedPath = request.getEncodedPath();
        String prefix = ProtocolConstants.WEBDAV_PATH + rootPath + "/";
        if (!encodedPath.startsWith(prefix)) {
            response.sendError(HttpResponse.__500_Internal_Server_Error,
                               "Inconsistent path \"" + encodedPath + "\"");
            request.setHandled(true);
            return;
        }
        String relativePath = encodedPath.substring(prefix.length());

        // Split path into components

	String[] path = FSCache.split(relativePath);
	if (logger.isLoggable(Level.FINE))
            logger.fine("Instr parsed \"" + FSCache.combine(path) + "\" @ " +
                        (System.currentTimeMillis() - startTime) + " ms)");

        // Load into cache

        try {
            boolean writing =
                (method == M_PUT || method == M_MOVE || method == M_MKCOL);

            file = CacheLoader.load(path, writing);

            if (logger.isLoggable(Level.FINE)) {
                long d = System.currentTimeMillis() - startTime;
                String f = file.fileName();
                logger.fine("instr " + d + " LOOKUP \"" + f + "\" " +
                            threadID);
                logger.fine("View " + file.getViewName() + " is " +
                            (file.isViewCollapsingNulls()? "" : "not ") +
                            "collapsing trailing nulls");
            }

            String[] extras = null;
            if (file.fileType() == FSCacheObject.ROOTFILETYPE)
                extras = path;
            else {
                // The distance of the node from the root == the number
                // of components in the full path since "/" has 0 path
                // components.

                int numExtraComps = path.length - file.rootDistance();
                if (numExtraComps > 0) {
                    extras = new String[numExtraComps];
                    for (int i = file.rootDistance(), j = 0;
                         j < extras.length; i++, j++)
                        extras[j] = path[i];
                }
            }

            if (file.isFile())
                switch (method) {
                case M_GET:
                case M_HEAD:
                case M_OPTIONS:
                case M_PROPFIND:
                    response.setField("ETag", file.getOID().toExternalHexString());
                }

            response.setStatus(HttpResponse.__200_OK);

            if (logger.isLoggable(Level.FINE)) {
                String s = "Instr handling " + file + " {";
                if (extras != null)
                    for (int i = 0; i < extras.length; i++)
                        s += " \"" + extras[i] + "\"";
                logger.fine(s + " } @ " +
                            (System.currentTimeMillis() - startTime) + " ms");
            }

            handlers[method].handle(file, extras,
                                    request, response,
                                    request.getInputStream(),
                                    response.getOutputStream());
        }
        catch (FSCacheException e) {
            if (logger.isLoggable(Level.FINE))
                logger.log(Level.FINE, "FSCacheException: " + e, e);
            response.sendError(e.getError(), e.getMessage());
        }
        catch (HttpException e) {
            if (logger.isLoggable(Level.FINE))
                logger.log(Level.FINE, "HTTP exception: " + e, e);
            response.sendError(e.getCode(), e.toString());
        }
        catch (Exception e) {
            String s = "Couldn't handle " + toString(request);
            logger.log(Level.SEVERE, s + "; " + StringUtil.image(file), e);
            response.sendError(HttpResponse.__500_Internal_Server_Error,
                               s + ": " + StringUtil.image(e));
        }

        long reqTime = System.currentTimeMillis() - startTime;
        if (reqTime > MAX_REQ_TIME)
            logger.warning("LATE! "  + reqTime + "ms " + toString(request));
        if (logger.isLoggable(Level.INFO))
            logger.info("INSTR "  + reqTime + " SUM " + response.getStatus() +
                        " " + toString(request) + " " + threadID);

        if (logger.isLoggable(Level.FINEST))
            logger.finest(HCFile.fileCache.toString());

	request.setHandled(true);
    }

    static String toString(HttpRequest request) {
        return StringUtil.image(request.toString(), REQUEST_DUMP_MAX);
    }

    private int getClientPort(HttpRequest request) {
        Object conn = request.getHttpConnection().getConnection();
        if (conn instanceof Socket)
            return ((Socket)conn).getPort();
        return 0;
    }

    static private String toString(String[] names) {
        StringBuffer sb = new StringBuffer("{");

        String delim = "";
        for (int i = 0; i < names.length; i++) {
            sb.append(delim).append(names[i]);
            delim = ", ";
        }

        return sb.append('}').toString();
    }
}
