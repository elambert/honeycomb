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



package com.sun.honeycomb.oa.upgrade;

import com.sun.honeycomb.util.posix.StatFS;
import com.sun.honeycomb.common.UID;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.Type;

import java.io.IOException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

public class UpgradeableList implements Iterator {
    private static Logger log
        = Logger.getLogger(UpgradeableList.class.getName());
    private final String tempFilePrefix;
    private OutboardList outboardList;
    private Type type;

    public UpgradeableList(Disk disk) {
	this(disk, DiskUpgrader.DATA);
    }
    public UpgradeableList(Disk disk, Type type) {
	this.type = type;
        File dir = new File(disk.getPath() + File.separator
                            + DiskUpgrader.WORK_DIRECTORY);
	tempFilePrefix = "UpgradeableList_" + type.name + "_";
        outboardList
            = new OutboardList(tempFilePrefix, dir);
    }
    public Type getType() {
	return type;
    }
    public synchronized Iterator iterator() {
        return outboardList.iterator();
    }
    public synchronized boolean hasNext() {
        return outboardList.hasNext();
    }
    public synchronized Object next() {
        return outboardList.next();
    }
    public synchronized void add(List sortedList) throws UpgraderException {
        try {
            for (Iterator it = sortedList.iterator(); it.hasNext(); ) {
                FileInfo fi = (FileInfo) it.next();
                outboardList.put(fi);
            }
            outboardList.flush();
        } catch (IOException e) {
            throw new UpgraderException(e);
        }
    }
    public int count() {
        return outboardList.count();
    }
    public void remove() {
        outboardList.remove();
    }

    /**********************************************************************/
    public static class OutboardList implements Iterator {
        private static int BACKING_LIMIT = 1000;
        private RandomAccessFile raf;
        private ByteBuffer buffer;
        private int width;
        private int count;
        private List backingList = new LinkedList();
        private Iterator iterator;
        private int position;
        private File tempFile;
	private File sourceDir;

        public OutboardList(String name, File dir) {
	    sourceDir = dir;
	    final String filePrefix = name;
            String filename
                = new UpgraderNewObjectIdentifier(new UID(), 1,
                                                  (byte) 1, 0).toString();
            width = new FileInfo(filename, new Long(0)).getBytes().limit();
            buffer = ByteBuffer.allocate(BACKING_LIMIT * width);
            // Delete any temp files left behind from previous upgrade.
            String[] tempFiles = sourceDir.list(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return (name.startsWith(filePrefix)) ? true : false;
                    }
                });
            if (tempFiles != null) {
                for (int i = 0; i < tempFiles.length; i++) {
                    new File(sourceDir, tempFiles[i]).delete();
                }
            }
            // Create a temp file.
            try {
                tempFile = File.createTempFile(name, ".swap", dir);
                tempFile.deleteOnExit();
                raf = new RandomAccessFile(tempFile, "rw");
            } catch (FileNotFoundException fnfe) {
                throw new IllegalStateException(fnfe.getMessage());
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe.getMessage());
            }
        }
        public void clear() {
            reset();
            count = 0;
        }
        private void reset() {
            try {
                raf.seek(0);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage());
            }
            backingList.clear();
            iterator = backingList.iterator();
            position = 0;
            buffer.clear();
        }
        public Iterator iterator() {
            reset();
            return this;
        }
        public boolean hasNext() {
            if (iterator.hasNext()) {
                return true;
            }
            if (position < count) {
                refill(position, BACKING_LIMIT);
                position += backingList.size();
                iterator = backingList.iterator();
                return iterator.hasNext();
            }
            return false;
        }
        public Object next() {
            return iterator.next();
        }
        public void remove() {
	    delete();
        }
	private void delete() {
	    try {
		raf.close();
	    } catch (IOException ignored) {
	    }
	    tempFile.delete();
	}
        public void put(FileInfo fi) throws IOException {
            buffer.put(fi.getBytes());
            count++;
            if (!buffer.hasRemaining()) {
                flush();
            }
        }
        public void flush() throws IOException {
            buffer.flip();
            raf.getChannel().write(buffer);
            buffer.clear();
        }
        private void refill(int position, int size) {
            if (position >= count) {
                backingList.clear();
                return;
            }
            try {
                raf.seek(position * width);
                int limit = Math.min(size, (count - position));
                backingList.clear();
                buffer.clear();
                raf.getChannel().read(buffer);
                buffer.flip();
                for (int i = 0; i < limit; i++) {
                    backingList.add(new FileInfo(buffer));
                }
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }
        public int count() {
            return count;
        }
    }

    /**********************************************************************/
    public static class FileInfo implements Comparable {
        private final String file;
        private Long inode;
        public FileInfo(String file, String dir) {
            this.file = file;
            String path = dir + "/" + file;
            inode = new Long(StatFS.getInodeNumber(path));
            if (inode.longValue() == -1) {
                throw new IllegalStateException("Cannot get inode number for "
                                                + path);
            }
        }
        public FileInfo(ByteBuffer buffer) {
            byte[] bytes = new byte[52];
            buffer.get(bytes);
            file = new String(bytes).trim();
            inode = new Long(buffer.getLong());
        }
        public FileInfo(String file, Long inode) {
            this.file = file;
            this.inode = inode;
        }
        public String getFile() {
            return file;
        }
        public boolean equals(Object o) {
            return (inode.compareTo(((FileInfo) o).inode) == 0);
        }
        public int compareTo(Object o) {
            return inode.compareTo(((FileInfo) o).inode);
        }
        public ByteBuffer getBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(60);
            buffer.put(String.format("%1$52s", file).getBytes());
            buffer.putLong(inode.longValue());
            buffer.flip();
            return buffer;
        }
    }
}
