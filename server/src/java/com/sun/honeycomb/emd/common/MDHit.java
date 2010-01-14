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

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExternalObjectIdentifier;
import java.io.DataOutput;
import java.io.DataInput;
import java.io.IOException;

public class MDHit 
    implements Comparable {
    
    private NewObjectIdentifier oid;
    private float score;
    private Object extraInfo;
    private String oidString;
    


     public MDHit(NewObjectIdentifier oid,
 		 Object extraInfo) {
         this(oid, (float)1, extraInfo);
     }

     public MDHit(String oid,
 		 Object extraInfo) {
         this(oid, (float)1, extraInfo);
         oidString = oid;
     }

     public MDHit(NewObjectIdentifier nOid,
 		 float nScore,
 		 Object nExtraInfo) {
        oid = nOid;
        score = nScore;
        extraInfo = nExtraInfo;
     }

    /// regenerate oid from oidString in internal Hex Format
    public MDHit(String nOid,
                 float nScore,
                 Object nExtraInfo) {
        this(NewObjectIdentifier.fromHexString(nOid), nScore, nExtraInfo);
    }

    /// return oid in internal Hex String format
    public String getOid() {
        if (oidString == null) {
            oidString = oid.toHexString();
        }
        return oidString;
    }
    public String getExternalOid() {
        return oid.toExternalHexString();
    }

    public NewObjectIdentifier constructOid() {
        return oid;
    }
    
    public float getScore() {
	return(score);
    }

    public Object getExtraInfo() {
	return(extraInfo);
    }
    
    public int compareTo(Object other) {
        if (!(other instanceof MDHit)) {
            throw new IllegalArgumentException("other is not a MDHit");
        }
	
        MDHit o = (MDHit)other;
       
		if (Math.abs(o.score - score) > 0.000001) {
		    return(score > o.score ? -1 : 1);
		}

        return(getOid().compareTo(o.getOid()));
    }
   
    public boolean equals(Object other) {
        return (compareTo(other) == 0); 
    }
}
