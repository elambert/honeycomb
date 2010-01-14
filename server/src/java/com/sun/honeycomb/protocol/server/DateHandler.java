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

import java.io.IOException;
import java.io.DataOutputStream;
import java.util.logging.Level;
import java.util.Date;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpOutputStream;

public class DateHandler extends ProtocolHandler {

    public DateHandler(final ProtocolBase newService) {
        super(newService);
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        // Get the retention time
        long date = System.currentTimeMillis();

        // Write it out to the http stream
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        writeMulticellConfig(out);
        DataOutputStream dOut = new DataOutputStream(out);

        // Binary based response
        dOut.writeLong(date);

        // Flush before returning
        dOut.flush();

        // Debug
        if (LOGGER.isLoggable(Level.INFO)) {
            Date currentDate = new Date(date);
            LOGGER.info("DateHandler.handleGetDate() wrote date " +
                        date + " (" + currentDate.toString() +
                        ") to the http response stream");
        }
    }
}
