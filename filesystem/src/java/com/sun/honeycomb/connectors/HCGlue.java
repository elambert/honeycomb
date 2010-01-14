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



package com.sun.honeycomb.connectors;

import com.sun.honeycomb.fscache.FSCacheException;

import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.CanonicalStrings;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.StringUtil;

import com.sun.honeycomb.coordinator.Coordinator;

import com.sun.honeycomb.emd.MetadataClient.QueryResult;
import com.sun.honeycomb.emd.MetadataClient;

import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.common.QueryMap;

import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.config.Filename;
import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;

import com.sun.honeycomb.emd.remote.MDOutputStream;

import com.sun.honeycomb.protocol.server.ProtocolService;
import com.sun.honeycomb.protocol.server.RetrieveHandler;
import com.sun.honeycomb.protocol.server.ServiceRegistration.ContextRegistration;
import com.sun.honeycomb.protocol.server.ServiceRegistration.EventRegistrant;
import com.sun.honeycomb.protocol.server.ServiceRegistration;

import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ByteBufferPool;

import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.common.BandwidthStatsAccumulator;
import com.sun.honeycomb.common.StatsAccumulator;

import com.sun.honeycomb.fscache.FSCache;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.OutputStream;

import java.beans.PropertyChangeListener;

import java.net.URLDecoder;
import java.text.NumberFormat;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Date;
import java.util.Properties;
import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpContext;


/**
 * The interface used by everything in the filesystem module to talk to
 * HC.
 */
public class HCGlue extends HCInterface {
    private static final Logger logger =
        Logger.getLogger(HCGlue.class.getName());
 
    public static BandwidthStatsAccumulator putStats = new BandwidthStatsAccumulator();
    public static BandwidthStatsAccumulator getStats = new BandwidthStatsAccumulator();
   
    private static final String ECACHE = CacheInterface.EXTENDED_CACHE;

    private static MetadataClient mdClient = null;
    private static Coordinator coord = null;

    private static boolean initialized = false;
    private static RetrieveHandler handler = null;
    private static HCGlue theInstance = null;
    private static int readBufferSize = 0;

    private static NumberFormat byteFormatter = null;
    private static NumberFormat timeFormatter = null;

    // Workaround for CR 6676969: limit max no. of values returned to
    // some fraction of the fs cache size
    private static final float CR6676969_LIMIT = 50f; // percent

    private int valuesLimit;

    static ArrayList oidAttr  = null;

    /** Get the singleton instance that implements the interface */
    public static HCInterface getInstance() {
        initialise();
        return theInstance;
    }

    private static synchronized void initHandler() {
        if (handler != null)
            return;

        ProtocolService protocol = ProtocolService.getProtocolService();
        Class handlerClass = RetrieveHandler.class;

        HttpContext ctx = protocol.getContext(ProtocolConstants.RETRIEVE_PATH);
        if (ctx == null)
            throw new InternalException("Retrieve handler not registered");

        if ((handler = (RetrieveHandler) ctx.getHandler(handlerClass)) == null)
            throw new InternalException("Couldn't get retrieve handler");

        readBufferSize = Coordinator.getInstance().getReadBufferSize();
    }

    private static synchronized void initialise() {
        if (initialized)
            return;

        byteFormatter = NumberFormat.getNumberInstance();
        byteFormatter.setMaximumFractionDigits(1);
        timeFormatter = NumberFormat.getNumberInstance();
        timeFormatter.setMaximumFractionDigits(2);

        theInstance = new HCGlue();

        oidAttr = new ArrayList();
        oidAttr.add(FIELD_OID);

        mdClient = MetadataClient.getInstance();
        coord = Coordinator.getInstance();
        MDHandler.init();
        initialized = true;
    }

    protected HCGlue() {
    }

    public void init() {
    }

