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
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.disks.Disk;

public class FragmentFileSetPerf
    extends FragmentFileSet {

    FragmentFileSetPerf(NewObjectIdentifier oid, Disk[] layout, 
                        ObjectReliability rel) throws OAException {
        super(oid, layout, rel);
    }

    FragmentFileSetPerf(NewObjectIdentifier oid,
                        Disk[] layout, 
                        ObjectReliability rel,
                        int recoverFlag,
                        boolean isLocal) throws OAException {
        super(oid, layout, rel, recoverFlag, isLocal);
    }

    FragmentFileSetPerf(NewObjectIdentifier oid,
                        Disk[] allDisks,
                        ObjectReliability rel,
                        boolean crawl) throws OAException {
        super(oid, allDisks, rel, crawl);
    }

    public FragmentFileSetPerf() {
        super();
    }

    public void create(NewObjectIdentifier link,
                       long size,
                       long create, 
                       long retention,
                       long experation,
                       long autoClose, 
                       long deletion,
                       byte shred,
                       short checksumAlg,
                       int fragmentSize,
                       int chunkSize)
        throws OAException {
        
        long startTime = System.currentTimeMillis();
        super.create(link, size, create, retention, experation, autoClose,
                     deletion, shred, checksumAlg, fragmentSize, chunkSize);
        long endTime = System.currentTimeMillis();
        
        OAClientStats.fragmentFileSetCreate.set(new Long(endTime-startTime));
    }
}
