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



package com.sun.honeycomb.hctest.cases.interfaces;

import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.NoSuchAlgorithmException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.ArchiveException;

/**
 * Code specific to testing the Basic Java API (NameValueObjectArchive).
 */
public class HoneycombBasicJavaAPI extends HoneycombJavaAPI {
    public static String typeString = "BasicJavaAPI";

    public HoneycombBasicJavaAPI(String dVIP, int dPort)
                                                  throws HoneycombTestException{
        super(dVIP, dPort);

        type = typeString;
	try{
	    oa = new TestNVOA(dataVIP, dPort);
	}
	catch (ArchiveException ae){
	    throw new HoneycombTestException("Error instantiation NameValueObjectArchive", ae);
	}
	catch (IOException ioe){
	    throw new HoneycombTestException("Error instantiation NameValueObjectArchive", ioe);
	}
    }

    public void storeObject() throws HoneycombTestException {
        FileChannel filechannel = null;
        FileInputStream fileinputstream = null;
        CmdResult cr = null;

        // These dictate how the common library function behaves
        boolean useAdvAPI = false;
        boolean passMD = false;
        boolean streamMD = false;
        ReadableByteChannel dataChannel = null;
        HashMap metadataRecord = null;
        ReadableByteChannel metadataChannel = null;
        String cacheID = null;

        preStoreObject();
        String hash = computedDataHash;

        if (streamData) {
            try {
                dataChannel =
                    new HCTestReadableByteChannel(filesize, seed, null);
            } catch (NoSuchAlgorithmException nsae) {
                throw new HoneycombTestException(nsae);
            }
            hash = null;
        } else {
            try {
                fileinputstream = new FileInputStream(filename);
                dataChannel = fileinputstream.getChannel();
            } catch (IOException iox) {
                throw new HoneycombTestException("Failed to open file: " +
                    iox.getMessage(), iox);
            }
        }

        try {
            if (activeMetadataObject == null ||
                activeMetadataObject.hm == null) {
                Log.INFO("Storing without specifying any metadata");
                // XXX trusting filesize and if it's a file, computedHash
                cr = htc.storeObject(useAdvAPI, passMD, streamMD, dataChannel,
                    metadataRecord, metadataChannel, cacheID, 
                    filesize, hash);
                expectedResults.afterStoreWithNonQueriableMetadata();
            } else {
                Log.INFO("Storing with metadata: " + activeMetadataObject.hm);

                passMD = true;
                metadataRecord = activeMetadataObject.hm;
                
                // XXX trusting filesize and if it's a file, computedHash
                cr = htc.storeObject(useAdvAPI, passMD, streamMD, dataChannel,
                    metadataRecord, metadataChannel, cacheID,
                    filesize, hash);
                if (activeMetadataObject.queriable) {
                    expectedResults.afterStoreWithQueriableMetadata();
                } else {
                    expectedResults.afterStoreWithNonQueriableMetadata();
                }
            }

            if (streamData) {
                computedDataHash = cr.datasha1;
            }
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        } finally {
            try {
                filechannel.close();
                fileinputstream.close();

                if (metadataChannel != null) {
                    metadataChannel.close();
                }
            } catch (Throwable ignore) {}
        }

        // Metadata info is always expected from the basic API
        processStoreResult(cr, true);
    }

    public void retrieveMetadata() throws HoneycombTestException {
        NameValueRecord nvr = null;
        NameValueObjectArchive nvoa = (NameValueObjectArchive) oa;

        preRetrieveMetadata();

        if (activeMetadataObject == null) {
            // XXX count as failure?
            Log.ERROR("Called retrieveMetadata with null");
            return;
        }

        Log.INFO("Retrieving metadata " +
            activeMetadataObject.summaryString());

        try {
            // XXX other kinds of retrieve?
            nvr = nvoa.retrieveMetadata(
                new ObjectIdentifier(activeMetadataObject.metadataOID));
        } catch (ArchiveException e) {
            throw new HoneycombTestException("failed to retrieve metadata: " +
                e.getMessage());
        } catch (Throwable e) {
            throw new HoneycombTestException("failed to retrieve metadata " +
                "(unexpected exception): " + e.toString());
        }    

        // This case shouldn't be hit, but I saw this once and it caused
        // the test to get an NPE and bomb out...better to catch it and
        // continue.
        if (nvr == null) {
            throw new HoneycombTestException("nvr is unexpectedly null after " +
                "retrieve metadata but no exception was thrown");
        }

        retrievedMetadataObject = new MetadataObject(nvr);
    }

    // XXX not in 1.0
    public void query() throws HoneycombTestException {
        queryResults = null;
        NameValueObjectArchive nvoa = (NameValueObjectArchive) oa;
        String queryString;

        preQuery();

        if (activeMetadataObject == null) {
            // XXX count as failure?
            Log.ERROR("Called query with null");
            return;
        }

        queryString = activeMetadataObject.queryString();
        Log.INFO("Querying for metadata " + queryString + "; max results " +
            maxQueryResults + "; for MD obj " +
            activeMetadataObject.summaryString());

        try {
            // XXX other kinds of query?
            // XXX cookies
            queryResults = nvoa.query(queryString, maxQueryResults);
        } catch (ArchiveException e) {
            throw new HoneycombTestException("failed to query: " +
                e.getMessage());
        } catch (Throwable e) {
            throw new HoneycombTestException("failed to retrieve " +
                "(unexpected exception): " + e.toString());
        }    
    }
}
