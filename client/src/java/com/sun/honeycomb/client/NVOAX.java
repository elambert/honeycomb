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



package com.sun.honeycomb.client;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InvalidOidException;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.StringTerminatedInputStream;

import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * This class is a shadow of the main entry point into the 
 * @HoneycombProductName@, intended for available but non-javadoc'd methods.
 */
public class NVOAX extends NameValueObjectArchive {

    public NVOAX(String address) throws ArchiveException, IOException{
        super(address, ProtocolConstants.DEFAULT_PORT);
    }

    public NVOAX(String address, int port) 
        throws ArchiveException, IOException {
        super(address, port);
    }

    /**
     * Upload a new data object to the designated cell with no user metadata.
     * Returns a <a href=SystemRecord.html>SystemRecord</a> instance containing
     * the system metadata for the new data object.
     *
     * @throws ArchiveException if bad cellid or if the store fails due to an 
     * error on the server
     * @throws IOException if the store fails due to a communication
     * problem
     */
    public SystemRecord storeObject(int cellid, ReadableByteChannel dataChannel)
        throws ArchiveException, IOException {
        if (cellid < 0  ||  cellid > 127) {
            throw new ArchiveException("Cellid [" + cellid + 
                                       "] must be in 0..127");
        }
        return storeObject(dataChannel, CACHE_ID, new byte[0], cellid);
    }


    /**
     * Upload a new data object to the designated cell with a Name-Value 
     * metadata record created from <code>record</code>. Returns a
     * <a href=SystemRecord.html>SystemRecord</a> instance containing the 
     * system metadata for the new object.
     *
     * @throws ArchiveException if bad cellid or if the store fails due to 
     * an error on the server
     * @throws IOException if the store fails due to a communication
     * problem
     */
    public SystemRecord storeObject(int cellid, ReadableByteChannel dataChannel,
                                    NameValueRecord record)
        throws ArchiveException, IOException {

        if (cellid < 0  ||  cellid > 127) {
            throw new ArchiveException("Cellid [" + cellid + 
                                       "] must be in 0..127");
        }
        return storeObject(dataChannel,
			   CACHE_ID,
			   createBytesForRecord(record), 
                           cellid);
    }



}
