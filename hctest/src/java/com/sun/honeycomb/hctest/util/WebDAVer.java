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



/**
   Uses http://www.innovation.ch/java/HTTPClient/ following the example
   of http://www.bcholmes.org/geek/slide-my-webdav-client.html
*/

package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.HoneycombLocalSuite;

import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.DavTestSchema;
import com.sun.honeycomb.hctest.util.WebDAVer;
import com.sun.honeycomb.hctest.util.URLEncoder;

import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;

import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.FileUtil;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;

import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.XMLEncoder;

import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.TestRequestParameters;

import HTTPClient.HTTPConnection;
import HTTPClient.HTTPResponse;
import HTTPClient.NVPair;
import HTTPClient.HttpOutputStream;
import HTTPClient.AuthorizationInfo;
import HTTPClient.ModuleException;

import org.jdom.input.SAXBuilder;
import org.jdom.Document;
import org.jdom.DocType;
import org.jdom.Element;
import org.jdom.Namespace;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.io.InputStream;
import java.io.StringReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

import java.util.Enumeration;

public class WebDAVer {

    // For the WebDAV Depth header
    public static final int DEPTH_INFINITE = -1;

    private static final int BUFSIZE = 1024 * 1024;

    // Size of scratch files
    private static final int FILESIZE = 100;

    private static byte[] writeBuf;
    private static NVPair[] putHeaders;

    private static String HC_HASH = HoneycombTestConstants.CURRENT_HASH_ALG;

    private static final Namespace XMLNS = Namespace.getNamespace("DAV:");
    private static final Namespace HCFSNS = Namespace.getNamespace("HCFS");
    private static final NVPair CLIENT =
        new NVPair("client", "com.sun.honeycomb.hctest.util.WebDAVer 0.1");

    private HTTPConnection conn = null;

    static {
         writeBuf = new byte[BUFSIZE];
         for (int i = 0; i < BUFSIZE; i++)
             writeBuf[i] = (byte) ('a' + i%26);

         putHeaders = new NVPair[1];
         putHeaders[0] = new NVPair("Content-type", "text/plain");

    }

    public WebDAVer(String addr) throws HoneycombTestException {
        if (addr == null || addr.length() == 0)
            throw new HoneycombTestException("addr is nullish");

        String host = null;
        int port = ProtocolConstants.WEBDAV_PORT;

        if (addr.startsWith("http://"))
            addr = addr.substring(7);

        int i = addr.indexOf(':');
        if (i < 0)
            host = addr;
        else {
            try {
                port = Integer.parseInt(addr.substring(i+1));
            }
            catch (NumberFormatException e) {
                throw new HoneycombTestException(e);
            }
            host = addr.substring(0, i);
        }
            
        Log.INFO("Connecting to " + host + ":" + port);

        try {
            conn = new HTTPConnection(host, port);
        }
        catch (Exception e) {
            throw new HoneycombTestException("Couldn't connect to " +
                                             host + ":" + port + " -- " +
                                             e.getMessage());
        }
        FileUtil.tryLocalTempDir();
    }

    /**
     * Add DIGEST credentials to all requests
     *
     * @param realm the realm that the username and password are in
     * @param user username
     * @param password password
     */
    public void setCredentials(String realm, String user, String password)
            throws HoneycombTestException {
        try {
            String host = conn.getHost();
            int port = conn.getPort();
            AuthorizationInfo.addDigestAuthorization(host, port, realm,
                                                     user, password);
        }
        catch (Exception e) {
            throw new HoneycombTestException("Couldn't add credential " +
                                             realm + ":" + user + " -- " + e);
        }
    }

    /** Close the connection to the server */
    public void close() throws HoneycombTestException {
        try {
            conn.stop();
        }
        catch (Exception e) {
            throw new HoneycombTestException("Closing HTTP connection", e);
        }
    }

