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
import java.nio.channels.WritableByteChannel;
import java.nio.channels.Channels;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.security.NoSuchAlgorithmException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ListIterator;
import java.util.HashMap;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.ArchiveException;

/**
 * Shared code between the Basic Java API and the Advanced Java API.
 */
public class HoneycombJavaAPI extends HoneycombInterface {
    ObjectArchive oa;
    public static String hashAlg = "sha1";

    public HoneycombJavaAPI(String dVIP, int dPort)
                                                 throws HoneycombTestException {
        super(dVIP, dPort);

        type = "JavaAPI";
    }

    public String IDString() {
        String s = "DataOID" + DELIM + dataOID + "; ";
        ListIterator li = metadataObjects.listIterator();
        int i = 0;
        while (li.hasNext()) {
            MetadataObject mo = (MetadataObject) li.next();
            s += "[" + i++ + "] MetadataOID" + DELIM + mo.metadataOID +
                "(" + mo.statusString() + "); ";
        }

        return (s);
    }

    public boolean useAdvAPI() {
        if (type.equals(HoneycombAdvancedJavaAPI.typeString)) {
            return (true);
        } else {
            return (false);
        }
    }

    public void processStoreResult(CmdResult cr, boolean expectMD)
        throws HoneycombTestException {
        ObjectIdentifier oid;
        SystemRecord sr = cr.sr;

        // if we don't pass MD then getObjectIdentifier
        // returns the data OID
        if (expectMD) {
            oid = sr.getObjectIdentifier();
            activeMetadataObject.metadataOID = oid.toString();
            oid = QAClient.getLinkIdentifier(sr);
            dataOID = oid.toString();
        } else {
            oid = sr.getObjectIdentifier();
            dataOID = oid.toString();
        }

        Log.DEBUG("Store took " + cr.time + " milliseconds");

        String alg = sr.getDigestAlgorithm();
        if (alg == null) {
            Log.WARN("Ack! Bug 6216444 (On successful store, " +
                "SystemRecord.getDigestAlgorithm() returns null) is still " +
                "not fixed!");
        } else if (!alg.equals(hashAlg)) {
            throw new HoneycombTestException("Expected digest algorithm " +
                hashAlg + " but got " + alg + " instead");
        } else {
            Log.DEBUG("Digest is " + alg + " as expected");
        }

        returnedDataHash = HCUtil.bytesToHexString(sr.getDataDigest());

        if (returnedDataHash == null) {
            // XXX this is going to block us from catching bugs...
            Log.WARN("Ack! Bug 6187594 (client API isn't returning data " +
                "and MD hash) is not fixed...hacking the data sha1");
            returnedDataHash = computedDataHash;
        }

    }

    public void storeMetadata() throws HoneycombTestException {
        CmdResult cr = null;

        // These dictate how the common library function behaves
        boolean useAdvAPI = useAdvAPI();
        ObjectIdentifier oid = null;
        boolean streamMD = false;
        HashMap metadataRecord = null;
        ReadableByteChannel metadataChannel = null;
        String cacheID = null;


        // If use NVOA, pass a MD OID (XXX add tests for object OID as well)
        oid = findObjectIdentifierToUse();

        preStoreMetadata();

        try {
            if (activeMetadataObject == null ||
                activeMetadataObject.hm == null) {
                // XXX?
                throw new HoneycombTestException("addMD with no MD?!? for oid" + oid);
                //Log.INFO("XXX Adding metadata with no MD!?!");
                //sr = nvoa.storeMetadata(dataOID, mo.hm);
                //expectedResults.afterStoreWithNonSearchableMetadata();
            } else {
                Log.INFO("Storing metadata as a NameValueRecord " +
                    activeMetadataObject.hm + " for oid " + oid);

                metadataRecord = activeMetadataObject.hm;

                cr = htc.storeMetadata(useAdvAPI, oid, streamMD, metadataRecord,
                    metadataChannel, cacheID);
            }

            if (activeMetadataObject.queriable) {
                expectedResults.afterAddQueriableMetadata();
            } else {
                expectedResults.afterAddMetadata();
            }
        } catch (Throwable t) {
            // we failed so we want to remove the partial MD obj from our
            // list since it didn't actually get stored
            cleanupMetadataObjects();
            throw new HoneycombTestException(t);
        } finally {
            try {
                if (metadataChannel != null) {
                    metadataChannel.close();
                }
            } catch (Throwable ignore) {}
        }

        processStoreMetadataResult(cr);
    }

