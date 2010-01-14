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



package com.sun.honeycomb.archivers;

import java.io.OutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the Java interface for libarchive.
 *
 * The intended usage is: you open the archive and repeat {
 *     read the next header
 *     read the file's data
 * }. If you're only interested in the headers, you don't have to
 * fetch the file's data. Used this way the archive will be opened
 * exactly once and read sequentially.
 *
 * If asked for a specific file, we have to compare its position with
 * the current position. Since we don't know how to tell libarchive to
 * rewind, we have no choice but to close and re-open to start at the
 * top. If the archive is being read from a stream, an exception will
 * be thrown.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
class LibArchive implements HCXArchive {

    private static final Logger logger =
        Logger.getLogger(LibArchive.class.getName());

    private static final int BUFFER_SIZE = 4096;

    private static final int ARCHIVE_NONE = 0;
    private static final int ARCHIVE_BY_NAME = 1;
    private static final int ARCHIVE_BY_FD = 2;
    private static final int ARCHIVE_BY_STREAM = 3;

    private int archiveType = ARCHIVE_NONE;
    private String filename = null;
    private FileDescriptor fd = null;
    private RandomAccessFile file = null;

    private long handle = 0;
    private boolean isCompressed = false;
    private long currentIndex;
    private long currentOffset;
    private FileChunk prevChunk = null;

    private long cookie = 0L;
    private boolean started = false;

    LibArchive() {

    }

    protected void finalize() {
        if (handle != 0)
            closeArchive(handle);
    }

    public OutputStream open() throws IOException {
        if (archiveType != ARCHIVE_NONE) {
            String msg = "Archive of type " + archiveType;
            msg += " already open; can't now open by stream";
            throw new RuntimeException(msg);
        }
        archiveType = ARCHIVE_BY_STREAM;

        return getStream();     // Non-blocking
    }

    public void open(FileDescriptor fd) throws IOException {
        if (archiveType != ARCHIVE_NONE) {
            String msg = "Archive of type " + archiveType;
            msg += " already open; can't now open by fd";
            throw new RuntimeException(msg);
        }
        archiveType = ARCHIVE_BY_FD;

        this.fd = fd;
        this.filename = null;
        openAr();
    }

    public void open(String f) throws IOException {
        if (archiveType != ARCHIVE_NONE) {
            String msg = "Archive of type " + archiveType;
            msg += " already open; can't now open by name";
            throw new RuntimeException(msg);
        }
        archiveType = ARCHIVE_BY_NAME;

        this.filename = f;
        this.fd = null;
        openAr();
    }

    public void close() throws IOException {
        if (handle == 0)
            return;

        closeArchive(handle);

        filename = null;
        handle = 0;
        currentIndex = 0;
    }

    public boolean isCompressed() {
        init();
        return isCompressed; 
    }

    public HCXArchive.Stat nextHeader() throws IOException {
        if (handle == 0)
            throw new RuntimeException("Archive not open");

        init();

        HCXArchive.Stat stat = nextHeader(handle);

        if (stat != null) {
            currentIndex = stat.index();
            currentOffset = 0L;
            prevChunk = null;
        }
        else 
            close();

        return stat;
    }

    public byte[] getContents(long fileIndex,
                              long offsetBytes, int lengthBytes)
        throws IOException {

        init();

        if (handle == 0)
            openAr();

        // Now we have to go through the archive until we can find the
        // file we need. We'd like to be able to seek in the file to
        // the right place (since we've already constructed the table
        // of contents, we know where it is), but alas! if the archive
        // is compressed, this becomes non-trivial, and libarchive
        // doesn't support anything like seek().

        if (currentIndex > fileIndex) {
            close();
            openAr();
        }

        while (currentIndex < fileIndex)
            nextHeader();

        if (currentIndex == fileIndex)
            // Yay!
            return read(offsetBytes, lengthBytes);

        return null;
    }

    ////////////////////////////////////////////////////////////////////////

    /** This is the class that the file read JNI method builds and returns */
    class FileChunk {
        private byte[] data = null;
        private long offset = 0;
        FileChunk(byte[] data) {
            this(data, 0);
        }
        FileChunk(byte[] data, long offset) {
            this.data = data;
            this.offset = offset;
        }
        byte[] data() { return data; }
        int size() { return data.length; }
        long offset() { return offset; }
        public String toString() {
            if (data == null)
                return "+" + offset + "[]";
            return "+" + offset + "[" + data.length + "]";
        }
    }