    public CmdResult getFile(String url, boolean calcHash) 
            throws HoneycombTestException {
        Log.DEBUG("getFile: " + url);
        CmdResult cr = new CmdResult();

        byte[] readBuf = new byte[BUFSIZE];

        try {
            MessageDigest sha = null; // for hash
            if (calcHash)
                sha = MessageDigest.getInstance(HC_HASH);

            cr.filename = url;
            long t1 = System.currentTimeMillis();
            HTTPResponse rsp = conn.Get(url);

            if (rsp.getStatusCode() >= 300)
                throw makeTestException("getFile", url, rsp);

            cr.filesize = 0;
            InputStream is = rsp.getInputStream();

            while (true) {
                int ct = is.read(readBuf);
                if (ct == -1)
                    break;
                cr.filesize += ct;
                if (calcHash)
                    sha.update(readBuf, 0, ct);
            }

            cr.time = System.currentTimeMillis() - t1;
            cr.pass = true;

            if (calcHash)
                cr.datasha1 = HCUtil.convertHashBytesToString(sha.digest());
        }
        catch (HoneycombTestException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            cr.pass = false;
            cr.addException(e);
        }
        return cr;
    }

    public InputStream getFileStream(String url)
            throws HoneycombTestException, IOException {
        return getFileStream(url, null);
    }
    public InputStream getFileStream(String url, Map headers)
        throws HoneycombTestException, IOException {
        Log.DEBUG("getFileStream: " + url);
        CmdResult cr = new CmdResult();

        byte[] readBuf = new byte[BUFSIZE];

        try {
            cr.filename = url;
            HTTPResponse rsp = conn.Get(url);

            if (rsp.getStatusCode() >= 300)
                throw makeTestException("getFile", url, rsp);

            if (headers != null)
                for (Enumeration hdrs = rsp.listHeaders();
                     hdrs.hasMoreElements(); ) {
                    String name = (String) hdrs.nextElement();
                    String value = rsp.getHeader(name);
                    headers.put(name.toLowerCase(), value);
                }

            cr.filesize = 0;
            return rsp.getInputStream();
        }
        catch (ModuleException e) {
            e.printStackTrace();
            cr.pass = false;
            cr.addException(e);
        }
        return null;
    }

    /** Create a random new file in a davtest view and return its URL */
    public String createFile(int viewIndex, DavTestSchema schema)
            throws HoneycombTestException {
        String url = null;

        try {
            Map md = new HashMap();
            schema.addDavMD(md);
            url = schema.getDavTestName(md, viewIndex);

            NVPair[] headers = new NVPair[1];
            headers[0] = new NVPair("Content-type", "text/plain");

            if (putFile(url, FILESIZE, false, headers).pass)
                return url;
        }
        catch (HoneycombTestException e) {

            if (e.exitStatus != null) {
                int rc = e.exitStatus.getReturnCode();
                String err = e.exitStatus.getOutputString(false);
                String msg = "Couldn't upload scratch file!";

                if (rc == 400)
                    msg += " Is metadata_config_davtest.xml loaded?";

                msg += " Error " + rc + ":" + err;

                throw new HoneycombTestException(msg);
            }
            else
                throw new RuntimeException(e);
        }

        throw new HoneycombTestException("Couldn't create file [" + url + "]");
    }

    /**
     * Store a file of specified size at the given URL. The data is
     * arbitrarily generated.
     */
    public CmdResult putFile(String url, int size, boolean calcHash)
            throws HoneycombTestException {
        return putFile(url, size, calcHash, null);
    }

    /**
     * Store a file of specified size at the given URL, including any
     * extra headers. The data is arbitrarily generated.
     */
    public CmdResult putFile(String url, int size, boolean calcHash,
                             NVPair[] extraHeaders)
            throws HoneycombTestException  {
        try {
            return putFile(url, size, null, calcHash, extraHeaders);
        }
        catch (HoneycombTestException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            CmdResult cr = new CmdResult();
            cr.pass = false;
            cr.addException(e);
            return cr;
        }
    }

    /**
     * Store a file at the given URL with the data from the byte
     * channel.
     */
    public CmdResult putFile(String url, ReadableByteChannel contents)
            throws HoneycombTestException {
        return putFile(url, contents, false, null);
    }

