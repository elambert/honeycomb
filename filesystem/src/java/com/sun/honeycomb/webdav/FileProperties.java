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



package com.sun.honeycomb.webdav;

import com.sun.honeycomb.common.XMLEncoder;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;

import com.sun.honeycomb.fscache.FSCacheException;
import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.fscache.FSCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.text.SimpleDateFormat;
import java.text.FieldPosition;

import java.nio.ByteBuffer;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

import java.util.Set;
import java.util.Date;
import java.util.Calendar;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpException;

/**
 * This is a helper class for the WebDAV filesystem module. It acts
 * as the glue between WebDAV and Honeycomb metadata.
 *
 * @author Shamim Mohamed <shamim@sun.com>
 */
public class FileProperties {

    /////////////////////////////////////////////////////////
    //                                                     //
    // Want to add more WebDAV properties?                 //
    //    1. constant PROP_*    add to list                //
    //    2. initialize()       add to propNameMap         //
    //    3. getProperty(int)   add implementation         //
    //                                                     //
    // Caveat: Also see ProppatchHandler, where compliance //
    // tags are used.                                      //
    //                                                     //
    /////////////////////////////////////////////////////////

    // Statics

    private static final int PROP_NULL			=  0;
    private static final int PROP_CREATIONDATE  	=  1;
    private static final int PROP_DISPLAYNAME   	=  2;
    private static final int PROP_RESOURCETYPE  	=  3;
    private static final int PROP_EXECUTABLE    	=  4;
    private static final int PROP_SOURCE        	=  5;
    private static final int PROP_GETCONTENTLENGTH      =  6;
    private static final int PROP_GETCONTENTLANGUAGE    =  7;
    private static final int PROP_GETCONTENTTYPE        =  8;
    private static final int PROP_GETETAG       	=  9;
    private static final int PROP_GETLASTMODIFIED       = 10;
    // These are HCFS properties
    private static final int PROP_HCFS                  = 11;
    private static final int PROP_UID			= 11;
    private static final int PROP_GID			= 12;
    private static final int PROP_PERMISSIONS		= 13;
    private static final int PROP_OID       		= 14;

    private static final int PROP_EXPIRATION    	= 15;
    private static final int PROP_LEGAL_HOLDS    	= 16;

    private static final String PROP_LEGALHOLDTAG =
        Constants.HCFS_NAMESPACE + ":case";

    // Also used for expiration dates
    private static final SimpleDateFormat creationDateFormat =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final SimpleDateFormat modificationDateFormat =
        new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

    // For depth
    static final int INFINITY = -666;

    // Cached metadata attributes

    private static String[] propNames = null;
    private static Map propNameMap = null;
    private static final Logger logger =
        Logger.getLogger(PropfindHandler.class.getName());

    static synchronized void initialize() {
        if (propNameMap != null) return;

        propNameMap = new HashMap();

        propNameMap.put("",
                        new Integer(PROP_NULL));
        propNameMap.put("creationdate",
                        new Integer(PROP_CREATIONDATE));
        propNameMap.put("displayname",
                        new Integer(PROP_DISPLAYNAME));
        propNameMap.put("resourcetype",
                        new Integer(PROP_RESOURCETYPE));
        propNameMap.put("executable",
                        new Integer(PROP_EXECUTABLE));
        propNameMap.put("source",
                        new Integer(PROP_SOURCE));
        propNameMap.put("getcontentlength",
                        new Integer(PROP_GETCONTENTLENGTH));
        propNameMap.put("getcontentlanguage",
                        new Integer(PROP_GETCONTENTLANGUAGE));
        propNameMap.put("getcontenttype",
                        new Integer(PROP_GETCONTENTTYPE));
        propNameMap.put("getetag",
                        new Integer(PROP_GETETAG));
        propNameMap.put("getlastmodified",
                        new Integer(PROP_GETLASTMODIFIED));
        propNameMap.put("uid",
                        new Integer(PROP_UID));
        propNameMap.put("gid",
                        new Integer(PROP_GID));
        propNameMap.put("mode",
                        new Integer(PROP_PERMISSIONS));
        propNameMap.put("oid",
                        new Integer(PROP_OID));

        /* Retention has been disabled
        propNameMap.put("expiration",
                        new Integer(PROP_EXPIRATION));
        propNameMap.put("legalholds",
                        new Integer(PROP_LEGAL_HOLDS));
        */

        propNames = new String[propNameMap.size()];
        Set s = propNameMap.keySet();
        for (Iterator i = s.iterator(); i.hasNext(); ) {
            String pName = (String) i.next();
            Integer index = (Integer) propNameMap.get(pName);
            propNames[index.intValue()] = pName;
        }

        if (logger.isLoggable(Level.FINE)) {
            String msg = "Known WebDAV properties:";
            for (int i = 0; i < propNames.length; i++)
                msg += " " + propNames[i];
            logger.fine(msg);
        }
    }

