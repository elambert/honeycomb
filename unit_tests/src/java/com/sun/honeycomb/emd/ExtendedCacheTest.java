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



package com.sun.honeycomb.emd;

import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.disks.Disk;
import java.io.File;
import com.sun.honeycomb.common.NewObjectIdentifier;
import java.io.IOException;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.remote.MDInputStream;
import com.sun.honeycomb.emd.remote.InMemoryMDStream;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.emd.config.RootNamespace;

public class ExtendedCacheTest {

    /**********************************************************************
     *
     * Static methods / fields
     *
     **********************************************************************/

    private static final int NB_OBJECTS = 0x1000; //4k objects
    private static final int NB_ITERATION = 1000;
    private static final int NB_THREADS = 10;

    private static final String FIELD_INDEX     = "indexField";

    /**********************************************************************
     *
     * Fixture implementation
     *
     **********************************************************************/

    private CacheInterface cache;
    private Disk disk;
    private File rootDirectory;
    private NewObjectIdentifier[] oidCache;
    private Random random;
    
    public ExtendedCacheTest() {
    }
    
    public void setUp() 
        throws EMDException, IOException {

        RootNamespace.getInstance("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                                  "<metadataConfig>"+
                                  "  <schema>"+
                                  "    <namespace name=\"system\" writable=\"false\" extensible=\"false\">"+
                                  "      <field name=\"object_id\" type=\"string\" indexable=\"true\"/>"+
                                  "    </namespace>"+
                                  "    <field name=\""+FIELD_INDEX+"\" type=\"long\" indexable=\"true\"/>"+
                                  "  </schema>"+
                                  "</metadataConfig>");

        cache = CacheManager.getInstance().getServerCache(CacheInterface.EXTENDED_CACHE);
        disk = Disk.getNullDisk(0, 0, 0, 0, 0);
        rootDirectory = File.createTempFile("utests", "", new File("/tmp"));
        rootDirectory.delete();
        rootDirectory.mkdir();
        rootDirectory.deleteOnExit();
        random = new Random(System.currentTimeMillis());
        System.out.println("The test directory is "+rootDirectory.getAbsolutePath());

        cache.start();
        cache.registerDisk(rootDirectory.getAbsolutePath(),
                           disk);
        populate();
        System.out.println("Test is set up");
    }

    public void tearDown() 
        throws EMDException, IOException {
        cache.unregisterDisk(disk);
        cache.stop();
        System.out.println("Deleting "+rootDirectory.getAbsolutePath());
        Process p = Runtime.getRuntime().exec("/bin/rm -fr "+rootDirectory.getAbsolutePath());
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**********************************************************************
     *
     * Utility routines
     *
     **********************************************************************/
    
    private static NewObjectIdentifier getOid() {
        return(new NewObjectIdentifier(0, (byte)0, 0, null));
    }

    public void populate() 
        throws EMDException {
        
        oidCache = new NewObjectIdentifier[NB_OBJECTS];
        long startTime = System.currentTimeMillis();
        PopulateThread[] threads = new PopulateThread[NB_THREADS];
        Throwable[] exceptions = new Throwable[NB_THREADS];

        for (int i=0; i<NB_THREADS; i++) {
            threads[i] = new PopulateThread(cache, disk, 
                                            NB_OBJECTS*i/NB_THREADS,
                                            NB_OBJECTS*(i+1)/NB_THREADS,
                                            oidCache, i, exceptions);
            threads[i].start();
        }

        for (int i=0; i<NB_THREADS; i++) {
            boolean joined = false;
            while (!joined) {
                try {
                    threads[i].join();
                    joined = true;
                } catch (InterruptedException ignored) {
                }
            }

            if (exceptions[i] != null) {
                EMDException newe = new EMDException("Thread "+i+" got an exception");
                newe.initCause(exceptions[i]);
                throw newe;
            }
        }
        
        System.out.println("Adding "+NB_OBJECTS+" objects took "+
                           (System.currentTimeMillis()-startTime)+" ms.");
    }

    /**********************************************************************
     *
     * testLs
     *
     **********************************************************************/
    
    public boolean testPopulate() 
        throws EMDException {
		return(true);
    }

    /**********************************************************************
     *
     * PopulateThread
     *
     **********************************************************************/

    private static class PopulateThread
        extends Thread {

        private CacheInterface cache;
        private Disk disk;
        private int min;
        private int max;
        private NewObjectIdentifier[] oidCache;
        private int position;
        private Throwable[] exceptions;

        private PopulateThread(CacheInterface nCache,
                               Disk nDisk,
                               int nMin,
                               int nMax,
                               NewObjectIdentifier[] nOidCache,
                               int nPosition,
                               Throwable[] nException) {
            cache = nCache;
            disk = nDisk;
            min = nMin;
            max = nMax;
            oidCache = nOidCache;
            position = nPosition;
            exceptions = nException;
        }
        
        public void run() {
            exceptions[position] = null;
            
            try {
                System.out.println("Thread "+getName()+" is starting ["
                                   +min+"-"+max+"]");

                for (int i=min; i<max; i++) {
                    if ((i-min) % 0x20 == 0) {
                        System.out.println("Thread "+getName()+" storing object "+i);
                    }

                    oidCache[i] = getOid();

                    Map attributes = new HashMap();
                    attributes.put(SystemMetadata.FIELD_NAMESPACE+"."+SystemMetadata.FIELD_OBJECTID,
                                   oidCache[i].toString());
                    attributes.put(FIELD_INDEX, Integer.toString(i));
            
                    cache.setMetadata(oidCache[i], attributes, disk);
                }

                System.out.println("Thread "+getName()+" is done");
            } catch (Throwable t) {
                System.out.println("Thread "+getName()+" got an exception ["
                                   +t.getMessage()+"]");
                exceptions[position] = t;
            }
        }
    }
}
