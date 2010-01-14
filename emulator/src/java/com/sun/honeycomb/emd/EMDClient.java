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

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Iterator;

import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.StringList;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.CanonicalEncoding;
import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.cache.MDHeader;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.remote.StreamHead;
import com.sun.honeycomb.emd.remote.ConnectionFactory;
import com.sun.honeycomb.emd.remote.InMemoryMDStream;
import com.sun.honeycomb.emd.remote.MDInputStream;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.disks.Disk;

public class EMDClient 
    implements MetadataInterface {

    private static Logger LOG = Logger.getLogger(EMDClient.class.getName());

    public static final int MAX_ERROR_COUNT = 1;
    
    public String getCacheId() {
        return("default");
    }

    public void inithook() 
        throws EMDException {
        //do nothing
    }

    private CacheInterface getCache(String cacheId)
        throws EMDException {
        return(CacheManager.getInstance().getServerCache(cacheId));
    }

    /****************************************
     *
     * Query APIs
     *
     ****************************************/
    
    private MetadataClient.QueryResult queryPlus(int[] mapIds,
                                                 String cacheId,
                                                 String query,
                                                 ArrayList attributes,
                                                 Cookie _cookie,
                                                 int maxResults,
                                                 int timeout,
                                                 boolean forceResults,
                                                 boolean abortOnFaiure,
                                                 Object[] boundParameters,
                                                 MDOutputStream _outputStream) 
        throws EMDException {
        CacheInterface cache = getCache(cacheId);
        MDOutputStream stream = _outputStream;
        if (stream == null) {
            stream = new InMemoryMDStream();
        }
        
        EMDCookie cookie = (EMDCookie)_cookie;
        NewObjectIdentifier oid = null;
        int toBeSkipped = 0;
        
        if (cookie != null) {
            LOG.info("received cookie='"+cookie.toString()+"'");
            query = cookie.getQuery();
            oid = cookie.getLastOid();
            toBeSkipped = cookie.getToBeSkipped();
            boundParameters = cookie.getBoundParameters();
            attributes = cookie.getAttributes();
        }

        LOG.info("query='"+query+"' maxResults="+maxResults+
                 " attributes=("+
                 CanonicalEncoding.literalsToString(attributes)+
                 ") parameters=("+
                 CanonicalEncoding.parametersToString(boundParameters)+
                 ")");
        cache.queryPlus(stream, null, query, attributes, cookie, maxResults,
                        timeout, forceResults, boundParameters);
	
        MetadataClient.QueryResult result = new MetadataClient.QueryResult();
        result.results = new ArrayList();
	
        //The emulator always reports the current time as the 
        // query integrity time.
        result.queryIntegrityTime = System.currentTimeMillis();

        if (_outputStream == null) {
            Object object;
            MDInputStream inputStream = (MDInputStream)stream;
            while (!((object = inputStream.getObject()) instanceof MDInputStream.EndOfStream)) {
                result.results.add(object);
		//System.err.println("Added " + object);
            }
        }

        result.cookie = null;
        MDHit lastHit = (MDHit)stream.getLastObject();
        if (lastHit != null) {
            result.cookie = new EMDCookie(lastHit.constructOid(),
                                          query, 0, 
                                          boundParameters, 
                                          attributes);
        }
        
        return(result);
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
        return(queryPlus(null, cacheId, query, attributes, _cookie, maxResults,
                         timeout, forceResults, abortOnFailure, boundParameters, 
                         outputStream));
    }
    
    public Cookie querySeek(String query, int index, Object[] boundParameters, ArrayList attributes) {
        return new EMDCookie(null, query, index, boundParameters,attributes);
    }
    
    /****************************************
     *
     * selectUnique APIs
     *
     ****************************************/

    public MetadataClient.SelectUniqueResult selectUnique(String cacheId,
                                                          String query,
                                                          String attribute,
                                                          Cookie _cookie,
                                                          int maxResults,
                                                          int timeout,
                                                          boolean forceResults,
                                                          Object[] boundParameters,
                                                          MDOutputStream _outputStream) 
        throws EMDException {

        CacheInterface cache = getCache(cacheId);
        MDOutputStream stream = _outputStream;
        if (stream == null) {
            stream = new InMemoryMDStream();
        }

        EMDCookie cookie = (EMDCookie)_cookie;
        String lastAttribute = null;
        int toBeSkipped = 0;
	
        if (cookie != null) {
            query = cookie.getQuery();
            attribute = cookie.getAttribute();
            lastAttribute = cookie.getLastAttribute();
            toBeSkipped = cookie.getToBeSkipped();
            boundParameters = cookie.getBoundParameters();
        }

        cache.selectUnique(stream, query, attribute, lastAttribute,
                           maxResults, timeout, forceResults,
                           boundParameters);
	
        MetadataClient.SelectUniqueResult result = new MetadataClient.SelectUniqueResult();
	
        if (_outputStream == null) {
            Object object;
            ArrayList stringList = new ArrayList();
            MDInputStream inputStream = (MDInputStream)stream;
            while (!((object = inputStream.getObject()) instanceof MDInputStream.EndOfStream)) {
                stringList.add(object.toString());
            }
            result.results = new StringList(stringList);
        } else {
            result.results = new StringList(null);
        }

        result.cookie = null;
        if (stream.getLastObject() != null) {
            result.cookie = new EMDCookie(stream.getLastObject().toString(),
                                          query,
                                          attribute,
                                          0,
                                          boundParameters);
        }

        return(result);
    }

    /**
     * Method to generate a cookie based on the query and the index from
     * where the results need to be fetched.
     *
     * @param parsedTree
     *   The query expression tree to perform on the metadata
     * @param attribute
     *   The attribute name for the select unique query
     * @param index
     *   The index of the results for the cookie
     * @return Cookie
     *   The instance of the cookie to fetch the rest of the results. This
     *   can be null if the index is more than the number of results for the
     *   query.
     * TBD: Should thrown an exception for invalid arguments
     */
    
    public Cookie selectUniqueSeek(String query,
                                   String attribute,
                                   int index,
                                   Object[] boundParameters) {
        return new EMDCookie(null, query, attribute, index, boundParameters);
    }

    /****************************************
     *
     * setMetadata API
     *
     ****************************************/

    /**
     * Method to store the metadata of an object in the cache. This method
     * might not store the metadata in the cache if it already exists or if
     * it does not belong to the cache. The disk object is used to determine
     * if the call needs to be made to the local or remote cache server.
     *
     * <hr>
     * For the system cache, argument is a SystemMetadata object
     * For the extended cache, argument is either a Map containing the info
     * or null
     * <hr>
     *
     * @param argument what allows the cache to do its job (see above)
     * @param disk the disk to store the metadata in
     * @return true if the metadata was stored successfully or was not

     *              supposed to be stored. false if there was a failure.
     */

    public boolean setMetadata(String cacheId,
			       NewObjectIdentifier oid,
			       Object argument) {
        setMetadata(cacheId, null, oid, argument);
        return true;
    }

    private void setMetadata(String cacheId,
                             ConnectionFactory.DiskConnection[] connections,
                             NewObjectIdentifier oid,
                             Object argument) {

        try {
            CacheInterface cache = getCache(cacheId);
            cache.setMetadata(oid, argument, MetadataService.instance.getDisk());
        } catch (EMDException e) {
	    LOG.log(Level.INFO,
		    "setMetadata failed with exception "+e.getMessage(),
		    e);
        }
    }
    public boolean setMetadata(String cacheId,
			       Map items) {

        if (items == null) {
            LOG.severe("Invalid argument in setMetadata [null]");
            return false;
        }

	SortedMap sortedItems = new TreeMap(items);
	for (Iterator i = sortedItems.keySet().iterator(); i.hasNext(); ) {
	    NewObjectIdentifier oid = (NewObjectIdentifier) i.next();
	    Map attributes = (Map)sortedItems.get(oid);
	    if (setMetadata(cacheId,oid,attributes))
		return true;
	}
	return false;
    }
    
    /**
     * This <code>setMetadata</code> method is the OA callback to populate
     * a local cache.
     *
     * @param <code>SystemMetadata</code> are the SystemMetadata extracted
     * from the local fragment
     * @param <code>Disk</code> is the disk containing the fragment
     */
    
    public void setMetadata(SystemMetadata systemMD,
                            byte[] MDField,
                            Disk disk) {
        throw new RuntimeException("Not implemented");
    }
    
    /**********************************************************************
     *
     * removeMetadata API
     *
     **********************************************************************/
    
    /**
     * Method to remove an object's metadata entry from the cache given its
     * unique identifier.
     *
     * @param oid the object's unique identifier
     * @return boolean true if successful false otherwise.
     */

    public void removeMetadata(NewObjectIdentifier oid,
                               byte[] MDField,
                               Disk disk) 
        throws EMDException {

        // Find the cacheId
        MDHeader header = null;
        try {
            header = new MDHeader(MDField);
        } catch (IOException e) {
            EMDException newe = new EMDException("Failed to retrieve the cacheId value ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
	
        String cacheId = header.getCacheId();
        
        removeMetadata(oid, cacheId);
    }
    
    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId) 
        throws EMDException {

        CacheInterface cache = getCache(cacheId);
        cache.removeMetadata(oid, MetadataService.instance.getDisk());

    }
   
    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId,
                               Disk disk ) {
        // nothing to do, there is no system cache on emulator
        // and that is what this method is used for
    }   
    
    public void updateSchema()
        throws EMDException {
        throw new EMDException("updateSchema not implemented in the emulator");
    }

    public void clearFailure() 
        throws EMDException {
        throw new EMDException("clearFailure not implemented in the emulator");
    }
    public int getCacheStatus()
        throws EMDException {
        throw new EMDException("getCacheStatus is not implemented for the distributed caches");
    }

    public String getEMDCacheStatus()
        throws EMDException {
        throw new EMDException("getEMDCacheStatus is not implemented for the distributed caches");
    }

    /** 
     * Check if this OID exists in the SystemCache on given disk.
     *
     * @param oid       oid of object whose systemMD seeking
     * @param disk      we only look at system cache on this disk
     */
    public boolean existsSystemCache(NewObjectIdentifier oid,
      boolean restoredOnly, Disk disk) {
        return true;
    }


    // HEALING FAKES

    public boolean usesDisk(int layoutMapId, String cacheId, Disk disk) {return true;}

    public void setExtMetadata(NewObjectIdentifier oid,
                               String cacheId, Disk disk) {

        LOG.fine("setExtMetadata for oid "+oid+" on disk ["+disk+"]");

        setMetadata(cacheId, oid, disk);
    }

    public void setSysMetadata(NewObjectIdentifier oid,
                               String cacheId, Disk disk) {

        LOG.fine("setExtMetadata for oid "+oid+" on disk ["+disk+"]");

	//System.err.println("set MD");
        setMetadata(cacheId, oid, disk);
    }

    public void setSysMetadata(SystemMetadata systemMD, Disk disk) {
        setMetadata(systemMD, CacheClientInterface.SYSTEM_CACHE, disk);
    }
    public void setMetadata(SystemMetadata systemMD, String cacheID, Disk disk) {
	System.err.println("Called setMetadata " + systemMD);
        //setMetadata(CacheClientInterface.SYSTEM_CACHE, systemMD, disk);
    }

    /**********************************************************************
     *
     * Legal hold API
     *
     **********************************************************************/

    /** Add a legal hold given a cache id **/
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold) {

        // Debug
        LOG.info("Add legal hold (" + oid + ", [" + legalHold + "]");
        LOG.info("XXX Not yet implemented in the emulator!");
    }

    /** Adds a legal hold to the system cache on the given disk **/
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold,
                             Disk disk) {

        // Debug
        LOG.info("Add legal hold (" + oid + ", [" + legalHold +
                 "]) to disk " + disk.toString());
        LOG.info("XXX Not yet implemented in the emulator!");
    }

    // Remove the legal hold
    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold) {
        LOG.info("Remove legal hold [" + legalHold + "] for OID " + oid);
        LOG.info("XXX Not yet implemented in the emulator!");
    }

    public QueryResult queryPlus(String cacheId, String query,
            ArrayList attributes, Cookie _cookie, int maxResults, int timeout,
            boolean forceResults, Object[] boundParameters,
            MDOutputStream outputStream, Disk disk) throws EMDException {
        throw new RuntimeException("Method not implemented.");
    }

    public long getLastCreateTime() {
        return 0;
    }
}
