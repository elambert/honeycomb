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



package com.sun.honeycomb.emd.cache;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.MetadataInterface;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.jar.JarFile;
import java.io.FilenameFilter;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Map;

public class CacheManager 
    implements FilenameFilter {

    /*
     * Static fields and methods
     */
    
    private static final String CACHE_PATH = "/opt/honeycomb/lib/md_caches";
    
	private static final String CACHE_PATH_PROPERTY = "md.cache.path";
    private static final String CACHE_MANIFEST_PROPERTY = "Cache-Class";
    
    private static final Logger LOG = Logger.getLogger(CacheManager.class.getName());
    private static CacheManager singleton = null;
    
    public static synchronized CacheManager getInstance() {
        if (singleton != null) {
            return(singleton);
        }
        
        try {
            singleton = new CacheManager();
            singleton.init();
        } catch (EMDException e) {
            LOG.log(Level.SEVERE,
                    "Failed to initialize the Cache Manager",
                    e);
            singleton = null;
        }

        return(singleton);
    }

    /*
     * CacheManager class
     */

    private CacheClassLoader classLoader;

    private HashMap clientInterfaces;
    private HashMap serverCaches;
    private HashMap externalHooks;

    private CacheManager() {
        classLoader = null;
        serverCaches = null;
        clientInterfaces = null;
        externalHooks = null;
    }
    
    public boolean accept(File dir,
                          String name) {
        return(name.endsWith(".jar"));
    }
    
    private void init()
        throws EMDException {
        
        // Get all the available jar files.
		String cachePath = System.getProperty(CACHE_PATH_PROPERTY);
		if (cachePath == null) {
			cachePath = CACHE_PATH;
		} else {
			LOG.warning("The metadata cache path is ["+cachePath+"]");
		}

        File cacheDirectory = new File(cachePath);
        File[] jars = cacheDirectory.listFiles(this);
        JarFile[] jarFiles = new JarFile[jars.length];
        ArrayList cacheList = new ArrayList();
        Attributes.Name attributeName = new Attributes.Name(CACHE_MANIFEST_PROPERTY);

        try {

            for (int i=0; i<jars.length; i++) {
                jarFiles[i] = new JarFile(jars[i]);

                LOG.info("The "+jars[i].getName()+" jar file has been loaded");
            
                // Check if the jar is a cache (look for the CACHE_MANIFEST_PROPERTY
                // in the Manifest file)
            
                Attributes attributes = jarFiles[i].getManifest().getMainAttributes();

                if (attributes.containsKey(attributeName)) {
                    cacheList.add((String)attributes.get(attributeName).toString());
                }
            }
        
            // Create the class Loader
            classLoader = new CacheClassLoader(jarFiles);

            // Load the caches
            clientInterfaces = new HashMap();
            serverCaches = new HashMap();
            externalHooks = new HashMap();
            
            for (int i=0; i<cacheList.size(); i++) {
                String cacheName = (String)cacheList.get(i);

                try {
                    Object cache = classLoader.loadClass(cacheName).newInstance();

                    if (cache instanceof CacheClientInterface) {
                        String cacheId = ((CacheClientInterface)cache).getCacheId();
                        Object existing = clientInterfaces.get(cacheId);
                        if (existing == null) {
                            clientInterfaces.put(cacheId, cache);
                        } else {
                            if (cache instanceof MetadataInterface) {
                                // Overwrite the existing one
                                clientInterfaces.put(cacheId, cache);
                            }
                        }
                    }
                    if (cache instanceof CacheInterface) {
                        serverCaches.put(((CacheInterface)cache).getCacheId(), cache);
                    }
                    if (cache instanceof MetadataInterface) {
                        externalHooks.put(((MetadataInterface)cache).getCacheId(), cache);
                    }
                    
                } catch (ClassNotFoundException e) {
                    LOG.severe("Couldn't find class "+cacheName+". Ignoring this cache");
                } catch (InstantiationException e) {
                    LOG.severe("Couldn't instanciate class "+cacheName+". Ignoring this cache");
                } catch (IllegalAccessException e) {
                    LOG.severe("Couldn't access class "+cacheName+". Ignoring this cache");
                }
            }

        } catch (IOException e) {
            EMDException newe = new EMDException("Failed to init the CacheManager");
            newe.initCause(e);
            throw newe;
        }

        log("Client interfaces", clientInterfaces);
        log("Server caches", serverCaches);
        log("External Hooks", externalHooks);
    }

    private void log(String label,
                     Map map) {
        if (map.size() == 0) {
            LOG.info("No "+label+" have been loaded");
        }
        int index = 0;
        Iterator ite = map.keySet().iterator();
        while (ite.hasNext()) {
            index++;
            String id = (String)ite.next();
            String className = map.get(id).getClass().getName();
            LOG.info(label+" ["+index+"] : ["+id+"] is served by ["+className+"]");
        }
    }

    public CacheInterface getServerCache(String name) 
        throws EMDException {
        CacheInterface result = (CacheInterface)serverCaches.get(name);
        if (result == null) {
            throw new EMDException("The Metadata server cache ["+
                                   name+"] is unknown");
        }

        return(result);
    }
    
    public CacheClientInterface getClientInterface(String name) 
        throws EMDException {
        CacheClientInterface result = (CacheClientInterface)clientInterfaces.get(name);
        if (result == null) {
            throw new EMDException("The Metadata client Interface ["+
                                   name+"] is unknown");
        }
        
        return(result);
    }
    
    public HashMap getExternalHooks() {
        return(externalHooks);
    }
    
    public CacheInterface[] getServerCaches() {
        StringBuffer log = null;
        
        if (LOG.isLoggable(Level.FINE)) {
            log = new StringBuffer();
            log.append("The CacheManager has the following caches :");
        }
        Collection values = serverCaches.values();
        CacheInterface[] result = new CacheInterface[values.size()];
        Iterator ite = values.iterator();
        
        for (int i=0; ite.hasNext(); i++) {
            result[i] = (CacheInterface)ite.next();
            if (LOG.isLoggable(Level.FINE)) {
                log.append(" "+result[i].getCacheId());
            }
        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(log.toString());
        }

        return(result);
    }
}
