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



package media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;

import media.document.PdfMetadataExtractor;
import media.image.JpegMetadataExtractor;
import media.video.AviMetadataExtractor;
import media.video.MpegMetadataExtractor;

public class MediaUpload {
    private static void usage(String progName) {
        System.out.println("java -cp <...> " + progName +
                           " <file/directory> <host1> [host2] ...");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            usage(args[0]);
        }

        File toUpload = new File(args[1]);
        if (!toUpload.exists()) {
            System.out.println("File [" + toUpload.getName() +
                               "] does not exist");
            System.exit(1);
        }

        // Construct the list of hosts
        String[] hosts = new String[args.length - 2];
        for (int i=0; i<hosts.length; i++) {
            hosts[i] = args[2+i];
        }

        // Check to see if this is a directory
        if (toUpload.isDirectory()) {
            PooledExecutor pool = new PooledExecutor(new LinkedQueue());
            pool.setKeepAliveTime(1000); // live forever
            pool.createThreads(hosts.length);
            uploadDirectory(hosts, toUpload, pool, 0);
            pool.shutdownAfterProcessingCurrentlyQueuedTasks();
            try {
                pool.awaitTerminationAfterShutdown();
            } catch (Exception e) {
                System.out.println(e);
            }
        } else {
            uploadFile(args[1], toUpload);
        }
    }

    private static void uploadDirectory(String[] hosts,
                                        File parent,
                                        PooledExecutor pool,
                                        int counter) {
        System.out.println("Queuing data in [" + parent.getPath() + "]");
        File[] files = parent.listFiles();
        for (int i=0; i<files.length; i++) {
            if (files[i].isDirectory()) {
                uploadDirectory(hosts, files[i], pool, counter);
            } else {
                MediaUploadTask task =
                    new MediaUploadTask(hosts[counter%hosts.length], files[i]);
                try {
                    pool.execute(task);
                } catch (InterruptedException e) {
                    System.out.println("Error uploading [" +
                                       files[i].getPath() + "]");
                }
                counter++;
            }
        }
    }

    public static void uploadFile(String host, File toUpload) {
        Map metadata = null;
        if (toUpload.getName().endsWith(".jpg") ||
            toUpload.getName().endsWith(".jpeg") ||
            toUpload.getName().endsWith(".JPG") ||
            toUpload.getName().endsWith(".JPEG")) {
            metadata = new HashMap();
            try {
                JpegMetadataExtractor.extract(metadata, toUpload);
            } catch (FileNotFoundException e) {
                System.out.println("Media file " + toUpload.getName() +
                                   " not found. Exception: " + e);
                System.exit(1);
            }

        } else if (toUpload.getName().endsWith(".avi") ||
                   toUpload.getName().endsWith(".AVI")) {
            metadata = new HashMap();
            try {
                AviMetadataExtractor.extract(metadata, toUpload);
            } catch (FileNotFoundException e) {
                System.out.println("Media file " + toUpload.getName() +
                                   " not found. Exception: " + e);
                System.exit(1);
            }
        } else if (toUpload.getName().endsWith(".mpg") ||
                   toUpload.getName().endsWith(".MPG") ||
                   toUpload.getName().endsWith(".mpeg") ||
                   toUpload.getName().endsWith(".MPEG")) {
            metadata = new HashMap();
            try {
                MpegMetadataExtractor.extract(metadata, toUpload);
            } catch (FileNotFoundException e) {
                System.out.println("Media file " + toUpload.getName() +
                                   " not found. Exception: " + e);
                System.exit(1);
            }
        } else if (toUpload.getName().endsWith(".pdf") ||
                   toUpload.getName().endsWith(".PDF")) {
            metadata = new HashMap();
            try {
                PdfMetadataExtractor.extract(metadata, toUpload);
            } catch (FileNotFoundException e) {
                System.out.println("Media file " + toUpload.getName() +
                                   " not found. Exception: " + e);
                System.exit(1);
            }
        } else {
            System.out.println("Unknown media. Storing without metadata...");
        }

        /*
        if (metadata != null) {
            printMetadata(metadata);
        }
        */

        storeFile(host, toUpload, metadata);
    }

    private static void storeFile(String host,
                                  File file,
                                  Map metadata) {
        try {
            NameValueObjectArchive archive = new NameValueObjectArchive(host);
            SystemRecord smd = null;
            if (metadata != null) {
                NameValueRecord record = archive.createRecord();
                record.putAll(metadata);
                smd = archive.storeObject
                    (new FileInputStream(file).getChannel(), record);
            } else {
                smd = archive.storeObject
                    (new FileInputStream(file).getChannel());
            }
            System.out.println("Stored file [" + file.getName() +
                               "] as OID [" + smd.getObjectIdentifier() +
                               "] on host [" + host + "]");
        } catch (FileNotFoundException e) {
            System.out.println("The file " + file.getName() +
                               " cannot be found");
        } catch (ArchiveException e) {
            e.printStackTrace();
            System.out.println("Honeycomb exception [" + e.getMessage() + "]");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IOException [" + e.getMessage() + "]");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printMetadata(Map metadata) {
        Iterator itr = metadata.keySet().iterator();
        while (itr.hasNext()) {
            String key = (String)itr.next();
            System.out.println(key + " = [" + (String)metadata.get(key) + "]");
        }
    }
}
