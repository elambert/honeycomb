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

import java.util.Random;
import java.util.Date;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;

public class RetrieveThread extends Thread
{
    private NameValueObjectArchive archive;
    private Queue oids;
    private int pattern;
    boolean doContentVerification;
    private long sleepTime = 0; // msecs
    private long numErrors = 0;
    
    public RetrieveThread(NameValueObjectArchive archive,
                          Queue oids,
                          boolean doContentVerification)
    {
        this.archive=archive;
        this.oids=oids;
        this.pattern=pattern;
        this.doContentVerification=doContentVerification;
    }

    public void run()
    {
        RetrieveChannel retrieveChannel = new RetrieveChannel();
        retrieveChannel.doContentVerification = doContentVerification;

        long sizeBytes = 0;
        ObjectIdentifier oid = null;
        //String uid = "unknown_uid";
        Uid uid = null;
        
        InputOutput line = null;
        while ((line = (InputOutput) oids.pop()) != null) {
            long t0 = 0, t1;
            String tag = null;
            oid = new ObjectIdentifier(line.oid);
            boolean ok = false;
            for (int i=0; i<5; i++) {
                
                try {
                    sizeBytes = 0;
                    t0 = System.currentTimeMillis();
                    tag = "## md " + t0 + " " + oid;
                    System.out.println(tag);
                    NameValueRecord metadata = archive.retrieveMetadata(oid);
                    System.out.println(tag);
                    sizeBytes = metadata.getLong("system.test.type_long");
                    uid = new Uid(metadata.getString("system.test.type_string"));
                    retrieveChannel.sizeBytes = sizeBytes;
                    retrieveChannel.pattern = uid.channelPattern;
                    retrieveChannel.seed = uid.generateSeed();
                    retrieveChannel.reset();
                    tag = "## " + System.currentTimeMillis() + " " + oid;
                    System.out.println(tag);
                    t0 = System.currentTimeMillis();
                    long bytesRetrieved = 
                                archive.retrieveObject(oid, retrieveChannel);
                    t1 = System.currentTimeMillis();
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                          t1,
                                          oid.toString(),
                                          uid.toString(),
                                          sizeBytes,
                                          InputOutput.RETRIEVE,
                                          bytesRetrieved == sizeBytes);
                    if (bytesRetrieved != sizeBytes) {
                        System.err.println(oid.toString() + " ERR sizeBytes(" +
                             sizeBytes + ") != bytesRetrieved(" + 
                             bytesRetrieved + ")");
                    }
                
                    Thread.sleep(sleepTime);

                    ok = true;
                    break; // ok
                } catch (Throwable throwable) {
                    t1 = System.currentTimeMillis();
                    System.err.println(
                        (new Date()) + " " + System.currentTimeMillis() +
                        ": An unexpected error has occured on line " +
                        line + "; total errors " + ++numErrors);
                    throwable.printStackTrace();
                
                    // log line about the error to error file
                    System.out.println(tag);
                    InputOutput.printLine(t0,
                                          t1,
                                          oid != null?oid.toString():line.oid,
                                          uid != null?uid.toString():line.uid,
                                          sizeBytes,
                                          InputOutput.RETRIEVE,
                                          false, true,throwable);
                }
            }
            if (!ok) {
                System.err.println("# FAIL " + oid);
                System.out.println("# FAIL " + oid);
            }
        }
    }
}