    /*
     * Here are some things to keep in mind about the HADB layer (md_clustra):
     *
     *  - You don't want to introduce joins /between/ tables just to
     *    get the desired attributes.  The queries will be faster if
     *    they can stay within a single table.
     *
     *    For example, if the desired attribute is filesystem.mimetype
     *    and the query is on a bunch of fields in davtest, then based
     *    on past experience we are better off doing a davtest query
     *    to get the oid and then a filesystem table query to get the
     *    mimetype.  (This might be worth some experimentation at
     *    different sizes of data set to see if there is a crossover
     *    point where the join is just as fast.)

     *  - We do have a new capability in queryPlus to generate outer
     *    join instead of inner join.  If some of the attributes might
     *    have a NULL value, then this would preserve the rows that
     *    lack such a value, rather than eliminating such rows from
     *    the result set.
     *
     *  - Our kind of outer join only makes sense to use when you are 
     *    already having to do a join across tables.  Because it bends
     *    over backwards to preserve rows with null values, it
     *    actually always does a join against the system table, since
     *    that is the only table that will always have a value.  So if
     *    you have a one-table query, you are better off making it
     *    just a normal inner join and then fetching the attributes
     *    later as described above.
     *
     * So a general plan of attack would be to determine if the needed
     * query is a one-table query or a multiple-table query.  For a
     * multiple-table query we would suspect we are always better off
     * using the outer join and selecting all the attributes.  For a
     * one-table query, we suspect we would be better off to do the
     * single-table query by itself, and then one query per other
     * table that holds some of the desired attributes, eg. one query
     * for the system table and one for the filesystem table in the
     * common case.
     *
     *                         -- [comments from Peter C. 23 Feb 2007]
     *
     * There are a couple of good things about webdav queries:
     * ignoring the "unset=" feature for now, if any of the query
     * attributes is unset, the object does not appear in the view.
     * The only "optional" attributes are the POSIX attributes
     * filesystem -- which is always an extra query, controlled by
     * "fsattrs=true" in the view.  This means a query needs only
     * inner joins.
     *
     * In the future (esp. when the "unset=" feature is implemented),
     * this code needs to be aware of tables. If the query attributes
     * span multiple tables, use one outer join queryPlus for
     * everything (including optional attributes like filesystem.*).
     */

    /*
     * A note about outer joins:    
     *
     * If you start using outer joins, you would then modify the logic
     * in QueryConvert.populateRegistry to leave out the automatic
     * inclusion of the system table.  I am pretty sure we don't need
     * that for now.
     *
     * (Hmmm.  Here is the case where we might need the join against
     * the system table in the future: suppose there is a query that
     * lists A IS NULL and B IS NULL and .... and Z is NULL.  And
     * suppose there is an object with none of A to Z set and no value
     * in any of the tables where A to Z reside set.  You might expect
     * such an object to show up in the query results but it won't,
     * unless you include the system table.  I am happy just making a
     * release note about such a limitation for now...)
     *                                   -- Peter C. 27 Feb 2007
     */

    /**
     * A metadata query. Results are returned to listener via callback. 
     */
    public int query(String[] names, String[] values, ArrayList requiredAttrs,
                     MDListener listener) {
        Object[] boundParameters;

        if (logger.isLoggable(Level.FINE)) {
            String delim = "";
            StringBuffer sb = new StringBuffer();
            sb.append("Running query ");
            sb.append(toString(names, values, requiredAttrs));
            logger.fine(sb.toString());
        }

        BoundQuery q = makeQuery(names, values, requiredAttrs);

        int numResults = 0;
        long startTime = System.currentTimeMillis(), elapsed;

        // Workaround for bug 6676969: limit # results to X % of the cache
        valuesLimit = (int)
            (0.5 + FSCache.getInstance().getCacheSize()*CR6676969_LIMIT/100);

        if (values.length < names.length)
            // Instead of using selectUnique, just use QueryPlus to
            // get matching objects and manually build up a Set of
            // their attrName values
            numResults = 
                getUniqueValues(q.query, names[values.length], q.boundParameters,
                                listener);

        else if (values.length == names.length)
            numResults = getAllObjects(q.query, requiredAttrs, q.boundParameters, 
                                       listener);

        else
            throw new InternalException("Too many values in query");
        elapsed = System.currentTimeMillis() - startTime;
       
        if (logger.isLoggable(Level.INFO)) {
            StringBuffer msg = new StringBuffer("INSTR ");

            msg.append(elapsed);
            msg.append(" QUERY ").append(q.toString());
            msg.append(" [").append(numResults).append("] ");

            logger.info(msg.toString());
        }

        return numResults;
    }

    /**
     * Given a list of needed attributes and an OID, return an array
     * (in canonical string format) of the needed values for that OID.
     *
     * This is a unique query so we don't need callbacks.
     */
    public String[] queryObject(NewObjectIdentifier oid,
                                ArrayList desiredAttrs) {

        String[] values = new String[desiredAttrs.size()];
        for (int i = 0; i < values.length; i++)
            values[i] = null;

        /*
         * If all the attributes in desiredAttrs are in the same
         * table, one table scan will find them all. However if they
         * are in multiple tables, an outer join will need to be
         * done. Instead, partition the attributes by table and do one
         * query for each subset.
         *
         * We're going to defer this correct solution; for now, do one
         * query for system.* attributes and one for filesystem.*;
         * then one for the remaining.
         */

        ArrayList[] attrLists = new ArrayList[3];

        for (int i = 0; i < attrLists.length; i++)
            attrLists[i] = new ArrayList();

        for (int i = 0; i < desiredAttrs.size(); i++) {
            String attrName = (String) desiredAttrs.get(i);
            if (attrName.startsWith("system."))
                attrLists[0].add(attrName);
            else if (attrName.startsWith("filesystem."))
                attrLists[1].add(attrName);
            else
                attrLists[2].add(attrName);
        }

        // Do the required number of queries

        Map allValues = new HashMap();
        for (int i = 0; i < attrLists.length; i++)
            if (attrLists[i].size() > 0)
                queryAttributes(oid, attrLists[i], allValues);

        // and collect the results together

        for (int i = 0; i < desiredAttrs.size(); i++) {
            String attrName = (String) desiredAttrs.get(i);
            values[i] = (String) allValues.get(attrName);
        }
        return values;
    }

