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

import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.FsMetadata;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.StringUtil;

import com.sun.honeycomb.emd.config.Namespace;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Filename;
import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.FsAttribute;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.parsers.FilenameParser;

import com.sun.honeycomb.coordinator.Coordinator;

import org.mortbay.http.HttpContext;

import java.beans.PropertyChangeListener;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.Properties;
import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;

import java.util.logging.LogRecord;
import java.util.logging.Handler;
import java.util.logging.StreamHandler;
import java.util.logging.Formatter;

import java.text.SimpleDateFormat;
import java.util.Date;

//                               CAVE!
//
// Since this class is used for unit tests and debugging (only), it is
// *very* important this be correct! Screw any optimizations, go for
// simplicity. (At some point someone might decide to implement this
// using some sort of relational database -- Derby?)

// Data are stored between executions in a plain text file, one entry
// per line, with columns separated by the vertical bar '|'. OIDs are
// stored as a sequence of 56 hex digits.
//
// Literal '|'s are quoted with a leading backslash, which implies
// literal backslashes must be quoted.
//
// The metadata config (namespaces, schema, views) is stored in
// ${directory}/config. The database is stored in ${directory}/DB.
//
// File contents are stored in the same directory, each named with its
// OID.
//
// The complete "Honeycomb state" is thus a directory, and the whole
// directory should be treated as a unit.

public class Simulator extends HCInterface {

    public static final String FIELD_EXPIRATION =
        SystemMetadata.FIELD_NAMESPACE + "." + "expiration_date";
    public static final String FIELD_LEGALHOLDS =
        SystemMetadata.FIELD_NAMESPACE + "." + "legal_holds";

    private static final int OID_SIZE = 30;
    private static final int BUFSIZE = 8192;
    private static Random random = null;

    private static final Logger logger =
        Logger.getLogger(Simulator.class.getName());

    class Row {
        NewObjectIdentifier oid;
        Map attributes;
        Row (NewObjectIdentifier oid, Map attributes) {
            this.oid = oid;
            this.attributes = attributes;
        }
        NewObjectIdentifier oid() { return oid; }
        Map attributes() { return attributes; }
        String getValue(String name) { return (String) attributes.get(name); }
        public String toString() {
            StringBuffer sb = new StringBuffer("[");

            for (Iterator i = attributes.keySet().iterator(); i.hasNext(); ) {
                String name = (String) i.next();

                sb.append(' ').append(name).append('=');
                sb.append(StringUtil.image(attributes.get(name)));
            }

            return sb.append(" ]").toString();
        }
    }

    protected Object legalholdLock = null;

    private boolean modified = false;
    private boolean initialized = false;
    protected File directory = null;

    // This is the database. It maps hexString(oid) to Row.
    protected Map table = null;

    public Simulator(File directory) {
        this.directory = directory;
        random = new Random(System.currentTimeMillis());
        legalholdLock = new Object();
        table = new HashMap();

        initialized = true;
    }

    public void init() throws IOException {
        read();
        new SaverThread(this).start();
    }

    protected void finalize() {
        if (table != null)
            try {
                write();
            }
            catch (Exception ignored) {}
    }

    public synchronized void save() throws IOException {
        if (!initialized) throw new RuntimeException("Uninitialized");
        if (!modified) return;

        logger.info("Saving DB....");
        write();
        modified = false;
        logger.info("Saved DB.");
    }

    public synchronized void close() throws IOException {
        if (!initialized) throw new RuntimeException("Uninitialized");
        initialized = false;
        logger.info("Closing DB....");
        write();
        logger.info("Closed DB.");
        table = new HashMap();
    }

    /**
     * A metadata query: a list of names, and a (possibly smaller)
     * list of values. If they have the same size, the results will
     * be a list of OIDs; otherwise, all the defined values for the
     * next undefined attribute.
     *
     * Results are returned to listener via callback.
     */
    public synchronized int query(String[] names, String[] values,
                                  ArrayList desiredAttrs,
                                  MDListener listener) {
        if (!initialized) throw new RuntimeException("Uninitialized");

        String msg = null;
        if (logger.isLoggable(Level.INFO)) {
            msg = "Query [[";
            for (int i = 0; i < values.length; i++)
                msg += " " + names[i] + "='" + values[i] + "'";
            msg += " ]] attrs {";
            for (int i = 0; i < desiredAttrs.size(); i++)
                msg += " " + desiredAttrs.get(i);
        }

        if (names.length < values.length)
            throw new RuntimeException("More values than names");

        int nEntries;
        if (names.length == values.length)
            nEntries = findObjects(names, values, desiredAttrs, listener);
        else
            nEntries = findValues(names, values, listener);

        if (msg != null)
            logger.info(msg + " } -> " + nEntries + " values.");

        return nEntries;
    }

