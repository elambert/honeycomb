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

import com.sun.honeycomb.common.FsMetadata;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.Filename;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.FsAttribute;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.coordinator.Coordinator;

import org.mortbay.http.HttpContext;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.beans.PropertyChangeListener;

import java.nio.channels.WritableByteChannel;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Date;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The interface used by everything in the filesystem module to talk to
 * HC.
 */
public abstract class HCInterface {
    private static final Logger logger =
        Logger.getLogger(HCInterface.class.getName());

    public static final String FIELD_CTIME = 
        SystemMetadata.FIELD_NAMESPACE + "." + SystemMetadata.FIELD_CTIME;
    public static final String FIELD_SIZE =
        SystemMetadata.FIELD_NAMESPACE + "." + SystemMetadata.FIELD_SIZE;
    public static final String FIELD_OID =
        SystemMetadata.FIELD_NAMESPACE + "." + SystemMetadata.FIELD_OBJECTID;

    public static final String FIELD_MTIME = FsMetadata.FIELD_MTIME;
    public static final String FIELD_UID = FsMetadata.FIELD_UID;
    public static final String FIELD_GID = FsMetadata.FIELD_GID;
    public static final String FIELD_MODE = FsMetadata.FIELD_MODE;
    public static final String FIELD_MIMETYPE = FsMetadata.FIELD_MIMETYPE;

    public static final long UNKNOWN_SIZE = Coordinator.UNKNOWN_SIZE;

    //////////////////////////////////////////////////////////////////////

    public abstract void init() throws IOException;

    /**
     * A metadata query: a list of names, and a (possibly smaller)
     * list of values. If they have the same size, the results will
     * be a list of OIDs; otherwise, all the defined values for the
     * next undefined attribute.
     *
     * Results are returned to listener via callback. Return value is
     * the total number of results returned.
     */
    public abstract int query(String[] names, String[] values,
                              ArrayList desiredAttrs,
                              MDListener listener);

    /**
     * The existence of an intermediate directory in the filesystem
     * means at least one object exists somewhere under there. So
     * given an array of names and a smaller (or equal) array of
     * values, are there any objects that match? The OID returned will
     * be of an arbitrary object that matches.
     */
    public abstract HCObject getObject(String[] names, String[] values);

    /** A unique query, so we don't need callbacks */
    public abstract String[] queryObject(NewObjectIdentifier oid,
                                         ArrayList desiredAttrs);


    /** Write out file contents */
    public abstract boolean writeObject(NewObjectIdentifier oid,
                                        OutputStream os,
                                        long offset, long length)
        throws IOException;

    /** Write out file contents using NIO */
    public abstract boolean writeObject(NewObjectIdentifier oid,
                                        WritableByteChannel channel,
                                        long offset, long length)
        throws IOException;


    /** Create a new file with metadata */
    public abstract NewObjectIdentifier storeFile(Map metadata,
                                                  InputStream inp)
        throws IOException;



    /**
     * Ask for notifications when objects are deleted, and register
     * the HTTP context with the Protocol server
     */
    public abstract void register(DeleteListener obj, HttpContext context);

    public abstract void delete(NewObjectIdentifier oid)
        throws ArchiveException;

    /** Save a property to the cluster's persistent config store */
    public abstract void saveClusterProperty(String name, String value);

    /** Get a property from the cluster's persistent config store */
    public abstract Properties getClusterProperties();

    /** Add a listener for property change events */
    public abstract void addPropertyChangeListener(PropertyChangeListener l);

    //////////////////////////////////////////////////////////////////////
    // Compliance attributes

    /** Set the expiration date on an object. null => "unspecified" */
    public abstract void setExpiration(NewObjectIdentifier oid, Date when)
        throws ArchiveException;

    /** Get the expiration date of an object. Exception -> no expiration */
    public abstract Date getExpiration(NewObjectIdentifier oid)
        throws ArchiveException;

