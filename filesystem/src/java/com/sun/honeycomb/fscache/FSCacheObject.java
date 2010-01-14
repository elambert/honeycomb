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
import com.sun.honeycomb.fscache.FSCacheException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;

/** Objects stored in FSCache */
public abstract class FSCacheObject implements Comparable {
    private static final Logger logger =
        Logger.getLogger(FSCacheObject.class.getName());

    public static final byte ROOTFILETYPE = 1;
    public static final byte DIRECTORYTYPE = 2;
    public static final byte FILELEAFTYPE = 3;
    public static final byte ARCHIVEDIRTYPE = 4;
    public static final byte ARCHIVEFILETYPE = 5;

    // An object to be stored in the FS cache (like HCFile) needs to
    // implement this method, which FsCache will use when it decides
    // to fill out directories. The implementation will call
    // FsCache.addEntry() as reqd. and the "group" object should be
    // passed back in. Don't forget to set isComplete = true after
    // all the children have been read in.
    public abstract int addChildren(Object group) throws FSCacheException;

    // Since an object's OID is not fixed by the FsCache interface,
    // an FsCacheObject will have to implement these to read in and
    // write out its identifier.
    public abstract void readInOID(DataInputStream input) throws IOException;
    public abstract void writeOutOID(DataOutputStream os) throws IOException;
    public abstract byte[] getOidBytesExternal();

    // Factory constructor that FSCache calls to populate the cache
    public abstract FSCacheObject newObject();

    ///////////////////////////////////////////////////////////////////

    private byte        fileType;
    private String      fileName;
    private String      shortName;
    private boolean     isComplete;
    private int         index;
    private long        atime;
    private long        mtime;
    private long        crtime;
    private long        size;
    private long        uid;
    private long        gid;
    private long        mode;
    private String      mimeType;
    private List        children;
    private FSCacheObject parent;
    private long        lastUpdate;
    private boolean     isOld;

    // If this is a file object and its MIME type is in the view's
    // "archive auto-open" list
    private boolean     isArchiveObject;
    // For objects extracted from inside archives, the path of the
    // archive's root...
    private String      archiveObjectPath;
    // ... and the relative path from the archive root
    private String      pathInArchive;

    public FSCacheObject() {
        this(ROOTFILETYPE, null, 0, 0L, 0L, 0L, 99, 99, 0444, 0,
             "application/octet-stream", false);
    }

    /** Used to create "deep archive" objects */
    public FSCacheObject(byte fileType, String archiveName, String archivePath,
                         long atime, long mtime, long crtime,
                         long uid, long gid, long mode, long size) {
        this(fileType, null, -1, atime, mtime, crtime,
             uid, gid, mode, size, null, false);

        isArchiveObject = true;
        archiveObjectPath = archiveName;
        pathInArchive = archivePath;
        // And the full path of this file (in a deep archive) is
        fileName = archiveObjectPath + "/" + pathInArchive;
        shortName = basename(fileName);
    }

    public FSCacheObject(byte fileType, String fileName, int index,
                         long atime, long mtime, long crtime,
                         long uid, long gid, long mode,
                         long size, String mimeType, boolean isComplete) {
        if (mtime < crtime)
            mtime = crtime;
        if (atime < crtime)
            atime = crtime;

        this.fileType = fileType;
        this.fileName = fileName;
        this.isComplete = isComplete;
        this.index = index;
        this.crtime = crtime;
        this.atime = atime;
        this.mtime = mtime;
        this.size = size;
        this.uid = uid;
        this.gid = gid;
        this.mode = mode;
        this.mimeType = mimeType;
        this.children = new LinkedList();
        this.shortName = basename(fileName);
        this.isArchiveObject = false;
        this.archiveObjectPath = null;
        this.isOld = false;
        this.parent = null;
    }

