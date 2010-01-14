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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.common.ByteArrays;
import java.nio.ByteBuffer;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.resources.ByteBufferList;

public class OAClientPerf
    extends OAClient {

    private static OAClient oaclient = null;
    
    public static OAClient getInstance() {
        synchronized (OAClient.class) {
            if (oaclient == null) {
                // This is the first time getInstance has been called
                oaclient = new OAClientPerf(false);
            }
        }
        return oaclient;
    }
    
    private OAClientPerf(boolean isTest) {
        super(isTest);
    }

    protected void commit(boolean flush,
                          Context ctx)
        throws OAException {

        boolean firstCommit = (OAClientStats.commit.get() == null);
        
        long startTime = System.currentTimeMillis();
        super.commit(flush, ctx);
        long endTime = System.currentTimeMillis();

        if (firstCommit) {
            OAClientStats.commit.set(new Long(endTime-startTime));
        }
    }
}