    /** A unique query, so we don't need callbacks */
    public synchronized String[] queryObject(NewObjectIdentifier oid,
                                             ArrayList desiredAttrs) {
        if (!initialized) throw new RuntimeException("Uninitialized");

        if (logger.isLoggable(Level.INFO)) {
            String msg = "Query [[ " + HCInterface.FIELD_OID + "='"
                + oid.toHexString() + "' ]] attrs {";
            for (int i = 0; i < desiredAttrs.size(); i++)
                msg += " " + desiredAttrs.get(i);
            logger.info(msg + " }");
        }

        return getAttributes(oid, desiredAttrs);
    }

    /** See if any objects exist that satisfy the names/values */
    public HCObject getObject(String[] names, String[] values) {
        
        if (!initialized) throw new RuntimeException("Uninitialized");

        String msg = null;
        if (logger.isLoggable(Level.INFO)) {
            msg = "Query [[";
            for (int i = 0; i < values.length; i++)
                if (values[i] == null)
                    msg += " " + names[i] + "= NULL";
                else
                    msg += " " + names[i] + "='" + values[i] + "'";
            msg += " ]]";
        }

        if (names.length < values.length)
            throw new RuntimeException("More values than names");

        return getMatch(names, values);
    }

    public synchronized boolean isDeletable(NewObjectIdentifier oid) {
        // Check expiration dates and legal holds

        try {
            if (getLegalHolds(oid) != null)
                return false;
        }
        catch (ArchiveException e) {
            // OK.
        }

        String error = null;
        try {
            Date expiration = getExpiration(oid);
            if (expiration == null ||
                    expiration.getTime() > System.currentTimeMillis())
                return false;
        }
        catch (ArchiveException e) {
            // Pas de problem!
        }

        return true;
    }

    public synchronized void delete(NewObjectIdentifier oid)
            throws ArchiveException {

        logger.info("**** DELETE " + oid.toHexString());

        if (!isDeletable(oid))
            throw new ArchiveException("File is not deletable");

        // Delete from DB
        dbDelete(oid);

        // Delete the file
        File f = makeFile(directory, oid);
        f.delete();
    }

    /** Create a new file with metadata */
    public synchronized NewObjectIdentifier storeFile(Map metadata,
                                                      InputStream inp)
            throws IOException {
        if (!initialized) throw new RuntimeException("Uninitialized");
        modified = true;

        NewObjectIdentifier oid = newOID();

        long fileSize = storeData(makeFile(directory, oid), inp);

        // Populate md with system attributes ctime, size, and oid

        metadata.put(HCInterface.FIELD_OID, oid.toExternalHexString());
        metadata.put(HCInterface.FIELD_CTIME,
                     Long.toString(System.currentTimeMillis()));
        metadata.put(HCInterface.FIELD_SIZE, Long.toString(fileSize));

        // Put in DB
        store(oid, metadata);

        return oid;
    }

    /** Write out file contents */
    public synchronized boolean writeObject(NewObjectIdentifier oid,
                                            OutputStream os,
                                            long offset, long length)
            throws IOException {
        if (!initialized) throw new RuntimeException("Uninitialized");

        File f = makeFile(directory, oid);
        return writeObject(f, null, os, offset, length);
    }

    /** Write out file contents using NIO */
    public synchronized boolean writeObject(NewObjectIdentifier oid,
                                            WritableByteChannel channel,
                                            long offset, long length)
            throws IOException {
        if (!initialized) throw new RuntimeException("Uninitialized");

        File f = makeFile(directory, oid);
        return writeObject(f, channel, null, offset, length);
    }

    /**
     * Ask for notifications when objects are deleted, and register
     * the HTTP context with the Protocol server
     */
    public synchronized void register(DeleteListener obj,
                                      HttpContext context) {
        if (!initialized) throw new RuntimeException("Uninitialized");

    }