    public void setParent(FSCacheObject parent) {
        this.parent = parent;
    }
    public void setFileType(byte fileType) {
        this.fileType = fileType;
    }
    protected void setFileName(String fileName) {
        this.fileName = fileName;
        this.shortName = basename(fileName);
    }
    public void setDisplayName(String name) {
        this.shortName = name;
    }
    public void setIndex(int index) {
        this.index = index;
    }
    public void setMtime(long mtime) {
        if (mtime < crtime)
            this.mtime = crtime;
        else
            this.mtime = mtime;
    }
    public void setCrtime(long crtime) {
        this.crtime = crtime;
    }
    public void setSize(long size) {
        this.size = size;
    }
    public void setUid(long uid) {
        this.uid = uid;
    }
    public void setGid(long gid) {
        this.gid = gid;
    }
    public void setMode(long mode) {
        this.mode = mode;
    }
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    public void setIsArchiveObject(boolean isArchiveObject) {
        this.isArchiveObject = isArchiveObject;
    }
    public void setArchiveObjectPath(String archiveObjectPath,
                                     String pathInArchive) {
        this.isArchiveObject = true;
        this.archiveObjectPath = archiveObjectPath;
        this.pathInArchive = pathInArchive;
        this.fileName = archiveObjectPath;
        if (pathInArchive != null)
            this.fileName += "/" + pathInArchive;
    }

    public synchronized void setAtime(long t) {
        if (t > atime)
            atime = t;
    }

    public String fileName() { return fileName; }
    public String displayName() { return shortName; }
    public byte fileType() { return fileType; }
    public boolean isArchiveObject() { return isArchiveObject; }
    public int  index() { return index; }
    public long mtime() { return mtime; }
    public long atime() { return atime; }
    public long crtime() { return crtime; }
    public long size() { return size; }
    public long uid() { return uid; }
    public long gid() { return gid; }
    public long mode() { return mode; }
    public String mimeType() { return mimeType; }
    public List children() { return children; }
    public FSCacheObject parent() { return parent; }
    public int numChildren() { return children.size(); }
    public String archiveObjectPath() { return archiveObjectPath; }
    public String pathInArchive() { return pathInArchive; }
    public boolean isComplete() { return isComplete; }

    public boolean isOld() {
        if (!isOld) {
            long age = System.currentTimeMillis() - lastUpdate;
            isOld = age > FSCache.getCoherencyTime();
        }

        return isOld;
    }

    public void setComplete(boolean isComplete) {
        this.isComplete = isComplete;
        if (isComplete)
            this.lastUpdate = System.currentTimeMillis();
    }

    public boolean isFile() {
        return fileType == ARCHIVEFILETYPE ||
            (fileType == FILELEAFTYPE && !isArchiveObject);
    }

