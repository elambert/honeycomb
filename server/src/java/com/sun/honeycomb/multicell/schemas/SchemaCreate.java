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



package com.sun.honeycomb.multicell.schemas;

import java.io.IOException;
import java.io.File;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.common.CliConstants;
import com.sun.honeycomb.multicell.MultiCellLogger;
import com.sun.honeycomb.multicell.MultiCellException;

public class SchemaCreate {

    static private final String TMP_FILE = "/tmp/schema.xml";

    private int curChunk;
    private int nbChunks;
    private int lastChunkSize;
    private FileInputStream input;
    private MultiCellLogger logger;


    public SchemaCreate(MultiCellLogger logger)
        throws EMDConfigException, IOException, MultiCellException {

        this.logger = logger;

        File tmpFile = new File(TMP_FILE);
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        RootNamespace rootNamespace =  RootNamespace.getInstance();
        Writer out = 
          new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8");
        rootNamespace.export(out, true);
        out.close();
        curChunk = 0;
        nbChunks =
          (int) (tmpFile.length() / CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE);
        lastChunkSize =
          (int) (tmpFile.length() %  CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE);
        if (lastChunkSize > 0) {
            nbChunks += 1;
        } else {
            lastChunkSize = CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE;
        }
        try {
            input = new FileInputStream(tmpFile);
        } catch(FileNotFoundException fnf) {
            logger.logSevere("Can't find temp file " + TMP_FILE);
            throw new MultiCellException("Internal error while comparing " +
              " the schemas between cells");
        }
    }
    
    public int getNbSchemaPieces() {
        return nbChunks;
    }

    public String getNextSchemaPiece() throws IOException, MultiCellException {
        
        int nbToRead = (curChunk < (nbChunks - 1)) ? 
          CliConstants.MAX_BYTE_SCHEMA_PER_ENVELOPE : lastChunkSize;
        byte [] res = new byte[nbToRead];

        int nbRead = input.read(res, 0, nbToRead);
        if (nbRead != nbToRead) {
            logger.logSevere("Unexpected number of bytes while reading " +
              TMP_FILE);
            throw new MultiCellException("Internal error while comparing " +
              " the schemas between cells");
        }
        curChunk++;
        return new String(res, "UTF-8");
    }

    public void freeResources() {
        try {
            input.close();
        } catch (IOException ignore) {
            logger.logWarning("Failed to close input stream for " + TMP_FILE);
        }
    }
}
