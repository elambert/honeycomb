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



package com.sun.honeycomb.protocol;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpRecoverableException;
import org.apache.commons.httpclient.MethodRetryHandler;
import org.apache.commons.httpclient.methods.GetMethod;

public class SimpleRetrieveTest {

    static {
        System.out.println("SimpleRetrieveTest.static");
        suppressClientLogging();
    }

    private static void suppressClientLogging() {
        suppressLoggingForClass(HttpMethodBase.class);
        suppressLoggingForClass(HttpConnection.class);
    }

    private static void suppressLoggingForClass(Class clazz) {
        Logger logger = Logger.getLogger(clazz.getName());
        logger.setLevel(Level.OFF);
    }

    public static void main(String[] args) throws IOException {
/*
        retrieveObjectClassic(args[0], args[1] + "-classic");
        System.gc();

        retrieveObjectNIO(args[0], args[1] + "-nio");
        System.gc();
*/
        retrieveObjectHybrid(args[0], args[1] + "-hybrid");
        System.gc();
    }

    private static void retrieveObjectClassic(String oid, String path) throws IOException {
        System.out.println("classic");

        OutputStream out = new FileOutputStream(path);
        HttpClient client = new HttpClient();
        GetMethod method = new GetMethod();

        HostConfiguration config = new HostConfiguration();
        config.setHost("localhost", 8080);

        method.setHostConfiguration(config);
        method.setPath("/retrieve");
        method.setQueryString("id=" + oid);

        client.executeMethod(method);
        InputStream in = method.getResponseBodyAsStream();

        byte[] bytes = new byte[16 * 1024];

        long size = 0;
        int read = 0;
        long before = System.currentTimeMillis();

        while ((read = in.read(bytes)) >= 0) {
            out.write(bytes, 0, read);
            size += read;
        }

        long after = System.currentTimeMillis();
        System.out.println("    size = " + size);
        System.out.println("    read took " + (after - before) + " ms");

        method.releaseConnection();
        method.recycle();
    }

    private static void retrieveObjectNIO(String oid, String path) throws IOException {
        System.out.println("nio");

        WritableByteChannel outChannel = new FileOutputStream(path).getChannel();
        HttpClient client = new HttpClient();
        GetMethod method = new GetMethod();

        HostConfiguration config = new HostConfiguration();
        config.setHost("localhost", 8080);

        method.setHostConfiguration(config);
        method.setPath("/retrieve");
        method.setQueryString("id=" + oid);

        client.executeMethod(method);
        InputStream in = method.getResponseBodyAsStream();
        ReadableByteChannel inChannel = Channels.newChannel(in);

        ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);

        long size = 0;
        int read = 0;
        long before = System.currentTimeMillis();

        while ((read = inChannel.read(buffer)) >= 0) {
            buffer.flip();

            while (buffer.hasRemaining() && outChannel.write(buffer) >= 0);

            if (buffer.hasRemaining()) {
                 throw new RuntimeException("failed to write entire buffer to channel");
            }

            buffer.rewind();
            size += read;
        }

        long after = System.currentTimeMillis();
        System.out.println("    size = " + size);
        System.out.println("    read took " + (after - before) + " ms");

        method.releaseConnection();
        method.recycle();
    
    }

    private static void retrieveObjectHybrid(String oid, String path) throws IOException {
        System.out.println("hybrid2");

        WritableByteChannel outChannel = new FileOutputStream(path).getChannel();
        HttpClient client = new HttpClient();

        client.setConnectionTimeout(0);
        client.setTimeout(0);

        GetMethod method = new GetMethod();

        HostConfiguration config = new HostConfiguration();
        config.setHost("localhost", 8080);

        method.setHostConfiguration(config);
        method.setPath("/retrieve");
        method.setQueryString("id=" + oid);
        method.setMethodRetryHandler(new NullRetryHandler());

        int status = client.executeMethod(method);
        InputStream in = method.getResponseBodyAsStream();

        ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
        byte[] bytes = buffer.array();

        long size = 0;
        int read = 0;
        long before, after, totalTime = 0;

        before = System.currentTimeMillis();

        while ((read = in.read(bytes)) >= 0) {
            after = System.currentTimeMillis();
            totalTime += (after - before);

            System.out.println("    read took " + (after - before) + " ms");
            System.out.println("    read " + read + " bytes");

            int written, totalWritten = 0;

            buffer.position(0);
            buffer.limit(read);

            while (totalWritten < read &&
                   (written = outChannel.write(buffer)) >= 0) {
                   totalWritten += written;
            }

            if (totalWritten < read) {
                throw new RuntimeException("failed to write entire buffer");
            }

            size += read;
            before = System.currentTimeMillis();
        }

        System.out.println("read took " + totalTime + " ms");
        System.out.println("size = " + size);

        method.releaseConnection();
        method.recycle();
    }
    
    private static final class NullRetryHandler implements MethodRetryHandler {

        public boolean retryMethod(HttpMethod method,
                                   HttpConnection connection,
                                   HttpRecoverableException recoverableException,
                                   int executionCount,
                                   boolean requestSent) {
            return false;
        }
    }
}
