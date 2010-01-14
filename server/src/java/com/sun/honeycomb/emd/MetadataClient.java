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

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.StringList;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.emd.remote.ConnectionFactory;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.emd.remote.ConnectionFactory.DiskConnection;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDDiskAbstraction;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.disks.Disk;
import java.io.DataOutput;
import java.io.IOException;
import com.sun.honeycomb.emd.common.MDHit;
import java.util.HashMap;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.cache.MDHeader;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.config.ClusterProperties;

public class MetadataClient {
    
    public static final int DEFAULT_TIMEOUT = 300; // in s. = 5 min
    
    // Logger
    protected static final Logger LOG = 
        Logger.getLogger(MetadataClient.class.getName());

    /****************************************
     *
     * singleton methods
     *
     ****************************************/
    
    private static MetadataClient singleton = null;
    
    public static synchronized MetadataClient getInstance() {
        if (singleton == null) {
            singleton = new MetadataClient();
        }
        return(singleton);
    }
    
    /**********************************************************************
     *
     * QueryResult class
     *
     **********************************************************************/

    public static class QueryResult {
        public Cookie cookie;
        public ArrayList results;
        public long queryIntegrityTime; // msec since epoch
        
        public QueryResult() {
            cookie = null;
            results = null;
            queryIntegrityTime = 0;
        }

        /**
         * Used by QueryHandler to return the results to the client lib
         */
        public int serializeOids(DataOutput dataOut) throws IOException {
	    
            MDHit hit;

            if (results != null) {
                for (int i=0; i<results.size(); i++) {
                    hit = (MDHit)results.get(i);

                    NewObjectIdentifier oid = hit.constructOid();
                    oid.serializeExternal(dataOut);
                }
                return results.size();
            } else {
                return 0;
            }
        } // serializeOids
    } // QueryResult class 
    
    /**********************************************************************
     *
     * SelectUniqueResult class
     *
     **********************************************************************/

    public static class SelectUniqueResult {
        public Cookie cookie;
        public StringList results;
        
        public SelectUniqueResult() {
            cookie = null;
            results = null;
        }
        
        public void serialize(DataOutput dataOut) throws IOException {
            if (cookie != null) {
                byte[] bytes = cookie.getBytes();
                dataOut.writeInt(bytes.length);
                dataOut.write(bytes);
            } else {
                dataOut.writeInt(0);
            }

            if (results != null) {
                dataOut.writeBoolean(true);
                results.serialize(dataOut);
            } else {
                dataOut.writeBoolean(false);
            }
        }
    }
    
    /**********************************************************************
     *
     * MetadataClient class
     *
     **********************************************************************/
    
    private HashMap externalHooks;
    private EMDClient defaultClient;

    private MetadataClient() {
        externalHooks = CacheManager.getInstance().getExternalHooks();
        if (externalHooks.size() == 0) {
            externalHooks = null;
        }
        defaultClient = new EMDClient();
    }

    /**********************************************************************
     *
     * Query APIs
     *
     **********************************************************************/

    public QueryResult queryPlus(String cacheId,
                                 String query,
                                 ArrayList attributes,
                                 Cookie _cookie,
                                 int maxResults,
                                 int timeout,
                                 boolean forceResults,
                                 Object[] boundParameters)
        throws EMDException {
        return(queryPlus(cacheId, query, attributes, _cookie, maxResults,
                         timeout, forceResults, false, boundParameters, null));
    }
    
    public QueryResult queryPlus(String cacheId,
                                 String query,
                                 ArrayList attributes,
                                 Cookie _cookie,
                                 int maxResults,
                                 int timeout,
                                 boolean forceResults,
                                 boolean abortOnFailure,
                                 Object[] boundParameters)
        throws EMDException {
        return(queryPlus(cacheId, query, attributes, _cookie, maxResults,
                         timeout, forceResults, abortOnFailure, boundParameters, null));
    }

