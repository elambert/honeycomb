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



package com.sun.honeycomb.fs;

import java.util.Map;
import com.sun.honeycomb.adapter.UID;

public class FileAttrs {
    private UID parentOid; // only set for Regular Files
    private UID oid;
    private long atime;
    private long mtime;
    private long size;
    private Map extraAttributes;
    
    public FileAttrs(UID nParentOid,
                     UID nOid,
                     long nAtime,
                     long nSize,
                     Map nExtraAttributes) {
        parentOid = nParentOid;
        oid = nOid;
        atime = nAtime;
        mtime = nAtime;
        size = nSize;
        extraAttributes = nExtraAttributes;
    }
    
    public FileAttrs(UID nOid,
                     long nAtime,
                     long nSize,
                     Map nExtraAttributes) {
        parentOid = null;
        oid = nOid;
        atime = nAtime;
        mtime = nAtime;
        size = nSize;
        extraAttributes = nExtraAttributes;
    }
    
    public UID getParentOid() {
        return (parentOid);
    }

    public UID getOid() {
        return(oid);
    }
    
    public long getATime() {
        return(atime);
    }

    public long getMTime() {
        return (mtime);
    }
    
    public long getSize() {
        return(size);
    }

    public void setSize(long sz) {
        size = sz;
    }    

    public void setMTime(long tm) {
        mtime = tm;
        //XXX update atime ? different filesystems implement this differently
        atime = tm;
    }

    public void setATime(long tm) {
        atime = tm;
    }
    
    public Map getExtraAttributes() {
        return(extraAttributes);
    }
}