    /** Get the MD values and add them to the Map */
    private void queryAttributes(NewObjectIdentifier oid,
                                 ArrayList desiredAttrs, Map values) {

        String query = "\"" + FIELD_OID + "\"=?";

        // By using a byte[] here we mean to search for the Internal OID
        Object[] boundParameters = { oid.getDataBytes()};

        if (logger.isLoggable(Level.INFO)) {
            String s = toString(query, desiredAttrs, boundParameters);
            logger.info("Querying " + s + " to Map " + values);
        }

        String request = null;
        String msg = "";

        long startTime = System.currentTimeMillis();
        long elapsed = 0;

        QueryMap mdvalues = null;
        try {
            // Since some of the requested attributes may be unset,
            // we request an outer join of all the tables that
            // desiredAttrs live in.

            QueryResult rv =
                mdClient.queryPlus(ECACHE, query, desiredAttrs,
                                   null, 1, -1, true, boundParameters);
            elapsed = System.currentTimeMillis() - startTime;

            if (rv == null || rv.results == null || rv.results.size() == 0) {
                // No values

                if (logger.isLoggable(Level.INFO)) {
                    String s = toString(query, desiredAttrs, boundParameters);
                    if (logger.isLoggable(Level.FINE))
                        logger.fine("No attributes: " + s);
                    logger.info("INSTR " + elapsed + " QUERYobj " + s);
                }

                return;
            }

            MDHit h = (MDHit) rv.results.get(0);
            mdvalues = (QueryMap) h.getExtraInfo();
        }
        catch (EMDException e) {
            logger.log(Level.SEVERE,
                       toString(query, desiredAttrs, boundParameters), e);
            throw new InternalException(e);
        }

        for (int i = 0; i < desiredAttrs.size(); i++)
            try {
                String name = (String) desiredAttrs.get(i);
                Object value = mdvalues.get(name);

                if (logger.isLoggable(Level.FINE))
                    msg += " " + name + "=<" + value + ">";

                // Convert result to Canonical String output.
                values.put(name, CanonicalStrings.encode(value, name));
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, request, e);
                throw new InternalException(e);
            }

        if (logger.isLoggable(Level.FINE))
            logger.fine(msg);