    /**
     * Store a file at the given URL with the data from the byte
     * channel, including any extra headers.
     */
    public CmdResult putFile(String url, ReadableByteChannel contents,
                             boolean calcHash, NVPair[] extraHeaders) 
            throws HoneycombTestException {
        try {
            return putFile(url, 0, contents, calcHash, extraHeaders);
        }
        catch (HoneycombTestException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            CmdResult cr = new CmdResult();
            cr.pass = false;
            cr.addException(e);
            return cr;
        }
    }

    /**
     * The real "store file" method.
     */
    private CmdResult putFile(String url,
                              // Only one of these two should be given:
                              int nBytes, ReadableByteChannel contents,
                              boolean calcHash, NVPair[] extraHeaders)
            throws HoneycombTestException, IOException, ModuleException {

        NVPair[] headers = null;

        MessageDigest sha = null;
        if (calcHash)
            try {
                sha = MessageDigest.getInstance(HC_HASH);
            }
            catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

        CmdResult cr = new CmdResult();
        cr.filename = url;

        byte[] byte_buffer = new byte[BUFSIZE];
        ByteBuffer buff = ByteBuffer.wrap(byte_buffer);

        int toWrite, written = 0;
        long startTime = System.currentTimeMillis();

        if (contents != null)
            // If we're not writing from our buffer, don't set Content-type
            headers = extraHeaders;
        else if (extraHeaders == null)
                headers = putHeaders;
        else {
            // Need to combine the two arrays
            NVPair[] newHeaders =
                new NVPair[putHeaders.length + extraHeaders.length];

            int i = 0;
            for (int j = 0; j < putHeaders.length; j++)
                newHeaders[i++] = putHeaders[j];
            for (int j = 0; j < extraHeaders.length; j++)
                newHeaders[i++] = extraHeaders[j];

            headers = newHeaders;
        }

        HttpOutputStream out = new HttpOutputStream();
        HTTPResponse response = conn.Put(url, out, headers);

        // Write into the HTTP stream
        for (;;) {
            if (contents != null) {
                // Read from the "contents" channel and write it out
                if ((toWrite = contents.read(buff)) < 0)
                    break;

                byte[] data = buff.array();
                if (sha != null)
                    sha.update(data);

                out.write(data, 0, toWrite);
                buff.clear();
            }
            else {
                // Write from the buffer writeBuf
                if (written >= nBytes)
                    break;

                toWrite = nBytes - written;
                if (toWrite > BUFSIZE) {
                    out.write(writeBuf);
                    written += BUFSIZE;
                    if (sha != null)
                        sha.update(writeBuf);
                }
                else {
                    out.write(writeBuf, 0, toWrite);
                    if (sha != null)
                        sha.update(writeBuf, 0, toWrite);
                    break;
                }
            }
        }
        out.close();

        if (response.getStatusCode() >= 300)
            throw makeTestException("putFile", url, response);

        cr.mdoid = response.getHeader("ETag");
        cr.time = System.currentTimeMillis() - startTime;
        cr.pass = true;

        if (sha != null)
            cr.datasha1 = HCUtil.convertHashBytesToString(sha.digest());

        return cr;
    }

    /**
     * A low-level method that connects to the "ExtensionMethod" function
     * of HTTPConnection.
     *
     * @param method method to use (PROPFIND or PROPPATCH, case-insensitive)
     * @param path the path
     * @param body the XML request to send
     * @param extraHeaders any extra headers (e.g. Depth = 1)
     * @return the XML reply from the server is passed back in CmdResult.string
     */
    public CmdResult doMethodWithXML(String method, String path, String body,
                                     Map extraHeaders)
            throws HoneycombTestException {

        CmdResult retval = new CmdResult();
        byte[] xml = getUTF8Bytes(body);

        // We add 3 headers ourselves
        int nHeaders = 3;
        if (extraHeaders != null)
            nHeaders += extraHeaders.size();

        NVPair[] headers = new NVPair[nHeaders];

        int j = 0;
        headers[j++] = CLIENT;
        headers[j++] = new NVPair("Content-type", "text/xml");
        headers[j++] = new NVPair("Content-length", xml.length + "");

        if (extraHeaders != null)
            for (Iterator i = extraHeaders.keySet().iterator(); i.hasNext();) {
                String key = (String) i.next();
                String value = (String) extraHeaders.get(key);
                headers[j++] = new NVPair(key, value);
            }

        HTTPResponse response = null;
        long startTime = System.currentTimeMillis();

        try {
            response = conn.ExtensionMethod(method, path, xml, headers);
            if (response.getStatusCode() >= 300)
                throw makeTestException(method, path, response);
            retval.time = System.currentTimeMillis() - startTime;
            retval.string = new String(response.getData());
            retval.pass = true;
        }
        catch (HoneycombTestException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            retval.pass = false;
            retval.addException(e);
        }

        return retval;
    }

