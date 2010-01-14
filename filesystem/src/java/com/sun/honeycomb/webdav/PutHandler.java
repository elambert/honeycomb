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
import com.sun.honeycomb.fscache.FSCacheObject;
import com.sun.honeycomb.fscache.FSCacheException;

import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Map;
import java.util.HashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpInputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class PutHandler extends SpecificHandler {
    private static Logger logger = Logger.getLogger(PutHandler.class.getName());


    public void handle(HCFile file, String[] extraPathComps,
		       HttpRequest request, HttpResponse response,
		       InputStream inputStream, OutputStream outputStream)
	    throws IOException, HttpException {
        int pos;

        long startTime = System.currentTimeMillis();

        HttpFields trailer = response.getTrailer();
        String mimeType = request.getField("content-type");

        if (file.isViewReadOnly())
            throw new HttpException(HttpResponse.__400_Bad_Request,
                                    "View " + file.getViewName() +
                                    " is read-only");

        if (extraPathComps == null)
             throw new HttpException(HttpResponse.__409_Conflict,
                                     "File " + file.fileName() +
                                     " already exists");

        String newPath = file.fileName() + FSCache.combine(extraPathComps);

        String name = extraPathComps[extraPathComps.length - 1];

        if (logger.isLoggable(Level.FINE))
            logger.fine("Instr PUT creating dirs for " + newPath + " @ " +
                     (System.currentTimeMillis() - startTime) + " ms");

        // Create leading directories

        try {
            file = file.mkDirsFor(extraPathComps);
        } catch (FSCacheException e) {
            throw e.getHttpException();
        }

        // Make sure the object can have file children
	if (!file.isViewCollapsingNulls() && !file.canMakeFile())
	    throw new HttpException(HttpResponse.__403_Forbidden,
				    "Cannot create a new file at \""+
				    file.fileName() + "\": path too short");

        try {
            Map md = MDHandler.parseEMD(file, name);
            if (mimeType != null)
                md.put(HCInterface.FIELD_MIMETYPE, mimeType);

            if (logger.isLoggable(Level.FINE))
                logger.fine("Instr PUT calling coordinator @ " +
                         (System.currentTimeMillis() - startTime) + " ms");

            startTime = System.currentTimeMillis();

            NewObjectIdentifier oid =
                HCFile.hc.storeFile(md, response.getInputStream());

            response.setField("ETag", oid.toExternalHexString());

            if (logger.isLoggable(Level.INFO))
                logger.info(newPath + " <==> " + oid.toExternalHexString());

            if (logger.isLoggable(Level.FINE)) {
                long mdTime = System.currentTimeMillis() - startTime;
                logger.fine("INSTR Store " + oid +
                         " (" + mimeType + ") @ " + mdTime + " ms");
            }

            HCFile.createHCFile(file, name, oid, md);

            // All done!

        }
        catch (FSCacheException e) {
            throw e.getHttpException();
        } catch (InternalException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, 
                           "store failed on InternalException: " +
                           e.getMessage(), e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__500_Internal_Server_Error,
                      e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "store failed on IllegalArgumentException: " +
                           e.getMessage(),
                           e);
            }       
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e.getMessage());
            return;
        } catch (IOException e) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                           "store failed client-side IOException: " +
                           e.getMessage());
            }       

            try {
                sendError(response,
                          trailer,
                          HttpResponse.__400_Bad_Request,
                          e.getMessage());
            } catch (IOException ex) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO,
                               "failed to send response: " +
                               ex.getMessage());
                }
            }

            return;
        }

    }

    public PutHandler() {}

}
