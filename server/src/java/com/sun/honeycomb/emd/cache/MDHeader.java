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



package com.sun.honeycomb.emd.cache;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.oa.FragmentFooter;
import java.io.ByteArrayOutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

public class MDHeader {
    private static final Logger LOG = Logger.getLogger(MDHeader.class.getName());
    
    private static final int CURRENT_VERSION         = 1;

    private int version;
    private String cacheId;
    
    public MDHeader(String nCacheId) {
        version = CURRENT_VERSION;
        cacheId = nCacheId;
    }

    public MDHeader(byte[] data) 
        throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(data);
        DataInputStream input = new DataInputStream(stream);
        version = input.readInt();
        cacheId = input.readUTF();
        if (cacheId.length() == 0) {
            cacheId = null;
        }
    }
    
    public byte[] toByteArray() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(FragmentFooter.METADATA_FIELD_LENGTH);
        DataOutputStream output = new DataOutputStream(stream);
        
        try {
            output.writeInt(version);
            output.writeUTF(cacheId);
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to encode the MD header",
                    e);
            return(null);
        }
        
        if (stream.size() > FragmentFooter.METADATA_FIELD_LENGTH) {
            throw new RuntimeException("The MD header exceeds the footer space ["+
                                       stream.size()+
                                       " - "+FragmentFooter.METADATA_FIELD_LENGTH+"]");
        }

        byte[] result = new byte[FragmentFooter.METADATA_FIELD_LENGTH];
        for (int i=0; i<stream.size(); i++) {
            result[i] = stream.toByteArray()[i];
        }

        return(result);
    }

    public String getCacheId() { return(cacheId); }
}
