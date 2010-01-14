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

import com.sun.honeycomb.common.StringUtil;
import com.sun.honeycomb.common.InternalException;

import com.sun.honeycomb.connectors.HCInterface;
import com.sun.honeycomb.connectors.MDHandler;

import java.util.Set;
import java.util.Map;
import java.util.Iterator;

import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheLoader {

    private static final Logger logger =
        Logger.getLogger(CacheLoader.class.getName());

    /**
     * Load the object corresponding to path into the cache. If
     * "isWriting" is set, return just the view object, since creating
     * a file will create all ancestor directories.
     */
    public static HCFile load(String[] path, boolean isWriting)
            throws FSCacheException {

        String parsedPath = FSCache.combine(path);

        if (logger.isLoggable(Level.FINE))
            logger.fine("Loading " + StringUtil.image(parsedPath));

        // Quick short-circuit: if path already exists, return it quick
        try {
            return (HCFile) HCFile.fileCache.lookup(null, parsedPath);
        }
        catch (FSCacheException ignored) {}

        String rootPath = HCFile.getRoot().fileName();
        if (rootPath.equals("/"))
            rootPath = "";

        // First, load the view
        String viewName = rootPath + "/" + path[0];
        HCFile viewObj = findView(viewName);

        if (logger.isLoggable(Level.FINE))
            logger.fine("Have view " + viewName +
                        (isWriting? ";":"; not") + " writing");

        // If it's a write operation, we're going to create all the
        // directories so we don't need to run queries and pre-load
        // the cache.
        if (isWriting)
            return viewObj;

        // Get the names and values that form a query

        String filename = null;
        String names[] = viewObj.getViewAttrNames();

        int archPathPos = -1;   // If there are components past the filename

        if (path.length > names.length + 1)  // +1 is the view name
            filename = path[names.length + 1];

        if (path.length > names.length + 2) // +2 => filename, viewname
            archPathPos = names.length + 2;
        
        String[] values = getValuesFromPath(path, names.length);

        // Construct the path and search the cache

        StringBuffer hcObjNameBuf = new StringBuffer(viewName);
        // FSCache.combine() returns "/" for empty arrays so don't
        // call it when there are no values, i.e. when the request is
        // for a view directory
        if (values.length != 0)
            hcObjNameBuf.append(FSCache.combine(values));
        if (filename != null)
            hcObjNameBuf.append('/').append(filename);
        String hcObjName = hcObjNameBuf.toString();
        if (logger.isLoggable(Level.FINE))
            logger.fine("Lookup " + StringUtil.image(hcObjName));

        // Is it in the cache yet?
        HCFile hcObj = lookup(hcObjName);

        if (hcObj == null) {
            // Need to query and create objects

            if (logger.isLoggable(Level.FINE))
                logger.fine("Need query for " + StringUtil.image(hcObjName));

            hcObj = queryAndCreate(viewObj, parsedPath,
                                   names, values, filename);
        }

        if (hcObj == null)
            // Query failed
            throw new FSCacheException(404, "No such file: " + parsedPath);

        if (logger.isLoggable(Level.FINE))
            logger.fine("Freshly created HCFile " + hcObj);

        if (archPathPos < 0) // No archive path components
            return hcObj;

        if (!hcObj.isArchiveObject())
            throw new FSCacheException(404, "No such file: " + parsedPath);

        HCFile obj = handleFromArchive(hcObj, parsedPath, path, archPathPos);
        if (obj == null)
            throw new FSCacheException(404, "No such file: " + parsedPath);

        return obj;
    }

    /**
     * Query the EMD cache for an object in a view that satisfies the
     * request represented the sequence of values and a filename
     */
    private static HCFile queryAndCreate(HCFile viewObj, String parsedPath,
                                         String[] names, String[] values,
                                         String filename)
            throws FSCacheException {
        // Query and create objects

        String[] qNames = names;
        String[] qValues = values;

        HCFile hcObj = null;
        HCInterface.HCObject obj = null;

        if (filename == null) {
            // We don't have a full path; query for directory.

            if (logger.isLoggable(Level.FINE))
                logger.fine("Short path " + StringUtil.image(parsedPath));

            // If we're collapsing trailing nulls, that's the same
            // as truncating the view, so we truncate the list of
            // names to one more than the number of values.
            if (viewObj.isViewCollapsingNulls() &&
                qValues.length < qNames.length - 1) {
                String[] newNames = new String[qValues.length + 1];
                for (int j = 0; j < newNames.length; j++)
                    newNames[j] = qNames[j];
                qNames = newNames;
            }

            // Add the attributes in the filename to the names
            // list, so the query will have "is not null" for
            // those attributes.
            ArrayList fnameAttrs =
                viewObj.getViewFilename().getNeededAttributes();
            String[] allAttrs =
                new String[qNames.length + fnameAttrs.size()];
            int j = 0;
            for (j = 0; j < qNames.length; j++)
                allAttrs[j] = qNames[j];
            for (int k = 0; k < fnameAttrs.size(); k++)
                allAttrs[j++] = (String) fnameAttrs.get(k);

            if (logger.isLoggable(Level.FINE))
                logger.fine("See if we have at least one object for " +
                            toString(allAttrs, qValues));

            obj = HCFile.queryObject(allAttrs, qValues);

            if (obj == null) {
                // There are no objects that match the
                // query. However if GenFS is set, there may still
                // be files in the dir.

                if (!viewObj.isViewCollapsingNulls()) {
                    // We never allow empty directories -- except for
                    // view directories.
                    if (qValues.length > 0)
                        throw new FSCacheException(404, "No such file: " +
                                                        parsedPath);
                }

                // GenFS

                // Last value may be a filename
                filename = values[values.length - 1];

                // Extend the values array with nulls. Don't
                // forget to skip the last component, which we now
                // know is the filename.
                qValues = new String[names.length];

                int i;
                for (i = 0; i < values.length-1; i++)
                    qValues[i] = values[i];
                for ( ; i < qValues.length; i++)
                    qValues[i] = null;

                if (logger.isLoggable(Level.FINE))
                    logger.fine("GenFS trying " + toString(names, qValues));
            }
        }

        if (obj == null && filename != null) {
            // Parse filename to get values; add name-value pairs to
            // arrays
            if (logger.isLoggable(Level.FINE))
                logger.fine("Parsing \"" + filename + "\"");

            Map filenameAttrs = null;
            try {
                filenameAttrs = MDHandler.parseEMD(viewObj, filename);
            }
            catch (FSCacheException e) {
                throw new FSCacheException(404, "No such file: " + parsedPath);
            }

            int newSize = qNames.length + filenameAttrs.size();

            String[] newNames = new String[newSize];
            String[] newValues = new String[newSize];

            int i;
            for (i = 0; i < qNames.length; i++) {
                newNames[i] = qNames[i];
                newValues[i] = qValues[i];
            }

            for (Iterator iter = filenameAttrs.keySet().iterator();
                 iter.hasNext(); i++) {
                String name = (String) iter.next();
                String value = (String) filenameAttrs.get(name);
                newNames[i] = name;
                newValues[i] = value;
            }
           
            qNames = newNames;
            qValues = newValues;

            if (logger.isLoggable(Level.FINE))
                logger.fine("Querying " + toString(qNames, qValues));
           
            obj = HCFile.queryObject(qNames, qValues);
        }

        if (obj == null)
            throw new FSCacheException(404, "No such file: " + parsedPath);

        // CREATE

        if (logger.isLoggable(Level.FINE))
            logger.fine("Create directories for " + parsedPath);
           
        // We now know how many directories there are from
        // the values array. Create all those directories.
        StringBuffer newPath = new StringBuffer(viewObj.fileName());
        HCFile node = viewObj;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                if (viewObj.isViewCollapsingNulls())
                    break;
                throw new RuntimeException("Null in values[" + i + "]");
            }

            newPath.append('/').append(values[i]);

            if (logger.isLoggable(Level.FINER))
                logger.finer("Checking " + newPath);

            if ((hcObj = lookup(newPath.toString())) == null) {
                // Need to create and add an HCFile

                String[] newAttrs = new String[i + 1];
                for (int j = 0; j <= i; j++)
                    newAttrs[j] = values[j];

                if (logger.isLoggable(Level.FINE))
                    logger.fine("Creating " + newPath);

                hcObj = new HCFile(node.getViewIndex(), newAttrs);
                hcObj.setParent(node);
                HCFile.fileCache.add(null, hcObj);
            }
            node = hcObj;
        }

        if (filename != null) {
            // Create the file object
            hcObj = MDHandler.makeObject(obj.oid(), hcObj, filename,
                                         obj.crTime(), obj.size());
            HCFile.fileCache.add(null, hcObj);
        }

        return hcObj;
    }

    /**
     * Given an archive object, load a file (and all ancestor
     * directories) from the values in path, starting at position pos
     */
    private static HCFile handleFromArchive(HCFile hcObj, String parsedPath,
                                            String[] path, int pos)
            throws FSCacheException {
        // "Deep archive" level

        // Remember, HC objects are read-only, so if the
        // entire path doesn't exist in the archive object, we
        // can terminate processing immediately -- no need for
        // further MD queries etc. (TODO)

        logger.info("Handling <deep-archive> object");

        // First, see if we can add the whole path in one shot, i.e.
        // just scanning the archive file once from start to end.

        StringBuffer subPath = new StringBuffer();
        String delim = "";
        for (int i = pos; i < path.length; i++) {
            subPath.append(delim).append(path[i]);
            delim = "/";
        }
        addPath(null, hcObj, subPath.toString());

        try {
            return (HCFile) HCFile.fileCache.lookup(null, parsedPath);
        }
        catch (FSCacheException ignored) {}

        // Otherwise, use addChildren repeatedly. This is guaranteed to
        // find objects even if they can't be found in the first pass.
        // (cf. "synthetic directories" in archivers.ArchiveReader)

        subPath = new StringBuffer();
        for (int i = pos; i < path.length; i++) {
            subPath.append('/').append(path[i]);
            logger.info("trying " +  subPath + " in " + hcObj);

            hcObj.addChildren(null);
            if ((hcObj = lookup(hcObj.fileName() + "/" + path[i])) == null)
                throw new FSCacheException(404, "No such file: " + parsedPath);
        }

        return hcObj;
    }

    /**
     * This object is an archive object. Lookup path and all its
     * ancestors in one swoop. This assumes that ancestors of nodes
     * always come first; if that is not the case, this method will
     * fail and the usual component-by-component cache population
     * algorithm in FSCache.refreshDirectory() will be used.
     */
    private static synchronized HCFile addPath(Object o,
                                               HCFile archive, String path)
            throws FSCacheException  {

        if (logger.isLoggable(Level.FINE))
            logger.fine("Adding \"" + path + "\" to " + archive.fileName());

        Set archiveTopLevelDirs = archive.getTopLevelArchiveDirs();
        long now = System.currentTimeMillis();

        // Add the top-level dirs to the cache
        for (Iterator i = archiveTopLevelDirs.iterator(); i.hasNext(); ) {
            String dir = (String) i.next();
            String[] comps = dir.split("/");
            StringBuffer sb = new StringBuffer();
            HCFile f, obj = archive;
            for (int j = 0; j < comps.length; j++) {
                sb.append(comps[j]);

                try {
                    String name = archive.fileName() + "/" + sb.toString();
                    f = (HCFile) HCFile.fileCache.lookup(null, name);
                }
                catch (FSCacheException e) {
                    if (logger.isLoggable(Level.FINE))
                        logger.fine("Creating \"" + sb + "\"");

                    f = new HCFile(FSCacheObject.ARCHIVEDIRTYPE,
                                   archive.fileName(), sb.toString(), 0L, now,
                                   0, 0, 0555);
                    f.setParent(obj);
                    HCFile.fileCache.add(null, f);
                }

                if (j < comps.length - 1)
                    // The only descendants this object has are
                    // synthetic, and there's only one (since the last
                    // iteration of this loop adds a top-level
                    // directory in the archive object); which means
                    // we know this object is complete.
                    f.setComplete(true);

                obj = f;
                sb.append('/');
            }
        }

        // Figure out if the path we're going to add to the cache has
        // an ancestor in archiveTopLevelDirs
        String archiveAncestor = null;
        for (Iterator i = archiveTopLevelDirs.iterator(); i.hasNext(); ) {
            String dir = (String) i.next();
            if (path.startsWith(dir)) {
                archiveAncestor = dir;
                break;
            }
        }

        if (archiveAncestor == null)
            // path not in the archive
            return null;

        // There's a trailing slash on archiveAncestor
        archiveAncestor =
            archiveAncestor.substring(0, archiveAncestor.length() - 1);

        return HCArchiveReader.addPath(o, archive, archiveAncestor, path);
    }

    /**
     * Find a view of the specified name, refreshing the list of views
     * if necessary
     */
    private static HCFile findView(String viewName)
            throws FSCacheException {

        HCFile viewObj = lookup(viewName);

        if (viewObj == null) {
            // Refresh the root in case the view object was spilled or
            // is a newly added view
            HCFile.getRoot().setComplete(false);
            HCFile.getRoot().addChildren(null);
            if ((viewObj = lookup(viewName)) == null) {
                if (logger.isLoggable(Level.INFO))
                    logger.info("No view \"" + viewName + "\"");
                throw new FSCacheException(400, "No such view: " + viewName);
            }
        }

        return viewObj; 
    }

    private static HCFile lookup(String path) {
        try {
            if (logger.isLoggable(Level.FINE))
                logger.fine("lookup(" + path + ")");

            return (HCFile) HCFile.fileCache.lookup(null, path);
        }
        catch (FSCacheException e) {
            return null;
        }
    }

    /**
     * Extract values from path that correspond with the names in a view.
     */
    private static String[] getValuesFromPath(String[] path, int numNames) {
        int nComps = path.length - 1; // don't count view name
        int nValues = nComps;
        if (nComps > numNames) {
            nValues = numNames;
        }
        if (nComps > numNames + 1) // +1 is the filename
            nComps = numNames + 1;

        String[] values = new String[nValues];
        for (int i = 0; i < values.length; i++)
            values[i] = path[i+1]; // +1 is the view name

        return values;
    }

    static private String toString(String[] names, String[] values) {
        StringBuffer sb = new StringBuffer("[");

        int i;
        String delim = "";
        for (i = 0; i < values.length; i++) {
            sb.append(delim).append(names[i]).append('=');
            sb.append(StringUtil.image(values[i]));
            delim = ", ";
        }
        for ( ; i < names.length; i++)
            sb.append(delim).append('?').append(names[i]);

        return sb.append(']').toString();
    }

}