    //////////////////////////////////////////////////////////////////////
    
    /** Save a property to the persistent config store */
    public void saveClusterProperty(String name, String value) {
        System.setProperty(name, value);
    }

    /** Get a property from the persistent config store */
    public Properties getClusterProperties() {
        Properties prop = new Properties(System.getProperties());

        return prop;
    }

    /** Add a listener for property change events */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        // Not implemented
    }

    //////////////////////////////////////////////////////////////////////
    // Compliance attributes -- FIELD_EXPIRATION, FIELD_LEGALHOLDS

    /** Set the expiration date on an object */
    public void setExpiration(NewObjectIdentifier oid, Date when)
            throws ArchiveException {
        Date oldExpiration = null;
        String error = null;

        try {
            oldExpiration = getExpiration(oid);

            // Check the new expiration date against the old

            if (oldExpiration != null && when == null)
                error = "Can't overwrite a specific date with " +
                    "\"unspecified\"";

            if (oldExpiration != null && when != null && 
                    oldExpiration.after(when))
                error = "Can't move the expiration date back";

            if (oldExpiration == null && when == null)
                return;
        }
        catch (ArchiveException e) {
            // If there was no old expiration date, everything is allowed
        }

        if (error != null)
            throw new ArchiveException(error);

        if (when == null) {
            setMD(oid, FIELD_EXPIRATION, "-1");
            return;
        }

        long t = when.getTime();
        if (t > System.currentTimeMillis())
            setMD(oid, FIELD_EXPIRATION, Long.toString(t));
        else
            throw new ArchiveException("Time " + when + " is in the past");
    }

    /** Get the expiration date of an object */
    public Date getExpiration(NewObjectIdentifier oid)
            throws ArchiveException {
        long expiration;
        try {
            String exp = getMD(oid, FIELD_EXPIRATION);
            if (exp == null || exp.length() == 0)
                // No expiration set
                throw new ArchiveException("No expiration date");

            expiration = Long.parseLong(exp);
            if (expiration == -1)
                return null;

            Date d = new Date(expiration);
            logger.info(d.toString());
            return d;
        }
        catch (NumberFormatException e) {
            throw new RuntimeException(e);
        }
        catch (ArchiveException e) {
            throw e;
        }
        catch (Exception e) {
            logger.warning("AAA! " + e);
            e.printStackTrace();
        }
        return null;
    }

    /** Add a legal holds on the object */
    public void addLegalHold(NewObjectIdentifier oid, String tag)
            throws ArchiveException {
        synchronized(legalholdLock) {
            Set holds = getLegalHolds(oid);
            if (holds == null)
                holds = new HashSet();
            holds.add(tag);
            setLegalHolds(oid, holds);
        }
    }

    /** Remove a legal hold on an object */
    public void removeLegalHold(NewObjectIdentifier oid, String tag)
            throws ArchiveException {
        synchronized(legalholdLock) {
            Set holds = getLegalHolds(oid);
            if (holds == null)
                holds = new HashSet();
            holds.remove(tag);
            setLegalHolds(oid, holds);
        }
    }

    /** Set the list of legal holds on the object */
    public void setLegalHolds(NewObjectIdentifier oid, Set value)
            throws ArchiveException {
        synchronized(legalholdLock) {
            setMD(oid, FIELD_LEGALHOLDS, toString(value));
        }
        
    }

    /** Get the list of legal holds on an object */
    public Set getLegalHolds(NewObjectIdentifier oid)
            throws ArchiveException {
        synchronized(legalholdLock) {
            return toSet(getMD(oid, FIELD_LEGALHOLDS));
        }
    }

    //////////////////////////////////////////////////////////////////////

    protected boolean writeObject(File f,
                                  WritableByteChannel channel, OutputStream os,
                                  long offset, long length)
            throws IOException {

        if (logger.isLoggable(Level.FINE))
            logger.fine("Writing [" + offset + ":+" + length + "] of " +
                        f.getAbsolutePath() + " (filesize=" + f.length() + ")");

        int nread;
        byte[] buffer = new byte[BUFSIZE];
        ByteBuffer buf = ByteBuffer.allocate(BUFSIZE);
        FileInputStream inp = new FileInputStream(f);

        long remaining = length;
        inp.skip(offset);

        while (remaining > 0) {
            int toRead = BUFSIZE;
            if (remaining < toRead)
                toRead = (int) remaining;

            if ((nread = inp.read(buffer, 0, toRead)) <= 0)
                break;

            if (logger.isLoggable(Level.FINE))
                logger.fine("Writing " + nread + " bytes of " +
                            remaining + " remaining");

            if (channel != null) {
                buf.clear();
                buf.put(buffer, 0, nread).flip();
                channel.write(buf);
            }
            else
                os.write(buffer, 0, nread);

            remaining -= nread;
        }

        inp.close();
        return true;
    }

    protected long storeData(File f, InputStream inp)
            throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        long fileSize = 0;
        int nread = 0;
        byte[] buffer = new byte[BUFSIZE];
        while ((nread = inp.read(buffer)) > 0) {
            os.write(buffer, 0, nread);
            fileSize += nread;
        }
        os.close();
        return fileSize;
    }

    //////////////////////////////////////////////////////////////////////

    private void read() throws IOException {

        table = new HashMap();

        NewObjectIdentifier oid;
        File dbFile = new File(directory, "DB");
        BufferedReader db = new BufferedReader(new FileReader(dbFile));

        logger.info("Reading DB from " + dbFile.getAbsolutePath());

        String line;
        while ((line = db.readLine()) != null) {
            // The line has braces around it
            Map md = getMap(line.substring(1, line.length() - 1));
            String oidStr = (String) md.get(HCInterface.FIELD_OID);
            try {
                oid = NewObjectIdentifier.fromHexString(oidStr);
            }
            catch (Exception e) {
                e.printStackTrace();
                System.err.println("Bad OID: \"" + oidStr + "\" in " +
                                   StringUtil.mapToString(md));
                continue;
            }

            // Check the size of the file
            File content = new File(directory, oid.toExternalHexString());
            if (!content.exists()) {
                System.err.println("No content for " + oid +
                                   " (" + content.getAbsolutePath() + ")");
                continue;
            }
            md.put(HCInterface.FIELD_SIZE, Long.toString(content.length()));

            Row f = new Row(oid, md);
            table.put(oid.toExternalHexString(), f);

            if (logger.isLoggable(Level.FINER))
                logger.finer("Object " + f);
        }
        db.close();

        // All done.
    }

    // Mapping from OID to filename
    protected File makeFile(File dir, NewObjectIdentifier oid) {
        return new File(dir, oid.toExternalHexString());
    }

    protected void write() throws IOException {

        File dbFile = new File(directory, "DB");
        PrintWriter db =
            new PrintWriter(new BufferedWriter(new FileWriter(dbFile)));

        logger.info("Writing DB to " + dbFile.getAbsolutePath());

        for (Iterator i = table.keySet().iterator(); i.hasNext(); ) {
            Row r = (Row) table.get(i.next());
            db.println(r.toString());
            if (logger.isLoggable(Level.FINEST))
                logger.finest("Writing " + r);
        }

        db.close();
    }

    /** Overwrite any old metadata with this new set */
    protected void store(NewObjectIdentifier oid, Map metadata) {
        synchronized(table) {
            table.put(oid.toExternalHexString(), new Row(oid, metadata));
        }
    }

    /** Delete the metadata associated with the object */
    protected void dbDelete(NewObjectIdentifier oid) {
        synchronized(table) {
            table.remove(oid.toHexString());
        }
    }

    /** Get metadata for an object */
    protected Map retrieve(NewObjectIdentifier oid) {
        synchronized(table) {
            Row r = (Row) table.get(oid.toHexString());
            if (r == null)
                return null;
            return r.attributes();
        }
    }

    /** Add a name-value pair to object's metadata */
    protected void setMD(NewObjectIdentifier oid, String name, String value) {
        synchronized(table) {
            Map m = retrieve(oid);
            if (m == null)
                m = new HashMap();
            m.put(name, value);
            store(oid, m);
        }
    }

    protected String getMD(NewObjectIdentifier oid, String name) {
        synchronized(table) {
            Map m = retrieve(oid);
            logger.fine("Getting \"" + name + "\"");
            dump(m, Level.FINE);
            if (m == null)
                return null;
            return (String) m.get(name);
        }
    }

    protected int findValues(String[] names, String[] values,
                             MDListener listener) {
        String attrName = names[values.length];
        Set newValues = new HashSet();
        for (Iterator i = table.keySet().iterator(); i.hasNext(); ) {
            Row r = (Row) table.get(i.next());
            if (match(r, names, values)) {
                String value = r.getValue(attrName);
                logger.finest("For " + attrName + ": \"" + value + "\"");
                if (value != null)
                    newValues.add(value);
            }
        }

        int n = 0;
        for (Iterator i = newValues.iterator(); i.hasNext(); n++)
            listener.nextValue((String)i.next());
        return n;
    }

    protected int findObjects(String[] names, String[] values,
                              ArrayList desiredAttrs,
                              MDListener listener) {
        int n = 0;
        for (Iterator i = table.keySet().iterator(); i.hasNext(); ) {
            Row r = (Row) table.get(i.next());
            if (logger.isLoggable(Level.FINEST))
                logger.finest("Testing row " + r);
            if (match(r, names, values)) {
                n++;
                listener.nextObject(r.oid(), makeMap(r, desiredAttrs));
            }
        }
        return n;
    }

    private boolean match(Row r, String[] names, String[] values) {
        for (int i = 0; i < values.length; i++) {
            String value = r.getValue(names[i]);
            if (value == null || !value.equals(values[i]))
                return false;
        }

        return true;
    }

    protected String[] getAttributes(NewObjectIdentifier oid,
                                     ArrayList desiredAttrs) {
        Row r = (Row) table.get(oid.toExternalHexString());
        if (r == null)
            throw new RuntimeException("No such object: " + oid.toExternalHexString());

        return fillIn(r, desiredAttrs);
    }

    protected HCObject getMatch(String[] names, String[] values) {
        throw new RuntimeException("Unimplemented");
    }

    private String[] fillIn(Row r, ArrayList desiredAttrs) {
        String[] rv = new String[desiredAttrs.size()];
        for (int i = 0; i < rv.length; i++)
            rv[i] = r.getValue((String)desiredAttrs.get(i));
        return rv;
    }

    /** Lookup the desired names and return as a Map */
    private Map makeMap(Row r, String[] names) {
        Map rv = new HashMap();
        for (int i = 0; i < names.length; i++)
            rv.put(names[i], r.getValue(names[i]));
        return rv;
    }
    private Map makeMap(Row r, ArrayList names) {
        Map rv = new HashMap();
        for (int i = 0; i < names.size(); i++) {
            String name = (String) names.get(i);
            rv.put(name, r.getValue(name));
        }
        return rv;
    }

    //////////////////////////////////////////////////////////////////////
    // Static methods


    //////////////////////////////////////////////////////////////////////
    // Public static methods

    public static synchronized void record(NewObjectIdentifier oid,
                                           Map metadata,
                                           String[] names, String[] values,
                                           String[] vaNames, String[] attrs) {
        try {
            FileWriter recorder = new FileWriter("/data/3/DB", true);

            StringBuffer sb = new StringBuffer();
            sb.append(HCInterface.FIELD_OID + "=\"");
            sb.append(oid.toExternalHexString()).append('"');

            for (Iterator i = metadata.keySet().iterator(); i.hasNext(); ) {
                String name = (String) i.next();
                sb.append(' ').append(name).append('=');
                sb.append(StringUtil.image(metadata.get(name)));
            }
            for (int i = 0; i < attrs.length; i++) {
                sb.append(' ').append(vaNames[i]).append('=');
                sb.append(StringUtil.image(attrs[i]));
            }
            for (int i = 0; i < values.length; i++) {
                sb.append(' ').append(names[i]).append('=');
                sb.append(StringUtil.image(names[i]));
            }

            sb.append('\n');
            recorder.write(sb.toString(), 0, sb.length());
            recorder.close();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "!!!", e);
        }
    }

    public static Map getMap(String s) {

        Map retval = new HashMap();

        // Repeatedly: match name, then =, then double quoted value
        int pos = 0;
        for (;;) {
            int index = s.indexOf('=', pos);
            if (index < 0)
                break;

            String name = s.substring(pos, index).trim();

            // Skip past the =
            if (++index == s.length() - 1)
                 throw new RuntimeException("No value for <" + name + ">");

            // Skip spaces
            while (s.charAt(index) == ' ')
                index++;

            // Find the next double quote
            if (s.charAt(index) != '"')
                throw new RuntimeException("Value for <" + name + "> !quoted");

            pos = index + 1;

            // Now scan ahead till we find an unquoted '"'

            StringBuffer sb = new StringBuffer();
            for (; pos < s.length(); pos++) {
                char c = s.charAt(pos);

                if (c == '"') {
                    // Done
                    retval.put(name, sb.toString());
                    pos++;
                    break;
                }

                if (c == '\\') {
                    if (pos == s.length() - 1)
                        throw new RuntimeException("Last char is backslash!");

                    char n = s.charAt(pos + 1);
                    if (n == '\\' || n == '"') {
                        sb.append(n);
                        pos++;
                        continue;
                    }
                }

                sb.append(c);
            }
            if (pos >= s.length())
                break;

            if (s.charAt(pos) != ' ')
                throw new RuntimeException("No space between pairs");
            pos++;
        }

        return retval;
    }

    public static void setupLogging(Level level) {
        try {
            String conf = "handlers=com.sun.honeycomb.connectors.UTHandler";
            conf += "\n.level=" + level + "\ncom.sun.level=" + level;
            LogManager mgr = LogManager.getLogManager();
            mgr.readConfiguration(new ByteArrayInputStream(conf.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Logger base = Logger.getLogger("com.sun");
        base.setLevel(level);
    }

    /** At regular intervals save the DB */
    class SaverThread extends Thread {
        Simulator parent = null;
        private boolean terminate = false;
        SaverThread(Simulator parent) {
            this.parent = parent;
        }
        public void run() {
            while (!terminate) {
                try {
                    Thread.sleep(60000);
                }
                catch (InterruptedException ie) {
                    logger.info("Sleep interrupted");
                }
                try {
                    parent.save();
                }
                catch (IOException e) {
                    logger.log(Level.WARNING, "Couldn't save!", e);
                }
            }
        }
        public void terminate() { terminate = true; }
    }

    static String nsname(String s) {
        int pos = s.lastIndexOf('.');
        if (pos < 0)
            return "";

        return s.substring(0, pos);
    }

    static String basename(String s) {
        int pos = s.lastIndexOf('.');
        if (pos < 0)
            return s;

        return s.substring(pos + 1);
    }

    static NewObjectIdentifier newOID() {
        return new NewObjectIdentifier(42, NewObjectIdentifier.DATA_TYPE, 0);
    }

    /** Parse a string into a set of tags */
    static private Set toSet(String tags) {
        if (tags == null || tags.length() == 0)
            return null;

        int p1, p2;
        Set retval = new HashSet();

        p1 = 0;
        while (p1 >= 0 && p1 < tags.length()) {
            if (tags.charAt(p1) != '\'')
                throw new RuntimeException("Expected ' at \"" + tags +
                                           "\"[" + p1 + "]");
            p1++;               // Skip the first apostrophe

            StringBuffer sb = new StringBuffer();
            for (;;) {
                if ((p2 = tags.indexOf('\'', p1)) < 0)
                    throw new RuntimeException("Expected ' after \"" + tags +
                                               "\"[" + (p1+1) + "]");

                sb.append(tags.substring(p1, p2));
                p1 = p2 + 1;

                if (p1 == tags.length() || tags.charAt(p1) != '\'')
                    // Done with string
                    break;

                sb.append('\'');
            }

            retval.add(sb.toString());
            p1 = tags.indexOf('\'', p1);
        }

        return retval;
    }

    /** Encode set of tags into a string */
    static private String toString(Set tags) {
        if (tags == null)
            return "";

        String delim = "";
        StringBuffer sb = new StringBuffer();

        for (Iterator i = tags.iterator(); i.hasNext(); ) {
            sb.append(delim).append('\'');
            sb.append(HCInterface.quoteEMD((String)i.next()));
            sb.append('\'');
            delim = " ";
        }

        return sb.toString();
    }

    private static void dump(Map m, Level l) {
        String msg = "{";
        String delim = "";
        for (Iterator i = m.keySet().iterator(); i.hasNext(); ) {
            String key = (String) i.next();

            msg += delim + key + "=" + StringUtil.image(m.get(key));
            delim = ", ";
        }

        logger.log(l, msg + "}");
    }
}