    /**
     * Delete a file.
     *
     * @param url the file to delete
     */
    public CmdResult deleteFile(String url) throws HoneycombTestException {
        Log.DEBUG("deleteFile: " + url);
        CmdResult retval = new CmdResult();

        try {
            retval.filename = url;
            long startTime = System.currentTimeMillis();
            HTTPResponse response = conn.Delete(url);
            retval.time = System.currentTimeMillis() - startTime;

            if (response.getStatusCode() >= 300)
                throw makeTestException("deleteFile", url, response);
            
            retval.pass = true;
            retval.string = new String(response.getData());
        }
        catch (HoneycombTestException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
            retval.pass = false;
            retval.addException(e);
        }
        return retval;
    }

    /** Check for the existence of a webdav URL. */
    public boolean exists(String url) throws HoneycombTestException {
        try {
            CmdResult rc = getFile(url, false);
            return true;
        }
        catch (HoneycombTestException e) {
            if (e.getMessage().startsWith("getFile error 404"))
                return false;
            throw e;
        }
    }

    public CmdResult list(String path) throws HoneycombTestException {
        return list(path, 1);
    }

    public CmdResult list(String path, int depth) throws HoneycombTestException {
        return list(path, depth, true);
    }

    public CmdResult list(String path, int depth, boolean detailed)
            throws HoneycombTestException {
        CmdResult cr = new CmdResult();
        LinkedList files = new java.util.LinkedList();

        // set up query
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        sb.append("<D:propfind XMLNS:D=\"DAV:\">");
        if (detailed)
            sb.append("<D:allprop/>"); // names & values
        else
            sb.append("<D:propname/>"); // names only
        sb.append("</D:propfind>");
        byte[] qbuf = getUTF8Bytes(sb.toString());

        NVPair[] headers = new NVPair[4];
        headers[0] = CLIENT;

        if (depth < 0)
            headers[1] = new NVPair("depth", "infinity");
        else
            headers[1] = new NVPair("depth", Integer.toString(depth));

        headers[2] = new NVPair("Content-type", "text/xml");
        headers[3] = new NVPair("Content-length", Integer.toString(qbuf.length));

        try {
            //
            //  query
            //
            long t1 = System.currentTimeMillis();
            HTTPResponse rsp = conn.ExtensionMethod("PROPFIND", path, 
                                                    qbuf, headers);
            cr.time = System.currentTimeMillis() - t1;

            if (rsp.getStatusCode() >= 300)
                throw makeTestException("list", path, rsp);

            cr.pass = true;

            //
            //  parse response
            //
            cr.string = new String(rsp.getData());
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(new StringReader(cr.string));
            List l = doc.getContent();
            Iterator it = l.iterator();
            Element root = null;
            int ct = 0;
            while (it.hasNext()) {
                ct++;
                root = (Element)it.next();
            }
            if (ct != 1)
                throw new HoneycombTestException("Expected 1 content element, got " + ct);
            if (!root.getName().equals("multistatus")) 
                throw new HoneycombTestException("Expected multistatus, got " +
                                                 root.getName());

            l = root.getAttributes();
            if (l.size() != 0)
                throw new HoneycombTestException("Unexpected: root elt has attributes");

            cr.count = 0;
            cr.list = new java.util.LinkedList();
            l = root.getChildren();
            it = l.iterator();
            while (it.hasNext()) {
                cr.count++;
                Element e = (Element)it.next();
                if (!e.getName().equals("response"))
                    throw new HoneycombTestException("Unexpected: child: " +
                                                     e.getName());
                //
                //  parse this response element
                //
                //  response.href
                Element href = e.getChild("href", XMLNS);
                if (href == null)
                    throw new HoneycombTestException("No href: " + e);
                String epath = href.getValue();
                if (epath == null)
                    throw new HoneycombTestException("empty href: " + e);
                int ix = epath.indexOf("/webdav");
                if (ix == -1)
                    throw new HoneycombTestException("href missing '/webdav': " + e);
                epath = epath.substring(ix);
                //Log.INFO("href.path: " + epath);

                //  response.propstat
                Element propstat = e.getChild("propstat", XMLNS);
                if (propstat == null)
                    throw new HoneycombTestException("No propstat (" +
                                                     epath + "): " + e);
                //  response.propstat.status
                Element status = propstat.getChild("status", XMLNS);
                if (status == null)
                    throw new HoneycombTestException("No status (" + 
                                                     epath + "): " + e);
                String estatus = status.getValue();
                if (!estatus.equals("HTTP/1.1 200 OK"))
                    throw new HoneycombTestException("Bad status (" +
                                                     estatus + "): " + e);
                //  response.propstat.prop
                Element prop = propstat.getChild("prop", XMLNS);
                if (prop == null)
                    throw new HoneycombTestException("No prop (" +
                                                     epath + "): " + e);
                boolean isCollection = false;
                Element rtype = prop.getChild("resourcetype", XMLNS);
                if (rtype != null) {
                    Element coll = rtype.getChild("collection", XMLNS);
                    if (coll == null)
                        throw new HoneycombTestException("Resourcetype != collection: " + rtype);
                    isCollection = true;
                }
                String ecttype = null;
                Element cttype = prop.getChild("getcontenttype", XMLNS);
                if (cttype == null)
                    Log.WARN(
                             "No getcontenttype: " + epath);
                else
                    ecttype = cttype.getValue();
                String emode = null;
                Element mode = prop.getChild("mode", null);
                if (mode != null)
                    emode = mode.getValue();
                Element displayname = prop.getChild("displayname", XMLNS);
                if (displayname == null)
                    throw new HoneycombTestException("displayname is null (" +
                                                     epath + "): " + e);
                String edisplayname = displayname.getValue();
                Element create = prop.getChild("creationdate", XMLNS);
                if (create == null)
                    throw new HoneycombTestException("creationdate is null (" +
                                                     epath + "): " + e);
                String ecreate = create.getValue();
                Element length = prop.getChild("getcontentlength", XMLNS);
                if (length == null)
                    throw new HoneycombTestException("getcontentlength is null (" +
                                                     epath + "): " + e);
                long llength = Long.parseLong(length.getValue());

                // different for dir vs. node
                String eoid = null;
                String lastmod = null;
                int uid = -1;
                int gid = -1;
                if (!isCollection) {
                    Element el = prop.getChild("hc-oid", XMLNS);
                    if (el != null)
                        eoid = el.getValue();
                    el = prop.getChild("getlastmodified", XMLNS);
                    if (el == null)
                        throw new HoneycombTestException("Not collection but no getlastmodified (" +
                                                         epath + "): " + e);
                    lastmod = el.getValue();
                    el = prop.getChild("uid", null);
                    if (el != null)
                        uid = Integer.parseInt(el.getValue());
                    el = prop.getChild("gid", null);
                    if (el != null)
                        gid = Integer.parseInt(el.getValue());
                }

                // 
                //  consistency checks
                //
                if (isCollection  &&  (ecttype == null  ||
                                       !ecttype.equals("httpd/unix-directory")))
                    throw new HoneycombTestException("Collection but getcontenttype is " + 
                                                     ecttype + " (" + epath + "): " + e);
                //
                //  add to list
                //
                ListResponse lr = new ListResponse(epath, isCollection,
                                                   ecttype, edisplayname,
                                                   emode, ecreate, llength,
                                                   eoid, lastmod, uid, gid);
                if (isCollection)
                    cr.list.add(lr);
                else
                    files.add(lr);
            }
        } catch (HoneycombTestException e) {
            throw e;
        } catch (Exception e) {
            //Log.ERROR("ex: " + e);
            //e.printStackTrace();
            cr.pass = false;
            cr.addException(e);
        }

        //
        //  sort files into directories => note that
        //  directories are not nested
        //
        Iterator it = files.iterator();
        while (it.hasNext()) {
            ListResponse lr = (ListResponse) it.next();
            //
            //  '/' is escaped in attributes, so lastIndexOf('/') 
            //  is really the end of the dirs and not e.g. a mimetime 
            //  '/' as in "app/xxx".
            //
            String dirPath = lr.path.substring(0, lr.path.lastIndexOf('/')+1);
            Iterator it2 = cr.list.iterator();
            while (it2.hasNext()) {
                ListResponse lr2 = (ListResponse) it2.next();
                if (lr2.path.equals(dirPath)) {
                    it.remove();
                    lr2.addFile(lr);
                    break;
                }
            }
        }
        // shouldn't be any files left over, but just in case
        if (files.size() > 0) {
            if (cr.list.size() > 0) {
                it = files.iterator();
                while (it.hasNext()) {
                    ListResponse lr = (ListResponse) it.next();
                    Log.INFO("orphan: " + lr.path);
                }
            }
            cr.list.addAll(files);
        }
        return cr;
    }

