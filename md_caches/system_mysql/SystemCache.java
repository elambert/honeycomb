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



/**********************************************************************
 *
 *
 * IMPORTANT !!! This system cache implementation is not maintained
 * as of 05/25/04.
 *
 **********************************************************************/

import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;

import com.sun.honeycomb.platform.diskinit.Disk;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.SystemMetadata;

import com.sun.honeycomb.emd.ObjectInfo;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.RelationalSchema;
import com.sun.honeycomb.emd.common.RelationalAttribute;
import com.sun.honeycomb.emd.cache.CacheInterface;
import java.util.logging.Level;
import java.util.ArrayList;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import java.lang.StringBuffer;

/**
 * The class <code>SystemCache</code> is the process unit
 * for the recovery table. It relies on the the MySqlProcessUnit
 * implementation.
 */

public class SystemCache
    extends MySqlCache {

    public String getCacheId() {
        return(CacheInterface.SYSTEM_CACHE);
    }

    public String getHTMLDescription() {
        return("A system cache implementation that relies on <b>MySQL</b>");
    }
    
    /*
     * Fields
     */
    
    private static final Logger LOG = Logger.getLogger(SystemCache.class.getName());
    
    /*
     * Methods
     */

    public SystemCache() {
        super("system_");
    }

    public void registerDisk(String MDPath,
                             Disk disk) 
        throws EMDException {
        super.registerDisk(MDPath, disk);

        try {
            
            String tableName = getTableName(disk);
            String SQLCreate = schema.toSqlCreate(tableName,
                                                  MDPath);
            createSchema(tableName, SQLCreate);

        } catch (ArchiveException e) {
            unregisterDisk(disk);
            EMDException newe = new EMDException("Failed to create all the database tables ["+e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    public void unregisterDisk(Disk disk) 
        throws EMDException {
        super.unregisterDisk(disk);
    }

    /**
     * The following method inserts a new record in the recovery table
     * @param statement a <code>Statement</code> value
     * @param info an <code>ObjectInfo</code> value
     * @param disk a <code>Disk</code> value
     * @exception EMDException if an error occurs
     */

    protected void setMetadata(Statement statement,
                               NewObjectIdentifier oid,
                               Object argument,
                               Disk disk)
        throws EMDException {
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("setMetadata has been called in the ExtendedCache (disk "+disk);
        }

        if (argument == null) {
            throw new EMDException("The SystemMetadata argument has to be given to"
                                   +" populate the system cache");
        }
        
        SystemMetadata systemMetadata = (SystemMetadata)argument;
        
        // Create the SQL request to insert the entry
        StringBuffer SQLrequest = new StringBuffer();
        
        SQLrequest.append("insert ignore " + getTableName(disk)
                          + " set ");

        HashMap map = new HashMap();
        boolean first = true;
        systemMetadata.populateStrings(map, true);     //Strings only -- and include systemCache fields

        Iterator iter = map.keySet().iterator();
        while (iter.hasNext()) {
            String name = (String)iter.next();
            String value = (String)map.get(name);
            if (!first) {
                SQLrequest.append(", ");
            } else {
                first = false;
            }
            SQLrequest.append(name+"=\""+value+"\"");
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Inserting a new EMD entry with the SQL command : " +
                     SQLrequest);
        }
        
        try {
            statement.execute(SQLrequest.toString());
        } catch (SQLException e) {
            EMDException newe = new EMDException("Failed to store the MD in the database");
            newe.initCause(e);
            throw newe;
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("system MD have been inserted");
        }
    }
    
    protected void queryPlus(Statement statement,
                             MDOutputStream output,
                             String _query,
                             ArrayList attributes,
                             NewObjectIdentifier cookie,
                             int maxResults,
                             boolean forceResults)
        throws EMDException {

        // Nothing to do. The SystemCache is not used to run queries
    }

    protected void selectUnique(Statement statement,
                                MDOutputStream output,
                                String _query,
                                String attribute,
                                String cookie,
                                int maxResults,
                                boolean forceResults)
        throws EMDException {
        
        // Nothing to do. The SystemCache is not used to get unique selections
    }
    
    protected RelationalSchema readSchema() {
        RelationalAttribute[] attributes =
            SystemMetadata.getAttributesDefinition(factory, true);

        RelationalSchema result = factory.allocateSchema(attributes);
        return(result);
    }
}
