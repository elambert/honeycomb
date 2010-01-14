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

import org.mortbay.http.HttpException;
import java.util.logging.Logger;
import org.mortbay.http.HttpResponse;
import javax.xml.parsers.DocumentBuilder;
import org.mortbay.http.HttpRequest;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.fscache.FSCacheObject;
import com.sun.honeycomb.fscache.FSCacheException;

/** A collection exists only in the cache; there is no OA object. */
public class MkcolHandler extends SpecificHandler {
    
    private static final Logger LOG =
        Logger.getLogger(MkcolHandler.class.getName());

    public MkcolHandler() {
    }

    public void handle(HCFile file,
		       String[] extras,
		       HttpRequest request,
		       HttpResponse response,
		       InputStream inputStream,
		       OutputStream outputStream)
	throws IOException, HttpException {
	
        if (file.fileType() == FSCacheObject.ROOTFILETYPE)
            throw new HttpException(HttpResponse.__400_Bad_Request,
                                    "View creation not supported");

        if (extras == null)
             throw new HttpException(HttpResponse.__409_Conflict,
                                     file.fileName() + " already exists");

        // Construct a dummy path as for a file that would live in this dir
        String[] comps = new String[extras.length + 1];
        for (int i = 0; i < extras.length; i++)
            comps[i] = extras[i];
        comps[extras.length] = "X";

        // Create leading directories for the dummy file
        try {
            file.mkDirsFor(comps);
        } catch (FSCacheException e) {
            throw e.getHttpException();
        }

	response.setStatus(HttpResponse.__201_Created);
    }
}