    public static class ListResponse {
        public String path;
        public boolean isCollection;
        public String getcontenttype;
        public String displayname;
        public String mode;
        public String creationdate;
        public long length;
        public String oid;
        public String lastmod;
        public int uid, gid;
        public java.util.LinkedList contents = null;

        public ListResponse(String path, boolean isCollection, 
                            String getcontenttype, String displayname, 
                            String mode, String creationdate,
                            long length, String oid, 
                            String lastmod, int uid, int gid) {
            this.path = path;
            this.isCollection = isCollection;
            this.getcontenttype = getcontenttype;
            this.displayname = displayname;
            this.mode = mode;
            this.creationdate = creationdate;
            this.length = length;
            this.oid = oid;
            this.lastmod = lastmod;
            this.uid = uid;
            this.gid = gid;
        }
        public void addFile(ListResponse f) {
            if (contents == null)
                contents = new LinkedList();
            contents.add(f);
        }
        public String toString() {
            return toString("");
        }
        public String toString(String prefix) {
            StringBuffer sb = new StringBuffer();
            sb.append(prefix).append("path: ").append(path).append('\n');
            sb.append(prefix).append("disp: ").append(displayname).append('\n');
            sb.append(prefix).append("coll: ").append(isCollection).append('\n');
            sb.append(prefix).append("uid:  ").append(uid).append('\n');
            sb.append(prefix).append("gid:  ").append(gid).append('\n');
            sb.append(prefix).append("mode: ").append(mode).append('\n');
            if (contents != null) {
                Iterator it = contents.iterator();
                while (it.hasNext()) {
                    ListResponse lr = (ListResponse) it.next();
                    sb.append(lr.toString("  "));
                }
            }
            return sb.toString();
        }
    }

    public long getServerTime() throws HoneycombTestException {
        try {
            return conn.Get("/").getHeaderAsDate("Date").getTime();
        }
        catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    /** Beautify and convert an HTTP error to a Test exception */
    private HoneycombTestException makeTestException(String tag, String url,
                                                     HTTPResponse response) {
        int status = 0;
        String msg = null;

        List st = new LinkedList();
        List output = new LinkedList();

        try {
            output.add(new String(response.getData()));
            status = response.getStatusCode();
            String reason = URLEncoder.decode(response.getReasonLine());
            st.add(reason);

            msg = tag + " error " + status + ": " + reason;
            Log.INFO(msg);
        }
        catch (Exception e) {
            e.printStackTrace();
            msg = e.getMessage();
        }

        HoneycombTestException e = new HoneycombTestException(msg);
        e.exitStatus = new ExitStatus(url, status, output, st);

        return e;
    }

    public byte[] getUTF8Bytes(String s) {
        try {
            return s.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // Use the platform default
            return s.getBytes();
        }
    }

}