        if (logger.isLoggable(Level.INFO))
            logger.info("INSTR " + elapsed + " QUERYobj " + 
                        toString(query, desiredAttrs, boundParameters));
    }

    /** Get an arbitrary object that satisfies the names/values */
    public HCObject getObject(String[] names, String[] values) {
        long startTime = 0, elapsed = 0;

        BoundQuery q = null;
        try {
            q = makeQuery(names, values);
        }
        catch (IllegalArgumentException e) {
            // This means the query cannot possibly match. It's not
            // necessarily an error -- CanonicalStrings.decode()
            // throws this exception (via getObjectValue()) when it
            // couldn't parse a value. This is OK if this is object is
            // in a "genfs" view, so we don't log a warning.
            logger.log(Level.FINE, "Couldn't find a matching object", e);
            return null;
        }
        catch (Exception e) {
            // We don't know what this is: log it and move on. We really
            // should pass the exception up and handle whatever error it is
            // gracefully, but that requires some changes in the code that
            // calls this method -- see CacheLoader.load().
            String err = "Couldn't find a matching object for " +
                toString(names, values);
            logger.log(Level.WARNING, err, e);
            return null;
        }

        /*
         * Here, we'll be better off getting the matching OID and then
         * doing a separate query to get the sysAttrs. If we put it
         * into the same query, HADB will be doing a join between the
         * system table and everything else.
         */

        if (logger.isLoggable(Level.FINE))
            logger.fine("Getting " + toString(q.query, oidAttr,
                                              q.boundParameters));

        List result = null;
        try {
            startTime = System.currentTimeMillis();

            try {
                QueryResult rv =
                    mdClient.queryPlus(ECACHE, q.query, oidAttr,
                                       null, 1, -1, true,
                                       q.boundParameters);
                elapsed = System.currentTimeMillis() - startTime;
                result = rv.results;
            }
            catch (EMDException e) {
                // If a query couldn't possibly match due to size
                // mismatch, HADB throws a SQLException instead of just
                // returning null
                logger.info("EMDException from queryPlus: " + e);
            }

            if (logger.isLoggable(Level.INFO)) {
                StringBuffer s = new StringBuffer("INSTR ");
                s.append(elapsed);
                s.append(" QUERYexist ");
                s.append(toString(q.query, q.boundParameters));

                s.append((result != null && result.size() > 0)?
                         " SUCCESS" : " FAIL");

                logger.info(s.toString());
            }

            if (result == null || result.size() <= 0)
                return null;

            MDHit h = (MDHit) result.get(0);
            // Get OID in INTERNAL form.
            NewObjectIdentifier oid = h.constructOid();

            // Now query for the system attributes of this OID

            Map sysValues = new HashMap();
            queryAttributes(oid, MDHandler.sysAttrs, sysValues);

            long ctime = 0, size = 0;

            for (int i = 0; i < MDHandler.sysAttrs.size(); i++) 
                try {
                    String name = (String) MDHandler.sysAttrs.get(i);
                    String value = (String) sysValues.get(name);
                    if (name.equals(FIELD_CTIME))
                        ctime = Long.parseLong(value);
                    if (name.equals(FIELD_SIZE))
                        size = Long.parseLong(value);
                }
                catch(NumberFormatException e) {
                    throw new InternalException(e);
                }

            // Finally! Create the object and return it.
            return new HCObject(oid, ctime, size);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, q.query, e);
            throw new InternalException(e);
        }
    }

    /** Fetch file contents */
    public boolean writeObject(NewObjectIdentifier oid, OutputStream os,
                               long offset, long length)
            throws IOException {
        long startTime = System.currentTimeMillis();
        try {
            initHandler();
            return handler.handleRetrieve(os, oid, offset, length);
        }
        catch (ArchiveException e) {}
        finally {
            long webdavGetTime = System.currentTimeMillis() - startTime; 
            getStats.add(length, webdavGetTime); 
            if (logger.isLoggable(Level.INFO))
                logger.info("INSTR " + webdavGetTime + 
                            " RETRIEVE " + oid.toExternalHexString());
        }
        return false;
    }

    /** Fetch file contents using NIO */
    public boolean writeObject(NewObjectIdentifier oid,
                               WritableByteChannel channel,
                               long wOffset, long length)
            throws IOException {
        initHandler();
        if (logger.isLoggable(Level.FINE))
            logger.fine("Writing [" + wOffset + ":+" + length + "] of " + oid);

        long offset = wOffset;
        long lastByte = offset + length;

        long startTime = System.currentTimeMillis();
        long oaStart = 0, oaElapsed = 0;

        int index = 0;
        while (offset < lastByte) {
            int toRead = (int) (lastByte - offset);

            boolean last = (toRead <= readBufferSize);
            if (!last)
                toRead = readBufferSize;

            ByteBufferList buffers = new ByteBufferList();
            try {
                if (logger.isLoggable(Level.INFO))
                    oaStart = System.currentTimeMillis();
                int nRead = coord.readData(oid, buffers, offset, toRead, last);
                offset += nRead;
                if (logger.isLoggable(Level.INFO))
                    oaElapsed += System.currentTimeMillis() - oaStart;

                if (logger.isLoggable(Level.FINE))
                    logger.fine(index + ": writing " + nRead + " bytes (" +
                                offset + "/" + lastByte + ")" );

                buffers.writeToChannel(channel);
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Coordinator exception", e);
                return false;
            } 
            finally {
                ByteBufferPool.getInstance().checkInBufferList(buffers);
            }
            index++;
        }

        long webdavGetTime = System.currentTimeMillis() - startTime; 
        if (logger.isLoggable(Level.INFO))
            logger.info("INSTR " + webdavGetTime + "," + oaElapsed +
                        " RETRIEVE /NIO/" + index + " " + 
                        oid.toExternalHexString() + " " + length);

        getStats.add(length, webdavGetTime); 
        return true;
    }

    /** Create a new file with metadata */
    public NewObjectIdentifier storeFile(Map metadata, InputStream inp)
            throws IOException {
        ExtendedCacheEntry md = new ExtendedCacheEntry();

        StringBuffer sb = null;
        if (logger.isLoggable(Level.INFO))
            sb = new StringBuffer("{ ");

        for (Iterator i = metadata.keySet().iterator(); i.hasNext(); ) {
            String name = (String) i.next();
            String value = (String) metadata.get(name);
            md.put(name, value);

            if (sb != null)
                sb.append(name).append("=\"").append(value).append("\" ");
        }

        if (sb != null)
            logger.info(sb.append('}').toString());

        // Add system attributes to the metadata Map
        SystemMetadata smd = null;
        try {
            smd = storeFile(md, inp);

            // false => populate user-visible attributes only
            smd.populateStrings(metadata, false);

            // We don't want the OID in the metadata, because it is in
            // the wrong format.
            metadata.remove(FIELD_OID);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected!", e);
            throw new RuntimeException(e);
        }

        NewObjectIdentifier oid = smd.getOID();

        return oid;
    }

    //////////////////////////////////////////////////////////////////////

    /** Ask for notifications when objects are deleted */
    public void register(DeleteListener obj, HttpContext context) {
        DeleteWrapper wr = new DeleteWrapper(obj);

        ServiceRegistration protocolServer =
            (ServiceRegistration) ProtocolService.getProtocolService();
        TreeSet evts = new TreeSet();
        evts.add(new Integer(EventRegistrant.API_DELETE));
        ContextRegistration ctxReg =
            new ContextRegistration(context.getContextPath(),
                                    wr, evts, context, false);
        protocolServer.registerContext(ctxReg);
    }

    public void delete(NewObjectIdentifier oid) throws ArchiveException {
        long startTime = System.currentTimeMillis();
        coord.delete(oid, true, false);
        long elapsed = System.currentTimeMillis() - startTime;

        if (logger.isLoggable(Level.INFO))
            logger.info("INSTR " + elapsed +
                        " DELETE " + oid.toExternalHexString());
    }

    //////////////////////////////////////////////////////////////////////
    // Compliance attributes

    /** Set the expiration date on an object */
    public void setExpiration(NewObjectIdentifier oid, Date when)
            throws ArchiveException {
        if (when == null)
            coord.setRetentionTime(oid, -1);
        else
            coord.setRetentionTime(oid, when.getTime());
    }

    /** Get the expiration date of an object */
    public Date getExpiration(NewObjectIdentifier oid)
            throws ArchiveException {
        long expiration = coord.getRetentionTime(oid);
        if (expiration < 0)
            return null;        // => "unknown"
        return new Date(coord.getRetentionTime(oid));
    }

    /** Add a legal holds on the object */
    public void addLegalHold(NewObjectIdentifier oid, String tag)
            throws ArchiveException {
        coord.addLegalHold(oid, tag);
    }

    /** Remove a legal hold on an object */
    public void removeLegalHold(NewObjectIdentifier oid, String tag)
            throws ArchiveException {
        coord.removeLegalHold(oid, tag);
    }

    /** Set the list of legal holds on the object */
    public void setLegalHolds(NewObjectIdentifier oid, Set value)
            throws ArchiveException {
        int j, nHolds = 0;
        Iterator i;

        if (value != null)
            nHolds = value.size();

        String[] holds = new String[nHolds];

        for (i = value.iterator(), j = 0; i.hasNext(); j++)
            holds[j] = (String) i.next();

        coord.setLegalHolds(oid, holds);
    }

    /** Get the list of legal holds on an object */
    public Set getLegalHolds(NewObjectIdentifier oid)
            throws ArchiveException {

        String[] holds = coord.getLegalHolds(oid);

        if (holds == null)
            return null;

        Set holdset = new HashSet();
        for (int i = 0; i < holds.length; i++)
            holdset.add(holds[i]);

        return holdset;
    }

    public boolean isDeletable(NewObjectIdentifier oid)
            throws ArchiveException {
        return coord.isComplianceDeletable(oid);
    }

    //////////////////////////////////////////////////////////////////////
    // Cluster properties

    public Properties getClusterProperties() {
        Properties config = new Properties();

        ClusterProperties conf = ClusterProperties.getInstance();
        Enumeration names = conf.propertyNames();

        if (names != null)
            while (names.hasMoreElements()) {
                String name = (String) names.nextElement();
                String value = conf.getProperty(name);
                config.setProperty(name, value);
            }

        return config;
    }

    public void saveClusterProperty(String name, String value) {
        ClusterProperties conf = ClusterProperties.getInstance();

        try {
            conf.put(name, value);
        }
        catch (Exception e) {
            throw new InternalException(e);
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        ClusterProperties conf = ClusterProperties.getInstance();
        conf.addPropertyListener(l);
    }

    //////////////////////////////////////////////////////////////////////

    /**
     * Since selectUnique is horribly slow for HADB, we use queryPlus
     * to get matching objects and manually build up a Set of their
     * attrName values. However, for a null query we have no choice.
     */
    private class SelectUniqueListener implements MDOutputStream {
        ArrayList attrs = null;
        Set values = null;
        private Object lastObject = null;
        private int numValues = 0;
        SelectUniqueListener(String attrName) {
            attrs = new ArrayList();
            values = new HashSet();
            attrs.add(attrName);
        }
        public Object getLastObject() { return lastObject; }
        public void clearLastObject() {
            lastObject = null;
        }
        public int numValues() { return numValues; }
        public void sendObject(Object o) throws EMDException {
            lastObject = o;

            if (logger.isLoggable(Level.FINER))
                logger.finer("Object #" + numValues + " of type " +
                             o.getClass() + ": \"" + o + "\"");
            numValues++;

            if (o instanceof MDHit) {
                MDHit hit = (MDHit) o;
                NewObjectIdentifier oid = hit.constructOid();
                QueryMap results = (QueryMap) hit.getExtraInfo();


                String name = (String)attrs.get(0);
                String value =
                    CanonicalStrings.encode(results.get(name), name);
                synchronized (values) {
                    values.add(value);
                }

                if (values.size() > valuesLimit)
                    throw new EMDException("Too many values for cache " +
                                           "(CR6676969 limit=" + valuesLimit + ")");
            }
            else
                logger.warning("SelectUnique listener gets value " +
                               o.getClass() + ": \"" + o + "\"?");
               
        }
    
        public void nextValue(String value) {}
    }

    private int getUniqueValues(String query, String attrName, 
                                Object[] boundParameters, MDListener listener) {
        if (query == null)
            // We cannot use null queries. Since we're trying to find
            // unique values, it suffices to use "attrName IS NOT NULL" 
            // instead.
            query = attrName + " IS NOT NULL";

        long startTime = 0, elapsed = 0;

        SelectUniqueListener lsnr = new SelectUniqueListener(attrName);
        ArrayList l = new ArrayList();
        l.add(attrName);

        startTime = System.currentTimeMillis();

        // If queryPlus throws an EMDException, don't throw hands up
        // helplessly, but return the partial list of values
        // accumulated so far -- in other words, the try/catch block
        // should only cover the call to queryPlus so even if an
        // exception is thrown we do all the other actions like tell
        // the listener about the values etc.
        try {
            mdClient.queryPlus(ECACHE, query, l,
                               null, -1, -1, false,
                               boundParameters, lsnr);
        }
        catch (EMDException e) {
            logger.log(Level.WARNING,
                       "Couldn't get values for attribute " + attrName +
                       " in query \"" + query + "\"", e);
        }

        elapsed = System.currentTimeMillis() - startTime;

        for (Iterator i = lsnr.values.iterator(); i.hasNext(); )
            listener.nextValue((String)i.next());

        if (logger.isLoggable(Level.INFO)) {
            StringBuffer msg = new StringBuffer("INSTR ");
            msg.append(elapsed);
            msg.append(" QUERYuniquevals ");
            msg.append(toString(query, boundParameters));
            logger.info(msg.toString());
        }

        return lsnr.numValues();
    }

    private class GetObjectsListener implements MDOutputStream {
        private ArrayList attrs;
        private MDListener listener;
        private Object lastObject = null;
        private int numValues = 0;
        GetObjectsListener(ArrayList attrs, MDListener listener) {
            this.attrs = attrs;
            this.listener = listener;
        }
        public Object getLastObject() { return lastObject; }
        public void clearLastObject() {
            lastObject = null;
        }
        public int numValues() { return numValues; }
        public void sendObject(Object o) throws EMDException {
            lastObject = o;
            if (!(o instanceof MDHit))
                return;

            numValues++;

            if (numValues > valuesLimit)
                throw new EMDException("Too many objects for cache " +
                                       "(CR6676969 limit=" + valuesLimit + ")");
 
            MDHit hit = (MDHit) o;
            NewObjectIdentifier oid = hit.constructOid();

            QueryMap results = (QueryMap) hit.getExtraInfo();
            Map metadata = new HashMap();

            for (int i = 0; i < attrs.size(); i++) {
                String name = (String) attrs.get(i);
                String val = CanonicalStrings.encode(results.get(name), name);
                metadata.put(name, val);
            }

            listener.nextObject(oid, metadata);
        }
    }
    private int getAllObjects(String query, ArrayList requiredAttrs, 
                              Object[] boundParameters, MDListener listener) {
        GetObjectsListener l = new GetObjectsListener(requiredAttrs, listener);

        long startTime = System.currentTimeMillis();

        try {
            mdClient.queryPlus(ECACHE, query, requiredAttrs, null, -1, -1, true,
                               boundParameters, l);
        }
        catch (EMDException e) {
            logger.log(Level.WARNING, "Couldn't run query \"" + query + "\"", e);
        }

        if (logger.isLoggable(Level.INFO)) {
            StringBuffer msg = new StringBuffer("INSTR ");
            msg.append(System.currentTimeMillis() - startTime);
            msg.append(" QUERYallobjs ");
            msg.append(toString(query, requiredAttrs, boundParameters));
            logger.info(msg.toString());
        }

        return l.numValues();
    }

    private SystemMetadata storeFile(ExtendedCacheEntry md, InputStream in)
            throws IOException, FSCacheException, ArchiveException {
        String threadID = Thread.currentThread().toString();

        long startTime = System.currentTimeMillis();

        NewObjectIdentifier oid = 
            coord.createObject(Coordinator.UNKNOWN_SIZE, md,
                               Coordinator.EXPLICIT_CLOSE,
                               (long)0, (long)0, (byte)0);
        long endTime = System.currentTimeMillis();
        long creatTime = endTime - startTime;

        if (oid == null)
            return null;

        long t = 0, oaTime = 0;

        ByteBufferPool bufferPool = ByteBufferPool.getInstance();
        ByteBuffer buffer = null;

        int bufSize = coord.getWriteBufferSize();
        byte[] bytes = new byte[bufSize];

        long bytesWritten = 0;

        startTime = System.currentTimeMillis();
        for (;;) {

            // Make sure we don't leak buffers
            buffer = bufferPool.checkOutBuffer(bufSize);
            try {

                boolean eof = false;
                int totRead = 0;
                
                // Fill up buffer

                while (totRead < bufSize) {
                    int nread;

                    int offset = totRead;
                    int length = bufSize - totRead;

                    if ((nread = in.read(bytes, offset, length)) < 0) {
                        eof = true;
                        break;
                    }

                    totRead += nread;
                }

                // Write buffer

                if (totRead > 0) {
                    // copy the bytes to the direct buffer

                    buffer.put(bytes, 0, totRead);
                    buffer.flip();

                    if (logger.isLoggable(Level.INFO))
                        t = System.currentTimeMillis();
                    coord.writeData(oid, buffer, bytesWritten, true);
                    if (logger.isLoggable(Level.INFO))
                        oaTime += System.currentTimeMillis() - t;

                    bytesWritten += totRead;
                }

                if (eof) {
                    endTime = System.currentTimeMillis();
                    long totalTime = endTime - startTime;

                    startTime = endTime;

                    SystemMetadata smd = coord.close(oid);
                    endTime = System.currentTimeMillis();
                    long closeTime = endTime - startTime;
		    putStats.add(bytesWritten, totalTime); 
                    if (logger.isLoggable(Level.INFO)) {
                        // BandwidthStatsAccumulator, used by webdav proxy 
                        logger.info("INSTR " + creatTime + "+" + closeTime +
                                    " MDCREATE " + oid.toExternalHexString() +
                                    " " + threadID);

                        double oaDuration = oaTime/1000.0;
                        double duration = totalTime/1000.0;
                        double rate = bytesWritten/duration;
                        if (duration == 0) rate = 0.0;
                        double oaRate = bytesWritten/oaDuration;
                        if (oaDuration == 0) oaRate = 0.0;
                        logger.info("INSTR " + totalTime + 
                                    " FILESTORE " + oid.toExternalHexString() +
                                    " (OA: " +
                                    oaTime + "ms) " +
                                    humanize(bytesWritten) + "B " +
                                    humanize(rate) + "B/s (OA: " +
                                    humanize(oaRate) + "B/s) " +
                                    threadID);
                    }
                    return smd;
                }
            }
            finally {
                long now = System.currentTimeMillis();
                bufferPool.checkInBuffer(buffer);
                if (logger.isLoggable(Level.FINE))
                    logger.fine("instr " + (System.currentTimeMillis() - now) +
                                " buffer checkin " + threadID);
            }
        }
    }

    private static String humanize(long b) {
        if (b < 0)
            return "-" + humanize(-b);

        if (b < 1024)
            return Long.toString(b);

        return humanize((float) b);
    }

    private static String humanize(double value) {
        return humanize((float) value);
    }

    private static String humanize(float value) {
        if (value < 0)
            return "-" + humanize(-value);

        if (value < 1024)
            return Float.toString(value);

        if (value < 1048576L)
            return byteFormatter.format(value/1024) + "ki";
        if (value < 1073741824L)
            return byteFormatter.format(value/1048576) + "Mi";
        if (value < 1099511627776L)
            return byteFormatter.format(value/1073741824L) + "Gi";

        return byteFormatter.format(value/1099511627776L) + "Ti";
    }

    /**
     * A "bound query": all the literals are separated into an
     * Object[] and "?" used in the query. This allows us to
     * intelligently handle the various data-types in the JDBC
     * calls. (See the MD Unicode and Data-Types design docs.)
     */
    private static class BoundQuery {
        String query;
        Object[] boundParameters;
        BoundQuery(String q, Object[] p) {
            query = q;
            boundParameters = p;
        }

        public String toString() {
            return HCGlue.toString(query, boundParameters);
        }
    }

    /**
     * Build an internal query (BoundQuery) from a URL-encoded list of
     * names and values.
     */
    private static BoundQuery makeQuery(String[] names, String[] values) {
        return makeQuery(names, values, null);
    }

    /**
     * Build an internal query (BoundQuery) from a URL-encoded list of
     * names and values, and a list of required MD attributes.
     */
    private static BoundQuery makeQuery(String[] names, String[] values,
                                        ArrayList requiredAttrs) {
        Set queryAttrs = new HashSet();

        // How many values do we have?
        int len = 0;
        if (values != null) 
            len = values.length;

        List params = new ArrayList();

        StringBuilder sb = new StringBuilder();
        String separator = "";

        for (int i = 0, j = 0; i < len; i++) {
            sb.append(separator);
            separator = " and ";

            sb.append(attrQuote(names[i]));
            queryAttrs.add(names[i]);

            Object value = getObjectValue(names[i], values[i]);
            if (value == null)
                sb.append(" IS NULL");
            else {
                sb.append(" = ?");
                params.add(value);
            }
        }

        // If values is smaller than names, this is a directory query,
        // and we're only interested in objects that actually exist in
        // the view, i.e. the attribute values must be non-null.

        for (int i = len; i < names.length; i++) {
            sb.append(separator);
            separator = " and ";
            sb.append(attrQuote(names[i])).append(" IS NOT NULL");
            queryAttrs.add(names[i]);
        }

        if (requiredAttrs != null)
            for (int i = 0; i < requiredAttrs.size(); i++) {
                String attrName = (String) requiredAttrs.get(i);
                if (queryAttrs.contains(attrName))
                    continue;

                sb.append(separator);
                sb.append(attrQuote(attrName)).append(" IS NOT NULL");
            }

        return new BoundQuery(sb.toString(), params.toArray());
    } // makeQuery

    /** decode a URL-encoded attribute as an internal object value */
    private static Object getObjectValue(String name, String value) {
        if (value == null)
            return null;

        Object obj = null;
        try {
            // All attribute values are URL-quoted
            String val = URLDecoder.decode(value, "UTF-8");

            // returns OID fields as an ExternalObjectIdentifier
            obj = CanonicalStrings.decode(val,name);
        } catch (NumberFormatException e) {
            String error = "Attribute \"" + name +
                "\" cannot have a value of \"" + value + "\"";
            throw new IllegalArgumentException(error);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return obj;
    } // getObjectValue

    /** Replace double quotes with \" quoted double quotes */
    private static String attrQuote(String s) {
        StringBuffer ret = new StringBuffer();

        ret.append("\"");
        int pos = 0;
        for (;;) {
            int c = s.indexOf('\"', pos);
            if (c < 0)
                break;

            c++;
            ret.append(s.substring(pos, c)).append("\\");
            pos = c;
        }

        return ret.append(s.substring(pos)).append("\"").toString();
    }

    ////////////////////////////////////////////////////////////////////////
    // Various toString() methods to print queries

    private static String toString(QueryMap q) {
        String[] names = q.getNames();
        StringBuffer b = new StringBuffer("<");
        for (int i = 0; i < names.length; i++) {
            b.append(' ').append(names[i]).append('=');
            //FIXME: Use canonical string encoding???
            b.append(StringUtil.image(q.get(names[i])));
        }
        return b.append(' ').append('>').toString();
    }

    private static String toString(String query, String attr,
                                   Object[] params) {
        StringBuffer s = new StringBuffer();
        s.append("[[").append(query).append("](");
        s.append(CanonicalStrings.parametersToString(params));
        s.append(")] { " + attr + " }");
        return s.toString();
    }

    private static String toString(String query, ArrayList attrs,
                                   Object[] params) {
        StringBuffer s = new StringBuffer();
        s.append("[[").append(query).append("](");
        if (params != null)
            s.append(CanonicalStrings.parametersToString(params));
        s.append(" )] {");
        if (attrs != null)
            for (int i = 0; i < attrs.size(); i++)
                s.append(' ').append((String) attrs.get(i));
        s.append(" }");

        return s.toString();
    }

    private static String toString(String query, Object[] params) {
        return toString(query, (ArrayList) null, params);
    }

    private static String toString(String query, ArrayList requiredAttrs) {
        return toString(query, requiredAttrs, null);
    }

    private static String toString(String[] names, String[] values) {
        String delim = "";

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < names.length; i++) {
            sb.append(delim).append(names[i]).append('=');
            if (i >= values.length)
                sb.append("?");
            else
                sb.append(StringUtil.image(values[i]));
            delim = ", ";
        }

        return sb.toString();
    }

    private static String toString(String[] names, String[] values,
                                   ArrayList requiredAttrs) {
        String q = toString(names, values);
        return toString(q, requiredAttrs);
    }

    //////////////////////////////////////////////////////////////////////

    private class DeleteWrapper implements EventRegistrant {
        private DeleteListener listener;

        DeleteWrapper(DeleteListener listener) {
            this.listener = listener;
        }

        public boolean apiCallback(int evt, NewObjectIdentifier oid,
                                   NewObjectIdentifier dataOid) {
            if (evt != EventRegistrant.API_DELETE)
                return false;

            listener.deleted(oid);
            // Do you want to mark dataOid as deleted too? 
            // See how Coordinator handles its caches.
            return true;
        }
    }
}