    public QueryResult queryPlus(String cacheId,
                                 String query,
                                 ArrayList attributes,
                                 Cookie _cookie,
                                 int maxResults,
                                 int timeout,
                                 boolean forceResults)
        throws EMDException {
        return(queryPlus(cacheId, query, attributes, _cookie, maxResults,
                         timeout, forceResults, null));
    }

    public QueryResult queryPlus(String cacheId,
                                 String query,
                                 ArrayList attributes,
                                 Cookie cookie,
                                 int maxResults) 
        throws ArchiveException {
        return(queryPlus(cacheId, query, attributes, cookie, maxResults,
                         DEFAULT_TIMEOUT, false));
    }

    public QueryResult query(String cacheId,
            String query,
            Cookie _cookie,
            int maxResults,
            int timeout,
            boolean forceResults,
            Object[] boundParameters)
        throws EMDException {
        return query (cacheId, query, _cookie, maxResults, timeout, 
                        forceResults, false, boundParameters);
    }
    
    public QueryResult query(String cacheId,
                             String query,
                             Cookie _cookie,
                             int maxResults,
                             int timeout,
                             boolean forceResults,
                             boolean abortOnFailure,
                             Object[] boundParameters) 
        throws EMDException {
        QueryResult result = queryPlus(cacheId, query, null, 
                                   _cookie, maxResults, timeout, forceResults, 
                                   abortOnFailure, boundParameters);
        return(result);
    }

    public QueryResult query(String cacheId,
            String query,
            Cookie _cookie,
            int maxResults,
            int timeout,
            boolean forceResults) 
        throws EMDException {
        return query (cacheId, query, _cookie, maxResults, 
                        timeout, forceResults, false);  
        
    }
    public QueryResult query(String cacheId,
                             String query,
                             Cookie _cookie,
                             int maxResults,
                             int timeout,
                             boolean forceResults,
                             boolean abortOnFailure) 
        throws EMDException {
        QueryResult result = query(cacheId, query,
                                   _cookie, maxResults, timeout, forceResults, 
                                   abortOnFailure, null);
        return(result);
    }

    public QueryResult query(String cacheId,
                             String query,
                             Cookie cookie,
                             int maxResults,
                             boolean abortOnFailure)
        throws ArchiveException {
        return(query(cacheId, query, cookie, maxResults, DEFAULT_TIMEOUT, false, abortOnFailure));
    }
    
    public QueryResult query(String cacheId,
                             String query,
                             Cookie cookie,
                             int maxResults) 
        throws ArchiveException {
        return(query(cacheId, query, cookie, maxResults, DEFAULT_TIMEOUT, false));
    }
    public QueryResult query(String cacheId,
                             String query,
                             Cookie cookie,
                             int maxResults,
                             Object[] boundParameters) 
        throws ArchiveException {
        return(query(cacheId, query, cookie, maxResults, DEFAULT_TIMEOUT, false, 
                     boundParameters));
    }

    public QueryResult query(String cacheId,
                             String query,
                             int maxResults) 
        throws ArchiveException {
        return(query(cacheId, query, maxResults, DEFAULT_TIMEOUT));
    }

    public QueryResult query(String cacheId,
                             String query,
                             int maxResults,
                             int timeout)
        throws ArchiveException {
        return query(cacheId, query, null, maxResults, timeout, true);
    }

    public QueryResult query(String cacheId,
                             String query,
                             int maxResults,
                             Disk disk)
        throws ArchiveException {
        return query(cacheId,query,null,null,maxResults,disk);
    } 
    
    public QueryResult query(String cacheId,
                             String query,
                             ArrayList attributes,
                             int maxResults,
                             Disk disk)
        throws ArchiveException {
        return query(cacheId,query,attributes,null,maxResults,disk);
    }
 
    public QueryResult query(String cacheId,
                             String query,
                             Cookie cookie,
                             int maxResults,
                             Disk disk)
        throws ArchiveException {
        return query(cacheId,query,null,cookie,maxResults,disk);
    }
    