    public void processStoreMetadataResult(CmdResult cr)
        throws HoneycombTestException {
        SystemRecord sr = cr.sr;
        ObjectIdentifier oid;
        oid = sr.getObjectIdentifier();
        activeMetadataObject.metadataOID = oid.toString();
        Log.INFO("storedMetadata returned " + activeMetadataObject.metadataOID);

        String alg = sr.getDigestAlgorithm();
        if (alg == null) {
            Log.WARN("Ack! Bug 6216444 (On successful storeMetadata, " +
                "SystemRecord.getDigestAlgorithm() returns null & no data hash)"
                + " is still not fixed!");
        } else if (!alg.equals(hashAlg)) {
            throw new HoneycombTestException("Expected digest algorithm " +
                hashAlg + " but got " + alg + " instead");
        } else {
            Log.DEBUG("Digest is " + alg + " as expected");
        }

        // Per Sacha, we should be getting back the data hash after
        // storeMetadata
        String dataHash = HCUtil.bytesToHexString(sr.getDataDigest());

        if (dataHash == null) {
            // XXX this is going to block us from catching bugs...
            Log.WARN("Ack! Bug 6216444 On successful store(Metadata), " +
                "SystemRecord.getDigestAlgorithm() returns null " +
                "& no data hash");
        } else {
            // compare with our data hash to make sure it is the right thing
            if (!dataHash.equals(computedDataHash)) {
                throw new HoneycombTestException("A different data hash (" +
                    dataHash + ") was returned from storeMetadata.  Expected " +
                    computedDataHash);
            } else {
                Log.INFO("data hash returned as expected from storeMetadata: " +
                    dataHash);
            }
        }
    }

    public void retrieveObject() throws HoneycombTestException {
        FileOutputStream stream = null;
        WritableByteChannel channel = null;

        preRetrieveObject();
        ObjectIdentifier oid = findObjectIdentifierToUse();
        Log.INFO("Retrieving object " + oid +
            " to " + filenameRetrieve);

        if (streamData) {
            try {
                channel = new HCTestWritableByteChannel(filesize, null);
            } catch (NoSuchAlgorithmException nsae) {
                throw new HoneycombTestException(nsae);
            }
        } else {
            try {
                stream = new FileOutputStream(filenameRetrieve);
                channel = stream.getChannel();
            } catch (IOException e) {
                throw new HoneycombTestException("failed to open file: " +
                    e.getMessage());
            }
        }

        try {
            oa.retrieveObject(oid, channel);
            if (streamData) {
                computedRetrieveStreamDataHash =
                    ((HCTestWritableByteChannel)channel).computeHash();
            }
        } catch (ArchiveException e) { 
            throw new HoneycombTestException("failed to retrieve object: " +
                e.getMessage());
        } catch (Throwable e) {
            throw new HoneycombTestException("failed to retrieve file " +
                "(unexpected exception): " + e.toString());
        } finally {
            try {
                if (channel != null) {
                    channel.close();
                } 
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                // We don't exit here because only the close failed.
                Log.ERROR("failed to close file: " + e.getMessage());
            }
        }
    }

    // XXX Are delete semantics the same for both APIs?  Does
    // NVOA.delete on MD implicitly delete both data and MD?
    public void deleteObject() throws HoneycombTestException {
        preDeleteObject();

        ObjectIdentifier oid = findObjectIdentifierToUse();
        Log.INFO("Deleting object " + oid);
        try {
            htc.delete(useAdvAPI(), oid);
            if (useRefCountSemantics) {
                activeMetadataObject.deleted = true;
                if (activeMetadataObject.queriable) {
                    expectedResults.afterDeleteQueriableMetadata();
                } else {
                    expectedResults.afterDeleteMetadata();
                }
            } else {
                expectedResults.afterDeleteObject();
            }
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("failed to delete object: " +
                e.getMessage());
        }
    }

    public void deleteMetadata() throws HoneycombTestException {
        preDeleteMetadata();

        Log.INFO("Deleting metadata " + activeMetadataObject.summaryString());

        try {
            htc.delete(useAdvAPI(),
                new ObjectIdentifier(activeMetadataObject.metadataOID));
            if (activeMetadataObject.queriable) {
                expectedResults.afterDeleteQueriableMetadata();
            } else {
                expectedResults.afterDeleteMetadata();
            }
            activeMetadataObject.deleted = true;
            Log.DEBUG(activeMetadataObject.toString());
        } catch (HoneycombTestException e) {
            throw new HoneycombTestException("failed to delete metadata: " +
                e.getMessage());
        }
    }
}
