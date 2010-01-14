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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class XMLRandomTest {

    private static final int DEFAULT_THREAD_COUNT = 1;

    // 10 MB of random data
    private static final int RANDOM_BYTE_LENGTH = 10 * 1024 * 1024;
    // 16k oids
    private static final int MAX_ID_COUNT = 16 * 1024;

    private static final int OID_SIZE = 26;

    private static byte[] randomBytes;
    private static NameValueObjectArchive archive;
    private static List identifiers;
    private static Random mainRandom;
    private static boolean badBehaviorEnabled = false;

    private static void exitUsage() {
        System.out.println("usage: RandomTest [threadcount] <host1 [port1]> ...");
        System.exit(1);
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            exitUsage();
        }

        int start = 0;
        int threadCount = DEFAULT_THREAD_COUNT;

        try {
            threadCount = Integer.valueOf(args[0]).intValue();
            start++;
        } catch (NumberFormatException e) {
            // Do nothing - assume the first argument is a host name
            // rather than a thread count
        }

        String[] hosts = new String[args.length - start];
        int[] ports = new int[args.length - start];
        int hostCount = 0;
        int portCount = 0;

        for (int i = start; i < args.length; i++) {
            try {
                int port = Integer.valueOf(args[i]).intValue();
                ports[portCount++] = port;
            } catch (NumberFormatException e) {
                hosts[hostCount++] = args[i];
            }
        }

        System.out.println("using hosts:");
        for (int i = 0; i < hostCount; i++) {
            int port = (portCount > 0) ? ports[i] : 8080;
            System.out.println("    " + hosts[i] + ":" + port);
        }

        String[] tmpHosts = new String[hostCount];
        System.arraycopy(hosts, 0, tmpHosts, 0, hostCount);
        hosts = tmpHosts;

        if (portCount > 0) {
            int[] tmpPorts = new int[portCount];
            System.arraycopy(ports, 0, tmpPorts, 0, portCount);
            ports = tmpPorts;
        } else {
            ports = null;
        }

        if (ports != null) {
            archive = new NameValueObjectArchive(hosts[0], ports[0]);
        } else {
            archive = new NameValueObjectArchive(hosts[0]);
        }

//        archive.setActiveHostTimeout(30000);
        archive.setConnectionTimeout(0);
        archive.setSocketTimeout(0);

        identifiers = new ArrayList(MAX_ID_COUNT);

        mainRandom = new Random(System.currentTimeMillis());
        randomBytes = new byte[RANDOM_BYTE_LENGTH];

        // Populate the data array with random bytes
        mainRandom.nextBytes(randomBytes);

        System.out.println("spawning " +
                           threadCount +
                           " thread" +
                           ((threadCount != 1) ? "s" : ""));

        for (int i = 0; i < threadCount; i++) {
            if (i > 0) {
                try {
                    // Wait a random interval < 100 ms to start
                    // the next thread.
                    Thread.sleep(mainRandom.nextInt(100));
                } catch (InterruptedException e) {
                }
            }

            String name = String.valueOf(i + 1);
            if (name.length() < 2) {
                name = "0" + name;
            }

            new WorkerThread(name).start();
        }
    }

    /**
     * Add an oid to the list for possible future retrieval. If we already
     * have MAX_ID_COUNT oids, then replace one at random.
     */
    private static synchronized void addIdentifier(ObjectIdentifier oid) {
        if (identifiers.size() < MAX_ID_COUNT) {
            identifiers.add(oid);
        } else {
            int index = mainRandom.nextInt(MAX_ID_COUNT);
            identifiers.set(index, oid);
        }
    }

    /**
     * Select an oid at random for retrieval. If we don't have any,
     * return null.
     */
    private static synchronized ObjectIdentifier selectIdentifier() {
        if (identifiers.size() > 0) {
            int index = mainRandom.nextInt(identifiers.size());
            return (ObjectIdentifier)identifiers.get(index);
        }

        return null;
    }

    private static class WorkerThread extends Thread {

        private Random random;
        byte[] idBytes;

        public WorkerThread(String name) {
            super(name);
            random = new Random(System.currentTimeMillis());
            idBytes = new byte[OID_SIZE];
        }

        public void run() {
            while (true) {
                try {
                    performOperation();
                    // Wait a random interval < 100 ms before the
                    // next operation
                    sleep(random.nextInt(100));
                } catch (InterruptedException e) {
                    // Do nothing
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }

        // Select an operation to perform at random.
        private void performOperation() throws Throwable {
            int operation = random.nextInt(4);

            switch (operation) {
                case 0:
                    performStore();
                    break;
                case 1:
                    performRetrieveData();
                    break;
                case 2:
                    performRetrieveMetadata();
                    break;
                case 3:
                    performDelete();
                    break;
                case 4:
                    performQuery();
                    break;
                case 5:
                    performGetConfiguration();
                    break;
            }
        }

        /**
         * Store a new object using a randomly selected piece of the
         * shared byte array. Possibly pass the actual length, no length,
         * or an incorrect length.  If the store succeeds, remember the oid
         * for future retrieval.
         */
        private void performStore() {
            int offset = random.nextInt(randomBytes.length);
            int length = random.nextInt((randomBytes.length - offset) + 1);

//            int length = random.nextInt(11);
            if (length == 0) {
                length += 1;
            }

            ByteArrayInputStream stream =
                new ByteArrayInputStream(randomBytes, offset, length);
            ReadableByteChannel channel = Channels.newChannel(stream);

            long size = length;
            // long size = ObjectArchive.UNKNOWN_SIZE;
            if (badBehaviorEnabled) {
                switch (random.nextInt(2)) {
                    case 0:
                        size = -1;
                        break;
                    case 1:
                        long error = length / (random.nextInt(10) + 1);
                        size += (random.nextBoolean()) ? error : -error;
                        break;
                    default:
                        // use the correct size
                }
            }

            System.out.println(getName() +
                               " - storing object with length " +
                               size +
                               " (" +
                               length +
                               ")");

            try {
                SystemRecord smd = null;
                ObjectIdentifier oid = null;

                long startTime = System.currentTimeMillis();
                smd = archive.storeObject(channel,
                                          ObjectArchive.getEmptyChannel());
                long endTime = System.currentTimeMillis();
                oid = smd.getIdentifier();

                System.out.println(getName() +
                                   " - store took " +
                                   (endTime - startTime) +
                                   " ms, got id: " +
                                   oid);
                addIdentifier(oid);
            } catch (ArchiveException e) {
                System.out.println(getName() +
                                   " - failed to store: " +
                                   e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println(getName() +
                                   " - failed to store: " +
                                   e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    channel.close();
                    stream.close();
                } catch (IOException e) {
                    System.out.println(getName() +
                                       " - failed to store: " +
                                       e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private void performRetrieveData() {
            performRetrieve(true);
        }
        
        private void performRetrieveMetadata() {
            performRetrieve(false);
        }

        /**
         * Retrieve an object chosen at random or one that doesn't exist.
         */
        private void performRetrieve(boolean data) {
            ObjectIdentifier oid = null;

            // Maybe choose an existing oid at random...
//            if (random.nextBoolean()) {
                oid = selectIdentifier();
                if (oid != null) {
                    System.out.println(getName() +
                                       " - retrieving " +
                                       ((data) ? "object" : "metadata") +
                                       " with known id " +
                                       oid);
                } else {
                    return;
                }
//            }

            // Or make one up and expect it to fail
            if (oid == null) {
                random.nextBytes(idBytes);
//                oid = new ObjectIdentifier(idBytes);

                System.out.println(getName() +
                                   " - retrieving " +
                                   ((data) ? "object" : "metadata") +
                                   " with random id " +
                                   oid);
            }

            FileOutputStream stream;
            try {
                stream = new FileOutputStream("/dev/null");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(getName() +
                                           " - couldn't find /dev/null!",
                                           e);
            }

            FileChannel channel = stream.getChannel();

            try {
                long size = 0;
                NameValueRecord record = null;

                long startTime = System.currentTimeMillis();
                if (data) {
                    size = archive.retrieveObject(oid, channel);
                } else {
                    record = archive.retrieveMetadata(oid);
                }
                long endTime = System.currentTimeMillis();

                if (data) {
                    System.out.println(getName() +
                                       " - read " +
                                       size +
                                       " bytes in " +
                                       (endTime - startTime) +
                                       " ms");
                } else {
                    System.out.println(getName() +
                                       " - read record:\n" +
                                       record +
                                       "\nin " +
                                       (endTime - startTime) +
                                       " ms");
                }
            } catch (NoSuchObjectException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on"+
                                   " NoSuchObjectException: " +
                                   e.getMessage());
            } catch (ArchiveException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " ArchiveException: "+
                                   e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " IOException: " +
                                   e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    channel.close();
                    stream.close();
                } catch (IOException e) {
                    System.out.println(getName() +
                                       " - failed to store: " +
                                       e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        /**
         * Delete an object chosen at random or one that doesn't exist.
         */
        private void performDelete() {
            ObjectIdentifier oid = null;

            // Maybe choose an existing oid at random...
//            if (random.nextBoolean()) {
                oid = selectIdentifier();
                if (oid != null) {
                    System.out.println(getName() +
                                       " - deleting object with known id " +
                                       oid);
                } else {
                    return;
                }
//            }

            // Or make one up and expect it to fail
            if (oid == null) {
                random.nextBytes(idBytes);
//                oid = new ObjectIdentifier(idBytes);

                System.out.println(getName() +
                                   " - deleting object with random id " +
                                   oid);
            }

            try {
                long startTime = System.currentTimeMillis();
                archive.delete(oid);
                long endTime = System.currentTimeMillis();

                System.out.println(getName() +
                                   " - deleted object in " +
                                   (endTime - startTime) +
                                   " ms");;
            } catch (NoSuchObjectException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on"+
                                   " NoSuchObjectException: " +
                                   e.getMessage());
            } catch (ArchiveException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " ArchiveException: "+
                                   e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " IOException: " +
                                   e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * Perform a query.
         */
        private void performQuery() {
            String query = "\"object_size\" > \"1024102\"";
            // String query = null;
            String cookie = null;
            int maxResults = 500;

            try {
                boolean done = false;
                while (!done) {
                    System.out.println("performing query: " + query);

                    long startTime = System.currentTimeMillis();
                    QueryResultSet results = archive.query(query,
                                                           maxResults,
                                                           cookie);
                    long endTime = System.currentTimeMillis();

                    System.out.println(getName() +
                                       " - got " + 
                                       results.getResultCount() +
                                       " query results in " +
                                       (endTime - startTime) +
                                       " ms");

                    cookie = results.getCookie();

                    if (results.getResultCount() == 0) {
                        System.out.println("    no results - done");
                        done = true;
                    } else {
                        System.out.println("    results:");
                        for (int i = 0; i < results.getResultCount(); i++) {
                            System.out.println("        " + results.getResult(i));
                        }
                    }
                }
            } catch (ArchiveException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " ArchiveException: "+
                                   e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " IOException: " +
                                   e.getMessage());
                e.printStackTrace();
            }
        }

        private void performGetConfiguration() {
            System.out.println("getting cache configuration");

            try {
                long startTime = System.currentTimeMillis();
                NameValueSchema schema = archive.getSchema();
                long endTime = System.currentTimeMillis();

                System.out.println(getName() +
                                   " - got schema:\n" + 
                                   schema +
                                   "\nin " +
                                   (endTime - startTime) +
                                   " ms");
            } catch (ArchiveException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " ArchiveException: "+
                                   e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println(getName() +
                                   " - failed to retrieve on" +
                                   " IOException: " +
                                   e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
