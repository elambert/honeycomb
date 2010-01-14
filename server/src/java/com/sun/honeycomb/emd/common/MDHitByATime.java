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



package com.sun.honeycomb.emd.common;

import java.util.logging.Logger;

import com.sun.honeycomb.common.NewObjectIdentifier;

public class MDHitByATime extends MDHit {
 
    private Logger LOG = Logger.getLogger(MDHit.class.getName());
    
    private long _atime = -1;
   
    public MDHitByATime(NewObjectIdentifier oid , Object extrainfo, long atime) {
        this(oid.toHexString(),extrainfo, atime); 
    }
    
    public MDHitByATime(String oid, Object extraInfo, long atime) {
        super(oid, extraInfo);
        _atime = atime;
    }

    public int compareTo(Object other) {
       
        if (!(other instanceof MDHitByATime)) {
            throw new IllegalArgumentException("other is not a MDHitByATime");
        }
       
        MDHitByATime o = (MDHitByATime)other;

        if (_atime == o._atime)
            return super.compareTo(o);
        else 
            return (_atime < o._atime ? -1 : 1);
    }
    
    public String toString() {
        return  "{ oid=" + getOid() + ", atime=" + _atime + " }";
    }

    public long getATime() {
        return _atime;
    }
}
