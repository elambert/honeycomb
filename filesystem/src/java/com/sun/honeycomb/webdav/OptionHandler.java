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

import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.fscache.FSCache;
import com.sun.honeycomb.fscache.FSCacheObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class OptionHandler extends SpecificHandler {

    private static final Logger logger =
        Logger.getLogger(OptionHandler.class.getName());

    private static final String USER_AGENT_FIELD = "User-Agent";
    private static final String MACOSX_PREFIX = "WebDAVFS";

    public OptionHandler() {
    }

    public void handle(HCFile file, String[] extraPath,
		       HttpRequest request, HttpResponse response,
		       InputStream inputStream, OutputStream outputStream)
	    throws IOException, HttpException {
	String userAgent = request.getField(USER_AGENT_FIELD);
        boolean isOSXFinder = userAgent != null &&
            userAgent.startsWith(MACOSX_PREFIX);

        if (extraPath != null)
            throw new HttpException(HttpResponse.__404_Not_Found,
                                    "No such file: " + file.fileName() +
                                    FSCache.combine(extraPath));

        String davLevel = "1";
        if (isOSXFinder)
            davLevel = "1,2";

        if (logger.isLoggable(Level.FINE))
            logger.fine("OPTIONS request from client \"" + userAgent + 
                        "\"; DAV level = " + davLevel);

        response.addField("DAV", davLevel);
	response.addField("MS-Author-Via", "DAV"); // ???

        if (file.fileType() == FSCacheObject.ROOTFILETYPE)
            // The root is a read-only directory
            response.addField("Allow", "OPTIONS, PROPFIND");
        else if (file.isFile()) {
            // Plain file
            String options = "OPTIONS, PROPFIND, GET, HEAD";
            if (file.isDeletable())
                options += ", DELETE";
            response.addField("Allow", options);
        }
        else {
            // Directories: a directory always has a view it belongs to
            int distanceFromLeaves =  file.rootDistance() -
                file.getViewAttrNames().length - 1; // view name is the -1

            if (distanceFromLeaves > 0)
                // "Deep archive" directory; it's read-only
                response.addField("Allow", "OPTIONS, PROPFIND");
            else if (distanceFromLeaves < 0)
                // Somewhere in the middle; allowed to create directories
                response.addField("Allow", "OPTIONS, PROPFIND, MKCOL");
            else
                // distanceFromLeaves == 0, i.e. lowest level and PUT is OK
                response.addField("Allow", "OPTIONS, PROPFIND, PUT");
        }
    }
}