    ////////////////////////////////////////////////////////////////////////

    private void openAr() throws IOException {
        switch (archiveType) {

        case ARCHIVE_BY_NAME:
            file = new RandomAccessFile(filename, "r");
            if ((handle = openArchive(file.getFD())) <= 0)
                throw new IOException("Can't open \"" + filename + "\"");
            break;

        case ARCHIVE_BY_FD:
            if ((handle = openArchive(fd)) <= 0)
                throw new IOException("Can't open file descriptor " + fd);
            break;

        default:
            throw new RuntimeException("Bad value in switch");
        }

        isCompressed = compressed(handle);
        currentIndex = 0;
    }

    /** Creates and sets up the pipe */
    private OutputStream getStream() throws IOException {
        if (archiveType != ARCHIVE_BY_STREAM || handle != 0)
            return null;

        if ((handle = openArchive(null)) <= 0)
            throw new IOException("Can't open null archive");

        if (!getStream(handle, this))
            // sets this.fd and this.cookie
            throw new IOException("Couldn't create archive stream");

        if (fd == null)
            throw new IOException("Couldn't get a writeable fd");

        return new FileOutputStream(fd);
    }

    private synchronized void init() {
        // libarchive may now start reading, and may block if reqd.

        switch (archiveType) {
        case ARCHIVE_BY_STREAM:
            if (handle == 0)
                throw new RuntimeException("Not associated");

            if (started)
                return;

            startReading(handle, cookie);
            started = true;
            break;

        case ARCHIVE_BY_NAME:
            if (filename == null)
                throw new RuntimeException("Archive not open");
            break;

        case ARCHIVE_BY_FD:
            if (fd == null)
                throw new RuntimeException("Not open");
            break;
        }
    }

    private byte[] read(long offset, int length) throws IOException {
        if (offset < currentOffset) {
            // Ugh! We need to reset everything and start over XXX TODO:
            // (This is actually a "can never happen" since HCFile is not
            // supposed to do this.)
            close();
            throw new RuntimeException("please re-try!");
        }

        long toSkip = offset - currentOffset;
        while (toSkip > 0) {
            int toRead = BUFFER_SIZE;
            if (toSkip < BUFFER_SIZE)
                toRead = (int) toSkip;

            FileChunk chunk = readData(handle, toRead);
            if (chunk == null)
                // EOF
                return null;
            currentOffset += chunk.size();
            toSkip -= chunk.size();
        }

        ByteBuffer result = ByteBuffer.allocate((int)length);

        int remaining = length;
        while (remaining > 0) {
            FileChunk chunk = readData(handle, remaining);
            if (chunk == null)
                // EOF
                break;
            currentOffset += chunk.size();
            if (chunk.size() >= remaining) {
                result.put(chunk.data(), 0, remaining);
                break;
            }
            result.put(chunk.data());
            remaining -= chunk.size();
        }

        result.flip();
        byte[] retval = new byte[result.remaining()];
        result.get(retval);
        return retval;
    }

    /*
    ////////////////////////////////////////////////////////////////////////
   
    private byte[] getChunk(long offset, int length) throws IOException {
        // The file is at position currentOffset. Repeatedly get file
        // chunks until we reach position "offset", then read upto
        // "len" bytes.

        // WARNING: file chunks will not be contiguous if there are
        // holes in the file.

        ByteBuffer result = ByteBuffer.allocate((int)length);

        if (offset < currentOffset) {
            // Ugh! We need to reset everything and start over XXX TODO:
            close();
            throw new RuntimeException("please re-try!");
        }

        if (prevChunk == null)
            // First chunk
            prevChunk = nextChunk(handle);

        FileChunk chunk;
        int copied = 0;         // no. of bytes already copied to result
        int remaining = length; // no. of bytes yet to be copied
        long pos = offset;      // current position in file of interest

        for (chunk = prevChunk; chunk != null; chunk = nextChunk(handle)) {

            if (chunk.offset() + chunk.size() <= pos)
                // Data is not in this chunk
                continue;

            long skip = pos - chunk.offset();

            if (skip < 0) {
                // We're in a hole
                skip = -skip;
                if (skip > remaining)
                    skip = remaining;
                addNULs(result, skip);
                copied += skip;
                remaining -= skip;
                pos += skip;
                skip = 0;
            }
            if (remaining <= 0)
                break;

            int len = remaining;

            if (skip + len > chunk.size())
                len = chunk.size() - skip;

            result.put(chunk.data(), skip, len);
            remaining -= len;
            copied += len;
            pos += len;

            if (remaining <= 0)
                break;
        }
        prevChunk = chunk;

        result.flip();
        return result.array();
    }

    // FIXME -- need bzero() here
    private void addNULs(ByteBuffer b, long size) {
        for (int i = 0; i < size; i++)
            b.put((byte)0);
    }
    */

