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

import com.sun.honeycomb.fscache.HCFile;

import com.sun.honeycomb.fscache.FSCacheObject;
import com.sun.honeycomb.fscache.FSCacheException;

import com.sun.honeycomb.common.StringUtil;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.config.Filename;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.RootNamespace;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MDHandler implements MDListener {

    private static final Logger logger =
        Logger.getLogger(MDHandler.class.getName());
    
    private Object objGroup;

    private short viewIndex;
    private HCFile parent;

    private long numObjects = 0;

    private static boolean initialized = false;

    static ArrayList fsAttrs = null;
    static ArrayList sysAttrs = null;

    synchronized static void init() {
        if (initialized)
            return;

        sysAttrs = new ArrayList();
        sysAttrs.add(HCInterface.FIELD_CTIME);
        sysAttrs.add(HCInterface.FIELD_SIZE);

        fsAttrs = new ArrayList();
        fsAttrs.add(HCInterface.FIELD_UID);
        fsAttrs.add(HCInterface.FIELD_GID);
        fsAttrs.add(HCInterface.FIELD_MODE);
        fsAttrs.add(HCInterface.FIELD_MTIME);
        fsAttrs.add(HCInterface.FIELD_MIMETYPE);

        initialized = true;
    }

    MDHandler(Object group, HCFile parent) {
        this.objGroup = group;
        this.parent = parent;
        viewIndex = parent.getViewIndex();
    }

    long numAdded() {
        return numObjects;
    }

    //////////////////////////////////////////////////////////////////////
    // MD callbacks

    // All the metadata should be in CanonicalStrings format by now.
    public void nextObject(NewObjectIdentifier oid, Map metadata) {
        init();
        if (logger.isLoggable(Level.FINE))
            logger.fine("    object " + oid);

        try {
            if (HCFile.fileCache.add(objGroup, newFile(oid, metadata)))
                numObjects++;
        }
        catch (NoSuchElementException e) {
            if (logger.isLoggable(Level.FINE))
                logger.fine("Not adding " + oid.toExternalHexString() +
                            ": no attribute \"" + e.getMessage() + "\"");
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't add object " + oid, e);
        }
    }

    public void nextValue(String name) {
        init();
        if (logger.isLoggable(Level.FINE))
            logger.fine("    value \"" + name + "\"");

        if (name == null || name.length() == 0)
            return;

        try {
            if (HCFile.fileCache.add(objGroup, newDirectory(name)))
                numObjects++;
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't add dir " + name, e);
        }
    }

    //////////////////////////////////////////////////////////////////////
    // Private

    private HCFile newDirectory(String name) throws EMDException {
        int length = 0;
        String[] parentAttributes = parent.getAttributes();
        if (parentAttributes != null)
            length = parentAttributes.length;

        // Copy all but the last attribute from the parent
        String[] attributes = new String[length + 1];
        for (int i = 0; i < length; i++)
            attributes[i] = parentAttributes[i];

        attributes[length] = name;

        HCFile f =  new HCFile(viewIndex, attributes);
        f.setParent(parent);

        if (logger.isLoggable(Level.FINE))
            logger.fine(parent.fileName() + ": \"" + name + "\"");

        return f;
    }

    private HCFile newFile(NewObjectIdentifier oid, Map metadata)
            throws EMDException {

        // Keep in mind that the object here is one that matched a
        // query (i.e. has values for all the <Attribute> elements in
        // the view spec.) but we are only going to show files for
        // which we can actually construct a filename. In other words,
        // get the list of attributes that go into the Filename, and
        // make sure they're all defined in the Map.

        Filename filenameRep =  HCFile.views[viewIndex].getFilename();
        ArrayList neededAttrs = filenameRep.getNeededAttributes();

        String fileName = null;
        if (filenameRep == null)
            fileName = oid.toString();
        else {
            for (Iterator i = neededAttrs.iterator(); i.hasNext(); ) {
                String attr = (String) i.next();
                if (metadata.get(attr) == null)
                    throw new NoSuchElementException(attr);
            }
            fileName = filenameRep.convert(metadata);
        }

        // Query for the system attributes

        long crtime = 0, size = 0;
        String[] values = HCFile.hc.queryObject(oid, sysAttrs);
        for (int i = 0; i < sysAttrs.size(); i++) 
            try {
                String name = (String) sysAttrs.get(i);
                if (name.equals(HCInterface.FIELD_CTIME))
                    crtime = Long.parseLong(values[i]);
                else if (name.equals(HCInterface.FIELD_SIZE))
                    size = Long.parseLong(values[i]);
            }
            catch (NumberFormatException e) {
                throw new InternalException(e);
            }

        return makeObject(oid, parent, fileName, crtime, size);
    }

    public static HCFile makeObject(NewObjectIdentifier oid,
                                    HCFile parent, String fileName,
                                    long crTime, long size) {
        init();
        if (logger.isLoggable(Level.FINE))
            logger.fine(parent.fileName() + ": \"" + fileName + "\", ctime=" +
                        crTime + ", size=" + size + ", oid " + oid);

        long uid = -1;
        long gid = -1;
        long mode = -1;
        long mtime = -1;
        String mimetype = null;

        if (parent.viewUsesExtendedAttrs()) {
            /*
             * Do another query for filesystem attributes.  We cannot
             * do a single query because that query will skip the entries
             * without filesystem attributes defined
             */
            String[] values = HCFile.hc.queryObject(oid, fsAttrs);

            if (logger.isLoggable(Level.FINE)) {
                String msg = "";
                for (int i = 0; i < fsAttrs.size(); i++)
                    msg += " " + fsAttrs.get(i) + "=\"" + values[i] + "\"";
                logger.fine("fs attributes" + msg);
            }

            uid = getLong(values[0]);
            gid = getLong(values[1]);
            mode = getLong(values[2]);
            mtime = getLong(values[3]);
            mimetype = values[4];
        }

        HCFile newFile =
            new HCFile(FSCacheObject.FILELEAFTYPE,
                       parent.getViewIndex(), parent.getAttributes(),
                       fileName, oid, size, crTime, uid, gid, mode);

        newFile.setParent(parent);
        newFile.setDisplayName(fileName);
        if (mtime > 0)
            newFile.setMtime(mtime);
        else
            newFile.setMtime(crTime);
        if (mimetype == null)
            newFile.setMimeType("application/octet-stream");
        else
            newFile.setMimeType(mimetype);

        return newFile;
    }

    ////////////////////////////////////////////////////////////////////////
    // Helpers for HCFile

    /**
     * Using the view attr names, file attr values, and the extra
     * attributes parsed from the filename, construct a Map
     *
     * Used by the PUT handler to make new objects
     */
    public static Map parseEMD(HCFile file, String filename) 
            throws FSCacheException {
        String trMsg = null;

        init();
        Map result = new HashMap();

        String[] viewAttrNames = file.getViewAttrNames();
        String[] attrValues = file.getAttributes();

        for (int i = 0; i < attrValues.length; i++)
            result.put(viewAttrNames[i], attrValues[i]);

        try {
            Filename filenameRep = file.getViewFilename();
            Namespace namespace = file.getViewNamespace();
            String ns = null;
            if (namespace != null)
                ns = namespace.getQualifiedName();

            if (logger.isLoggable(Level.FINE))
                trMsg = "<" + filenameRep + ">: \"" + filename + "\" ->";

            if (filenameRep != null) {
                Map filenameValues = filenameRep.parseFilename(filename);

                Iterator i = filenameValues.keySet().iterator();
                while (i.hasNext()) {
                    String key = (String) i.next();
                    String value = filenameValues.get(key).toString();

                    key = validate(ns, key, value);
                    result.put(key, value);

                    if (trMsg != null)
                        trMsg += " " + StringUtil.image(key) + "=" +
                            StringUtil.image(value) + ";";
                }
            }

            if (logger.isLoggable(Level.FINE))
                logger.fine("Parsing values from filename: " + trMsg);
        }
        catch (EMDException e) {
            String s = "Couldn't parse \"" + filename + "\": " + e;
            int err = FSCacheException.FSERR_INVAL;
            FSCacheException newe = new FSCacheException(err, s);
            newe.initCause(e);
            throw newe;
        }

        return result;
    }

    /** The entry point for HCFile to add children by making MD queries */
    public static int runQueryAddChildren(HCFile parent, Object group,
                                          String[] names, String[] values,
                                          Filename filename)
            throws FSCacheException {
        init();
        ArrayList required = null;

        if (values.length < names.length) {
            // directory: want the first name after known values 
            required = new ArrayList();
            required.add(names[values.length]);
        }
        else {
            required = filename.getNeededAttributes();

            // System attributes (ctime, size) always exist and we'll
            // query for them separately, otherwise HADB will need to
            // join the system table too just to handle the query.

            // The others (e.g. the filesystem attributes) are in a
            // different table, and we do a separate query for those
            // attributes if the view has "fsattrs" set. (That's done
            // in the newFile callback.)

            if (logger.isLoggable(Level.FINE)) {
                StringBuffer sb = new StringBuffer();

                sb.append("Querying for file object; required attributes:");
                for (int i = 0; i < required.size(); i++)
                    sb.append(' ').append((String)required.get(i));

                logger.fine(sb.toString());
            }
        }

        return parent.hc.query(names, values, required,
                               new MDHandler(group, parent));
    }

    /** Validate that the name exists and the value is legal for it */
    private static String validate(String ns, String name, String value)
            throws EMDConfigException  {
        // If the attribute name does not exist as is, see if it
        // exists in the namespace

        RootNamespace rootNS = RootNamespace.getInstance();

        try {
            rootNS.validate(name, value);
            return name;
        }
        catch (EMDConfigException e) {
            logger.fine("Can't assign [" + name + "] <- \"" + value + "\"");

            // Try it in the given namespace. If that fails, throw the
            // *original* exception.

            if (ns != null) {
                name = ns + "." + name;
                try {
                    rootNS.validate(name, value);
                    return name;
                }
                catch (EMDConfigException e2) { }
            }

            throw e;
        }
    }

    private static long getLong(Map m, String name) {
        Object o = m.get(name);
        if (o instanceof Long) {
            Long value = (Long) o;
            return value.longValue();
        }
        return getLong(m.get(name).toString());
    }
    private static long getLong(String v) {
        try {
            return Long.parseLong(v);
        }
        catch (Exception ignored) {}
        return -0xdeadbeef;
    }
}
