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

import com.sun.honeycomb.common.ProtocolConstants;

import java.io.OutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * To read an HCXArchive and extract a file, construct the archive
 * table of contents etc.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class ArchiveReader {

    private static final Logger logger =
        Logger.getLogger(ArchiveReader.class.getName());

    private static final int BUFFER_SIZE = 4096;

    private HCXArchive archive = null;
    private HCXArchive.Stat currentHeader = null;
    private List toc = null;

    private Object setLock = null;
    private Set dirSet = null;
    private Set synthetics = null;

    private OutputStream os = null;

    public ArchiveReader() {
        archive = new LibArchive();
        toc = new LinkedList();
        dirSet = new HashSet();
        synthetics = new HashSet();
        setLock = new Object();
    }

    /** Create the writeable stream that the archive will be written to */
    public OutputStream getStream() throws IOException {
        return archive.open();
    }

    public void close() throws IOException {
        if (archive != null)
            archive.close();
        archive = null;
    }

    public void setOutput(OutputStream os) { this.os = os; }

    /** Skip ahead in the archive stream to the next object */
    public HCXArchive.Stat next() {
        try {
            return nextNoSynthetics();
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't skip in archive", e);
        }
        return null;
    }

    /**
     * Skip ahead in the archive stream to the next object, including
     * implicit directories
     */
    public HCXArchive.Stat nextWithFill() {
        try {
            return nextWithSynthetics();
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't skip in archive", e);
        }
        return null;
    }

    /** Skip ahead in the archive stream to a specific object */
    public HCXArchive.Stat skipTo(String path) {
        try {
            return doSkip(path);
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't skip in archive", e);
        }
        return null;
    }

    /** Is the current object a directory? */
    public boolean isDirectory() {
        if (currentHeader == null)
            // We start with current file == "/"
            return true;

        int m = (int) currentHeader.mode();
        return (m & HCXArchive.MODEMASK_TYPEM) == HCXArchive.MODEMASK_IFDIR;
    }

    /** Write out [a chunk of] the current object */
    public boolean write(long offset, long length) {
        try {
            doWrite(offset, length);
            return true;
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't write requested object", e);
        }
        return false;
    }

    /**
     * Get table of contents of the archive. Since an archive can only
     * be read sequentially, no data operations are possible after
     * this method has been called.
     */
    String[] getContents() {
        try {
            while (archive != null) {
                if ((currentHeader = archive.nextHeader()) == null)
                    break;
                toc.add(currentHeader.name());
            }
            archive = null;
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Error trying to get contents", e);
        }

        int j = 0;
        String[] ret = new String[toc.size()];
        for (Iterator i = toc.iterator(); i.hasNext(); )
            ret[j++] = (String) i.next();
        return ret;
    }

    /*
     * It may be that the directory name itself is not in the archive,
     * but files contained in it are. Construct a synthetic directory
     * listing with the names of elements that would be in it had the
     * directory been present.
     */
    boolean writeSyntheticDirectory(String path) {
        try {
            writeSynthetic(path);
            return true;
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "writing synthetic directory ", e);
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //                           PRIVATE                                //
    //////////////////////////////////////////////////////////////////////

    /**
     * Maintain the list of directories, and at the end, emit any
     * missing directories.
     */
    private HCXArchive.Stat nextWithSynthetics() throws IOException {
        // The problem is that some archives don't include entries for
        // directories. So we maintain a list of directories we've
        // seen, and a list of implicit directories that exist because
        // we've seen their children. At the end of the archive
        // object, we return all those entries.
        HCXArchive.Stat stat = doNext();

        if (stat == null)
            // EOF; return synthetic directories
            stat = nextSynthetic();
        else {
            addParentDirname(stat.name());
            if ((stat.mode() & HCXArchive.MODEMASK_IFDIR) != 0)
                addDirname(stat.name());
        }

        return stat;
    }

    private HCXArchive.Stat nextNoSynthetics() throws IOException {
        HCXArchive.Stat stat = doNext();

        if (stat != null) {
            addParentDirname(stat.name());
            if ((stat.mode() & HCXArchive.MODEMASK_IFDIR) != 0)
                addDirname(stat.name());
        }

        return stat;
    }

    // Only call this after EOF!
    private HCXArchive.Stat nextSynthetic() {
        if (archive != null)
            throw new RuntimeException("Can't get synthetics till after EOF");

        synchronized (setLock) {
            if (dirSet.size() > 0) {
                for (Iterator i = dirSet.iterator(); i.hasNext(); ) {
                    String name = (String) i.next();
                    synthetics.remove(name);
                    i.remove();
                }
            }
            synthetics.remove(".");

            Iterator i = synthetics.iterator();
            if (!i.hasNext())
                return null;

            String dir = (String) i.next();
            i.remove();

            long now = System.currentTimeMillis();
            long mode = HCXArchive.MODEMASK_IFDIR | 0755;
            return new HCXArchive.Stat(dir, 0, -1, 0, 666, now, now, mode);
        }
    }

    private void addParentDirname(String path) {
        if (path == null || path.equals(".") || path.equals("/"))
            return;

        String pPath = parentOf(path);
        synchronized (setLock) {
            synthetics.add(pPath);
        }

        addParentDirname(pPath);
    }

    private void addDirname(String path) {
        synchronized (setLock) {
            dirSet.add(path);
        }
    }

    /////////////////////////////////////////////////////////////////

    private HCXArchive.Stat doNext() throws IOException {
        if (archive == null)
            // Already at EOF
            return null;

        if ((currentHeader = archive.nextHeader()) == null)
            archive = null;
        else {
            currentHeader.setName(canonicalize(currentHeader.name()));
            toc.add(currentHeader.name());
        }

        return currentHeader;
    }

    private HCXArchive.Stat doSkip(String path) throws IOException {
        // All paths are absolute, rooted at the archive root. This
        // means we have to correctly handle names in the archive of
        // the form "/foo/bar", "foo/bar", "./foo/bar" -- all are
        // equivalent and refer to the same file in the archive.

        if ((path = canonicalize(path)) == null)
            return null;

        while (nextWithSynthetics() != null) {
            if (currentHeader != null) {
                if (path.equals(currentHeader.name()))
                    return currentHeader;
                else
                    if (logger.isLoggable(Level.FINER))
                        logger.finer("skipping " + currentHeader);
            }
        }

        // Reached the end
        archive = null;
        return null;
    }

    private void doWrite(long offset, long length) throws IOException {
        if (archive == null)
            // Already at EOF
            throw new IOException("At EOF");

        if (isDirectory()) {
            emitDirHTMLStart();

            // Repeatedly get headers and collect the ones that are
            // children of this one
            String objName = canonicalize(currentHeader.name());
            emit(objName, ".", currentHeader);
            emit(parentOf(objName), "..", null);

            while ((currentHeader = archive.nextHeader()) != null) {
                String name = canonicalize(currentHeader.name());
                if (parentOf(name).equals(objName))
                    emit(name, basename(name), currentHeader);
            }

            emitDirHTMLEnd();
        }
        else {
            if (currentHeader == null)
                throw new RuntimeException("No current object");

            long remaining = length;
            long pos = offset;
            while (remaining > 0) {
                int toRead = BUFFER_SIZE;
                if (remaining < BUFFER_SIZE)
                    toRead = (int) remaining;
                byte[] buf =
                    archive.getContents(currentHeader.index(), pos, toRead);

                if (logger.isLoggable(Level.FINE))
                    logger.fine("Writing " + buf.length + " bytes @offset " +
                                pos + " to network");

                remaining -= buf.length;
                pos += buf.length;

                os.write(buf);
            }
        }
    }

    private void writeSynthetic(String path) throws IOException {
        String[] toc = getContents();
        emitDirHTMLStart(path);

        emit(path, ".", null);
        emit(parentOf(path), "..", null);

        for (int i = 0; i < toc.length; i++) {
            if (parentOf(toc[i]).equals(path))
                emit(toc[i], basename(toc[i]), null);
        }
        emitDirHTMLEnd();
    }

    private void write(String s) throws IOException {
        try {
            os.write(s.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    ////////////////////////////////////////////////////////////////
    // private static methods

    private static String canonicalize(String path) {
        if (path == null)
            return null;

        StringBuffer sb = new StringBuffer();

        // to canonical form "foo/bar/foobar" for path comparisons

        String s = "/" + path + "/";
        String[] names = s.split("/+");
        for (int i = 1; i < names.length; i++) {
            if (i < (names.length - 1)) {
                if (names[i+1].equals("..")) {
                    i++;
                    continue;
                }
            }
            if (names[i].equals(".")) {
                continue;
            }
            sb.append("/").append(names[i]);
        }

        String retval = sb.toString();
        if (retval.length() == 0)
            return ".";
        else
            return sb.toString().substring(1);
    }

    private static String parentOf(String path) {
        // Path is in canonical form.
        int pos = path.lastIndexOf('/');
        if (pos < 0)
            return ".";
        return path.substring(0, pos);
    }


    private static String basename(String name) {
        String ret = name;
        try {
            ret = name.substring(1 + name.lastIndexOf('/'));
        } catch (Exception e) {}
        return ret;
    }

    ////////////////////////////////////////////////////////////////
    // HTML

    private void emit(String hrefName, String displayName, HCXArchive.Stat hdr)
            throws IOException {
        write("<tr><td><a href=\"");
        write(ProtocolConstants.WEBDAV_PATH);
        write("/");
        write(hrefName);
        write("\">");
        write(displayName);
        write("</a></td><td>");

        if (hdr == null) 
            write("<em>&lt;Unknown&gt;</em>");
        else {
            write(" <tt>");
            write(hdr.toString());
            write("</tt> ");
        }

        write("</td></tr>\n");
    }

    private void emitDirHTMLStart() throws IOException {
        emitDirHTMLStart(currentHeader.name());
    }
    private void emitDirHTMLStart(String name) throws IOException {
        write("<html><head><title>");
        write(name);
        write("</title></head><body>\n");
        write("Directory \"");
        write(basename(name));
        write("\":<table>\n");
    }

    private void emitDirHTMLEnd() throws IOException {
        write("</table></body></html>\n");
    }

    ////////////////////////////////////////////////////////////////

    public static void main(String[] args) {

        String arch_name = args[0];
        String path = null;
        if (args.length > 1)
            path = canonicalize(args[1]);

        doit(arch_name, path);
    }

    private static void doit(final String arch_name, final String path) {
        ArchiveReader ar =  new ArchiveReader();
        try {
            // Start thread to write into t
            final OutputStream os = ar.getStream();

            Thread w = new Thread(new Runnable() {
                    public void run() {
                        int nbytes = 0;

                        System.err.print("Writer...");
                        System.err.flush();

                        // Read file and write it into the output stream
                        FileInputStream fis = null;
                        try { fis = new FileInputStream(arch_name); }
                        catch (Exception e) {}
                        if (fis == null) {
                            System.err.println("Couldn't open " + arch_name);
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

            ar.setOutput(System.out);

            HCXArchive.Stat stat;

            if (path == null) {
                // Print the whole archive
                System.out.print("");
                System.out.println("Entire archive:");
                while ((stat = ar.next()) != null)
                    System.out.println(stat.toString());
                return;
            }

            // skip forward in the archive to the requested file
            if ((stat = ar.skipTo(path)) != null) {
                // It's in the archive

                if (ar.isDirectory())
                    System.err.println("directory");

                if (!ar.write(0, stat.size()))
                    System.err.println("Error!");
            }
            else {
                // Construct synthetic directory
                System.err.println("File \"" + path + "\" not found in \"" +
                                   arch_name + "\"");

                System.err.println("synthetic directory");
                ar.writeSyntheticDirectory(path);
                System.exit(0);
            }

        } catch (Exception e) {
            System.err.println("AAAaaa!");
            e.printStackTrace();
            System.exit(2);
        }
    }
}
