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



package com.sun.honeycomb.common;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.StringBuffer;
import com.sun.honeycomb.common.EMDSchemaAttribute;

/** Class representing Schema that is fit for public consumption */
public class EMDSchema {
    
    // Pass in a list of SchemaAttriute objects
    public EMDSchema(List list) {
        if(list == null) {
            list = new ArrayList();
        } else {
            this.list = list;
        }
    }
    
    public List getAttributes() {
        return list;
    }
    
    public void serialize(DataOutput dout) throws IOException {
        dout.writeInt(list.size());
        Iterator iter = list.iterator();
        while(iter.hasNext()) {
            ((EMDSchemaAttribute) iter.next()).serialize(dout);
        }
    }
    
    public static EMDSchema deserialize(DataInput din) throws IOException {
        int attrNum = din.readInt();
        List l = new ArrayList(attrNum);
        while (attrNum > 0) {
            l.add(EMDSchemaAttribute.deserialize(din));
            attrNum--;
        }
	
        return new EMDSchema(l);
    }
    
    private List list = null;
    
    public String toString() {
        Iterator ite = list.iterator();
        StringBuffer result = new StringBuffer();
        
        while (ite.hasNext()) {
            result.append(((EMDSchemaAttribute)ite.next()).toString()+"\n");
        }

        return(result.toString());
    }
}
