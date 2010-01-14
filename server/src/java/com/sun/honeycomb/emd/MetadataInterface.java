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
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.disks.Disk;

public interface MetadataInterface {

    public String getCacheId();

    ///Emulator hook to initialize the extended cache database 
    public void inithook() 
        throws EMDException;

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
        throws EMDException;

    
    public MetadataClient.QueryResult queryPlus(String cacheId,
                                                String query,
                                                ArrayList attributes,
                                                Cookie _cookie,
                                                int maxResults,
                                                int timeout,
                                                boolean forceResults,
                                                Object[] boundParameters,
                                                MDOutputStream outputStream,
                                                Disk disk) 
        throws EMDException;

    public Cookie querySeek(String query, int index, Object[]boundParameters, ArrayList attributes);

    public MetadataClient.SelectUniqueResult selectUnique(String cacheId,
                                                          String query,
                                                          String attribute,
                                                          Cookie _cookie,
                                                          int maxResults,
                                                          int timeout,
                                                          boolean forceResults,
                                                          Object[] boundParameters,
                                                          MDOutputStream outputStream) 
        throws EMDException;
    
    public Cookie selectUniqueSeek(String query,
                                   String attribute,
                                   int index,
                                   Object[] boundParameters);
    
    public boolean setMetadata(String cacheId,
                               NewObjectIdentifier oid,
                               Object argument);
    
    public boolean setMetadata(String cacheId,
			      Map items);

    // OA Callback (OBSOLETE)
    public void setMetadata(SystemMetadata systemMD,
                            byte[] MDField,
                            Disk disk);
    
    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId) 
        throws EMDException;

    // Schema update
    public void updateSchema()
        throws EMDException;

    // Clear failure (targeted at HADB)
    public void clearFailure()
        throws EMDException;

    // Get Cache Status (targeted at HADB)
    public int getCacheStatus()
        throws EMDException;

    // Get Hadb Status (targeted at HADB)
    public String getEMDCacheStatus()
        throws EMDException;

    // Legal holds
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold, Disk disk);

    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold);

    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold);

    //return last create time of the database
    // cf updateQueryIntegrityTime in MgmtServer.java
    public long getLastCreateTime();

}
