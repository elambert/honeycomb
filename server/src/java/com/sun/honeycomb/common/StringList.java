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

public class StringList {
    
    // Constructor
    public StringList(List list) {
        if(list == null) {
            this.list = new ArrayList();
        } else {
            this.list = list;
        }
    }
    
    // Accessor Methods
    public List getList() {return list;}
    public int size() {return (list != null) ? list.size() : 0;}

    // Equality
    public boolean equals(Object anObject) {
        return ((anObject != null) &&
                (anObject.getClass() == this.getClass()) &&
                (((StringList)anObject).list.equals(list)));
    }
    
    // Binary Serialize
    public void serialize(DataOutput dout) throws IOException {
        Iterator iter = list.iterator();
        dout.writeInt(list.size());
        while(iter.hasNext()) {
            dout.writeUTF((String) iter.next());
        }
    }
    
    // Text Serialize
    public String toString() {
        
        if(list.size() == 0) {
            return emptyString;
        }
        
        StringBuffer sbuf = new StringBuffer();
        Iterator iter = list.iterator();
        while(iter.hasNext()) {
            sbuf.append((String) iter.next() + "\n");
        }
        return sbuf.toString();
    }
    
    // Binary Deserialize
    public static StringList deserialize(DataInput din) 
        throws IOException {
        
        int listSize = din.readInt();
        ArrayList list = new ArrayList(listSize);
        while(listSize-- > 0){
            list.add(din.readUTF());
        }
        
        return new StringList(list);
    }

    public final static StringList EMPTY_LIST = new StringList(null);
    
    // Private Member
    private List list;
    private static final String emptyString = "* Empty *\n";
}