    public boolean addChild(FSCacheObject child) {
        synchronized (children) {
            if (children.contains(child))
                return true;        // no error
            children.add(child);
            return children.contains(child);
        }
    }
    public boolean removeChild(FSCacheObject child) {
        synchronized (children) {
            if (!children.contains(child))
                return true;        // no error
            isComplete = false;
            children.remove(child);
            return !children.contains(child);
        }
    }
    public boolean inChildren(FSCacheObject obj) {
        synchronized (children) {
            return children.contains(obj);
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append('[').append(fileName()).append(' ');
        sb.append('(').append(displayName()).append(')').append(' ');
        switch (fileType()) {
        case ROOTFILETYPE: sb.append('/').append(numChildren()); break;
        case DIRECTORYTYPE: sb.append('d').append(numChildren()); break;
        case FILELEAFTYPE: sb.append('f'); break;
        case ARCHIVEDIRTYPE: sb.append('A').append(numChildren()); break;
        case ARCHIVEFILETYPE: sb.append('a'); break;
        default: sb.append(fileType()); break;
        }
        sb.append(" 0").append(Long.toOctalString(mode()));
        sb.append(' ').append('i').append(index());
        sb.append(' ').append(size()).append(' ').append(uid()).append(':');
        sb.append(gid()).append(' ').append(mimeType()).append(' ');
        sb.append('@').append(crtime).append('/').append(mtime).append(' ');

        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            writeOutOID(new DataOutputStream(stream));
            sb.append(ByteArrays.toHexString(stream.toByteArray())).append(' ');
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        sb.append(isArchiveObject()?'A':'-').append(' ');
        if (archiveObjectPath() != null)
            sb.append(':').append(archiveObjectPath());

        return sb.append(" ]").toString();
    }

    public String parentName() {
        if (fileType() == ROOTFILETYPE)
            return fileName();
        return parent.fileName();
    }

    public int compareTo(Object o) {
        if (!(o instanceof FSCacheObject))
            throw new RuntimeException("Not an FSCacheObject!");
        FSCacheObject other = (FSCacheObject) o;

        int diff = (int)(atime() - other.atime());

        if (diff < 0)
            return -1;
        if (diff > 0)
            return 1;

        // If the access times are equal, compare names. We negate
        // here because we want /foo/bar to sort before /foo, which
        // means /foo/bar will be spilled from the cache before /foo.

        return -fileName().compareTo(other.fileName());
    }

    public boolean equals(Object o) {
        if (!(o instanceof FSCacheObject))
            return false;
        FSCacheObject obj = (FSCacheObject) o;
        return fileName().equals(obj.fileName());
    }

    public int hashCode() {
        return fileName().hashCode();
    }

    ////////////////////////////////////////////////////////////////////
    //
    // Reading/writing objects to/from streams
    //
    ////////////////////////////////////////////////////////////////////

    // When reading/writing objects from/to a DataStream, these are
    // the various offsets and lengths -- keep this consistent with
    // readIn() and writeIn()! (It's used by the JNI cache implementation.)

    public static final int ATIME_OFFSET = 8;
    public static final int ATIME_LEN = 8;
    public static final int HC_INDEX_OFFSET = 16;
    public static final int HC_INDEX_LEN = 4;
    public static final int FTYPE_OFFSET = 32;
    public static final int OID_OFFSET = 34;
    public static final int OID_LEN = 30;
    public static final int NAMELEN_OFFSET = 64;
    public static final int NAME_OFFSET = 66;

    public void readIn(DataInputStream input) throws IOException {
        // Note: this order is used in fscache.c
        mtime = input.readLong();
        atime = input.readLong();
        index = input.readInt();
        lastUpdate = input.readLong();
        fileType = input.readByte();
        isComplete = (input.readByte() != 0);

        readInOID(input);

        short filenameLength = input.readShort();
        if (filenameLength < 0)
            fileName = null;
        else {
            byte[] filenameBytes = new byte[filenameLength];
            input.read(filenameBytes);
            fileName = new String(filenameBytes);
        }

        size = input.readLong();
        crtime = input.readLong();
        uid = input.readLong();
        gid = input.readLong();
        mode = input.readLong();

        short mimetypeLength = input.readShort();
        if (mimetypeLength < 0)
            mimeType = null;
        else {
            byte[] mimetypeBytes = new byte[mimetypeLength];
            input.read(mimetypeBytes);
            mimeType = new String(mimetypeBytes);
        }

        isArchiveObject = (input.readByte() != 0);

        short archpathLength = input.readShort();
        if (archpathLength < 0)
            archiveObjectPath = null;
        else {
            byte[] archpathBytes = new byte[archpathLength];
            input.read(archpathBytes);
            archiveObjectPath = new String(archpathBytes);
        }
    }

    public void writeOut(DataOutputStream os) throws IOException {
        os.writeLong(mtime());
        os.writeLong(atime());
        os.writeInt(index());
        os.writeLong(lastUpdate);
        os.writeByte(fileType());
        os.writeByte(isComplete? 1 : 0);

        writeOutOID(os);

        if (fileName() == null)
            os.writeShort(-1);
        else {
            byte[] nameArray;
            try {
                nameArray = fileName().getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            os.writeShort((short)nameArray.length);
            os.write(nameArray);
        }

        os.writeLong(size());
        os.writeLong(crtime());
        os.writeLong(uid());
        os.writeLong(gid());
        os.writeLong(mode());

        if (mimeType() == null)
            os.writeShort(-1);
        else {
            byte[] mimeArray;
            try {
                mimeArray = mimeType().getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            os.writeShort((short)mimeArray.length);
            os.write(mimeArray);
        }

        os.writeByte(isArchiveObject()? 1 : 0);
        
        if (archiveObjectPath() == null)
            os.writeShort(-1);
        else {
            byte[] arArray;
            try {
                arArray = archiveObjectPath().getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            os.writeShort((short)arArray.length);
            os.write(arArray);
        }
    }

    /* Don't consider \/ to be a path separator */
    static private String basename(String s) {
        if (s == null || s.equals("/"))
            return s;

        int pos = s.length();
        int nextPos;

        while ((nextPos = s.lastIndexOf('/', pos-1)) >= 0) {
            if (nextPos == 0 || s.charAt(nextPos-1) != '\\')
                return s.substring(nextPos + 1);
            pos = nextPos;
        }

        return s;
    }
}