    public QueryResult query(String cacheId,
                             String query,
                             ArrayList attributes,
                             Cookie cookie,  
                             int maxResults,
                             Disk disk)
        throws ArchiveException {
        return getClient(cacheId).queryPlus(cacheId, 
                                            query, 
                                            attributes,
                                            cookie,
                                            maxResults, 
                                            -1,
                                            false,
                                            null,
                                            null,
                                            disk);
    }

    /**********************************************************************
     *
     * SelectUnique APIs
     *
     **********************************************************************/

    public SelectUniqueResult selectUnique(String cacheId,
                                           String query,
                                           String attribute,
                                           Cookie _cookie,
                                           int maxResults,
                                           int timeout,
                                           boolean forceResults,
                                           Object[] boundParameters) 
        throws EMDException {
        return(selectUnique(cacheId, query, attribute, _cookie, maxResults,
                            timeout, forceResults, boundParameters, null));
    }

    public SelectUniqueResult selectUnique(String cacheId,
                                           String query,
                                           String attribute,
                                           Cookie _cookie,
                                           int maxResults,
                                           int timeout,
                                           boolean forceResults) 
        throws EMDException {
        return(selectUnique(cacheId, query, attribute, _cookie, maxResults,
                            timeout, forceResults, null));
    }
    
    public SelectUniqueResult selectUnique(String cacheId,
                                           String query,
                                           String attribute,
                                           int maxResults,
                                           int timeout) 
        throws ArchiveException {
        return(selectUnique(cacheId, query, attribute, null, maxResults, timeout, true));
    }

    public SelectUniqueResult selectUnique(String cacheId,
                                           String query,
                                           String attribute,
                                           int maxResults) 
        throws ArchiveException {
        return selectUnique(cacheId, query, attribute, maxResults, DEFAULT_TIMEOUT);
    }

    /**********************************************************************
     *
     * Implementation that considers potential external hooks
     *
     **********************************************************************/

    private MetadataInterface getClient(String cacheId) {
        if (externalHooks == null) {
            return(defaultClient);
        }
        MetadataInterface result = (MetadataInterface)externalHooks.get(cacheId);
        if (result == null) {
            return(defaultClient);
        } else {
            return(result);
        }
    }
    
    public MetadataClient.QueryResult queryPlus(String cacheId,
            String query,
            ArrayList attributes,
            Cookie _cookie,
            int maxResults,
            int timeout,
            boolean forceResults,
            Object[] boundParameters,
            MDOutputStream outputStream) 
        throws EMDException {
        return( queryPlus(cacheId,
                query,
                attributes,
                _cookie,
                maxResults,
                timeout,
                forceResults,
                false,
                boundParameters,
                outputStream) );
    }
    
    public MetadataClient.QueryResult queryPlus(String cacheId,
                                                String query,
                                                ArrayList attributes,
                                                Cookie _cookie,
                                                int maxResults,
                                                int timeout,
                                                boolean forceResults,
                                                boolean abortOnFailure,
                                                Object[] boundParameters,
                                                MDOutputStream outputStream) 
        throws EMDException {
        return( getClient(cacheId).queryPlus(cacheId,
                                             query,
                                             attributes,
                                             _cookie,
                                             maxResults,
                                             timeout,
                                             forceResults,
                                             abortOnFailure,
                                             boundParameters,
                                             outputStream) );
    }

    public Cookie querySeek(String query, int index, Object[] boundParameters, ArrayList attributes) {
        return(defaultClient.querySeek(query, index, boundParameters, attributes));
    }
    
    public MetadataClient.SelectUniqueResult selectUnique(String cacheId,
                                                          String query,
                                                          String attribute,
                                                          Cookie _cookie,
                                                          int maxResults,
                                                          int timeout,
                                                          boolean forceResults,
                                                          Object[] boundParameters,
                                                          MDOutputStream outputStream) 
        throws EMDException {
        return( getClient(cacheId).selectUnique(cacheId,
                                                query,
                                                attribute,
                                                _cookie,
                                                maxResults,
                                                timeout,
                                                forceResults,
                                                boundParameters,
                                                outputStream) );
    }
    
