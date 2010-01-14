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



package com.sun.honeycomb.coordinator;

import java.io.IOException;
import java.io.FileWriter;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.coordinator.Coordinator;
import java.io.File;

public class CoordinatorPerf
    extends Coordinator {
    
    private static final String OUTPUT_FILE = "/data/0/perf/CoordinatorPerf.txt";
    private static final Logger LOG = Logger.getLogger(CoordinatorPerf.class.getName());
    
    private static Coordinator instance;
    public static Coordinator getInstance() {
        synchronized (Coordinator.class) {
            if (instance == null) {
                instance = new CoordinatorPerf();
            }
        }

        return instance;
    }
    
    private static FileWriter output = null;

    public CoordinatorPerf() {
        super();
        try {
            File file = new File(OUTPUT_FILE);
            boolean preexisted = file.exists();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            
            output = new FileWriter(file, true);

            if (!preexisted) {
                printHeader();
            }

        } catch (IOException e) {
            RuntimeException newe = new RuntimeException("Failed to create the profile file ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        LOG.info("PERF The coordinator perf class has been initialized");
    }

    private void printHeader() 
        throws IOException {
        output.write("#\n"+
                     "# Coordinator performance report (Honeycomb cluster)\n"+
                     "#\n"+
                     "# The field list is :\n"+
                     "# - time when the operation started\n"+
                     "# - Thread name\n"+
                     "# - size of manipulated data\n"+
                     "# - time (in ms.) used to perform the operation\n"+
                     "#\n\n");
        output.flush();
    }

    public void writeData(NewObjectIdentifier oid,
                          ByteBuffer buffer,
                          long offset,
                          boolean explicitClose)
        throws ArchiveException {
        long startTime = System.currentTimeMillis();
        long size = buffer.remaining();
        super.writeData(oid, buffer, offset, explicitClose);
        long stopTime = System.currentTimeMillis();
        
        try {
            output.write(startTime+
                         " "+Thread.currentThread().getName()+
                         " "+size+
                         " "+(stopTime-startTime)+
                         "\n");
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "PERF Failed to flush record",
                    e);
        }
    }
}
