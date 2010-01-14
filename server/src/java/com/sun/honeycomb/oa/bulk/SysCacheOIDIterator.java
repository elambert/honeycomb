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



package com.sun.honeycomb.oa.bulk;

import java.util.logging.Logger;

import com.sun.honeycomb.admin.mgmt.server.MgmtServerIntf;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.server.SysCache;

public class SysCacheOIDIterator implements OIDIterator {
    
    private Logger Log = Logger.getLogger(SysCacheOIDIterator.class.getName());
    private MetadataClient.QueryResult _results = null;
    private int _maxResults = 5*1024;
    private boolean _force = false;
    private long _endTime = 0;
    
    private static final int MAX_RETRIES = 7;
    
    private String _query = null;
    /**
     * simple implementation of querying the system cache in order to obtain 
     * a list of oids for consumption by the BackupSession 
     *  
     * @param query
     * @throws ArchiveException
     */ 
    public SysCacheOIDIterator(long start, long end) throws ArchiveException {
        this (start, end, false);
    }
    
    public SysCacheOIDIterator (long start, long end, boolean force) throws ArchiveException {
        _query = "getChanges " + start + " " + end;
        _force = force;
        _endTime = end;
        requery();
    }
        
//    public SysCacheOIDIterator(long start, long end, int maxResults) throws ArchiveException {   
//        _maxResults = maxResults; 
//        _query = "getChanges " + start + " " + end;
//        _force = false;
//        requery();
//    }
    
    private void requery() throws ArchiveException {
    	int numRetries = 0;
    	boolean done = false;
    	
    	do {
    		try {
    			_results = MetadataClient
    				.getInstance().query(CacheClientInterface.SYSTEM_CACHE, 
    				                     _query,
    				                     (_results != null ? _results.cookie : null),
    				                     _maxResults, true);
    	        Log.info("Sys Cache Query result size: " + _results.results.size());
    			done = true;
    		} catch (Exception e) {
    		    if (numRetries++>=MAX_RETRIES) {
    		        String msg ="Unable to execute query \"" 
    		                + _query + "\" maximum number of retries reached!";
    		        Log.severe(msg);
    		        throw new ArchiveException (msg, e);
    		    } else {
    		        Log.info("Error executing query \""+ _query + "\": "
    		                    + e.toString());
    		    }
    		    
    		}
    	} while (!done);
    }
   
    /**
     * @throws ArchiveException 
     * 
     */
    public boolean hasNext() throws ArchiveException {
        if (_results.results.isEmpty()) { 
            requery();
            return !_results.results.isEmpty();
        } else 
            return true;
    }

    /**
     * 
     */
    public NewObjectIdentifier next() {
        MDHit mdhit = (MDHit)_results.results.remove(0);
        return NewObjectIdentifier.fromHexString(mdhit.getOid());
    }
    
    /**
     * 
     */
    public long getEndTime() { return _endTime; }
}
