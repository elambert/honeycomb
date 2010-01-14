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

import com.sun.honeycomb.common.ProtocolConstants;

import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.fscache.FSCache;
import com.sun.honeycomb.fscache.FSCacheObject;
import com.sun.honeycomb.fscache.FSCacheException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.net.URLDecoder;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class GetHandler extends SpecificHandler {

    private static final Logger logger =
        Logger.getLogger(GetHandler.class.getName());

    private static final int DEFAULT_HTTP_BUFFER_SIZE = 2048;
    private static final String PNAME_BUFSIZ = "buffer.size";

    private int bufSize = 0;

    public GetHandler() {
        bufSize = HCDAV.getIntProperty(PNAME_BUFSIZ, DEFAULT_HTTP_BUFFER_SIZE);
        logger.info("Using HTTP buffer size " + bufSize + " bytes.");
    }

    public void handle(HCFile file, String[] extraPath,
		       HttpRequest request, HttpResponse response,
		       InputStream inputStream, OutputStream outputStream)
	    throws IOException, HttpException {

        if (extraPath != null)
            throw new HttpException(HttpResponse.__404_Not_Found,
                                    "No such file: " + file.fileName() +
                                    FSCache.combine(extraPath));

        HttpFields trailer = response.getTrailer();

        try {
            if (file.isFile()) {
                if (!returnFile(file, outputStream, request, response))
                    throw new RuntimeException("Couldn't write file");
            }
            else
                listCollection(file, response, outputStream);
        }
        catch (IllegalArgumentException e) {
            String s = e.toString();
            logger.fine("Bad argument; " + s);
            sendError(response, trailer, HttpResponse.__400_Bad_Request, s);
            return;
	}
        catch (FSCacheException e) {
	    throw e.getHttpException();
	}
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (trailer != null) {
            trailer.add(TRAILER_STATUS, String.valueOf(HttpResponse.__200_OK));
            ((HttpOutputStream)outputStream).setTrailer(trailer);
        }
    }

    ////////////////////////////////////////////////////////////////////////

    // Methods that emit HTML for directory pages
    private StringBuffer newHTMLbuffer(String quotedName) {
        try {
            String name = URLDecoder.decode(quotedName, "UTF-8");

            StringBuffer buffer = new StringBuffer();
            buffer.append("<html><head><title>");
            buffer.append(name);
            buffer.append("</title></head><body><h1><tt>");
            buffer.append(htmlEncode(name));
            buffer.append("</tt></h1>\n");
            return buffer;
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    private void newEntry(StringBuffer buffer, String path, String qName) {
        try {
            String name = URLDecoder.decode(qName, "UTF-8");

            buffer.append("<a href=\"").append(ProtocolConstants.WEBDAV_PATH);
            buffer.append(path);
            buffer.append("\">");
            buffer.append(htmlEncode(name));
            buffer.append("</a><br>\n");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    private byte[] getContent(StringBuffer buffer) {
        try {
            buffer.append("<hr><p><a href=\"");
            buffer.append(ProtocolConstants.WEBDAV_PATH);
            buffer.append("/\"><i>Honeycomb</i></a>");
            buffer.append("</body></html>");
            return buffer.toString().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private String htmlEncode(String s) {
        StringBuffer sb = new StringBuffer();
        String rest = s;

        String[] comps;
        int index = 0;          // position in s

        for (;;) {
            comps = rest.split("[^A-Za-z0-9 .,-=!?*/]", 2);
            sb.append(comps[0]);

            if (comps.length == 1)
                // There are no special characters in rest, i.e. we're done
                break;

            // Bump up position in the string, and get the special char
            index += comps[0].length();
            sb.append("&#").append((int)s.charAt(index++)).append(';');

            rest = comps[1];
        }

        return sb.toString();
    }

    // Sort order of the display name.
    class HCFileComparer implements Comparator {
        public int compare(Object o1, Object o2) {
            HCFile f1 = (HCFile) o1;
            HCFile f2 = (HCFile) o2;
            return f1.displayName().compareTo(f2.displayName());
        }
        public int compareTo(Object o) {
            return (o == this)? 0 : 1;
        }
    }

    ////////////////////////////////////////////////////////////////////////

    private void listCollection(HCFile file, HttpResponse response,
				OutputStream os)
	    throws HttpException, IOException, FSCacheException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("is Dir " + file);

	if (HCFile.fileCache == null)
            throw new HttpException(HttpResponse.__500_Internal_Server_Error,
                                    "No fileCache for: " + file.fileName());

        // TODO: The entire directory listing is constructed into a
        // string and returned. This could be done better....

        StringBuffer buffer = newHTMLbuffer(file.fileName());

        newEntry(buffer, file.fileName(), ".");
        newEntry(buffer, file.parentName(), "..");

        List children = HCFile.fileCache.listChildren(null, file);

        Collections.sort(children, new HCFileComparer());

        for (Iterator i = children.iterator(); i.hasNext(); ) {
            HCFile child = (HCFile) i.next();
            newEntry(buffer, child.fileName(), child.displayName());
        }

        // Finalise: get the properly encoded (UTF-8) bytes
        byte[] content = getContent(buffer);

        response.setContentType("text/html; charset=UTF-8");
        response.setContentLength(content.length);

        long pos = 0;
        long toWrite = content.length;
        while (toWrite > 0) {
            long len = toWrite;
            if (bufSize > 0 && len > bufSize)
                len = bufSize;
            os.write(content, (int)pos, (int)len);
            pos += len;
            toWrite -= len;
        }
    }

    private boolean returnFile(HCFile file, OutputStream os,
                               HttpRequest request, HttpResponse response)
            throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("is File " + file);

        String rangeHeader = request.getField(HttpFields.__Range);
        if (rangeHeader != null && logger.isLoggable(Level.INFO))
            logger.info("Range: \"" + rangeHeader + "\" in request " +
                        MainHandler.toString(request));
        Range r = new Range(rangeHeader, file.size());

        if (logger.isLoggable(Level.FINE))
            logger.fine("Range " + r);

        if (r.offset() > 0 || r.length() < file.size()) {
            response.setStatus(HttpResponse.__206_Partial_Content);
            String hdr = "bytes " + r.offset();
            hdr += "-" + (r.offset() + r.length() - 1);
            hdr += "/" + file.size();
            response.setField("Content-Range", hdr);

            if (logger.isLoggable(Level.FINE))
                logger.fine("Added Content-Range header " + hdr);
        }

        if (file.mimeType() != null)
            response.setContentType(file.mimeType());
        else
            response.setContentType("application/octet-stream");

        if (logger.isLoggable(Level.FINE))
            logger.fine("Setting content length to " + (int) r.length());

        response.setContentLength((int) r.length());

        return file.writeContents(r.offset(), r.length(), os);
    }

    /*
      Section from RFC 2626, Sec 14.35:

      Byte range specifications in HTTP apply to the sequence of bytes
      in the entity-body (not necessarily the same as the
      message-body).

      A byte range operation MAY specify a single range of bytes, or a
      set of ranges within a single entity.

        ranges-specifier = byte-ranges-specifier
        byte-ranges-specifier = bytes-unit "=" byte-range-set
        byte-range-set  = 1#( byte-range-spec | suffix-byte-range-spec )
        byte-range-spec = first-byte-pos "-" [last-byte-pos]
        first-byte-pos  = 1*DIGIT
        last-byte-pos   = 1*DIGIT

      The first-byte-pos value in a byte-range-spec gives the
      byte-offset of the first byte in a range. The last-byte-pos
      value gives the byte-offset of the last byte in the range; that
      is, the byte positions specified are inclusive. Byte offsets
      start at zero.

      If the last-byte-pos value is present, it MUST be greater than
      or equal to the first-byte-pos in that byte-range-spec, or the
      byte- range-spec is syntactically invalid. The recipient of a
      byte-range- set that includes one or more syntactically invalid
      byte-range-spec values MUST ignore the header field that
      includes that byte-range- set.

      If the last-byte-pos value is absent, or if the value is greater
      than or equal to the current length of the entity-body,
      last-byte-pos is taken to be equal to one less than the current
      length of the entity- body in bytes.

      By its choice of last-byte-pos, a client can limit the number of
      bytes retrieved without knowing the size of the entity.

    */

    private class Range {
        private long offset;
        private long length;

        Range(String s, long fileSize) throws IllegalArgumentException {
            this.offset = 0;
            this.length = fileSize;
            parse(s, fileSize);
        }

        long offset() { return offset; }
        long length() { return length; }
        public String toString() { return "[" + offset + ":+" + length + "]"; }

        private void parse(String rng, long fileSize)
                throws IllegalArgumentException {
            if (rng == null) {
                logger.fine("Parsing (null)");
                return;
            }

            if (logger.isLoggable(Level.FINE))
                logger.fine("Parsing \"" + rng + "\"");

            if (rng.startsWith(ProtocolConstants.BYTES_PREFIX))
                rng = rng.substring(ProtocolConstants.BYTES_PREFIX.length());

            int index = rng.indexOf(ProtocolConstants.RANGE_SEPARATOR);
            if (index < 0)
                throw new IllegalArgumentException("Range \"" + rng + "\"");

            try {
                long firstByte = 0;
                long lastByte = fileSize - 1;

                String beginString = rng.substring(0, index);
                String endString = rng.substring(index + 1);

                if (beginString.length() > 0)
                    firstByte = Long.parseLong(beginString);
                if (endString.length() > 0)
                    lastByte = Long.parseLong(endString);

                if (firstByte >= fileSize) {
                    offset = fileSize;
                    length = 0;
                    return;
                }

                if (firstByte < 0)
                    firstByte = 0;
                if (lastByte < 0)
                    lastByte = 0;
                if (lastByte > fileSize - 1)
                    lastByte = fileSize - 1;
                if (firstByte > lastByte)
                    lastByte = firstByte;

                offset = firstByte;
                length = lastByte - firstByte + 1;
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private String basename(String s) {
        if (s == null)
            return null;

        int pos = s.lastIndexOf('/');
        if (pos < 0)
            return s;
        return s.substring(pos + 1);
    }
}
