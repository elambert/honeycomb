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

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.admin.mgmt.server.MgmtServerIntf;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.Node;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.Cookie;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.StringList;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.cache.MDHeader;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDDiskAbstraction;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.common.MDHitByATime;
import com.sun.honeycomb.emd.remote.ConnectionFactory;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.emd.remote.ObjectBroker;
import com.sun.honeycomb.emd.remote.StreamHead;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.util.ExtLevel;

/**
 * This class implements the EMD APIs
 */

public class EMDClient
    implements MetadataInterface {

    private static Logger LOG = Logger.getLogger(EMDClient.class.getName());
    
    // Since we cannot easily determine the state of all system caches today,
    // the safest thing to do abort a query on any errors
    public static final int MAX_ERROR_COUNT = 1;
    
    public String getCacheId() {
        return("default");
    }
    
    public void inithook() 
        throws EMDException {
        //do nothing
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
                                                 boolean abortOnFailure,
                                                 Object[] boundParameters,
                                                 MDOutputStream outputStream) 
        throws EMDException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("EMDClient queryPlus called ["+
                     query+"]");
        }

        // Sanity checks
        if ((_cookie != null)
            && (maxResults == -1)) {
            // Has to specify an offset AND a row count
            throw new EMDException("Invalid argument : when " +
                                   "using cookies, you have to " +
                                   "specify the number of " +
                                   "entries to return");
        }

        ConnectionFactory.DiskConnection[] connections =
            ConnectionFactory.getConnections(mapIds,
                                             cacheId);

        if (LOG.isLoggable(Level.FINE)) {
            StringBuffer buffer = new StringBuffer();
            buffer.append("The query ["+query+"] is sent to : ");
            for (int i=0; i<connections.length; i++) {
                connections[i].toString(buffer);
                buffer.append(" <> ");
            }
            LOG.fine(buffer.toString());
        }
        
        Socket[] sockets = new Socket[connections.length];
        ObjectBroker[] brokers = new ObjectBroker[connections.length];
        StreamHead[] heads = new StreamHead[connections.length];
        
        try {
            EMDCookie cookie = (EMDCookie)_cookie;
            NewObjectIdentifier oid = null;
            int toBeSkipped = 0;

            if (cookie != null) {
                query = cookie.getQuery();
                oid = cookie.getLastOid();
                toBeSkipped = cookie.getToBeSkipped();
                boundParameters = cookie.getBoundParameters();
            }

            // Construct the brokers and launch the queries
            for (int i=0; i<sockets.length; i++) {
                sockets[i] = null;

                // Connect to the node
                try {
                    sockets[i] = ConnectionFactory.connect(connections[i]);
                } catch (IOException e) {
                    LOG.warning("Failed to connect to node "+connections[i].getNodeAddress()
                                +". Skipping this node ["+e.getMessage()+"]");


                    String str = BundleAccess.getInstance().getBundle().getString("warn.emd.query.io");
                    Object [] args = {connections[i].toString()};
                    LOG.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));


                    try {
                        if (sockets[i] != null) {
                            sockets[i].close();
                        }
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;

                    throw new EMDException("Failed to connect to node " + 
                                           connections[i].getNodeAddress() +
                                           ". Skipping this node.",e);

                }

                // Launch the query
                if (sockets[i] == null) {
                    brokers[i] = null;
                    heads[i] = null;
                } else {
                    brokers[i] = new ObjectBroker(sockets[i]);
                    brokers[i].launchQueryClient(cacheId, 
                                                 connections[i].getDisks(),
                                                 query, 
                                                 attributes, 
                                                 cookie,
                                                 maxResults+toBeSkipped, 
                                                 timeout, 
                                                 forceResults,
                                                 boundParameters);
                    heads[i] = new StreamHead(brokers[i]);
                }
            }

            // Merge the result and compute the output
            ArrayList array = null;
            MDHit lastHit = null;

            if (outputStream == null) {
                array = StreamHead.mergeStreams(heads, toBeSkipped, maxResults, abortOnFailure);
                if (array != null && array.size() != 0)
                    lastHit = (MDHit)array.get(array.size()-1);
            } else {
                StreamHead.mergeStreams(heads, outputStream, toBeSkipped, maxResults);
                lastHit = (MDHit) outputStream.getLastObject();
            }

            MetadataClient.QueryResult result = new MetadataClient.QueryResult();
            if (lastHit != null) {
                long atime = -1;
                if (lastHit instanceof MDHitByATime ) 
                    atime = ((MDHitByATime)lastHit).getATime();
                    
                result.cookie = new EMDCookie(lastHit.constructOid(),
                                              query,
                                              0,
                                              boundParameters,
                                              attributes,
                                              atime);
                result.results = array;
            } else {
                result.cookie = null;
                result.results = new ArrayList();
            }
            return(result);

        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to run the distributed query",
                    e);

            String str = BundleAccess.getInstance().getBundle().getString("err.emd.query.io");
            Object [] args = {query};
            LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

            return(null);
        } finally {
            // Close the connections
            for (int i=0; i<sockets.length; i++) {
                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                        sockets[i] = null;
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    public MetadataClient.QueryResult queryPlus(String cacheId,
                                                String query,
                                                ArrayList attributes,
                                                Cookie _cookie,
                                                int maxResults,
                                                int timeout,
                                                boolean forceResults,
                                                Object [] boundParameters,
                                                MDOutputStream outputStream) 
        throws EMDException {
        return(queryPlus(cacheId, query, attributes, _cookie, maxResults,
                         timeout, forceResults, false, boundParameters, 
                         outputStream));
    }
    
    public MetadataClient.QueryResult queryPlus(String cacheId,
                                                String query,
                                                ArrayList attributes,
                                                Cookie _cookie,
                                                int maxResults,
                                                int timeout,
                                                boolean forceResults,
                                                boolean abortOnFailure,
                                                Object [] boundParameters,
                                                MDOutputStream outputStream) 
        throws EMDException {
        CacheClientInterface cache = CacheManager.getInstance().getClientInterface(cacheId);
        if (cache == null) {
            throw new IllegalArgumentException("invalid cache id: " +
                                               cacheId);
        }

        int[] maps = cache.layoutMapIdsToQuery(query, LayoutClient.NUM_MAP_IDS);
        
        return(queryPlus(maps, cacheId, query, attributes, _cookie, maxResults,
                         timeout, forceResults, false, boundParameters, 
                         outputStream));
    }
    
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
                                       throws EMDException {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("EMDClient queryPlus called [" + query + "]");
        }

        // Sanity checks
        if ((_cookie != null) && (maxResults == -1)) {
            // Has to specify an offset AND a row count
            throw new EMDException("Invalid argument : when "
                    + "using cookies, you have to " + "specify the number of "
                    + "entries to return");
        }

        ConnectionFactory.DiskConnection[] connections = makeConn(disk);

        if (LOG.isLoggable(Level.FINE)) {
            StringBuffer buffer = new StringBuffer();
            buffer.append("The query [" + query + "] is sent to : ");
            for (int i = 0; i < connections.length; i++) {
                connections[i].toString(buffer);
                buffer.append(" <> ");
            }
            LOG.fine(buffer.toString());
        }

        Socket[] sockets = new Socket[connections.length];
        ObjectBroker[] brokers = new ObjectBroker[connections.length];
        StreamHead[] heads = new StreamHead[connections.length];

        try {
            EMDCookie cookie = (EMDCookie) _cookie;
            NewObjectIdentifier oid = null;
            int toBeSkipped = 0;

            if (cookie != null) {
                query = cookie.getQuery();
                oid = cookie.getLastOid();
                toBeSkipped = cookie.getToBeSkipped();
                boundParameters = cookie.getBoundParameters();
            }

            // Construct the brokers and launch the queries
            for (int i = 0; i < sockets.length; i++) {
                sockets[i] = null;

                // Connect to the node
                try {
                    sockets[i] = ConnectionFactory.connect(connections[i]);
                } catch (IOException e) {
                    LOG.warning("Failed to connect to node "
                            + connections[i].getNodeAddress()
                            + ". Skipping this node [" + e.getMessage() + "]");

                    String str = BundleAccess.getInstance().getBundle()
                            .getString("warn.emd.query.io");
                    Object[] args = { connections[i].toString() };
                    LOG.log(ExtLevel.EXT_WARNING, MessageFormat.format(str,
                            args));

                    try {
                        if (sockets[i] != null) {
                            sockets[i].close();
                        }
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;

                    throw new EMDException("Failed to connect to node " + 
                                           connections[i].getNodeAddress() +
                                           ". Skipping this node.",e);

                }

                // Launch the query
                if (sockets[i] == null) {
                    brokers[i] = null;
                    heads[i] = null;
                } else {
                    brokers[i] = new ObjectBroker(sockets[i]);
                    brokers[i].launchQueryClient(cacheId, connections[i]
                            .getDisks(), query, attributes, cookie, maxResults
                            + toBeSkipped, timeout, forceResults,
                            boundParameters);
                    heads[i] = new StreamHead(brokers[i]);
                }
            }

            // Merge the result and compute the output
            ArrayList array = null;
            MDHit lastHit = null;

            if (outputStream == null) {
                array = StreamHead.mergeStreams(heads, toBeSkipped, maxResults, false);
                if (array != null && array.size() != 0)
                    lastHit = (MDHit) array.get(array.size() - 1);
            } else {
                StreamHead.mergeStreams(heads, outputStream, toBeSkipped,
                        maxResults);
                lastHit = (MDHit) outputStream.getLastObject();
            }

            MetadataClient.QueryResult result = new MetadataClient.QueryResult();
            if (lastHit != null) {
                long atime = -1;
                if (lastHit instanceof MDHitByATime) { 
                    atime = ((MDHitByATime)lastHit).getATime();
                }
                
                result.cookie = new EMDCookie(lastHit.constructOid(), 
                                              query, 
                                              0,
                                              boundParameters, 
                                              attributes,
                                              atime);
                result.results = array;
            } else {
                result.cookie = null;
                result.results = new ArrayList();
            }
            return (result);

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to run the distributed query", e);

            String str = BundleAccess.getInstance().getBundle().getString(
                    "err.emd.query.io");
            Object[] args = { query };
            LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

            return (null);
        } finally {
            // Close the connections
            for (int i = 0; i < sockets.length; i++) {
                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                        sockets[i] = null;
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
    
    public Cookie querySeek(String query, int index, Object[] boundParameters, ArrayList attributes) {
        return new EMDCookie(null, query, index, boundParameters, attributes);
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
                                                          MDOutputStream outputStream) 
        throws EMDException {
        // Sanity checks
        if ((_cookie != null)
            && (maxResults == -1)) {
            // Has to specify an offset AND a row count
            throw new EMDException("Invalid argument : when " +
                                   "using cookies, you have to " +
                                   "specify the number of " +
                                   "entries to return");
        }

        ConnectionFactory.DiskConnection[] connections = ConnectionFactory.getConnections();
        Socket socket = null;
        ArrayList sockets = new ArrayList();
        ObjectBroker[] brokers = null;
        StreamHead[] heads = null;

        try {

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

            // Construct the brokers and launch the queries
            for (int i=0; i<connections.length; i++) {
                try {
                    socket = ConnectionFactory.connect(connections[i]);
                    sockets.add(socket);
                } catch (IOException e) {
                    LOG.warning("Failed to connect to node "+connections[i].getNodeAddress()
                                +". Skipping this node ["+e.getMessage()+"]");

                    String str = BundleAccess.getInstance().getBundle().getString("warn.emd.selectUnique.io");
                    Object [] args = {connections[i].toString()};
                    LOG.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));

                    throw new EMDException("Failed to connect to node " + 
                                           connections[i].getNodeAddress() +
                                           ". Skipping this node.",e);

                }
            }

            brokers = new ObjectBroker[sockets.size()];
            heads = new StreamHead[sockets.size()];
            Iterator iter = sockets.iterator();
            
            for (int i=0; i<brokers.length; i++) {
                brokers[i] = new ObjectBroker((Socket)iter.next());
                brokers[i].launchSelectUniqueClient(cacheId, 
                                                    query,
                                                    attribute, 
                                                    lastAttribute ,
                                                    maxResults+toBeSkipped, 
                                                    timeout, 
                                                    forceResults,
                                                    boundParameters);
                heads[i] = new StreamHead(brokers[i]);
            }

            // Merge the result and compute the output
            ArrayList array = null;
            Object lastHit = null;

            if (outputStream == null) {
                array = StreamHead.mergeStreams(heads, toBeSkipped, maxResults, false);
                if (array != null && array.size() != 0)
                    lastHit = array.get(array.size()-1);
            } else {
                StreamHead.mergeStreams(heads, outputStream, toBeSkipped, maxResults);
                lastHit = outputStream.getLastObject();
            }
            
            MetadataClient.SelectUniqueResult result = new MetadataClient.SelectUniqueResult();
            if (lastHit != null) {

		if (array != null){
		    ArrayList stringList = new ArrayList();
		    for (int i=0; i<array.size(); i++) {
			stringList.add(array.get(i).toString());
		    }
		    result.results = new StringList(stringList);
		}
		
                result.cookie = new EMDCookie(lastHit.toString(),
                                              query,
                                              attribute,
                                              0,
                                              boundParameters);
            } else {
                result.cookie = null;
                result.results = StringList.EMPTY_LIST;
            }
            
            return(result);
            
        } catch (IOException e) {
            EMDException newe = new EMDException("Failed to run the distributed select unique ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } finally {
            // Close the connections
            Iterator iter = sockets.iterator();
            while (iter.hasNext()) {
                try {
                    ((Socket)iter.next()).close();
                } catch (IOException ignored) {
                }
            }
        }
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
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("setMetadata has been called ["+cacheId
                     + " - "+oid+" - " + argument+"]");
        }
        
        ConnectionFactory.DiskConnection[] connections 
            = ConnectionFactory.getConnections(oid.getLayoutMapId(),
                                               cacheId);

        int inserts = setMetadata(cacheId, connections, oid, argument);
 
        // The number of required caches is defined as the number of
        // redundant fragments, plus one, since if we lose that number of
        // fragments we've lost data.
        int nbOfNeededInserts = OAClient.getInstance().getReliability()
                                                .getRedundantFragCount() + 1;
        
        // Normally we only insert into the 1st three fragments system
        // caches. If we didn't succeed in all of these, we attempt to
        // insert into every fragments system cache. This helps prevent 
        // cases of having missing entries at query time, since query
        // will query all caches, including those for fragments 3-6. These
        // extra entries will be cleaned up by DataDoctor as it heals
        if (cacheId.equals(CacheInterface.SYSTEM_CACHE) 
                && inserts < nbOfNeededInserts) {
 
            // Re-acquire connects, forcing all caches to be used.
            connections = ConnectionFactory.getConnections(oid.getLayoutMapId(),
                                                           cacheId, true);
            int retriedInserts = setMetadata(cacheId, connections, oid, argument);
            
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine ("Inserted " + retriedInserts + " records into " 
                              + "system caches after initial attempts inserted "
                              + inserts + " for oid " + oid);
            }
            
            // If we failed to get the required number of cache inserts after
            // retrying all fragments' system caches, alert the System Cache
            // state machine (via ClusterProperties via MgmtServer)
            if (! (retriedInserts >= nbOfNeededInserts)) {
                if (LOG.isLoggable(Level.INFO)) {
                    LOG.info ("Alerting SysCache state machine of "
                            + "failure to insert records for " + oid 
                            + ". " + retriedInserts + " of " + nbOfNeededInserts
                            + " inserts performed after initial attempts"
                            + " inserted " + inserts);
                }
                try {
                    getMgmtProxy().setSyscacheInsertFailureTime(
                                                    System.currentTimeMillis());
                } catch (Exception e) {
                    String error = 
                        "Failing to store oid " + oid.toString() 
                        + " due to syscache insert failures (needed "
                        + nbOfNeededInserts + ", inserts " + inserts 
                        + ", second inserts " + retriedInserts 
                        + ") and failure to notify state machine: " 
                        + e.getMessage();
                    LOG.log(Level.SEVERE,error,e);
                    throw new SyscacheInsertionFailureException (error,e);
                }
            }
        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("The metadata have been set for object "+oid);
        }
        return false;
    }
        
    public boolean setMetadata(String cacheId,
			       Map items) {

	throw new InternalException("setMetadata(Map) not implemented in EMDClient");
    }


    private int setMetadata(String cacheId,
                             ConnectionFactory.DiskConnection[] connections,
                             NewObjectIdentifier oid,
                             Object argument) {

        Socket[] sockets = new Socket[connections.length];
        ObjectBroker[] brokers = new ObjectBroker[connections.length];

        int inserts = 0;
        
        for (int i=0; i<connections.length; i++) {
            try {
                sockets[i] = ConnectionFactory.connect(connections[i]);
                brokers[i] = new ObjectBroker(sockets[i]);
                if (LOG.isLoggable(Level.FINE)) {
                    StringBuffer log = new StringBuffer();
                    log.append("RPC for setMetadata to ");
                    connections[i].toString(log);
                    LOG.fine(log.toString());
                }
                brokers[i].launchSetMetadata(cacheId, oid, argument, connections[i].getDisks());
            } catch (ConnectException e) {
                LOG.warning("Failed to update the metadata on ["+connections[i]+
                            "] for oid "+oid+
                            " [cache "+cacheId+"]. Error is ["+
                            e.getMessage()+"]");



                String str = BundleAccess.getInstance().getBundle().getString("err.emd.setMetadata.io");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));


                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;
                }
                brokers[i] = null;
            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Failed to update the metadata on ["+connections[i]+
                        "] for oid "+oid+
                        " [cache "+cacheId+"]",
                        e);

                String str = BundleAccess.getInstance().getBundle().getString("err.emd.setMetadata.io");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));


                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;
                }
                brokers[i] = null;
            } catch (EMDException e ) {
                LOG.log(Level.SEVERE,
                        "Failed to update the metadata on ["+connections[i]+
                        "] for oid "+oid+
                        " [cache "+cacheId+"]",
                        e);
                String str = BundleAccess.getInstance().getBundle().getString("err.emd.setMetadata.cache");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;
                }
                brokers[i] = null;
            }
        }

        for (int i=0; i<connections.length; i++) {
            try {
                if (brokers[i] != null) {
                    brokers[i].waitForCompletion();
                    inserts++;
                }
            } catch (EMDException e) {
                LOG.log(Level.SEVERE,
                        "The setMetadata operation didn't return properly from ["+connections[i]+
                        "] for oid "+oid+
                        " [cache "+cacheId+"]",
                        e);

                String str = BundleAccess.getInstance().getBundle().getString("err.emd.setMetadata.cache");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));


            } finally {
                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Metadata insert succeeded with " + inserts 
                         + " caches successfully updated.");
        }
        return inserts;
    }

    /** 
     * Called to populate caches on given disk (not used during STORE).
     * Inserts into BOTH the (1) system cache, with the given systemMD,
     * and the (2) non-system cache designated by the cacheId.  
     * 
     * @param systemMD  extracted from the fragment file
     * @param cacheID   cache in which to store the md
     * @param disk      only insert into cache on this disk
     */
    public void setMetadata(SystemMetadata systemMD, String cacheId,
                            Disk disk) {

        LOG.fine("setMetadata for oid "+systemMD.getOID()+
                 " on disk ["+disk+"]");

        // get connection to the desired disk
        ConnectionFactory.DiskConnection[] connections = makeConn(disk);

        // set system metadata
        setSysMetadata(systemMD, disk, connections);

        // if cacheId represents system cache, we're done
        if (cacheId == null || 
                cacheId.equals(CacheClientInterface.SYSTEM_CACHE)) {
            return;
        }

        setExtMetadata(systemMD.getOID(), cacheId, disk, connections);

    }

    /** 
     * Sets the system metadata on the given disk (not extended MD) 
     * 
     * @param systemMD  extracted from the fragment file
     * @param disk      only insert into cache on this disk
     */
    public void setSysMetadata(SystemMetadata systemMD, Disk disk) {

        LOG.fine("setSysMetadata for oid "+systemMD.getOID()+
                 " on disk ["+disk+"]");

        setSysMetadata(systemMD, disk, makeConn(disk));
    }

    /** 
     * Sets metadata for cacheId on given disk (not system MD) 
     *
     * @param oid       oid of the Metadata object we're setting
     * @param cacheId   cache in which to store the md
     * @param disk      only insert into cache on this disk
     */
    public void setExtMetadata(NewObjectIdentifier oid,
                               String cacheId, Disk disk) {

        LOG.fine("setExtMetadata for oid "+oid+" on disk ["+disk+"]");

        setExtMetadata(oid, cacheId, disk, makeConn(disk));
    }
        
    /** 
     * Check if the given disk is used to store metadata for an object
     * with the specified layoutMapId.  Used to repopulate local caches.
     *
     * @param layoutMapId       layoutMapId for the object of interest      
     * @param cacheId           either system or an extended cache
     */
    public boolean usesDisk(int layoutMapId, String cacheId, Disk disk) {

        ArrayList disks = MDDiskAbstraction.getInstance((byte)0)
                          .getUsedDisksFromMapId(layoutMapId, cacheId);
        return disks.contains(disk);
    }

    
    /** Sets the system metadata on the given disk (not extended MD) */
    private void setSysMetadata(SystemMetadata systemMD, Disk disk,
                        ConnectionFactory.DiskConnection[] connections) {

        // only insert if layoutMapId still maps to this disk
        String sysCacheId = CacheClientInterface.SYSTEM_CACHE; 
        if (usesDisk(systemMD.getLayoutMapId(), sysCacheId, disk)) {
            // set the system metadata
            setMetadata(sysCacheId, connections, 
                        systemMD.getOID(), systemMD);
        }
    }

    /** Sets metadata for cacheId on given disk (not system MD) */
    private void setExtMetadata(NewObjectIdentifier oid,
                        String cacheId, Disk disk,
                        ConnectionFactory.DiskConnection[] connections) {

        // not all layout disks necessarily used, depends on reduncancy
        if (usesDisk(oid.getLayoutMapId(), cacheId, disk)) {
            try {
                CacheRecord metadataObject = CacheManager.getInstance().
                                            getClientInterface(cacheId).
                                            generateMetadataObject(oid);
                // Set the metadata
                setMetadata(cacheId, connections, oid, metadataObject);
            } catch (EMDException e) {
                LOG.log(Level.SEVERE, "Failed to update the metadata ["+
                        e.getMessage()+"]", e);
            }

        }
    }

    /** Get a connectioin to the given disk */
    private ConnectionFactory.DiskConnection[] makeConn(Disk disk) {

        ConnectionFactory.DiskConnection[] connections = 
            new ConnectionFactory.DiskConnection[1];
        connections[0] = ConnectionFactory.getConnection(disk);

        return connections;
    }

    /** 
     * Open a socket for the given connection, null if failed.
     *
     * @param connections       connection array of size 1
     */
    private Socket[] makeSock(ConnectionFactory.DiskConnection[] 
                                connections)  {

        if (connections.length != 1) {
            throw new IllegalArgumentException("expected only one "+
                "connection, but array has lenght "+connections.length);
        }

        Socket[] sockets = new Socket[1];
        try {
            sockets[0] = ConnectionFactory.connect(connections[0]);
        } catch (IOException ioe) {
            // failed to connect to cache, wierd because this is local
            if (sockets[0] != null) {
                try {
                    sockets[0].close();
                } catch (IOException ignored) {
                }
            }
            return null;
        }

        return sockets;
    }

    /**
     * This <code>setMetadata</code> method is the OA callback to populate
     * a local cache.
     *
     * @param <code>SystemMetadata</code> are the SystemMetadata extracted
     * from the local fragment
     * @param <code>Disk</code> is the disk containing the fragment
     *
     * note: this method DEPRECIATED, crawl uses above methods instead.  
     * Kept here for reference until replacement methods are validated.
     */
    
    public void setMetadata(SystemMetadata systemMD,
                            byte[] MDField,
                            Disk disk) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("setMetadata has been called for oid "+systemMD.getOID()
                     +" on disk ["+disk+"]");
        }

        // Precompute the connection
        ConnectionFactory.DiskConnection[] connections = new ConnectionFactory.DiskConnection[1];
        connections[0] = ConnectionFactory.getConnection(disk);
            
        // Check if the system metadata should be set
        ArrayList disks = MDDiskAbstraction.getInstance((byte)0)
            .getUsedDisksFromMapId(systemMD.getLayoutMapId(),
                                   CacheClientInterface.SYSTEM_CACHE);
        if (disks.contains(disk)) {
            setMetadata(CacheClientInterface.SYSTEM_CACHE, connections, systemMD.getOID(), systemMD);
        }
        
        try {
            // Find the cacheId
            MDHeader header = new MDHeader(MDField);
        
            String cacheId = header.getCacheId();

            if ( cacheId != null ) {
                disks = MDDiskAbstraction.getInstance((byte)0)
                    .getUsedDisksFromMapId(systemMD.getLayoutMapId(),
                                           cacheId);
                
                if (disks.contains(disk)) {
                    CacheRecord metadataObject = CacheManager.getInstance().getClientInterface(cacheId)
                        .generateMetadataObject(systemMD.getOID());
                    
                    // Set the metadata
                    setMetadata(cacheId, connections, systemMD.getOID(), metadataObject);
                }
            }
            
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to update the metadata ["+e.getMessage()+"]",
                    e);
        } catch (EMDException e) {
            LOG.log(Level.SEVERE,
                    "Failed to update the metadata ["+e.getMessage()+"]",
                    e);
        }
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

        ConnectionFactory.DiskConnection[] connections = new ConnectionFactory.DiskConnection[1];
        connections[0] = ConnectionFactory.getConnection(disk);
        String cacheId = header.getCacheId();
        
        // Remove the system Metadata
        removeMetadata(oid, CacheClientInterface.SYSTEM_CACHE, connections);

        // Remove the other metadata entry
        if (cacheId != null) {
            removeMetadata(oid, cacheId, connections);
        }
    }
    
    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId) 
        throws EMDException {
        // Remove the system Metadata
        ConnectionFactory.DiskConnection[] connections = null;
        connections = ConnectionFactory.getConnections();
        removeMetadata(oid, CacheClientInterface.SYSTEM_CACHE, connections);
        
        LOG.info("Removed the system metadata for "+oid);

        // Clean the other cache
        if (cacheId != null) {
            removeMetadata(oid, cacheId, connections);
            if (LOG.isLoggable(Level.FINE)) {
                StringBuffer log = new StringBuffer();
                for (int i=0; i<connections.length; i++) {
                    log.append(" - ");
                    connections[i].toString(log);
                }
                LOG.fine("Removed the "+cacheId+" metadata for "+oid+"["+
                         log.toString()+"]");
            }
        }
    }

    public void removeMetadata(NewObjectIdentifier oid,
                               String cacheId,
                               Disk disk ) {
        ConnectionFactory.DiskConnection[] connections = {ConnectionFactory.getConnection(disk)}; 
        removeMetadata(oid, cacheId, connections);
    }
    
    private void removeMetadata(NewObjectIdentifier oid,
                                String cacheId,
                                ConnectionFactory.DiskConnection[] connections) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("removeMetadata has been called for oid "+oid);
        }
        
        // Precompute the connection
        Socket socket = null;

        for (int i=0; i<connections.length; i++) {
            try {
                socket = ConnectionFactory.connect(connections[i]);
                ObjectBroker broker = new ObjectBroker(socket);
                broker.removeMetadataClient(cacheId, oid, connections[i].getDisks());
            } catch (ConnectException e) {
                LOG.warning("Couldn't remove the "+cacheId+" metadata for ["+
                            oid+"] from ["+connections[i].getNodeAddress()+"]. Error is ["+
                            e.getMessage()+"]");

                String str = BundleAccess.getInstance().getBundle().getString("err.emd.removeMetadata.io");

                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));


            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Couldn't remove the "+cacheId+" metadata for ["+
                        oid+"] from ["+connections[i].getNodeAddress()+"]",
                        e);
		
                String str = BundleAccess.getInstance().getBundle().getString("err.emd.removeMetadata.io");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

            } catch (EMDException e) {
                LOG.log(Level.SEVERE,
                        "Couldn't remove the "+cacheId+" metadata for ["+
                        oid+"] from ["+connections[i].getNodeAddress()+"]",
                        e);
                String str = BundleAccess.getInstance().getBundle().getString("err.emd.removeMetadata.cache");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    socket = null;
                }
            }
        }
    }

    public void updateSchema()
        throws EMDException {
        throw new EMDException("updateSchema is not implemented for the distributed caches");
    }

    public void clearFailure()
        throws EMDException {
        throw new EMDException("clearFailure is not implemented for the distributed caches");
    }

    public int getCacheStatus()
        throws EMDException {
        throw new EMDException("getCacheStatus is not implemented for the distributed caches");
    }

    public String getEMDCacheStatus()
        throws EMDException {
        throw new EMDException("getEMDCacheStatus is not implemented for the distributed caches");
    }

    public long getLastCreateTime() {
        return 0;
    }

    /**********************************************************************
     *
     * Legal hold API
     *
     **********************************************************************/    
    /** Adds a legal hold to the system cache on the given disk **/
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold,
                             Disk disk) {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Ading (" + oid + ", " + legalHold + ") to disk " + disk);
        }

        String sysCacheId = CacheClientInterface.SYSTEM_CACHE; 
        if (usesDisk(oid.getLayoutMapId(), sysCacheId, disk)) {

	    // get connection to the desired disk
	    ConnectionFactory.DiskConnection[] connections = makeConn(disk);

            // set the system metadata
            addLegalHold(connections, oid, legalHold);
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OID " + oid + " DOES NOT use cache "
                         + sysCacheId + " on disk " + disk);
            }
        }
    }

    /** Add a legal hold given a cache id **/
    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold) {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Adding (" + oid + ", " + legalHold + ")");
        }

        String sysCacheId = CacheClientInterface.SYSTEM_CACHE;
        ConnectionFactory.DiskConnection[] connections 
            = ConnectionFactory.getConnections(oid.getLayoutMapId(), sysCacheId);

        addLegalHold(connections, oid, legalHold);
    }

    /** Private addLegalHold **/
    private void addLegalHold(ConnectionFactory.DiskConnection[] connections,
                              NewObjectIdentifier oid,
                              String legalHold) {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Ading (" + oid + ", " + legalHold + ")");
        }

        if (connections == null) {
            LOG.severe("connections is null");
            return;
        }

        String sysCacheId = CacheClientInterface.SYSTEM_CACHE;
        Socket[] sockets = new Socket[connections.length];
        ObjectBroker[] brokers = new ObjectBroker[connections.length];

        for (int i=0; i<connections.length; i++) {
            try {
                sockets[i] = ConnectionFactory.connect(connections[i]);
                brokers[i] = new ObjectBroker(sockets[i]);
                if (LOG.isLoggable(Level.FINE)) {
                    StringBuffer log = new StringBuffer();
                    log.append("RPC for addLegalHold to ");
                    connections[i].toString(log);
                    LOG.fine(log.toString());
                }
                brokers[i].launchAddLegalHold(sysCacheId, oid, legalHold,
                                              connections[i].getDisks());
            } catch (ConnectException e) {
                LOG.warning("Failed to update the legal hold on [" + 
                            connections[i]+
                            "] for oid " + oid +
                            " [cache " + sysCacheId + "]. Error is [" +
                            e.getMessage() + "]");

                String str = BundleAccess.getInstance().getBundle().
                    getString("err.emd.addLegalHold.io");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;
                }
                brokers[i] = null;
            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Failed to add a legal hold on ["+connections[i]+
                        "] for oid " + oid +
                        " [cache " + sysCacheId + "]",
                        e);

                String str = BundleAccess.getInstance().getBundle().
                    getString("err.emd.addLegalHold.io");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;
                }
                brokers[i] = null;
            } catch (EMDException e ) {
                LOG.log(Level.SEVERE,
                        "Failed to update the metadata on ["+connections[i]+
                        "] for oid "+oid+
                        " [cache "+sysCacheId+"]",
                        e);
                String str = BundleAccess.getInstance().getBundle().
                    getString("err.emd.addLegalHold.cache");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                    sockets[i] = null;
                }
                brokers[i] = null;
            }
        }

        for (int i=0; i<connections.length; i++) {
            try {
                if (brokers[i] != null) {
                    brokers[i].waitForCompletion();
                }
            } catch (EMDException e) {
                LOG.log(Level.SEVERE,
                        "The addLegalHold operation didn't return " +
                        "properly from ["+connections[i]+
                        "] for oid "+oid+
                        " [cache " + sysCacheId + "]",
                        e);

                String str = BundleAccess.getInstance().getBundle().
                    getString("err.emd.addLegalHold.cache");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));


            } finally {
                if (sockets[i] != null) {
                    try {
                        sockets[i].close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    // Remove the legal hold
    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold) {
        ConnectionFactory.DiskConnection[] connections = null;
        connections = ConnectionFactory.getConnections();
        removeLegalHold(oid, legalHold, connections);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Removed legal hold [" + legalHold + "] for OID " + oid);
        }
    }

    private void removeLegalHold(NewObjectIdentifier oid,
                                 String legalHold,
                                 ConnectionFactory.
                                 DiskConnection[] connections) {

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("removeLegalHold has been called for oid " + oid);
        }

        String sysCacheId = CacheClientInterface.SYSTEM_CACHE; 
        
        // Precompute the connection
        Socket socket = null;

        for (int i=0; i<connections.length; i++) {
            try {
                socket = ConnectionFactory.connect(connections[i]);
                ObjectBroker broker = new ObjectBroker(socket);
                broker.launchRemoveLegalHold(sysCacheId, oid, legalHold,
                                             connections[i].getDisks());
            } catch (ConnectException e) {
                LOG.warning("Couldn't remove the " + sysCacheId +
                            " legal hold for [" + oid + 
                            "] from [" + connections[i].getNodeAddress() +
                            "]. Error is [" + e.getMessage() + "]");

                String str = BundleAccess.getInstance().getBundle().
                    getString("err.emd.removeLegalHold.io");

                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

            } catch (IOException e) {
                LOG.log(Level.SEVERE,
                        "Couldn't remove the " + sysCacheId +
                        " legal hold for [" + oid +
                        "] from [" + connections[i].getNodeAddress() + "]",
                        e);
		
                String str = BundleAccess.getInstance().getBundle().
                    getString("err.emd.removeMetadata.io");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

            } catch (EMDException e) {
                LOG.log(Level.SEVERE,
                        "Couldn't remove the " + sysCacheId +
                        " legal hold for [" + oid + "] from [" +
                        connections[i].getNodeAddress() + "]",
                        e);
                String str = BundleAccess.getInstance().getBundle().
                    getString("err.emd.removeMetadata.cache");
                Object [] args = {connections[i].toString()};
                LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    socket = null;
                }
            }
        }
    }
    
    static public MgmtServerIntf getMgmtProxy() {
        Object obj = ServiceManager.proxyFor(ServiceManager.LOCAL_NODE);
        if (! (obj instanceof NodeMgrService.Proxy))
            throw new RuntimeException (
                    "Can't get node manager service from EMDClient");
        NodeMgrService.Proxy nodeMgr = (NodeMgrService.Proxy) obj;
        Node master = nodeMgr.getMasterNode();
        if (null==master)
            throw new RuntimeException ("Can't get master node pointer.");
        ManagedService.ProxyObject proxy =
            ServiceManager.proxyFor(master.nodeId(), "MgmtServer");
        if(null==proxy)
            throw new RuntimeException ("Proxy is null... that's baaaad.");
        if (! (proxy instanceof MgmtServerIntf.Proxy))
            throw new RuntimeException 
                ("Can't get MgmtServerIntf from master node.");
        ManagedService.RemoteInvocation api = proxy.getAPI();
        if (null==api || ! (api instanceof MgmtServerIntf))
            throw new RuntimeException ("Unexpected value for MgmtServer API");
        return ((MgmtServerIntf)api); 
    }
}
