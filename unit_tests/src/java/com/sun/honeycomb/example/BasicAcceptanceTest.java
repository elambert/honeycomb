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



package com.sun.honeycomb.example;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectExistsException;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.ObjectArchive;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.caches.NameValueRecord;
import com.sun.honeycomb.client.caches.NameValueSchema;
import com.sun.honeycomb.client.caches.SystemRecord;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class BasicAcceptanceTest {

    private static final int DEFAULT_ITERATIONS = 10;

    private static NameValueObjectArchive archive;
    private static int iterations;
    private static byte[][] objectDatas;
    private static ArrayList[] oidLists;
    private static WritableByteChannel nullChannel;

    public static void main(String args[])
        throws ArchiveException, IOException {

        if (args.length < 1 || args.length > 2) {
            exitUsage();
        }

        String host = args[0];
        archive = new NameValueObjectArchive(host);

        if (args.length == 1) {
            iterations = DEFAULT_ITERATIONS;
        } else {
            try {
                iterations = Integer.valueOf(args[1]).intValue();
            } catch (NumberFormatException e) {
                exitUsage();
            }
        }

        // byte arrays to use for storing objects
        objectDatas = new byte[][] {
            new byte[0],              // empty
            new byte[2 * 1024],       // 2 KB
            new byte[64 * 1024],      // 64 KB (buffer size)
            new byte[5 * 64 * 1024],  // 5 * 64 KB (fragment size)
            new byte[2 * 1024 * 1024] // 2 MB (fragment size)
        };

        Random random = new Random();
        for (int i = 0; i < objectDatas.length; i++) {
            random.nextBytes(objectDatas[i]);
        }

        oidLists = new ArrayList[objectDatas.length];

        // keep one list of oids for each object size
        for (int i = 0; i < objectDatas.length; i++) {
            oidLists[i] = new ArrayList(iterations);
        }

        // create data sink
        nullChannel = new FileOutputStream("/dev/null").getChannel();

        // create the oid set for the list of all oids that are created
        // in this test
        HashSet oidSet = new HashSet();

        testStoreAndRetrieve(oidSet);
        testExistence(oidSet, true);
        testDelete();
        testExistence(oidSet, false);
    }

    private static void exitUsage() {
        System.err.println("usage: BasicAcceptanceTest <host> [iterations]");
        System.exit(1);
    }

    private static void testStoreAndRetrieve(HashSet oidSet)
        throws ArchiveException, IOException {

        System.out.println("storing " +
                           (objectDatas.length * iterations * 2) +
                           " objects");

        // store <iterations> objects of each size
        SystemRecord smd;
        for (int i = 0; i < iterations; i++) {
            for (int j = 0; j < objectDatas.length; j++) {
                ByteArrayInputStream bytesIn;
                bytesIn = new ByteArrayInputStream(objectDatas[j]);
                ReadableByteChannel dataChannel = Channels.newChannel(bytesIn);

                // add an empty metadata
                smd = archive.storeObject(dataChannel);
                System.out.println("Stored oid " + smd.getIdentifier());
                oidLists[j].add(smd.getIdentifier());
                oidSet.add(smd.getIdentifier());

                // and a populated one
                NameValueRecord record = new NameValueRecord();
                record.put("artist", getValueString("artist", i, j));
                record.put("album", getValueString("album", i, j));
                record.put("title", getValueString("title", i, j));

                smd = archive.storeMetadata(smd.getIdentifier(), record);
                System.out.println("Stored oid " + smd.getIdentifier());
                oidLists[j].add(smd.getIdentifier());
                oidSet.add(smd.getIdentifier());
            }
        }

        System.out.println("\nretrieving objects and metadata");

        // retrieve all the objects and their metadata
        long size;
        NameValueRecord emd;
        for (int i = 0; i < iterations; i++) {
            for (int j = 0; j < objectDatas.length; j++) {
                ObjectIdentifier oid = (ObjectIdentifier)oidLists[j].get(2 * i);

                // retrieve using empty md "pointer"
                size = archive.retrieveObject(oid, nullChannel);
                if (size != objectDatas[j].length) {
                    throw new IllegalStateException("expected size " +
                                                    objectDatas[j].length +
                                                    " but got " +
                                                    size +
                                                    "(" + i + ", " + j + ")");
                }

                // this one should be empty
                emd = archive.retrieveMetadata(oid);
                if (emd.get("artist") != null) {
                    throw new IllegalStateException("expected empty metadata " +
                                                    "but got a populated one: " +
                                                    "artist = " + emd.get("artist") +
                                                    "(" + i + ", " + j + ")");
                }

                oid = (ObjectIdentifier)oidLists[j].get((2 * i) + 1);

                // retrieve using populated md "pointer"
                size = archive.retrieveObject(oid, nullChannel);
                if (size != objectDatas[j].length) {
                    throw new IllegalStateException("expected size " +
                                                    objectDatas[j].length +
                                                    " but got " +
                                                    size +
                                                    "(" + i + ", " + j + "), oid=" +
                                                    oid);
                }

                // this one should be populated
                emd = archive.retrieveMetadata(oid);
                if (emd.get("artist") == null ||
                    !emd.get("artist").equals(getValueString("artist", i, j))) {

                    throw new IllegalStateException("artist field doesn't match (" +
                                                    i + ", " + j + "): " +
                                                    emd);
                }
                System.out.print(".");
            }
        }
        System.out.println();
    }

    private static String getValueString(String prefix, int i, int j) {
        return prefix + "-" + i + "-" + j;
    }

    /**
     * Query the archive and check for oid existence.
     *
     * @param oidsToCheck the hash set of oids to check in the archive
     * @param existence true if the test should be made to make sure that oids
     *                  are returned in the archive. false to make sure that
     *                  the oids do not exist in the archive.
     */
    private static void testExistence(HashSet oidsToCheck, boolean existence)
        throws ArchiveException, IOException {
        // this should get the oids of all objects from the archive
        String query = "\"object_size\" > \"0\" OR \"object_size\" = \"0\"";
        int maxBatchSize = 1000;

        System.out.println("Testing archive for " +
                           ((existence) ? "existence" : "non existence"));

        boolean done = false;
        HashSet archiveOIDSet = new HashSet();
        boolean exists = false;
        QueryResultSet results = archive.query(query, maxBatchSize);
        while (!done) {
            while (results.hasNext()) {
                // check to make sure that the list of created oids exist
                // in the archive
                ObjectIdentifier oid = results.nextIdentifier();
                if (oidsToCheck.contains(oid)) {
                    if (existence) {
                        archiveOIDSet.add(oid);
                    } else {
                        throw new IllegalStateException("deleted oid appeared" +
                                                        " in query: " + oid);
                    }
                }
            }

            if (results.getResultCount() == maxBatchSize) {
                results = archive.query(results, maxBatchSize);
            } else {
                done = true;
            }
        }

        if (existence) {
            // Check to make sure that all oids have been matched
            if (archiveOIDSet.equals(oidsToCheck)) {
                System.out.println("PASSED: Archive contains all oids");
            } else {
                System.out.println("FAILED: Archive does not contain all " +
                                   "oids");
            }
        } else {
            System.out.println("PASSED: Archive does not contain " +
                               "specified oids");
        }
    }

    private static void testDelete()
        throws ArchiveException, IOException {

        System.out.println("deleting objects");

        // first delete the objects
        for (int i = 0; i < oidLists.length; i++) {
            for (int j = 0; j < oidLists[i].size(); j++) {
                archive.delete((ObjectIdentifier)oidLists[i].get(j));
                System.out.print(".");
            }
        }
        System.out.println();

        /* Don't do this test yet - since we're not purging the read cache
         * on delete, there's a good chance you'll succeed in reading an
         * object you had read just before deleting it.
         *

        System.out.println("attempting to retrieve deleted objects");

        for (int i = 0; i < oidLists.length; i++) {
            for (int j = 0; j < oidLists[i].size(); j++) {
                try {
                    archive.retrieveObject((ObjectIdentifier)oidLists[i].get(j),
                                           nullChannel);

                    throw new IllegalStateException("retrieved object that was " +
                                                    "expected to be deleted (" +
                                                    i + ", " + j + "): " +
                                                    (ObjectIdentifier)oidLists[i].get(j));
                } catch (NoSuchObjectException e) {
                    // do nothing - we didn't expect to find it
                }
            }
        }
         */
    }
}