    /**
     * This is a BFS of the sub-tree rooted at this file but limited to
     * "depth" levels below
     *
     * Dunno if this class is the right place for this method, but
     * PropfindHandler is not it
     */
    static HCFile[] getDescendants(HCFile file, int depth)
            throws HttpException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("getting desc. for " + file.toString() + ":" + depth);

        List nextGen = null;
        try {
            file.addChildren(null);
            nextGen = HCFile.fileCache.listChildren(null, file);
        }
        catch (FSCacheException e) {
            // No children -- not an error
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Argh!", e);
        }
        if (nextGen == null)
            return null;

        List files = new LinkedList();
        for (int i = 0; depth == INFINITY || i < depth; i++) {
            List thisGen = nextGen;
            nextGen = new LinkedList();

            for (Iterator c = thisGen.iterator(); c.hasNext(); ) {
                HCFile f = (HCFile) c.next();
                files.add(f);

                if (f.isFile())
                    continue;

                if (depth != INFINITY && i >= depth - 1)
                    // Not going any deeper; no need to add children
                    continue;

                // Add all of f's children to nextGen
                List children = null;
                try {
                    f.addChildren(null);
                    children = HCFile.fileCache.listChildren(null, f);
                }
                catch (FSCacheException e) {
                    logger.log(Level.SEVERE, "Argh!", e);
                    return null;
                }

                if (children != null)
                    for (Iterator j = children.iterator(); j.hasNext(); )
                        nextGen.add(j.next());
            }

            if (nextGen.size() == 0)
                break;
        }

