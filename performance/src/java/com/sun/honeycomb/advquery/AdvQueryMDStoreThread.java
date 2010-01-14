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



import java.net.InetAddress;
import java.util.Random;
import java.util.ArrayList;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.test.util.HoneycombTestException;

/**
 * Thread for creating objects and metadata that are stored in honeycomb.
 * Note: Each thread has it's own random # generator for metadata and it's
 * own seed. Each thread also writes the object stored and it's metadata to
 * the log file.
 *
 */
public class AdvQueryMDStoreThread extends Thread {
    private NameValueObjectArchive archive;
    private long minSizeBytes;
    private long maxSizeBytes;
    private int bytePatternSize;
    private long runtimeMillis;
    // Pause in milliseconds between stores. Zero means no pause
    private long pauseMillis;
    private long numErrors = 0;
    
    private Random randSize;
    private AdvQueryRandomUtil prng = null;
    private AdvQueryRandomUtil dataPRNG = null;
    
    private boolean withMD;
    private AdvQueryMDPatternElement[] userMetadataPatterns = null;
    private long storeCount = 0;
    private double serviceTime = 0;
    
    /**
     * Creates thread instance for creating and loading randomly generated
     * objects and metadata to honeycomb.
     */
    public AdvQueryMDStoreThread(NameValueObjectArchive archive,
            long minSizeBytes,
            long maxSizeBytes,
            int bytePatternSize,
            long runtimeMillis,
            boolean withMD,
            AdvQueryMDPatternElement[] mdList,
            byte[] seed,
            AdvQueryRandomUtil dataPRNG) throws HoneycombTestException {
        this.archive=archive;
        this.minSizeBytes=minSizeBytes;
        this.maxSizeBytes=maxSizeBytes;
        this.bytePatternSize = bytePatternSize;
        this.runtimeMillis=runtimeMillis;
        this.withMD = withMD;
        this.dataPRNG = dataPRNG;
        if (mdList != null & mdList.length > 0) {
            userMetadataPatterns = new AdvQueryMDPatternElement[mdList.length];
            for (int i = 0; i < mdList.length; i++) {
                userMetadataPatterns[i] =
                        new AdvQueryMDPatternElement(mdList[i]);
            }
        }
        prng = new AdvQueryRandomUtil(seed);
    }

   /**
     * Creates thread instance for creating and loading randomly generated
     * objects and metadata to honeycomb.
     */
    public AdvQueryMDStoreThread(NameValueObjectArchive archive,
            long minSizeBytes,
            long maxSizeBytes,
            int bytePatternSize,
            long runtimeMillis,
            boolean withMD,
            AdvQueryMDPatternElement[] mdList,
            byte[] seed,
            AdvQueryRandomUtil dataPRNG,
            long pause) throws HoneycombTestException {
        
        this(archive, minSizeBytes, maxSizeBytes, bytePatternSize,
                runtimeMillis, withMD, mdList, seed, dataPRNG);
        this.pauseMillis = pause;
    }
    
    /**
     * Gets the number of objects this thread has created and stored.
     */
    public long getStoreCount() {
        return storeCount;
    }
    
    /**
     * Gets the number of errors encountered by this thread during store
     * operations.
     */
    public long getNumErrors() {
        return numErrors;
    }
    
    /**
     *  Gets the amount of time actually spent servicing the store request.
     */
    public double getServiceTIme() {
        return serviceTime;
    }
    
    /**
     *  Runs a thread to randomly generate objects and metadata and store
     *  them in honeycomb.
     */
    public void run() {
        long startTime = System.currentTimeMillis();
        AdvQueryStoreChannel storeChannel;
        
        randSize = new Random(startTime + (long) this.hashCode());
        
        long cnt =0;
        boolean checkPause = false; 
        while (runtimeMillis < 0 ||
                runtimeMillis > (System.currentTimeMillis() - startTime)) {
            try {                
                if (pauseMillis > 0 && checkPause) {
                     try {
                         System.out.println("Pausing thread[" + this.getName() +
                              "] at " + System.currentTimeMillis());
                         sleep(pauseMillis);
                      } catch (InterruptedException ex) {
                         // do nothing;
                      }
                }

               checkPause = true;

               long sizeBytes =
                        (maxSizeBytes == minSizeBytes) ?
                            minSizeBytes :
                            (minSizeBytes + (Math.abs(randSize.nextLong()) % 
                                (maxSizeBytes - minSizeBytes)));
                long t0, t1;
                storeChannel = new AdvQueryStoreChannel(
                        dataPRNG, sizeBytes, bytePatternSize);
                SystemRecord systemRecord;
                String uid = null;
                t0 = System.currentTimeMillis();
                t1 = System.currentTimeMillis();
                                
                String mdString = "//";
                if (withMD) {
                    NameValueRecord metadata = archive.createRecord();
                    AdvQueryManageMetadata generatedMD = 
                            AdvQueryManageMetadata.addMetadata(
                            metadata, prng, dataPRNG,
                            userMetadataPatterns, startTime, storeCount);
                    uid = generatedMD.getUID();
                    mdString = AdvQueryManageMetadata.toString(
                            metadata, userMetadataPatterns);                  
                    
                    t0 = System.currentTimeMillis();
                    systemRecord = archive.storeObject(storeChannel, metadata);
                    t1 = System.currentTimeMillis();
                } else {
                    t0 = System.currentTimeMillis();
                    systemRecord = archive.storeObject(storeChannel);
                    t1 = System.currentTimeMillis();
                }
                
                // Write each object & it's metadata to the output log
                AdvQueryInputOutput.printLine(Thread.currentThread().getName(),
                        t0,
                        t1,
                        mdString,
                        systemRecord.getObjectIdentifier().toString(),
                        uid,
                        sizeBytes,
                        AdvQueryInputOutput.STORE,
                        AdvQueryManageMetadata.getHostname(),
                        true);
                
                storeCount++;
                serviceTime += t1 - t0;
            } catch (Throwable throwable) {
                System.err.println("Thread: " +
                        Thread.currentThread().getName() +
                        "   An unexpected error has occured; total errors " +
                        ++numErrors);
                try {
                    // The IllegalFormatException subclasses often throw
                    // another exception when try to print the stack trace.
                    // So put this in a try-catch and print an extra
                    // newline char jut in case.
                    throwable.printStackTrace(System.err);
                } catch (Throwable thr) {
                    System.err.println("\n");
                }
                
            }
        }   // end while
    }
    /*
     *  Query for the record just stored
     */
    private boolean queryObject(AdvQueryManageMetadata metadata) {
        boolean found = false;
        
        return found;
        
    }
}