    public Cookie selectUniqueSeek(String query,
                                   String attribute,
                                   int index,
                                   Object[] boundParameters) {
        return(defaultClient.selectUniqueSeek(query, attribute, index, boundParameters));
    }


    /**
     * Retrieve the correct value from the system cache. By correct we mean that
     * this is the most upto date system cache record for this object. The reason
     * why there can be records that are not upto date is because on operations
     * of addMetadata and delete we can skip some fragments and those would be 
     * repopulated with the wrong value for sometime before DD could fix them.
     * By having redundancy at the system cache level we know that we have at
     * least 1 record that is the most upto date one.
     * 
     * @param cacheId
     * @param oid
     * @return
     * @throws EMDException
     */
    public SystemMetadata retrieveMetadata(String cacheId,
                                           NewObjectIdentifier oid) 
           throws EMDException {

        // TODO: if we find it usefull we can retrieve a given metadata record
        // from the extended cache but for now only going to handle the
        // the system cache cache and throw an exception in anyother cases.
        if (!cacheId.equals(CacheClientInterface.SYSTEM_CACHE))
            throw new EMDException(
                    "only system cache is supported for retrieve metadata");


        ArrayList hits = new ArrayList();
        
        ArrayList disks = MDDiskAbstraction.getInstance((byte)0)
                          .getUsedDisksFromMapId(oid.getLayoutMapId(), cacheId);
   
        for (int i = 0; i < disks.size(); i++) { 
	        try {
	            QueryResult some = query(CacheClientInterface.SYSTEM_CACHE,
	                                     SystemCacheConstants.SYSTEM_QUERY_CHECKOID 
                                         + " " + oid.toExternalHexString() + 
                                         " false ", new ArrayList(), 1, 
                                         (Disk)disks.get(i));
                hits.addAll(some.results);
	        } catch (ArchiveException ignore) { }
        }
       
        /* 
         * Now we have to pick the most upto date 
         */
        if (hits.size() >= 1) {
            MDHit hit = (MDHit)hits.get(0);
            
            if (!(hit.getExtraInfo() instanceof SystemMetadata))
                throw new RuntimeException("ExtraInfo can only be an instance of SystemMetadata");
            
            SystemMetadata bestOne = (SystemMetadata)hit.getExtraInfo();
            
            for (int i = 1 ; i < hits.size(); i++ ) {
                
                if (!(((MDHit)hits.get(i)).getExtraInfo() instanceof SystemMetadata))
                    throw new RuntimeException("ExtraInfo can only be an instance of SystemMetadata");
            
                SystemMetadata aux = (SystemMetadata)((MDHit)hits.get(i)).getExtraInfo();
              
                if (aux.getDTime() > bestOne.getDTime()) 
                    bestOne = aux;
                else if (aux.getMaxRefcount() > bestOne.getMaxRefcount()) 
                    bestOne = aux;
            }
            
            return bestOne;
        } else {
            throw new EMDException("System record not found for "
                    + oid.toHexString());
        }
    }  
    
    public boolean setMetadata(String cacheId,
                            NewObjectIdentifier oid,
                            Object argument)
    {
        return getClient(cacheId).setMetadata(cacheId,
                                       oid,
                                       argument);
    }

    public boolean setMetadata(String cacheId,
			       Map items)
    {
        return getClient(cacheId).setMetadata(cacheId,
					      items);
    }
    
    public void setMetadata(SystemMetadata systemMD,
                            byte[] MDField,
                            Disk disk) {
        try {
            getClient(new MDHeader(MDField).getCacheId()).setMetadata(systemMD, MDField, disk);
        } catch (IOException ignore) {
        }
    }
    