    /** Add a legal holds on the object */
    public abstract void addLegalHold(NewObjectIdentifier oid, String tag)
        throws ArchiveException;

    /** Remove a legal hold on an object */
    public abstract void removeLegalHold(NewObjectIdentifier oid, String tag)
        throws ArchiveException;

    /** Set the list of legal holds on the object */
    public abstract void setLegalHolds(NewObjectIdentifier oid, Set value)
        throws ArchiveException;

    /** Get the list of legal holds on an object */
    public abstract Set getLegalHolds(NewObjectIdentifier oid)
        throws ArchiveException;

    public abstract boolean isDeletable(NewObjectIdentifier oid)
        throws ArchiveException;

    //////////////////////////////////////////////////////////////////////
    // Convenience methods

    /* See if object can be deleted
    public boolean isDeletable(NewObjectIdentifier oid)
            throws ArchiveException {

        // The mere existence of legal holds is enough to forbid deletion
        // (we don't care what the value is)
        if (getLegalHolds(oid) != null)
            return false;

        // If no expiration date is set, the object can be deleted
        try {
            Date expiration = getExpiration(oid);
            if (expiration == null)
                // "Unknown"/"unspecified" expiration => no delete
                return false;

            long now = System.currentTimeMillis();
            return expiration.getTime() < now;
        }
        catch (ArchiveException e) {
            return true;
        }
        
    }
    */

    /** Replace any apostrophes with double apostrophes */
    static String quoteEMD(String s) {
        if (s == null)
            return "";

        StringBuffer ret = new StringBuffer(s.length()+6);

        int pos = 0;
        for (;;) {
            int c = s.indexOf('\'', pos);
            if (c < 0)
                break;

            ret.append(s.substring(pos, c)).append('\'').append('\'');
            pos = c + 1;
        }

        ret.append(s.substring(pos));

        return ret.toString();
    }

    //////////////////////////////////////////////////////////////////////

    // Used by getObject for returned value
    // OID in INTERNAL form
    public static class HCObject {
        private NewObjectIdentifier oid;
        private long crTime;
        private long size;

        HCObject(NewObjectIdentifier oid, long crTime, long size) {
            this.oid = oid;
            this.crTime = crTime;
            this.size = size;
        }
        public NewObjectIdentifier oid() { return oid; }
        public long crTime() { return crTime; }
        public long size() { return size; }
    }

    //////////////////////////////////////////////////////////////////////

    private static FsView[] allViews = null;
    private static String[] allVars = null;

    /** Get the list of defined views */
    public static synchronized FsView[] getViews() {
        try {
            int nbViews = RootNamespace.getInstance().getNbViews();

            if ((allViews == null) || (nbViews!=allViews.length)) {
                allViews = RootNamespace.getInstance().getViews();
            }
        } catch (EMDConfigException e) {
            logger.log(Level.SEVERE, "Couldn't load views", e);
            throw new RuntimeException(e);
        }

        return allViews;
    }

    public static synchronized String[] getNames() {
        if (allVars == null) {
            Set vars = new TreeSet();
            vars.add(""); //Leave room for FIELD_OID
            vars.add(FIELD_CTIME);
            vars.add(FIELD_MTIME);
            vars.add(FIELD_SIZE);
            vars.add(FIELD_UID);
            vars.add(FIELD_GID);
            vars.add(FIELD_MODE);
            vars.add(FIELD_MIMETYPE);

            getViews();
            for (int i = 0; i < allViews.length; i++) {
                FsView view = allViews[i];
                List attrs = view.getAttributes();
                for (Iterator j = attrs.iterator(); j.hasNext(); ) {
                    FsAttribute attr = (FsAttribute) j.next();
                    vars.add(attr.getField().getQualifiedName());
                }

                vars.addAll(view.getFilename().getNeededAttributes());
            }
            vars.remove(FIELD_OID);
            allVars = new String[vars.size()];
            vars.toArray(allVars);
        }
        allVars[0] = FIELD_OID; //Replace "" field with OID

        return allVars;
    }
}
