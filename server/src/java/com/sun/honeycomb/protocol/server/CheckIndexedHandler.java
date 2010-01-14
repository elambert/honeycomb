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

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectLostException;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.common.StatsAccumulator;
import com.sun.honeycomb.common.ProtocolConstants;

import java.io.IOException;
import java.io.DataOutputStream;
import java.util.logging.Level;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpOutputStream;

public class CheckIndexedHandler extends ProtocolHandler {

    public static StatsAccumulator checkIndexedStats =
        new StatsAccumulator();

    public static StatsAccumulator checkIndexedInsertStats =
        new StatsAccumulator();

    public CheckIndexedHandler(final ProtocolBase newService) {
        super(newService);
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        String cacheId = getRequestCacheID(request, response, trailer);

        NewObjectIdentifier oid;
        try {
            oid = getRequestIdentifier(request, response, trailer);
        } catch (IllegalArgumentException e) {
            // already sent error reply in superclass
            return;
        }

        try {
            handleCheckIndexed(cacheId, oid, response);

        } catch (NoSuchObjectException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,"Set failed on NoSuchObjectException: " +
                           e.getMessage());
            }
            if (LOGGER.isLoggable(Level.FINE)) 
                LOGGER.log(Level.FINE,"Exception details",e);
            sendError(response,
                      trailer,
                      HttpResponse.__404_Not_Found,
                      e.getMessage());
        } catch (ObjectLostException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,"checkIndexed failed on ObjectLostException: " +
                           e.getMessage(),e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__410_Gone,
                      e.getMessage());
        } catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "checkIndexed failed on ArchiveException: " +
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
                           "checkIndexed failed on InternalException: " +
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
                           "checkIndexed failed on IllegalArgumentException: " +
                           e.getMessage(),
                           e);
            }       
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e.getMessage());
        } // try/catch
    } // handle()

    void handleCheckIndexed(String cacheId, 
                            NewObjectIdentifier oid,
                            HttpResponse response)
        throws ArchiveException, IOException {
        long t1 = System.currentTimeMillis();

        // (-1 = already indexed, 0 = still not indexed, 1 = now indexed)
        int wasIndexed = Coordinator.getInstance().checkIndexed(cacheId,oid);

        long check_indexed_time = System.currentTimeMillis() - t1;
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("MEAS checkIndexed ("+wasIndexed+") __ time " + t1);
        }
        if (wasIndexed > 0) {
            //checkIndexed and added
            checkIndexedInsertStats.add(t1);
        } else {
            //checkIndexed and did not add
            checkIndexedStats.add(t1);
        }

        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        writeMulticellConfig(out);

        // Binary based response
        DataOutputStream dOut = new DataOutputStream(out);
        dOut.writeInt(wasIndexed);
        // Flush before returning
        dOut.flush();
    } //handleCheckIndexed
}

