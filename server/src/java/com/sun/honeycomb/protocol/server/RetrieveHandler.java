
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



package com.sun.honeycomb.protocol.server;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.ObjectLostException;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.BandwidthStatsAccumulator;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.resources.ByteBufferList;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

public class RetrieveHandler extends ProtocolHandler {

    public boolean mdOnly = false;

    public static BandwidthStatsAccumulator retrieveStats =
                                               new BandwidthStatsAccumulator();
    public static BandwidthStatsAccumulator retrieveMDStats =
                                               new BandwidthStatsAccumulator();

    public RetrieveHandler(final ProtocolBase newService) {
        super(newService);
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        NewObjectIdentifier identifier;
        try {
            identifier = getRequestIdentifier(request, response, trailer);
        } catch (IllegalArgumentException e) {
            // already sent error reply in superclass
            return;
        }

        Range r = null;
        try {
            r = new Range(request.getField(HttpFields.__Range));
        } catch (IllegalArgumentException e) {
            sendError(response, trailer, HttpResponse.__400_Bad_Request,
                      e.toString());
            return;
        }

        long offset = r.offset();
        long length = r.length();

        // response.setContentType(file.getMimeType());

        response.setContentType(ProtocolConstants.OCTET_STREAM_TYPE);

        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();

        try {
            if (handleRetrieve(out,
                               identifier, 
                               offset,
                               length)) {
                trailer.add(ProtocolConstants.TRAILER_STATUS,
                            String.valueOf(HttpResponse.__200_OK));
                out.setTrailer(trailer);
            }

        } catch (NoSuchObjectException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,"retrieve failed on NoSuchObjectException.",e);
            }
            sendError(response,
	      trailer,
	      HttpResponse.__404_Not_Found,
	      e);
        } catch (ObjectCorruptedException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,"retrieve failed on ObjectCorruptedException.",e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__424_Failed_Dependency,
                      e);
        } catch (ObjectLostException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,"retrieve failed on ObjectLostException.",e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__410_Gone,
                      e);
        } catch (ArchiveException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                           "retrieve failed on ArchiveException.", e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e);
        } catch (InternalException e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.SEVERE,
                           "retrieve failed on InternalException.", e);
            }
            sendError(response,
                      trailer,
                      HttpResponse.__500_Internal_Server_Error,
                      e);
        } catch (IllegalArgumentException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                           "retrieve failed on IllegalArgumentException.", e);
            }       
            sendError(response,
                      trailer,
                      HttpResponse.__400_Bad_Request,
                      e);
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                           "retrieve failed on client-side IOException.", e);
                          
            }       
            try {
                sendError(response,
                          trailer,
                          HttpResponse.__400_Bad_Request,
                          e);
            } catch (IOException ex) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO, "failed to send response.", ex);
                }
            }
        }
    }

    public boolean handleRetrieve(final OutputStream out,
                                  final NewObjectIdentifier oid,
                                  final long offset,
                                  final long length)
        throws ArchiveException, IOException {

        writeMulticellConfig(out);

        long t1 = System.currentTimeMillis();

        Coordinator coord = Coordinator.getInstance();
        long offsetToRead = (offset != Coordinator.UNKNOWN_SIZE)
                          ? offset
                          : 0;
        long lengthToRead = (length != Coordinator.UNKNOWN_SIZE)
                          ? length
                          : Long.MAX_VALUE;

        int bufferSize = coord.getReadBufferSize();
        long totalRead = 0;
        int read;
        int readLength = (int)Math.min((long)bufferSize, lengthToRead - totalRead);

        ByteBufferList bufferList = new ByteBufferList();
        byte[] bytes = new byte[bufferSize];

        long write_time = 0;

        try {

            while (totalRead < lengthToRead) {

                read = read(coord,
                                oid,
                                bufferList,
                                offsetToRead,
                                readLength,
                                readLength < bufferSize);

                if (read < 1)
                    break;

                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("read " +
                                  read +
                                  " bytes at offset " +
                                  offsetToRead);
                }

                // accumulate the write time to subtract
                long t2 = System.currentTimeMillis();
                writeBufferList(bufferList, bytes, out);
                write_time += System.currentTimeMillis() - t2;

                bufferList.clear();

                totalRead += read;
                offsetToRead += read;
                readLength = (int)Math.min((long)bufferSize, lengthToRead - totalRead);

                if (read < bufferSize) {
                    break;
                }
            }
        } finally {
            bufferList.clear();
        }
        long read_time = System.currentTimeMillis() - t1;

        read_time -= write_time;

        if (mdOnly) {
            retrieveMDStats.add(totalRead, read_time);
        } else {
            retrieveStats.add(totalRead, read_time);
        }
 
        if (LOGGER.isLoggable(Level.INFO)) {

            if (mdOnly)
                LOGGER.info("MEAS getmd __ size " + totalRead + 
                                                      " time " + read_time);
            else if (length == Coordinator.UNKNOWN_SIZE)
                LOGGER.info("MEAS retrieve __ size " + totalRead + 
                                                      " time " + read_time);
            else
                LOGGER.info("MEAS rretrieve __ size " + totalRead + 
                                                      " time " + read_time);
        }
        return true;
    }

    protected int read(Coordinator coordinator,
                       NewObjectIdentifier oid,
                       ByteBufferList bufferList,
                       long offset,
                       int length,
                       boolean lastRead)
        throws ArchiveException {
        return coordinator.readData(oid,
                                    bufferList,
                                    offset,
                                    length,
                                    lastRead);
    }

    private void writeBufferList(ByteBufferList bufferList,
                                 byte[] bytes,
                                 OutputStream out) throws IOException {
        ByteBuffer[] buffers = bufferList.getBuffers();

        for (int i = 0; i < buffers.length; i++) {
            int toWrite = buffers[i].remaining();

            buffers[i].get(bytes, 0, toWrite);
            out.write(bytes, 0, toWrite);
        }
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
        private long offset = 0;
        private long length = Coordinator.UNKNOWN_SIZE;
        Range(String s) throws IllegalArgumentException {
            if (s == null)
                // We'll use default values
                return;

            parse(s);
        }

        long offset() { return offset; }
        long length() { return length; }

        private void parse(String range) throws IllegalArgumentException {
            if (range.startsWith(ProtocolConstants.BYTES_PREFIX))
                range = range.substring(ProtocolConstants.BYTES_PREFIX.length());

            int index;

            if ((index = range.indexOf(ProtocolConstants.RANGE_SEPARATOR)) >= 0) {
                String beginString = range.substring(0, index);
                String endString = range.substring(index + 1);
                long lastByte = Coordinator.UNKNOWN_SIZE;

                if (beginString.length() > 0) {
                    try {
                        offset = Long.parseLong(beginString);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Range first-byte \"" +
                                            beginString + "\" non-numeric");
                    }
                }

                if (endString.length() > 0) {
                    try {
                        lastByte = Long.parseLong(endString);
                        length = lastByte - offset + 1;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Range last-byte \"" + endString +
                                            "\" non-numeric");
                    }
                }

                return;
            }

            throw new IllegalArgumentException("bad range");
        }
    }

}
