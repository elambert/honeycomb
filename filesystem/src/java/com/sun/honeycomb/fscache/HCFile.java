
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

import com.sun.honeycomb.archivers.HCXArchive;
import com.sun.honeycomb.archivers.ArchiveReader;

import com.sun.honeycomb.connectors.HCInterface;
import com.sun.honeycomb.connectors.MDHandler;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ByteArrays;

import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.Filename;
import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.FsAttribute;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.nio.channels.Channels;


public class HCFile extends FSCacheObject {

    //////////////////////////////////////////////////////////////////////
    // Private

    public static HCInterface hc = null;


    private static final String PNAME_CACHECLASSNAME =
        "honeycomb.fscache.classname";

    private static final String cacheClassPrefix = "com.sun.honeycomb.fscache";
    private static final String defaultCacheClassName = "JavaFSCache";

    private static final byte INCARNATION_MAGIC = (byte)0xca;
    
    private static final Logger logger = Logger.getLogger(HCFile.class.getName());

    public static FSCache fileCache = null;
    private static String cacheClassName = null;

    public static FsView[] views;
    private static Map viewMap = null;

    private static boolean initialized = false;

    private static int CUR_PARENT_INDEX = 0;

    private static byte[] nullOid;

    // Fields 

    private short viewIndex;     // Index of the object's view
    private String[] viewAttrNames;

    private String[] attributes; // The sequence of attributes is the path
                                 // (null if ROOTFILETYPE)

    private int rootDistance;    // Distance of node from root

    private NewObjectIdentifier oid; // null if this is a ROOTFILETYPE

    private Set archiveTopLevelDirs = null;
    private Filename viewFilename = null;

    //////////////////////////////////////////////////////////////////////
    // Public

    // Statics

    public synchronized static void init(Properties config, HCInterface hc)
            throws FSCacheException {

        if (initialized)
            return;

        HCFile.hc = hc;

        cacheClassName = config.getProperty(PNAME_CACHECLASSNAME);
        if (cacheClassName == null)
            cacheClassName = defaultCacheClassName;

        HCFile.cacheClassName = cacheClassPrefix + "." + cacheClassName;

        int err = FSCacheException.FSERR_SERVERFAULT;

        if (!getViews())
            throw new RuntimeException("Couldn't load views");

        try {
            Class cacheClass = Class.forName(cacheClassName);
            fileCache = (FSCache) cacheClass.newInstance();
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "**** exception!", e);

            String msg = "Couldn't load the FS cache class \"" +
                cacheClassName + "\"";
            FSCacheException newe = new FSCacheException(err, msg);
            newe.initCause(e);
            throw newe;
        }

        nullOid = new byte[NewObjectIdentifier.OID_LENGTH];
        Arrays.fill(nullOid, (byte)0);

        // The root filename MUST be well-formed: /, /foo, /foo/bar, etc.
        // -- starts with /, no trailing slash, no multiple slashes.
        HCFile root = new HCFile();
        root.setFileName("/");
        // The root directory has no parent.
        
        fileCache.initialize(root, config);
        root.addChildren(null);

        logger.info("HCFile initialized.");

