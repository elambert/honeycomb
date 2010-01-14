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

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.StringList;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.disks.Disk;

public class MetadataClient {
   
    private static MetadataClient singleton = null;
    private static final Logger log = Logger.getLogger(MetadataClient.class.getName());
 
    public static synchronized MetadataClient getInstance() {
        if (singleton == null) {
            singleton = new MetadataClient();
        }
        return(singleton);
    }
   
    /**********************************************************************
    *
    * Implementation that considers potential external hooks
    *
    **********************************************************************/

   private MetadataInterface getClient(String cacheId) {
       if (cacheId.equals(CacheClientInterface.SYSTEM_CACHE)) {
           log.info("Using in memory system cache for unit testing.");
           return InMemMetadata.getInstance();
       } else {
           log.warning("System cache: " + cacheId + " not implemented for UT tests.");
           return null; 
       }
   }
   
   public QueryResult query(String cacheId,
                            String query,
                            int maxResults) 
          throws ArchiveException {
      return query(cacheId, query, null, maxResults);
   }
   
   public QueryResult query(String cacheId,
                            String query,
                            Cookie cookie,
                            int maxResults,
                            boolean abortOnFailure)
           throws ArchiveException {
       return getClient(cacheId).queryPlus(cacheId, query, null, cookie, 
               maxResults, 0, true, false, null, null);     
   }
   
   public QueryResult query(String cacheId,
                            String query,
                            Cookie cookie,
                            int maxResults) 
          throws ArchiveException {
       return query (cacheId, query, cookie, maxResults, false);
   }
   
   public QueryResult queryPlus(String cacheId,
                            String query,
                            ArrayList attributes,
                            Cookie cookie,
                            int maxResults) 
          throws ArchiveException {
       return getClient(cacheId).queryPlus(cacheId, query, attributes, cookie, 
                                           maxResults, 0, true, false, null, null);
   }
   
   public SystemMetadata retrieveMetadata(String cacheId,
            NewObjectIdentifier oid) throws EMDException {

        if (!cacheId.equals(CacheClientInterface.SYSTEM_CACHE))
            throw new EMDException(
                    "only system cache is supported for retrieve metadata");

        QueryResult qresult;

        try {
            qresult = queryPlus(CacheClientInterface.SYSTEM_CACHE,
                                SystemCacheConstants.SYSTEM_QUERY_CHECKOID + " "
                                    + oid.toExternalHexString(), 
                                new ArrayList(), null, 1);
        } catch (ArchiveException e) {
            throw new EMDException(e);
        }

        if (qresult.results.size() >= 1) {
            return (SystemMetadata) ((MDHit) qresult.results.get(0))
                    .getExtraInfo();
        } else {
            throw new EMDException("System record not found for "
                    + oid.toHexString());
        }
    }  
   
   public boolean setMetadata(String cacheId,
                            NewObjectIdentifier oid,
                            Object argument) {
        MetadataInterface mdi = getClient(cacheId);
        
        if (mdi == null)
            return true;
      
        return mdi.setMetadata(cacheId,oid,argument);
    }

    public void setMetadata(SystemMetadata systemMD,
                            byte[] MDField,
                            Disk disk) {
        MetadataInterface mdi = getClient(CacheClientInterface.SYSTEM_CACHE);
        mdi.setMetadata(systemMD,MDField,disk);
    }

    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId) 
        throws EMDException {
        log.info("No extended cache implmemented all operations will be done on in memory system cache.");
        
        MetadataInterface mdi = getClient(cacheId);
        if (mdi == null)
            return;
       
        mdi.removeMetadata(oid, cacheId);
    }
    
    /**********************************************************************
    *
    * QueryResult class
    *
    **********************************************************************/

   public static class QueryResult {
       public Cookie cookie;
       public ArrayList results;
       
       public QueryResult() {
           cookie = null;
           results = null;
       }

       //
       // Used by QueryHandler to retrun the results to the client clib
       // - constructOid is called with 'true' to cconstruct
       //   the external form of the OIDs.
       //
       public int serializeOids(DataOutput dataOut) throws IOException {
        
           MDHit hit;

           if (results != null) {
               for (int i=0; i<results.size(); i++) {
                   hit = (MDHit)results.get(i);
                   NewObjectIdentifier oid = hit.constructOid();
                   oid.serializeExternal(dataOut);
               }
               return results.size();
           }
        else{
        return 0;
        }
    }
   }
   
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

    public long getLastCreateTime() {
        return 0;
    }

    /** does the given metadata object exist in the cache */
    public boolean existsExtCache(String cacheId, NewObjectIdentifier oid) {
        return true;
    }

}
