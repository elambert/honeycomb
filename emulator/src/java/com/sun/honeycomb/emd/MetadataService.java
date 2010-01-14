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

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.cm.EmulatedService;
import com.sun.honeycomb.cm.NodeMgr;
import java.io.File;
import com.sun.honeycomb.emd.cache.CacheInterface;
import java.util.logging.Logger;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.common.EMDException;

import java.util.HashMap;
import java.util.Iterator;

public class MetadataService
    implements EmulatedService {

    private static final Logger LOG = Logger.getLogger(MetadataService.class.getName());

    private Disk disk;
    private String mdRoot;
    private boolean running;

    public static MetadataService instance = null;

    public MetadataService() {
        disk = new Disk("Fake disk");
        mdRoot = NodeMgr.getEmulatorRoot()+"/var/metadata";
        File mdFile = new File(mdRoot);
        if (!mdFile.exists()) {
            mdFile.mkdir();
        }
        instance = this;
        running = false;
    }

    public Disk getDisk() {
        return(disk);
    }

    public void run() {
        running = true;
        
        
        //Call the Emulator start-up hook in each externalHook
        // (This causes HADB to create its tables.)
        HashMap hooks = CacheManager.getInstance().getExternalHooks();
        for (Iterator ite = hooks.values().iterator(); ite.hasNext();) {
            MetadataInterface hook = (MetadataInterface) ite.next();
            if (hook != null) {
                try {
                    hook.inithook();
                } catch (EMDException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        CacheInterface[] caches = CacheManager.getInstance().getServerCaches();
        for (int i=0; i<caches.length; i++) {
            try {
                caches[i].start();
                caches[i].registerDisk(mdRoot, disk);
                LOG.info("Metadata has been started");
            } catch (EMDException e) {
                throw new RuntimeException(e);
            }
        }

        while (running == true) {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
    
    public String getName() {
        return("Metadata");
    }
    
    public void shutdown() {
        CacheInterface[] caches = CacheManager.getInstance().getServerCaches();
        for (int i=0; i<caches.length; i++) {
            try {
                caches[i].unregisterDisk(disk);
                caches[i].stop();
                LOG.info("Metadata has been shutdown");
            } catch (EMDException e) {
                RuntimeException newe = new RuntimeException(e.getMessage());
                newe.initCause(e);
                throw newe;
            }
        }

        Derby.getInstance().stop();

        running = false;
        synchronized (this) {
            notify();
        }
    }
}
