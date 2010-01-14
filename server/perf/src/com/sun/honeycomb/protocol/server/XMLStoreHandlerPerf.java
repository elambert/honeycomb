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

import com.sun.honeycomb.common.SystemMetadata;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpFields;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.io.FileWriter;
import java.io.File;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.common.NewObjectIdentifier;
import java.nio.ByteBuffer;
import com.sun.honeycomb.oa.OAClientStats;

public class XMLStoreHandlerPerf
    extends XMLStoreHandler {

    private static final String OUTPUT_FILE = "/data/0/perf/StoreHandlerPerf.txt";
    private static final Logger LOG = Logger.getLogger(XMLStoreHandlerPerf.class.getName());
    private static FileWriter output = null;

    public static ThreadLocal threadLocal = new ThreadLocal();
    
    public XMLStoreHandlerPerf(final String name) {
        super(name);
        init();
    }

    public XMLStoreHandlerPerf() {
        super();
        init();
    }

    private void init() {
        try {
            synchronized (XMLStoreHandlerPerf.class) {
                if (output != null) {
                    return;
                }

                File file = new File(OUTPUT_FILE);
                boolean preexisted = file.exists();
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
            
                output = new FileWriter(file, true);

                if (!preexisted) {
                    printHeader();
                }
            }
            
        } catch (IOException e) {
            RuntimeException newe = new RuntimeException("Failed to create the profile file ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
        
        LOG.info("PERF The XMLStoreHandler perf class has been initialized");
    }

    private void printHeader() 
        throws IOException {
        output.write("#\n"+
                     "# XMLStoreHandler performance report (Honeycomb cluster)\n"+
                     "#\n"+
                     "# The field list is :\n"+
                     "# - time when the operation started\n"+
                     "# - Thread name\n"+
                     "# - size of manipulated data\n"+
                     "# - time (in ms.) used to perform the while store operation\n"+
                     "# - time (in ms.) used to perform the open\n"+
                     "# - time (in ms.) used to perform the write(s)\n"+
                     "# - time (in ms.) used to perform the close\n"+
                     "# - FragmentFileSet.create time\n"+
                     "# - OA.commit in open\n"+
                     "#\n\n");
        output.flush();
    }

    protected SystemMetadata handleStore(final HttpRequest request,
                                         final HttpResponse response,
                                         final HttpFields trailer,
                                         final InputStream in) 
        throws ArchiveException, IOException {

        OAClientStats.fragmentFileSetCreate.set(null);
        OAClientStats.commit.set(null);

        long startTime = System.currentTimeMillis();

        Coordinator coord = Coordinator.getInstance();
        NewObjectIdentifier oid = create(request,
                                         response,
                                         trailer,
                                         coord,
                                         in);

        long afterOpen = System.currentTimeMillis();

        if (oid == null) {
            return null;
        }

        ByteBufferPool bufferPool = ByteBufferPool.getInstance();
        ByteBuffer buffer = null;
        int bufferSize = coord.getWriteBufferSize();
        byte[] bytes = new byte[bufferSize];
        int read = 0;
        long bytesWritten = 0;

        while (read >= 0) {
            buffer = bufferPool.checkOutBuffer(bufferSize);
            int bufferRead = 0;

            try {
                // read until we get some data or reach the end of the byte array
                while (bufferRead < bufferSize &&
                       (read = in.read(bytes, bufferRead, bufferSize - bufferRead)) >= 0) {
                    bufferRead += read;
                }

                if (bufferRead > 0) {
                    // copy the bytes to the direct buffer
                    buffer.put(bytes, 0, bufferRead);
                    buffer.flip();

                    write(coord, oid, buffer, bytesWritten);

                    bytesWritten += bufferRead;
                }
            } finally {
                bufferPool.checkInBuffer(buffer);
            }
        }

        long afterWrite = System.currentTimeMillis();

        SystemMetadata result = coord.close(oid);

        long endTime = System.currentTimeMillis();
            
        try {
            output.write(startTime+
                         " "+Thread.currentThread().getName()+
                         " "+result.getSize()+
                         " "+(endTime-startTime)+
                         " "+(afterOpen-startTime)+
                         " "+(afterWrite-afterOpen)+
                         " "+(endTime-afterWrite)+
                         " "+OAClientStats.fragmentFileSetCreate.get()+
                         " "+OAClientStats.commit.get()+
                         "\n");
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "PERF Failed to flush record",
                    e);
        }
        
        return(result);
    }
}
