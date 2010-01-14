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

import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.connectors.HCInterface;
import com.sun.honeycomb.archivers.HCXArchive;
import com.sun.honeycomb.archivers.ArchiveReader;

import com.sun.honeycomb.common.NewObjectIdentifier;

import java.io.OutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 * Util class for HCFile, to hookup the "deep archive" stuff
 */
class HCArchiveReader {

    protected static final Logger logger =
        Logger.getLogger(HCArchiveReader.class.getName());

    // This object is an archive object. Lookup path and all its
    // ancestors in one swoop. This assumes that ancestors of nodes
    // always come before nodes; if that is not the case, this method
    // will fail and the usual component-by-component cache population
    // algorithm in FSCache.refreshDirectory() will be used. The
    // "archiveAncestor" is one of the top-level dirs in the archive.
    static HCFile addPath(Object o, HCFile archiveRoot,
                          String archiveAncestor, String path)
            throws FSCacheException  {
        HCFile f = null;
        HCFile parent = archiveRoot;

        if (logger.isLoggable(Level.FINE))
            logger.fine("Adding \"" + path + "\" (" + archiveAncestor + ")");

        // Example: to add path src/trunk/filesystem/bin/svndiff with
        // archiveAncestor "src/trunk/filesystem/". We have to add
        // directories $archiveRoot/src and $archiveRoot/src/trunk
        // before adding $archiveRoot/src/trunk/filesystem,
        // $archiveRoot/src/trunk/filesystem/bin, and
        // $archiveRoot/src/trunk/filesystem/bin/svndiff from the
        // archive.

        StringBuffer relPathBuf = new StringBuffer();

        String[] tokens = archiveAncestor.split("/");

        StringBuffer sb = new StringBuffer(archiveRoot.fileName());

        // Don't add the last component of archiveAncestor: that's
        // added in the first iteration of the archive reader loop
        // below
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append('/').append(tokens[i]);
            relPathBuf.append('/').append(tokens[i]);

            f = new HCFile(FSCacheObject.ARCHIVEDIRTYPE,
                           archiveRoot.fileName(), relPathBuf.substring(1),
                           0, System.currentTimeMillis(),
                           0, 0, 0666);

            if (logger.isLoggable(Level.FINE))
                logger.fine("Adding archive child " + f);

            f.setParent(parent);
            parent.addChild(f);
            parent.setComplete(true);
            HCFile.fileCache.add(o, f);
            parent = f;
        }

        // Now relPathBuf is the parent of archiveAncestor. Split the
        // rest of the path into tokens, and these directories can all
        // be added from the archive.
        //
        // Note: relPathBuf has a leading slash, and in subPath we
        // want to delete an extra slash; so the relPathBuf.length()
        // here is really relPathBuf.length() - 1 + 1
        //
        //     archAnc =  src/trunk/filesystem
        //     path    =  src/trunk/filesystem/bin/svndiff
        //     relPath = /src/trunk
        //     subPath =            filesystem/bin/svndiff
        //
        // If archiveAncestor only has one component,
        //
        //     archAnc =  src
        //     path    =  src/trunk/filesystem/bin/svndiff
        //     relPath = 
        //     subPath =  src/trunk/filesystem/bin/svndiff

        String subPath = path.substring(relPathBuf.length());
        String relPath = relPathBuf.toString();
        if (relPathBuf.length() > 0)
            // trim leading slash from relPath
            relPath = relPathBuf.substring(1);

        // Load the archive file into the archive reader
        ArchiveReader ar = openArchive(archiveRoot);

        sb = new StringBuffer(relPath);

        String[] names = subPath.split("/");

        for (int i = 0; i < names.length; i++) {
            sb.append('/').append(names[i]);
            String newPath = sb.toString();
            if (logger.isLoggable(Level.FINE))
                logger.fine("Trying \"" + newPath + "\" in archive");

            HCXArchive.Stat stat = ar.skipTo(newPath);
            if (stat == null)
                // No such directory
                return null;

            byte type = FSCacheObject.ARCHIVEFILETYPE;
            if (ar.isDirectory())
                type = FSCacheObject.ARCHIVEDIRTYPE;

            f = new HCFile(type, archiveRoot.fileName(), newPath,
                           stat.size(), stat.mtime(),
                           stat.uid(), stat.gid(), stat.mode());

            if (logger.isLoggable(Level.FINE))
                logger.fine("Adding archive child " + f);

            f.setParent(parent);
            parent.addChild(f);
            HCFile.fileCache.add(o, f);
            parent = f;
        }