    public void removeMetadata(NewObjectIdentifier oid,
                               byte[] MDField,
                               Disk disk) 
        throws EMDException {
        defaultClient.removeMetadata(oid, MDField, disk);
    }
    
    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId,
                               Disk disk) 
        throws EMDException {
        defaultClient.removeMetadata(oid,cacheId,disk);
    }
    
    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId) 
        throws EMDException {
        getClient(cacheId).removeMetadata(oid, cacheId);
    }

    public void addLegalHold(NewObjectIdentifier oid,
                            String legalHold,
                            Disk disk) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Adding (" + oid + ", " + legalHold + ") to disk " + disk);
        }
        defaultClient.addLegalHold(oid, legalHold, disk);
    }

    public void addLegalHold(NewObjectIdentifier oid,
                            String legalHold) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Adding (" + oid + ", " + legalHold + ")");
        }
        getClient(CacheClientInterface.SYSTEM_CACHE).
            addLegalHold(oid, legalHold);
    }

    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Removing (" + oid + ", " + legalHold + ")");
        }
        getClient(CacheClientInterface.SYSTEM_CACHE).
            removeLegalHold(oid, legalHold);
    }

    // these two methods should only be called for a cache that uses
    // defaultClient, such as system cache or SQLite, since for others
    // (like Clustra) we do not necessarily know on which disks an
    // object is stored when it's inserted into the MD cache
    public boolean usesDisk(int layoutMapId, String cacheId, Disk disk) {
        return defaultClient.usesDisk(layoutMapId, cacheId, disk);
    }
    public void setSysMetadata(SystemMetadata systemMD, Disk disk) {
        defaultClient.setSysMetadata(systemMD, disk);
    }
 
    /** does the given metadata object exist in the cache */
    public boolean existsExtCache(String cacheId, NewObjectIdentifier oid) 
        throws ArchiveException {
                             
        QueryResult qresult;
        // Use dynamic parameters with OID in internal (byte) form
        qresult = query(cacheId, 
                        SystemMetadata.FIELD_NAMESPACE+"."+
                        SystemMetadata.FIELD_OBJECTID+
                        "=?",
                        null, 1, 
                        new Object[] {oid.getDataBytes()});

        if (qresult.results.size() >= 1) {
            return true;
        } else {
            return false;
        }

    }

    public void updateSchema() 
        throws EMDException {
        getClient(CacheClientInterface.EXTENDED_CACHE).updateSchema();
    }

    public void clearFailure() 
        throws EMDException {
        getClient(CacheClientInterface.EXTENDED_CACHE).clearFailure();
    }
    public int getCacheStatus()
        throws EMDException {
        return getClient(CacheClientInterface.EXTENDED_CACHE).getCacheStatus();
    }

    public String getEMDCacheStatus()
        throws EMDException {
        return getClient(CacheClientInterface.EXTENDED_CACHE).getEMDCacheStatus();
    }

    public long getLastCreateTime() {
        return getClient(CacheClientInterface.EXTENDED_CACHE).getLastCreateTime();
    }

    public int checkIndexed(String cacheId, NewObjectIdentifier oid) 
        throws ArchiveException {

        try {
            if (existsExtCache(cacheId, oid)) {
                return -1;
            }
        } catch (ArchiveException e) {
            LOG.log(Level.INFO,
                    "checkIndexed: existsExtCache got exception"+e.getMessage(),
                     e);
            throw e;
        }
        LOG.info("checkIndexed: oid "+oid+" not found in "+
                 cacheId+" cache, inserting");

        //FIXME:  Should we create MetadataInterface method for the following?
        CacheClientInterface cache = 
            CacheManager.getInstance().getClientInterface(cacheId);
        CacheRecord mdObject = cache.generateMetadataObject(oid);
                    
        if (!setMetadata(cacheId, oid, mdObject)) {
            return 0;
        }
        return 1;
    }

}
