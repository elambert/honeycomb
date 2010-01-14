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
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;

public class DeleterThread extends Thread
{
    private NameValueObjectArchive archive;
    private long minSizeBytes;
    private long maxSizeBytes;
    private long runtimeMillis;
    private long numOps; // max ops to do
    private long numErrors = 0;
    private boolean isExtendedMetadata;
    private long sleepMillis;
    
    private double timeTag;

    private Random randSize;
    private Random randSeed;

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

    public DeleterThread(NameValueObjectArchive archive, double timeTag,
                       long minSizeBytes, 
                       long maxSizeBytes,
                       long runtimeMillis,
                       long numOps,
                       boolean isExtendedMetadata,
                       long sleepMillis)
    {
        synchronized (DeleterThread.class) {
            this.objId = objCount++;
        }
        this.archive=archive;
        this.timeTag = timeTag;
        this.minSizeBytes=minSizeBytes;
        this.maxSizeBytes=maxSizeBytes;
        this.runtimeMillis=runtimeMillis;
        this.numOps=numOps;
        this.isExtendedMetadata = isExtendedMetadata;
        this.sleepMillis=sleepMillis;
    }

    public void run()
    {
        long startTime = System.currentTimeMillis();
        long deleteCount = 0;
        long sizeBytes = 0;
        long seed = 0;

        StoreChannel storeChannel = new StoreChannel();

        Uid uid = new Uid();

        randSize = new Random(startTime + (long) this.hashCode());
          
        // do stores until time expires, or numOps stores are done
        while ((runtimeMillis < 0 || 
                runtimeMillis > (System.currentTimeMillis() - startTime)) &&
               (numOps < 0 || numOps > (deleteCount+numErrors))) 
        {
            sizeBytes = (maxSizeBytes == minSizeBytes) ? minSizeBytes :
                        (minSizeBytes + (Math.abs(randSize.nextLong()) % 
                        (maxSizeBytes - minSizeBytes)));
            storeChannel.sizeBytes = sizeBytes;
            // store a new data object with a System Metadata Record,
            // if isExtendedMetadata equals true, then it will add 
            // perf metadata as well
            NameValueRecord metadata = null;
            try {
                metadata = MetadataGenerator.generateMetadata(archive, 
                        isExtendedMetadata, 
                        uid.toString(), sizeBytes, timeTag);
            } catch (Throwable t) {
                System.err.println("MetadataGenerator.generateMetadata: " + t);
                System.exit(1);
            }

            long t0 = 0, t1;
            String tag = null;
            boolean ok = false;
            ObjectIdentifier oid = null;
            for (int i=0; i<5; i++) {
                try {
                
                    tag = "## " + System.currentTimeMillis() + " " + uid;
                    System.out.println(tag);
                    t0 = System.currentTimeMillis();
                    seed = t0;
                    storeChannel.reset();
                    SystemRecord systemRecord = 
                        archive.storeObject(storeChannel, metadata);
                    System.out.println(tag);
                    oid = systemRecord.getObjectIdentifier();
                    ok = true;
                    break;
                } catch (Throwable throwable) {
                    t1 = System.currentTimeMillis();
                    numErrors++;
                    System.err.println(
                        (new Date()) + " " + System.currentTimeMillis() +
                        ": An unexpected error has occured for obj# " +
                        (deleteCount+numErrors) + "; size " + sizeBytes +
                        "; uid " + uid + "; total errors " + numErrors);
                    throwable.printStackTrace();

                    // log error line to our log
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                          t1,
                                          "unknown_oid",
                                          uid.toString(),
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
                System.err.println("# FAIL store size = " + sizeBytes);
                System.out.println("# FAIL store size = " + sizeBytes);
                continue; // try another size
            }
            ok = false;
            for (int i=0; i<5; i++) {
                try {
                    tag = "## " + System.currentTimeMillis() + " " + oid;
                    System.out.println(tag);
                    archive.delete(oid);
                    t1 = System.currentTimeMillis();
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                          t1,
                                          oid.toString(),
                                          uid.toString(),
                                          sizeBytes,
                                          InputOutput.DELETE,
                                          true);
                    deleteCount++;
                    ok = true;
                    break;
                } catch (Throwable throwable) {
                    t1 = System.currentTimeMillis();
                    numErrors++;
                    System.err.println(
                        (new Date()) + " " + System.currentTimeMillis() +
                        ": An unexpected error has occured for obj# " +
                        (deleteCount+numErrors) + "; size " + sizeBytes +
                        "; uid " + uid + "; total errors " + numErrors);
                    throwable.printStackTrace();

                    // log error line to our log
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                          t1,
                                          oid.toString(),
                                          uid.toString(),
                                          sizeBytes,
                                          InputOutput.DELETE,
                                          false,true,throwable);
                }
            }
            if (!ok) {
                System.err.println("FAIL del " + oid);
                System.out.println("FAIL del " + oid);
            }
            if (sleepMillis != 0) {
                try { sleep(sleepMillis); } catch (Exception ignore) {}
            }
            uid.next();
        }
    }
}
