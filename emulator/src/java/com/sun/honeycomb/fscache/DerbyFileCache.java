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



package com.sun.honeycomb.fscache;

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.config.ClusterProperties;

import com.sun.honeycomb.emd.Derby;
import com.sun.honeycomb.emd.DerbyAttributes;
import com.sun.honeycomb.emd.common.EMDException;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.List;
import java.util.LinkedList;
import java.util.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DerbyFileCache
    extends FSCache {

    private static Logger LOG = Logger.getLogger(DerbyFileCache.class.getName());

    private static final String FILECACHE_DB = "fscache";
    private static final String FILECACHE_TABLE = "main";

    private FSCacheObject root = null;

    private HashMap volatileCache;

    public DerbyFileCache() 
	throws FSCacheException {
	
	volatileCache = new HashMap();

	try {
            
            Derby.getInstance().checkTable(FILECACHE_DB, FILECACHE_TABLE,
                                           getAttributes());

	} catch (EMDException e) {
	    LOG.log(Level.SEVERE,
		    "Failed to initialize the DerbyFileCache ["+
		    e.getMessage()+"]",
		    e);
	    FSCacheException newe = new FSCacheException(FSCacheException.FSERR_SERVERFAULT,
							 "Failed to initialize the DerbyFileCache ["+
							 e.getMessage()+"]");
	    newe.initCause(e);
	    throw newe;
	}
    }
    
    public Object startGroup() throws FSCacheException { return null; }
    public void endGroup(Object group) throws FSCacheException { }

    public void initialize(FSCacheObject root, Properties config)
            throws FSCacheException {
        init(root);
    }
    public void initialize(FSCacheObject root, ClusterProperties config)
            throws FSCacheException {
        init(root);
    }

    private void init(FSCacheObject root) throws FSCacheException {
        this.root = root;
        add(null, root);
    }

    private String pathSanityCheck(String path)
	throws FSCacheException {
	if (path == null) {
	    throw new FSCacheException(FSCacheException.FSERR_PERM,
				       "No such file [null]");
	}	    
	
        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF")) ||
	    (path.endsWith(".DS_Store")) ||
	    (path.endsWith(".hidden"))) {
	    throw new FSCacheException(FSCacheException.FSERR_NOENT,
				       "No such file ["+path+"]");
	}
	
	if ((path.endsWith("/") && !path.equals("/"))) {
	    path = path.substring(0, path.length()-1);
	}

	return(path);
    }

    public FSCacheObject lookup(Object cookie, String path)
	throws FSCacheException {
	
	path = pathSanityCheck(path);

        FSCacheObject result = null;
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            connection = Derby.getInstance().getConnection(FILECACHE_DB);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("select blob from ");
            sb.append(FILECACHE_TABLE);
            sb.append(" where path='");
            sb.append(path);
            sb.append("'");
            resultSet = statement.executeQuery(sb.toString());

            if (resultSet.next()) {
                InputStream inp = resultSet.getBlob(1).getBinaryStream();
                DataInputStream dis = new DataInputStream(inp);
                result = root.newObject();
                result.readIn(dis);
            }
        } catch (IOException e) {
            LOG.warning("Couldn't read object ["+path+"] from blob: " + e);
        } catch (SQLException e) {
            LOG.warning("Failed to resolve ["+path+"] -"+
                        e.getMessage());
        } finally {
            if (resultSet != null)
                try { resultSet.close(); } catch (SQLException e) {}
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }
	
        if (result == null) {
            try {
                result = volatileResolve(path);
            } catch (FSCacheException ignored) {
                result = null;
            }
        }

        return(result);
    }

    public List listChildren(Object cookie, FSCacheObject parent)
        throws FSCacheException {
        // Instead of calling back to HCFile to do a MD query

        List retval = new LinkedList();

        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        FSCacheObject entry = null;

        try {
            connection = Derby.getInstance().getConnection(FILECACHE_DB);
            statement = connection.createStatement();
            StringBuffer sb = new StringBuffer("select blob from ");
            sb.append(FILECACHE_TABLE);
            sb.append(" where parentPath='");
            sb.append(parent.fileName());
            sb.append("'");
            resultSet = statement.executeQuery(sb.toString());

            while ( resultSet.next() ) {

                InputStream inp = resultSet.getBlob(1).getBinaryStream();
                DataInputStream dis = new DataInputStream(inp);
                entry = root.newObject();
                try {
                    entry.readIn(dis);
                } catch (IOException e) {
                    LOG.warning("Couldn't read object from blob: " + e);
                }
                retval.add(entry);
            }

        } catch (SQLException e) {
            LOG.warning("Failed to readir ["+parent.fileName()+"] -"+
                        e.getMessage());
        } finally {
            if (resultSet != null)
                try { resultSet.close(); } catch (SQLException e) {}
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }

        return retval;
    }

    public boolean update(Object cookie,
                          FSCacheObject entry,
                          boolean modified)
	throws FSCacheException {

        StringBuffer sb = new StringBuffer("update ");
        sb.append(FILECACHE_TABLE);
        sb.append(" set blob=? where path='");
        sb.append(entry.fileName());
        sb.append("'");
        
        // Do the update
        
        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = Derby.getInstance().getConnection(FILECACHE_DB);

            statement = connection.prepareStatement(sb.toString());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                entry.writeOut(new DataOutputStream(stream));
            }
            catch (IOException e) {
                LOG.log(Level.WARNING, "Couldn't serialize out", e);
            }
            byte[] bytes = stream.toByteArray();

            statement.setBinaryStream(1, new ByteArrayInputStream(bytes), bytes.length);
            
            statement.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Failed to update ["+entry.fileName()+"] in the cache - "+
                        e.getMessage());
        } finally {
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }

        return true;
    }
    
    public boolean remove(Object cookie,
                          FSCacheObject file,
                          boolean recursive) {
        return true;
    }


    public boolean add(Object cookie,
                       FSCacheObject file) {

        StringBuffer sb = new StringBuffer("insert into ");
        sb.append(FILECACHE_TABLE);
        sb.append(" (");
        for (int i=0; i<colNames.length; i++) {
            if (i>0)
                sb.append(", ");
            sb.append(colNames[i]);
        }
        sb.append(") values ('");
        
        sb.append(file.fileName());
        sb.append("'");
        
        // Parent PATH
        sb.append(", ");
        if (file.fileType() == FSCacheObject.ROOTFILETYPE) {
            sb.append("'-'");
        } else {
            int pos = file.fileName().lastIndexOf('/');
            sb.append("'");
            sb.append(file.fileName().substring(0, pos));
            sb.append("'");
        }
        
        sb.append(", '");
        if (file.fileType() == FSCacheObject.FILELEAFTYPE) {
            // Only leaf objects have an OID. Get OID as hex string
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                file.writeOutOID(new DataOutputStream(stream));
            }
            catch (IOException e) {
                LOG.log(Level.WARNING, "Couldn't serialize out", e);
            }
            sb.append(ByteArrays.toHexString(stream.toByteArray()));
        } else {
            sb.append("-");
        }            
        sb.append("'");

        // The BLOB
        sb.append(", ?");

        sb.append(")");

        // Do the insert
        
        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = Derby.getInstance().getConnection(FILECACHE_DB);

            statement = connection.prepareStatement(sb.toString());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                file.writeOut(new DataOutputStream(stream));
            }
            catch (IOException e) {
                LOG.log(Level.WARNING, "Couldn't serialize out", e);
            }
            byte[] bytes = stream.toByteArray();

            statement.setBinaryStream(1, new ByteArrayInputStream(bytes), bytes.length);
            
            statement.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Failed to insert ["+file.fileName()+"] in the cache - "+
                        e.getMessage());
        } finally {
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }

        return(true);
    }

    public void cleanEntry(FSCacheObject file)
        throws FSCacheException {
        throw new FSCacheException(FSCacheException.FSERR_NOTSUPP,
                                   "cleanEntry not supported");
    }

    public String toString() {
        return "unimplemented";
    }
        
    public void dumpCache() {
    }


    public boolean remove(Object cookie, byte[] oid) {
        Connection connection = null;
        Statement statement = null;

        try {
            StringBuffer sb = new StringBuffer("delete from ");
            sb.append(FILECACHE_TABLE);
            sb.append(" where oid='");
            sb.append(ByteArrays.toHexString(oid));
            sb.append("'");

            connection = Derby.getInstance().getConnection(FILECACHE_DB);
            statement = connection.createStatement();

            statement.executeUpdate(sb.toString());
        } catch (SQLException e) {
            LOG.warning("failed to delete all entries for object OID = " +
                        ByteArrays.toHexString(oid) + " " + e);
        } finally {
            if (statement != null)
                try { statement.close(); } catch (SQLException e) {}
            if (connection != null)
                try { connection.close(); } catch (SQLException e) {}
        }

        return true;
    }

    public void dump(OutputStream os) throws IOException {}

    /**********************************************************************
     *
     * Temporary cache API
     *
     **********************************************************************/
    
    public FSCacheObject volatileResolve(String path)
	throws FSCacheException {
	path = pathSanityCheck(path);
	FSCacheObject result = (FSCacheObject)volatileCache.get(path);
	if (result == null) {
	    throw new FSCacheException(FSCacheException.FSERR_NOENT,
				       "No such file ["+
				       path+"] in the volatile cache");
	}
	
	return(result);
    }
    
    public void volatileAddEntry(FSCacheObject entry) {
	volatileCache.put(entry.fileName(), entry);
    }
    
    public ArrayList volatileReaddir(FSCacheObject parent) {
	ArrayList result = new ArrayList();
	Iterator files = volatileCache.values().iterator();
	while (files.hasNext()) {
	    FSCacheObject file = (FSCacheObject)files.next();

            String parentPath = "/";
            int pos = file.fileName().lastIndexOf('/');
            if (pos > 0)
                parentPath = file.fileName().substring(0, pos);

	    if (parentPath.equals(parent.fileName())) {
		result.add(file);
	    }
	}
	
	return(result);
    }
    
    public void volatileCleanEntry(FSCacheObject file) {
	volatileCache.remove(file.fileName());
    }

    /*********************************************************************
     *
     * Utility routines
     *
     *********************************************************************/

    private static final String[] colNames  = {
        "path", "parentPath", "oid", "blob"
    };

    private static final String[] colTypes = {
        "varchar(1024)", "varchar(1024)", "char(57)", "blob(4096)"
    };
        
    private static DerbyAttributes getAttributes() {
        DerbyAttributes atts = new DerbyAttributes();
        for (int i=0; i<colNames.length; i++) {
            atts.add(colNames[i], colTypes[i],
                     i==0 ? DerbyAttributes.FLAG_PRIMARYKEY : (byte)0 );
        }
        return(atts);
    }
}