        return f;
    }

    static int importArchiveChildren(Object o, HCFile archiveRoot,
                                     HCFile parent)
            throws FSCacheException {
        // This object is an archive object. Open the archive and add
        // all files that are children of this node.

        int nEntries = 0;
        int nMatchingEntries = 0;
        String archiveRootPath = archiveRoot.fileName();

        // Initialize an ArchiveReader with the contents of the archive
        ArchiveReader ar = null;
        try {
            // Find objects that are my children and add them to the
            // cache. We also need to figure out if they are directories
            // or plain files.

            ar = openArchive(archiveRoot);

            // Some archives don't include entries for all
            // directories; nextWithFill() inserts the "missing"
            // directories so we get a complete tree rooted at the
            // parent

            if (logger.isLoggable(Level.FINE))
                logger.fine("Importing children for " + parent);

            HCXArchive.Stat stat;
            while ((stat = ar.nextWithFill()) != null) {

                String fullPath = archiveRootPath + "/" + stat.name();
                String parentPath = parentOf(fullPath);

                nEntries++;
                if (logger.isLoggable(Level.FINER))
                    logger.finer("Got " + fullPath + " (" + stat.name() + ")" +
                                 "; parentOf(o) = \"" + parentPath + "\"");

                if (parentPath.equals(parent.fileName())) {
                    byte type = FSCacheObject.ARCHIVEFILETYPE;
                    if (ar.isDirectory())
                        type = FSCacheObject.ARCHIVEDIRTYPE;

                    HCFile f = new HCFile(type, archiveRootPath, stat.name(),
                                          stat.size(), stat.mtime(),
                                          stat.uid(), stat.gid(), stat.mode());

                    f.setParent(parent);
                    parent.addChild(f);
                    if (logger.isLoggable(Level.FINE))
                        logger.fine("Found \"" + stat.name() + "\"; add " + f);
                    HCFile.fileCache.add(o, f);
                    nMatchingEntries++;
                }
            }

            if (logger.isLoggable(Level.FINE))
                logger.fine("Entries: matching/total " +
                            nMatchingEntries + "/" + nEntries +
                            " in " + parent);

            parent.setComplete(true);
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Import FAILED " + parent.toString(), e);
            return -1;
        }
        finally {
            try {
                if (ar != null)
                    ar.close();
            } catch (IOException e) {}
        }

        return nMatchingEntries;
    }

    static ArchiveReader openArchive(HCFile archObj) {
        try {
            if (logger.isLoggable(Level.FINE))
                logger.fine("Importing from " + archObj);

            ArchiveReader ar = new ArchiveReader();
            writeObjectAsync(ar.getStream(), archObj.getOID(), archObj.size());
            return ar;
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Opening archive " + archObj, e);
        }

        return null;
    }

    // Return the list of top-level directories in the archive. This
    // is usually the arguments passed to tar, e.g.
    //     tar cf archive.tar foobar bin/foo/bar /foo
    // has top level dirs "foobar" "bin/foo/bar"  and "foo"
    static Set getTopLevelDirs(HCFile archObj) {
        ArchiveReader ar = null;
        Set dirs = new HashSet();

        try {
            ar = openArchive(archObj);
            if (ar == null)
                return null;

            HCXArchive.Stat stat;
            while ((stat = ar.next()) != null) {
                String path = stat.name() + "/";

                if (!hasAncestorInDirs(dirs, path))
                    dirs.add(path);

                if (logger.isLoggable(Level.FINER))
                    logger.finer(" +++ \"" + path + "\"");
            }
        }
        finally {
            try {
                if (ar != null)
                    ar.close();
            } catch (IOException e) {}
        }

        if (logger.isLoggable(Level.FINE)) {
            String msg = "Archive \"" + archObj.fileName() + "\":";
            for (Iterator i = dirs.iterator(); i.hasNext(); )
                msg += " \"" + i.next().toString() + "\"";
            logger.fine(msg);
        }

        return dirs;
    }

    // return value: whether or not path has an ancestor in the list
    // of directories
    private static boolean hasAncestorInDirs(Set dirs, String path) {
        for (Iterator i = dirs.iterator(); i.hasNext(); ) {
            String d = (String) i.next();
            if (path.startsWith(d))
                // Everything's OK
                return true;

            if (d.startsWith(path)) {
                // Need to replace d with path
                i.remove();
                return false;
            }
        }

        // "path" has no ancestor in dirs
        return false;
    }

    private static String parentOf(String path) {
        if (path.equals("/"))
            return "/";

        int pos = path.lastIndexOf('/');
        if (pos < 0)
            return ".";
        if (pos == 0)
            return "/";

        return path.substring(0, pos);
    }

    static private void writeObjectAsync(OutputStream os,
                                         NewObjectIdentifier oid,
                                         long objSize) {
        WorkerThread wr = new WorkerThread(os, oid, objSize);
        wr.start();
    }

    static class WorkerThread extends Thread {
        private OutputStream os;
        private NewObjectIdentifier oid;
        private long size;

        public WorkerThread(OutputStream os, NewObjectIdentifier oid, long l) {
            this.os = os;
            this.oid = oid;
            this.size = l;
        }

        public void run() {
            try {
                HCFile.hc.writeObject(oid, os, 0L, size);
            }
            catch (Exception e) {}
            finally {
                try { os.close(); } catch (Exception e) {}
            }
        }
    }

    public static void main(String[] args) {
        List paths = new LinkedList();

        paths.add("src/trunk/filesystem/");
        paths.add("src/trunk/filesystem/.svn/");
        paths.add("src/trunk/filesystem/.svn/text-base/");
        paths.add("src/trunk/filesystem/.svn/text-base/build.properties.svn-base/");
        paths.add("src/trunk/filesystem/.svn/text-base/build.xml.svn-base/");
        paths.add("src/trunk/filesystem/.svn/prop-base/");
        paths.add("src/trunk/filesystem/.svn/prop-base/build.properties.svn-base/");
        paths.add("src/trunk/filesystem/.svn/prop-base/build.xml.svn-base/");
        paths.add("src/trunk/filesystem/.svn/props/");
        paths.add("src/trunk/filesystem/.svn/props/build.properties.svn-work/");
        paths.add("src/trunk/filesystem/.svn/props/build.xml.svn-work/");
        paths.add("src/trunk/filesystem/.svn/wcprops/");
        paths.add("src/trunk/filesystem/.svn/wcprops/build.properties.svn-work/");
        paths.add("src/trunk/filesystem/.svn/wcprops/build.xml.svn-work/");
        paths.add("src/trunk/filesystem/.svn/tmp/");
        paths.add("src/trunk/filesystem/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/.svn/entries/");
        paths.add("src/trunk/filesystem/.svn/empty-file/");
        paths.add("src/trunk/filesystem/.svn/README.txt/");
        paths.add("src/trunk/filesystem/.svn/format/");
        paths.add("src/trunk/filesystem/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/");
        paths.add("src/trunk/filesystem/src/.svn/");
        paths.add("src/trunk/filesystem/src/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/.svn/props/");
        paths.add("src/trunk/filesystem/src/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/.svn/entries/");
        paths.add("src/trunk/filesystem/src/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/.svn/format/");
        paths.add("src/trunk/filesystem/src/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/java/");
        paths.add("src/trunk/filesystem/src/java/.svn/");
        paths.add("src/trunk/filesystem/src/java/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/java/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/java/.svn/props/");
        paths.add("src/trunk/filesystem/src/java/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/java/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/java/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/java/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/java/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/java/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/java/.svn/entries/");
        paths.add("src/trunk/filesystem/src/java/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/java/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/java/.svn/format/");
        paths.add("src/trunk/filesystem/src/java/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/props/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/entries/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/format/");
        paths.add("src/trunk/filesystem/src/java/com/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/entries/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/format/");
        paths.add("src/trunk/filesystem/src/java/com/sun/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/entries/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/format/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/HeadHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/DeleteHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/XMLEncoder.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/URLencoder.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/MkcolHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/MoveHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/XMLProperty.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/PutHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/OptionHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/Constants.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/XMLCallback.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/GetHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/PropfindHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/MainHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/WebdavServiceAPI.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/SpecificHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/HCDAV.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/FileProperties.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/text-base/XMLWriter.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/HeadHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/DeleteHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/XMLEncoder.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/URLencoder.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/MkcolHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/MoveHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/XMLProperty.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/PutHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/OptionHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/Constants.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/XMLCallback.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/GetHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/PropfindHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/MainHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/WebdavServiceAPI.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/SpecificHandler.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/HCDAV.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/FileProperties.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/prop-base/XMLWriter.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/HeadHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/DeleteHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/XMLEncoder.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/URLencoder.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/MkcolHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/MoveHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/XMLProperty.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/PutHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/OptionHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/Constants.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/XMLCallback.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/GetHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/PropfindHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/MainHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/WebdavServiceAPI.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/SpecificHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/HCDAV.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/FileProperties.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/props/XMLWriter.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/HeadHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/DeleteHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/XMLEncoder.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/URLencoder.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/MkcolHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/MoveHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/XMLProperty.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/PutHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/OptionHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/Constants.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/XMLCallback.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/GetHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/PropfindHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/MainHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/WebdavServiceAPI.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/SpecificHandler.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/HCDAV.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/FileProperties.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/wcprops/XMLWriter.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/entries/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/format/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/PropfindHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/HeadHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/DeleteHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/XMLEncoder.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/URLencoder.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/MkcolHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/MoveHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/XMLProperty.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/PutHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/OptionHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/Constants.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/XMLCallback.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/GetHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/WebdavServiceAPI.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/MainHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/XMLWriter.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/HCDAV.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/SpecificHandler.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/FileProperties.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/MainHandler.java~/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/FileProperties.java~/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/GetHandler.java~/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/webdav/PropfindHandler.java~/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/CursorCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/ArchiveReader.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/SimpleFSCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/LibArchive.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/FSCacheException.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/ObjectRetrieve.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/MDCallback.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/EMDGlue.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/BDBNativeFileCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/LockManager.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/FSCacheObject.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/DbcNativeCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/HCXArchive.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/HCFile.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/FSCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/text-base/JavaFSCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/CursorCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/ArchiveReader.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/SimpleFSCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/LibArchive.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/FSCacheException.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/ObjectRetrieve.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/MDCallback.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/EMDGlue.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/BDBNativeFileCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/LockManager.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/FSCacheObject.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/DbcNativeCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/HCXArchive.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/HCFile.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/FSCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/prop-base/JavaFSCache.java.svn-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/CursorCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/ArchiveReader.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/SimpleFSCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/LibArchive.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/FSCacheException.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/ObjectRetrieve.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/MDCallback.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/EMDGlue.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/BDBNativeFileCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/LockManager.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/FSCacheObject.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/DbcNativeCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/HCXArchive.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/HCFile.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/FSCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/props/JavaFSCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/CursorCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/ArchiveReader.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/SimpleFSCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/LibArchive.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/FSCacheException.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/ObjectRetrieve.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/MDCallback.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/EMDGlue.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/BDBNativeFileCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/LockManager.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/FSCacheObject.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/DbcNativeCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/HCXArchive.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/HCFile.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/FSCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/wcprops/JavaFSCache.java.svn-work/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/entries/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/format/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/FSCacheException.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/CursorCache.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/ArchiveReader.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/SimpleFSCache.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/LibArchive.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/BDBNativeFileCache.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/ObjectRetrieve.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/MDCallback.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/EMDGlue.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/FSCacheObject.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/LockManager.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/FSCacheObject.java~/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/DbcNativeCache.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/HCXArchive.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/HCFile.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/FSCache.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/JavaFSCache.java/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/MDCallback.java~/");
        paths.add("src/trunk/filesystem/src/java/com/sun/honeycomb/fscache/JavaFSCache.java~/");
        paths.add("src/trunk/filesystem/src/pkg/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/props/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/props/depend.svn-work/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/entries/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/format/");
        paths.add("src/trunk/filesystem/src/pkg/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/pkg/preremove/");
        paths.add("src/trunk/filesystem/src/pkg/depend/");
        paths.add("src/trunk/filesystem/src/pkg/prototype/");
        paths.add("src/trunk/filesystem/src/pkg/postinstall/");
        paths.add("src/trunk/filesystem/src/pkg/pkginfo/");
        paths.add("src/trunk/filesystem/src/native/");
        paths.add("src/trunk/filesystem/src/native/.svn/");
        paths.add("src/trunk/filesystem/src/native/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/native/.svn/prop-base/");
        paths.add("src/trunk/filesystem/src/native/.svn/props/");
        paths.add("src/trunk/filesystem/src/native/.svn/wcprops/");
        paths.add("src/trunk/filesystem/src/native/.svn/tmp/");
        paths.add("src/trunk/filesystem/src/native/.svn/tmp/text-base/");
        paths.add("src/trunk/filesystem/src/native/.svn/tmp/prop-base/");
        paths.add("src/trunk/filesystem/src/native/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/native/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/native/.svn/entries/");
        paths.add("src/trunk/filesystem/src/native/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/native/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/native/.svn/format/");
        paths.add("src/trunk/filesystem/src/native/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/native/archive/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/entries/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/format/");
        paths.add("src/trunk/filesystem/src/native/archive/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/native/archive/Makefile/");
        paths.add("src/trunk/filesystem/src/native/archive/hctar.c/");
        paths.add("src/trunk/filesystem/src/native/fscache/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/text-base/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/tmp/props/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/tmp/wcprops/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/entries/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/empty-file/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/README.txt/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/format/");
        paths.add("src/trunk/filesystem/src/native/fscache/.svn/dir-wcprops/");
        paths.add("src/trunk/filesystem/src/native/fscache/Makefile/");
        paths.add("src/trunk/filesystem/src/native/fscache/trace.c/");
        paths.add("src/trunk/filesystem/src/native/fscache/trace.h/");
        paths.add("src/trunk/filesystem/src/native/fscache/fscache_priv.h/");
        paths.add("src/trunk/filesystem/src/native/fscache/db.h/");
        paths.add("src/trunk/filesystem/src/native/fscache/fscache.c/");
        paths.add("src/trunk/filesystem/build.properties/");
        paths.add("src/trunk/filesystem/build.xml/");
        paths.add("bin/");
        paths.add("bin/svndiff/");
        paths.add("bin/runtest/");
        paths.add("bin/hcinstall/");
        paths.add("bin/hcpush/");
        paths.add("bin/hcbuild/");
        paths.add("bin/loadtest/");
        paths.add("bin/svn-keywords/");
        paths.add("bin/.#hcservers/");
        paths.add("bin/hcbuild~/");
        paths.add("bin/hcinstall~/");
        paths.add("bin/catprop/");
        paths.add("bin/gentemp/");
        paths.add("bin/catprop~/");
        paths.add("bin/hcpush~/");
        paths.add("bin/svndiff~/");
        paths.add("bin/wdiff/");
        paths.add("bin/wdiff~/");
        paths.add("bin/mdget/");
        paths.add("bin/mdget~/");
        paths.add("bin/hcbuild2/");
        paths.add("bin/hcservers/");
        paths.add("bin/hcpush2/");
        paths.add("bin/minipush/");
        paths.add("bin/#hcservers#/");
        paths.add("bin/minipush~/");
        paths.add("bin/store/");
        paths.add("bin/store~/");
        paths.add("Mail/");
        paths.add("Mail/context/");

        Set dirs = new HashSet();

        for (Iterator i = paths.iterator(); i.hasNext(); ) {
            String path = (String) i.next();
            if (!hasAncestorInDirs(dirs, path))
                dirs.add(path);
        }

        String msg = "Top-level dirs:";
        for (Iterator i = dirs.iterator(); i.hasNext(); )
            msg += " \"" + i.next().toString() + "\"";
        System.out.println(msg);
    }
}
