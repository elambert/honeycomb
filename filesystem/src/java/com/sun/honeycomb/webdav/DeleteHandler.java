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

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.ObjectLostException;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.resources.ByteBufferList;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import java.util.logging.Logger;
import java.io.InputStream;
import org.mortbay.http.HttpException;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.fscache.FSCacheException;

public class DeleteHandler
    extends SpecificHandler {

    private static final Logger logger = 
        Logger.getLogger(HeadHandler.class.getName());

    private Coordinator coordinator;

    public void handle(HCFile file,
		       String[] extraPath,
		       HttpRequest request,
		       HttpResponse response,
		       InputStream inputStream,
		       OutputStream outputStream)
	throws IOException, HttpException {

        try {
            if (extraPath != null)
                throw new HttpException(HttpResponse.__404_Not_Found,
                                        "No such file: " + file.fileName() +
                                        "/" + extraPath);

            if (!file.isFile())
                throw new HttpException(HttpResponse.__501_Not_Implemented,
                                        "Directory " + file.fileName() +
                                        " cannot be deleted");

            if (!file.isDeletable())
                throw new HttpException(HttpResponse.__403_Forbidden,
                                        "File " + file.fileName() +
                                        " cannot be deleted");
            if (!file.delete())
                throw new HttpException(HttpResponse.__500_Internal_Server_Error,
                                        "Couldn't delete deletable file " +
                                        file.fileName());
        }
        catch (HttpException e) {
            throw e;
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Exception!", e);
            throw new HttpException(HttpResponse.__500_Internal_Server_Error,
                                    "Couldn't delete " + file.fileName() +
                                    ": " + e);

        }
    }

}