        initialized = true;
    }

    public synchronized static void reset() {
        initialized = false;
        FSCache.reset();
        fileCache = null;
        logger.info("FS cache cleared.");
    }

    public static HCFile getRoot() {
        return (HCFile) fileCache.getRoot();
    }

    public static HCInterface.HCObject queryObject(String[] names,
                                                   String[] values) {
        return hc.getObject(names, values);
    }

    /**
     * Create and return a new HCFile object
     *
     * @param parent the object who's child the new object will be
     * @param name the basename of the new file
     * @param oid the OID of the new object
     * @param extra attribute values besides the ones inherited from parent
     *
     * Used only by the PUT handler.
     */
    public static HCFile createHCFile(HCFile parent, String name,
                                      NewObjectIdentifier oid,
                                      Map extra)
        throws FSCacheException {

        if (logger.isLoggable(Level.FINE)) {
            String msg = "New object has attributes";
            Iterator i = extra.keySet().iterator();
            while (i.hasNext()) {
                String key = (String) i.next();
                String value = (String) extra.get(key);
                msg += " " + key + "=\"" + value + "\"";
            }
            logger.fine(msg);
        }

        long crTime = getLong(extra, HCInterface.FIELD_CTIME, 0); 
        long size = getLong(extra, HCInterface.FIELD_SIZE, 0);

        long uid = getLong(extra, HCInterface.FIELD_UID, -3);
        long gid = getLong(extra, HCInterface.FIELD_GID, -3);
        long mode = getLong(extra, HCInterface.FIELD_MODE, 0440);

        HCFile newFile =
            new HCFile(FILELEAFTYPE,
                       parent.getViewIndex(), parent.getAttributes(),
                       name, oid, size, crTime, uid, gid, mode);

        newFile.setParent(parent);
        newFile.setMimeType(getString(extra, HCInterface.FIELD_MIMETYPE,
                                      "application/octet-stream"));

        fileCache.add(null, newFile);

        return newFile;
    }

    // Constructors


    // Constructs the root file
    public HCFile() {
        super();
        viewIndex = -1;
        attributes = null;
        oid = null;
        rootDistance = 0;
    }

    // Shortcut for directory entries
    public HCFile(short view, String[] attrs) {
        this(DIRECTORYTYPE, view, attrs, null, null, 0,
             System.currentTimeMillis(), 0, 0, 0555);
    }

    // Used for creation of all non-archive cache objects
    public HCFile(byte fileType, short viewIndex, String[] attributes,
                  String filename, NewObjectIdentifier oid,
                  long size, long crtime, long uid, long gid, long mode) {
        this(fileType, viewIndex, attributes, filename, oid,
             (fileType == DIRECTORYTYPE)? nextParentIndex() : -1,
             size, crtime, uid, gid, mode, null);
    }

    // This is the most "complete" constructor, i.e. all fields are specified
    public HCFile(byte fileType, short viewIndex, String[] attributes,
                  String filename, NewObjectIdentifier oid,
                  int idx, long size,
                  long crtime, long uid, long gid, long mode,
                  String archiveObjectPath) {
        super(fileType, filename, idx, crtime, crtime, crtime, uid, gid,
              mode, size, null, false);

        this.viewIndex = viewIndex;
        this.attributes = attributes;
        this.oid = oid;
        setFilename();
    }

    /** This is the constructor used for archive objects, and is mostly
     * independent of the above constructors */
    public HCFile(byte fileType, String archivePath, String pathInArchive,
                  long size, long crtime, long uid, long gid, long mode) {
        super(fileType, archivePath, pathInArchive, crtime, crtime, crtime,
              uid, gid, mode, size);

        // Lookup archive object and get viewIndex, OID etc.
        HCFile parent = null;
        try {
            parent = (HCFile) fileCache.lookup(null, archiveObjectPath());
        }
        catch (FSCacheException e) {
            String msg = "Couldn't find parent \"" + archiveObjectPath() +
                "\" for object \"" + fileName() + "\"";
            throw new RuntimeException(msg);
        }

        this.viewIndex = parent.getViewIndex();
        this.attributes = parent.getAttributes();
        this.oid = parent.getOID();
        this.viewAttrNames = parent.getViewAttrNames();
    }


    ////////////////////////////////////////////////////////////////////////

    public synchronized void setParent(FSCacheObject parent) {
        if (parent == null)
            return;

        super.setParent(parent);

        rootDistance = ((HCFile)parent()).rootDistance() + 1;
        if (parent.fileType() == ROOTFILETYPE) {
            makeViewAttrNames();
            return;
        }

        this.viewAttrNames = ((HCFile)parent()).getViewAttrNames();
    }

    /**
     * Create directories in the cache such that the path combine(pathTokens)
     * from "this" is a plain file. (This is a convenience method for the PUT
     * handler.)
     */
    public HCFile mkDirsFor(String[] pathTokens) throws FSCacheException {
        if (this == null || pathTokens == null || pathTokens.length == 0)
            return this;

        String[] attrs = getAttributes();

        // A String[] path never includes the view name. In pathTokens
        // we also have a filename.
        if (pathTokens.length + attrs.length > getViewAttrNames().length + 1)
            // Too many components in the path
            throw new FSCacheException(FSCacheException.FSERR_NOENT,
                                       "Path " + FSCache.combine(pathTokens) +
                                       " too long");

        HCFile node = this;
        String newPath = fileName();

        for (int i = 0; i < pathTokens.length - 1; i++) {

            // Build new attribute array and copy in parent's values
            String[] newAttrs = new String[attrs.length + 1];
            for (int j = 0; j < attrs.length; j++)
                newAttrs[j] = attrs[j];

            newAttrs[attrs.length] = pathTokens[i];

            HCFile newNode = new HCFile(getViewIndex(), newAttrs);
            newNode.setParent(node);
            fileCache.add(null, newNode);

            attrs = newAttrs;
            node = newNode;
        }

        return node;
    }

    public String getContentLanguage() {
        return("en");
    }

    public short getViewIndex() {
        return viewIndex;
    }
    public String[] getAttributes() {
        return attributes;
    }
    public NewObjectIdentifier getOID() {
        return oid;
    }

    public int rootDistance() {
        return rootDistance;
    }

    public byte[] getOidBytesExternal() {
        if (oid == null)
            return nullOid;
        else
            return ByteArrays.toByteArray(oid.toExternalHexString());
    }

    public String getOidHexString() {
        return ByteArrays.toHexString(getOidBytesExternal());
    }

    public String[] getViewArchiveTypes() {
        return views[viewIndex].getArchiveTypes();
    }

    public boolean viewUsesExtendedAttrs() {
        return views[viewIndex].usesExtendedAttrs();
    }

    public boolean writeContents(long offset, long length, OutputStream os)
            throws IOException {

        if (length < 0)
            throw new InternalException("Write negative no. of bytes???");

        if (length == 0) {
            logger.info("Ignoring request to write 0 bytes");
            return true;
        }

        if (logger.isLoggable(Level.INFO))
            logger.info("Writing [" + offset + ":+" + length + "] of " +
                     fileName() + " (filesize=" + size() + ")");

        if (!isArchiveObject())
            return hc.writeObject(getOID(), Channels.newChannel(os),
                                  offset, length);

        String archivePath = archiveObjectPath();

        // Find path by subtracting archive root from filename
        String ppath = archivePath + "/";
        if (!fileName().startsWith(ppath))
            throw new RuntimeException("Bad obj. path: " + toString());
        String subPath = fileName().substring(ppath.length());

        try {
            HCFile archObj = (HCFile) fileCache.lookup(null, archivePath);
            // Write to Channel instead of OutputStream => libarchive crash
            return archObj.writeFromArchive(os, subPath, offset, length);
        }
        catch (FSCacheException e) {
            logger.severe("Couldn't find archive object " + archiveObjectPath());
            return false;
        }
    }

    public String[] getViewAttrNames() { return viewAttrNames; }

    public boolean isViewReadOnly() {
        if ((viewIndex < 0) || (viewIndex >= views.length))
            throw new RuntimeException("Invalid view index: " + viewIndex);
        return views[viewIndex].isReadOnly();
    }

    public boolean isViewCollapsingNulls() {
        if ((viewIndex < 0) || (viewIndex >= views.length))
            throw new RuntimeException("Invalid view index: " + viewIndex);
        return views[viewIndex].isCollapsingNulls();
    }

    public synchronized Filename getViewFilename() {
        if (viewFilename == null) {
            if ((viewIndex < 0) || (viewIndex >= views.length))
                throw new RuntimeException("Invalid view index: " + viewIndex);
            viewFilename = views[viewIndex].getFilename();
        }
        return viewFilename;
    }

    public Namespace getViewNamespace() {
        if ((viewIndex < 0) || (viewIndex >= views.length))
            throw new RuntimeException("Invalid view index: " + viewIndex);
        return views[viewIndex].getNamespace();
    }

    public String getViewName() {
        if ((viewIndex < 0) || (viewIndex >= views.length))
            throw new RuntimeException("Invalid view index: " + viewIndex);
        return views[viewIndex].getName();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(super.toString());

        // Replace the trailing "]" from FSCacheObject's string
        sb.setCharAt(sb.length()-1, '{');

        if (fileType() == ROOTFILETYPE)
            sb.append('-');
        else
            sb.append(views[viewIndex].getName());

        sb.append(' ').append('+').append(rootDistance());

        if (attributes != null) {
            for (int i = 0; i < attributes.length; i++)
                sb.append(' ').append('"').append(attributes[i]).append('"');
            sb.append(' ');
        }

        return sb.append('}').append(']').toString();
    }

    //serialize external-format oid
    public void writeOutOID(DataOutputStream os) throws IOException {
        os.write(getOidBytesExternal());
    }

    //deserialize external-format OID
    public void readInOID(DataInputStream input) throws IOException {
        byte[] oidBytes = new byte[NewObjectIdentifier.OID_LENGTH];
        input.read(oidBytes);
        if (ByteArrays.equals(oidBytes, nullOid))
            oid = null;
        else
            oid = NewObjectIdentifier.fromExternalHexString(
                       ByteArrays.toHexString(oidBytes));
    }

    public void readIn(DataInputStream input) throws IOException {
        super.readIn(input);

        viewIndex = input.readShort();

        short nAttr = input.readShort();
        if (nAttr <= 0)
            attributes = null;
        else {
            attributes = new String[nAttr];
            for (int i = 0; i < nAttr; i++) {
                short strLen = input.readShort();
                byte[] attr = new byte[strLen];
                input.read(attr);
                attributes[i] = new String(attr);
            }
        }
    }

    public void writeOut(DataOutputStream os) throws IOException {
        super.writeOut(os);

        os.writeShort(getViewIndex());

        String[] attributes = getAttributes();
        if (attributes == null)
            os.writeShort(0);
        else {
            os.writeShort((short) attributes.length);
            for (int i = 0; i < attributes.length; i++) {
                byte[] attrArray;
                try {
                    attrArray = attributes[i].getBytes("UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                os.writeShort((short) attrArray.length);
                os.write(attrArray);
            }
        }
    }

    public synchronized Set getTopLevelArchiveDirs() {
        if (fileType() != FILELEAFTYPE || !isArchiveObject())
            throw new InternalException("not an archive object");

        if (archiveTopLevelDirs == null)
            archiveTopLevelDirs = HCArchiveReader.getTopLevelDirs(this);

        return archiveTopLevelDirs;
    }

    public boolean delete() {
        try {
            hc.delete(getOID());
            return true;
        }
        catch (ArchiveException e) {
            logger.log(Level.WARNING, "Couldn't delete " + toString(), e);
        }
        return false;
    }

    /**
     * The main interface to MD
     */
    public int addChildren(Object group) throws FSCacheException {

        boolean ok = false;

        if (isComplete() && !isOld()) {
            // We've already added all children
            if (logger.isLoggable(Level.FINE))
                logger.fine("Children up to date in " + fileName());
            return numChildren();
        }

        long startTime = System.currentTimeMillis();
        int numValues = refreshChildren(group);

        if (numValues >= 0) {
            setComplete(true);
            setMtime(System.currentTimeMillis());
            setAtime(mtime());

            if (logger.isLoggable(Level.INFO))
                logger.info("To add " + numChildren() + " children of \"" +
                            fileName() + "\": " +
                            (System.currentTimeMillis() - startTime) + "ms");
        }
        else
            logger.warning("Children of " + fileName() + " still incomplete.");

        if (numValues != numChildren())
            logger.info("Possible inconsistency in " + fileName() + ": saw " +
                        numValues + " objects but now " +
                        numChildren() + " there are children.");

        return numChildren();
    }

    public void setMimeType(String mimeType) {
        super.setMimeType(mimeType);

        // Also see if this is an archive object that we auto-open
        if (fileType() == FILELEAFTYPE && 
                typeIsIn(mimeType, getViewArchiveTypes()))
            setArchiveObjectPath(fileName(), null);

        // Archive file objects inherit archiveObjectPath from their parent
    }


    public boolean canMakeDir() {
        // The object must NOT be at the lowest level of the synthetic
        // FS hierarchy (not counting inside archive objects).  In
        // other words, the object must have fewer attribute values
        // than its view has attributes.
        return fileType() == DIRECTORYTYPE &&
            attributes.length < views[viewIndex].getAttributes().size();
    }

    public boolean canMakeFile() {
        // The object must be at the lowest level of the synthetic FS
        // hierarchy (not counting inside archive objects).  In other
        // words, the parent must have exactly the same number of
        // attribute values as its view has attributes.

        return fileType() == DIRECTORYTYPE &&
            attributes.length == views[viewIndex].getAttributes().size();
    }

    public FSCacheObject newObject() {
        return new HCFile();
    }

    ////////////////////////////////////////////////////////////////////////

    // Other properties: for compliance. The only known attributes are
    // "expiration date" (which is a string version of a date+time) and
    // "legal holds" (a list of strings).

    public void setExpiration(Date when) throws ArchiveException {
        hc.setExpiration(getOID(), when);
    }

    public Date getExpiration() throws ArchiveException {
        return hc.getExpiration(getOID());
    }

    public void addLegalHold(String tag) throws ArchiveException {
        hc.addLegalHold(getOID(), tag);
    }

    public void removeLegalHold(String tag) throws ArchiveException {
        hc.removeLegalHold(getOID(), tag);
    }

    public void setLegalHolds(Set value) throws ArchiveException {
        hc.setLegalHolds(getOID(), value);
    }

    public Set getLegalHolds() throws ArchiveException {
        return hc.getLegalHolds(getOID());
    }

    public boolean isDeletable() {
        try {
            return hc.isDeletable(getOID());
        }
        catch (ArchiveException e) {}
        return true;
    }

    /**********************************************************************
     * 
     * Private methods
     *
     **********************************************************************/

    /** Extract and write out a file from an archive */
    boolean writeFromArchive(OutputStream os,
                             String path, long offset, long len)
            throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("Searching archive \"" + fileName() +
                     "\" for \"" + path + "\"");

        ArchiveReader ar = HCArchiveReader.openArchive(this);
        try {
            HCXArchive.Stat stat = ar.skipTo(path);
            if (stat == null)
                // Should never happen
                throw new RuntimeException("Is \"" + path + "\" in archive "
                                           + fileName() + "?");

            if (len == HCInterface.UNKNOWN_SIZE)
                len = stat.size() - offset;

            if (logger.isLoggable(Level.INFO))
                logger.info("Writing " + stat + ": " + len +
                         " bytes (offset " + offset + ")");

            ar.setOutput(os);
            ar.write(offset, len);
        }
        finally {
            ar.close();
        }

        logger.fine("Done.");

        return true;
    }

    /** This is where all queries are done */
    private int refreshChildren(Object group) throws FSCacheException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("Refreshing children for \"" + fileName() + "\"");

        // For the root, the children are all the views
        if (fileType() == ROOTFILETYPE) {
            if (getViews())
                logger.info("Views reloaded");
            else
                throw new InternalException("Couldn't load views!");

            for (short i = 0; i < views.length; i++) {
                HCFile v = new HCFile(i, new String[0]);
                v.setParent(fileCache.getRoot());
                if (logger.isLoggable(Level.INFO))
                    logger.info("View " + v);
                fileCache.add(group, v);
            }
            return views.length;
        }

        if ((viewIndex < 0) || (viewIndex >= views.length))
            throw new FSCacheException(FSCacheException.FSERR_SERVERFAULT,
                                       "Invalid view index: " + viewIndex);
        FsView view = views[viewIndex];

        // When an object is first created and its MIME type set, we
        // also figured out whether or not it's an auto-open archive
        // object.
        if (isArchiveObject())
            return importArchiveChildren(group);

        if (fileType() == FILELEAFTYPE)
            throw new FSCacheException(FSCacheException.FSERR_NOENT,
                                       "Not a directory: " + fileName());

        // Otherwise, we need to do a EMD query.

        int nValues = 0;
        String[] attrNames = getViewAttrNames();
        String[] names;         // actually used in the query
        String[] values;         // actually used in the query

        if (attributes.length < attrNames.length && view.isCollapsingNulls()) {
            // Add any files that show up at this level

            if (logger.isLoggable(Level.FINE))
                logger.fine(fileName() + " adding intermediate (genfs) files");

            int i;
            values = new String[attrNames.length];
            for (i = 0; i < attributes.length; i++)
                values[i] = attributes[i];
            for ( ; i < values.length; i++)
                values[i] = null;

            nValues +=
                MDHandler.runQueryAddChildren(this, group,
                                              attrNames, values,
                                              view.getFilename());

            // For directory children, if we're collapsing nulls we
            // have to relax the restriction that all attributes in
            // the view must have values; only the attributes that
            // have values and the next one need be considered. In
            // other words, collapsing nulls is the same as trucating
            // the view spec. to the number of non-null attr values.
            names = new String[1 + attributes.length];
            values = new String[attributes.length];
            for (i = 0; i < attributes.length; i++) {
                names[i] = attrNames[i];
                values[i] = attributes[i];
            }
            names[i] = attrNames[i];
        }
        else {
            names = attrNames;
            values = attributes;
        }

        if (logger.isLoggable(Level.FINE))
            logger.fine(fileName() + " adding children");

        // Run query and add children

        nValues += MDHandler.runQueryAddChildren(this, group,
                                                 names, values,
                                                 view.getFilename());

        return nValues;
    }

    // This method is not called for archive objects
    private void setFilename() {
        String rootPath = fileCache.getRoot().fileName();
        if (rootPath.equals("/"))
            rootPath = "";
        StringBuffer buf = new StringBuffer(rootPath);

        buf.append('/').append(FSCache.quote(views[viewIndex].getName()));

        String attrList = FSCache.combine(attributes);
        if (attrList.length() > 1) // leading slash
            buf.append(FSCache.combine(attributes));

        // Calculate displayname
        String displayName = views[viewIndex].getName();
        if (attributes != null && attributes.length > 0)
            displayName = attributes[attributes.length - 1];

        if (fileType() == FILELEAFTYPE) {
            buf.append('/').append(fileName());
            displayName = fileName();
        }

        setFileName(buf.toString());
        if (logger.isLoggable(Level.FINE))
            logger.fine("Setting displayName \"" + displayName + "\" for " +
                        toString());
        setDisplayName(displayName);
    }

    private int importArchiveChildren(Object o) throws FSCacheException {
        // This object is an archive object. Open the archive and add
        // all files that are children of this node.

        HCFile archObj = (HCFile) fileCache.lookup(o, archiveObjectPath());

        return HCArchiveReader.importArchiveChildren(o, archObj, this);
    }

    private void makeViewAttrNames() {
        if ((viewIndex < 0) || (viewIndex >= views.length))
            throw new RuntimeException("Invalid view index: " + viewIndex);
        ArrayList attrs = views[viewIndex].getAttributes();

        StringBuffer sb = null;
        if (logger.isLoggable(Level.FINEST)) {
            sb = new StringBuffer();
            sb.append("View <");
            sb.append(views[viewIndex].getName());
            sb.append(">:");
        }

        viewAttrNames = new String[attrs.size()];
        for (int i = 0; i < attrs.size(); i++) {
            FsAttribute attr = (FsAttribute) attrs.get(i);
            viewAttrNames[i] = attr.getField().getQualifiedName();

            if (sb != null) {
                sb.append(' ').append(attr.getField().getTypeString());
                sb.append(' ').append(viewAttrNames[i]);
            }
        }

        if (sb != null)
            logger.finest(sb.toString());
    } // makeViewAttrNames

    /** Refresh the set of known views */
    private static synchronized boolean getViews() throws FSCacheException {
        views = hc.getViews();
        viewMap = makeMap(views);
        return true;
    }

    private static long getLong(Map m, String name, long defVal) {
        try {
            return Long.parseLong((String)m.get(name));
        }
        catch (Exception ignored) {}
        return defVal;
    }

    private static String getString(Map m, String name, String defVal) {
        String v = null;
        try {
            v = (String) m.get(name);
        }
        catch (Exception ignored) {}

        if (v != null)
            return v;
        return defVal;
    }

    private static boolean typeIsIn(String mimeType, String[] allTypes) {
        if (mimeType == null || allTypes == null)
            return false;

        if (logger.isLoggable(Level.FINE)) {
            String msg = "Checking \"" + mimeType + "\" against {";
            for (int i = 0; i < allTypes.length; i++)
                msg += " \"" + allTypes[i] + "\"";
            logger.fine(msg + " }");
        }

        String parsedType = mimeType;
        // Remove leading "application/(x-)?"
        if (parsedType.startsWith("application/"))
            parsedType = parsedType.substring(parsedType.indexOf('/') + 1);
        if (parsedType.startsWith("x-"))
            parsedType = parsedType.substring(2);
        if (parsedType.equals("iso9660-image"))
            parsedType = "iso9660";

        for (int i = 0; i < allTypes.length; i++)
            if (allTypes[i].equals(parsedType))
                return true;
        return false;
    }

    private static synchronized int nextParentIndex() {
        return  ++CUR_PARENT_INDEX;
    }

    private static Map makeMap(FsView[] views) {
        Map m = new HashMap();
        for (short i = 0; i < views.length; i++) {
            if (logger.isLoggable(Level.FINE)) {
                String msg = "View \"" + views[i].getName() + "\"";
                Namespace ns = views[i].getNamespace();
                if (ns != null)
                    msg += " (default namespace " + ns.getName() + ")";
                logger.fine(msg + ".");
            }
            m.put(views[i].getName(), new Short(i));
        }
        return m;
    }
}
