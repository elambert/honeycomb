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

public class ComplianceHandler extends ProtocolHandler {

    public static StatsAccumulator complianceStats = new StatsAccumulator();

    public ComplianceHandler(final ProtocolBase newService) {
        super(newService);
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        NewObjectIdentifier identifier;
        try {
            identifier = getRequestIdentifier(request, response, trailer);
        } catch (IllegalArgumentException e) {
            // already sent error reply in superclass
            return;
        }

        try {
            HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
            writeMulticellConfig(out);

            // Set retention path handler
            if (request.getPath().
                equals(ProtocolConstants.SET_RETENTION_PATH)) {
                handleSetRetentionTime(request,
                                       response,
                                       identifier,
                                       trailer);

            // Set retention relative path handler
            } else if (request.getPath().
                equals(ProtocolConstants.SET_RETENTION_RELATIVE_PATH)) {
                handleSetRetentionTimeRelative(request,
                                               response,
                                               identifier,
                                               trailer);

            // Get retention path handler
            } else if (request.getPath().
                       equals(ProtocolConstants.GET_RETENTION_PATH)) {
                handleGetRetentionTime(request,
                                       response,
                                       identifier,
                                       trailer);

            // Add legal hold tag path handler
            } else if (request.getPath().
                       equals(ProtocolConstants.ADD_HOLD_PATH)) {
                handleAddLegalHold(request,
                                   response,
                                   identifier,
                                   trailer);

            // Remove legal hold tag path handler
            } else if (request.getPath().
                       equals(ProtocolConstants.REMOVE_HOLD_PATH)) {
                handleRemoveLegalHold(request,
                                      response,
                                      identifier,
                                      trailer);
            }

        } catch (NoSuchObjectException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Set failed on NoSuchObjectException: " +
                            e.getMessage());
            }
            sendError(response,
                      trailer,
                      HttpResponse.__404_Not_Found,
                      e.getMessage());
        } catch (ObjectLostException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("compliance failed on ObjectLostException: " +
                            e.getMessage());
            }
            sendError(response,
                      trailer,
                      HttpResponse.__410_Gone,
                      e.getMessage());
        } catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "compliance failed on ArchiveException: " +
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
                           "compliance failed on InternalException: " +
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
                           "compliance failed on IllegalArgumentException: " +
                           e.getMessage(),
                           e);
            }       
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e.getMessage());
        }
    }

    // Set the retention time
    protected void handleSetRetentionTime(final HttpRequest request,
                                          final HttpResponse response,
                                          final NewObjectIdentifier oid,
                                          final HttpFields trailer)
        throws ArchiveException, IOException {
        String retentionString = getRequestParameter(ProtocolConstants.DATE_PARAMETER,
                                                     true,
                                                     request,
                                                     response,
                                                     trailer);
        long retentionTime = Long.parseLong(retentionString);
        Coordinator.getInstance().setRetentionTime(oid, retentionTime);
    }

    // Set the relative retention time
    protected void handleSetRetentionTimeRelative(final HttpRequest request,
                                                  final HttpResponse response,
                                                  final NewObjectIdentifier oid,
                                                  final HttpFields trailer)
        throws ArchiveException, IOException {
        String retentionString = getRequestParameter(ProtocolConstants.RETENTION_LENGTH_PARAMETER,
                                                     true,
                                                     request,
                                                     response,
                                                     trailer);

        // Retrieve and convert the retention length into milliseconds
        long retentionLength = (Long.parseLong(retentionString) * 1000);

        // Calculate the retention time relative to now
        long newRetentionTime = retentionLength + System.currentTimeMillis();
        
        // Set the new retention time
        Coordinator.getInstance().setRetentionTime(oid, newRetentionTime);

        // Write it out to the http stream
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        DataOutputStream dOut = new DataOutputStream(out);

        // Binary based response
        dOut.writeLong(newRetentionTime);

        // Flush before returning
        dOut.flush();
    }

    // Get the retention time
    protected void handleGetRetentionTime(final HttpRequest request,
                                          final HttpResponse response,
                                          final NewObjectIdentifier oid,
                                          final HttpFields trailer)
        throws ArchiveException, IOException {

        // Get the retention time
        long retentionTime = Coordinator.getInstance().getRetentionTime(oid);

        // Write it out to the http stream
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        DataOutputStream dOut = new DataOutputStream(out);

        // Binary based response
        dOut.writeLong(retentionTime);

        // Flush before returning
        dOut.flush();
    }

    // Add a legal hold tag
    protected void handleAddLegalHold(final HttpRequest request,
                                      final HttpResponse response,
                                      final NewObjectIdentifier oid,
                                      final HttpFields trailer)
        throws ArchiveException, IOException {
        String legalHold = getRequestParameter(ProtocolConstants.HOLD_TAG_PARAMETER,
                                               true,
                                               request,
                                               response,
                                               trailer);
        Coordinator.getInstance().addLegalHold(oid, legalHold);
    }

    // Remove a legal hold tag
    protected void handleRemoveLegalHold(final HttpRequest request,
                                         final HttpResponse response,
                                         final NewObjectIdentifier oid,
                                         final HttpFields trailer)
        throws ArchiveException, IOException {
        String legalHold = getRequestParameter(ProtocolConstants.HOLD_TAG_PARAMETER,
                                               true,
                                               request,
                                               response,
                                               trailer);
        Coordinator.getInstance().removeLegalHold(oid, legalHold);
    }
}
