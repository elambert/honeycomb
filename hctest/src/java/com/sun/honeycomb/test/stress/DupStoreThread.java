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



package com.sun.honeycomb.test.stress;

import java.net.InetAddress;
import java.util.Random;
import java.util.Date;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.ObjectIdentifier;

public class DupStoreThread extends Thread
{
    private NameValueObjectArchive archive;
    private boolean isExtendedMetadata;
    private boolean pollIndexed = false;
    private long numErrors = 0;
    private Queue oids;
    
    private double timeTag;

    private static int objCount = 0;
    private int objId;

    private static String hostname;
    static {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (Throwable t) {
            t.printStackTrace();
            System.err.println("error: unable to determine local hostname.");
            System.err.println("(abort)");
            System.exit(-1);
        }
    }

    public DupStoreThread(NameValueObjectArchive archive, 
                          double timeTag,
                          boolean isExtendedMetadata,
                          boolean pollIndexed,
                          Queue oids)
    {
        synchronized (DupStoreThread.class) {
            this.objId = objCount++;
        }
        this.archive=archive;
        this.timeTag = timeTag;
        this.isExtendedMetadata = isExtendedMetadata;
        this.pollIndexed = pollIndexed;
        this.oids = oids;
    }

    void pollIndexed(SystemRecord systemRecord, String tag) {

        if (systemRecord.isIndexed())
            return;

        tag += "pollIndexed";

        ObjectIdentifier oid = systemRecord.getObjectIdentifier();

        System.out.println("# Not Indexed: " + oid);
        long t0 = System.currentTimeMillis();
        int retCode = 0;
        while (true) {
            try {
                System.out.println(tag);
                retCode = archive.checkIndexed(oid);
                System.out.println(tag);
                if (retCode != 0)
                    break;
            } catch (Throwable ae) {
                System.out.println("# FAILED index time: " +
                                        (System.currentTimeMillis() - t0));
                System.out.println(tag);
                System.err.println((new Date()) + " " + 
                                   System.currentTimeMillis() +
                                    ": An unexpected checkIndexed error for " +
                                    oid);
                ae.printStackTrace();
            }
            try {
                Thread.sleep(5000);  // Retry each 5 seconds
            } catch (Exception ignore) {}
        }
        System.out.println("# ret " + retCode + 
                                        " index time: " + 
                                        (System.currentTimeMillis() - t0));
    }
    public void run()
    {
        long startTime = System.currentTimeMillis();
        long storeCount = 0;

        StoreChannel storeChannel = new StoreChannel();

        InputOutput line = null;
        while ((line = (InputOutput) oids.pop()) != null) {
            long t0 = 0, t1;
            SystemRecord systemRecord = null;
            Uid uid;
            try {
                uid = new Uid(line.uid);
            }
            catch (Throwable t) {
                System.err.println("uid parse error: " + line.uid);
                t.printStackTrace();
                continue;
            }

            long sizeBytes = line.sizeBytes;
            storeChannel.pattern = uid.channelPattern;
            storeChannel.sizeBytes = sizeBytes;
            storeChannel.seed = uid.generateSeed();
            NameValueRecord metadata = null;
            try {
                metadata = MetadataGenerator.generateMetadata(archive, 
                           isExtendedMetadata, 
                           uid.toString(), sizeBytes, timeTag);
            } catch (Throwable t) {
                System.err.println("Can't generate metadata, quitting: " + t);
                System.exit(1);
            }

            boolean ok = false;
            String tag = null;
            for (int i=0; i<5; i++) {
                try {
                
                    // store a new data object with a System Metadata Record,
                    // if isExtendedMetadata equals true, then it will add 
                    // perf metadata as well
                    storeChannel.reset();
                    tag = "## " + System.currentTimeMillis() + " " + uid +
                          " " + sizeBytes;
                    System.out.println(tag);
                    t0 = System.currentTimeMillis();
                    systemRecord = 
                        archive.storeObject(storeChannel, metadata);
                    t1 = System.currentTimeMillis();
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                  t1,
                                  systemRecord.getObjectIdentifier().toString(),
                                  uid.toString(),
                                  sizeBytes,
                                  InputOutput.STORE,
                                  true);
                    storeCount++;
                    if (pollIndexed) {
                        pollIndexed(systemRecord, tag);
                    }
                    ok = true;
                    break; // all ok
                } catch (Throwable throwable) {
                    // store errors
                    t1 = System.currentTimeMillis();
                    numErrors++;
                    System.err.println(
                        (new Date()) + " " + System.currentTimeMillis() +
                        ": An unexpected error has occured for obj# " +
                        (storeCount+numErrors) + "; size " + sizeBytes +
                        "; uid " + uid.toString() + "; total errors " + 
                        numErrors);
                    throwable.printStackTrace();

                    // log error line to our log
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                      t1,
                                      "unknown_oid",
                                      uid != null?uid.toString():"unknown uid",
                                      sizeBytes,
                                      InputOutput.STORE,
                                      false,true,throwable);

                    // if cluster is full, end test
                    if (throwable.toString().indexOf("CapacityLimitException")
                        != -1) {
                        System.out.println("# CLUSTER FULL, ENDING TEST");
                        System.exit(0);
                    }
                }
            }
            if (!ok) {
                System.err.println("# FAIL size = " + sizeBytes);
                System.out.println("# FAIL size = " + sizeBytes);
            }
        }
    }
}
