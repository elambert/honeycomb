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



package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.client.SystemRecord;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *  Holder for whitebox info about an oid.
 */
public class HOidInfo implements Serializable {

    // from honeycomb.common.NewObjectIdentifier
    public static final byte NULL_TYPE = 0;
    public static final byte DATA_TYPE = 1;
    public static final byte METADATA_TYPE = 2;
    public static final int UNDEFINED = -1;

    public String oid = null; 
    public String int_oid = null;

    public int type = UNDEFINED;
    public int chunk = UNDEFINED;

    public boolean missingFrags = false;
    public boolean extraFrags = false;

    public SystemRecord sr = null;

    public HOidInfo other = null;  // MD or data, depending

    public ArrayList chunks = null;

    public String toString() {
        
        StringBuffer sb = new StringBuffer();
        sb.append(oid).append(" chunk ").append(chunk).append("\n");
        sb.append("\tinternal oid: ").append(int_oid).append("\n");
        sb.append("\ttype: ");
        switch(type) {
            case DATA_TYPE:
                sb.append("data\n"); break;
            case METADATA_TYPE:
                sb.append("metadata\n"); break;
            default:
                sb.append("other [?]\n"); break; // shouldn't happen
        }
        if (chunks == null  ||  chunks.size() == 0) {
            sb.append("\tchunks:  none\n");
        } else {

            if (missingFrags)
                sb.append("\tMISSING FRAGS\n");
            if (extraFrags)
                sb.append("\tEXTRA FRAGS\n");

            for (int i=0; i<chunks.size(); i++) 
                sb.append(chunks.get(i).toString());
        }
        if (other != null)
            sb.append("other oid:\n").append(other.toString());
        return sb.toString();
     }       
}

