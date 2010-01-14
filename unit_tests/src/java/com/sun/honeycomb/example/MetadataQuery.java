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

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.common.ArchiveException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Date;

public class MetadataQuery {

    private static NameValueObjectArchive archive;

    public static void main(String[] args)
        throws ArchiveException, IOException {

        String host = args[0];
        archive = new NameValueObjectArchive(host);

        String query;
        if (args.length > 1) {
            query = args[1];
        } else {
            long millisNow = System.currentTimeMillis();
            long millisPerDay = 24 * 60 * 60 * 1000;
            long millisYesterday = millisNow - millisPerDay;
            Date dateYesterday = new Date(millisYesterday);

            query = "\"object_ctime\" > \"" + dateYesterday.toString() +
                    "\" AND \"mime-type\" = \"image/jpeg\"";
        }

        downloadQueryResults(query);
    }

    public static void downloadQueryResults(String query)
        throws ArchiveException, IOException {

        int maxBatchSize = 1000;
        boolean done = false;
        QueryResultSet results = archive.query(query, maxBatchSize);

        while (!done) {
            while (results.hasNext()) {
                downloadFile(results.nextIdentifier());
            }

            if (results.getResultCount() == maxBatchSize) {
                results = archive.query(results, maxBatchSize);
            } else {
                done = true;
            }
        }
    }

    public static void downloadFile(ObjectIdentifier oid)
        throws ArchiveException, IOException {
        String dataPath = "/tmp" + oid.toString() + ".data";
        FileOutputStream dataOut = new FileOutputStream(dataPath);
        archive.retrieveObject(oid, dataOut.getChannel());

        String metadataPath = "/tmp/" + oid.toString() + ".metadata";
        FileOutputStream metadataOut = new FileOutputStream(metadataPath);
        archive.retrieveMetadata(oid, metadataOut.getChannel());
    }
}