        HCFile[] fileArray = new HCFile[files.size()];
        return (HCFile[]) files.toArray(fileArray);
    }

    //////////////////////////////////////////////////////////////////////

    private HCFile file;

    FileProperties() {
        initialize();
        this.file = null;
    }

    FileProperties(HCFile file) {
        initialize();
        this.file = file;
    }

    /** Return an array with the names of all properties this file has */
    String[] getPropertyNames() {
        List v = new ArrayList();

        for (int i = 0; i < propNames.length; i++) {
            if (getProperty(i) != null)
                v.add(XMLname(propNames[i]));
        }

        String[] names = new String[v.size()];
        return (String[]) v.toArray(names);
    }

    /** Return a Map from property name to (string) value */
    Map getAllProperties() {
        Map m = new HashMap();

        for (int i = 0; i < propNames.length; i++) {
            String p = getProperty(i);
            if (p != null)
                m.put(propNames[i], p);
        }
        return m;
    }

    /** Lookup a property */
    String getProperty(String propName) {
        Integer o = (Integer) propNameMap.get(propName);
        if (o == null)
            return null;
        return getProperty(o.intValue());
    }

    //////////////////////////////////////////////////////////////////////
    // Private

    /**
     * The implementation: for each WebDAV property, add a case here
     * that returns the value of the property as a string. If no such
     * property exists, return null.
     *
     * @param p the property to get
     */
    private String getProperty(int p) {
        switch (p) {

        case PROP_CREATIONDATE:
            return q(creationDateFormat.format(new Date(file.crtime())));

        case PROP_GETLASTMODIFIED:
            return q(modificationDateFormat.format(new Date(file.mtime())));

        case PROP_DISPLAYNAME:
            try {
                return q(URLEncoder.encode(file.displayName(), "UTF-8"));
            }
            catch (UnsupportedEncodingException ignored) {}
            break;

        case PROP_RESOURCETYPE:
            if (!file.isFile())
                return "<collection/>";
            break;

        case PROP_EXECUTABLE:
            return "F";

        case PROP_SOURCE:
            break;

        case PROP_GETCONTENTLENGTH:
            return q(file.size());

        case PROP_GETCONTENTLANGUAGE:
            if (file.isFile()) return q(file.getContentLanguage());
            break;

        case PROP_OID:
            if (file.isFile()) return q(file.getOidHexString());
            break;

        case PROP_GETETAG:
            break;

        case PROP_GETCONTENTTYPE:
            if (file.isFile())
               return file.mimeType();
            else
                return "httpd/unix-directory";

        case PROP_UID:
            if (file.isFile() && file.uid() >= 0) return q(file.uid());
            break;

        case PROP_GID:
            if (file.isFile() && file.gid() >= 0) return q(file.gid());
            break;

        case PROP_PERMISSIONS:
            if (file.isFile()) {
                long mode = file.mode();
                if (mode >= 0)
                    return q(prMode((int) mode));
            }
            else
                return "dr-xr-xr-x";
            break;

            /* Retention has been disabled
        case PROP_EXPIRATION:
            try {
                Date expiration = file.getExpiration();
                if (expiration == null)
                    return "unknown";
                else
                    return q(expiration);
            }
            catch (ArchiveException e) {
                // Object has no expiration date
            }
            catch (Exception e) {
                logger.warning("AAA! " + e);
                e.printStackTrace();
            }
            break;

        case PROP_LEGAL_HOLDS:
            try {
                Set holds = file.getLegalHolds();
                if (holds != null)
                    return formatHolds(holds, PROP_LEGALHOLDTAG);
            }
            catch (ArchiveException e) {
                // No legal holds
            }
            break;
            */

        }
        return null;
    }

    // Methods to convert (and quote UTF-8 if necessary) various types

    private static String q(Object o) {
        // Find the type of the object and do the appropriate thing

        if (o == null)
            return "(null)";

        String type = o.getClass().getName();

        if (type.endsWith(".String"))
            return q((String) o);
        if (type.endsWith(".Long"))
            return q(((Long) o).longValue());
        if (type.endsWith(".Double"))
            return q(((Double) o).doubleValue());
        if (type.endsWith(".Blob"))
            return q(format((Blob)o));
        if (type.endsWith(".Calendar"))
            return q(format((Calendar) o));
        if (type.endsWith(".Time"))
            return q(format((Time) o));
        if (type.endsWith(".Date"))
            return q(format((Date) o));

        return q(o.toString());
    }

    private static String q(long x) { return Long.toString(x); }
    private static String q(int x)  { return Integer.toString(x); }
    private static String q(double x)  { return Double.toString(x); }
    private static String q(String x) { return XMLEncoder.encode(x); }


    //////////////////////////////////////////////////////////////////////
    // utils


    /**
     * Basic get. The returned Object is one of String, Long, Double,
     * ByteBuffer (blob), java.util.Calendar, and Time
     */
    Object get(String attrName) {
        return "${" + attrName + "}";
    }

    // Mode as from ls(1)
    public static String prMode(long mode) {
        // Return a string like -rwxrwxr-x
        StringBuffer buffer = new StringBuffer(10);

        if (mode < 0 || mode > 0177777) // Code here handles up to 0177777
            return "0" + Long.toOctalString(mode);

        for (int i = 0; i < 3; i++) {
            int m = (int) mode & 7;
            mode >>>= 3;
            switch (m) {
            case 0:
                buffer.insert(0, "---");
                break;
            case 1:
                buffer.insert(0, "--x");
                break;
            case 2:
                buffer.insert(0, "-w-");
                break;
            case 3:
                buffer.insert(0, "-wx");
                break;
            case 4:
                buffer.insert(0, "r--");
                break;
            case 5:
                buffer.insert(0, "r-x");
                break;
            case 6:
                buffer.insert(0, "rw-");
                break;
            case 7:
                buffer.insert(0, "rwx");
                break;
            }
        }

        long m = mode & 7;
        mode >>>= 3;

        if ((m & 1) != 0) {
            if (buffer.charAt(8) == '-')
                buffer.setCharAt(8, 'T');
            else
                buffer.setCharAt(8, 't');
        }
        if ((m & 2) != 0) {
            if (buffer.charAt(5) == '-')
                buffer.setCharAt(5, 'G');
            else
                buffer.setCharAt(5, 'g');
        }
        if ((m & 4) != 0) {
            if (buffer.charAt(2) == '-')
                buffer.setCharAt(2, 'S');
            else
                buffer.setCharAt(2, 's');
        }

        m = mode & 15;
        mode >>>= 4;

        switch ((int)m) {
        case 1: buffer.insert(0, "p"); break;
        case 2: buffer.insert(0, "c"); break;
        case 4: buffer.insert(0, "d"); break;
        case 6: buffer.insert(0, "b"); break;
        case 8: buffer.insert(0, "-"); break;
        case 10: buffer.insert(0, "l"); break;
        case 12: buffer.insert(0, "s"); break;
        case 13: buffer.insert(0, "D"); break;
        case 14: buffer.insert(0, "E"); break;
        default: buffer.insert(0, "?"); break;
        }

        return buffer.toString();
    }

    // Methods to format various Object types 

    static String format(Calendar c) {
        StringBuffer buffer = new StringBuffer();

        throw new InternalException("unimplemented");

        // return buffer.toString();
    }
    static String format(Time c) {
        Date when = new Date(c.value());
        return creationDateFormat.format(when);
    }
    static String format(Blob b) {
        StringBuffer buffer = new StringBuffer(2 * b.length());
        for (int i = 0; i < b.length(); i++)
            buffer.append(Integer.toHexString(b.getByteAt(i)));
        return buffer.toString();
    }

    // This is an expiration date
    static String format(Date c) {
        StringBuffer sb = new StringBuffer();
        creationDateFormat.format(c, sb, new FieldPosition(0));
        return sb.toString();
    }

    // This is a list of legal holds
    static String formatHolds(Set holds, String eltName) {
        StringBuffer buffer = new StringBuffer();
        for (Iterator i = holds.iterator(); i.hasNext(); ) {
            buffer.append('<').append(eltName).append('>');
            buffer.append(q(i.next().toString()));
            buffer.append('<').append('/').append(eltName).append('>');
        }
        return buffer.toString();
    }


    // Inner classes

    class Time {
        private long timeval;
        Time() { this(0); }
        Time(long l) { this.timeval = l; }
        long value() { return timeval; }
    }

    class Blob {
        private byte[] arr;

        Blob() {
            this(null);
        }

        Blob(byte[] arr) {
            this.arr = arr;
        }
            
        int length() {
            return ((arr == null)? 0 : arr.length);
        }

        byte getByteAt(int index)
                throws ArrayIndexOutOfBoundsException {
            return arr[index];
        }
    }

    public static String XMLname(String propName) {
        Integer o = (Integer) propNameMap.get(propName);
        if (o == null || o.intValue() < PROP_HCFS)
            return propName;

        StringBuffer sb = new StringBuffer(Constants.HCFS_NAMESPACE);
        return sb.append(':').append(propName).toString();
    }

    public static void main(String[] args) {
        for (int i = 512; i < 4096; i++)
            System.out.println(prMode(i));
    }

    //////////////////////////////////////////////////////////////////////
    // End of FileProperties
}