    ////////////////////////////////////////////////////////////////////////
    // Native stuff

    static {
        System.loadLibrary("hctar");
    }

    // The C code calls this to return the two ends of the pipe
    void setCookie(long cookie, FileDescriptor fd) {
        this.cookie = cookie;
        this.fd = fd;
    }

    private native long openArchive(FileDescriptor f);
    private native boolean getStream(long handle, Object parent);
    private native void startReading(long handle, long cookie);
    private native void closeArchive(long handle);

    private native boolean compressed(long handle);
    private native HCXArchive.Stat nextHeader(long handle);
    private native FileChunk nextChunk(long handle);
    private native FileChunk readData(long handle, int maxBytes);
    private native boolean skipData(long handle);

    ////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) {
        LibArchive t = new LibArchive();
        boolean useStream = false;

        if (args.length < 2) {
            System.err.println("Usage: LibArchive [-s] archive-name file-name");
            System.exit(1);
        }

        if (args[0].equals("-s")) {
            if (args.length < 3) {
                System.err.println("Usage: LibArchive [-s] archive-name file-name");
                System.exit(1);
            }
            useStream = true;
            args[0] = args[1];
            args[1] = args[2];
        }
        final String arch_name = args[0];
        final String file_name = args[1];

        Thread w = null;
        try {
            if (useStream) {
                // In a different thread, write the file into the stream

                final OutputStream os = t.open();

                w = new Thread(new Runnable() {
                        public void run() {
                            int nbytes = 0;

                            System.err.print("Writer...");
                            System.err.flush();

                            // Read file and write it into the output stream
                            FileInputStream fis = null;
                            try { fis = new FileInputStream(arch_name); }
                            catch (Exception e) {}
                            if (fis == null) {
                                System.err.println("Couldn't open " +
                                                   arch_name);
                                System.exit(1);
                            }

                            byte[] buf = new byte[1000];
                            int nread;

                            try {
                                while ((nread = fis.read(buf)) > 0) {
                                    nbytes += nread;
                                    os.write(buf, 0, nread);
                                }
                            }
                            catch (Exception ex) {
                                System.err.println("AAAaa!");
                                ex.printStackTrace();
                            }
                            finally {
                                try {os.close();} catch (Exception e) {}
                            } 

                            System.err.println(" done (" + nbytes +
                                               " bytes written)");
                            System.err.flush();
                        }
                    });
                w.start();
            }
            else
                t.open(arch_name);

            System.out.print("Archive \"" + arch_name + "\":\n");
            printFile(t, file_name);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                t.close();
                if (w != null)
                    w.join();
            } catch (Exception ignored) {}
        }
    }

    private static void printFile(HCXArchive t, String file_name)
            throws IOException {
        long offset = -1;
        int size = 0, fileno = 0;
        HCXArchive.Stat hdr = null;

        while ((hdr = t.nextHeader()) != null) {
            fileno++;
            System.out.println("    " + fileno + ": " + hdr);

            if (hdr.name().equals(file_name)) {
                offset = hdr.index();
                size = (int) hdr.size();
                for (int i = 0; i < size; ) {
                    byte[] data = t.getContents(offset, i, 20);
                    if (data == null)
                        break;
                    String s = new String(data);
                    i += s.length();
                    System.out.print(s);
                }
            }
        }

    }


}
