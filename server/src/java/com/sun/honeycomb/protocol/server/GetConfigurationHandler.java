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

import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.StatsAccumulator;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import java.util.ArrayList;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;

public class GetConfigurationHandler extends ProtocolHandler {

    public static StatsAccumulator getschemaStats = new StatsAccumulator();

    public GetConfigurationHandler(final ProtocolBase newService) {
        super(newService);
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        String cacheID = getRequestCacheID(request, response, trailer);
        if (cacheID == null) {
            return;
        }

        // temporary - currently only support the extended cache
        if (!cacheID.equals(CacheInterface.EXTENDED_CACHE)) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "get configuration failed on unsupported" +
                           " cache:" +
                           cacheID);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      "unsupported cache: " + cacheID);
            return;
        }

        HttpOutputStream out = 
            (HttpOutputStream)response.getOutputStream();

        try {            
            handleGetSchema(request, response, out);
        }  catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "get configuration failed on ArchiveException: " +
                           e.getMessage(),
                           e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e.getMessage());
        } catch (InternalException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "get configuration failed on InternalException: " +
                           e.getMessage(),
                           e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__500_Internal_Server_Error,
                      e.getMessage());
        } catch (IllegalArgumentException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "store failed on IllegalArgumentException: " +
                           e.getMessage(),
                           e);
            }       
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e.getMessage());
            return;
        }
    }

    protected void handleGetSchema(final HttpRequest request,
                                   final HttpResponse response,
                                   final HttpOutputStream out)
        throws ArchiveException, IOException {
        
        long t1 = System.currentTimeMillis();
        
        ArrayList fields = new ArrayList();
        RootNamespace.getInstance().getFields(fields, true);

        t1 = System.currentTimeMillis() - t1;
        getschemaStats.add(t1);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("MEAS getschema __ time " + t1);
        }

        response.setContentType(ProtocolConstants.PLAIN_TEXT_TYPE);
        response.setCharacterEncoding(ProtocolConstants.ASCII_ENCODING);

        writeMulticellConfig(out);

        StringBuffer result = new StringBuffer();

        result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        result.append("<schema name=\"relationalMD\" version=\"1\">\n");
        result.append("\n  <attributes>\n");

        for (int i=0; i<fields.size(); i++) {
            Field field = (Field)fields.get(i);
            int length = field.getLength();
            result.append("    <attribute name=\"");
            result.append(field.getQualifiedName());
            result.append("\" type=\"");
            result.append(field.getTypeString());
            result.append("\" writable=\"");
            result.append(field.isWritable());
            result.append("\" queryable=\"");
            result.append(field.isQueryable());
            if (length > 0) {
                result.append("\" length=\"");
                result.append(length);
            }
            result.append("\"/>\n");
        }
        result.append("  </attributes>\n\n");
	    
        result.append("</schema>\n");
        
        out.write(result.toString().getBytes(ProtocolConstants.UTF8_ENCODING));
    }
}
