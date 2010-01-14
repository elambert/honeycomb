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
import java.nio.channels.ReadableByteChannel;
import java.security.NoSuchAlgorithmException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.client.*;

/**
 * Code specific to testing the Advanced Java API (ObjectArchive).
 */
public class HoneycombAdvancedJavaAPI extends HoneycombJavaAPI {
    public static String typeString = "AdvancedJavaAPI";

    public HoneycombAdvancedJavaAPI(String dVIP, int dPort)
                                                 throws HoneycombTestException {
        super(dVIP, dPort);

        type = typeString;
        useRefCountSemantics = false;
	try
	{
            oa = new ObjectArchive(dataVIP, dPort);
        }
        catch (Throwable t) {
          throw new HoneycombTestException(t);
        }
    }

    public void storeObject() throws HoneycombTestException {
        FileChannel filechannel = null;
        FileInputStream fileinputstream = null;
        CmdResult cr = null;

        // These dictate how the common library function behaves
        boolean useAdvAPI = true;
        boolean passMD = false;
        boolean streamMD = false;
        ReadableByteChannel dataChannel = null;
        HashMap mdMap = null;
        ReadableByteChannel metadataChannel = null;
        String cacheID = null;

        preStoreObject();

        if (streamData) {
            try {
                dataChannel =
                    new HCTestReadableByteChannel(filesize, seed, null);
            } catch (NoSuchAlgorithmException nsae) {
                throw new HoneycombTestException(nsae);
            }
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
            // XXX test option to link them later...
            if (activeMetadataObject == null ||
                activeMetadataObject.hm == null) {
                Log.INFO("Storing without specifying any metadata");
                // XXX trusting filesize and computedDataHash 
                // from preStoreObject
                cr = htc.storeObject(useAdvAPI, passMD, streamMD, dataChannel,
                    mdMap, metadataChannel, cacheID, 
                    filesize, computedDataHash);

                expectedResults.afterStoreWithoutMetadata();
            } else {
                // XXX PORT
                throw new HoneycombTestException("MD in adv API not supp");
            }
            if (streamData) {
                computedDataHash = cr.datasha1;
            }
        } catch(Throwable t) {
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

        processStoreResult(cr, passMD);
    }

    public void query() throws HoneycombTestException {
        throw new HoneycombTestException("Query on the AdvAPI is not " +
            "permitted currently; queries are always considered to fail");
    }
}
