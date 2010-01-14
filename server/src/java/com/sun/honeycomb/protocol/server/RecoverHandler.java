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
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectLostException;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.emd.EMDClient;
import com.sun.honeycomb.emd.ObjectInfo;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class RecoverHandler extends ProtocolHandler {

    private static final String ID_PARAMETER = "id";
    private static final String FRAGMENT_ID_PARAMETER = "fragmentId";
    
    public RecoverHandler(final ProtocolBase service) {
        super(service);
    }
    
    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {
        NewObjectIdentifier identifier = null;
        int fragmentId = -1;
        String identifierString = request.getParameter(ID_PARAMETER);
        String fragmentIdString = request.getParameter(FRAGMENT_ID_PARAMETER);
        
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.fine("recovering object with id " + identifierString);
        }
        
        if (identifierString != null) {
            try {
                identifier = new NewObjectIdentifier(identifierString);
            } catch (IllegalArgumentException e) {
                // Do nothing - we handle this later
            }
        }
        
        if (fragmentIdString != null) {
            try{
                fragmentId = Integer.parseInt(fragmentIdString);
            } catch (NumberFormatException nfe) {
                // Do nothing, we handle this later
            }
        }
        
        if (identifier == null) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("recover failed due to invalid identifer: " +
                            identifierString);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      "invalid identifier: " + identifierString);
            return;
        }
        
        if (fragmentId == -1) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("recover failed due to invalid fragmentId: " +
                            fragmentIdString);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      "invalid fragmentId: " + fragmentIdString);
            return;
        }
        
        response.setContentType(ProtocolConstants.OCTET_STREAM_TYPE);
        
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        writeMulticellConfig(out);
        WritableByteChannel channel = Channels.newChannel(out);
        
        try {
            handleRecover(request, response, channel, identifier, fragmentId);
            String responseStr =  "Recovered " + identifier.toString() + ".\n";
            byte[] responseData = responseStr.getBytes();
            response.setContentType(ProtocolConstants.PLAIN_TEXT_TYPE);
            response.setCharacterEncoding(ProtocolConstants.ASCII_ENCODING);
            response.setContentLength(responseData.length);
            ((HttpOutputStream)response.getOutputStream()).write(responseData);
        } catch (NoSuchObjectException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,"recover failed on NoSuchObjectException.",e);
            }
            
            sendError(response,
                      trailer,
                      HttpResponse.__404_Not_Found,
                      e.getMessage());
        } catch (ObjectCorruptedException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                		   "recover failed on ObjectCorruptedException: " + 
                		   e.getMessage(),e);
            }
            
            sendError(response,
                      trailer,
                      HttpResponse.__424_Failed_Dependency,
                      e.getMessage());
        } catch (ObjectLostException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                		   "recover failed on ObjectLostException: " + 
                		   e.getMessage(),e);
            }
            
            sendError(response,
                      trailer,
                      HttpResponse.__410_Gone,
                      e.getMessage());
        } catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           "recover failed on ArchiveException: " +
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
                           "recover failed on InternalException: " +
                           e.getMessage(),
                           e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__500_Internal_Server_Error,
                      e.getMessage());
        }
    }

    protected void handleRecover(final HttpRequest request,
                                 final HttpResponse response,
                                 final WritableByteChannel channel,
                                 final NewObjectIdentifier oid,
                                 final int fragmentId)
        throws ArchiveException, IOException {
        ObjectInfo oi = null;
// BROKEN
/*
        try {
            oi = EMDClient.getMetadata(oid);
            getService().getArchive().recover(oid, oi, fragmentId);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.fine("recovered " + oid.toString() + ".");
            }
        } catch (NoSuchObjectException nsoe) {
            LOGGER.warning("No Such Object Exception getting md: " + nsoe);
        }
*/
    }
}
    
