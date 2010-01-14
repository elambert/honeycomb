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

import com.sun.honeycomb.common.*;
import com.sun.honeycomb.protocol.client.*;

import java.util.prefs.*;
import javax.swing.*;
import com.sun.honeycomb.emd.parsers.QueryNode;
import com.sun.honeycomb.common.NewObjectIdentifier;

public class EMDClient {

    private static final int ACTIVE_HOST_TIMEOUT = 30000; // in ms.
    private static String[] clusterAddrs = null;

    private static ObjectArchive oa = null;

    public static synchronized void setClusterAddrs(String[] newClusterAddrs) {
        clusterAddrs = newClusterAddrs;
    }

    public static synchronized ObjectArchive getOA() {
        if (oa != null) {
            return(oa);
        }

        if (clusterAddrs == null) {
            return(null);
        }

        oa = new ObjectArchive(clusterAddrs);
        oa.setActiveHostTimeout(ACTIVE_HOST_TIMEOUT);
        return(oa);
    }

    public static class EMDQueryResult {
        public Cookie cookie;
        public ObjectIdentifierList results;
        
        public EMDQueryResult() {
            cookie = null;
            results = null;
        }
    }

    public static class EMDSelectUniqueResult {
        public Cookie cookie;
        public StringList results;
        
        public EMDSelectUniqueResult() {
            cookie = null;
            results = null;
        }
    }

    public static EMDQueryResult query(QueryNode parsedTree,
                                       Cookie cookie,
                                       int maxResults) {
        if (cookie != null) {
            return(null);
        }

        EMDQueryResult result = new EMDQueryResult();
        
        String query = parsedTree == null
            ? null
            : parsedTree.toString();

        try {
            result.results = getOA().query(query,
                                           maxResults);
        } catch (ArchiveException e) {
            e.printStackTrace();
            result = null;
        }
        
        return(result);
    }

    public static EMDSelectUniqueResult selectUnique(QueryNode parsedTree,
                                                     String attribute,
                                                     Cookie cookie,
                                                     int maxResults) {
        if (cookie != null) {
            return(null);
        }

        EMDSelectUniqueResult result = new EMDSelectUniqueResult();

        String query = parsedTree == null
            ? null
            : parsedTree.toString();

        try {
            result.results = getOA().selectUnique(query,
                                                  attribute,
                                                  maxResults);
        } catch (ArchiveException e) {
            e.printStackTrace();
            result = null;
        }
        
        return(result);
    }

    public static ObjectMetadataInterface getMetadataObject(NewObjectIdentifier oid) {
        try {
            return(getOA().getMetadata(oid));
        } catch (ArchiveException e) {
            e.printStackTrace();
            return(null);
        }
    }
}
