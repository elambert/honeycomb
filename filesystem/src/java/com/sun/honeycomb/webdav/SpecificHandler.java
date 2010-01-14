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

import java.io.InputStream;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import java.io.OutputStream;
import java.io.IOException;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpOutputStream;
import com.sun.honeycomb.fscache.HCFile;

public abstract class SpecificHandler {

    protected static final String TRAILER_STATUS = "trailer-status";
    protected static final String TRAILER_REASON = "trailer-reason";

    public SpecificHandler() {
    }
    
    public abstract void handle(HCFile file, String[] extraPathComponents,
				HttpRequest request, HttpResponse response,
				InputStream in, OutputStream out)
	    throws IOException, HttpException;

    protected void sendError(final HttpResponse response,
			     final HttpFields trailer,
			     final int status, final String reason)
            throws IOException {
        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
	
	if (!out.isWritten())
	    response.sendError(status, reason);
    }
}
