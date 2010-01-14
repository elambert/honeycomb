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
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Vector;
import java.util.Collections;


import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.MetadataClient.SelectUniqueResult;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDDiskAbstraction;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.common.MDHitByATime;
import com.sun.honeycomb.emd.remote.MDOutputStream;

public class InMemMetadata extends Object implements MetadataInterface {

    private static Logger LOG = Logger.getLogger(InMemMetadata.class.getName());
    private static InMemMetadata _instance = null;
  
    private Hashtable _cache = null;
        
    private InMemMetadata() {
        _cache = new Hashtable();
    }
    
    public static synchronized InMemMetadata getInstance() {
        if (_instance == null)
            _instance = new InMemMetadata();
        
        return _instance;
    }
    
    public void clear() {
        _cache.clear();
    }
    
    public int getSize() {
        return _cache.size();
    }
    
    public void clearFailure() throws EMDException {
        LOG.info("Calling clearfailure, empty implementation for unit tests.");
    }

    public int getCacheStatus()
        throws EMDException {
        LOG.info("Calling getCacheStatus, empty implementation for unit tests.");
        return 0;
    }

    public String getEMDCacheStatus()
        throws EMDException {
        LOG.info("Calling getEMDCacheStatus, empty implementation for unit tests.");
        return "HAFaultTolerant";
    }
    public String getCacheId() {
        return CacheClientInterface.SYSTEM_CACHE;
    }

    public QueryResult queryPlus(String cacheId, String query,
            ArrayList attributes, Cookie _cookie, int maxResults, int timeout,
            boolean forceResults, boolean abortOnFailure, 
            Object[] boundParameters, MDOutputStream outputStream)
            throws EMDException {

        if (!cacheId.equals(getCacheId()))
            throw new EMDException("Bad cacheId: " +cacheId + " only " + getCacheId() + " suppported.");

        QueryResult result = new QueryResult();
        result.results = new ArrayList();
        String[] args = query.split(" ");

        if (args[0].startsWith(SystemCacheConstants.SYSTEM_QUERY_CHECKOID)) {
            // checkOID query
            NewObjectIdentifier oid = NewObjectIdentifier.fromExternalHexString(args[1]);
            LOG.info(SystemCacheConstants.SYSTEM_QUERY_CHECKOID + " On " + oid);
            
            // Sort hashtable.
            Vector v = new Vector(_cache.values());
            Comparator c = new SystemMetadataComparator();
            Collections.sort(v, c);
            
            Enumeration e = v.elements();
            while (e.hasMoreElements()) {
                SystemMetadata sm = (SystemMetadata) e.nextElement();
                if (sm.getOID().compareTo(oid) == 0) {
                    if (attributes != null)
                        result.results.add(new MDHit(sm.getOID(),sm));
                    else
                        result.results.add(new MDHit(sm.getOID(),null));
                }
            }
            
        } else if (args[0].startsWith(SystemCacheConstants.SYSTEM_QUERY_GETCHANGES)) {
            // getChanges query
            EMDCookie cookie = (EMDCookie) _cookie;
            long t1, t2;
         
            if (cookie != null) {
                t1 = cookie.getLastATime();
            } else
                t1 = new Long(args[1]).longValue();
            
            t2 = new Long(args[2]).longValue();
            MDHitByATime mdhit = null;
            LOG.info("Querying system cache entries between " + t1 + " and " + t2);
            // for each cache
            
            // Sort hashtable.
            Vector v = new Vector(_cache.values());
            Comparator c = new SystemMetadataComparator();
            Collections.sort(v, c);
            
            Enumeration e = v.elements();
            
            // position ourselves at the last cookie's position.
            if (cookie != null)
	            while (true) {
	                SystemMetadata sm = (SystemMetadata) e.nextElement();
	                long atime = (sm.getCTime() > sm.getDTime() ? 
                                              sm.getCTime() : sm.getDTime());
	                if (sm.getOID().equals(cookie.getLastOid()) &&
	                    atime == cookie.getLastATime())
	                    break;
	            }
           
            while (e.hasMoreElements()) {
                SystemMetadata sm = (SystemMetadata) e.nextElement();;
                long atime = (sm.getCTime() > sm.getDTime() ? sm.getCTime() : sm.getDTime());
   
                if (atime >= t1 && atime <= t2) {
                    if (cookie == null || 
                        !sm.getOID().equals(cookie.getLastOid())) {
                        mdhit = new MDHitByATime(sm.getOID(),sm,atime);
                        result.results.add(mdhit);
                    }
                }
            }
           
            if (mdhit != null) { 
                result.cookie = new EMDCookie(mdhit.constructOid(),
                                              null,
                                              -1,
                                              null,
                                              null,
                                              mdhit.getATime());
                LOG.info("cookie being sent back: " + result.cookie);
            }
            
        } else if (args[0].startsWith(SystemCacheConstants.SYSTEM_QUERY_GETMDSTHATPOINT)) {
           // getMDsThatPoint query
            
        } else if (args[0].startsWith(SystemCacheConstants.SYSTEM_QUERY_GETOBJECTS)) {
           // getObjects query
        } else 
            throw new EMDException("Unkown query type " + query);
        
        return result;
    }

