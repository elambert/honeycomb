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



import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;

import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.CacheRecord;
import com.sun.honeycomb.common.Encoding;

import com.sun.honeycomb.emd.Derby;
import com.sun.honeycomb.emd.EMDCookie;
import com.sun.honeycomb.emd.cache.CacheClientInterface;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.cache.ExtendedCacheEntry;
import com.sun.honeycomb.emd.config.SessionEncoding;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.emd.cache.SystemCacheConstants;


public class DerbySystemCache
    implements CacheClientInterface, CacheInterface {

    private static final int QUERY_CODE_UNKNOWN         = 0;
    private static final int QUERY_CODE_HOLD            = 5;

    private static final Logger LOG = Logger.getLogger(DerbySystemCache.class.getName());

    private Disk registeredDisk;

    public DerbySystemCache() {
        registeredDisk = null;
    }

    /*
     * CacheClientInterface interface
     */

    public String getCacheId() {
        return(CacheClientInterface.SYSTEM_CACHE);
    }

    public String getHTMLDescription() {
        return("A key/value cache implementation that relies on <b>Derby</b>");
    }

    public boolean isRunning() {
        return true;
    }

    public void generateMetadataStream(CacheRecord mdObject,
                                       OutputStream output) 
        throws EMDException {
        ExtendedCacheEntry attributes = (ExtendedCacheEntry)mdObject;
        
        if (attributes == null) {
            // This an empty map to generate XML with an empty content
            attributes = new ExtendedCacheEntry();
        }
            
        try {
            NameValueXML.createXML(attributes, output, SessionEncoding.getEncoding());
        } catch (IOException e) {
            EMDException newe = new EMDException("Couldn't generate extended cache metadata");
            newe.initCause(e);
            throw newe;
        }
    }
    
    public CacheRecord parseMetadata(InputStream in, long mdLength,
                                     Encoding en)
        throws EMDException {
        // This method should never be invoked.
        throw new UnsupportedOperationException("DerbySystemCache." +
                                                "parseMetadata shouldn't " +
                                                "be used");
    }

    public CacheRecord generateMetadataObject(NewObjectIdentifier oid) 
        throws EMDException {
        // This method should never be invoked.
        throw new UnsupportedOperationException("DerbySystemCache." +
                                                "generateMetadataObject " +
                                                "shouldn't be used");
    }
    
    public int getMetadataLayoutMapId(CacheRecord argument,
                                      int nbOfPartitions) {
        throw new UnsupportedOperationException("DerbySystemCache." +
                                                "getMetadataLayoutMapId " +
                                                "shouldn't be used");
    }

    public int[] layoutMapIdsToQuery(String query,
                                     int nbOfPartitions) {
        return(null);
    }

    public void sanityCheck(CacheRecord argument)
        throws EMDException {
        // No sanity check implemented in this cache for now
    }

    /*
     * CacheInterface interface
     */

    public void registerDisk(String MDPath,
                             Disk disk)
        throws EMDException {
        if (registeredDisk != null) {
            LOG.warning("Being asked to register more than 1 disk");
            unregisterDisk(registeredDisk);
        }
        
        registeredDisk = disk;
    }

    public void unregisterDisk(Disk disk)
        throws EMDException {
        if ((disk == null) || (disk != registeredDisk)) {
            throw new EMDException("Cannot unregister disk ["+
                                   disk+"]");
        }

        registeredDisk = null;
    }

    public boolean isRegistered(Disk disk) {
        if ((disk == null) || (disk != registeredDisk)) {
            return(false);
        }
        return(true);
    }

    public void setMetadata(NewObjectIdentifier oid,
			    Object argument,
			    Disk disk)
        throws EMDException {
        LOG.fine("setMetadata has been called in the DerbySystemCache");
    }

    public void removeMetadata(NewObjectIdentifier _oid,
                               Disk disk)
        throws EMDException {
        LOG.fine("removeMetadata has been called in the DerbySystemCache");
    }

    public void queryPlus(MDOutputStream output,
                             ArrayList disks,
                             String nQuery,
                             ArrayList attributes,
                             EMDCookie cookie,
                             int maxResults, int timeout,
                             boolean forceResults,
                             Object[] boundParameters)
        throws EMDException {

        SystemCacheQuery query = new SystemCacheQuery(nQuery);
        
        switch (query.getType()) {
        case QUERY_CODE_HOLD:
            // Make a single legal hold string from all the arguments
            String hold = null;
            String[] args = query.getArguments();
            if (args.length > 1) {
                for (int i=1; i<args.length; i++) {
                    if (i == 1) {
                        hold = args[i];
                    } else {
                        hold = hold + " " + args[i];
                    }
                }
            }
            queryHold(output, disks, hold, attributes, cookie,
                      maxResults, timeout, forceResults);
            break;
        }
    }

    private static final String OADB_NAME = "oadb";
    private static final String HOLDTABLE_NAME = "hold";

    private void queryHold(MDOutputStream output,
                           ArrayList disks,
                           String legalHold,
                           ArrayList attributes,
                           EMDCookie cookie,
                           int maxResults, int timeout,
                           boolean forceResults)
        throws EMDException {

        Connection connection = null;
        Statement statement = null;
        ResultSet results = null;
        Object extraInfo = null;

        // Debug
        LOG.info("Running the queryHold query for legal hold [" +
                 legalHold + "]");

        try {
            connection = Derby.getInstance().getConnection(OADB_NAME);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("select oid from ");
            sb.append(HOLDTABLE_NAME);
            sb.append(" where hold='");
            sb.append(legalHold);
            sb.append("'");

            if (cookie != null) {
                sb.append(" and oid='");
                sb.append(cookie.getLastOid().toHexString());
                sb.append("'");
            }

            results = statement.executeQuery(sb.toString());

            if (attributes != null) {
                extraInfo = legalHold;
            }

            int resultNum = 0;
            while (results.next()  &&
                   (maxResults > 0 ? resultNum < maxResults : true)) {
                String oidString = results.getString(1);
                
                NewObjectIdentifier oid =
                    NewObjectIdentifier.fromHexString(oidString.trim());

                if ((cookie == null)
                     || (oid.compareTo(cookie.getLastOid()) > 0)) {

                    if (attributes != null) {
                        extraInfo = legalHold;
                    }
                    output.sendObject(new MDHit(oid, extraInfo));
                    resultNum++;
                }

                // Debug
                LOG.info("Found oid: " + oid.toHexString());
            }
        } catch (SQLException e) {
            EMDException newe =
                new EMDException("Failed to query legal hold [" +
                                 legalHold + "] - [" +
                                 e.getMessage() + "]");
            newe.initCause(e);
            throw newe;        
        } finally {
            if (results != null)
                try { results.close(); } catch (SQLException e) {}
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }
    }
    
    public void selectUnique(MDOutputStream outputStream,
                             String query,
                             String attribute,
                             String lastAttribute,
                             int maxResults, int timeout,
                             boolean forceResults,
                             Object[] boundParameters)
        throws EMDException {
        throw new EMDException("Select unique operations are not " +
                               "supported in the emulator system cache");
    }

    /*
     * start / stop methods
     */
    
    public void start()
        throws EMDException {
        // Nothing to do. We rely on the MetadataService to initialize Derby
    }

    public void stop()
        throws EMDException {
        // Nothing to do. We rely on the MetadataService to stop Derby
    }

    /**********************************************************************
     *
     * SystemCacheQuery class
     *
     **********************************************************************/
    
    private class SystemCacheQuery {
        private int type;
        private String[] arguments;

        private SystemCacheQuery(String query)
            throws EMDException {

            if (query == null) {
                throw new EMDException("Query cannot be null");
            }

            arguments = query.split(" ");
            type = QUERY_CODE_UNKNOWN;

            if (type == QUERY_CODE_UNKNOWN) {
                if(arguments[0].equals(SystemCacheConstants.SYSTEM_QUERY_HOLD)) {
                   type = QUERY_CODE_HOLD;
                }
            }
            if (type == QUERY_CODE_UNKNOWN) {
                throw new EMDException("Unknown query type ["
                                       +arguments[0]+"]");
            }
        }
        
        private int getType() { return(type); }
        private String[] getArguments() { return(arguments); }
    }

    /**********************************************************************
     *
     * Compliance APIS. Needed to satisfy CacheInterface but not used.
     *
     **********************************************************************/

    public void addLegalHold(NewObjectIdentifier oid,
                             String legalHold, Disk disk)  {
	LOG.info("Legal holds are stored in the emulator cache by OA");
    }

    public void removeLegalHold(NewObjectIdentifier oid,
                                String legalHold, Disk disk) {
    	LOG.info("Legal holds are stored in the emulator cache by OA");
    }
    
    public void sync(Disk disk) throws EMDException {
        // no implementation for now
    }
    
    public void wipe(Disk disk) throws EMDException {
    }

    public void doPeriodicWork(Disk disk) throws EMDException {
        // no implementation for now
    }
}