    public Cookie querySeek(String query, int index, Object[] boundParameters, ArrayList attributes) {
        // TODO Auto-generated method stub
        return null;
    }

    public void removeMetadata(NewObjectIdentifier oid, String cacheId)
            throws EMDException {
        LOG.info("Removing SystemMetadata for object " + oid);
        _cache.remove(oid);
    }

    public SelectUniqueResult selectUnique(String cacheId, String query,
            String attribute, Cookie _cookie, int maxResults, int timeout,
            boolean forceResults, MDOutputStream outputStream)
            throws EMDException {
        // TODO Auto-generated method stub
        return null;
    }

    public Cookie selectUniqueSeek(String query, String attribute, int index, Object[]boundParameters) {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean setMetadata(String cacheId, NewObjectIdentifier oid, Object argument) {
       
        if (argument instanceof SystemMetadata) {
            SystemMetadata sm = (SystemMetadata) argument; 
            _cache.put(sm.getOID(), sm);
            return true;
        }
       
        return false;
    }

    public boolean setMetadata(String cacheId, Map items) {
        // I Don't see this being used by anyone ? ?? old interface ????
        LOG.warning("method InMemMetadata.setMetadta(String cacheId, Map items) not supported...");
        return false;
    }

    public void setMetadata(SystemMetadata systemMD, byte[] MDField, Disk disk) {
        _cache.put(systemMD.getOID(),systemMD);
    }

    public void updateSchema() throws EMDException {
        LOG.warning("method InMemMetadata.updateSchema not supported...");
    }

    public void inithook() throws EMDException {
        // TODO Auto-generated method stub
        
    }

    public SelectUniqueResult selectUnique(String cacheId, String query,
            String attribute, Cookie _cookie, int maxResults, int timeout,
            boolean forceResults, Object[] boundParameters,
            MDOutputStream outputStream) throws EMDException {
        // TODO Auto-generated method stub
        return null;
    }

    /** Adds a legal hold to the system cache on the given disk **/
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold,
                             Disk disk) {
    }

    /** Add a legal hold given a cache id **/
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold) {
    }

    // Remove the legal hold
    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold) {
    }

    public QueryResult queryPlus(String cacheId, String query,
            ArrayList attributes, Cookie _cookie, int maxResults, int timeout,
            boolean forceResults, Object[] boundParameters,
            MDOutputStream outputStream, Disk disk) throws EMDException {
        throw new RuntimeException("Unsupported method in the InMemMetadata cache.");
    }

    public long getLastCreateTime() {
        return 0;
    }
    
    class SystemMetadataComparator implements Comparator {
  
		public int compare(Object o1, Object o2) {
			if (!(o1 instanceof SystemMetadata) ||
    	    	!(o2 instanceof SystemMetadata))
    	        throw new ClassCastException("SystemMetadata objects expected.");
    	    SystemMetadata SM1 = (SystemMetadata) o1;
    	    SystemMetadata SM2 = (SystemMetadata) o2;
    		long atime1 = (SM1.getCTime() > SM1.getDTime() ?
    				SM1.getCTime() : SM1.getDTime());
    		long atime2 = (SM2.getCTime() > SM2.getDTime() ?
    				SM2.getCTime() : SM2.getDTime());
    		if (atime1 < atime2)  return -1;
    		if (atime1 > atime2) return 1;
    		// must be equal
    		return SM1.getOID().compareTo(SM2.getOID());
		}
    }
}
